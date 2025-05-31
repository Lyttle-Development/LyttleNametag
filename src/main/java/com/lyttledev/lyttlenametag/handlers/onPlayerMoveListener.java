package com.lyttledev.lyttlenametag.handlers;

import com.lyttledev.lyttlenametag.LyttleNametag;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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

import java.util.*;

public class onPlayerMoveListener implements Listener {
    private final LyttleNametag plugin;
    private final Map<UUID, List<TextDisplay>> playerTextDisplays = new HashMap<>();

    public onPlayerMoveListener(LyttleNametag plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        // Periodic cleanup task: remove any orphaned TextDisplays (e.g., due to crashes or reloads)
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

    private void spawnNametag(Player player) {
        // Base location: spawn point for the root TextDisplay (adjust Y as needed)
        Location baseLoc = player.getLocation().clone().add(0, 2.5, 0);
        World world = baseLoc.getWorld();

        // Define multi-line nametag content (use MiniMessage syntax for colors)
        List<String> lines = Arrays.asList(
            "<gold>OWNER</gold>",
            "<aqua>STUALYTTLE</aqua>",
            "<green>QUITE OP?</green>",
            "<red>NOICE!</red>"
        );

        List<TextDisplay> displays = new ArrayList<>();

        // 1) Spawn a “root” TextDisplay that will carry all the others as passengers
        TextDisplay root = world.spawn(baseLoc, TextDisplay.class, entity -> {
            // Example “default” text—won’t actually be seen once we stack the real lines below
            entity.text(Component.text("", NamedTextColor.WHITE));
            entity.setBillboard(Display.Billboard.CENTER);
            // Optionally tweak other properties on the root if desired (e.g., line width, shadow)
        });
        displays.add(root);

        // 2) Spawn each colored line underneath the root and mount it as a passenger
        TextDisplay previous = root;
        // Increase this value if they still end up overlapping. 0.3–0.4 is usually enough in TextDisplay units.
        double yOffsetPerLine = 0.3;

        for (int i = 0; i < lines.size(); i++) {
            String rawMiniMsg = lines.get(i);

            // Compute a Y offset for this line relative to the root’s spawn point
            Location lineLoc = baseLoc.clone().add(0, -yOffsetPerLine * (i + 1), 0);

            TextDisplay lineDisplay = world.spawn(lineLoc, TextDisplay.class, entity -> {
                // Parse the MiniMessage string to get a colored Component
                Component parsed = MiniMessage.miniMessage().deserialize(rawMiniMsg);
                entity.text(parsed);
                entity.setBillboard(Display.Billboard.CENTER);
                // You can also adjust entity.lineWidth(), entity.shadow(), etc., here if needed
            });

            // Mount this line under the previous one (stacking)
            previous.addPassenger(lineDisplay);
            displays.add(lineDisplay);
            previous = lineDisplay;
        }

        // 3) Finally, mount the root TextDisplay onto the player so it follows them perfectly
        player.addPassenger(root);

        // Store references so we can remove them later on quit or cleanup
        playerTextDisplays.put(player.getUniqueId(), displays);
    }

    private void removeNametag(Player player) {
        UUID uuid = player.getUniqueId();
        List<TextDisplay> stands = playerTextDisplays.remove(uuid);
        if (stands != null) {
            for (TextDisplay stand : stands) {
                if (!stand.isDead()) {
                    stand.remove();
                }
            }
        }
    }

    /**
     * Remove any orphaned TextDisplays for players who are no longer online.
     * (Useful if the server crashed or the plugin was reloaded.)
     */
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

    /**
     * Call this from your plugin’s onDisable() to clean up everything on shutdown.
     */
    public void removeAllNametagsOnShutdown() {
        for (List<TextDisplay> stands : playerTextDisplays.values()) {
            for (TextDisplay stand : stands) {
                if (!stand.isDead()) {
                    stand.remove();
                }
            }
        }
        playerTextDisplays.clear();
    }
}
