package com.eliteessentials.commands.hytale;

import com.eliteessentials.EliteEssentials;
import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.config.PluginConfig;
import com.eliteessentials.model.Location;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.permissions.PermissionService;
import com.eliteessentials.services.BackService;
import com.eliteessentials.services.CooldownService;
import com.eliteessentials.services.WarmupService;
import com.eliteessentials.util.CommandPermissionUtil;
import com.eliteessentials.util.MessageFormatter;
import com.eliteessentials.util.TeleportUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.Optional;
import java.util.UUID;

/**
 * Command: /back
 * Teleports the player to their previous location.
 * 
 * Permissions:
 * - eliteessentials.command.tp.back - Use /back command
 * - eliteessentials.command.tp.back.ondeath - Return to death location
 * - eliteessentials.command.tp.bypass.warmup.back - Skip warmup
 * - eliteessentials.command.tp.bypass.cooldown.back - Skip cooldown
 * - eliteessentials.command.tp.cooldown.back.<seconds> - Custom cooldown
 */
public class HytaleBackCommand extends AbstractPlayerCommand {

    private static final String COMMAND_NAME = "back";
    
    private final BackService backService;
    private final CooldownService cooldownService;

    public HytaleBackCommand(BackService backService, CooldownService cooldownService) {
        super(COMMAND_NAME, "Return to your previous location");
        this.backService = backService;
        this.cooldownService = cooldownService;
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, 
                          @Nonnull PlayerRef player, @Nonnull World world) {
        PluginConfig config = EliteEssentials.getInstance().getConfigManager().getConfig();
        if (!CommandPermissionUtil.canExecuteWithCost(ctx, player, Permissions.BACK, 
                config.back.enabled, "back", config.back.cost)) {
            return;
        }
        
        ConfigManager configManager = EliteEssentials.getInstance().getConfigManager();
        UUID playerId = player.getUuid();
        WarmupService warmupService = EliteEssentials.getInstance().getWarmupService();
        
        // Get effective cooldown from permissions
        int effectiveCooldown = PermissionService.get().getTpCommandCooldown(playerId, COMMAND_NAME, config.back.cooldownSeconds);
        
        // Check cooldown if player has one
        if (effectiveCooldown > 0) {
            int cooldownRemaining = cooldownService.getCooldownRemaining(COMMAND_NAME, playerId);
            if (cooldownRemaining > 0) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("onCooldown", "seconds", String.valueOf(cooldownRemaining)), "#FF5555"));
                return;
            }
        }
        
        // Check if already warming up
        if (warmupService.hasActiveWarmup(playerId)) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("teleportInProgress"), "#FF5555"));
            return;
        }
        
        // Peek at the location first (don't pop yet - only pop after successful teleport)
        Optional<Location> previousLocation = backService.peekLocation(playerId);
        
        if (previousLocation.isEmpty()) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("backNoLocation"), "#FF5555"));
            return;
        }

        Location destination = previousLocation.get();
        
        // Get current position for warmup
        TransformComponent transform = (TransformComponent) store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("couldNotGetPosition"), "#FF5555"));
            return;
        }
        
        Vector3d currentPos = transform.getPosition();

        // Get target world
        World targetWorld = Universe.get().getWorld(destination.getWorld());
        if (targetWorld == null) {
            targetWorld = world;
        }
        final World finalWorld = targetWorld;

        // Capture effective cooldown for use in lambda
        final int finalEffectiveCooldown = effectiveCooldown;
        
        // For cross-world teleports, we must execute on the TARGET world's thread
        final boolean isCrossWorld = !world.getName().equals(finalWorld.getName());
        
        // Define the teleport action
        Runnable doTeleport = () -> {
            // Now pop the location (consume it)
            backService.popLocation(playerId);
            
            Vector3d targetPos = new Vector3d(destination.getX(), destination.getY(), destination.getZ());
            // Always use pitch=0 to keep player upright, preserve yaw for direction
            Vector3f targetRot = new Vector3f(0, destination.getYaw(), 0);
            
            // Pre-load destination chunk before teleporting to prevent
            // "entity moved into unloaded chunk" crash
            TeleportUtil.safeTeleport(world, finalWorld, targetPos, targetRot, store, ref,
                () -> {
                    // Charge cost AFTER successful teleport
                    CommandPermissionUtil.chargeCost(ctx, player, "back", config.back.cost);
                    
                    // Set cooldown after successful teleport
                    if (finalEffectiveCooldown > 0) {
                        cooldownService.setCooldown(COMMAND_NAME, playerId, finalEffectiveCooldown);
                    }
                    
                    player.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("backTeleported"), "#55FF55"));
                },
                () -> {
                    player.sendMessage(MessageFormatter.formatWithFallback("&cTeleport failed - destination chunk could not be loaded.", "#FF5555"));
                }
            );
        };

        // Get effective warmup (check bypass permission)
        int warmupSeconds = CommandPermissionUtil.getEffectiveWarmup(playerId, COMMAND_NAME, config.back.warmupSeconds);
        
        if (warmupSeconds > 0) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("backWarmup", "seconds", String.valueOf(warmupSeconds)), "#FFAA00"));
        }
        warmupService.startWarmup(player, currentPos, warmupSeconds, doTeleport, COMMAND_NAME, world, store, ref);
    }
}
