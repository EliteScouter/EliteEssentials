package com.eliteessentials;

import com.eliteessentials.commands.hytale.*;
import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.config.PluginConfig;
import com.eliteessentials.events.StarterKitEvent;
import com.eliteessentials.integration.LuckPermsIntegration;
import com.eliteessentials.integration.PAPIIntegration;
import com.eliteessentials.integration.VaultUnlockedIntegration;
import com.eliteessentials.listeners.ChatListener;
import com.eliteessentials.listeners.ConnectListener;
import com.eliteessentials.listeners.JoinQuitListener;
import com.eliteessentials.listeners.RespawnListener;
import com.eliteessentials.services.AfkService;
import com.eliteessentials.services.AliasService;
import com.eliteessentials.services.AutoBroadcastService;
import com.eliteessentials.services.BackService;
import com.eliteessentials.services.CooldownService;
import com.eliteessentials.services.CostService;
import com.eliteessentials.services.DamageTrackingService;
import com.eliteessentials.services.DataMigrationService;
import com.eliteessentials.services.DeathTrackingService;
import com.eliteessentials.services.GodService;
import com.eliteessentials.services.GroupChatService;
import com.eliteessentials.services.HomeService;
import com.eliteessentials.services.IgnoreService;
import com.eliteessentials.services.KitService;
import com.eliteessentials.services.MailService;
import com.eliteessentials.services.MessageService;
import com.eliteessentials.services.MuteService;
import com.eliteessentials.services.BanService;
import com.eliteessentials.services.TempBanService;
import com.eliteessentials.services.IpBanService;
import com.eliteessentials.services.FreezeService;
import com.eliteessentials.services.PlayerService;
import com.eliteessentials.services.PlayTimeRewardService;
import com.eliteessentials.services.RtpService;
import com.eliteessentials.services.SleepService;
import com.eliteessentials.services.SpawnProtectionService;
import com.eliteessentials.services.TpaService;
import com.eliteessentials.services.VanishService;
import com.eliteessentials.services.WarmupService;
import com.eliteessentials.services.WarpService;
import com.eliteessentials.storage.DiscordStorage;
import com.eliteessentials.storage.MotdStorage;
import com.eliteessentials.storage.PlayerFileStorage;
import com.eliteessentials.storage.PlayTimeRewardStorage;
import com.eliteessentials.storage.RulesStorage;
import com.eliteessentials.storage.SpawnStorage;
import com.eliteessentials.storage.WarpStorage;
import com.eliteessentials.systems.DamageTrackingSystem;
import com.eliteessentials.systems.PlayerDeathSystem;
import com.eliteessentials.systems.SpawnProtectionSystem;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

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
    private static final Logger logger = Logger.getLogger("EliteEssentials");
    
    private ConfigManager configManager;
    private PlayerFileStorage playerFileStorage;
    private WarpStorage warpStorage;
    private SpawnStorage spawnStorage;
    private MotdStorage motdStorage;
    private RulesStorage rulesStorage;
    private DiscordStorage discordStorage;
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
    private VanishService vanishService;
    private GroupChatService groupChatService;
    private MessageService messageService;
    private KitService kitService;
    private SpawnProtectionService spawnProtectionService;
    private AutoBroadcastService autoBroadcastService;
    private AliasService aliasService;
    private PlayerService playerService;
    private CostService costService;
    private MailService mailService;
    private PlayTimeRewardStorage playTimeRewardStorage;
    private PlayTimeRewardService playTimeRewardService;
    private AfkService afkService;
    private IgnoreService ignoreService;
    private MuteService muteService;
    private BanService banService;
    private TempBanService tempBanService;
    private IpBanService ipBanService;
    private FreezeService freezeService;
    private HytaleFlyCommand flyCommand;
    private PlayerDeathSystem playerDeathSystem;
    private DamageTrackingSystem damageTrackingSystem;
    private SpawnProtectionSystem spawnProtectionSystem;
    private RespawnListener respawnListener;
    private StarterKitEvent starterKitEvent;
    private JoinQuitListener joinQuitListener;
    private ChatListener chatListener;
    private ConnectListener connectListener;
    private VaultUnlockedIntegration vaultUnlockedIntegration;
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
        getLogger().at(Level.INFO).log("Hytale provided data directory: " + baseDataPath.toAbsolutePath());
        
        // Find mods folder in the path
        java.nio.file.Path modsPath = findModsFolder(baseDataPath);
        if (modsPath != null) {
            // Use mods/EliteEssentials/ directly
            this.dataFolder = modsPath.resolve("EliteEssentials").toFile();
            getLogger().at(Level.INFO).log("Using normalized data folder: " + this.dataFolder.getAbsolutePath());
            
            // Check if old data exists in Hytale's original path (migration hint)
            File hytaleOriginal = baseDataPath.toFile();
            if (!hytaleOriginal.equals(this.dataFolder) && hytaleOriginal.exists()) {
                File oldSpawn = new File(hytaleOriginal, "spawn.json");
                if (oldSpawn.exists()) {
                    getLogger().at(Level.WARNING).log("Found spawn.json in old location: " + oldSpawn.getAbsolutePath());
                    getLogger().at(Level.WARNING).log("You may need to copy files from the old location to: " + this.dataFolder.getAbsolutePath());
                }
            }
        } else {
            // Fallback to what Hytale gave us
            this.dataFolder = baseDataPath.toFile();
            getLogger().at(Level.WARNING).log("Could not find mods folder, using: " + this.dataFolder.getAbsolutePath());
        }
        
        // Load configuration
        configManager = new ConfigManager(this.dataFolder);
        configManager.loadConfig();
        
        // Initialize per-player file storage (new system)
        playerFileStorage = new PlayerFileStorage(this.dataFolder);
        
        // Run migration from old monolithic files to per-player files
        DataMigrationService migrationService = new DataMigrationService(this.dataFolder, playerFileStorage);
        if (migrationService.needsMigration()) {
            getLogger().at(Level.INFO).log("Detected old data files, running migration...");
            if (!migrationService.migrate()) {
                getLogger().at(Level.SEVERE).log("Migration failed! Check logs for details.");
            }
        }
        
        // Initialize server-wide storage (not per-player)
        warpStorage = new WarpStorage(this.dataFolder);
        warpStorage.load();
        
        spawnStorage = new SpawnStorage(this.dataFolder);
        spawnStorage.load();
        
        motdStorage = new MotdStorage(this.dataFolder);
        motdStorage.load();
        
        rulesStorage = new RulesStorage(this.dataFolder);
        rulesStorage.load();
        
        discordStorage = new DiscordStorage(this.dataFolder);
        discordStorage.load();
        
        // Initialize services (now using PlayerFileStorage)
        cooldownService = new CooldownService();
        warmupService = new WarmupService();
        homeService = new HomeService(playerFileStorage);
        backService = new BackService(configManager, playerFileStorage);
        warpService = new WarpService(warpStorage);
        warpService.setConfigManager(configManager);
        damageTrackingService = new DamageTrackingService();
        deathTrackingService = new DeathTrackingService(backService, configManager);
        tpaService = new TpaService(configManager);
        rtpService = new RtpService(configManager);
        sleepService = new SleepService(configManager);
        godService = new GodService();
        vanishService = new VanishService(configManager);
        vanishService.setPlayerFileStorage(playerFileStorage);
        groupChatService = new GroupChatService(this.dataFolder, configManager);
        messageService = new MessageService();
        kitService = new KitService(this.dataFolder);
        kitService.setPlayerFileStorage(playerFileStorage);
        spawnProtectionService = new SpawnProtectionService(configManager);
        autoBroadcastService = new AutoBroadcastService(this.dataFolder);
        aliasService = new AliasService(this.dataFolder, getCommandRegistry());
        playerService = new PlayerService(playerFileStorage, configManager);
        costService = new CostService(configManager);
        mailService = new MailService(playerFileStorage, configManager);
        afkService = new AfkService(configManager);
        ignoreService = new IgnoreService(playerFileStorage);
        muteService = new MuteService(this.dataFolder);
        banService = new BanService(this.dataFolder);
        tempBanService = new TempBanService(this.dataFolder);
        ipBanService = new IpBanService(this.dataFolder);
        freezeService = new FreezeService(this.dataFolder);
        
        // Wire mute/ignore services into group chat for filtering
        groupChatService.setMuteService(muteService);
        groupChatService.setIgnoreService(ignoreService);
        
        // Initialize playtime rewards
        playTimeRewardStorage = new PlayTimeRewardStorage(this.dataFolder);
        playTimeRewardStorage.load();
        playTimeRewardService = new PlayTimeRewardService(playTimeRewardStorage, playerService, configManager);
        playTimeRewardService.setPlayerFileStorage(playerFileStorage);
        
        // Cross-link for session sync during periodic play time flushes
        playerService.setPlayTimeRewardService(playTimeRewardService);
        
        // Initialize VaultUnlocked integration (optional - for cross-plugin economy support)
        try {
            vaultUnlockedIntegration = new VaultUnlockedIntegration(configManager, playerService);
        } catch (NoClassDefFoundError e) {
            // VaultUnlocked not installed - this is fine, integration is optional
            getLogger().at(Level.INFO).log("VaultUnlocked not found - economy integration disabled");
            vaultUnlockedIntegration = null;
        }
        
        // Register custom UseBlock interaction for spawn protection (flower/pebble pickup blocking).
        // InteractivelyPickupItemEvent.setCancelled() is broken in Hytale API, so we intercept
        // at the interaction level instead - same approach as SimpleClaims.
        try {
            var interaction = getCodecRegistry(com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction.CODEC);
            interaction.register("UseBlock", com.eliteessentials.interactions.SpawnUseBlockInteraction.class, 
                com.eliteessentials.interactions.SpawnUseBlockInteraction.CUSTOM_CODEC);
            getLogger().at(Level.INFO).log("Custom UseBlock interaction registered for spawn pickup protection.");
        } catch (Exception e) {
            getLogger().at(Level.WARNING).log("Could not register custom UseBlock interaction: " + e.getMessage());
        }
        
        getLogger().at(Level.INFO).log("EliteEssentials setup complete.");
    }

    @Override
    protected void start() {
        getLogger().at(Level.INFO).log("EliteEssentials is starting...");
        
        // Register commands
        registerCommands();
        
        // Register permissions with LuckPerms for autocomplete/discovery
        LuckPermsIntegration.registerPermissions();

        // Register PlaceholderAPI integration
        PAPIIntegration.register(this);
        
        // Register starter kit event for new players
        starterKitEvent = new StarterKitEvent(kitService);
        starterKitEvent.setPlayerFileStorage(playerFileStorage);
        starterKitEvent.registerEvents(getEventRegistry());
        getLogger().at(Level.INFO).log("Starter kit system registered.");
        
        // Register join/quit listener for join/quit messages, first join, and MOTD
        joinQuitListener = new JoinQuitListener(configManager, motdStorage, playerService);
        joinQuitListener.setPlayerFileStorage(playerFileStorage);
        joinQuitListener.setSpawnStorage(spawnStorage);
        joinQuitListener.setVanishService(vanishService);
        joinQuitListener.setMailService(mailService);
        joinQuitListener.registerEvents(getEventRegistry());
        getLogger().at(Level.INFO).log("Join/Quit listener registered.");
        
        // Register chat listener for group-based chat formatting
        chatListener = new ChatListener(configManager);
        chatListener.setIgnoreService(ignoreService);
        chatListener.setMuteService(muteService);
        chatListener.registerEvents(getEventRegistry());
        if (configManager.getConfig().chatFormat.enabled) {
            getLogger().at(Level.INFO).log("Chat formatting system registered.");
        }
        
        // Register connect listener for ban/tempban/ipban/freeze enforcement
        connectListener = new ConnectListener(configManager);
        connectListener.setBanService(banService);
        connectListener.setTempBanService(tempBanService);
        connectListener.setIpBanService(ipBanService);
        connectListener.setFreezeService(freezeService);
        connectListener.registerEvents(getEventRegistry());
        getLogger().at(Level.INFO).log("Connect listener registered (ban/freeze enforcement).");
        
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
        
        // Register spawn protection systems (always register - checks enabled state internally)
        try {
            // Initialize spawn location from stored spawn
            initializeSpawnProtectionLocation();
            
            spawnProtectionSystem = new SpawnProtectionSystem(spawnProtectionService);
            spawnProtectionSystem.register(EntityStore.REGISTRY);
            
            if (configManager.getConfig().spawnProtection.enabled) {
                if (spawnStorage.hasSpawn()) {
                    getLogger().at(Level.INFO).log("SpawnProtectionSystem registered - spawn area protected!");
                } else {
                    getLogger().at(Level.WARNING).log("SpawnProtectionSystem registered but no spawn set. Use /setspawn to enable protection.");
                }
            } else {
                getLogger().at(Level.INFO).log("SpawnProtectionSystem registered (currently disabled in config).");
            }
        } catch (Exception e) {
            getLogger().at(Level.WARNING).log("Could not register spawn protection: " + e.getMessage());
        }
        
        // Register respawn system (handles respawning at spawn if no bed is set)
        try {
            respawnListener = new RespawnListener(spawnStorage);
            EntityStore.REGISTRY.registerSystem(respawnListener);
            getLogger().at(Level.INFO).log("RespawnListener registered - players without beds will respawn at /setspawn location!");
        } catch (Exception e) {
            getLogger().at(Level.WARNING).log("Could not register respawn system: " + e.getMessage());
        }
        
        // Start auto broadcast system
        if (configManager.getConfig().autoBroadcast.enabled) {
            autoBroadcastService.start();
        }
        
        // Load and register command aliases
        if (configManager.getConfig().aliases.enabled) {
            aliasService.load();
        }
        
        // Start AFK detection service
        if (configManager.getConfig().afk.enabled) {
            afkService.start();
            getLogger().at(Level.INFO).log("AFK detection service started.");
        }
        
        // Start playtime rewards service
        if (configManager.getConfig().playTimeRewards.enabled) {
            playTimeRewardService.start();
            getLogger().at(Level.INFO).log("PlayTime Rewards service started.");
        }
        
        // Start periodic play time save (crash protection)
        playerService.startPeriodicSave();
        
        // Initialize VaultUnlocked integration (economy cross-plugin support)
        if (configManager.getConfig().economy.enabled && vaultUnlockedIntegration != null) {
            vaultUnlockedIntegration.initialize();
        }
        
        getLogger().at(Level.INFO).log("EliteEssentials started successfully!");
        
        // Validate all JSON config files at the END of startup so errors are visible
        validateConfigsOnStartup();
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
        if (respawnListener != null) {
            try {
                EntityStore.REGISTRY.unregisterSystem(RespawnListener.class);
            } catch (Exception e) {
                // Ignore unregister errors
            }
        }
        
        // Save all player data (homes, back locations, etc. are now in player files)
        if (playerFileStorage != null) {
            playerFileStorage.saveAll();
            getLogger().at(Level.INFO).log("Player data saved.");
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
        if (joinQuitListener != null) {
            joinQuitListener.shutdown();
        }
        if (autoBroadcastService != null) {
            autoBroadcastService.shutdown();
        }
        if (playTimeRewardService != null) {
            playTimeRewardService.stop();
        }
        if (afkService != null) {
            afkService.stop();
        }
        if (playerService != null) {
            playerService.stopPeriodicSave();
        }
        if (vaultUnlockedIntegration != null) {
            vaultUnlockedIntegration.shutdown();
        }
        
        getLogger().at(Level.INFO).log("EliteEssentials disabled.");
    }
    
    private void registerCommands() {
        PluginConfig config = configManager.getConfig();
        StringBuilder registeredCommands = new StringBuilder();
        
        // Home commands
        if (config.homes.enabled) {
            getCommandRegistry().registerCommand(new HytaleHomeCommand(homeService, backService));
            getCommandRegistry().registerCommand(new HytaleSetHomeCommand(homeService));
            getCommandRegistry().registerCommand(new HytaleDelHomeCommand(homeService));
            getCommandRegistry().registerCommand(new HytaleHomesCommand(homeService));
            registeredCommands.append("/home, /sethome, /delhome, /homes, ");
        }
        
        // Back command
        if (config.back.enabled) {
            getCommandRegistry().registerCommand(new HytaleBackCommand(backService, cooldownService));
            registeredCommands.append("/back, ");
        }
        
        // RTP command
        if (config.rtp.enabled) {
            getCommandRegistry().registerCommand(new HytaleRtpCommand(rtpService, backService, configManager, warmupService));
            registeredCommands.append("/rtp, ");
        }
        
        // TPA commands
        if (config.tpa.enabled) {
            getCommandRegistry().registerCommand(new HytaleTpaCommand(tpaService));
            getCommandRegistry().registerCommand(new HytaleTpahereCommand(tpaService));
            getCommandRegistry().registerCommand(new HytaleTpAcceptCommand(tpaService, backService));
            getCommandRegistry().registerCommand(new HytaleTpDenyCommand(tpaService));
            registeredCommands.append("/tpa, /tpahere, /tpaccept, /tpdeny, ");
        }
        
        // Admin teleport commands (always register - admin only)
        getCommandRegistry().registerCommand(new HytaleTphereCommand(backService));
        registeredCommands.append("/tphere, ");
        
        // Spawn commands
        if (config.spawn.enabled) {
            getCommandRegistry().registerCommand(new HytaleSpawnCommand(backService));
            getCommandRegistry().registerCommand(new HytaleSetSpawnCommand(spawnStorage));
            registeredCommands.append("/spawn, /setspawn, ");
        }
        
        // Warp commands
        if (config.warps.enabled) {
            try {
                getCommandRegistry().registerCommand(new HytaleWarpCommand(warpService, backService));
                getCommandRegistry().registerCommand(new HytaleWarpAdminCommand(warpService));
                getCommandRegistry().registerCommand(new HytaleWarpSetPermCommand(warpService));
                getCommandRegistry().registerCommand(new HytaleWarpSetDescCommand(warpService));
                registeredCommands.append("/warp, /warpadmin, /setwarp, /warpsetperm, /warpsetdesc, ");
            } catch (Exception e) {
                getLogger().at(Level.SEVERE).log("Failed to register warp commands: " + e.getMessage());
            }
        }
        
        // Sleep command
        if (config.sleep.enabled) {
            getCommandRegistry().registerCommand(new HytaleSleepPercentCommand(configManager));
            registeredCommands.append("/sleeppercent, ");
        }
        
        // Admin commands (always register - admin only)
        getCommandRegistry().registerCommand(new HytaleReloadCommand());
        getCommandRegistry().registerCommand(new HytaleAliasCommand());
        getCommandRegistry().registerCommand(new HytaleMigrationCommand());
        registeredCommands.append("/eliteessentials, /alias, /eemigration, ");
        
        // God command
        if (config.god.enabled) {
            getCommandRegistry().registerCommand(new HytaleGodCommand(godService, configManager, cooldownService));
            registeredCommands.append("/god, ");
        }
        
        // Vanish command
        if (config.vanish.enabled) {
            getCommandRegistry().registerCommand(new HytaleVanishCommand(configManager, vanishService));
            registeredCommands.append("/vanish, ");
        }
        
        // Group chat command
        if (config.groupChat.enabled) {
            getCommandRegistry().registerCommand(new HytaleGroupChatCommand(groupChatService, configManager, muteService, playerService));
            getCommandRegistry().registerCommand(new HytaleChatsCommand(groupChatService, configManager));
            getCommandRegistry().registerCommand(new HytaleGcSpyCommand(groupChatService, configManager));
            getCommandRegistry().registerCommand(new HytaleGcSetCommand(groupChatService, playerService, configManager));
            registeredCommands.append("/gc, /g, /chats, /gcspy, /gcset, ");
        }
        
        // Send message command (admin - works from console)
        getCommandRegistry().registerCommand(new HytaleSendMessageCommand(configManager, groupChatService));
        registeredCommands.append("/sendmessage, ");
        
        // Repair command
        if (config.repair.enabled) {
            getCommandRegistry().registerCommand(new HytaleRepairCommand(configManager, cooldownService));
            registeredCommands.append("/repair, ");
        }
        
        // Heal command
        if (config.heal.enabled) {
            getCommandRegistry().registerCommand(new HytaleHealCommand(configManager, cooldownService));
            registeredCommands.append("/heal, ");
        }
        
        // Messaging commands
        if (config.msg.enabled) {
            getCommandRegistry().registerCommand(new HytaleMsgCommand(messageService, configManager));
            getCommandRegistry().registerCommand(new HytaleReplyCommand(messageService, configManager));
            registeredCommands.append("/msg, /reply, ");
        }
        
        // Top command
        if (config.top.enabled) {
            getCommandRegistry().registerCommand(new HytaleTopCommand(backService, configManager, cooldownService));
            registeredCommands.append("/top, ");
        }
        
        // Fly commands
        if (config.fly.enabled) {
            flyCommand = new HytaleFlyCommand(configManager, cooldownService);
            getCommandRegistry().registerCommand(flyCommand);
            getCommandRegistry().registerCommand(new HytaleFlySpeedCommand(configManager));
            registeredCommands.append("/fly, /flyspeed, ");
        }
        
        // Kit command
        if (config.kits.enabled) {
            getCommandRegistry().registerCommand(new HytaleKitCommand(kitService, configManager));
            registeredCommands.append("/kit, ");
        }
        
        // MOTD command
        if (config.motd.enabled) {
            getCommandRegistry().registerCommand(new HytaleMotdCommand(configManager, motdStorage));
            registeredCommands.append("/motd, ");
        }
        
        // Rules command
        if (config.rules.enabled) {
            getCommandRegistry().registerCommand(new HytaleRulesCommand(configManager, rulesStorage));
            registeredCommands.append("/rules, ");
        }
        
        // Broadcast command
        if (config.broadcast.enabled) {
            getCommandRegistry().registerCommand(new HytaleBroadcastCommand(configManager));
            registeredCommands.append("/broadcast, ");
        }
        
        // Clear chat command
        if (config.clearChat.enabled) {
            getCommandRegistry().registerCommand(new HytaleClearChatCommand(configManager));
            registeredCommands.append("/clearchat, ");
        }
        
        // Clear inventory command
        if (config.clearInv.enabled) {
            getCommandRegistry().registerCommand(new HytaleClearInvCommand(configManager, cooldownService));
            registeredCommands.append("/clearinv, ");
        }
        
        // Trash command
        if (config.trash.enabled) {
            getCommandRegistry().registerCommand(new HytaleTrashCommand(configManager, cooldownService));
            registeredCommands.append("/trash, ");
        }
        
        // List command
        if (config.list.enabled) {
            getCommandRegistry().registerCommand(new HytaleListCommand(configManager));
            registeredCommands.append("/list, ");
        }
        
        // Discord command
        if (config.discord.enabled) {
            getCommandRegistry().registerCommand(new HytaleDiscordCommand(configManager, discordStorage));
            registeredCommands.append("/discord, ");
        }
        
        // Economy commands
        // When useExternalEconomy is true, skip ALL economy commands - let the external plugin handle everything
        if (config.economy.enabled && !config.economy.useExternalEconomy) {
            getCommandRegistry().registerCommand(new HytaleWalletCommand(configManager, playerService));
            getCommandRegistry().registerCommand(new HytalePayCommand(configManager, playerService));
            getCommandRegistry().registerCommand(new HytaleBaltopCommand(configManager, playerService));
            getCommandRegistry().registerCommand(new HytaleEcoCommand(configManager, playerService));
            registeredCommands.append("/wallet, /pay, /baltop, /eco, ");
        }
        
        // Player info commands (always register - useful utility)
        getCommandRegistry().registerCommand(new HytaleSeenCommand(configManager, playerService));
        registeredCommands.append("/seen, ");
        
        // Joindate command
        if (config.joindate.enabled) {
            getCommandRegistry().registerCommand(new HytaleJoindateCommand(configManager, playerService));
            registeredCommands.append("/joindate, ");
        }
        
        // Playtime command
        if (config.playtime.enabled) {
            getCommandRegistry().registerCommand(new HytalePlaytimeCommand(configManager, playerService));
            registeredCommands.append("/playtime, ");
        }
        
        // Mail command
        if (config.mail.enabled) {
            getCommandRegistry().registerCommand(new HytaleMailCommand(mailService, configManager, playerFileStorage));
            registeredCommands.append("/mail, ");
        }
        
        // Help command (always register)
        getCommandRegistry().registerCommand(new HytaleHelpCommand());
        registeredCommands.append("/eehelp");
        
        // AFK command
        if (config.afk.enabled) {
            getCommandRegistry().registerCommand(new HytaleAfkCommand(afkService, configManager));
            registeredCommands.append(", /afk");
        }
        
        // Ignore commands
        if (config.ignore.enabled) {
            getCommandRegistry().registerCommand(new HytaleIgnoreCommand(ignoreService, configManager));
            getCommandRegistry().registerCommand(new HytaleUnignoreCommand(ignoreService, configManager, playerFileStorage));
            registeredCommands.append(", /ignore, /unignore");
        }
        
        // Mute commands (admin only)
        if (config.mute.enabled) {
            getCommandRegistry().registerCommand(new HytaleMuteCommand(muteService, configManager));
            getCommandRegistry().registerCommand(new HytaleUnmuteCommand(muteService, configManager));
            registeredCommands.append(", /mute, /unmute");
        }
        
        // Ban commands (admin only)
        if (config.ban.enabled) {
            getCommandRegistry().registerCommand(new HytaleBanCommand(banService, configManager));
            getCommandRegistry().registerCommand(new HytaleUnbanCommand(banService, configManager));
            getCommandRegistry().registerCommand(new HytaleTempBanCommand(tempBanService, configManager));
            getCommandRegistry().registerCommand(new HytaleIpBanCommand(ipBanService, configManager));
            getCommandRegistry().registerCommand(new HytaleUnIpBanCommand(ipBanService, configManager));
            registeredCommands.append(", /ban, /unban, /tempban, /ipban, /unipban");
        }
        
        // Freeze command (admin only)
        if (config.freeze.enabled) {
            getCommandRegistry().registerCommand(new HytaleFreezeCommand(freezeService, configManager));
            registeredCommands.append(", /freeze");
        }
        
        getLogger().at(Level.INFO).log("Commands registered: " + registeredCommands.toString());
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

    public VanishService getVanishService() {
        return vanishService;
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
    
    public GroupChatService getGroupChatService() {
        return groupChatService;
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
    
    public SpawnStorage getSpawnStorage() {
        return spawnStorage;
    }
    
    public MotdStorage getMotdStorage() {
        return motdStorage;
    }
    
    public RulesStorage getRulesStorage() {
        return rulesStorage;
    }
    
    public DiscordStorage getDiscordStorage() {
        return discordStorage;
    }
    
    public StarterKitEvent getStarterKitEvent() {
        return starterKitEvent;
    }
    
    public AliasService getAliasService() {
        return aliasService;
    }
    
    public PlayerService getPlayerService() {
        return playerService;
    }
    
    public MailService getMailService() {
        return mailService;
    }
    
    public PlayerFileStorage getPlayerFileStorage() {
        return playerFileStorage;
    }
    
    public CostService getCostService() {
        return costService;
    }
    
    public VaultUnlockedIntegration getVaultUnlockedIntegration() {
        return vaultUnlockedIntegration;
    }
    
    public PlayTimeRewardService getPlayTimeRewardService() {
        return playTimeRewardService;
    }
    
    public AfkService getAfkService() {
        return afkService;
    }
    
    public IgnoreService getIgnoreService() {
        return ignoreService;
    }
    
    public MuteService getMuteService() {
        return muteService;
    }
    
    public BanService getBanService() {
        return banService;
    }
    
    public TempBanService getTempBanService() {
        return tempBanService;
    }
    
    public IpBanService getIpBanService() {
        return ipBanService;
    }
    
    public FreezeService getFreezeService() {
        return freezeService;
    }
    
    public File getPluginDataFolder() {
        return dataFolder;
    }
    
    public File getDataFolder() {
        return dataFolder;
    }
    
    public WarpStorage getWarpStorage() {
        return warpStorage;
    }
    
    /**
     * Reload the plugin configuration.
     * Called by /eliteessentials reload command.
     */
    public void reloadConfig() {
        getLogger().at(Level.INFO).log("Reloading EliteEssentials configuration...");
        
        // Reload main config
        configManager.loadConfig();
        
        // Update packet filters for all online players (for suppressDefaultMessages setting)
        if (joinQuitListener != null) {
            joinQuitListener.updatePacketFiltersForAll();
        }
        
        // Reload storage files (allows external edits to be picked up)
        motdStorage.load();
        rulesStorage.load();
        discordStorage.load();
        warpStorage.load();
        spawnStorage.load();
        
        // Refresh spawn protection locations from reloaded spawn storage
        spawnProtectionService.loadFromStorage(spawnStorage);
        
        // Reload services
        kitService.reload();
        if (starterKitEvent != null) {
            starterKitEvent.reload();
        }
        
        // Reload auto broadcast
        if (autoBroadcastService != null) {
            if (configManager.getConfig().autoBroadcast.enabled) {
                autoBroadcastService.reload();
            } else {
                autoBroadcastService.shutdown();
            }
        }
        
        // Reload aliases (note: deleted aliases won't be removed until restart)
        if (aliasService != null && configManager.getConfig().aliases.enabled) {
            aliasService.reload();
        }
        
        // Reload group chat configuration
        if (groupChatService != null) {
            groupChatService.reload();
        }
        
        // Reload player file storage index
        if (playerFileStorage != null) {
            playerFileStorage.reload();
        }
        
        // Reload playtime rewards
        if (playTimeRewardService != null) {
            playTimeRewardService.reload();
        }
        
        // Reload AFK service
        if (afkService != null) {
            afkService.reload();
        }
        
        // Reload mute service
        if (muteService != null) {
            muteService.reload();
        }
        
        // Reload ban services
        if (banService != null) {
            banService.reload();
        }
        if (tempBanService != null) {
            tempBanService.reload();
        }
        if (ipBanService != null) {
            ipBanService.reload();
        }
        if (freezeService != null) {
            freezeService.reload();
        }
        
        // Restart periodic play time save (interval may have changed)
        if (playerService != null) {
            playerService.startPeriodicSave();
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
    
    /**
     * Initialize spawn protection locations from stored spawns (per-world).
     */
    private void initializeSpawnProtectionLocation() {
        // Load all per-world spawns into protection service
        spawnProtectionService.loadFromStorage(spawnStorage);
        
        java.util.Set<String> protectedWorlds = spawnProtectionService.getProtectedWorlds();
        if (!protectedWorlds.isEmpty()) {
            int radius = configManager.getConfig().spawnProtection.radius;
            getLogger().at(Level.INFO).log("Spawn protection initialized for " + protectedWorlds.size() + 
                " world(s): " + String.join(", ", protectedWorlds) + " (radius: " + radius + ")");
        }
    }
    
    /**
     * Validate all JSON config files on startup.
     * Logs errors for any malformed files to help server admins catch issues early.
     */
    private void validateConfigsOnStartup() {
        java.util.List<ConfigManager.ConfigValidationResult> errors = configManager.validateAllFiles();
        
        if (errors.isEmpty()) {
            logger.info("All configuration files validated successfully.");
        } else {
            logger.severe("========================================");
            logger.severe("  CONFIG VALIDATION ERRORS DETECTED!");
            logger.severe("========================================");
            
            for (ConfigManager.ConfigValidationResult error : errors) {
                logger.severe("File: " + error.getFilename());
                logger.severe(error.getErrorMessage());
                logger.severe("----------------------------------------");
            }
            
            logger.severe("Fix these errors and restart the server or use /ee reload");
            logger.severe("========================================");
        }
    }
}
