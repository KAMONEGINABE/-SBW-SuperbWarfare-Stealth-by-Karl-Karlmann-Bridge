package net.addon.superbstealth.event;

import com.atsuishio.superbwarfare.api.event.ShootEvent;
import com.atsuishio.superbwarfare.data.gun.GunData;
import com.atsuishio.superbwarfare.data.gun.GunProp;
import com.atsuishio.superbwarfare.data.gun.value.AttachmentType;
import net.addon.superbstealth.SuperbStealthMod;
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
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;

/**
 * SuperbWarfare の発砲イベント (ShootEvent.Post) を購読し、
 * 音響物理シミュレーション・診断可視化・Stealth Mod への通知パイプラインを統括するリスナ。
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

        // 射撃地点周辺の部屋出口（開いたドア等）をMobループ前に1度だけ事前解決してキャッシュ
        BlockPos cachedShooterExit = SoundOcclusionHelper.findShooterRoomExit(level, BlockPos.containing(shootPos), 4);

        ServerPlayer debugPlayer = (shooter instanceof ServerPlayer sp && SuperbStealthMod.DEBUG_PLAYERS.contains(sp.getUUID())) ? sp : null;
        if (debugPlayer != null) {
            DebugParticleManager.sendShotHeader(debugPlayer, effectiveSoundRadius, isSuppressed);
        }

        // 4. 音波物理計算・診断可視化・ステルス警戒度適用のパイプライン処理
        for (Mob mob : nearbyMobs) {
            Vec3 mobEyePos = mob.getEyePosition();
            // 音波の遮蔽・多重貫通減衰・最大3ホップの連鎖開口部迂回（分枝限定法・事前キャッシュ付き）をシミュレート
            SoundResult result = SoundOcclusionHelper.checkSoundPropagation(level, shootPos, mobEyePos, effectiveSoundRadius, cachedShooterExit);

            // 診断・デバッグ視覚化 (有効時のみ)
            if (debugPlayer != null) {
                DebugParticleManager.renderDebugVisualization(level, debugPlayer, shootPos, mob, mobEyePos, result);
            }

            // Stealth Mod Capability への通知・警戒度更新
            if (StealthAlertHandler.alertMob(mob, shootPos, result, isSuppressed, effectiveSoundRadius, gameTime)) {
                notifiedCount++;
            } else if (!result.reached()) {
                blockedCount++;
            }

            if (result.reached() && result.hops() > 0) {
                detouredCount++;
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
}
