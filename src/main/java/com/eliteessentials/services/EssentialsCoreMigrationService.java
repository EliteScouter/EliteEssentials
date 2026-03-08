package com.eliteessentials.services;

import com.eliteessentials.model.*;
import com.eliteessentials.storage.PlayerFileStorage;
import com.eliteessentials.storage.SpawnStorage;
import com.eliteessentials.storage.WarpStorage;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.logging.Logger;

/**
 * Migrates data from nhulston's EssentialsCore plugin to EliteEssentials.
 * 
 * Source: mods/com.nhulston_Essentials/
 * - warps.json (JSON object: name -> location)
 * - spawn.json (single location object)
 * - kits.toml (TOML format)
 * - uuids.json (player name -> UUID cache)
 * - players/{uuid}.json (homes and kitCooldowns)
 */
public class EssentialsCoreMigrationService {
    
    private static final Logger logger = Logger.getLogger("EliteEssentials");
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    
    private final File modsFolder;
    private final WarpStorage warpStorage;
    private final SpawnStorage spawnStorage;
    private final KitService kitService;
    private final PlayerFileStorage playerFileStorage;
    
    // Migration stats
    private int warpsImported = 0;
    private int spawnsImported = 0;
    private int kitsImported = 0;
    private int playerFilesFound = 0;
    private int playersImported = 0;
    private int playersSkippedExist = 0;
    private int homesImported = 0;
    private int kitCooldownsImported = 0;
    private final List<String> errors = new ArrayList<>();
    
