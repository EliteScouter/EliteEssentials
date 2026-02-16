package com.eliteessentials.commands.hytale;

import com.eliteessentials.api.EconomyAPI;
import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.config.PluginConfig;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.services.PlayerService;
import com.eliteessentials.util.CommandPermissionUtil;
import com.eliteessentials.util.MessageFormatter;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * Command: /wallet
 * View your balance or manage other players' wallets.
 * 
 * Usage:
 * - /wallet - View your balance
 * - /wallet <player> - View another player's balance (admin)
 * - /wallet set <player> <amount> - Set player's balance (admin)
 * - /wallet add <player> <amount> - Add to player's balance (admin)
 * - /wallet remove <player> <amount> - Remove from player's balance (admin)
 * 
 * Examples:
 * - /wallet
 * - /wallet Steve
 * - /wallet set Steve 1000
 * - /wallet add Steve 500
 */
public class HytaleWalletCommand extends AbstractPlayerCommand {

    private static final String COMMAND_NAME = "wallet";
    
    private final ConfigManager configManager;
    private final PlayerService playerService;

    public HytaleWalletCommand(ConfigManager configManager, PlayerService playerService) {
        super(COMMAND_NAME, "View your wallet balance or manage others");
        this.configManager = configManager;
        this.playerService = playerService;
        
        // Add subcommand variants
        addUsageVariant(new WalletViewOtherCommand(configManager, playerService));
        addUsageVariant(new WalletAdminCommand(configManager, playerService));
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                          @Nonnull PlayerRef player, @Nonnull World world) {
        PluginConfig.EconomyConfig economyConfig = configManager.getConfig().economy;
        UUID playerId = player.getUuid();
        
        // Check if economy is enabled
        if (!economyConfig.enabled) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("commandDisabled"), "#FF5555"));
            return;
        }
        
        // Check permission
        if (!CommandPermissionUtil.canExecute(ctx, player, Permissions.WALLET, economyConfig.enabled)) {
            return;
        }
        
        double balance = playerService.getBalance(playerId);
        ctx.sendMessage(MessageFormatter.formatWithFallback(
            configManager.getMessage("walletBalance", 
                "balance", EconomyAPI.format(balance),
                "currency", EconomyAPI.getCurrencyNamePlural()), "#55FF55"));
    }
    
    /**
     * /wallet <player> - View another player's balance
     * Example: /wallet Steve
     */
    private static class WalletViewOtherCommand extends AbstractPlayerCommand {
        private final ConfigManager configManager;
        private final PlayerService playerService;
        private final RequiredArg<String> targetArg;
        
        WalletViewOtherCommand(ConfigManager configManager, PlayerService playerService) {
            super(COMMAND_NAME);
            this.configManager = configManager;
            this.playerService = playerService;
            this.targetArg = withRequiredArg("player", "Player name to view balance", ArgTypes.STRING);
        }
        
        @Override
        protected boolean canGeneratePermission() {
            return false;
        }
        
        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                              @Nonnull PlayerRef player, @Nonnull World world) {
            PluginConfig.EconomyConfig economyConfig = configManager.getConfig().economy;
            
            if (!economyConfig.enabled) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("commandDisabled"), "#FF5555"));
                return;
            }
            
            String targetName = ctx.get(targetArg);
            
            // Check if it's an admin action keyword
            if (targetName.equalsIgnoreCase("set") || targetName.equalsIgnoreCase("add") || targetName.equalsIgnoreCase("remove")) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("walletAdminUsage"), "#FFAA00"));
                return;
            }
            
            if (!CommandPermissionUtil.canExecuteAdmin(ctx, player, Permissions.WALLET_OTHERS, economyConfig.enabled)) {
                return;
            }
            
            UUID targetId = findPlayerId(targetName, playerService);
            
            if (targetId == null) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("playerNotFound", "player", targetName), "#FF5555"));
                return;
            }
            
            double balance = playerService.getBalance(targetId);
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("walletBalanceOther",
                    "player", targetName,
                    "balance", EconomyAPI.format(balance),
                    "currency", EconomyAPI.getCurrencyNamePlural()), "#55FF55"));
        }
    }
    
    /**
     * /wallet <action> <player> <amount> - Admin command for set/add/remove
     * Example: /wallet set Steve 1000
     */
    private static class WalletAdminCommand extends AbstractPlayerCommand {
        private final ConfigManager configManager;
        private final PlayerService playerService;
        private final RequiredArg<String> actionArg;
        private final RequiredArg<String> targetArg;
        private final RequiredArg<Double> amountArg;
        
        WalletAdminCommand(ConfigManager configManager, PlayerService playerService) {
            super(COMMAND_NAME);
            this.configManager = configManager;
            this.playerService = playerService;
            this.actionArg = withRequiredArg("action", "set, add, or remove", ArgTypes.STRING);
            this.targetArg = withRequiredArg("player", "Player name (online or offline)", ArgTypes.STRING);
            this.amountArg = withRequiredArg("amount", "Amount of currency", ArgTypes.DOUBLE);
        }
        
        @Override
        protected boolean canGeneratePermission() {
            return false;
        }
        
        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                              @Nonnull PlayerRef player, @Nonnull World world) {
            PluginConfig.EconomyConfig economyConfig = configManager.getConfig().economy;
            
            if (!economyConfig.enabled) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("commandDisabled"), "#FF5555"));
                return;
            }
            
            if (!CommandPermissionUtil.canExecuteAdmin(ctx, player, Permissions.WALLET_ADMIN, economyConfig.enabled)) {
                return;
            }
            
            String action = ctx.get(actionArg).toLowerCase();
            String targetName = ctx.get(targetArg);
            double amount = ctx.get(amountArg);
            
            // Validate action
            if (!action.equals("set") && !action.equals("add") && !action.equals("remove")) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("walletAdminUsage"), "#FFAA00"));
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
            
            UUID targetId = findPlayerId(targetName, playerService);
            PlayerRef targetPlayer = findPlayerRef(targetName);
            if (targetId == null) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("playerNotFound", "player", targetName), "#FF5555"));
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
                                "player", targetName,
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
                                "player", targetName,
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
                                "player", targetName,
                                "amount", EconomyAPI.format(amount),
                                "balance", EconomyAPI.format(newBalance)), "#55FF55"));
                    } else {
                        ctx.sendMessage(MessageFormatter.formatWithFallback(
                            configManager.getMessage("walletInsufficientFunds", "player", targetName), "#FF5555"));
                    }
                }
            }
        }
    }
    
    /**
     * Helper to find player UUID by name (online or offline).
     */
    private static UUID findPlayerId(String name, PlayerService playerService) {
        // Check online players first
        for (PlayerRef p : Universe.get().getPlayers()) {
            if (p.getUsername().equalsIgnoreCase(name)) {
                return p.getUuid();
            }
        }
        // Check offline players in cache
        return playerService.getPlayerByName(name).map(d -> d.getUuid()).orElse(null);
    }
    
    /**
     * Helper to find player reference by name (online only).
     */
    private static PlayerRef findPlayerRef(String name) {
        for (PlayerRef p : Universe.get().getPlayers()) {
            if (p.getUsername().equalsIgnoreCase(name)) {
                return p;
            }
        }
        return null;
    }
}
