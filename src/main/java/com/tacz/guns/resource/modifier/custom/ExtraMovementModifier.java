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

        double minPercent = -2.0, maxPercent = 2.0;

        double defaultPercent = ((0 - minPercent) / (maxPercent - minPercent));

        double baseModifierPercent = ((baseMultiplier - minPercent) / (maxPercent - minPercent));
        double aimModifierPercent = ((aimMultiplier - minPercent) / (maxPercent - minPercent));
        double reloadModifierPercent = ((reloadMultiplier - minPercent) / (maxPercent - minPercent));

        String baseTitleKey = "gui.tacz.gun_refit.property_diagrams.movement_speed";
        String aimTitleKey = "gui.tacz.gun_refit.property_diagrams.aim_speed";
        String reloadTitleKey = "gui.tacz.gun_refit.property_diagrams.reload_speed";

        String basePositivelyString = String.format("%+.1f%%", baseMultiplier * 100);
        String baseNegativelyString = String.format("%+.1f%%", baseMultiplier * 100);

        String aimPositivelyString = String.format("%+.1f%%", aimMultiplier * 100);
        String aimNegativelyString = String.format("%+.1f%%", aimMultiplier * 100);

        String reloadPositivelyString = String.format("%+.1f%%", reloadMultiplier * 100);
        String reloadNegativelyString = String.format("%+.1f%%", reloadMultiplier * 100);

        String defaultString = "0.0%";

        return List.of(
                new DiagramsData(defaultPercent, baseModifierPercent, baseMultiplier, baseTitleKey, basePositivelyString, baseNegativelyString, defaultString, true),
                new DiagramsData(defaultPercent, aimModifierPercent, aimMultiplier, aimTitleKey, aimPositivelyString, aimNegativelyString, defaultString, true),
                new DiagramsData(defaultPercent, reloadModifierPercent, reloadMultiplier, reloadTitleKey, reloadPositivelyString, reloadNegativelyString, defaultString, true)
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
