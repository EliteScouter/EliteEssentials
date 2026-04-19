package com.eliteessentials.commands.hytale;

import com.eliteessentials.EliteEssentials;
import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.model.Warp;
import com.eliteessentials.permissions.PermissionService;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.services.WarpService;
import com.eliteessentials.util.MessageFormatter;
import com.eliteessentials.commands.args.SimpleStringArg;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import com.eliteessentials.util.CommandSpyUtil;

/**
 * Command: /warpsetperm <name> <all|op>
 * Sets the permission level for a warp.
 */
public class HytaleWarpSetPermCommand extends AbstractPlayerCommand {

    private static final String COMMAND_NAME = "warpsetperm";
    
    private final WarpService warpService;
    private final RequiredArg<String> nameArg;
    private final RequiredArg<String> permArg;

    public HytaleWarpSetPermCommand(WarpService warpService) {
        super(COMMAND_NAME, "Set warp permission (Admin)");
        this.warpService = warpService;
        this.nameArg = withRequiredArg("name", "Warp name", SimpleStringArg.WARP_NAME);
        this.permArg = withRequiredArg("permission", "all or op", SimpleStringArg.PERMISSION);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                          @Nonnull PlayerRef player, @Nonnull World world) {
        CommandSpyUtil.notify(ctx);
        ConfigManager configManager = EliteEssentials.getInstance().getConfigManager();
        
        if (!PermissionService.get().canUseAdminCommand(ctx.sender(), Permissions.WARPADMIN, true)) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("noPermission"), "#FF5555"));
            return;
        }
        
        String warpName = ctx.get(nameArg);
        String permStr = ctx.get(permArg).toLowerCase();
        
        Warp.Permission permission;
        if ("op".equals(permStr) || "admin".equals(permStr)) {
            permission = Warp.Permission.OP;
        } else if ("all".equals(permStr) || "everyone".equals(permStr)) {
            permission = Warp.Permission.ALL;
        } else {
            ctx.sendMessage(Message.join(
                Message.raw("Invalid permission '").color("#FF5555"),
                Message.raw(permStr).color("#FFFFFF"),
                Message.raw("'. Use 'all' or 'op'.").color("#FF5555")
            ));
            return;
        }
        
        boolean updated = warpService.updateWarpPermission(warpName, permission);
        
        if (!updated) {
            ctx.sendMessage(Message.join(
                Message.raw("Warp '").color("#FF5555"),
                Message.raw(warpName).color("#FFFFFF"),
                Message.raw("' not found.").color("#FF5555")
            ));
            return;
        }
        
        String permDisplay = permission == Warp.Permission.OP ? "OP only" : "everyone";
        ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("warpAdminPermissionUpdated", "name", warpName, "permission", permDisplay), "#55FF55"));
    }
}
