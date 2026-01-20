package com.eliteessentials.commands.hytale;

import com.eliteessentials.EliteEssentials;
import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.services.HomeService;
import com.eliteessentials.util.CommandPermissionUtil;
import com.eliteessentials.util.MessageFormatter;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Set;
import java.util.UUID;

import javax.annotation.Nonnull;

/**
 * Command: /homes
 * Lists all player homes.
 * 
 * Permissions:
 * - eliteessentials.command.homes.self - List own homes
 * - eliteessentials.command.homes.other - List other players' homes (admin)
 */
public class HytaleHomesCommand extends AbstractPlayerCommand {

    private static final String COMMAND_NAME = "homes";
    
    private final HomeService homeService;

    public HytaleHomesCommand(HomeService homeService) {
        super(COMMAND_NAME, "List your homes");
        this.homeService = homeService;
        
        // Permission check handled in execute() via CommandPermissionUtil
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, 
                          @Nonnull PlayerRef player, @Nonnull World world) {
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

        int count = homes.size();
        int max = homeService.getMaxHomes(playerId);
        String maxStr = max == Integer.MAX_VALUE ? "∞" : String.valueOf(max);
        
        ctx.sendMessage(Message.join(
            MessageFormatter.formatWithFallback(configManager.getMessage("homeListHeader", "count", String.valueOf(count), "max", maxStr), "#55FF55"),
            Message.raw(" ").color("#55FF55"),
            Message.raw(String.join(", ", homes)).color("#FFFFFF")
        ));
    }
}
