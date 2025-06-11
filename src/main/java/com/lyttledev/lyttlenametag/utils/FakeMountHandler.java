package com.lyttledev.lyttlenametag.utils;

import com.lyttledev.lyttlenametag.LyttleNametag;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPassengers;
import java.util.List;
import java.util.UUID;

public class FakeMountHandler {
    private final Plugin plugin;

    public FakeMountHandler(LyttleNametag plugin) {
        this.plugin = plugin;
    }

    public void sendFakeMount(Player targetPlayer, List<TextDisplay> nametags) {
        int[] entityIds = nametags.stream().mapToInt(TextDisplay::getEntityId).toArray();
        int[] passengers = new int[entityIds[0]];

        WrapperPlayServerSetPassengers mountPacket = new WrapperPlayServerSetPassengers(targetPlayer.getEntityId(), passengers);

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.equals(targetPlayer)) {
                PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, mountPacket);
            }
        }
    }
}