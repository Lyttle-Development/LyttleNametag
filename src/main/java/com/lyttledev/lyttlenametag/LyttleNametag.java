package com.lyttledev.lyttlenametag;

import com.lyttledev.lyttlenametag.commands.*;
import com.lyttledev.lyttlenametag.handlers.onPlayerMoveListener;
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
    public onPlayerMoveListener playerMove;


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
        this.playerMove = new onPlayerMoveListener(this);
    }

    @Override
    public void onDisable() {
        this.playerMove.removeAllNametagsOnShutdown();
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
//            case "0":
//                // Migrate config entries.
//
//                // Update config version.
//                config.general.set("config_version", 1);
//
//                // Recheck if the config is fully migrated.
//                migrateConfig();
//                break;
            default:
                break;
        }
    }
}
