package com.eliteessentials.commands.hytale;

import com.eliteessentials.EliteEssentials;
import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.config.PluginConfig;
import com.eliteessentials.gui.WarpSelectionPage;
import com.eliteessentials.model.Location;
import com.eliteessentials.model.Warp;
import com.eliteessentials.permissions.PermissionService;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.services.BackService;
import com.eliteessentials.services.CooldownService;
import com.eliteessentials.services.CostService;
import com.eliteessentials.services.WarpService;
import com.eliteessentials.services.WarmupService;
import com.eliteessentials.util.CommandPermissionUtil;
import com.eliteessentials.util.MessageFormatter;
import com.eliteessentials.util.PlayerSuggestionProvider;
import com.eliteessentials.util.TeleportUtil;
import com.eliteessentials.util.WorldBlacklistUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import org.joml.Vector3d;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import com.eliteessentials.util.CommandSpyUtil;

/**
 * Command: /warp [name|list] [player]
 * - /warp                  - Opens GUI (player)
 * - /warp list             - Text list of warps (player)
 * - /warp <name>           - Teleport self to warp (player)
 * - /warp <name> <player>  - Teleport another player to a warp (admin / console)
 *
 * Extends {@link CommandBase} (not AbstractPlayerCommand) so the server console
 * can send a player to a warp for automation, matching the /rtp and /spawn pattern.
 */
public class HytaleWarpCommand extends CommandBase {

    private static final String COMMAND_NAME = "warp";
    
    private final WarpService warpService;
    private final BackService backService;

