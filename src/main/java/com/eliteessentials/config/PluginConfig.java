package com.eliteessentials.config;

import java.util.HashMap;
import java.util.Map;

/**
 * POJO representing the plugin configuration structure.
 * Loaded from config.json via Gson.
 * 
 * This file is saved to: mods/EliteEssentials/config.json
 * Server owners can edit this file to customize all settings.
 */
public class PluginConfig {

    // ==================== GENERAL ====================
    
    /** Enable debug logging (verbose output for troubleshooting) */
    public boolean debug = false;
    
    /** 
     * Enable advanced permissions system.
     * When false (default): Simple mode - commands are either for Everyone or Admin only.
     * When true: Full granular permissions (eliteessentials.command.home.home, etc.)
     */
    public boolean advancedPermissions = false;

    // ==================== COMMAND CONFIGS ====================
    
    public RtpConfig rtp = new RtpConfig();
    public BackConfig back = new BackConfig();
    public TpaConfig tpa = new TpaConfig();
    public HomesConfig homes = new HomesConfig();
    public SpawnConfig spawn = new SpawnConfig();
    public WarpsConfig warps = new WarpsConfig();
    public SleepConfig sleep = new SleepConfig();
    public DeathMessagesConfig deathMessages = new DeathMessagesConfig();
    public GodConfig god = new GodConfig();
    public HealConfig heal = new HealConfig();
    public MsgConfig msg = new MsgConfig();
    public FlyConfig fly = new FlyConfig();
    public VanishConfig vanish = new VanishConfig();
    public GroupChatConfig groupChat = new GroupChatConfig();
    public RepairConfig repair = new RepairConfig();
    public TopConfig top = new TopConfig();
    public KitsConfig kits = new KitsConfig();
    public SpawnProtectionConfig spawnProtection = new SpawnProtectionConfig();
    public MotdConfig motd = new MotdConfig();
    public RulesConfig rules = new RulesConfig();
    public JoinMsgConfig joinMsg = new JoinMsgConfig();
    public BroadcastConfig broadcast = new BroadcastConfig();
    public ClearInvConfig clearInv = new ClearInvConfig();
    public ListConfig list = new ListConfig();
    public ChatFormatConfig chatFormat = new ChatFormatConfig();
    public DiscordConfig discord = new DiscordConfig();
    public AutoBroadcastConfig autoBroadcast = new AutoBroadcastConfig();
    public AliasConfig aliases = new AliasConfig();
    public EconomyConfig economy = new EconomyConfig();
    public MailConfig mail = new MailConfig();
    
    // ==================== MESSAGES ====================
    
    /**
     * @deprecated Messages are now stored in messages.json.
     * This field is kept only for migration purposes and default value generation.
     * Use ConfigManager.getMessage() to access messages.
     */
    @Deprecated
    public Map<String, String> messages = new HashMap<>();

    public PluginConfig() {
        initDefaultMessages();
    }
    
