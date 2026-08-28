package com.eliteessentials.commands.hytale;

import com.eliteessentials.EliteEssentials;
import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.permissions.PermissionService;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.services.WarpService;
import com.eliteessentials.util.MessageFormatter;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.eliteessentials.commands.base.EliteCommandBase;

import javax.annotation.Nonnull;
import com.eliteessentials.util.CommandSpyUtil;

/**
 * Command: /warpsetdesc <name> <description...>
 * Sets the description for a warp. Description can contain spaces.
 */
public class HytaleWarpSetDescCommand extends EliteCommandBase {

    private static final String COMMAND_NAME = "warpsetdesc";
    
    private final WarpService warpService;

    public HytaleWarpSetDescCommand(WarpService warpService) {
        super(COMMAND_NAME, "Set warp description (Admin)");
        this.warpService = warpService;
        
        // Allow extra arguments to capture full description with spaces
        setAllowsExtraArguments(true);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void executeSync(@Nonnull CommandContext ctx) {
        CommandSpyUtil.notify(ctx);
        ConfigManager configManager = EliteEssentials.getInstance().getConfigManager();
        
        if (!PermissionService.get().canUseAdminCommand(ctx.sender(), Permissions.WARPADMIN, true)) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("noPermission"), "#FF5555"));
            return;
        }
        
        // Parse raw input: "/warpsetdesc <name> <description...>"
        String rawInput = ctx.getInputString();
        String[] parts = rawInput.split("\\s+", 3); // Split into: command, name, description
        
        if (parts.length < 2) {
            ctx.sendMessage(Message.raw("Usage: /warpsetdesc <name> <description>").color("#FF5555"));
            return;
        }
        
        String warpName = parts[1];
        String description = parts.length >= 3 ? parts[2] : "";
        
        if (description.trim().isEmpty()) {
            ctx.sendMessage(Message.raw("Usage: /warpsetdesc <name> <description>").color("#FF5555"));
            return;
        }
        
        boolean updated = warpService.updateWarpDescription(warpName, description);
        
        if (!updated) {
            ctx.sendMessage(Message.join(
                Message.raw("Warp '").color("#FF5555"),
                Message.raw(warpName).color("#FFFFFF"),
                Message.raw("' not found.").color("#FF5555")
            ));
            return;
        }
        
        ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("warpAdminDescriptionUpdated", "name", warpName, "description", description), "#55FF55"));
    }
}
