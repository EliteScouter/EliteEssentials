package com.eliteessentials.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldConfig;
import com.hypixel.hytale.server.core.universe.world.spawn.GlobalSpawnProvider;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Logger;

/**
 * Storage for per-world spawn locations.
 * Saves to spawn.json in the plugin data folder.
 * 
 * Supports both single-spawn and multi-spawn modes:
 * - Single spawn (perWorld=false): one spawn per world, stored as array with one entry
 * - Multi spawn (perWorld=true): multiple named spawns per world with nearest-spawn logic
 * 
 * Storage format (v2 - array-based):
 *   { "worldName": [ { name, primary, protection, world, x, y, z, yaw, pitch }, ... ] }
 * 
 * Automatically migrates from v1 format (object-based):
 *   { "worldName": { world, x, y, z, yaw, pitch } }
 */
public class SpawnStorage {

    private static final Logger logger = Logger.getLogger("EliteEssentials");
    private static final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();
    private static final Type SPAWN_LIST_MAP_TYPE = new TypeToken<Map<String, List<SpawnData>>>(){}.getType();

    private static final String FIRST_JOIN_SPAWN_FILE = "firstjoinspawn.json";

    private final File dataFolder;
    private Map<String, List<SpawnData>> spawns = new HashMap<>();
    private SpawnData firstJoinSpawn;

    public SpawnStorage(File dataFolder) {
        this.dataFolder = dataFolder;
    }

    public void load() {
        loadFirstJoinSpawn();
        File file = new File(dataFolder, "spawn.json");
        logger.info("Looking for spawn.json at: " + file.getAbsolutePath());
        
        if (!file.exists()) {
            spawns = new HashMap<>();
            logger.warning("spawn.json not found - no spawn points set. Use /setspawn to set spawn locations.");
            return;
        }

        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            String content = readFully(reader);
            JsonElement root = JsonParser.parseString(content);
            
            if (!root.isJsonObject()) {
                spawns = new HashMap<>();
                logger.warning("spawn.json is not a valid JSON object.");
                return;
            }
            
            JsonObject obj = root.getAsJsonObject();
            if (obj.size() == 0) {
                spawns = new HashMap<>();
                logger.info("spawn.json is empty - no spawn points set.");
                return;
            }
            
            // Detect format: if any value is an array, it's v2; if it's an object, it's v1
            boolean isV2 = false;
            boolean isV1 = false;
            for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                if (entry.getValue().isJsonArray()) {
                    isV2 = true;
                } else if (entry.getValue().isJsonObject()) {
                    isV1 = true;
                }
            }
            
