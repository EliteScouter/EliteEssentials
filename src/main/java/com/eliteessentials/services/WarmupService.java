package com.eliteessentials.services;

import com.eliteessentials.EliteEssentials;
import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.util.MessageFormatter;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.logging.Logger;

/**
 * Service for handling teleport warmups - players must stand still for a duration.
 * Uses a polling approach similar to HomeManager for reliable movement detection.
 */
public class WarmupService {

    private static final Logger logger = Logger.getLogger("EliteEssentials");
    
    // Movement threshold squared (1 block) - same as HomeManager
    private static final double MOVE_EPSILON_SQUARED = 1.0;
    
    // Poll interval in milliseconds - frequent polling catches movement reliably
    private static final long POLL_INTERVAL_MS = 100;

    private final ScheduledExecutorService poller;
    private final Map<UUID, PendingWarmup> pending = new ConcurrentHashMap<>();
    private ScheduledFuture<?> pollTask;

    public WarmupService() {
        this.poller = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "EliteEssentials-Warmup");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Start a warmup for a player. They must stand still for the duration.
     */
    public void startWarmup(PlayerRef player, Vector3d startPosition, int warmupSeconds, 
                            Runnable onComplete, String commandName,
                            World world, Store<EntityStore> store, Ref<EntityStore> ref) {
        startWarmup(player, startPosition, warmupSeconds, onComplete, commandName, world, store, ref, false);
    }
    
    /**
     * Start a warmup for a player with optional silent mode.
     * @param silent If true, suppress countdown messages (but still show cancel message if moved)
     */
    public void startWarmup(PlayerRef player, Vector3d startPosition, int warmupSeconds, 
                            Runnable onComplete, String commandName,
                            World world, Store<EntityStore> store, Ref<EntityStore> ref, boolean silent) {
        UUID playerId = player.getUuid();
        
        // Cancel any existing warmup for this player
        PendingWarmup existing = pending.remove(playerId);
        if (existing != null) {
            existing.cancelled = true;
        }
        
        // If no warmup needed, execute immediately
        if (warmupSeconds <= 0) {
            onComplete.run();
            return;
        }
        
        // If missing required context, execute immediately
        if (startPosition == null || world == null || store == null || ref == null) {
            logger.warning("[Warmup] Missing context for " + commandName + ", executing immediately");
            onComplete.run();
            return;
        }
        
        ConfigManager configManager = EliteEssentials.getInstance().getConfigManager();
        if (configManager.isDebugEnabled()) {
            logger.info("[Warmup] Starting " + warmupSeconds + "s warmup for " + commandName);
        }
        
        // Create pending warmup with end time in nanos (like HomeManager)
        long endTimeNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(warmupSeconds);
        PendingWarmup warmup = new PendingWarmup(
            playerId, ref, new Vector3d(startPosition), endTimeNanos,
            onComplete, commandName, world, store, warmupSeconds, silent
        );
        
        pending.put(playerId, warmup);
        ensurePollerRunning();
    }
    
    /**
     * Overload for commands that don't have world/store/ref - no movement checking.
     */
    public void startWarmup(PlayerRef player, Vector3d startPosition, int warmupSeconds, 
                            Runnable onComplete, String commandName) {
        startWarmup(player, startPosition, warmupSeconds, onComplete, commandName, null, null, null);
    }
    
    private void ensurePollerRunning() {
        if (pollTask != null && !pollTask.isCancelled()) {
            return;
        }
        pollTask = poller.scheduleAtFixedRate(
            this::pollWarmups,
            POLL_INTERVAL_MS, POLL_INTERVAL_MS, TimeUnit.MILLISECONDS
        );
    }
    
    private void pollWarmups() {
        if (pending.isEmpty()) {
            return;
        }
        
        for (PendingWarmup warmup : pending.values()) {
            if (warmup.cancelled) {
                pending.remove(warmup.playerUuid);
                continue;
            }
            
            World world = warmup.world;
            if (world == null) {
                pending.remove(warmup.playerUuid);
                continue;
            }
            
            // Execute the tick on the game thread
            world.execute(() -> tickWarmup(warmup));
        }
        
        // Stop poller if no more pending warmups
        if (pending.isEmpty() && pollTask != null) {
            pollTask.cancel(false);
            pollTask = null;
        }
    }

