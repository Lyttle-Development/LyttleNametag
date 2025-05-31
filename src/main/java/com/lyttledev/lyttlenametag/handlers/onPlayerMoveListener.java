package com.lyttledev.lyttlenametag.handlers;

import com.lyttledev.lyttlenametag.LyttleNametag;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class onPlayerMoveListener implements Listener {
    private final LyttleNametag plugin;
    private final Map<UUID, List<ArmorStand>> playerArmorStands = new HashMap<>();

    public onPlayerMoveListener(LyttleNametag plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        this.plugin = plugin;

        // Periodic cleanup task: remove any orphaned armor stands (e.g., due to crashes)
        new BukkitRunnable() {
            @Override
            public void run() {
                cleanupOrphans();
            }
        }.runTaskTimer(plugin, 20 * 60, 20 * 60); // first run after 60s, then every 60s
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        spawnNametag(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        removeNametag(event.getPlayer());
    }

    // We no longer teleport on move—armor stands are directly mounted to the player.

    private void spawnNametag(Player player) {
        // Base location (just to have a spawn point; the passenger mounting handles positioning afterward)
        Location baseLoc = player.getLocation().clone().add(0, 2.3, 0);
        World world = baseLoc.getWorld();

        // Define multi-line nametag content
        List<String> lines = Arrays.asList(
            "OWNER",
            "STUALYTTLE",
            "QUITE OP?",
            "NOICE!"
        );

        List<ArmorStand> stands = new ArrayList<>();

        // 1) Spawn a root armor stand that will be mounted on the player
        ArmorStand root = world.spawn(baseLoc, ArmorStand.class);
        root.setVisible(false);
        root.setGravity(false);
        root.setMarker(true);
        root.setCustomNameVisible(false);
        root.setPersistent(false); // so it won't persist if server crashes
        stands.add(root);

        // 2) For each line, spawn an armor stand at a downward offset and mount it as a passenger on the previous stand
        ArmorStand previous = root;
        double yOffsetPerLine = 0.25; // vertical spacing between lines

        for (int i = 0; i < lines.size(); i++) {
            String lineText = lines.get(i);

            // Spawn each line slightly below the root, using yOffsetPerLine * (i + 1)
            Location lineLoc = baseLoc.clone().add(0, -yOffsetPerLine * (i + 1), 0);
            ArmorStand lineStand = world.spawn(lineLoc, ArmorStand.class);

            lineStand.setCustomName(lineText);
            lineStand.setCustomNameVisible(true);
            lineStand.setVisible(false);
            lineStand.setGravity(false);
            lineStand.setMarker(true);
            lineStand.setPersistent(false);

            // Stack it as a passenger of the previous stand
            previous.addPassenger(lineStand);
            stands.add(lineStand);

            // Next line will stack on this one
            previous = lineStand;
        }

        // 3) Finally, mount the root stand onto the player so that ALL lines move exactly with the player
        player.addPassenger(root);

        // Save references so we can remove later
        playerArmorStands.put(player.getUniqueId(), stands);
    }

    private void removeNametag(Player player) {
        UUID uuid = player.getUniqueId();
        List<ArmorStand> stands = playerArmorStands.remove(uuid);
        if (stands != null) {
            for (ArmorStand stand : stands) {
                if (!stand.isDead()) {
                    stand.remove();
                }
            }
        }
    }

    /**
     * Periodic cleanup of any armor stand that has no valid mounted player.
     * This handles cases like server crashes or plugin reloads where armor stands might remain.
     */
    private void cleanupOrphans() {
        // Iterate over all saved UID keys first
        for (UUID uuid : new ArrayList<>(playerArmorStands.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            // If player is offline or null, remove all their stands immediately
            if (player == null || !player.isOnline()) {
                List<ArmorStand> orphanStands = playerArmorStands.remove(uuid);
                if (orphanStands != null) {
                    for (ArmorStand stand : orphanStands) {
                        if (!stand.isDead()) {
                            stand.remove();
                        }
                    }
                }
            }
        }

        // Additionally, scan the world(s) for any lingering armor stands that have our tag/marker but no passenger relation.
        // This extra check ensures that if the plugin rebooted and lost its in-memory map, we still clear old stands.
        for (World world : Bukkit.getWorlds()) {
            for (ArmorStand stand : world.getEntitiesByClass(ArmorStand.class)) {
                // Check for our marker properties:
                if (stand.isMarker() && !stand.isCustomNameVisible()) {
                    // If it's mounted on a player, ignore it; otherwise, remove it.
                    if (stand.getPassengers().isEmpty() && !(stand.getVehicle() instanceof Player)) {
                        stand.remove();
                    }
                }
                // Also clear any line-stands left behind without a valid root
                if (stand.isMarker() && stand.isCustomNameVisible()) {
                    // A named marker stand should be a passenger of another armor stand. If it has no vehicle, it's orphaned.
                    if (stand.getVehicle() == null) {
                        stand.remove();
                    }
                }
            }
        }
    }

    /**
     * Call this from your plugin's onDisable() to ensure all armor stands are removed on shutdown.
     */
    public void removeAllNametagsOnShutdown() {
        for (List<ArmorStand> stands : playerArmorStands.values()) {
            for (ArmorStand stand : stands) {
                if (!stand.isDead()) {
                    stand.remove();
                }
            }
        }
        playerArmorStands.clear();
    }
}
