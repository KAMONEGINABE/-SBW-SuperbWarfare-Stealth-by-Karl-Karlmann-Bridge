package net.addon.superbstealth.stealth;

import net.addon.superbstealth.config.SuperbStealthConfig;
import net.addon.superbstealth.util.SoundOcclusionHelper.SoundResult;
import net.minecraft.world.entity.Mob;
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
     */
    public static boolean alertMob(
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

        var optState = mob.getCapability(StealthStateProvider.STEALTH_CAPABILITY);
        if (!optState.isPresent()) {
            return false;
        }

        IStealthState state = optState.orElse(null);
        if (state == null) {
            return false;
        }

        float strengthRatio = Math.max(0.0F, Math.min(1.0F, result.remainingStrength() / effectiveSoundRadius));

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
