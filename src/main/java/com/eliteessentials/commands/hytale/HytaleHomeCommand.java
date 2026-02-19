package com.eliteessentials.commands.hytale;

import com.eliteessentials.EliteEssentials;
import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.config.PluginConfig;
import com.eliteessentials.model.Home;
import com.eliteessentials.model.Location;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.services.BackService;
import com.eliteessentials.services.HomeService;
import com.eliteessentials.services.WarmupService;
import com.eliteessentials.gui.HomeSelectionPage;
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

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nonnull;

/**
 * Command: /home [name]
 * Teleports the player to their saved home location.
 * 
 * Permissions:
 * - eliteessentials.command.home.self - Teleport to own homes
 * - eliteessentials.bypass.warmup.home - Skip warmup
 * - eliteessentials.bypass.cooldown.home - Skip cooldown
 */
public class HytaleHomeCommand extends AbstractPlayerCommand {

    private static final String COMMAND_NAME = "home";
    
    private final HomeService homeService;
    private final BackService backService;

    public HytaleHomeCommand(HomeService homeService, BackService backService) {
        super(COMMAND_NAME, "Teleport to your home location");
        this.homeService = homeService;
        this.backService = backService;
        
        // Permission check handled in execute() via CommandPermissionUtil
        
        addUsageVariant(new HomeWithNameCommand(homeService, backService));
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, 
                          @Nonnull PlayerRef player, @Nonnull World world) {
        PluginConfig config = EliteEssentials.getInstance().getConfigManager().getConfig();
        if (!CommandPermissionUtil.canExecute(ctx, player, Permissions.HOME, config.homes.enabled)) {
            return;
        }
        Set<String> homes = homeService.getHomeNames(player.getUuid());
        if (homes.size() > 1) {
            Player playerEntity = store.getComponent(ref, Player.getComponentType());
            if (playerEntity == null) {
                ctx.sendMessage(MessageFormatter.formatWithFallback("&cCould not open homes menu.", "#FF5555"));
                return;
            }
            ConfigManager configManager = EliteEssentials.getInstance().getConfigManager();
            HomeSelectionPage page = new HomeSelectionPage(player, homeService, backService, configManager, world);
            playerEntity.getPageManager().openCustomPage(ref, store, page);
            return;
        }
        goHome(ctx, store, ref, player, world, "home", homeService, backService);
    }

    public static void goHome(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref,
                       PlayerRef player, World world, String homeName,
                       HomeService homeService, BackService backService) {
        goHome(ctx, store, ref, player, world, homeName, homeService, backService, false);
    }
    
    public static void goHome(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref,
                       PlayerRef player, World world, String homeName,
                       HomeService homeService, BackService backService, boolean silent) {
        PluginConfig config = EliteEssentials.getInstance().getConfigManager().getConfig();
        if (!CommandPermissionUtil.canExecuteWithCost(ctx, player, Permissions.HOME, 
                config.homes.enabled, "home", config.homes.cost)) {
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
        
        Optional<Home> homeOpt = homeService.getHome(playerId, homeName);
        
        if (homeOpt.isEmpty()) {
            Set<String> homes = homeService.getHomeNames(playerId);
            if (homes.isEmpty()) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("homeNoHomeSet"), "#FF5555"));
                return;
            }
            ctx.sendMessage(Message.join(
                MessageFormatter.formatWithFallback(configManager.getMessage("homeNotFound", "name", homeName), "#FF5555"),
                Message.raw(" Your homes: ").color("#FF5555"),
                Message.raw(String.join(", ", homes)).color("#FFFFFF")
            ));
            return;
        }

        Home home = homeOpt.get();
        Location loc = home.getLocation();

        // Get current position for warmup and /back
        TransformComponent transform = (TransformComponent) store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("couldNotGetPosition"), "#FF5555"));
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
        final String finalHomeName = homeName;
        final boolean finalSilent = silent;

        // Define the teleport action - always use PlayerRef to get fresh refs at teleport time
        Runnable doTeleport = () -> {
            backService.pushLocation(playerId, currentLoc);
            
            Vector3d targetPos = new Vector3d(loc.getX(), loc.getY(), loc.getZ());
            // Always use pitch=0 to keep player upright, preserve yaw for direction
            Vector3f targetRot = new Vector3f(0, loc.getYaw(), 0);
            
            TeleportUtil.safeTeleport(world, finalWorld, targetPos, targetRot, player,
                () -> {
                    CommandPermissionUtil.chargeCost(ctx, player, "home", config.homes.cost);
                    if (!finalSilent) {
                        player.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("homeTeleported", "name", finalHomeName), "#55FF55"));
                    }
                },
                () -> {
                    player.sendMessage(MessageFormatter.formatWithFallback("&cTeleport failed - destination chunk could not be loaded.", "#FF5555"));
                }
            );
        };

        // Get effective warmup (check bypass permission)
        int warmupSeconds = CommandPermissionUtil.getEffectiveWarmup(playerId, COMMAND_NAME, config.homes.warmupSeconds);
        
        if (warmupSeconds > 0 && !silent) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("homeWarmup", "name", finalHomeName, "seconds", String.valueOf(warmupSeconds)), "#FFAA00"));
        }
        // Pass false for warmup silent - we want countdown messages to show
        warmupService.startWarmup(player, currentPos, warmupSeconds, doTeleport, COMMAND_NAME, world, store, ref, false);
    }
    
    /**
     * Variant: /home <name>
     */
    private static class HomeWithNameCommand extends AbstractPlayerCommand {
        private final HomeService homeService;
        private final BackService backService;
        private final RequiredArg<String> nameArg;
        
        HomeWithNameCommand(HomeService homeService, BackService backService) {
            super(COMMAND_NAME);
            this.homeService = homeService;
            this.backService = backService;
            this.nameArg = withRequiredArg("name", "Home name", SimpleStringArg.HOME_NAME);
            
            // Permission check handled in execute() via CommandPermissionUtil
        }
        
        @Override
        protected boolean canGeneratePermission() {
            return false;
        }
        
        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                              @Nonnull PlayerRef player, @Nonnull World world) {
            String homeName = ctx.get(nameArg);
            HytaleHomeCommand.goHome(ctx, store, ref, player, world, homeName, homeService, backService);
        }
    }
}
