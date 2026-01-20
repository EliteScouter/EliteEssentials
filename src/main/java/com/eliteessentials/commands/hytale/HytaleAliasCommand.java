package com.eliteessentials.commands.hytale;

import com.eliteessentials.EliteEssentials;
import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.permissions.PermissionService;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.services.AliasService;
import com.eliteessentials.storage.AliasStorage.AliasData;
import com.eliteessentials.util.MessageFormatter;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;

import javax.annotation.Nonnull;
import java.util.Map;

/**
 * Command: /alias
 * Manage command aliases.
 * 
 * Usage:
 * - /alias create <name> <command...> [permission] - Create an alias
 * - /alias delete <name> - Delete an alias
 * - /alias list - List all aliases
 * - /alias info <name> - Show alias details
 */
public class HytaleAliasCommand extends CommandBase {

    public HytaleAliasCommand() {
        super("alias", "Manage command aliases");
        setAllowsExtraArguments(true);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void executeSync(@Nonnull CommandContext ctx) {
        ConfigManager configManager = EliteEssentials.getInstance().getConfigManager();
        
        if (!PermissionService.get().canUseAdminCommand(ctx.sender(), Permissions.ADMIN_ALIAS, true)) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("noPermission"), "#FF5555"));
            return;
        }

        // Parse: /alias <action> [name] [command...]
        String rawInput = ctx.getInputString();
        String[] parts = rawInput.split("\\s+");
        
        if (parts.length < 2) {
            showUsage(ctx);
            return;
        }
        
        String action = parts[1].toLowerCase();
        AliasService aliasService = EliteEssentials.getInstance().getAliasService();

        switch (action) {
            case "create":
            case "add":
                handleCreate(ctx, parts, aliasService, configManager);
                break;
            case "delete":
            case "remove":
            case "del":
                handleDelete(ctx, parts, aliasService, configManager);
                break;
            case "list":
                handleList(ctx, aliasService);
                break;
            case "info":
                handleInfo(ctx, parts, aliasService, configManager);
                break;
            default:
                showUsage(ctx);
                break;
        }
    }

    private void handleCreate(CommandContext ctx, String[] parts, AliasService aliasService, ConfigManager configManager) {
        // /alias create <name> <command...> [permission]
        // parts[0]=alias, parts[1]=create, parts[2]=name, parts[3+]=command
        if (parts.length < 4) {
            ctx.sendMessage(Message.raw("Usage: /alias create <name> <command> [permission]").color("#FFAA00"));
            ctx.sendMessage(Message.raw("Example: /alias create explore warp explore").color("#777777"));
            ctx.sendMessage(Message.raw("Example: /alias create vipkit kit vip op").color("#777777"));
            return;
        }

        String name = parts[2];
        
        // Build command string from parts[3] onwards
        StringBuilder cmdBuilder = new StringBuilder();
        for (int i = 3; i < parts.length; i++) {
            if (i > 3) cmdBuilder.append(" ");
            cmdBuilder.append(parts[i]);
        }
        String commandStr = cmdBuilder.toString();

        // Parse permission from last word if it's "everyone", "op", or contains a dot
        String command;
        String permission = "everyone";
        
        String lastPart = parts[parts.length - 1].toLowerCase();
        if (parts.length > 4 && (lastPart.equals("everyone") || lastPart.equals("op") || lastPart.contains("."))) {
            permission = lastPart;
            // Rebuild command without the last part
            cmdBuilder = new StringBuilder();
            for (int i = 3; i < parts.length - 1; i++) {
                if (i > 3) cmdBuilder.append(" ");
                cmdBuilder.append(parts[i]);
            }
            command = cmdBuilder.toString();
        } else {
            command = commandStr;
        }

        if (command.isEmpty()) {
            ctx.sendMessage(Message.raw("You must specify a command for the alias.").color("#FF5555"));
            return;
        }

        if (command.startsWith("/")) {
            command = command.substring(1);
        }

        if (name.equalsIgnoreCase("alias") || name.equalsIgnoreCase("ee") || 
            name.equalsIgnoreCase("eliteessentials")) {
            ctx.sendMessage(Message.raw("Cannot create alias with reserved name: " + name).color("#FF5555"));
            return;
        }

        boolean isNew = aliasService.createAlias(name, command, permission);
        
        if (isNew) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("aliasCreated", "name", name, "command", command, "permission", permission),
                "#55FF55"));
        } else {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("aliasUpdated", "name", name, "command", command, "permission", permission),
                "#55FF55"));
        }
    }

    private void handleDelete(CommandContext ctx, String[] parts, AliasService aliasService, ConfigManager configManager) {
        // /alias delete <name>
        if (parts.length < 3) {
            ctx.sendMessage(Message.raw("Usage: /alias delete <name>").color("#FFAA00"));
            return;
        }

        String name = parts[2];

        if (aliasService.deleteAlias(name)) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("aliasDeleted", "name", name),
                "#55FF55"));
            ctx.sendMessage(Message.raw("Note: The command will be fully removed after server restart.").color("#777777"));
        } else {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("aliasNotFound", "name", name),
                "#FF5555"));
        }
    }

    private void handleList(CommandContext ctx, AliasService aliasService) {
        Map<String, AliasData> aliases = aliasService.getAllAliases();

        if (aliases.isEmpty()) {
            ctx.sendMessage(Message.raw("No aliases configured.").color("#FFAA00"));
            ctx.sendMessage(Message.raw("Use /alias create <name> <command> to create one.").color("#777777"));
            return;
        }

        ctx.sendMessage(Message.raw("=== Command Aliases ===").color("#55FFFF"));
        for (Map.Entry<String, AliasData> entry : aliases.entrySet()) {
            String name = entry.getKey();
            AliasData data = entry.getValue();
            ctx.sendMessage(Message.join(
                Message.raw("/" + name).color("#55FF55"),
                Message.raw(" -> ").color("#777777"),
                Message.raw("/" + data.command).color("#FFFFFF"),
                Message.raw(" [" + data.permission + "]").color("#AAAAAA")
            ));
        }
        ctx.sendMessage(Message.raw("Total: " + aliases.size() + " alias(es)").color("#777777"));
    }

    private void handleInfo(CommandContext ctx, String[] parts, AliasService aliasService, ConfigManager configManager) {
        // /alias info <name>
        if (parts.length < 3) {
            ctx.sendMessage(Message.raw("Usage: /alias info <name>").color("#FFAA00"));
            return;
        }

        String name = parts[2];
        AliasData data = aliasService.getStorage().getAlias(name);
        
        if (data == null) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("aliasNotFound", "name", name),
                "#FF5555"));
            return;
        }

        ctx.sendMessage(Message.raw("=== Alias: /" + name + " ===").color("#55FFFF"));
        ctx.sendMessage(Message.join(
            Message.raw("Command: ").color("#AAAAAA"),
            Message.raw("/" + data.command).color("#FFFFFF")
        ));
        ctx.sendMessage(Message.join(
            Message.raw("Permission: ").color("#AAAAAA"),
            Message.raw(data.permission).color("#FFAA00")
        ));
        
        if ("everyone".equalsIgnoreCase(data.permission)) {
            ctx.sendMessage(Message.raw("  (Anyone can use this alias)").color("#777777"));
        } else if ("op".equalsIgnoreCase(data.permission)) {
            ctx.sendMessage(Message.raw("  (Only admins/OPs can use this alias)").color("#777777"));
        } else {
            ctx.sendMessage(Message.raw("  (Requires permission: " + data.permission + ")").color("#777777"));
        }
    }

    private void showUsage(CommandContext ctx) {
        ctx.sendMessage(Message.raw("=== Alias Commands ===").color("#55FFFF"));
        ctx.sendMessage(Message.join(
            Message.raw("/alias create <name> <command> [permission]").color("#55FF55"),
            Message.raw(" - Create alias").color("#777777")
        ));
        ctx.sendMessage(Message.join(
            Message.raw("/alias delete <name>").color("#55FF55"),
            Message.raw(" - Delete alias").color("#777777")
        ));
        ctx.sendMessage(Message.join(
            Message.raw("/alias list").color("#55FF55"),
            Message.raw(" - List all aliases").color("#777777")
        ));
        ctx.sendMessage(Message.join(
            Message.raw("/alias info <name>").color("#55FF55"),
            Message.raw(" - Show alias details").color("#777777")
        ));
        ctx.sendMessage(Message.raw("Permissions: everyone, op, or custom.node").color("#AAAAAA"));
    }
}
