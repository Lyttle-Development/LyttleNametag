package com.lyttledev.lyttlenametag.handlers;

import com.lyttledev.lyttlenametag.LyttleNametag;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class onPlayerMoveListener implements Listener {
    private final LyttleNametag plugin;

    public onPlayerMoveListener(LyttleNametag plugin) {
        // Register the listener with the plugin's event manager
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerMove(org.bukkit.event.player.PlayerMoveEvent event) {
        // Log the player and its location
        org.bukkit.entity.Player player = event.getPlayer();
        org.bukkit.Location location = player.getLocation();
        plugin.getLogger().info("Player " + player.getName() + " moved to location: " + location.toString());
    }
}
