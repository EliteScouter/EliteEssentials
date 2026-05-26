package com.eliteessentials.commands.hytale;

import com.eliteessentials.EliteEssentials;
import com.eliteessentials.commands.args.SimpleStringArg;
import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.config.PluginConfig;
import com.eliteessentials.model.Location;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.services.BackService;
import com.eliteessentials.services.CooldownService;
import com.eliteessentials.services.WarmupService;
import com.eliteessentials.storage.SpawnStorage;
import com.eliteessentials.util.CommandPermissionUtil;
import com.eliteessentials.util.MessageFormatter;
import com.eliteessentials.util.TeleportUtil;
import com.eliteessentials.util.WorldBlacklistUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import org.joml.Vector3d;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.UUID;

import javax.annotation.Nonnull;
import com.eliteessentials.util.CommandSpyUtil;

/**
 * Command: /spawn [name]
 * Teleports the player to a spawn point.
 * 
 * When perWorld=false: always teleports to mainWorld's primary spawn (no multi-spawn).
 * When perWorld=true:
 *   - /spawn      → nearest spawn in current world
 *   - /spawn name → specific named spawn in current world
 * 
 * Permissions:
 * - eliteessentials.command.spawn.use - Use /spawn command
 * - eliteessentials.bypass.warmup.spawn - Skip warmup
 * - eliteessentials.bypass.cooldown.spawn - Skip cooldown
 */
public class HytaleSpawnCommand extends AbstractPlayerCommand {

    private static final String COMMAND_NAME = "spawn";
    private final BackService backService;

    public HytaleSpawnCommand(BackService backService) {
        super(COMMAND_NAME, "Teleport to spawn");
        this.backService = backService;
        
        addUsageVariant(new SpawnWithNameCommand(backService));
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, 
                          @Nonnull PlayerRef player, @Nonnull World world) {
        CommandSpyUtil.notify(ctx);
        doSpawn(ctx, store, ref, player, world, null, backService);
    }
    
    /**
     * Core spawn teleport logic. Used by both /spawn and /spawn <name>.
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
    
    /**
     * Variant: /spawn <name>
     */
    private static class SpawnWithNameCommand extends AbstractPlayerCommand {
        private final BackService backService;
        private final RequiredArg<String> nameArg;
        
        SpawnWithNameCommand(BackService backService) {
            super(COMMAND_NAME);
            this.backService = backService;
            this.nameArg = withRequiredArg("name", "Spawn point name", SimpleStringArg.SPAWN_NAME);
        }
        
        @Override
        protected boolean canGeneratePermission() {
            return false;
        }
        
        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                              @Nonnull PlayerRef player, @Nonnull World world) {
        CommandSpyUtil.notify(ctx);
            String name = ctx.get(nameArg);
            HytaleSpawnCommand.doSpawn(ctx, store, ref, player, world, name, backService);
        }
    }
}
