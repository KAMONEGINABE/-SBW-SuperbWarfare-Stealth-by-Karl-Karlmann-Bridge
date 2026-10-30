package net.addon.superbstealth.util;

import net.addon.superbstealth.config.SuperbStealthConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Calculates direct sound penetration attenuation and detour attenuation through doors and openings.
 */
public class SoundOcclusionHelper {

    public static final float DEFAULT_FLOOR_CEILING_PENALTY = 12.0F;
    public static final float DEFAULT_WALL_PENALTY = 8.0F;
    public static final float DEFAULT_DETOUR_PENALTY = 2.0F;
    public static final int DEFAULT_MAX_DETOUR_HOPS = 3;

    public static float getFloorCeilingPenalty() {
        return SuperbStealthConfig.CONFIG.floorCeilingPenalty.get().floatValue();
    }

    public static float getWallPenalty() {
        return SuperbStealthConfig.CONFIG.wallPenalty.get().floatValue();
    }

    public static float getDetourPenalty() {
        return SuperbStealthConfig.CONFIG.detourPenalty.get().floatValue();
    }

    public static int getMaxDetourHops() {
        return SuperbStealthConfig.CONFIG.maxDetourHops.get();
    }

    public static int getMaxPenetrations() {
        return SuperbStealthConfig.CONFIG.maxPenetrations.get();
    }

    public record SoundResult(
            boolean reached,
            double effectiveDistance,
            float remainingStrength,
            int hops,
            List<Vec3> waypoints,
            Vec3 stopPos
    ) {
        public static SoundResult blocked(Vec3 stopPos) {
            return new SoundResult(false, 0.0, 0.0F, 0, Collections.emptyList(), stopPos);
        }

        public static SoundResult reachedDirect(double dist, float remaining, Vec3 source, Vec3 target) {
            List<Vec3> pts = new ArrayList<>(2);
            pts.add(source);
            pts.add(target);
            return new SoundResult(true, dist, remaining, 0, pts, null);
        }

        public static SoundResult reachedMultiHop(double dist, float remaining, int hops, List<Vec3> waypoints) {
            return new SoundResult(true, dist, remaining, hops, waypoints, null);
        }
    }

    /**
     * 音源からターゲット地点への音波伝播をシミュレートする（後方互換用オーバーロード）。
     */
    public static SoundResult checkSoundPropagation(Level level, Vec3 source, Vec3 target, float initialRadius) {
        return checkSoundPropagation(level, source, target, initialRadius, null);
    }

    /**
     * Simulates sound propagation from source to target.
     * If obstacles block direct raycast to the mob, calculates both penetration and detour paths,
     * selecting whichever delivers higher remaining sound strength.
     *
     * @param cachedShooterExit Pre-resolved room exit around shooter, or null to scan internally.
     */
    public static SoundResult checkSoundPropagation(Level level, Vec3 source, Vec3 target, float initialRadius, BlockPos cachedShooterExit) {
        if (initialRadius <= 0.0F) {
            return SoundResult.blocked(source);
        }

        double straightDist = source.distanceTo(target);
        if (straightDist > initialRadius) {
            return SoundResult.blocked(target);
        }

        ClipContext directCtx = new ClipContext(source, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, null);
        BlockHitResult directHit = level.clip(directCtx);
        if (directHit.getType() == HitResult.Type.MISS) {
            return SoundResult.reachedDirect(straightDist, (float) (initialRadius - straightDist), source, target);
        }

        SoundResult penResult = checkMultiPenetration(level, source, target, initialRadius, straightDist);
        float minStrengthThreshold = penResult.reached() ? penResult.remainingStrength() : 0.0F;

        // Skip detour search if penetration penalty is already lower than detour penalty (detour cannot physically beat it).
        float detourPenalty = getDetourPenalty();
        if (penResult.reached()) {
            float totalPenPenalty = (float) (initialRadius - straightDist) - penResult.remainingStrength();
            if (totalPenPenalty <= detourPenalty) {
                return penResult;
            }
        }

        List<Vec3> initialPath = new ArrayList<>();
        initialPath.add(source);
        Set<BlockPos> visited = new HashSet<>();
        SoundResult detResult = searchMultiHopDetour(
                level, source, target, initialRadius, 0, initialPath, visited,
                minStrengthThreshold, cachedShooterExit
        );

        boolean penReached = penResult.reached();
        boolean detReached = detResult.reached();

        if (penReached && detReached) {
            return (detResult.remainingStrength() >= penResult.remainingStrength()) ? detResult : penResult;
        } else if (detReached) {
            return detResult;
        } else if (penReached) {
            return penResult;
        } else {
            return (detResult.stopPos() != null) ? detResult : penResult;
        }
    }

