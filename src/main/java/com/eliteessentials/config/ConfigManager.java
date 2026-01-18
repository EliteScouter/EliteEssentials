package com.eliteessentials.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Manages plugin configuration loading and access.
 * Supports config migration - preserves user values while adding new fields from defaults.
 */
public class ConfigManager {

    private static final Logger logger = Logger.getLogger("EliteEssentials");
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    
    private final File dataFolder;
    private PluginConfig config;

    public ConfigManager(File dataFolder) {
        this.dataFolder = dataFolder;
    }

    public void loadConfig() {
        if (!dataFolder.exists()) {
            if (dataFolder.mkdirs()) {
                logger.info("Created plugin folder: " + dataFolder.getAbsolutePath());
            } else {
                logger.warning("Could not create plugin folder: " + dataFolder.getAbsolutePath());
            }
        }
        
        File configFile = new File(dataFolder, "config.json");
        
        if (!configFile.exists()) {
            logger.info("Config file not found, creating default config.json...");
            config = new PluginConfig();
            saveConfig();
            return;
        }
        
        // Load existing config and merge with defaults
        try {
            config = loadAndMergeConfig(configFile);
            logger.info("Configuration loaded from: " + configFile.getAbsolutePath());
        } catch (Exception e) {
            logger.severe("Failed to load config.json: " + e.getMessage());
            config = new PluginConfig();
        }
    }

    /**
     * Loads user config and merges it with defaults.
     * User values are preserved, missing fields are filled from defaults.
     * The merged config is saved back to disk.
     */
    private PluginConfig loadAndMergeConfig(File configFile) throws IOException {
        // Load user's existing config as JsonObject
        JsonObject userJson;
        try (Reader reader = new InputStreamReader(new FileInputStream(configFile), StandardCharsets.UTF_8)) {
            userJson = gson.fromJson(reader, JsonObject.class);
        }
        
        if (userJson == null) {
            userJson = new JsonObject();
        }
        
        // Create default config and convert to JsonObject
        PluginConfig defaults = new PluginConfig();
        JsonObject defaultJson = gson.toJsonTree(defaults).getAsJsonObject();
        
        // Merge: defaults as base, user values override
        JsonObject merged = deepMerge(defaultJson, userJson);
        
        // Convert merged JSON back to PluginConfig
        PluginConfig mergedConfig = gson.fromJson(merged, PluginConfig.class);
        
        // Count message keys to detect if new messages were added
        int userMessageCount = 0;
        int defaultMessageCount = 0;
        if (userJson.has("messages") && userJson.get("messages").isJsonObject()) {
            userMessageCount = userJson.getAsJsonObject("messages").size();
        }
        if (defaultJson.has("messages") && defaultJson.get("messages").isJsonObject()) {
            defaultMessageCount = defaultJson.getAsJsonObject("messages").size();
        }
        
        logger.info("Config messages: user has " + userMessageCount + ", defaults has " + defaultMessageCount);
        
        // Force save if message count differs (new messages added)
        if (defaultMessageCount > userMessageCount) {
            logger.info("New message keys detected! Updating config with " + (defaultMessageCount - userMessageCount) + " new messages...");
            config = mergedConfig;
            saveConfig();
            return mergedConfig;
        }
        
        // Check if merge added any other new fields
        String userJsonStr = gson.toJson(userJson);
        String mergedJsonStr = gson.toJson(merged);
        
        if (!userJsonStr.equals(mergedJsonStr)) {
            logger.info("Config updated with new fields from this version. Saving...");
            config = mergedConfig;
            saveConfig();
        }
        
        return mergedConfig;
    }

    /**
     * Deep merge two JsonObjects.
     * - Base provides the structure and default values
     * - Override values replace base values where they exist
     * - New fields in base (not in override) are added
     * - Nested objects are merged recursively
     */
    private JsonObject deepMerge(JsonObject base, JsonObject override) {
        JsonObject result = new JsonObject();
        
        // Start with all base entries
        for (Map.Entry<String, JsonElement> entry : base.entrySet()) {
            String key = entry.getKey();
            JsonElement baseValue = entry.getValue();
            
            if (override.has(key)) {
                JsonElement overrideValue = override.get(key);
                
                // If both are objects, merge recursively
                if (baseValue.isJsonObject() && overrideValue.isJsonObject()) {
                    result.add(key, deepMerge(baseValue.getAsJsonObject(), overrideValue.getAsJsonObject()));
                } else {
                    // Use override value (user's setting)
                    result.add(key, overrideValue);
                }
            } else {
                // Key only in base (new field) - use default
                result.add(key, baseValue);
            }
        }
        
        // Add any keys that exist only in override (user added custom fields)
        for (Map.Entry<String, JsonElement> entry : override.entrySet()) {
            if (!result.has(entry.getKey())) {
                result.add(entry.getKey(), entry.getValue());
            }
        }
        
        return result;
    }

    public void saveConfig() {
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        
        File configFile = new File(dataFolder, "config.json");
        
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(configFile), StandardCharsets.UTF_8)) {
            gson.toJson(config, writer);
            logger.info("Configuration saved to: " + configFile.getAbsolutePath());
        } catch (Exception e) {
            logger.severe("Failed to save config.json: " + e.getMessage());
        }
    }

    public PluginConfig getConfig() {
        return config;
    }

    // Convenience methods for common config access
    
    public int getRtpMinRange() {
        return config.rtp.minRange;
    }

    public int getRtpMaxRange() {
        return config.rtp.maxRange;
    }

    public int getRtpCooldown() {
        return config.rtp.cooldownSeconds;
    }

    public int getRtpMaxAttempts() {
        return config.rtp.maxAttempts;
    }

    public int getBackMaxHistory() {
        return config.back.maxHistory;
    }

    public boolean isBackOnDeathEnabled() {
        return config.back.workOnDeath;
    }

    public int getTpaTimeout() {
        return config.tpa.timeoutSeconds;
    }

    public int getMaxHomes() {
        return config.homes.maxHomes;
    }

    public String getMessage(String key) {
        return config.messages.getOrDefault(key, "&cMissing message: " + key);
    }

    /**
     * Gets a message and replaces placeholders with values.
     * Placeholders use {key} format, e.g., {player}, {seconds}, {name}
     */
    public String getMessage(String key, String... replacements) {
        String message = getMessage(key);
        if (replacements.length % 2 != 0) {
            return message; // Invalid replacements, return as-is
        }
        for (int i = 0; i < replacements.length; i += 2) {
            message = message.replace("{" + replacements[i] + "}", replacements[i + 1]);
        }
        return message;
    }
    
    /**
     * Sets a message value in the config.
     * @param key Message key
     * @param value New message value
     */
    public void setMessage(String key, String value) {
        config.messages.put(key, value);
    }

    public String getPrefix() {
        return config.messages.getOrDefault("prefix", "&7[&bEliteEssentials&7] ");
    }

    public boolean isDebugEnabled() {
        return config.debug;
    }
    
    public boolean isAdvancedPermissions() {
        return config.advancedPermissions;
    }
}
