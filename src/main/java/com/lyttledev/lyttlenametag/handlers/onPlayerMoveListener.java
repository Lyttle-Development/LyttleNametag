package com.lyttledev.lyttlenametag.handlers;

import com.lyttledev.lyttlenametag.LyttleNametag;
import com.lyttledev.lyttleutils.types.Message.Replacements;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class onPlayerMoveListener implements Listener {
    private final LyttleNametag plugin;
    private final Map<UUID, TextDisplay> playerTextDisplays = new HashMap<>();

    public onPlayerMoveListener(LyttleNametag plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        // Periodic cleanup task for orphaned TextDisplays
        new BukkitRunnable() {
            @Override
            public void run() {
                cleanupOrphans();
            }
        }.runTaskTimer(plugin, 20 * 60, 20 * 60); // first run at 60s, then every 60s
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        spawnNametag(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        removeNametag(event.getPlayer());
    }

    private void spawnNametag(Player player) {
        // 1) Compute a base location above the player's head
        Location baseLoc = player.getLocation().clone().add(0, 2.5, 0);
        World world = baseLoc.getWorld();

        Replacements replacements = Replacements.builder()
            .add("<PLAYER>", player.getName())
            .add("<DISPLAYNAME>", player.displayName() != null ? player.displayName().toString() : player.getName())
            .add("<WORLD>", world.getName())
            .add("<X>", String.valueOf(player.getLocation().getBlockX()))
            .add("<Y>", String.valueOf(player.getLocation().getBlockY()))
            .add("<Z>", String.valueOf(player.getLocation().getBlockZ()))
            .build();

        // Build the Component from your plugin's configuration
        Component configMessage = plugin.message.getMessage("nametag", replacements, player);

        // 4) Spawn a single TextDisplay
        TextDisplay display = world.spawn(baseLoc, TextDisplay.class, entity -> {
            entity.text(configMessage);
            entity.setBillboard(Display.Billboard.CENTER);

            // Positive Y‐offset to push this line ABOVE the root
            float yTranslate = (float) 0.25;

            entity.setTransformation(
                new Transformation(
                    new Vector3f(0f, yTranslate, 0f),       // translation UP by spacer
                    new Quaternionf(0f, 0f, 0f, 1f),  // no rotation
                    new Vector3f(1f, 1f, 1f),            // uniform scale
                    new Quaternionf(0f, 0f, 0f, 1f)   // pivot-rotation (identity)
                )
            );
        });

        // 6) Mount the display onto the player so it follows them
        player.addPassenger(display);

        // 7) Hide this display from the player who owns it
        player.hideEntity(plugin, display);

        // 8) Store the reference so we can remove it later
        playerTextDisplays.put(player.getUniqueId(), display);
    }

    private void removeNametag(Player player) {
        UUID uuid = player.getUniqueId();
        TextDisplay display = playerTextDisplays.remove(uuid);

        if (display != null && !display.isDead()) {
            // If for some reason the player never quit cleanly, un-hide then remove
            player.showEntity(plugin, display);
            display.remove();
        }
    }

    private void cleanupOrphans() {
        // Iterate over a copy of the keySet to avoid ConcurrentModificationException
        for (UUID uuid : new ArrayList<>(playerTextDisplays.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                TextDisplay orphan = playerTextDisplays.remove(uuid);
                if (orphan != null && !orphan.isDead()) {
                    orphan.remove();
                }
            }
        }
    }

    public void removeAllNametagsOnShutdown() {
        for (Map.Entry<UUID, TextDisplay> entry : playerTextDisplays.entrySet()) {
            UUID uuid = entry.getKey();
            Player player = Bukkit.getPlayer(uuid);
            TextDisplay display = entry.getValue();

            if (display != null && !display.isDead()) {
                // If the player is still online, un-hide first (just in case)
                if (player != null && player.isOnline()) {
                    player.showEntity(plugin, display);
                }
                display.remove();
            }
        }
        playerTextDisplays.clear();
    }
}
