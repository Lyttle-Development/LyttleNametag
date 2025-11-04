package com.lyttledev.lyttlenametag.commands;

import com.lyttledev.lyttlenametag.LyttleNametag;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;

public class LyttleNametagCommand {
    private static LyttleNametag plugin;

    public static void createCommand(LyttleNametag lyttlePlugin, Commands commands) {
        plugin = lyttlePlugin;

        // Define the different nodes
        LiteralArgumentBuilder<CommandSourceStack> top = Commands.literal("lyttlenametag")
                .then(Commands.literal("reload")
                        .requires(source -> source.getSender().hasPermission("lyttlenametag.lyttlenametag.reload"))
                        .executes(LyttleNametagCommand::reloadNode));

        // Defines root node functions
        top.requires(source -> source.getSender().hasPermission("lyttlenametag.lyttlenametag"));
        top.executes(LyttleNametagCommand::rootNode);

        // Finish the command
        commands.register(
                top.build(),
                "Admin command for the LyttleNametag plugin"
        );
    }

    private static int rootNode(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        Component version = Component.text("Plugin version: " + plugin.getDescription().getVersion());
        plugin.message.sendMessageRaw(sender, version);
        return Command.SINGLE_SUCCESS;
    }

    private static int reloadNode(CommandContext<CommandSourceStack> context) {
        final CommandSender sender = context.getSource().getSender();
        plugin.config.reload();
        plugin.message.sendMessageRaw(sender, Component.text("The config has been reloaded"));
        return Command.SINGLE_SUCCESS;
    }
}
