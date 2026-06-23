package com.eliteessentials.commands.hytale;

import com.eliteessentials.EliteEssentials;
import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.config.PluginConfig;
import com.eliteessentials.model.Location;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.services.BackService;
import com.eliteessentials.services.CooldownService;
import com.eliteessentials.services.WarmupService;
import com.eliteessentials.storage.SpawnStorage;
import com.eliteessentials.util.CommandPermissionUtil;
import com.eliteessentials.util.CommandSpyUtil;
import com.eliteessentials.util.MessageFormatter;
import com.eliteessentials.util.PlayerSuggestionProvider;
import com.eliteessentials.util.TeleportUtil;
import com.eliteessentials.util.WorldBlacklistUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import org.joml.Vector3d;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.UUID;

import javax.annotation.Nonnull;

/**
 * Command: /spawn [name]  (player)  |  /spawn <player>  (console)
 * Teleports a player to a spawn point.
 *
 * Player sender:
 *   - /spawn      -> auto-select spawn (nearest when perWorld=true, primary otherwise)
 *   - /spawn name -> specific named spawn in current world (when perWorld=true)
 *
 * Console sender (for server automation):
 *   - /spawn <player> -> send the named online player to spawn (bypasses warmup/cooldown)
 *
 * This command extends {@link CommandBase} (not AbstractPlayerCommand) so the
 * server console can run it with a player argument, matching the /rtp pattern.
 *
 * Permissions:
 * - eliteessentials.command.spawn.use - Use /spawn command
 * - eliteessentials.bypass.warmup.spawn - Skip warmup
 * - eliteessentials.bypass.cooldown.spawn - Skip cooldown
 */
public class HytaleSpawnCommand extends CommandBase {

    private static final String COMMAND_NAME = "spawn";
    private final BackService backService;

    public HytaleSpawnCommand(BackService backService) {
        super(COMMAND_NAME, "Teleport to spawn");
        this.backService = backService;

        // Allow a trailing argument: spawn point name (player) or player name (console)
        setAllowsExtraArguments(true);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void executeSync(@Nonnull CommandContext ctx) {
        CommandSpyUtil.notify(ctx);

        // Parse: /spawn [arg]
        String[] parts = ctx.getInputString().split("\\s+");
        String arg1 = parts.length >= 2 ? parts[1] : null;

        Object sender = ctx.sender();
        boolean isConsole = !(sender instanceof PlayerRef) && !(sender instanceof Player);

        if (isConsole) {
            // Console usage: /spawn <player> [world] [spawnName]
            if (arg1 == null) {
                ctx.sendMessage(Message.raw("Console usage: /spawn <player> [world] [spawnName]").color("#FF5555"));
                return;
            }
            PlayerRef target = PlayerSuggestionProvider.findPlayer(arg1);
            if (target == null) {
                ConfigManager configManager = EliteEssentials.getInstance().getConfigManager();
                ctx.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("playerNotFound", "player", arg1), "#FF5555"));
                return;
            }
            String worldArg = parts.length >= 3 ? parts[2] : null;
            String spawnNameArg = parts.length >= 4 ? parts[3] : null;
            adminTeleportToSpawn(ctx, target, backService, worldArg, spawnNameArg);
            return;
        }

        // Player sender - resolve self ref and run the normal /spawn flow on the world thread.
        PlayerRef senderRef = resolveSenderPlayer(sender);
        if (senderRef == null) {
            ctx.sendMessage(Message.raw("Could not determine player.").color("#FF5555"));
            return;
        }

        World world = Universe.get().getWorld(senderRef.getWorldUuid());
        if (world == null) {
            ctx.sendMessage(Message.raw("Could not determine your world.").color("#FF5555"));
            return;
        }