    /**
     * Initialize default messages.
     * These are used for migration and to ensure all message keys exist.
     */
    private void initDefaultMessages() {
        // ==================== GENERAL ====================
        messages.put("prefix", "&7[&bEliteEssentials&7]&r ");
        messages.put("noPermission", "&cYou don't have permission to use this command.");
        messages.put("playerNotFound", "&cPlayer '&e{player}&c' is not online.");
        messages.put("commandDisabled", "&cThis command is disabled.");
        messages.put("onCooldown", "&eYou must wait &c{seconds} &eseconds before using this command again.");
        messages.put("warmupStarted", "&eTeleporting in &a{seconds} &eseconds. Don't move!");
        messages.put("warmupCancelled", "&cTeleport cancelled - you moved!");
        messages.put("warmupCountdown", "&eTeleporting in &a{seconds}&e...");
        messages.put("teleportInProgress", "&cYou already have a teleport in progress!");
        messages.put("couldNotGetPosition", "&cCould not get your position.");
        
        // ==================== TPA ====================
        messages.put("tpaRequestSent", "&aTeleport request sent to &f{player}&a.");
        messages.put("tpaRequestReceived", "&e{player} &awants to teleport to you.");
        messages.put("tpaRequestInstructions", "&7Type &a/tpaccept &7to accept or &c/tpdeny &7to deny.");
        messages.put("tpaSelfRequest", "&cYou cannot teleport to yourself.");
        messages.put("tpaAlreadyPending", "&cYou already have a pending request to this player.");
        messages.put("tpaRequestFailed", "&cCould not send teleport request.");
        messages.put("tpaNoPending", "&cYou have no pending teleport requests.");
        messages.put("tpaExpired", "&cTeleport request has expired.");
        messages.put("tpaPlayerOffline", "&c{player} is no longer online.");
        messages.put("tpaAccepted", "&aTeleport request accepted! &f{player} &awill teleport to you shortly.");
        messages.put("tpaAcceptedRequester", "&a{player} accepted your teleport request!");
        messages.put("tpaRequesterWarmup", "&eTeleporting to &f{player} &ein &a{seconds} &eseconds... Stand still!");
        messages.put("tpaRequesterInProgress", "&cThe requester already has a teleport in progress.");
        messages.put("tpaDenied", "&cTeleport request from &f{player} &cdenied.");
        messages.put("tpaDeniedRequester", "&c{player} denied your teleport request.");
        messages.put("tpaCouldNotFindRequester", "&cCould not find requester.");
        messages.put("tpaCouldNotGetRequesterPosition", "&cCould not get requester's position.");
        messages.put("tpaOpenFailed", "&cCould not open the teleport menu.");
        
        // ==================== TPAHERE ====================
        messages.put("tpahereRequestSent", "&aTeleport request sent to &f{player}&a. They will teleport to you if they accept.");
        messages.put("tpahereRequestReceived", "&e{player} &awants you to teleport to them.");
        messages.put("tpahereAcceptedTarget", "&aTeleporting to &f{player}&a...");
        messages.put("tpahereAcceptedRequester", "&a{player} &aaccepted your request and is teleporting to you!");
        
        // ==================== TPHERE (Admin) ====================
        messages.put("tphereSuccess", "&aTeleported &f{player} &ato your location.");
        messages.put("tphereTeleported", "&eYou have been teleported to &f{player}&e.");
        messages.put("tphereSelf", "&cYou cannot teleport yourself to yourself!");
        
        // ==================== HOMES ====================
        messages.put("homeNoHomes", "&eYou have no homes set. Use &a/sethome &eto create one.");
        messages.put("homeListHeader", "&aYour homes &7({count}/{max})&a:");
        messages.put("homeNotFound", "&cHome &e'{name}' &cnot found.");
        messages.put("homeNoHomeSet", "&eYou don't have a home set. Use &a/sethome &efirst.");
        messages.put("homeTeleported", "&aTeleported to home &e'{name}'&a.");
        messages.put("homeWarmup", "&eTeleporting to home &a'{name}' &ein &a{seconds} &eseconds... Stand still!");
        messages.put("homeSet", "&aHome &e'{name}' &ahas been set!");
        messages.put("homeLimitReached", "&cYou have reached your home limit &7({max})&c.");
        messages.put("homeInvalidName", "&cInvalid home name.");
        messages.put("homeSetFailed", "&cFailed to set home.");
        messages.put("homeDeleted", "&aHome &e'{name}' &ahas been deleted.");
        messages.put("homeDeleteFailed", "&cFailed to delete home.");
        messages.put("homeRenamed", "&aHome renamed to &e'{name}'&a.");
        messages.put("homeRenameFailed", "&cFailed to rename home.");
        messages.put("homeNameTaken", "&cA home named &e'{name}' &calready exists.");
        messages.put("homeEditOpenFailed", "&cCould not open home editor.");
        messages.put("cannotSetHomeInInstance", "&cYou cannot set a home in a temporary instance world!");
        
        // ==================== WARPS ====================
        messages.put("warpNoWarps", "&cNo warps available.");
        messages.put("warpListHeader", "&aAvailable warps: &f");
        messages.put("warpNotFound", "&cWarp &e'{name}' &cnot found. Available: &7{list}");
        messages.put("warpNoPermission", "&cYou don't have permission to use this warp.");
        messages.put("warpTeleported", "&aTeleported to warp &e'{name}'&a.");
        messages.put("warpWarmup", "&eTeleporting to warp &a'{name}' &ein &a{seconds} &eseconds... Stand still!");
        messages.put("warpCreated", "&aCreated warp &e'{name}' &afor &7{permission} &aat &7{location}&a.");
        messages.put("warpUpdated", "&aUpdated warp &e'{name}' &afor &7{permission} &aat &7{location}&a.");
        messages.put("warpInvalidPermission", "&cInvalid permission &e'{value}'&c. Use &7'all' &cor &7'op'&c.");
        messages.put("warpDeleted", "&aDeleted warp &e'{name}'&a.");
        messages.put("warpDeleteFailed", "&cFailed to delete warp.");
        messages.put("warpListTitle", "&b&l=== &fServer Warps &b&l===");
        messages.put("warpListFooter", "&7Use &a/warp <name> &7to teleport.");
        messages.put("cannotSetWarpInInstance", "&cYou cannot set a warp in a temporary instance world!");
        
        // ==================== WARP ADMIN ====================
        messages.put("warpAdminNoWarps", "&cNo warps configured.");
        messages.put("warpAdminCreateHint", "&7Use &a/warpadmin create <name> [all|op] &7to create one.");
        messages.put("warpAdminTitle", "&b&l=== &fWarp Admin Panel &b&l===");
        messages.put("warpAdminTotal", "&7Total warps: &a{count}");
        messages.put("warpAdminCommands", "&eCommands:");
        messages.put("warpAdminInfoTitle", "&b&l=== &fWarp: &e{name} &b&l===");
        messages.put("warpAdminPermissionUpdated", "&aWarp &e'{name}' &apermission updated to &7{permission}&a.");
        messages.put("warpAdminDescriptionUpdated", "&aWarp &e'{name}' &adescription updated to: &7{description}");
        
        // ==================== BACK ====================
        messages.put("backNoLocation", "&cNo previous location to go back to.");
        messages.put("backTeleported", "&aTeleported to your previous location.");
        messages.put("backWarmup", "&eTeleporting back in &a{seconds} &eseconds... Stand still!");
        
        // ==================== SPAWN ====================
        messages.put("spawnNoSpawn", "&cNo spawn point set. An admin must use &e/setspawn &cfirst.");
        messages.put("spawnNotFound", "&cCould not find spawn point.");
        messages.put("spawnTeleported", "&aTeleported to spawn!");
        messages.put("spawnWarmup", "&eTeleporting to spawn in &a{seconds} &eseconds... Stand still!");
        
        // ==================== RTP ====================
        messages.put("rtpSearching", "&eSearching for a safe location...");
        messages.put("rtpPreparing", "&ePreparing random teleport... Stand still for &a{seconds} &eseconds!");
        messages.put("rtpTeleported", "&aTeleported to &7{location}&a.");
        messages.put("rtpTeleportedWorld", "&aTeleported to &7{location} &ain world &b{world}&a.");
        messages.put("rtpFailed", "&cCould not find a safe location after &e{attempts} &cattempts. Try again.");
        messages.put("rtpCouldNotDeterminePosition", "&cCould not determine your position.");
        
        // ==================== SLEEP ====================
        messages.put("sleepProgress", "&e{sleeping}&7/&e{needed} &7players sleeping...");
        messages.put("sleepSkipping", "&a{sleeping}&7/&a{needed} &aplayers sleeping - Skipping to morning!");
        
        // ==================== GOD MODE ====================
        messages.put("godEnabled", "&aGod mode enabled. You are now invincible!");
        messages.put("godDisabled", "&cGod mode disabled.");
        
        // ==================== HEAL ====================
        messages.put("healSuccess", "&aYou have been healed to full health!");
        messages.put("healFailed", "&cCould not heal you.");
        
        // ==================== PRIVATE MESSAGING ====================
        messages.put("msgUsage", "&cUsage: &e/msg <player> <message>");
        messages.put("msgSelf", "&cYou cannot message yourself.");
        messages.put("msgSent", "&d[To &f{player}&d] &7{message}");
        messages.put("msgReceived", "&d[From &f{player}&d] &7{message}");
        messages.put("replyNoOne", "&cYou have no one to reply to.");
        messages.put("replyOffline", "&cThat player is no longer online.");
        messages.put("replyUsage", "&cUsage: &e/reply <message>");
        
        // ==================== FLY ====================
        messages.put("flyEnabled", "&aFlight mode enabled! Double-tap jump to fly.");
        messages.put("flyDisabled", "&cFlight mode disabled.");
        messages.put("flyFailed", "&cCould not access movement settings.");
        messages.put("flySpeedSet", "&aFly speed set to &e{speed}x&a.");
        messages.put("flySpeedReset", "&aFly speed reset to default.");
        messages.put("flySpeedInvalid", "&cInvalid speed value. Use a number &7(10-100) &cor &e'reset'&c.");
        messages.put("flySpeedOutOfRange", "&cSpeed must be between &e10 &cand &e100&c, or use &e'reset'&c.");
        
        // ==================== VANISH ====================
        messages.put("vanishEnabled", "&aYou are now vanished. Other players cannot see you.");
        messages.put("vanishDisabled", "&cYou are now visible to other players.");
        messages.put("vanishReminder", "&c&l>> YOU ARE STILL VANISHED <<");
        messages.put("vanishFakeLeave", "&e{player} &7left the server.");
        messages.put("vanishFakeJoin", "&e{player} &7joined the server.");
        
        // ==================== GROUP CHAT ====================
        messages.put("groupChatNoAccess", "&cYou don't have access to any chat channels.");
        messages.put("groupChatUsage", "&cUsage: &e/gc <message>");
        messages.put("groupChatUsageGroup", "&cUsage: &e/gc {group} <message>");
        messages.put("groupChatUsageMultiple", "&cUsage: &e/gc [chat] <message> &7- Chats: {groups}");
        
        // ==================== CHATS LIST ====================
        messages.put("chatsNoAccess", "&cYou don't have access to any chat channels.");
        messages.put("chatsHeader", "&b&l=== &fYour Chat Channels &7({count}) &b&l===");
        messages.put("chatsEntry", "{color}{prefix} &f{name} &7- {displayName}");
        messages.put("chatsFooter", "&7Use &a/gc [chat] <message> &7or &a/g [chat] <message> &7to chat.");
        
        // ==================== REPAIR ====================
        messages.put("repairSuccess", "&aRepaired the item in your hand.");
        messages.put("repairAllSuccess", "&aRepaired &e{count} &aitems.");
        messages.put("repairNoItem", "&cYou are not holding an item.");
        messages.put("repairNotDamaged", "&cThis item is not damaged.");
        messages.put("repairNothingToRepair", "&cNo items need repair.");
        messages.put("repairNoPermissionAll", "&cYou don't have permission to repair all items.");
        
        // ==================== TOP ====================
        messages.put("topTeleported", "&aTeleported to the top!");
        messages.put("topChunkNotLoaded", "&cChunk not loaded.");
        messages.put("topNoGround", "&cNo solid ground found above.");
        
        // ==================== KITS ====================
        messages.put("kitNoKits", "&cNo kits are available.");
        messages.put("kitNotFound", "&cKit not found.");
        messages.put("kitNoPermission", "&cYou don't have permission to use this kit.");
        messages.put("kitOnCooldown", "&cThis kit is on cooldown. &e{time} &cremaining.");
        messages.put("kitAlreadyClaimed", "&cYou have already claimed this one-time kit.");
        messages.put("kitClaimed", "&aYou received the &e{kit} &akit!");
        messages.put("kitClaimFailed", "&cCould not claim kit.");
        messages.put("kitOpenFailed", "&cCould not open kit menu.");
        
        // ==================== MOTD ====================
        messages.put("motdTitle", "&b&l=== &fMessage of the Day &b&l===");
        messages.put("motdLine1", "&aWelcome to the server!");
        messages.put("motdLine2", "&7Type &e/help &7for commands.");
        messages.put("motdLine3", "&aHave fun!");
        messages.put("motdEmpty", "&cNo MOTD configured.");
        
        // ==================== RULES ====================
        messages.put("rulesEmpty", "&cNo rules configured.");
        
        // ==================== JOIN MESSAGES ====================
        messages.put("joinMessage", "&e{player} &7joined the server.");
        messages.put("firstJoinMessage", "&e{player} &ajoined the server for the first time! Welcome!");
        messages.put("quitMessage", "&e{player} &7left the server.");
        messages.put("worldJoinMessage", "&7{player} entered {world}");
        messages.put("worldLeaveMessage", "&7{player} left {world}");
        
        // ==================== BROADCAST ====================
        messages.put("broadcast", "&6&l[BROADCAST] &r&e{message}");
        
        // ==================== CLEAR INVENTORY ====================
        messages.put("clearInvSuccess", "&aCleared &e{count} &aitems from your inventory.");
        messages.put("clearInvFailed", "&cCould not clear inventory.");
        
        // ==================== LIST (Online Players) ====================
        messages.put("listHeader", "&aOnline Players &7({count}/{max})&a:");
        messages.put("listPlayers", "&f{players}");
        messages.put("listNoPlayers", "&cNo players online.");
        
        // ==================== WARPS (additional) ====================
        messages.put("warpLimitReached", "&cWarp limit reached! &7({count}/{max})");
        messages.put("warpLimitInfo", "&7Warp limit: &e{count}&7/&e{max}");
        
        // ==================== DISCORD ====================
        messages.put("discordEmpty", "&cNo discord information configured.");
        
        // ==================== ALIASES ====================
        messages.put("aliasCreated", "&aCreated alias &e/{name} &a-> &f/{command} &7[{permission}]");
        messages.put("aliasUpdated", "&aUpdated alias &e/{name} &a-> &f/{command} &7[{permission}]");
        messages.put("aliasDeleted", "&aDeleted alias &e/{name}&a.");
        messages.put("aliasNotFound", "&cAlias &e'{name}' &cnot found.");
        
        // ==================== ECONOMY ====================
        messages.put("walletBalance", "&aYour balance: &e{balance} &7{currency}");
        messages.put("walletBalanceOther", "&a{player}'s balance: &e{balance} &7{currency}");
        messages.put("walletAdminUsage", "&eUsage: &f/wallet <set|add|remove> <player> <amount>");
        messages.put("walletSet", "&aSet &e{player}&a's balance to &e{balance}&a.");
        messages.put("walletAdded", "&aAdded &e{amount} &ato &e{player}&a's wallet. New balance: &e{balance}");
        messages.put("walletRemoved", "&aRemoved &e{amount} &afrom &e{player}&a's wallet. New balance: &e{balance}");
        messages.put("walletInvalidAmount", "&cInvalid amount. Must be a positive number.");
        messages.put("walletInsufficientFunds", "&c{player} doesn't have enough funds.");
        messages.put("walletFailed", "&cFailed to update wallet.");
        messages.put("paySent", "&aSent &e{amount} &ato &f{player}&a.");
        messages.put("payReceived", "&aReceived &e{amount} &afrom &f{player}&a.");
        messages.put("payInvalidAmount", "&cAmount must be greater than 0.");
        messages.put("payMinimum", "&cMinimum payment is &e{amount}&c.");
        messages.put("paySelf", "&cYou cannot pay yourself.");
        messages.put("payInsufficientFunds", "&cInsufficient funds. Your balance: &e{balance}");
        messages.put("payFailed", "&cPayment failed.");
        messages.put("baltopHeader", "&b&l=== &fRichest Players &b&l===");
        messages.put("baltopEntry", "&e{rank}. &f{player} &7- &a{balance}");
        
        // ==================== COMMAND COSTS ====================
        messages.put("costCharged", "&7-{cost} {currency}");
        messages.put("costInsufficientFunds", "&cInsufficient funds. Cost: &e{cost} {currency}&c, Balance: &e{balance} {currency}");
        messages.put("costFailed", "&cFailed to process payment.");
        messages.put("baltopYourBalance", "&7Your balance: &a{balance}");
        messages.put("baltopEmpty", "&cNo player data found.");
        
        // ==================== MAIL ====================
        messages.put("mailUsage", "&eUsage: &f/mail <send|read|list|clear|delete>");
        messages.put("mailSendUsage", "&eUsage: &f/mail send <player> <message>");
        messages.put("mailDeleteUsage", "&eUsage: &f/mail delete <number>");
        messages.put("mailEmpty", "&7You have no mail.");
        messages.put("mailSent", "&aMail sent to &f{player}&a.");
        messages.put("mailReceived", "&aYou received new mail from &f{player}&a! Type &e/mail read &ato view.");
        messages.put("mailSendSelf", "&cYou cannot send mail to yourself.");
        messages.put("mailPlayerNotFound", "&cPlayer '&e{player}&c' has never joined this server.");
        messages.put("mailOnCooldown", "&cPlease wait &e{seconds} &cseconds before sending mail to this player again.");
        messages.put("mailRecipientFull", "&c{player}'s mailbox is full.");
        messages.put("mailSendFailed", "&cFailed to send mail.");
        messages.put("mailMessageTooLong", "&cMessage too long. Maximum &e{max} &ccharacters.");
        messages.put("mailListHeader", "&b&l=== &fYour Mail &7({count} total, {unread} unread) &b&l===");
        messages.put("mailListEntry", "{status}&f{number}. &7{date} &e{player}&7: &f{preview}");
        messages.put("mailListMore", "&7...and {count} more. Use &e/mail read <number> &7to view.");
        messages.put("mailListFooter", "&7Use &a/mail read [number] &7to read, &c/mail clear &7to delete all.");
        messages.put("mailReadHeader", "&b&l=== &fMail {number}/{total} &b&l===");
        messages.put("mailReadFrom", "&7From: &e{player} &7on &e{date}");
        messages.put("mailReadContent", "&f{message}");
        messages.put("mailNotFound", "&cMail not found.");
        messages.put("mailInvalidNumber", "&cInvalid mail number.");
        messages.put("mailCleared", "&aCleared &e{count} &amail messages.");
        messages.put("mailClearedRead", "&aCleared &e{count} &aread mail messages.");
        messages.put("mailDeleted", "&aMail deleted.");
        messages.put("mailDeleteFailed", "&cFailed to delete mail.");
        messages.put("mailNotifyLogin", "&aYou have &e{count} &aunread mail message(s). Type &e/mail &ato view.");
        
        // ==================== SEEN ====================
        messages.put("seenOnline", "&a{player} &7is currently &aonline&7.");
        messages.put("seenLastSeen", "&f{player} &7was last seen &e{time}&7.");
        messages.put("seenNeverJoined", "&c{player} &7has never joined this server.");
        
        // ==================== DEATH MESSAGES ====================
        messages.put("deathByEntity", "{player} was killed by {killer}");
        messages.put("deathByPlayer", "{player} was killed by {killer}");
        messages.put("deathByFall", "{player} fell to their death");
        messages.put("deathByFire", "{player} burned to death");
        messages.put("deathByLava", "{player} burned to death");
        messages.put("deathByDrowning", "{player} drowned");
        messages.put("deathBySuffocation", "{player} suffocated");
        messages.put("deathByVoid", "{player} fell into the void");
        messages.put("deathByStarvation", "{player} starved to death");
        messages.put("deathByProjectile", "{player} was shot");
        messages.put("deathByExplosion", "{player} blew up");
        messages.put("deathByLightning", "{player} was struck by lightning");
        messages.put("deathByFreeze", "{player} froze to death");
        messages.put("deathByPoison", "{player} was poisoned");
        messages.put("deathByWither", "{player} withered away");
        messages.put("deathGeneric", "{player} died");
        
        // ==================== GUI LABELS ====================
        messages.put("guiHomesTitle", "Your Homes ({count}/{max})");
        messages.put("guiWarpsTitle", "Server Warps");
        messages.put("guiKitStatusLocked", "[Locked]");
        messages.put("guiKitStatusClaimed", "Claimed");
        messages.put("guiKitStatusReady", "Ready");
        messages.put("guiWarpStatusOpOnly", "[OP Only]");
        
        // ==================== PLAYTIME REWARDS ====================
        messages.put("playTimeRewardReceived", "&a[Reward] &fYou received: &e{reward}");
        messages.put("playTimeMilestoneBroadcast", "&6[Milestone] &f{player} &7reached &e{reward} &7({time} playtime)!");
    }

