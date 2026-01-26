package com.eliteessentials.services;

import com.eliteessentials.model.*;
import com.eliteessentials.storage.PlayerFileStorage;
import com.eliteessentials.storage.WarpStorage;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Logger;

/**
 * Migrates data from Hyssentials plugin to EliteEssentials.
 * 
 * Source: mods/com.leclowndu93150_Hyssentials/
 * - homes.json (UUID -> { homeName -> location })
 * - warps.json (warpName -> location)
 */
public class HyssentialsMigrationService {
    
    private static final Logger logger = Logger.getLogger("EliteEssentials");
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    
    private final File modsFolder;
    private final WarpStorage warpStorage;
    private final PlayerFileStorage playerFileStorage;
    
    // Migration stats
    private int warpsImported = 0;
    private int playersImported = 0;
    private int homesImported = 0;
    private final List<String> errors = new ArrayList<>();
    
    public HyssentialsMigrationService(File dataFolder, WarpStorage warpStorage, 
                                       PlayerFileStorage playerFileStorage) {
        // Go up from EliteEssentials folder to mods folder
        this.modsFolder = dataFolder.getParentFile();
        this.warpStorage = warpStorage;
        this.playerFileStorage = playerFileStorage;
    }
    
    /**
     * Check if Hyssentials data exists.
     */
    public boolean hasHyssentialsData() {
        File hyssentialsFolder = new File(modsFolder, "com.leclowndu93150_Hyssentials");
        return hyssentialsFolder.exists() && hyssentialsFolder.isDirectory();
    }
    
    /**
     * Get the Hyssentials folder path.
     */
    public File getHyssentialsFolder() {
        return new File(modsFolder, "com.leclowndu93150_Hyssentials");
    }
    
    /**
     * Run the full migration.
     * @return MigrationResult with stats and any errors
     */
    public MigrationResult migrate() {
        // Reset stats
        warpsImported = 0;
        playersImported = 0;
        homesImported = 0;
        errors.clear();
        
        File hyssentialsFolder = getHyssentialsFolder();
        
        if (!hyssentialsFolder.exists()) {
            errors.add("Hyssentials folder not found at: " + hyssentialsFolder.getAbsolutePath());
            return new MigrationResult(false, warpsImported, playersImported, homesImported, errors);
        }
        
        logger.info("[Migration] ========================================");
        logger.info("[Migration] Starting Hyssentials migration...");
        logger.info("[Migration] Source: " + hyssentialsFolder.getAbsolutePath());
        logger.info("[Migration] ========================================");
        
        // Migrate warps
        migrateWarps(hyssentialsFolder);
        
        // Migrate homes
        migrateHomes(hyssentialsFolder);
        
        logger.info("[Migration] ========================================");
        logger.info("[Migration] Hyssentials migration complete!");
        logger.info("[Migration] - Warps: " + warpsImported);
        logger.info("[Migration] - Players: " + playersImported);
        logger.info("[Migration] - Homes: " + homesImported);
        if (!errors.isEmpty()) {
            logger.info("[Migration] - Errors: " + errors.size());
        }
        logger.info("[Migration] ========================================");
        
        return new MigrationResult(errors.isEmpty(), warpsImported, playersImported, homesImported, errors);
    }
    
    /**
     * Migrate warps from Hyssentials warps.json.
     * Format: { "warpName": { "worldName": "default", "x": 1.0, "y": 2.0, "z": 3.0, "pitch": 0.0, "yaw": 0.0 } }
     */
    private void migrateWarps(File hyssentialsFolder) {
        File warpsFile = new File(hyssentialsFolder, "warps.json");
        if (!warpsFile.exists()) {
            logger.info("[Migration] No warps.json found, skipping warp migration.");
            return;
        }
        
        logger.info("[Migration] Migrating Hyssentials warps.json...");
        
        try (Reader reader = new InputStreamReader(new FileInputStream(warpsFile), StandardCharsets.UTF_8)) {
            Type type = new TypeToken<Map<String, HyssentialsLocation>>(){}.getType();
            Map<String, HyssentialsLocation> hysWarps = gson.fromJson(reader, type);
            
            if (hysWarps == null || hysWarps.isEmpty()) {
                logger.info("[Migration] - No warps found in file.");
                return;
            }
            
            for (Map.Entry<String, HyssentialsLocation> entry : hysWarps.entrySet()) {
                String warpName = entry.getKey();
                HyssentialsLocation hysLoc = entry.getValue();
                
                // Check if warp already exists
                if (warpStorage.hasWarp(warpName)) {
                    logger.info("[Migration] - Skipping warp '" + warpName + "' (already exists)");
                    continue;
                }
                
                // Convert to our format
                Location location = new Location(
                    hysLoc.worldName,
                    hysLoc.x,
                    hysLoc.y,
                    hysLoc.z,
                    hysLoc.yaw,
                    hysLoc.pitch
                );
                
                Warp warp = new Warp(warpName, location, Warp.Permission.ALL, "Hyssentials Migration");
                warpStorage.setWarp(warp);
                warpsImported++;
                logger.info("[Migration] - Imported warp: " + warpName);
            }
            
        } catch (Exception e) {
            String error = "Failed to migrate Hyssentials warps: " + e.getMessage();
            logger.severe("[Migration] " + error);
            errors.add(error);
        }
    }
    