    /**
     * 再帰的な連鎖開口部探索（最大3ホップ、分枝限定法・枝刈り付き）。
     */
    private static SoundResult searchMultiHopDetour(
            Level level,
            Vec3 currentPos,
            Vec3 target,
            float remainingRadius,
            int currentHop,
            List<Vec3> currentPath,
            Set<BlockPos> visitedOpenings,
            float minStrengthThreshold,
            BlockPos cachedShooterExit
    ) {
        ClipContext ctx = new ClipContext(currentPos, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, null);
        BlockHitResult hit = level.clip(ctx);

        if (hit.getType() == HitResult.Type.MISS) {
            double distToTarget = currentPos.distanceTo(target);
            float remainingStrength = remainingRadius - (float) distToTarget;
            if (distToTarget <= remainingRadius && remainingStrength > minStrengthThreshold) {
                List<Vec3> completePath = new ArrayList<>(currentPath);
                completePath.add(target);
                double totalDist = calculatePathLength(completePath);
                return SoundResult.reachedMultiHop(totalDist, remainingStrength, currentHop, completePath);
            }
            return SoundResult.blocked(target);
        }

        if (currentHop >= getMaxDetourHops()) {
            return SoundResult.blocked(hit.getLocation());
        }

        float detourPenalty = getDetourPenalty();

        // Prune branch if theoretical maximum remaining strength cannot beat current best
        double distToTarget = currentPos.distanceTo(target);
        float theoreticalMaxRemaining = remainingRadius - (float) distToTarget - detourPenalty;
        if (theoreticalMaxRemaining <= minStrengthThreshold) {
            return SoundResult.blocked(hit.getLocation());
        }

        List<BlockPos> openingCandidates = new ArrayList<>();

        if (currentHop == 0) {
            BlockPos sourceExit = (cachedShooterExit != null)
                    ? cachedShooterExit
                    : findShooterRoomExit(level, BlockPos.containing(currentPos), 4);
            if (sourceExit != null && !visitedOpenings.contains(sourceExit)) {
                openingCandidates.add(sourceExit);
            }
        }

        BlockPos hitPos = hit.getBlockPos();
        Direction face = hit.getDirection();
        BlockPos hitOpening = findNearestOpening(level, hitPos, face, 4);
        if (hitOpening != null && !visitedOpenings.contains(hitOpening) && !openingCandidates.contains(hitOpening)) {
            openingCandidates.add(hitOpening);
        }

        if (openingCandidates.isEmpty()) {
            return SoundResult.blocked(hit.getLocation());
        }

        SoundResult bestResult = SoundResult.blocked(hit.getLocation());
        float currentThreshold = minStrengthThreshold;

        for (BlockPos opening : openingCandidates) {
            visitedOpenings.add(opening);
            Vec3 openingExitPoint = calculateOpeningExitPoint(level, opening, face, target);
            double distToOpening = currentPos.distanceTo(openingExitPoint);

            float nextRemaining = remainingRadius - (float) distToOpening - detourPenalty;

            double distFromOpeningToTarget = openingExitPoint.distanceTo(target);
            if (nextRemaining > 0.0F && (nextRemaining - (float) distFromOpeningToTarget) > currentThreshold) {
                List<Vec3> nextPath = new ArrayList<>(currentPath);
                nextPath.add(openingExitPoint);

                // Start slightly inside the opening block to prevent raycast origin from embedding inside stair/slab hitboxes
                Vec3 rayDir = target.subtract(openingExitPoint).normalize();
                Vec3 nextStartPos = openingExitPoint.subtract(rayDir.scale(0.02));

                SoundResult subResult = searchMultiHopDetour(
                        level,
                        nextStartPos,
                        target,
                        nextRemaining,
                        currentHop + 1,
                        nextPath,
                        visitedOpenings,
                        currentThreshold,
                        null
                );

                if (subResult.reached() && subResult.remainingStrength() > bestResult.remainingStrength()) {
                    bestResult = subResult;
                    currentThreshold = Math.max(currentThreshold, subResult.remainingStrength());
                }
            }

            visitedOpenings.remove(opening);
        }

        return bestResult;
    }

