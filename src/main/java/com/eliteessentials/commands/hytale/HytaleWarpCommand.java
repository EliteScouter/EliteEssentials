package com.eliteessentials.commands.hytale;

import com.eliteessentials.EliteEssentials;
import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.config.PluginConfig;
import com.eliteessentials.model.Location;
import com.eliteessentials.model.Warp;
import com.eliteessentials.permissions.PermissionService;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.services.BackService;
import com.eliteessentials.services.WarpService;
import com.eliteessentials.services.WarmupService;
import com.eliteessentials.util.CommandPermissionUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.eliteessentials.commands.args.SimpleStringArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Command: /warp [name]
 * Teleports the player to a server warp location.
 * Without arguments, lists available warps.
 * 
 * Permissions:
 * - eliteessentials.command.warp - Use /warp command
 * - eliteessentials.warp.<name> - Access specific warp (for custom permission warps)
 * - eliteessentials.bypass.warmup.warp - Skip warmup
 * - eliteessentials.bypass.cooldown.warp - Skip cooldown
 */
public class HytaleWarpCommand extends AbstractPlayerCommand {

    private static final String COMMAND_NAME = "warp";
    
    private final WarpService warpService;
    private final BackService backService;

    public HytaleWarpCommand(WarpService warpService, BackService backService) {
        super(COMMAND_NAME, "Teleport to a warp location");
        this.warpService = warpService;
        this.backService = backService;
        
        // Permission check handled in execute() via CommandPermissionUtil
        
        addUsageVariant(new WarpWithNameCommand(warpService, backService));
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref,
                          PlayerRef player, World world) {
        PluginConfig config = EliteEssentials.getInstance().getConfigManager().getConfig();
        ConfigManager configManager = EliteEssentials.getInstance().getConfigManager();
        if (!CommandPermissionUtil.canExecute(ctx, player, Permissions.WARP, config.warps.enabled)) {
            return;
        }
        
        UUID playerId = player.getUuid();
        PermissionService perms = PermissionService.get();
        
        // /warp with no args - list available warps
        boolean isAdmin = perms.hasPermission(playerId, Permissions.ADMIN);
        List<Warp> accessibleWarps = warpService.getAllWarps().values().stream()
            .filter(w -> perms.canAccessWarp(playerId, w.getName(), w.getPermission()))
            .collect(Collectors.toList());
        
        if (accessibleWarps.isEmpty()) {
            ctx.sendMessage(Message.raw(configManager.getMessage("warpNoWarps")).color("#FF5555"));
            return;
        }
        
        String warpList = accessibleWarps.stream()
                .map(w -> {
                    String name = w.getName();
                    if (isAdmin && w.isOpOnly()) {
                        return name + " (OP)";
                    }
                    return name;
                })
                .collect(Collectors.joining(", "));
        
        ctx.sendMessage(Message.join(
            Message.raw(configManager.getMessage("warpListHeader")).color("#55FF55"),
            Message.raw(warpList).color("#FFFFFF")
        ));
    }

