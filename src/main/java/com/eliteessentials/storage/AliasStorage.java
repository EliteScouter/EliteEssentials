package com.eliteessentials.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Storage for command aliases.
 * Aliases map a custom command name to an existing command string.
 * 
 * Example: "explore" -> "warp explore"
 * When player types /explore, it executes /warp explore
 * 
 * Stored in aliases.json as:
 * {
 *   "explore": {
 *     "command": "warp explore",
 *     "permission": "everyone"
 *   }
 * }
 */
public class AliasStorage {

    private static final Logger logger = Logger.getLogger("EliteEssentials");
    private static final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();
    private static final Type ALIASES_TYPE = new TypeToken<Map<String, AliasData>>(){}.getType();

    private final File dataFolder;
    private final Object fileLock = new Object();
    private Map<String, AliasData> aliases = new HashMap<>();

    public AliasStorage(File dataFolder) {
        this.dataFolder = dataFolder;
    }

    public void load() {
        File file = new File(dataFolder, "aliases.json");
        if (!file.exists()) {
            aliases = new HashMap<>();
            return;
        }

        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            Map<String, AliasData> loaded = gson.fromJson(reader, ALIASES_TYPE);
            if (loaded != null) {
                aliases = loaded;
                logger.info("Loaded " + aliases.size() + " command aliases");
            } else {
                aliases = new HashMap<>();
            }
        } catch (Exception e) {
            logger.warning("Failed to load aliases.json: " + e.getMessage());
            aliases = new HashMap<>();
        }
    }

    public void save() {
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        File file = new File(dataFolder, "aliases.json");
        synchronized (fileLock) {
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
                gson.toJson(aliases, writer);
            } catch (Exception e) {
                logger.warning("Failed to save aliases.json: " + e.getMessage());
            }
        }
    }

    /**
     * Create or update an alias.
     * @param aliasName The command name (without /)
     * @param command The command to execute (without /)
     * @param permission "everyone", "op", or a custom permission node
     * @return true if created, false if updated existing
     */
    public boolean createAlias(String aliasName, String command, String permission) {
        boolean isNew = !aliases.containsKey(aliasName.toLowerCase());
        aliases.put(aliasName.toLowerCase(), new AliasData(command, permission));
        save();
        return isNew;
    }

    /**
     * Delete an alias.
     * @return true if deleted, false if didn't exist
     */
    public boolean deleteAlias(String aliasName) {
        AliasData removed = aliases.remove(aliasName.toLowerCase());
        if (removed != null) {
            save();
            return true;
        }
        return false;
    }

    /**
     * Get an alias by name.
     */
    public AliasData getAlias(String aliasName) {
        return aliases.get(aliasName.toLowerCase());
    }

    /**
     * Check if an alias exists.
     */
    public boolean hasAlias(String aliasName) {
        return aliases.containsKey(aliasName.toLowerCase());
    }

    /**
     * Get all alias names.
     */
    public Set<String> getAliasNames() {
        return aliases.keySet();
    }

    /**
     * Get all aliases.
     */
    public Map<String, AliasData> getAllAliases() {
        return new HashMap<>(aliases);
    }

    /**
     * Data class for alias configuration.
     */
    public static class AliasData {
        /** The command to execute (without leading /) */
        public String command;
        
        /** 
         * Permission level:
         * - "everyone" = anyone can use
         * - "op" = only OPs/admins
         * - custom string = specific permission node (e.g., "eliteessentials.alias.explore")
         */
        public String permission;
        
        /**
         * If true, suppress teleport confirmation messages (e.g., "Teleported to warp 'explore'")
         * Useful when you have world MOTDs that provide context instead.
         */
        public boolean silent = false;

        public AliasData() {}

        public AliasData(String command, String permission) {
            this.command = command;
            this.permission = permission;
            this.silent = false;
        }
        
        public AliasData(String command, String permission, boolean silent) {
            this.command = command;
            this.permission = permission;
            this.silent = silent;
        }
    }
}
