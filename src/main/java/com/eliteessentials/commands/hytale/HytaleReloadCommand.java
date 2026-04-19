package com.eliteessentials.commands.hytale;

import com.eliteessentials.EliteEssentials;
import com.eliteessentials.commands.args.SimpleStringArg;
import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.permissions.PermissionService;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.services.GroupSyncService;
import com.eliteessentials.util.MessageFormatter;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;

import javax.annotation.Nonnull;
import com.eliteessentials.util.CommandSpyUtil;

/**
 * Command: /eliteessentials <action>
 * Admin commands for EliteEssentials.
 *
 * Actions:
 * - reload: Reload configuration
 * - groupsync [ee-to-lp|lp-to-ee]: Sync groups between LuckPerms and EE config
 * - migration: See /eemigration for migration commands
 *
 * Permissions:
 * - Admin only (simple mode)
 * - eliteessentials.admin.reload, eliteessentials.admin.groupsync (advanced mode)
 */
public class HytaleReloadCommand extends CommandBase {

    private final RequiredArg<String> actionArg;

    public HytaleReloadCommand() {
        super("eliteessentials", "EliteEssentials admin commands");

        // Add /ee as a short alias
        addAliases("ee");

        setAllowsExtraArguments(true);

        this.actionArg = withRequiredArg("action", "Action (reload, groupsync, migration)", SimpleStringArg.ACTION);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void executeSync(@Nonnull CommandContext ctx) {
        CommandSpyUtil.notify(ctx);
        String action = ctx.get(actionArg);

        if ("groupsync".equalsIgnoreCase(action)) {
            handleGroupSync(ctx);
            return;
        }

        // reload and migration use ADMIN_RELOAD
        PermissionService perms = PermissionService.get();
        if (!perms.canUseAdminCommand(ctx.sender(), Permissions.ADMIN_RELOAD, true)) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(EliteEssentials.getInstance().getConfigManager().getMessage("noPermission"), "#FF5555"));
            return;
        }

