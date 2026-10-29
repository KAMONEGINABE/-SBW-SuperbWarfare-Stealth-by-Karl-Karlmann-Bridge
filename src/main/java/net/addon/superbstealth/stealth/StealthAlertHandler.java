package net.addon.superbstealth.stealth;

import net.addon.superbstealth.config.SuperbStealthConfig;
import net.addon.superbstealth.util.SoundOcclusionHelper.SoundResult;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.stealth.capability.IStealthState;
import net.stealth.capability.StealthStateProvider;

/**
 * Stealth by Karl Karlmann Mod の Capability (IStealthState) との連携処理を一元管理するハンドラ。
 * 音波の残存強度に基づいた警戒度・音源優先度の計算および安全な注入を担当する。
 */
public class StealthAlertHandler {

    /**
     * 音波伝播結果に基づいて Mob の Stealth Capability を更新する。
     *
     * @param mob                  対象の Mob
     * @param shootPos             発砲地点のワールド座標
     * @param result               音波伝播計算結果
     * @param isSuppressed         サプレッサー装着状態
     * @param effectiveSoundRadius 有効音波半径
     * @param gameTime             現在のワールドゲーム時間
     * @return 実際に警戒度が更新・通知された場合は true
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

        // 残存音波強度比率 (0.0F 〜 1.0F)
        float strengthRatio = Math.max(0.0F, Math.min(1.0F, result.remainingStrength() / effectiveSoundRadius));

        // 残存強度に応じた音源優先度 (Min/Max 補間)
        float minPriority = SuperbStealthConfig.CONFIG.soundPriorityMin.get().floatValue();
        float maxPriority = SuperbStealthConfig.CONFIG.soundPriorityMax.get().floatValue();
        float priority = minPriority + (strengthRatio * (maxPriority - minPriority));
        state.setSuspiciousLocation(shootPos, priority, gameTime);

        if (isSuppressed) {
            // サプレッサー射撃: 残存強度に応じた警戒度ゲインを加算 (Min/Max 補間, 上限 suppressedMaxAlert)
            float minGain = SuperbStealthConfig.CONFIG.suppressedMinAlertGain.get().floatValue();
            float maxGain = SuperbStealthConfig.CONFIG.suppressedMaxAlertGain.get().floatValue();
            float alertGain = minGain + (strengthRatio * (maxGain - minGain));

            float maxAlert = SuperbStealthConfig.CONFIG.suppressedMaxAlert.get().floatValue();
            float newAlert = Math.min(maxAlert, state.getAlertLevel() + alertGain);
            state.setAlertLevel(newAlert);
        } else {
            // 通常射撃: 爆音のため一気に設定警戒度 (デフォルト 0.80F) へ引き上げ
            float unsuppressedAlert = SuperbStealthConfig.CONFIG.unsuppressedAlertLevel.get().floatValue();
            float newAlert = Math.max(state.getAlertLevel(), unsuppressedAlert);
            state.setAlertLevel(newAlert);
        }

        return true;
    }
}
