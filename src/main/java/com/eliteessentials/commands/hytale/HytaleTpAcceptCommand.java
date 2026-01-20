package com.eliteessentials.commands.hytale;

import com.eliteessentials.EliteEssentials;
import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.config.PluginConfig;
import com.eliteessentials.model.Location;
import com.eliteessentials.model.TpaRequest;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.services.BackService;
import com.eliteessentials.services.TpaService;
import com.eliteessentials.services.WarmupService;
import com.eliteessentials.util.CommandPermissionUtil;
import com.eliteessentials.util.MessageFormatter;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import javax.annotation.Nonnull;

/**
 * Command: /tpaccept
 * Accepts a pending teleport request and teleports the requester to you.
 * 
 * Permissions:
 * - eliteessentials.command.tpaccept - Accept teleport requests
 * - eliteessentials.bypass.warmup.tpa - Skip warmup for TPA
 */
public class HytaleTpAcceptCommand extends AbstractPlayerCommand {

    private static final Logger logger = Logger.getLogger("EliteEssentials");
    private static final String COMMAND_NAME = "tpaccept";
    
    private final TpaService tpaService;
    private final BackService backService;

    public HytaleTpAcceptCommand(TpaService tpaService, BackService backService) {
        super(COMMAND_NAME, "Accept a teleport request");
        this.tpaService = tpaService;
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
        ConfigManager configManager = EliteEssentials.getInstance().getConfigManager();
        PluginConfig config = configManager.getConfig();
        if (!CommandPermissionUtil.canExecute(ctx, player, Permissions.TPACCEPT, config.tpa.enabled)) {
            return;
        }
        
        UUID playerId = player.getUuid();
        
        // First, peek at the pending request WITHOUT removing it
        List<TpaRequest> pendingRequests = tpaService.getPendingRequests(playerId);
        
        if (pendingRequests.isEmpty()) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("tpaNoPending"), "#FF5555"));
            return;
        }

        // Get the most recent request (last in list)
        TpaRequest request = pendingRequests.get(pendingRequests.size() - 1);
        
        if (request.isExpired()) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("tpaExpired"), "#FF5555"));
            return;
        }

        // Get the requester player from the server - validate BEFORE accepting
        PlayerRef requester = Universe.get().getPlayer(request.getRequesterId());
        
        if (requester == null || !requester.isValid()) {
            // Remove the invalid request so player can accept other requests
            tpaService.denyRequestFrom(playerId, request.getRequesterId());
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("tpaPlayerOffline", "player", request.getRequesterName()), "#FF5555"));
            return;
        }
        
        // Get requester's ref and store directly from PlayerRef (like Essentials does)
        Ref<EntityStore> requesterRef = requester.getReference();
        
        if (requesterRef == null || !requesterRef.isValid()) {
            // Remove the invalid request
            tpaService.denyRequestFrom(playerId, request.getRequesterId());
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("tpaCouldNotFindRequester"), "#FF5555"));
            return;
        }
        
        Store<EntityStore> requesterStore = requesterRef.getStore();
        
        // Get requester's transform from store/ref (not holder - holder may be null for remote players)
        TransformComponent requesterTransform = (TransformComponent) requesterStore.getComponent(requesterRef, TransformComponent.getComponentType());
        if (requesterTransform == null) {
            // Remove the invalid request
            tpaService.denyRequestFrom(playerId, request.getRequesterId());
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("tpaCouldNotGetRequesterPosition"), "#FF5555"));
            return;
        }
        
        // Get target world from the store's external data
        EntityStore requesterEntityStore = requesterStore.getExternalData();
        World requesterWorld = requesterEntityStore != null ? requesterEntityStore.getWorld() : world;
        final World finalRequesterWorld = requesterWorld != null ? requesterWorld : world;
        
        // Get target (acceptor's) position
        TransformComponent targetTransform = (TransformComponent) store.getComponent(ref, TransformComponent.getComponentType());
        if (targetTransform == null) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("couldNotGetPosition"), "#FF5555"));
            return;
        }
        
        Vector3d targetPos = targetTransform.getPosition();
        
        // NOW that all validations passed, officially accept (remove) the request
        tpaService.acceptRequestFrom(playerId, request.getRequesterId());
        
        Vector3d requesterPos = requesterTransform.getPosition();
        HeadRotation reqHeadRot = (HeadRotation) requesterStore.getComponent(requesterRef, HeadRotation.getComponentType());
        Vector3f reqRot = reqHeadRot != null ? reqHeadRot.getRotation() : new Vector3f(0, 0, 0);

        // Save requester's location for /back
        String worldName = finalRequesterWorld.getName();
        Location requesterLoc = new Location(
            worldName,
            requesterPos.getX(), requesterPos.getY(), requesterPos.getZ(),
            reqRot.x, reqRot.y
        );

        // Define the teleport action based on request type
        Runnable doTeleport = () -> {
            if (request.getType() == TpaRequest.Type.TPA) {
                // Regular TPA: Requester teleports to target (acceptor)
                backService.pushLocation(request.getRequesterId(), requesterLoc);
                
                // Get target's yaw to face the same direction
                HeadRotation targetHeadRot = (HeadRotation) store.getComponent(ref, HeadRotation.getComponentType());
                float targetYaw = targetHeadRot != null ? targetHeadRot.getRotation().y : 0;
                
                // Always use pitch=0 to keep player upright
                Teleport teleport = new Teleport(world, targetPos, new Vector3f(0, targetYaw, 0));
                requesterStore.putComponent(requesterRef, Teleport.getComponentType(), teleport);
                logger.fine("[TPA] Teleport component added for " + request.getRequesterName());
                
                requester.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("tpaAcceptedRequester", "player", player.getUsername()), "#55FF55"));
            } else {
                // TPAHERE: Target (acceptor) teleports to requester
                HeadRotation targetHeadRot = (HeadRotation) store.getComponent(ref, HeadRotation.getComponentType());
                Vector3f targetRot = targetHeadRot != null ? targetHeadRot.getRotation() : new Vector3f(0, 0, 0);
                Location targetLoc = new Location(
                    world.getName(),
                    targetPos.getX(), targetPos.getY(), targetPos.getZ(),
                    targetRot.x, targetRot.y
                );
                
                backService.pushLocation(playerId, targetLoc);
                
                // Get requester's yaw to face the same direction
                float requesterYaw = reqRot.y;
                
                // Always use pitch=0 to keep player upright
                Teleport teleport = new Teleport(finalRequesterWorld, requesterPos, new Vector3f(0, requesterYaw, 0));
                store.putComponent(ref, Teleport.getComponentType(), teleport);
                logger.fine("[TPAHERE] Teleport component added for " + player.getUsername());
                
                ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("tpahereAcceptedTarget", "player", request.getRequesterName()), "#55FF55"));
                requester.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("tpahereAcceptedRequester", "player", player.getUsername()), "#55FF55"));
            }
        };

        // Get effective warmup (check bypass permission for requester)
        int warmupSeconds = CommandPermissionUtil.getEffectiveWarmup(request.getRequesterId(), "tpa", config.tpa.warmupSeconds);
        WarmupService warmupService = EliteEssentials.getInstance().getWarmupService();
        
        // Check if requester already has a warmup
        if (warmupService.hasActiveWarmup(request.getRequesterId())) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("tpaRequesterInProgress"), "#FF5555"));
            return;
        }
        
        ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("tpaAccepted", "player", request.getRequesterName()), "#55FF55"));
        
        if (warmupSeconds > 0) {
            requester.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("tpaRequesterWarmup", "player", player.getUsername(), "seconds", String.valueOf(warmupSeconds)), "#FFAA00"));
        }
        
        warmupService.startWarmup(requester, requesterPos, warmupSeconds, doTeleport, "tpa", 
                                   finalRequesterWorld, requesterStore, requesterRef);
    }
}