        if ("reload".equalsIgnoreCase(action)) {
            handleReload(ctx);
        } else if ("migration".equalsIgnoreCase(action)) {
            ctx.sendMessage(Message.raw("Usage: /eemigration <source> [force]").color("#FFAA00"));
            ctx.sendMessage(Message.raw("  essentialscore - Import warps, spawn, kits, homes, and cooldowns from EssentialsCore").color("#AAAAAA"));
            ctx.sendMessage(Message.raw("  hyssentials - Import homes and warps from Hyssentials").color("#AAAAAA"));
            ctx.sendMessage(Message.raw("  essentialsplus - Import warps, kits, spawns, homes, and user profiles from EssentialsPlus").color("#AAAAAA"));
            ctx.sendMessage(Message.raw("  homesplus - Import homes from HomesPlus").color("#AAAAAA"));
            ctx.sendMessage(Message.raw("  sql - Migrate JSON data into configured SQL database").color("#AAAAAA"));
            ctx.sendMessage(Message.raw("  cleanup - Move migrated JSON files into backup/ folder").color("#AAAAAA"));
            ctx.sendMessage(Message.raw("  force - Add after source to overwrite existing data").color("#AAAAAA"));
        } else {
            ctx.sendMessage(Message.raw("Unknown action. Available: reload, groupsync, migration").color("#FF5555"));
        }
    }

    private void handleGroupSync(CommandContext ctx) {
        PermissionService perms = PermissionService.get();
        if (!perms.canUseAdminCommand(ctx.sender(), Permissions.ADMIN_GROUPSYNC, true)) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(EliteEssentials.getInstance().getConfigManager().getMessage("noPermission"), "#FF5555"));
            return;
        }

        // Parse: /ee groupsync [direction]
        String rawInput = ctx.getInputString();
        String[] parts = rawInput.split("\\s+");

        if (parts.length < 3 || parts[2].isEmpty()) {
            showGroupSyncUsage(ctx);
            return;
        }

        String direction = parts[2].toLowerCase().replace("_", "-");
        GroupSyncService syncService = new GroupSyncService(EliteEssentials.getInstance().getConfigManager());
        GroupSyncService.GroupSyncResult result;

        switch (direction) {
            case "lp-to-ee":
            case "lpteee":
            case "lp2ee":
                result = syncService.syncLuckPermsToEe();
                break;
            case "hp-to-ee":
            case "hpteee":
            case "hp2ee":
                result = syncService.syncHyperPermsToEe();
                break;
            case "ee-to-lp":
            case "eetolp":
            case "ee2lp":
                result = syncService.syncEeToLuckPerms();
                break;
            case "ee-to-hp":
            case "eetohp":
            case "ee2hp":
                result = syncService.syncEeToHyperPerms();
                break;
            default:
                showGroupSyncUsage(ctx);
                return;
        }

        if (result.getErrorMessage() != null) {
            ctx.sendMessage(Message.raw(result.getErrorMessage()).color("#FF5555"));
            return;
        }
        if (result.success) {
            if (result.permsToEe) {
                ctx.sendMessage(Message.raw("Sync complete. Added " + result.addedFormats + " group(s) to chat formats, " + result.addedLimits + " to warp limits from " + result.backendName + ".").color("#55FF55"));
            } else {
                ctx.sendMessage(Message.raw("Sync complete. Created " + result.createdGroups + " group(s) in " + result.backendName + ".").color("#55FF55"));
            }
            if (!result.errors.isEmpty()) {
                for (String err : result.errors) {
                    ctx.sendMessage(Message.raw("  - " + err).color("#FFAA00"));
                }
            }
        }
    }

    private void showGroupSyncUsage(CommandContext ctx) {
                ctx.sendMessage(Message.raw("=== /ee groupsync - Sync groups between EE config and permission plugins ===").color("#55FFFF"));
        ctx.sendMessage(Message.raw("Copy groups FROM a permission plugin INTO your config:").color("#CCCCCC"));
        ctx.sendMessage(Message.join(
            Message.raw("  /ee groupsync lp-to-ee").color("#55FF55"),
            Message.raw(" - LuckPerms groups -> config.json (chatFormat, warps)").color("#AAAAAA")
        ));
        ctx.sendMessage(Message.join(
            Message.raw("  /ee groupsync hp-to-ee").color("#55FF55"),
            Message.raw(" - HyperPerms groups -> config.json (chatFormat, warps)").color("#AAAAAA")
        ));
        ctx.sendMessage(Message.raw("Create groups in a permission plugin FROM your config:").color("#CCCCCC"));
        ctx.sendMessage(Message.join(
            Message.raw("  /ee groupsync ee-to-lp").color("#55FF55"),
            Message.raw(" - EE config groups -> LuckPerms (creates missing groups)").color("#AAAAAA")
        ));
        ctx.sendMessage(Message.join(
            Message.raw("  /ee groupsync ee-to-hp").color("#55FF55"),
            Message.raw(" - EE config groups -> HyperPerms (creates missing groups)").color("#AAAAAA")
        ));
        ctx.sendMessage(Message.raw("What gets synced:").color("#CCCCCC"));
        ctx.sendMessage(Message.raw("  • chatFormat.groupFormats, chatFormat.groupPriorities, warps.groupLimits, groupchats.json").color("#AAAAAA"));
        ctx.sendMessage(Message.raw("lp = LuckPerms  |  hp = HyperPerms  |  ee = EliteEssentials config").color("#BBBBBB"));
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
            
            // Retry external economy detection if configured
            var vaultIntegration = EliteEssentials.getInstance().getVaultUnlockedIntegration();
            if (vaultIntegration != null && vaultIntegration.retryExternalEconomy()) {
                ctx.sendMessage(Message.raw("VaultUnlocked: Now using external economy!").color("#55FF55"));
            }
        } catch (Exception e) {
            ctx.sendMessage(Message.raw("Failed to reload configuration: " + e.getMessage()).color("#FF5555"));
        }
    }
}
