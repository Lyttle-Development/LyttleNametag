package com.lyttledev.lyttlenametag.handlers;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.world.Location;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPassengers;

import com.lyttledev.lyttlenametag.LyttleNametag;
import com.lyttledev.lyttleutils.types.Message.Replacements;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class NametagHandler implements Listener {
    private final LyttleNametag plugin;
    private final Map<UUID, NametagEntity> playerNametags = new ConcurrentHashMap<>();
    private final AtomicInteger entityIdCounter = new AtomicInteger(Integer.MAX_VALUE / 2);

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
        // Delay creation to ensure player is fully loaded
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            spawnNametag(event.getPlayer());

            // Show existing nametags to the joining player
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                if (onlinePlayer.equals(event.getPlayer())) continue;
                showNametagToPlayer(onlinePlayer, event.getPlayer());
            }
        }, 10L);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        removeNametag(event.getPlayer());
    }

    @EventHandler
    public void onPlayerToggleSneak(PlayerToggleSneakEvent event) {
        Player sneakingPlayer = event.getPlayer();

        if (event.isSneaking()) {
            // Hide nametag when sneaking
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                if (sneakingPlayer.equals(viewer)) continue;
                hideNametagFromPlayer(sneakingPlayer, viewer);
            }
        } else {
            // Show nametag when not sneaking
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                if (sneakingPlayer.equals(viewer)) continue;
                showNametagToPlayer(sneakingPlayer, viewer);
            }
        }
    }

    private void spawnNametag(Player player) {
        removeNametag(player);

        org.bukkit.Location baseLoc = player.getLocation();
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

        NametagEntity nametagEntity = new NametagEntity(
                entityIdCounter.decrementAndGet(),
                player.getEntityId(),
                MiniMessage.miniMessage().serialize(configMessage)
        );



        playerNametags.put(player.getUniqueId(), nametagEntity);

        // Send the nametag packets to all players except the owner
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.equals(player)) {
                showNametagToPlayer(player, viewer);
            }
        }
    }

    private void showNametagToPlayer(Player owner, Player viewer) {
        NametagEntity entity = playerNametags.get(owner.getUniqueId());
        if (entity == null) return;

        try {
            // STEP 1
            org.bukkit.Location bukkit_loc = owner.getLocation().clone();

            Location location = new Location(
                    bukkit_loc.getX(),
                    bukkit_loc.getY(),
                    bukkit_loc.getZ(),
                    bukkit_loc.getYaw(),
                    bukkit_loc.getPitch()
            );

            WrapperPlayServerSpawnEntity spawnPacket = new WrapperPlayServerSpawnEntity(
                    entity.getEntityId(),
                    UUID.randomUUID(),
                    EntityTypes.TEXT_DISPLAY,
                    location,
                    0f, // yaw
                    0, // data
                    new Vector3d(0, 0, 0) // velocity
            );

            List<EntityData<?>> metadata = new ArrayList<>();
            metadata.add(new EntityData<>(0, EntityDataTypes.BYTE, (byte) 0x20)); // Invisible

            String jsonText = "{\"text\":\"" + escapeJsonString(entity.getText()) + "\"}";
            metadata.add(new EntityData<>(2, EntityDataTypes.OPTIONAL_COMPONENT, Optional.of(jsonText)));

            metadata.add(new EntityData<>(3, EntityDataTypes.BOOLEAN, true)); // Custom name visible
            metadata.add(new EntityData<>(5, EntityDataTypes.BOOLEAN, true)); // No gravity
            // ArmorStand flags: 0x10 = marker, 0x01 = small
            byte armorStandFlags = (byte) (0x01);
            metadata.add(new EntityData<>(15, EntityDataTypes.BYTE, armorStandFlags)); // Marker + small

            WrapperPlayServerEntityMetadata metadataPacket = new WrapperPlayServerEntityMetadata(
                    entity.getEntityId(),
                    metadata
            );

            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, spawnPacket);
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, metadataPacket);

            // STEP 2
            int currentParent = owner.getEntityId();

            WrapperPlayServerSetPassengers passengersPacket = new WrapperPlayServerSetPassengers(
                    currentParent,
                    new int[]{entity.getEntityId()}
            );
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, passengersPacket);

        } catch (Exception e) {
            plugin.getLogger().severe("Error showing nametag: " + e.getMessage());
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

    private String escapeJsonString(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                .replace("\b", "\\b")
                .replace("\f", "\\f");
    }

    private void hideNametagFromPlayer(Player owner, Player viewer) {
        NametagEntity entity = playerNametags.get(owner.getUniqueId());
        if (entity == null) return;

        int entityId = entity.getEntityId();
        WrapperPlayServerDestroyEntities destroyPacket = new WrapperPlayServerDestroyEntities(entityId);

        PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, destroyPacket);
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

    public void reloadNametags() {
        removeAllNametagsOnShutdown();
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            spawnNametag(onlinePlayer);
        }
    }

    private static class NametagEntity {
        private final int entityId;
        private final int parentEntityId;
        private final String text;

        public NametagEntity(int entityId, int parentEntityId, String text) {
            this.entityId = entityId;
            this.parentEntityId = parentEntityId;
            this.text = text;
        }

        public int getEntityId() {
            return entityId;
        }
        public int getParentEntityId() {
            return parentEntityId;
        }
        public String getText() {
            return text;
        }
    }
}