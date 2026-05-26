package com.eliteessentials.services;

import com.eliteessentials.model.Kit;
import com.eliteessentials.model.PlayerFile;
import com.eliteessentials.permissions.PermissionService;
import com.eliteessentials.storage.PlayerStorageProvider;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Logger;

/**
 * Service for managing kits - loading, saving, and cooldown tracking.
 * Kit definitions are stored in kits.json (server-wide).
 * Kit claims and cooldowns are stored in per-player files via PlayerFileStorage.
 */
public class KitService {

    private static final Logger logger = Logger.getLogger("EliteEssentials");
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private final File dataFolder;
    private final Map<String, Kit> kits = new LinkedHashMap<>();
    private PlayerStorageProvider playerFileStorage;
    
    // Lock for file I/O operations to prevent concurrent writes
    private final Object fileLock = new Object();

    public KitService(File dataFolder) {
        this.dataFolder = dataFolder;
        loadKits();
    }
    
    /**
     * Set the player file storage (called after initialization).
     */
    public void setPlayerFileStorage(PlayerStorageProvider storage) {
        this.playerFileStorage = storage;
    }

    /**
     * Ensure a PlayerFile exists (create + cache + persist if missing) for the given player.
     *
     * This is a self-heal path for the case where {@code PlayerReadyEvent} never fired for the
     * player (observed in practice when a player's initial login is abnormally slow, e.g. a
     * previous handshake timeout plus a long anti-cheat registration). Without this, every
     * KitService read/write silently no-ops because {@code getPlayer(UUID)} returns null for
     * players who are neither cached nor on disk — which previously allowed infinite /kit
     * usage with no cooldown recorded.
     *
     * Callers should invoke this at every kit entry point (command handler, NPC, GUI) before
     * any cooldown / onetime lookup.
     *
     * @return true if the PlayerFile exists or was created; false if storage is unavailable.
     */
    public boolean ensurePlayerFile(UUID playerId, String playerName) {
        if (playerFileStorage == null) {
            logger.warning("[Kit] ensurePlayerFile: storage is null, cannot ensure PlayerFile for " + playerId);
            return false;
        }

        // Fast path: already cached or on disk.
        PlayerFile existing = playerFileStorage.getPlayer(playerId);
        if (existing != null) {
            return true;
        }

        // Slow path: the 2-arg overload creates + caches + indexes + marks dirty. We then
        // force an immediate save so the file survives a crash/restart even if no further
        // operations mark it dirty. This mirrors what PlayerService.onPlayerJoin does for
        // new players.
        PlayerFile created = playerFileStorage.getPlayer(playerId, playerName);
        if (created == null) {
            logger.warning("[Kit] ensurePlayerFile: failed to create PlayerFile for " + playerName + " (" + playerId + ")");
            return false;
        }

        playerFileStorage.saveAndMarkDirty(playerId);
        logger.warning("[Kit] Self-healed missing PlayerFile for " + playerName + " (" + playerId
            + "). This usually means PlayerReadyEvent did not fire for this player at initial join.");
        return true;
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
     * Get remaining cooldown for a player's kit usage.
     * Uses permission-based cooldown if available (per-rank), otherwise kit's default.
     * @return Remaining seconds, or 0 if not on cooldown
     */
    public long getRemainingCooldown(UUID playerId, String kitId) {
        Kit kit = getKit(kitId);
        if (kit == null || kit.getCooldown() <= 0) {
            // Even if kit default is 0, check if a permission-based cooldown applies
            if (kit == null) return 0;
        }

        // Get effective cooldown (permission-based or kit default)
        int effectiveCooldown = getEffectiveCooldown(playerId, kitId);
        if (effectiveCooldown <= 0) {
            return 0;
        }

        if (playerFileStorage == null) {
            return 0;
        }
        
        PlayerFile playerFile = playerFileStorage.getPlayer(playerId);
        if (playerFile == null) {
            // Fail closed: if we cannot read cooldown state, assume the player is still on cooldown.
            // Previously this returned 0, which allowed infinite kit claims when PlayerReadyEvent
            // failed to fire at player join (no PlayerFile existed anywhere). Callers should use
            // ensurePlayerFile() before this path; this branch is defense-in-depth.
            logger.warning("[Kit] PlayerFile missing for " + playerId
                + " on cooldown check for kit '" + kitId
                + "' — treating as on cooldown (fail-closed).");
            return effectiveCooldown;
        }

        long lastUsed = playerFile.getKitLastUsed(kitId);
        if (lastUsed == 0) {
            return 0;
        }

        long elapsed = (System.currentTimeMillis() - lastUsed) / 1000;
        long remaining = effectiveCooldown - elapsed;
        return Math.max(0, remaining);
    }

    /**
     * Get the effective cooldown for a player claiming a kit.
     * Checks permission-based cooldown first (per-rank via LuckPerms), falls back to kit default.
     * @return Cooldown in seconds (0 = no cooldown)
     */
    public int getEffectiveCooldown(UUID playerId, String kitId) {
        Kit kit = getKit(kitId);
        if (kit == null) return 0;
        return PermissionService.get().getKitCooldown(playerId, kit.getId(), kit.getCooldown());
    }

    /**
     * Set cooldown for a player's kit usage
     */
    public void setKitUsed(UUID playerId, String kitId) {
        if (playerFileStorage == null) return;
        
        PlayerFile playerFile = playerFileStorage.getPlayer(playerId);
        if (playerFile == null) {
            // Defense-in-depth: log rather than silently lose the cooldown write.
            // Callers should ensurePlayerFile() before this path.
            logger.warning("[Kit] Cannot record cooldown for kit '" + kitId
                + "' for " + playerId + ": PlayerFile missing (callers should ensurePlayerFile first).");
            return;
        }
        
        playerFile.setKitUsed(kitId);
        playerFileStorage.saveAndMarkDirty(playerId);
    }

    /**
     * Clear cooldowns for a player (on disconnect or admin command)
     */
    public void clearCooldowns(UUID playerId) {
        if (playerFileStorage == null) return;
        
        PlayerFile playerFile = playerFileStorage.getPlayer(playerId);
        if (playerFile == null) return;
        
        playerFile.clearKitCooldowns();
        playerFileStorage.saveAndMarkDirty(playerId);
    }

    /**
     * Reload kits from file
     */
    public void reload() {
        loadKits();
    }

    /**
     * Check if player has already claimed a one-time kit
     */
    public boolean hasClaimedOnetime(UUID playerId, String kitId) {
        if (playerFileStorage == null) return false;
        
        PlayerFile playerFile = playerFileStorage.getPlayer(playerId);
        if (playerFile == null) {
            // Fail closed: if we cannot read claim state, assume already claimed so we don't
            // hand out one-time kits (e.g. starter) repeatedly. Callers should ensurePlayerFile()
            // before this path; when they do, a freshly-created PlayerFile has no claims recorded
            // and this branch is not reached, so legitimate new players still get their kit.
            logger.warning("[Kit] hasClaimedOnetime: PlayerFile missing for " + playerId
                + ", kit '" + kitId + "' — treating as claimed (fail-closed).");
            return true;
        }
        
        boolean hasClaimed = playerFile.hasClaimedKit(kitId);
        
        // Only log if debug is enabled
        if (hasClaimed) {
            try {
                com.eliteessentials.EliteEssentials plugin = com.eliteessentials.EliteEssentials.getInstance();
                if (plugin != null && plugin.getConfigManager().isDebugEnabled()) {
                    logger.info("hasClaimedOnetime check: playerId=" + playerId + ", kitId=" + kitId + 
                               ", claimed=" + hasClaimed);
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
        if (playerFileStorage == null) return;
        
        try {
            com.eliteessentials.EliteEssentials plugin = com.eliteessentials.EliteEssentials.getInstance();
            if (plugin != null && plugin.getConfigManager().isDebugEnabled()) {
                logger.info("setOnetimeClaimed called: playerId=" + playerId + ", kitId=" + kitId);
            }
        } catch (Exception e) {
            // Ignore if we can't get config
        }
        
        PlayerFile playerFile = playerFileStorage.getPlayer(playerId);
        if (playerFile == null) {
            // Defense-in-depth: log rather than silently lose the claim write. Callers should
            // ensurePlayerFile() before this path.
            logger.warning("[Kit] Cannot record one-time claim for kit '" + kitId
                + "' for " + playerId + ": PlayerFile missing (callers should ensurePlayerFile first).");
            return;
        }
        
        playerFile.claimKit(kitId);
        playerFileStorage.saveAndMarkDirty(playerId);
        
        try {
            com.eliteessentials.EliteEssentials plugin = com.eliteessentials.EliteEssentials.getInstance();
            if (plugin != null && plugin.getConfigManager().isDebugEnabled()) {
                logger.info("After adding, kitClaims for player: " + playerFile.getKitClaims());
            }
        } catch (Exception e) {
            // Ignore if we can't get config
        }
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
}