            if (isV2) {
                spawns = gson.fromJson(content, SPAWN_LIST_MAP_TYPE);
                if (spawns == null) spawns = new HashMap<>();
                int totalSpawns = spawns.values().stream().mapToInt(List::size).sum();
                logger.info("Loaded " + totalSpawns + " spawn point(s) across " + spawns.size() + " world(s) from: " + file.getAbsolutePath());
            } else if (isV1) {
                migrateFromV1(obj);
            } else {
                // Try single-spawn legacy format (just one SpawnData at root)
                migrateFromLegacy(content);
            }
        } catch (Exception e) {
            logger.warning("Failed to load spawn.json: " + e.getMessage());
            spawns = new HashMap<>();
        }
    }
    
    /**
     * Migrate from v1 format: { "worldName": { world, x, y, z, yaw, pitch } }
     * to v2 format: { "worldName": [ { name: "main", primary: true, ... } ] }
     */
    private void migrateFromV1(JsonObject root) {
        spawns = new HashMap<>();
        int migrated = 0;
        
        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            if (!entry.getValue().isJsonObject()) continue;
            
            try {
                SpawnData oldSpawn = gson.fromJson(entry.getValue(), SpawnData.class);
                if (oldSpawn != null && oldSpawn.world != null) {
                    oldSpawn.name = "main";
                    oldSpawn.primary = true;
                    oldSpawn.protection = true;
                    
                    List<SpawnData> list = new ArrayList<>();
                    list.add(oldSpawn);
                    spawns.put(entry.getKey(), list);
                    migrated++;
                }
            } catch (Exception e) {
                logger.warning("Failed to migrate spawn for world '" + entry.getKey() + "': " + e.getMessage());
            }
        }
        
        if (migrated > 0) {
            save();
            logger.info("Migrated " + migrated + " spawn point(s) from v1 to v2 format (multi-spawn support).");
        }
    }
    
    /**
     * Migrate from legacy single-spawn format (bare SpawnData at root).
     */
    private void migrateFromLegacy(String content) {
        try {
            SpawnData oldSpawn = gson.fromJson(content, SpawnData.class);
            if (oldSpawn != null && oldSpawn.world != null) {
                oldSpawn.name = "main";
                oldSpawn.primary = true;
                oldSpawn.protection = true;
                
                spawns = new HashMap<>();
                List<SpawnData> list = new ArrayList<>();
                list.add(oldSpawn);
                spawns.put(oldSpawn.world, list);
                save();
                logger.info("Migrated legacy single-spawn format to v2 for world: " + oldSpawn.world);
            }
        } catch (Exception e) {
            logger.warning("Failed to migrate legacy spawn format: " + e.getMessage());
            spawns = new HashMap<>();
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

    // ==================== SINGLE-SPAWN API (backward compatible) ====================

    /**
     * Get the primary spawn for a world.
     * Backward compatible - returns the primary spawn point.
     */
    public SpawnData getSpawn(String worldName) {
        return getPrimarySpawn(worldName);
    }
    
    /**
     * Set the primary spawn for a world (single-spawn mode).
     * If a primary spawn exists, it's updated. Otherwise a new one is created.
     */
    public void setSpawn(String world, double x, double y, double z, float yaw, float pitch) {
        List<SpawnData> list = spawns.computeIfAbsent(world, k -> new ArrayList<>());
        
        // Find existing primary
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).primary) {
                SpawnData updated = new SpawnData(world, x, y, z, yaw, pitch);
                updated.name = list.get(i).name;
                updated.primary = true;
                updated.protection = list.get(i).protection;
                list.set(i, updated);
                save();
                return;
            }
        }
        
        // No primary exists - create one
        SpawnData spawn = new SpawnData(world, x, y, z, yaw, pitch);
        spawn.name = "main";
        spawn.primary = true;
        spawn.protection = true;
        list.add(spawn);
        save();
    }

    /**
     * Check if a world has any spawn set.
     */
    public boolean hasSpawn(String worldName) {
        List<SpawnData> list = spawns.get(worldName);
        return list != null && !list.isEmpty();
    }
    
    /**
     * Check if any spawn is set across all worlds.
     */
    public boolean hasSpawn() {
        return spawns.values().stream().anyMatch(list -> !list.isEmpty());
    }
    
    /**
     * Get all world names that have spawns set.
     */
    public Set<String> getWorldsWithSpawn() {
        Set<String> result = new HashSet<>();
        for (Map.Entry<String, List<SpawnData>> entry : spawns.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    // ==================== MULTI-SPAWN API ====================

    /**
     * Get the primary spawn for a world.
     */
    public SpawnData getPrimarySpawn(String worldName) {
        List<SpawnData> list = spawns.get(worldName);
        if (list == null || list.isEmpty()) return null;
        
        for (SpawnData spawn : list) {
            if (spawn.primary) return spawn;
        }
        // Fallback: return first spawn if no primary flag
        return list.get(0);
    }

    /**
     * Get all spawns for a world.
     */
    public List<SpawnData> getSpawns(String worldName) {
        List<SpawnData> list = spawns.get(worldName);
        return list != null ? Collections.unmodifiableList(list) : Collections.emptyList();
    }

    /**
     * Get a spawn by name in a specific world (case-insensitive).
     */
    public SpawnData getSpawnByName(String worldName, String name) {
        List<SpawnData> list = spawns.get(worldName);
        if (list == null) return null;
        
        for (SpawnData spawn : list) {
            if (spawn.name != null && spawn.name.equalsIgnoreCase(name)) {
                return spawn;
            }
        }
        return null;
    }

    /**
     * Find the nearest spawn point to the given coordinates in a world.
     * Uses 2D distance (X/Z) since Y differences matter less for "nearest spawn".
     */
    public SpawnData getNearestSpawn(String worldName, double x, double z) {
        List<SpawnData> list = spawns.get(worldName);
        if (list == null || list.isEmpty()) return null;
        if (list.size() == 1) return list.get(0);
        
        SpawnData nearest = null;
        double nearestDistSq = Double.MAX_VALUE;
        
        for (SpawnData spawn : list) {
            double dx = spawn.x - x;
            double dz = spawn.z - z;
            double distSq = dx * dx + dz * dz;
            if (distSq < nearestDistSq) {
                nearestDistSq = distSq;
                nearest = spawn;
            } else if (nearest != null && Math.abs(distSq - nearestDistSq) < 1e-12 && spawn.primary && !nearest.primary) {
                // Tie: prefer primary spawn when distances are effectively equal
                nearest = spawn;
            }
        }
        
        return nearest;
    }

    /**
     * Add or update a named spawn point in a world.
     * If a spawn with the same name exists, it's updated.
     * 
     * @return the created/updated SpawnData, or null if max limit reached
     */
    public SpawnData addSpawn(String world, String name, double x, double y, double z, 
                              float yaw, float pitch, boolean primary, boolean protection, int maxPerWorld) {
        List<SpawnData> list = spawns.computeIfAbsent(world, k -> new ArrayList<>());
        
        // Check if updating an existing spawn
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).name != null && list.get(i).name.equalsIgnoreCase(name)) {
                SpawnData updated = new SpawnData(world, x, y, z, yaw, pitch);
                updated.name = name;
                updated.primary = primary;
                updated.protection = protection;
                
                // If setting this as primary, unset other primaries
                if (primary) {
                    for (SpawnData s : list) {
                        s.primary = false;
                    }
                }
                
                list.set(i, updated);
                save();
                return updated;
            }
        }
        
        // New spawn - check limit
        if (maxPerWorld > 0 && list.size() >= maxPerWorld) {
            return null;
        }
        
        // If setting this as primary, unset other primaries
        if (primary) {
            for (SpawnData s : list) {
                s.primary = false;
            }
        }
        
        // If this is the first spawn in the world, force it primary
        if (list.isEmpty()) {
            primary = true;
        }
        
        SpawnData spawn = new SpawnData(world, x, y, z, yaw, pitch);
        spawn.name = name;
        spawn.primary = primary;
        spawn.protection = protection;
        list.add(spawn);
        save();
        return spawn;
    }

    /**
     * Remove a named spawn point from a world.
     * 
     * @return true if removed, false if not found
     */
    public boolean removeSpawn(String worldName, String name) {
        List<SpawnData> list = spawns.get(worldName);
        if (list == null) return false;
        
        boolean removed = list.removeIf(s -> s.name != null && s.name.equalsIgnoreCase(name));
        
        if (removed) {
            // If we removed the primary, promote the first remaining spawn
            if (!list.isEmpty()) {
                boolean hasPrimary = list.stream().anyMatch(s -> s.primary);
                if (!hasPrimary) {
                    list.get(0).primary = true;
                }
            } else {
                spawns.remove(worldName);
            }
            save();
        }
        
        return removed;
    }

    /**
     * Get the count of spawn points in a world.
     */
    public int getSpawnCount(String worldName) {
        List<SpawnData> list = spawns.get(worldName);
        return list != null ? list.size() : 0;
    }

    /**
     * Get all spawn points across all worlds that have protection enabled.
     * Used by SpawnProtectionService.
     */
    public Map<String, List<SpawnData>> getAllProtectedSpawns() {
        Map<String, List<SpawnData>> result = new HashMap<>();
        for (Map.Entry<String, List<SpawnData>> entry : spawns.entrySet()) {
            List<SpawnData> protectedSpawns = new ArrayList<>();
            for (SpawnData spawn : entry.getValue()) {
                if (spawn.protection) {
                    protectedSpawns.add(spawn);
                }
            }
            if (!protectedSpawns.isEmpty()) {
                result.put(entry.getKey(), protectedSpawns);
            }
        }
        return result;
    }

    // ==================== FIRST-JOIN SPAWN ====================

    public SpawnData getFirstJoinSpawn() {
        return firstJoinSpawn;
    }

    public boolean hasFirstJoinSpawn() {
        return firstJoinSpawn != null;
    }

    public void setFirstJoinSpawn(String world, double x, double y, double z, float yaw, float pitch) {
        firstJoinSpawn = new SpawnData(world, x, y, z, yaw, pitch);
        firstJoinSpawn.name = "__firstjoin";
        firstJoinSpawn.primary = false;
        firstJoinSpawn.protection = false;
        saveFirstJoinSpawn();
    }

    public boolean deleteFirstJoinSpawn() {
        if (firstJoinSpawn == null) return false;
        firstJoinSpawn = null;
        File file = new File(dataFolder, FIRST_JOIN_SPAWN_FILE);
        if (file.exists()) {
            file.delete();
        }
        logger.info("First-join spawn point removed.");
        return true;
    }

    private void loadFirstJoinSpawn() {
        File file = new File(dataFolder, FIRST_JOIN_SPAWN_FILE);
        if (!file.exists()) {
            firstJoinSpawn = null;
            return;
        }
        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            firstJoinSpawn = gson.fromJson(reader, SpawnData.class);
            if (firstJoinSpawn != null && firstJoinSpawn.world != null) {
                logger.info("Loaded first-join spawn at " +
                    String.format("%.1f, %.1f, %.1f", firstJoinSpawn.x, firstJoinSpawn.y, firstJoinSpawn.z) +
                    " in world '" + firstJoinSpawn.world + "'");
            } else {
                firstJoinSpawn = null;
            }
        } catch (Exception e) {
            logger.warning("Failed to load " + FIRST_JOIN_SPAWN_FILE + ": " + e.getMessage());
            firstJoinSpawn = null;
        }
    }

    private void saveFirstJoinSpawn() {
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        File file = new File(dataFolder, FIRST_JOIN_SPAWN_FILE);
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            gson.toJson(firstJoinSpawn, writer);
            logger.info("First-join spawn saved to " + FIRST_JOIN_SPAWN_FILE);
        } catch (Exception e) {
            logger.warning("Failed to save " + FIRST_JOIN_SPAWN_FILE + ": " + e.getMessage());
        }
    }

    // ==================== WORLD SYNC ====================

    /**
     * Sync the primary spawn to a world's native spawn provider.
     * Only syncs the primary spawn - Hytale's native provider supports one spawn per world.
     * 
     * NOTE: We intentionally do NOT call worldConfig.markChanged() because that triggers
     * spawn marker entity recreation in SpawnReferenceSystems, which causes
     * ArrayIndexOutOfBoundsException crashes during chunk loading when duplicate
     * marker entities collide.
     */
    public void syncSpawnToWorld(World world, SpawnData spawn) {
        try {
            WorldConfig worldConfig = world.getWorldConfig();
            Transform spawnTransform = new Transform(
                new Vector3d(spawn.x, spawn.y, spawn.z),
                new Vector3f(0, spawn.yaw, 0)
            );
            worldConfig.setSpawnProvider(new GlobalSpawnProvider(spawnTransform));
            logger.info("[SpawnSync] Set native spawn provider for world '" + world.getName() + 
                "' at " + String.format("%.1f, %.1f, %.1f", spawn.x, spawn.y, spawn.z));
        } catch (Exception e) {
            logger.warning("[SpawnSync] Failed to sync spawn for world '" + world.getName() + "': " + e.getMessage());
        }
    }

    // ==================== UTILITIES ====================

    private String readFully(Reader reader) throws IOException {
        StringBuilder sb = new StringBuilder();
        char[] buffer = new char[4096];
        int read;
        while ((read = reader.read(buffer)) != -1) {
            sb.append(buffer, 0, read);
        }
        return sb.toString();
    }

    /**
     * Data class for a spawn location.
     * Extended from v1 with name, primary flag, and per-spawn protection toggle.
     */
    public static class SpawnData {
        public String name;
        public boolean primary;
        public boolean protection = true;
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
