package net.addon.superbstealth;

import net.addon.superbstealth.command.SuperbStealthCommands;
import net.addon.superbstealth.event.GunShootEventListener;
import net.addon.superbstealth.util.DebugParticleManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
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

    // デバッグ視覚化が有効なプレイヤーUUIDのセット
    public static final Set<UUID> DEBUG_PLAYERS = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public SuperbStealthMod(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();
        MinecraftForge.EVENT_BUS.register(new GunShootEventListener());
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
