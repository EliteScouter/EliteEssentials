package com.eliteessentials.util;

import com.eliteessentials.EliteEssentials;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.logging.Logger;

/**
 * Utility for safe teleportation that ensures the destination chunk is loaded
 * before moving the player. Prevents "entity moved into unloaded chunk" crashes
 * that can bring down the world.
 */
public class TeleportUtil {

    private static final Logger logger = Logger.getLogger("EliteEssentials");

    /**
     * Safely teleport a player by pre-loading the destination chunk.
     * The onSuccess callback runs on the world thread after the chunk is confirmed loaded.
     * The onFailure callback runs if the chunk cannot be loaded.
     *
     * @param world       The CURRENT world the player is in (teleport executes on this thread)
     * @param targetWorld The destination world (may be same as world)
     * @param targetPos   The destination position
     * @param targetRot   The destination rotation
     * @param store       The current world's store
     * @param ref         The player's entity ref
     * @param onSuccess   Runs on world thread after teleport component is applied (may be null)
     * @param onFailure   Runs on world thread if chunk load fails (may be null)
     */
    public static void safeTeleport(World world, World targetWorld, Vector3d targetPos, Vector3f targetRot,
                                     Store<EntityStore> store, Ref<EntityStore> ref,
                                     Runnable onSuccess, Runnable onFailure) {
        boolean debug = EliteEssentials.getInstance().getConfigManager().isDebugEnabled();

        // Calculate which chunk the destination is in
        long chunkIndex = ChunkUtil.indexChunkFromBlock(targetPos.getX(), targetPos.getZ());

        // Determine which world to load the chunk from (the destination world)
        World chunkWorld = targetWorld != null ? targetWorld : world;

        // Fast path: chunk already loaded
        if (chunkWorld.getChunk(chunkIndex) != null) {
            if (debug) {
                logger.info("[TeleportUtil] Destination chunk already loaded, teleporting immediately");
            }
            executeOnWorldThread(world, targetWorld, targetPos, targetRot, store, ref, onSuccess);
            return;
        }

        // Slow path: load chunk async, then teleport
        if (debug) {
            logger.info("[TeleportUtil] Destination chunk not loaded, loading async before teleport...");
        }

        chunkWorld.getChunkAsync(chunkIndex).whenComplete((loadedChunk, error) -> {
            if (error != null || loadedChunk == null) {
                logger.warning("[TeleportUtil] Failed to load destination chunk: " +
                        (error != null ? error.getMessage() : "null chunk"));
                if (onFailure != null) {
                    world.execute(onFailure);
                }
                return;
            }

            if (debug) {
                logger.info("[TeleportUtil] Destination chunk loaded successfully, teleporting");
            }
            executeOnWorldThread(world, targetWorld, targetPos, targetRot, store, ref, onSuccess);
        });
    }

    private static void executeOnWorldThread(World world, World targetWorld, Vector3d targetPos, Vector3f targetRot,
                                              Store<EntityStore> store, Ref<EntityStore> ref, Runnable onSuccess) {
        world.execute(() -> {
            if (!ref.isValid()) return;
            Teleport teleport = new Teleport(targetWorld, targetPos, targetRot);
            store.putComponent(ref, Teleport.getComponentType(), teleport);
            if (onSuccess != null) {
                onSuccess.run();
            }
        });
    }
}
