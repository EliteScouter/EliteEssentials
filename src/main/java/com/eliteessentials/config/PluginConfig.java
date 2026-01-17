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
    
    // ==================== MESSAGES ====================
    
    public Map<String, String> messages = new HashMap<>();

    public PluginConfig() {
        initDefaultMessages();
    }
    
    private void initDefaultMessages() {
        // ==================== GENERAL ====================
        messages.put("prefix", "[EliteEssentials] ");
        messages.put("noPermission", "You don't have permission to use this command.");
        messages.put("playerNotFound", "Player not found.");
        messages.put("commandDisabled", "This command is disabled.");
        messages.put("onCooldown", "You must wait {seconds} seconds before using this command again.");
        messages.put("warmupStarted", "Teleporting in {seconds} seconds. Don't move!");
        messages.put("warmupCancelled", "Teleport cancelled - you moved!");
        messages.put("warmupCountdown", "Teleporting in {seconds}...");
        messages.put("teleportInProgress", "You already have a teleport in progress!");
        messages.put("couldNotGetPosition", "Could not get your position.");
        
        // ==================== TPA ====================
        messages.put("tpaRequestSent", "Teleport request sent to {player}.");
        messages.put("tpaRequestReceived", "{player} wants to teleport to you.");
        messages.put("tpaRequestInstructions", "Type /tpaccept to accept or /tpdeny to deny.");
        messages.put("tpaSelfRequest", "You cannot teleport to yourself.");
        messages.put("tpaAlreadyPending", "You already have a pending request to this player.");
        messages.put("tpaRequestFailed", "Could not send teleport request.");
        messages.put("tpaNoPending", "You have no pending teleport requests.");
        messages.put("tpaExpired", "Teleport request has expired.");
        messages.put("tpaPlayerOffline", "{player} is no longer online.");
        messages.put("tpaAccepted", "Teleport request accepted! {player} will teleport to you shortly.");
        messages.put("tpaAcceptedRequester", "{player} accepted your teleport request!");
        messages.put("tpaRequesterWarmup", "Teleporting to {player} in {seconds} seconds... Stand still!");
        messages.put("tpaRequesterInProgress", "The requester already has a teleport in progress.");
        messages.put("tpaDenied", "Teleport request from {player} denied.");
        messages.put("tpaDeniedRequester", "{player} denied your teleport request.");
        messages.put("tpaCouldNotFindRequester", "Could not find requester.");
        messages.put("tpaCouldNotGetRequesterPosition", "Could not get requester's position.");
        
        // ==================== HOMES ====================
        messages.put("homeNoHomes", "You have no homes set. Use /sethome to create one.");
        messages.put("homeListHeader", "Your homes ({count}/{max}):");
        messages.put("homeNotFound", "Home '{name}' not found.");
        messages.put("homeNoHomeSet", "You don't have a home set. Use /sethome first.");
        messages.put("homeTeleported", "Teleported to home '{name}'.");
        messages.put("homeWarmup", "Teleporting to home '{name}' in {seconds} seconds... Stand still!");
        messages.put("homeSet", "Home '{name}' has been set!");
        messages.put("homeLimitReached", "You have reached your home limit ({max}).");
        messages.put("homeInvalidName", "Invalid home name.");
        messages.put("homeSetFailed", "Failed to set home.");
        messages.put("homeDeleted", "Home '{name}' has been deleted.");
        messages.put("homeDeleteFailed", "Failed to delete home.");
        
        // ==================== WARPS ====================
        messages.put("warpNoWarps", "No warps available.");
        messages.put("warpListHeader", "Available warps: ");
        messages.put("warpNotFound", "Warp '{name}' not found. Available: {list}");
        messages.put("warpNoPermission", "You don't have permission to use this warp.");
        messages.put("warpTeleported", "Teleported to warp '{name}'.");
        messages.put("warpWarmup", "Teleporting to warp '{name}' in {seconds} seconds... Stand still!");
        messages.put("warpCreated", "Created warp '{name}' for {permission} at {location}.");
        messages.put("warpUpdated", "Updated warp '{name}' for {permission} at {location}.");
        messages.put("warpInvalidPermission", "Invalid permission '{value}'. Use 'all' or 'op'.");
        messages.put("warpDeleted", "Deleted warp '{name}'.");
        messages.put("warpDeleteFailed", "Failed to delete warp.");
        messages.put("warpListTitle", "=== Server Warps ===");
        messages.put("warpListFooter", "Use /warp <name> to teleport.");
        
        // ==================== WARP ADMIN ====================
        messages.put("warpAdminNoWarps", "No warps configured.");
        messages.put("warpAdminCreateHint", "Use /setwarp <name> [all|op] to create one.");
        messages.put("warpAdminTitle", "=== Warp Admin Panel ===");
        messages.put("warpAdminTotal", "Total warps: {count}");
        messages.put("warpAdminCommands", "Commands:");
        messages.put("warpAdminInfoTitle", "=== Warp: {name} ===");
        messages.put("warpAdminPermissionUpdated", "Warp '{name}' permission updated to {permission}.");
        
        // ==================== BACK ====================
        messages.put("backNoLocation", "No previous location to go back to.");
        messages.put("backTeleported", "Teleported to your previous location.");
        messages.put("backWarmup", "Teleporting back in {seconds} seconds... Stand still!");
        
        // ==================== SPAWN ====================
        messages.put("spawnNoSpawn", "No spawn point configured for this world.");
        messages.put("spawnNoSpawn", "No spawn point set. An admin must use /setspawn first.");
        messages.put("spawnNotFound", "Could not find spawn point.");
        messages.put("spawnTeleported", "Teleported to spawn!");
        messages.put("spawnWarmup", "Teleporting to spawn in {seconds} seconds... Stand still!");
        
        // ==================== RTP ====================
        messages.put("rtpSearching", "Searching for a safe location...");
        messages.put("rtpPreparing", "Preparing random teleport... Stand still for {seconds} seconds!");
        messages.put("rtpTeleported", "Teleported to {location}.");
        messages.put("rtpFailed", "Could not find a safe location after {attempts} attempts. Try again.");
        messages.put("rtpCouldNotDeterminePosition", "Could not determine your position.");
        
        // ==================== SLEEP ====================
        messages.put("sleepProgress", "{sleeping}/{needed} players sleeping...");
        messages.put("sleepSkipping", "{sleeping}/{needed} players sleeping - Skipping to morning!");
        
        // ==================== GOD MODE ====================
        messages.put("godEnabled", "God mode enabled. You are now invincible!");
        messages.put("godDisabled", "God mode disabled.");
        
        // ==================== HEAL ====================
        messages.put("healSuccess", "You have been healed to full health!");
        messages.put("healFailed", "Could not heal you.");
        
        // ==================== PRIVATE MESSAGING ====================
        messages.put("msgUsage", "Usage: /msg <player> <message>");
        messages.put("msgSelf", "You cannot message yourself.");
        messages.put("msgSent", "[To {player}] {message}");
        messages.put("msgReceived", "[From {player}] {message}");
        messages.put("replyNoOne", "You have no one to reply to.");
        messages.put("replyOffline", "That player is no longer online.");
        messages.put("replyUsage", "Usage: /reply <message>");
        
        // ==================== FLY ====================
        messages.put("flyEnabled", "Flight mode enabled! Double-tap jump to fly.");
        messages.put("flyDisabled", "Flight mode disabled.");
        messages.put("flyFailed", "Could not access movement settings.");
        messages.put("flySpeedSet", "Fly speed set to {speed}.");
        messages.put("flySpeedReset", "Fly speed reset to default.");
        messages.put("flySpeedInvalid", "Invalid speed value. Please enter a number.");
        messages.put("flySpeedOutOfRange", "Speed must be between 0 and 10.");
        
        // ==================== TOP ====================
        messages.put("topTeleported", "Teleported to the top!");
        messages.put("topChunkNotLoaded", "Chunk not loaded.");
        messages.put("topNoGround", "No solid ground found above.");
        
        // ==================== KITS ====================
        messages.put("kitNoKits", "No kits are available.");
        messages.put("kitNotFound", "Kit not found.");
        messages.put("kitNoPermission", "You don't have permission to use this kit.");
        messages.put("kitOnCooldown", "This kit is on cooldown. {time} remaining.");
        messages.put("kitAlreadyClaimed", "You have already claimed this one-time kit.");
        messages.put("kitClaimed", "You received the {kit} kit!");
        messages.put("kitClaimFailed", "Could not claim kit.");
        messages.put("kitOpenFailed", "Could not open kit menu.");
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
    }

    // ==================== WARPS ====================
    
    public static class WarpsConfig {
        /** Enable/disable warp commands (/warp, /setwarp, /delwarp, /warps) */
        public boolean enabled = true;
        
        /** Cooldown in seconds between /warp uses (0 = no cooldown) */
        public int cooldownSeconds = 0;
        
        /** Warmup in seconds - player must stand still (0 = instant) */
        public int warmupSeconds = 3;
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
    }
}
