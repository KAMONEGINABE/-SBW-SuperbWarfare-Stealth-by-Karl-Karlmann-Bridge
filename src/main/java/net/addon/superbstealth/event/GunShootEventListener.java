package net.addon.superbstealth.event;

import com.atsuishio.superbwarfare.api.event.ShootEvent;
import com.atsuishio.superbwarfare.data.gun.GunData;
import com.atsuishio.superbwarfare.data.gun.GunProp;
import com.atsuishio.superbwarfare.data.gun.value.AttachmentType;
import net.addon.superbstealth.SuperbStealthMod;
import net.addon.superbstealth.util.DebugParticleManager;
import net.addon.superbstealth.util.SoundOcclusionHelper;
import net.addon.superbstealth.util.SoundOcclusionHelper.SoundResult;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.stealth.capability.IStealthState;
import net.stealth.capability.StealthStateProvider;
import org.joml.Vector3f;

import java.util.List;

public class GunShootEventListener {

    private static final Vector3f COLOR_GREEN = new Vector3f(0.0F, 1.0F, 0.2F);
    private static final Vector3f COLOR_YELLOW = new Vector3f(1.0F, 0.85F, 0.0F);
    private static final Vector3f COLOR_RED = new Vector3f(1.0F, 0.15F, 0.15F);
    private static final int DEBUG_DURATION_TICKS = 100; // 5秒間 (100 ticks) 持続

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

        // サプレッサー装着判定 (BARREL == 2)
        boolean isSuppressed = data.attachment.get(AttachmentType.BARREL) == 2;

        // 1. バニラ GameEvent 発行
        // 【重要】サプレッサー装着時は、バニラ GameEvent の壁無視 16m 貫通による上階 Mob の誤爆知を防ぐため発行を停止。
        // 非サプレッサー時のみ、ウォーデンおよび Stealth HUD 音波メーター連動用として発行する。
        if (!isSuppressed) {
            level.gameEvent(shooter, GameEvent.PROJECTILE_SHOOT, shootPos);
        }

        // 2. 有効音波半径の算出
        Double configuredRadius = data.get(GunProp.SOUND_RADIUS);
        double baseVolume = (configuredRadius != null && configuredRadius > 0.0) ? configuredRadius : 14.0;
        float baseSoundRadius = (float) (baseVolume * 0.4 * 16.0);

        // サプレッサー装着時は音波半径を 1/4 (AK47で約 22.4m, MP5で約 17.6m, 拳銃で約 11.2m) に除算
        float effectiveSoundRadius = isSuppressed ? Math.max(8.0F, baseSoundRadius / 4.0F) : baseSoundRadius;

        // 3. 周辺 Mob の走査
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

        // 最適化: 射撃地点周辺の部屋出口（開いたドア等）をMobループ前に1度だけ事前解決してキャッシュ
        BlockPos cachedShooterExit = SoundOcclusionHelper.findShooterRoomExit(level, BlockPos.containing(shootPos), 4);

        ServerPlayer debugPlayer = (shooter instanceof ServerPlayer sp && SuperbStealthMod.DEBUG_PLAYERS.contains(sp.getUUID())) ? sp : null;
        if (debugPlayer != null) {
            debugPlayer.sendSystemMessage(Component.literal(
                    "§6=== [SuperbStealth Debug] 発砲検知 (半径: " + String.format("%.1f", effectiveSoundRadius) + "m / サプレッサー: " + isSuppressed + ") ==="
            ));
        }

