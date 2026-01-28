package com.eliteessentials.systems;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.model.Location;
import com.eliteessentials.services.BackService;
import com.eliteessentials.services.DamageTrackingService;
import com.eliteessentials.util.MessageFormatter;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PlayerDeathSystem extends RefChangeSystem<EntityStore, DeathComponent> {

    private static final Logger logger = Logger.getLogger("EliteEssentials");
    
    private final BackService backService;
    private final ConfigManager configManager;
    private final DamageTrackingService damageTrackingService;

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
                float yaw = 0f;
                if (headRotation != null) {
                    Vector3f rotation = headRotation.getRotation();
                    yaw = rotation.y;
                }
                // NOTE: pitch is intentionally set to 0 to avoid player tilt on teleport
                
                try {
                    EntityStore entityStore = store.getExternalData();
                    if (entityStore != null && entityStore.getWorld() != null) {
                        worldName = entityStore.getWorld().getName();
                    }
                } catch (Exception e) { }
                
                // NOTE: pitch is 0 to avoid player tilt on teleport
                Location deathLocation = new Location(worldName, pos.getX(), pos.getY(), pos.getZ(), yaw, 0f);
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
        }
    }
    
    private void broadcastDeathMessage(Universe universe, String message) {
        // Use MessageFormatter to process color codes in death messages
        Message chatMessage = MessageFormatter.formatWithFallback(message, "#FF5555");
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
                    // Check if the message is malformed/unresolved
                    // This can happen due to locale issues or missing translations
                    if (isUnresolvedMessage(ansiMessage)) {
                        // Try to extract NPC name from unresolved message pattern
                        // Pattern: server.npcRoles.Wolf_Black.name or server.npcRoles.Skeleton.name
                        String extractedName = extractNpcNameFromUnresolved(ansiMessage);
                        if (extractedName != null) {
                            return configManager.getMessage("deathByEntity", 
                                "player", playerName, "killer", extractedName);
                        }
                        // Fall through to other methods
                    } else {
                        // The message is like "You were killed by Skeleton Fighter!"
                        // Convert to third person: "PlayerName was killed by Skeleton Fighter!"
                        String thirdPerson = ansiMessage
                            .replace("You were", playerName + " was")
                            .replace("You ", playerName + " ");
                        return thirdPerson;
                    }
                }
            }
        } catch (Exception e) {
            // Fall through to other methods
        }
        
        // Try to extract from getDeathInfo()
        try {
            var deathInfo = deathComponent.getDeathInfo();
            if (deathInfo != null) {
                var source = deathInfo.getSource();
                if (source != null) {
                    String sourceClass = source.getClass().getSimpleName();
                    
                    if (sourceClass.contains("Entity")) {
                        // Try to get entity name from source
                        String entityName = getEntityNameFromSource(source, universe);
                        if (entityName != null) {
                            return configManager.getMessage("deathByEntity",
                                "player", playerName, "killer", entityName);
                        }
                        return configManager.getMessage("deathGeneric", "player", playerName);
                    } else if (sourceClass.contains("Projectile")) {
                        return configManager.getMessage("deathByProjectile", "player", playerName);
                    } else if (sourceClass.contains("Environment")) {
                        var cause = deathInfo.getCause();
                        if (cause != null) {
                            return getDeathMessageByCause(playerName, cause.getId());
                        }
                    }
                }
                
                // Fall back to cause
                var cause = deathInfo.getCause();
                if (cause != null) {
                    return getDeathMessageByCause(playerName, cause.getId());
                }
            }
        } catch (Exception e) {
            // Fall through to other methods
        }
        
        // Fall back to damage tracking service
        if (damageTrackingService != null) {
            UUID attackerId = damageTrackingService.getLastAttacker(playerId);
            if (attackerId != null) {
                PlayerRef attacker = universe.getPlayer(attackerId);
                if (attacker != null && attacker.isValid()) {
                    return configManager.getMessage("deathByPlayer",
                        "player", playerName, "killer", attacker.getUsername());
                }
            }
            
            String cause = damageTrackingService.getLastDamageCause(playerId);
            if (cause != null) {
                if (cause.startsWith("ENTITY:")) {
                    String entityType = cause.substring(7);
                    return configManager.getMessage("deathByEntity",
                        "player", playerName, "killer", formatEntityName(entityType));
                }
                return getDeathMessageByCause(playerName, cause);
            }
        }
        
        return configManager.getMessage("deathGeneric", "player", playerName);
    }
    
    /**
     * Get the appropriate death message based on cause, using configurable messages.
     */
    private String getDeathMessageByCause(String playerName, String cause) {
        if (cause == null) {
            return configManager.getMessage("deathGeneric", "player", playerName);
        }
        
        String messageKey = switch (cause.toUpperCase()) {
            case "FALL", "FALLING" -> "deathByFall";
            case "FIRE", "FIRE_TICK", "BURNING" -> "deathByFire";
            case "LAVA" -> "deathByLava";
            case "DROWNING", "DROWN" -> "deathByDrowning";
            case "SUFFOCATION", "SUFFOCATE" -> "deathBySuffocation";
            case "VOID", "OUT_OF_WORLD" -> "deathByVoid";
            case "STARVATION", "STARVE" -> "deathByStarvation";
            case "PROJECTILE", "ARROW" -> "deathByProjectile";
            case "EXPLOSION", "EXPLODE" -> "deathByExplosion";
            case "LIGHTNING" -> "deathByLightning";
            case "FREEZE", "FREEZING" -> "deathByFreeze";
            case "POISON" -> "deathByPoison";
            case "WITHER" -> "deathByWither";
            default -> "deathGeneric";
        };
        
        return configManager.getMessage(messageKey, "player", playerName);
    }
    
    private String getEntityNameFromSource(Object source, Universe universe) {
        try {
            // Try getRef() to get entity reference
            try {
                source.getClass().getMethod("getRef");
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
    
    /**
     * Extract NPC/entity name from unresolved death message.
     * Handles patterns like: server.npcRoles.Wolf_Black.name or server.npcRoles.Skeleton.name
     * 
     * @param message The unresolved message string
     * @return Formatted entity name (e.g., "Black Wolf", "Skeleton") or null if not found
     */
    private String extractNpcNameFromUnresolved(String message) {
        if (message == null) return null;
        
        // Pattern to match: server.npcRoles.ENTITY_NAME.name
        // Examples: server.npcRoles.Wolf_Black.name, server.npcRoles.Skeleton.name
        Pattern pattern = Pattern.compile("server\\.npcRoles\\.([^.]+)\\.name");
        Matcher matcher = pattern.matcher(message);
        
        if (matcher.find()) {
            String rawName = matcher.group(1);
            return formatNpcName(rawName);
        }
        
        return null;
    }
    
    /**
     * Format NPC name from internal format to readable format.
     * Simply replaces underscores with spaces - keeps original order.
     * Examples:
     * - "Wolf_Black" -> "Wolf Black"
     * - "Skeleton" -> "Skeleton"
     * - "Bear_Grizzly" -> "Bear Grizzly"
     * - "Feran_Longtooth" -> "Feran Longtooth"
     */
    private String formatNpcName(String rawName) {
        if (rawName == null || rawName.isEmpty()) return "a creature";
        
        // Simply replace underscores with spaces and capitalize each word
        String[] parts = rawName.split("_");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (result.length() > 0) result.append(" ");
            result.append(capitalize(part));
        }
        
        return result.toString();
    }
    
    /**
     * Capitalize first letter of a word.
     */
    private String capitalize(String word) {
        if (word == null || word.isEmpty()) return word;
        return word.substring(0, 1).toUpperCase() + word.substring(1).toLowerCase();
    }
    
    /**
     * Check if a death message is unresolved/malformed.
     * This can happen due to locale issues, missing translations, or serialization problems.
     * 
     * Detects patterns like:
     * - "StringParamValue@abc123" (unresolved parameter)
     * - "FormattedMessage@abc123" (raw object toString)
     * - "server.general.killedBy" (raw localization key)
     * - Object hash patterns like "@a1b2c3d4"
     */
    private boolean isUnresolvedMessage(String message) {
        if (message == null) return true;
        
        // Check for raw object toString patterns (ClassName@hexhash)
        if (message.contains("@") && message.matches(".*@[0-9a-fA-F]+.*")) {
            return true;
        }
        
        // Check for unresolved localization keys
        if (message.contains("server.general.") || message.contains("server.npc")) {
            return true;
        }
        
        // Check for specific known bad patterns
        if (message.contains("StringParamValue") || 
            message.contains("ParamValue") || 
            message.contains("FormattedMessage")) {
            return true;
        }
        
        return false;
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