    public HytaleWarpCommand(WarpService warpService, BackService backService) {
        super(COMMAND_NAME, "Teleport to a warp location");
        this.warpService = warpService;
        this.backService = backService;
        
        addAliases("warps");
        setAllowsExtraArguments(true);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void executeSync(@Nonnull CommandContext ctx) {
        CommandSpyUtil.notify(ctx);
        ConfigManager configManager = EliteEssentials.getInstance().getConfigManager();

        // Parse: /warp [name|list] [player]
        String[] parts = ctx.getInputString().split("\\s+");
        String arg1 = parts.length >= 2 ? parts[1] : null;
        String arg2 = parts.length >= 3 ? parts[2] : null;

        Object sender = ctx.sender();
        boolean isConsole = !(sender instanceof PlayerRef) && !(sender instanceof Player);

        // --- Console path: /warp <name> <player> ---
        if (isConsole) {
            if (arg1 == null || arg2 == null) {
                ctx.sendMessage(Message.raw("Console usage: /warp <warpname> <player>").color("#FF5555"));
                return;
            }
            PlayerRef target = PlayerSuggestionProvider.findPlayer(arg2);
            if (target == null) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("playerNotFound", "player", arg2), "#FF5555"));
                return;
            }
            adminTeleportToWarp(ctx, target, arg1, warpService, backService);
            return;
        }

        // --- Player path ---
        PlayerRef senderRef = resolveSenderPlayer(sender);
        if (senderRef == null) {
            ctx.sendMessage(Message.raw("Could not determine player.").color("#FF5555"));
            return;
        }

        // /warp <name> <player> from an in-game admin: teleport the other player
        if (arg1 != null && arg2 != null) {
            if (!PermissionService.get().canUseAdminCommand(senderRef.getUuid(), Permissions.WARPS, true)) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("noPermission"), "#FF5555"));
                return;
            }
            PlayerRef target = PlayerSuggestionProvider.findPlayer(arg2);
            if (target == null) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("playerNotFound", "player", arg2), "#FF5555"));
                return;
            }
            adminTeleportToWarp(ctx, target, arg1, warpService, backService);
            return;
        }

        World world = Universe.get().getWorld(senderRef.getWorldUuid());
        if (world == null) {
            ctx.sendMessage(Message.raw("Could not determine your world.").color("#FF5555"));
            return;
        }

        final String input = arg1;
        final PlayerRef finalSender = senderRef;
        final World finalWorld = world;
        world.execute(() -> {
            Ref<EntityStore> ref = finalSender.getReference();
            if (ref == null || !ref.isValid()) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("couldNotGetPosition"), "#FF5555"));
                return;
            }
            Store<EntityStore> store = ref.getStore();
            if (store == null) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("couldNotGetPosition"), "#FF5555"));
                return;
            }
            handlePlayerWarp(ctx, store, ref, finalSender, finalWorld, input);
        });
    }

    /**
     * Player /warp handling on the world thread: no arg -> GUI, "list" -> list, otherwise teleport self.
     */
    private void handlePlayerWarp(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref,
                                  PlayerRef player, World world, String input) {
        ConfigManager configManager = EliteEssentials.getInstance().getConfigManager();
        PluginConfig config = configManager.getConfig();

        if (WorldBlacklistUtil.isWorldBlacklisted(world.getName(), config.warps.blacklistedWorlds)) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("commandBlacklistedWorld"), "#FF5555"));
            return;
        }

        if (!CommandPermissionUtil.canExecute(ctx, player, Permissions.WARPS, config.warps.enabled)) {
            return;
        }

        // /warp list - text list
        if ("list".equalsIgnoreCase(input)) {
            showWarpList(ctx, player, configManager);
            return;
        }

        // /warp <name> - teleport self to warp
        if (input != null) {
            goToWarp(ctx, store, ref, player, world, input, warpService, backService, false);
            return;
        }

        // /warp - open GUI
        UUID playerId = player.getUuid();
        PermissionService perms = PermissionService.get();

        boolean hasAccessibleWarps = false;
        for (Warp warp : warpService.getAllWarps().values()) {
            if (perms.canAccessWarp(playerId, warp.getName(), warp.getPermission())) {
                hasAccessibleWarps = true;
                break;
            }
        }

        if (!hasAccessibleWarps) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("warpNoWarps"), "#FF5555"));
            return;
        }

        Player playerEntity = store.getComponent(ref, Player.getComponentType());
        if (playerEntity == null) {
            ctx.sendMessage(MessageFormatter.formatWithFallback("&cCould not open warps menu.", "#FF5555"));
            return;
        }

        WarpSelectionPage page = new WarpSelectionPage(player, warpService, backService, configManager, world, ref, store);
        playerEntity.getPageManager().openCustomPage(ref, store, page);
    }

    private void showWarpList(CommandContext ctx, PlayerRef player, ConfigManager configManager) {
        UUID playerId = player.getUuid();
        PermissionService perms = PermissionService.get();
        boolean isAdmin = perms.isAdmin(playerId);

        List<Warp> accessibleWarps = warpService.getAllWarps().values().stream()
            .filter(w -> perms.canAccessWarp(playerId, w.getName(), w.getPermission()))
            .collect(Collectors.toList());

        if (accessibleWarps.isEmpty()) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("warpNoWarps"), "#FF5555"));
            return;
        }

        String warpList = accessibleWarps.stream()
                .map(w -> {
                    String name = w.getName();
                    if (isAdmin && w.isOpOnly()) {
                        return name + " (OP)";
                    }
                    return name;
                })
                .collect(Collectors.joining(", "));

        ctx.sendMessage(Message.join(
            MessageFormatter.formatWithFallback(configManager.getMessage("warpListHeader"), "#55FF55"),
            Message.raw(warpList).color("#FFFFFF")
        ));
    }

    /**
     * Resolve the sender to a live PlayerRef (handles both PlayerRef and Player sender types).
     */
    @SuppressWarnings("removal") // Entity.getUuid() deprecated; no alternative in CommandContext for executeSync
    private static PlayerRef resolveSenderPlayer(Object sender) {
        if (sender instanceof PlayerRef playerRef) {
            return playerRef;
        }
        if (sender instanceof Player player) {
            UUID uuid = player.getUuid();
            return uuid != null ? Universe.get().getPlayer(uuid) : null;
        }
        return null;
    }

    /**
     * Console/admin action: send another online player to a warp.
     * Bypasses warmup, cooldown, cost and warp-access checks (intended for server automation / admins).
     */
    private static void adminTeleportToWarp(CommandContext ctx, PlayerRef target, String warpName,
                                            WarpService warpService, BackService backService) {
        ConfigManager configManager = EliteEssentials.getInstance().getConfigManager();

        Optional<Warp> warpOpt = warpService.getWarp(warpName);
        if (warpOpt.isEmpty()) {
            String warpList = warpService.getAllWarps().values().stream()
                .map(Warp::getName).collect(Collectors.joining(", "));
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("warpNotFound", "name", warpName, "list", warpList), "#FF5555"));
            return;
        }
        Warp warp = warpOpt.get();
        Location loc = warp.getLocation();

        World currentWorld = Universe.get().getWorld(target.getWorldUuid());
        if (currentWorld == null) {
            ctx.sendMessage(Message.raw("Could not determine the player's world.").color("#FF5555"));
            return;
        }
        World resolvedTargetWorld = Universe.get().getWorld(loc.getWorld());
        final World finalTargetWorld = resolvedTargetWorld != null ? resolvedTargetWorld : currentWorld;
        final World finalCurrentWorld = currentWorld;
        final String finalWarpName = warp.getName();

        finalCurrentWorld.execute(() -> {
            Ref<EntityStore> ref = target.getReference();
            if (ref != null && ref.isValid()) {
                Store<EntityStore> store = ref.getStore();
                if (store != null) {
                    TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
                    if (transform != null) {
                        Vector3d currentPos = transform.getPosition();
                        HeadRotation headRotation = store.getComponent(ref, HeadRotation.getComponentType());
                        Rotation3f rotation = headRotation != null ? headRotation.getRotation() : new Rotation3f(0, 0, 0);
                        backService.pushLocation(target.getUuid(), new Location(
                            finalCurrentWorld.getName(),
                            currentPos.x, currentPos.y, currentPos.z,
                            rotation.y, 0f));
                    }
                }
            }

            Vector3d targetPos = new Vector3d(loc.getX(), loc.getY(), loc.getZ());
            Rotation3f targetRot = new Rotation3f(0, loc.getYaw(), 0);

            TeleportUtil.safeTeleport(finalCurrentWorld, finalTargetWorld, targetPos, targetRot, target,
                () -> {
                    target.sendMessage(MessageFormatter.formatWithFallback(
                        configManager.getMessage("warpTeleported", "name", finalWarpName), "#55FF55"));
                    ctx.sendMessage(Message.raw(
                        "Teleported " + target.getUsername() + " to warp " + finalWarpName + ".").color("#55FF55"));
                },
                () -> ctx.sendMessage(Message.raw(
                    "Teleport failed - destination chunk could not be loaded.").color("#FF5555"))
            );
        });
    }

    /**
     * Teleport a player to a warp location.
     * @param silent If true, suppresses SUCCESS messages only (errors still show for user feedback)
     */
    public static void goToWarp(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref,
                         PlayerRef player, World world, String warpName,
                         WarpService warpService, BackService backService, boolean silent) {
        ConfigManager configManager = EliteEssentials.getInstance().getConfigManager();
        PluginConfig config = configManager.getConfig();
        
        UUID playerId = player.getUuid();
        WarmupService warmupService = EliteEssentials.getInstance().getWarmupService();
        CooldownService cooldownService = EliteEssentials.getInstance().getCooldownService();
        PermissionService perms = PermissionService.get();
        
        // Get effective cooldown from permissions (handles bypass + custom values)
        int effectiveCooldown = CommandPermissionUtil.getEffectiveTpCooldown(playerId, COMMAND_NAME, config.warps.cooldownSeconds);
        
        // Check cooldown if player has one
        if (effectiveCooldown > 0) {
            int cooldownRemaining = cooldownService.getCooldownRemaining(COMMAND_NAME, playerId);
            if (cooldownRemaining > 0) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("onCooldown", "seconds", String.valueOf(cooldownRemaining)), "#FF5555"));
                return;
            }
        }
        
        if (warmupService.hasActiveWarmup(playerId)) {
            // Always show this error - player needs to know why nothing happened
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("teleportInProgress"), "#FF5555"));
            return;
        }
        
        Optional<Warp> warpOpt = warpService.getWarp(warpName);
        
        if (warpOpt.isEmpty()) {
            // Always show warp not found - player needs feedback
            List<Warp> available = warpService.getAllWarps().values().stream()
                .filter(w -> perms.canAccessWarp(playerId, w.getName(), w.getPermission()))
                .collect(Collectors.toList());
            if (available.isEmpty()) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("warpNoWarps"), "#FF5555"));
            } else {
                String warpList = available.stream().map(Warp::getName).collect(Collectors.joining(", "));
                ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("warpNotFound", "name", warpName, "list", warpList), "#FF5555"));
            }
            return;
        }
        
        Warp warp = warpOpt.get();
        
        if (!perms.canAccessWarp(playerId, warp.getName(), warp.getPermission())) {
            // Always show permission error - critical feedback for user
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("warpNoPermission"), "#FF5555"));
            return;
        }
        
        CostService costService = EliteEssentials.getInstance().getCostService();
        double cost = config.warps.cost;
        // Cost check always shows errors (insufficient funds)
        if (costService != null && !costService.checkCanAfford(ctx, player, "warp", cost, false)) {
            return;
        }
        
        Location loc = warp.getLocation();
        
        TransformComponent transform = (TransformComponent) store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            // Always show position error
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("couldNotGetPosition"), "#FF5555"));
            return;
        }
        
        Vector3d currentPos = transform.getPosition();
        HeadRotation headRotation = (HeadRotation) store.getComponent(ref, HeadRotation.getComponentType());
        Rotation3f rotation = headRotation != null ? headRotation.getRotation() : new Rotation3f(0, 0, 0);
        
        Location currentLoc = new Location(
            world.getName(),
            currentPos.x, currentPos.y, currentPos.z,
            rotation.y, 0f
        );
        
        World targetWorld = Universe.get().getWorld(loc.getWorld());
        if (targetWorld == null) {
            targetWorld = world;
        }
        final World finalWorld = targetWorld;
        final String finalWarpName = warp.getName();
        final CostService finalCostService = costService;
        final double finalCost = cost;
        final boolean finalSilent = silent;
        final int finalEffectiveCooldown = effectiveCooldown;
        
        // Always use PlayerRef to get fresh refs at teleport time
        Runnable doTeleport = () -> {
            backService.pushLocation(playerId, currentLoc);
            
            Vector3d targetPos = new Vector3d(loc.getX(), loc.getY(), loc.getZ());
            Rotation3f targetRot = new Rotation3f(0, loc.getYaw(), 0);
            
            TeleportUtil.safeTeleport(world, finalWorld, targetPos, targetRot, player,
                () -> {
                    if (finalCostService != null) {
                        finalCostService.charge(ctx, player, "warp", finalCost);
                    }
                    if (finalEffectiveCooldown > 0) {
                        cooldownService.setCooldown(COMMAND_NAME, playerId, finalEffectiveCooldown);
                    }
                    if (!finalSilent) {
                        player.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("warpTeleported", "name", finalWarpName), "#55FF55"));
                    }
                },
                () -> {
                    player.sendMessage(MessageFormatter.formatWithFallback("&cTeleport failed - destination chunk could not be loaded.", "#FF5555"));
                }
            );
        };

        int warmupSeconds = CommandPermissionUtil.getEffectiveWarmup(playerId, COMMAND_NAME, config.warps.warmupSeconds);
        
        // Only suppress warmup message when silent (countdown still shows via warmup service)
        if (warmupSeconds > 0 && !silent) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("warpWarmup", "name", finalWarpName, "seconds", String.valueOf(warmupSeconds)), "#FFAA00"));
        }
        warmupService.startWarmup(player, currentPos, warmupSeconds, doTeleport, COMMAND_NAME, world, store, ref, false);
    }
}
