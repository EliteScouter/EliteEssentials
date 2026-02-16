package com.eliteessentials.integration;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.services.PlayerService;
import net.milkbowl.vault2.economy.AccountPermission;
import net.milkbowl.vault2.economy.Economy;
import net.milkbowl.vault2.economy.EconomyResponse;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.*;
import java.util.logging.Logger;

/**
 * EliteEssentials implementation of the VaultUnlocked Economy interface.
 * This allows other plugins to interact with EliteEssentials economy through VaultUnlocked.
 * 
 * Based on Ecotale's implementation pattern.
 */
public class EliteEssentialsEconomy implements Economy {
    
    private static final Logger logger = Logger.getLogger("EliteEssentials");
    
    private final ConfigManager configManager;
    private final PlayerService playerService;
    
    public EliteEssentialsEconomy(ConfigManager configManager, PlayerService playerService) {
        this.configManager = configManager;
        this.playerService = playerService;
    }
    
    @Override
    public boolean isEnabled() {
        return configManager.getConfig().economy.enabled;
    }
    
    @NotNull
    @Override
    public String getName() {
        return "EliteEssentials";
    }
    
    @Override
    public boolean hasSharedAccountSupport() {
        return false;
    }
    
    @Override
    public boolean hasMultiCurrencySupport() {
        return false;
    }
    
    @Override
    public int fractionalDigits(@NotNull String pluginName) {
        return 2;
    }
    
    @NotNull
    @Override
    public String format(@NotNull BigDecimal amount) {
        return format(getName(), amount);
    }
    
    @NotNull
    @Override
    public String format(@NotNull String pluginName, @NotNull BigDecimal amount) {
        String symbol = configManager.getConfig().economy.currencySymbol;
        return String.format("%s%.2f", symbol, amount.doubleValue());
    }
    
    @NotNull
    @Override
    public String format(@NotNull BigDecimal amount, @NotNull String currency) {
        return format(getName(), amount);
    }
    
    @NotNull
    @Override
    public String format(@NotNull String pluginName, @NotNull BigDecimal amount, @NotNull String currency) {
        return format(pluginName, amount);
    }
    
    @Override
    public boolean hasCurrency(@NotNull String currency) {
        String currencyName = configManager.getConfig().economy.currencyName;
        String currencyPlural = configManager.getConfig().economy.currencyNamePlural;
        return currency.equalsIgnoreCase(currencyName) || currency.equalsIgnoreCase(currencyPlural);
    }
    
    @NotNull
    @Override
    public String getDefaultCurrency(@NotNull String pluginName) {
        return configManager.getConfig().economy.currencyNamePlural;
    }
    
    @NotNull
    @Override
    public String defaultCurrencyNamePlural(@NotNull String pluginName) {
        return configManager.getConfig().economy.currencyNamePlural;
    }
    
    @NotNull
    @Override
    public String defaultCurrencyNameSingular(@NotNull String pluginName) {
        return configManager.getConfig().economy.currencyName;
    }
    
    @NotNull
    @Override
    public Collection<String> currencies() {
        return List.of(configManager.getConfig().economy.currencyNamePlural);
    }
    
    @Override
    public boolean createAccount(@NotNull UUID accountID, @NotNull String name) {
        return createAccount(accountID, name, true);
    }
    
    @Override
    public boolean createAccount(@NotNull UUID accountID, @NotNull String name, boolean player) {
        if (!player) {
            return false; // Only support player accounts
        }
        
        // Account is created automatically when player joins
        return playerService.getPlayer(accountID).isPresent();
    }
    
    @Override
    public boolean createAccount(@NotNull UUID accountID, @NotNull String name, @NotNull String worldName) {
        return createAccount(accountID, name, true);
    }
    
    @Override
    public boolean createAccount(@NotNull UUID accountID, @NotNull String name, @NotNull String worldName, boolean player) {
        return createAccount(accountID, name, player);
    }
    
    @NotNull
    @Override
    public Map<UUID, String> getUUIDNameMap() {
        // EliteEssentials stores names in PlayerData, but we don't expose them here
        return Collections.emptyMap();
    }
    
    @Override
    public Optional<String> getAccountName(@NotNull UUID accountID) {
        return playerService.getPlayer(accountID).map(p -> p.getName());
    }
    
    @Override
    public boolean hasAccount(@NotNull UUID accountID) {
        return playerService.getPlayer(accountID).isPresent();
    }
    
    @Override
    public boolean hasAccount(@NotNull UUID accountID, @NotNull String worldName) {
        return hasAccount(accountID);
    }
    
    @Override
    public boolean renameAccount(@NotNull UUID accountID, @NotNull String name) {
        // Names are updated automatically when player joins
        return true;
    }
    
    @Override
    public boolean renameAccount(@NotNull String pluginName, @NotNull UUID accountID, @NotNull String name) {
        return renameAccount(accountID, name);
    }
    
    @Override
    public boolean deleteAccount(@NotNull String pluginName, @NotNull UUID accountID) {
        // We don't support deleting accounts
        return false;
    }
    
    @Override
    public boolean accountSupportsCurrency(@NotNull String pluginName, @NotNull UUID accountID, @NotNull String currency) {
        return hasCurrency(currency);
    }
    
    @Override
    public boolean accountSupportsCurrency(@NotNull String pluginName, @NotNull UUID accountID, @NotNull String currency, @NotNull String world) {
        return hasCurrency(currency);
    }
    
    @NotNull
    @Override
    public BigDecimal getBalance(@NotNull String pluginName, @NotNull UUID accountID) {
        return BigDecimal.valueOf(playerService.getBalance(accountID));
    }
    
