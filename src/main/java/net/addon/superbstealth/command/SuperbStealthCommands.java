package net.addon.superbstealth.command;

import com.mojang.brigadier.CommandDispatcher;
import net.addon.superbstealth.SuperbStealthMod;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public class SuperbStealthCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("superbstealth")
                        .then(Commands.literal("debug")
                                .executes(context -> {
                                    CommandSourceStack source = context.getSource();
                                    if (source.getEntity() instanceof ServerPlayer player) {
                                        UUID uuid = player.getUUID();
                                        boolean enabled;
                                        if (SuperbStealthMod.DEBUG_PLAYERS.contains(uuid)) {
                                            SuperbStealthMod.DEBUG_PLAYERS.remove(uuid);
                                            enabled = false;
                                        } else {
                                            SuperbStealthMod.DEBUG_PLAYERS.add(uuid);
                                            enabled = true;
                                        }

                                        Component msg = Component.literal(
                                                "§7[SuperbStealth] 音響デバッグ視覚化を " +
                                                        (enabled ? "§a§l有効§r" : "§c§l無効§r") +
                                                        " §7にしました。"
                                        );
                                        player.sendSystemMessage(msg);
                                    } else {
                                        source.sendSuccess(() -> Component.literal("プレイヤーのみ実行可能です。"), false);
                                    }
                                    return 1;
                                })
                        )
        );
    }
}