    // ==================== RTP (Random Teleport) ====================
    
    public static class RtpConfig {
        /** Enable/disable the /rtp command */
        public boolean enabled = true;
        
        /** Minimum distance from player for random location (default for all worlds) */
        public int minRange = 100;
        
        /** Maximum distance from player for random location (default for all worlds) */
        public int maxRange = 5000;
        
        /**
         * Per-world RTP range configuration.
         * Key = world name (case-sensitive), Value = WorldRtpRange with min/max for that world.
         * If a world is not in this map, it uses the default minRange/maxRange above.
         * Example: {"nether": {minRange: 50, maxRange: 2000}, "end": {minRange: 100, maxRange: 1000}}
         */
        public Map<String, WorldRtpRange> worldRanges = createDefaultWorldRanges();
        
        /** Cooldown in seconds between uses (0 = no cooldown) */
        public int cooldownSeconds = 30;
        
        /** Warmup in seconds - player must stand still (0 = instant) */
        public int warmupSeconds = 3;
        
        /** Max attempts to find a safe location before giving up */
        public int maxAttempts = 5;
        
        /** Minimum Y level - rejects locations below this (avoid dungeons) */
        public int minSurfaceY = 50;
        
        /** Timeout in milliseconds for loading unloaded chunks (0 = skip unloaded chunks) */
        public int chunkLoadTimeoutMs = 500;
        
