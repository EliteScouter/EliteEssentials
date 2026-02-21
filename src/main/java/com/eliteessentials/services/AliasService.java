package com.eliteessentials.services;

import com.eliteessentials.EliteEssentials;
import com.eliteessentials.commands.hytale.HytaleHomeCommand;
import com.eliteessentials.commands.hytale.HytaleKitCommand;
import com.eliteessentials.commands.hytale.HytaleWarpCommand;
import com.eliteessentials.model.Location;
import com.eliteessentials.storage.AliasStorage;
import com.eliteessentials.storage.AliasStorage.AliasData;
import com.eliteessentials.permissions.PermissionService;
import com.eliteessentials.util.MessageFormatter;

import com.eliteessentials.util.TeleportUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandManager;
import com.hypixel.hytale.server.core.command.system.CommandRegistry;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.Invulnerable;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.io.File;
import java.util.*;
import java.util.logging.Logger;

/**
 * Service for managing command aliases.
 * 
 * Aliases can target ANY command on the server (EliteEssentials or other mods).
 * Commands are dispatched through CommandManager using the PlayerRef overload,
 * so the API resolves the real Player component and permission checks work natively.
 * 
 * Security model (two gates):
 * 1. Alias permission check (everyone/op/custom node) - can the player USE this alias?
 * 2. Target command permission check (via native Player sender) - can the player RUN the target?
 * 
 * EliteEssentials commands that benefit from silent mode and /back saving are handled
 * through optimized paths. All other commands use generic dispatch.
 */
public class AliasService {
    private static final Logger logger = Logger.getLogger("EliteEssentials");
    private final AliasStorage storage;
    private final CommandRegistry commandRegistry;
    private final Map<String, AbstractPlayerCommand> registeredCommands = new HashMap<>();

    /**
     * Set of EE commands that have optimized handling (silent mode, /back saving, etc.).
     * Any command NOT in this set goes through generic dispatch.
     */
    private static final Set<String> OPTIMIZED_COMMANDS = Set.of(
        "warp", "spawn", "home", "homes", "heal", "god", "fly",
        "rules", "motd", "discord", "kit", "back", "top", "list",
        "clearinv", "repair", "vanish"
    );

    public AliasService(File dataFolder, CommandRegistry commandRegistry) {
        this.storage = new AliasStorage(dataFolder);
        this.commandRegistry = commandRegistry;
    }

    public void load() { storage.load(); registerAllAliases(); }
    public void reload() { storage.load(); registerAllAliases(); }

    private void registerAllAliases() {
        int count = 0;
        for (Map.Entry<String, AliasData> entry : storage.getAllAliases().entrySet()) {
            if (!registeredCommands.containsKey(entry.getKey())) {
                try {
                    AliasPlayerCommand cmd = new AliasPlayerCommand(entry.getKey(), entry.getValue());
                    commandRegistry.registerCommand(cmd);
                    registeredCommands.put(entry.getKey(), cmd);
                    count++;
                } catch (Exception e) { logger.warning("Failed to register alias: " + e.getMessage()); }
            }
        }
        if (count > 0) logger.info("Registered " + count + " alias commands");
    }

    public boolean createAlias(String name, String command, String permission) {
        // Auto-generate permission node for custom permissions
        String normalizedPermission = normalizePermission(name, permission);
        boolean isNew = storage.createAlias(name, command, normalizedPermission);
        if (isNew && !registeredCommands.containsKey(name.toLowerCase())) {
            AliasData data = storage.getAlias(name);
            if (data != null) {
                try {
                    AliasPlayerCommand cmd = new AliasPlayerCommand(name.toLowerCase(), data);
                    commandRegistry.registerCommand(cmd);
                    registeredCommands.put(name.toLowerCase(), cmd);
                } catch (Exception e) { logger.warning("Failed to register alias: " + e.getMessage()); }
            }
        }
        return isNew;
    }

    /**
     * Normalize permission string for aliases.
     * - "everyone" -> "everyone" (no change)
     * - "op" -> "op" (no change)
     * - custom permission -> "eliteessentials.command.alias.<name>"
     */
    public static String normalizePermission(String aliasName, String permission) {
        if ("everyone".equalsIgnoreCase(permission) || "op".equalsIgnoreCase(permission)) {
            return permission;
        }
        // Custom permission - auto-generate eliteessentials.command.alias.<name>
        return "eliteessentials.command.alias." + aliasName.toLowerCase();
    }

    public boolean deleteAlias(String name) { return storage.deleteAlias(name); }
    public Map<String, AliasData> getAllAliases() { return storage.getAllAliases(); }
    public boolean hasAlias(String name) { return storage.hasAlias(name); }
    public AliasStorage getStorage() { return storage; }

    /**
     * Check if a command name has an optimized EE handler.
     */
    public static boolean isOptimizedCommand(String commandName) {
        return OPTIMIZED_COMMANDS.contains(commandName.toLowerCase());
    }

