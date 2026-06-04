package com.tacz.guns.resource.modifier.custom;

import com.google.gson.annotations.SerializedName;
import com.tacz.guns.api.GunProperties;
import com.tacz.guns.api.modifier.CacheValue;
import com.tacz.guns.api.modifier.IAttachmentModifier;
import com.tacz.guns.api.modifier.JsonProperty;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.modifier.AttachmentCacheProperty;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.resource.pojo.data.gun.MoveSpeed;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 这个字段使用modifier还是太奇怪了，姑且只用于缓存
 */
public class ExtraMovementModifier implements IAttachmentModifier<MoveSpeed, MoveSpeed> {
    public static final String ID = GunProperties.MOVE_SPEED.name();

    @Override
    public String getId() {
        return ID;
    }

    @Override
    @SuppressWarnings("deprecation")
    public JsonProperty<MoveSpeed> readJson(String json) {
        ExtraMovementModifier.Data data = CommonAssetsManager.GSON.fromJson(json, ExtraMovementModifier.Data.class);
        MoveSpeed moveSpeed = data.getMoveSpeed();
        return  new ExtraSpeedJsonProperty(moveSpeed);
    }

    @Override
    public List<DiagramsData> getPropertyDiagramsData(ItemStack gunItem, GunData gunData, AttachmentCacheProperty cacheProperty) {
        MoveSpeed cache = cacheProperty.getCache(ID);

        float baseMultiplier = cache.getBaseMultiplier();
        float aimMultiplier = cache.getAimMultiplier();
        float reloadMultiplier = cache.getReloadMultiplier();

        double minPercent = -1.0, maxPercent = 2.0;

        double defaultBasePercent = ((gunData.getMoveSpeed().getBaseMultiplier() - minPercent) / (maxPercent - minPercent));
        double defaultAimPercent = ((gunData.getMoveSpeed().getAimMultiplier() - minPercent) / (maxPercent - minPercent));
        double defaultReloadPercent = ((gunData.getMoveSpeed().getReloadMultiplier() - minPercent) / (maxPercent - minPercent));

        double baseModifierPercent = (baseMultiplier - minPercent) / (maxPercent - minPercent) - defaultBasePercent;
        double aimModifierPercent = (aimMultiplier - minPercent) / (maxPercent - minPercent) - defaultAimPercent;
        double reloadModifierPercent = (reloadMultiplier - minPercent) / (maxPercent - minPercent) - defaultReloadPercent;

        String baseTitleKey = "gui.tacz.gun_refit.property_diagrams.movement_speed";
        String aimTitleKey = "gui.tacz.gun_refit.property_diagrams.aim_speed";
        String reloadTitleKey = "gui.tacz.gun_refit.property_diagrams.reload_speed";

        double baseModifier = baseMultiplier - gunData.getMoveSpeed().getBaseMultiplier();
        String basePositivelyString = String.format("%.1f%% §a(+%.1f%%)", baseMultiplier * 100 + 100, baseModifier * 100);
        String baseNegativelyString = String.format("%.1f%% §c(+%.1f%%)", baseMultiplier * 100 + 100, baseModifier * 100);

        double aimModifier = aimMultiplier - gunData.getMoveSpeed().getAimMultiplier();
        String aimPositivelyString = String.format("%.1f%% §a(+%.1f%%)", aimMultiplier * 100 + 100, aimModifier * 100);
        String aimNegativelyString = String.format("%.1f%% §c(+%.1f%%)", aimMultiplier * 100 + 100, aimModifier * 100);

        double reloadModifier = reloadMultiplier - gunData.getMoveSpeed().getReloadMultiplier();
        String reloadPositivelyString = String.format("%.1f%% §a(+%.1f%%)", reloadMultiplier * 100 + 100, reloadModifier * 100);
        String reloadNegativelyString = String.format("%.1f%% §c(+%.1f%%)", reloadMultiplier * 100 + 100, reloadModifier * 100);

        String defaultBaseString = String.format("%.1f%%", baseMultiplier * 100 + 100);
        String defaultAimString = String.format("%.1f%%", aimMultiplier * 100 + 100);
        String defaultReloadString = String.format("%.1f%%", reloadMultiplier * 100 + 100);

        return List.of(
                new DiagramsData(defaultBasePercent, baseModifierPercent, baseModifier, baseTitleKey, basePositivelyString, baseNegativelyString, defaultBaseString, true),
                new DiagramsData(defaultAimPercent, aimModifierPercent, aimModifier, aimTitleKey, aimPositivelyString, aimNegativelyString, defaultAimString, true),
                new DiagramsData(defaultReloadPercent, reloadModifierPercent, reloadModifier, reloadTitleKey, reloadPositivelyString, reloadNegativelyString, defaultReloadString, true)
        );
    }

    @Override
    public int getDiagramsDataSize() {
        return 3;
    }

    @Override
    public CacheValue<MoveSpeed> initCache(ItemStack gunItem, GunData gunData) {
        return new CacheValue<>(gunData.getMoveSpeed());
    }

    @Override
    public void eval(List<MoveSpeed> modifiers, CacheValue<MoveSpeed> cache) {
        cache.setValue(MoveSpeed.of(cache.getValue(), modifiers));
    }

    public static class ExtraSpeedJsonProperty extends JsonProperty<MoveSpeed> {
        public ExtraSpeedJsonProperty(MoveSpeed value) {
            super(value);
        }

        @Override
        public void initComponents() {
            MoveSpeed speed = getValue();
            if(speed == null)return;
            resolveComponent(speed.getBaseMultiplier(), "movement_speed");
            resolveComponent(speed.getAimMultiplier(), "aim_speed");
            resolveComponent(speed.getReloadMultiplier(), "reload_speed");
        }

        private void resolveComponent(float amount, String key) {
            if (amount > 0) {
                components.add(Component.translatable(String.format("tooltip.tacz.attachment.%s.increase", key)).withStyle(ChatFormatting.GREEN));
            } else if (amount < 0) {
                components.add(Component.translatable(String.format("tooltip.tacz.attachment.%s.decrease", key)).withStyle(ChatFormatting.RED));
            }
        }
    }

    public static class Data {
        @SerializedName("movement_speed")
        @Nullable
        private MoveSpeed moveSpeed = null;

        @Nullable
        public MoveSpeed getMoveSpeed() {
            return moveSpeed;
        }
    }
}