    /**
     * Calculates the detour exit point near the opening surface to prevent rays from hitting block corners at acute angles.
     */
    private static Vec3 calculateOpeningExitPoint(Level level, BlockPos opening, Direction hitFace, Vec3 target) {
        Vec3 center = Vec3.atCenterOf(opening);

        boolean isVerticalOpening = (hitFace.getAxis() == Direction.Axis.Y)
                || isPhysicallyPassable(level, opening.above())
                || isPhysicallyPassable(level, opening.below());

        if (isVerticalOpening && Math.abs(target.y - center.y) > 0.5) {
            double exitY = (target.y > center.y) ? (opening.getY() + 1.0) : (opening.getY());

            Vec3 dir = target.subtract(center);
            double lenHoriz = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
            double offsetX = 0.0;
            double offsetZ = 0.0;
            if (lenHoriz > 1.0E-4) {
                offsetX = Math.min(0.35, Math.max(-0.35, (dir.x / lenHoriz) * 0.35));
                offsetZ = Math.min(0.35, Math.max(-0.35, (dir.z / lenHoriz) * 0.35));
            }

            return new Vec3(opening.getX() + 0.5 + offsetX, exitY, opening.getZ() + 0.5 + offsetZ);
        } else {
            AABB openingBounds = new AABB(opening);
            Vec3 rayDir = target.subtract(center).normalize();

            Optional<Vec3> exit = openingBounds.clip(center, target);
            if (exit.isPresent()) {
                return exit.get();
            }

            return center.add(rayDir.scale(0.49));
        }
    }

    private static double calculatePathLength(List<Vec3> path) {
        double dist = 0.0;
        for (int i = 0; i < path.size() - 1; i++) {
            dist += path.get(i).distanceTo(path.get(i + 1));
        }
        return dist;
    }

    /**
     * Traces raycast through multiple walls. Warps to the exit face of each hit block to prevent
     * loop exhaustion inside thick walls, and applies attenuation proportional to penetrated thickness.
     */
    private static SoundResult checkMultiPenetration(Level level, Vec3 source, Vec3 target, float initialRadius, double straightDist) {
        float remainingRadius = initialRadius;
        Vec3 currentStart = source;
        Vec3 rayDir = target.subtract(source).normalize();
        Set<BlockPos> traversedBlocks = new HashSet<>();
        Vec3 lastHitLoc = source;
        int maxPenetrations = getMaxPenetrations();
        boolean reachedTarget = false;

        while (maxPenetrations-- > 0) {
            ClipContext ctx = new ClipContext(currentStart, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, null);
            BlockHitResult hit = level.clip(ctx);

            if (hit.getType() == HitResult.Type.MISS) {
                reachedTarget = true;
                break;
            }

            BlockPos hitPos = hit.getBlockPos();
            Direction face = hit.getDirection();
            Vec3 entryPt = hit.getLocation();
            lastHitLoc = entryPt;

            AABB box = new AABB(hitPos);
            Optional<Vec3> exit = box.clip(entryPt.add(rayDir.scale(0.001)), target);
            Vec3 exitPt = exit.orElse(entryPt.add(rayDir.scale(1.0)));

            if (!traversedBlocks.contains(hitPos)) {
                traversedBlocks.add(hitPos);
                boolean isFloorCeiling = (face.getAxis() == Direction.Axis.Y);

                double topY = hitPos.getY() + 1.0;
                double bottomY = hitPos.getY();
                if (Math.abs(exitPt.y - topY) < 0.001 || Math.abs(exitPt.y - bottomY) < 0.001) {
                    isFloorCeiling = true;
                }

                float basePenalty = isFloorCeiling ? getFloorCeilingPenalty() : getWallPenalty();

                double thickness = Math.max(1.0, entryPt.distanceTo(exitPt));
                float penalty = (float) (basePenalty * thickness);
                remainingRadius -= penalty;

                if (remainingRadius <= 0.0F || straightDist > remainingRadius) {
                    return SoundResult.blocked(lastHitLoc);
                }
            }

            // Warp to just outside exit face to continue raycast into next block/space
            currentStart = exitPt.add(rayDir.scale(0.01));

            if (currentStart.distanceTo(source) >= straightDist) {
                reachedTarget = true;
                break;
            }
        }

        if (!reachedTarget) {
            return SoundResult.blocked(lastHitLoc);
        }

        return (straightDist <= remainingRadius)
                ? SoundResult.reachedDirect(straightDist, remainingRadius - (float) straightDist, source, target)
                : SoundResult.blocked(lastHitLoc);
    }

