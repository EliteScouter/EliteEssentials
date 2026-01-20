package com.eliteessentials.commands.hytale;

import com.eliteessentials.EliteEssentials;
import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.config.PluginConfig;
import com.eliteessentials.model.Warp;
import com.eliteessentials.permissions.PermissionService;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.services.WarpService;
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

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

/**
 * Command: /warps
 * Lists all available warps for the player.
 * 
 * Permissions:
 * - eliteessentials.command.warps - List warps
 */
public class HytaleWarpsCommand extends AbstractPlayerCommand {

    private static final String COMMAND_NAME = "warps";
    
    private final WarpService warpService;

    public HytaleWarpsCommand(WarpService warpService) {
        super(COMMAND_NAME, "List all available warps");
        this.warpService = warpService;
        
        // Permission check handled in execute() via CommandPermissionUtil
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
        boolean isAdmin = perms.hasPermission(playerId, Permissions.ADMIN);
        
        List<Warp> accessibleWarps = warpService.getAllWarps().values().stream()
            .filter(w -> perms.canAccessWarp(playerId, w.getName(), w.getPermission()))
            .collect(Collectors.toList());
        
        if (accessibleWarps.isEmpty()) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("warpNoWarps"), "#FF5555"));
            return;
        }
        
        ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("warpListTitle"), "#55FFFF"));
        
        for (Warp warp : accessibleWarps) {
            String coords = String.format("%.0f, %.0f, %.0f", 
                warp.getLocation().getX(), 
                warp.getLocation().getY(), 
                warp.getLocation().getZ());
            
            Message line;
            if (isAdmin) {
                String permTag = warp.isOpOnly() ? " [OP]" : " [ALL]";
                line = Message.join(
                    Message.raw("  " + warp.getName()).color("#FFFFFF"),
                    Message.raw(permTag).color(warp.isOpOnly() ? "#FF5555" : "#55FF55"),
                    Message.raw(" - ").color("#AAAAAA"),
                    Message.raw(coords).color("#AAAAAA"),
                    Message.raw(" (" + warp.getLocation().getWorld() + ")").color("#555555")
                );
            } else {
                line = Message.join(
                    Message.raw("  " + warp.getName()).color("#FFFFFF"),
                    Message.raw(" - ").color("#AAAAAA"),
                    Message.raw(coords).color("#AAAAAA")
                );
            }
            ctx.sendMessage(line);
        }
        
        ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("warpListFooter"), "#AAAAAA"));
    }
}
