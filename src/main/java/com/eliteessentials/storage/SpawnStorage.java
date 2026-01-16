package com.eliteessentials.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

/**
 * Storage for server spawn location.
 * Saves to spawn.json in the plugin data folder.
 */
public class SpawnStorage {

    private static final Logger logger = Logger.getLogger("EliteEssentials");
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private final File dataFolder;
    private SpawnData spawn;

    public SpawnStorage(File dataFolder) {
        this.dataFolder = dataFolder;
    }

    public void load() {
        File file = new File(dataFolder, "spawn.json");
        if (!file.exists()) {
            spawn = null;
            return;
        }

        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            spawn = gson.fromJson(reader, SpawnData.class);
            if (spawn != null) {
                logger.info("Spawn loaded: " + spawn.world + " at " + 
                    String.format("%.1f, %.1f, %.1f", spawn.x, spawn.y, spawn.z));
            }
        } catch (Exception e) {
            logger.warning("Failed to load spawn.json: " + e.getMessage());
            spawn = null;
        }
    }

    public void save() {
        if (spawn == null) return;

        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        File file = new File(dataFolder, "spawn.json");
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            gson.toJson(spawn, writer);
            logger.info("Spawn saved to spawn.json");
        } catch (Exception e) {
            logger.warning("Failed to save spawn.json: " + e.getMessage());
        }
    }

    public SpawnData getSpawn() {
        return spawn;
    }

    public void setSpawn(String world, double x, double y, double z, float yaw, float pitch) {
        this.spawn = new SpawnData(world, x, y, z, yaw, pitch);
        save();
    }

    public boolean hasSpawn() {
        return spawn != null;
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
