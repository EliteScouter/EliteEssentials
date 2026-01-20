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
import com.eliteessentials.util.MessageFormatter;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.UUID;

import javax.annotation.Nonnull;

/**
 * Command: /spawn
 * Teleports the player to the server spawn point (set via /setspawn).
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
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, 
                          @Nonnull PlayerRef player, @Nonnull World world) {
        UUID playerId = player.getUuid();
        ConfigManager configManager = EliteEssentials.getInstance().getConfigManager();
        PluginConfig config = configManager.getConfig();
        CooldownService cooldownService = EliteEssentials.getInstance().getCooldownService();
        WarmupService warmupService = EliteEssentials.getInstance().getWarmupService();
        SpawnStorage spawnStorage = EliteEssentials.getInstance().getSpawnStorage();
        
        // Check permission and enabled state
        if (!CommandPermissionUtil.canExecute(ctx, player, Permissions.SPAWN, config.spawn.enabled)) {
            return;
        }
        
        // Check cooldown (with bypass check)
        if (!CommandPermissionUtil.canBypassCooldown(playerId, COMMAND_NAME)) {
            int cooldownRemaining = cooldownService.getCooldownRemaining(COMMAND_NAME, playerId);
            if (cooldownRemaining > 0) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("onCooldown", "seconds", String.valueOf(cooldownRemaining)), "#FF5555"));
                return;
            }
        }
        
        // Get spawn from storage
        SpawnStorage.SpawnData spawn = spawnStorage.getSpawn();
        if (spawn == null) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("spawnNoSpawn"), "#FF5555"));
            return;
        }
        
        // Get target world
        World targetWorld = Universe.get().getWorld(spawn.world);
        if (targetWorld == null) {
            ctx.sendMessage(Message.raw("Spawn world not found: " + spawn.world).color("#FF5555"));
            return;
        }
        
        // Get current position for /back and warmup
        TransformComponent currentTransform = (TransformComponent) store.getComponent(ref, TransformComponent.getComponentType());
        if (currentTransform == null) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("couldNotGetPosition"), "#FF5555"));
            return;
        }
        
        Vector3d currentPos = currentTransform.getPosition();
        HeadRotation headRotation = (HeadRotation) store.getComponent(ref, HeadRotation.getComponentType());
        Vector3f currentRot = headRotation != null ? headRotation.getRotation() : new Vector3f(0, 0, 0);
        
        Location currentLoc = new Location(
            world.getName(),
            currentPos.getX(), currentPos.getY(), currentPos.getZ(),
            currentRot.x, currentRot.y
        );
        
        // Spawn position and rotation
        // Vector3f structure: x = pitch, y = yaw, z = roll
        // Always use pitch=0 to keep player upright, preserve yaw for direction
        Vector3d spawnPos = new Vector3d(spawn.x, spawn.y, spawn.z);
        Vector3f spawnRot = new Vector3f(0, spawn.yaw, 0);
        
        // Define the actual teleport action
        Runnable doTeleport = () -> {
            // Save location for /back
            backService.pushLocation(playerId, currentLoc);

            // Teleport player to spawn
            targetWorld.execute(() -> {
                Teleport teleport = new Teleport(targetWorld, spawnPos, spawnRot);
                store.putComponent(ref, Teleport.getComponentType(), teleport);
                
                ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("spawnTeleported"), "#55FF55"));
            });
            
            // Set cooldown
            cooldownService.setCooldown(COMMAND_NAME, playerId, config.spawn.cooldownSeconds);
        };
        
        // Get effective warmup (check bypass permission)
        int warmupSeconds = CommandPermissionUtil.getEffectiveWarmup(playerId, COMMAND_NAME, config.spawn.warmupSeconds);
        
        if (warmupSeconds > 0) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("spawnWarmup", "seconds", String.valueOf(warmupSeconds)), "#FFAA00"));
        }
        warmupService.startWarmup(player, currentPos, warmupSeconds, doTeleport, COMMAND_NAME, world, store, ref);
    }
}
