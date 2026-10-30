package net.addon.superbstealth.mixin;

import com.atsuishio.superbwarfare.data.gun.GunData;
import com.atsuishio.superbwarfare.data.gun.GunProp;
import com.atsuishio.superbwarfare.data.gun.SoundInfo;
import com.atsuishio.superbwarfare.data.gun.value.AttachmentType;
import com.atsuishio.superbwarfare.item.gun.GunItem;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GunItem.class)
public class MixinGunItem {

    /**
     * Mutes long-range reverb sounds when a suppressor is attached, playing only the close fire sound at 1/4 volume.
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
                float soundRadius = (soundRadiusObj != null) ? soundRadiusObj.floatValue() : 14.0F;

                SoundInfo soundInfo = data.get(GunProp.SOUND_INFO);
                if (soundInfo != null) {
                    SoundEvent sound3p = soundInfo.getFire3PSilent();
                    if (sound3p != null) {
                        float suppressedVolume = (soundRadius * 0.4F) / 4.0F;
                        shooter.playSound(sound3p, suppressedVolume, pitch);
                    }
                }
            }
        }
    }
}
