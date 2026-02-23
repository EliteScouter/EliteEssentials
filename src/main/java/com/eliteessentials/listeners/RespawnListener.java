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
 * Handles player respawns after death - specifically CROSS-WORLD respawn.
 *
 * Behaviour:
 *  - If player has at least one respawn point (bed / custom), vanilla respawn is used.
 *  - If perWorld=true, Hytale's native WorldSpawnPoint handles same-world respawn
 *    (we sync /setspawn to the world's ISpawnProvider on startup and /setspawn).
 *  - If perWorld=false and player dies in the mainWorld, native respawn handles it.
 *  - If perWorld=false and player dies in a NON-main world, we do a cross-world
 *    teleport to the mainWorld's spawn (Hytale can't do cross-world respawn natively).
 *
 * Implementation detail:
 *  - We hook DeathComponent removal, and only intervene for cross-world cases.
 *  - Teleport is delayed 1s and executed on the death world's executor to avoid
 *    fighting the core JoinWorld/ClientReady flow.
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
                logger.info("[Respawn] Ref " + ref + " has no Player component (exception), skipping: " + ex.getMessage());
                logger.info("[Respawn] ========================================");
            }
            return;
        }

        if (player == null) {
            if (debugEnabled) {
                logger.info("[Respawn] Ref " + ref + " has no Player component, skipping.");
                logger.info("[Respawn] ========================================");
            }
            return;
        }

        // Get PlayerRef for UUID
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        final UUID playerId = playerRef != null ? playerRef.getUuid() : null;

        // Get death world
        EntityStore entityStore;
        World deathWorld;

        try {
            entityStore = store.getExternalData();
        } catch (Exception ex) {
            if (debugEnabled) {
                logger.info("[Respawn] Could not get external EntityStore: " + ex.getMessage());
                logger.info("[Respawn] ========================================");
            }
            return;
        }

        if (entityStore == null || entityStore.getWorld() == null) {
            if (debugEnabled) {
                logger.info("[Respawn] EntityStore or World is null, falling back to vanilla respawn.");
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
            return;
        }

        // --- Same-world respawn is now handled by Hytale's native WorldSpawnPoint ---
        // We synced our /setspawn to the world's ISpawnProvider, so Hytale's respawn
        // controller will teleport bedless players to the right spot automatically.
        // We only need to intervene for CROSS-WORLD respawn (perWorld=false, different world).

        if (config.spawn.perWorld) {
            // perWorld=true: each world has its own spawn, Hytale handles it natively
            if (debugEnabled) {
                logger.info("[Respawn] perWorld=true, native spawn provider handles same-world respawn.");
                logger.info("[Respawn] ========================================");
            }
            return;
        }

        // perWorld=false: check if we need cross-world teleport
        String targetWorldName = config.spawn.mainWorld;

        if (targetWorldName.equalsIgnoreCase(currentWorldName)) {
            // Dying in the main world - native spawn provider handles it
            if (debugEnabled) {
                logger.info("[Respawn] Player died in mainWorld '" + targetWorldName + "', native spawn provider handles respawn.");
                logger.info("[Respawn] ========================================");
            }
            return;
        }

        // Check if this world is excluded from respawn teleport (e.g. PVPArena worlds).
        // Entries support wildcards: "arena*" matches "Arena_1vs1-standard_1771781348556_3", etc.
        if (config.spawn.respawnExcludedWorlds != null) {
            for (String excluded : config.spawn.respawnExcludedWorlds) {
                if (excluded != null && worldMatchesPattern(currentWorldName, excluded)) {
                    if (debugEnabled) {
                        logger.info("[Respawn] World '" + currentWorldName + "' matched excluded pattern '" + excluded + "', skipping spawn teleport.");
                        logger.info("[Respawn] ========================================");
                    }
                    return;
                }
            }
        }

        // Cross-world respawn: player died in a non-main world, needs to go to mainWorld
        SpawnStorage.SpawnData ourSpawn = spawnStorage.getSpawn(targetWorldName);

        if (ourSpawn == null) {
            if (debugEnabled) {
                logger.info("[Respawn] No /setspawn set for mainWorld '" + targetWorldName + "', using vanilla respawn.");
                logger.info("[Respawn] ========================================");
            }
            return;
        }

        World targetWorld = findWorldByName(targetWorldName);

        if (targetWorld == null) {
            logger.warning("[Respawn] Target mainWorld '" + targetWorldName + "' not found, using vanilla respawn");
            if (debugEnabled) {
                logger.info("[Respawn] ========================================");
            }
            return;
        }

        if (debugEnabled) {
            logger.info("[Respawn] Cross-world respawn: '" + currentWorldName + "' -> '" + targetWorldName + "'");
        }

        // Prepare immutable copies for async usage
        final World deathWorldFinal = deathWorld;
        final Vector3d spawnPos = new Vector3d(ourSpawn.x, ourSpawn.y, ourSpawn.z);
        final Vector3f spawnRot = new Vector3f(0, ourSpawn.yaw, 0);
        final Store<EntityStore> storeFinal = store;
        final Ref<EntityStore> refFinal = ref;

        // Delay the cross-world teleport to avoid interfering with the core respawn handshake
        CompletableFuture
                .delayedExecutor(1L, TimeUnit.SECONDS)
                .execute(() -> {
                    try {
                        if (!deathWorldFinal.isAlive()) {
                            if (debugEnabled) {
                                logger.info("[Respawn] Death world is no longer alive; skipping.");
                            }
                            return;
                        }

                        deathWorldFinal.execute(() -> {
                            try {
                                if (!refFinal.isValid()) {
                                    if (debugEnabled) {
                                        logger.info("[Respawn] Ref no longer valid; skipping.");
                                        logger.info("[Respawn] ========================================");
                                    }
                                    return;
                                }

                                if (playerId != null && !TeleportGuard.get().tryAcquireAutomatic(playerId, "Respawn-CrossWorld", debugEnabled)) {
                                    if (debugEnabled) {
                                        logger.info("[Respawn] ========================================");
                                    }
                                    return;
                                }

                                Teleport teleport = new Teleport(targetWorld, spawnPos, spawnRot);
                                storeFinal.putComponent(refFinal, Teleport.getComponentType(), teleport);

                                if (debugEnabled) {
                                    logger.info("[Respawn] Cross-world teleport to mainWorld '" +
                                            targetWorldName + "' at " + String.format("%.1f, %.1f, %.1f",
                                            ourSpawn.x, ourSpawn.y, ourSpawn.z));
                                    logger.info("[Respawn] ========================================");
                                }
                            } catch (Throwable t) {
                                logger.warning("[Respawn] Failed cross-world teleport: " + t.getMessage());
                            }
                        });
                    } catch (Throwable t) {
                        logger.warning("[Respawn] Failed to schedule cross-world teleport: " + t.getMessage());
                    }
                });
    }

    /**
     * Checks whether a world name matches a pattern.
     * Supports case-insensitive exact matches and glob-style wildcards (*).
     * e.g. "arena*" matches "Arena_1vs1-standard_1771781348556_3"
     */
    private boolean worldMatchesPattern(String worldName, String pattern) {
        if (!pattern.contains("*")) {
            return pattern.equalsIgnoreCase(worldName);
        }
        // Convert glob pattern to regex: escape everything, then replace \* with .*
        String regex = "(?i)" + java.util.regex.Pattern.quote(pattern).replace("\\*", ".*");
        return worldName.matches(regex);
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
