package com.eliteessentials.commands.hytale;

import com.eliteessentials.api.EconomyAPI;
import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.config.PluginConfig;
import com.eliteessentials.permissions.PermissionService;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.services.PlayerService;
import com.eliteessentials.util.MessageFormatter;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;

import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * Command: /eco <action> <player> [amount]
 * Console-compatible economy management command.
 * 
 * Usage:
 * - /eco check <player> - Check player's balance
 * - /eco set <player> <amount> - Set player's balance
 * - /eco add <player> <amount> - Add to player's balance  
 * - /eco remove <player> <amount> - Remove from player's balance
 * 
 * Examples:
 * - /eco check Steve
 * - /eco set Steve 1000
 * - /eco add Steve 500
 * - /eco remove Steve 100
 * 
 * Can be run from console or by admins in-game.
 * Permission: eliteessentials.command.economy.wallet.admin
 */
public class HytaleEcoCommand extends CommandBase {

    private final ConfigManager configManager;
    private final PlayerService playerService;

    public HytaleEcoCommand(ConfigManager configManager, PlayerService playerService) {
        super("eco", "Economy management (check/set/add/remove)");
        this.configManager = configManager;
        this.playerService = playerService;
        
        setAllowsExtraArguments(true);
        
        addAliases("economy");
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void executeSync(@Nonnull CommandContext ctx) {
        PluginConfig.EconomyConfig economyConfig = configManager.getConfig().economy;
        
        if (!economyConfig.enabled) {
            ctx.sendMessage(Message.raw("Economy system is disabled.").color("#FF5555"));
            return;
        }
        
        // Check permission
        if (!PermissionService.get().canUseAdminCommand(ctx.sender(), Permissions.WALLET_ADMIN, economyConfig.enabled)) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("noPermission"), "#FF5555"));
            return;
        }
        
        // Parse: /eco <action> <player> [amount]
        String rawInput = ctx.getInputString();
        String[] parts = rawInput.split("\\s+");
        
        if (parts.length < 2) {
            showUsage(ctx);
            return;
        }
        
        String action = parts[1].toLowerCase();
        
        if (action.equals("check")) {
            if (parts.length < 3) {
                showUsage(ctx);
                return;
            }
            String playerName = parts[2];
            UUID targetId = findPlayerId(playerName);
            if (targetId == null) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("playerNotFound", "player", playerName), "#FF5555"));
                return;
            }
            double balance = playerService.getBalance(targetId);
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("walletBalanceOther",
                    "player", playerName,
                    "balance", EconomyAPI.format(balance),
                    "currency", EconomyAPI.getCurrencyNamePlural()), "#55FF55"));
        } else if (action.equals("set") || action.equals("add") || action.equals("remove")) {
            if (parts.length < 4) {
                showUsage(ctx);
                return;
            }
            String playerName = parts[2];
            double amount;
            try {
                amount = Double.parseDouble(parts[3]);
            } catch (NumberFormatException e) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("walletInvalidAmount"), "#FF5555"));
                return;
            }
            
            UUID targetId = findPlayerId(playerName);
            PlayerRef targetPlayer = findPlayerRef(playerName);
            if (targetId == null) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("playerNotFound", "player", playerName), "#FF5555"));
                return;
            }
            
            // Determine sender name (console or player name)
            String senderName = ctx.sender() instanceof PlayerRef ? 
                ((PlayerRef) ctx.sender()).getUsername() : "Server";
            
            switch (action) {
                case "set" -> {
                    if (playerService.setBalance(targetId, amount, targetPlayer, senderName)) {
                        ctx.sendMessage(MessageFormatter.formatWithFallback(
                            configManager.getMessage("walletSet",
                                "player", playerName,
                                "amount", EconomyAPI.format(amount),
                                "balance", EconomyAPI.format(amount)), "#55FF55"));
                    } else {
                        ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("walletFailed"), "#FF5555"));
                    }
                }
                case "add" -> {
                    if (playerService.addMoney(targetId, amount, targetPlayer, senderName)) {
                        double newBalance = playerService.getBalance(targetId);
                        ctx.sendMessage(MessageFormatter.formatWithFallback(
                            configManager.getMessage("walletAdded",
                                "player", playerName,
                                "amount", EconomyAPI.format(amount),
                                "balance", EconomyAPI.format(newBalance)), "#55FF55"));
                    } else {
                        ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("walletFailed"), "#FF5555"));
                    }
                }
                case "remove" -> {
                    if (playerService.removeMoney(targetId, amount, targetPlayer, senderName)) {
                        double newBalance = playerService.getBalance(targetId);
                        ctx.sendMessage(MessageFormatter.formatWithFallback(
                            configManager.getMessage("walletRemoved",
                                "player", playerName,
                                "amount", EconomyAPI.format(amount),
                                "balance", EconomyAPI.format(newBalance)), "#55FF55"));
                    } else {
                        ctx.sendMessage(MessageFormatter.formatWithFallback(
                            configManager.getMessage("walletInsufficientFunds", "player", playerName), "#FF5555"));
                    }
                }
            }
        } else {
            showUsage(ctx);
        }
    }
    
    private void showUsage(CommandContext ctx) {
        ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("ecoUsage"), "#FFAA00"));
    }
    
    private UUID findPlayerId(String name) {
        // Check online players first
        for (PlayerRef p : Universe.get().getPlayers()) {
            if (p.getUsername().equalsIgnoreCase(name)) {
                return p.getUuid();
            }
        }
        // Check offline players in cache
        return playerService.getPlayerByName(name).map(d -> d.getUuid()).orElse(null);
    }
    
    private PlayerRef findPlayerRef(String name) {
        // Check online players first
        for (PlayerRef p : Universe.get().getPlayers()) {
            if (p.getUsername().equalsIgnoreCase(name)) {
                return p;
            }
        }
        return null;
    }
    
    /**
     * /eco <action> <player> <amount>
     * Example: /eco set Steve 1000
     */
    private static class EcoWithAmountCommand extends CommandBase {
        private final ConfigManager configManager;
        private final PlayerService playerService;
        private final RequiredArg<String> actionArg;
        private final RequiredArg<String> playerArg;
        private final RequiredArg<Double> amountArg;
        
        EcoWithAmountCommand(ConfigManager configManager, PlayerService playerService) {
            super("eco");
            this.configManager = configManager;
            this.playerService = playerService;
            this.actionArg = withRequiredArg("action", "set, add, or remove", ArgTypes.STRING);
            this.playerArg = withRequiredArg("player", "Player name (online or offline)", ArgTypes.STRING);
            this.amountArg = withRequiredArg("amount", "Amount of currency", ArgTypes.DOUBLE);
        }
        
        @Override
        protected boolean canGeneratePermission() {
            return false;
        }
        
        @Override
        protected void executeSync(@Nonnull CommandContext ctx) {
            PluginConfig.EconomyConfig economyConfig = configManager.getConfig().economy;
            
            if (!economyConfig.enabled) {
                ctx.sendMessage(Message.raw("Economy system is disabled.").color("#FF5555"));
                return;
            }
            
            // Check permission
            if (!PermissionService.get().canUseAdminCommand(ctx.sender(), Permissions.WALLET_ADMIN, economyConfig.enabled)) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("noPermission"), "#FF5555"));
                return;
            }
            
            String action = ctx.get(actionArg).toLowerCase();
            String playerName = ctx.get(playerArg);
            double amount = ctx.get(amountArg);
            
            // Validate action
            if (!action.equals("set") && !action.equals("add") && !action.equals("remove")) {
                ctx.sendMessage(Message.raw("Invalid action. Use: set, add, remove, or check").color("#FF5555"));
                return;
            }
            
            // Validate amount
            if (action.equals("set") && amount < 0) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("walletInvalidAmount"), "#FF5555"));
                return;
            }
            if ((action.equals("add") || action.equals("remove")) && amount <= 0) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("walletInvalidAmount"), "#FF5555"));
                return;
            }
            
            UUID targetId = findPlayerId(playerName);
            PlayerRef targetPlayer = findPlayerRef(playerName);
            if (targetId == null) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("playerNotFound", "player", playerName), "#FF5555"));
                return;
            }
            
            // Determine sender name (console or player name)
            String senderName = ctx.sender() instanceof PlayerRef ? 
                ((PlayerRef) ctx.sender()).getUsername() : "Server";
            
            switch (action) {
                case "set" -> {
                    if (playerService.setBalance(targetId, amount, targetPlayer, senderName)) {
                        ctx.sendMessage(MessageFormatter.formatWithFallback(
                            configManager.getMessage("walletSet",
                                "player", playerName,
                                "amount", EconomyAPI.format(amount),
                                "balance", EconomyAPI.format(amount)), "#55FF55"));
                    } else {
                        ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("walletFailed"), "#FF5555"));
                    }
                }
                case "add" -> {
                    if (playerService.addMoney(targetId, amount, targetPlayer, senderName)) {
                        double newBalance = playerService.getBalance(targetId);
                        ctx.sendMessage(MessageFormatter.formatWithFallback(
                            configManager.getMessage("walletAdded",
                                "player", playerName,
                                "amount", EconomyAPI.format(amount),
                                "balance", EconomyAPI.format(newBalance)), "#55FF55"));
                    } else {
                        ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("walletFailed"), "#FF5555"));
                    }
                }
                case "remove" -> {
                    if (playerService.removeMoney(targetId, amount, targetPlayer, senderName)) {
                        double newBalance = playerService.getBalance(targetId);
                        ctx.sendMessage(MessageFormatter.formatWithFallback(
                            configManager.getMessage("walletRemoved",
                                "player", playerName,
                                "amount", EconomyAPI.format(amount),
                                "balance", EconomyAPI.format(newBalance)), "#55FF55"));
                    } else {
                        ctx.sendMessage(MessageFormatter.formatWithFallback(
                            configManager.getMessage("walletInsufficientFunds", "player", playerName), "#FF5555"));
                    }
                }
            }
        }
        
        private UUID findPlayerId(String name) {
            for (PlayerRef p : Universe.get().getPlayers()) {
                if (p.getUsername().equalsIgnoreCase(name)) {
                    return p.getUuid();
                }
            }
            return playerService.getPlayerByName(name).map(d -> d.getUuid()).orElse(null);
        }
        
        private PlayerRef findPlayerRef(String name) {
            for (PlayerRef p : Universe.get().getPlayers()) {
                if (p.getUsername().equalsIgnoreCase(name)) {
                    return p;
                }
            }
            return null;
        }
    }
}
