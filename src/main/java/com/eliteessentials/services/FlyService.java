package com.eliteessentials.services;

import com.eliteessentials.config.ConfigManager;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.protocol.SavedMovementStates;
import com.hypixel.hytale.protocol.packets.player.SetMovementStates;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.eliteessentials.config.PluginConfig;
import com.eliteessentials.util.MessageFormatter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Schedules auto-disable of flight when cost-per-minute is used.
 * After the configured duration, flight is turned off and the player is notified.
 */
public class FlyService {

    private final ConfigManager configManager;
    private final ScheduledExecutorService scheduler;
    /** All scheduled tasks per player (expiry + warning notifications). */
    private final Map<UUID, List<ScheduledFuture<?>>> playerTasks = new ConcurrentHashMap<>();

    public FlyService(ConfigManager configManager) {
        this.configManager = configManager;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "EliteEssentials-FlyExpiry");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Schedule flight to auto-disable after the given duration, plus optional warnings before expiry.
     * Cancels any existing expiry for this player.
     */
    public void scheduleExpiry(UUID playerId, int durationSeconds) {
        cancelExpiry(playerId);
        List<ScheduledFuture<?>> tasks = new ArrayList<>();
        PluginConfig.FlyConfig flyConfig = configManager.getConfig().fly;
        List<Integer> warningSeconds = flyConfig.expiryWarningSeconds != null ? flyConfig.expiryWarningSeconds : List.of();

        for (Integer w : warningSeconds) {
            if (w == null || w <= 0 || w >= durationSeconds) continue;
            int delaySeconds = durationSeconds - w;
            final int secondsLeft = w;
            ScheduledFuture<?> f = scheduler.schedule(() -> sendExpiryWarning(playerId, secondsLeft), delaySeconds, TimeUnit.SECONDS);
            tasks.add(f);
        }
        ScheduledFuture<?> expiryFuture = scheduler.schedule(() -> expireFlight(playerId), durationSeconds, TimeUnit.SECONDS);
        tasks.add(expiryFuture);
        playerTasks.put(playerId, tasks);
    }

    private void sendExpiryWarning(UUID playerId, int secondsLeft) {
        PlayerRef playerRef = Universe.get().getPlayer(playerId);
        if (playerRef == null || !playerRef.isValid()) return;
        String message = configManager.getMessage("flyExpiringIn", "seconds", String.valueOf(secondsLeft));
        playerRef.sendMessage(MessageFormatter.formatWithFallback(message, "#FFAA00"));
    }

    /**
     * Cancel any scheduled flight expiry and warnings for this player (e.g. they toggled fly off manually).
     */
    public void cancelExpiry(UUID playerId) {
        List<ScheduledFuture<?>> existing = playerTasks.remove(playerId);
        if (existing != null) {
            for (ScheduledFuture<?> f : existing) {
                f.cancel(false);
            }
        }
    }

    private void expireFlight(UUID playerId) {
        playerTasks.remove(playerId);
        PlayerRef playerRef = Universe.get().getPlayer(playerId);
        if (playerRef == null || !playerRef.isValid()) {
            return;
        }
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) {
            return;
        }
        Store<EntityStore> store = ref.getStore();
        World world = Universe.get().getWorld(playerRef.getWorldUuid());
        if (world == null) {
            return;
        }
        world.execute(() -> {
            MovementManager movementManager = store.getComponent(ref, MovementManager.getComponentType());
            if (movementManager == null) {
                return;
            }
            var settings = movementManager.getSettings();
            if (!settings.canFly) {
                return; // already off
            }
            settings.canFly = false;
            movementManager.update(playerRef.getPacketHandler());

            MovementStatesComponent movementStatesComponent = store.getComponent(ref, MovementStatesComponent.getComponentType());
            if (movementStatesComponent != null) {
                MovementStates movementStates = movementStatesComponent.getMovementStates();
                if (movementStates.flying) {
                    movementStates.flying = false;
                    playerRef.getPacketHandler().writeNoCache(new SetMovementStates(new SavedMovementStates(false)));
                }
            }

            String message = configManager.getMessage("flyExpired");
            playerRef.sendMessage(MessageFormatter.formatWithFallback(message, "#FFAA00"));
        });
    }

    /**
     * Shutdown the scheduler. Call on plugin disable.
     */
    public void shutdown() {
        for (List<ScheduledFuture<?>> tasks : playerTasks.values()) {
            for (ScheduledFuture<?> f : tasks) {
                f.cancel(false);
            }
        }
        playerTasks.clear();
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            scheduler.shutdownNow();
        }
    }
}
