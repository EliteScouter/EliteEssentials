package com.eliteessentials.services;

import com.eliteessentials.config.ConfigManager;
import com.hypixel.hytale.builtin.beds.sleep.components.PlayerSleep;
import com.hypixel.hytale.builtin.beds.sleep.components.PlayerSomnolence;
import com.hypixel.hytale.builtin.beds.sleep.resources.WorldSlumber;
import com.hypixel.hytale.builtin.beds.sleep.resources.WorldSomnolence;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Service that monitors sleeping players and triggers night skip
 * when the configured percentage of players are sleeping.
 */
public class SleepService {

    private static final Logger logger = Logger.getLogger("EliteEssentials");

    private final ConfigManager configManager;
    private final ScheduledExecutorService scheduler;
    private volatile boolean slumberTriggered = false;
    private volatile int lastSleepingCount = -1;

    public SleepService(ConfigManager configManager) {
        this.configManager = configManager;
        
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "EliteEssentials-SleepCheck");
            t.setDaemon(true);
            return t;
        });
        
        // Check every 1 second
        scheduler.scheduleAtFixedRate(this::checkSleepingPlayers, 5, 1, TimeUnit.SECONDS);
        
        logger.fine("[SleepService] Started with " + configManager.getConfig().sleep.sleepPercentage + "% threshold");
    }

    private void checkSleepingPlayers() {
        if (!configManager.getConfig().sleep.enabled) {
            return;
        }
        
        int requiredPercent = configManager.getConfig().sleep.sleepPercentage;
        
        // If 100%, let vanilla handle it
        if (requiredPercent >= 100) {
            return;
        }
        
        try {
            Universe universe = Universe.get();
            if (universe == null) return;
            
            for (World world : universe.getWorlds().values()) {
                checkWorldSleep(world, requiredPercent);
            }
        } catch (Exception e) {
            // Silently ignore - universe might not be ready
        }
    }

    private void checkWorldSleep(World world, int requiredPercent) {
        world.execute(() -> {
            try {
                List<PlayerRef> players = getPlayersInWorld(world);
                
                if (players.isEmpty()) {
                    return;
                }
                
                int totalPlayers = players.size();
                int sleepingPlayers = 0;
                
                EntityStore entityStore = world.getEntityStore();
                Store<EntityStore> store = entityStore.getStore();
                
                for (PlayerRef player : players) {
                    if (isPlayerInBedOnWorldThread(player, store, entityStore)) {
                        sleepingPlayers++;
                    }
                }
                
                // Reset when no one is sleeping
                if (sleepingPlayers == 0) {
                    slumberTriggered = false;
                    lastSleepingCount = -1;
                    return;
                }
                
                // Check if it's nighttime - only matters when someone is trying to sleep
                if (!isNighttime(world)) {
                    return;
                }
                
                // Calculate how many players needed based on percentage
                int playersNeeded = Math.max(1, (int) Math.ceil(totalPlayers * requiredPercent / 100.0));
                
                // Check if threshold is met
                if (sleepingPlayers >= playersNeeded && !slumberTriggered) {
                    triggerSlumber(world, store, sleepingPlayers, playersNeeded, players);
                    slumberTriggered = true;
                    lastSleepingCount = sleepingPlayers;
                } else if (sleepingPlayers != lastSleepingCount) {
                    // Only show "waiting" message if threshold NOT met yet
                    lastSleepingCount = sleepingPlayers;
                    sendSleepMessage(players, sleepingPlayers, playersNeeded);
                }
                
            } catch (Exception e) {
                logger.warning("[SleepService] Error checking world sleep: " + e.getMessage());
            }
        });
    }
    
    private void sendSleepMessage(List<PlayerRef> players, int sleeping, int needed) {
        for (PlayerRef player : players) {
            try {
                player.sendMessage(Message.raw(sleeping + "/" + needed + " players sleeping...").color("#FFFF55"));
            } catch (Exception e) {
                // Ignore
            }
        }
    }
    
    private boolean isNighttime(World world) {
        try {
            EntityStore entityStore = world.getEntityStore();
            Store<EntityStore> store = entityStore.getStore();
            
            WorldTimeResource timeResource = store.getResource(WorldTimeResource.getResourceType());
            if (timeResource == null) {
                return true; // Can't determine, allow sleep
            }
            
            Instant gameTime = timeResource.getGameTime();
            
            int daytimeSeconds = world.getDaytimeDurationSeconds();
            int nighttimeSeconds = world.getNighttimeDurationSeconds();
            int fullDaySeconds = daytimeSeconds + nighttimeSeconds;
            
            if (fullDaySeconds <= 0) {
                return true;
            }
            
            long epochSeconds = gameTime.getEpochSecond();
            // Use floorMod to handle negative epoch values correctly
            long secondsIntoDay = Math.floorMod(epochSeconds, fullDaySeconds);
            
            // Nighttime starts after daytime ends
            return secondsIntoDay >= daytimeSeconds;
            
        } catch (Exception e) {
            return true;
        }
    }
    
    private boolean isPlayerInBedOnWorldThread(PlayerRef player, Store<EntityStore> store, EntityStore entityStore) {
        try {
            if (player == null || !player.isValid()) {
                return false;
            }
            
            com.hypixel.hytale.component.Ref<EntityStore> ref = entityStore.getRefFromUUID(player.getUuid());
            if (ref == null) {
                return false;
            }
            
            PlayerSomnolence somnolence = store.getComponent(ref, PlayerSomnolence.getComponentType());
            if (somnolence == null) {
                return false;
            }
            
            PlayerSleep state = somnolence.getSleepState();
            if (state == null) {
                return false;
            }
            
            return !(state instanceof PlayerSleep.FullyAwake);
            
        } catch (Exception e) {
            return false;
        }
    }
    
    private List<PlayerRef> getPlayersInWorld(World world) {
        List<PlayerRef> result = new ArrayList<>();
        try {
            Universe universe = Universe.get();
            if (universe == null) return result;
            
            java.util.UUID worldUuid = world.getWorldConfig().getUuid();
            
            for (PlayerRef player : universe.getPlayers()) {
                if (player != null && player.isValid()) {
                    java.util.UUID playerWorldUuid = player.getWorldUuid();
                    if (playerWorldUuid != null && playerWorldUuid.equals(worldUuid)) {
                        result.add(player);
                    }
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return result;
    }

    private void triggerSlumber(World world, Store<EntityStore> store, int sleeping, int needed, List<PlayerRef> players) {
        logger.fine("[SleepService] Triggering night skip in " + world.getName());
        
        // Send final message
        for (PlayerRef player : players) {
            try {
                player.sendMessage(Message.raw(sleeping + "/" + needed + " players sleeping - Skipping to morning!").color("#55FF55"));
            } catch (Exception e) {
                // Ignore
            }
        }
        
        try {
            WorldTimeResource timeResource = store.getResource(WorldTimeResource.getResourceType());
            if (timeResource == null) {
                logger.warning("[SleepService] Could not get WorldTimeResource");
                return;
            }
            
            Instant currentTime = timeResource.getGameTime();
            Instant morningTime = calculateNextMorning(currentTime, world);
            
            WorldSlumber slumber = new WorldSlumber(currentTime, morningTime, 3.0f);
            
            WorldSomnolence worldSomnolence = store.getResource(WorldSomnolence.getResourceType());
            if (worldSomnolence != null) {
                worldSomnolence.setState(slumber);
                logger.fine("[SleepService] Night skip triggered successfully!");
            } else {
                logger.warning("[SleepService] WorldSomnolence resource not found");
            }
            
        } catch (Exception e) {
            logger.warning("[SleepService] Error triggering slumber: " + e.getMessage());
        }
    }
    
    private Instant calculateNextMorning(Instant currentTime, World world) {
        try {
            int nighttimeSeconds = world.getNighttimeDurationSeconds();
            return currentTime.plus(Duration.ofSeconds(nighttimeSeconds / 2));
        } catch (Exception e) {
            return currentTime.plus(Duration.ofHours(8));
        }
    }

    public int getSleepPercentage() {
        return configManager.getConfig().sleep.sleepPercentage;
    }

    public void setSleepPercentage(int percent) {
        configManager.getConfig().sleep.sleepPercentage = Math.max(0, Math.min(100, percent));
        configManager.saveConfig();
        logger.fine("[SleepService] Sleep percentage set to " + percent + "%");
    }

    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
