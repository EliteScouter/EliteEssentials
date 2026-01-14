package com.eliteessentials.commands.hytale;

import com.eliteessentials.EliteEssentials;
import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.config.PluginConfig;
import com.eliteessentials.services.WarpService;
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

/**
 * Command: /delwarp <name>
 * Deletes a server warp.
 * Admin only command.
 */
public class HytaleDelWarpCommand extends AbstractPlayerCommand {

    private static final String ADMIN_PERMISSION = "eliteessentials.admin";
    
    private final WarpService warpService;
    private final RequiredArg<String> nameArg;

    public HytaleDelWarpCommand(WarpService warpService) {
        super("delwarp", "Delete a warp (Admin)");
        this.warpService = warpService;
        this.nameArg = withRequiredArg("name", "Warp name to delete", ArgTypes.STRING);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref,
                          PlayerRef player, World world) {
        ConfigManager configManager = EliteEssentials.getInstance().getConfigManager();
        
        // Admin only command
        if (ctx.sender() == null || !ctx.sender().hasPermission(ADMIN_PERMISSION)) {
            ctx.sendMessage(Message.raw(configManager.getMessage("noPermission")).color("#FF5555"));
            return;
        }
        
        PluginConfig config = configManager.getConfig();
        if (!CommandPermissionUtil.canExecute(ctx, player, config.warps.enabled)) {
            return;
        }
        
        String warpName = ctx.get(nameArg);
        
        if (!warpService.warpExists(warpName)) {
            ctx.sendMessage(Message.raw(configManager.getMessage("warpNotFound", "name", warpName, "list", "")).color("#FF5555"));
            return;
        }
        
        boolean deleted = warpService.deleteWarp(warpName);
        
        if (deleted) {
            ctx.sendMessage(Message.raw(configManager.getMessage("warpDeleted", "name", warpName)).color("#55FF55"));
        } else {
            ctx.sendMessage(Message.raw(configManager.getMessage("warpDeleteFailed")).color("#FF5555"));
        }
    }
}
