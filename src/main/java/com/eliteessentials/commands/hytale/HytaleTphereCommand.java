package com.eliteessentials.commands.hytale;

import com.eliteessentials.EliteEssentials;
import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.model.Location;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.services.BackService;
import com.eliteessentials.util.CommandPermissionUtil;
import com.eliteessentials.util.MessageFormatter;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.List;

import javax.annotation.Nonnull;

/**
 * Command: /tphere <player>
 * Instantly teleports a player to the admin's location.
 * 
 * This is an admin-only command with no warmup or cooldown.
 * The target player's /back location is saved before teleporting.
 * 
 * Permissions:
 * - eliteessentials.command.tp.tphere (Admin only)
 */
public class HytaleTphereCommand extends AbstractPlayerCommand {

    private static final String COMMAND_NAME = "tphere";
    
    private final BackService backService;
    private final RequiredArg<String> targetArg;

    public HytaleTphereCommand(BackService backService) {
        super(COMMAND_NAME, "Teleport a player to your location");
        this.backService = backService;
        // Use STRING instead of PLAYER_REF to show custom error message
        this.targetArg = withRequiredArg("player", "Target player", ArgTypes.STRING);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, 
                          @Nonnull PlayerRef player, @Nonnull World world) {
        // Permission check - admin only
        if (!CommandPermissionUtil.canExecuteAdmin(ctx, player, Permissions.TPHERE, true)) {
            return;
        }
        
        ConfigManager configManager = EliteEssentials.getInstance().getConfigManager();
        String targetName = ctx.get(targetArg);
        
        // Find target player by name
        PlayerRef target = findPlayer(targetName);
        
        if (target == null) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("playerNotFound", "player", targetName), "#FF5555"));
            return;
        }
        
        // Can't teleport yourself
        if (target.getUuid().equals(player.getUuid())) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("tphereSelf"), "#FF5555"));
            return;
        }
        
        // Get target's entity ref
        Ref<EntityStore> targetRef = target.getReference();
        if (targetRef == null || !targetRef.isValid()) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("playerNotFound", "player", targetName), "#FF5555"));
            return;
        }
        
        Store<EntityStore> targetStore = targetRef.getStore();
        if (targetStore == null) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("playerNotFound", "player", targetName), "#FF5555"));
            return;
        }
        
        // Get target's current position for /back
        TransformComponent targetTransform = (TransformComponent) targetStore.getComponent(targetRef, TransformComponent.getComponentType());
        if (targetTransform != null) {
            Vector3d targetPos = targetTransform.getPosition();
            HeadRotation targetHeadRot = (HeadRotation) targetStore.getComponent(targetRef, HeadRotation.getComponentType());
            Vector3f targetRot = targetHeadRot != null ? targetHeadRot.getRotation() : new Vector3f(0, 0, 0);
            
            // Get target's world
            EntityStore targetEntityStore = targetStore.getExternalData();
            World targetWorld = targetEntityStore != null ? targetEntityStore.getWorld() : world;
            String worldName = targetWorld != null ? targetWorld.getName() : world.getName();
            
            // Save target's location for /back
            Location targetLoc = new Location(
                worldName,
                targetPos.getX(), targetPos.getY(), targetPos.getZ(),
                targetRot.x, targetRot.y
            );
            backService.pushLocation(target.getUuid(), targetLoc);
        }
        
        // Get admin's current position and rotation
        TransformComponent adminTransform = (TransformComponent) store.getComponent(ref, TransformComponent.getComponentType());
        HeadRotation adminHeadRotation = (HeadRotation) store.getComponent(ref, HeadRotation.getComponentType());
        
        if (adminTransform == null || adminHeadRotation == null) {
            ctx.sendMessage(Message.raw("Failed to get your location.").color("#FF5555"));
            return;
        }
        
        Vector3d adminPos = adminTransform.getPosition();
        Vector3f adminRot = adminHeadRotation.getRotation();
        
        // Create teleport with admin's position and rotation (pitch=0 for upright landing)
        Vector3f targetRotation = new Vector3f(0.0f, adminRot.y, 0.0f);
        Teleport teleport = new Teleport(world, adminPos, targetRotation);
        
        // Use putComponent for creative mode compatibility
        targetStore.putComponent(targetRef, Teleport.getComponentType(), teleport);
        
        // Send messages
        ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("tphereSuccess", 
            "player", target.getUsername()), "#55FF55"));
        target.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("tphereTeleported", 
            "player", player.getUsername()), "#FFFF55"));
    }
    
    /**
     * Find a player by name (case-insensitive).
     */
    private PlayerRef findPlayer(String name) {
        List<PlayerRef> players = Universe.get().getPlayers();
        for (PlayerRef p : players) {
            if (p.getUsername().equalsIgnoreCase(name)) {
                return p;
            }
        }
        return null;
    }
}
