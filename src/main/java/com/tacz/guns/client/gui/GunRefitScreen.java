package com.tacz.guns.client.gui;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.animation.statemachine.AnimationStateMachine;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.event.client.GunRefitScreenRenderEvent;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.attachment.UniversalAttachmentType;
import com.tacz.guns.client.animation.screen.RefitTransform;
import com.tacz.guns.client.event.FirstPersonRenderEvent;
import com.tacz.guns.client.event.FirstPersonRenderGunEvent;
import com.tacz.guns.client.gui.components.FlatColorButton;
import com.tacz.guns.client.gui.components.refit.*;
import com.tacz.guns.client.renderer.item.AnimateGeoItemRenderer;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import com.tacz.guns.client.sound.SoundPlayManager;
import com.tacz.guns.compat.oculus.OculusCompat;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.network.message.ClientMessageLaserColor;
import com.tacz.guns.network.message.ClientMessageRefitGun;
import com.tacz.guns.network.message.ClientMessageUnloadAttachment;
import com.tacz.guns.sound.SoundManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LightLayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = GunMod.MOD_ID)
public class GunRefitScreen extends Screen {
    public static final ResourceLocation SLOT_TEXTURE = new ResourceLocation(GunMod.MOD_ID, "textures/gui/refit_slot.png");
    public static final ResourceLocation TURN_PAGE_TEXTURE = new ResourceLocation(GunMod.MOD_ID, "textures/gui/refit_turn_page.png");
    public static final ResourceLocation UNLOAD_TEXTURE = new ResourceLocation(GunMod.MOD_ID, "textures/gui/refit_unload.png");
    public static final ResourceLocation ICONS_TEXTURE = new ResourceLocation(GunMod.MOD_ID, "textures/gui/refit_slot_icons.png");

    public static final int ICON_UV_SIZE = 32;
    public static final int SLOT_SIZE = 18;
    private static final int INVENTORY_ATTACHMENT_SLOT_COUNT = 8;
    private static boolean HIDE_GUN_PROPERTY_DIAGRAMS = true;

    private int currentPage = 0;

    public GunRefitScreen() {
        super(Component.literal("Gun Refit Screen"));
        RefitTransform.init();
    }

    public static int getSlotTextureXOffset(ItemStack gunItem, AttachmentType attachmentType) {
        IGun iGun = IGun.getIGunOrNull(gunItem);
        if (iGun == null) {
            return -1;
        }
        if (!iGun.allowAttachmentType(gunItem, attachmentType)) {
            return ICON_UV_SIZE * 6;
        }
        switch (attachmentType) {
            case GRIP -> {
                return 0;
            }
            case LASER -> {
                return ICON_UV_SIZE;
            }
            case MUZZLE -> {
                return ICON_UV_SIZE * 2;
            }
            case SCOPE -> {
                return ICON_UV_SIZE * 3;
            }
            case STOCK -> {
                return ICON_UV_SIZE * 4;
            }
            case EXTENDED_MAG -> {
                return ICON_UV_SIZE * 5;
            }
        }
        return -1;
    }

    public static int getSlotsTextureWidth() {
        return ICON_UV_SIZE * 7;
    }

