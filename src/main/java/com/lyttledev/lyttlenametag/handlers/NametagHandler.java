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
    private final Map<UUID, List<NametagEntity>> playerNametags = new ConcurrentHashMap<>();
    private final AtomicInteger entityIdCounter = new AtomicInteger(Integer.MAX_VALUE / 2);

    // For passenger chain: let Minecraft spacing handle most of the separation
    private static final float BASE_Y_OFFSET = 0.0f;
    private static final float LINE_HEIGHT = 0.0f;

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
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.equals(event.getPlayer())) continue;
                showNametagToPlayer(online, event.getPlayer());
            }
        }, 10L);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        removeNametag(event.getPlayer());
    }

    @EventHandler
    public void onPlayerToggleSneak(PlayerToggleSneakEvent event) {
        Player sneaking = event.getPlayer();

        if (event.isSneaking()) {
            // Hide nametag when sneaking
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                if (sneaking.equals(viewer)) continue;
                hideNametagFromPlayer(sneaking, viewer);
            }
        } else {
            // Show nametag when not sneaking
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                if (sneaking.equals(viewer)) continue;
                showNametagToPlayer(sneaking, viewer);
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

        List<NametagEntity> nametagEntities = new ArrayList<>();

        // Each line gets its own entity, but no artificial Y offset
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int entityId = entityIdCounter.decrementAndGet();

            NametagEntity nametagEntity = new NametagEntity(
                    entityId,
                    player.getEntityId(),
                    line,
                    0.0f // No offset; passenger chain will space them
            );

            nametagEntities.add(nametagEntity);
        }

        playerNametags.put(player.getUniqueId(), nametagEntities);

        // Send the nametag packets to all players except the owner
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.equals(player)) {
                showNametagToPlayer(player, viewer);
            }
        }
    }

    private void showNametagToPlayer(Player owner, Player viewer) {
        List<NametagEntity> entities = playerNametags.get(owner.getUniqueId());
        if (entities == null || entities.isEmpty()) return;

        try {
            // STEP 1: Spawn all armor stand entities AT PLAYER'S LOCATION (no Y offset)
            for (NametagEntity entity : entities) {
                org.bukkit.Location bukkit_loc = owner.getLocation().clone();

                Location pe_location = new Location(
                        bukkit_loc.getX(),
                        bukkit_loc.getY(),
                        bukkit_loc.getZ(),
                        bukkit_loc.getYaw(),
                        bukkit_loc.getPitch()
                );

                WrapperPlayServerSpawnEntity spawnPacket = new WrapperPlayServerSpawnEntity(
                        entity.getEntityId(),
                        UUID.randomUUID(),
                        EntityTypes.ARMOR_STAND,
                        pe_location,
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
            }

            // STEP 2: Chain passengers BOTTOM TO TOP so spacing is tightest
            int currentParent = owner.getEntityId();
            List<NametagEntity> reversed = new ArrayList<>(entities);
            Collections.reverse(reversed);

            for (NametagEntity entity : reversed) {
                WrapperPlayServerSetPassengers passengersPacket = new WrapperPlayServerSetPassengers(
                        currentParent,
                        new int[]{entity.getEntityId()}
                );
                PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, passengersPacket);
                currentParent = entity.getEntityId();
            }

        } catch (Exception e) {
            plugin.getLogger().warning("Error showing nametag: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void removeNametag(Player player) {
        List<NametagEntity> entities = playerNametags.remove(player.getUniqueId());
        if (entities != null) {
            int[] entityIds = entities.stream().mapToInt(NametagEntity::getEntityId).toArray();
            WrapperPlayServerDestroyEntities destroyPacket = new WrapperPlayServerDestroyEntities(entityIds);

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
        List<NametagEntity> entities = playerNametags.get(owner.getUniqueId());
        if (entities == null || entities.isEmpty()) return;

        int[] entityIds = entities.stream().mapToInt(NametagEntity::getEntityId).toArray();
        WrapperPlayServerDestroyEntities destroyPacket = new WrapperPlayServerDestroyEntities(entityIds);

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
        for (Map.Entry<UUID, List<NametagEntity>> entry : playerNametags.entrySet()) {
            int[] entityIds = entry.getValue().stream()
                    .mapToInt(NametagEntity::getEntityId)
                    .toArray();
            if (entityIds.length > 0) {
                WrapperPlayServerDestroyEntities destroyPacket = new WrapperPlayServerDestroyEntities(entityIds);
                for (Player viewer : Bukkit.getOnlinePlayers()) {
                    PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, destroyPacket);
                }
            }
        }
        playerNametags.clear();
    }

    public void reloadNametags() {
        removeAllNametagsOnShutdown();
        for (Player online : Bukkit.getOnlinePlayers()) {
            spawnNametag(online);
        }
    }

    private static class NametagEntity {
        private final int entityId;
        private final int parentEntityId;
        private final String text;
        private final float yOffset;

        public NametagEntity(int entityId, int parentEntityId, String text, float yOffset) {
            this.entityId = entityId;
            this.parentEntityId = parentEntityId;
            this.text = text;
            this.yOffset = yOffset;
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
        public float getYOffset() {
            return yOffset;
        }
    }
}