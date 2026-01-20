package com.eliteessentials.services;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.model.Location;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.logging.Logger;

/**
 * Service that tracks player positions and saves their location on death.
 * Uses multiple detection strategies:
 * 1. Polls player positions every POLL_INTERVAL_MS to maintain accurate last-known location
 * 2. Detects when player becomes invalid (death/disconnect)
 * 3. Detects sudden large teleports (respawn after death)
 * 4. Tracks health changes if available
 * 
 * All death locations are immediately persisted to JSON via BackService.
 */
public class DeathTrackingService {

    private static final Logger logger = Logger.getLogger("EliteEssentials");
    
    // Detection thresholds
    private static final double RESPAWN_DISTANCE_THRESHOLD = 50.0;  // Blocks - lowered for better detection
    private static final double STABLE_MOVEMENT_THRESHOLD = 5.0;    // Max distance to consider player "settled"
    
    // Polling interval for position tracking
    private static final long POLL_INTERVAL_MS = 200;
    
    private final BackService backService;
    private final ConfigManager configManager;
    private final ScheduledExecutorService scheduler;
    
    // Track last known position for each player (updated frequently)
    private final Map<UUID, Location> lastKnownPositions = new ConcurrentHashMap<>();
    // Track the "stable" position before any death event (the one we want to save)
    private final Map<UUID, Location> stablePositions = new ConcurrentHashMap<>();
    // Track if player was valid last check (to detect death/disconnect)
    private final Map<UUID, Boolean> wasValid = new ConcurrentHashMap<>();
    // Track if we already saved death location for this "death session"
    private final Map<UUID, Boolean> deathLocationSaved = new ConcurrentHashMap<>();
    // Known player UUIDs we're tracking
    private final Set<UUID> trackedPlayers = ConcurrentHashMap.newKeySet();
    
    private ScheduledFuture<?> trackingTask;
    private boolean started = false;

