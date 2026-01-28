package com.eliteessentials.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Storage for per-world spawn locations.
 * Saves to spawn.json in the plugin data folder.
 * 
 * Each world can have its own spawn point. When a player uses /spawn,
 * they teleport to the spawn in their current world.
 */
public class SpawnStorage {

    private static final Logger logger = Logger.getLogger("EliteEssentials");
    private static final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();
    private static final Type SPAWN_MAP_TYPE = new TypeToken<Map<String, SpawnData>>(){}.getType();

    private final File dataFolder;
    private Map<String, SpawnData> spawns = new HashMap<>();

    public SpawnStorage(File dataFolder) {
        this.dataFolder = dataFolder;
    }

    public void load() {
        File file = new File(dataFolder, "spawn.json");
        logger.info("Looking for spawn.json at: " + file.getAbsolutePath());
        
        if (!file.exists()) {
            spawns = new HashMap<>();
            logger.warning("spawn.json not found - no spawn points set. Use /setspawn to set spawn locations.");
            return;
        }

        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            // Try to load as new format (Map)
            spawns = gson.fromJson(reader, SPAWN_MAP_TYPE);
            if (spawns == null) {
                spawns = new HashMap<>();
            }
            
            // Check if we got valid data - if the map has entries but they're all null or invalid,
            // it means we loaded old format as new format (Gson doesn't throw, just returns bad data)
            boolean hasValidData = false;
            for (SpawnData spawn : spawns.values()) {
                if (spawn != null && spawn.world != null) {
                    hasValidData = true;
                    break;
                }
            }
            
            if (!spawns.isEmpty() && !hasValidData) {
                // Old format was parsed incorrectly - try migration
                logger.info("Detected old spawn format, attempting migration...");
                spawns = new HashMap<>();
                migrateOldFormat(file);
            } else {
                logger.info("Loaded spawns for " + spawns.size() + " world(s) from: " + file.getAbsolutePath());
            }
        } catch (Exception e) {
            // Try to migrate from old format (single SpawnData)
            try {
                migrateOldFormat(file);
            } catch (Exception e2) {
                logger.warning("Failed to load spawn.json: " + e.getMessage());
                spawns = new HashMap<>();
            }
        }
    }
    
    /**
     * Migrate from old single-spawn format to new per-world format.
     */
    private void migrateOldFormat(File file) throws IOException {
        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            SpawnData oldSpawn = gson.fromJson(reader, SpawnData.class);
            if (oldSpawn != null && oldSpawn.world != null) {
                spawns = new HashMap<>();
                spawns.put(oldSpawn.world, oldSpawn);
                save();
                logger.info("Migrated old spawn format to per-world format for world: " + oldSpawn.world);
            }
        }
    }

    public void save() {
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        File file = new File(dataFolder, "spawn.json");
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            gson.toJson(spawns, writer);
            logger.info("Spawn data saved to spawn.json");
        } catch (Exception e) {
            logger.warning("Failed to save spawn.json: " + e.getMessage());
        }
    }

    /**
     * Get spawn for a specific world.
     */
    public SpawnData getSpawn(String worldName) {
        return spawns.get(worldName);
    }
    
    /**
     * Get spawn for a specific world, or null if not set.
     * @deprecated Use getSpawn(String worldName) instead
     */
    @Deprecated
    public SpawnData getSpawn() {
        // For backwards compatibility, return first spawn if only one exists
        if (spawns.size() == 1) {
            return spawns.values().iterator().next();
        }
        return null;
    }

    /**
     * Set spawn for a specific world.
     */
    public void setSpawn(String world, double x, double y, double z, float yaw, float pitch) {
        spawns.put(world, new SpawnData(world, x, y, z, yaw, pitch));
        save();
    }

    /**
     * Check if a world has a spawn set.
     */
    public boolean hasSpawn(String worldName) {
        return spawns.containsKey(worldName);
    }
    
    /**
     * Check if any spawn is set.
     */
    public boolean hasSpawn() {
        return !spawns.isEmpty();
    }
    
    /**
     * Get all world names that have spawns set.
     */
    public java.util.Set<String> getWorldsWithSpawn() {
        return spawns.keySet();
    }

    /**
     * Simple data class for spawn location.
     */
    public static class SpawnData {
        public String world;
        public double x;
        public double y;
        public double z;
        public float yaw;
        public float pitch;

        public SpawnData() {}

        public SpawnData(String world, double x, double y, double z, float yaw, float pitch) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }
}
