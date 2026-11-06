package net.addon.superbstealth.compat.tacz;

import net.addon.superbstealth.SuperbStealthMod;
import net.minecraftforge.common.MinecraftForge;

/**
 * Separating registration into a dedicated class prevents the JVM classloader from prematurely
 * attempting to resolve TACZ classes (e.g. {@code GunFirePostEvent}) when TACZ is not installed.
 * This class must only be invoked after verifying {@code ModList.get().isLoaded("tacz")}.</p>
 */
public final class TaczCompat {

    private TaczCompat() {
    }

    public static void init() {
        SuperbStealthMod.LOGGER.info("Initializing TACZ gunfire acoustic integration.");
        MinecraftForge.EVENT_BUS.register(new TaczGunEventListener());
    }
}
