package net.addon.superbstealth.util;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class DebugParticleManager {

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

    private static final List<PersistentLine> LINES = new CopyOnWriteArrayList<>();
    private static final List<PersistentMarker> MARKERS = new CopyOnWriteArrayList<>();

    public static void spawnLine(ServerLevel level, Vec3 from, Vec3 to, Vector3f color, int durationTicks) {
        drawLine(level, from, to, color);
        LINES.add(new PersistentLine(level, from, to, color, durationTicks));
    }

    public static void spawnMarker(ServerLevel level, Vec3 pos, Vector3f color, float size, int durationTicks) {
        drawMarker(level, pos, color, size);
        MARKERS.add(new PersistentMarker(level, pos, color, size, durationTicks));
    }

    public static void tick() {
        if (!LINES.isEmpty()) {
            for (PersistentLine line : LINES) {
                line.remainingTicks--;
                if (line.remainingTicks % 8 == 0 && line.remainingTicks > 0) {
                    drawLine(line.level, line.from, line.to, line.color);
                }
            }
            LINES.removeIf(l -> l.remainingTicks <= 0);
        }

        if (!MARKERS.isEmpty()) {
            for (PersistentMarker marker : MARKERS) {
                marker.remainingTicks--;
                if (marker.remainingTicks % 8 == 0 && marker.remainingTicks > 0) {
                    drawMarker(marker.level, marker.pos, marker.color, marker.size);
                }
            }
            MARKERS.removeIf(m -> m.remainingTicks <= 0);
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
}
