package net.addon.superbstealth.event;

import com.atsuishio.superbwarfare.api.event.ShootEvent;
import com.atsuishio.superbwarfare.data.gun.GunData;
import com.atsuishio.superbwarfare.data.gun.GunProp;
import com.atsuishio.superbwarfare.data.gun.value.AttachmentType;
import net.addon.superbstealth.SuperbStealthMod;
import net.addon.superbstealth.config.SuperbStealthConfig;
import net.addon.superbstealth.sculk.SculkVibrationHandler;
import net.addon.superbstealth.stealth.StealthAlertHandler;
import net.addon.superbstealth.util.DebugParticleManager;
import net.addon.superbstealth.util.SoundOcclusionHelper;
import net.addon.superbstealth.util.SoundOcclusionHelper.SoundResult;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.VanillaGameEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;

/**
 * Handles gun firing events, calculating sound occlusion and alerting nearby mobs.
 */
public class GunShootEventListener {

    @SubscribeEvent
    public void onGunShootPost(ShootEvent.Post event) {
        ServerLevel level = event.getLevel();
        if (level == null || level.isClientSide()) {
            return;
        }

        Entity shooter = event.getShooter();
        GunData data = event.getData();
        if (data == null) {
            return;
        }

        Vec3 shootPos = event.getParameters().shootPosition;
        if (shootPos == null) {
            shootPos = shooter != null ? shooter.position() : Vec3.ZERO;
        }

        boolean isSuppressed = data.attachment.get(AttachmentType.BARREL) == 2;

        // Post directly to Forge event bus to trigger Stealth Mod HUD noise meter without alerting vanilla 16m radius
        MinecraftForge.EVENT_BUS.post(new VanillaGameEvent(level, GameEvent.PROJECTILE_SHOOT, shootPos, GameEvent.Context.of(shooter)));

        // SuperbWarfare SOUND_RADIUS is volume, not distance in meters.
        // Audible distance in Minecraft physics is volume * 0.4 * 16m, allowing detection dozens of meters away.
        Double configuredRadius = data.get(GunProp.SOUND_RADIUS);
        double fallbackVolume = SuperbStealthConfig.CONFIG.fallbackGunVolume.get();
        double baseVolume = (configuredRadius != null && configuredRadius > 0.0) ? configuredRadius : fallbackVolume;
        float baseSoundRadius = (float) (baseVolume * 0.4 * 16.0);

        float effectiveSoundRadius;
        if (isSuppressed) {
            float multiplier = SuperbStealthConfig.CONFIG.suppressorRadiusMultiplier.get().floatValue();
            float minRadius = SuperbStealthConfig.CONFIG.suppressorMinRadius.get().floatValue();
            effectiveSoundRadius = Math.max(minRadius, baseSoundRadius * multiplier);
        } else {
            effectiveSoundRadius = baseSoundRadius;
        }

        AABB scanBox = new AABB(shootPos, shootPos).inflate(effectiveSoundRadius);
        List<Mob> nearbyMobs = level.getEntitiesOfClass(Mob.class, scanBox, mob -> {
            if (mob == null || !mob.isAlive()) {
                return false;
            }
            if (shooter != null && mob.isAlliedTo(shooter)) {
                return false;
            }
            return true;
        });

        int notifiedCount = 0;
        int blockedCount = 0;
        int detouredCount = 0;
        long gameTime = level.getGameTime();

        int scanRadius = SuperbStealthConfig.CONFIG.shooterExitScanRadius.get();
        BlockPos cachedShooterExit = SoundOcclusionHelper.findShooterRoomExit(level, BlockPos.containing(shootPos), scanRadius);

        ServerPlayer debugPlayer = (shooter instanceof ServerPlayer sp && SuperbStealthMod.DEBUG_PLAYERS.contains(sp.getUUID())) ? sp : null;
        if (debugPlayer != null) {
            DebugParticleManager.sendShotHeader(debugPlayer, effectiveSoundRadius, isSuppressed);
        }

        for (Mob mob : nearbyMobs) {
            Vec3 mobEyePos = mob.getEyePosition();
            SoundResult result = SoundOcclusionHelper.checkSoundPropagation(level, shootPos, mobEyePos, effectiveSoundRadius, cachedShooterExit);

            if (debugPlayer != null) {
                DebugParticleManager.renderDebugVisualization(level, debugPlayer, shootPos, mob, mobEyePos, result);
            }

            if (StealthAlertHandler.alertMob(shooter, mob, shootPos, result, isSuppressed, effectiveSoundRadius, gameTime)) {
                notifiedCount++;
            } else if (!result.reached()) {
                blockedCount++;
            }

            if (result.reached() && result.hops() > 0) {
                detouredCount++;
            }
        }

        SculkVibrationHandler.triggerSculkSensors(level, shootPos, effectiveSoundRadius, isSuppressed, shooter, cachedShooterExit);

        SuperbStealthMod.LOGGER.debug(
                "Gunshot at [{}, {}, {}] (Suppressed: {}, Radius: {}m). Notified: {}, Blocked: {}, Detoured: {}",
                String.format("%.1f", shootPos.x),
                String.format("%.1f", shootPos.y),
                String.format("%.1f", shootPos.z),
                isSuppressed,
                String.format("%.1f", effectiveSoundRadius),
                notifiedCount,
                blockedCount,
                detouredCount
        );
    }
}