    public EssentialsCoreMigrationService(File dataFolder, WarpStorage warpStorage, SpawnStorage spawnStorage,
                                          KitService kitService, PlayerFileStorage playerFileStorage) {
        // Go up from EliteEssentials folder to mods folder
        this.modsFolder = dataFolder.getParentFile();
        this.warpStorage = warpStorage;
        this.spawnStorage = spawnStorage;
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
     * @param force if true, overwrite existing homes/cooldowns with EssentialsCore data
     * @return MigrationResult with stats and any errors
     */
    public MigrationResult migrate(boolean force) {
        // Reset stats
        warpsImported = 0;
        spawnsImported = 0;
        kitsImported = 0;
        playerFilesFound = 0;
        playersImported = 0;
        playersSkippedExist = 0;
        homesImported = 0;
        kitCooldownsImported = 0;
        errors.clear();
        
        File essentialsFolder = getEssentialsCoreFolder();
        
        if (!essentialsFolder.exists()) {
            errors.add("EssentialsCore folder not found at: " + essentialsFolder.getAbsolutePath());
            return buildResult(false);
        }
        
        logger.info("[Migration] ========================================");
        logger.info("[Migration] Starting EssentialsCore migration" + (force ? " (FORCE MODE)" : "") + "...");
        logger.info("[Migration] Source: " + essentialsFolder.getAbsolutePath());
        logger.info("[Migration] ========================================");
        
        // Load UUID->name cache from uuids.json
        Map<String, String> uuidToName = loadUuidNameCache(essentialsFolder);
        
        // Migrate warps
        migrateWarps(essentialsFolder);
        
        // Migrate spawn
        migrateSpawn(essentialsFolder);
        
        // Migrate kits
        migrateKits(essentialsFolder);
        
        // Migrate player data (homes + kit cooldowns)
        migratePlayerData(essentialsFolder, uuidToName, force);
        
        logger.info("[Migration] ========================================");
        logger.info("[Migration] Migration complete!");
        logger.info("[Migration] - Warps: " + warpsImported);
        logger.info("[Migration] - Spawns: " + spawnsImported);
        logger.info("[Migration] - Kits: " + kitsImported);
        logger.info("[Migration] - Player files found: " + playerFilesFound);
        logger.info("[Migration] - Players migrated: " + playersImported);
        if (playersSkippedExist > 0) {
            logger.info("[Migration] - Players skipped (already migrated): " + playersSkippedExist);
        }
        logger.info("[Migration] - Homes: " + homesImported);
        logger.info("[Migration] - Kit cooldowns: " + kitCooldownsImported);
        if (!errors.isEmpty()) {
            logger.info("[Migration] - Errors: " + errors.size());
        }
        logger.info("[Migration] ========================================");
        
        return buildResult(errors.isEmpty());
    }
    
    /** Convenience overload for non-force migration. */
    public MigrationResult migrate() {
        return migrate(false);
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
     * Migrate spawn from EssentialsCore spawn.json.
     * Format: { "world": "explore", "x": 1.0, "y": 2.0, "z": 3.0, "yaw": 0.0, "pitch": 0.0 }
     */
    private void migrateSpawn(File essentialsFolder) {
        File spawnFile = new File(essentialsFolder, "spawn.json");
        if (!spawnFile.exists()) {
            logger.info("[Migration] No spawn.json found, skipping spawn migration.");
            return;
        }
        
        logger.info("[Migration] Migrating spawn.json...");
        
        try (Reader reader = new InputStreamReader(new FileInputStream(spawnFile), StandardCharsets.UTF_8)) {
            EssentialsCoreLocation ecSpawn = gson.fromJson(reader, EssentialsCoreLocation.class);
            
            if (ecSpawn == null || ecSpawn.world == null) {
                logger.info("[Migration] - spawn.json is empty or invalid.");
                return;
            }
            
            // Check if a spawn already exists for this world
            if (spawnStorage.hasSpawn(ecSpawn.world)) {
                logger.info("[Migration] - Skipping spawn for world '" + ecSpawn.world + "' (already exists)");
                return;
            }
            
            spawnStorage.setSpawn(ecSpawn.world, ecSpawn.x, ecSpawn.y, ecSpawn.z, ecSpawn.yaw, ecSpawn.pitch);
            spawnsImported++;
            logger.info("[Migration] - Imported spawn for world: " + ecSpawn.world + 
                " at " + String.format("%.1f, %.1f, %.1f", ecSpawn.x, ecSpawn.y, ecSpawn.z));
            
        } catch (Exception e) {
            String error = "Failed to migrate spawn: " + e.getMessage();
            logger.severe("[Migration] " + error);
            errors.add(error);
        }
    }
    
    /**
     * Load the UUID-to-name cache from uuids.json.
     * Format: { "playerName": "uuid-string", ... }
     * Returns a map of UUID string -> player name for reverse lookups.
     */
    private Map<String, String> loadUuidNameCache(File essentialsFolder) {
        Map<String, String> uuidToName = new HashMap<>();
        File uuidsFile = new File(essentialsFolder, "uuids.json");
        
        if (!uuidsFile.exists()) {
            logger.info("[Migration] No uuids.json found, player names will default to 'Unknown'.");
            return uuidToName;
        }
        
        try (Reader reader = new InputStreamReader(new FileInputStream(uuidsFile), StandardCharsets.UTF_8)) {
            Type type = new TypeToken<Map<String, String>>(){}.getType();
            Map<String, String> nameToUuid = gson.fromJson(reader, type);
            
            if (nameToUuid != null) {
                for (Map.Entry<String, String> entry : nameToUuid.entrySet()) {
                    uuidToName.put(entry.getValue(), entry.getKey());
                }
                logger.info("[Migration] Loaded " + uuidToName.size() + " player name(s) from uuids.json");
            }
        } catch (Exception e) {
            logger.warning("[Migration] Failed to load uuids.json: " + e.getMessage());
        }
        
        return uuidToName;
    }
    
    /**
     * Migrate player data from EssentialsCore players/{uuid}.json files.
     * Migrates homes AND kit cooldowns.
     * Format: { "homes": { "homeName": { world, x, y, z, yaw, pitch, createdAt } }, "kitCooldowns": { "kitId": timestamp } }
     */
    private void migratePlayerData(File essentialsFolder, Map<String, String> uuidToName, boolean force) {
        File playersFolder = new File(essentialsFolder, "players");
        if (!playersFolder.exists() || !playersFolder.isDirectory()) {
            logger.info("[Migration] No players folder found at: " + playersFolder.getAbsolutePath());
            logger.info("[Migration] Skipping player data migration.");
            return;
        }
        
        File[] playerFiles = playersFolder.listFiles((dir, name) -> name.endsWith(".json"));
        if (playerFiles == null || playerFiles.length == 0) {
            logger.info("[Migration] Players folder exists but contains no .json files: " + playersFolder.getAbsolutePath());
            return;
        }
        
        logger.info("[Migration] Migrating player data (homes + kit cooldowns)...");
        playerFilesFound = playerFiles.length;
        logger.info("[Migration] Found " + playerFilesFound + " player file(s) in: " + playersFolder.getAbsolutePath());
        
        int skippedEmpty = 0;
        
        for (File playerFile : playerFiles) {
            try {
                String filename = playerFile.getName();
                String uuidStr = filename.replace(".json", "");
                UUID uuid = UUID.fromString(uuidStr);
                
                try (Reader reader = new InputStreamReader(new FileInputStream(playerFile), StandardCharsets.UTF_8)) {
                    EssentialsCorePlayer ecPlayer = gson.fromJson(reader, EssentialsCorePlayer.class);
                    
                    if (ecPlayer == null) {
                        skippedEmpty++;
                        continue;
                    }
                    
                    boolean hasHomes = ecPlayer.homes != null && !ecPlayer.homes.isEmpty();
                    boolean hasCooldowns = ecPlayer.kitCooldowns != null && !ecPlayer.kitCooldowns.isEmpty();
                    
                    if (!hasHomes && !hasCooldowns) {
                        skippedEmpty++;
                        continue;
                    }
                    
                    // Resolve player name from uuids.json cache
                    String playerName = uuidToName.getOrDefault(uuidStr, "Unknown");
                    
                    PlayerFile ourPlayer = playerFileStorage.getPlayer(uuid);
                    if (ourPlayer == null) {
                        ourPlayer = playerFileStorage.getPlayer(uuid, playerName);
                    } else if ("Unknown".equals(ourPlayer.getName()) && !"Unknown".equals(playerName)) {
                        ourPlayer.setName(playerName);
                    }
                    
                    // Migrate homes
                    int homesForPlayer = 0;
                    int homesSkipped = 0;
                    if (hasHomes) {
                        for (Map.Entry<String, EssentialsCoreHome> entry : ecPlayer.homes.entrySet()) {
                            String homeName = entry.getKey();
                            EssentialsCoreHome ecHome = entry.getValue();
                            
                            if (homeName == null || homeName.isEmpty()) continue;
                            if (!force && ourPlayer.hasHome(homeName)) {
                                homesSkipped++;
                                continue;
                            }
                            
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
                    }
                    
                    // Migrate kit cooldowns
                    int cooldownsForPlayer = 0;
                    if (hasCooldowns) {
                        for (Map.Entry<String, Long> entry : ecPlayer.kitCooldowns.entrySet()) {
                            String kitId = entry.getKey();
                            Long lastUsed = entry.getValue();
                            
                            if (kitId == null || lastUsed == null) continue;
                            
                            if (force || ourPlayer.getKitLastUsed(kitId) == 0L) {
                                ourPlayer.getKitCooldowns().put(kitId.toLowerCase(), lastUsed);
                                cooldownsForPlayer++;
                                kitCooldownsImported++;
                            }
                        }
                    }
                    
                    boolean changed = homesForPlayer > 0 || cooldownsForPlayer > 0;
                    
                    if (changed) {
                        playerFileStorage.saveAndMarkDirty(uuid);
                        playersImported++;
                        logger.info("[Migration] - Player " + playerName + " (" + uuidStr + "): " +
                            homesForPlayer + " homes, " + cooldownsForPlayer + " kit cooldowns");
                    } else if (homesSkipped > 0) {
                        playersSkippedExist++;
                    }
                }
                
            } catch (IllegalArgumentException e) {
                logger.warning("[Migration] - Skipping invalid player file: " + playerFile.getName());
            } catch (Exception e) {
                String error = "Failed to migrate player " + playerFile.getName() + ": " + e.getMessage();
                logger.warning("[Migration] " + error);
                errors.add(error);
            }
        }
        
        logger.info("[Migration] - Migrated " + homesImported + " homes and " + kitCooldownsImported + 
            " kit cooldowns for " + playersImported + " players");
        if (skippedEmpty > 0) {
            logger.info("[Migration] - Skipped " + skippedEmpty + " player(s) with no homes/cooldowns");
        }
        if (playersSkippedExist > 0) {
            logger.info("[Migration] - Skipped " + playersSkippedExist + " player(s) whose homes already exist (previously migrated)");
        }
    }

    
    private MigrationResult buildResult(boolean success) {
        return new MigrationResult(success, warpsImported, spawnsImported, kitsImported,
            playerFilesFound, playersImported, playersSkippedExist, homesImported, kitCooldownsImported, errors);
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
        private final int spawnsImported;
        private final int kitsImported;
        private final int playerFilesFound;
        private final int playersImported;
        private final int playersSkippedExist;
        private final int homesImported;
        private final int kitCooldownsImported;
        private final List<String> errors;
        
        public MigrationResult(boolean success, int warpsImported, int spawnsImported, int kitsImported, 
                              int playerFilesFound, int playersImported, int playersSkippedExist,
                              int homesImported, int kitCooldownsImported, List<String> errors) {
            this.success = success;
            this.warpsImported = warpsImported;
            this.spawnsImported = spawnsImported;
            this.kitsImported = kitsImported;
            this.playerFilesFound = playerFilesFound;
            this.playersImported = playersImported;
            this.playersSkippedExist = playersSkippedExist;
            this.homesImported = homesImported;
            this.kitCooldownsImported = kitCooldownsImported;
            this.errors = new ArrayList<>(errors);
        }
        
        public boolean isSuccess() { return success; }
        public int getWarpsImported() { return warpsImported; }
        public int getSpawnsImported() { return spawnsImported; }
        public int getKitsImported() { return kitsImported; }
        public int getPlayerFilesFound() { return playerFilesFound; }
        public int getPlayersImported() { return playersImported; }
        public int getPlayersSkippedExist() { return playersSkippedExist; }
        public int getHomesImported() { return homesImported; }
        public int getKitCooldownsImported() { return kitCooldownsImported; }
        public List<String> getErrors() { return errors; }
        
        public int getTotalImported() {
            return warpsImported + spawnsImported + kitsImported + homesImported + kitCooldownsImported;
        }
    }
}