    /**
     * Migrate homes from Hyssentials homes.json.
     * Format: { "uuid": { "homeName": { "worldName": "default", "x": 1.0, "y": 2.0, "z": 3.0, "pitch": 0.0, "yaw": 0.0 } } }
     */
    private void migrateHomes(File hyssentialsFolder) {
        File homesFile = new File(hyssentialsFolder, "homes.json");
        if (!homesFile.exists()) {
            logger.info("[Migration] No homes.json found, skipping home migration.");
            return;
        }
        
        logger.info("[Migration] Migrating Hyssentials homes.json...");
        
        try (Reader reader = new InputStreamReader(new FileInputStream(homesFile), StandardCharsets.UTF_8)) {
            // Format: UUID string -> Map of home name -> location
            Type type = new TypeToken<Map<String, Map<String, HyssentialsLocation>>>(){}.getType();
            Map<String, Map<String, HyssentialsLocation>> hysHomes = gson.fromJson(reader, type);
            
            if (hysHomes == null || hysHomes.isEmpty()) {
                logger.info("[Migration] - No homes found in file.");
                return;
            }
            
            for (Map.Entry<String, Map<String, HyssentialsLocation>> playerEntry : hysHomes.entrySet()) {
                String uuidStr = playerEntry.getKey();
                Map<String, HyssentialsLocation> playerHomes = playerEntry.getValue();
                
                if (playerHomes == null || playerHomes.isEmpty()) {
                    continue;
                }
                
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    
                    // Get or create our player file
                    PlayerFile ourPlayer = playerFileStorage.getPlayer(uuid);
                    if (ourPlayer == null) {
                        // Create new player with unknown name (will update when they join)
                        ourPlayer = playerFileStorage.getPlayer(uuid, "Unknown");
                    }
                    
                    int homesForPlayer = 0;
                    for (Map.Entry<String, HyssentialsLocation> homeEntry : playerHomes.entrySet()) {
                        String homeName = homeEntry.getKey();
                        HyssentialsLocation hysLoc = homeEntry.getValue();
                        
                        // Skip if home already exists
                        if (ourPlayer.hasHome(homeName)) {
                            logger.info("[Migration] - Skipping home '" + homeName + "' for " + uuidStr + " (already exists)");
                            continue;
                        }
                        
                        // Convert to our format
                        Location location = new Location(
                            hysLoc.worldName,
                            hysLoc.x,
                            hysLoc.y,
                            hysLoc.z,
                            hysLoc.yaw,
                            hysLoc.pitch
                        );
                        
                        Home home = new Home(homeName, location);
                        ourPlayer.setHome(home);
                        homesForPlayer++;
                        homesImported++;
                    }
                    
                    if (homesForPlayer > 0) {
                        playerFileStorage.saveAndMarkDirty(uuid);
                        playersImported++;
                        logger.info("[Migration] - Imported " + homesForPlayer + " home(s) for player " + uuidStr);
                    }
                    
                } catch (IllegalArgumentException e) {
                    logger.warning("[Migration] - Skipping invalid UUID: " + uuidStr);
                }
            }
            
        } catch (Exception e) {
            String error = "Failed to migrate Hyssentials homes: " + e.getMessage();
            logger.severe("[Migration] " + error);
            errors.add(error);
        }
        
        logger.info("[Migration] - Migrated " + homesImported + " homes for " + playersImported + " players");
    }
    
    // ==================== Inner Classes for Hyssentials Format ====================
    
    /**
     * Hyssentials location format.
     * Note: Uses "worldName" instead of "world", and pitch/yaw order differs.
     */
    private static class HyssentialsLocation {
        String worldName;
        double x;
        double y;
        double z;
        float pitch;
        float yaw;
    }
    
    // ==================== Migration Result ====================
    
    /**
     * Result of a migration operation.
     */
    public static class MigrationResult {
        private final boolean success;
        private final int warpsImported;
        private final int playersImported;
        private final int homesImported;
        private final List<String> errors;
        
        public MigrationResult(boolean success, int warpsImported, 
                              int playersImported, int homesImported, List<String> errors) {
            this.success = success;
            this.warpsImported = warpsImported;
            this.playersImported = playersImported;
            this.homesImported = homesImported;
            this.errors = new ArrayList<>(errors);
        }
        
        public boolean isSuccess() { return success; }
        public int getWarpsImported() { return warpsImported; }
        public int getPlayersImported() { return playersImported; }
        public int getHomesImported() { return homesImported; }
        public List<String> getErrors() { return errors; }
        
        public int getTotalImported() {
            return warpsImported + homesImported;
        }
    }
}
