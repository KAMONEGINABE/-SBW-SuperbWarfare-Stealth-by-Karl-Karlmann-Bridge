package net.addon.superbstealth.mixin;

import com.atsuishio.superbwarfare.data.PMC;
import com.atsuishio.superbwarfare.data.gun.DefaultGunData;
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

    /**
     * Modifies gun properties when a suppressor is attached, applying configurable multipliers
     * for spread, damage, velocity, fire rate, recoil, and weight.
     */
    @Inject(method = "modifyProperty", at = @At("RETURN"), remap = false)
    private void superbstealth$modifySuppressorProperties(PMC<GunData, DefaultGunData> modifier, CallbackInfo ci) {
        if (modifier == null) {
            return;
        }
        Object rawData = modifier.getData();
        if (!(rawData instanceof GunData data)) {
            return;
        }

        if (data.attachment.get(AttachmentType.BARREL) == 2) {
            SuperbStealthConfig cfg = SuperbStealthConfig.CONFIG;

            double spreadMul = cfg.suppressorSpreadMultiplier.get();
            if (spreadMul != 1.0) {
                Double cur = modifier.get(GunProp.SPREAD);
                if (cur != null) {
                    modifier.set(GunProp.SPREAD, cur * spreadMul);
                }
            }

            double zoomSpreadMul = cfg.suppressorZoomSpreadRateMultiplier.get();
            if (zoomSpreadMul != 1.0) {
                Double cur = modifier.get(GunProp.ZOOM_SPREAD_RATE);
                if (cur != null) {
                    modifier.set(GunProp.ZOOM_SPREAD_RATE, cur * zoomSpreadMul);
                }
            }

            double dmgMul = cfg.suppressorDamageMultiplier.get();
            if (dmgMul != 1.0) {
                Double cur = modifier.get(GunProp.DAMAGE);
                if (cur != null) {
                    modifier.set(GunProp.DAMAGE, cur * dmgMul);
                }
            }

            double velMul = cfg.suppressorVelocityMultiplier.get();
            if (velMul != 1.0) {
                Double cur = modifier.get(GunProp.VELOCITY);
                if (cur != null) {
                    modifier.set(GunProp.VELOCITY, cur * velMul);
                }
            }

            double rpmMul = cfg.suppressorRpmMultiplier.get();
            if (rpmMul != 1.0) {
                Integer cur = modifier.get(GunProp.RPM);
                if (cur != null) {
                    modifier.set(GunProp.RPM, (int) Math.round(cur * rpmMul));
                }
            }

            double recoilMul = cfg.suppressorRecoilMultiplier.get();
            if (recoilMul != 1.0) {
                Double curRecoil = modifier.get(GunProp.RECOIL);
                if (curRecoil != null) {
                    modifier.set(GunProp.RECOIL, curRecoil * recoilMul);
                }
                Double curX = modifier.get(GunProp.RECOIL_X);
                if (curX != null) {
                    modifier.set(GunProp.RECOIL_X, curX * recoilMul);
                }
                Double curY = modifier.get(GunProp.RECOIL_Y);
                if (curY != null) {
                    modifier.set(GunProp.RECOIL_Y, curY * recoilMul);
                }
            }

            double addWeight = cfg.suppressorAdditionalWeight.get();
            if (addWeight != 0.0) {
                Double cur = modifier.get(GunProp.WEIGHT);
                if (cur != null) {
                    modifier.set(GunProp.WEIGHT, cur + addWeight);
                }
            }
        }
    }
}
