package com.eliteessentials;

import com.eliteessentials.commands.hytale.*;
import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.events.StarterKitEvent;
import com.eliteessentials.integration.LuckPermsIntegration;
import com.eliteessentials.services.BackService;
import com.eliteessentials.services.CooldownService;
import com.eliteessentials.services.DamageTrackingService;
import com.eliteessentials.services.DeathTrackingService;
import com.eliteessentials.services.GodService;
import com.eliteessentials.services.HomeService;
import com.eliteessentials.services.KitService;
import com.eliteessentials.services.MessageService;
import com.eliteessentials.services.RtpService;
import com.eliteessentials.services.SleepService;
import com.eliteessentials.services.SpawnProtectionService;
import com.eliteessentials.services.TpaService;
import com.eliteessentials.services.WarmupService;
import com.eliteessentials.services.WarpService;
import com.eliteessentials.storage.BackStorage;
import com.eliteessentials.storage.HomeStorage;
import com.eliteessentials.storage.WarpStorage;
import com.eliteessentials.systems.DamageTrackingSystem;
import com.eliteessentials.systems.PlayerDeathSystem;
import com.eliteessentials.systems.SpawnProtectionSystem;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.io.File;
import java.util.logging.Level;

/**
 * EliteEssentials - Essential commands for Hytale servers.
 * 
 * Features:
 * - /home, /sethome, /delhome - Named home locations
 * - /back - Return to previous locations
 * - /rtp - Random teleport
 * - /tpa, /tpaccept, /tpdeny - Teleport requests
 */
public class EliteEssentials extends JavaPlugin {

    private static EliteEssentials instance;
    
    private ConfigManager configManager;
    private HomeStorage homeStorage;
    private BackStorage backStorage;
    private WarpStorage warpStorage;
    private HomeService homeService;
    private BackService backService;
    private TpaService tpaService;
    private RtpService rtpService;
    private SleepService sleepService;
    private WarmupService warmupService;
    private CooldownService cooldownService;
    private WarpService warpService;
    private DamageTrackingService damageTrackingService;
    private DeathTrackingService deathTrackingService;
    private GodService godService;
    private MessageService messageService;
    private KitService kitService;
    private SpawnProtectionService spawnProtectionService;
    private HytaleFlyCommand flyCommand;
    private PlayerDeathSystem playerDeathSystem;
    private DamageTrackingSystem damageTrackingSystem;
    private SpawnProtectionSystem spawnProtectionSystem;
    private StarterKitEvent starterKitEvent;
    private File dataFolder;

    public EliteEssentials(JavaPluginInit init) {
        super(init);
        instance = this;
    }

    @Override
    protected void setup() {
        getLogger().at(Level.INFO).log("EliteEssentials is setting up...");
        
        // Get data folder - Hytale may create mods/Group_Name/ or mods/Group/Name/
        // We want just mods/EliteEssentials/ so construct it directly
        java.nio.file.Path baseDataPath = getDataDirectory();
        
        // Find mods folder in the path
        java.nio.file.Path modsPath = findModsFolder(baseDataPath);
        if (modsPath != null) {
            // Use mods/EliteEssentials/ directly
            this.dataFolder = modsPath.resolve("EliteEssentials").toFile();
            getLogger().at(Level.INFO).log("Using data folder: " + this.dataFolder.getAbsolutePath());
        } else {
            // Fallback to what Hytale gave us
            this.dataFolder = baseDataPath.toFile();
            getLogger().at(Level.WARNING).log("Could not find mods folder, using: " + this.dataFolder.getAbsolutePath());
        }
        
        // Load configuration
        configManager = new ConfigManager(this.dataFolder);
        configManager.loadConfig();
        
        // Initialize storage
        homeStorage = new HomeStorage(this.dataFolder);
        homeStorage.load();
        
        backStorage = new BackStorage(this.dataFolder);
        backStorage.load();
        
        warpStorage = new WarpStorage(this.dataFolder);
        warpStorage.load();
        
        // Initialize services
        cooldownService = new CooldownService();
        warmupService = new WarmupService();
        homeService = new HomeService(homeStorage, configManager);
        backService = new BackService(configManager, backStorage);
        warpService = new WarpService(warpStorage, configManager);
        damageTrackingService = new DamageTrackingService();
        deathTrackingService = new DeathTrackingService(backService, configManager);
        tpaService = new TpaService(configManager);
        rtpService = new RtpService(configManager);
        sleepService = new SleepService(configManager);
        godService = new GodService();
        messageService = new MessageService();
        kitService = new KitService(this.dataFolder);
        spawnProtectionService = new SpawnProtectionService(configManager);
        
        getLogger().at(Level.INFO).log("EliteEssentials setup complete.");
    }

