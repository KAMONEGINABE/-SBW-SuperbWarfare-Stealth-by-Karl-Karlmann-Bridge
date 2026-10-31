package net.addon.superbstealth.sculk;

import net.addon.superbstealth.config.SuperbStealthConfig;
import net.addon.superbstealth.util.SoundOcclusionHelper;
import net.addon.superbstealth.util.SoundOcclusionHelper.SoundResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SculkSensorBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;
import net.minecraft.world.phys.Vec3;

/**
 * Handles occlusion-based vibration dispatching to nearby sculk sensors.
 */
public class SculkVibrationHandler {

    /**
     * Scans nearby chunks for sculk sensors, traces sound occlusion raycasts,
     * and triggers vibration with configured frequency if sound reaches the sensor.
     */
    public static void triggerSculkSensors(
            ServerLevel level,
            Vec3 shootPos,
            float effectiveSoundRadius,
            boolean isSuppressed,
            Entity shooter,
            BlockPos cachedShooterExit
    ) {
        int minChunkX = SectionPos.blockToSectionCoord((int) (shootPos.x - effectiveSoundRadius));
        int maxChunkX = SectionPos.blockToSectionCoord((int) (shootPos.x + effectiveSoundRadius));
        int minChunkZ = SectionPos.blockToSectionCoord((int) (shootPos.z - effectiveSoundRadius));
        int maxChunkZ = SectionPos.blockToSectionCoord((int) (shootPos.z + effectiveSoundRadius));

        int frequency = isSuppressed
                ? SuperbStealthConfig.CONFIG.suppressedSculkFrequency.get()
                : SuperbStealthConfig.CONFIG.unsuppressedSculkFrequency.get();

        GameEvent vibrationEvent = VibrationSystem.getResonanceEventByFrequency(frequency);
        if (vibrationEvent == null) {
            vibrationEvent = isSuppressed ? GameEvent.PROJECTILE_SHOOT : GameEvent.EXPLODE;
        }

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                if (!level.hasChunk(cx, cz)) continue;
                LevelChunk chunk = level.getChunk(cx, cz);
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (be instanceof SculkSensorBlockEntity sensor) {
                        BlockPos sensorPos = sensor.getBlockPos();
                        Vec3 sensorCenter = Vec3.atCenterOf(sensorPos);
                        if (shootPos.distanceTo(sensorCenter) > effectiveSoundRadius) continue;

                        SoundResult sensorResult = SoundOcclusionHelper.checkSoundPropagation(level, shootPos, sensorCenter, effectiveSoundRadius, cachedShooterExit);
                        if (sensorResult.reached()) {
                            sensor.getListener().forceScheduleVibration(level, vibrationEvent, GameEvent.Context.of(shooter), shootPos);
                        }
                    }
                }
            }
        }
    }
}
