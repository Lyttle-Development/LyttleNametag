package com.lyttledev.lyttlenametag.handlers;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.world.Location;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPassengers;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import com.lyttledev.lyttlenametag.LyttleNametag;
import com.lyttledev.lyttleutils.types.Message.Replacements;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class NametagHandler implements Listener {
    private final LyttleNametag plugin;
    private final Map<UUID, NametagEntity> playerNametags = new ConcurrentHashMap<>();
    private final AtomicInteger entityIdCounter = new AtomicInteger(Integer.MAX_VALUE / 2);
    private BukkitTask timer;
    private BukkitTask hardReloadTimer;
    private final double nametagSpawnHeight = 1.8; // Height above player's head for nametag

    public NametagHandler(LyttleNametag plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        startTimer();
        startHardReloadTimer();
    }

    public void reload() {
        startTimer();
        reloadNametags();
        startHardReloadTimer();
    }

    private void startTimer() {
        if (timer != null) {
            timer.cancel();
        }

        double interval = (double) plugin.config.general.get("interval");

        // Periodic cleanup and update
        this.timer = new BukkitRunnable() {
            @Override
            public void run() {
                cleanupOrphans();
                updateNametagTexts();
            }
        }.runTaskTimer(plugin, 0, Math.round(interval * 20));
    }

    private void startHardReloadTimer() {
        if (hardReloadTimer != null) {
            hardReloadTimer.cancel();
        }
        // Hard reload every 60 seconds (1 minute)
        this.hardReloadTimer = new BukkitRunnable() {
            @Override
            public void run() {
                reloadNametags();
            }
        }.runTaskTimer(plugin, 0, 20 * 60); // 20 ticks per second * 60 seconds
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            spawnNametag(event.getPlayer());
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                if (onlinePlayer.equals(event.getPlayer())) continue;
                showNametagToPlayer(onlinePlayer, event.getPlayer());
            }
        }, 10L);
        // Hard reload on join
        reloadTimeOut();
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        removeNametag(event.getPlayer());
        // Hard reload on quit
        reloadTimeOut();
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        // Hard reload on death (after death event to ensure state is correct)
        reloadTimeOut();
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        // Hard reload on respawn (after respawn so location is correct)
        reloadTimeOut();
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        // Hard reload on world change
        reloadTimeOut();
    }

    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        // Hard reload on teleport (after teleport)
        reloadTimeOut();
    }

    @EventHandler
    public void onPlayerGameModeChange(PlayerGameModeChangeEvent event) {
        // Hard reload on game mode change (after change)
        reloadTimeOut();
    }

    private void reloadTimeOut() {
        // Run the reloadNametags method after a delay
        Bukkit.getScheduler().runTaskLater(plugin, this::reloadNametags, 20L); // 20 ticks = 1 second
    }

    private void spawnNametag(Player player) {
        removeNametag(player);

        org.bukkit.Location baseLoc = player.getLocation().clone();
        baseLoc.setY(baseLoc.getY() + nametagSpawnHeight);
        World world = baseLoc.getWorld();

        Replacements replacements = Replacements.builder()
                .add("<PLAYER>", player.getName())
                .add("<DISPLAYNAME>", player.displayName() != null ? player.displayName().toString() : player.getName())
                .add("<WORLD>", world.getName())
                .add("<X>", String.valueOf(baseLoc.getBlockX()))
                .add("<Y>", String.valueOf(baseLoc.getBlockY()))
                .add("<Z>", String.valueOf(baseLoc.getBlockZ()))
                .build();

        String nametag = (String) plugin.config.general.get("nametag");
        Component message = plugin.message.getMessageRaw(nametag, replacements, player);

        NametagEntity nametagEntity = new NametagEntity(
                entityIdCounter.decrementAndGet(),
                message
        );

        playerNametags.put(player.getUniqueId(), nametagEntity);

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.equals(player)) {
                showNametagToPlayer(player, viewer);
            }
        }
    }

    private void showNametagToPlayer(Player owner, Player viewer) {
        NametagEntity entity = playerNametags.get(owner.getUniqueId());
        if (entity == null) return;
        int ownerId = owner.getEntityId();
        int entityId = entity.getEntityId();

        try {
            org.bukkit.Location bukkit_location = owner.getLocation().clone();
            bukkit_location.setY(bukkit_location.getY() + nametagSpawnHeight);

            Location packetevents_location = new Location(
                    bukkit_location.getX(),
                    bukkit_location.getY(),
                    bukkit_location.getZ(),
                    bukkit_location.getYaw(),
                    bukkit_location.getPitch()
            );

            // Create the spawn packet for the text display entity
            WrapperPlayServerSpawnEntity spawnPacket = new WrapperPlayServerSpawnEntity(
                    entityId,
                    UUID.randomUUID(),
                    EntityTypes.TEXT_DISPLAY,
                    packetevents_location,
                    0f, // yaw
                    0, // data
                    new Vector3d(0, 0, 0) // velocity
            );

            // Add metadata to the Text Display, see protocol info here: https://minecraft.wiki/w/Java_Edition_protocol/Entity_metadata#Text_Display
            List<EntityData<?>> metadata = new ArrayList<>();

            // Center the nametag above the player's head and let it follow the surrounding players.
            metadata.add(new EntityData<>(15, EntityDataTypes.BYTE, (byte) 0x03));

            float defaultViewDistance = 1.0f; // default (1 unit) equals 64 blocks
            // Tested: Default is 80 for our metadata.
            // Default Minecraft client nametag distance is 64 blocks.
            float blocksPerDefault = 80.0f;
            float oneBlockViewDistance = defaultViewDistance / blocksPerDefault;

            int blocksConfig = (int) plugin.config.general.get("view_distance");
            int blocks = blocksConfig > 0 ? blocksConfig : 64; // Default to 64 blocks if not set

            metadata.add(new EntityData<>(17, EntityDataTypes.FLOAT, oneBlockViewDistance * blocks));

            // Set the text content of the text display entity
            Component text = entity.getText();
            metadata.add(new EntityData<>(23, EntityDataTypes.ADV_COMPONENT, text));

            // Set background color to fully transparent
            int backgroundOpacityConfig = (int) plugin.config.general.get("background_opacity");
            int backgroundOpacity = backgroundOpacityConfig >= 0 && backgroundOpacityConfig <= 255 ? backgroundOpacityConfig : 80;
            metadata.add(new EntityData<>(25, EntityDataTypes.INT, backgroundOpacity));

            WrapperPlayServerEntityMetadata metadataPacket = new WrapperPlayServerEntityMetadata(entityId, metadata);

            WrapperPlayServerSetPassengers passengersPacket = new WrapperPlayServerSetPassengers(ownerId, new int[]{entityId});

            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, spawnPacket);
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, metadataPacket);
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, passengersPacket);
        } catch (Exception e) {
            plugin.getLogger().severe("Error showing nametag: " + e.getMessage());
        }
    }

    private void updateNametagTexts() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            NametagEntity entity = playerNametags.get(player.getUniqueId());
            if (entity == null) continue;

            org.bukkit.Location baseLoc = player.getLocation().clone();
            baseLoc.setY(baseLoc.getY() + nametagSpawnHeight);
            World world = baseLoc.getWorld();

            Replacements replacements = Replacements.builder()
                    .add("<PLAYER>", player.getName())
                    .add("<DISPLAYNAME>", player.displayName() != null ? player.displayName().toString() : player.getName())
                    .add("<WORLD>", world.getName())
                    .add("<X>", String.valueOf(baseLoc.getBlockX()))
                    .add("<Y>", String.valueOf(baseLoc.getBlockY()))
                    .add("<Z>", String.valueOf(baseLoc.getBlockZ()))
                    .build();

            String nametag = (String) plugin.config.general.get("nametag");
            Component message = plugin.message.getMessageRaw(nametag, replacements, player);

            if (!entity.getText().equals(message)) {
                entity.setText(message);
                sendNametagTextUpdate(player, entity);
            }
        }
    }

    private void sendNametagTextUpdate(Player owner, NametagEntity entity) {
        int entityId = entity.getEntityId();
        List<EntityData<?>> metadata = new ArrayList<>();
        Component text = entity.getText();
        text = text.appendNewline();
        metadata.add(new EntityData<>(23, EntityDataTypes.ADV_COMPONENT, text));
        WrapperPlayServerEntityMetadata metadataPacket = new WrapperPlayServerEntityMetadata(entityId, metadata);

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.equals(owner)) {
                PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, metadataPacket);
            }
        }
    }

    private void removeNametag(Player player) {
        NametagEntity entity = playerNametags.remove(player.getUniqueId());
        if (entity != null) {
            int entityId = entity.getEntityId();
            WrapperPlayServerDestroyEntities destroyPacket = new WrapperPlayServerDestroyEntities(entityId);

            for (Player viewer : Bukkit.getOnlinePlayers()) {
                PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, destroyPacket);
            }
        }
    }

    private void cleanupOrphans() {
        for (UUID uuid : new ArrayList<>(playerNametags.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                playerNametags.remove(uuid);
            }
        }
    }

    public void removeAllNametagsOnShutdown() {
        for (Map.Entry<UUID, NametagEntity> entry : playerNametags.entrySet()) {
            int entityId = entry.getValue().getEntityId();
            WrapperPlayServerDestroyEntities destroyPacket = new WrapperPlayServerDestroyEntities(entityId);
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, destroyPacket);
            }
        }
        playerNametags.clear();
    }

    private void reloadNametags() {
        removeAllNametagsOnShutdown();
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            spawnNametag(onlinePlayer);
        }
    }

    public static class NametagEntity {
        private final int entityId;
        private Component text;

        public NametagEntity(int entityId, Component text) {
            this.entityId = entityId;
            this.text = text;
        }

        public int getEntityId() {
            return entityId;
        }
        public Component getText() {
            return text;
        }
        public void setText(Component text) {
            this.text = text;
        }
    }
}