        /** Default Y height to use when chunk is not loaded (0 = skip unloaded chunks) */
        public int defaultHeight = 128;
        
        /** Seconds of invulnerability after RTP to prevent fall damage (0 = disabled) */
        public int invulnerabilitySeconds = 5;
        
        /** Cost to use this command (0 = free, requires economy enabled) */
        public double cost = 0.0;
        
        private static Map<String, WorldRtpRange> createDefaultWorldRanges() {
            Map<String, WorldRtpRange> ranges = new HashMap<>();
            // Example configurations - server owners can customize these
            // ranges.put("nether", new WorldRtpRange(50, 2000));
            // ranges.put("end", new WorldRtpRange(100, 1000));
            return ranges;
        }
        
        /**
         * Get the RTP range for a specific world.
         * Returns world-specific range if configured, otherwise returns default range.
         */
        public WorldRtpRange getRangeForWorld(String worldName) {
            WorldRtpRange worldRange = worldRanges.get(worldName);
            if (worldRange != null) {
                return worldRange;
            }
            // Return default range
            return new WorldRtpRange(minRange, maxRange);
        }
    }
    
    /**
     * Per-world RTP range configuration.
     */
    public static class WorldRtpRange {
        /** Minimum distance for this world */
        public int minRange;
        
        /** Maximum distance for this world */
        public int maxRange;
        
