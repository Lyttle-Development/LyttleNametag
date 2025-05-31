package com.lyttledev.lyttlenametag.handlers;

import com.lyttledev.lyttlenametag.LyttleNametag;
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

import java.util.*;

public class onPlayerMoveListener implements Listener {
    private final LyttleNametag plugin;
    private final Map<UUID, List<TextDisplay>> playerTextDisplays = new HashMap<>();
    private final MiniMessage mini = MiniMessage.miniMessage();

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

        // 2) Define your colored lines using MiniMessage syntax
        List<String> lines = Arrays.asList( // TODO: make this configurable
            "<gold>OWNER</gold>",
            "<aqua>STUALYTTLE</aqua>",
            "<green>QUITE OP?</green>",
            "<red>NOICE!</red>"
        );

        // 3) Spacer between each line (in blocks)
        double spacer = 0.1;  // <— adjust this to increase/decrease vertical gap

        List<TextDisplay> displays = new ArrayList<>();

        // 4) Spawn the root TextDisplay (no visible text itself)
        TextDisplay root = world.spawn(baseLoc, TextDisplay.class, entity -> {
            entity.text(Component.text(""));
            entity.setBillboard(Display.Billboard.CENTER);
        });
        displays.add(root);

        // 5) For each line, spawn at exactly the same 'baseLoc',
        //    then immediately apply a positive Y‐translation so it sits ABOVE the root.
        for (int i = 0; i < lines.size(); i++) {
            String rawMiniMsg = lines.get(i);

            int finalI = i;
            TextDisplay lineDisplay = world.spawn(baseLoc, TextDisplay.class, entity -> {
                Component parsed = mini.deserialize(rawMiniMsg);
                entity.text(parsed);
                entity.setBillboard(Display.Billboard.CENTER);

                // Positive Y‐offset to push this line ABOVE the root
                float yTranslate = (float) (spacer * (finalI + 1));

                entity.setTransformation(
                    new Transformation(
                        new Vector3f(0f, yTranslate, 0f),    // translation UP by spacer*(i+1)
                        new Quaternionf(0f, 0f, 0f, 1f),     // no rotation
                        new Vector3f(1f, 1f, 1f),            // uniform scale
                        new Quaternionf(0f, 0f, 0f, 1f)      // pivot-rotation (identity)
                    )
                );
            });

            // Mount each line directly onto the root
            root.addPassenger(lineDisplay);
            displays.add(lineDisplay);
        }

        // 6) Finally, mount the root onto the player so all lines follow him
        player.addPassenger(root);

        // 7) Hide every TextDisplay from the player who owns them
        for (TextDisplay td : displays) {
            player.hideEntity(plugin, td);
        }

        // 8) Store references so we can remove on quit/cleanup
        playerTextDisplays.put(player.getUniqueId(), displays);
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
