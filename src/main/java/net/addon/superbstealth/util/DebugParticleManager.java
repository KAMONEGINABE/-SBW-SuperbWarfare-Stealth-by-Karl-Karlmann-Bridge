package net.addon.superbstealth.util;

import net.addon.superbstealth.util.SoundOcclusionHelper.SoundResult;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class DebugParticleManager {

    public static final Vector3f COLOR_GREEN = new Vector3f(0.0F, 1.0F, 0.2F);
    public static final Vector3f COLOR_YELLOW = new Vector3f(1.0F, 0.85F, 0.0F);
    public static final Vector3f COLOR_RED = new Vector3f(1.0F, 0.15F, 0.15F);
    public static final int DEBUG_DURATION_TICKS = 100; // 5秒間 (100 ticks) 持続

    private static class PersistentLine {
        final ServerLevel level;
        final Vec3 from;
        final Vec3 to;
        final Vector3f color;
        int remainingTicks;

        PersistentLine(ServerLevel level, Vec3 from, Vec3 to, Vector3f color, int ticks) {
            this.level = level;
            this.from = from;
            this.to = to;
            this.color = color;
            this.remainingTicks = ticks;
        }
    }

    private static class PersistentMarker {
        final ServerLevel level;
        final Vec3 pos;
        final Vector3f color;
        final float size;
        int remainingTicks;

        PersistentMarker(ServerLevel level, Vec3 pos, Vector3f color, float size, int ticks) {
            this.level = level;
            this.pos = pos;
            this.color = color;
            this.size = size;
            this.remainingTicks = ticks;
        }
    }

    private static final List<PersistentLine> LINES = Collections.synchronizedList(new ArrayList<>());
    private static final List<PersistentMarker> MARKERS = Collections.synchronizedList(new ArrayList<>());

    public static void spawnLine(ServerLevel level, Vec3 from, Vec3 to, Vector3f color, int durationTicks) {
        drawLine(level, from, to, color);
        LINES.add(new PersistentLine(level, from, to, color, durationTicks));
    }

    public static void spawnMarker(ServerLevel level, Vec3 pos, Vector3f color, float size, int durationTicks) {
        drawMarker(level, pos, color, size);
        MARKERS.add(new PersistentMarker(level, pos, color, size, durationTicks));
    }

    public static void tick() {
        synchronized (LINES) {
            Iterator<PersistentLine> it = LINES.iterator();
            while (it.hasNext()) {
                PersistentLine line = it.next();
                line.remainingTicks--;
                if (line.remainingTicks % 8 == 0 && line.remainingTicks > 0) {
                    drawLine(line.level, line.from, line.to, line.color);
                }
                if (line.remainingTicks <= 0) {
                    it.remove();
                }
            }
        }

        synchronized (MARKERS) {
            Iterator<PersistentMarker> it = MARKERS.iterator();
            while (it.hasNext()) {
                PersistentMarker marker = it.next();
                marker.remainingTicks--;
                if (marker.remainingTicks % 8 == 0 && marker.remainingTicks > 0) {
                    drawMarker(marker.level, marker.pos, marker.color, marker.size);
                }
                if (marker.remainingTicks <= 0) {
                    it.remove();
                }
            }
        }
    }

    private static void drawLine(ServerLevel level, Vec3 from, Vec3 to, Vector3f color) {
        double dist = from.distanceTo(to);
        int steps = Math.max(1, (int) (dist * 2.0));
        Vec3 stepVec = to.subtract(from).scale(1.0 / steps);
        Vec3 cur = from;
        DustParticleOptions particle = new DustParticleOptions(color, 1.0F);
        for (int i = 0; i <= steps; i++) {
            level.sendParticles(particle, cur.x, cur.y, cur.z, 1, 0, 0, 0, 0);
            cur = cur.add(stepVec);
        }
    }

    private static void drawMarker(ServerLevel level, Vec3 pos, Vector3f color, float size) {
        DustParticleOptions particle = new DustParticleOptions(color, size);
        level.sendParticles(particle, pos.x, pos.y, pos.z, 4, 0.1, 0.1, 0.1, 0);
    }

    /**
     * デバッグ有効プレイヤーへ発砲ヘッダーメッセージを送信する。
     */
    public static void sendShotHeader(ServerPlayer player, float effectiveSoundRadius, boolean isSuppressed) {
        player.sendSystemMessage(Component.literal(
                "§6=== [SuperbStealth Debug] 発砲検知 (半径: " + String.format("%.1f", effectiveSoundRadius) + "m / サプレッサー: " + isSuppressed + ") ==="
        ));
    }

    /**
     * 音波伝播結果の可視化パーティクル線およびチャット通知を描画・送信する。
     */
    public static void renderDebugVisualization(ServerLevel level, ServerPlayer player, Vec3 shootPos, Mob mob, Vec3 mobEyePos, SoundResult result) {
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
                    spawnLine(level, from, to, lineColor, DEBUG_DURATION_TICKS);

                    // 中間開口部には黄色マーカーを設置
                    if (!isFinalLeg) {
                        spawnMarker(level, to, COLOR_YELLOW, 2.0F, DEBUG_DURATION_TICKS);
                    }
                }
            } else {
                spawnLine(level, shootPos, mobEyePos, COLOR_GREEN, DEBUG_DURATION_TICKS);
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
            spawnLine(level, shootPos, stopPos, COLOR_RED, DEBUG_DURATION_TICKS);
            spawnMarker(level, stopPos, COLOR_RED, 2.0F, DEBUG_DURATION_TICKS);

            player.sendSystemMessage(Component.literal(
                    "§7- §e" + mob.getName().getString() + "§7: §c遮断 (消音)"
            ));
        }
    }
}
