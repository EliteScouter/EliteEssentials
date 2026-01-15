package com.eliteessentials.commands.hytale;

import com.eliteessentials.EliteEssentials;
import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.services.HomeService;
import com.eliteessentials.util.CommandPermissionUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.UUID;

/**
 * Command: /delhome [name]
 * Deletes a saved home.
 * 
 * Permissions:
 * - eliteessentials.command.delhome.self - Delete own homes
 * - eliteessentials.command.delhome.other - Delete other players' homes (admin)
 */
public class HytaleDelHomeCommand extends AbstractPlayerCommand {

    private static final String COMMAND_NAME = "delhome";
    
    private final HomeService homeService;

    public HytaleDelHomeCommand(HomeService homeService) {
        super(COMMAND_NAME, "Delete your home");
        this.homeService = homeService;
        
        // Permission check handled in execute() via CommandPermissionUtil
        
        // Add variant with name argument
        addUsageVariant(new DelHomeWithNameCommand(homeService));
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref, 
                          PlayerRef player, World world) {
        boolean enabled = EliteEssentials.getInstance().getConfigManager().getConfig().homes.enabled;
        if (!CommandPermissionUtil.canExecute(ctx, player, Permissions.DELHOME, enabled)) {
            return;
        }
        
        deleteHome(ctx, player, "home", homeService);
    }
    
    static void deleteHome(CommandContext ctx, PlayerRef player, String homeName, HomeService homeService) {
        boolean enabled = EliteEssentials.getInstance().getConfigManager().getConfig().homes.enabled;
        if (!CommandPermissionUtil.canExecute(ctx, player, Permissions.DELHOME, enabled)) {
            return;
        }
        
        ConfigManager configManager = EliteEssentials.getInstance().getConfigManager();
        UUID playerId = player.getUuid();
        HomeService.Result result = homeService.deleteHome(playerId, homeName);

        switch (result) {
            case SUCCESS -> ctx.sendMessage(Message.raw(configManager.getMessage("homeDeleted", "name", homeName)).color("#55FF55"));
            case HOME_NOT_FOUND -> ctx.sendMessage(Message.raw(configManager.getMessage("homeNotFound", "name", homeName)).color("#FF5555"));
            default -> ctx.sendMessage(Message.raw(configManager.getMessage("homeDeleteFailed")).color("#FF5555"));
        }
    }
    
    /**
     * Variant: /delhome <name>
     */
    private static class DelHomeWithNameCommand extends AbstractPlayerCommand {
        private final HomeService homeService;
        private final RequiredArg<String> nameArg;
        
        DelHomeWithNameCommand(HomeService homeService) {
            super(COMMAND_NAME);
            this.homeService = homeService;
            this.nameArg = withRequiredArg("name", "Home name", ArgTypes.STRING);
            
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
            HytaleDelHomeCommand.deleteHome(ctx, player, homeName, homeService);
        }
    }
}