    public DeathTrackingService(BackService backService, ConfigManager configManager) {
        this.backService = backService;
        this.configManager = configManager;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "EliteEssentials-DeathTracking");
            t.setDaemon(true);
            return t;
        });
    }
    
    /**
     * Start tracking player positions.
     */
    public void start() {
        boolean backOnDeath = configManager.isBackOnDeathEnabled();
        logger.fine("[DeathTracking] Starting service. backOnDeath config: " + backOnDeath);
        
        if (!backOnDeath) {
            logger.fine("[DeathTracking] Back on death is disabled in config, service not started.");
            return;
        }
        
        // Poll every POLL_INTERVAL_MS for more accurate position tracking
        trackingTask = scheduler.scheduleAtFixedRate(this::trackPlayers, 500, POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);
        started = true;
        logger.fine("[DeathTracking] Service started successfully with " + POLL_INTERVAL_MS + "ms polling.");
    }
    
    /**
     * Register a player to be tracked.
     */
    public void trackPlayer(UUID playerId) {
        if (playerId != null && started) {
            trackedPlayers.add(playerId);
            deathLocationSaved.put(playerId, false);
            logger.fine("[DeathTracking] Now tracking player: " + playerId);
        }
    }
    
    private void trackPlayers() {
        try {
            Universe universe = Universe.get();
            if (universe == null) return;
            
            // Auto-discover players
            try {
                for (Object playerObj : universe.getPlayers()) {
                    if (playerObj instanceof PlayerRef) {
                        PlayerRef ref = (PlayerRef) playerObj;
                        if (ref.isValid()) {
                            UUID uuid = ref.getUuid();
                            if (!trackedPlayers.contains(uuid)) {
                                trackedPlayers.add(uuid);
                                deathLocationSaved.put(uuid, false);
                                logger.fine("[DeathTracking] Auto-discovered player: " + uuid);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // getPlayers might not work as expected
            }
            
            // Check all tracked players
            for (UUID playerId : new HashSet<>(trackedPlayers)) {
                try {
                    checkPlayer(playerId, universe);
                } catch (Exception e) {
                    logger.fine("[DeathTracking] Error checking player " + playerId + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.warning("[DeathTracking] Error in trackPlayers: " + e.getMessage());
        }
    }
    
    private void checkPlayer(UUID playerId, Universe universe) {
        if (playerId == null) return;
        
        PlayerRef playerRef = universe.getPlayer(playerId);
        boolean isValid = playerRef != null && playerRef.isValid();
        
        Boolean previouslyValid = wasValid.get(playerId);
        Location stablePos = stablePositions.get(playerId);
        
        // DETECTION 1: Player became invalid (death or disconnect)
        if (previouslyValid != null && previouslyValid && !isValid) {
            if (stablePos != null && !Boolean.TRUE.equals(deathLocationSaved.get(playerId))) {
                logger.fine("[DeathTracking] Player " + playerId + " became INVALID. Saving death location: " + 
                    String.format("%.1f, %.1f, %.1f", stablePos.getX(), stablePos.getY(), stablePos.getZ()));
                backService.pushDeathLocation(playerId, stablePos);
                deathLocationSaved.put(playerId, true);
            }
        }
        
        wasValid.put(playerId, isValid);
        
        if (isValid) {
            Location newPos = updatePlayerPosition(playerId, playerRef);
            
            if (newPos != null) {
                Location previousPos = lastKnownPositions.get(playerId);
                
                // DETECTION 2: Large sudden teleport (respawn after death)
                if (previousPos != null) {
                    double distance = calculateDistance(previousPos, newPos);
                    
                    // If player suddenly moved far, they likely respawned
                    if (distance > RESPAWN_DISTANCE_THRESHOLD) {
                        // Only save if we haven't already saved for this death
                        if (!Boolean.TRUE.equals(deathLocationSaved.get(playerId))) {
                            logger.fine("[DeathTracking] Detected RESPAWN for " + playerId + 
                                       ": moved " + String.format("%.1f", distance) + " blocks. " +
                                       "Saving pre-respawn location: " + 
                                       String.format("%.1f, %.1f, %.1f", previousPos.getX(), previousPos.getY(), previousPos.getZ()));
                            backService.pushDeathLocation(playerId, previousPos);
                            deathLocationSaved.put(playerId, true);
                        }
                        
                        // Reset stable position to new location after respawn
                        stablePositions.put(playerId, newPos.clone());
                    } else {
                        // Normal movement - update stable position
                        // Only update stable if movement is small (player is "settled")
                        if (distance < STABLE_MOVEMENT_THRESHOLD) {
                            stablePositions.put(playerId, newPos.clone());
                            // Reset death saved flag when player is moving normally
                            deathLocationSaved.put(playerId, false);
                        }
                    }
                } else {
                    // First position for this player
                    stablePositions.put(playerId, newPos.clone());
                }
                
                lastKnownPositions.put(playerId, newPos);
            }
        }
    }
    
    private double calculateDistance(Location a, Location b) {
        return Math.sqrt(
            Math.pow(a.getX() - b.getX(), 2) +
            Math.pow(a.getY() - b.getY(), 2) +
            Math.pow(a.getZ() - b.getZ(), 2)
        );
    }
    
    private Location updatePlayerPosition(UUID playerId, PlayerRef playerRef) {
        try {
            Holder<EntityStore> holder = playerRef.getHolder();
            if (holder == null) return null;
            
            TransformComponent transform = holder.getComponent(TransformComponent.getComponentType());
            if (transform == null) return null;
            
            Vector3d pos = transform.getPosition();
            HeadRotation headRotation = holder.getComponent(HeadRotation.getComponentType());
            Vector3f rotation = headRotation != null ? headRotation.getRotation() : new Vector3f(0, 0, 0);
            
            String worldName = "world";
            return new Location(worldName, pos.getX(), pos.getY(), pos.getZ(), rotation.x, rotation.y);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Manually record a death location (can be called from death events if available).
     */
    public void recordDeath(UUID playerId, Location deathLocation) {
        if (configManager.isBackOnDeathEnabled() && deathLocation != null) {
            logger.fine("[DeathTracking] MANUAL death record for " + playerId + ": " +
                String.format("%.1f, %.1f, %.1f", deathLocation.getX(), deathLocation.getY(), deathLocation.getZ()));
            backService.pushDeathLocation(playerId, deathLocation);
            deathLocationSaved.put(playerId, true);
        }
    }
    
    /**
     * Get the last known position for a player (useful for manual death recording).
     */
    public Location getLastKnownPosition(UUID playerId) {
        Location pos = stablePositions.get(playerId);
        if (pos == null) {
            pos = lastKnownPositions.get(playerId);
        }
        return pos != null ? pos.clone() : null;
    }
    
    public void removePlayer(UUID playerId) {
        trackedPlayers.remove(playerId);
        lastKnownPositions.remove(playerId);
        stablePositions.remove(playerId);
        wasValid.remove(playerId);
        deathLocationSaved.remove(playerId);
    }
    
    public void shutdown() {
        if (trackingTask != null) {
            trackingTask.cancel(false);
        }
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.fine("[DeathTracking] Service stopped.");
    }
}
