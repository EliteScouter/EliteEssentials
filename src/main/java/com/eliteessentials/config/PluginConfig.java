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
        messages.put("warpAdminCreateHint", "&7Use &a/setwarp <name> [all|op] &7to create one.");
        messages.put("warpAdminTitle", "&b&l=== &fWarp Admin Panel &b&l===");
        messages.put("warpAdminTotal", "&7Total warps: &a{count}");
        messages.put("warpAdminCommands", "&eCommands:");
        messages.put("warpAdminInfoTitle", "&b&l=== &fWarp: &e{name} &b&l===");
        messages.put("warpAdminPermissionUpdated", "&aWarp &e'{name}' &apermission updated to &7{permission}&a.");
        
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
    }

    // ==================== RTP (Random Teleport) ====================
    
    public static class RtpConfig {
        /** Enable/disable the /rtp command */
        public boolean enabled = true;
        
        /** Minimum distance from player for random location */
        public int minRange = 100;
        
        /** Maximum distance from player for random location */
        public int maxRange = 5000;
        
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
    }

    // ==================== TPA (Teleport Ask) ====================
    
    public static class TpaConfig {
        /** Enable/disable /tpa, /tpaccept, /tpdeny commands */
        public boolean enabled = true;
        
        /** Seconds before a TPA request expires */
        public int timeoutSeconds = 30;
        
        /** Warmup in seconds after accepting - requester must stand still (0 = instant) */
        public int warmupSeconds = 3;
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
    }

    // ==================== HEAL ====================
    
    public static class HealConfig {
        /** Enable/disable the /heal command */
        public boolean enabled = true;
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
    }

    // ==================== TOP ====================
    
    public static class TopConfig {
        /** Enable/disable the /top command */
        public boolean enabled = true;
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
        
        /** Enable/disable first join message (broadcast to everyone) */
        public boolean firstJoinEnabled = true;
        
        /** 
         * Suppress default Hytale join messages (recommended: true)
         * Prevents the built-in "player has joined default" message
         */
        public boolean suppressDefaultMessages = true;
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
}
