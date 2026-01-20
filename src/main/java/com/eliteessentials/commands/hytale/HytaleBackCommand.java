package com.eliteessentials.commands.hytale;

import com.eliteessentials.EliteEssentials;
import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.config.PluginConfig;
import com.eliteessentials.model.Location;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.services.BackService;
import com.eliteessentials.services.WarmupService;
import com.eliteessentials.util.CommandPermissionUtil;
import com.eliteessentials.util.MessageFormatter;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
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
 * - eliteessentials.command.back - Use /back command
 * - eliteessentials.command.back.ondeath - Return to death location
 * - eliteessentials.bypass.warmup.back - Skip warmup
 * - eliteessentials.bypass.cooldown.back - Skip cooldown
 */
public class HytaleBackCommand extends AbstractPlayerCommand {

    private static final String COMMAND_NAME = "back";
    
    private final BackService backService;

    public HytaleBackCommand(BackService backService) {
        super(COMMAND_NAME, "Return to your previous location");
        this.backService = backService;
        
        // Permission check handled in execute() via CommandPermissionUtil
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, 
                          @Nonnull PlayerRef player, @Nonnull World world) {
        PluginConfig config = EliteEssentials.getInstance().getConfigManager().getConfig();
        if (!CommandPermissionUtil.canExecute(ctx, player, Permissions.BACK, config.back.enabled)) {
            return;
        }
        
        ConfigManager configManager = EliteEssentials.getInstance().getConfigManager();
        UUID playerId = player.getUuid();
        WarmupService warmupService = EliteEssentials.getInstance().getWarmupService();
        
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

        // Define the teleport action
        Runnable doTeleport = () -> {
            // Now pop the location (consume it)
            backService.popLocation(playerId);
            
            world.execute(() -> {
                Vector3d targetPos = new Vector3d(destination.getX(), destination.getY(), destination.getZ());
                // Always use pitch=0 to keep player upright, preserve yaw for direction
                Vector3f targetRot = new Vector3f(0, destination.getYaw(), 0);
                
                Teleport teleport = new Teleport(finalWorld, targetPos, targetRot);
                store.putComponent(ref, Teleport.getComponentType(), teleport);
                
                ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("backTeleported"), "#55FF55"));
            });
        };

        // Get effective warmup (check bypass permission)
        int warmupSeconds = CommandPermissionUtil.getEffectiveWarmup(playerId, COMMAND_NAME, config.back.warmupSeconds);
        
        if (warmupSeconds > 0) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("backWarmup", "seconds", String.valueOf(warmupSeconds)), "#FFAA00"));
        }
        warmupService.startWarmup(player, currentPos, warmupSeconds, doTeleport, COMMAND_NAME, world, store, ref);
    }
}
