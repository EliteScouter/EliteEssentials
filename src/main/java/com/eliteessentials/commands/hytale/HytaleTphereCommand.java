package com.eliteessentials.commands.hytale;

import com.eliteessentials.EliteEssentials;
import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.model.Location;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.services.BackService;
import com.eliteessentials.util.CommandPermissionUtil;
import com.eliteessentials.util.MessageFormatter;
import com.eliteessentials.util.TeleportUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import org.joml.Vector3d;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.eliteessentials.commands.base.ElitePlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.List;

import javax.annotation.Nonnull;
import com.eliteessentials.util.CommandSpyUtil;

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
public class HytaleTphereCommand extends ElitePlayerCommand {

    private static final String COMMAND_NAME = "tphere";
    
    private final BackService backService;
    private final RequiredArg<PlayerRef> targetArg;

    public HytaleTphereCommand(BackService backService) {
        super(COMMAND_NAME, "Teleport a player to your location");
        this.backService = backService;
        // PLAYER_REF wires the engine's built-in online-player tab autocomplete
        // (server 0.5.1+ chat suggestion UI) and parses straight to PlayerRef.
        this.targetArg = withRequiredArg("player", "Target player", ArgTypes.PLAYER_REF);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, 
                          @Nonnull PlayerRef player, @Nonnull World world) {
        CommandSpyUtil.notify(ctx);
        // Permission check - admin only
        if (!CommandPermissionUtil.canExecuteAdmin(ctx, player, Permissions.TPHERE, true)) {
            return;
        }
        
        ConfigManager configManager = EliteEssentials.getInstance().getConfigManager();
        PlayerRef target = ctx.get(targetArg);
        String targetName = target != null ? target.getUsername() : "";

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
        
        // Get admin's current position and rotation (safe - we're on admin's world thread)
        TransformComponent adminTransform = (TransformComponent) store.getComponent(ref, TransformComponent.getComponentType());
        HeadRotation adminHeadRotation = (HeadRotation) store.getComponent(ref, HeadRotation.getComponentType());
        
        if (adminTransform == null || adminHeadRotation == null) {
            ctx.sendMessage(Message.raw("Failed to get your location.").color("#FF5555"));
            return;
        }
        
        Vector3d adminPos = adminTransform.getPosition();
        Rotation3f adminRot = adminHeadRotation.getRotation();
        
        // Destination rotation: preserve admin yaw, pitch=0 to avoid player tilt
        Rotation3f destRotation = new Rotation3f(0.0f, adminRot.y, 0.0f);
        
        // CRITICAL: Target may be on a different world thread.
        // All target store/ref access MUST happen on the target's world thread.
        // We get the target's world via PlayerRef without touching the store.
        Ref<EntityStore> targetRefForWorld = target.getReference();
        if (targetRefForWorld == null || !targetRefForWorld.isValid()) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("playerNotFound", "player", targetName), "#FF5555"));
            return;
        }
        Store<EntityStore> targetStoreForWorld = targetRefForWorld.getStore();
        EntityStore targetEntityStore = targetStoreForWorld != null ? targetStoreForWorld.getExternalData() : null;
        World targetWorld = targetEntityStore != null ? targetEntityStore.getWorld() : world;
        final World finalTargetWorld = targetWorld != null ? targetWorld : world;
        
        finalTargetWorld.execute(() -> {
            // Now on target's world thread - safe to access target's store/ref
            Ref<EntityStore> freshTargetRef = target.getReference();
            if (freshTargetRef == null || !freshTargetRef.isValid()) return;
            
            Store<EntityStore> freshTargetStore = freshTargetRef.getStore();
            if (freshTargetStore == null) return;
            
            // Save target's current location for /back
            TransformComponent targetTransform = (TransformComponent) freshTargetStore.getComponent(freshTargetRef, TransformComponent.getComponentType());
            if (targetTransform != null) {
                Vector3d targetPos = targetTransform.getPosition();
                HeadRotation targetHeadRot = (HeadRotation) freshTargetStore.getComponent(freshTargetRef, HeadRotation.getComponentType());
                Rotation3f targetRot = targetHeadRot != null ? targetHeadRot.getRotation() : new Rotation3f(0, 0, 0);
                EntityStore freshEntityStore = freshTargetStore.getExternalData();
                World currentTargetWorld = freshEntityStore != null ? freshEntityStore.getWorld() : finalTargetWorld;
                String worldName = currentTargetWorld != null ? currentTargetWorld.getName() : finalTargetWorld.getName();
                Location targetLoc = new Location(
                    worldName,
                    targetPos.x, targetPos.y, targetPos.z,
                    targetRot.y, 0f  // yaw only, pitch=0 to prevent tilt
                );
                backService.pushLocation(target.getUuid(), targetLoc);
            }
            
            // Teleport target to admin's location using TeleportUtil (handles Teleport.createForPlayer correctly)
            TeleportUtil.safeTeleport(finalTargetWorld, world, adminPos, destRotation, target,
                () -> target.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("tphereTeleported",
                    "player", player.getUsername()), "#FFFF55")),
                null
            );
        });
        
        // Send confirmation to admin (safe - on admin's world thread)
        ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("tphereSuccess", 
            "player", target.getUsername()), "#55FF55"));
    }
}
