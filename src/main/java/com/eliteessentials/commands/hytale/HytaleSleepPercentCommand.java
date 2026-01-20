package com.eliteessentials.commands.hytale;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.util.CommandPermissionUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.eliteessentials.commands.args.SimpleIntArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * Command: /sleeppercent <percentage>
 * Admin command to set the percentage of players needed to sleep to skip night.
 * 
 * Permissions:
 * - eliteessentials.command.sleeppercent - Use this command (admin only by default)
 */
public class HytaleSleepPercentCommand extends AbstractPlayerCommand {

    private static final String COMMAND_NAME = "sleeppercent";
    
    private final ConfigManager configManager;

    public HytaleSleepPercentCommand(ConfigManager configManager) {
        super(COMMAND_NAME, "Set sleep percentage to skip night");
        this.configManager = configManager;
        
        // Permission check handled in execute() via CommandPermissionUtil
        
        // Add variant with percentage argument
        addUsageVariant(new SetPercentCommand(configManager));
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, 
                          @Nonnull PlayerRef player, @Nonnull World world) {
        // Check admin permission
        boolean enabled = configManager.getConfig().sleep.enabled;
        if (!CommandPermissionUtil.canExecuteAdmin(ctx, player, Permissions.SLEEPPERCENT, enabled)) {
            return;
        }
        
        // Show current percentage
        int current = configManager.getConfig().sleep.sleepPercentage;
        ctx.sendMessage(Message.join(
            Message.raw("Current sleep percentage: ").color("#55FF55"),
            Message.raw(current + "%").color("#FFFFFF"),
            Message.raw(" of players must sleep to skip night.").color("#55FF55")
        ));
        ctx.sendMessage(Message.raw("Usage: /sleeppercent <0-100>").color("#AAAAAA"));
    }
    
    /**
     * Variant: /sleeppercent <percentage>
     */
    private static class SetPercentCommand extends AbstractPlayerCommand {
        private final ConfigManager configManager;
        private final RequiredArg<Integer> percentArg;
        
        SetPercentCommand(ConfigManager configManager) {
            super(COMMAND_NAME);
            this.configManager = configManager;
            this.percentArg = withRequiredArg("percentage", "Percentage (0-100)", SimpleIntArg.PERCENTAGE);
            
            // Permission check handled in execute() via CommandPermissionUtil
        }
        
        @Override
        protected boolean canGeneratePermission() {
            return false;
        }
        
        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                              @Nonnull PlayerRef player, @Nonnull World world) {
            // Check admin permission
            boolean enabled = configManager.getConfig().sleep.enabled;
            if (!CommandPermissionUtil.canExecuteAdmin(ctx, player, Permissions.SLEEPPERCENT, enabled)) {
                return;
            }
            
            int percent = ctx.get(percentArg);
            
            // Validate range
            if (percent < 0 || percent > 100) {
                ctx.sendMessage(Message.raw("Percentage must be between 0 and 100.").color("#FF5555"));
                return;
            }
            
            // Update config
            configManager.getConfig().sleep.sleepPercentage = percent;
            configManager.saveConfig();
            
            ctx.sendMessage(Message.join(
                Message.raw("Sleep percentage set to ").color("#55FF55"),
                Message.raw(percent + "%").color("#FFFFFF"),
                Message.raw(". Night will skip when this many players are sleeping.").color("#55FF55")
            ));
            
            if (percent == 0) {
                ctx.sendMessage(Message.raw("Note: 0% means night will skip instantly when anyone sleeps!").color("#FFAA00"));
            } else if (percent == 100) {
                ctx.sendMessage(Message.raw("Note: 100% means all players must sleep (default behavior).").color("#FFAA00"));
            }
        }
    }
}
