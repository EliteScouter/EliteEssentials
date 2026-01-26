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
import java.nio.file.Files;
import java.time.Instant;
import java.util.*;
import java.util.logging.Logger;

/**
 * Migrates data from nhulston's EssentialsCore plugin to EliteEssentials.
 * 
 * Source: mods/com.nhulston_Essentials/
 * - warps.json (JSON object: name -> location)
 * - kits.toml (TOML format)
 * - players/{uuid}.json (homes and kitCooldowns)
 */
public class EssentialsCoreMigrationService {
    
    private static final Logger logger = Logger.getLogger("EliteEssentials");
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    
    private final File modsFolder;
    private final WarpStorage warpStorage;
    private final KitService kitService;
    private final PlayerFileStorage playerFileStorage;
    
    // Migration stats
    private int warpsImported = 0;
    private int kitsImported = 0;
    private int playersImported = 0;
    private int homesImported = 0;
    private final List<String> errors = new ArrayList<>();
    
    public EssentialsCoreMigrationService(File dataFolder, WarpStorage warpStorage, 
                                          KitService kitService, PlayerFileStorage playerFileStorage) {
        // Go up from EliteEssentials folder to mods folder
        this.modsFolder = dataFolder.getParentFile();
        this.warpStorage = warpStorage;
        this.kitService = kitService;
        this.playerFileStorage = playerFileStorage;
    }
    
    /**
     * Check if EssentialsCore data exists.
     */
    public boolean hasEssentialsCoreData() {
        File essentialsFolder = new File(modsFolder, "com.nhulston_Essentials");
        return essentialsFolder.exists() && essentialsFolder.isDirectory();
    }
    
    /**
     * Get the EssentialsCore folder path.
     */
    public File getEssentialsCoreFolder() {
        return new File(modsFolder, "com.nhulston_Essentials");
    }
    
    /**
     * Run the full migration.
     * @return MigrationResult with stats and any errors
     */
    public MigrationResult migrate() {
        // Reset stats
        warpsImported = 0;
        kitsImported = 0;
        playersImported = 0;
        homesImported = 0;
        errors.clear();
        
        File essentialsFolder = getEssentialsCoreFolder();
        
        if (!essentialsFolder.exists()) {
            errors.add("EssentialsCore folder not found at: " + essentialsFolder.getAbsolutePath());
            return new MigrationResult(false, warpsImported, kitsImported, playersImported, homesImported, errors);
        }
        
        logger.info("[Migration] ========================================");
        logger.info("[Migration] Starting EssentialsCore migration...");
        logger.info("[Migration] Source: " + essentialsFolder.getAbsolutePath());
        logger.info("[Migration] ========================================");
        
        // Migrate warps
        migrateWarps(essentialsFolder);
        
        // Migrate kits
        migrateKits(essentialsFolder);
        
        // Migrate player homes
        migratePlayerHomes(essentialsFolder);
        
        logger.info("[Migration] ========================================");
        logger.info("[Migration] Migration complete!");
        logger.info("[Migration] - Warps: " + warpsImported);
        logger.info("[Migration] - Kits: " + kitsImported);
        logger.info("[Migration] - Players: " + playersImported);
        logger.info("[Migration] - Homes: " + homesImported);
        if (!errors.isEmpty()) {
            logger.info("[Migration] - Errors: " + errors.size());
        }
        logger.info("[Migration] ========================================");
        
        return new MigrationResult(errors.isEmpty(), warpsImported, kitsImported, playersImported, homesImported, errors);
    }

    
    /**
     * Migrate warps from EssentialsCore warps.json.
     * Format: { "warpName": { "world": "default", "x": 1.0, "y": 2.0, "z": 3.0, "yaw": 0.0, "pitch": 0.0 } }
     */
    private void migrateWarps(File essentialsFolder) {
        File warpsFile = new File(essentialsFolder, "warps.json");
        if (!warpsFile.exists()) {
            logger.info("[Migration] No warps.json found, skipping warp migration.");
            return;
        }
        
        logger.info("[Migration] Migrating warps.json...");
        
        try (Reader reader = new InputStreamReader(new FileInputStream(warpsFile), StandardCharsets.UTF_8)) {
            Type type = new TypeToken<Map<String, EssentialsCoreLocation>>(){}.getType();
            Map<String, EssentialsCoreLocation> ecWarps = gson.fromJson(reader, type);
            
            if (ecWarps == null || ecWarps.isEmpty()) {
                logger.info("[Migration] - No warps found in file.");
                return;
            }
            
            for (Map.Entry<String, EssentialsCoreLocation> entry : ecWarps.entrySet()) {
                String warpName = entry.getKey();
                EssentialsCoreLocation ecLoc = entry.getValue();
                
                // Check if warp already exists
                if (warpStorage.hasWarp(warpName)) {
                    logger.info("[Migration] - Skipping warp '" + warpName + "' (already exists)");
                    continue;
                }
                
                // Convert to our format
                Location location = new Location(
                    ecLoc.world,
                    ecLoc.x,
                    ecLoc.y,
                    ecLoc.z,
                    ecLoc.yaw,
                    ecLoc.pitch
                );
                
                Warp warp = new Warp(warpName, location, Warp.Permission.ALL, "EssentialsCore Migration");
                warpStorage.setWarp(warp);
                warpsImported++;
                logger.info("[Migration] - Imported warp: " + warpName);
            }
            
        } catch (Exception e) {
            String error = "Failed to migrate warps: " + e.getMessage();
            logger.severe("[Migration] " + error);
            errors.add(error);
        }
    }
    
