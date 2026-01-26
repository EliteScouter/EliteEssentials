package com.eliteessentials.permissions;

/**
 * Permission constants for EliteEssentials.
 * 
 * Permission Structure (hierarchical: namespace.category.action):
 * - eliteessentials.command.home.*     - Home commands + limits + bypass
 * - eliteessentials.command.tp.*       - Teleport commands + bypass
 * - eliteessentials.command.warp.*     - Warp commands + warp access
 * - eliteessentials.command.spawn.*    - Spawn commands
 * - eliteessentials.command.misc.*     - Miscellaneous commands
 * - eliteessentials.admin.*            - Admin permissions
 */
public final class Permissions {

    private Permissions() {} // Utility class

    // ==================== NAMESPACE ====================
    public static final String NAMESPACE = "eliteessentials";
    public static final String COMMAND_BASE = NAMESPACE + ".command";
    public static final String ADMIN_BASE = NAMESPACE + ".admin";

    // ==================== HOME CATEGORY ====================
    // eliteessentials.command.home.*
    public static final String HOME_CATEGORY = COMMAND_BASE + ".home";
    
    // Commands
    public static final String HOME = HOME_CATEGORY + ".home";
    public static final String SETHOME = HOME_CATEGORY + ".sethome";
    public static final String DELHOME = HOME_CATEGORY + ".delhome";
    public static final String HOMES = HOME_CATEGORY + ".homes";
    
    // Home limits: eliteessentials.command.home.limit.<number>
    public static final String HOME_LIMIT_PREFIX = HOME_CATEGORY + ".limit.";
    public static final String HOME_LIMIT_UNLIMITED = HOME_CATEGORY + ".limit.unlimited";
    
    // Home bypass: eliteessentials.command.home.bypass.*
    public static final String HOME_BYPASS = HOME_CATEGORY + ".bypass";
    public static final String HOME_BYPASS_COOLDOWN = HOME_BYPASS + ".cooldown";
    public static final String HOME_BYPASS_WARMUP = HOME_BYPASS + ".warmup";

    // ==================== TELEPORT CATEGORY ====================
    // eliteessentials.command.tp.*
    public static final String TP_CATEGORY = COMMAND_BASE + ".tp";
    
    // Commands
    public static final String TPA = TP_CATEGORY + ".tpa";
    public static final String TPAHERE = TP_CATEGORY + ".tpahere";
    public static final String TPACCEPT = TP_CATEGORY + ".tpaccept";
    public static final String TPDENY = TP_CATEGORY + ".tpdeny";
    public static final String RTP = TP_CATEGORY + ".rtp";
    public static final String BACK = TP_CATEGORY + ".back";
    public static final String BACK_ONDEATH = BACK + ".ondeath";
    public static final String TPHERE = TP_CATEGORY + ".tphere";
    
    // TP bypass: eliteessentials.command.tp.bypass.*
    public static final String TP_BYPASS = TP_CATEGORY + ".bypass";
    public static final String TP_BYPASS_COOLDOWN = TP_BYPASS + ".cooldown";
    public static final String TP_BYPASS_WARMUP = TP_BYPASS + ".warmup";
    // Per-command bypass: eliteessentials.command.tp.bypass.cooldown.<cmd>
    public static final String TP_BYPASS_COOLDOWN_PREFIX = TP_BYPASS_COOLDOWN + ".";
    public static final String TP_BYPASS_WARMUP_PREFIX = TP_BYPASS_WARMUP + ".";

    // ==================== WARP CATEGORY ====================
    // eliteessentials.command.warp.*
    public static final String WARP_CATEGORY = COMMAND_BASE + ".warp";
    
    // Commands
    public static final String WARP = WARP_CATEGORY + ".use";
    public static final String WARPS = WARP_CATEGORY + ".list";
    public static final String SETWARP = WARP_CATEGORY + ".set";
    public static final String DELWARP = WARP_CATEGORY + ".delete";
    public static final String WARPADMIN = WARP_CATEGORY + ".admin";
    
    // Warp access: eliteessentials.command.warp.<warpname>
    // (handled by warpAccess() method)
    
    // Warp limits: eliteessentials.command.warp.limit.<number>
    public static final String WARP_LIMIT_PREFIX = WARP_CATEGORY + ".limit.";
    public static final String WARP_LIMIT_UNLIMITED = WARP_CATEGORY + ".limit.unlimited";
    
    // Warp bypass: eliteessentials.command.warp.bypass.*
    public static final String WARP_BYPASS = WARP_CATEGORY + ".bypass";
    public static final String WARP_BYPASS_COOLDOWN = WARP_BYPASS + ".cooldown";
    public static final String WARP_BYPASS_WARMUP = WARP_BYPASS + ".warmup";

