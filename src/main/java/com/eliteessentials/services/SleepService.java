package com.eliteessentials.services;

import com.eliteessentials.config.ConfigManager;
import com.hypixel.hytale.builtin.beds.sleep.components.PlayerSleep;
import com.hypixel.hytale.builtin.beds.sleep.components.PlayerSomnolence;
import com.hypixel.hytale.builtin.beds.sleep.resources.WorldSlumber;
import com.hypixel.hytale.builtin.beds.sleep.resources.WorldSomnolence;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.gameplay.SleepConfig;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Service that monitors sleeping players and triggers night skip
 * when the configured percentage of players are sleeping.
 */
public class SleepService {

    private final ConfigManager configManager;
    private final ScheduledExecutorService scheduler;
    private volatile boolean slumberTriggered = false;
    private volatile int lastSleepingCount = -1;
    private volatile boolean initialized = false;

    public SleepService(ConfigManager configManager) {
        this.configManager = configManager;
        
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "EliteEssentials-SleepCheck");
            t.setDaemon(true);
            return t;
        });
        
        // Delay start by 10 seconds to let the world fully initialize
        scheduler.schedule(() -> initialized = true, 10, TimeUnit.SECONDS);
        
        // Check every 1 second
        scheduler.scheduleAtFixedRate(this::checkSleepingPlayers, 10, 1, TimeUnit.SECONDS);
    }

    private void checkSleepingPlayers() {
        if (!initialized || !configManager.getConfig().sleep.enabled) {
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
                EntityStore entityStore = world.getEntityStore();
                if (entityStore == null) return;
                
                Store<EntityStore> store = entityStore.getStore();
                if (store == null) return;
                
                // Get WorldSomnolence resource - this tracks the world's sleep state
                WorldSomnolence worldSomnolence = store.getResource(WorldSomnolence.getResourceType());
                if (worldSomnolence == null) return;
                
                // If world is already in slumber (skipping to morning), don't interfere
                if (worldSomnolence.getState() instanceof WorldSlumber) {
                    return;
                }
                
                List<PlayerRef> players = new ArrayList<>(world.getPlayerRefs());
                if (players.isEmpty()) {
                    return;
                }
                
                int totalPlayers = players.size();
                int sleepingPlayers = 0;
                
                // Get current game time for checking NoddingOff/MorningWakeUp states
                WorldTimeResource timeResource = store.getResource(WorldTimeResource.getResourceType());
                if (timeResource == null) return;
                
                Instant gameTime = timeResource.getGameTime();
                
                // Check if it's nighttime (between sleep start hour and wake up hour)
                // Sleep is allowed from 9 PM (21:00) to 5 AM (05:00)
                LocalDateTime currentDateTime = LocalDateTime.ofInstant(gameTime, ZoneOffset.UTC);
                int currentHour = currentDateTime.getHour();
                
                // Nighttime: hour >= 21 OR hour < 5
                // Daytime: hour >= 5 AND hour < 21
                boolean isDaytime = currentHour >= 5 && currentHour < 21;
                if (isDaytime) {
                    // Reset flags during daytime
                    slumberTriggered = false;
                    lastSleepingCount = -1;
                    return;
                }
                
                for (PlayerRef player : players) {
                    Ref<EntityStore> ref = player.getReference();
                    if (ref == null) continue;
                    
                    PlayerSomnolence somnolence = store.getComponent(ref, PlayerSomnolence.getComponentType());
                    if (somnolence == null) continue;
                    
                    PlayerSleep state = somnolence.getSleepState();
                    
                    // Only count players in Slumber state (fully asleep - game only allows this at night)
                    if (state instanceof PlayerSleep.Slumber) {
                        sleepingPlayers++;
                    }
                    // Count NoddingOff only if enough time has passed (3.2 seconds)
                    else if (state instanceof PlayerSleep.NoddingOff noddingOff) {
                        Instant threshold = noddingOff.realTimeStart().plusMillis(3200);
                        if (Instant.now().isAfter(threshold)) {
                            sleepingPlayers++;
                        }
                    }
                }
                
                // Reset when no one is sleeping
                if (sleepingPlayers == 0) {
                    slumberTriggered = false;
                    lastSleepingCount = -1;
                    return;
                }
                
                // Calculate percentage and check threshold
                int currentPercent = (sleepingPlayers * 100) / totalPlayers;
                int playersNeeded = Math.max(1, (int) Math.ceil(totalPlayers * requiredPercent / 100.0));
                
                if (currentPercent >= requiredPercent && !slumberTriggered) {
                    triggerSlumber(store, world, worldSomnolence, players, sleepingPlayers, playersNeeded);
                    slumberTriggered = true;
                    lastSleepingCount = sleepingPlayers;
                } else if (sleepingPlayers != lastSleepingCount) {
                    lastSleepingCount = sleepingPlayers;
                    sendSleepMessage(players, sleepingPlayers, playersNeeded);
                }
                
            } catch (Exception e) {
                // Silently ignore errors
            }
        });
    }
    
    private void sendSleepMessage(List<PlayerRef> players, int sleeping, int needed) {
        String message = configManager.getMessage("sleepProgress", "sleeping", String.valueOf(sleeping), "needed", String.valueOf(needed));
        for (PlayerRef player : players) {
            try {
                player.sendMessage(Message.raw(message).color("#FFFF55"));
            } catch (Exception e) {
                // Ignore
            }
        }
    }
    
    /**
     * Triggers the slumber state - sets time to morning and transitions sleeping players to MorningWakeUp.
     * This is the key method that the vanilla game uses to properly complete the sleep cycle.
     */
    private void triggerSlumber(Store<EntityStore> store, World world, WorldSomnolence worldSomnolence, 
                                 List<PlayerRef> players, int sleeping, int needed) {
        // Don't trigger if already in slumber
        if (worldSomnolence.getState() instanceof WorldSlumber) {
            return;
        }
        
        WorldTimeResource timeResource = store.getResource(WorldTimeResource.getResourceType());
        if (timeResource == null) {
            return;
        }
        
        // Get wake-up hour from game config
        SleepConfig sleepConfig = world.getGameplayConfig().getWorldConfig().getSleepConfig();
        float wakeUpHour = sleepConfig.getWakeUpHour();
        
        Instant currentTime = timeResource.getGameTime();
        Instant wakeUpTime = computeWakeupInstant(currentTime, wakeUpHour);
        
        // Set the game time to morning
        timeResource.setGameTime(wakeUpTime, world, store);
        
        // Transition all sleeping players to MorningWakeUp state
        for (PlayerRef player : players) {
            Ref<EntityStore> ref = player.getReference();
            if (ref == null) continue;
            
            PlayerSomnolence somnolence = store.getComponent(ref, PlayerSomnolence.getComponentType());
            if (somnolence == null) continue;
            
            PlayerSleep state = somnolence.getSleepState();
            
            // Only transition players who are actually sleeping (NoddingOff or Slumber)
            if (state instanceof PlayerSleep.NoddingOff || state instanceof PlayerSleep.Slumber) {
                PlayerSomnolence newSomnolence = new PlayerSomnolence(new PlayerSleep.MorningWakeUp(wakeUpTime));
                store.putComponent(ref, PlayerSomnolence.getComponentType(), newSomnolence);
            }
        }
        
        // Send success message
        String message = configManager.getMessage("sleepSkipping", "sleeping", String.valueOf(sleeping), "needed", String.valueOf(needed));
        for (PlayerRef player : players) {
            try {
                player.sendMessage(Message.raw(message).color("#55FF55"));
            } catch (Exception e) {
                // Ignore
            }
        }
    }
    
    /**
     * Computes the next wake-up instant based on the current time and wake-up hour.
     * If the wake-up hour has already passed today, returns tomorrow's wake-up time.
     */
    private Instant computeWakeupInstant(Instant currentTime, float wakeUpHour) {
        LocalDateTime current = LocalDateTime.ofInstant(currentTime, ZoneOffset.UTC);
        
        int hour = (int) wakeUpHour;
        float fractional = wakeUpHour - hour;
        int minute = (int) (fractional * 60.0f);
        
        LocalDateTime wakeUp = current.toLocalDate().atTime(hour, minute);
        
        // If we're past the wake-up time, use tomorrow
        if (!current.isBefore(wakeUp)) {
            wakeUp = wakeUp.plusDays(1);
        }
        
        return wakeUp.toInstant(ZoneOffset.UTC);
    }
    
    public int getSleepPercentage() {
        return configManager.getConfig().sleep.sleepPercentage;
    }

    public void setSleepPercentage(int percent) {
        configManager.getConfig().sleep.sleepPercentage = Math.max(0, Math.min(100, percent));
        configManager.saveConfig();
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
