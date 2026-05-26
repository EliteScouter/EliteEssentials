package com.eliteessentials.services;

import com.eliteessentials.config.ConfigManager;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import org.joml.Vector3d;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.protocol.SavedMovementStates;
import com.hypixel.hytale.protocol.packets.player.SetMovementStates;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
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
import java.util.logging.Logger;

/**
 * Schedules auto-disable of flight when cost-per-minute is used.
 * After the configured duration, flight is turned off and the player is notified.
 */
public class FlyService {

    private static final int MAX_HEIGHT = 256;
    private static final Logger logger = Logger.getLogger("EliteEssentials");

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

            // Safe landing: teleport player to ground to prevent fall damage
            safeLandPlayer(store, ref, playerRef, world);

            String message = configManager.getMessage("flyExpired");
            playerRef.sendMessage(MessageFormatter.formatWithFallback(message, "#FFAA00"));
        });
    }

    /**
     * Teleports the player to the highest solid block below them to prevent fall damage
     * when flight is disabled. Uses the same ground detection as /top.
     */
    private void safeLandPlayer(Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef player, World world) {
        try {
            TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
            if (transform == null) return;

            Vector3d pos = transform.getPosition();
            int blockX = (int) Math.floor(pos.x);
            int blockZ = (int) Math.floor(pos.z);

            long chunkIndex = ChunkUtil.indexChunkFromBlock(blockX, blockZ);
            WorldChunk chunk = world.getChunk(chunkIndex);
            if (chunk == null) return;

            Integer groundY = findHighestSolidBlock(chunk, blockX, blockZ, (int) pos.y);
            if (groundY == null) return;

            // Only teleport if player is above the ground (would actually fall)
            double targetY = groundY + 1;
            if (pos.y - targetY < 3.0) return;

            double centerX = Math.floor(pos.x) + 0.5;
            double centerZ = Math.floor(pos.z) + 0.5;
            Vector3d targetPos = new Vector3d(centerX, targetY, centerZ);
            Rotation3f targetRot = new Rotation3f(0, 0, 0);

            // Preserve player's current yaw
            HeadRotation headRotation = store.getComponent(ref, HeadRotation.getComponentType());
            if (headRotation != null) {
                targetRot = new Rotation3f(0, headRotation.getRotation().y, 0);
            }

            Teleport teleport = Teleport.createForPlayer(world, targetPos, targetRot);
            store.addComponent(ref, Teleport.getComponentType(), teleport);
        } catch (Exception e) {
            logger.warning("[FlyService] Safe landing failed: " + e.getMessage());
        }
    }

    /**
     * Finds the highest solid block at the given X/Z position, starting from the player's Y level downward.
     */
    private Integer findHighestSolidBlock(WorldChunk chunk, int x, int z, int startY) {
        int scanFrom = Math.min(startY, MAX_HEIGHT);
        for (int y = scanFrom; y >= 0; y--) {
            BlockType blockType = chunk.getBlockType(x, y, z);
            if (blockType != null && blockType.getMaterial() == BlockMaterial.Solid) {
                return y;
            }
        }
        return null;
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