    @NotNull
    @Override
    public BigDecimal getBalance(@NotNull String pluginName, @NotNull UUID accountID, @NotNull String world) {
        return getBalance(pluginName, accountID);
    }
    
    @NotNull
    @Override
    public BigDecimal getBalance(@NotNull String pluginName, @NotNull UUID accountID, @NotNull String world, @NotNull String currency) {
        return getBalance(pluginName, accountID);
    }
    
    @Override
    public boolean has(@NotNull String pluginName, @NotNull UUID accountID, @NotNull BigDecimal amount) {
        double balance = playerService.getBalance(accountID);
        return balance >= amount.doubleValue();
    }
    
    @Override
    public boolean has(@NotNull String pluginName, @NotNull UUID accountID, @NotNull String worldName, @NotNull BigDecimal amount) {
        return has(pluginName, accountID, amount);
    }
    
    @Override
    public boolean has(@NotNull String pluginName, @NotNull UUID accountID, @NotNull String worldName, @NotNull String currency, @NotNull BigDecimal amount) {
        return has(pluginName, accountID, amount);
    }
    
    @NotNull
    @Override
    public EconomyResponse withdraw(@NotNull String pluginName, @NotNull UUID accountID, @NotNull BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            return new EconomyResponse(
                BigDecimal.ZERO,
                getBalance(pluginName, accountID),
                EconomyResponse.ResponseType.FAILURE,
                "Cannot withdraw negative amount"
            );
        }
        
        double balance = playerService.getBalance(accountID);
        if (balance < amount.doubleValue()) {
            return new EconomyResponse(
                BigDecimal.ZERO,
                BigDecimal.valueOf(balance),
                EconomyResponse.ResponseType.FAILURE,
                "Insufficient funds"
            );
        }
        
        boolean success = playerService.removeMoney(accountID, amount.doubleValue(), null, null);
        EconomyResponse.ResponseType status = success ? 
            EconomyResponse.ResponseType.SUCCESS : 
            EconomyResponse.ResponseType.FAILURE;
        
        return new EconomyResponse(
            amount,
            getBalance(pluginName, accountID),
            status,
            success ? null : "Transaction failed"
        );
    }
    
    @NotNull
    @Override
    public EconomyResponse withdraw(@NotNull String pluginName, @NotNull UUID accountID, @NotNull String worldName, @NotNull BigDecimal amount) {
        return withdraw(pluginName, accountID, amount);
    }
    
    @NotNull
    @Override
    public EconomyResponse withdraw(@NotNull String pluginName, @NotNull UUID accountID, @NotNull String worldName, @NotNull String currency, @NotNull BigDecimal amount) {
        return withdraw(pluginName, accountID, amount);
    }
    
    @NotNull
    @Override
    public EconomyResponse deposit(@NotNull String pluginName, @NotNull UUID accountID, @NotNull BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            return new EconomyResponse(
                BigDecimal.ZERO,
                getBalance(pluginName, accountID),
                EconomyResponse.ResponseType.FAILURE,
                "Cannot deposit negative amount"
            );
        }
        
        boolean success = playerService.addMoney(accountID, amount.doubleValue(), null, null);
        EconomyResponse.ResponseType status = success ? 
            EconomyResponse.ResponseType.SUCCESS : 
            EconomyResponse.ResponseType.FAILURE;
        
        return new EconomyResponse(
            amount,
            getBalance(pluginName, accountID),
            status,
            success ? null : "Transaction failed"
        );
    }
    
    @NotNull
    @Override
    public EconomyResponse deposit(@NotNull String pluginName, @NotNull UUID accountID, @NotNull String worldName, @NotNull BigDecimal amount) {
        return deposit(pluginName, accountID, amount);
    }
    
    @NotNull
    @Override
    public EconomyResponse deposit(@NotNull String pluginName, @NotNull UUID accountID, @NotNull String worldName, @NotNull String currency, @NotNull BigDecimal amount) {
        return deposit(pluginName, accountID, amount);
    }
    
    // Shared account methods - not supported
    
    @Override
    public boolean createSharedAccount(@NotNull String pluginName, @NotNull UUID accountID, @NotNull String name, @NotNull UUID owner) {
        return false;
    }
    
    @Override
    public boolean isAccountOwner(@NotNull String pluginName, @NotNull UUID accountID, @NotNull UUID uuid) {
        return false;
    }
    
    @Override
    public boolean setOwner(@NotNull String pluginName, @NotNull UUID accountID, @NotNull UUID uuid) {
        return false;
    }
    
    @Override
    public boolean isAccountMember(@NotNull String pluginName, @NotNull UUID accountID, @NotNull UUID uuid) {
        return false;
    }
    
    @Override
    public boolean addAccountMember(@NotNull String pluginName, @NotNull UUID accountID, @NotNull UUID uuid) {
        return false;
    }
    
    @Override
    public boolean addAccountMember(@NotNull String pluginName, @NotNull UUID accountID, @NotNull UUID uuid, @NotNull AccountPermission... initialPermissions) {
        return false;
    }
    
    @Override
    public boolean removeAccountMember(@NotNull String pluginName, @NotNull UUID accountID, @NotNull UUID uuid) {
        return false;
    }
    
    @Override
    public boolean hasAccountPermission(@NotNull String pluginName, @NotNull UUID accountID, @NotNull UUID uuid, @NotNull AccountPermission permission) {
        return false;
    }
    
    @Override
    public boolean updateAccountPermission(@NotNull String pluginName, @NotNull UUID accountID, @NotNull UUID uuid, @NotNull AccountPermission permission, boolean value) {
        return false;
    }
}
