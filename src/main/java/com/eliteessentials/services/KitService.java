package com.eliteessentials.services;

import com.eliteessentials.model.Kit;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Service for managing kits - loading, saving, and cooldown tracking.
 */
public class KitService {

    private static final Logger logger = Logger.getLogger("EliteEssentials");
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private final File dataFolder;
    private final Map<String, Kit> kits = new LinkedHashMap<>();
    // Player UUID -> Kit ID -> Last use timestamp
    private final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();
    // Player UUID -> Kit ID -> true if claimed (for onetime kits)
    private final Map<UUID, Set<String>> onetimeClaimed = new ConcurrentHashMap<>();
    
    // Lock for file I/O operations to prevent concurrent writes
    private final Object fileLock = new Object();

    public KitService(File dataFolder) {
        this.dataFolder = dataFolder;
        loadKits();
        loadOnetimeClaims();
    }

    /**
     * Load kits from kits.json
     */
    public void loadKits() {
        File kitsFile = new File(dataFolder, "kits.json");
        
        if (!kitsFile.exists()) {
            createDefaultKits();
            return;
        }

        try (Reader reader = new InputStreamReader(new FileInputStream(kitsFile), StandardCharsets.UTF_8)) {
            Type listType = new TypeToken<List<Kit>>(){}.getType();
            List<Kit> loadedKits = gson.fromJson(reader, listType);
            
            kits.clear();
            if (loadedKits != null) {
                for (Kit kit : loadedKits) {
                    kits.put(kit.getId().toLowerCase(), kit);
                }
            }
            logger.info("Loaded " + kits.size() + " kits from kits.json");
        } catch (Exception e) {
            logger.severe("Failed to load kits.json: " + e.getMessage());
            createDefaultKits();
        }
    }