        for (Mob mob : nearbyMobs) {
            Vec3 mobEyePos = mob.getEyePosition();
            // 音波の遮蔽・多重貫通減衰・最大3ホップの連鎖開口部迂回（分枝限定法・事前キャッシュ付き）をシミュレート
            SoundResult result = SoundOcclusionHelper.checkSoundPropagation(level, shootPos, mobEyePos, effectiveSoundRadius, cachedShooterExit);

            // デバッグ視覚化 (5秒間持続する多段パーティクル線 & チャット通知)
            if (debugPlayer != null) {
                renderDebugVisualization(level, debugPlayer, shootPos, mob, mobEyePos, result);
            }

            if (!result.reached()) {
                blockedCount++;
                continue;
            }

            if (result.hops() > 0) {
                detouredCount++;
            }

            var optState = mob.getCapability(StealthStateProvider.STEALTH_CAPABILITY);
            if (optState.isPresent()) {
                IStealthState state = optState.orElse(null);
                if (state != null) {
                    // 残存音波強度に応じた音源優先度 (5.0F 〜 15.0F)
                    float strengthRatio = Math.max(0.0F, Math.min(1.0F, result.remainingStrength() / effectiveSoundRadius));
                    float priority = 5.0F + (strengthRatio * 10.0F);

                    state.setSuspiciousLocation(shootPos, priority, gameTime);

                    if (isSuppressed) {
                        // サプレッサー射撃: 残存強度に応じて警戒度を加算 (+0.20F 注視 〜 +0.50F 調査開始)
                        float alertGain = 0.20F + (strengthRatio * 0.30F);
                        float newAlert = Math.min(0.85F, state.getAlertLevel() + alertGain);
                        state.setAlertLevel(newAlert);
                    } else {
                        // 通常射撃: 爆音のため一気に 0.80F へ引き上げ (即座に INVESTIGATE_THRESHOLD 0.45F を突破し調査開始)
                        float newAlert = Math.max(state.getAlertLevel(), 0.80F);
                        state.setAlertLevel(newAlert);
                    }

                    notifiedCount++;
                }
            }
        }

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

    private void renderDebugVisualization(ServerLevel level, ServerPlayer player, Vec3 shootPos, Mob mob, Vec3 mobEyePos, SoundResult result) {
        if (result.reached()) {
            List<Vec3> waypoints = result.waypoints();
            if (waypoints != null && waypoints.size() >= 2) {
                // ウェイポイント間を順次描画
                for (int i = 0; i < waypoints.size() - 1; i++) {
                    Vec3 from = waypoints.get(i);
                    Vec3 to = waypoints.get(i + 1);
                    boolean isFinalLeg = (i == waypoints.size() - 2);

                    // 途中区間は黄色、最終区間は緑色
                    Vector3f lineColor = isFinalLeg ? COLOR_GREEN : COLOR_YELLOW;
                    DebugParticleManager.spawnLine(level, from, to, lineColor, DEBUG_DURATION_TICKS);

                    // 中間開口部には黄色マーカーを設置
                    if (!isFinalLeg) {
                        DebugParticleManager.spawnMarker(level, to, COLOR_YELLOW, 2.0F, DEBUG_DURATION_TICKS);
                    }
                }
            } else {
                DebugParticleManager.spawnLine(level, shootPos, mobEyePos, COLOR_GREEN, DEBUG_DURATION_TICKS);
            }

            String hopInfo = (result.hops() > 0)
                    ? " §6[" + result.hops() + "回屈折迂回]"
                    : " §2[直進]";

            player.sendSystemMessage(Component.literal(
                    "§7- §e" + mob.getName().getString() + "§7: §a到達 §7(残存: §f" +
                            String.format("%.1f", result.remainingStrength()) + "m§7)" + hopInfo
            ));
        } else {
            // 遮断ルート (赤色)
            Vec3 stopPos = (result.stopPos() != null) ? result.stopPos() : mobEyePos;
            DebugParticleManager.spawnLine(level, shootPos, stopPos, COLOR_RED, DEBUG_DURATION_TICKS);
            DebugParticleManager.spawnMarker(level, stopPos, COLOR_RED, 2.0F, DEBUG_DURATION_TICKS);

            player.sendSystemMessage(Component.literal(
                    "§7- §e" + mob.getName().getString() + "§7: §c遮断 (消音)"
            ));
        }
    }
}
