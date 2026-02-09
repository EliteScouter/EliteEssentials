package com.eliteessentials.api;

import com.eliteessentials.EliteEssentials;
import com.eliteessentials.integration.VaultUnlockedIntegration;
import com.eliteessentials.model.PlayerFile;
import com.eliteessentials.services.PlayerService;

import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Public API for EliteEssentials Economy system.
 * Other mods can use this to interact with player wallets.
 * 
 * Supports VaultUnlocked integration:
 * - When useExternalEconomy is enabled, delegates to external economy via VaultUnlocked
 * - When vaultUnlockedProvider is enabled, other plugins can use VaultUnlocked to access our economy
 * 
 * Usage from other mods:
 * <pre>
 * // Check if economy is enabled
 * if (EconomyAPI.isEnabled()) {
 *     // Get balance
 *     double balance = EconomyAPI.getBalance(playerUuid);
 *     
 *     // Check if player can afford something
 *     if (EconomyAPI.has(playerUuid, 100.0)) {
 *         // Withdraw money
 *         if (EconomyAPI.withdraw(playerUuid, 100.0)) {
 *             // Purchase successful
 *         }
 *     }
 *     
 *     // Give money
 *     EconomyAPI.deposit(playerUuid, 50.0);
 * }
 * </pre>
 */
public final class EconomyAPI {

    private static final Logger logger = Logger.getLogger("EliteEssentials");

    private EconomyAPI() {} // Static API class

    /**
     * Check if the economy system is enabled.
     * Always check this before using other API methods.
     */
    public static boolean isEnabled() {
        EliteEssentials plugin = EliteEssentials.getInstance();
        if (plugin == null || plugin.getConfigManager() == null) {
            return false;
        }
        return plugin.getConfigManager().getConfig().economy.enabled;
    }
    
    /**
     * Check if we're using an external economy via VaultUnlocked.
     * Returns true if:
     * 1. VaultUnlocked integration detected an external economy, OR
     * 2. Config has useExternalEconomy=true (even if detection hasn't completed yet)
     */
    public static boolean isUsingExternalEconomy() {
        try {
            // First check if VaultUnlocked integration has confirmed external economy
            VaultUnlockedIntegration vault = VaultUnlockedIntegration.get();
            if (vault != null && vault.isUsingExternalEconomy()) {
                return true;
            }
            
            // Also check config - if useExternalEconomy is true, we should try to use it
            // even if detection hasn't completed yet (will fail gracefully)
            EliteEssentials plugin = EliteEssentials.getInstance();
            if (plugin != null && plugin.getConfigManager() != null) {
                return plugin.getConfigManager().getConfig().economy.useExternalEconomy;
            }
        } catch (NoClassDefFoundError e) {
            // VaultUnlocked classes not available - fall back to internal economy
            logger.warning("[Economy] VaultUnlocked classes not found - using internal economy. Error: " + e.getMessage());
        }
        
        return false;
    }

    /**
     * Get the currency name (singular).
     */
    public static String getCurrencyName() {
        EliteEssentials plugin = EliteEssentials.getInstance();
        if (plugin == null) return "coin";
        return plugin.getConfigManager().getConfig().economy.currencyName;
    }

    /**
     * Get the currency name (plural).
     */
    public static String getCurrencyNamePlural() {
        EliteEssentials plugin = EliteEssentials.getInstance();
        if (plugin == null) return "coins";
        return plugin.getConfigManager().getConfig().economy.currencyNamePlural;
    }

    /**
     * Get the currency symbol.
     */
    public static String getCurrencySymbol() {
        EliteEssentials plugin = EliteEssentials.getInstance();
        if (plugin == null) return "$";
        return plugin.getConfigManager().getConfig().economy.currencySymbol;
    }

    /**
     * Get a player's balance.
     * @param playerId Player UUID
     * @return Balance, or 0.0 if player not found or economy disabled
     */
    public static double getBalance(UUID playerId) {
        if (!isEnabled()) return 0.0;
        
        try {
            // Check for external economy first
            VaultUnlockedIntegration vault = VaultUnlockedIntegration.get();
            if (vault != null && vault.isUsingExternalEconomy()) {
                return vault.getExternalBalance(playerId);
            }
        } catch (NoClassDefFoundError e) {
            // VaultUnlocked classes not available - fall back to internal economy
            logger.warning("[Economy] VaultUnlocked classes not found during getBalance - using internal economy");
        }
        
        PlayerService service = getPlayerService();
        if (service == null) return 0.0;
        
        return service.getBalance(playerId);
    }

    /**
     * Check if a player has at least the specified amount.
     * @param playerId Player UUID
     * @param amount Amount to check
     * @return true if player has enough, false otherwise
     */
    public static boolean has(UUID playerId, double amount) {
        if (!isEnabled()) return false;
        
        try {
            // Check for external economy first
            VaultUnlockedIntegration vault = VaultUnlockedIntegration.get();
            if (vault != null && vault.isUsingExternalEconomy()) {
                return vault.externalHas(playerId, amount);
            }
        } catch (NoClassDefFoundError e) {
            // VaultUnlocked classes not available - fall back to internal economy
            logger.warning("[Economy] VaultUnlocked classes not found during has() check - using internal economy");
        }
        
        return getBalance(playerId) >= amount;
    }