    /**
     * 射撃者の周囲にある「物理的に開いているドア」や「部屋の出口」を探索する。
     */
    public static BlockPos findShooterRoomExit(Level level, BlockPos center, int radius) {
        BlockPos nearestDoor = null;
        double nearestDistSq = Double.MAX_VALUE;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -1; dy <= 2; dy++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    if (pos.equals(center)) continue;

                    BlockState state = level.getBlockState(pos);
                    // 空気以外のブロック（ドアやトラップドアなど）で、物理的に通過可能なものを最優先
                    if (!state.isAir() && isPhysicallyPassable(level, pos)) {
                        double distSq = center.distSqr(pos);
                        if (distSq < nearestDistSq) {
                            nearestDistSq = distSq;
                            nearestDoor = pos;
                        }
                    }
                }
            }
        }

        return nearestDoor;
    }

    /**
     * Finds nearby openings (air, open doors) around hit location.
     * Floor/ceiling checks only verify the opening space itself so lower stairs are not falsely excluded;
     * raycasts in subsequent hops determine actual clearance.
     */
    private static BlockPos findNearestOpening(Level level, BlockPos hitPos, Direction face, int maxDist) {
        if (face.getAxis() == Direction.Axis.Y) {
            Direction[] horizontalDirs = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
            for (int d = 1; d <= maxDist; d++) {
                for (Direction dir : horizontalDirs) {
                    BlockPos checkPos = hitPos.relative(dir, d);
                    if (isPhysicallyPassable(level, checkPos)) {
                        return checkPos;
                    }
                }
            }
        } else {
            Direction lateral = face.getClockWise();
            for (int d = 1; d <= maxDist; d++) {
                for (int dy = -2; dy <= 2; dy++) {
                    BlockPos checkPosRight = hitPos.relative(lateral, d).above(dy);
                    if (isPhysicallyPassable(level, checkPosRight)) {
                        return checkPosRight;
                    }

                    BlockPos checkPosLeft = hitPos.relative(lateral.getOpposite(), d).above(dy);
                    if (isPhysicallyPassable(level, checkPosLeft)) {
                        return checkPosLeft;
                    }
                }
            }

            if (isPhysicallyPassable(level, hitPos.above())) {
                return hitPos.above();
            }
        }

        return null;
    }

    /**
     * Tests if sound can physically pass through this block.
     * Cross-ray tests are used because door open flags can invert depending on placement orientation.
     */
    private static boolean isPhysicallyPassable(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return true;
        }
        VoxelShape shape = state.getCollisionShape(level, pos);
        if (shape.isEmpty()) {
            return true;
        }

        if (Block.isShapeFullBlock(shape) || shape == Shapes.block()) {
            return false;
        }

        Vec3 center = Vec3.atCenterOf(pos);

        ClipContext ctxX = new ClipContext(center.add(-0.6, 0, 0), center.add(0.6, 0, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, null);
        if (level.clip(ctxX).getType() == HitResult.Type.MISS) {
            return true;
        }

        ClipContext ctxZ = new ClipContext(center.add(0, 0, -0.6), center.add(0, 0, 0.6), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, null);
        if (level.clip(ctxZ).getType() == HitResult.Type.MISS) {
            return true;
        }

        return false;
    }
}