        public WorldRtpRange() {
            this(100, 5000);
        }
        
        public WorldRtpRange(int minRange, int maxRange) {
            this.minRange = minRange;
            this.maxRange = maxRange;
        }
    }

    // ==================== BACK ====================
    
    public static class BackConfig {
        /** Enable/disable the /back command */
        public boolean enabled = true;
        
        /** How many previous locations to remember per player */
        public int maxHistory = 5;
        
        /** Save location on death (allows /back to death point) */
        public boolean workOnDeath = true;
        
        /** Cooldown in seconds between uses (0 = no cooldown) */
        public int cooldownSeconds = 0;
        
        /** Warmup in seconds - player must stand still (0 = instant) */
        public int warmupSeconds = 0;
        
        /** Cost to use this command (0 = free, requires economy enabled) */
        public double cost = 0.0;
    }

    // ==================== TPA (Teleport Ask) ====================
    
    public static class TpaConfig {
        /** Enable/disable /tpa, /tpaccept, /tpdeny commands */
        public boolean enabled = true;
        
        /** Seconds before a TPA request expires */
        public int timeoutSeconds = 30;
        
        /** Warmup in seconds after accepting - requester must stand still (0 = instant) */
        public int warmupSeconds = 3;
        
        /** Cost to use /tpa (0 = free, requires economy enabled) */
        public double cost = 0.0;
        
