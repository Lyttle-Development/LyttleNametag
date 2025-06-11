package com.lyttledev.lyttlenametag.handlers;

import com.lyttledev.lyttlenametag.LyttleNametag;
import com.lyttledev.lyttlenametag.utils.FakeMountHandler;
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
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class NametagHandler implements Listener {
    private final LyttleNametag plugin;
    private final Map<UUID, List<TextDisplay>> playerTextDisplays = new HashMap<>();
    private final FakeMountHandler fakeMountHandler;

    private static final float LINE_HEIGHT = 0.25f;
    private static final float BASE_Y_OFFSET = 2.5f;

    public NametagHandler(LyttleNametag plugin) {
        this.plugin = plugin;
        this.fakeMountHandler = new FakeMountHandler(plugin);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        // Periodic cleanup and reload
        new BukkitRunnable() {
            @Override
            public void run() {
                cleanupOrphans();
                reloadNametags();
            }
        }.runTaskTimer(plugin, 20 * 60, 20 * 60);

        // Keep displays following and remounting
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    List<TextDisplay> displays = playerTextDisplays.get(p.getUniqueId());
                    if (displays == null) continue;
                    Location base = p.getLocation().add(0, BASE_Y_OFFSET, 0);
                    for (int i = 0; i < displays.size(); i++) {
                        TextDisplay disp = displays.get(i);
                        if (disp.isDead()) continue;
                        float yOff = LINE_HEIGHT * (i + 1);
                        disp.teleport(base.clone().add(0, yOff, 0));
                        // resend mount packet to each viewer
                        for (Player viewer : Bukkit.getOnlinePlayers()) {
                            fakeMountHandler.sendFakeMount(viewer, displays);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        spawnNametag(e.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        removeNametag(e.getPlayer());
    }

    @EventHandler
    public void onPlayerToggleSneak(PlayerToggleSneakEvent e) {
        Player sneaking = e.getPlayer();
        List<TextDisplay> displays = playerTextDisplays.getOrDefault(sneaking.getUniqueId(), Collections.emptyList());
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            for (TextDisplay disp : displays) {
                if (sneaking.equals(viewer)) continue;
                if (e.isSneaking()) viewer.hideEntity(plugin, disp);
                else viewer.showEntity(plugin, disp);
            }
        }
    }

    private void spawnNametag(Player player) {
        removeNametag(player);
        Location baseLoc = player.getLocation().clone().add(0, BASE_Y_OFFSET, 0);
        World world = baseLoc.getWorld();

        Replacements repl = Replacements.builder()
            .add("<PLAYER>", player.getName())
            .add("<DISPLAYNAME>", player.displayName().toString())
            .add("<WORLD>", world.getName())
            .add("<X>", String.valueOf(player.getLocation().getBlockX()))
            .add("<Y>", String.valueOf(player.getLocation().getBlockY()))
            .add("<Z>", String.valueOf(player.getLocation().getBlockZ()))
            .build();

        Component msg = plugin.message.getMessage("nametag", repl, player);
        String[] lines = LegacyComponentSerializer.legacySection()
                            .serialize(msg)
                            .split("\\r?\\n");

        List<TextDisplay> displays = Arrays.stream(lines)
            .map(line -> {
                int idx = Arrays.asList(lines).indexOf(line);
                Location loc = baseLoc.clone();
                TextDisplay disp = world.spawn(loc, TextDisplay.class, ent -> {
                    ent.text(LegacyComponentSerializer.legacySection().deserialize(line));
                    ent.setBillboard(Display.Billboard.CENTER);
                    ent.setTransformation(new Transformation(
                        new Vector3f(0f, LINE_HEIGHT * (idx + 1), 0f),
                        new Quaternionf(),
                        new Vector3f(1f,1f,1f),
                        new Quaternionf()
                    ));
                });
                disp.addScoreboardTag("nametag_display");
                disp.setPersistent(false);
                playerTextDisplays.computeIfAbsent(player.getUniqueId(), u -> new ArrayList<>()).add(disp);
                // hide self-view
                player.hideEntity(plugin, disp);
                return disp;
            }).collect(Collectors.toList());

        playerTextDisplays.put(player.getUniqueId(), displays);
        fakeMountHandler.sendFakeMount(player, displays);
    }

    private void removeNametag(Player player) {
        List<TextDisplay> list = playerTextDisplays.remove(player.getUniqueId());
        if (list != null) {
            for (TextDisplay disp : list) {
                if (!disp.isDead()) {
                    for (Player p : Bukkit.getOnlinePlayers()) p.showEntity(plugin, disp);
                    disp.remove();
                }
            }
        }
    }

    private void cleanupOrphans() {
        for (UUID id : new ArrayList<>(playerTextDisplays.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.isOnline()) {
                removeNametag(p != null ? p : Bukkit.getOfflinePlayer(id).getPlayer());
            }
        }
    }

    public void removeAllNametagsOnShutdown() {
        playerTextDisplays.keySet().forEach(id -> {
            Player p = Bukkit.getPlayer(id);
            removeNametag(p);
        });
    }

    public void reloadNametags() {
        removeAllNametagsOnShutdown();
        Bukkit.getOnlinePlayers().forEach(this::spawnNametag);
    }
}