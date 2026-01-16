package com.eliteessentials.commands.hytale;

import com.eliteessentials.EliteEssentials;
import com.eliteessentials.commands.args.SimpleStringArg;
import com.eliteessentials.permissions.PermissionService;
import com.eliteessentials.permissions.Permissions;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;

/**
 * Command: /eliteessentials reload
 * Reloads the plugin configuration.
 * 
 * Permissions:
 * - Admin only (simple mode)
 * - eliteessentials.admin.reload (advanced mode)
 */
public class HytaleReloadCommand extends CommandBase {

    private final RequiredArg<String> actionArg;

    public HytaleReloadCommand() {
        super("eliteessentials", "EliteEssentials admin commands");
        
        // Add /ee as a short alias
        addAliases("ee");
        
        // Permission check handled in executeSync()
        
        this.actionArg = withRequiredArg("action", "Action to perform", SimpleStringArg.ACTION);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void executeSync(CommandContext ctx) {
        // Check admin permission
        PermissionService perms = PermissionService.get();
        if (!perms.canUseAdminCommand(ctx.sender(), Permissions.ADMIN_RELOAD, true)) {
            ctx.sendMessage(Message.raw(EliteEssentials.getInstance().getConfigManager().getMessage("noPermission")).color("#FF5555"));
            return;
        }
        
        String action = ctx.get(actionArg);
        
        if ("reload".equalsIgnoreCase(action)) {
            try {
                EliteEssentials.getInstance().reloadConfig();
                ctx.sendMessage(Message.raw("EliteEssentials configuration reloaded!").color("#55FF55"));
            } catch (Exception e) {
                ctx.sendMessage(Message.raw("Failed to reload configuration: " + e.getMessage()).color("#FF5555"));
            }
        } else {
            ctx.sendMessage(Message.raw("Unknown action. Usage: /eliteessentials reload").color("#FF5555"));
        }
    }
}