    /**
     * Save kits to kits.json
     */
    public void saveKits() {
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        File kitsFile = new File(dataFolder, "kits.json");
        synchronized (fileLock) {
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(kitsFile), StandardCharsets.UTF_8)) {
                gson.toJson(new ArrayList<>(kits.values()), writer);
                logger.info("Saved " + kits.size() + " kits to kits.json");
            } catch (Exception e) {
                logger.severe("Failed to save kits.json: " + e.getMessage());
            }
        }
    }

    /**
     * Create default starter kit
     */
    private void createDefaultKits() {
        // Don't create any default kits - start with empty list
        logger.info("No kits.json found, starting with 0 kits");
        saveKits();
    }

    /**
     * Get a kit by ID
     */
    public Kit getKit(String kitId) {
        return kits.get(kitId.toLowerCase());
    }

    /**
     * Get all kits
     */
    public Collection<Kit> getAllKits() {
        return kits.values();
    }

    /**
     * Create or update a kit
     */
    public void saveKit(Kit kit) {
        kits.put(kit.getId().toLowerCase(), kit);
        saveKits();
    }

    /**
     * Delete a kit
     */
    public boolean deleteKit(String kitId) {
        Kit removed = kits.remove(kitId.toLowerCase());
        if (removed != null) {
            saveKits();
            return true;
        }
        return false;
    }

    /**
     * Get remaining cooldown for a player's kit usage
     * @return Remaining seconds, or 0 if not on cooldown
     */
    public long getRemainingCooldown(UUID playerId, String kitId) {
        Kit kit = getKit(kitId);
        if (kit == null || kit.getCooldown() <= 0) {
            return 0;
        }

        Map<String, Long> playerCooldowns = cooldowns.get(playerId);
        if (playerCooldowns == null) {
            return 0;
        }

        Long lastUsed = playerCooldowns.get(kitId.toLowerCase());
        if (lastUsed == null) {
            return 0;
        }

        long elapsed = (System.currentTimeMillis() - lastUsed) / 1000;
        long remaining = kit.getCooldown() - elapsed;
        return Math.max(0, remaining);
    }

    /**
     * Set cooldown for a player's kit usage
     */
    public void setKitUsed(UUID playerId, String kitId) {
        cooldowns.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>())
                 .put(kitId.toLowerCase(), System.currentTimeMillis());
    }

    /**
     * Clear cooldowns for a player (on disconnect or admin command)
     */
    public void clearCooldowns(UUID playerId) {
        cooldowns.remove(playerId);
    }

    /**
     * Reload kits from file
     */
    public void reload() {
        loadKits();
        loadOnetimeClaims();
    }

    /**
     * Check if player has already claimed a one-time kit
     */
    public boolean hasClaimedOnetime(UUID playerId, String kitId) {
        Set<String> claimed = onetimeClaimed.get(playerId);
        boolean hasClaimed = claimed != null && claimed.contains(kitId.toLowerCase());
        // Only log if debug is enabled - need to get config from EliteEssentials instance
        if (hasClaimed) {
            try {
                com.eliteessentials.EliteEssentials plugin = com.eliteessentials.EliteEssentials.getInstance();
                if (plugin != null && plugin.getConfigManager().isDebugEnabled()) {
                    logger.info("hasClaimedOnetime check: playerId=" + playerId + ", kitId=" + kitId + 
                               ", claimed=" + hasClaimed + ", claimedSet=" + claimed);
                }
            } catch (Exception e) {
                // Ignore if we can't get config
            }
        }
        return hasClaimed;
    }

    /**
     * Mark a one-time kit as claimed
     */
    public void setOnetimeClaimed(UUID playerId, String kitId) {
        try {
            com.eliteessentials.EliteEssentials plugin = com.eliteessentials.EliteEssentials.getInstance();
            if (plugin != null && plugin.getConfigManager().isDebugEnabled()) {
                logger.info("setOnetimeClaimed called: playerId=" + playerId + ", kitId=" + kitId);
            }
        } catch (Exception e) {
            // Ignore if we can't get config
        }
        
        onetimeClaimed.computeIfAbsent(playerId, k -> ConcurrentHashMap.newKeySet())
                      .add(kitId.toLowerCase());
        
        try {
            com.eliteessentials.EliteEssentials plugin = com.eliteessentials.EliteEssentials.getInstance();
            if (plugin != null && plugin.getConfigManager().isDebugEnabled()) {
                logger.info("After adding, onetimeClaimed for player: " + onetimeClaimed.get(playerId));
            }
        } catch (Exception e) {
            // Ignore if we can't get config
        }
        
        saveOnetimeClaims();
    }

    /**
     * Get all starter kits (kits named "starter" are auto-given to new players)
     */
    public List<Kit> getStarterKits() {
        List<Kit> starters = new ArrayList<>();
        for (Kit kit : kits.values()) {
            // A kit is a starter kit if its ID is "starter" (case-insensitive)
            if (kit.getId().equalsIgnoreCase("starter")) {
                starters.add(kit);
            }
        }
        return starters;
    }

    /**
     * Load one-time kit claims from file
     */
    private void loadOnetimeClaims() {
        File claimsFile = new File(dataFolder, "kit_claims.json");
        
        if (!claimsFile.exists()) {
            return;
        }

        try (Reader reader = new InputStreamReader(new FileInputStream(claimsFile), StandardCharsets.UTF_8)) {
            Type mapType = new TypeToken<Map<String, List<String>>>(){}.getType();
            Map<String, List<String>> loadedClaims = gson.fromJson(reader, mapType);
            
            onetimeClaimed.clear();
            if (loadedClaims != null) {
                for (Map.Entry<String, List<String>> entry : loadedClaims.entrySet()) {
                    try {
                        UUID playerId = UUID.fromString(entry.getKey());
                        Set<String> kitIds = ConcurrentHashMap.newKeySet();
                        kitIds.addAll(entry.getValue());
                        onetimeClaimed.put(playerId, kitIds);
                    } catch (IllegalArgumentException e) {
                        logger.warning("Invalid UUID in kit_claims.json: " + entry.getKey());
                    }
                }
            }
            logger.info("Loaded one-time kit claims for " + onetimeClaimed.size() + " players");
        } catch (Exception e) {
            logger.severe("Failed to load kit_claims.json: " + e.getMessage());
        }
    }

    /**
     * Save one-time kit claims to file
     */
    private void saveOnetimeClaims() {
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        File claimsFile = new File(dataFolder, "kit_claims.json");
        
        // Convert to serializable format (UUID -> String)
        Map<String, List<String>> serializableClaims = new LinkedHashMap<>();
        for (Map.Entry<UUID, Set<String>> entry : onetimeClaimed.entrySet()) {
            serializableClaims.put(entry.getKey().toString(), new ArrayList<>(entry.getValue()));
        }
        
        synchronized (fileLock) {
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(claimsFile), StandardCharsets.UTF_8)) {
                gson.toJson(serializableClaims, writer);
            } catch (Exception e) {
                logger.severe("Failed to save kit_claims.json: " + e.getMessage());
            }
        }
    }
}