    /**
     * Maps known EE command names to their required permission node.
     * Used by alias dispatch to enforce target command permissions (Gate 2)
     * regardless of the alias's own permission setting.
     */
    private static final Map<String, String> COMMAND_PERMISSION_MAP = new HashMap<>();
    private static final Set<String> ADMIN_ONLY_COMMANDS = Set.of(
        "setspawn", "tphere", "warpadmin", "warpsetperm", "warpsetdesc",
        "kitcreate", "kitdelete", "broadcast", "clearchat", "eco",
        "sleeppercent", "eliteessentials", "alias", "mute", "unmute",
        "ban", "unban", "tempban", "ipban", "unipban", "freeze",
        "invsee", "sendmessage", "gcspy", "gcset", "eemigration"
    );

    static {
        // Teleportation
        COMMAND_PERMISSION_MAP.put("home", com.eliteessentials.permissions.Permissions.HOME);
        COMMAND_PERMISSION_MAP.put("sethome", com.eliteessentials.permissions.Permissions.SETHOME);
        COMMAND_PERMISSION_MAP.put("delhome", com.eliteessentials.permissions.Permissions.DELHOME);
        COMMAND_PERMISSION_MAP.put("homes", com.eliteessentials.permissions.Permissions.HOMES);
        COMMAND_PERMISSION_MAP.put("back", com.eliteessentials.permissions.Permissions.BACK);
        COMMAND_PERMISSION_MAP.put("spawn", com.eliteessentials.permissions.Permissions.SPAWN);
        COMMAND_PERMISSION_MAP.put("warp", com.eliteessentials.permissions.Permissions.WARP);
        COMMAND_PERMISSION_MAP.put("rtp", com.eliteessentials.permissions.Permissions.RTP);
        COMMAND_PERMISSION_MAP.put("top", com.eliteessentials.permissions.Permissions.TOP);
        COMMAND_PERMISSION_MAP.put("tpa", com.eliteessentials.permissions.Permissions.TPA);
        COMMAND_PERMISSION_MAP.put("tpahere", com.eliteessentials.permissions.Permissions.TPAHERE);
        COMMAND_PERMISSION_MAP.put("tpaccept", com.eliteessentials.permissions.Permissions.TPACCEPT);
        COMMAND_PERMISSION_MAP.put("tpdeny", com.eliteessentials.permissions.Permissions.TPDENY);
        // Kits & Communication
        COMMAND_PERMISSION_MAP.put("kit", com.eliteessentials.permissions.Permissions.KIT);
        COMMAND_PERMISSION_MAP.put("msg", com.eliteessentials.permissions.Permissions.MSG);
        COMMAND_PERMISSION_MAP.put("reply", com.eliteessentials.permissions.Permissions.MSG);
        COMMAND_PERMISSION_MAP.put("motd", com.eliteessentials.permissions.Permissions.MOTD);
        COMMAND_PERMISSION_MAP.put("rules", com.eliteessentials.permissions.Permissions.RULES);
        COMMAND_PERMISSION_MAP.put("discord", com.eliteessentials.permissions.Permissions.DISCORD);
        COMMAND_PERMISSION_MAP.put("list", com.eliteessentials.permissions.Permissions.LIST);
        COMMAND_PERMISSION_MAP.put("mail", com.eliteessentials.permissions.Permissions.MAIL);
        // Economy
        COMMAND_PERMISSION_MAP.put("wallet", com.eliteessentials.permissions.Permissions.WALLET);
        COMMAND_PERMISSION_MAP.put("pay", com.eliteessentials.permissions.Permissions.PAY);
        COMMAND_PERMISSION_MAP.put("baltop", com.eliteessentials.permissions.Permissions.BALTOP);
        // Player commands
        COMMAND_PERMISSION_MAP.put("fly", com.eliteessentials.permissions.Permissions.FLY);
        COMMAND_PERMISSION_MAP.put("flyspeed", com.eliteessentials.permissions.Permissions.FLYSPEED);
        COMMAND_PERMISSION_MAP.put("god", com.eliteessentials.permissions.Permissions.GOD);
        COMMAND_PERMISSION_MAP.put("heal", com.eliteessentials.permissions.Permissions.HEAL);
        COMMAND_PERMISSION_MAP.put("clearinv", com.eliteessentials.permissions.Permissions.CLEARINV);
        COMMAND_PERMISSION_MAP.put("repair", com.eliteessentials.permissions.Permissions.REPAIR);
        COMMAND_PERMISSION_MAP.put("trash", com.eliteessentials.permissions.Permissions.TRASH);
        COMMAND_PERMISSION_MAP.put("vanish", com.eliteessentials.permissions.Permissions.VANISH);
        COMMAND_PERMISSION_MAP.put("seen", com.eliteessentials.permissions.Permissions.SEEN);
        COMMAND_PERMISSION_MAP.put("joindate", com.eliteessentials.permissions.Permissions.JOINDATE);
        COMMAND_PERMISSION_MAP.put("playtime", com.eliteessentials.permissions.Permissions.PLAYTIME);
        COMMAND_PERMISSION_MAP.put("afk", com.eliteessentials.permissions.Permissions.AFK);
        COMMAND_PERMISSION_MAP.put("ignore", com.eliteessentials.permissions.Permissions.IGNORE);
        COMMAND_PERMISSION_MAP.put("gc", com.eliteessentials.permissions.Permissions.GROUP_CHAT);
        COMMAND_PERMISSION_MAP.put("g", com.eliteessentials.permissions.Permissions.GROUP_CHAT);
        COMMAND_PERMISSION_MAP.put("chats", com.eliteessentials.permissions.Permissions.CHATS_LIST);
        // Admin commands
        COMMAND_PERMISSION_MAP.put("setspawn", com.eliteessentials.permissions.Permissions.SETSPAWN);
        COMMAND_PERMISSION_MAP.put("tphere", com.eliteessentials.permissions.Permissions.TPHERE);
        COMMAND_PERMISSION_MAP.put("warpadmin", com.eliteessentials.permissions.Permissions.WARPADMIN);
        COMMAND_PERMISSION_MAP.put("broadcast", com.eliteessentials.permissions.Permissions.BROADCAST);
        COMMAND_PERMISSION_MAP.put("clearchat", com.eliteessentials.permissions.Permissions.CLEARCHAT);
        COMMAND_PERMISSION_MAP.put("alias", com.eliteessentials.permissions.Permissions.ADMIN_ALIAS);
        COMMAND_PERMISSION_MAP.put("eliteessentials", com.eliteessentials.permissions.Permissions.ADMIN_RELOAD);
        COMMAND_PERMISSION_MAP.put("mute", com.eliteessentials.permissions.Permissions.ADMIN_MUTE);
        COMMAND_PERMISSION_MAP.put("unmute", com.eliteessentials.permissions.Permissions.ADMIN_UNMUTE);
        COMMAND_PERMISSION_MAP.put("ban", com.eliteessentials.permissions.Permissions.ADMIN_BAN);
        COMMAND_PERMISSION_MAP.put("unban", com.eliteessentials.permissions.Permissions.ADMIN_UNBAN);
        COMMAND_PERMISSION_MAP.put("tempban", com.eliteessentials.permissions.Permissions.ADMIN_TEMPBAN);
        COMMAND_PERMISSION_MAP.put("ipban", com.eliteessentials.permissions.Permissions.ADMIN_IPBAN);
        COMMAND_PERMISSION_MAP.put("unipban", com.eliteessentials.permissions.Permissions.ADMIN_UNIPBAN);
        COMMAND_PERMISSION_MAP.put("freeze", com.eliteessentials.permissions.Permissions.ADMIN_FREEZE);
        COMMAND_PERMISSION_MAP.put("invsee", com.eliteessentials.permissions.Permissions.INVSEE);
    }

