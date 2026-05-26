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
import org.joml.Vector3d;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.data.PlayerConfigData;
import com.hypixel.hytale.server.core.entity.entities.player.data.PlayerRespawnPointData;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.jetbrains.annotations.NotNull;

import com.eliteessentials.services.GreetingService;
import com.eliteessentials.spawn.DeathPositionCache;

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
    private final DeathPositionCache deathPositionCache;
    private GreetingService greetingService;

    public RespawnListener(SpawnStorage spawnStorage, DeathPositionCache deathPositionCache) {
        this.spawnStorage = spawnStorage;
        this.deathPositionCache = deathPositionCache;
    }

    public void setGreetingService(GreetingService service) {
        this.greetingService = service;
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
        // Capture death position for nearest-spawn logic (used by NearestSpawnProvider or our delayed teleport).
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) return;
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) return;
        Vector3d pos = transform.getPosition();
        deathPositionCache.put(playerRef.getUuid(), pos.x, pos.z);
        if (EliteEssentials.getInstance().getConfigManager().isDebugEnabled()) {
            logger.info("[Respawn] DeathComponent ADDED - stored death position for " + playerRef.getUuid() +
                " at " + String.format("%.1f, %.1f", pos.x, pos.z));
        }
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

        if (debugEnabled) {
            Object provider = null;
            try {
                provider = deathWorld.getWorldConfig().getSpawnProvider();
            } catch (Exception ignored) {}
            logger.info("[Respawn] PlayerId: " + playerId + ", World: " + currentWorldName);
            logger.info("[Respawn] SpawnProvider: " + (provider != null ? provider.getClass().getSimpleName() : "null"));
            logger.info("[Respawn] perWorld: " + config.spawn.perWorld + ", mainWorld: " + config.spawn.mainWorld);
        }

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

        // --- NearestSpawnProvider handled respawn (single teleport, no flash) ---
        String chosenSpawnName = playerId != null ? deathPositionCache.pollChosenSpawn(playerId) : null;
        if (debugEnabled && chosenSpawnName == null) {
            logger.info("[Respawn] chosenSpawn=null (NearestSpawnProvider did NOT handle - provider not used or world has GlobalSpawnProvider)");
        }
        if (chosenSpawnName != null) {
            if (playerId != null) {
                deathPositionCache.remove(playerId);
            }
            if (debugEnabled) {
                logger.info("[Respawn] *** NearestSpawnProvider handled respawn (single TP) ***");
                logger.info("[Respawn] Chosen spawn: '" + chosenSpawnName + "'");
                logger.info("[Respawn] ========================================");
            }
            if (playerRef != null) {
                var configManager = EliteEssentials.getInstance().getConfigManager();
                playerRef.sendMessage(com.eliteessentials.util.MessageFormatter.formatWithFallback(
                    configManager.getMessage("spawnRespawnedAt", "name", chosenSpawnName), "#AAAAAA"));
            }
            if (greetingService != null && playerRef != null) {
                greetingService.evaluate(playerRef, "respawn", currentWorldName, false, chosenSpawnName);
            }
            return;
        }

        // --- Same-world respawn handling (fallback when world doesn't have NearestSpawnProvider) ---
        // When perWorld=true with multiple spawn points, we need to find the nearest
        // spawn to the death location (Hytale's native provider only knows one spawn).
        // When only one spawn exists, the native provider handles it.

        if (config.spawn.perWorld) {
            java.util.List<SpawnStorage.SpawnData> worldSpawns = spawnStorage.getSpawns(currentWorldName);
            if (worldSpawns.size() <= 1 || !config.spawn.multiNearbySpawn) {
                if (debugEnabled) {
                    logger.info("[Respawn] perWorld=true, spawnCount=" + worldSpawns.size() +
                        (config.spawn.multiNearbySpawn ? "" : ", multiNearbySpawn=false") + " - native provider handles it.");
                    logger.info("[Respawn] ========================================");
                }
                return;
            }
            
            // Multiple spawns - find nearest to DEATH location (captured when component added).
            // Do NOT use current Transform - by respawn time player may already be at primary spawn.
            double deathX, deathZ;
            double[] stored = playerId != null ? deathPositionCache.poll(playerId) : null;
            boolean usedStoredDeathPos = false;
            if (stored != null && stored.length >= 2) {
                deathX = stored[0];
                deathZ = stored[1];
                usedStoredDeathPos = true;
            } else {
                TransformComponent deathTransform = store.getComponent(ref, TransformComponent.getComponentType());
                if (deathTransform != null) {
                    Vector3d p = deathTransform.getPosition();
                    deathX = p.x;
                    deathZ = p.z;
                } else {
                    deathX = 0;
                    deathZ = 0;
                }
            }
            if (debugEnabled) {
                logger.info("[Respawn] *** Fallback: delayed TP (world has GlobalSpawnProvider or provider not used) ***");
                logger.info("[Respawn] Death position: " + String.format("%.1f, %.1f", deathX, deathZ) + " (from " + (usedStoredDeathPos ? "cache" : "current Transform") + ")");
                logger.info("[Respawn] Spawns in world: " + spawnStorage.getSpawnCount(currentWorldName));
            }
            SpawnStorage.SpawnData nearestSpawn = spawnStorage.getNearestSpawn(currentWorldName, deathX, deathZ);
            
            if (nearestSpawn == null) {
                if (debugEnabled) {
                    logger.info("[Respawn] perWorld=true, no spawn found for world '" + currentWorldName + "'.");
                    logger.info("[Respawn] ========================================");
                }
                return;
            }
            
            if (debugEnabled) {
                logger.info("[Respawn] Nearest spawn: '" + nearestSpawn.name + "' (primary=" + nearestSpawn.primary + ") at " +
                    String.format("%.1f, %.1f, %.1f", nearestSpawn.x, nearestSpawn.y, nearestSpawn.z));
            }
            // Check if nearest is the primary - if so, native provider already handles it
            if (nearestSpawn.primary) {
                if (debugEnabled) {
                    logger.info("[Respawn] Nearest is primary - native provider already put player there, nothing to do.");
                    logger.info("[Respawn] ========================================");
                }
                return;
            }
            
            if (debugEnabled) {
                logger.info("[Respawn] Nearest is NOT primary - scheduling 1s delayed TP to '" + nearestSpawn.name + "'");
            }
            
            final Vector3d nearSpawnPos = new Vector3d(nearestSpawn.x, nearestSpawn.y, nearestSpawn.z);
            final Rotation3f nearSpawnRot = new Rotation3f(0, nearestSpawn.yaw, 0);
            final Store<EntityStore> storeFinal2 = store;
            final Ref<EntityStore> refFinal2 = ref;
            final World deathWorldFinal2 = deathWorld;
            final String nearestName = nearestSpawn.name;
            
            CompletableFuture
                    .delayedExecutor(1L, TimeUnit.SECONDS)
                    .execute(() -> {
                        try {
                            if (!deathWorldFinal2.isAlive()) return;
                            deathWorldFinal2.execute(() -> {
                                try {
                                    if (!refFinal2.isValid()) return;
                                    if (playerId != null && !TeleportGuard.get().tryAcquireAutomatic(playerId, "Respawn-NearestSpawn", debugEnabled)) return;
                                    
                                    com.hypixel.hytale.server.core.modules.entity.teleport.Teleport teleport = 
                                        new com.hypixel.hytale.server.core.modules.entity.teleport.Teleport(deathWorldFinal2, nearSpawnPos, nearSpawnRot);
                                    storeFinal2.putComponent(refFinal2, com.hypixel.hytale.server.core.modules.entity.teleport.Teleport.getComponentType(), teleport);
                                    
                                    // Notify the player which spawn they respawned at
                                    if (playerRef != null && nearestName != null) {
                                        var configManager = EliteEssentials.getInstance().getConfigManager();
                                        playerRef.sendMessage(com.eliteessentials.util.MessageFormatter.formatWithFallback(
                                            configManager.getMessage("spawnRespawnedAt", "name", nearestName), "#AAAAAA"));
                                    }
                                    
                                    // Evaluate greeting rules (respawn trigger)
                                    if (greetingService != null && playerRef != null) {
                                        greetingService.evaluate(playerRef, "respawn", currentWorldName, false, nearestName);
                                    }
                                    
                                    if (debugEnabled) {
                                        logger.info("[Respawn] Teleported to nearest spawn '" + nearestName + "'");
                                        logger.info("[Respawn] ========================================");
                                    }
                                } catch (Throwable t) {
                                    logger.warning("[Respawn] Failed nearest-spawn teleport: " + t.getMessage());
                                }
                            });
                        } catch (Throwable t) {
                            logger.warning("[Respawn] Failed to schedule nearest-spawn teleport: " + t.getMessage());
                        }
                    });
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
            logger.info("[Respawn] *** Cross-world respawn ***");
            logger.info("[Respawn] " + currentWorldName + " -> " + targetWorldName + " at " +
                String.format("%.1f, %.1f, %.1f", ourSpawn.x, ourSpawn.y, ourSpawn.z));
            logger.info("[Respawn] Scheduling 1s delayed cross-world TP");
        }

        // Prepare immutable copies for async usage
        final World deathWorldFinal = deathWorld;
        final Vector3d spawnPos = new Vector3d(ourSpawn.x, ourSpawn.y, ourSpawn.z);
        final Rotation3f spawnRot = new Rotation3f(0, ourSpawn.yaw, 0);
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

                                // Evaluate greeting rules (respawn trigger, cross-world)
                                if (greetingService != null && playerRef != null) {
                                    greetingService.evaluate(playerRef, "respawn", targetWorldName, false, null);
                                }

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
        // Build regex by splitting on '*', quoting each literal segment, then joining with '.*'
        String[] parts = pattern.split("\\*", -1);
        StringBuilder sb = new StringBuilder("(?i)");
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append(".*");
            sb.append(java.util.regex.Pattern.quote(parts[i]));
        }
        return worldName.matches(sb.toString());
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
