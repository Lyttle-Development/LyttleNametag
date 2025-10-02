package com.lyttledev.lyttlenametag;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import com.lyttledev.lyttlenametag.commands.LyttleNametagCommand;
import com.lyttledev.lyttlenametag.handlers.NametagHandler;
import com.lyttledev.lyttlenametag.types.Configs;
import com.lyttledev.lyttleutils.utils.communication.Console;
import com.lyttledev.lyttleutils.utils.communication.Message;
import com.lyttledev.lyttleutils.utils.storage.GlobalConfig;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import com.github.retrooper.packetevents.settings.PacketEventsSettings;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class LyttleNametag extends JavaPlugin {
    public Configs config;
    public Console console;
    public Message message;
    public GlobalConfig global;
    public NametagHandler nametagHandler;

    @Override
    public void onLoad() {
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
        //On Bukkit, calling this here is essential, hence the name "load"
        PacketEvents.getAPI().load();
    }

    @Override
    public void onEnable() {
        initPacketEvents();
        saveDefaultConfig();
        // Setup config after creating the configs
        this.config = new Configs(this);
        this.global = new GlobalConfig(this);
        // Migrate config
        migrateConfig();

        // Plugin startup logic
        this.console = new Console(this);
        this.message = new Message(this, config.messages, global);

        // Commands
        new LyttleNametagCommand(this);

        // Handlers
        this.nametagHandler = new NametagHandler(this);
    }

    private void initPacketEvents() {
        // Get the PacketEvents API instance
        PacketEventsAPI<?> instance = PacketEvents.getAPI();
        // Configure PacketEvents settings
        PacketEventsSettings settings = instance.getSettings();
        // Disable update check and debug mode
        settings.checkForUpdates(false);
        // Disable debug mode
        settings.debug(false);

        // Initialize PacketEvents
        instance.init();
    }

    @Override
    public void onDisable() {
        this.nametagHandler.removeAllNametagsOnShutdown();
        PacketEvents.getAPI().terminate();
    }

    @Override
    public void saveDefaultConfig() {
        String configPath = "config.yml";
        if (!new File(getDataFolder(), configPath).exists())
            saveResource(configPath, false);

        String messagesPath = "messages.yml";
        if (!new File(getDataFolder(), messagesPath).exists())
            saveResource(messagesPath, false);

        // Defaults:
        String defaultPath = "#defaults/";
        String defaultGeneralPath =  defaultPath + configPath;
        saveResource(defaultGeneralPath, true);

        String defaultMessagesPath =  defaultPath + messagesPath;
        saveResource(defaultMessagesPath, true);
    }

    private void migrateConfig() {
        if (!config.general.contains("config_version")) {
            config.general.set("config_version", 0);
        }

        switch (config.general.get("config_version").toString()) {
            case "0":
                // Migrate config entries.
                config.general.set("nametag", config.messages.get("nametag"));
                config.messages.remove("nametag");

                // Update config version.
                config.general.set("config_version", 1);

                // Recheck if the config is fully migrated.
                migrateConfig();
                break;
            case "1":
                // Migrate config entries.
                config.general.set("interval", config.defaultGeneral.get("interval"));

                // Update config version.
                config.general.set("config_version", 2);

                // Recheck if the config is fully migrated.
                migrateConfig();
                break;
            case "2":
                // Migrate config entries.
                config.general.set("view_distance", config.defaultGeneral.get("view_distance"));

                // Update config version.
                config.general.set("config_version", 3);

                // Recheck if the config is fully migrated.
                migrateConfig();
                break;
            default:
                break;
        }
    }
}
