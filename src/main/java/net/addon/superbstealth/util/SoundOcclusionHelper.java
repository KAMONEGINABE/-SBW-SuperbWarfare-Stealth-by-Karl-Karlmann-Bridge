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

public class SoundOcclusionHelper {

    public static final float DEFAULT_FLOOR_CEILING_PENALTY = 12.0F; // 床/天井スラブ貫通ペナルティ (-12m)
    public static final float DEFAULT_WALL_PENALTY = 8.0F;           // 間仕切り壁・ドア貫通ペナルティ (-8m)
    public static final float DEFAULT_DETOUR_PENALTY = 2.0F;         // 回折・迂回ペナルティ (-2m)
    public static final int DEFAULT_MAX_DETOUR_HOPS = 3;             // 最大屈折回数 (ホップ数)

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
     * 音源からターゲット地点への音波伝播をシミュレートする。
     * 直進多重貫通波（ルートA）と連鎖開口部回折波（ルートB）を並行評価し、
     * 最も残存音波強度（Volume）が大きい最強ルートを自動採用する（Max-Volume Selection）。
     *
     * @param cachedShooterExit 事前に解決された射撃者周囲の部屋出口（null の場合は内部で走査）
     */
    public static SoundResult checkSoundPropagation(Level level, Vec3 source, Vec3 target, float initialRadius, BlockPos cachedShooterExit) {
        if (initialRadius <= 0.0F) {
            return SoundResult.blocked(source);
        }

        double straightDist = source.distanceTo(target);
        if (straightDist > initialRadius) {
            return SoundResult.blocked(target);
        }

        // 1. 直線上に障害物がないか確認 (見通しがあれば最優先で即時リターン)
        ClipContext directCtx = new ClipContext(source, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, null);
        BlockHitResult directHit = level.clip(directCtx);
        if (directHit.getType() == HitResult.Type.MISS) {
            return SoundResult.reachedDirect(straightDist, (float) (initialRadius - straightDist), source, target);
        }

        // 2. ルート A: 直進多重貫通減衰の計算 (壁 -8m / 床 -12m 累積, 出射面判定付き)
        SoundResult penResult = checkMultiPenetration(level, source, target, initialRadius, straightDist);

        // ルートA到達時、その残存強度をルートBの枝刈り閾値（minStrengthThreshold）として利用する。
        // 未到達の場合は 0.0F（消音されないこと）が足切り閾値。
        float minStrengthThreshold = penResult.reached() ? penResult.remainingStrength() : 0.0F;

        // 【最適化: 理論上限によるルートB完全スキップ判定】
        // 迂回には最低でも 1回のホップ（回折ペナルティ）と直線以上の移動距離が必要となるため、
        // ルートAの貫通減衰ペナルティが回折ペナルティ以下であれば、
        // 物理的にルートBがルートAを上回ることは絶対にあり得ない。
        float detourPenalty = getDetourPenalty();
        if (penResult.reached()) {
            float totalPenPenalty = (float) (initialRadius - straightDist) - penResult.remainingStrength();
            if (totalPenPenalty <= detourPenalty) {
                return penResult;
            }
        }

        // 3. ルート B: 連鎖開口部迂回の計算 (最大3ホップ, 回折 -2m 累積, 分枝限定法による早期枝刈り付き)
        List<Vec3> initialPath = new ArrayList<>();
        initialPath.add(source);
        Set<BlockPos> visited = new HashSet<>();
        SoundResult detResult = searchMultiHopDetour(
                level, source, target, initialRadius, 0, initialPath, visited,
                minStrengthThreshold, cachedShooterExit
        );

        // 4. 最大音量（残存強度の最大値）を選択
        boolean penReached = penResult.reached();
        boolean detReached = detResult.reached();

        if (penReached && detReached) {
            // 両方届く場合は、より音が大きい（残存強度が高い）ルートを採用！
            return (detResult.remainingStrength() >= penResult.remainingStrength()) ? detResult : penResult;
        } else if (detReached) {
            return detResult;
        } else if (penReached) {
            return penResult;
        } else {
            // 両方遮断された場合、より遠くまで進んだ（敵に近い）衝突地点を採用
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
        // 現在位置からターゲットへ直接視線が通るか確認
        ClipContext ctx = new ClipContext(currentPos, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, null);
        BlockHitResult hit = level.clip(ctx);

        if (hit.getType() == HitResult.Type.MISS) {
            // ターゲットまで直接到達！
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

        // これ以上屈折できない（最大ホップ到達）なら枝刈り
        if (currentHop >= getMaxDetourHops()) {
            return SoundResult.blocked(hit.getLocation());
        }

        float detourPenalty = getDetourPenalty();

        // 【最適化: 理論上限判定による早期枝刈り (Branch and Bound)】
        // 現在地からターゲットへの直線距離を最短で移動し、あと最低1回の回折ペナルティを受けた場合の最大可能残存強度
        double distToTarget = currentPos.distanceTo(target);
        float theoreticalMaxRemaining = remainingRadius - (float) distToTarget - detourPenalty;
        if (theoreticalMaxRemaining <= minStrengthThreshold) {
            // これ以上探索しても既存の最良解（ルートAまたは探索済みの迂回解）を上回ることは絶対にない
            return SoundResult.blocked(hit.getLocation());
        }

        // 開口部候補を探索
        List<BlockPos> openingCandidates = new ArrayList<>();

        // ホップ0（射撃直後）の場合、射撃者の周囲にある開いたドア（部屋の出口）を優先追加
        if (currentHop == 0) {
            BlockPos sourceExit = (cachedShooterExit != null)
                    ? cachedShooterExit
                    : findShooterRoomExit(level, BlockPos.containing(currentPos), 4);
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
        float currentThreshold = minStrengthThreshold;

        for (BlockPos opening : openingCandidates) {
            visitedOpenings.add(opening);
            Vec3 openingExitPoint = calculateOpeningExitPoint(level, opening, face, target);
            double distToOpening = currentPos.distanceTo(openingExitPoint);

            // 回折ペナルティを減算
            float nextRemaining = remainingRadius - (float) distToOpening - detourPenalty;

            // 開口部を出た後の理論上限（開口部からターゲットへの直線距離）が現在の最良閾値を超えるかチェック
            double distFromOpeningToTarget = openingExitPoint.distanceTo(target);
            if (nextRemaining > 0.0F && (nextRemaining - (float) distFromOpeningToTarget) > currentThreshold) {
                List<Vec3> nextPath = new ArrayList<>(currentPath);
                nextPath.add(openingExitPoint);

                // 開口部ブロック（100%空気）の内側スレスレ（境界の手前2cm）から次区間のレイキャストを開始
                // これにより、上のブロックが逆向き階段やハーフブロック等の場合でも開始点の自己埋没（Self-Hit）を防止
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
     * 開口部ブロック（階段穴やドア）から次の空間へ抜ける出口座標（openingExitPoint）を計算する。
     * 床・天井の穴なら垂直境界（上面/下面）、壁の穴・ドアなら水平出射境界を基準にし、
     * 開口部自体の内壁や床スラブの角への誤衝突を幾何学的に防止する。
     */
    private static Vec3 calculateOpeningExitPoint(Level level, BlockPos opening, Direction hitFace, Vec3 target) {
        Vec3 center = Vec3.atCenterOf(opening);

        boolean isVerticalOpening = (hitFace.getAxis() == Direction.Axis.Y)
                || isPhysicallyPassable(level, opening.above())
                || isPhysicallyPassable(level, opening.below());

        if (isVerticalOpening && Math.abs(target.y - center.y) > 0.5) {
            // 床または天井の開口部（階段吹き抜け等）
            // 下から上へ向かう場合は上面境界（y + 1.0）、上から下へ向かう場合は下面境界（y）が出口面
            double exitY = (target.y > center.y) ? (opening.getY() + 1.0) : (opening.getY());

            // 水平位置は、穴の中心からターゲットへ向かう方向に少しだけオフセット（穴の縁を越えない安全マージン: ±0.35m）
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
            // 垂直壁の開口部（ドアや壁の穴）
            AABB openingBounds = new AABB(opening);
            Vec3 rayDir = target.subtract(center).normalize();

            // 開口部空間領域（openingBounds）からターゲット方向へ抜ける出射点を算出
            Optional<Vec3> exit = openingBounds.clip(center, target);
            if (exit.isPresent()) {
                return exit.get();
            }

            // フォールバック: ターゲット方向への境界シフト
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
     * 直線上のすべての壁・床を貫通走査し、累積減衰を計算する。
     * 出口面遷移（Exit Point Warp）により、衝突ブロックの反対側へ即時ワープして通過厚みを正確に測定。
     * 通過した厚み（実距離）に応じた比例減衰を適用し、壁の縦貫通すり抜けを完全防止する。
     */
    private static SoundResult checkMultiPenetration(Level level, Vec3 source, Vec3 target, float initialRadius, double straightDist) {
        float remainingRadius = initialRadius;
        Vec3 currentStart = source;
        Vec3 rayDir = target.subtract(source).normalize();
        Set<BlockPos> traversedBlocks = new HashSet<>();
        Vec3 lastHitLoc = source;
        int maxPenetrations = getMaxPenetrations(); // 安全リミット
        boolean reachedTarget = false;

        while (maxPenetrations-- > 0) {
            ClipContext ctx = new ClipContext(currentStart, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, null);
            BlockHitResult hit = level.clip(ctx);

            if (hit.getType() == HitResult.Type.MISS) {
                // ターゲットまでの間に障害物がもう存在しない（完全に空間へ抜け切った）
                reachedTarget = true;
                break;
            }

            BlockPos hitPos = hit.getBlockPos();
            Direction face = hit.getDirection();
            Vec3 entryPt = hit.getLocation();
            lastHitLoc = entryPt;

            // 衝突ブロックの境界箱（AABB）から出口点（exitPt）を算出
            AABB box = new AABB(hitPos);
            Optional<Vec3> exit = box.clip(entryPt.add(rayDir.scale(0.001)), target);
            Vec3 exitPt = exit.orElse(entryPt.add(rayDir.scale(1.0)));

            // ユニークブロック判定（1ブロックにつき1回のみ減衰）
            if (!traversedBlocks.contains(hitPos)) {
                traversedBlocks.add(hitPos);
                boolean isFloorCeiling = (face.getAxis() == Direction.Axis.Y);

                // 出射面が上面または下面である場合も床スラブ貫通とみなす
                double topY = hitPos.getY() + 1.0;
                double bottomY = hitPos.getY();
                if (Math.abs(exitPt.y - topY) < 0.001 || Math.abs(exitPt.y - bottomY) < 0.001) {
                    isFloorCeiling = true;
                }

                float basePenalty = isFloorCeiling ? getFloorCeilingPenalty() : getWallPenalty();

                // 通過した厚み（実距離）に応じた比例減衰（最小でも 1.0m 分、斜めや縦貫通なら距離分）
                double thickness = Math.max(1.0, entryPt.distanceTo(exitPt));
                float penalty = (float) (basePenalty * thickness);
                remainingRadius -= penalty;

                // 減衰しきった、または残存半径が直線距離を下回ったら即座に消音
                if (remainingRadius <= 0.0F || straightDist > remainingRadius) {
                    return SoundResult.blocked(lastHitLoc);
                }
            }

            // 出口境界のわずか先（0.01m先）へ一気にワープして次区間のレイを開始
            currentStart = exitPt.add(rayDir.scale(0.01));

            // ターゲットを通り過ぎたらループ終了
            if (currentStart.distanceTo(source) >= straightDist) {
                reachedTarget = true;
                break;
            }
        }

        // ループ上限に達しても空間へ抜け切れていない（障害物の中にいる）場合は確実に遮断
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
     * 衝突地点周辺で音が抜けられる開口部（空気または非衝突ブロック、物理的に開いたドア）を探索する。
     */
    private static BlockPos findNearestOpening(Level level, BlockPos hitPos, Direction face, int maxDist) {
        if (face.getAxis() == Direction.Axis.Y) {
            // 床または天井に衝突：水平4方向（東西南北）に開口部（階段吹き抜け、床の穴）を探索
            // 大雑把な上下ブロックの事前フィルタは行わず、穴自体の空気判定のみを行い、
            // 空間が本当に抜けているかは次ホップの高精度なバニラレイキャスト（level.clip）に委譲する。
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

            // 階段ブロック等に水平衝突した場合、その真上が空気であれば階段空間として開口部とする
            if (isPhysicallyPassable(level, hitPos.above())) {
                return hitPos.above();
            }
        }

        return null;
    }

    /**
     * そのブロックが物理的に「音が通り抜けられる開口部」かどうかを判定する。
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

        // 完全な立方体（フルブロック）であれば音は通過できないため即時除外（不要なレイキャストを回避）
        if (Block.isShapeFullBlock(shape) || shape == Shapes.block()) {
            return false;
        }

        // ドアや薄い障害物の場合、ブロックの中心を東西または南北に通り抜けられるかテスト
        Vec3 center = Vec3.atCenterOf(pos);

        // X軸（東西）の通り抜けテスト（長さ 1.2 ブロック）
        ClipContext ctxX = new ClipContext(center.add(-0.6, 0, 0), center.add(0.6, 0, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, null);
        if (level.clip(ctxX).getType() == HitResult.Type.MISS) {
            return true;
        }

        // Z軸（南北）の通り抜けテスト（長さ 1.2 ブロック）
        ClipContext ctxZ = new ClipContext(center.add(0, 0, -0.6), center.add(0, 0, 0.6), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, null);
        if (level.clip(ctxZ).getType() == HitResult.Type.MISS) {
            return true;
        }

        return false;
    }
}