        /** Cost to use /tpahere (0 = free, requires economy enabled) */
        public double tpahereCost = 0.0;
    }

    // ==================== HOMES ====================
    
    public static class HomesConfig {
        /** Enable/disable /home, /sethome, /delhome, /homes commands */
        public boolean enabled = true;
        
        /** Maximum homes per player */
        public int maxHomes = 3;
        
        /** Default max homes for new players */
        public int defaultMaxHomes = 3;
        
        /** Cooldown in seconds between /home uses (0 = no cooldown) */
        public int cooldownSeconds = 0;
        
        /** Warmup in seconds - player must stand still (0 = instant) */
        public int warmupSeconds = 3;
        
        /** Cost to teleport home (0 = free, requires economy enabled) */
        public double cost = 0.0;
        
        /** Cost to set a home (0 = free, requires economy enabled) */
        public double setHomeCost = 0.0;
    }

    // ==================== SPAWN ====================
    
    public static class SpawnConfig {
        /** Enable/disable the /spawn command */
        public boolean enabled = true;
        
        /** Cooldown in seconds between uses (0 = no cooldown) */
        public int cooldownSeconds = 0;
        
        /** Warmup in seconds - player must stand still (0 = instant) */
        public int warmupSeconds = 3;
        
        /** 
         * Per-world spawn behavior.
         * When false (default): /spawn always teleports to the main world's spawn.
         * When true: /spawn teleports to the spawn point of the player's current world.
         */
        public boolean perWorld = false;
        
        /** 
         * Main world name (used when perWorld = false).
         * Players will always teleport to this world's spawn regardless of which world they're in.
         */
        public String mainWorld = "default";
        
        /** Cost to use this command (0 = free, requires economy enabled) */
        public double cost = 0.0;
        
        /**
         * Teleport new players to /setspawn location on first join.
         * When true: First-time players are teleported to the spawn point after joining.
         * When false (default): Players spawn at the world's default spawn location.
         */
        public boolean teleportOnFirstJoin = true;
    }

    // ==================== WARPS ====================
    
    public static class WarpsConfig {
        /** Enable/disable warp commands (/warp, /setwarp, /delwarp, /warps) */
        public boolean enabled = true;
        
        /** Cooldown in seconds between /warp uses (0 = no cooldown) */
        public int cooldownSeconds = 0;
        
        /** Warmup in seconds - player must stand still (0 = instant) */
        public int warmupSeconds = 3;
        
        /**
         * Maximum warps that can be created.
         * Set to -1 for unlimited warps.
         * In advanced permissions mode, use eliteessentials.command.warp.limit.<number> to override per-group.
         */
        public int maxWarps = -1;
        
        /**
         * Warp limits per group (for advanced permissions mode).
         * Key = group name (case-insensitive), Value = max warps for that group.
         * Use -1 for unlimited. Players get the highest limit from their groups.
         * Example: {"Admin": -1, "VIP": 10, "Default": 3}
         */
        public Map<String, Integer> groupLimits = createDefaultWarpLimits();
        
        /** Cost to use /warp (0 = free, requires economy enabled) */
        public double cost = 0.0;
        
        private static Map<String, Integer> createDefaultWarpLimits() {
            Map<String, Integer> limits = new HashMap<>();
            limits.put("Admin", -1);      // Unlimited
            limits.put("Owner", -1);      // Unlimited
            limits.put("Moderator", 20);
            limits.put("VIP", 10);
            limits.put("Default", 5);
            return limits;
        }
    }

    // ==================== SLEEP (Night Skip) ====================
    
    public static class SleepConfig {
        /** Enable/disable the sleep percentage feature */
        public boolean enabled = true;
        
        /** Percentage of players that must sleep to skip night (0-100) */
        public int sleepPercentage = 50;
    }

    // ==================== DEATH MESSAGES ====================
    
    public static class DeathMessagesConfig {
        /** Enable/disable death messages in chat */
        public boolean enabled = true;
        
        /** Show killer name when killed by player/mob */
        public boolean showKiller = true;
        
        /** Show death cause (fall, fire, drowning, etc.) */
        public boolean showCause = true;
    }

    // ==================== GOD MODE ====================
    
    public static class GodConfig {
        /** Enable/disable the /god command */
        public boolean enabled = true;
        
        /** Cooldown in seconds between uses (0 = no cooldown) */
        public int cooldownSeconds = 0;
    }

    // ==================== HEAL ====================
    
    public static class HealConfig {
        /** Enable/disable the /heal command */
        public boolean enabled = true;
        
        /** Cooldown in seconds between uses (0 = no cooldown) */
        public int cooldownSeconds = 0;
        
        /** Cost to use this command (0 = free, requires economy enabled) */
        public double cost = 0.0;
    }

    // ==================== PRIVATE MESSAGING ====================
    
    public static class MsgConfig {
        /** Enable/disable /msg, /reply commands */
        public boolean enabled = true;
    }

    // ==================== FLY ====================
    
    public static class FlyConfig {
        /** Enable/disable the /fly command */
        public boolean enabled = true;
        
        /** Cooldown in seconds between uses (0 = no cooldown) */
        public int cooldownSeconds = 0;
    }

    // ==================== VANISH ====================
    
    public static class VanishConfig {
        /** Enable/disable the /vanish command */
        public boolean enabled = true;
        
        /** Hide vanished players from the Server Players list (tab list) */
        public boolean hideFromList = true;
        
        /** Hide vanished players from the world map */
        public boolean hideFromMap = true;
        
