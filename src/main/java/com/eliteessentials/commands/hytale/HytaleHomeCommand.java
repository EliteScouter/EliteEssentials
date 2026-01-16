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

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

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
    protected void execute(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref, 
                          PlayerRef player, World world) {
        PluginConfig config = EliteEssentials.getInstance().getConfigManager().getConfig();
        if (!CommandPermissionUtil.canExecute(ctx, player, Permissions.HOME, config.homes.enabled)) {
            return;
        }
        goHome(ctx, store, ref, player, world, "home", homeService, backService);
    }

    static void goHome(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref,
                       PlayerRef player, World world, String homeName,
                       HomeService homeService, BackService backService) {
        PluginConfig config = EliteEssentials.getInstance().getConfigManager().getConfig();
        if (!CommandPermissionUtil.canExecute(ctx, player, Permissions.HOME, config.homes.enabled)) {
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
        
        Optional<Home> homeOpt = homeService.getHome(playerId, homeName);
        
        if (homeOpt.isEmpty()) {
            Set<String> homes = homeService.getHomeNames(playerId);
            if (homes.isEmpty()) {
                ctx.sendMessage(Message.raw(configManager.getMessage("homeNoHomeSet")).color("#FF5555"));
                return;
            }
            ctx.sendMessage(Message.join(
                Message.raw(configManager.getMessage("homeNotFound", "name", homeName)).color("#FF5555"),
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
            ctx.sendMessage(Message.raw(configManager.getMessage("couldNotGetPosition")).color("#FF5555"));
            return;
        }
        
        Vector3d currentPos = transform.getPosition();
        HeadRotation headRotation = (HeadRotation) store.getComponent(ref, HeadRotation.getComponentType());
        Vector3f rotation = headRotation != null ? headRotation.getRotation() : new Vector3f(0, 0, 0);
        
        Location currentLoc = new Location(
            world.getName(),
            currentPos.getX(), currentPos.getY(), currentPos.getZ(),
            rotation.getYaw(), rotation.getPitch()
        );

        // Get target world
        World targetWorld = Universe.get().getWorld(loc.getWorld());
        if (targetWorld == null) {
            targetWorld = world;
        }
        final World finalWorld = targetWorld;
        final String finalHomeName = homeName;

        // Define the teleport action
        Runnable doTeleport = () -> {
            backService.pushLocation(playerId, currentLoc);
            
            world.execute(() -> {
                Vector3d targetPos = new Vector3d(loc.getX(), loc.getY(), loc.getZ());
                Vector3f targetRot = new Vector3f(loc.getPitch(), loc.getYaw(), 0);
                
                Teleport teleport = new Teleport(finalWorld, targetPos, targetRot);
                store.putComponent(ref, Teleport.getComponentType(), teleport);
                
                ctx.sendMessage(Message.raw(configManager.getMessage("homeTeleported", "name", finalHomeName)).color("#55FF55"));
            });
        };

        // Get effective warmup (check bypass permission)
        int warmupSeconds = CommandPermissionUtil.getEffectiveWarmup(playerId, COMMAND_NAME, config.homes.warmupSeconds);
        
        if (warmupSeconds > 0) {
            ctx.sendMessage(Message.raw(configManager.getMessage("homeWarmup", "name", finalHomeName, "seconds", String.valueOf(warmupSeconds))).color("#FFAA00"));
        }
        warmupService.startWarmup(player, currentPos, warmupSeconds, doTeleport, COMMAND_NAME, world, store, ref);
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
        protected void execute(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref,
                              PlayerRef player, World world) {
            String homeName = ctx.get(nameArg);
            HytaleHomeCommand.goHome(ctx, store, ref, player, world, homeName, homeService, backService);
        }
    }
}