    private void tickWarmup(PendingWarmup warmup) {
        Store<EntityStore> store = warmup.store;
        Ref<EntityStore> ref = warmup.playerRef;
        
        // Validate ref is still valid before accessing components
        if (ref == null || !ref.isValid()) {
            pending.remove(warmup.playerUuid);
            return;
        }
        
        // Get Player component to send messages
        Player playerComponent;
        try {
            playerComponent = (Player) store.getComponent(ref, Player.getComponentType());
        } catch (Exception e) {
            pending.remove(warmup.playerUuid);
            return;
        }
        
        if (playerComponent == null) {
            pending.remove(warmup.playerUuid);
            return;
        }
        
        // Get current position
        Vector3d currentPos = getPlayerPosition(ref, store);
        if (currentPos == null) {
            pending.remove(warmup.playerUuid);
            return;
        }
        
        ConfigManager configManager = EliteEssentials.getInstance().getConfigManager();
        
        // Check if player moved (using squared distance like HomeManager)
        if (hasMoved(warmup.startPos, currentPos)) {
            pending.remove(warmup.playerUuid);
            playerComponent.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("warmupCancelled"), "#FF5555"));
            return;
        }
        
        // Check if warmup time has elapsed
        long now = System.nanoTime();
        long remainingNanos = warmup.endTimeNanos - now;
        
        if (remainingNanos <= 0) {
            // Warmup complete - execute the teleport
            pending.remove(warmup.playerUuid);
            
            // Final validation before executing teleport
            if (ref == null || !ref.isValid()) {
                return;
            }
            
            try {
                warmup.onComplete.run();
            } catch (Exception e) {
                logger.warning("[Warmup] Error executing teleport: " + e.getMessage());
            }
            return;
        }
        
        // Announce countdown (only when seconds change, and not in silent mode)
        int remainingSeconds = (int) Math.ceil(remainingNanos / 1_000_000_000.0);
        if (!warmup.silent && remainingSeconds != warmup.lastAnnouncedSeconds && remainingSeconds > 0) {
            warmup.lastAnnouncedSeconds = remainingSeconds;
            playerComponent.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("warmupCountdown", "seconds", String.valueOf(remainingSeconds)), "#FFAA00"));
        }
    }
    
    private boolean hasMoved(Vector3d start, Vector3d current) {
        double dx = current.getX() - start.getX();
        double dy = current.getY() - start.getY();
        double dz = current.getZ() - start.getZ();
        double distanceSquared = dx*dx + dy*dy + dz*dz;
        return distanceSquared > MOVE_EPSILON_SQUARED;
    }
    
    private Vector3d getPlayerPosition(Ref<EntityStore> ref, Store<EntityStore> store) {
        try {
            if (ref == null || !ref.isValid()) {
                return null;
            }
            TransformComponent transform = (TransformComponent) store.getComponent(ref, TransformComponent.getComponentType());
            if (transform == null) {
                return null;
            }
            return transform.getPosition();
        } catch (Exception e) {
            // Entity may have been removed
            return null;
        }
    }

    public void cancelWarmup(UUID playerId) {
        PendingWarmup warmup = pending.remove(playerId);
        if (warmup != null) {
            warmup.cancelled = true;
        }
    }

    public boolean hasActiveWarmup(UUID playerId) {
        return pending.containsKey(playerId);
    }

    public void shutdown() {
        for (UUID playerId : pending.keySet()) {
            cancelWarmup(playerId);
        }
        
        if (pollTask != null) {
            pollTask.cancel(false);
        }
        
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

    /**
     * Internal class to track pending warmup state.
     */
    private static class PendingWarmup {
        final UUID playerUuid;
        final Ref<EntityStore> playerRef;
        final Vector3d startPos;
        final long endTimeNanos;
        final Runnable onComplete;
        final String commandName;
        final World world;
        final Store<EntityStore> store;
        final boolean silent;
        volatile boolean cancelled = false;
        int lastAnnouncedSeconds;

        PendingWarmup(UUID playerUuid, Ref<EntityStore> playerRef, Vector3d startPos, 
                      long endTimeNanos, Runnable onComplete, String commandName,
                      World world, Store<EntityStore> store, int initialSeconds, boolean silent) {
            this.playerUuid = playerUuid;
            this.playerRef = playerRef;
            this.startPos = startPos;
            this.endTimeNanos = endTimeNanos;
            this.onComplete = onComplete;
            this.commandName = commandName;
            this.world = world;
            this.store = store;
            this.silent = silent;
            this.lastAnnouncedSeconds = initialSeconds + 1; // So first tick announces
        }
    }
}
