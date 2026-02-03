package com.eliteessentials.integration;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.services.PlayerService;
import net.cfh.vault.VaultUnlockedServicesManager;
import net.milkbowl.vault2.economy.Economy;

import java.math.BigDecimal;
import java.util.UUID;
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
 * Based on Ecotale's implementation pattern.
 */
public class VaultUnlockedIntegration {

    private static final Logger logger = Logger.getLogger("EliteEssentials");
    
    private static VaultUnlockedIntegration instance;
    
    private final ConfigManager configManager;
    private final PlayerService playerService;
    
    private boolean vaultUnlockedAvailable = false;
    private boolean registeredAsProvider = false;
    private boolean usingExternalEconomy = false;
    
    private Economy externalEconomy = null;
    
    private static final int EXTERNAL_ECONOMY_CHECK_DELAY_SECONDS = 30;
    
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
            // Try immediately first
            if (tryUseExternalEconomy()) {
                usingExternalEconomy = true;
                logger.info("[VaultUnlocked] Using external economy via VaultUnlocked: " + externalEconomy.getName());
                return; // Don't register as provider if using external
            } else {
                // External economy not found yet - schedule a delayed retry
                // Other economy plugins (like Ecotale) may register after us during server startup
                logger.info("[VaultUnlocked] External economy not found yet. Will retry in " + EXTERNAL_ECONOMY_CHECK_DELAY_SECONDS + " seconds...");
                logger.info("[VaultUnlocked] (Use /ee reload to manually retry external economy detection)");
                scheduleExternalEconomyRetry();
                return; // Don't register as provider yet - wait for retry
            }
        }
        
        // Register as economy provider (only if not using external)
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
     * Retry external economy detection. Can be called manually via /ee reload.
     * @return true if external economy was found and is now being used
     */
    public boolean retryExternalEconomy() {
        if (!vaultUnlockedAvailable) {
            return false;
        }
        
        var config = configManager.getConfig().economy;
        if (!config.useExternalEconomy) {
            return false;
        }
        
        if (usingExternalEconomy) {
            return true; // Already using external
        }
        
        if (tryUseExternalEconomy()) {
            usingExternalEconomy = true;
            logger.info("[VaultUnlocked] Found external economy! Now using: " + externalEconomy.getName());
            return true;
        }
        
        return false;
    }
    
    /**
     * Schedule a delayed retry to find external economy.
     * This handles the case where economy plugins load after EliteEssentials.
     */
    private void scheduleExternalEconomyRetry() {
        new Thread(() -> {
            try {
                Thread.sleep(EXTERNAL_ECONOMY_CHECK_DELAY_SECONDS * 1000L);
                
                if (tryUseExternalEconomy()) {
                    usingExternalEconomy = true;
                    logger.info("[VaultUnlocked] Found external economy on retry! Using: " + externalEconomy.getName());
                } else {
                    logger.warning("[VaultUnlocked] External economy still not found after " + EXTERNAL_ECONOMY_CHECK_DELAY_SECONDS + "s.");
                    logger.warning("[VaultUnlocked] Run '/ee reload' after all plugins have loaded to retry detection.");
                    
                    // Fall back to registering as provider if configured
                    var config = configManager.getConfig().economy;
                    if (config.vaultUnlockedProvider && !registeredAsProvider) {
                        if (registerAsProvider()) {
                            registeredAsProvider = true;
                            logger.info("[VaultUnlocked] Registered EliteEssentials as economy provider (fallback).");
                        }
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "EE-VaultUnlocked-Retry").start();
    }
    
    /**
     * Check if VaultUnlocked classes are available.
     */
    private boolean isVaultUnlockedPresent() {
        try {
            Class.forName("net.milkbowl.vault2.economy.Economy");
            Class.forName("net.cfh.vault.VaultUnlockedServicesManager");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
    
    /**
     * Try to use an external economy via VaultUnlocked.
     */
    private boolean tryUseExternalEconomy() {
        try {
            if (configManager.isDebugEnabled()) {
                logger.info("[VaultUnlocked] Checking for external economy...");
            }
            
            VaultUnlockedServicesManager manager = VaultUnlockedServicesManager.get();
            if (manager == null) {
                logger.warning("[VaultUnlocked] VaultUnlockedServicesManager.get() returned null!");
                return false;
            }
            
            if (configManager.isDebugEnabled()) {
                logger.info("[VaultUnlocked] ServicesManager obtained, checking economyObj()...");
            }
            
            Economy economy = manager.economyObj();
            
            if (economy == null) {
                logger.info("[VaultUnlocked] No economy provider registered with VaultUnlocked.");
                // Log additional diagnostic info
                if (configManager.isDebugEnabled()) {
                    logger.info("[VaultUnlocked] This means no plugin has called VaultUnlockedServicesManager.economy() to register.");
                    logger.info("[VaultUnlocked] Check if TheEconomy has VaultUnlocked support enabled in its config.");
                }
                return false;
            }
            
            // Verify it's not our own provider
            String name = economy.getName();
            if (configManager.isDebugEnabled()) {
                logger.info("[VaultUnlocked] Found economy: " + name + " (enabled: " + economy.isEnabled() + ")");
            }
            
            if ("EliteEssentials".equals(name)) {
                logger.info("[VaultUnlocked] Only our own economy is registered - using internal.");
                return false;
            }
            
            if (!economy.isEnabled()) {
                logger.warning("[VaultUnlocked] External economy '" + name + "' is not enabled.");
                return false;
            }
            
            externalEconomy = economy;
            return true;
        } catch (Exception e) {
            logger.warning("[VaultUnlocked] Error checking for external economy: " + e.getMessage());
            if (configManager.isDebugEnabled()) {
                e.printStackTrace();
            }
            return false;
        }
    }
    
    /**
     * Register EliteEssentials as a VaultUnlocked economy provider.
     */
    private boolean registerAsProvider() {
        try {
            EliteEssentialsEconomy economy = new EliteEssentialsEconomy(configManager, playerService);
            VaultUnlockedServicesManager.get().economy(economy);
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
     * Shutdown VaultUnlocked integration.
     */
    public void shutdown() {
        registeredAsProvider = false;
        usingExternalEconomy = false;
        externalEconomy = null;
    }
    
    // ==================== EXTERNAL ECONOMY ACCESS ====================
    
    /**
     * Check if external economy should be used.
     */
    private boolean shouldUseExternalEconomy() {
        return usingExternalEconomy && externalEconomy != null;
    }
    
    /**
     * Get balance from external economy.
     */
    public double getExternalBalance(UUID playerId) {
        if (!shouldUseExternalEconomy()) return 0.0;
        
        try {
            BigDecimal balance = externalEconomy.getBalance("EliteEssentials", playerId);
            
            if (configManager.isDebugEnabled()) {
                logger.info("[VaultUnlocked] getBalance result: " + balance);
            }
            
            return balance.doubleValue();
        } catch (Exception e) {
            logger.warning("[VaultUnlocked] Error getting external balance: " + e.getMessage());
            if (configManager.isDebugEnabled()) {
                e.printStackTrace();
            }
            return 0.0;
        }
    }
    
    /**
     * Check if player has amount in external economy.
     */
    public boolean externalHas(UUID playerId, double amount) {
        if (!shouldUseExternalEconomy()) return false;
        
        try {
            return externalEconomy.has("EliteEssentials", playerId, BigDecimal.valueOf(amount));
        } catch (Exception e) {
            logger.warning("[VaultUnlocked] Error checking external balance: " + e.getMessage());
            // Fall back to balance check
            return getExternalBalance(playerId) >= amount;
        }
    }
    
    /**
     * Withdraw from external economy.
     */
    public boolean externalWithdraw(UUID playerId, double amount) {
        if (!shouldUseExternalEconomy()) return false;
        
        try {
            var response = externalEconomy.withdraw("EliteEssentials", playerId, BigDecimal.valueOf(amount));
            
            if (configManager.isDebugEnabled()) {
                logger.info("[VaultUnlocked] Withdraw response: success=" + response.transactionSuccess() + 
                    ", amount=" + response.amount + 
                    ", balance=" + response.balance);
            }
            
            return response.transactionSuccess();
        } catch (Exception e) {
            logger.warning("[VaultUnlocked] Error withdrawing from external economy: " + e.getMessage());
            if (configManager.isDebugEnabled()) {
                e.printStackTrace();
            }
            return false;
        }
    }
    
    /**
     * Deposit to external economy.
     */
    public boolean externalDeposit(UUID playerId, double amount) {
        if (!shouldUseExternalEconomy()) return false;
        
        try {
            var response = externalEconomy.deposit("EliteEssentials", playerId, BigDecimal.valueOf(amount));
            
            if (configManager.isDebugEnabled()) {
                logger.info("[VaultUnlocked] Deposit response: success=" + response.transactionSuccess() + 
                    ", amount=" + response.amount + 
                    ", balance=" + response.balance);
            }
            
            return response.transactionSuccess();
        } catch (Exception e) {
            logger.warning("[VaultUnlocked] Error depositing to external economy: " + e.getMessage());
            if (configManager.isDebugEnabled()) {
                e.printStackTrace();
            }
            return false;
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
