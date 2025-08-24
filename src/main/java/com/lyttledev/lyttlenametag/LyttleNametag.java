package com.lyttledev.lyttlenametag;

import com.lyttledev.lyttlenametag.commands.*;
import com.lyttledev.lyttlenametag.handlers.NametagHandler;
import com.lyttledev.lyttlenametag.types.Configs;

import com.lyttledev.lyttleutils.utils.communication.Console;
import com.lyttledev.lyttleutils.utils.communication.Message;
import com.lyttledev.lyttleutils.utils.storage.GlobalConfig;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class LyttleNametag extends JavaPlugin {
    public Configs config;
    public Console console;
    public Message message;
    public GlobalConfig global;
    public NametagHandler nametagHandler;

    @Override
    public void onEnable() {
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

    @Override
    public void onDisable() {
        this.nametagHandler.removeAllNametagsOnShutdown();
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
