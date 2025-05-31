package com.lyttledev.lyttlenametag.handlers;

import com.lyttledev.lyttlenametag.LyttleNametag;
import com.lyttledev.lyttleutils.types.Message.Replacements;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
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

import java.io.Console;
import java.util.*;

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

        // Split the nametag text into lines based on newline characters
        Component configMessage = plugin.message.getMessage("nametag", replacements, player);

        // 4) Spawn the root TextDisplay (no visible text itself)
        TextDisplay root = world.spawn(baseLoc, TextDisplay.class, entity -> {
            entity.text(configMessage);
            entity.setBillboard(Display.Billboard.CENTER);
        });

        // 6) Finally, mount the root onto the player so all lines follow him
        player.addPassenger(root);

        // 7) Hide every TextDisplay from the player who owns them
        player.hideEntity(plugin, root);

        // 8) Store references so we can remove on quit/cleanup
        playerTextDisplays.put(player.getUniqueId(), root);
    }

    private void removeNametag(Player player) {
        UUID uuid = player.getUniqueId();
        List<TextDisplay> stands = playerTextDisplays.remove(uuid);
        if (stands != null) {
            for (TextDisplay stand : stands) {
                if (!stand.isDead()) {
                    // In case the player never quit cleanly, un-hide and then remove
                    player.showEntity(plugin, stand);
                    stand.remove();
                }
            }
        }
    }

    private void cleanupOrphans() {
        for (UUID uuid : new ArrayList<>(playerTextDisplays.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                List<TextDisplay> orphanStands = playerTextDisplays.remove(uuid);
                if (orphanStands != null) {
                    for (TextDisplay stand : orphanStands) {
                        if (!stand.isDead()) {
                            stand.remove();
                        }
                    }
                }
            }
        }
    }

    public void removeAllNametagsOnShutdown() {
        for (Map.Entry<UUID, List<TextDisplay>> entry : playerTextDisplays.entrySet()) {
            UUID uuid = entry.getKey();
            Player player = Bukkit.getPlayer(uuid);
            for (TextDisplay stand : entry.getValue()) {
                if (!stand.isDead()) {
                    // If the player is still online, un-hide first (just in case)
                    if (player != null && player.isOnline()) {
                        player.showEntity(plugin, stand);
                    }
                    stand.remove();
                }
            }
        }
        playerTextDisplays.clear();
    }
}
