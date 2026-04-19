package com.eliteessentials.commands.hytale;

import com.eliteessentials.EliteEssentials;
import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.gui.HomeSelectionPage;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.services.BackService;
import com.eliteessentials.services.HomeService;
import com.eliteessentials.util.CommandPermissionUtil;
import com.eliteessentials.util.MessageFormatter;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Set;
import java.util.UUID;

import javax.annotation.Nonnull;
import com.eliteessentials.util.CommandSpyUtil;

/**
 * Command: /homes
 * Opens a GUI showing all player homes for clicking to teleport.
 * 
 * Permissions:
 * - eliteessentials.command.homes.self - List own homes
 */
public class HytaleHomesCommand extends AbstractPlayerCommand {

    private static final String COMMAND_NAME = "homes";
    
    private final HomeService homeService;

    public HytaleHomesCommand(HomeService homeService) {
        super(COMMAND_NAME, "Open your homes menu");
        this.homeService = homeService;
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, 
                          @Nonnull PlayerRef player, @Nonnull World world) {
        CommandSpyUtil.notify(ctx);
        boolean enabled = EliteEssentials.getInstance().getConfigManager().getConfig().homes.enabled;
        if (!CommandPermissionUtil.canExecute(ctx, player, Permissions.HOMES, enabled)) {
            return;
        }
        
        ConfigManager configManager = EliteEssentials.getInstance().getConfigManager();
        UUID playerId = player.getUuid();
        Set<String> homes = homeService.getHomeNames(playerId);
        
        if (homes.isEmpty()) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("homeNoHomes"), "#FFAA00"));
            return;
        }

        // Get player component to open GUI
        Player playerEntity = store.getComponent(ref, Player.getComponentType());
        if (playerEntity == null) {
            ctx.sendMessage(MessageFormatter.formatWithFallback("&cCould not open homes menu.", "#FF5555"));
            return;
        }
        
        // Open the home selection GUI
        BackService backService = EliteEssentials.getInstance().getBackService();
        HomeSelectionPage page = new HomeSelectionPage(player, homeService, backService, configManager, world);
        playerEntity.getPageManager().openCustomPage(ref, store, page);
    }
}
