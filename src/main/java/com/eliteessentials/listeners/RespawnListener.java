package com.eliteessentials.listeners;

import com.eliteessentials.EliteEssentials;
import com.eliteessentials.config.PluginConfig;
import com.eliteessentials.storage.SpawnStorage;
import com.eliteessentials.util.TeleportGuard;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefChangeSystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.data.PlayerConfigData;
import com.hypixel.hytale.server.core.entity.entities.player.data.PlayerRespawnPointData;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Handles player respawns after death.
 *
 * Behaviour:
 *  - If player has at least one respawn point (bed / custom), vanilla respawn is used.
 *  - If player has no respawn points, they respawn at the /setspawn location (per-world or main world).
 *
 * Implementation detail:
 *  - We hook DeathComponent removal, BUT teleport is delayed and executed on the death world's executor
 *    to avoid fighting the core JoinWorld/ClientReady flow and causing "ghost" players.
 */
public class RespawnListener extends RefChangeSystem<EntityStore, DeathComponent> {

    private static final Logger logger = Logger.getLogger("EliteEssentials");

    private final SpawnStorage spawnStorage;

    public RespawnListener(SpawnStorage spawnStorage) {
        this.spawnStorage = spawnStorage;
    }

    @Override
    public @NotNull ComponentType<EntityStore, DeathComponent> componentType() {
        return DeathComponent.getComponentType();
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    public void onComponentAdded(@NotNull Ref<EntityStore> ref,
                                 @NotNull DeathComponent component,
                                 @NotNull Store<EntityStore> store,
                                 @NotNull CommandBuffer<EntityStore> buffer) {
        // We only care about respawn (component removal).
    }

    @Override
    public void onComponentSet(@NotNull Ref<EntityStore> ref,
                               DeathComponent oldComponent,
                               @NotNull DeathComponent newComponent,
                               @NotNull Store<EntityStore> store,
                               @NotNull CommandBuffer<EntityStore> buffer) {
        // No-op
    }

    @Override
    public void onComponentRemoved(@NotNull Ref<EntityStore> ref,
                                   @NotNull DeathComponent component,
                                   @NotNull Store<EntityStore> store,
                                   @NotNull CommandBuffer<EntityStore> buffer) {

        boolean debugEnabled = EliteEssentials.getInstance().getConfigManager().isDebugEnabled();
        PluginConfig config = EliteEssentials.getInstance().getConfigManager().getConfig();

        if (debugEnabled) {
            logger.info("[Respawn] ========== PLAYER RESPAWNING ==========");
            logger.info("[Respawn] Ref: " + ref);
        }

        // Ensure this ref actually belongs to a Player
        Player player;
        try {
            player = store.getComponent(ref, Player.getComponentType());
        } catch (Exception ex) {
            if (debugEnabled) {
                logger.info("[Respawn] Ref " + ref + " has no Player component (exception), skipping respawn handling: " + ex.getMessage());
                logger.info("[Respawn] ========================================");
            }
            return;
        }

        if (player == null) {
            if (debugEnabled) {
                logger.info("[Respawn] Ref " + ref + " has no Player component, skipping respawn handling.");
                logger.info("[Respawn] ========================================");
            }
            return;
        }

        // Get death world
        EntityStore entityStore;
        World deathWorld;

        // Get PlayerRef for UUID (needed for teleport guard)
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        final UUID playerId = playerRef != null ? playerRef.getUuid() : null;

        try {
            entityStore = store.getExternalData();
        } catch (Exception ex) {
            if (debugEnabled) {
                logger.info("[Respawn] Could not get external EntityStore for ref " + ref + ": " + ex.getMessage());
                logger.info("[Respawn] ========================================");
            }
            return;
        }

        if (entityStore == null || entityStore.getWorld() == null) {
            if (debugEnabled) {
                logger.info("[Respawn] EntityStore or World is null for ref " + ref + ", falling back to vanilla respawn.");
                logger.info("[Respawn] ========================================");
            }
            return;
        }

        deathWorld = entityStore.getWorld();
        String currentWorldName = deathWorld.getName();

        // --- Bed / custom respawn detection via PlayerConfigData ---
        boolean hasBedSpawn = false;
        try {
            PlayerConfigData data = player.getPlayerConfigData();
            PlayerRespawnPointData[] respawnPoints =
                    data.getPerWorldData(deathWorld.getName()).getRespawnPoints();
            hasBedSpawn = (respawnPoints != null && respawnPoints.length > 0);
        } catch (Exception ex) {
            if (debugEnabled) {
                logger.info("[Respawn] Failed to inspect PlayerConfigData for bed spawn: " + ex.getMessage());
            }
        }

        if (hasBedSpawn) {
            if (debugEnabled) {
                logger.info("[Respawn] Player has bed/custom respawn points; using vanilla respawn.");
                logger.info("[Respawn] ========================================");
            }
            // Let vanilla bed / respawn logic stand.
            return;
        }

        // --- No bed/custom respawn: fall back to our /setspawn logic ---

        String targetWorldName;
        if (config.spawn.perWorld) {
            targetWorldName = currentWorldName;
            if (debugEnabled) {
                logger.info("[Respawn] perWorld=true, using current world spawn: " + targetWorldName);
            }
        } else {
            targetWorldName = config.spawn.mainWorld;
            if (debugEnabled) {
                logger.info("[Respawn] perWorld=false, using mainWorld spawn: " + targetWorldName);
            }
        }

        SpawnStorage.SpawnData ourSpawn = spawnStorage.getSpawn(targetWorldName);

        if (ourSpawn == null) {
            // No spawn set for target world - let vanilla handle respawn
            if (debugEnabled) {
                logger.info("[Respawn] No /setspawn set for world '" + targetWorldName + "', using vanilla respawn.");
                logger.info("[Respawn] ========================================");
            }
            return;
        }

        World targetWorld = findWorldByName(targetWorldName);
        if (targetWorld == null) {
            logger.warning("[Respawn] Target world '" + targetWorldName + "' not found, using vanilla respawn");
            if (debugEnabled) {
                logger.info("[Respawn] ========================================");
            }
            return;
        }

        boolean isCrossWorld = !targetWorldName.equalsIgnoreCase(currentWorldName);
        if (isCrossWorld && debugEnabled) {
            logger.info("[Respawn] Cross-world teleport from '" + currentWorldName +
                    "' to '" + targetWorldName + "'");
        }

        // Prepare immutable copies for async usage
        final World deathWorldFinal = deathWorld;
        final World targetWorldFinal = targetWorld;
        final Vector3d spawnPos = new Vector3d(ourSpawn.x, ourSpawn.y, ourSpawn.z);
        final Vector3f spawnRot = new Vector3f(0, ourSpawn.yaw, 0); // pitch=0, yaw, roll=0
        final Store<EntityStore> storeFinal = store;
        final Ref<EntityStore> refFinal = ref;

        // === CRITICAL CHANGE ===
        // Delay the teleport a bit and run it on the death world's executor.
        // This avoids interfering with the core respawn/ClientReady handshake and fixes "ghost" players.
        CompletableFuture
                .delayedExecutor(1L, TimeUnit.SECONDS) // 1s delay like HyThroneUtils
                .execute(() -> {
                    try {
                        if (!deathWorldFinal.isAlive()) {
                            if (debugEnabled) {
                                logger.info("[Respawn] Death world is no longer alive; skipping delayed teleport.");
                            }
                            return;
                        }

                        deathWorldFinal.execute(() -> {
                            try {
                                if (!refFinal.isValid()) {
                                    if (debugEnabled) {
                                        logger.info("[Respawn] Ref is no longer valid at delayed teleport time; skipping.");
                                        logger.info("[Respawn] ========================================");
                                    }
                                    return;
                                }

                                // Guard: skip if another teleport is already in-flight for this player
                                if (playerId != null && !TeleportGuard.get().tryAcquireAutomatic(playerId, "Respawn", debugEnabled)) {
                                    if (debugEnabled) {
                                        logger.info("[Respawn] ========================================");
                                    }
                                    return;
                                }

                                Teleport teleport = new Teleport(targetWorldFinal, spawnPos, spawnRot);
                                storeFinal.putComponent(refFinal, Teleport.getComponentType(), teleport);

                                if (debugEnabled) {
                                    logger.info("[Respawn] (DELAYED) No bed spawn - teleporting to /setspawn in world '" +
                                            targetWorldName + "' at " + String.format("%.1f, %.1f, %.1f",
                                            ourSpawn.x, ourSpawn.y, ourSpawn.z));
                                    logger.info("[Respawn] ========================================");
                                }
                            } catch (Throwable t) {
                                logger.warning("[Respawn] Failed to apply Teleport component in delayed task: " + t.getMessage());
                            }
                        });
                    } catch (Throwable t) {
                        logger.warning("[Respawn] Failed to schedule delayed teleport: " + t.getMessage());
                    }
                });
    }

    /**
     * Find a world by name (case-insensitive).
     */
    private World findWorldByName(String worldName) {
        if (worldName == null) return null;

        try {
            Universe universe = Universe.get();
            if (universe == null) return null;

            for (World w : universe.getWorlds().values()) {
                if (w.getName().equalsIgnoreCase(worldName)) {
                    return w;
                }
            }
        } catch (Exception e) {
            logger.warning("[Respawn] Error finding world '" + worldName + "': " + e.getMessage());
        }
        return null;
    }
}
