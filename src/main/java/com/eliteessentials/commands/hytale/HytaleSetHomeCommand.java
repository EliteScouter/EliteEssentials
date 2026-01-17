package com.eliteessentials.commands.hytale;

import com.eliteessentials.EliteEssentials;
import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.model.Location;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.services.HomeService;
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
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.UUID;

/**
 * Command: /sethome [name]
 * Sets a home at the player's current location.
 * 
 * Permissions:
 * - eliteessentials.command.sethome.self - Set own homes
 * - eliteessentials.limit.homes.<number> - Max homes allowed
 * - eliteessentials.limit.homes.unlimited - Unlimited homes
 */
public class HytaleSetHomeCommand extends AbstractPlayerCommand {

    private static final String COMMAND_NAME = "sethome";
    
    private final HomeService homeService;

    public HytaleSetHomeCommand(HomeService homeService) {
        super(COMMAND_NAME, "Set your home location");
        this.homeService = homeService;
        
        // Permission check handled in execute() via CommandPermissionUtil
        
        // Add variant that accepts a name argument
        addUsageVariant(new SetHomeWithNameCommand(homeService));
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref, 
                          PlayerRef player, World world) {
        boolean enabled = EliteEssentials.getInstance().getConfigManager().getConfig().homes.enabled;
        if (!CommandPermissionUtil.canExecute(ctx, player, Permissions.SETHOME, enabled)) {
            return;
        }
        
        // Default: use "home" as name
        setHome(ctx, store, ref, player, world, "home");
    }
    
    static void setHome(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref,
                        PlayerRef player, World world, String homeName, HomeService homeService) {
        boolean enabled = EliteEssentials.getInstance().getConfigManager().getConfig().homes.enabled;
        if (!CommandPermissionUtil.canExecute(ctx, player, Permissions.SETHOME, enabled)) {
            return;
        }
        
        ConfigManager configManager = EliteEssentials.getInstance().getConfigManager();
        
        TransformComponent transform = (TransformComponent) store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            ctx.sendMessage(Message.raw(configManager.getMessage("couldNotGetPosition")).color("#FF5555"));
            return;
        }

        HeadRotation headRotation = (HeadRotation) store.getComponent(ref, HeadRotation.getComponentType());
        Vector3f rotation = headRotation != null ? headRotation.getRotation() : new Vector3f(0, 0, 0);
        
        Vector3d position = transform.getPosition();
        UUID playerId = player.getUuid();

        Location location = new Location(
            world.getName(),
            position.getX(),
            position.getY(),
            position.getZ(),
            rotation.y,  // yaw=rotation.y
            rotation.x   // pitch=rotation.x
        );

        HomeService.Result result = homeService.setHome(playerId, homeName, location);

        switch (result) {
            case SUCCESS -> ctx.sendMessage(Message.raw(configManager.getMessage("homeSet", "name", homeName)).color("#55FF55"));
            case LIMIT_REACHED -> {
                int max = homeService.getMaxHomes(playerId);
                ctx.sendMessage(Message.raw(configManager.getMessage("homeLimitReached", "max", String.valueOf(max))).color("#FF5555"));
            }
            case INVALID_NAME -> ctx.sendMessage(Message.raw(configManager.getMessage("homeInvalidName")).color("#FF5555"));
            default -> ctx.sendMessage(Message.raw(configManager.getMessage("homeSetFailed")).color("#FF5555"));
        }
    }
    
    private void setHome(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref,
                         PlayerRef player, World world, String homeName) {
        setHome(ctx, store, ref, player, world, homeName, homeService);
    }
    
    /**
     * Variant: /sethome <name>
     */
    private static class SetHomeWithNameCommand extends AbstractPlayerCommand {
        private final HomeService homeService;
        private final RequiredArg<String> nameArg;
        
        SetHomeWithNameCommand(HomeService homeService) {
            super(COMMAND_NAME);
            this.homeService = homeService;
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
            HytaleSetHomeCommand.setHome(ctx, store, ref, player, world, homeName, homeService);
        }
    }
}