    @Override
    protected void start() {
        getLogger().at(Level.INFO).log("EliteEssentials is starting...");
        
        // Register commands
        registerCommands();
        
        // Register permissions with LuckPerms for autocomplete/discovery
        LuckPermsIntegration.registerPermissions();
        
        // Register starter kit event for new players
        starterKitEvent = new StarterKitEvent(kitService, this.dataFolder);
        starterKitEvent.registerEvents(getEventRegistry());
        getLogger().at(Level.INFO).log("Starter kit system registered.");
        
        // Register the death tracking ECS system (hooks into Hytale's death events)
        if (configManager.isBackOnDeathEnabled() || configManager.getConfig().deathMessages.enabled) {
            try {
                // Register damage tracking system first (to track who damaged who)
                damageTrackingSystem = new DamageTrackingSystem(damageTrackingService);
                EntityStore.REGISTRY.registerSystem(damageTrackingSystem);
                getLogger().at(Level.INFO).log("DamageTrackingSystem registered - tracking damage sources!");
                
                // Register death system (uses damage tracking for death messages)
                playerDeathSystem = new PlayerDeathSystem(backService, configManager, damageTrackingService);
                EntityStore.REGISTRY.registerSystem(playerDeathSystem);
                getLogger().at(Level.INFO).log("PlayerDeathSystem registered - death tracking enabled!");
            } catch (Exception e) {
                getLogger().at(Level.WARNING).log("Could not register death systems: " + e.getMessage());
                // Fall back to polling-based death tracking
                if (deathTrackingService != null && configManager.isBackOnDeathEnabled()) {
                    deathTrackingService.start();
                    getLogger().at(Level.INFO).log("Falling back to polling-based death tracking.");
                }
            }
        }
        
        // Register spawn protection systems
        if (configManager.getConfig().spawnProtection.enabled) {
            try {
                spawnProtectionSystem = new SpawnProtectionSystem(spawnProtectionService);
                spawnProtectionSystem.register(EntityStore.REGISTRY);
                getLogger().at(Level.INFO).log("SpawnProtectionSystem registered - spawn area protected!");
            } catch (Exception e) {
                getLogger().at(Level.WARNING).log("Could not register spawn protection: " + e.getMessage());
            }
        }
        
        getLogger().at(Level.INFO).log("EliteEssentials started successfully!");
    }

    @Override
    protected void shutdown() {
        getLogger().at(Level.INFO).log("EliteEssentials is shutting down...");
        
        // Unregister the death systems
        if (damageTrackingSystem != null) {
            try {
                EntityStore.REGISTRY.unregisterSystem(DamageTrackingSystem.class);
            } catch (Exception e) {
                // Ignore unregister errors
            }
        }
        if (playerDeathSystem != null) {
            try {
                EntityStore.REGISTRY.unregisterSystem(PlayerDeathSystem.class);
            } catch (Exception e) {
                // Ignore unregister errors
            }
        }
        
        // Save all data
        if (homeStorage != null) {
            homeStorage.save();
            getLogger().at(Level.INFO).log("Homes saved.");
        }
        
        if (backService != null) {
            backService.save();
            getLogger().at(Level.INFO).log("Back locations saved.");
        }
        
        if (warpService != null) {
            warpService.save();
            getLogger().at(Level.INFO).log("Warps saved.");
        }
        
        // Cleanup services
        if (tpaService != null) {
            tpaService.shutdown();
        }
        if (sleepService != null) {
            sleepService.shutdown();
        }
        if (warmupService != null) {
            warmupService.shutdown();
        }
        if (deathTrackingService != null) {
            deathTrackingService.shutdown();
        }
        
        getLogger().at(Level.INFO).log("EliteEssentials disabled.");
    }
    