    /**
     * Migrate kits from EssentialsCore kits.toml.
     * TOML format with [kits.kitname] sections.
     */
    private void migrateKits(File essentialsFolder) {
        File kitsFile = new File(essentialsFolder, "kits.toml");
        if (!kitsFile.exists()) {
            logger.info("[Migration] No kits.toml found, skipping kit migration.");
            return;
        }
        
        logger.info("[Migration] Migrating kits.toml...");
        
        try {
            List<String> lines = Files.readAllLines(kitsFile.toPath(), StandardCharsets.UTF_8);
            Map<String, EssentialsCoreKit> parsedKits = parseTomlKits(lines);
            
            if (parsedKits.isEmpty()) {
                logger.info("[Migration] - No kits found in file.");
                return;
            }
            
            for (Map.Entry<String, EssentialsCoreKit> entry : parsedKits.entrySet()) {
                String kitId = entry.getKey();
                EssentialsCoreKit ecKit = entry.getValue();
                
                // Check if kit already exists
                if (kitService.getKit(kitId) != null) {
                    logger.info("[Migration] - Skipping kit '" + kitId + "' (already exists)");
                    continue;
                }
                
                // Convert items
                List<KitItem> items = new ArrayList<>();
                for (EssentialsCoreKitItem ecItem : ecKit.items) {
                    items.add(new KitItem(
                        ecItem.itemId,
                        ecItem.quantity,
                        ecItem.section,
                        ecItem.slot
                    ));
                }
                
                // Create kit with our format
                // EssentialsCore uses type="add" vs "replace", we use replaceInventory boolean
                boolean replaceInventory = "replace".equalsIgnoreCase(ecKit.type);
                
                Kit kit = new Kit(
                    kitId,
                    ecKit.displayName != null ? ecKit.displayName : kitId,
                    "Imported from EssentialsCore",
                    null, // icon
                    ecKit.cooldown,
                    replaceInventory,
                    false, // onetime
                    kitId.equalsIgnoreCase("starter"), // starterKit if named "starter"
                    items
                );
                
                kitService.saveKit(kit);
                kitsImported++;
                logger.info("[Migration] - Imported kit: " + kitId + " (" + items.size() + " items)");
            }
            
        } catch (Exception e) {
            String error = "Failed to migrate kits: " + e.getMessage();
            logger.severe("[Migration] " + error);
            errors.add(error);
        }
    }

    
    /**
     * Parse TOML kit format into our structure.
     * Handles [kits.kitname] sections with display-name, cooldown, type, and [[kits.kitname.items]] arrays.
     */
    private Map<String, EssentialsCoreKit> parseTomlKits(List<String> lines) {
        Map<String, EssentialsCoreKit> kits = new LinkedHashMap<>();
        
        String currentKitId = null;
        EssentialsCoreKit currentKit = null;
        EssentialsCoreKitItem currentItem = null;
        boolean inItemsArray = false;
        
        for (String line : lines) {
            line = line.trim();
            
            // Skip comments and empty lines
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            
            // Check for kit section header: [kits.kitname]
            if (line.startsWith("[kits.") && !line.contains(".items]]")) {
                // Save previous item if any
                if (currentItem != null && currentKit != null) {
                    currentKit.items.add(currentItem);
                    currentItem = null;
                }
                
                // Extract kit name
                String kitSection = line.substring(6); // Remove "[kits."
                kitSection = kitSection.replace("]", "");
                currentKitId = kitSection;
                currentKit = new EssentialsCoreKit();
                kits.put(currentKitId, currentKit);
                inItemsArray = false;
                continue;
            }
            
            // Check for items array header: [[kits.kitname.items]]
            if (line.startsWith("[[kits.") && line.endsWith(".items]]")) {
                // Save previous item if any
                if (currentItem != null && currentKit != null) {
                    currentKit.items.add(currentItem);
                }
                currentItem = new EssentialsCoreKitItem();
                inItemsArray = true;
                continue;
            }
            
            // Parse key-value pairs
            if (line.contains("=") && currentKit != null) {
                int eqIndex = line.indexOf('=');
                String key = line.substring(0, eqIndex).trim();
                String value = line.substring(eqIndex + 1).trim();
                
                // Remove quotes from string values
                if (value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1);
                }
                
                if (inItemsArray && currentItem != null) {
                    // Item properties
                    switch (key) {
                        case "item-id":
                            currentItem.itemId = value;
                            break;
                        case "quantity":
                            currentItem.quantity = parseIntSafe(value, 1);
                            break;
                        case "section":
                            currentItem.section = value;
                            break;
                        case "slot":
                            currentItem.slot = parseIntSafe(value, 0);
                            break;
                    }
                } else {
                    // Kit properties
                    switch (key) {
                        case "display-name":
                            currentKit.displayName = value;
                            break;
                        case "cooldown":
                            currentKit.cooldown = parseIntSafe(value, 0);
                            break;
                        case "type":
                            currentKit.type = value;
                            break;
                    }
                }
            }
        }
        