    @Override
    public void init() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        boolean hasGun = false;
        for (int i = 0; i < 9; i++) {
            if (player.getInventory().getItem(i).getItem() instanceof IGun) {
                hasGun = true;
                break;
            }
        }
        this.clearWidgets();
        // 添加配件槽位
        this.addAttachmentTypeButtons();
        // 添加可选配件列表
        this.addInventoryAttachmentButtons();
        // 添加属性图隐藏按钮
        if (!hasGun) {
            this.addRenderableWidget(new FlatColorButton(
                    11, 11, 310, 16,
                    Component.literal("Возьмите оружие в руки"),
                    b -> {
                        for (int i = 0; i < 9; i++) {
                            if (player.getInventory().getItem(i).getItem() instanceof IGun) {
               ё                 player.getInventory().selected = i;
                                break;
                            }
                        }
                    }
            ));
        } else {
            if (HIDE_GUN_PROPERTY_DIAGRAMS) {
                this.addRenderableWidget(new FlatColorButton(
                        11, 11, 310, 16,
                        Component.translatable("gui.tacz.gun_refit.property_diagrams.show"),
                        b -> switchHideButton()
                ));
            } else {
                this.addRenderableWidget(new FlatColorButton(
                        14, 14, 12, 12,
                        Component.literal("S"),
                        b -> {
                            if (player.isSpectator()) return;
                            if (IGun.mainHandHoldGun(player)) {
                                IClientPlayerGunOperator.fromLocalPlayer(player).fireSelect();
                                int select = player.getInventory().selected;
                                this.init();
                                player.getInventory().selected = select;
                            }
                        }
                ).setTooltips(Component.translatable("gui.tacz.gun_refit.property_diagrams.fire_mode.switch")));

                int buttonYOffset = GunPropertyDiagrams.getHidePropertyButtonYOffset();
                this.addRenderableWidget(new FlatColorButton(
                        11, buttonYOffset + 5, 330, 12,
                        Component.translatable("gui.tacz.gun_refit.property_diagrams.hide"),
                        b -> switchHideButton()
                ));
            }
        }
        this.addGunsButtons();
        this.addSwitchButtons();
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float pPartialTick) {
        MinecraftForge.EVENT_BUS.post(new GunRefitScreenRenderEvent.Pre(graphics, pPartialTick));
//        renderGunInGui(FirstPersonRenderEvent.tt, graphics, pPartialTick, Minecraft.getInstance().getWindow().getGuiScaledWidth(), Minecraft.getInstance().getWindow().getGuiScaledHeight(), 1);
        super.render(graphics, mouseX, mouseY, pPartialTick);

        if (!HIDE_GUN_PROPERTY_DIAGRAMS) {
            GunPropertyDiagrams.draw(graphics, font, 11, 11);
        }

        this.renderables.stream().filter(w -> w instanceof IComponentTooltip).forEach(w -> ((IComponentTooltip) w)
                .renderTooltip(component -> graphics.renderComponentTooltip(font, component, mouseX, mouseY)));
        this.renderables.stream().filter(w -> w instanceof IStackTooltip).forEach(w -> ((IStackTooltip) w)
                .renderTooltip(stack -> graphics.renderTooltip(font, stack, mouseX, mouseY)));

        MinecraftForge.EVENT_BUS.post(new GunRefitScreenRenderEvent.Post(graphics, pPartialTick));
    }

    public static void renderGunInGui(TextureTarget renderTarget, GuiGraphics graphics, float pticks, float screenWidth, float screenHeight, float scale) {

        LocalPlayer player = Minecraft.getInstance().player;
        ClientLevel level = Minecraft.getInstance().level;

        if (player == null || level == null) return;


        RenderTarget mainRenderTarget = Minecraft.getInstance().getMainRenderTarget();

        VertexSorting cachedVertexSorting = RenderSystem.getVertexSorting();
        Matrix4f cachedProjectionMatrix = new Matrix4f(RenderSystem.getProjectionMatrix());
        Matrix4f cachedModelviewMatrix = new Matrix4f(RenderSystem.getModelViewMatrix());

        RenderSystem.getModelViewMatrix().set(new Matrix4f());
        RenderSystem.setProjectionMatrix(getProjectionMatrix(), VertexSorting.DISTANCE_TO_ORIGIN);


        PoseStack matrices = new PoseStack();
        matrices.translate(0,-0.55,-0.33);
        matrices.mulPose(Axis.YP.rotationDegrees(180));
        matrices.scale(0.1f * scale,0.1f * scale,0.1f * scale);


        renderTarget.bindWrite(false);
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.enableBlend();


        BufferBuilder builder = Tesselator.getInstance().getBuilder();

        RenderSystem.setShader(GameRenderer::getPositionColorTexLightmapShader);
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP);

        Lighting.setupLevel(new Matrix4f());

        ItemStack stack = player.getMainHandItem();
        int blockLight = level.getBrightness(LightLayer.BLOCK, player.blockPosition());
        int skyLight = level.getBrightness(LightLayer.SKY, player.blockPosition());
        if (IClientItemExtensions.of(stack.getItem()).getCustomRenderer() instanceof AnimateGeoItemRenderer<?, ?> renderer) {
            renderer.renderFirstPerson(player, stack, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, graphics.pose(), graphics.bufferSource(), LightTexture.pack(blockLight, skyLight), pticks);
            graphics.bufferSource().endLastBatch();
        }

        RenderSystem.setProjectionMatrix(cachedProjectionMatrix, cachedVertexSorting);
        RenderSystem.getModelViewMatrix().set(cachedModelviewMatrix);


        //eto pizdec
        Lighting.setupFor3DItems();
        mainRenderTarget.bindWrite(false);
        RenderSystem.setShaderTexture(0, renderTarget.getColorTextureId());
        matrices = graphics.pose();
        matrices.pushPose();
        RenderSystem.disableCull();
        matrices.translate(0,screenHeight,0);
        matrices.scale(1,-1,1);

        blitWithPoseStack(matrices, 0, 0, 0, 0, screenWidth, screenHeight, screenWidth, screenHeight, 1);
        matrices.popPose();

        renderTarget.clear(Minecraft.ON_OSX);
        RenderSystem.enableCull();
    }

    public static void blitWithPoseStack(PoseStack matrices, float x, float y, float texPosX, float texPosY, float width, float height, float texWidth, float texHeight, float alpha)
    {
        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorTexShader);
        BufferBuilder vertex = Tesselator.getInstance().getBuilder();
        float u1 = texPosX / texWidth;
        float u2 = (texPosX + width) / texWidth;
        float v1 = texPosY / texHeight;
        float v2 = (texPosY + height) / texHeight;
        Matrix4f m = matrices.last().pose();
        if(vertex.building()) vertex.endOrDiscardIfEmpty();
        vertex.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);
        vertex.vertex(m, x, y, 0).color(1, 1, 1, alpha).uv(u1, v1).endVertex();
        vertex.vertex(m, x, y + height, 0).color(1, 1, 1, alpha).uv(u1, v2).endVertex();
        vertex.vertex(m, x + width, y + height, 0).color(1, 1, 1, alpha).uv(u2, v2).endVertex();
        vertex.vertex(m, x + width, y, 0).color(1, 1, 1, alpha).uv(u2, v1).endVertex();
        BufferUploader.drawWithShader(vertex.end());
        RenderSystem.disableBlend();
    }

    public static Matrix4f getProjectionMatrix(){
        Matrix4f projection = new Matrix4f().perspective(
                (float)(70 * (float) (Math.PI / 180.0)),
                (float)Minecraft.getInstance().getWindow().getWidth() / (float)Minecraft.getInstance().getWindow().getHeight(),
                0.001F,
                ForgeHooksClient.getGuiFarPlane()
        );
        return projection;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void addInventoryAttachmentButtons() {
        LocalPlayer player = getMinecraft().player;
        if (RefitTransform.getCurrentTransformType() == AttachmentType.NONE || player == null) {
            return;
        }
        int startX = this.width - 30;
        int startY = 50;
        int pageStart = currentPage * INVENTORY_ATTACHMENT_SLOT_COUNT;
        int count = 0;
        int currentY = startY;
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack inventoryItem = inventory.getItem(i);
            IAttachment attachment = IAttachment.getIAttachmentOrNull(inventoryItem);
            IGun iGun = IGun.getIGunOrNull(player.getMainHandItem());
            if (attachment != null && iGun != null /*&& attachment.getType(inventoryItem) == RefitTransform.getCurrentTransformType()*/) {
                if (!iGun.allowAttachment(player.getMainHandItem(), inventoryItem)) {
                    continue;
                }
                count++;
                if (count <= pageStart) {
                    continue;
                }
                if (count > pageStart + INVENTORY_ATTACHMENT_SLOT_COUNT) {
                    continue;
                }
                InventoryAttachmentSlot button = new InventoryAttachmentSlot(startX, currentY, i, inventory, b -> {
                    int slotIndex = ((InventoryAttachmentSlot) b).getSlotIndex();
                    SoundPlayManager.playerRefitSound(inventory.getItem(slotIndex), player, SoundManager.INSTALL_SOUND);
                    ClientMessageRefitGun message = new ClientMessageRefitGun(slotIndex, inventory.selected, RefitTransform.getCurrentTransformType());
                    NetworkHandler.CHANNEL.sendToServer(message);
                });
                this.addRenderableWidget(button);
                currentY = currentY + SLOT_SIZE;
            }
        }
        int totalPage = (count - 1) / INVENTORY_ATTACHMENT_SLOT_COUNT;
        RefitTurnPageButton turnPageButtonUp = new RefitTurnPageButton(startX, startY - 10, true, b -> {
            if (currentPage > 0) {
                currentPage--;
                init();
            }
        });
        RefitTurnPageButton turnPageButtonDown = new RefitTurnPageButton(startX, startY + SLOT_SIZE * INVENTORY_ATTACHMENT_SLOT_COUNT + 2, false, b -> {
            if (currentPage < totalPage) {
                currentPage++;
                init();
            }
        });
        if (currentPage < totalPage) {
            this.addRenderableWidget(turnPageButtonDown);
        }
        if (currentPage > 0) {
            this.addRenderableWidget(turnPageButtonUp);
        }
    }

    private void addGunsButtons() {
        Player player = Minecraft.getInstance().player;
        if (player == null) return;
        ArrayList<Integer> gunInventoryIndeces = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            if (player.getInventory().getItem(i).getItem() instanceof IGun) gunInventoryIndeces.add(i);
            if (!(player.getInventory().getSelected().getItem() instanceof IGun)) player.getInventory().selected = i;
        }

        int slotSize = 26;
        int x = Math.round(this.width / 2f - ((slotSize + 3) * gunInventoryIndeces.size() / 2f));
        int y = this.height - slotSize - 10;
        for (int index : gunInventoryIndeces) {
            GunSelectButton button = new GunSelectButton(x, y, slotSize, slotSize, index, b -> {

                player.getInventory().selected = index;
                RefitTransform.changeRefitScreenView(AttachmentType.NONE);
                this.init();
            });
            this.addRenderableWidget(button);
            x += slotSize + 3;
        }
    }

    private void addSwitchButtons() {
        SwitchPageButton left = createSwitchButton(true);
        if (left != null) this.addRenderableWidget(left);

        SwitchPageButton right = createSwitchButton(false);
        if (right != null) this.addRenderableWidget(right);
    }

    private SwitchPageButton createSwitchButton(boolean left) {
        return null;
    }

    private void addAttachmentTypeButtons() {
        LocalPlayer player = getMinecraft().player;
        if (player == null) {
            return;
        }
        IGun iGun = IGun.getIGunOrNull(player.getMainHandItem());
        if (iGun == null) {
            return;
        }
        int startX = this.width - 30 - SLOT_SIZE * 5;
        int startY = 10;
        Inventory inventory = player.getInventory();
        for (UniversalAttachmentType universalType : UniversalAttachmentType.getVisible()) {
            AttachmentType type = universalType.getMappedType();
            if (type == AttachmentType.NONE) {
                if (RefitTransform.getCurrentTransformType() == AttachmentType.NONE) {
                    TimelessAPI.getGunDisplay(player.getMainHandItem())
                            .map(GunDisplayInstance::getLaserConfig)
                            .ifPresent(laserConfig -> {
                                if (laserConfig.canEdit()) {
                                    // 添加镭射颜色选择器
                                    HSVSliderGroup hsvSliderGroup = new HSVSliderGroup(width-140, height-64, 120, 16, inventory, inventory.selected, AttachmentType.NONE);
                                    this.addRenderableWidget(hsvSliderGroup.getHueSlider());
                                    this.addRenderableWidget(hsvSliderGroup.getSaturationSlider());
                                }});
                }
                continue;
            }
            GunAttachmentSlot button = new GunAttachmentSlot(startX, startY, type, inventory.selected, inventory, b -> {
                AttachmentType buttonType = ((GunAttachmentSlot) b).getType();
                // 如果这个槽位不允许安装配件，则默认退回概览，不选中槽位。
                if (!((GunAttachmentSlot) b).isAllow()) {
                    if (RefitTransform.changeRefitScreenView(AttachmentType.NONE)) {
                        int select = player.getInventory().selected;
                        this.init();
                        player.getInventory().selected = select;
                    }
                    return;
                }
                // 点击的是当前选中的槽位，则退回概览
                if (RefitTransform.getCurrentTransformType() == buttonType && buttonType != AttachmentType.NONE) {
                    if (RefitTransform.changeRefitScreenView(AttachmentType.NONE)) {
                        int select = player.getInventory().selected;
                        this.init();
                        player.getInventory().selected = select;
                    }
                    return;
                }
                // 切换选中的槽位。
                if (RefitTransform.changeRefitScreenView(buttonType)) {
                    int select = player.getInventory().selected;
                    this.init();
                    player.getInventory().selected = select;
                }
            });
            if (RefitTransform.getCurrentTransformType() == type) {
                button.setSelected(true);
                // 添加拆卸配件按钮
                RefitUnloadButton unloadButton = new RefitUnloadButton(startX + 5, startY + SLOT_SIZE + 2, b -> {
                    ItemStack attachmentItem = button.getAttachmentItem();
                    if (!attachmentItem.isEmpty()) {
                        int freeSlot = inventory.getFreeSlot();
                        if (freeSlot != -1) {
                            SoundPlayManager.playerRefitSound(attachmentItem, player, SoundManager.UNINSTALL_SOUND);
                            ClientMessageUnloadAttachment message = new ClientMessageUnloadAttachment(inventory.selected, RefitTransform.getCurrentTransformType());
                            NetworkHandler.CHANNEL.sendToServer(message);
                        } else {
//                            player.sendSystemMessage(Component.translatable("gui.tacz.gun_refit.unload.no_space"));
                        }
                    }
                });
                if (!button.getAttachmentItem().isEmpty()) {
                    this.addRenderableWidget(unloadButton);

                    if (button.getAttachmentItem().getItem() instanceof IAttachment iAttachment) {
                        TimelessAPI.getClientAttachmentIndex(iAttachment.getAttachmentId(button.getAttachmentItem()))
                                .map(ClientAttachmentIndex::getLaserConfig)
                                .ifPresent(laserConfig -> {
                                    if (laserConfig.canEdit()) {
                                        // 添加镭射颜色选择器
                                        HSVSliderGroup hsvSliderGroup = new HSVSliderGroup(width-140, height-64, 120, 16, inventory, inventory.selected, type);
                                        this.addRenderableWidget(hsvSliderGroup.getHueSlider());
                                        this.addRenderableWidget(hsvSliderGroup.getSaturationSlider());
                                    }});
                    }
                }
            }
            this.addRenderableWidget(button);
            startX = startX + SLOT_SIZE;
        }
    }

    @Override
    public void onClose() {
        // 关闭界面时，一次性上传所有的染色数据
        LocalPlayer player = getMinecraft().player;
        if (player != null) {
            ItemStack gun = player.getMainHandItem();
            if (player.getMainHandItem().getItem() instanceof IGun) {
                ClientMessageLaserColor message = new ClientMessageLaserColor(gun, player.getInventory().selected);
                NetworkHandler.CHANNEL.sendToServer(message);
            }
        }
        super.onClose();
    }

    private void switchHideButton() {
        Player player = Minecraft.getInstance().player;
        HIDE_GUN_PROPERTY_DIAGRAMS = !HIDE_GUN_PROPERTY_DIAGRAMS;
        if (player == null) return;
        int select = player.getInventory().selected;
        this.init();
        player.getInventory().selected = select;
    }
}
