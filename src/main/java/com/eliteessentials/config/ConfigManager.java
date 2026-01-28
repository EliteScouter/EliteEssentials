package com.eliteessentials.config;

import com.eliteessentials.storage.MessagesStorage;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.MalformedJsonException;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Manages plugin configuration loading and access.
 * Supports config migration - preserves user values while adding new fields from defaults.
 * 
 * Messages are stored in a separate messages.json file.
 * On upgrade from older versions, messages are automatically migrated from config.json to messages.json.
 */
public class ConfigManager {

    private static final Logger logger = Logger.getLogger("EliteEssentials");
    private static final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()  // Keep & as & instead of \u0026
            .create();
    
    private final File dataFolder;
    private PluginConfig config;
    private MessagesStorage messagesStorage;

    public ConfigManager(File dataFolder) {
        this.dataFolder = dataFolder;
        this.messagesStorage = new MessagesStorage(dataFolder);
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
            // Create default messages.json
            loadMessages();
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
        
        // Load messages (handles migration from config.json if needed)
        loadMessages();
    }
    
    /**
     * Load messages from messages.json.
     * If messages.json doesn't exist, check for migration from config.json.
     */
    private void loadMessages() {
        // Try to load existing messages.json
        if (messagesStorage.load()) {
            // Merge with defaults to add any new message keys
            PluginConfig defaults = new PluginConfig();
            if (messagesStorage.mergeWithDefaults(defaults.messages)) {
                logger.info("Added new message keys to messages.json");
                messagesStorage.save();
            }
            return;
        }
        
        // messages.json doesn't exist - check if we need to migrate from config.json
        File configFile = new File(dataFolder, "config.json");
        if (configFile.exists()) {
            try {
                migrateMessagesFromConfig(configFile);
            } catch (Exception e) {
                logger.warning("Failed to migrate messages from config.json: " + e.getMessage());
                // Fall back to defaults
                PluginConfig defaults = new PluginConfig();
                messagesStorage.setMessages(defaults.messages);
                messagesStorage.save();
            }
        } else {
            // Fresh install - create default messages
            PluginConfig defaults = new PluginConfig();
            messagesStorage.setMessages(defaults.messages);
            messagesStorage.save();
        }
    }
    
    /**
     * Migrate messages from config.json to messages.json.
     * This is a one-time migration for users upgrading from older versions.
     */
    private void migrateMessagesFromConfig(File configFile) throws IOException {
        logger.info("Checking for messages migration from config.json...");
        
        // Read config.json
        JsonObject configJson;
        try (Reader reader = new InputStreamReader(new FileInputStream(configFile), StandardCharsets.UTF_8)) {
            configJson = gson.fromJson(reader, JsonObject.class);
        }
        
        if (configJson == null) {
            configJson = new JsonObject();
        }
        
        // Check if config has messages
        if (configJson.has("messages") && configJson.get("messages").isJsonObject()) {
            JsonObject messagesJson = configJson.getAsJsonObject("messages");
            
            // Convert to Map<String, String>
            java.util.Map<String, String> migratedMessages = new java.util.HashMap<>();
            for (Map.Entry<String, JsonElement> entry : messagesJson.entrySet()) {
                if (entry.getValue().isJsonPrimitive()) {
                    migratedMessages.put(entry.getKey(), entry.getValue().getAsString());
                }
            }
            
            if (!migratedMessages.isEmpty()) {
                logger.info("Migrating " + migratedMessages.size() + " messages from config.json to messages.json...");
                
                // Merge with defaults to ensure all keys exist
                PluginConfig defaults = new PluginConfig();
                for (Map.Entry<String, String> entry : defaults.messages.entrySet()) {
                    if (!migratedMessages.containsKey(entry.getKey())) {
                        migratedMessages.put(entry.getKey(), entry.getValue());
                    }
                }
                
                messagesStorage.setMessages(migratedMessages);
                messagesStorage.save();
                
                // Remove messages from config.json
                configJson.remove("messages");
                try (Writer writer = new OutputStreamWriter(new FileOutputStream(configFile), StandardCharsets.UTF_8)) {
                    gson.toJson(configJson, writer);
                }
                logger.info("Migration complete! Messages moved to messages.json and removed from config.json");
                return;
            }
        }
        
        // No messages in config.json - create defaults
        logger.info("No messages found in config.json, creating default messages.json...");
        PluginConfig defaults = new PluginConfig();
        messagesStorage.setMessages(defaults.messages);
        messagesStorage.save();
    }
    
