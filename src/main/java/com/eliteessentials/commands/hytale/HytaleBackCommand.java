package com.eliteessentials.commands.hytale;

import com.eliteessentials.EliteEssentials;
import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.config.PluginConfig;
import com.eliteessentials.model.Location;
import com.eliteessentials.services.BackService;
import com.eliteessentials.services.WarmupService;
import com.eliteessentials.util.CommandPermissionUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Optional;
import java.util.UUID;

/**
 * Command: /back
 * Teleports the player to their previous location.
 * Supports warmup (stand still) from config.
 */
public class HytaleBackCommand extends AbstractPlayerCommand {

    private final BackService backService;

    public HytaleBackCommand(BackService backService) {
        super("back", "Return to your previous location");
        this.backService = backService;
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref, 
                          PlayerRef player, World world) {
        PluginConfig config = EliteEssentials.getInstance().getConfigManager().getConfig();
        if (!CommandPermissionUtil.canExecute(ctx, player, config.back.enabled)) {
            return;
        }
        
        ConfigManager configManager = EliteEssentials.getInstance().getConfigManager();
        UUID playerId = player.getUuid();
        WarmupService warmupService = EliteEssentials.getInstance().getWarmupService();
        
        // Check if already warming up
        if (warmupService.hasActiveWarmup(playerId)) {
            ctx.sendMessage(Message.raw(configManager.getMessage("teleportInProgress")).color("#FF5555"));
            return;
        }
        
        // Peek at the location first (don't pop yet - only pop after successful teleport)
        Optional<Location> previousLocation = backService.peekLocation(playerId);
        
        if (previousLocation.isEmpty()) {
            ctx.sendMessage(Message.raw(configManager.getMessage("backNoLocation")).color("#FF5555"));
            return;
        }

        Location destination = previousLocation.get();
        
        // Get current position for warmup
        TransformComponent transform = (TransformComponent) store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            ctx.sendMessage(Message.raw(configManager.getMessage("couldNotGetPosition")).color("#FF5555"));
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
                Vector3f targetRot = new Vector3f(destination.getPitch(), destination.getYaw(), 0);
                
                Teleport teleport = new Teleport(finalWorld, targetPos, targetRot);
                store.addComponent(ref, Teleport.getComponentType(), teleport);
                
                ctx.sendMessage(Message.raw(configManager.getMessage("backTeleported")).color("#55FF55"));
            });
        };

        // Start warmup or teleport immediately
        int warmupSeconds = config.back.warmupSeconds;
        if (warmupSeconds > 0) {
            ctx.sendMessage(Message.raw(configManager.getMessage("backWarmup", "seconds", String.valueOf(warmupSeconds))).color("#FFAA00"));
        }
        warmupService.startWarmup(player, currentPos, warmupSeconds, doTeleport, "back", world, store, ref);
    }
}