        /** Send fake join/leave messages when vanishing/unvanishing */
        public boolean mimicJoinLeave = true;
        
        /** 
         * Persist vanish state across server restarts/reconnects.
         * When true: Players who disconnect while vanished will remain vanished when they rejoin.
         * When false (default): Vanish resets on disconnect.
         */
        public boolean persistOnReconnect = true;
        
        /**
         * Suppress real join/quit messages for vanished players.
         * When true: No join message when a vanished player connects, no quit message when they disconnect.
         * Works with persistOnReconnect to keep vanished players truly hidden.
         */
        public boolean suppressJoinQuitMessages = true;
        
        /**
         * Show a reminder to vanished players when they rejoin.
         * Only applies when persistOnReconnect is true.
         */
        public boolean showReminderOnJoin = true;
    }

    // ==================== GROUP CHAT ====================
    
    public static class GroupChatConfig {
        /** 
         * Enable/disable group chat feature.
         * Requires LuckPerms for group detection.
         */
        public boolean enabled = true;
    }

    // ==================== REPAIR ====================
    
    public static class RepairConfig {
        /** Enable/disable the /repair command */
        public boolean enabled = true;
        
        /** Cooldown in seconds between uses (0 = no cooldown) */
        public int cooldownSeconds = 0;
    }

    // ==================== TOP ====================
    
    public static class TopConfig {
        /** Enable/disable the /top command */
        public boolean enabled = true;
        
        /** Cooldown in seconds between uses (0 = no cooldown) */
        public int cooldownSeconds = 0;
        
        /** Cost to use this command (0 = free, requires economy enabled) */
        public double cost = 0.0;
    }

    // ==================== KITS ====================
    
    public static class KitsConfig {
        /** Enable/disable kit commands */
        public boolean enabled = true;
    }

    // ==================== SPAWN PROTECTION ====================
    
    public static class SpawnProtectionConfig {
        /** 
         * Enable/disable spawn protection.
         * NOTE: You must use /setspawn to set the spawn location before protection will work.
         */
        public boolean enabled = false;
        
        /** Radius in blocks from spawn to protect (square area) */
        public int radius = 50;
        
        /** Minimum Y level to protect (-1 = no limit) */
        public int minY = -1;
        
        /** Maximum Y level to protect (-1 = no limit) */
        public int maxY = -1;
        
        /** Disable PvP in spawn area */
        public boolean disablePvp = true;
        
        /** Disable ALL damage in spawn area (fall damage, fire, drowning, etc.) */
        public boolean disableAllDamage = false;
    }
    
    // ==================== MOTD (Message of the Day) ====================
    
    public static class MotdConfig {
        /** Enable/disable MOTD display on join */
        public boolean enabled = true;
        
        /** Show MOTD automatically when player joins */
        public boolean showOnJoin = true;
        
        /** Delay in seconds before showing MOTD on join (0 = instant) */
        public int delaySeconds = 1;
        
        /** Server name for {server} placeholder */
        public String serverName = "Our Server";
    }
    
    // ==================== RULES ====================
    
    public static class RulesConfig {
        /** Enable/disable the /rules command */
        public boolean enabled = true;
    }
    
    // ==================== JOIN MESSAGES ====================
    
    public static class JoinMsgConfig {
        /** Enable/disable join messages */
        public boolean joinEnabled = true;
        
        /** Enable/disable quit messages */
        public boolean quitEnabled = true;
        
        /** Enable/disable first join message (broadcast to everyone) */
        public boolean firstJoinEnabled = true;
        
        /** 
         * Suppress default Hytale join messages (recommended: true)
         * Prevents the built-in "player has joined default" message
         */
        public boolean suppressDefaultMessages = true;
        
        /**
         * Enable/disable world change messages.
         * When true, broadcasts when players teleport between worlds.
         * Set to false to completely hide world change notifications.
         */
        public boolean worldChangeEnabled = false;
    }
    
    // ==================== BROADCAST ====================
    
    public static class BroadcastConfig {
        /** Enable/disable the /broadcast command */
        public boolean enabled = true;
    }
    
    // ==================== CLEAR INVENTORY ====================
    
    public static class ClearInvConfig {
        /** Enable/disable the /clearinv command */
        public boolean enabled = true;
        
        /** Cooldown in seconds between uses (0 = no cooldown) */
        public int cooldownSeconds = 0;
    }
    
    // ==================== LIST (Online Players) ====================
    
    public static class ListConfig {
        /** Enable/disable the /list command */
        public boolean enabled = true;
        
        /** Maximum players (for display purposes) */
        public int maxPlayers = 100;
    }
    
    // ==================== CHAT FORMAT ====================
    
    public static class ChatFormatConfig {
        /** Enable/disable group-based chat formatting */
        public boolean enabled = true;
        
        /**
         * Allow regular players to use color codes in chat (&c, &#FF0000, etc).
         * When true: Everyone can use colors in chat.
         * When false: Only admins/OPs can use colors (recommended).
         * 
         * In advanced permission mode, players with eliteessentials.chat.color can also use colors.
         */
        public boolean allowPlayerColors = false;
        
        /**
         * Allow regular players to use formatting codes in chat (&l bold, &o italic).
         * When true: Everyone can use formatting in chat.
         * When false: Only admins/OPs can use formatting.
         * 
         * In advanced permission mode, players with eliteessentials.chat.format can also use formatting.
         */
        public boolean allowPlayerFormatting = false;
        
        /** 
         * Chat format per group.
         * Placeholders: {player}, {displayname}, {message}, {group}
         * Color codes: &0-f, &l (bold), &o (italic), &r (reset)
         * 
         * Groups are checked in priority order (highest priority first).
         * Works with both LuckPerms groups and simple permission groups.
         */
        public Map<String, String> groupFormats = createDefaultGroupFormats();
        