    /**
     * Reload messages from messages.json.
     */
    public void reloadMessages() {
        messagesStorage.load();
        // Merge with defaults to add any new message keys
        PluginConfig defaults = new PluginConfig();
        if (messagesStorage.mergeWithDefaults(defaults.messages)) {
            messagesStorage.save();
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
        
        // Remove messages from both - they're handled separately in messages.json now
        userJson.remove("messages");
        defaultJson.remove("messages");
        
        // Merge: defaults as base, user values override
        JsonObject merged = deepMerge(defaultJson, userJson);
        
        // Convert merged JSON back to PluginConfig
        PluginConfig mergedConfig = gson.fromJson(merged, PluginConfig.class);
        
        // Check if merge added any new fields (excluding messages)
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
     * - Nested objects are merged recursively (except for specific fields)
     */
    private JsonObject deepMerge(JsonObject base, JsonObject override) {
        JsonObject result = new JsonObject();
        
        // Start with all base entries
        for (Map.Entry<String, JsonElement> entry : base.entrySet()) {
            String key = entry.getKey();
            JsonElement baseValue = entry.getValue();
            
            if (override.has(key)) {
                JsonElement overrideValue = override.get(key);
                
                // Special handling for chat format maps - don't merge, use user's version entirely
                if (key.equals("chatFormat") && baseValue.isJsonObject() && overrideValue.isJsonObject()) {
                    result.add(key, mergeChatFormat(baseValue.getAsJsonObject(), overrideValue.getAsJsonObject()));
                }
                // If both are objects, merge recursively
                else if (baseValue.isJsonObject() && overrideValue.isJsonObject()) {
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
    
    /**
     * Special merge for chatFormat - don't merge groupFormats/groupPriorities maps,
     * use user's version entirely to allow removal of default groups.
     */
    private JsonObject mergeChatFormat(JsonObject base, JsonObject override) {
        JsonObject result = new JsonObject();
        
        // For each field in base
        for (Map.Entry<String, JsonElement> entry : base.entrySet()) {
            String key = entry.getKey();
            
            if (override.has(key)) {
                // For groupFormats and groupPriorities, use user's version entirely (don't merge)
                if (key.equals("groupFormats") || key.equals("groupPriorities")) {
                    result.add(key, override.get(key));
                } else {
                    // For other fields, use user's value
                    result.add(key, override.get(key));
                }
            } else {
                // New field not in user config - add from defaults
                result.add(key, entry.getValue());
            }
        }
        
        // Add any user-added fields
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
        
        try {
            // Convert config to JSON, then remove messages before saving
            JsonObject configJson = gson.toJsonTree(config).getAsJsonObject();
            configJson.remove("messages"); // Messages are stored in messages.json
            
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(configFile), StandardCharsets.UTF_8)) {
                gson.toJson(configJson, writer);
                logger.info("Configuration saved to: " + configFile.getAbsolutePath());
            }
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
        return messagesStorage.getMessage(key);
    }

    /**
     * Gets a message and replaces placeholders with values.
     * Placeholders use {key} format, e.g., {player}, {seconds}, {name}
     */
    public String getMessage(String key, String... replacements) {
        return messagesStorage.getMessage(key, replacements);
    }
    
    /**
     * Sets a message value.
     * @param key Message key
     * @param value New message value
     */
    public void setMessage(String key, String value) {
        messagesStorage.setMessage(key, value);
    }
    
    /**
     * Save messages to file.
     */
    public void saveMessages() {
        messagesStorage.save();
    }

    public String getPrefix() {
        return messagesStorage.getMessage("prefix");
    }
    
    /**
     * Get the messages storage for direct access.
     */
    public MessagesStorage getMessagesStorage() {
        return messagesStorage;
    }

    public boolean isDebugEnabled() {
        return config.debug;
    }
    
    public boolean isAdvancedPermissions() {
        return config.advancedPermissions;
    }
    
    /**
     * Validates the config.json file without loading it.
     * Returns a result with success status and error details if validation fails.
     */
    public ConfigValidationResult validateConfig() {
        return validateJsonFile(new File(dataFolder, "config.json"), "config.json");
    }
    
    /**
     * Validates all JSON files that get reloaded.
     * Returns a list of validation errors (empty if all valid).
     */
    public java.util.List<ConfigValidationResult> validateAllFiles() {
        java.util.List<ConfigValidationResult> errors = new java.util.ArrayList<>();
        
        // List of all JSON files in our plugin folder
        String[] pluginFiles = {
            "config.json",
            "messages.json",
            "motd.json",
            "rules.json",
            "discord.json",
            "warps.json",
            "spawn.json",
            "kits.json",
            "kit_claims.json",
            "aliases.json",
            "players.json",
            "homes.json",
            "first_join.json"
        };
        
        for (String filename : pluginFiles) {
            File file = new File(dataFolder, filename);
            if (file.exists()) {
                ConfigValidationResult result = validateJsonFile(file, filename);
                if (!result.isValid()) {
                    errors.add(result);
                }
            }
        }
        
        return errors;
    }
    
    /**
     * Validates a specific JSON file.
     */
    private ConfigValidationResult validateJsonFile(File file, String filename) {
        if (!file.exists()) {
            return new ConfigValidationResult(true, null, filename);
        }
        
        // Read file content to track line numbers
        String content;
        try {
            content = readFileContent(file);
        } catch (IOException e) {
            return new ConfigValidationResult(false, "Could not read file: " + e.getMessage(), filename);
        }
        
        // Try to parse with detailed error tracking
        try (JsonReader reader = new JsonReader(new StringReader(content))) {
            reader.setLenient(false);
            // Use JsonElement to handle both objects and arrays
            gson.fromJson(reader, com.google.gson.JsonElement.class);
            return new ConfigValidationResult(true, null, filename);
        } catch (JsonSyntaxException e) {
            String errorMsg = parseJsonError(e, content);
            return new ConfigValidationResult(false, errorMsg, filename);
        } catch (MalformedJsonException e) {
            String errorMsg = parseJsonError(e, content);
            return new ConfigValidationResult(false, errorMsg, filename);
        } catch (Exception e) {
            return new ConfigValidationResult(false, "Error: " + e.getMessage(), filename);
        }
    }
    
    /**
     * Reads file content as a string.
     */
    private String readFileContent(File file) throws IOException {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }
        return content.toString();
    }
    
    /**
     * Parses JSON exception to extract line/column info and provide helpful message.
     */
    private String parseJsonError(Exception e, String content) {
        String message = e.getMessage();
        
        // Try to extract line and column from Gson error messages
        // Format: "... at line X column Y ..."
        int line = -1;
        int column = -1;
        
        if (message != null) {
            // Look for "line X column Y" pattern
            int lineIdx = message.indexOf("line ");
            if (lineIdx >= 0) {
                try {
                    int start = lineIdx + 5;
                    int end = start;
                    while (end < message.length() && Character.isDigit(message.charAt(end))) {
                        end++;
                    }
                    if (end > start) {
                        line = Integer.parseInt(message.substring(start, end));
                    }
                } catch (NumberFormatException ignored) {}
            }
            
            int colIdx = message.indexOf("column ");
            if (colIdx >= 0) {
                try {
                    int start = colIdx + 7;
                    int end = start;
                    while (end < message.length() && Character.isDigit(message.charAt(end))) {
                        end++;
                    }
                    if (end > start) {
                        column = Integer.parseInt(message.substring(start, end));
                    }
                } catch (NumberFormatException ignored) {}
            }
        }
        
        StringBuilder result = new StringBuilder();
        result.append("JSON syntax error");
        
        if (line > 0) {
            result.append(" at line ").append(line);
            if (column > 0) {
                result.append(", column ").append(column);
            }
            
            // Show the problematic line
            String[] lines = content.split("\n");
            if (line <= lines.length) {
                result.append("\n  -> ").append(lines[line - 1].trim());
            }
        }
        
        // Add common fix hints based on error message
        if (message != null) {
            if (message.contains("Expected ':' ") || message.contains("Unterminated")) {
                result.append("\n  Hint: Check for missing quotes, colons, or commas");
            } else if (message.contains("Expected ',' ") || message.contains("Expected '}'")) {
                result.append("\n  Hint: Check for missing or extra commas");
            } else if (message.contains("Unexpected character")) {
                result.append("\n  Hint: Check for invalid characters or missing quotes");
            }
        }
        
        return result.toString();
    }
    
    /**
     * Result of config validation.
     */
    public static class ConfigValidationResult {
        private final boolean valid;
        private final String errorMessage;
        private final String filename;
        
        public ConfigValidationResult(boolean valid, String errorMessage, String filename) {
            this.valid = valid;
            this.errorMessage = errorMessage;
            this.filename = filename;
        }
        
        public boolean isValid() {
            return valid;
        }
        
        public String getErrorMessage() {
            return errorMessage;
        }
        
        public String getFilename() {
            return filename;
        }
    }
}
