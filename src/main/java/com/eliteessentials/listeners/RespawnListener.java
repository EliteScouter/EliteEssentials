package com.eliteessentials.listeners;

import com.eliteessentials.EliteEssentials;
import com.eliteessentials.storage.SpawnStorage;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefChangeSystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.jetbrains.annotations.NotNull;

import java.util.logging.Logger;

/**
 * Handles player respawns after death.
 * 
 * If player has a bed spawn set, they respawn at their bed (vanilla behavior).
 * If player has no bed spawn, they respawn at the /setspawn location.
 * 
 * This system listens for DeathComponent removal, which happens when a player respawns.
 * 
 * NOTE: This class uses reflection to call Player.getRespawnPosition() because:
 * 1. The method requires a ComponentAccessor parameter that isn't easily accessible
 * 2. The Hytale API doesn't expose a simpler way to check bed spawn status
 * 3. Direct API access would require internal class dependencies
 * If the Hytale API provides a simpler method in the future, this should be refactored.
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
    public void onComponentAdded(@NotNull Ref<EntityStore> ref, @NotNull DeathComponent component,
                                 @NotNull Store<EntityStore> store, @NotNull CommandBuffer<EntityStore> buffer) {
        // Do nothing when player dies
    }
    
    @Override
    public void onComponentSet(@NotNull Ref<EntityStore> ref, DeathComponent oldComponent, 
                               @NotNull DeathComponent newComponent,
                               @NotNull Store<EntityStore> store, @NotNull CommandBuffer<EntityStore> buffer) {
        // Do nothing on component update
    }
    
    @Override
    public void onComponentRemoved(@NotNull Ref<EntityStore> ref, @NotNull DeathComponent component,
                                   @NotNull Store<EntityStore> store, @NotNull CommandBuffer<EntityStore> buffer) {
        
        boolean debugEnabled = EliteEssentials.getInstance().getConfigManager().isDebugEnabled();
        
        if (debugEnabled) {
            logger.info("[Respawn] ========== PLAYER RESPAWNING ==========");
            logger.info("[Respawn] Ref: " + ref);
        }
        
        // Get world info early - needed for both bed check and spawn teleport
        EntityStore entityStore = store.getExternalData();
        World world = entityStore.getWorld();
        
        // Get our spawn location for comparison
        SpawnStorage.SpawnData ourSpawn = spawnStorage.getSpawn();
        
        // Check if player has a bed spawn set using Player.getRespawnPosition()
        try {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player != null) {
                try {
                    // Use reflection to call getRespawnPosition with proper args
                    var method = player.getClass().getMethod("getRespawnPosition", 
                        Ref.class, String.class, 
                        Class.forName("com.hypixel.hytale.component.ComponentAccessor"));
                    
                    Object respawnPos = method.invoke(player, ref, world.getName(), store);
                    
                    if (debugEnabled) {
                        logger.info("[Respawn] DEBUG: getRespawnPosition() = " + respawnPos);
                        if (ourSpawn != null) {
                            logger.info("[Respawn] DEBUG: Our /setspawn = " + 
                                String.format("%.1f, %.1f, %.1f", ourSpawn.x, ourSpawn.y, ourSpawn.z));
                        }
                    }
                    
                    // If respawnPos is not null, check if it matches our spawn
                    // If it matches our spawn, that means no bed is set (vanilla is using our spawn)
                    // If it doesn't match, player has a bed spawn set
                    if (respawnPos != null && ourSpawn != null) {
                        // Extract position from Transform using reflection
                        var getPosMethod = respawnPos.getClass().getMethod("getPosition");
                        Object posObj = getPosMethod.invoke(respawnPos);
                        
                        // Get x, y, z from Vector3d
                        double respawnX = (double) posObj.getClass().getField("x").get(posObj);
                        double respawnY = (double) posObj.getClass().getField("y").get(posObj);
                        double respawnZ = (double) posObj.getClass().getField("z").get(posObj);
                        
                        if (debugEnabled) {
                            logger.info("[Respawn] DEBUG: Respawn position: " + 
                                String.format("%.1f, %.1f, %.1f", respawnX, respawnY, respawnZ));
                        }
                        
                        // Check if respawn position matches our spawn (within 1 block tolerance)
                        boolean matchesOurSpawn = 
                            Math.abs(respawnX - ourSpawn.x) < 1.0 &&
                            Math.abs(respawnY - ourSpawn.y) < 1.0 &&
                            Math.abs(respawnZ - ourSpawn.z) < 1.0;
                        
                        if (!matchesOurSpawn) {
                            // Player has a bed spawn that's different from our spawn
                            if (debugEnabled) {
                                logger.info("[Respawn] Player has bed spawn (different from /setspawn), using vanilla respawn");
                                logger.info("[Respawn] ========================================");
                            }
                            return;
                        } else {
                            if (debugEnabled) {
                                logger.info("[Respawn] Respawn position matches /setspawn - no bed set");
                            }
                        }
                    } else if (respawnPos != null && ourSpawn == null) {
                        // We have no spawn set, but player has a respawn position (must be bed)
                        if (debugEnabled) {
                            logger.info("[Respawn] Player has respawn position and no /setspawn set, using vanilla respawn");
                            logger.info("[Respawn] ========================================");
                        }
                        return;
                    }
                } catch (Exception e) {
                    if (debugEnabled) {
                        logger.info("[Respawn] DEBUG: Could not call getRespawnPosition: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            }
        } catch (Exception e) {
            if (debugEnabled) {
                logger.info("[Respawn] DEBUG: Error checking respawn position: " + e.getMessage());
            }
        }
        
        // Player has no bed spawn - teleport to /setspawn location
        if (ourSpawn == null) {
            if (debugEnabled) {
                logger.info("[Respawn] No spawn set, using vanilla respawn");
                logger.info("[Respawn] ========================================");
            }
            return;
        }
        
        // Check if spawn is for the current world
        if (!world.getName().equals(ourSpawn.world)) {
            if (debugEnabled) {
                logger.info("[Respawn] Spawn is for different world (" + ourSpawn.world + " vs " + world.getName() + "), using vanilla respawn");
                logger.info("[Respawn] ========================================");
            }
            return;
        }
        
        // Teleport player to spawn using CommandBuffer (safe for system context)
        Vector3d spawnPos = new Vector3d(ourSpawn.x, ourSpawn.y, ourSpawn.z);
        Vector3f spawnRot = new Vector3f(ourSpawn.pitch, ourSpawn.yaw, 0); // pitch, yaw, roll
        
        Teleport teleport = new Teleport(spawnPos, spawnRot);
        buffer.putComponent(ref, Teleport.getComponentType(), teleport);
        
        if (debugEnabled) {
            logger.info("[Respawn] No bed spawn - teleporting to /setspawn at " + 
                String.format("%.1f, %.1f, %.1f", ourSpawn.x, ourSpawn.y, ourSpawn.z));
            logger.info("[Respawn] ========================================");
        }
    }
}
