package com.eliteessentials.integration;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.services.PlayerService;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.*;
import java.util.logging.Logger;

/**
 * Integration with VaultUnlocked API for cross-plugin economy support.
 * 
 * VaultUnlocked provides a unified abstraction layer for economy, permissions, and chat.
 * This integration allows EliteEssentials to:
 * 1. Register as an economy provider (other plugins can use our economy)
 * 2. Use an external economy plugin (we use another plugin's economy)
 * 
 * VaultUnlocked is optional - if not installed, we fall back to internal economy.
 * 
 * Note: This integration is fully reflection-based to allow building with JVM 21
 * while running on JVM 25+ servers where VaultUnlocked is available.
 */
public class VaultUnlockedIntegration {

    private static final Logger logger = Logger.getLogger("EliteEssentials");
    
    private static VaultUnlockedIntegration instance;
    
    private final ConfigManager configManager;
    private final PlayerService playerService;
    
    private boolean vaultUnlockedAvailable = false;
    private boolean registeredAsProvider = false;
    private boolean usingExternalEconomy = false;
    
    // Cached reflection objects
    private Class<?> economyClass;
    private Class<?> economyResponseClass;
    private Class<?> responseTypeClass;
    private Object servicesManager;
    
    public VaultUnlockedIntegration(ConfigManager configManager, PlayerService playerService) {
        this.configManager = configManager;
        this.playerService = playerService;
        instance = this;
    }
    
    /**
     * Initialize VaultUnlocked integration.
     * Call this during plugin startup after PlayerService is ready.
     */
    public void initialize() {
        // Check if VaultUnlocked is available
        if (!isVaultUnlockedPresent()) {
            logger.info("[VaultUnlocked] VaultUnlocked not found - using internal economy only.");
            return;
        }
        
        vaultUnlockedAvailable = true;
        logger.info("[VaultUnlocked] VaultUnlocked detected!");
        
        var config = configManager.getConfig().economy;
        
        // Check if we should use external economy
        if (config.useExternalEconomy) {
            if (tryUseExternalEconomy()) {
                usingExternalEconomy = true;
                logger.info("[VaultUnlocked] Using external economy via VaultUnlocked.");
                return; // Don't register as provider if using external
            } else {
                logger.warning("[VaultUnlocked] useExternalEconomy enabled but no external economy found. Using internal economy.");
            }
        }
        
        // Register as economy provider
        if (config.vaultUnlockedProvider) {
            if (registerAsProvider()) {
                registeredAsProvider = true;
                logger.info("[VaultUnlocked] Registered EliteEssentials as economy provider.");
            } else {
                logger.warning("[VaultUnlocked] Failed to register as economy provider.");
            }
        }
    }
    
