package com.eliteessentials.systems;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.model.Location;
import com.eliteessentials.services.BackService;
import com.eliteessentials.services.DamageTrackingService;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefChangeSystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.UUID;
import java.util.logging.Logger;

public class PlayerDeathSystem extends RefChangeSystem<EntityStore, DeathComponent> {

    private static final Logger logger = Logger.getLogger("EliteEssentials");
    
    private final BackService backService;
    private final ConfigManager configManager;
    private final DamageTrackingService damageTrackingService;
    private boolean loggedDeathComponent = false;

    public PlayerDeathSystem(BackService backService, ConfigManager configManager, 
                             DamageTrackingService damageTrackingService) {
        this.backService = backService;
        this.configManager = configManager;
        this.damageTrackingService = damageTrackingService;
    }

    @Override
    public ComponentType<EntityStore, DeathComponent> componentType() {
        return DeathComponent.getComponentType();
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    public void onComponentAdded(Ref<EntityStore> ref, DeathComponent deathComponent,
                                  Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer) {
        try {
            Universe universe = Universe.get();
            if (universe == null) return;
            
            // Debug: Log DeathComponent methods and values (only if debug enabled)
            if (configManager.isDebugEnabled()) {
                logDeathComponentInfo(deathComponent);
            }
            
            ComponentType<EntityStore, PlayerRef> playerRefType = universe.getPlayerRefComponentType();
            if (playerRefType == null) return;
            
            PlayerRef playerRef = store.getComponent(ref, playerRefType);
            if (playerRef == null) return;
            
            UUID playerId = playerRef.getUuid();
            String playerName = playerRef.getUsername();
            
            TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
            String worldName = "world";
            
            if (transform != null) {
                Vector3d pos = transform.getPosition();
                HeadRotation headRotation = store.getComponent(ref, HeadRotation.getComponentType());
                float yaw = 0f, pitch = 0f;
                if (headRotation != null) {
                    Vector3f rotation = headRotation.getRotation();
                    yaw = rotation.getYaw();
                    pitch = rotation.getPitch();
                }
                
                try {
                    EntityStore entityStore = store.getExternalData();
                    if (entityStore != null && entityStore.getWorld() != null) {
                        worldName = entityStore.getWorld().getName();
                    }
                } catch (Exception e) { }
                
                Location deathLocation = new Location(worldName, pos.getX(), pos.getY(), pos.getZ(), yaw, pitch);
                backService.pushDeathLocation(playerId, deathLocation);
            }
            
            if (configManager.getConfig().deathMessages.enabled) {
                String deathMessage = buildDeathMessage(playerName, playerId, deathComponent, universe);
                broadcastDeathMessage(universe, deathMessage);
            }
            
            // Clear damage tracking for this player after death
            if (damageTrackingService != null) {
                damageTrackingService.clearPlayer(playerId);
            }
            
        } catch (Exception e) {
            logger.warning("[PlayerDeathSystem] Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void logDeathComponentInfo(DeathComponent deathComponent) {
        if (loggedDeathComponent) return;
        loggedDeathComponent = true;
        
        try {
            logger.info("[PlayerDeathSystem] === DeathComponent Debug Info ===");
            
            // Log all methods
            StringBuilder sb = new StringBuilder();
            sb.append("[PlayerDeathSystem] DeathComponent methods: ");
            for (var method : deathComponent.getClass().getMethods()) {
                if (method.getParameterCount() == 0 && !method.getName().equals("getClass")) {
                    sb.append(method.getName()).append("(), ");
                }
            }
            logger.info(sb.toString());
            
            // Try to invoke getter methods
            for (var method : deathComponent.getClass().getMethods()) {
                if (method.getParameterCount() == 0 && 
                    (method.getName().startsWith("get") || method.getName().startsWith("is")) &&
                    !method.getName().equals("getClass")) {
                    try {
                        Object result = method.invoke(deathComponent);
                        String resultStr = result != null ? result.toString() : "null";
                        if (resultStr.length() > 200) resultStr = resultStr.substring(0, 200) + "...";
                        logger.info("[PlayerDeathSystem] " + method.getName() + "() = " + resultStr + 
                                   (result != null ? " [" + result.getClass().getName() + "]" : ""));
                        
                        // If result is an object, log its methods too
                        if (result != null && !result.getClass().isPrimitive() && 
                            !result.getClass().getName().startsWith("java.lang")) {
                            logObjectMethods(result, method.getName());
                        }
                    } catch (Exception e) {
                        logger.info("[PlayerDeathSystem] " + method.getName() + "() = ERROR: " + e.getMessage());
                    }
                }
            }
            
            logger.info("[PlayerDeathSystem] === End Debug Info ===");
        } catch (Exception e) {
            logger.warning("[PlayerDeathSystem] Error logging DeathComponent: " + e.getMessage());
        }
    }
    
    private void logObjectMethods(Object obj, String parentName) {
        try {
            for (var method : obj.getClass().getMethods()) {
                if (method.getParameterCount() == 0 && 
                    (method.getName().startsWith("get") || method.getName().startsWith("is")) &&
                    !method.getName().equals("getClass")) {
                    try {
                        Object result = method.invoke(obj);
                        String resultStr = result != null ? result.toString() : "null";
                        if (resultStr.length() > 100) resultStr = resultStr.substring(0, 100) + "...";
                        logger.info("[PlayerDeathSystem]   -> " + parentName + "." + method.getName() + "() = " + resultStr +
                                   (result != null ? " [" + result.getClass().getSimpleName() + "]" : ""));
                    } catch (Exception e) { }
                }
            }
        } catch (Exception e) { }
    }
    
    private void broadcastDeathMessage(Universe universe, String message) {
        Message chatMessage = Message.raw(message).color("#FF5555");
        for (PlayerRef player : universe.getPlayers()) {
            try {
                if (player != null && player.isValid()) {
                    player.sendMessage(chatMessage);
                }
            } catch (Exception e) { }
        }
    }
    
    @SuppressWarnings("deprecation") // getCause() is deprecated but no alternative available yet
    private String buildDeathMessage(String playerName, UUID playerId, DeathComponent deathComponent, Universe universe) {
        // Try to get the death message directly from DeathComponent
        try {
            var deathMessage = deathComponent.getDeathMessage();
            if (deathMessage != null) {
                String ansiMessage = deathMessage.getAnsiMessage();
                if (ansiMessage != null && !ansiMessage.isEmpty()) {
                    // Check if the message contains unresolved StringParamValue objects
                    // These show up as "StringParamValue@" in the raw output
                    if (ansiMessage.contains("StringParamValue@") || ansiMessage.contains("ParamValue@")) {
                        if (configManager.isDebugEnabled()) {
                            logger.info("[PlayerDeathSystem] Skipping unresolved death message: " + ansiMessage);
                        }
                        // Fall through to other methods
                    } else {
                        // The message is like "You were killed by Skeleton Fighter!"
                        // Convert to third person: "PlayerName was killed by Skeleton Fighter!"
                        String thirdPerson = ansiMessage
                            .replace("You were", playerName + " was")
                            .replace("You ", playerName + " ");
                        if (configManager.isDebugEnabled()) {
                            logger.info("[PlayerDeathSystem] Using death message: " + thirdPerson);
                        }
                        return thirdPerson;
                    }
                }
            }
        } catch (Exception e) {
            logger.fine("[PlayerDeathSystem] Could not get death message: " + e.getMessage());
        }
        
        // Try to extract from getDeathInfo()
        try {
            var deathInfo = deathComponent.getDeathInfo();
            if (deathInfo != null) {
                var source = deathInfo.getSource();
                if (source != null) {
                    String sourceClass = source.getClass().getSimpleName();
                    if (configManager.isDebugEnabled()) {
                        logger.info("[PlayerDeathSystem] Death source type: " + sourceClass);
                    }
                    
                    if (sourceClass.contains("Entity")) {
                        // Try to get entity name from source
                        String entityName = getEntityNameFromSource(source, universe);
                        if (entityName != null) {
                            return playerName + " was killed by " + entityName;
                        }
                        return playerName + " was killed";
                    } else if (sourceClass.contains("Projectile")) {
                        return playerName + " was shot";
                    } else if (sourceClass.contains("Environment")) {
                        var cause = deathInfo.getCause();
                        if (cause != null) {
                            return playerName + getCauseMessage(cause.getId());
                        }
                    }
                }
                
                // Fall back to cause
                var cause = deathInfo.getCause();
                if (cause != null) {
                    String causeId = cause.getId();
                    if (configManager.isDebugEnabled()) {
                        logger.info("[PlayerDeathSystem] Death cause: " + causeId);
                    }
                    return playerName + getCauseMessage(causeId);
                }
            }
        } catch (Exception e) {
            logger.fine("[PlayerDeathSystem] Could not get death info: " + e.getMessage());
        }
        
        // Fall back to damage tracking service
        if (damageTrackingService != null) {
            UUID attackerId = damageTrackingService.getLastAttacker(playerId);
            if (attackerId != null) {
                PlayerRef attacker = universe.getPlayer(attackerId);
                if (attacker != null && attacker.isValid()) {
                    return playerName + " was killed by " + attacker.getUsername();
                }
            }
            
            String cause = damageTrackingService.getLastDamageCause(playerId);
            if (cause != null) {
                if (cause.startsWith("ENTITY:")) {
                    String entityType = cause.substring(7);
                    return playerName + " was killed by " + formatEntityName(entityType);
                }
                return playerName + getCauseMessage(cause);
            }
        }
        
        return playerName + " died";
    }
    
    private String getEntityNameFromSource(Object source, Universe universe) {
        try {
            // Try getRef() to get entity reference
            try {
                var getRef = source.getClass().getMethod("getRef");
                Object ref = getRef.invoke(source);
                if (ref != null && configManager.isDebugEnabled()) {
                    logger.info("[PlayerDeathSystem] Got ref from source: " + ref + " [" + ref.getClass().getSimpleName() + "]");
                    // Log ref methods
                    for (var method : ref.getClass().getMethods()) {
                        if (method.getParameterCount() == 0 && 
                            (method.getName().startsWith("get") || method.getName().startsWith("is")) &&
                            !method.getName().equals("getClass")) {
                            try {
                                Object result = method.invoke(ref);
                                logger.info("[PlayerDeathSystem]   ref." + method.getName() + "() = " + result);
                            } catch (Exception e) { }
                        }
                    }
                }
            } catch (NoSuchMethodException e) { }
            
            // Try getEntity()
            try {
                var getEntity = source.getClass().getMethod("getEntity");
                Object entity = getEntity.invoke(source);
                if (entity != null) {
                    return getEntityName(entity, universe);
                }
            } catch (NoSuchMethodException e) { }
            
            // Try getAttacker()
            try {
                var getAttacker = source.getClass().getMethod("getAttacker");
                Object attacker = getAttacker.invoke(source);
                if (attacker != null) {
                    return getEntityName(attacker, universe);
                }
            } catch (NoSuchMethodException e) { }
            
        } catch (Exception e) {
            logger.fine("[PlayerDeathSystem] Error getting entity from source: " + e.getMessage());
        }
        return null;
    }
    
    private String formatEntityName(String entityType) {
        if (entityType == null || entityType.isEmpty()) return "a creature";
        // Convert CamelCase to readable: "ZombieVillager" -> "Zombie Villager"
        String readable = entityType.replaceAll("([a-z])([A-Z])", "$1 $2");
        return readable.toLowerCase();
    }
    
    private String getCauseMessage(String cause) {
        if (cause == null) return " died";
        return switch (cause.toUpperCase()) {
            case "FALL", "FALLING" -> " fell to their death";
            case "FIRE", "FIRE_TICK", "LAVA", "BURNING" -> " burned to death";
            case "DROWNING", "DROWN" -> " drowned";
            case "SUFFOCATION", "SUFFOCATE" -> " suffocated";
            case "VOID", "OUT_OF_WORLD" -> " fell into the void";
            case "STARVATION", "STARVE" -> " starved to death";
            case "PROJECTILE", "ARROW" -> " was shot";
            case "EXPLOSION", "EXPLODE" -> " blew up";
            case "LIGHTNING" -> " was struck by lightning";
            case "FREEZE", "FREEZING" -> " froze to death";
            case "POISON" -> " was poisoned";
            case "WITHER" -> " withered away";
            case "PLAYER" -> " was killed";
            default -> " died";
        };
    }

    @Override
    public void onComponentSet(Ref<EntityStore> ref, DeathComponent oldComponent, DeathComponent newComponent,
                                Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer) { }

    @Override
    public void onComponentRemoved(Ref<EntityStore> ref, DeathComponent deathComponent,
                                    Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer) { }
    
    private String getEntityName(Object entity, Universe universe) {
        try {
            // Check if it's a PlayerRef
            if (entity instanceof PlayerRef playerRef) {
                return playerRef.getUsername();
            }
            
            // Try to get UUID and look up player
            try {
                var getUuid = entity.getClass().getMethod("getUuid");
                Object uuid = getUuid.invoke(entity);
                if (uuid instanceof UUID) {
                    PlayerRef player = universe.getPlayer((UUID) uuid);
                    if (player != null && player.isValid()) {
                        return player.getUsername();
                    }
                }
            } catch (NoSuchMethodException e) { }
            
            // Try getName()
            try {
                var getName = entity.getClass().getMethod("getName");
                Object name = getName.invoke(entity);
                if (name != null) return name.toString();
            } catch (NoSuchMethodException e) { }
            
            // Try getType()
            try {
                var getType = entity.getClass().getMethod("getType");
                Object type = getType.invoke(entity);
                if (type != null) return formatEntityName(type.toString());
            } catch (NoSuchMethodException e) { }
            
            // Fall back to class name
            return formatEntityName(entity.getClass().getSimpleName());
        } catch (Exception e) {
            return "something";
        }
    }
}