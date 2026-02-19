package com.eliteessentials.util;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.logging.Logger;

/**
 * Utility for player teleportation matching EssentialsPlus's proven approach:
 * direct synchronous addComponent with NO async chunk pre-loading and NO
 * extra world.execute() wrapping.
 *
 * The engine's TeleportSystem handles chunk loading internally when it
 * processes the Teleport component. Pre-loading chunks or deferring the
 * addComponent to a later tick via world.execute() causes stale store/ref,
 * leading to IndexOutOfBoundsException in ArchetypeChunk.getComponent.
 *
 * CRITICAL: NEVER call tryRemoveComponent before addComponent for Teleport.
 * CRITICAL: NEVER use getChunkAsync before teleporting - it creates stale refs.
 * CRITICAL: NEVER wrap addComponent in world.execute() if already on the world
 *           thread - the deferred execution causes the ref to go stale.
 * CRITICAL: Call these methods ONLY from the world thread (command execute, or
 *           inside an existing world.execute() block).
 */
public class TeleportUtil {

    private static final Logger logger = Logger.getLogger("EliteEssentials");

    /**
     * Teleport a player using store/ref directly. MUST be called on the world thread.
     * This is the preferred approach for same-world teleports from commands.
     *
     * Calls addComponent IMMEDIATELY (no world.execute wrapping) to avoid
     * deferring to a later tick where the ref would be stale.
     *
     * @param targetWorld The destination world (may be null for same-world)
     * @param targetPos   The destination position
     * @param targetRot   The destination rotation (head rotation)
     * @param store       The player's current store (from command context)
     * @param ref         The player's current ref (from command context)
     * @param onSuccess   Callback after teleport component is applied (may be null)
     * @param onFailure   Callback if ref is invalid (may be null)
     */
    public static void safeTeleport(World world, World targetWorld, Vector3d targetPos, Vector3f targetRot,
                                     Store<EntityStore> store, Ref<EntityStore> ref,
                                     Runnable onSuccess, Runnable onFailure) {
        if (!ref.isValid()) {
            logger.warning("[TeleportUtil] Player ref is not valid, aborting teleport");
            if (onFailure != null) onFailure.run();
            return;
        }

        Teleport teleport = Teleport.createForPlayer(targetWorld, targetPos, targetRot);
        store.addComponent(ref, Teleport.getComponentType(), teleport);

        if (onSuccess != null) {
            onSuccess.run();
        }
    }

    /**
     * Teleport a player using PlayerRef. MUST be called on the world thread.
     * Used for cross-world teleports or when store/ref are not available.
     *
     * Gets fresh store/ref from PlayerRef and calls addComponent IMMEDIATELY.
     *
     * @param world       The CURRENT world the player is in
     * @param targetWorld The destination world
     * @param targetPos   The destination position
     * @param targetRot   The destination rotation (head rotation)
     * @param playerRef   The player's PlayerRef
     * @param onSuccess   Callback after teleport component is applied (may be null)
     * @param onFailure   Callback if player ref is invalid (may be null)
     */
    public static void safeTeleport(World world, World targetWorld, Vector3d targetPos, Vector3f targetRot,
                                     PlayerRef playerRef,
                                     Runnable onSuccess, Runnable onFailure) {
        Ref<EntityStore> freshRef = playerRef.getReference();
        if (freshRef == null || !freshRef.isValid()) {
            logger.warning("[TeleportUtil] Player ref is no longer valid, aborting teleport");
            if (onFailure != null) onFailure.run();
            return;
        }

        Store<EntityStore> freshStore = freshRef.getStore();
        if (freshStore == null) {
            logger.warning("[TeleportUtil] Player store is null, aborting teleport");
            if (onFailure != null) onFailure.run();
            return;
        }

        Teleport teleport = Teleport.createForPlayer(targetWorld, targetPos, targetRot);
        freshStore.addComponent(freshRef, Teleport.getComponentType(), teleport);

        if (onSuccess != null) {
            onSuccess.run();
        }
    }
}
