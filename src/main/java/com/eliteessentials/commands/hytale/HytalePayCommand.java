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
 * Command: /pay <player> <amount>
 * Transfer money to another online player.
 * 
 * Usage: /pay <player> <amount>
 * Example: /pay Steve 100
 * 
 * Note: Target player must be online. Minimum payment amount is configurable.
 */
public class HytalePayCommand extends AbstractPlayerCommand {

    private static final String COMMAND_NAME = "pay";
    
    private final RequiredArg<String> targetArg;
    private final RequiredArg<Double> amountArg;
    
    private final ConfigManager configManager;
    private final PlayerService playerService;

    public HytalePayCommand(ConfigManager configManager, PlayerService playerService) {
        super(COMMAND_NAME, "Send money to another player");
        this.configManager = configManager;
        this.playerService = playerService;
        
        this.targetArg = withRequiredArg("player", "Player to pay (must be online)", ArgTypes.STRING);
        this.amountArg = withRequiredArg("amount", "Amount to send", ArgTypes.DOUBLE);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                          @Nonnull PlayerRef player, @Nonnull World world) {
        PluginConfig.EconomyConfig economyConfig = configManager.getConfig().economy;
        UUID senderId = player.getUuid();
        
        // Check if economy is enabled
        if (!economyConfig.enabled) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("commandDisabled"), "#FF5555"));
            return;
        }
        
        // Check permission
        if (!CommandPermissionUtil.canExecute(ctx, player, Permissions.PAY, economyConfig.enabled)) {
            return;
        }
        
        String targetName = ctx.get(targetArg);
        double amount = ctx.get(amountArg);
        
        // Validate amount
        if (amount <= 0) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("payInvalidAmount"), "#FF5555"));
            return;
        }
        
        // Check minimum payment
        if (amount < economyConfig.minPayment) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("payMinimum", "amount", EconomyAPI.format(economyConfig.minPayment)), "#FF5555"));
            return;
        }
        
        // Find target player
        PlayerRef targetPlayer = null;
        for (PlayerRef p : Universe.get().getPlayers()) {
            if (p.getUsername().equalsIgnoreCase(targetName)) {
                targetPlayer = p;
                break;
            }
        }
        
        if (targetPlayer == null) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("playerNotFound", "player", targetName), "#FF5555"));
            return;
        }
        
        UUID targetId = targetPlayer.getUuid();
        
        // Can't pay yourself
        if (senderId.equals(targetId)) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("paySelf"), "#FF5555"));
            return;
        }
        
        // Check if sender has enough
        double senderBalance = playerService.getBalance(senderId);
        if (senderBalance < amount) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("payInsufficientFunds", "balance", EconomyAPI.format(senderBalance)), "#FF5555"));
            return;
        }
        
        // Perform transfer
        if (EconomyAPI.transfer(senderId, targetId, amount)) {
            // Notify sender
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("paySent", 
                    "amount", EconomyAPI.format(amount),
                    "player", targetPlayer.getUsername()), "#55FF55"));
            
            // Notify receiver
            targetPlayer.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("payReceived",
                    "amount", EconomyAPI.format(amount),
                    "player", player.getUsername()), "#55FF55"));
            
            // Notify player of balance change if configured
            // For /pay, sender is the player running the command, receiver is targetPlayer
            playerService.notifyBalanceChange(senderId, playerService.getBalance(senderId) + amount, playerService.getBalance(senderId), player, player.getUsername());
            playerService.notifyBalanceChange(targetId, playerService.getBalance(targetId) - amount, playerService.getBalance(targetId), targetPlayer, player.getUsername());
        } else {
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("payFailed"), "#FF5555"));
        }
    }
}
