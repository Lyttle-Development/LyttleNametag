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
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class onPlayerMoveListener implements Listener {
    private final LyttleNametag plugin;
    private final Map<UUID, List<ArmorStand>> playerArmorStands = new HashMap<>();

    public onPlayerMoveListener(LyttleNametag plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        this.plugin = plugin;

        // Periodic cleanup task
        new BukkitRunnable() {
            @Override
            public void run() {
                cleanupOutOfRangeArmorStands();
            }
        }.runTaskTimer(plugin, 20 * 60, 20 * 30); // Run every 30 seconds after 60s delay
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        spawnNametag(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        removeNametag(event.getPlayer());
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        updateNametagLocation(player);
    }

    private void spawnNametag(Player player) {
        Location loc = player.getLocation().add(0, 2.3, 0);
        List<String> lines = Arrays.asList("OWNER", "STUALYTTLE", "QUITE OP?", "NOICE!"); // Customize lines
        List<ArmorStand> stands = new ArrayList<>();

        for (int i = 0; i < lines.size(); i++) {
            ArmorStand stand = loc.getWorld().spawn(loc.clone().add(0, -0.25 * i, 0), ArmorStand.class);
            stand.setCustomName(lines.get(i));
            stand.setCustomNameVisible(true);
            stand.setVisible(false);
            stand.setGravity(false);
            stand.setMarker(true);
            stand.setPersistent(false); // Let Bukkit remove them if needed

            stands.add(stand);
        }

        playerArmorStands.put(player.getUniqueId(), stands);
    }

    private void removeNametag(Player player) {
        List<ArmorStand> stands = playerArmorStands.remove(player.getUniqueId());
        if (stands != null) {
            for (ArmorStand stand : stands) {
                stand.remove();
            }
        }
    }

    private void updateNametagLocation(Player player) {
        List<ArmorStand> stands = playerArmorStands.get(player.getUniqueId());
        if (stands == null) return;

        Location base = player.getLocation().add(0, 2.3, 0);
        for (int i = 0; i < stands.size(); i++) {
            ArmorStand stand = stands.get(i);
            stand.teleport(base.clone().add(0, -0.25 * i, 0));
        }
    }

    private void cleanupOutOfRangeArmorStands() {
        for (UUID uuid : new ArrayList<>(playerArmorStands.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) continue;

            List<ArmorStand> stands = playerArmorStands.get(uuid);
            if (stands == null) continue;

            for (ArmorStand stand : new ArrayList<>(stands)) {
                if (stand.getLocation().distanceSquared(player.getLocation()) > 100) { // ~10 blocks
                    stand.remove();
                    stands.remove(stand);
                }
            }

            if (stands.isEmpty()) {
                playerArmorStands.remove(uuid);
            }
        }
    }

    public void removeAllNametagsOnShutdown() {
        for (List<ArmorStand> stands : playerArmorStands.values()) {
            for (ArmorStand stand : stands) {
                stand.remove();
            }
        }
        playerArmorStands.clear();
    }
}