package com.eliteessentials.systems;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.model.Location;
import com.eliteessentials.services.BackService;
import com.eliteessentials.services.DamageTrackingService;
import com.eliteessentials.util.MessageFormatter;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Holder;
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
import com.hypixel.hytale.server.core.universe.world.World;
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

    public PlayerDeathSystem(BackService backService,
                             ConfigManager configManager,
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
    public void onComponentAdded(Ref<EntityStore> ref,
                                 DeathComponent deathComponent,
                                 Store<EntityStore> store,
                                 CommandBuffer<EntityStore> commandBuffer) {
        try {
            Universe universe = Universe.get();
            if (universe == null) {
                logger.info("[PlayerDeathSystem] Universe is null in onComponentAdded");
                return;
            }

            ComponentType<EntityStore, PlayerRef> playerRefType = universe.getPlayerRefComponentType();
            if (playerRefType == null) {
                logger.info("[PlayerDeathSystem] PlayerRef component type is null");
                return;
            }

            PlayerRef playerRef = store.getComponent(ref, playerRefType);
            if (playerRef == null) {
                logger.info("[PlayerDeathSystem] Ref " + ref + " has no PlayerRef component");
                return;
            }

            UUID playerId = playerRef.getUuid();
            String playerName = playerRef.getUsername();

            // --- Death location via ECS Store (NOT holder) ---
            try {
                // World from store external data
                String worldName = "world";
                try {
                    EntityStore entityStore = store.getExternalData();
                    if (entityStore != null) {
                        World world = entityStore.getWorld();
                        if (world != null) {
                            worldName = world.getName();
                        }
                    }
                } catch (Exception ignored) {
                }

                // Prefer ECS components attached to this ref
                TransformComponent transform =
                        store.getComponent(ref, TransformComponent.getComponentType());
                HeadRotation headRotation =
                        store.getComponent(ref, HeadRotation.getComponentType());

                // Optional: fallback to holder if available, but don't *require* it
                if (transform == null) {
                    try {
                        var holder = playerRef.getHolder();
                        if (holder != null) {
                            transform = holder.getComponent(TransformComponent.getComponentType());
                        }
                    } catch (Exception ignored) {}
                }
                if (headRotation == null) {
                    try {
                        var holder = playerRef.getHolder();
                        if (holder != null) {
                            headRotation = holder.getComponent(HeadRotation.getComponentType());
                        }
                    } catch (Exception ignored) {}
                }

                if (transform != null) {
                    Vector3d pos = transform.getPosition();

                    float yaw = 0f;
                    if (headRotation != null) {
                        Vector3f rotation = headRotation.getRotation();
                        yaw = rotation.y;
                    }

                    // pitch = 0 on purpose; only preserve yaw
                    Location deathLocation = new Location(
                            worldName,
                            pos.getX(),
                            pos.getY(),
                            pos.getZ(),
                            yaw,
                            0f
                    );

                    backService.pushDeathLocation(playerId, deathLocation);

                    if (configManager.isDebugEnabled()) {
                        logger.info("[PlayerDeathSystem] Recorded death location for " + playerName +
                                " (" + playerId + "): " + worldName + " @ " +
                                String.format("%.1f, %.1f, %.1f (yaw=%.1f) [history=%d]",
                                        pos.getX(), pos.getY(), pos.getZ(), yaw,
                                        backService.getHistorySize(playerId)));
                    }
                } else if (configManager.isDebugEnabled()) {
                    logger.info("[PlayerDeathSystem] TransformComponent missing for " +
                            playerName + " (" + playerId + ") at death (store + holder both null).");
                }
            } catch (Exception ex) {
                logger.fine("[PlayerDeathSystem] Failed to resolve death location for " +
                        playerName + " (" + playerId + "): " + ex.getMessage());
            }

            // --- Death messages (unchanged) ---
            if (configManager.getConfig().deathMessages.enabled) {
                String deathMessage = buildDeathMessage(playerName, playerId, deathComponent, universe);
                broadcastDeathMessage(universe, deathMessage);
            }

            if (damageTrackingService != null) {
                damageTrackingService.clearPlayer(playerId);
            }

        } catch (Exception e) {
            logger.warning("[PlayerDeathSystem] Error in onComponentAdded: " + e.getMessage());
        }
    }

    private void broadcastDeathMessage(Universe universe, String message) {
        Message chatMessage = MessageFormatter.formatWithFallback(message, "#FF5555");
        for (PlayerRef player : universe.getPlayers()) {
            try {
                if (player != null && player.isValid()) {
                    player.sendMessage(chatMessage);
                }
            } catch (Exception ignored) {
            }
        }
    }

    @SuppressWarnings("deprecation") // getCause() is deprecated but no alternative available yet
    private String buildDeathMessage(String playerName, UUID playerId,
                                     DeathComponent deathComponent, Universe universe) {
        // Try to get the death message directly from DeathComponent
        try {
            var deathMessage = deathComponent.getDeathMessage();
            if (deathMessage != null) {
                String ansiMessage = deathMessage.getAnsiMessage();
                if (ansiMessage != null && !ansiMessage.isEmpty()) {
                    // Check if the message is malformed/unresolved
                    if (isUnresolvedMessage(ansiMessage)) {
                        // Try to extract NPC name from unresolved message pattern
                        String extractedName = extractNpcNameFromUnresolved(ansiMessage);
                        if (extractedName != null) {
                            return configManager.getMessage("deathByEntity",
                                    "player", playerName, "killer", extractedName);
                        }
                        // Fall through to other methods
                    } else {
                        // "You were killed by Skeleton Fighter!" → "PlayerName was killed by Skeleton Fighter!"
                        String thirdPerson = ansiMessage
                                .replace("You were", playerName + " was")
                                .replace("You ", playerName + " ");
                        return thirdPerson;
                    }
                }
            }
        } catch (Exception ignored) {
        }

        // Try to extract from getDeathInfo()
        try {
            var deathInfo = deathComponent.getDeathInfo();
            if (deathInfo != null) {
                var source = deathInfo.getSource();
                if (source != null) {
                    String sourceClass = source.getClass().getSimpleName();

                    if (sourceClass.contains("Entity")) {
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
        } catch (Exception ignored) {
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
            // Try getEntity()
            try {
                var getEntity = source.getClass().getMethod("getEntity");
                Object entity = getEntity.invoke(source);
                if (entity != null) {
                    return getEntityName(entity, universe);
                }
            } catch (NoSuchMethodException ignored) {
            }

            // Try getAttacker()
            try {
                var getAttacker = source.getClass().getMethod("getAttacker");
                Object attacker = getAttacker.invoke(source);
                if (attacker != null) {
                    return getEntityName(attacker, universe);
                }
            } catch (NoSuchMethodException ignored) {
            }

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
     */
    private String extractNpcNameFromUnresolved(String message) {
        if (message == null) return null;

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
     * "Wolf_Black" -> "Wolf Black", etc.
     */
    private String formatNpcName(String rawName) {
        if (rawName == null || rawName.isEmpty()) return "a creature";

        String[] parts = rawName.split("_");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (result.length() > 0) result.append(" ");
            result.append(capitalize(part));
        }

        return result.toString();
    }

    private String capitalize(String word) {
        if (word == null || word.isEmpty()) return word;
        return word.substring(0, 1).toUpperCase() + word.substring(1).toLowerCase();
    }

    /**
     * Check if a death message is unresolved/malformed.
     */
    private boolean isUnresolvedMessage(String message) {
        if (message == null) return true;

        if (message.contains("@") && message.matches(".*@[0-9a-fA-F]+.*")) {
            return true;
        }

        if (message.contains("server.general.") || message.contains("server.npc")) {
            return true;
        }

        if (message.contains("StringParamValue") ||
                message.contains("ParamValue") ||
                message.contains("FormattedMessage")) {
            return true;
        }

        return false;
    }

    @Override
    public void onComponentSet(Ref<EntityStore> ref,
                               DeathComponent oldComponent,
                               DeathComponent newComponent,
                               Store<EntityStore> store,
                               CommandBuffer<EntityStore> commandBuffer) {
        // No-op
    }

    @Override
    public void onComponentRemoved(Ref<EntityStore> ref,
                                   DeathComponent deathComponent,
                                   Store<EntityStore> store,
                                   CommandBuffer<EntityStore> commandBuffer) {
        // We only record on add; respawn is handled elsewhere.
    }

    private String getEntityName(Object entity, Universe universe) {
        try {
            // Check if it's a PlayerRef
            if (entity instanceof PlayerRef playerRef) {
                return playerRef.getUsername();
            }

            // Try getUuid() and look up player
            try {
                var getUuid = entity.getClass().getMethod("getUuid");
                Object uuid = getUuid.invoke(entity);
                if (uuid instanceof UUID) {
                    PlayerRef player = universe.getPlayer((UUID) uuid);
                    if (player != null && player.isValid()) {
                        return player.getUsername();
                    }
                }
            } catch (NoSuchMethodException ignored) {
            }

            // Try getName()
            try {
                var getName = entity.getClass().getMethod("getName");
                Object name = getName.invoke(entity);
                if (name != null) return name.toString();
            } catch (NoSuchMethodException ignored) {
            }

            // Try getType()
            try {
                var getType = entity.getClass().getMethod("getType");
                Object type = getType.invoke(entity);
                if (type != null) return formatEntityName(type.toString());
            } catch (NoSuchMethodException ignored) {
            }

            // Fall back to class name
            return formatEntityName(entity.getClass().getSimpleName());
        } catch (Exception e) {
            return "something";
        }
    }
}
