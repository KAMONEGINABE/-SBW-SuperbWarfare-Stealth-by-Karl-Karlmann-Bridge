package net.addon.superbstealth.mixin;

import com.atsuishio.superbwarfare.data.gun.GunData;
import com.atsuishio.superbwarfare.data.gun.GunProp;
import com.atsuishio.superbwarfare.data.gun.SoundInfo;
import com.atsuishio.superbwarfare.data.gun.value.AttachmentType;
import com.atsuishio.superbwarfare.item.gun.GunItem;
import net.addon.superbstealth.config.SuperbStealthConfig;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GunItem.class)
public class MixinGunItem {

    /**
     * Mutes long-range reverb sounds when a suppressor is attached, playing only the close fire sound
     * at a volume configured to match the gameplay stealth detection radius.
     */
    @Inject(method = "playFireSounds", at = @At("HEAD"), cancellable = true, remap = false)
    private void superbstealth$suppressFireSounds(GunData data, Entity shooter, boolean zoom, CallbackInfo ci) {
        if (shooter != null && data != null) {
            boolean isSilent = data.attachment.get(AttachmentType.BARREL) == 2;
            if (isSilent) {
                ci.cancel();

                double heat = data.heat.get();
                float pitch = (float) (heat <= 75.0 ? 1.0F : (1.0 - 0.02 * Math.abs(75.0 - heat)));

                Double soundRadiusObj = data.get(GunProp.SOUND_RADIUS);
                double fallbackVolume = SuperbStealthConfig.CONFIG.fallbackGunVolume.get();
                double baseVolume = (soundRadiusObj != null && soundRadiusObj > 0.0) ? soundRadiusObj : fallbackVolume;
                float baseSoundRadius = (float) (baseVolume * 0.4 * 16.0);

                float multiplier = SuperbStealthConfig.CONFIG.suppressorRadiusMultiplier.get().floatValue();
                float minRadius = SuperbStealthConfig.CONFIG.suppressorMinRadius.get().floatValue();
                float effectiveSoundRadius = Math.max(minRadius, baseSoundRadius * multiplier);

                SoundInfo soundInfo = data.get(GunProp.SOUND_INFO);
                if (soundInfo != null) {
                    SoundEvent sound3p = soundInfo.getFire3PSilent();
                    if (sound3p != null) {
                        // Minecraft audible distance is volume * 16m.
                        // Dividing effectiveSoundRadius by 16 ensures the sound reach precisely matches mob detection range.
                        float suppressedVolume = effectiveSoundRadius / 16.0F;
                        shooter.playSound(sound3p, suppressedVolume, pitch);
                    }
                }
            }
        }
    }
}
