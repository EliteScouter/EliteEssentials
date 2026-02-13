package com.eliteessentials.services;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.config.PluginConfig;
import com.eliteessentials.util.MessageFormatter;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.protocol.packets.interface_.RemoveFromServerPlayerList;
import com.hypixel.hytale.protocol.packets.interface_.AddToServerPlayerList;
import com.hypixel.hytale.protocol.packets.interface_.ServerPlayerListPlayer;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.logging.Logger;

/**
 * Service for managing AFK (Away From Keyboard) state.
 * 
 * Tracks player positions and detects inactivity. Players are marked AFK
 * after a configurable timeout, or can manually toggle with /afk.
 * 
 * Position checks run on the world thread (via world.execute()) to ensure
 * safe component access, matching the pattern used by WarmupService.
 * 
 * AFK players:
 * - Show [AFK] prefix in tab list and /list
 * - Optionally don't count toward playtime rewards
 * - Optionally have their AFK status broadcast to chat
 * - Are removed from AFK when they move
 */
public class AfkService {

    private static final Logger logger = Logger.getLogger("EliteEssentials");

    // Movement threshold squared (1 block) - same as WarmupService
    private static final double MOVE_EPSILON_SQUARED = 1.0;
    
    // Poll interval in milliseconds for scheduling position checks
    private static final long POLL_INTERVAL_MS = 2000;

    private final ConfigManager configManager;
    
    // UUID -> AFK state
    private final Set<UUID> afkPlayers = ConcurrentHashMap.newKeySet();
    
    // UUID -> last known position (for inactivity detection)
    private final Map<UUID, Vector3d> lastPositions = new ConcurrentHashMap<>();
    
    // UUID -> timestamp of last movement (epoch millis)
    private final Map<UUID, Long> lastMovementTime = new ConcurrentHashMap<>();
    
    // UUID -> Store/Ref/World for position checking on the world thread
    private final Map<UUID, PlayerStoreRef> playerStoreRefs = new ConcurrentHashMap<>();
    
    // Players who manually toggled AFK via /afk - movement will remove them
    private final Set<UUID> manualAfk = ConcurrentHashMap.newKeySet();
    
    private ScheduledExecutorService poller;
    private ScheduledFuture<?> pollTask;

    public AfkService(ConfigManager configManager) {
        this.configManager = configManager;
    }

