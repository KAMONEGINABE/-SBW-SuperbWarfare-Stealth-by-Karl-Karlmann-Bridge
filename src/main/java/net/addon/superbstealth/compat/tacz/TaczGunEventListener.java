package net.addon.superbstealth.compat.tacz;

import com.tacz.guns.api.GunProperties;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.event.common.GunFireEvent;
import com.tacz.guns.resource.modifier.AttachmentCacheProperty;
import it.unimi.dsi.fastutil.Pair;
import net.addon.superbstealth.config.SuperbStealthConfig;
import net.addon.superbstealth.core.AcousticDispatcher;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.VanillaGameEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

// Translates TACZ gunfire events and dispatches acoustic occlusion simulations.

public class TaczGunEventListener {

    // Fallback audible distance (in blocks) matching TACZ's native default.
    // In practice, TACZ's AttachmentCacheProperty always populates GunProperties.SILENCE
    // with 64m (unsuppressed) or 16m (suppressed) upon gun draw, so this only acts as a safety guard.
    private static final float FALLBACK_SOUND_RADIUS = 64.0F;

    @SubscribeEvent
    public void onGunFire(GunFireEvent event) {
        // Acoustic simulation only runs on the logical server and for non-cancelled
        // shots
        if (!event.getLogicalSide().isServer() || event.isCanceled()) {
            return;
        }

        LivingEntity shooter = event.getShooter();
        if (shooter == null || shooter.level() == null || shooter.level().isClientSide()) {
            return;
        }

        if (!(shooter.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        Vec3 shootPos = shooter.getEyePosition();

        boolean isSuppressed = false;
        double radiusMultiplier = SuperbStealthConfig.CONFIG.taczSoundRadiusMultiplier.get();
        float soundRadiusBlocks = (float) (FALLBACK_SOUND_RADIUS * radiusMultiplier);

        // Unlike SBW, TACZ caches the final sound distance already adjusted by
        // suppressor modifiers; use the pre-computed value directly.

        IGunOperator operator = IGunOperator.fromLivingEntity(shooter);
        if (operator != null) {
            AttachmentCacheProperty cacheProperty = operator.getCacheProperty();
            if (cacheProperty != null) {
                Pair<Integer, Boolean> silence = cacheProperty.getCache(GunProperties.SILENCE.name());
                if (silence != null) {
                    Integer cachedDistance = silence.left();
                    if (cachedDistance != null && cachedDistance > 0) {
                        soundRadiusBlocks = (float) (cachedDistance * radiusMultiplier);
                    }
                    Boolean cachedSilenced = silence.right();
                    if (cachedSilenced != null) {
                        isSuppressed = cachedSilenced;
                    }
                }
            }
        }

        // Calling vanilla shooter.gameEvent() triggers hardcoded 16m radius detection
        // on all vanilla listeners.
        // Posting VanillaGameEvent directly to Forge bus allows Stealth Mod's client
        // HUD noise meter using our occlusion system.

        if (shooter instanceof Player) {
            GameEvent hudEvent = isSuppressed ? GameEvent.PROJECTILE_SHOOT : GameEvent.EXPLODE;
            MinecraftForge.EVENT_BUS
                    .post(new VanillaGameEvent(serverLevel, hudEvent, shootPos, GameEvent.Context.of(shooter)));
        }

        AcousticDispatcher.dispatchGunfire(serverLevel, shooter, shootPos, soundRadiusBlocks, isSuppressed);
    }
}
