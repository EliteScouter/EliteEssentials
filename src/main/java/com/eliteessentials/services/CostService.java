package com.eliteessentials.services;

import com.eliteessentials.api.EconomyAPI;
import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.permissions.PermissionService;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.util.MessageFormatter;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.util.UUID;
import java.util.logging.Logger;

/**
 * Service for handling command costs.
 * Integrates with the economy system to charge players for using commands.
 * 
 * Features:
 * - Configurable cost per command
 * - Bypass permission support (eliteessentials.bypass.cost.*)
 * - Admin bypass (admins never pay)
 * - Graceful handling when economy is disabled
 */
public class CostService {

    private static final Logger logger = Logger.getLogger("EliteEssentials");
    
    private final ConfigManager configManager;

    public CostService(ConfigManager configManager) {
        this.configManager = configManager;
    }

    /**
     * Check if a player can afford a command and charge them if so.
     * Returns true if the command can proceed (free, bypassed, or paid).
     * Returns false if the player cannot afford it.
     * 
     * @param ctx Command context for sending messages
     * @param player Player to charge
     * @param commandName Command name for bypass permission check
     * @param cost Cost amount from config (0 = free)
     * @return true if command can proceed, false if insufficient funds
     */
    public boolean chargeIfNeeded(CommandContext ctx, PlayerRef player, String commandName, double cost) {
        UUID playerId = player.getUuid();
        
        // Get effective cost (considers bypass and permission-based costs)
        double effectiveCost = getEffectiveCost(playerId, commandName, cost);
        
        // No cost = always proceed
        if (effectiveCost <= 0) {
            return true;
        }
        
        // Economy disabled = always proceed (free)
        if (!EconomyAPI.isEnabled()) {
            return true;
        }
        
        // Check if player can afford
        double balance = EconomyAPI.getBalance(playerId);
        if (balance < effectiveCost) {
            String currency = EconomyAPI.getCurrencyNamePlural();
            String message = configManager.getMessage("costInsufficientFunds",
                "cost", String.format("%.2f", effectiveCost),
                "balance", String.format("%.2f", balance),
                "currency", currency);
            ctx.sendMessage(MessageFormatter.formatWithFallback(message, "#FF5555"));
            return false;
        }
        
        // Charge the player
        if (EconomyAPI.withdraw(playerId, effectiveCost)) {
            String currency = effectiveCost == 1.0 ? EconomyAPI.getCurrencyName() : EconomyAPI.getCurrencyNamePlural();
            String message = configManager.getMessage("costCharged",
                "cost", String.format("%.2f", effectiveCost),
                "currency", currency);
            ctx.sendMessage(MessageFormatter.formatWithFallback(message, "#AAAAAA"));
            
            if (configManager.isDebugEnabled()) {
                logger.info("Charged " + playerId + " " + effectiveCost + " for " + commandName);
            }
            return true;
        } else {
            // Withdraw failed for some reason
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("costFailed"), "#FF5555"));
            return false;
        }
    }

    /**
     * Check if a player can bypass cost for a command.
     * Admins always bypass. Otherwise checks specific and general bypass permissions.
     */
    public boolean canBypassCost(UUID playerId, String commandName) {
        PermissionService perms = PermissionService.get();
        
        // Admins always bypass
        if (perms.isAdmin(playerId)) {
            return true;
        }
        
        // Check general bypass permission
        if (perms.hasPermission(playerId, Permissions.BYPASS_COST)) {
            return true;
        }
        
        // Check command-specific bypass permission
        if (perms.hasPermission(playerId, Permissions.bypassCost(commandName))) {
            return true;
        }
        
        return false;
    }

    /**
     * Get the effective cost for a player (0 if they can bypass, or permission-based cost).
     * 
     * Priority:
     * 1. Bypass permission (returns 0)
     * 2. Permission-based cost (eliteessentials.cost.<command>.<amount>)
     * 3. Config default
     */
    public double getEffectiveCost(UUID playerId, String commandName, double configCost) {
        if (canBypassCost(playerId, commandName)) {
            return 0.0;
        }
        // Check for permission-based cost override
        return PermissionService.get().getCommandCost(playerId, commandName, configCost);
    }

    /**
     * Check if a player can afford a cost without charging.
     * Useful for showing cost info before executing.
     * Uses effective cost (considers permission-based costs).
     */
    public boolean canAfford(UUID playerId, String commandName, double configCost) {
        double effectiveCost = getEffectiveCost(playerId, commandName, configCost);
        if (effectiveCost <= 0 || !EconomyAPI.isEnabled()) {
            return true;
        }
        return EconomyAPI.getBalance(playerId) >= effectiveCost;
    }
    
    /**
     * Check if a player can afford a raw cost without permission checks.
     * Useful for GUI displays where you just need balance check.
     */
    public boolean canAffordRaw(UUID playerId, double cost) {
        if (cost <= 0 || !EconomyAPI.isEnabled()) {
            return true;
        }
        return EconomyAPI.getBalance(playerId) >= cost;
    }

    /**
     * Check if a player can afford a command cost (considering bypass and permission-based costs).
     * Does NOT charge - just checks if they can proceed.
     * 
     * @param ctx Command context for sending messages
     * @param player Player to check
     * @param commandName Command name for bypass permission check
     * @param cost Cost amount from config (0 = free)
     * @return true if player can afford or bypasses, false if insufficient funds
     */
    public boolean checkCanAfford(CommandContext ctx, PlayerRef player, String commandName, double cost) {
        return checkCanAfford(ctx, player, commandName, cost, false);
    }
    
    /**
     * Check if a player can afford a command cost (considering bypass and permission-based costs).
     * Does NOT charge - just checks if they can proceed.
     * 
     * @param ctx Command context for sending messages
     * @param player Player to check
     * @param commandName Command name for bypass permission check
     * @param cost Cost amount from config (0 = free)
     * @param silent If true, suppresses error messages
     * @return true if player can afford or bypasses, false if insufficient funds
     */
    public boolean checkCanAfford(CommandContext ctx, PlayerRef player, String commandName, double cost, boolean silent) {
        UUID playerId = player.getUuid();
        
        // Get effective cost (considers bypass and permission-based costs)
        double effectiveCost = getEffectiveCost(playerId, commandName, cost);
        
        // No cost = always proceed
        if (effectiveCost <= 0) {
            return true;
        }
        
        // Economy disabled = always proceed (free)
        if (!EconomyAPI.isEnabled()) {
            return true;
        }
        
        // Check if player can afford
        double balance = EconomyAPI.getBalance(playerId);
        if (balance < effectiveCost) {
            if (!silent) {
                String currency = EconomyAPI.getCurrencyNamePlural();
                String message = configManager.getMessage("costInsufficientFunds",
                    "cost", String.format("%.2f", effectiveCost),
                    "balance", String.format("%.2f", balance),
                    "currency", currency);
                ctx.sendMessage(MessageFormatter.formatWithFallback(message, "#FF5555"));
            }
            return false;
        }
        
        return true;
    }

    /**
     * Charge a player for a command (call after successful execution).
     * Assumes checkCanAfford was already called.
     * 
     * @param ctx Command context for sending messages
     * @param player Player to charge
     * @param commandName Command name for bypass permission check
     * @param cost Cost amount from config (0 = free)
     * @return true if charged successfully or bypassed, false if failed
     */
    public boolean charge(CommandContext ctx, PlayerRef player, String commandName, double cost) {
        UUID playerId = player.getUuid();
        
        // Get effective cost (considers bypass and permission-based costs)
        double effectiveCost = getEffectiveCost(playerId, commandName, cost);
        
        // No cost = nothing to charge
        if (effectiveCost <= 0) {
            return true;
        }
        
        // Economy disabled = nothing to charge
        if (!EconomyAPI.isEnabled()) {
            return true;
        }
        
        // Charge the player
        if (EconomyAPI.withdraw(playerId, effectiveCost)) {
            String currency = effectiveCost == 1.0 ? EconomyAPI.getCurrencyName() : EconomyAPI.getCurrencyNamePlural();
            String message = configManager.getMessage("costCharged",
                "cost", String.format("%.2f", effectiveCost),
                "currency", currency);
            ctx.sendMessage(MessageFormatter.formatWithFallback(message, "#AAAAAA"));
            
            if (configManager.isDebugEnabled()) {
                logger.info("Charged " + playerId + " " + effectiveCost + " for " + commandName);
            }
            return true;
        } else {
            // Withdraw failed for some reason
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("costFailed"), "#FF5555"));
            return false;
        }
    }

    /**
     * Format a cost for display.
     */
    public String formatCost(double cost) {
        if (!EconomyAPI.isEnabled() || cost <= 0) {
            return "Free";
        }
        return EconomyAPI.format(cost);
    }
}