        // Save last item if any
        if (currentItem != null && currentKit != null) {
            currentKit.items.add(currentItem);
        }
        
        return kits;
    }
    
    private int parseIntSafe(String value, int defaultValue) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    /**
     * Migrate player homes from EssentialsCore players/{uuid}.json files.
     * Format: { "homes": { "homeName": { world, x, y, z, yaw, pitch, createdAt } }, "kitCooldowns": {} }
     */
    private void migratePlayerHomes(File essentialsFolder) {
        File playersFolder = new File(essentialsFolder, "players");
        if (!playersFolder.exists() || !playersFolder.isDirectory()) {
            logger.info("[Migration] No players folder found, skipping home migration.");
            return;
        }
        
        logger.info("[Migration] Migrating player homes...");
        
        File[] playerFiles = playersFolder.listFiles((dir, name) -> name.endsWith(".json"));
        if (playerFiles == null || playerFiles.length == 0) {
            logger.info("[Migration] - No player files found.");
            return;
        }
        
        for (File playerFile : playerFiles) {
            try {
                // Extract UUID from filename
                String filename = playerFile.getName();
                String uuidStr = filename.replace(".json", "");
                UUID uuid = UUID.fromString(uuidStr);
                
                // Read EssentialsCore player file
                try (Reader reader = new InputStreamReader(new FileInputStream(playerFile), StandardCharsets.UTF_8)) {
                    EssentialsCorePlayer ecPlayer = gson.fromJson(reader, EssentialsCorePlayer.class);
                    
                    if (ecPlayer == null || ecPlayer.homes == null || ecPlayer.homes.isEmpty()) {
                        continue;
                    }
                    
                    // Get or create our player file
                    PlayerFile ourPlayer = playerFileStorage.getPlayer(uuid);
                    if (ourPlayer == null) {
                        // Create new player with unknown name (will update when they join)
                        ourPlayer = playerFileStorage.getPlayer(uuid, "Unknown");
                    }
                    
                    int homesForPlayer = 0;
                    for (Map.Entry<String, EssentialsCoreHome> entry : ecPlayer.homes.entrySet()) {
                        String homeName = entry.getKey();
                        EssentialsCoreHome ecHome = entry.getValue();
                        
                        // Skip if home already exists
                        if (ourPlayer.hasHome(homeName)) {
                            continue;
                        }
                        
                        // Convert to our format
                        Location location = new Location(
                            ecHome.world,
                            ecHome.x,
                            ecHome.y,
                            ecHome.z,
                            ecHome.yaw,
                            ecHome.pitch
                        );
                        
                        Home home = new Home(homeName, location);
                        if (ecHome.createdAt > 0) {
                            home.setCreatedAt(ecHome.createdAt);
                        }
                        
                        ourPlayer.setHome(home);
                        homesForPlayer++;
                        homesImported++;
                    }
                    
                    if (homesForPlayer > 0) {
                        playerFileStorage.saveAndMarkDirty(uuid);
                        playersImported++;
                    }
                }
                
            } catch (IllegalArgumentException e) {
                // Invalid UUID in filename, skip
                logger.warning("[Migration] - Skipping invalid player file: " + playerFile.getName());
            } catch (Exception e) {
                String error = "Failed to migrate player " + playerFile.getName() + ": " + e.getMessage();
                logger.warning("[Migration] " + error);
                errors.add(error);
            }
        }
        
        logger.info("[Migration] - Migrated " + homesImported + " homes for " + playersImported + " players");
    }

    
    // ==================== Inner Classes for EssentialsCore Format ====================
    
    /**
     * EssentialsCore location format.
     */
    private static class EssentialsCoreLocation {
        String world;
        double x;
        double y;
        double z;
        float yaw;
        float pitch;
    }
    
    /**
     * EssentialsCore home format (same as location but with createdAt).
     */
    private static class EssentialsCoreHome {
        String world;
        double x;
        double y;
        double z;
        float yaw;
        float pitch;
        long createdAt;
    }
    
    /**
     * EssentialsCore player file format.
     */
    private static class EssentialsCorePlayer {
        Map<String, EssentialsCoreHome> homes;
        Map<String, Long> kitCooldowns;
    }
    
    /**
     * EssentialsCore kit format (parsed from TOML).
     */
    private static class EssentialsCoreKit {
        String displayName;
        int cooldown;
        String type = "add";
        List<EssentialsCoreKitItem> items = new ArrayList<>();
    }
    
    /**
     * EssentialsCore kit item format.
     */
    private static class EssentialsCoreKitItem {
        String itemId;
        int quantity = 1;
        String section = "hotbar";
        int slot = 0;
    }
    
    // ==================== Migration Result ====================
    
    /**
     * Result of a migration operation.
     */
    public static class MigrationResult {
        private final boolean success;
        private final int warpsImported;
        private final int kitsImported;
        private final int playersImported;
        private final int homesImported;
        private final List<String> errors;
        
        public MigrationResult(boolean success, int warpsImported, int kitsImported, 
                              int playersImported, int homesImported, List<String> errors) {
            this.success = success;
            this.warpsImported = warpsImported;
            this.kitsImported = kitsImported;
            this.playersImported = playersImported;
            this.homesImported = homesImported;
            this.errors = new ArrayList<>(errors);
        }
        
        public boolean isSuccess() { return success; }
        public int getWarpsImported() { return warpsImported; }
        public int getKitsImported() { return kitsImported; }
        public int getPlayersImported() { return playersImported; }
        public int getHomesImported() { return homesImported; }
        public List<String> getErrors() { return errors; }
        
        public int getTotalImported() {
            return warpsImported + kitsImported + homesImported;
        }
    }
}
