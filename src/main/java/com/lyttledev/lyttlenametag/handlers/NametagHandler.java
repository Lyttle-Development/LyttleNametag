package com.lyttledev.lyttlenametag.handlers;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.world.Location;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3f;
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
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
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
    private BukkitTask visibilityTimer; // Fast, lightweight visibility enforcement (vanish/world) without flicker
    private final Map<UUID, Map<UUID, Boolean>> viewerVisibility = new ConcurrentHashMap<>(); // viewer -> (owner -> visible)
    private final double nametagSpawnHeight = 1.8; // Height above player's head for nametag

    public NametagHandler(LyttleNametag plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        startTimer();
        startHardReloadTimer();
        startVisibilityTimer();
    }

    public void reload() {
        startTimer();
        reloadNametags();
        startHardReloadTimer();
        startVisibilityTimer();
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
                // Enforce viewer visibility each cycle to catch vanish changes even if lines didn't change
                enforceVisibilityMatrix();
                updateNametagTexts();
            }
        }.runTaskTimer(plugin, 0, Math.round(interval * 20));
    }

    private void startHardReloadTimer() {
        if (hardReloadTimer != null) {
            hardReloadTimer.cancel();
        }
        // Soft refresh every 60 seconds (1 minute) without destroying/spawning to avoid flicker
        this.hardReloadTimer = new BukkitRunnable() {
            @Override
            public void run() {
                softRefreshNametags();
            }
        }.runTaskTimer(plugin, 0, 20 * 60); // 20 ticks per second * 60 seconds
    }

    // Fast visibility enforcement loop (every 5 ticks) to react quickly to vanish/unvanish and cross-world moves
    private void startVisibilityTimer() {
        if (visibilityTimer != null) {
            visibilityTimer.cancel();
        }
        this.visibilityTimer = new BukkitRunnable() {
            @Override
            public void run() {
                enforceVisibilityMatrix();
            }
        }.runTaskTimer(plugin, 0L, 5L); // ~0.25s
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            spawnNametag(event.getPlayer());
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                if (onlinePlayer.equals(event.getPlayer())) continue;
                // Only show nametags of players in the same world as the joining player and that the viewer can see
                if (!sameWorld(onlinePlayer, event.getPlayer()) || !event.getPlayer().canSee(onlinePlayer)) {
                    NametagEntity e = playerNametags.get(onlinePlayer.getUniqueId());
                    if (e != null) {
                        sendDestroyToViewer(event.getPlayer(), e.getEntityIds());
                    }
                    // Update cache as hidden
                    setLastVisibility(event.getPlayer(), onlinePlayer, false);
                    continue;
                }
                showNametagToPlayer(onlinePlayer, event.getPlayer());
                // Update cache as visible
                setLastVisibility(event.getPlayer(), onlinePlayer, true);
            }
        }, 10L);
        // Hard reload on join
        reloadTimeOut();
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Clear this player as a viewer from the visibility cache
        clearViewerFromVisibilityCache(event.getPlayer().getUniqueId());
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

    // Instant, no-delay update on toggle sneak to avoid flicker and slowness
    @EventHandler
    public void onPlayerToggleSneak(PlayerToggleSneakEvent event) {
        // Update nametag in-place (metadata only), do NOT trigger reload/destroy/spawn
        updateOwnerVisibilityNametag(event.getPlayer());
        // Also enforce visibility immediately (in case vanish plugins interact with sneak)
        enforceVisibilityMatrix();
    }

    // Instant update when invisibility potion is applied/removed
    @EventHandler
    public void onEntityPotionEffect(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        if (event.getModifiedType() != PotionEffectType.INVISIBILITY) return;
        Player player = (Player) event.getEntity();
        Bukkit.getScheduler().runTask(plugin, () -> {
            updateOwnerVisibilityNametag(player);
            enforceVisibilityMatrix();
        });
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

        // Render the nametag template into separate lines and chain them bottom-up (each line rides the previous one).
        // NOTE: We always allocate the full template line count to avoid re-spawn flicker on visibility toggles.
        String nametagTemplate = (String) plugin.config.general.get("nametag");
        List<Component> linesBottomUp = renderLinesBottomUp(nametagTemplate, replacements, player);

        // Create entity IDs for each line (one Text Display per line)
        List<Integer> entityIds = new ArrayList<>(linesBottomUp.size());
        for (int i = 0; i < linesBottomUp.size(); i++) {
            entityIds.add(entityIdCounter.decrementAndGet());
        }

        NametagEntity nametagEntity = new NametagEntity(
                entityIds,
                linesBottomUp
        );

        playerNametags.put(player.getUniqueId(), nametagEntity);

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.equals(player)) {
                // Only show to viewers in the same world who can see the player (not vanished for them)
                if (!shouldHideForViewer(player, viewer)) {
                    showNametagToPlayer(player, viewer);
                    setLastVisibility(viewer, player, true);
                } else {
                    // Ensure it's hidden for non-eligible viewers
                    sendDestroyToViewer(viewer, nametagEntity.getEntityIds());
                    setLastVisibility(viewer, player, false);
                }
            }
        }

        // If the player is currently globally hidden (sneak/invis), immediately hide by swapping text to empty (no respawn).
        if (isGloballyHidden(player)) {
            updateOwnerVisibilityNametag(player);
        }
    }

    private List<Component> renderLinesBottomUp(String nametagTemplate, Replacements replacements, Player player) {
        // Split by newline, preserve trailing empty lines, then render each line separately
        String[] rawLines = nametagTemplate.split("\\R", -1);
        List<Component> topDown = new ArrayList<>(rawLines.length);
        for (String rawLine : rawLines) {
            Component lineComponent = plugin.message.getMessageRaw(rawLine, replacements, player);
            topDown.add(lineComponent);
        }
        // Reverse to bottom-up so the last (often empty) line becomes the bottom-most rider
        List<Component> bottomUp = new ArrayList<>(topDown);
        Collections.reverse(bottomUp);
        return bottomUp;
    }

    private void showNametagToPlayer(Player owner, Player viewer) {
        NametagEntity entity = playerNametags.get(owner.getUniqueId());
        if (entity == null) return;

        // Only show if owner and viewer are in the same world and viewer can see owner; otherwise ensure it's destroyed for this viewer
        if (shouldHideForViewer(owner, viewer)) {
            sendDestroyToViewer(viewer, entity.getEntityIds());
            setLastVisibility(viewer, owner, false);
            return;
        }

        int ownerId = owner.getEntityId();

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

            float defaultViewDistance = 1.0f; // default (1 unit) equals 64 blocks
            // Tested: Default is 80 for our metadata.
            // Default Minecraft client nametag distance is 64 blocks.
            float blocksPerDefault = 80.0f;
            float oneBlockViewDistance = defaultViewDistance / blocksPerDefault;

            int blocksConfig = (int) plugin.config.general.get("view_distance");
            int blocks = blocksConfig > 0 ? blocksConfig : 64; // Default to 64 blocks if not set

            // Configurable line spacing in blocks (world units). Default ~0.275 blocks.
            Object lineSpacingObj = plugin.config.general.get("line_spacing");
            double lineSpacing = (lineSpacingObj instanceof Number) ? ((Number) lineSpacingObj).doubleValue() : 0.275D;

            // Create the spawn packet for each text display entity (one per line), bottom-up
            List<Integer> lineEntityIds = entity.getEntityIds();
            List<Component> linesBottomUp = entity.getLines(); // bottom-up order

            for (int i = 0; i < lineEntityIds.size(); i++) {
                int lineEntityId = lineEntityIds.get(i);

                WrapperPlayServerSpawnEntity spawnPacket = new WrapperPlayServerSpawnEntity(
                        lineEntityId,
                        UUID.randomUUID(),
                        EntityTypes.TEXT_DISPLAY,
                        packetevents_location,
                        0f, // yaw
                        0,  // data
                        new Vector3d(0, 0, 0) // velocity
                );

                // Add metadata to the Text Display, see protocol info here: https://minecraft.wiki/w/Java_Edition_protocol/Entity_metadata#Text_Display
                List<EntityData<?>> metadata = new ArrayList<>();

                // Display entity flags: enable transformations + centered billboard so it faces viewers
                metadata.add(new EntityData<>(15, EntityDataTypes.BYTE, (byte) 0x03));

                // View distance
                metadata.add(new EntityData<>(17, EntityDataTypes.FLOAT, oneBlockViewDistance * blocks));

                // Apply per-line vertical translation so lines have spacing while riding each other.
                // Translation is in world units (blocks). Bottom line = 0, next = spacing, etc.
                float yOffset = (float) ((i + 1) * lineSpacing);
                metadata.add(new EntityData<>(11, EntityDataTypes.VECTOR3F, new Vector3f(0f, yOffset, 0f)));

                // Set the text content of this line (each line is its own display)
                Component lineText = linesBottomUp.get(i);
                // If owner is globally hidden, ensure we send empty text to this viewer too
                if (isGloballyHidden(owner)) {
                    lineText = Component.text("");
                }
                metadata.add(new EntityData<>(23, EntityDataTypes.ADV_COMPONENT, lineText));

                // Set background color to fully transparent (optional)
                // metadata.add(new EntityData<>(25, EntityDataTypes.INT, 0));

                WrapperPlayServerEntityMetadata metadataPacket = new WrapperPlayServerEntityMetadata(lineEntityId, metadata);

                PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, spawnPacket);
                PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, metadataPacket);
            }

            // Chain passengers: start from the owner, then each line rides the previous one (bottom-up)
            int parentId = ownerId;
            for (int lineEntityId : lineEntityIds) {
                WrapperPlayServerSetPassengers passengersPacket = new WrapperPlayServerSetPassengers(parentId, new int[]{lineEntityId});
                PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, passengersPacket);
                parentId = lineEntityId; // next line rides this line
            }

            // Mark as visible for this viewer-owner pair
            setLastVisibility(viewer, owner, true);
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

            // When globally hidden (sneaking/invisible), keep entity count stable and set all lines to empty to avoid respawn flicker.
            List<Component> newLinesBottomUp;
            if (isGloballyHidden(player)) {
                newLinesBottomUp = emptyLines(entity.getEntityIds().size());
            } else {
                String nametagTemplate = (String) plugin.config.general.get("nametag");
                List<Component> rendered = renderLinesBottomUp(nametagTemplate, replacements, player);
                // Normalize to the current entity count to avoid destroy/spawn
                newLinesBottomUp = normalizeToSize(rendered, entity.getEntityIds().size());
            }

            // Update changed lines only (no destroy/spawn to prevent flicker)
            boolean anyChanged = false;
            List<Component> old = entity.getLines();
            if (old.size() != newLinesBottomUp.size()) {
                // Sizes should generally match due to normalization; if not, normalize old view and continue
                newLinesBottomUp = normalizeToSize(newLinesBottomUp, old.size());
            }
            for (int i = 0; i < newLinesBottomUp.size(); i++) {
                if (!newLinesBottomUp.get(i).equals(old.get(i))) {
                    anyChanged = true;
                    break;
                }
            }

            if (anyChanged) {
                entity.setLines(newLinesBottomUp);
                sendNametagTextUpdate(player, entity);
            }
        }
    }

    // Instant metadata-only swap for visibility state (sneak/invisible) (no destroy/spawn, no delay).
    private void updateOwnerVisibilityNametag(Player player) {
        NametagEntity entity = playerNametags.get(player.getUniqueId());
        if (entity == null) return;

        List<Component> target;
        if (isGloballyHidden(player)) {
            target = emptyLines(entity.getEntityIds().size());
        } else {
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

            String nametagTemplate = (String) plugin.config.general.get("nametag");
            List<Component> rendered = renderLinesBottomUp(nametagTemplate, replacements, player);
            target = normalizeToSize(rendered, entity.getEntityIds().size());
        }

        // Only send updates for lines that changed
        boolean anyChanged = false;
        List<Component> old = entity.getLines();
        for (int i = 0; i < target.size(); i++) {
            if (!target.get(i).equals(old.get(i))) {
                anyChanged = true;
                break;
            }
        }
        if (!anyChanged) return;

        entity.setLines(target);
        sendNametagTextUpdate(player, entity);
    }

    // Non-destructive periodic refresh: re-apply text state and passenger chains without destroy/spawn.
    private void softRefreshNametags() {
        cleanupOrphans();
        for (Player owner : Bukkit.getOnlinePlayers()) {
            NametagEntity entity = playerNametags.get(owner.getUniqueId());
            if (entity == null) {
                // If somehow missing, spawn anew (rare). This will only affect that player’s nametag.
                spawnNametag(owner);
                continue;
            }
            // Re-apply current visibility state instantly (metadata only)
            updateOwnerVisibilityNametag(owner);
            // Re-send passenger chain to ensure client keeps the riding hierarchy, scoped per-world and per-visibility
            resendPassengerChain(owner, entity);
        }
    }

    // Enforce per-viewer visibility transitions (spawn/destroy) based on world and vanish state.
    private void enforceVisibilityMatrix() {
        // Iterate owners with nametags
        for (Map.Entry<UUID, NametagEntity> entry : playerNametags.entrySet()) {
            UUID ownerId = entry.getKey();
            Player owner = Bukkit.getPlayer(ownerId);
            if (owner == null || !owner.isOnline()) continue;

            NametagEntity entity = entry.getValue();
            List<Integer> ids = entity.getEntityIds();

            for (Player viewer : Bukkit.getOnlinePlayers()) {
                if (viewer.getUniqueId().equals(ownerId)) continue;

                boolean desiredVisible = !shouldHideForViewer(owner, viewer);
                boolean lastVisible = getLastVisibility(viewer, owner);

                if (desiredVisible && !lastVisible) {
                    // Spawn to this viewer now
                    showNametagToPlayer(owner, viewer);
                    setLastVisibility(viewer, owner, true);
                } else if (!desiredVisible && lastVisible) {
                    // Destroy for this viewer now
                    sendDestroyToViewer(viewer, ids);
                    setLastVisibility(viewer, owner, false);
                }
            }
        }
    }

    private void resendPassengerChain(Player owner, NametagEntity entity) {
        int parentId = owner.getEntityId();
        for (int lineEntityId : entity.getEntityIds()) {
            WrapperPlayServerSetPassengers passengersPacket = new WrapperPlayServerSetPassengers(parentId, new int[]{lineEntityId});
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                if (viewer.equals(owner)) continue;
                if (!shouldHideForViewer(owner, viewer)) {
                    PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, passengersPacket);
                } else {
                    // Ensure hidden in other worlds or for viewers that cannot see the owner
                    sendDestroyToViewer(viewer, entity.getEntityIds());
                    setLastVisibility(viewer, owner, false);
                }
            }
            parentId = lineEntityId;
        }
    }

    private boolean isGloballyHidden(Player owner) {
        // Hide nametag when sneaking or invisible via potion/flag
        return owner.isSneaking()
                || owner.hasPotionEffect(PotionEffectType.INVISIBILITY)
                || owner.isInvisible();
    }

    private boolean shouldHideForViewer(Player owner, Player viewer) {
        // Hide from viewer if not same world or viewer cannot see owner (e.g., vanished)
        return !sameWorld(owner, viewer) || !viewer.canSee(owner);
    }

    private boolean sameWorld(Player a, Player b) {
        return a.getWorld().getUID().equals(b.getWorld().getUID());
    }

    private boolean getLastVisibility(Player viewer, Player owner) {
        Map<UUID, Boolean> m = viewerVisibility.get(viewer.getUniqueId());
        if (m == null) return false;
        return m.getOrDefault(owner.getUniqueId(), false);
    }

    private void setLastVisibility(Player viewer, Player owner, boolean visible) {
        viewerVisibility
                .computeIfAbsent(viewer.getUniqueId(), k -> new ConcurrentHashMap<>())
                .put(owner.getUniqueId(), visible);
    }

    private void clearOwnerFromVisibilityCache(UUID ownerId) {
        for (Map<UUID, Boolean> m : viewerVisibility.values()) {
            if (m != null) {
                m.remove(ownerId);
            }
        }
    }

    private void clearViewerFromVisibilityCache(UUID viewerId) {
        viewerVisibility.remove(viewerId);
    }

    private List<Component> normalizeToSize(List<Component> src, int size) {
        List<Component> out = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            if (i < src.size()) {
                out.add(src.get(i));
            } else {
                out.add(Component.text(""));
            }
        }
        // If src is longer, trim to size (avoid respawn)
        if (out.size() > size) {
            return new ArrayList<>(out.subList(0, size));
        }
        return out;
    }

    private List<Component> emptyLines(int size) {
        List<Component> out = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            out.add(Component.text(""));
        }
        return out;
    }

    private void sendNametagTextUpdate(Player owner, NametagEntity entity) {
        List<Integer> ids = entity.getEntityIds();
        List<Component> lines = entity.getLines();

        // For each line entity, send metadata update for text (index 23)
        for (int i = 0; i < ids.size(); i++) {
            int entityId = ids.get(i);
            List<EntityData<?>> metadata = new ArrayList<>();
            Component text = lines.get(i);
            metadata.add(new EntityData<>(23, EntityDataTypes.ADV_COMPONENT, text));
            WrapperPlayServerEntityMetadata metadataPacket = new WrapperPlayServerEntityMetadata(entityId, metadata);

            for (Player viewer : Bukkit.getOnlinePlayers()) {
                if (viewer.equals(owner)) continue;
                if (!shouldHideForViewer(owner, viewer)) {
                    PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, metadataPacket);
                } else {
                    // If viewer is in a different world or cannot see the owner, ensure the nametag is destroyed for them
                    sendDestroyToViewer(viewer, ids);
                    setLastVisibility(viewer, owner, false);
                }
            }
        }
    }

    private void sendDestroyToViewer(Player viewer, List<Integer> entityIds) {
        for (int entityId : entityIds) {
            WrapperPlayServerDestroyEntities destroyPacket = new WrapperPlayServerDestroyEntities(entityId);
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, destroyPacket);
        }
    }

    private void removeNametag(Player player) {
        NametagEntity entity = playerNametags.remove(player.getUniqueId());
        // Remove this owner from all viewer caches
        clearOwnerFromVisibilityCache(player.getUniqueId());
        if (entity != null) {
            // Destroy all line entities for this player's nametag
            for (int entityId : entity.getEntityIds()) {
                WrapperPlayServerDestroyEntities destroyPacket = new WrapperPlayServerDestroyEntities(entityId);
                for (Player viewer : Bukkit.getOnlinePlayers()) {
                    PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, destroyPacket);
                }
            }
        }
    }

    private void cleanupOrphans() {
        // Remove owners who are offline from internal maps
        for (UUID uuid : new ArrayList<>(playerNametags.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                playerNametags.remove(uuid);
                clearOwnerFromVisibilityCache(uuid);
            }
        }
        // Remove offline viewers from cache
        for (UUID viewerId : new ArrayList<>(viewerVisibility.keySet())) {
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer == null || !viewer.isOnline()) {
                clearViewerFromVisibilityCache(viewerId);
            }
        }
    }

    public void removeAllNametagsOnShutdown() {
        for (Map.Entry<UUID, NametagEntity> entry : playerNametags.entrySet()) {
            // Destroy all line entities for each nametag
            for (int entityId : entry.getValue().getEntityIds()) {
                WrapperPlayServerDestroyEntities destroyPacket = new WrapperPlayServerDestroyEntities(entityId);
                for (Player viewer : Bukkit.getOnlinePlayers()) {
                    PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, destroyPacket);
                }
            }
        }
        playerNametags.clear();
        viewerVisibility.clear();
    }

    private void reloadNametags() {
        removeAllNametagsOnShutdown();
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            spawnNametag(onlinePlayer);
        }
    }

    public static class NametagEntity {
        private final List<Integer> entityIds; // bottom-up order
        private List<Component> lines; // bottom-up order

        public NametagEntity(List<Integer> entityIds, List<Component> lines) {
            this.entityIds = entityIds;
            this.lines = lines;
        }

        public List<Integer> getEntityIds() {
            return entityIds;
        }

        public List<Component> getLines() {
            return lines;
        }

        public void setLines(List<Component> lines) {
            this.lines = lines;
        }
    }
}