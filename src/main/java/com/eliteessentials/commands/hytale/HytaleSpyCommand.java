package com.eliteessentials.commands.hytale;

import com.eliteessentials.EliteEssentials;
import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.config.PluginConfig;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.services.SpyService;
import com.eliteessentials.util.CommandPermissionUtil;
import com.eliteessentials.util.MessageFormatter;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.UUID;
import com.eliteessentials.util.CommandSpyUtil;

/**
 * Command: /spy [gchat|dm|command]
 * 
 * Unified spy command that lets admins toggle different spy modes:
 * - gchat: See all group chat messages from channels you're not in
 * - dm: See all private messages between players
 * - command: See all commands executed by players
 * 
 * Usage:
 * - /spy - Show current spy status and help
 * - /spy gchat - Toggle group chat spy
 * - /spy dm - Toggle DM spy
 * - /spy command - Toggle command spy
 * 
 * Permission: eliteessentials.admin.spy (Admin only)
 */
public class HytaleSpyCommand extends AbstractPlayerCommand {

    private final SpyService spyService;
    private final ConfigManager configManager;

    public HytaleSpyCommand(SpyService spyService, ConfigManager configManager) {
        super("spy", "Toggle spy modes (gchat, dm, command)");
        this.spyService = spyService;
        this.configManager = configManager;

        setAllowsExtraArguments(true);
        addAliases("gcspy");
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                          @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef player, @Nonnull World world) {
        CommandSpyUtil.notify(ctx);

        PluginConfig.SpyConfig spyConfig = configManager.getConfig().spy;

        // Admin-only command
        if (!CommandPermissionUtil.canExecuteAdmin(ctx, player, Permissions.SPY, spyConfig.enabled)) {
            return;
        }

        UUID playerId = player.getUuid();

        // Parse subcommand from raw input
        String rawInput = ctx.getInputString().trim();
        String[] parts = rawInput.split("\\s+", 2);

        // No subcommand: show status and help
        if (parts.length < 2) {
            showStatus(ctx, playerId);
            return;
        }

        String subcommand = parts[1].toLowerCase();

        switch (subcommand) {
            case "gchat" -> handleGchat(ctx, playerId, spyConfig);
            case "dm" -> handleDm(ctx, playerId, spyConfig);
            case "command", "cmd" -> handleCommand(ctx, playerId, spyConfig);
            default -> showStatus(ctx, playerId);
        }
    }

    private void handleGchat(CommandContext ctx, UUID playerId, PluginConfig.SpyConfig spyConfig) {
        if (!spyConfig.gchatSpyEnabled) {
            ctx.sendMessage(MessageFormatter.format(
                configManager.getMessage("spyGchatDisabledConfig")));
            return;
        }

        boolean enabled = spyService.toggleGchatSpy(playerId);
        if (enabled) {
            ctx.sendMessage(MessageFormatter.format(
                configManager.getMessage("spyGchatEnabled")));
        } else {
            ctx.sendMessage(MessageFormatter.format(
                configManager.getMessage("spyGchatDisabled")));
        }
    }

    private void handleDm(CommandContext ctx, UUID playerId, PluginConfig.SpyConfig spyConfig) {
        if (!spyConfig.dmSpyEnabled) {
            ctx.sendMessage(MessageFormatter.format(
                configManager.getMessage("spyDmDisabledConfig")));
            return;
        }

        boolean enabled = spyService.toggleDmSpy(playerId);
        if (enabled) {
            ctx.sendMessage(MessageFormatter.format(
                configManager.getMessage("spyDmEnabled")));
        } else {
            ctx.sendMessage(MessageFormatter.format(
                configManager.getMessage("spyDmDisabled")));
        }
    }

    private void handleCommand(CommandContext ctx, UUID playerId, PluginConfig.SpyConfig spyConfig) {
        if (!spyConfig.commandSpyEnabled) {
            ctx.sendMessage(MessageFormatter.format(
                configManager.getMessage("spyCommandDisabledConfig")));
            return;
        }

        boolean enabled = spyService.toggleCommandSpy(playerId);
        if (enabled) {
            ctx.sendMessage(MessageFormatter.format(
                configManager.getMessage("spyCommandEnabled")));
        } else {
            ctx.sendMessage(MessageFormatter.format(
                configManager.getMessage("spyCommandDisabled")));
        }
    }

    private void showStatus(CommandContext ctx, UUID playerId) {
        List<String> activeModes = spyService.getActiveSpyModes(playerId);
        String status = activeModes.isEmpty() ? "none" : String.join(", ", activeModes);

        ctx.sendMessage(MessageFormatter.format(
            configManager.getMessage("spyStatusHeader")));
        ctx.sendMessage(MessageFormatter.format(
            configManager.getMessage("spyStatusActive", "modes", status)));
        ctx.sendMessage(MessageFormatter.format(
            configManager.getMessage("spyUsageGchat")));
        ctx.sendMessage(MessageFormatter.format(
            configManager.getMessage("spyUsageDm")));
        ctx.sendMessage(MessageFormatter.format(
            configManager.getMessage("spyUsageCommand")));
    }
}
