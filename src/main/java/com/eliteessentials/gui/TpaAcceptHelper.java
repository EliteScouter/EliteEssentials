package com.eliteessentials.gui;

import com.eliteessentials.EliteEssentials;
import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.config.PluginConfig;
import com.eliteessentials.model.Location;
import com.eliteessentials.model.TpaRequest;
import com.eliteessentials.services.BackService;
import com.eliteessentials.services.WarmupService;
import com.eliteessentials.util.CommandPermissionUtil;
import com.eliteessentials.util.MessageFormatter;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.UUID;

/**
 * Helper class for executing TPA teleports from the GUI.
 * Extracts common teleport logic to be shared between command and GUI.
 */
public final class TpaAcceptHelper {

    private TpaAcceptHelper() {}

    /**
     * Execute the TPA teleport after a request has been accepted.
     */
    public static void executeTeleport(PlayerRef acceptor, Ref<EntityStore> acceptorRef, 
                                        Store<EntityStore> acceptorStore, PlayerRef requester,
                                        TpaRequest request, ConfigManager configManager,
                                        BackService backService) {
        PluginConfig config = configManager.getConfig();
        
        // Get requester's ref and store
        Ref<EntityStore> requesterRef = requester.getReference();
        if (requesterRef == null || !requesterRef.isValid()) {
            acceptor.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("tpaCouldNotFindRequester"), "#FF5555"));
            return;
        }
        
        Store<EntityStore> requesterStore = requesterRef.getStore();
        
        // Get worlds
        EntityStore acceptorEntityStore = acceptorStore.getExternalData();
        World acceptorWorld = acceptorEntityStore != null ? acceptorEntityStore.getWorld() : null;
        
        EntityStore requesterEntityStore = requesterStore.getExternalData();
        World requesterWorld = requesterEntityStore != null ? requesterEntityStore.getWorld() : null;
        
        if (acceptorWorld == null || requesterWorld == null) {
            acceptor.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("tpaCouldNotFindRequester"), "#FF5555"));
            return;
        }
        
        // Get transforms - must be done on the correct world thread
        // For simplicity, we'll execute on acceptor's world and handle cross-world
        acceptorWorld.execute(() -> {
            if (!acceptorRef.isValid() || !requesterRef.isValid()) {
                return;
            }
            
            TransformComponent acceptorTransform = acceptorStore.getComponent(acceptorRef, TransformComponent.getComponentType());
            if (acceptorTransform == null) {
                acceptor.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("couldNotGetPosition"), "#FF5555"));
                return;
            }
            
            Vector3d acceptorPos = acceptorTransform.getPosition();
            HeadRotation acceptorHeadRot = acceptorStore.getComponent(acceptorRef, HeadRotation.getComponentType());
            float acceptorYaw = acceptorHeadRot != null ? acceptorHeadRot.getRotation().y : 0;
            
            // Now get requester position on their world thread
            requesterWorld.execute(() -> {
                if (!requesterRef.isValid()) {
                    return;
                }
                
                TransformComponent requesterTransform = requesterStore.getComponent(requesterRef, TransformComponent.getComponentType());
                if (requesterTransform == null) {
                    acceptor.sendMessage(MessageFormatter.formatWithFallback(
                        configManager.getMessage("tpaCouldNotGetRequesterPosition"), "#FF5555"));
                    return;
                }
                
                Vector3d requesterPos = requesterTransform.getPosition();
                HeadRotation requesterHeadRot = requesterStore.getComponent(requesterRef, HeadRotation.getComponentType());
                float requesterYaw = requesterHeadRot != null ? requesterHeadRot.getRotation().y : 0;
                
                // Determine who teleports based on request type
                boolean isTpaHere = request.getType() == TpaRequest.Type.TPAHERE;
                
                // Define teleport action
                Runnable doTeleport;
                UUID teleportingPlayerId;
                PlayerRef teleportingPlayer;
                Vector3d teleportingPlayerPos;
                World teleportingWorld;
                Store<EntityStore> teleportingStore;
                Ref<EntityStore> teleportingRef;
                
                if (isTpaHere) {
                    // TPAHERE: acceptor teleports to requester
                    teleportingPlayerId = acceptor.getUuid();
                    teleportingPlayer = acceptor;
                    teleportingPlayerPos = acceptorPos;
                    teleportingWorld = acceptorWorld;
                    teleportingStore = acceptorStore;
                    teleportingRef = acceptorRef;
                    
                    Location backLoc = new Location(acceptorWorld.getName(), 
                        acceptorPos.getX(), acceptorPos.getY(), acceptorPos.getZ(), acceptorYaw, 0f);
                    
                    doTeleport = () -> {
                        backService.pushLocation(acceptor.getUuid(), backLoc);
                        acceptorWorld.execute(() -> {
                            if (!acceptorRef.isValid()) return;
                            Teleport teleport = new Teleport(requesterWorld, requesterPos, new Vector3f(0, requesterYaw, 0));
                            acceptorStore.putComponent(acceptorRef, Teleport.getComponentType(), teleport);
                            CommandPermissionUtil.chargeCostDirect(requester.getUuid(), "tpahere", config.tpa.tpahereCost);
                        });
                    };
                } else {
                    // TPA: requester teleports to acceptor
                    teleportingPlayerId = request.getRequesterId();
                    teleportingPlayer = requester;
                    teleportingPlayerPos = requesterPos;
                    teleportingWorld = requesterWorld;
                    teleportingStore = requesterStore;
                    teleportingRef = requesterRef;
                    
                    Location backLoc = new Location(requesterWorld.getName(),
                        requesterPos.getX(), requesterPos.getY(), requesterPos.getZ(), requesterYaw, 0f);
                    
                    doTeleport = () -> {
                        backService.pushLocation(request.getRequesterId(), backLoc);
                        requesterWorld.execute(() -> {
                            if (!requesterRef.isValid()) return;
                            Teleport teleport = new Teleport(acceptorWorld, acceptorPos, new Vector3f(0, acceptorYaw, 0));
                            requesterStore.putComponent(requesterRef, Teleport.getComponentType(), teleport);
                            CommandPermissionUtil.chargeCostDirect(request.getRequesterId(), "tpa", config.tpa.cost);
                            requester.sendMessage(MessageFormatter.formatWithFallback(
                                configManager.getMessage("tpaAcceptedRequester", "player", acceptor.getUsername()), "#55FF55"));
                        });
                    };
                }
                
                // Check warmup
                int warmupSeconds = CommandPermissionUtil.getEffectiveWarmup(teleportingPlayerId, "tpa", config.tpa.warmupSeconds);
                WarmupService warmupService = EliteEssentials.getInstance().getWarmupService();
                
                if (warmupService.hasActiveWarmup(teleportingPlayerId)) {
                    acceptor.sendMessage(MessageFormatter.formatWithFallback(
                        configManager.getMessage("tpaRequesterInProgress"), "#FF5555"));
                    return;
                }
                
                if (warmupSeconds > 0) {
                    teleportingPlayer.sendMessage(MessageFormatter.formatWithFallback(
                        configManager.getMessage("tpaRequesterWarmup", 
                            "player", isTpaHere ? request.getRequesterName() : acceptor.getUsername(),
                            "seconds", String.valueOf(warmupSeconds)), "#FFAA00"));
                }
                
                warmupService.startWarmup(teleportingPlayer, teleportingPlayerPos, warmupSeconds, 
                    doTeleport, "tpa", teleportingWorld, teleportingStore, teleportingRef);
            });
        });
    }
}
