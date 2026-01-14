package com.eliteessentials.commands.hytale;

import com.eliteessentials.EliteEssentials;
import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.config.PluginConfig;
import com.eliteessentials.model.Location;
import com.eliteessentials.model.TpaRequest;
import com.eliteessentials.services.BackService;
import com.eliteessentials.services.TpaService;
import com.eliteessentials.services.WarmupService;
import com.eliteessentials.util.CommandPermissionUtil;
import com.hypixel.hytale.component.Holder;
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

import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Command: /tpaccept
 * Accepts a pending teleport request and teleports the requester to you.
 * Supports warmup (requester must stand still) from config.
 */
public class HytaleTpAcceptCommand extends AbstractPlayerCommand {

    private static final Logger logger = Logger.getLogger("EliteEssentials");
    
    private final TpaService tpaService;
    private final BackService backService;

    public HytaleTpAcceptCommand(TpaService tpaService, BackService backService) {
        super("tpaccept", "Accept a teleport request");
        this.tpaService = tpaService;
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
        if (!CommandPermissionUtil.canExecute(ctx, player, config.tpa.enabled)) {
            return;
        }
        
        ConfigManager configManager = EliteEssentials.getInstance().getConfigManager();
        UUID playerId = player.getUuid();
        
        Optional<TpaRequest> requestOpt = tpaService.acceptRequest(playerId);

        if (requestOpt.isEmpty()) {
            ctx.sendMessage(Message.raw(configManager.getMessage("tpaNoPending")).color("#FF5555"));
            return;
        }

        TpaRequest request = requestOpt.get();
        
        if (request.isExpired()) {
            ctx.sendMessage(Message.raw(configManager.getMessage("tpaExpired")).color("#FF5555"));
            return;
        }

        // Get the requester player from the server
        PlayerRef requester = Universe.get().getPlayer(request.getRequesterId());
        
        if (requester == null || !requester.isValid()) {
            ctx.sendMessage(Message.raw(configManager.getMessage("tpaPlayerOffline", "player", request.getRequesterName())).color("#FF5555"));
            return;
        }
        
        // Get target (acceptor's) position
        TransformComponent targetTransform = (TransformComponent) store.getComponent(ref, TransformComponent.getComponentType());
        if (targetTransform == null) {
            ctx.sendMessage(Message.raw(configManager.getMessage("couldNotGetPosition")).color("#FF5555"));
            return;
        }
        
        Vector3d targetPos = targetTransform.getPosition();
        
        // Get requester's current position for warmup and /back
        Holder<EntityStore> requesterHolder = requester.getHolder();
        if (requesterHolder == null) {
            ctx.sendMessage(Message.raw(configManager.getMessage("tpaCouldNotFindRequester")).color("#FF5555"));
            return;
        }
        
        TransformComponent requesterTransform = requesterHolder.getComponent(TransformComponent.getComponentType());
        if (requesterTransform == null) {
            ctx.sendMessage(Message.raw(configManager.getMessage("tpaCouldNotGetRequesterPosition")).color("#FF5555"));
            return;
        }
        
        Vector3d requesterPos = requesterTransform.getPosition();
        HeadRotation reqHeadRot = requesterHolder.getComponent(HeadRotation.getComponentType());
        Vector3f reqRot = reqHeadRot != null ? reqHeadRot.getRotation() : new Vector3f(0, 0, 0);
        
        // Get requester's world
        World requesterWorld = Universe.get().getWorld(requester.getWorldUuid());
        if (requesterWorld == null) {
            requesterWorld = world;
        }
        final World finalRequesterWorld = requesterWorld;
        
        // Get requester's store and ref for warmup
        EntityStore requesterEntityStore = finalRequesterWorld.getEntityStore();
        Store<EntityStore> requesterStore = requesterEntityStore.getStore();
        Ref<EntityStore> requesterRef = requesterEntityStore.getRefFromUUID(request.getRequesterId());
        
        if (requesterRef == null) {
            ctx.sendMessage(Message.raw(configManager.getMessage("tpaCouldNotFindRequester")).color("#FF5555"));
            return;
        }

        // Save requester's location for /back
        String worldName = finalRequesterWorld.getName();
        Location requesterLoc = new Location(
            worldName,
            requesterPos.getX(), requesterPos.getY(), requesterPos.getZ(),
            reqRot.x, reqRot.y
        );

        // Define the teleport action
        Runnable doTeleport = () -> {
            backService.pushLocation(request.getRequesterId(), requesterLoc);
            
            finalRequesterWorld.execute(() -> {
                try {
                    Teleport teleport = new Teleport(world, targetPos, Vector3f.NaN);
                    requesterStore.addComponent(requesterRef, Teleport.getComponentType(), teleport);
                    logger.fine("[TPA] Teleport component added for " + request.getRequesterName());
                } catch (Exception e) {
                    logger.warning("[TPA] Error teleporting: " + e.getMessage());
                }
            });
            
            requester.sendMessage(Message.raw(configManager.getMessage("tpaAcceptedRequester", "player", player.getUsername())).color("#55FF55"));
        };

        // Start warmup on the REQUESTER (they must stand still)
        int warmupSeconds = config.tpa.warmupSeconds;
        WarmupService warmupService = EliteEssentials.getInstance().getWarmupService();
        
        // Check if requester already has a warmup
        if (warmupService.hasActiveWarmup(request.getRequesterId())) {
            ctx.sendMessage(Message.raw(configManager.getMessage("tpaRequesterInProgress")).color("#FF5555"));
            return;
        }
        
        ctx.sendMessage(Message.raw(configManager.getMessage("tpaAccepted", "player", request.getRequesterName())).color("#55FF55"));
        
        if (warmupSeconds > 0) {
            requester.sendMessage(Message.raw(configManager.getMessage("tpaRequesterWarmup", "player", player.getUsername(), "seconds", String.valueOf(warmupSeconds))).color("#FFAA00"));
        }
        
        warmupService.startWarmup(requester, requesterPos, warmupSeconds, doTeleport, "tpa", 
                                   finalRequesterWorld, requesterStore, requesterRef);
    }
}
