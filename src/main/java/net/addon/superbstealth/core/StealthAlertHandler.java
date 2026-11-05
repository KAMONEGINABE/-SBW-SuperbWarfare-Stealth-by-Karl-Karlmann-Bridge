package net.addon.superbstealth.core;

import net.addon.superbstealth.config.SuperbStealthConfig;
import net.addon.superbstealth.core.SoundOcclusionHelper.SoundResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;
import net.minecraft.world.phys.Vec3;
import net.stealth.capability.IStealthState;
import net.stealth.capability.StealthStateProvider;

/**
 * Manages updating alert level and sound priority in Stealth Mod's capability (IStealthState).
 */
public class StealthAlertHandler {

    /**
     * Updates mob stealth state based on sound propagation results.
     * For suppressed shots, alert gain and priority scale with remaining sound strength.
     * Unsuppressed shots immediately elevate alert to maximum investigation level.
     * If the mob is a Warden, anger and vibration animations are applied via official APIs.
     */
    public static boolean alertMob(
            Entity shooter,
            Mob mob,
            Vec3 shootPos,
            SoundResult result,
            boolean isSuppressed,
            float effectiveSoundRadius,
            long gameTime
    ) {
        if (!result.reached()) {
            return false;
        }

        float strengthRatio = Math.max(0.0F, Math.min(1.0F, result.remainingStrength() / effectiveSoundRadius));

        // Warden reaction: apply anger and schedule vibration animation through vanilla APIs
        if (mob instanceof Warden warden && mob.level() instanceof ServerLevel serverLevel) {
            int angerAmount = isSuppressed ? Math.max(2, (int) (8 * strengthRatio)) : 10;
            if (shooter != null) {
                warden.increaseAngerAt(shooter, angerAmount, true);
            }
            GameEvent vibrationEvent = isSuppressed ? GameEvent.PROJECTILE_SHOOT : GameEvent.EXPLODE;
            var listener = new VibrationSystem.Listener(warden);
            listener.forceScheduleVibration(serverLevel, vibrationEvent, GameEvent.Context.of(shooter), shootPos);
        }

        var optState = mob.getCapability(StealthStateProvider.STEALTH_CAPABILITY);
        if (!optState.isPresent()) {
            return false;
        }

        IStealthState state = optState.orElse(null);
        if (state == null) {
            return false;
        }

        float minPriority = SuperbStealthConfig.CONFIG.soundPriorityMin.get().floatValue();
        float maxPriority = SuperbStealthConfig.CONFIG.soundPriorityMax.get().floatValue();
        float priority = minPriority + (strengthRatio * (maxPriority - minPriority));
        state.setSuspiciousLocation(shootPos, priority, gameTime);

        if (isSuppressed) {
            float minGain = SuperbStealthConfig.CONFIG.suppressedMinAlertGain.get().floatValue();
            float maxGain = SuperbStealthConfig.CONFIG.suppressedMaxAlertGain.get().floatValue();
            float alertGain = minGain + (strengthRatio * (maxGain - minGain));

            float maxAlert = SuperbStealthConfig.CONFIG.suppressedMaxAlert.get().floatValue();
            float newAlert = Math.min(maxAlert, state.getAlertLevel() + alertGain);
            state.setAlertLevel(newAlert);
        } else {
            float unsuppressedAlert = SuperbStealthConfig.CONFIG.unsuppressedAlertLevel.get().floatValue();
            float newAlert = Math.max(state.getAlertLevel(), unsuppressedAlert);
            state.setAlertLevel(newAlert);
        }

        return true;
    }
}
