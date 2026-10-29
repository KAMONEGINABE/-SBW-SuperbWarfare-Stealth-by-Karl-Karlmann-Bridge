package net.addon.superbstealth.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SoundOcclusionHelper {

    public static final float FLOOR_CEILING_PENALTY = 10.0F; // 床/天井スラブ貫通ペナルティ (-10m)
    public static final float WALL_PENALTY = 5.0F;           // 間仕切り壁・ドア貫通ペナルティ (-5m)
    public static final float DETOUR_PENALTY = 4.0F;         // 回折・迂回ペナルティ (-4m)
    public static final int MAX_DETOUR_HOPS = 3;             // 最大屈折回数 (ホップ数)

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
     * 音源からターゲット地点への音波伝播をシミュレートする。
     * 最大3回までの連鎖開口部迂回（Multi-hop Waypoint Traversal）および直進多重貫通減衰を評価する。
     */
    public static SoundResult checkSoundPropagation(Level level, Vec3 source, Vec3 target, float initialRadius) {
        if (initialRadius <= 0.0F) {
            return SoundResult.blocked(source);
        }

        double straightDist = source.distanceTo(target);
        if (straightDist > initialRadius) {
            return SoundResult.blocked(target);
        }

        // 1. 直線上に障害物がないか確認
        ClipContext directCtx = new ClipContext(source, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, null);
        BlockHitResult directHit = level.clip(directCtx);
        if (directHit.getType() == HitResult.Type.MISS) {
            // 直線到達 (0ホップ)
            return SoundResult.reachedDirect(straightDist, (float) (initialRadius - straightDist), source, target);
        }

        // 2. 最大3回の連鎖開口部迂回（ドア -> 階段穴 -> 敵）を探索
        List<Vec3> initialPath = new ArrayList<>();
        initialPath.add(source);
        Set<BlockPos> visited = new HashSet<>();

        SoundResult detourResult = searchMultiHopDetour(level, source, target, initialRadius, 0, initialPath, visited);
        if (detourResult.reached()) {
            return detourResult;
        }

        // 3. 迂回できない、または迂回ルートが消音した場合は、直進の多重貫通減衰を評価
        return checkMultiPenetration(level, source, target, initialRadius, straightDist);
    }

    /**
     * 再帰的な連鎖開口部探索（最大3ホップ）。
     */
    private static SoundResult searchMultiHopDetour(
            Level level,
            Vec3 currentPos,
            Vec3 target,
            float remainingRadius,
            int currentHop,
            List<Vec3> currentPath,
            Set<BlockPos> visitedOpenings
    ) {
        // 現在位置からターゲットへ直接視線が通るか確認
        ClipContext ctx = new ClipContext(currentPos, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, null);
        BlockHitResult hit = level.clip(ctx);

        if (hit.getType() == HitResult.Type.MISS) {
            // ターゲットまで直接到達！
            double distToTarget = currentPos.distanceTo(target);
            if (distToTarget <= remainingRadius) {
                List<Vec3> completePath = new ArrayList<>(currentPath);
                completePath.add(target);
                double totalDist = calculatePathLength(completePath);
                float remainingStrength = remainingRadius - (float) distToTarget;
                return SoundResult.reachedMultiHop(totalDist, remainingStrength, currentHop, completePath);
            }
            return SoundResult.blocked(target);
        }

        // これ以上屈折できない（最大ホップ到達）なら枝刈り
        if (currentHop >= MAX_DETOUR_HOPS) {
            return SoundResult.blocked(hit.getLocation());
        }

        // 開口部候補を探索
        List<BlockPos> openingCandidates = new ArrayList<>();

        // ホップ0（射撃直後）の場合、射撃者の周囲にある開いたドア（部屋の出口）を優先追加
        if (currentHop == 0) {
            BlockPos sourceExit = findShooterRoomExit(level, BlockPos.containing(currentPos), 4);
            if (sourceExit != null && !visitedOpenings.contains(sourceExit)) {
                openingCandidates.add(sourceExit);
            }
        }

        // 衝突地点周辺の開口部（階段穴や壁の穴）を追加
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

        for (BlockPos opening : openingCandidates) {
            visitedOpenings.add(opening);
            Vec3 openingCenter = Vec3.atCenterOf(opening);
            double distToOpening = currentPos.distanceTo(openingCenter);

            // 回折ペナルティ（-4m）を減算
            float nextRemaining = remainingRadius - (float) distToOpening - DETOUR_PENALTY;
            if (nextRemaining > 0.0F) {
                List<Vec3> nextPath = new ArrayList<>(currentPath);
                nextPath.add(openingCenter);

                SoundResult subResult = searchMultiHopDetour(
                        level,
                        openingCenter,
                        target,
                        nextRemaining,
                        currentHop + 1,
                        nextPath,
                        visitedOpenings
                );

                if (subResult.reached() && subResult.remainingStrength() > bestResult.remainingStrength()) {
                    bestResult = subResult;
                }
            }

            visitedOpenings.remove(opening);
        }

        return bestResult;
    }

    private static double calculatePathLength(List<Vec3> path) {
        double dist = 0.0;
        for (int i = 0; i < path.size() - 1; i++) {
            dist += path.get(i).distanceTo(path.get(i + 1));
        }
        return dist;
    }

    /**
     * 直線上のすべての壁・床を貫通走査し、累積減衰を計算する。
     * 1つのブロックで入る面と出る面の両方をカウントしないよう、BlockPos単位で管理。
     */
    private static SoundResult checkMultiPenetration(Level level, Vec3 source, Vec3 target, float initialRadius, double straightDist) {
        float remainingRadius = initialRadius;
        Vec3 currentStart = source;
        Vec3 rayDir = target.subtract(source).normalize();
        Set<BlockPos> traversedBlocks = new HashSet<>();
        Vec3 lastHitLoc = source;
        int maxPenetrations = 8; // 安全リミット（最大8枚貫通まで）

        while (maxPenetrations-- > 0) {
            ClipContext ctx = new ClipContext(currentStart, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, null);
            BlockHitResult hit = level.clip(ctx);

            if (hit.getType() == HitResult.Type.MISS) {
                // 障害物がもう存在しない
                return (straightDist <= remainingRadius)
                        ? SoundResult.reachedDirect(straightDist, remainingRadius - (float) straightDist, source, target)
                        : SoundResult.blocked(lastHitLoc);
            }

            BlockPos hitPos = hit.getBlockPos();
            Direction face = hit.getDirection();
            lastHitLoc = hit.getLocation();

            // ユニークブロック判定（1ブロックにつき1回のみ減衰）
            if (!traversedBlocks.contains(hitPos)) {
                traversedBlocks.add(hitPos);
                boolean isFloorCeiling = (face.getAxis() == Direction.Axis.Y);
                float penalty = isFloorCeiling ? FLOOR_CEILING_PENALTY : WALL_PENALTY;
                remainingRadius -= penalty;

                // 減衰しきった、または残存半径が直線距離を下回ったら即座に消音
                if (remainingRadius <= 0.0F || straightDist > remainingRadius) {
                    return SoundResult.blocked(lastHitLoc);
                }
            }

            // 次のレイの開始点を衝突点のわずか先（0.05ブロック先）に進める
            currentStart = hit.getLocation().add(rayDir.scale(0.05));

            // ターゲットを通り過ぎたらループ終了
            if (currentStart.distanceTo(source) >= straightDist) {
                break;
            }
        }

        return (straightDist <= remainingRadius)
                ? SoundResult.reachedDirect(straightDist, remainingRadius - (float) straightDist, source, target)
                : SoundResult.blocked(lastHitLoc);
    }

    /**
     * 射撃者の周囲にある「物理的に開いているドア」や「部屋の出口」を探索する。
     */
    private static BlockPos findShooterRoomExit(Level level, BlockPos center, int radius) {
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
     * 衝突地点周辺で音が抜けられる開口部（空気または非衝突ブロック、物理的に開いたドア）を探索する。
     */
    private static BlockPos findNearestOpening(Level level, BlockPos hitPos, Direction face, int maxDist) {
        if (face.getAxis() == Direction.Axis.Y) {
            // 床または天井に衝突：水平4方向（東西南北）に開口部（階段吹き抜け、床の穴）を探索
            Direction[] horizontalDirs = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
            for (int d = 1; d <= maxDist; d++) {
                for (Direction dir : horizontalDirs) {
                    BlockPos checkPos = hitPos.relative(dir, d);
                    if (isPhysicallyPassable(level, checkPos)) {
                        // 上下にも空間が抜けているか確認
                        BlockPos otherSide = (face == Direction.UP) ? checkPos.above() : checkPos.below();
                        if (isPhysicallyPassable(level, otherSide)) {
                            return checkPos;
                        }
                    }
                }
            }
        } else {
            // 垂直な壁に衝突：壁面に沿った水平方向（左右）および上下（-2〜+2）を探索
            Direction lateral = face.getClockWise();
            for (int d = 1; d <= maxDist; d++) {
                for (int dy = -2; dy <= 2; dy++) {
                    // 右側
                    BlockPos checkPosRight = hitPos.relative(lateral, d).above(dy);
                    if (isPhysicallyPassable(level, checkPosRight)) {
                        return checkPosRight;
                    }

                    // 左側
                    BlockPos checkPosLeft = hitPos.relative(lateral.getOpposite(), d).above(dy);
                    if (isPhysicallyPassable(level, checkPosLeft)) {
                        return checkPosLeft;
                    }
                }
            }
        }

        return null;
    }

    /**
     * そのブロックが物理的に「音が通り抜けられる開口部」かどうかを判定する。
     * ドアの設置向き（FACING）や OPEN フラグに依存せず、
     * ブロック中心を通る東西（X軸）または南北（Z軸）の短いローカルレイキャスト（長さ 1.2m）で直接判定する。
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

        // ドアや薄い障害物の場合、ブロックの中心を東西または南北に通り抜けられるかテスト
        Vec3 center = Vec3.atCenterOf(pos);

        // X軸（東西）の通り抜けテスト（長さ 1.2 ブロック）
        ClipContext ctxX = new ClipContext(center.add(-0.6, 0, 0), center.add(0.6, 0, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, null);
        if (level.clip(ctxX).getType() == HitResult.Type.MISS) {
            return true; // 東西に通路が開いている！
        }

        // Z軸（南北）の通り抜けテスト（長さ 1.2 ブロック）
        ClipContext ctxZ = new ClipContext(center.add(0, 0, -0.6), center.add(0, 0, 0.6), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, null);
        if (level.clip(ctxZ).getType() == HitResult.Type.MISS) {
            return true; // 南北に通路が開いている！
        }

        return false;
    }
}
