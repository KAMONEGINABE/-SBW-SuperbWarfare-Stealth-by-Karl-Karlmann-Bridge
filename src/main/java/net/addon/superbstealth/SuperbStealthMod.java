package net.addon.superbstealth;

import net.addon.superbstealth.command.SuperbStealthCommands;
import net.addon.superbstealth.compat.tacz.TaczCompat;
import net.addon.superbstealth.config.SuperbStealthConfig;
import net.addon.superbstealth.event.GunShootEventListener;
import net.addon.superbstealth.core.DebugParticleManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod(SuperbStealthMod.MOD_ID)
public class SuperbStealthMod {
    public static final String MOD_ID = "superbstealth";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final Set<UUID> DEBUG_PLAYERS = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public SuperbStealthMod(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();
        context.registerConfig(ModConfig.Type.COMMON, SuperbStealthConfig.SPEC, "superbstealth-common.toml");
        MinecraftForge.EVENT_BUS.register(new GunShootEventListener());

        if (ModList.get().isLoaded("tacz")) {
            TaczCompat.init();
        } else {
            LOGGER.info("TACZ is not loaded. Skipping TACZ acoustic listener registration.");
        }

        MinecraftForge.EVENT_BUS.register(this);
        LOGGER.info("SuperbWarfare Stealth Bridge initialized.");
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        SuperbStealthCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            DebugParticleManager.tick();
        }
    }
}
