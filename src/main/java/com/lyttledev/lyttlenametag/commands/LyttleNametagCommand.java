package com.lyttledev.lyttlenametag.commands;

import com.lyttledev.lyttlenametag.LyttleNametag;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;

public class LyttleNametagCommand implements CommandExecutor, TabCompleter {
    private final LyttleNametag plugin;

    public LyttleNametagCommand(LyttleNametag plugin) {
        plugin.getCommand("lyttlenametag").setExecutor(this);
        this.plugin = plugin;
    }

    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        // Check for permission
        if (!(sender.hasPermission("lyttlenametag.lyttlenametag"))) {
            plugin.message.sendMessage(sender, "no_permission");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("Plugin version: " + plugin.getDescription().getVersion());
        }

        if (args.length == 1) {
            if (args[0].equalsIgnoreCase("reload")) {
                plugin.config.reload();
                plugin.message.sendMessageRaw(sender, MiniMessage.miniMessage().deserialize("The config has been reloaded"));
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1) {
            return List.of("reload");
        }

        return List.of();
    }
}
