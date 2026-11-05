package net.addon.superbstealth.event;

import com.atsuishio.superbwarfare.api.event.ShootEvent;
import com.atsuishio.superbwarfare.data.gun.GunData;
import com.atsuishio.superbwarfare.data.gun.GunProp;
import com.atsuishio.superbwarfare.data.gun.value.AttachmentType;
import net.addon.superbstealth.config.SuperbStealthConfig;
import net.addon.superbstealth.core.AcousticDispatcher;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.VanillaGameEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Handles SuperbWarfare gun firing events, translating weapon properties and delegating
 * acoustic occlusion simulation to {@link AcousticDispatcher}.
 */
public class GunShootEventListener {

    @SubscribeEvent
    public void onGunShootPost(ShootEvent.Post event) {
        ServerLevel level = event.getLevel();
        if (level == null || level.isClientSide()) {
            return;
        }

        Entity shooter = event.getShooter();
        GunData gunData = event.getData();
        if (gunData == null) {
            return;
        }

        Vec3 shootPos = event.getParameters().shootPosition;
        if (shootPos == null) {
            shootPos = shooter != null ? shooter.position() : Vec3.ZERO;
        }

        boolean isSuppressed = gunData.attachment.get(AttachmentType.BARREL) == 2;

        // Post directly to Forge event bus to trigger Stealth Mod HUD noise meter without alerting vanilla 16m radius
        MinecraftForge.EVENT_BUS.post(new VanillaGameEvent(level, GameEvent.PROJECTILE_SHOOT, shootPos, GameEvent.Context.of(shooter)));

        // SuperbWarfare SOUND_RADIUS is volume, not distance in meters.
        // Audible distance in Minecraft physics is volume * 0.4 * 16m, allowing detection dozens of meters away.
        Double configuredRadius = gunData.get(GunProp.SOUND_RADIUS);
        double fallbackVolume = SuperbStealthConfig.CONFIG.fallbackGunVolume.get();
        double baseVolume = (configuredRadius != null && configuredRadius > 0.0) ? configuredRadius : fallbackVolume;
        float baseSoundRadiusBlocks = (float) (baseVolume * 0.4 * 16.0);

        float effectiveSoundRadiusBlocks;
        if (isSuppressed) {
            float multiplier = SuperbStealthConfig.CONFIG.suppressorRadiusMultiplier.get().floatValue();
            float minRadius = SuperbStealthConfig.CONFIG.suppressorMinRadius.get().floatValue();
            effectiveSoundRadiusBlocks = Math.max(minRadius, baseSoundRadiusBlocks * multiplier);
        } else {
            effectiveSoundRadiusBlocks = baseSoundRadiusBlocks;
        }

        AcousticDispatcher.dispatchGunfire(level, shooter, shootPos, effectiveSoundRadiusBlocks, isSuppressed);
    }
}