    /**
     * Start the AFK detection poller.
     */
    public void start() {
        PluginConfig.AfkConfig config = configManager.getConfig().afk;
        if (!config.enabled) {
            return;
        }
        
        poller = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "EliteEssentials-AFK");
            t.setDaemon(true);
            return t;
        });
        
        pollTask = poller.scheduleAtFixedRate(this::schedulePositionChecks, POLL_INTERVAL_MS, POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);
        logger.info("AFK detection service started (timeout: " + config.inactivityTimeoutMinutes + "m).");
    }

    /**
     * Stop the AFK detection poller.
     */
    public void stop() {
        if (pollTask != null) {
            pollTask.cancel(false);
            pollTask = null;
        }
        if (poller != null) {
            poller.shutdown();
            try {
                if (!poller.awaitTermination(5, TimeUnit.SECONDS)) {
                    poller.shutdownNow();
                }
            } catch (InterruptedException e) {
                poller.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Track a player when they join.
     */
    public void onPlayerJoin(UUID playerId, Store<EntityStore> store, Ref<EntityStore> ref) {
        lastMovementTime.put(playerId, System.currentTimeMillis());
        
        // Resolve the world from the store's external data for world.execute() calls
        try {
            EntityStore entityStore = store.getExternalData();
            World world = entityStore.getWorld();
            playerStoreRefs.put(playerId, new PlayerStoreRef(store, ref, world));
        } catch (Exception e) {
            // Store the ref without world - we'll try to resolve it later
            playerStoreRefs.put(playerId, new PlayerStoreRef(store, ref, null));
        }
        
        // Capture initial position on the world thread
        PlayerStoreRef psr = playerStoreRefs.get(playerId);
        if (psr != null && psr.world != null) {
            psr.world.execute(() -> {
                try {
                    if (!psr.ref.isValid()) return;
                    TransformComponent transform = psr.store.getComponent(psr.ref, TransformComponent.getComponentType());
                    if (transform != null) {
                        lastPositions.put(playerId, new Vector3d(transform.getPosition()));
                    }
                } catch (Exception e) {
                    // Position will be captured on next poll
                }
            });
        }
    }

    /**
     * Clean up when a player leaves.
     */
    public void onPlayerQuit(UUID playerId) {
        afkPlayers.remove(playerId);
        manualAfk.remove(playerId);
        lastPositions.remove(playerId);
        lastMovementTime.remove(playerId);
        playerStoreRefs.remove(playerId);
    }

    /**
     * Check if a player is AFK.
     */
    public boolean isAfk(UUID playerId) {
        return afkPlayers.contains(playerId);
    }

    /**
     * Manually toggle AFK state (from /afk command).
     * @return true if player is now AFK, false if no longer AFK
     */
    public boolean toggleAfk(UUID playerId) {
        if (afkPlayers.contains(playerId)) {
            manualAfk.remove(playerId);
            removeAfk(playerId, true);
            return false;
        } else {
            manualAfk.add(playerId);
            setAfk(playerId, true);
            return true;
        }
    }

    /**
     * Set a player as AFK.
     */
    private void setAfk(UUID playerId, boolean manual) {
        if (afkPlayers.contains(playerId)) {
            return; // Already AFK
        }
        
        afkPlayers.add(playerId);
        
        PluginConfig.AfkConfig config = configManager.getConfig().afk;
        
        // Broadcast AFK message if enabled
        if (config.broadcastAfk) {
            try {
                PlayerRef playerRef = Universe.get().getPlayer(playerId);
                if (playerRef != null && playerRef.isValid()) {
                    String message = configManager.getMessage("afkOn", "player", playerRef.getUsername());
                    broadcastMessage(message);
                }
            } catch (Exception e) {
                logger.warning("[AFK] Failed to broadcast AFK message: " + e.getMessage());
            }
        }
        
        // Update tab list to show [AFK] prefix
        if (config.showInTabList) {
            updateTabListForPlayer(playerId, true);
        }
        
        if (configManager.isDebugEnabled()) {
            logger.info("[AFK] Player " + playerId + " is now AFK" + (manual ? " (manual)" : " (inactivity)"));
        }
    }

    /**
     * Remove a player from AFK state.
     */
    private void removeAfk(UUID playerId, boolean manual) {
        if (!afkPlayers.remove(playerId)) {
            return; // Wasn't AFK
        }
        
        manualAfk.remove(playerId);
        
        // Reset movement time so they don't immediately go AFK again
        lastMovementTime.put(playerId, System.currentTimeMillis());
        
        PluginConfig.AfkConfig config = configManager.getConfig().afk;
        
        // Broadcast return message if enabled
        if (config.broadcastAfk) {
            try {
                PlayerRef playerRef = Universe.get().getPlayer(playerId);
                if (playerRef != null && playerRef.isValid()) {
                    String message = configManager.getMessage("afkOff", "player", playerRef.getUsername());
                    broadcastMessage(message);
                }
            } catch (Exception e) {
                logger.warning("[AFK] Failed to broadcast AFK return message: " + e.getMessage());
            }
        }
        
        // Update tab list to remove [AFK] prefix
        if (config.showInTabList) {
            updateTabListForPlayer(playerId, false);
        }
        
        if (configManager.isDebugEnabled()) {
            logger.info("[AFK] Player " + playerId + " is no longer AFK" + (manual ? " (manual)" : " (movement)"));
        }
    }

    /**
     * Schedules position checks on each player's world thread.
     * This runs from the background poller thread but dispatches the actual
     * component access to world.execute() for thread safety - same pattern
     * as WarmupService.pollWarmups().
     */
    private void schedulePositionChecks() {
        try {
            PluginConfig.AfkConfig config = configManager.getConfig().afk;
            if (!config.enabled) {
                return;
            }
            
            Universe universe = Universe.get();
            if (universe == null) return;
            
            for (PlayerRef playerRef : universe.getPlayers()) {
                if (playerRef == null || !playerRef.isValid()) continue;
                
                UUID playerId = playerRef.getUuid();
                PlayerStoreRef psr = playerStoreRefs.get(playerId);
                if (psr == null || !psr.ref.isValid()) continue;
                
                // Resolve world if we didn't have it at join time
                if (psr.world == null) {
                    try {
                        EntityStore entityStore = psr.store.getExternalData();
                        World world = entityStore.getWorld();
                        psr = new PlayerStoreRef(psr.store, psr.ref, world);
                        playerStoreRefs.put(playerId, psr);
                    } catch (Exception e) {
                        continue; // Can't check without world
                    }
                }
                
                // Dispatch position check to the world thread for safe component access
                final PlayerStoreRef finalPsr = psr;
                psr.world.execute(() -> checkPlayerPosition(playerId, finalPsr, config));
            }
        } catch (Exception e) {
            logger.warning("[AFK] Error scheduling position checks: " + e.getMessage());
        }
    }

    /**
     * Check a single player's position for movement. Runs on the world thread.
     */
    private void checkPlayerPosition(UUID playerId, PlayerStoreRef psr, PluginConfig.AfkConfig config) {
        try {
            if (!psr.ref.isValid()) return;
            
            TransformComponent transform = psr.store.getComponent(psr.ref, TransformComponent.getComponentType());
            if (transform == null) return;
            
            Vector3d currentPos = transform.getPosition();
            Vector3d lastPos = lastPositions.get(playerId);
            long now = System.currentTimeMillis();
            
            if (lastPos != null) {
                double dx = currentPos.getX() - lastPos.getX();
                double dy = currentPos.getY() - lastPos.getY();
                double dz = currentPos.getZ() - lastPos.getZ();
                double distSq = dx * dx + dy * dy + dz * dz;
                
                if (distSq > MOVE_EPSILON_SQUARED) {
                    // Player moved - update position and timestamp
                    lastMovementTime.put(playerId, now);
                    lastPositions.put(playerId, new Vector3d(currentPos));
                    
                    // If they were AFK (manual or auto), movement removes it
                    if (afkPlayers.contains(playerId)) {
                        if (configManager.isDebugEnabled()) {
                            logger.info("[AFK] Movement detected for " + playerId + " (dist=" + Math.sqrt(distSq) + "), removing AFK");
                        }
                        removeAfk(playerId, false);
                    }
                } else {
                    // Player hasn't moved - check inactivity timeout for auto-AFK
                    if (config.inactivityTimeoutMinutes > 0 && !afkPlayers.contains(playerId)) {
                        Long lastMove = lastMovementTime.get(playerId);
                        long timeoutMs = config.inactivityTimeoutMinutes * 60_000L;
                        if (lastMove != null && (now - lastMove) >= timeoutMs) {
                            setAfk(playerId, false);
                        }
                    }
                }
            } else {
                // First position capture
                lastPositions.put(playerId, new Vector3d(currentPos));
                lastMovementTime.putIfAbsent(playerId, now);
            }
        } catch (Exception e) {
            // Silently skip - player may be in transition
            if (configManager.isDebugEnabled()) {
                logger.info("[AFK] Error checking position for " + playerId + ": " + e.getMessage());
            }
        }
    }

    /**
     * Update the tab list for a player going AFK or returning.
     * Removes and re-adds the player with [AFK] prefix or normal name.
     */
    private void updateTabListForPlayer(UUID targetId, boolean afk) {
        try {
            Universe universe = Universe.get();
            if (universe == null) return;
            
            PlayerRef targetPlayer = universe.getPlayer(targetId);
            if (targetPlayer == null || !targetPlayer.isValid()) return;
            
            String displayName = afk 
                ? configManager.getMessage("afkPrefix", "player", targetPlayer.getUsername())
                : targetPlayer.getUsername();
            
            // Remove then re-add with updated name
            RemoveFromServerPlayerList removePacket = new RemoveFromServerPlayerList(new UUID[] { targetId });
            ServerPlayerListPlayer listPlayer = new ServerPlayerListPlayer(
                targetPlayer.getUuid(),
                displayName,
                targetPlayer.getWorldUuid(),
                0
            );
            AddToServerPlayerList addPacket = new AddToServerPlayerList(new ServerPlayerListPlayer[] { listPlayer });
            
            for (PlayerRef player : universe.getPlayers()) {
                try {
                    player.getPacketHandler().write(removePacket);
                    player.getPacketHandler().write(addPacket);
                } catch (Exception e) {
                    // Skip players with packet issues
                }
            }
        } catch (Exception e) {
            logger.warning("[AFK] Failed to update tab list: " + e.getMessage());
        }
    }

    /**
     * Broadcast a message to all online players.
     */
    private void broadcastMessage(String message) {
        try {
            Universe universe = Universe.get();
            if (universe == null) return;
            
            for (PlayerRef player : universe.getPlayers()) {
                if (player != null && player.isValid()) {
                    player.sendMessage(MessageFormatter.format(message));
                }
            }
        } catch (Exception e) {
            logger.warning("[AFK] Failed to broadcast message: " + e.getMessage());
        }
    }

    /**
     * Get the set of AFK player UUIDs (for external checks like rewards).
     */
    public Set<UUID> getAfkPlayers() {
        return Set.copyOf(afkPlayers);
    }

    /**
     * Clear all AFK state (for reload).
     */
    public void clearAll() {
        afkPlayers.clear();
        manualAfk.clear();
        lastPositions.clear();
        lastMovementTime.clear();
        playerStoreRefs.clear();
    }

    /**
     * Reload the service (restart poller with potentially new config).
     */
    public void reload() {
        stop();
        clearAll();
        start();
    }

    /**
     * Holds store/ref/world for a player for position checking on the world thread.
     */
    private static class PlayerStoreRef {
        final Store<EntityStore> store;
        final Ref<EntityStore> ref;
        final World world;
        
        PlayerStoreRef(Store<EntityStore> store, Ref<EntityStore> ref, World world) {
            this.store = store;
            this.ref = ref;
            this.world = world;
        }
    }
}
