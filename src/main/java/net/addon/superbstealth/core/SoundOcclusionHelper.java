package net.addon.superbstealth.core;

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
    private static Vec3 calculateOpeningExitPoint(Level level, BlockPos opening, Direction initialFace, Vec3 target) {
        Vec3 center = Vec3.atCenterOf(opening);
        BlockState state = level.getBlockState(opening);
        VoxelShape shape = state.getCollisionShape(level, opening);

        Vec3 exitVector;
        if (shape.isEmpty()) {
            exitVector = Vec3.atLowerCornerOf(initialFace.getNormal()).scale(0.35);
        } else {
            AABB bounds = shape.bounds();
            Vec3 rayDir = target.subtract(center).normalize();
            Optional<Vec3> exit = bounds.move(opening).clip(center, center.add(rayDir.scale(2.0)));
            exitVector = exit.map(p -> p.subtract(center).scale(0.8)).orElse(Vec3.ZERO);
        }

        return center.add(exitVector);
    }

    private static double calculatePathLength(List<Vec3> path) {
        double len = 0.0;
        for (int i = 0; i < path.size() - 1; i++) {
            len += path.get(i).distanceTo(path.get(i + 1));
        }
        return len;
    }

    /**
     * Calculates attenuation through physical solid block thickness via step-wise raycasting.
     */
    private static SoundResult checkMultiPenetration(Level level, Vec3 source, Vec3 target, float initialRadius, double straightDist) {
        Vec3 rayDir = target.subtract(source).normalize();
        Vec3 currentStart = source;
        float remainingRadius = initialRadius;
        int penetrations = 0;
        int maxPenetrations = getMaxPenetrations();
        Vec3 lastStopPos = source;

        while (penetrations < maxPenetrations) {
            ClipContext forwardCtx = new ClipContext(currentStart, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, null);
            BlockHitResult entryHit = level.clip(forwardCtx);
            if (entryHit.getType() == HitResult.Type.MISS) {
                double remainingDist = currentStart.distanceTo(target);
                remainingRadius -= (float) remainingDist;
                if (remainingRadius > 0.0F) {
                    return SoundResult.reachedDirect(straightDist, remainingRadius, source, target);
                } else {
                    return SoundResult.blocked(target);
                }
            }

            penetrations++;
            Vec3 entryPoint = entryHit.getLocation();
            lastStopPos = entryPoint;
            Direction entryFace = entryHit.getDirection();
            double distToEntry = currentStart.distanceTo(entryPoint);
            remainingRadius -= (float) distToEntry;

            if (remainingRadius <= 0.0F) {
                return SoundResult.blocked(entryPoint);
            }

            // Reverse raycast from target to locate the exit point of the obstruction
            ClipContext backwardCtx = new ClipContext(target, entryPoint, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, null);
            BlockHitResult exitHit = level.clip(backwardCtx);

            Vec3 exitPoint;
            if (exitHit.getType() == HitResult.Type.MISS) {
                exitPoint = entryPoint.add(rayDir.scale(0.1));
            } else {
                exitPoint = exitHit.getLocation();
            }

            double obstructionThickness = Math.max(0.1, entryPoint.distanceTo(exitPoint));
            float penaltyPerMeter = (entryFace.getAxis() == Direction.Axis.Y)
                    ? getFloorCeilingPenalty()
                    : getWallPenalty();

            float penalty = (float) (obstructionThickness * penaltyPerMeter);
            remainingRadius -= penalty;

            if (remainingRadius <= 0.0F) {
                return SoundResult.blocked(entryPoint);
            }

            // Nudge forward past exit surface to avoid clipping the same boundary
            currentStart = exitPoint.add(rayDir.scale(0.05));
            if (currentStart.distanceTo(source) >= straightDist) {
                return SoundResult.reachedDirect(straightDist, remainingRadius, source, target);
            }
        }

        return SoundResult.blocked(lastStopPos);
    }

    /**
     * Scans for open paths out of the shooter's immediate room/enclosure.
     */
    public static BlockPos findShooterRoomExit(Level level, BlockPos origin, int radius) {
        if (!isRoomEnclosed(level, origin)) {
            return null;
        }

        BlockPos nearest = null;
        double bestDistSq = Double.MAX_VALUE;

        for (BlockPos checkPos : BlockPos.betweenClosed(
                origin.offset(-radius, -1, -radius),
                origin.offset(radius, 2, radius)
        )) {
            if (isDoorwayOrPortal(level, checkPos)) {
                double dSq = origin.distSqr(checkPos);
                if (dSq < bestDistSq) {
                    bestDistSq = dSq;
                    nearest = checkPos.immutable();
                }
            }
        }
        return nearest;
    }

    private static boolean isRoomEnclosed(Level level, BlockPos pos) {
        int solidFaces = 0;
        for (Direction dir : Direction.values()) {
            for (int step = 1; step <= 3; step++) {
                BlockPos p = pos.relative(dir, step);
                if (!level.getBlockState(p).isAir()) {
                    solidFaces++;
                    break;
                }
            }
        }
        return solidFaces >= 4;
    }

    private static boolean isDoorwayOrPortal(Level level, BlockPos pos) {
        return isPhysicallyPassable(level, pos) && (
                level.getBlockState(pos).is(net.minecraft.tags.BlockTags.DOORS) ||
                level.getBlockState(pos).is(net.minecraft.tags.BlockTags.TRAPDOORS) ||
                level.getBlockState(pos).is(net.minecraft.tags.BlockTags.FENCE_GATES)
        );
    }

    /**
     * Locates the nearest physically passable opening around an obstacle impact.
     */
    public static BlockPos findNearestOpening(Level level, BlockPos hitPos, Direction face, int maxDist) {
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