    /**
     * Gate 2: Check if the player has permission to run the target command.
     * This prevents privilege escalation through aliases - even if the alias
     * permission is "everyone", the player must still have the target command's
     * permission to execute it.
     *
     * For unknown commands (other plugins), returns true and relies on
     * the target command's own permission check via CommandManager dispatch.
     */
    static boolean canRunTargetCommand(UUID playerId, String commandName) {
        String lowerCmd = commandName.toLowerCase();
        PermissionService ps = PermissionService.get();

        // Admin-only commands always require admin
        if (ADMIN_ONLY_COMMANDS.contains(lowerCmd)) {
            return ps.isAdmin(playerId);
        }

        // Known EE commands - check their specific permission
        String permission = COMMAND_PERMISSION_MAP.get(lowerCmd);
        if (permission != null) {
            return ps.canUseEveryoneCommand(playerId, permission, true) || ps.isAdmin(playerId);
        }

        // Unknown command (other plugin) - let CommandManager handle permission
        return true;
    }


    // ==================== ALIAS COMMAND IMPLEMENTATION ====================

    private static class AliasPlayerCommand extends AbstractPlayerCommand {
        private final String aliasName;
        public AliasPlayerCommand(String name, AliasData data) {
            super(name, "Alias: " + data.command);
            this.aliasName = name;
            // Allow extra arguments so players can pass subcommands/args through
            // e.g., /lucky editor -> /lp editor
            setAllowsExtraArguments(true);
        }
        @Override protected boolean canGeneratePermission() { return false; }

        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                              @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef player, @Nonnull World world) {
            AliasData data = EliteEssentials.getInstance().getAliasService().getStorage().getAlias(aliasName);
            if (data == null) { ctx.sendMessage(Message.raw("Alias no longer exists.").color("#FF5555")); return; }

            // Gate 1: Check alias-level permission
            if (!checkPerm(player.getUuid(), data.permission)) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(
                    EliteEssentials.getInstance().getConfigManager().getMessage("noPermission"), "#FF5555"));
                return;
            }

            boolean debugEnabled = EliteEssentials.getInstance().getConfigManager().isDebugEnabled();
            boolean backSaved = false;
            boolean silent = data.silent;

            // Extract any extra arguments the player typed after the alias name
            // e.g., "/lucky editor" -> extraArgs = "editor"
            String rawInput = ctx.getInputString();
            String extraArgs = "";
            int spaceIdx = rawInput.indexOf(' ');
            if (spaceIdx >= 0) {
                extraArgs = rawInput.substring(spaceIdx + 1).trim();
            }

            // Support semicolon-separated command chains
            for (String cmd : data.command.split(";")) {
                cmd = cmd.trim();
                if (cmd.isEmpty()) continue;
                if (cmd.startsWith("/")) cmd = cmd.substring(1);

                // Append any extra arguments the player typed after the alias
                if (!extraArgs.isEmpty()) {
                    cmd = cmd + " " + extraArgs;
                }

                String[] parts = cmd.split(" ", 2);
                String commandName = parts[0].toLowerCase();
                String args = parts.length > 1 ? parts[1].trim() : "";

                // Gate 2: Check target command permission to prevent privilege escalation.
                // Even if the alias permission is "everyone", the player must have
                // permission to run the actual target command.
                if (!canRunTargetCommand(player.getUuid(), commandName)) {
                    if (debugEnabled) {
                        logger.info("[Alias] Gate 2 blocked /" + commandName + " for " + player.getUsername() 
                            + " - missing target command permission");
                    }
                    ctx.sendMessage(MessageFormatter.formatWithFallback(
                        EliteEssentials.getInstance().getConfigManager().getMessage("noPermission"), "#FF5555"));
                    return;
                }

                // Save /back location before first teleport command
                if (!backSaved && (commandName.equals("warp") || commandName.equals("spawn") || commandName.equals("home"))) {
                    saveBack(store, ref, player, world);
                    backSaved = true;
                }

                if (isOptimizedCommand(commandName)) {
                    // Optimized path: EE commands with silent/back support
                    if (debugEnabled) {
                        logger.info("[Alias] Optimized dispatch: /" + commandName + " " + args);
                    }
                    runOptimizedCmd(ctx, store, ref, player, world, commandName, args, silent);
                } else {
                    // Generic dispatch: any command from any mod, runs as the player
                    if (debugEnabled) {
                        logger.info("[Alias] Generic dispatch as player: /" + commandName + " " + args);
                    }
                    dispatchAsPlayer(ctx, player, world, cmd, debugEnabled);
                }
            }
        }

        /**
         * Generic command dispatch - runs the command as the player via CommandManager.
         * Uses the PlayerRef overload so the API resolves the actual Player component,
         * which is required by any command extending AbstractPlayerCommand.
         */
        private void dispatchAsPlayer(CommandContext ctx, PlayerRef player, World world, 
                                       String fullCommand, boolean debugEnabled) {
            try {
                world.execute(() -> {
                    try {
                        CommandManager cm = CommandManager.get();
                        // Use the PlayerRef overload - it resolves the Player component
                        // from the store, so target commands see a real Player sender
                        // instead of our PlayerCommandSender wrapper.
                        cm.handleCommand(player, fullCommand);
                        
                        if (debugEnabled) {
                            logger.info("[Alias] Successfully dispatched: /" + fullCommand + " for " + player.getUsername());
                        }
                    } catch (Exception e) {
                        logger.warning("[Alias] Failed to dispatch command '/" + fullCommand + "' for " 
                            + player.getUsername() + ": " + e.getMessage());
                        if (player.isValid()) {
                            player.sendMessage(Message.raw("Alias error: Could not execute /" + fullCommand).color("#FF5555"));
                        }
                    }
                });
            } catch (Exception e) {
                logger.warning("[Alias] Failed to process command '/" + fullCommand + "': " + e.getMessage());
                ctx.sendMessage(Message.raw("Alias error: Could not execute command.").color("#FF5555"));
            }
        }

        // ==================== OPTIMIZED EE COMMAND HANDLERS ====================
        // These provide silent mode, /back saving, and direct service access.
        // Existing aliases targeting these commands continue to work exactly as before.

        private void runOptimizedCmd(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref, 
                                      PlayerRef player, World world, String cn, String args, boolean silent) {
            try {
                switch (cn) {
                    case "warp": doWarp(ctx, store, ref, player, world, args, silent); break;
                    case "spawn": doSpawn(ctx, store, ref, player, world, silent); break;
                    case "home": doHome(ctx, store, ref, player, world, args, silent); break;
                    case "homes": doHomes(ctx, store, ref, player, world); break;
                    case "heal": doHeal(ctx, store, ref, player, silent); break;
                    case "god": doGod(ctx, store, ref, player, silent); break;
                    case "fly": doFly(ctx, store, ref, player, silent); break;
                    case "rules": doRules(player); break;
                    case "motd": doMotd(player, world); break;
                    case "discord": doDiscord(player); break;
                    case "kit": doKit(ctx, store, ref, player, world, args); break;
                    case "back": doBack(ctx, store, ref, player, world, silent); break;
                    case "top": doTop(ctx, store, ref, player, world, silent); break;
                    case "list": doList(ctx, player); break;
                    case "clearinv": doClearInv(ctx, store, ref, player, silent); break;
                    case "repair": doRepair(ctx, store, ref, player, silent); break;
                    case "vanish": doVanish(ctx, store, ref, player, silent); break;
                    default: break; // Should never hit - isOptimizedCommand guards this
                }
            } catch (Exception e) { logger.warning("[Alias] " + cn + ": " + e.getMessage()); }
        }

        private void doWarp(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef player, World world, String n, boolean silent) {
            if (n.isEmpty()) return;
            WarpService warpService = EliteEssentials.getInstance().getWarpService();
            BackService backService = EliteEssentials.getInstance().getBackService();
            HytaleWarpCommand.goToWarp(ctx, store, ref, player, world, n, warpService, backService, silent);
        }

        private void doSpawn(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef player, World world, boolean silent) {
            var backService = EliteEssentials.getInstance().getBackService();
            var cooldownService = EliteEssentials.getInstance().getCooldownService();
            var warmupService = EliteEssentials.getInstance().getWarmupService();
            var configManager = EliteEssentials.getInstance().getConfigManager();
            var config = configManager.getConfig();
            var spawnStorage = EliteEssentials.getInstance().getSpawnStorage();
            UUID playerId = player.getUuid();
            
            if (!PermissionService.get().canUseEveryoneCommand(playerId, com.eliteessentials.permissions.Permissions.SPAWN, config.spawn.enabled)) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("noPermission"), "#FF5555"));
                return;
            }
            
            if (!com.eliteessentials.util.CommandPermissionUtil.canBypassCooldown(playerId, "spawn")) {
                int cooldownRemaining = cooldownService.getCooldownRemaining("spawn", playerId);
                if (cooldownRemaining > 0) {
                    ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("onCooldown", "seconds", String.valueOf(cooldownRemaining)), "#FF5555"));
                    return;
                }
            }
            
            if (warmupService.hasActiveWarmup(playerId)) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("teleportInProgress"), "#FF5555"));
                return;
            }
            
            String targetWorldName = config.spawn.perWorld ? world.getName() : config.spawn.mainWorld;
            com.eliteessentials.storage.SpawnStorage.SpawnData s = spawnStorage.getSpawn(targetWorldName);
            if (s == null) { 
                ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("spawnNoSpawn"), "#FF5555")); 
                return; 
            }
            
            TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
            if (transform == null) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("couldNotGetPosition"), "#FF5555"));
                return;
            }
            
            Vector3d currentPos = transform.getPosition();
            HeadRotation headRotation = store.getComponent(ref, HeadRotation.getComponentType());
            Vector3f rotation = headRotation != null ? headRotation.getRotation() : new Vector3f(0, 0, 0);
            
            Location currentLoc = new Location(
                world.getName(),
                currentPos.getX(), currentPos.getY(), currentPos.getZ(),
                rotation.y, rotation.x
            );
            
            World targetWorld = Universe.get().getWorld(targetWorldName);
            if (targetWorld == null) targetWorld = world;
            final World finalTargetWorld = targetWorld;
            final boolean finalSilent = silent;
            
            Vector3d spawnPos = new Vector3d(s.x, s.y, s.z);
            Vector3f spawnRot = new Vector3f(0, s.yaw, 0);
            
            Runnable doTeleport = () -> {
                backService.pushLocation(playerId, currentLoc);
                TeleportUtil.safeTeleport(world, finalTargetWorld, spawnPos, spawnRot, player,
                    () -> {
                        if (!finalSilent) {
                            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("spawnTeleported"), "#55FF55"));
                        }
                    },
                    () -> {
                        ctx.sendMessage(MessageFormatter.formatWithFallback("&cTeleport failed - destination chunk could not be loaded.", "#FF5555"));
                    }
                );
                cooldownService.setCooldown("spawn", playerId, config.spawn.cooldownSeconds);
            };
            
            int warmupSeconds = com.eliteessentials.util.CommandPermissionUtil.getEffectiveWarmup(playerId, "spawn", config.spawn.warmupSeconds);
            if (warmupSeconds > 0 && !silent) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("spawnWarmup", "seconds", String.valueOf(warmupSeconds)), "#FFAA00"));
            }
            warmupService.startWarmup(player, currentPos, warmupSeconds, doTeleport, "spawn", world, store, ref, false);
        }

        private void doHome(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef player, World world, String n, boolean silent) {
            if (n.isEmpty()) n = "home";
            var homeService = EliteEssentials.getInstance().getHomeService();
            var backService = EliteEssentials.getInstance().getBackService();
            HytaleHomeCommand.goHome(ctx, store, ref, player, world, n, homeService, backService, silent);
        }

        private void doHeal(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef player, boolean silent) {
            var configManager = EliteEssentials.getInstance().getConfigManager();
            var config = configManager.getConfig();
            UUID playerId = player.getUuid();
            
            if (!PermissionService.get().canUseEveryoneCommand(playerId, com.eliteessentials.permissions.Permissions.HEAL, config.heal.enabled)) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("noPermission"), "#FF5555"));
                return;
            }
            
            EntityStatMap m = store.getComponent(ref, EntityStatMap.getComponentType());
            if (m != null) { 
                m.maximizeStatValue(DefaultEntityStatTypes.getHealth()); 
                if (!silent) {
                    ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("healSuccess"), "#55FF55")); 
                }
            }
        }

        private void doGod(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef player, boolean silent) {
            var configManager = EliteEssentials.getInstance().getConfigManager();
            var config = configManager.getConfig();
            UUID playerId = player.getUuid();
            
            if (!PermissionService.get().canUseEveryoneCommand(playerId, com.eliteessentials.permissions.Permissions.GOD, config.god.enabled)) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("noPermission"), "#FF5555"));
                return;
            }
            
            GodService gs = EliteEssentials.getInstance().getGodService();
            boolean on = gs.toggleGodMode(playerId);
            if (on) { 
                store.putComponent(ref, Invulnerable.getComponentType(), Invulnerable.INSTANCE); 
                if (!silent) {
                    ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("godEnabled"), "#55FF55")); 
                }
            } else { 
                store.removeComponent(ref, Invulnerable.getComponentType()); 
                if (!silent) {
                    ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("godDisabled"), "#FF5555")); 
                }
            }
        }

        private void doFly(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef player, boolean silent) {
            var configManager = EliteEssentials.getInstance().getConfigManager();
            var config = configManager.getConfig();
            UUID playerId = player.getUuid();
            
            if (!PermissionService.get().canUseEveryoneCommand(playerId, com.eliteessentials.permissions.Permissions.FLY, config.fly.enabled)) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("noPermission"), "#FF5555"));
                return;
            }
            
            MovementManager mm = store.getComponent(ref, MovementManager.getComponentType());
            if (mm == null) return;
            var s = mm.getSettings(); s.canFly = !s.canFly; mm.update(player.getPacketHandler());
            if (!silent) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage(s.canFly ? "flyEnabled" : "flyDisabled"), s.canFly ? "#55FF55" : "#FF5555"));
            }
        }

        private void doRules(PlayerRef player) {
            var rulesStorage = EliteEssentials.getInstance().getRulesStorage();
            var lines = rulesStorage.getRulesLines();
            if (lines.isEmpty()) {
                player.sendMessage(MessageFormatter.formatWithFallback(EliteEssentials.getInstance().getConfigManager().getMessage("rulesEmpty"), "#FF5555"));
                return;
            }
            for (String line : lines) {
                if (!line.trim().isEmpty()) player.sendMessage(MessageFormatter.format(line));
            }
        }

        private void doMotd(PlayerRef player, World world) {
            var motdStorage = EliteEssentials.getInstance().getMotdStorage();
            var config = EliteEssentials.getInstance().getConfigManager().getConfig();
            var lines = motdStorage.getMotdLines();
            if (lines.isEmpty()) {
                player.sendMessage(MessageFormatter.formatWithFallback(EliteEssentials.getInstance().getConfigManager().getMessage("motdEmpty"), "#FF5555"));
                return;
            }
            int playerCount = Universe.get().getPlayers().size();
            for (String line : lines) {
                if (!line.trim().isEmpty()) {
                    String processed = line.replace("{player}", player.getUsername())
                            .replace("{server}", config.motd.serverName)
                            .replace("{world}", world.getName())
                            .replace("{playercount}", String.valueOf(playerCount));
                    player.sendMessage(MessageFormatter.format(processed));
                }
            }
        }

        private void doDiscord(PlayerRef player) {
            var discordStorage = EliteEssentials.getInstance().getDiscordStorage();
            var lines = discordStorage.getDiscordLines();
            if (lines.isEmpty()) {
                player.sendMessage(MessageFormatter.formatWithFallback(EliteEssentials.getInstance().getConfigManager().getMessage("discordEmpty"), "#FF5555"));
                return;
            }
            for (String line : lines) {
                if (!line.trim().isEmpty()) player.sendMessage(MessageFormatter.format(line));
            }
        }

        private void doHomes(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef player, World world) {
            var configManager = EliteEssentials.getInstance().getConfigManager();
            var config = configManager.getConfig();
            UUID playerId = player.getUuid();
            
            if (!PermissionService.get().canUseEveryoneCommand(playerId, com.eliteessentials.permissions.Permissions.HOMES, config.homes.enabled)) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("noPermission"), "#FF5555"));
                return;
            }
            
            var homeService = EliteEssentials.getInstance().getHomeService();
            var homes = homeService.getHomes(playerId);
            if (homes.isEmpty()) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("homeNoHomes"), "#FFAA00"));
                return;
            }
            
            Player playerEntity = store.getComponent(ref, Player.getComponentType());
            if (playerEntity == null) {
                ctx.sendMessage(MessageFormatter.formatWithFallback("&cCould not open homes menu.", "#FF5555"));
                return;
            }
            
            var backService = EliteEssentials.getInstance().getBackService();
            com.eliteessentials.gui.HomeSelectionPage page = new com.eliteessentials.gui.HomeSelectionPage(player, homeService, backService, configManager, world);
            playerEntity.getPageManager().openCustomPage(ref, store, page);
        }

        private void doKit(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef player, World world, String args) {
            var configManager = EliteEssentials.getInstance().getConfigManager();
            var config = configManager.getConfig();
            UUID playerId = player.getUuid();
            
            if (!PermissionService.get().canUseEveryoneCommand(playerId, com.eliteessentials.permissions.Permissions.KIT, config.kits.enabled)) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("noPermission"), "#FF5555"));
                return;
            }
            
            if (args.isEmpty()) {
                ctx.sendMessage(Message.raw("Usage: kit <name>").color("#FFAA00"));
                return;
            }
            
            var kitService = EliteEssentials.getInstance().getKitService();
            HytaleKitCommand.claimKit(ctx, store, ref, player, args, kitService, configManager);
        }

        private void doBack(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef player, World world, boolean silent) {
            var configManager = EliteEssentials.getInstance().getConfigManager();
            var config = configManager.getConfig();
            UUID playerId = player.getUuid();
            
            if (!PermissionService.get().canUseEveryoneCommand(playerId, com.eliteessentials.permissions.Permissions.BACK, config.back.enabled)) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("noPermission"), "#FF5555"));
                return;
            }
            
            var backService = EliteEssentials.getInstance().getBackService();
            Optional<Location> locOpt = backService.popLocation(playerId);
            if (locOpt.isEmpty()) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("backNoLocation"), "#FF5555"));
                return;
            }
            
            Location loc = locOpt.get();
            World targetWorld = Universe.get().getWorld(loc.getWorld());
            if (targetWorld == null) targetWorld = world;
            final World finalWorld = targetWorld;
            
            Vector3d pos = new Vector3d(loc.getX(), loc.getY(), loc.getZ());
            Vector3f rot = new Vector3f(0, loc.getYaw(), 0);
            TeleportUtil.safeTeleport(world, finalWorld, pos, rot, player,
                () -> {
                    if (!silent) {
                        ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("backTeleported"), "#55FF55"));
                    }
                },
                () -> {
                    ctx.sendMessage(MessageFormatter.formatWithFallback("&cTeleport failed - destination chunk could not be loaded.", "#FF5555"));
                }
            );
        }

        private void doTop(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef player, World world, boolean silent) {
            var configManager = EliteEssentials.getInstance().getConfigManager();
            var config = configManager.getConfig();
            UUID playerId = player.getUuid();
            
            if (!PermissionService.get().canUseEveryoneCommand(playerId, com.eliteessentials.permissions.Permissions.TOP, config.top.enabled)) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("noPermission"), "#FF5555"));
                return;
            }
            
            TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
            if (transform == null) return;
            
            Vector3d pos = transform.getPosition();
            int blockX = (int) Math.floor(pos.getX());
            int blockZ = (int) Math.floor(pos.getZ());
            
            long chunkIndex = ChunkUtil.indexChunkFromBlock(blockX, blockZ);
            WorldChunk chunk = world.getChunk(chunkIndex);
            if (chunk == null) {
                ctx.sendMessage(Message.raw("Chunk not loaded.").color("#FF5555"));
                return;
            }
            
            int topY = -1;
            for (int y = 255; y >= 0; y--) {
                BlockType blockType = chunk.getBlockType(blockX, y, blockZ);
                if (blockType != null && blockType.getMaterial() == BlockMaterial.Solid) {
                    topY = y + 1;
                    break;
                }
            }
            if (topY < 0) {
                ctx.sendMessage(Message.raw("No solid ground found.").color("#FF5555"));
                return;
            }
            final int finalY = topY;
            Vector3d newPos = new Vector3d(pos.getX(), finalY, pos.getZ());
            HeadRotation hr = store.getComponent(ref, HeadRotation.getComponentType());
            Vector3f rot = hr != null ? new Vector3f(0, hr.getRotation().y, 0) : new Vector3f(0, 0, 0);
            TeleportUtil.safeTeleport(world, world, newPos, rot, player,
                () -> {
                    if (!silent) {
                        ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("topTeleported"), "#55FF55"));
                    }
                },
                () -> {
                    ctx.sendMessage(MessageFormatter.formatWithFallback("&cTeleport failed - chunk could not be loaded.", "#FF5555"));
                }
            );
        }

        private void doList(CommandContext ctx, PlayerRef player) {
            var configManager = EliteEssentials.getInstance().getConfigManager();
            var config = configManager.getConfig();
            UUID playerId = player.getUuid();
            
            if (!PermissionService.get().canUseEveryoneCommand(playerId, com.eliteessentials.permissions.Permissions.LIST, config.list.enabled)) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("noPermission"), "#FF5555"));
                return;
            }
            
            var players = Universe.get().getPlayers();
            if (players.isEmpty()) {
                ctx.sendMessage(Message.raw("No players online.").color("#FFAA00"));
                return;
            }
            StringBuilder sb = new StringBuilder();
            for (var p : players) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(p.getUsername());
            }
            ctx.sendMessage(Message.raw("Online (" + players.size() + "): " + sb.toString()).color("#55FF55"));
        }

        private void doClearInv(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef player, boolean silent) {
            var configManager = EliteEssentials.getInstance().getConfigManager();
            var config = configManager.getConfig();
            UUID playerId = player.getUuid();
            
            if (!PermissionService.get().canUseEveryoneCommand(playerId, com.eliteessentials.permissions.Permissions.CLEARINV, config.clearInv.enabled)) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("noPermission"), "#FF5555"));
                return;
            }
            
            Player playerComp = store.getComponent(ref, Player.getComponentType());
            if (playerComp == null) return;
            var inv = playerComp.getInventory();
            inv.getHotbar().clear();
            inv.getStorage().clear();
            if (!silent) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("clearInvSuccess"), "#55FF55"));
            }
        }

        private void doRepair(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef player, boolean silent) {
            var configManager = EliteEssentials.getInstance().getConfigManager();
            var config = configManager.getConfig();
            UUID playerId = player.getUuid();
            
            if (!PermissionService.get().canUseEveryoneCommand(playerId, com.eliteessentials.permissions.Permissions.REPAIR, config.repair.enabled)) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("noPermission"), "#FF5555"));
                return;
            }
            
            Player playerComp = store.getComponent(ref, Player.getComponentType());
            if (playerComp == null) return;
            var inventory = playerComp.getInventory();
            var hotbar = inventory.getHotbar();
            short slot = (short) inventory.getActiveHotbarSlot();
            var item = hotbar.getItemStack(slot);
            if (item == null || com.hypixel.hytale.server.core.inventory.ItemStack.isEmpty(item)) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("repairNoItem"), "#FF5555"));
                return;
            }
            if (item.getDurability() < item.getMaxDurability()) {
                var repairedItem = item.withDurability(item.getMaxDurability());
                hotbar.replaceItemStackInSlot(slot, item, repairedItem);
            }
            if (!silent) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("repairSuccess"), "#55FF55"));
            }
        }

        private void doVanish(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef player, boolean silent) {
            var configManager = EliteEssentials.getInstance().getConfigManager();
            var config = configManager.getConfig();
            UUID playerId = player.getUuid();
            
            if (!PermissionService.get().canUseEveryoneCommand(playerId, com.eliteessentials.permissions.Permissions.VANISH, config.vanish.enabled)) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("noPermission"), "#FF5555"));
                return;
            }
            
            var vanishService = EliteEssentials.getInstance().getVanishService();
            boolean vanished = vanishService.toggleVanish(playerId, player.getUsername());
            if (!silent) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage(vanished ? "vanishEnabled" : "vanishDisabled"), vanished ? "#55FF55" : "#FF5555"));
            }
        }

        // ==================== HELPERS ====================

        private void saveBack(Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef player, World world) {
            try {
                TransformComponent t = store.getComponent(ref, TransformComponent.getComponentType());
                if (t != null) {
                    Vector3d p = t.getPosition();
                    HeadRotation hr = store.getComponent(ref, HeadRotation.getComponentType());
                    float y = hr != null ? hr.getRotation().y : 0;
                    EliteEssentials.getInstance().getBackService().pushLocation(player.getUuid(), 
                        new Location(world.getName(), p.getX(), p.getY(), p.getZ(), y, 0));
                }
            } catch (Exception e) {
                logger.warning("[Alias] Failed to save back location: " + e.getMessage());
            }
        }

        /**
         * Check alias-level permission (Gate 1).
         * "everyone" = anyone, "op" = admins only, custom = permission node check.
         */
        private boolean checkPerm(UUID id, String perm) {
            PermissionService ps = PermissionService.get();
            if ("everyone".equalsIgnoreCase(perm)) return true;
            if ("op".equalsIgnoreCase(perm)) return ps.isAdmin(id);
            return EliteEssentials.getInstance().getConfigManager().isAdvancedPermissions() 
                ? ps.hasPermission(id, perm) : ps.isAdmin(id);
        }
    }
}