        /**
         * Group priority order (highest to lowest).
         * When a player has multiple groups, the highest priority group's format is used.
         */
        public Map<String, Integer> groupPriorities = createDefaultGroupPriorities();
        
        /** Default chat format if no group matches */
        public String defaultFormat = "&7{player}: &f{message}";
        
        private static Map<String, String> createDefaultGroupFormats() {
            Map<String, String> formats = new HashMap<>();
            formats.put("Owner", "&4[Owner] {player}&r: {message}");
            formats.put("Admin", "&c[Admin] {player}&r: {message}");
            formats.put("Moderator", "&9[Mod] {player}&r: {message}");
            formats.put("OP", "&c[OP] {player}&r: {message}");
            formats.put("VIP", "&6[VIP] {player}&r: {message}");
            formats.put("Player", "&a{player}&r: {message}");
            formats.put("Default", "&7{player}&r: {message}");
            return formats;
        }
        
        private static Map<String, Integer> createDefaultGroupPriorities() {
            Map<String, Integer> priorities = new HashMap<>();
            priorities.put("Owner", 100);
            priorities.put("Admin", 90);
            priorities.put("Moderator", 80);
            priorities.put("OP", 75);
            priorities.put("VIP", 50);
            priorities.put("Player", 10);
            priorities.put("Default", 0);
            return priorities;
        }
    }
    
    // ==================== DISCORD ====================
    
    public static class DiscordConfig {
        /** Enable/disable the /discord command */
        public boolean enabled = true;
    }
    
    // ==================== AUTO BROADCAST ====================
    
    public static class AutoBroadcastConfig {
        /** 
         * Enable/disable auto broadcast system.
         * Individual broadcasts can be enabled/disabled in autobroadcast.json
         */
        public boolean enabled = true;
    }
    
    // ==================== COMMAND ALIASES ====================
    
    public static class AliasConfig {
        /** 
         * Enable/disable command alias system.
         * Aliases are stored in aliases.json
         */
        public boolean enabled = true;
    }
    
    // ==================== ECONOMY ====================
    
    public static class EconomyConfig {
        /** 
         * Enable/disable the economy system.
         * When disabled, /pay, /wallet, /baltop commands won't work.
         */
        public boolean enabled = false;
        
        /** Currency name (singular) */
        public String currencyName = "coin";
        
        /** Currency name (plural) */
        public String currencyNamePlural = "coins";
        
        /** Currency symbol for display */
        public String currencySymbol = "$";
        
        /** Starting balance for new players */
        public double startingBalance = 0.0;
        
        /** Minimum amount for /pay command */
        public double minPayment = 1.0;
        
        /** Number of players to show in /baltop */
        public int baltopLimit = 10;
        
        // ==================== VAULTUNLOCKED INTEGRATION ====================
        
        /**
         * Register EliteEssentials as a VaultUnlocked economy provider.
         * When enabled, other plugins can use VaultUnlocked API to interact with our economy.
         * Requires VaultUnlocked plugin to be installed.
         */
        public boolean vaultUnlockedProvider = true;
        
        /**
         * Use an external economy plugin via VaultUnlocked instead of our internal economy.
         * When enabled, /wallet, /pay, /baltop will use the external economy.
         * Requires VaultUnlocked plugin and another economy plugin to be installed.
         * 
         * Note: If both vaultUnlockedProvider and useExternalEconomy are true,
         * useExternalEconomy takes precedence (we consume, not provide).
         */
        public boolean useExternalEconomy = false;
    }
    
    // ==================== MAIL ====================
    
    public static class MailConfig {
        /** Enable/disable the mail system */
        public boolean enabled = true;
        
        /** Maximum mail messages per player mailbox */
        public int maxMailPerPlayer = 50;
        
        /** Maximum message length in characters */
        public int maxMessageLength = 500;
        
        /** 
         * Cooldown in seconds between sending mail to the SAME player.
         * This prevents spam by limiting how often you can mail one person.
         * Set to 0 to disable cooldown.
         */
        public int sendCooldownSeconds = 30;
        
        /** Show notification on login if player has unread mail */
        public boolean notifyOnLogin = true;
        
        /** Delay in seconds before showing mail notification on login */
        public int notifyDelaySeconds = 3;
    }
    
    // ==================== PLAYTIME REWARDS ====================
    
    public PlayTimeRewardsConfig playTimeRewards = new PlayTimeRewardsConfig();
    
    public static class PlayTimeRewardsConfig {
        /**
         * Enable/disable the playtime rewards system.
         * Rewards are configured in playtime_rewards.json
         */
        public boolean enabled = false;
        
        /**
         * How often to check for rewards (in minutes).
         * Lower values = more responsive but more CPU usage.
         * Recommended: 1-5 minutes.
         */
        public int checkIntervalMinutes = 1;
        
        /**
         * Show a message when a player receives a reward.
         */
        public boolean showRewardMessage = true;
        
        /**
         * Broadcast milestone rewards to all players.
         */
        public boolean broadcastMilestones = true;
        
        /**
         * Only count playtime accumulated AFTER this system was first enabled.
         * When true: Players only earn rewards for time played after enabling.
         * When false: Players get catch-up rewards for all historical playtime.
         * 
         * IMPORTANT: Set this to true before enabling rewards on an existing server
         * to prevent players with lots of playtime from getting flooded with rewards.
         */
        public boolean onlyCountNewPlaytime = true;
        
        /**
         * Timestamp (epoch millis) when the reward system was first enabled.
         * This is set automatically when the system starts for the first time.
         * Do not modify manually unless you know what you're doing.
         */
        public long enabledTimestamp = 0;
    }
}
