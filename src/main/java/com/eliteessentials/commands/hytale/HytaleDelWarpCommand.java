package com.eliteessentials.commands.hytale;

import com.eliteessentials.EliteEssentials;
import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.config.PluginConfig;
import com.eliteessentials.permissions.Permissions;
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
 * 
 * Permissions:
 * - eliteessentials.command.delwarp - Delete warps (admin)
 */
public class HytaleDelWarpCommand extends AbstractPlayerCommand {

    private static final String COMMAND_NAME = "delwarp";
    
    private final WarpService warpService;
    private final RequiredArg<String> nameArg;

    public HytaleDelWarpCommand(WarpService warpService) {
        super(COMMAND_NAME, "Delete a warp (Admin)");
        this.warpService = warpService;
        this.nameArg = withRequiredArg("name", "Warp name to delete", ArgTypes.STRING);
        
        // Permission check handled in execute() via CommandPermissionUtil
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
        
        if (!CommandPermissionUtil.canExecuteAdmin(ctx, player, Permissions.DELWARP, config.warps.enabled)) {
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
