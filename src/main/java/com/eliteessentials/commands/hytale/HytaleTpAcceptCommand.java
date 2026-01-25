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
            tpaService.denyRequestFrom(playerId, request.getRequesterId());
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("tpaPlayerOffline", "player", request.getRequesterName()), "#FF5555"));
            return;
        }
        
        // Get requester's ref and store
        Ref<EntityStore> requesterRef = requester.getReference();
        
        if (requesterRef == null || !requesterRef.isValid()) {
            tpaService.denyRequestFrom(playerId, request.getRequesterId());
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("tpaCouldNotFindRequester"), "#FF5555"));
            return;
        }
        
        Store<EntityStore> requesterStore = requesterRef.getStore();
        
        // Get requester's world
        EntityStore requesterEntityStore = requesterStore.getExternalData();
        World requesterWorld = requesterEntityStore != null ? requesterEntityStore.getWorld() : null;
        
        if (requesterWorld == null) {
            tpaService.denyRequestFrom(playerId, request.getRequesterId());
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("tpaCouldNotFindRequester"), "#FF5555"));
            return;
        }
        
        // Check if players are in different worlds - need special handling
        boolean crossWorld = !world.getName().equals(requesterWorld.getName());
        
        if (crossWorld) {
            // Cross-world teleport: gather data from each world on its own thread
            executeCrossWorldTpa(ctx, store, ref, player, world, requester, requesterRef, 
                                 requesterStore, requesterWorld, request, configManager, config);
        } else {
            // Same world: can access both stores directly
            executeSameWorldTpa(ctx, store, ref, player, world, requester, requesterRef, 
                               requesterStore, request, configManager, config);
        }
    }
    
    /**
     * Handle TPA when both players are in the same world
     */
    private void executeSameWorldTpa(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref,
                                      PlayerRef player, World world, PlayerRef requester, 
                                      Ref<EntityStore> requesterRef, Store<EntityStore> requesterStore,
                                      TpaRequest request, ConfigManager configManager, PluginConfig config) {
        UUID playerId = player.getUuid();
        
        // Get requester's transform
        TransformComponent requesterTransform = (TransformComponent) requesterStore.getComponent(requesterRef, TransformComponent.getComponentType());
        if (requesterTransform == null) {
            tpaService.denyRequestFrom(playerId, request.getRequesterId());
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("tpaCouldNotGetRequesterPosition"), "#FF5555"));
            return;
        }
        
        // Get acceptor's transform
        TransformComponent targetTransform = (TransformComponent) store.getComponent(ref, TransformComponent.getComponentType());
        if (targetTransform == null) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("couldNotGetPosition"), "#FF5555"));
            return;
        }
        
        // Accept the request
        tpaService.acceptRequestFrom(playerId, request.getRequesterId());
        
        Vector3d requesterPos = requesterTransform.getPosition();
        Vector3d targetPos = targetTransform.getPosition();
        
        HeadRotation reqHeadRot = (HeadRotation) requesterStore.getComponent(requesterRef, HeadRotation.getComponentType());
        Vector3f reqRot = reqHeadRot != null ? reqHeadRot.getRotation() : new Vector3f(0, 0, 0);
        
        HeadRotation targetHeadRot = (HeadRotation) store.getComponent(ref, HeadRotation.getComponentType());
        float targetYaw = targetHeadRot != null ? targetHeadRot.getRotation().y : 0;
        
        // Prepare locations for /back
        Location requesterLoc = new Location(world.getName(), requesterPos.getX(), requesterPos.getY(), requesterPos.getZ(), reqRot.x, reqRot.y);
        Location targetLoc = new Location(world.getName(), targetPos.getX(), targetPos.getY(), targetPos.getZ(), 
                                          targetHeadRot != null ? targetHeadRot.getRotation().x : 0, targetYaw);
        
        // Define teleport action
        Runnable doTeleport = () -> {
            world.execute(() -> {
                if (request.getType() == TpaRequest.Type.TPA) {
                    // Requester teleports to acceptor
                    if (!requesterRef.isValid()) return;
                    backService.pushLocation(request.getRequesterId(), requesterLoc);
                    Teleport teleport = new Teleport(world, targetPos, new Vector3f(0, targetYaw, 0));
                    requesterStore.putComponent(requesterRef, Teleport.getComponentType(), teleport);
                    requester.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("tpaAcceptedRequester", "player", player.getUsername()), "#55FF55"));
                } else {
                    // Acceptor teleports to requester (TPAHERE)
                    if (!ref.isValid()) return;
                    backService.pushLocation(playerId, targetLoc);
                    Teleport teleport = new Teleport(world, requesterPos, new Vector3f(0, reqRot.y, 0));
                    store.putComponent(ref, Teleport.getComponentType(), teleport);
                    ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("tpahereAcceptedTarget", "player", request.getRequesterName()), "#55FF55"));
                    requester.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("tpahereAcceptedRequester", "player", player.getUsername()), "#55FF55"));
                }
            });
        };
        
        startWarmupAndTeleport(ctx, player, requester, requesterPos, request, configManager, config, 
                               doTeleport, world, requesterStore, requesterRef);
    }
    
    /**
     * Handle TPA when players are in different worlds
     */
    private void executeCrossWorldTpa(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref,
                                       PlayerRef player, World acceptorWorld, PlayerRef requester,
                                       Ref<EntityStore> requesterRef, Store<EntityStore> requesterStore,
                                       World requesterWorld, TpaRequest request, 
                                       ConfigManager configManager, PluginConfig config) {
        UUID playerId = player.getUuid();
        
        // We need to gather data from both worlds on their respective threads
        // First, gather requester data on requester's world thread
        requesterWorld.execute(() -> {
            if (!requesterRef.isValid()) {
                player.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("tpaCouldNotFindRequester"), "#FF5555"));
                return;
            }
            
            TransformComponent requesterTransform = (TransformComponent) requesterStore.getComponent(requesterRef, TransformComponent.getComponentType());
            if (requesterTransform == null) {
                tpaService.denyRequestFrom(playerId, request.getRequesterId());
                player.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("tpaCouldNotGetRequesterPosition"), "#FF5555"));
                return;
            }
            
            Vector3d requesterPos = requesterTransform.getPosition();
            HeadRotation reqHeadRot = (HeadRotation) requesterStore.getComponent(requesterRef, HeadRotation.getComponentType());
            Vector3f reqRot = reqHeadRot != null ? reqHeadRot.getRotation() : new Vector3f(0, 0, 0);
            
            // Now gather acceptor data on acceptor's world thread
            acceptorWorld.execute(() -> {
                if (!ref.isValid()) {
                    player.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("couldNotGetPosition"), "#FF5555"));
                    return;
                }
                
                TransformComponent targetTransform = (TransformComponent) store.getComponent(ref, TransformComponent.getComponentType());
                if (targetTransform == null) {
                    player.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("couldNotGetPosition"), "#FF5555"));
                    return;
                }
                
                // Accept the request now that we have all data
                tpaService.acceptRequestFrom(playerId, request.getRequesterId());
                
                Vector3d targetPos = targetTransform.getPosition();
                HeadRotation targetHeadRot = (HeadRotation) store.getComponent(ref, HeadRotation.getComponentType());
                float targetYaw = targetHeadRot != null ? targetHeadRot.getRotation().y : 0;
                Vector3f targetRot = targetHeadRot != null ? targetHeadRot.getRotation() : new Vector3f(0, 0, 0);
                
                // Prepare locations for /back
                Location requesterLoc = new Location(requesterWorld.getName(), requesterPos.getX(), requesterPos.getY(), requesterPos.getZ(), reqRot.x, reqRot.y);
                Location targetLoc = new Location(acceptorWorld.getName(), targetPos.getX(), targetPos.getY(), targetPos.getZ(), targetRot.x, targetRot.y);
                
                // Define teleport action
                Runnable doTeleport = () -> {
                    if (request.getType() == TpaRequest.Type.TPA) {
                        // Requester teleports to acceptor (cross-world)
                        backService.pushLocation(request.getRequesterId(), requesterLoc);
                        requesterWorld.execute(() -> {
                            if (!requesterRef.isValid()) return;
                            Teleport teleport = new Teleport(acceptorWorld, targetPos, new Vector3f(0, targetYaw, 0));
                            requesterStore.putComponent(requesterRef, Teleport.getComponentType(), teleport);
                            requester.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("tpaAcceptedRequester", "player", player.getUsername()), "#55FF55"));
                        });
                    } else {
                        // Acceptor teleports to requester (TPAHERE cross-world)
                        backService.pushLocation(playerId, targetLoc);
                        acceptorWorld.execute(() -> {
                            if (!ref.isValid()) return;
                            Teleport teleport = new Teleport(requesterWorld, requesterPos, new Vector3f(0, reqRot.y, 0));
                            store.putComponent(ref, Teleport.getComponentType(), teleport);
                            player.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("tpahereAcceptedTarget", "player", request.getRequesterName()), "#55FF55"));
                        });
                        requester.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("tpahereAcceptedRequester", "player", player.getUsername()), "#55FF55"));
                    }
                };
                
                // Send acceptance message
                player.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("tpaAccepted", "player", request.getRequesterName()), "#55FF55"));
                
                // Start warmup
                int warmupSeconds = CommandPermissionUtil.getEffectiveWarmup(request.getRequesterId(), "tpa", config.tpa.warmupSeconds);
                WarmupService warmupService = EliteEssentials.getInstance().getWarmupService();
                
                if (warmupService.hasActiveWarmup(request.getRequesterId())) {
                    player.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("tpaRequesterInProgress"), "#FF5555"));
                    return;
                }
                
                if (warmupSeconds > 0) {
                    requester.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("tpaRequesterWarmup", "player", player.getUsername(), "seconds", String.valueOf(warmupSeconds)), "#FFAA00"));
                }
                
                warmupService.startWarmup(requester, requesterPos, warmupSeconds, doTeleport, "tpa", 
                                          requesterWorld, requesterStore, requesterRef);
            });
        });
    }
    
    /**
     * Common warmup and teleport logic
     */
    private void startWarmupAndTeleport(CommandContext ctx, PlayerRef player, PlayerRef requester,
                                         Vector3d requesterPos, TpaRequest request,
                                         ConfigManager configManager, PluginConfig config,
                                         Runnable doTeleport, World world, 
                                         Store<EntityStore> requesterStore, Ref<EntityStore> requesterRef) {
        int warmupSeconds = CommandPermissionUtil.getEffectiveWarmup(request.getRequesterId(), "tpa", config.tpa.warmupSeconds);
        WarmupService warmupService = EliteEssentials.getInstance().getWarmupService();
        
        if (warmupService.hasActiveWarmup(request.getRequesterId())) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("tpaRequesterInProgress"), "#FF5555"));
            return;
        }
        
        ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("tpaAccepted", "player", request.getRequesterName()), "#55FF55"));
        
        if (warmupSeconds > 0) {
            requester.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("tpaRequesterWarmup", "player", player.getUsername(), "seconds", String.valueOf(warmupSeconds)), "#FFAA00"));
        }
        
        warmupService.startWarmup(requester, requesterPos, warmupSeconds, doTeleport, "tpa", 
                                   world, requesterStore, requesterRef);
    }
}
