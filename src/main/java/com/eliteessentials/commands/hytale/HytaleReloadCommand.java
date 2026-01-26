package com.eliteessentials.commands.hytale;

import com.eliteessentials.EliteEssentials;
import com.eliteessentials.commands.args.SimpleStringArg;
import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.permissions.PermissionService;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.services.EssentialsCoreMigrationService;
import com.eliteessentials.util.MessageFormatter;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;

import javax.annotation.Nonnull;

/**
 * Command: /eliteessentials <action>
 * Admin commands for EliteEssentials.
 * 
 * Actions:
 * - reload: Reload configuration
 * - migration essentialscore: Migrate data from nhulston's EssentialsCore
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
        
        this.actionArg = withRequiredArg("action", "Action to perform (reload, migration)", SimpleStringArg.ACTION);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void executeSync(@Nonnull CommandContext ctx) {
        // Check admin permission
        PermissionService perms = PermissionService.get();
        if (!perms.canUseAdminCommand(ctx.sender(), Permissions.ADMIN_RELOAD, true)) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(EliteEssentials.getInstance().getConfigManager().getMessage("noPermission"), "#FF5555"));
            return;
        }
        
        String action = ctx.get(actionArg);
        
        if ("reload".equalsIgnoreCase(action)) {
            handleReload(ctx);
        } else if ("migration".equalsIgnoreCase(action)) {
            ctx.sendMessage(Message.raw("Usage: /ee migration <essentialscore|hyssentials>").color("#FFAA00"));
            ctx.sendMessage(Message.raw("  essentialscore - Import warps, kits, and homes from nhulston's EssentialsCore").color("#AAAAAA"));
            ctx.sendMessage(Message.raw("  hyssentials - Import homes and warps from Hyssentials").color("#AAAAAA"));
        } else {
            ctx.sendMessage(Message.raw("Unknown action. Available: reload, migration").color("#FF5555"));
        }
    }
    
    private void handleReload(CommandContext ctx) {
        ConfigManager configManager = EliteEssentials.getInstance().getConfigManager();
        
        // Validate all JSON files before reloading
        java.util.List<ConfigManager.ConfigValidationResult> errors = configManager.validateAllFiles();
        
        if (!errors.isEmpty()) {
            ctx.sendMessage(Message.raw("Config reload failed - invalid JSON detected!").color("#FF5555"));
            for (ConfigManager.ConfigValidationResult error : errors) {
                ctx.sendMessage(Message.raw("File: " + error.getFilename()).color("#FFAA00"));
                ctx.sendMessage(Message.raw(error.getErrorMessage()).color("#FF7777"));
            }
            return;
        }
        
        try {
            EliteEssentials.getInstance().reloadConfig();
            ctx.sendMessage(Message.raw("EliteEssentials configuration reloaded!").color("#55FF55"));
        } catch (Exception e) {
            ctx.sendMessage(Message.raw("Failed to reload configuration: " + e.getMessage()).color("#FF5555"));
        }
    }
}