        final String spawnName = arg1; // may be null -> auto-select
        final PlayerRef finalSender = senderRef;
        final World finalWorld = world;
        world.execute(() -> {
            Ref<EntityStore> ref = finalSender.getReference();
            if (ref == null || !ref.isValid()) {
                ctx.sendMessage(Message.raw("Could not determine your position.").color("#FF5555"));
                return;
            }
            Store<EntityStore> store = ref.getStore();
            if (store == null) {
                ctx.sendMessage(Message.raw("Could not determine your position.").color("#FF5555"));
                return;
            }
            doSpawn(ctx, store, ref, finalSender, finalWorld, spawnName, backService);
        });
    }

    /**
     * Resolve the sender to a live PlayerRef (handles both PlayerRef and Player sender types).
     */
    @SuppressWarnings("removal") // Entity.getUuid() deprecated; no alternative in CommandContext for executeSync
    private static PlayerRef resolveSenderPlayer(Object sender) {
        if (sender instanceof PlayerRef playerRef) {
            return playerRef;
        }
        if (sender instanceof Player player) {
            UUID uuid = player.getUuid();
            return uuid != null ? Universe.get().getPlayer(uuid) : null;
        }
        return null;
    }

    /**
     * Console/admin action: send another online player to spawn.
     * Bypasses warmup, cooldown, cost and permission checks (intended for server automation).
     *
     * @param worldArg     optional world name; when null, uses the target's current world
     *                     (or the configured main world when perWorld is disabled)
     * @param spawnNameArg optional named spawn point within the resolved world; when null,
     *                     uses that world's primary ("main") spawn
     */
    private static void adminTeleportToSpawn(CommandContext ctx, PlayerRef target, BackService backService,
                                             String worldArg, String spawnNameArg) {
        EliteEssentials plugin = EliteEssentials.getInstance();
        ConfigManager configManager = plugin.getConfigManager();
        PluginConfig config = configManager.getConfig();
        SpawnStorage spawnStorage = plugin.getSpawnStorage();

        if (!config.spawn.enabled) {
            ctx.sendMessage(Message.raw("Spawn system is disabled.").color("#FF5555"));
            return;
        }

        World currentWorld = Universe.get().getWorld(target.getWorldUuid());
        if (currentWorld == null) {
            ctx.sendMessage(Message.raw("Could not determine the player's world.").color("#FF5555"));
            return;
        }

        // Resolve which world's spawn to use
        final String targetWorldName;
        if (worldArg != null) {
            String canonical = resolveSpawnWorldName(spawnStorage, worldArg);
            if (canonical == null) {
                ctx.sendMessage(Message.raw("No spawns are set for world '" + worldArg + "'.").color("#FF5555"));
                return;
            }
            targetWorldName = canonical;
        } else {
            targetWorldName = config.spawn.perWorld ? currentWorld.getName() : config.spawn.mainWorld;
        }

        // Resolve the spawn point: named when provided, otherwise the primary ("main") spawn
        SpawnStorage.SpawnData spawn;
        if (spawnNameArg != null) {
            spawn = spawnStorage.getSpawnByName(targetWorldName, spawnNameArg);
            if (spawn == null) {
                ctx.sendMessage(Message.raw(
                    "Spawn point '" + spawnNameArg + "' not found in world '" + targetWorldName + "'.").color("#FF5555"));
                return;
            }
        } else {
            spawn = spawnStorage.getPrimarySpawn(targetWorldName);
            if (spawn == null) {
                ctx.sendMessage(Message.raw("No spawn set for world: " + targetWorldName).color("#FF5555"));
                return;
            }
        }

        World resolvedTargetWorld = findLoadedWorld(targetWorldName);
        if (resolvedTargetWorld == null) {
            ctx.sendMessage(Message.raw("World '" + targetWorldName + "' is not loaded.").color("#FF5555"));
            return;
        }

        final World finalTargetWorld = resolvedTargetWorld;
        final World finalCurrentWorld = currentWorld;
        final SpawnStorage.SpawnData finalSpawn = spawn;

        finalCurrentWorld.execute(() -> {
            Ref<EntityStore> ref = target.getReference();
            if (ref == null || !ref.isValid()) {
                ctx.sendMessage(Message.raw("Could not determine the player's position.").color("#FF5555"));
                return;
            }
            Store<EntityStore> store = ref.getStore();
            if (store == null) {
                ctx.sendMessage(Message.raw("Could not determine the player's position.").color("#FF5555"));
                return;
            }
            TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
            if (transform == null) {
                ctx.sendMessage(Message.raw("Could not determine the player's position.").color("#FF5555"));
                return;
            }
            Vector3d currentPos = transform.getPosition();

            // Save /back location for the target
            HeadRotation headRotation = store.getComponent(ref, HeadRotation.getComponentType());
            Rotation3f rotation = headRotation != null ? headRotation.getRotation() : new Rotation3f(0, 0, 0);
            backService.pushLocation(target.getUuid(), new Location(
                finalCurrentWorld.getName(),
                currentPos.x, currentPos.y, currentPos.z,
                rotation.y, 0f));

            Vector3d spawnPos = new Vector3d(finalSpawn.x, finalSpawn.y, finalSpawn.z);
            Rotation3f spawnRot = new Rotation3f(0, finalSpawn.yaw, 0);

            TeleportUtil.safeTeleport(finalCurrentWorld, finalTargetWorld, spawnPos, spawnRot, target,
                () -> {
                    String spawnLabel = finalSpawn.name != null ? finalSpawn.name : "spawn";
                    target.sendMessage(MessageFormatter.formatWithFallback(
                        configManager.getMessage("spawnTeleported"), "#55FF55"));
                    ctx.sendMessage(Message.raw("Teleported " + target.getUsername() + " to spawn '"
                        + spawnLabel + "' in world '" + finalTargetWorld.getName() + "'.").color("#55FF55"));
                },
                () -> ctx.sendMessage(Message.raw(
                    "Teleport failed - destination chunk could not be loaded.").color("#FF5555"))
            );
        });
    }

    /**
     * Find the canonical world name (as stored in spawn data) matching the input, case-insensitively.
     * Returns null if no world has any spawns set under a matching name.
     */
    private static String resolveSpawnWorldName(SpawnStorage spawnStorage, String input) {
        for (String worldName : spawnStorage.getWorldsWithSpawn()) {
            if (worldName.equalsIgnoreCase(input)) {
                return worldName;
            }
        }
        return null;
    }

    /**
     * Resolve a loaded world by name, case-insensitively.
     */
    private static World findLoadedWorld(String name) {
        for (var entry : Universe.get().getWorlds().entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return Universe.get().getWorld(name);
    }

    /**
     * Core spawn teleport logic for a self /spawn. Used by the player path above.
     *
     * @param spawnName null = auto-select (nearest when perWorld=true, primary when perWorld=false)
     */
    static void doSpawn(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref,
                        PlayerRef player, World world, String spawnName, BackService backService) {
        UUID playerId = player.getUuid();
        ConfigManager configManager = EliteEssentials.getInstance().getConfigManager();
        PluginConfig config = configManager.getConfig();
        CooldownService cooldownService = EliteEssentials.getInstance().getCooldownService();
        WarmupService warmupService = EliteEssentials.getInstance().getWarmupService();
        SpawnStorage spawnStorage = EliteEssentials.getInstance().getSpawnStorage();
        
        if (WorldBlacklistUtil.isWorldBlacklisted(world.getName(), config.spawn.blacklistedWorlds)) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("commandBlacklistedWorld"), "#FF5555"));
            return;
        }
        
        if (!CommandPermissionUtil.canExecuteWithCost(ctx, player, Permissions.SPAWN, 
                config.spawn.enabled, "spawn", config.spawn.cost)) {
            return;
        }
        
        int effectiveCooldown = CommandPermissionUtil.getEffectiveTpCooldown(playerId, COMMAND_NAME, config.spawn.cooldownSeconds);
        
        if (effectiveCooldown > 0) {
            int cooldownRemaining = cooldownService.getCooldownRemaining(COMMAND_NAME, playerId);
            if (cooldownRemaining > 0) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("onCooldown", "seconds", String.valueOf(cooldownRemaining)), "#FF5555"));
                return;
            }
        }
        
        // Determine target world and spawn
        String targetWorldName = config.spawn.perWorld ? world.getName() : config.spawn.mainWorld;
        
        // Resolve spawn point
        SpawnStorage.SpawnData spawn;
        
        if (spawnName != null && config.spawn.perWorld) {
            // Specific named spawn requested
            spawn = spawnStorage.getSpawnByName(targetWorldName, spawnName);
            if (spawn == null) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("spawnDeleteNotFound", "name", spawnName), "#FF5555"));
                return;
            }
        } else if (config.spawn.perWorld) {
            // perWorld=true, no name → random / nearest / primary (random takes precedence if both enabled)
            if (config.spawn.multiRandomSpawn) {
                spawn = spawnStorage.getRandomSpawn(targetWorldName);
            } else if (config.spawn.multiNearbySpawn) {
                TransformComponent preCheck = store.getComponent(ref, TransformComponent.getComponentType());
                if (preCheck == null) {
                    ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("couldNotGetPosition"), "#FF5555"));
                    return;
                }
                Vector3d prePos = preCheck.getPosition();
                spawn = spawnStorage.getNearestSpawn(targetWorldName, prePos.x, prePos.z);
            } else {
                spawn = spawnStorage.getPrimarySpawn(targetWorldName);
            }
            if (spawn == null) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("spawnNoSpawn") + " (No spawn set for world: " + targetWorldName + ")", "#FF5555"));
                return;
            }
        } else {
            // perWorld=false → primary spawn of mainWorld
            spawn = spawnStorage.getPrimarySpawn(targetWorldName);
            if (spawn == null) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("spawnNoSpawn") + " (No spawn set for main world: " + targetWorldName + ")", "#FF5555"));
                return;
            }
        }
        
        // Get current position for /back and warmup
        TransformComponent currentTransform = store.getComponent(ref, TransformComponent.getComponentType());
        if (currentTransform == null) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("couldNotGetPosition"), "#FF5555"));
            return;
        }
        
        Vector3d currentPos = currentTransform.getPosition();
        HeadRotation headRotation = store.getComponent(ref, HeadRotation.getComponentType());
        Rotation3f currentRot = headRotation != null ? headRotation.getRotation() : new Rotation3f(0, 0, 0);
        
        Location currentLoc = new Location(
            world.getName(),
            currentPos.x, currentPos.y, currentPos.z,
            currentRot.y, 0f
        );
        
        World targetWorld = Universe.get().getWorld(targetWorldName);
        if (targetWorld == null) {
            targetWorld = world;
        }
        final World finalTargetWorld = targetWorld;
        
        Vector3d spawnPos = new Vector3d(spawn.x, spawn.y, spawn.z);
        Rotation3f spawnRot = new Rotation3f(0, spawn.yaw, 0);
        
        final int finalEffectiveCooldown = effectiveCooldown;
        final SpawnStorage.SpawnData finalSpawn = spawn;
        
        Runnable doTeleport = () -> {
            backService.pushLocation(playerId, currentLoc);

            TeleportUtil.safeTeleport(world, finalTargetWorld, spawnPos, spawnRot, player,
                () -> {
                    CommandPermissionUtil.chargeCost(ctx, player, "spawn", config.spawn.cost);
                    // Show named message if multi-spawn, basic message if single
                    boolean isMultiSpawn = config.spawn.perWorld && (config.spawn.multiNearbySpawn || config.spawn.multiRandomSpawn) && spawnStorage.getSpawnCount(targetWorldName) > 1;
                    if (isMultiSpawn && finalSpawn.name != null) {
                        player.sendMessage(MessageFormatter.formatWithFallback(
                            configManager.getMessage("spawnTeleportedNamed", "name", finalSpawn.name), "#55FF55"));
                    } else {
                        player.sendMessage(MessageFormatter.formatWithFallback(
                            configManager.getMessage("spawnTeleported"), "#55FF55"));
                    }
                },
                () -> {
                    player.sendMessage(MessageFormatter.formatWithFallback("&cTeleport failed - destination chunk could not be loaded.", "#FF5555"));
                }
            );
            
            if (finalEffectiveCooldown > 0) {
                cooldownService.setCooldown(COMMAND_NAME, playerId, finalEffectiveCooldown);
            }
        };
        
        int warmupSeconds = CommandPermissionUtil.getEffectiveWarmup(playerId, COMMAND_NAME, config.spawn.warmupSeconds);
        
        if (warmupSeconds > 0) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("spawnWarmup", "seconds", String.valueOf(warmupSeconds)), "#FFAA00"));
        }
        warmupService.startWarmup(player, currentPos, warmupSeconds, doTeleport, COMMAND_NAME, world, store, ref);
    }
}
