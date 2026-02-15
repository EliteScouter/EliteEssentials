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

    // ==================== GUI ====================
    
    /** GUI pagination and entry limits */
    public GuiConfig gui = new GuiConfig();

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
    public ClearChatConfig clearChat = new ClearChatConfig();
    public ClearInvConfig clearInv = new ClearInvConfig();
    public TrashConfig trash = new TrashConfig();
    public ListConfig list = new ListConfig();
    public ChatFormatConfig chatFormat = new ChatFormatConfig();
    public DiscordConfig discord = new DiscordConfig();
    public AutoBroadcastConfig autoBroadcast = new AutoBroadcastConfig();
    public AliasConfig aliases = new AliasConfig();
    public EconomyConfig economy = new EconomyConfig();
    public MailConfig mail = new MailConfig();
    public AfkConfig afk = new AfkConfig();
    public JoindateConfig joindate = new JoindateConfig();
    public PlaytimeConfig playtime = new PlaytimeConfig();
    public IgnoreConfig ignore = new IgnoreConfig();
    public MuteConfig mute = new MuteConfig();
    public BanConfig ban = new BanConfig();
    public FreezeConfig freeze = new FreezeConfig();
    
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
        
        // ==================== GROUP CHAT ====================
        messages.put("groupChatNoAccess", "&cYou don't have access to any chat channels.");
        messages.put("groupChatUsage", "&cUsage: &e/gc <message>");
        messages.put("groupChatUsageGroup", "&cUsage: &e/gc {group} <message>");
        messages.put("groupChatUsageMultiple", "&cUsage: &e/gc [chat] <message> &7- Chats: {groups}");
        messages.put("groupChatSpyEnabled", "&aGroup chat spy &2enabled&a. You will see all group chat messages.");
        messages.put("groupChatSpyDisabled", "&aGroup chat spy &cdisabled&a.");
        messages.put("groupChatDisabled", "&cGroup chat is disabled.");
        messages.put("groupChatDefaultSet", "&aDefault chat set to &e{chat}&a.");
        messages.put("groupChatDefaultCurrent", "&aYour default chat is &e{chat}&a.");
        messages.put("groupChatDefaultNone", "&7No default chat set. Using &e{chat}&7.");
        messages.put("groupChatNotFound", "&cChat &e{chat} &cdoes not exist.");
        messages.put("groupChatNoAccessSpecific", "&cYou don't have access to chat &e{chat}&c.");
        
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
        
        // ==================== CLEAR CHAT ====================
        messages.put("chatCleared", "&aChat has been cleared by an administrator.");
        
        // ==================== CLEAR INVENTORY ====================
        messages.put("clearInvSuccess", "&aCleared &e{count} &aitems from your inventory.");
        messages.put("clearInvFailed", "&cCould not clear inventory.");
        
        // ==================== TRASH ====================
        messages.put("trashOpened", "&aTrash window opened. Items placed here will be deleted when closed.");
        messages.put("trashFailed", "&cCould not open trash window.");
        
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
        messages.put("gui.HomesTitle", "Your Homes ({count}/{max})");
        messages.put("gui.WarpsTitle", "Server Warps");
        messages.put("gui.KitTitle", "Kits");
        messages.put("gui.TpaTitle", "TPA");
        messages.put("gui.TpahereTitle", "TPAHERE");
        messages.put("gui.TpaEmpty", "No other players online.");
        messages.put("gui.TpaRequestButton", "Request");
        messages.put("gui.PaginationPrev", "Prev");
        messages.put("gui.PaginationNext", "Next");
        messages.put("gui.PaginationLabel", "Page {current} / {total}");
        messages.put("gui.WarpButton", "Warp");
        messages.put("gui.WarpDeleteButton", "X");
        messages.put("gui.WarpDeleteConfirmButton", "OK?");
        messages.put("gui.KitClaimButton", "Claim");
        messages.put("gui.HomeEntryEdit", "Edit");
        messages.put("gui.HomeEntryGo", "Go");
        messages.put("gui.HomeEntryWorld", "World: {world} at {coords}");
        messages.put("gui.HomeEditTitle", "Edit Home");
        messages.put("gui.HomeEditNameLabel", "Home Name");
        messages.put("gui.HomeEditNamePlaceholder", "Enter a new name");
        messages.put("gui.HomeEditCancelButton", "Cancel");
        messages.put("gui.HomeEditRenameButton", "Rename");
        messages.put("gui.HomeEditDangerTitle", "Danger Zone");
        messages.put("gui.HomeEditDangerBody", "Do you want to delete this home?");
        messages.put("gui.HomeEditDeleteButton", "Delete");
        messages.put("gui.HomeEditDeleteConfirmButton", "Confirm");
        messages.put("gui.KitStatusLocked", "[Locked]");
        messages.put("gui.KitStatusClaimed", "Claimed");
        messages.put("gui.KitStatusReady", "Ready");
        messages.put("gui.WarpStatusOpOnly", "[OP Only]");
        messages.put("gui.TpaPendingTitle", "Pending Requests");
        messages.put("gui.TpaPendingFrom", "From: {player}");
        messages.put("gui.TpaAcceptButton", "Accept");
        messages.put("gui.TpaDenyButton", "Deny");
        messages.put("tpaUseAcceptCommand", "Type /tpaccept to accept the request from {player}");
        messages.put("tpaUseDenyCommand", "Type /tpdeny to deny the request from {player}");
        
        // ==================== PLAYTIME REWARDS ====================
        messages.put("playTimeRewardReceived", "&a[Reward] &fYou received: &e{reward}");
        messages.put("playTimeMilestoneBroadcast", "&6[Milestone] &f{player} &7reached &e{reward} &7({time} playtime)!");
        
        // ==================== AFK ====================
        messages.put("afkOn", "&7{player} is now AFK.");
        messages.put("afkOff", "&7{player} is no longer AFK.");
        messages.put("afkOnSelf", "&7You are now AFK.");
        messages.put("afkOffSelf", "&aYou are no longer AFK.");
        messages.put("afkPrefix", "[AFK] {player}");
        messages.put("afkListPrefix", "[AFK] {player}");
        
        // ==================== JOINDATE ====================
        messages.put("joindateSelf", "&7You first joined on &e{date}&7.");
        messages.put("joindateOther", "&f{player} &7first joined on &e{date}&7.");
        messages.put("joindateNeverJoined", "&c{player} &7has never joined this server.");
        
        // ==================== PLAYTIME ====================
        messages.put("playtimeSelf", "&7Your total playtime: &e{time}&7.");
        messages.put("playtimeOther", "&f{player}&7's total playtime: &e{time}&7.");
        messages.put("playtimeNeverJoined", "&c{player} &7has never joined this server.");
        
        // ==================== IGNORE ====================
        messages.put("ignoreUsage", "&cUsage: &e/ignore <player> &7or &e/ignore list");
        messages.put("ignoreSelf", "&cYou cannot ignore yourself.");
        messages.put("ignoreAdded", "&aYou are now ignoring &f{player}&a.");
        messages.put("ignoreAlready", "&cYou are already ignoring &f{player}&c.");
        messages.put("ignoreListEmpty", "&7You are not ignoring anyone.");
        messages.put("ignoreListHeader", "&b=== &fIgnored Players &7({count}) &b===");
        messages.put("unignoreUsage", "&cUsage: &e/unignore <player> &7or &e/unignore all");
        messages.put("unignoreRemoved", "&aYou are no longer ignoring &f{player}&a.");
        messages.put("unignoreNotIgnored", "&cYou are not ignoring &f{player}&c.");
        messages.put("unignoreAll", "&aUnignored &e{count} &aplayer(s).");
        
        // ==================== MUTE ====================
        messages.put("muteUsage", "&cUsage: &e/mute <player> [reason]");
        messages.put("muteSelf", "&cYou cannot mute yourself.");
        messages.put("muteSuccess", "&a{player} &ahas been muted.");
        messages.put("muteAlready", "&c{player} &cis already muted.");
        messages.put("mutedNotify", "&cYou have been muted by an administrator.");
        messages.put("mutedNotifyReason", "&cYou have been muted. Reason: &e{reason}");
        messages.put("mutedBlocked", "&cYou are muted and cannot send messages.");
        messages.put("unmuteUsage", "&cUsage: &e/unmute <player>");
        messages.put("unmuteSuccess", "&a{player} &ahas been unmuted.");
        messages.put("unmuteNotMuted", "&c{player} &cis not muted.");
        messages.put("unmutedNotify", "&aYou have been unmuted.");
        
        // ==================== BAN ====================
        messages.put("banUsage", "&cUsage: &e/ban <player> [reason]");
        messages.put("banSelf", "&cYou cannot ban yourself.");
        messages.put("banSuccess", "&a{player} &ahas been permanently banned.");
        messages.put("banAlready", "&c{player} &cis already banned.");
        messages.put("banKick", "&cYou have been banned by {bannedBy}.");
        messages.put("banKickReason", "&cYou have been banned by {bannedBy}. Reason: &e{reason}");
        messages.put("banConnectDenied", "You are permanently banned. Reason: {reason} - Banned by: {bannedBy}");
        messages.put("unbanUsage", "&cUsage: &e/unban <player>");
        messages.put("unbanSuccess", "&a{player} &ahas been unbanned.");
        messages.put("unbanNotBanned", "&c{player} &cis not banned.");
        
        // ==================== TEMPBAN ====================
        messages.put("tempbanUsage", "&cUsage: &e/tempban <player> <time> [reason] &7(e.g. 1d, 2h, 30m)");
        messages.put("tempbanSelf", "&cYou cannot temp ban yourself.");
        messages.put("tempbanInvalidTime", "&cInvalid time format. Use: &e1d, 2h, 30m, 1d12h");
        messages.put("tempbanSuccess", "&a{player} &ahas been temp banned for &e{time}&a.");
        messages.put("tempbanAlready", "&c{player} &cis already temp banned.");
        messages.put("tempbanKick", "&cYou have been temp banned for {time} by {bannedBy}.");
        messages.put("tempbanKickReason", "&cYou have been temp banned for {time} by {bannedBy}. Reason: &e{reason}");
        messages.put("tempbanConnectDenied", "You are temporarily banned. Time remaining: {time} - Reason: {reason} - Banned by: {bannedBy}");
        
        // ==================== IPBAN ====================
        messages.put("ipbanUsage", "&cUsage: &e/ipban <player> [reason]");
        messages.put("ipbanNoIp", "&cCould not determine IP address for &e{player}&c.");
        messages.put("ipbanAlready", "&c{player}'s &cIP is already banned.");
        messages.put("ipbanSuccess", "&a{player}'s &aIP (&e{ip}&a) has been banned.");
        messages.put("ipbanKick", "&cYour IP has been banned by {bannedBy}.");
        messages.put("ipbanKickReason", "&cYour IP has been banned by {bannedBy}. Reason: &e{reason}");
        messages.put("ipbanConnectDenied", "Your IP is banned. Reason: {reason} - Banned by: {bannedBy}");
        messages.put("unipbanUsage", "&cUsage: &e/unipban <ip or player>");
        messages.put("unipbanSuccess", "&aIP &e{ip} &ahas been unbanned.");
        messages.put("unipbanNotBanned", "&cIP &e{ip} &cis not banned.");
        messages.put("unipbanSuccessName", "&a{player}'s &aIP (&e{ip}&a) has been unbanned.");
        messages.put("unipbanNotBannedName", "&cNo IP ban found for &e{player}&c.");
        
        // ==================== FREEZE ====================
        messages.put("freezeUsage", "&cUsage: &e/freeze <player>");
        messages.put("freezeSuccess", "&a{player} &ahas been frozen.");
        messages.put("freezeNotify", "&cYou have been frozen by an administrator. You cannot move.");
        messages.put("freezeError", "&cCould not toggle freeze state.");
        messages.put("unfreezeSuccess", "&a{player} &ahas been unfrozen.");
        messages.put("unfreezeNotify", "&aYou have been unfrozen.");
        messages.put("freezeStillFrozen", "&cYou are still frozen. You cannot move.");
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
         * Example: {"explore": {minRange: 500, maxRange: 10000}, "hub": {minRange: 50, maxRange: 500}}
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
        
        /**
         * Force RTP to always teleport to a specific world, regardless of player's current world.
         * When false (default): RTP teleports within the player's current world.
         * When true: RTP always teleports to the world specified in forceWorld.
         */
        public boolean forceWorldEnabled = false;
        
        /**
         * The world name to force RTP to when forceWorldEnabled is true.
         * Example: "main" or "explore"
         * This is case-sensitive and must match the exact world name.
         */
        public String forceWorld = "";
        
        private static Map<String, WorldRtpRange> createDefaultWorldRanges() {
            Map<String, WorldRtpRange> ranges = new HashMap<>();
            // Example configurations - server owners can customize these
            // ranges.put("explore", new WorldRtpRange(500, 10000));
            // ranges.put("hub", new WorldRtpRange(50, 500));
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

    // ==================== GUI ====================
    
    public static class GuiConfig {
        /** Number of entries shown per page in the TPA/TPAHERE GUI */
        public int playersPerTpaPage = 6;
        
        /** Number of entries shown per page in the Warp GUI */
        public int warpsPerPage = 6;
        
        /** Number of entries shown per page in the Homes GUI */
        public int homesPerPage = 6;
        
        /** Number of entries shown per page in the Kits GUI */
        public int kitsPerPage = 6;
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
        
        /**
         * Teleport ALL players to /setspawn location on every login.
         * When true: Every player is teleported to spawn when they join the server,
         * regardless of which world they logged out in.
         * When false (default): Players spawn where they logged out.
         * Note: Uses mainWorld spawn when perWorld=false, or current world spawn when perWorld=true.
         */
        public boolean teleportOnEveryLogin = false;
        
        /**
         * Delay in seconds before teleporting players to spawn after joining.
         * This delay allows the player to fully load before teleporting.
         * Default is 2 seconds.
         */
        public int teleportDelaySeconds = 2;
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
        
        /** Hour when night starts and players can begin sleeping (0-23.99, default 19.5 = 7:30 PM) */
        public double nightStartHour = 19.5;
        
        /** Hour when morning arrives and players wake up (0-23.99, default 5.5 = 5:30 AM) */
        public double morningHour = 5.5;
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
        
        /**
         * Make vanished players immune to mob damage.
         * When true: Vanished players become invulnerable (like creative mode).
         * This prevents mobs from damaging vanished players.
         * Note: This makes the player invulnerable to ALL damage, not just mob damage.
         */
        public boolean mobImmunity = true;
    }

    // ==================== GROUP CHAT ====================
    
    public static class GroupChatConfig {
        /** 
         * Enable/disable group chat feature.
         * Requires LuckPerms for group detection.
         */
        public boolean enabled = true;
        
        /**
         * Use the same chat formatting (prefixes, colors, LuckPerms placeholders) from chatFormat
         * for player names in group chat messages.
         * 
         * When false (default): Player names appear as plain white text.
         * When true: Player names use the same group-based formatting as regular chat.
         * 
         * The group chat channel prefix (e.g., [ADMIN]) is always shown before the formatted name.
         */
        public boolean useChatFormatting = false;
        
        /**
         * Custom format for group chat messages when useChatFormatting is true.
         * 
         * Placeholders:
         * - {channel_prefix} - The chat channel prefix (e.g., [ADMIN])
         * - {channel_color} - The chat channel color code
         * - {chat_format} - The player's full chat format from chatFormat config (with {message} replaced)
         * - {player} - Player's username
         * - {message} - The chat message
         * 
         * Default puts the channel tag before the player's normal chat format.
         */
        public String formattedMessageFormat = "{channel_color}{channel_prefix} {chat_format}";
        
        /**
         * Allow admins to spy on all group chat channels with /gcspy.
         * Spying admins see messages from channels they don't belong to.
         */
        public boolean allowSpy = true;
        
        /**
         * Format for spy messages shown to admins watching channels they're not in.
         * 
         * Placeholders:
         * - {channel} - Channel name
         * - {player} - Player's username
         * - {message} - The chat message
         */
        public String spyFormat = "&8[GC-SPY] &7[{channel}] {player}: {message}";
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
        
        /** Disable block interactions (chests, doors, buttons, etc.) in spawn area */
        public boolean disableInteractions = false;
        
        /** 
         * Disable item pickups in spawn area.
         * NOTE: May not work properly due to Hytale API limitations.
         */
        public boolean disableItemPickup = false;
        
        /** Disable item drops in spawn area */
        public boolean disableItemDrop = false;
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
    
    // ==================== CLEAR CHAT ====================
    
    public static class ClearChatConfig {
        /** Enable/disable the /clearchat command */
        public boolean enabled = true;
    }
    
    // ==================== CLEAR INVENTORY ====================
    
    public static class ClearInvConfig {
        /** Enable/disable the /clearinv command */
        public boolean enabled = true;
        
        /** Cooldown in seconds between uses (0 = no cooldown) */
        public int cooldownSeconds = 0;
    }
    
    // ==================== TRASH ====================
    
    public static class TrashConfig {
        /** Enable/disable the /trash command */
        public boolean enabled = true;
        
        /** Cooldown in seconds between uses (0 = no cooldown) */
        public int cooldownSeconds = 0;
        
        /** Default number of slots in the trash window */
        public int defaultSize = 27;
        
        /** Maximum number of slots allowed (1-45) */
        public int maxSize = 45;
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
         * 
         * Basic Placeholders:
         * - {player} - Player's username
         * - {displayname} - Player's display name
         * - {message} - The chat message
         * 
         * LuckPerms Placeholders (requires LuckPerms):
         * - {prefix} or %luckperms_prefix% - Player's LuckPerms prefix
         * - {suffix} or %luckperms_suffix% - Player's LuckPerms suffix
         * - {group} or %luckperms_primary_group% - Player's primary group
         * 
         * Color codes: &0-f, &l (bold), &r (reset)
         * Hex colors: &#RRGGBB (e.g., &#FF5555)
         * 
         * Groups are checked in priority order (highest priority first).
         * Works with both LuckPerms groups and simple permission groups.
         * 
         * Example with LuckPerms prefix/suffix:
         * "Admin": "{prefix}&c{player}{suffix}&r: {message}"
         */
        public Map<String, String> groupFormats = createDefaultGroupFormats();
        
        /**
         * Group priority order (highest to lowest).
         * When a player has multiple groups, the highest priority group's format is used.
         */
        public Map<String, Integer> groupPriorities = createDefaultGroupPriorities();
        
        /** Default chat format if no group matches */
        public String defaultFormat = "&7{player}: &f{message}";

        public boolean placeholderapi = true;
        
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
         * When enabled, /wallet and /baltop will use the external economy.
         * 
         * IMPORTANT: When this is true, EliteEssentials will NOT register /eco and /pay commands
         * to avoid conflicts with external economy plugins (like Ecotale) that use the same names.
         * 
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
    
    // ==================== AFK ====================
    
    public static class AfkConfig {
        /** Enable/disable the AFK system */
        public boolean enabled = true;
        
        /**
         * Minutes of inactivity before a player is automatically marked AFK.
         * Set to 0 to disable automatic AFK detection (only /afk command).
         */
        public int inactivityTimeoutMinutes = 5;
        
        /** Broadcast AFK status changes to chat */
        public boolean broadcastAfk = true;
        
        /** Show [AFK] prefix in the tab player list */
        public boolean showInTabList = true;
        
        /**
         * Exclude AFK players from playtime reward checks.
         * When true, AFK players won't accumulate reward time.
         */
        public boolean excludeFromRewards = true;
    }
    
    // ==================== JOINDATE ====================
    
    public static class JoindateConfig {
        /** Enable/disable the /joindate command */
        public boolean enabled = true;
    }
    
    // ==================== PLAYTIME ====================
    
    public static class PlaytimeConfig {
        /** Enable/disable the /playtime command */
        public boolean enabled = true;
    }
    
    // ==================== IGNORE ====================
    
    public static class IgnoreConfig {
        /** Enable/disable the /ignore and /unignore commands */
        public boolean enabled = true;
    }
    
    // ==================== MUTE ====================
    
    public static class MuteConfig {
        /** Enable/disable the /mute and /unmute commands */
        public boolean enabled = true;
    }
    
    // ==================== BAN ====================
    
    public static class BanConfig {
        /** Enable/disable ban commands (/ban, /unban, /tempban, /ipban) */
        public boolean enabled = true;
    }
    
    // ==================== FREEZE ====================
    
    public static class FreezeConfig {
        /** Enable/disable the /freeze command */
        public boolean enabled = true;
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
        
        /**
         * Periodically save online players' play time to disk (in minutes).
         * Protects against play time loss during server crashes.
         * Set to 0 to disable (play time only saves on disconnect).
         * Recommended: 5-10 minutes.
         */
        public int periodicSaveMinutes = 5;
    }
}
