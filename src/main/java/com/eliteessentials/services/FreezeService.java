package com.eliteessentials.services;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.MovementSettings;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementConfig;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Manages frozen players.
 * Freeze works by setting all movement speeds to 0 via MovementSettings.
 * A scheduled enforcement loop re-applies freeze every 500ms to prevent
 * the engine from resetting movement (e.g. on respawn, game mode change).
 * State persists across restarts via freezes.json.
 */
public class FreezeService {

    private static final Logger logger = Logger.getLogger("EliteEssentials");
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Type FREEZE_MAP_TYPE = new TypeToken<Map<String, FreezeEntry>>(){}.getType();
    private static final long ENFORCE_INTERVAL_MS = 500;

    private final File freezeFile;
    private final Object fileLock = new Object();
    private final Map<String, FreezeEntry> frozenPlayers = new ConcurrentHashMap<>();
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> enforcementTask;

    public FreezeService(File dataFolder) {
        this.freezeFile = new File(dataFolder, "freezes.json");
        load();
    }

    /** Start the periodic enforcement loop that re-applies freeze to online players. */
    public void start() {
        if (scheduler != null) return;
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "EliteEssentials-FreezeEnforce");
            t.setDaemon(true);
            return t;
        });
        enforcementTask = scheduler.scheduleAtFixedRate(
                this::enforceAllFrozen, ENFORCE_INTERVAL_MS, ENFORCE_INTERVAL_MS, TimeUnit.MILLISECONDS);
        logger.info("[FreezeService] Enforcement loop started (interval: " + ENFORCE_INTERVAL_MS + "ms).");
    }

    /** Stop the enforcement loop. Call on plugin disable. */
    public void shutdown() {
        if (enforcementTask != null) {
            enforcementTask.cancel(false);
            enforcementTask = null;
        }
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(3, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                scheduler.shutdownNow();
            }
            scheduler = null;
        }
    }

    public void load() {
        if (!freezeFile.exists()) {
            return;
        }
        synchronized (fileLock) {
            try (Reader reader = new InputStreamReader(new FileInputStream(freezeFile), StandardCharsets.UTF_8)) {
                Map<String, FreezeEntry> loaded = gson.fromJson(reader, FREEZE_MAP_TYPE);
                frozenPlayers.clear();
                if (loaded != null) {
                    frozenPlayers.putAll(loaded);
                }
                logger.info("[FreezeService] Loaded " + frozenPlayers.size() + " frozen players.");
            } catch (IOException e) {
                logger.severe("Could not load freezes.json: " + e.getMessage());
            }
        }
    }

    private void save() {
        synchronized (fileLock) {
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(freezeFile), StandardCharsets.UTF_8)) {
                gson.toJson(frozenPlayers, FREEZE_MAP_TYPE, writer);
            } catch (IOException e) {
                logger.severe("Could not save freezes.json: " + e.getMessage());
            }
        }
    }

    public boolean freeze(UUID playerId, String playerName, String frozenBy) {
        String key = playerId.toString();
        if (frozenPlayers.containsKey(key)) {
            return false;
        }
        FreezeEntry entry = new FreezeEntry();
        entry.playerName = playerName;
        entry.frozenBy = frozenBy;
        entry.frozenAt = System.currentTimeMillis();
        frozenPlayers.put(key, entry);
        save();
        return true;
    }

    public boolean unfreeze(UUID playerId) {
        if (frozenPlayers.remove(playerId.toString()) != null) {
            save();
            return true;
        }
        return false;
    }

    /**
     * Unfreeze by player name (for offline players where UUID may not be known).
     * @return the UUID that was unfrozen, or null if not found
     */
    public UUID unfreezeByName(String playerName) {
        for (Map.Entry<String, FreezeEntry> entry : frozenPlayers.entrySet()) {
            if (entry.getValue().playerName != null && entry.getValue().playerName.equalsIgnoreCase(playerName)) {
                UUID uuid = UUID.fromString(entry.getKey());
                frozenPlayers.remove(entry.getKey());
                save();
                return uuid;
            }
        }
        return null;
    }

    public boolean isFrozen(UUID playerId) {
        return frozenPlayers.containsKey(playerId.toString());
    }

    public FreezeEntry getFreezeEntry(UUID playerId) {
        return frozenPlayers.get(playerId.toString());
    }

    /** Get the number of currently frozen players. */
    public int getFrozenCount() {
        return frozenPlayers.size();
    }

    public void reload() {
        load();
    }

    /**
     * Apply freeze movement settings to an online player.
     * Must be called on the player's world thread.
     */
    public static void applyFreeze(Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef playerRef) {
        MovementManager movementManager = store.getComponent(ref, MovementManager.getComponentType());
        if (movementManager == null) return;
        MovementSettings settings = movementManager.getSettings();
        settings.baseSpeed = 0f;
        settings.jumpForce = 0f;
        settings.horizontalFlySpeed = 0f;
        settings.verticalFlySpeed = 0f;
        movementManager.update(playerRef.getPacketHandler());
    }

    /**
     * Remove freeze and restore default movement settings.
     * Must be called on the player's world thread.
     */
    public static void removeFreeze(Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef playerRef) {
        MovementManager movementManager = store.getComponent(ref, MovementManager.getComponentType());
        if (movementManager == null) return;
        MovementSettings settings = movementManager.getSettings();
        settings.baseSpeed = MovementConfig.DEFAULT_MOVEMENT.getBaseSpeed();
        settings.jumpForce = MovementConfig.DEFAULT_MOVEMENT.getJumpForce();
        settings.horizontalFlySpeed = MovementConfig.DEFAULT_MOVEMENT.getHorizontalFlySpeed();
        settings.verticalFlySpeed = MovementConfig.DEFAULT_MOVEMENT.getVerticalFlySpeed();
        movementManager.update(playerRef.getPacketHandler());
    }

    /**
     * Periodic enforcement: re-apply freeze to all frozen online players.
     * Runs off the main thread, dispatches to each player's world thread.
     */
    private void enforceAllFrozen() {
        if (frozenPlayers.isEmpty()) return;
        Universe universe = Universe.get();
        if (universe == null) return;

        for (String key : frozenPlayers.keySet()) {
            try {
                UUID playerId = UUID.fromString(key);
                PlayerRef playerRef = universe.getPlayer(playerId);
                if (playerRef == null || !playerRef.isValid()) continue;

                Ref<EntityStore> ref = playerRef.getReference();
                if (ref == null || !ref.isValid()) continue;

                Store<EntityStore> store = ref.getStore();
                EntityStore entityStore = store.getExternalData();
                World world = entityStore != null ? entityStore.getWorld() : null;
                if (world == null) continue;

                world.execute(() -> {
                    try {
                        Ref<EntityStore> freshRef = playerRef.getReference();
                        if (freshRef == null || !freshRef.isValid()) return;
                        Store<EntityStore> freshStore = freshRef.getStore();
                        applyFreeze(freshStore, freshRef, playerRef);
                    } catch (Exception e) {
                        // Silently ignore - player may have disconnected
                    }
                });
            } catch (Exception e) {
                // Malformed UUID or other issue - skip
            }
        }
    }

    public static class FreezeEntry {
        public String playerName;
        public String frozenBy;
        public long frozenAt;
    }
}
