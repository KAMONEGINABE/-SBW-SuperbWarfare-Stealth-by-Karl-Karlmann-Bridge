package net.addon.superbstealth.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

public class SoundOcclusionHelper {

    public static final float FLOOR_CEILING_PENALTY = 10.0F; // 床/天井スラブ貫通ペナルティ (-10m)
    public static final float WALL_PENALTY = 5.0F;           // 間仕切り壁・ドア貫通ペナルティ (-5m)
    public static final float DETOUR_PENALTY = 4.0F;        // 回折・迂回ペナルティ (-4m)

    public record SoundResult(
            boolean reached,
            double effectiveDistance,
            float remainingStrength,
            boolean detoured,
            BlockPos openingPos,
            Vec3 stopPos
    ) {
        public static SoundResult blocked(Vec3 stopPos) {
            return new SoundResult(false, 0.0, 0.0F, false, null, stopPos);
        }

        public static SoundResult reachedDirect(double dist, float remaining) {
            return new SoundResult(true, dist, remaining, false, null, null);
        }

        public static SoundResult reachedDetour(double dist, float remaining, BlockPos openingPos) {
            return new SoundResult(true, dist, remaining, true, openingPos, null);
        }
    }

    /**
     * 音源からターゲット地点への音波伝播をシミュレートする。
     * 多重ブロック貫通減衰（面判定）、二重カウント防止、および開口部（ドア・階段）の迂回を考慮する。
     */
    public static SoundResult checkSoundPropagation(Level level, Vec3 source, Vec3 target, float initialRadius) {
        if (initialRadius <= 0.0F) {
            return SoundResult.blocked(source);
        }

        double straightDist = source.distanceTo(target);
        if (straightDist > initialRadius) {
            return SoundResult.blocked(target);
        }

        // 1. まず開口部（階段室や吹き抜け）を迂回できるルートが存在するかチェック
        SoundResult detourResult = checkDetourRoute(level, source, target, initialRadius);
        if (detourResult.reached()) {
            return detourResult;
        }

        // 2. 迂回できない、または迂回ルートが消音した場合は、直進の多重貫通減衰を評価
        return checkMultiPenetration(level, source, target, initialRadius, straightDist);
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
                        ? SoundResult.reachedDirect(straightDist, remainingRadius - (float) straightDist)
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
                ? SoundResult.reachedDirect(straightDist, remainingRadius - (float) straightDist)
                : SoundResult.blocked(lastHitLoc);
    }

    /**
     * 射線上の最初の障害物周辺から開口部（階段室・ドア）を迂回するルートを評価。
     * 開口部からターゲットまでの間にあるドア・壁も二重チェックする。
     */
    private static SoundResult checkDetourRoute(Level level, Vec3 source, Vec3 target, float initialRadius) {
        ClipContext ctx = new ClipContext(source, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, null);
        BlockHitResult hit = level.clip(ctx);

        if (hit.getType() == HitResult.Type.MISS) {
            // 直線上に障害物がないので迂回不要
            double straightDist = source.distanceTo(target);
            return SoundResult.reachedDirect(straightDist, (float) (initialRadius - straightDist));
        }

        BlockPos hitPos = hit.getBlockPos();
        Direction face = hit.getDirection();

        // 衝突点周辺（最大4マス）の開口部探索
        BlockPos opening = findNearestOpening(level, hitPos, face, 4);
        if (opening == null) {
            return SoundResult.blocked(hit.getLocation());
        }

        Vec3 openingCenter = Vec3.atCenterOf(opening);
        double distToOpening = source.distanceTo(openingCenter);
        double distFromOpeningToTarget = openingCenter.distanceTo(target);
        double totalDetourDist = distToOpening + distFromOpeningToTarget;

        // 回折ペナルティ（-4m）
        float effectiveRadius = initialRadius - DETOUR_PENALTY;
        if (totalDetourDist > effectiveRadius) {
            return SoundResult.blocked(openingCenter);
        }

        // 開口部からターゲットまでの間の二重遮蔽（部屋のドア等）をチェック
        ClipContext secondCtx = new ClipContext(openingCenter, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, null);
        BlockHitResult secondHit = level.clip(secondCtx);

        if (secondHit.getType() == HitResult.Type.BLOCK) {
            // 開口部からターゲットの間にも壁やドアが挟まっている場合、その減衰を適用
            Direction secondFace = secondHit.getDirection();
            float secondPenalty = (secondFace.getAxis() == Direction.Axis.Y) ? FLOOR_CEILING_PENALTY : WALL_PENALTY;
            effectiveRadius -= secondPenalty;

            if (totalDetourDist > effectiveRadius) {
                return SoundResult.blocked(secondHit.getLocation());
            }
        }

        return SoundResult.reachedDetour(totalDetourDist, (float) (effectiveRadius - totalDetourDist), opening);
    }

    /**
     * 衝突地点周辺で音が抜けられる開口部（空気または非衝突ブロック）を探索する。
     */
    private static BlockPos findNearestOpening(Level level, BlockPos hitPos, Direction face, int maxDist) {
        if (face.getAxis() == Direction.Axis.Y) {
            // 床または天井に衝突：水平4方向（東西南北）に開口部（階段吹き抜け、床の穴）を探索
            Direction[] horizontalDirs = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
            for (int d = 1; d <= maxDist; d++) {
                for (Direction dir : horizontalDirs) {
                    BlockPos checkPos = hitPos.relative(dir, d);
                    if (isOpening(level, checkPos)) {
                        // 上下にも空間が抜けているか確認
                        BlockPos otherSide = (face == Direction.UP) ? checkPos.above() : checkPos.below();
                        if (isOpening(level, otherSide)) {
                            return checkPos;
                        }
                    }
                }
            }
        } else {
            // 垂直な壁に衝突：壁面に沿った水平方向（左右）および上下を探索
            Direction lateral = face.getClockWise();
            for (int d = 1; d <= maxDist; d++) {
                for (int dy = -1; dy <= 1; dy++) {
                    // 右側
                    BlockPos checkPosRight = hitPos.relative(lateral, d).above(dy);
                    if (isWallOpening(level, checkPosRight, face)) {
                        return checkPosRight;
                    }

                    // 左側
                    BlockPos checkPosLeft = hitPos.relative(lateral.getOpposite(), d).above(dy);
                    if (isWallOpening(level, checkPosLeft, face)) {
                        return checkPosLeft;
                    }
                }
            }
        }

        return null;
    }

    /**
     * 指定座標が空気または衝突判定のない空間（開口部）かどうか。
     */
    private static boolean isOpening(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.isAir() || state.getCollisionShape(level, pos).isEmpty();
    }

    /**
     * 壁の開口部判定：その位置が開口部であり、かつ壁の法線方向（前後）にも抜けているか。
     */
    private static boolean isWallOpening(Level level, BlockPos pos, Direction face) {
        if (!isOpening(level, pos)) {
            return false;
        }
        // 壁の向こう側も抜けているか確認
        BlockPos throughPos = pos.relative(face.getOpposite());
        return isOpening(level, throughPos);
    }
}
