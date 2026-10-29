package net.addon.superbstealth.stealth;

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

        // 残存強度に応じた音源優先度 (5.0F 〜 15.0F)
        float priority = 5.0F + (strengthRatio * 10.0F);
        state.setSuspiciousLocation(shootPos, priority, gameTime);

        if (isSuppressed) {
            // サプレッサー射撃: 残存強度に応じて警戒度を加算 (+0.20F 注視 〜 +0.50F 調査開始, 最大 0.85F)
            float alertGain = 0.20F + (strengthRatio * 0.30F);
            float newAlert = Math.min(0.85F, state.getAlertLevel() + alertGain);
            state.setAlertLevel(newAlert);
        } else {
            // 通常射撃: 爆音のため一気に 0.80F へ引き上げ (即座に INVESTIGATE_THRESHOLD 0.45F を突破し調査開始)
            float newAlert = Math.max(state.getAlertLevel(), 0.80F);
            state.setAlertLevel(newAlert);
        }

        return true;
    }
}
