package com.eliteessentials.commands.hytale;

import com.eliteessentials.EliteEssentials;
import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.config.PluginConfig;
import com.eliteessentials.model.Warp;
import com.eliteessentials.services.WarpService;
import com.eliteessentials.util.CommandPermissionUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Command: /warps
 * Lists all available warps for the player.
 * Admins see all warps with permission indicators.
 */
public class HytaleWarpsCommand extends AbstractPlayerCommand {

    private static final String ADMIN_PERMISSION = "eliteessentials.admin";
    
    private final WarpService warpService;

    public HytaleWarpsCommand(WarpService warpService) {
        super("warps", "List all available warps");
        this.warpService = warpService;
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref,
                          PlayerRef player, World world) {
        ConfigManager configManager = EliteEssentials.getInstance().getConfigManager();
        PluginConfig config = configManager.getConfig();
        if (!CommandPermissionUtil.canExecute(ctx, player, config.warps.enabled)) {
            return;
        }
        
        boolean isOp = ctx.sender() != null && ctx.sender().hasPermission(ADMIN_PERMISSION);
        List<Warp> accessibleWarps = warpService.getAccessibleWarps(isOp);
        
        if (accessibleWarps.isEmpty()) {
            ctx.sendMessage(Message.raw(configManager.getMessage("warpNoWarps")).color("#FF5555"));
            return;
        }
        
        ctx.sendMessage(Message.raw(configManager.getMessage("warpListTitle")).color("#55FFFF"));
        
        for (Warp warp : accessibleWarps) {
            String coords = String.format("%.0f, %.0f, %.0f", 
                warp.getLocation().getX(), 
                warp.getLocation().getY(), 
                warp.getLocation().getZ());
            
            Message line;
            if (isOp) {
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
        
        ctx.sendMessage(Message.raw(configManager.getMessage("warpListFooter")).color("#AAAAAA"));
    }
}