    // ==================== SPAWN CATEGORY ====================
    // eliteessentials.command.spawn.*
    public static final String SPAWN_CATEGORY = COMMAND_BASE + ".spawn";
    
    public static final String SPAWN = SPAWN_CATEGORY + ".use";
    public static final String SETSPAWN = SPAWN_CATEGORY + ".set";
    
    // Spawn bypass
    public static final String SPAWN_BYPASS = SPAWN_CATEGORY + ".bypass";
    public static final String SPAWN_BYPASS_COOLDOWN = SPAWN_BYPASS + ".cooldown";
    public static final String SPAWN_BYPASS_WARMUP = SPAWN_BYPASS + ".warmup";

    // ==================== MISC CATEGORY ====================
    // eliteessentials.command.misc.*
    public static final String MISC_CATEGORY = COMMAND_BASE + ".misc";
    
    public static final String SLEEPPERCENT = MISC_CATEGORY + ".sleeppercent";
    public static final String GOD = MISC_CATEGORY + ".god";
    public static final String HEAL = MISC_CATEGORY + ".heal";
    public static final String HEAL_BYPASS_COOLDOWN = MISC_CATEGORY + ".heal.bypass.cooldown";
    public static final String HEAL_COOLDOWN_PREFIX = MISC_CATEGORY + ".heal.cooldown.";
    public static final String MSG = MISC_CATEGORY + ".msg";
    public static final String FLY = MISC_CATEGORY + ".fly";
    public static final String FLYSPEED = MISC_CATEGORY + ".flyspeed";
    public static final String TOP = TP_CATEGORY + ".top";
    public static final String MOTD = MISC_CATEGORY + ".motd";
    public static final String RULES = MISC_CATEGORY + ".rules";
    public static final String BROADCAST = MISC_CATEGORY + ".broadcast";
    public static final String CLEARINV = MISC_CATEGORY + ".clearinv";
    public static final String LIST = MISC_CATEGORY + ".list";
    public static final String DISCORD = MISC_CATEGORY + ".discord";
    public static final String SEEN = MISC_CATEGORY + ".seen";
    public static final String EEHELP = MISC_CATEGORY + ".eehelp";
    public static final String VANISH = MISC_CATEGORY + ".vanish";
    public static final String REPAIR = MISC_CATEGORY + ".repair";
    public static final String REPAIR_ALL = MISC_CATEGORY + ".repair.all";
    public static final String GROUP_CHAT = MISC_CATEGORY + ".groupchat";

    // ==================== KIT CATEGORY ====================
    // eliteessentials.command.kit.*
    public static final String KIT_CATEGORY = COMMAND_BASE + ".kit";
    
    public static final String KIT = KIT_CATEGORY + ".use";           // Base kit usage (any kit by name)
    public static final String KIT_GUI = KIT_CATEGORY + ".gui";       // Open kit selection GUI
    public static final String KIT_CREATE = KIT_CATEGORY + ".create";
    public static final String KIT_DELETE = KIT_CATEGORY + ".delete";
    public static final String KIT_BYPASS_COOLDOWN = KIT_CATEGORY + ".bypass.cooldown";
    
    // Kit access: eliteessentials.command.kit.<kitname>
    // (handled by kitAccess() method)

    // ==================== SPAWN PROTECTION ====================
    public static final String SPAWN_PROTECTION_BYPASS = SPAWN_CATEGORY + ".protection.bypass";

    // ==================== BYPASS CATEGORY ====================
    // eliteessentials.bypass.*
    public static final String BYPASS_BASE = NAMESPACE + ".bypass";
    
    /** Bypass all command costs */
    public static final String BYPASS_COST = BYPASS_BASE + ".cost";
    
    /** Bypass cost for specific command: eliteessentials.bypass.cost.<command> */
    public static final String BYPASS_COST_PREFIX = BYPASS_COST + ".";

    // ==================== ADMIN PERMISSIONS ====================
    // eliteessentials.admin.*
    public static final String ADMIN = ADMIN_BASE + ".*";
    public static final String ADMIN_RELOAD = ADMIN_BASE + ".reload";
    public static final String ADMIN_ALIAS = ADMIN_BASE + ".alias";
    public static final String ADMIN_SENDMESSAGE = ADMIN_BASE + ".sendmessage";
    public static final String ADMIN_RTP = ADMIN_BASE + ".rtp";

    // ==================== ECONOMY CATEGORY ====================
    // eliteessentials.command.economy.*
    public static final String ECONOMY_CATEGORY = COMMAND_BASE + ".economy";
    
