package com.eliteessentials.services;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.util.MessageFormatter;
import com.hypixel.hytale.builtin.beds.sleep.components.PlayerSleep;
import com.hypixel.hytale.builtin.beds.sleep.components.PlayerSomnolence;
import com.hypixel.hytale.builtin.beds.sleep.resources.WorldSlumber;
import com.hypixel.hytale.builtin.beds.sleep.resources.WorldSomnolence;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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

    // Time in milliseconds a player must be in NoddingOff state before counting as sleeping
    private static final long NODDING_OFF_THRESHOLD_MS = 3200;

    private final ConfigManager configManager;
    private final ScheduledExecutorService scheduler;
    private volatile boolean initialized = false;
    
    // Per-world sleep state tracking to prevent race conditions
    private final Map<String, WorldSleepState> worldSleepStates = new ConcurrentHashMap<>();
    
    private static class WorldSleepState {
        volatile boolean slumberTriggered = false;
        volatile int lastSleepingCount = -1;
        volatile int lastAfkCount = -1;
        
        void reset() {
            slumberTriggered = false;
            lastSleepingCount = -1;
            lastAfkCount = -1;
        }
    }

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
        // Get or create per-world state BEFORE entering world.execute()
        String worldName = world.getName();
        WorldSleepState sleepState = worldSleepStates.computeIfAbsent(worldName, k -> new WorldSleepState());
        
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
                if (players.isEmpty()) return;
                
                // Exclude AFK players from total count if configured
                int afkCount = 0;
                AfkService afkService = com.eliteessentials.EliteEssentials.getInstance().getAfkService();
                boolean excludeAfk = configManager.getConfig().afk.excludeFromSleep && afkService != null;
                if (excludeAfk) {
                    for (PlayerRef player : players) {
                        if (player != null && player.getUuid() != null && afkService.isAfk(player.getUuid())) {
                            afkCount++;
                        }
                    }
                    if (afkCount > 0 && configManager.isDebugEnabled() && afkCount != sleepState.lastAfkCount) {
                        logger.info("[Sleep] Excluding " + afkCount + " AFK player(s) from sleep percentage");
                    }
                    sleepState.lastAfkCount = afkCount;
                }
                
                int totalPlayers = players.size() - afkCount;
                if (totalPlayers <= 0) return; // All players are AFK
                int sleepingPlayers = 0;
                
                // Get current game time for checking NoddingOff/MorningWakeUp states
                WorldTimeResource timeResource = store.getResource(WorldTimeResource.getResourceType());
                if (timeResource == null) return;
                
                Instant gameTime = timeResource.getGameTime();
                
                // Check if it's nighttime based on config settings
                // Default: Sleep is allowed from 9 PM (21:00) to 5 AM (05:00)
                LocalDateTime currentDateTime = LocalDateTime.ofInstant(gameTime, ZoneOffset.UTC);
                double currentFractionalHour = currentDateTime.getHour() + currentDateTime.getMinute() / 60.0;
                
                double nightStart = configManager.getConfig().sleep.nightStartHour;
                double morningHour = configManager.getConfig().sleep.morningHour;
                
                // Nighttime check depends on whether night spans midnight
                // If nightStart > morningHour (e.g., 21 to 5), night spans midnight
                boolean isNighttime;
                if (nightStart > morningHour) {
                    // Night spans midnight: hour >= nightStart OR hour < morningHour
                    isNighttime = currentFractionalHour >= nightStart || currentFractionalHour < morningHour;
                } else {
                    // Night doesn't span midnight (unusual but supported)
                    isNighttime = currentFractionalHour >= nightStart && currentFractionalHour < morningHour;
                }
                
                if (!isNighttime) {
                    // Reset flags during daytime
                    sleepState.reset();
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
                    // Count NoddingOff only if enough time has passed
                    else if (state instanceof PlayerSleep.NoddingOff noddingOff) {
                        Instant threshold = noddingOff.realTimeStart().plusMillis(NODDING_OFF_THRESHOLD_MS);
                        if (Instant.now().isAfter(threshold)) {
                            sleepingPlayers++;
                        }
                    }
                }
                
                // Reset when no one is sleeping
                if (sleepingPlayers == 0) {
                    sleepState.reset();
                    return;
                }
                // Calculate percentage and check threshold
                int currentPercent = (sleepingPlayers * 100) / totalPlayers;
                int playersNeeded = Math.max(1, (int) Math.ceil(totalPlayers * requiredPercent / 100.0));
                
                // Synchronized block to prevent race conditions between check and update
                synchronized (sleepState) {
                    if (currentPercent >= requiredPercent && !sleepState.slumberTriggered) {
                        triggerSlumber(store, world, worldSomnolence, players, sleepingPlayers, playersNeeded);
                        sleepState.slumberTriggered = true;
                        sleepState.lastSleepingCount = sleepingPlayers;
                    } else if (sleepingPlayers != sleepState.lastSleepingCount && !sleepState.slumberTriggered && sleepingPlayers > 0) {
                        // Only send message if count changed, slumber not triggered, and someone is sleeping
                        sleepState.lastSleepingCount = sleepingPlayers;
                        sendSleepMessage(players, sleepingPlayers, playersNeeded);
                    }
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
                player.sendMessage(MessageFormatter.formatWithFallback(message, "#FFFF55"));
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
        
        // Use configured morning hour instead of game default (supports decimal for minutes)
        double configuredMorningHour = configManager.getConfig().sleep.morningHour;
        float wakeUpHour = (float) configuredMorningHour;
        
        Instant currentTime = timeResource.getGameTime();
        Instant wakeUpTime = computeWakeupInstant(currentTime, wakeUpHour);
        
        // Compute IRL duration for the slumber animation (matches engine's StartSlumberSystem logic)
        long gameHours = java.util.concurrent.TimeUnit.MILLISECONDS.toHours(
                java.time.Duration.between(currentTime, wakeUpTime).toMillis());
        float irlSeconds = (float) Math.ceil(Math.max(3.0, gameHours / 6.0));
        
        // Let the engine handle the time transition via WorldSlumber state.
        // The engine's UpdateWorldSlumberSystem will progressively advance time and
        // transition players to MorningWakeUp when complete. Calling setGameTime
        // directly conflicts with the engine's systems and can crash the world.
        worldSomnolence.setState(new WorldSlumber(currentTime, wakeUpTime, irlSeconds));
        worldSomnolence.resetNotificationCooldown();
        
        // Transition sleeping players to Slumber state so the engine recognizes them
        // during the UpdateWorldSlumberSystem tick (uses the factory method like the engine does)
        for (PlayerRef player : players) {
            Ref<EntityStore> ref = player.getReference();
            if (ref == null || !ref.isValid()) continue;
            
            PlayerSomnolence somnolence = store.getComponent(ref, PlayerSomnolence.getComponentType());
            if (somnolence == null) continue;
            
            PlayerSleep state = somnolence.getSleepState();
            
            // Ensure NoddingOff players are promoted to Slumber so the engine's
            // UpdateWorldSlumberSystem will properly transition them to MorningWakeUp
            if (state instanceof PlayerSleep.NoddingOff) {
                store.putComponent(ref, PlayerSomnolence.getComponentType(),
                        PlayerSleep.Slumber.createComponent(timeResource));
            }
        }
        
        // Send success message
        String message = configManager.getMessage("sleepSkipping", "sleeping", String.valueOf(sleeping), "needed", String.valueOf(needed));
        for (PlayerRef player : players) {
            try {
                player.sendMessage(MessageFormatter.formatWithFallback(message, "#55FF55"));
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