    static void goToWarp(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref,
                         PlayerRef player, World world, String warpName,
                         WarpService warpService, BackService backService) {
        PluginConfig config = EliteEssentials.getInstance().getConfigManager().getConfig();
        ConfigManager configManager = EliteEssentials.getInstance().getConfigManager();
        if (!CommandPermissionUtil.canExecute(ctx, player, Permissions.WARP, config.warps.enabled)) {
            return;
        }
        
        UUID playerId = player.getUuid();
        WarmupService warmupService = EliteEssentials.getInstance().getWarmupService();
        PermissionService perms = PermissionService.get();
        
        // Check if already warming up
        if (warmupService.hasActiveWarmup(playerId)) {
            ctx.sendMessage(Message.raw(configManager.getMessage("teleportInProgress")).color("#FF5555"));
            return;
        }
        
        Optional<Warp> warpOpt = warpService.getWarp(warpName);
        
        if (warpOpt.isEmpty()) {
            List<Warp> available = warpService.getAllWarps().values().stream()
                .filter(w -> perms.canAccessWarp(playerId, w.getName(), w.getPermission()))
                .collect(Collectors.toList());
            if (available.isEmpty()) {
                ctx.sendMessage(Message.raw(configManager.getMessage("warpNoWarps")).color("#FF5555"));
            } else {
                String warpList = available.stream().map(Warp::getName).collect(Collectors.joining(", "));
                ctx.sendMessage(Message.raw(configManager.getMessage("warpNotFound", "name", warpName, "list", warpList)).color("#FF5555"));
            }
            return;
        }
        
        Warp warp = warpOpt.get();
        
        // Check permission using PermissionService
        if (!perms.canAccessWarp(playerId, warp.getName(), warp.getPermission())) {
            ctx.sendMessage(Message.raw(configManager.getMessage("warpNoPermission")).color("#FF5555"));
            return;
        }
        
        Location loc = warp.getLocation();
        
        // Get current position for warmup and /back
        TransformComponent transform = (TransformComponent) store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            ctx.sendMessage(Message.raw(configManager.getMessage("couldNotGetPosition")).color("#FF5555"));
            return;
        }
        
        Vector3d currentPos = transform.getPosition();
        HeadRotation headRotation = (HeadRotation) store.getComponent(ref, HeadRotation.getComponentType());
        Vector3f rotation = headRotation != null ? headRotation.getRotation() : new Vector3f(0, 0, 0);
        
        Location currentLoc = new Location(
            world.getName(),
            currentPos.getX(), currentPos.getY(), currentPos.getZ(),
            rotation.y, rotation.x  // yaw=rotation.y, pitch=rotation.x
        );
        
        // Get target world
        World targetWorld = Universe.get().getWorld(loc.getWorld());
        if (targetWorld == null) {
            targetWorld = world;
        }
        final World finalWorld = targetWorld;
        final String finalWarpName = warp.getName();
        
        // Define the teleport action
        Runnable doTeleport = () -> {
            backService.pushLocation(playerId, currentLoc);
            
            world.execute(() -> {
                Vector3d targetPos = new Vector3d(loc.getX(), loc.getY(), loc.getZ());
                Vector3f targetRot = new Vector3f(loc.getPitch(), loc.getYaw(), 0);  // x=pitch, y=yaw, z=roll
                
                Teleport teleport = new Teleport(finalWorld, targetPos, targetRot);
                store.putComponent(ref, Teleport.getComponentType(), teleport);
                
                ctx.sendMessage(Message.raw(configManager.getMessage("warpTeleported", "name", finalWarpName)).color("#55FF55"));
            });
        };

        // Get effective warmup (check bypass permission)
        int warmupSeconds = CommandPermissionUtil.getEffectiveWarmup(playerId, COMMAND_NAME, config.warps.warmupSeconds);
        
        if (warmupSeconds > 0) {
            ctx.sendMessage(Message.raw(configManager.getMessage("warpWarmup", "name", finalWarpName, "seconds", String.valueOf(warmupSeconds))).color("#FFAA00"));
        }
        warmupService.startWarmup(player, currentPos, warmupSeconds, doTeleport, COMMAND_NAME, world, store, ref);
    }
    
    /**
     * Variant: /warp <name>
     */
    private static class WarpWithNameCommand extends AbstractPlayerCommand {
        private final WarpService warpService;
        private final BackService backService;
        private final RequiredArg<String> nameArg;
        
        WarpWithNameCommand(WarpService warpService, BackService backService) {
            super(COMMAND_NAME);
            this.warpService = warpService;
            this.backService = backService;
            this.nameArg = withRequiredArg("name", "Warp name", SimpleStringArg.WARP_NAME);
            
            // Permission check handled in execute() via CommandPermissionUtil
        }
        
        @Override
        protected boolean canGeneratePermission() {
            return false;
        }
        
        @Override
        protected void execute(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref,
                              PlayerRef player, World world) {
            String warpName = ctx.get(nameArg);
            HytaleWarpCommand.goToWarp(ctx, store, ref, player, world, warpName, warpService, backService);
        }
    }
}
