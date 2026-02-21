package com.eliteessentials.commands.hytale;

import com.eliteessentials.EliteEssentials;
import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.config.PluginConfig;
import com.eliteessentials.permissions.PermissionService;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.storage.CustomHelpStorage;
import com.eliteessentials.util.MessageFormatter;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * /eehelp - Shows EliteEssentials commands the player has permission to use.
 * 
 * Also displays custom help entries from custom_help.json, allowing server
 * admins to add help text for commands from other plugins.
 * 
 * Only displays commands that:
 * - Are enabled in config
 * - The player has permission to use (based on simple/advanced mode)
 */
public class HytaleHelpCommand extends CommandBase {

    public HytaleHelpCommand() {
        super("eehelp", "Show available EliteEssentials commands");
        addAliases("ehelp");
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;  // We manually register eliteessentials.command.misc.eehelp
    }

    @Override
    protected void executeSync(@Nonnull CommandContext ctx) {
        ConfigManager configManager = EliteEssentials.getInstance().getConfigManager();
        PluginConfig config = configManager.getConfig();
        PermissionService perms = PermissionService.get();
        CommandSender sender = ctx.sender();
        
        boolean isAdmin = perms.isAdmin(sender);
        
        List<CommandInfo> available = new ArrayList<>();
        
        // ==================== TELEPORTATION ====================
        if (config.homes.enabled && canUse(sender, Permissions.HOME, true)) {
            available.add(new CommandInfo("/home [name]", "Teleport to a home"));
            available.add(new CommandInfo("/sethome [name]", "Set a home location"));
            available.add(new CommandInfo("/delhome <name>", "Delete a home"));
            available.add(new CommandInfo("/homes", "List your homes"));
        }
        
        if (config.back.enabled && canUse(sender, Permissions.BACK, true)) {
            available.add(new CommandInfo("/back", "Return to previous location"));
        }
        
        if (config.spawn.enabled && canUse(sender, Permissions.SPAWN, true)) {
            available.add(new CommandInfo("/spawn", "Teleport to spawn"));
        }
        
        if (config.warps.enabled && canUse(sender, Permissions.WARPS, true)) {
            available.add(new CommandInfo("/warp [name|list]", "Teleport to a warp or open GUI"));
        }
        
        if (config.rtp.enabled && canUse(sender, Permissions.RTP, true)) {
            available.add(new CommandInfo("/rtp", "Random teleport"));
        }
        
        if (config.top.enabled && canUse(sender, Permissions.TOP, true)) {
            available.add(new CommandInfo("/top", "Teleport to highest block"));
        }
        
        // ==================== TPA ====================
        if (config.tpa.enabled && canUse(sender, Permissions.TPA, true)) {
            available.add(new CommandInfo("/tpa <player>", "Request to teleport to player"));
            available.add(new CommandInfo("/tpahere <player>", "Request player to teleport to you"));
            available.add(new CommandInfo("/tpaccept", "Accept teleport request"));
            available.add(new CommandInfo("/tpdeny", "Deny teleport request"));
        }
        
        // ==================== KITS ====================
        if (config.kits.enabled && canUse(sender, Permissions.KIT, true)) {
            available.add(new CommandInfo("/kit [name]", "Claim a kit or open GUI"));
        }
        
        // ==================== COMMUNICATION ====================
        if (config.msg.enabled && canUse(sender, Permissions.MSG, true)) {
            available.add(new CommandInfo("/msg <player> <message>", "Send private message"));
            available.add(new CommandInfo("/reply <message>", "Reply to last message"));
        }
        
        if (config.motd.enabled && canUse(sender, Permissions.MOTD, true)) {
            available.add(new CommandInfo("/motd", "View message of the day"));
        }
        
        if (config.rules.enabled && canUse(sender, Permissions.RULES, true)) {
            available.add(new CommandInfo("/rules", "View server rules"));
        }
        
        if (config.discord.enabled && canUse(sender, Permissions.DISCORD, true)) {
            available.add(new CommandInfo("/discord", "View Discord invite"));
        }
        
        if (config.list.enabled && canUse(sender, Permissions.LIST, true)) {
            available.add(new CommandInfo("/list", "List online players"));
        }
        
        if (config.mail.enabled && canUse(sender, Permissions.MAIL, true)) {
            available.add(new CommandInfo("/mail <send|read|list|clear>", "In-game mail system"));
        }
        
        // ==================== ECONOMY ====================
        if (config.economy.enabled) {
            if (canUse(sender, Permissions.WALLET, true)) {
                available.add(new CommandInfo("/wallet [player]", "Check balance"));
            }
            if (canUse(sender, Permissions.PAY, true)) {
                available.add(new CommandInfo("/pay <player> <amount>", "Send money"));
            }
            if (canUse(sender, Permissions.BALTOP, true)) {
                available.add(new CommandInfo("/baltop", "View richest players"));
            }
        }
        
        // ==================== PLAYER COMMANDS ====================
        if (config.fly.enabled && canUse(sender, Permissions.FLY, false)) {
            available.add(new CommandInfo("/fly", "Toggle flight mode"));
            available.add(new CommandInfo("/flyspeed <10-100>", "Set fly speed"));
        }
        
        if (config.god.enabled && canUse(sender, Permissions.GOD, false)) {
            available.add(new CommandInfo("/god", "Toggle god mode"));
        }
        
        if (config.heal.enabled && canUse(sender, Permissions.HEAL, false)) {
            available.add(new CommandInfo("/heal", "Restore health"));
        }
        
        if (config.clearInv.enabled && canUse(sender, Permissions.CLEARINV, false)) {
            available.add(new CommandInfo("/clearinv", "Clear inventory"));
        }
        
        if (config.repair.enabled && canUse(sender, Permissions.REPAIR, false)) {
            available.add(new CommandInfo("/repair [all]", "Repair held item or all items"));
        }
        
        if (config.trash.enabled && canUse(sender, Permissions.TRASH, true)) {
            available.add(new CommandInfo("/trash", "Open trash disposal"));
        }
        
        if (config.vanish.enabled && canUse(sender, Permissions.VANISH, false)) {
            available.add(new CommandInfo("/vanish", "Toggle vanish mode"));
        }
        
        // /seen is always available (no separate config toggle)
        if (canUse(sender, Permissions.SEEN, true)) {
            available.add(new CommandInfo("/seen <player>", "Check when player was last online"));
        }
        
        if (config.joindate.enabled && canUse(sender, Permissions.JOINDATE, true)) {
            available.add(new CommandInfo("/joindate [player]", "Check first join date"));
        }
        
        if (config.playtime.enabled && canUse(sender, Permissions.PLAYTIME, true)) {
            available.add(new CommandInfo("/playtime [player]", "Check total play time"));
        }
        
        if (config.afk.enabled && canUse(sender, Permissions.AFK, true)) {
            available.add(new CommandInfo("/afk", "Toggle AFK status"));
        }
        
        if (config.ignore.enabled && canUse(sender, Permissions.IGNORE, true)) {
            available.add(new CommandInfo("/ignore <player|list>", "Ignore a player"));
            available.add(new CommandInfo("/unignore <player|all>", "Unignore a player"));
        }
        
        if (config.groupChat.enabled && canUse(sender, Permissions.GROUP_CHAT, true)) {
            available.add(new CommandInfo("/gc [chat] <message>", "Send group chat message"));
            available.add(new CommandInfo("/chats", "List available chat channels"));
        }
        
        // ==================== ADMIN COMMANDS ====================
        if (isAdmin) {
            available.add(new CommandInfo("/setspawn", "Set server spawn point"));
            available.add(new CommandInfo("/tphere <player>", "Teleport player to you"));
            
            if (config.warps.enabled) {
                available.add(new CommandInfo("/warpadmin", "Manage warps"));
                available.add(new CommandInfo("/warpsetperm <name> <all|op>", "Set warp permission"));
                available.add(new CommandInfo("/warpsetdesc <name> <desc>", "Set warp description"));
            }
            
            if (config.kits.enabled) {
                available.add(new CommandInfo("/kitcreate <name>", "Create kit from inventory"));
                available.add(new CommandInfo("/kitdelete <name>", "Delete a kit"));
            }
            
            if (config.broadcast.enabled) {
                available.add(new CommandInfo("/broadcast <message>", "Broadcast to all players"));
            }
            
            if (config.clearChat.enabled) {
                available.add(new CommandInfo("/clearchat", "Clear server chat"));
            }
            
            if (config.economy.enabled) {
                available.add(new CommandInfo("/eco <give|take|set> <player> <amount>", "Manage economy"));
            }
            
            if (config.mute.enabled) {
                available.add(new CommandInfo("/mute <player> [reason]", "Mute a player"));
                available.add(new CommandInfo("/unmute <player>", "Unmute a player"));
            }
            
            if (config.ban.enabled) {
                available.add(new CommandInfo("/ban <player> [reason]", "Ban a player"));
                available.add(new CommandInfo("/unban <player>", "Unban a player"));
                available.add(new CommandInfo("/tempban <player> <time> [reason]", "Temporarily ban a player"));
                available.add(new CommandInfo("/ipban <player> [reason]", "IP ban a player"));
                available.add(new CommandInfo("/unipban <ip|player>", "Remove an IP ban"));
            }
            
            if (config.freeze.enabled) {
                available.add(new CommandInfo("/freeze <player>", "Freeze/unfreeze a player"));
            }
            
            available.add(new CommandInfo("/invsee <player>", "View a player's inventory"));
            available.add(new CommandInfo("/sleeppercent <0-100>", "Set sleep percentage"));
            available.add(new CommandInfo("/ee reload", "Reload configuration"));
            available.add(new CommandInfo("/alias", "Manage command aliases"));
        }
        
        // ==================== CUSTOM HELP ENTRIES ====================
        // Loaded from custom_help.json - allows admins to add entries for other plugins
        CustomHelpStorage customHelp = EliteEssentials.getInstance().getCustomHelpStorage();
        if (customHelp != null) {
            for (CustomHelpStorage.CustomHelpEntry entry : customHelp.getEntries()) {
                if (!entry.enabled) continue;
                if (canUseCustomEntry(sender, entry.permission, isAdmin)) {
                    available.add(new CommandInfo(entry.command, entry.description));
                }
            }
        }
        
        // ==================== DISPLAY ====================
        ctx.sendMessage(Message.raw("").color("#FFFFFF"));
        ctx.sendMessage(MessageFormatter.formatWithFallback("&b&l=== &fEliteEssentials Help &b&l===", "#55FFFF"));
        
        if (available.isEmpty()) {
            if (config.advancedPermissions) {
                ctx.sendMessage(MessageFormatter.formatWithFallback("&7You have no permissions granted.", "#AAAAAA"));
                ctx.sendMessage(MessageFormatter.formatWithFallback("&7Ask an admin to grant you permissions via LuckPerms.", "#AAAAAA"));
                ctx.sendMessage(MessageFormatter.formatWithFallback("&7Example: &e/lp user <name> permission set eliteessentials.command.*", "#AAAAAA"));
            } else {
                ctx.sendMessage(MessageFormatter.formatWithFallback("&7No commands are currently enabled.", "#AAAAAA"));
            }
        } else {
            ctx.sendMessage(MessageFormatter.formatWithFallback("&7Showing &a" + available.size() + " &7commands you can use:", "#AAAAAA"));
            ctx.sendMessage(Message.raw("").color("#FFFFFF"));
            
            for (CommandInfo cmd : available) {
                ctx.sendMessage(Message.join(
                    Message.raw("  " + cmd.usage).color("#FFAA00"),
                    Message.raw(" - " + cmd.description).color("#AAAAAA")
                ));
            }
        }
        
        ctx.sendMessage(Message.raw("").color("#FFFFFF"));
        
        if (!isAdmin) {
            ctx.sendMessage(MessageFormatter.formatWithFallback("&7Some commands may be hidden (admin only).", "#555555"));
        }
    }
    