    /**
     * Withdraw money from a player's wallet.
     * @param playerId Player UUID
     * @param amount Amount to withdraw (must be positive)
     * @return true if successful, false if insufficient funds or error
     */
    public static boolean withdraw(UUID playerId, double amount) {
        if (!isEnabled() || amount <= 0) return false;
        
        try {
            // Check for external economy first
            VaultUnlockedIntegration vault = VaultUnlockedIntegration.get();
            if (vault != null && vault.isUsingExternalEconomy()) {
                return vault.externalWithdraw(playerId, amount);
            }
        } catch (NoClassDefFoundError e) {
            // VaultUnlocked classes not available - fall back to internal economy
            logger.warning("[Economy] VaultUnlocked classes not found during withdraw - using internal economy");
        }
        
        PlayerService service = getPlayerService();
        if (service == null) return false;
        
        return service.removeMoney(playerId, amount);
    }

    /**
     * Deposit money into a player's wallet.
     * @param playerId Player UUID
     * @param amount Amount to deposit (must be positive)
     * @return true if successful, false if error
     */
    public static boolean deposit(UUID playerId, double amount) {
        if (!isEnabled() || amount <= 0) return false;
        
        try {
            // Check for external economy first
            VaultUnlockedIntegration vault = VaultUnlockedIntegration.get();
            if (vault != null && vault.isUsingExternalEconomy()) {
                return vault.externalDeposit(playerId, amount);
            }
        } catch (NoClassDefFoundError e) {
            // VaultUnlocked classes not available - fall back to internal economy
            logger.warning("[Economy] VaultUnlocked classes not found during deposit - using internal economy");
        }
        
        PlayerService service = getPlayerService();
        if (service == null) return false;
        
        return service.addMoney(playerId, amount);
    }

    /**
     * Set a player's balance directly.
     * Note: Not supported when using external economy.
     * @param playerId Player UUID
     * @param amount New balance (must be non-negative)
     * @return true if successful, false if error or using external economy
     */
    public static boolean setBalance(UUID playerId, double amount) {
        if (!isEnabled() || amount < 0) return false;
        
        try {
            // External economy doesn't support direct balance setting
            VaultUnlockedIntegration vault = VaultUnlockedIntegration.get();
            if (vault != null && vault.isUsingExternalEconomy()) {
                logger.warning("setBalance() not supported with external economy");
                return false;
            }
        } catch (NoClassDefFoundError e) {
            // VaultUnlocked classes not available - fall back to internal economy
            logger.warning("[Economy] VaultUnlocked classes not found during setBalance - using internal economy");
        }
        
        PlayerService service = getPlayerService();
        if (service == null) return false;
        
        return service.setBalance(playerId, amount);
    }

    /**
     * Transfer money between two players.
     * @param from Sender UUID
     * @param to Receiver UUID
     * @param amount Amount to transfer
     * @return true if successful, false if insufficient funds or error
     */
    public static boolean transfer(UUID from, UUID to, double amount) {
        if (!isEnabled() || amount <= 0) return false;
        if (from.equals(to)) return false;
        
        // Check if sender has enough
        if (!has(from, amount)) return false;
        
        // Perform transfer
        if (withdraw(from, amount)) {
            if (deposit(to, amount)) {
                return true;
            } else {
                // Rollback if deposit fails
                deposit(from, amount);
                return false;
            }
        }
        return false;
    }

    /**
     * Format an amount with the currency symbol.
     * @param amount Amount to format
     * @return Formatted string (e.g., "$100.00")
     */
    public static String format(double amount) {
        EliteEssentials plugin = EliteEssentials.getInstance();
        if (plugin == null) return String.format("$%.2f", amount);
        
        String symbol = plugin.getConfigManager().getConfig().economy.currencySymbol;
        return String.format("%s%.2f", symbol, amount);
    }

    /**
     * Format an amount with the currency name.
     * @param amount Amount to format
     * @return Formatted string (e.g., "100.00 coins")
     */
    public static String formatWithName(double amount) {
        EliteEssentials plugin = EliteEssentials.getInstance();
        if (plugin == null) return String.format("%.2f coins", amount);
        
        String name = amount == 1.0 ? getCurrencyName() : getCurrencyNamePlural();
        return String.format("%.2f %s", amount, name);
    }

    /**
     * Check if a player exists in the economy system.
     * @param playerId Player UUID
     * @return true if player has an account
     */
    public static boolean hasAccount(UUID playerId) {
        if (!isEnabled()) return false;
        
        PlayerService service = getPlayerService();
        if (service == null) return false;
        
        return service.getPlayer(playerId).isPresent();
    }

    /**
     * Get player data (for advanced usage).
     * Note: Returns empty when using external economy.
     * @param playerId Player UUID
     * @return Optional containing PlayerFile if found
     */
    public static Optional<PlayerFile> getPlayerData(UUID playerId) {
        try {
            // External economy doesn't expose player data
            VaultUnlockedIntegration vault = VaultUnlockedIntegration.get();
            if (vault != null && vault.isUsingExternalEconomy()) {
                return Optional.empty();
            }
        } catch (NoClassDefFoundError e) {
            // VaultUnlocked classes not available - fall back to internal economy
            logger.warning("[Economy] VaultUnlocked classes not found during getPlayerData - using internal economy");
        }
        
        PlayerService service = getPlayerService();
        if (service == null) return Optional.empty();
        
        return service.getPlayer(playerId);
    }

    private static PlayerService getPlayerService() {
        EliteEssentials plugin = EliteEssentials.getInstance();
        if (plugin == null) return null;
        return plugin.getPlayerService();
    }
}
