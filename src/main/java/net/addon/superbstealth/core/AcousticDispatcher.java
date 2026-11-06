package net.addon.superbstealth.core;

import net.addon.superbstealth.SuperbStealthMod;
import net.addon.superbstealth.config.SuperbStealthConfig;
import net.addon.superbstealth.core.SoundOcclusionHelper.SoundResult;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

// Dispatches gunfire acoustic propagation simulation and coordinates sound alerts to listeners.

public final class AcousticDispatcher {

    private AcousticDispatcher() {
    }


    public static void dispatchGunfire(
            ServerLevel level,
            @Nullable Entity shooter,
            Vec3 shootPos,
            float soundRadiusBlocks,
            boolean isSuppressed
    ) {
        if (level == null || level.isClientSide() || soundRadiusBlocks <= 0.0F) {
            return;
        }

        AABB scanBox = new AABB(shootPos, shootPos).inflate(soundRadiusBlocks);
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

        ServerPlayer debugPlayer = resolveDebugPlayer(shooter);
        if (debugPlayer != null) {
            DebugParticleManager.sendShotHeader(debugPlayer, soundRadiusBlocks, isSuppressed);
        }

        for (Mob mob : nearbyMobs) {
            Vec3 mobEyePos = mob.getEyePosition();
            SoundResult result = SoundOcclusionHelper.checkSoundPropagation(level, shootPos, mobEyePos, soundRadiusBlocks, cachedShooterExit);

            if (debugPlayer != null) {
                DebugParticleManager.renderDebugVisualization(level, debugPlayer, shootPos, mob, mobEyePos, result);
            }

            if (StealthAlertHandler.alertMob(shooter, mob, shootPos, result, isSuppressed, soundRadiusBlocks, gameTime)) {
                notifiedCount++;
            } else if (!result.reached()) {
                blockedCount++;
            }

            if (result.reached() && result.hops() > 0) {
                detouredCount++;
            }
        }

        // Entity queries cannot detect sculk sensors (BlockEntities); dispatch vibrations independently.

        SculkVibrationHandler.triggerSculkSensors(level, shootPos, soundRadiusBlocks, isSuppressed, shooter, cachedShooterExit);

        SuperbStealthMod.LOGGER.debug(
                "Gunfire at [{}, {}, {}] (Shooter: {}, Suppressed: {}, Radius: {}m). Notified: {}, Blocked: {}, Detoured: {}",
                String.format("%.1f", shootPos.x),
                String.format("%.1f", shootPos.y),
                String.format("%.1f", shootPos.z),
                shooter != null ? shooter.getName().getString() : "Unknown",
                isSuppressed,
                String.format("%.1f", soundRadiusBlocks),
                notifiedCount,
                blockedCount,
                detouredCount
        );
    }

    // Resolves a {@link ServerPlayer} who has enabled debug visualization, checking the shooter entity
    // directly as well as any vehicle passengers (e.g. player driving a tank or flying an attack helicopter).

    @Nullable
    private static ServerPlayer resolveDebugPlayer(@Nullable Entity shooter) {
        if (shooter instanceof ServerPlayer sp && SuperbStealthMod.DEBUG_PLAYERS.contains(sp.getUUID())) {
            return sp;
        }
        if (shooter != null) {
            for (Entity passenger : shooter.getPassengers()) {
                if (passenger instanceof ServerPlayer sp && SuperbStealthMod.DEBUG_PLAYERS.contains(sp.getUUID())) {
                    return sp;
                }
            }
        }
        return null;
    }
}