    /**
     * Check if sender can use a built-in command.
     * @param sender CommandSender
     * @param permission Permission node
     * @param isEveryoneCommand True if command is available to everyone by default
     */
    private boolean canUse(CommandSender sender, String permission, boolean isEveryoneCommand) {
        PermissionService perms = PermissionService.get();
        
        if (perms.isAdmin(sender)) {
            return true;
        }
        
        if (isEveryoneCommand) {
            return perms.canUseEveryoneCommand(sender, permission, true);
        } else {
            return false;
        }
    }
    
    /**
     * Check if sender can see a custom help entry based on its permission field.
     * "everyone" = visible to all, "op" = admins only, anything else = permission node.
     */
    private boolean canUseCustomEntry(CommandSender sender, String permission, boolean isAdmin) {
        if ("everyone".equalsIgnoreCase(permission)) {
            return true;
        }
        if ("op".equalsIgnoreCase(permission)) {
            return isAdmin;
        }
        // Custom permission node - check in advanced mode, admin-only in simple mode
        if (isAdmin) return true;
        if (EliteEssentials.getInstance().getConfigManager().isAdvancedPermissions()) {
            return PermissionService.get().hasPermission(sender, permission);
        }
        return false;
    }
    
    /**
     * Simple holder for command info.
     */
    private static class CommandInfo {
        final String usage;
        final String description;
        
        CommandInfo(String usage, String description) {
            this.usage = usage;
            this.description = description;
        }
    }
}