    private void registerCommands() {
        // Home commands
        getCommandRegistry().registerCommand(new HytaleHomeCommand(homeService, backService));
        getCommandRegistry().registerCommand(new HytaleSetHomeCommand(homeService));
        getCommandRegistry().registerCommand(new HytaleDelHomeCommand(homeService));
        getCommandRegistry().registerCommand(new HytaleHomesCommand(homeService));
        
        // Back command
        getCommandRegistry().registerCommand(new HytaleBackCommand(backService));
        
        // RTP command
        getCommandRegistry().registerCommand(new HytaleRtpCommand(rtpService, backService, configManager, warmupService));
        
        // TPA commands
        getCommandRegistry().registerCommand(new HytaleTpaCommand(tpaService));
        getCommandRegistry().registerCommand(new HytaleTpAcceptCommand(tpaService, backService));
        getCommandRegistry().registerCommand(new HytaleTpDenyCommand(tpaService));
        
        // Spawn command
        getCommandRegistry().registerCommand(new HytaleSpawnCommand(backService));
        
        // Warp commands
        try {
            getCommandRegistry().registerCommand(new HytaleWarpCommand(warpService, backService));
            getLogger().at(Level.INFO).log("Registered /warp command");
        } catch (Exception e) {
            getLogger().at(Level.SEVERE).log("Failed to register /warp: " + e.getMessage());
            e.printStackTrace();
        }
        try {
            getCommandRegistry().registerCommand(new HytaleSetWarpCommand(warpService));
            getLogger().at(Level.INFO).log("Registered /setwarp command");
        } catch (Exception e) {
            getLogger().at(Level.SEVERE).log("Failed to register /setwarp: " + e.getMessage());
            e.printStackTrace();
        }
        try {
            getCommandRegistry().registerCommand(new HytaleDelWarpCommand(warpService));
            getLogger().at(Level.INFO).log("Registered /delwarp command");
        } catch (Exception e) {
            getLogger().at(Level.SEVERE).log("Failed to register /delwarp: " + e.getMessage());
            e.printStackTrace();
        }
        try {
            getCommandRegistry().registerCommand(new HytaleWarpsCommand(warpService));
            getLogger().at(Level.INFO).log("Registered /warps command");
        } catch (Exception e) {
            getLogger().at(Level.SEVERE).log("Failed to register /warps: " + e.getMessage());
            e.printStackTrace();
        }
        try {
            getCommandRegistry().registerCommand(new HytaleWarpAdminCommand(warpService));
            getLogger().at(Level.INFO).log("Registered /warpadmin command");
        } catch (Exception e) {
            getLogger().at(Level.SEVERE).log("Failed to register /warpadmin: " + e.getMessage());
            e.printStackTrace();
        }
        
        // Admin commands (OP only)
        getCommandRegistry().registerCommand(new HytaleSleepPercentCommand(configManager));
        getCommandRegistry().registerCommand(new HytaleReloadCommand());
        
        // New commands
        getCommandRegistry().registerCommand(new HytaleGodCommand(godService, configManager));
        getCommandRegistry().registerCommand(new HytaleHealCommand(configManager));
        getCommandRegistry().registerCommand(new HytaleMsgCommand(messageService, configManager));
        getCommandRegistry().registerCommand(new HytaleReplyCommand(messageService, configManager));
        getCommandRegistry().registerCommand(new HytaleTopCommand(backService, configManager));
        flyCommand = new HytaleFlyCommand(configManager);
        getCommandRegistry().registerCommand(flyCommand);
        getCommandRegistry().registerCommand(new HytaleKitCommand(kitService, configManager));
        
        getLogger().at(Level.INFO).log("Commands registered: /home, /sethome, /delhome, /homes, /back, /rtp, /tpa, /tpaccept, /tpdeny, /spawn, /warp, /setwarp, /delwarp, /warps, /warpadmin, /sleeppercent, /eliteessentials, /god, /heal, /msg, /reply, /top, /fly, /kit");
    }

    public static EliteEssentials getInstance() {
        return instance;
    }
    
    public ConfigManager getConfigManager() {
        return configManager;
    }
    
    public HomeService getHomeService() {
        return homeService;
    }
    
    public BackService getBackService() {
        return backService;
    }
    
    public TpaService getTpaService() {
        return tpaService;
    }
    
    public RtpService getRtpService() {
        return rtpService;
    }
    
    public WarmupService getWarmupService() {
        return warmupService;
    }
    
    public CooldownService getCooldownService() {
        return cooldownService;
    }
    
    public WarpService getWarpService() {
        return warpService;
    }
    
    public DeathTrackingService getDeathTrackingService() {
        return deathTrackingService;
    }
    
    public GodService getGodService() {
        return godService;
    }
    
    public MessageService getMessageService() {
        return messageService;
    }
    
    public KitService getKitService() {
        return kitService;
    }
    
    public SpawnProtectionService getSpawnProtectionService() {
        return spawnProtectionService;
    }
    
    public StarterKitEvent getStarterKitEvent() {
        return starterKitEvent;
    }
    
    public File getPluginDataFolder() {
        return dataFolder;
    }
    
    /**
     * Reload the plugin configuration.
     * Called by /eliteessentials reload command.
     */
    public void reloadConfig() {
        getLogger().at(Level.INFO).log("Reloading EliteEssentials configuration...");
        configManager.loadConfig();
        kitService.reload();
        if (starterKitEvent != null) {
            starterKitEvent.reload();
        }
        getLogger().at(Level.INFO).log("Configuration reloaded.");
    }
    
    /**
     * Find the mods folder in the path hierarchy.
     */
    private java.nio.file.Path findModsFolder(java.nio.file.Path path) {
        java.nio.file.Path current = path;
        while (current != null && current.getNameCount() > 0) {
            String name = current.getFileName().toString();
            if ("mods".equals(name)) {
                return current;
            }
            java.nio.file.Path parent = current.getParent();
            if (parent == null || parent.equals(current)) {
                break;
            }
            current = parent;
        }
        return null;
    }
}
