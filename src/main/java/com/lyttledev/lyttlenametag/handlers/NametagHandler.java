package com.lyttledev.lyttlenametag.handlers;

import com.lyttledev.lyttlenametag.LyttleNametag;
import com.lyttledev.lyttleutils.types.Message.Replacements;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
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
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;
import java.util.stream.Collectors;

public class NametagHandler implements Listener {
    private final LyttleNametag plugin;
    private final Map<UUID, List<TextDisplay>> playerTextDisplays = new HashMap<>();

    // Vertical spacing between lines
    private static final float LINE_HEIGHT = 0.25f;
    // Offset above head
    private static final float BASE_Y_OFFSET = 2.5f;

    public NametagHandler(LyttleNametag plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        // Periodic cleanup and reload
        new BukkitRunnable() {
            @Override
            public void run() {
                cleanupOrphans();
                reloadNametags();
            }
        }.runTaskTimer(plugin, 20 * 60, 20 * 60);
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
    public void onPlayerToggleSneak(PlayerToggleSneakEvent event) {
        Player sneaking = event.getPlayer();
        List<TextDisplay> displays = playerTextDisplays.getOrDefault(sneaking.getUniqueId(), Collections.emptyList());
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            for (TextDisplay display : displays) {
                if (sneaking.equals(viewer)) {
                    continue;
                }
                if (event.isSneaking()) {
                    viewer.hideEntity(plugin, display);
                } else {
                    viewer.showEntity(plugin, display);
                }
            }
        }
    }

    private void spawnNametag(Player player) {
        removeNametag(player);

        Location baseLoc = player.getLocation().clone().add(0, BASE_Y_OFFSET, 0);
        World world = baseLoc.getWorld();

        Replacements replacements = Replacements.builder()
            .add("<PLAYER>", player.getName())
            .add("<DISPLAYNAME>", player.displayName() != null ? player.displayName().toString() : player.getName())
            .add("<WORLD>", world.getName())
            .add("<X>", String.valueOf(player.getLocation().getBlockX()))
            .add("<Y>", String.valueOf(player.getLocation().getBlockY()))
            .add("<Z>", String.valueOf(player.getLocation().getBlockZ()))
            .build();

        Component configMessage = plugin.message.getMessage("nametag", replacements, player);
        // Serialize to legacy text then split lines
        String raw = LegacyComponentSerializer.legacySection().serialize(configMessage);
        String[] lines = raw.split("\\r?\\n");

        List<TextDisplay> displays = Arrays.stream(lines)
            .map(line -> {
                final int index = Arrays.asList(lines).indexOf(line);
                Location loc = baseLoc.clone();
                TextDisplay disp = world.spawn(loc, TextDisplay.class, entity -> {
                    entity.text(LegacyComponentSerializer.legacySection().deserialize(line));
                    entity.setBillboard(Display.Billboard.CENTER);
                    float yOffset = (LINE_HEIGHT * index) + LINE_HEIGHT;
                    entity.setTransformation(new Transformation(
                        new Vector3f(0f, yOffset, 0f),
                        new Quaternionf(0f, 0f, 0f, 1f),
                        new Vector3f(1f, 1f, 1f),
                        new Quaternionf(0f, 0f, 0f, 1f)
                    ));
                });
                disp.addScoreboardTag("nametag_display");
                disp.setPersistent(false);
                player.addPassenger(disp);
                // Initially hide owner view so they don't see their own tag
                player.hideEntity(plugin, disp);
                return disp;
            })
            .collect(Collectors.toList());

        playerTextDisplays.put(player.getUniqueId(), displays);
    }

    private void removeNametag(Player player) {
        List<TextDisplay> displays = playerTextDisplays.remove(player.getUniqueId());
        if (displays != null) {
            for (TextDisplay display : displays) {
                if (!display.isDead()) {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.showEntity(plugin, display);
                    }
                    display.remove();
                }
            }
        }
    }

    private void cleanupOrphans() {
        for (UUID uuid : new ArrayList<>(playerTextDisplays.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                List<TextDisplay> displays = playerTextDisplays.remove(uuid);
                if (displays != null) {
                    displays.forEach(d -> { if (!d.isDead()) d.remove(); });
                }
            }
        }
    }

    public void removeAllNametagsOnShutdown() {
        playerTextDisplays.forEach((uuid, displays) -> {
            Player player = Bukkit.getPlayer(uuid);
            displays.forEach(disp -> {
                if (!disp.isDead()) {
                    if (player != null && player.isOnline()) player.showEntity(plugin, disp);
                    disp.remove();
                }
            });
        });
        playerTextDisplays.clear();
    }

    public void reloadNametags() {
        // Remove all then respawn
        removeAllNametagsOnShutdown();
        for (Player online : Bukkit.getOnlinePlayers()) {
            spawnNametag(online);
        }
    }
}