    public static final String WALLET = ECONOMY_CATEGORY + ".wallet";
    public static final String WALLET_OTHERS = ECONOMY_CATEGORY + ".wallet.others";
    public static final String WALLET_ADMIN = ECONOMY_CATEGORY + ".wallet.admin";
    public static final String PAY = ECONOMY_CATEGORY + ".pay";
    public static final String BALTOP = ECONOMY_CATEGORY + ".baltop";

    // ==================== HELPER METHODS ====================
    
    /**
     * Get home limit permission for a number.
     * @param count Number of homes
     * @return eliteessentials.command.home.limit.<count>
     */
    public static String homeLimit(int count) {
        return HOME_LIMIT_PREFIX + count;
    }
    
    /**
     * Get warp limit permission for a number.
     * @param count Number of warps
     * @return eliteessentials.command.warp.limit.<count>
     */
    public static String warpLimit(int count) {
        return WARP_LIMIT_PREFIX + count;
    }

    /**
     * Get permission for accessing a specific warp.
     * @param warpName Warp name
     * @return eliteessentials.command.warp.<warpName>
     */
    public static String warpAccess(String warpName) {
        return WARP_CATEGORY + "." + warpName.toLowerCase();
    }
    
    /**
     * Get permission for accessing a specific kit.
     * @param kitId Kit ID
     * @return eliteessentials.command.kit.<kitId>
     */
    public static String kitAccess(String kitId) {
        return KIT_CATEGORY + "." + kitId.toLowerCase();
    }
    
    /**
     * Get heal cooldown permission for a specific duration.
     * @param seconds Cooldown in seconds
     * @return eliteessentials.command.misc.heal.cooldown.<seconds>
     */
    public static String healCooldown(int seconds) {
        return HEAL_COOLDOWN_PREFIX + seconds;
    }
    
    /**
     * Get bypass cooldown permission for a tp command.
     * @param command Command name (e.g., "rtp", "back", "tpa")
     * @return eliteessentials.command.tp.bypass.cooldown.<command>
     */
    public static String tpBypassCooldown(String command) {
        return TP_BYPASS_COOLDOWN_PREFIX + command;
    }
    
    /**
     * Get bypass warmup permission for a tp command.
     * @param command Command name (e.g., "rtp", "back", "tpa")
     * @return eliteessentials.command.tp.bypass.warmup.<command>
     */
    public static String tpBypassWarmup(String command) {
        return TP_BYPASS_WARMUP_PREFIX + command;
    }

    // ==================== LEGACY COMPATIBILITY ====================
    // Aliases for backward compatibility - these map to the new structure
    
    public static final String HOME_SELF = HOME;
    public static final String SETHOME_SELF = SETHOME;
    public static final String DELHOME_SELF = DELHOME;
    public static final String HOMES_SELF = HOMES;
    
    // Legacy bypass - routes to appropriate category
    public static final String BYPASS_COOLDOWN = TP_BYPASS_COOLDOWN;
    public static final String BYPASS_WARMUP = TP_BYPASS_WARMUP;
    public static final String LIMIT_HOMES_PREFIX = HOME_LIMIT_PREFIX;
    public static final String LIMIT_HOMES_UNLIMITED = HOME_LIMIT_UNLIMITED;
    
    /**
     * Get bypass cooldown permission for a command.
     * Routes to the appropriate category based on command name.
     */
    public static String bypassCooldown(String command) {
        return switch (command) {
            case "home", "sethome", "delhome", "homes" -> HOME_BYPASS_COOLDOWN;
            case "warp", "warps" -> WARP_BYPASS_COOLDOWN;
            case "spawn" -> SPAWN_BYPASS_COOLDOWN;
            case "heal" -> HEAL_BYPASS_COOLDOWN;
            default -> TP_BYPASS_COOLDOWN_PREFIX + command;
        };
    }
    
    /**
     * Get bypass warmup permission for a command.
     * Routes to the appropriate category based on command name.
     */
    public static String bypassWarmup(String command) {
        return switch (command) {
            case "home", "sethome", "delhome", "homes" -> HOME_BYPASS_WARMUP;
            case "warp", "warps" -> WARP_BYPASS_WARMUP;
            case "spawn" -> SPAWN_BYPASS_WARMUP;
            default -> TP_BYPASS_WARMUP_PREFIX + command;
        };
    }
    
    /**
     * Get bypass cost permission for a specific command.
     * @param command Command name (e.g., "home", "rtp", "warp")
     * @return eliteessentials.bypass.cost.<command>
     */
    public static String bypassCost(String command) {
        return BYPASS_COST_PREFIX + command;
    }
}
