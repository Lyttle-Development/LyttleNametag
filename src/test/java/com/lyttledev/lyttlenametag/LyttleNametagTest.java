package com.lyttledev.lyttlenametag;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import static org.junit.jupiter.api.Assertions.*;

public class LyttleNametagTest {
    private ServerMock server;
    private LyttleNametag plugin;

    @BeforeEach
    public void setUp() {
        server = MockBukkit.mock();
        org.bukkit.plugin.PluginDescriptionFile pdf = new org.bukkit.plugin.PluginDescriptionFile("LyttleNametag", "1.3.1", "com.lyttledev.lyttlenametag.LyttleNametag");
        java.io.File jarFile = new java.io.File("build/libs/LyttleNametag-1.3.1.jar");
        
        try {
            org.bukkit.plugin.java.JavaPluginLoader loader = new org.bukkit.plugin.java.JavaPluginLoader(server);
            java.lang.reflect.Constructor<LyttleNametag> constr = LyttleNametag.class.getDeclaredConstructor(
                org.bukkit.plugin.java.JavaPluginLoader.class,
                org.bukkit.plugin.PluginDescriptionFile.class,
                java.io.File.class,
                java.io.File.class
            );
            constr.setAccessible(true);
            plugin = constr.newInstance(loader, pdf, new java.io.File(server.getPluginManager().getParentTemporaryDirectory(), "LyttleNametagTemp"), jarFile);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to instantiate plugin using protected constructor", e);
        }
        
        try {
            // server field
            java.lang.reflect.Field serverField = org.bukkit.plugin.java.JavaPlugin.class.getDeclaredField("server");
            serverField.setAccessible(true);
            serverField.set(plugin, server);
            
            // description field
            java.lang.reflect.Field descField = org.bukkit.plugin.java.JavaPlugin.class.getDeclaredField("description");
            descField.setAccessible(true);
            descField.set(plugin, pdf);
            
            // dataFolder field
            java.io.File dataFolder = new java.io.File(server.getPluginManager().getParentTemporaryDirectory(), "LyttleNametagTemp");
            dataFolder.mkdirs();
            java.lang.reflect.Field dataFolderField = org.bukkit.plugin.java.JavaPlugin.class.getDeclaredField("dataFolder");
            dataFolderField.setAccessible(true);
            dataFolderField.set(plugin, dataFolder);
            
            // logger field
            java.lang.reflect.Field loggerField = org.bukkit.plugin.java.JavaPlugin.class.getDeclaredField("logger");
            loggerField.setAccessible(true);
            loggerField.set(plugin, java.util.logging.Logger.getLogger("LyttleNametag"));
            
            // configFile field
            java.io.File configFile = new java.io.File(dataFolder, "config.yml");
            java.lang.reflect.Field configFileField = org.bukkit.plugin.java.JavaPlugin.class.getDeclaredField("configFile");
            configFileField.setAccessible(true);
            configFileField.set(plugin, configFile);

            // classLoader field
            java.lang.reflect.Field classLoaderField = org.bukkit.plugin.java.JavaPlugin.class.getDeclaredField("classLoader");
            classLoaderField.setAccessible(true);
            classLoaderField.set(plugin, LyttleNametag.class.getClassLoader());

            // allowsLifecycleRegistration field (if it exists)
            try {
                java.lang.reflect.Field allowsField = org.bukkit.plugin.java.JavaPlugin.class.getDeclaredField("allowsLifecycleRegistration");
                allowsField.setAccessible(true);
                allowsField.set(plugin, true);
            } catch (NoSuchFieldException ignored) {}

            // isEnabled field
            java.lang.reflect.Field enabledField = org.bukkit.plugin.java.JavaPlugin.class.getDeclaredField("isEnabled");
            enabledField.setAccessible(true);
            enabledField.set(plugin, true);
            
            // Register plugin
            server.getPluginManager().registerLoadedPlugin(plugin);
            
            // Call lifecycle methods
            plugin.onLoad();
            plugin.onEnable();
            
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to manually initialize JavaPlugin fields", e);
        }
    }

    @AfterEach
    public void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    public void testVaultHookFallback() {
        // Vault is not present on MockBukkit mock server by default
        assertNull(plugin.getVaultPermission(), "Vault should be null when the Vault plugin is not loaded");
    }

    @Test
    public void testDefaultConfigLoading() {
        assertNotNull(plugin.config, "Configs should be initialized on plugin startup");
        assertNotNull(plugin.config.general, "config.yml should be loaded");
    }

    @Test
    public void testViewSelfConfigExists() {
        assertTrue(plugin.config.general.contains("view_self"), "config should contain view_self option");
        assertEquals(true, plugin.config.general.get("view_self"), "view_self should default to true");
    }

    @Test
    public void testTamedMobsConfigExists() {
        assertTrue(plugin.config.general.contains("tamed_mobs.enabled"), "config should contain tamed_mobs.enabled");
        assertEquals(true, plugin.config.general.get("tamed_mobs.enabled"), "tamed_mobs.enabled should default to true");
        assertTrue(plugin.config.general.contains("tamed_mobs.show_unnamed"), "config should contain tamed_mobs.show_unnamed");
        assertEquals(false, plugin.config.general.get("tamed_mobs.show_unnamed"), "tamed_mobs.show_unnamed should default to false");
    }

    @Test
    public void testConfigVersionIsFour() {
        assertTrue(plugin.config.general.contains("config_version"), "config should contain config_version");
        assertEquals(4, Integer.parseInt(plugin.config.general.get("config_version").toString()), "config_version should be 4");
    }
}