    /**
     * Check if VaultUnlocked classes are available.
     */
    private boolean isVaultUnlockedPresent() {
        try {
            economyClass = Class.forName("net.milkbowl.vault2.economy.Economy");
            economyResponseClass = Class.forName("net.milkbowl.vault2.economy.EconomyResponse");
            responseTypeClass = Class.forName("net.milkbowl.vault2.economy.EconomyResponse$ResponseType");
            
            Class<?> servicesManagerClass = Class.forName("net.cfh.vault.VaultUnlockedServicesManager");
            servicesManager = servicesManagerClass.getMethod("get").invoke(null);
            
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        } catch (Exception e) {
            logger.warning("[VaultUnlocked] Error checking for VaultUnlocked: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Try to use an external economy via VaultUnlocked.
     */
    private boolean tryUseExternalEconomy() {
        try {
            Object economy = servicesManager.getClass()
                .getMethod("economyObj")
                .invoke(servicesManager);
            
            if (economy != null) {
                // Verify it's not our own provider
                if (economy.getClass().getName().contains("eliteessentials")) {
                    logger.info("[VaultUnlocked] Only our own economy is registered - using internal.");
                    return false;
                }
                
                String name = (String) economy.getClass().getMethod("getName").invoke(economy);
                logger.info("[VaultUnlocked] Found external economy: " + name);
                return true;
            }
            
            return false;
        } catch (Exception e) {
            logger.warning("[VaultUnlocked] Error checking for external economy: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Register EliteEssentials as a VaultUnlocked economy provider using dynamic proxy.
     */
    private boolean registerAsProvider() {
        try {
            // Create a dynamic proxy that implements the Economy interface
            Object economyProxy = Proxy.newProxyInstance(
                economyClass.getClassLoader(),
                new Class<?>[] { economyClass },
                new EconomyInvocationHandler()
            );
            
            // Register our economy proxy
            servicesManager.getClass()
                .getMethod("economy", economyClass)
                .invoke(servicesManager, economyProxy);
            
            return true;
        } catch (Exception e) {
            logger.warning("[VaultUnlocked] Error registering as provider: " + e.getMessage());
            if (configManager.isDebugEnabled()) {
                e.printStackTrace();
            }
            return false;
        }
    }
    
    /**
     * Dynamic proxy handler that implements VaultUnlocked Economy interface.
     */
    private class EconomyInvocationHandler implements InvocationHandler {
        
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String methodName = method.getName();
            
            switch (methodName) {
                // Plugin info
                case "getName":
                    return "EliteEssentials";
                case "isEnabled":
                    return configManager.getConfig().economy.enabled;
                    
                // Currency info
                case "currencyNameSingular":
                case "defaultCurrencyNameSingular":
                    return configManager.getConfig().economy.currencyName;
                case "currencyNamePlural":
                case "defaultCurrencyNamePlural":
                    return configManager.getConfig().economy.currencyNamePlural;
                case "fractionalDigits":
                    return 2;
                case "format":
                    if (args != null && args.length > 0 && args[0] instanceof BigDecimal) {
                        String symbol = configManager.getConfig().economy.currencySymbol;
                        return String.format("%s%.2f", symbol, ((BigDecimal) args[0]).doubleValue());
                    }
                    return "$0.00";
                    
                // Account management
                case "hasAccount":
                    if (args != null && args.length > 0 && args[0] instanceof UUID) {
                        return playerService.getPlayer((UUID) args[0]).isPresent();
                    }
                    return false;
                case "createAccount":
                    if (args != null && args.length > 0 && args[0] instanceof UUID) {
                        return playerService.getPlayer((UUID) args[0]).isPresent();
                    }
                    return false;
                case "getUUIDNameMap":
                    return Collections.emptyMap();
                case "getAccountName":
                    if (args != null && args.length > 0 && args[0] instanceof UUID) {
                        return playerService.getPlayer((UUID) args[0]).map(p -> p.getName());
                    }
                    return Optional.empty();
                    
                // Balance operations
                case "getBalance":
                    if (args != null && args.length > 0 && args[0] instanceof UUID) {
                        return BigDecimal.valueOf(playerService.getBalance((UUID) args[0]));
                    }
                    return BigDecimal.ZERO;
                case "has":
                    if (args != null && args.length >= 2 && args[0] instanceof UUID && args[1] instanceof BigDecimal) {
                        double balance = playerService.getBalance((UUID) args[0]);
                        return balance >= ((BigDecimal) args[1]).doubleValue();
                    }
                    return false;
                    
                // Transactions
                case "withdraw":
                    return handleWithdraw(args);
                case "deposit":
                    return handleDeposit(args);
                    
                // Unsupported features
                case "hasBankSupport":
                case "hasMultiCurrencySupport":
                    return false;
                case "currencies":
                    return Collections.singletonList(configManager.getConfig().economy.currencyNamePlural);
                case "defaultCurrency":
                    return configManager.getConfig().economy.currencyNamePlural;
                case "supportsCurrency":
                    if (args != null && args.length > 0 && args[0] instanceof String) {
                        String currency = (String) args[0];
                        return currency.equalsIgnoreCase(configManager.getConfig().economy.currencyName)
                            || currency.equalsIgnoreCase(configManager.getConfig().economy.currencyNamePlural);
                    }
                    return false;
                    
                // Bank operations - not supported
                case "createBank":
                case "deleteBank":
                case "bankBalance":
                case "bankHas":
                case "bankWithdraw":
                case "bankDeposit":
                case "isBankOwner":
                case "isBankMember":
                    return createEconomyResponse(BigDecimal.ZERO, BigDecimal.ZERO, "NOT_IMPLEMENTED", "Banks not supported");
                case "getBanks":
                    return Collections.emptyList();
                    
                // Object methods
                case "toString":
                    return "EliteEssentials Economy Provider";
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == args[0];
                    
                default:
                    if (configManager.isDebugEnabled()) {
                        logger.info("[VaultUnlocked] Unhandled method: " + methodName);
                    }
                    return null;
            }
        }
        
        private Object handleWithdraw(Object[] args) {
            if (args == null || args.length < 2 || !(args[0] instanceof UUID) || !(args[1] instanceof BigDecimal)) {
                return createEconomyResponse(BigDecimal.ZERO, BigDecimal.ZERO, "FAILURE", "Invalid arguments");
            }
            
            UUID uuid = (UUID) args[0];
            BigDecimal amount = (BigDecimal) args[1];
            
            if (amount.compareTo(BigDecimal.ZERO) < 0) {
                return createEconomyResponse(BigDecimal.ZERO, getBalanceAsBigDecimal(uuid), "FAILURE", "Cannot withdraw negative amount");
            }
            
            double balance = playerService.getBalance(uuid);
            if (balance < amount.doubleValue()) {
                return createEconomyResponse(BigDecimal.ZERO, BigDecimal.valueOf(balance), "FAILURE", "Insufficient funds");
            }
            
            boolean success = playerService.removeMoney(uuid, amount.doubleValue());
            if (success) {
                return createEconomyResponse(amount, getBalanceAsBigDecimal(uuid), "SUCCESS", null);
            } else {
                return createEconomyResponse(BigDecimal.ZERO, getBalanceAsBigDecimal(uuid), "FAILURE", "Transaction failed");
            }
        }
        
        private Object handleDeposit(Object[] args) {
            if (args == null || args.length < 2 || !(args[0] instanceof UUID) || !(args[1] instanceof BigDecimal)) {
                return createEconomyResponse(BigDecimal.ZERO, BigDecimal.ZERO, "FAILURE", "Invalid arguments");
            }
            
            UUID uuid = (UUID) args[0];
            BigDecimal amount = (BigDecimal) args[1];
            
            if (amount.compareTo(BigDecimal.ZERO) < 0) {
                return createEconomyResponse(BigDecimal.ZERO, getBalanceAsBigDecimal(uuid), "FAILURE", "Cannot deposit negative amount");
            }
            
            boolean success = playerService.addMoney(uuid, amount.doubleValue());
            if (success) {
                return createEconomyResponse(amount, getBalanceAsBigDecimal(uuid), "SUCCESS", null);
            } else {
                return createEconomyResponse(BigDecimal.ZERO, getBalanceAsBigDecimal(uuid), "FAILURE", "Transaction failed");
            }
        }
        
        private BigDecimal getBalanceAsBigDecimal(UUID uuid) {
            return BigDecimal.valueOf(playerService.getBalance(uuid));
        }
        
        @SuppressWarnings("unchecked")
        private Object createEconomyResponse(BigDecimal amount, BigDecimal balance, String responseType, String errorMessage) {
            try {
                // Get the ResponseType enum value
                Object type = Enum.valueOf((Class<Enum>) responseTypeClass, responseType);
                
                // Create EconomyResponse via constructor
                return economyResponseClass
                    .getConstructor(BigDecimal.class, BigDecimal.class, responseTypeClass, String.class)
                    .newInstance(amount, balance, type, errorMessage);
            } catch (Exception e) {
                logger.warning("[VaultUnlocked] Error creating EconomyResponse: " + e.getMessage());
                return null;
            }
        }
    }
    
    /**
     * Shutdown VaultUnlocked integration.
     */
    public void shutdown() {
        registeredAsProvider = false;
        usingExternalEconomy = false;
        servicesManager = null;
    }
    
    // ==================== EXTERNAL ECONOMY ACCESS ====================
    
    /**
     * Get balance from external economy.
     */
    public double getExternalBalance(UUID playerId) {
        if (!usingExternalEconomy) return 0.0;
        
        try {
            Object economy = getExternalEconomy();
            if (economy == null) return 0.0;
            
            Object result = economy.getClass()
                .getMethod("getBalance", UUID.class)
                .invoke(economy, playerId);
            
            if (result instanceof BigDecimal) {
                return ((BigDecimal) result).doubleValue();
            }
            return 0.0;
        } catch (Exception e) {
            logger.warning("[VaultUnlocked] Error getting external balance: " + e.getMessage());
            return 0.0;
        }
    }
    
    /**
     * Check if player has amount in external economy.
     */
    public boolean externalHas(UUID playerId, double amount) {
        if (!usingExternalEconomy) return false;
        
        try {
            Object economy = getExternalEconomy();
            if (economy == null) return false;
            
            Object result = economy.getClass()
                .getMethod("has", UUID.class, BigDecimal.class)
                .invoke(economy, playerId, BigDecimal.valueOf(amount));
            
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            logger.warning("[VaultUnlocked] Error checking external balance: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Withdraw from external economy.
     */
    public boolean externalWithdraw(UUID playerId, double amount) {
        if (!usingExternalEconomy) return false;
        
        try {
            Object economy = getExternalEconomy();
            if (economy == null) return false;
            
            Object response = economy.getClass()
                .getMethod("withdraw", UUID.class, BigDecimal.class)
                .invoke(economy, playerId, BigDecimal.valueOf(amount));
            
            // Check if transaction was successful
            Object responseType = response.getClass().getMethod("type").invoke(response);
            return "SUCCESS".equals(responseType.toString());
        } catch (Exception e) {
            logger.warning("[VaultUnlocked] Error withdrawing from external economy: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Deposit to external economy.
     */
    public boolean externalDeposit(UUID playerId, double amount) {
        if (!usingExternalEconomy) return false;
        
        try {
            Object economy = getExternalEconomy();
            if (economy == null) return false;
            
            Object response = economy.getClass()
                .getMethod("deposit", UUID.class, BigDecimal.class)
                .invoke(economy, playerId, BigDecimal.valueOf(amount));
            
            Object responseType = response.getClass().getMethod("type").invoke(response);
            return "SUCCESS".equals(responseType.toString());
        } catch (Exception e) {
            logger.warning("[VaultUnlocked] Error depositing to external economy: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Get the external economy object.
     */
    private Object getExternalEconomy() {
        try {
            if (servicesManager == null) return null;
            return servicesManager.getClass()
                .getMethod("economyObj")
                .invoke(servicesManager);
        } catch (Exception e) {
            return null;
        }
    }
    
    // ==================== GETTERS ====================
    
    public static VaultUnlockedIntegration get() {
        return instance;
    }
    
    public boolean isVaultUnlockedAvailable() {
        return vaultUnlockedAvailable;
    }
    
    public boolean isRegisteredAsProvider() {
        return registeredAsProvider;
    }
    
    public boolean isUsingExternalEconomy() {
        return usingExternalEconomy;
    }
}
