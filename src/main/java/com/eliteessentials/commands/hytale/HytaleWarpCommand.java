package com.eliteessentials.commands.hytale;

import com.eliteessentials.EliteEssentials;
import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.config.PluginConfig;
import com.eliteessentials.gui.WarpSelectionPage;
import com.eliteessentials.model.Location;
import com.eliteessentials.model.Warp;
import com.eliteessentials.permissions.PermissionService;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.services.BackService;
import com.eliteessentials.services.CostService;
import com.eliteessentials.services.WarpService;
import com.eliteessentials.services.WarmupService;
import com.eliteessentials.util.CommandPermissionUtil;
import com.eliteessentials.util.MessageFormatter;
import com.eliteessentials.util.TeleportUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.eliteessentials.commands.args.SimpleStringArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

/**
 * Command: /warp [name|list]
 * - /warp - Opens GUI
 * - /warp <name> - Teleport to warp
 * - /warp list - Text list of warps
 */
public class HytaleWarpCommand extends AbstractPlayerCommand {

    private static final String COMMAND_NAME = "warp";
    
    private final WarpService warpService;
    private final BackService backService;

    public HytaleWarpCommand(WarpService warpService, BackService backService) {
        super(COMMAND_NAME, "Teleport to a warp location");
        this.warpService = warpService;
        this.backService = backService;
        
        addAliases("warps");
        addUsageVariant(new WarpWithNameCommand(warpService, backService));
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
        
        if (!CommandPermissionUtil.canExecute(ctx, player, Permissions.WARPS, config.warps.enabled)) {
            return;
        }
        
        UUID playerId = player.getUuid();
        PermissionService perms = PermissionService.get();
        
        // Check if there are any accessible warps
        boolean hasAccessibleWarps = false;
        for (Warp warp : warpService.getAllWarps().values()) {
            if (perms.canAccessWarp(playerId, warp.getName(), warp.getPermission())) {
                hasAccessibleWarps = true;
                break;
            }
        }
        
        if (!hasAccessibleWarps) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("warpNoWarps"), "#FF5555"));
            return;
        }
        
        // Open GUI
        Player playerEntity = store.getComponent(ref, Player.getComponentType());
        if (playerEntity == null) {
            ctx.sendMessage(MessageFormatter.formatWithFallback("&cCould not open warps menu.", "#FF5555"));
            return;
        }
        
        WarpSelectionPage page = new WarpSelectionPage(player, warpService, backService, configManager, world, ref, store);
        playerEntity.getPageManager().openCustomPage(ref, store, page);
    }


    /**
     * Variant: /warp <name> - handles both warp names and "list"
     */
    private static class WarpWithNameCommand extends AbstractPlayerCommand {
        private final WarpService warpService;
        private final BackService backService;
        private final RequiredArg<String> nameArg;
        
        WarpWithNameCommand(WarpService warpService, BackService backService) {
            super(COMMAND_NAME);
            this.warpService = warpService;
            this.backService = backService;
            this.nameArg = withRequiredArg("name", "Warp name or 'list'", SimpleStringArg.WARP_NAME);
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
            
            if (!CommandPermissionUtil.canExecute(ctx, player, Permissions.WARPS, config.warps.enabled)) {
                return;
            }
            
            String input = ctx.get(nameArg);
            
            // Handle /warp list
            if ("list".equalsIgnoreCase(input)) {
                showWarpList(ctx, player, configManager);
                return;
            }
            
            // Otherwise teleport to warp
            goToWarp(ctx, store, ref, player, world, input, warpService, backService, false);
        }
        
        private void showWarpList(CommandContext ctx, PlayerRef player, ConfigManager configManager) {
            UUID playerId = player.getUuid();
            PermissionService perms = PermissionService.get();
            boolean isAdmin = perms.isAdmin(playerId);
            
            List<Warp> accessibleWarps = warpService.getAllWarps().values().stream()
                .filter(w -> perms.canAccessWarp(playerId, w.getName(), w.getPermission()))
                .collect(Collectors.toList());
            
            if (accessibleWarps.isEmpty()) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("warpNoWarps"), "#FF5555"));
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
                MessageFormatter.formatWithFallback(configManager.getMessage("warpListHeader"), "#55FF55"),
                Message.raw(warpList).color("#FFFFFF")
            ));
        }
    }

    /**
     * Teleport a player to a warp location.
     * @param silent If true, suppresses SUCCESS messages only (errors still show for user feedback)
     */
    public static void goToWarp(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref,
                         PlayerRef player, World world, String warpName,
                         WarpService warpService, BackService backService, boolean silent) {
        ConfigManager configManager = EliteEssentials.getInstance().getConfigManager();
        PluginConfig config = configManager.getConfig();
        
        UUID playerId = player.getUuid();
        WarmupService warmupService = EliteEssentials.getInstance().getWarmupService();
        PermissionService perms = PermissionService.get();
        
        if (warmupService.hasActiveWarmup(playerId)) {
            // Always show this error - player needs to know why nothing happened
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("teleportInProgress"), "#FF5555"));
            return;
        }
        
        Optional<Warp> warpOpt = warpService.getWarp(warpName);
        
        if (warpOpt.isEmpty()) {
            // Always show warp not found - player needs feedback
            List<Warp> available = warpService.getAllWarps().values().stream()
                .filter(w -> perms.canAccessWarp(playerId, w.getName(), w.getPermission()))
                .collect(Collectors.toList());
            if (available.isEmpty()) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("warpNoWarps"), "#FF5555"));
            } else {
                String warpList = available.stream().map(Warp::getName).collect(Collectors.joining(", "));
                ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("warpNotFound", "name", warpName, "list", warpList), "#FF5555"));
            }
            return;
        }
        
        Warp warp = warpOpt.get();
        
        if (!perms.canAccessWarp(playerId, warp.getName(), warp.getPermission())) {
            // Always show permission error - critical feedback for user
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("warpNoPermission"), "#FF5555"));
            return;
        }
        
        CostService costService = EliteEssentials.getInstance().getCostService();
        double cost = config.warps.cost;
        // Cost check always shows errors (insufficient funds)
        if (costService != null && !costService.checkCanAfford(ctx, player, "warp", cost, false)) {
            return;
        }
        
        Location loc = warp.getLocation();
        
        TransformComponent transform = (TransformComponent) store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            // Always show position error
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("couldNotGetPosition"), "#FF5555"));
            return;
        }
        
        Vector3d currentPos = transform.getPosition();
        HeadRotation headRotation = (HeadRotation) store.getComponent(ref, HeadRotation.getComponentType());
        Vector3f rotation = headRotation != null ? headRotation.getRotation() : new Vector3f(0, 0, 0);
        
        Location currentLoc = new Location(
            world.getName(),
            currentPos.getX(), currentPos.getY(), currentPos.getZ(),
            rotation.y, rotation.x
        );
        
        World targetWorld = Universe.get().getWorld(loc.getWorld());
        if (targetWorld == null) {
            targetWorld = world;
        }
        final World finalWorld = targetWorld;
        final String finalWarpName = warp.getName();
        final CostService finalCostService = costService;
        final double finalCost = cost;
        final boolean finalSilent = silent;
        
        // For cross-world teleports, we must execute on the CURRENT world's thread
        // and use the CURRENT world's store, but include the TARGET world in the Teleport constructor.
        final boolean isCrossWorld = !world.getName().equals(finalWorld.getName());
        
        Runnable doTeleport = () -> {
            backService.pushLocation(playerId, currentLoc);
            
            Vector3d targetPos = new Vector3d(loc.getX(), loc.getY(), loc.getZ());
            Vector3f targetRot = new Vector3f(0, loc.getYaw(), 0);
            
            // Pre-load destination chunk before teleporting to prevent
            // "entity moved into unloaded chunk" crash
            TeleportUtil.safeTeleport(world, finalWorld, targetPos, targetRot, store, ref,
                () -> {
                    if (finalCostService != null) {
                        finalCostService.charge(ctx, player, "warp", finalCost);
                    }
                    
                    // Only suppress success message when silent
                    if (!finalSilent) {
                        player.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("warpTeleported", "name", finalWarpName), "#55FF55"));
                    }
                },
                () -> {
                    player.sendMessage(MessageFormatter.formatWithFallback("&cTeleport failed - destination chunk could not be loaded.", "#FF5555"));
                }
            );
        };

        int warmupSeconds = CommandPermissionUtil.getEffectiveWarmup(playerId, COMMAND_NAME, config.warps.warmupSeconds);
        
        // Only suppress warmup message when silent (countdown still shows via warmup service)
        if (warmupSeconds > 0 && !silent) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("warpWarmup", "name", finalWarpName, "seconds", String.valueOf(warmupSeconds)), "#FFAA00"));
        }
        warmupService.startWarmup(player, currentPos, warmupSeconds, doTeleport, COMMAND_NAME, world, store, ref, false);
    }
}
