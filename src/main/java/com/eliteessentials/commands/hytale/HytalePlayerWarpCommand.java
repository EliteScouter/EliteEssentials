package com.eliteessentials.commands.hytale;

import com.eliteessentials.EliteEssentials;
import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.config.PluginConfig;
import com.eliteessentials.gui.PlayerWarpSelectionPage;
import com.eliteessentials.model.Location;
import com.eliteessentials.model.PlayerWarp;
import com.eliteessentials.permissions.PermissionService;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.services.BackService;
import com.eliteessentials.services.CooldownService;
import com.eliteessentials.services.CostService;
import com.eliteessentials.services.PlayerWarpService;
import com.eliteessentials.services.WarmupService;
import com.eliteessentials.util.CommandPermissionUtil;
import com.eliteessentials.util.MessageFormatter;
import com.eliteessentials.util.TeleportUtil;
import com.eliteessentials.util.WorldBlacklistUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Command: /pwarp [subcommand|name]
 * Player warps system. Players can create public/private warps.
 * 
 * Usage:
 * - /pwarp - Show help menu
 * - /pwarp gui - Open player warps GUI
 * - /pwarp <name> - Teleport to a player warp
 * - /pwarp create <public|private> <description...> - Create a warp at current location
 * - /pwarp delete <name> - Delete your warp
 * - /pwarp list - List your warps
 * - /pwarp info <name> - View warp details
 * - /pwarp setdesc <name> <description...> - Update description
 * - /pwarp setloc <name> - Update warp location to current position
 * - /pwarp toggle <name> - Toggle public/private
 */
public class HytalePlayerWarpCommand extends AbstractPlayerCommand {

    private static final String COMMAND_NAME = "pwarp";

    private final PlayerWarpService playerWarpService;
    private final BackService backService;

    public HytalePlayerWarpCommand(PlayerWarpService playerWarpService, BackService backService) {
        super(COMMAND_NAME, "Player warps - create and share warp locations");
        this.playerWarpService = playerWarpService;
        this.backService = backService;
        addAliases("pwarps", "playerwarp");
        setAllowsExtraArguments(true);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                          @Nonnull PlayerRef player, @Nonnull World world) {
        ConfigManager configManager = EliteEssentials.getInstance().getConfigManager();
        PluginConfig config = configManager.getConfig();

        if (WorldBlacklistUtil.isWorldBlacklisted(world.getName(), config.playerWarps.blacklistedWorlds)) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("commandBlacklistedWorld"), "#FF5555"));
            return;
        }

        if (!CommandPermissionUtil.canExecute(ctx, player, Permissions.PWARP, config.playerWarps.enabled)) {
            return;
        }

        // Parse raw input to get subcommand
        String rawInput = ctx.getInputString();
        String[] parts = rawInput.split("\\s+");

        // /pwarp with no args = help
        if (parts.length < 2) {
            showHelp(ctx, configManager);
            return;
        }

        String subcommand = parts[1].toLowerCase();

        switch (subcommand) {
            case "gui" -> handleGui(ctx, store, ref, player, world, configManager, config);
            case "create" -> handleCreate(ctx, parts, player, world, store, ref, configManager, config);
            case "delete", "del", "remove" -> handleDelete(ctx, parts, player, configManager);
            case "list" -> handleList(ctx, player, configManager);
            case "info" -> handleInfo(ctx, parts, configManager);
            case "setdesc" -> handleSetDesc(ctx, parts, player, configManager);
            case "setloc" -> handleSetLoc(ctx, parts, player, world, store, ref, configManager);
            case "toggle" -> handleToggle(ctx, parts, player, configManager);
            case "help" -> showHelp(ctx, configManager);
            default -> handleTeleport(ctx, subcommand, store, ref, player, world, configManager, config);
        }
    }

    private void showHelp(CommandContext ctx, ConfigManager configManager) {
        ctx.sendMessage(Message.raw("=== Player Warps ===").color("#55FFFF"));
        ctx.sendMessage(Message.join(
            Message.raw("/pwarp gui").color("#55FF55"),
            Message.raw(" - Open player warps GUI").color("#777777")
        ));
        ctx.sendMessage(Message.join(
            Message.raw("/pwarp <name>").color("#55FF55"),
            Message.raw(" - Teleport to a player warp").color("#777777")
        ));
        ctx.sendMessage(Message.join(
            Message.raw("/pwarp create <public|private> <name> <description>").color("#55FF55"),
            Message.raw(" - Create a warp").color("#777777")
        ));
        ctx.sendMessage(Message.join(
            Message.raw("/pwarp delete <name>").color("#55FF55"),
            Message.raw(" - Delete your warp").color("#777777")
        ));
        ctx.sendMessage(Message.join(
            Message.raw("/pwarp list").color("#55FF55"),
            Message.raw(" - List your warps").color("#777777")
        ));
        ctx.sendMessage(Message.join(
            Message.raw("/pwarp info <name>").color("#55FF55"),
            Message.raw(" - View warp details").color("#777777")
        ));
        ctx.sendMessage(Message.join(
            Message.raw("/pwarp setdesc <name> <description>").color("#55FF55"),
            Message.raw(" - Update description").color("#777777")
        ));
        ctx.sendMessage(Message.join(
            Message.raw("/pwarp setloc <name>").color("#55FF55"),
            Message.raw(" - Move warp to current location").color("#777777")
        ));
        ctx.sendMessage(Message.join(
            Message.raw("/pwarp toggle <name>").color("#55FF55"),
            Message.raw(" - Toggle public/private").color("#777777")
        ));
    }

    private void handleGui(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref,
                           PlayerRef player, World world, ConfigManager configManager, PluginConfig config) {
        if (!CommandPermissionUtil.canExecute(ctx, player, Permissions.PWARP_GUI, config.playerWarps.enabled)) {
            return;
        }

        Player playerEntity = store.getComponent(ref, Player.getComponentType());
        if (playerEntity == null) {
            ctx.sendMessage(MessageFormatter.formatWithFallback("&cCould not open player warps menu.", "#FF5555"));
            return;
        }

        PlayerWarpSelectionPage page = new PlayerWarpSelectionPage(
            player, playerWarpService, backService, configManager, world, ref, store);
        playerEntity.getPageManager().openCustomPage(ref, store, page);
    }

    private void handleCreate(CommandContext ctx, String[] parts, PlayerRef player, World world,
                              Store<EntityStore> store, Ref<EntityStore> ref,
                              ConfigManager configManager, PluginConfig config) {
        UUID playerId = player.getUuid();

        if (!CommandPermissionUtil.canExecute(ctx, player, Permissions.PWARP_CREATE, config.playerWarps.enabled)) {
            return;
        }

        // /pwarp create <public|private> <description...>
        // parts[0]=pwarp, parts[1]=create, parts[2]=visibility, parts[3+]=description
        if (parts.length < 4) {
            ctx.sendMessage(Message.raw("Usage: /pwarp create <public|private> <name> [description...]").color("#FFAA00"));
            ctx.sendMessage(Message.raw("Example: /pwarp create public myshop Welcome to my shop").color("#777777"));
            return;
        }

        String visStr = parts[2].toUpperCase();
        PlayerWarp.Visibility visibility;
        try {
            visibility = PlayerWarp.Visibility.valueOf(visStr);
        } catch (IllegalArgumentException e) {
            ctx.sendMessage(Message.raw("Visibility must be 'public' or 'private'.").color("#FF5555"));
            return;
        }

        String name = parts[3];

        // Build description from remaining parts
        StringBuilder descBuilder = new StringBuilder();
        for (int i = 4; i < parts.length; i++) {
            if (i > 4) descBuilder.append(" ");
            descBuilder.append(parts[i]);
        }
        String description = descBuilder.toString();

        // Check create cost
        CostService costService = EliteEssentials.getInstance().getCostService();
        double createCost = config.playerWarps.createCost;
        if (costService != null && createCost > 0) {
            if (!costService.checkCanAfford(ctx, player, "pwarp.create", createCost, false)) {
                return;
            }
        }

        // Get player location
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("couldNotGetPosition"), "#FF5555"));
            return;
        }

        Vector3d pos = transform.getPosition();
        HeadRotation headRotation = store.getComponent(ref, HeadRotation.getComponentType());
        Vector3f rotation = headRotation != null ? headRotation.getRotation() : new Vector3f(0, 0, 0);

        Location location = new Location(
            world.getName(), pos.getX(), pos.getY(), pos.getZ(), rotation.y, 0f
        );

        String ownerName = player.getUsername() != null ? player.getUsername() : playerId.toString();
        PlayerWarpService.Result result = playerWarpService.createWarp(
            name, location, playerId, ownerName, visibility, description);

        switch (result) {
            case SUCCESS -> {
                // Charge create cost
                if (costService != null && createCost > 0) {
                    costService.charge(ctx, player, "pwarp.create", createCost);
                }
                ctx.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("pwarpCreated", "name", name, "visibility", visibility.name()),
                    "#55FF55"));
            }
            case INVALID_NAME -> ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("pwarpInvalidName"), "#FF5555"));
            case NAME_TAKEN -> ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("pwarpNameTaken", "name", name), "#FF5555"));
            case LIMIT_REACHED -> {
                int limit = playerWarpService.getWarpLimit(playerId);
                ctx.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("pwarpLimitReached", "limit", String.valueOf(limit)), "#FF5555"));
            }
            case DESCRIPTION_TOO_LONG -> ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("pwarpDescriptionTooLong"), "#FF5555"));
            default -> ctx.sendMessage(Message.raw("Failed to create player warp.").color("#FF5555"));
        }
    }

    private void handleDelete(CommandContext ctx, String[] parts, PlayerRef player, ConfigManager configManager) {
        if (parts.length < 3) {
            ctx.sendMessage(Message.raw("Usage: /pwarp delete <name>").color("#FFAA00"));
            return;
        }
        String name = parts[2];
        UUID playerId = player.getUuid();
        boolean isAdmin = PermissionService.get().canUseAdminCommand(
            playerId, Permissions.PWARP_ADMIN_DELETE, true);

        PlayerWarpService.Result result = playerWarpService.deleteWarp(name, playerId, isAdmin);
        switch (result) {
            case SUCCESS -> ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("pwarpDeleted", "name", name), "#55FF55"));
            case NOT_FOUND -> ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("pwarpNotFound", "name", name), "#FF5555"));
            case NOT_OWNER -> ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("pwarpNotOwner"), "#FF5555"));
            default -> ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("pwarpDeleteFailed"), "#FF5555"));
        }
    }

    private void handleList(CommandContext ctx, PlayerRef player, ConfigManager configManager) {
        UUID playerId = player.getUuid();
        List<PlayerWarp> myWarps = playerWarpService.getWarpsByOwner(playerId);

        if (myWarps.isEmpty()) {
            ctx.sendMessage(Message.raw("You have no player warps.").color("#FFAA00"));
            ctx.sendMessage(Message.raw("Use /pwarp create <public|private> <name> <description> to create one.").color("#777777"));
            return;
        }

        int limit = playerWarpService.getWarpLimit(playerId);
        String limitStr = limit == -1 ? "unlimited" : String.valueOf(limit);
        ctx.sendMessage(Message.raw("=== Your Player Warps (" + myWarps.size() + "/" + limitStr + ") ===").color("#55FFFF"));
        for (PlayerWarp warp : myWarps) {
            String vis = warp.isPublic() ? "&a[Public]" : "&c[Private]";
            ctx.sendMessage(Message.join(
                Message.raw(warp.getName()).color("#55FF55"),
                Message.raw(" ").color("#777777"),
                MessageFormatter.formatWithFallback(vis, "#AAAAAA"),
                Message.raw(warp.getDescription().isEmpty() ? "" : " - " + warp.getDescription()).color("#777777")
            ));
        }
    }

    private void handleInfo(CommandContext ctx, String[] parts, ConfigManager configManager) {
        if (parts.length < 3) {
            ctx.sendMessage(Message.raw("Usage: /pwarp info <name>").color("#FFAA00"));
            return;
        }
        String name = parts[2];
        Optional<PlayerWarp> warpOpt = playerWarpService.getWarp(name);
        if (warpOpt.isEmpty()) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("pwarpNotFound", "name", name), "#FF5555"));
            return;
        }
        PlayerWarp warp = warpOpt.get();
        Location loc = warp.getLocation();
        ctx.sendMessage(MessageFormatter.formatWithFallback(
            configManager.getMessage("pwarpInfo", "name", warp.getName()), "#55FFFF"));
        ctx.sendMessage(MessageFormatter.formatWithFallback(
            configManager.getMessage("pwarpInfoOwner", "owner", warp.getOwnerName()), "#AAAAAA"));
        ctx.sendMessage(MessageFormatter.formatWithFallback(
            configManager.getMessage("pwarpInfoVisibility", "visibility", warp.getVisibility().name()), "#AAAAAA"));
        String desc = warp.getDescription().isEmpty() ? "(none)" : warp.getDescription();
        ctx.sendMessage(MessageFormatter.formatWithFallback(
            configManager.getMessage("pwarpInfoDescription", "description", desc), "#AAAAAA"));
        ctx.sendMessage(MessageFormatter.formatWithFallback(
            configManager.getMessage("pwarpInfoLocation", "world", loc.getWorld(),
                "x", String.valueOf(loc.getBlockX()), "y", String.valueOf(loc.getBlockY()),
                "z", String.valueOf(loc.getBlockZ())), "#AAAAAA"));
    }

    private void handleSetDesc(CommandContext ctx, String[] parts, PlayerRef player, ConfigManager configManager) {
        if (parts.length < 4) {
            ctx.sendMessage(Message.raw("Usage: /pwarp setdesc <name> <description...>").color("#FFAA00"));
            return;
        }
        String name = parts[2];
        StringBuilder descBuilder = new StringBuilder();
        for (int i = 3; i < parts.length; i++) {
            if (i > 3) descBuilder.append(" ");
            descBuilder.append(parts[i]);
        }
        PlayerWarpService.Result result = playerWarpService.updateDescription(name, player.getUuid(), descBuilder.toString());
        switch (result) {
            case SUCCESS -> ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("pwarpDescriptionUpdated", "name", name), "#55FF55"));
            case NOT_FOUND -> ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("pwarpNotFound", "name", name), "#FF5555"));
            case NOT_OWNER -> ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("pwarpNotOwner"), "#FF5555"));
            case DESCRIPTION_TOO_LONG -> ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("pwarpDescriptionTooLong"), "#FF5555"));
            default -> ctx.sendMessage(Message.raw("Failed to update description.").color("#FF5555"));
        }
    }

    private void handleSetLoc(CommandContext ctx, String[] parts, PlayerRef player, World world,
                              Store<EntityStore> store, Ref<EntityStore> ref, ConfigManager configManager) {
        if (parts.length < 3) {
            ctx.sendMessage(Message.raw("Usage: /pwarp setloc <name>").color("#FFAA00"));
            return;
        }
        String name = parts[2];
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("couldNotGetPosition"), "#FF5555"));
            return;
        }
        Vector3d pos = transform.getPosition();
        HeadRotation headRotation = store.getComponent(ref, HeadRotation.getComponentType());
        Vector3f rotation = headRotation != null ? headRotation.getRotation() : new Vector3f(0, 0, 0);
        Location location = new Location(world.getName(), pos.getX(), pos.getY(), pos.getZ(), rotation.y, 0f);

        PlayerWarpService.Result result = playerWarpService.updateLocation(name, player.getUuid(), location);
        switch (result) {
            case SUCCESS -> ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("pwarpLocationUpdated", "name", name), "#55FF55"));
            case NOT_FOUND -> ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("pwarpNotFound", "name", name), "#FF5555"));
            case NOT_OWNER -> ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("pwarpNotOwner"), "#FF5555"));
            default -> ctx.sendMessage(Message.raw("Failed to update location.").color("#FF5555"));
        }
    }

    private void handleToggle(CommandContext ctx, String[] parts, PlayerRef player, ConfigManager configManager) {
        if (parts.length < 3) {
            ctx.sendMessage(Message.raw("Usage: /pwarp toggle <name>").color("#FFAA00"));
            return;
        }
        String name = parts[2];
        PlayerWarpService.Result result = playerWarpService.toggleVisibility(name, player.getUuid());
        switch (result) {
            case SUCCESS -> {
                Optional<PlayerWarp> warpOpt = playerWarpService.getWarp(name);
                String vis = warpOpt.map(w -> w.getVisibility().name()).orElse("UNKNOWN");
                ctx.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("pwarpVisibilityChanged", "name", name, "visibility", vis), "#55FF55"));
            }
            case NOT_FOUND -> ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("pwarpNotFound", "name", name), "#FF5555"));
            case NOT_OWNER -> ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("pwarpNotOwner"), "#FF5555"));
            default -> ctx.sendMessage(Message.raw("Failed to toggle visibility.").color("#FF5555"));
        }
    }

    private void handleTeleport(CommandContext ctx, String warpName, Store<EntityStore> store, Ref<EntityStore> ref,
                                PlayerRef player, World world, ConfigManager configManager, PluginConfig config) {
        UUID playerId = player.getUuid();
        WarmupService warmupService = EliteEssentials.getInstance().getWarmupService();
        CooldownService cooldownService = EliteEssentials.getInstance().getCooldownService();

        int effectiveCooldown = CommandPermissionUtil.getEffectiveTpCooldown(playerId, COMMAND_NAME, config.playerWarps.cooldownSeconds);
        if (effectiveCooldown > 0) {
            int remaining = cooldownService.getCooldownRemaining(COMMAND_NAME, playerId);
            if (remaining > 0) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("onCooldown", "seconds", String.valueOf(remaining)), "#FF5555"));
                return;
            }
        }

        if (warmupService.hasActiveWarmup(playerId)) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("teleportInProgress"), "#FF5555"));
            return;
        }

        Optional<PlayerWarp> warpOpt = playerWarpService.getWarp(warpName);
        if (warpOpt.isEmpty()) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("pwarpNotFound", "name", warpName), "#FF5555"));
            return;
        }

        PlayerWarp warp = warpOpt.get();
        if (!warp.canAccess(playerId) && !PermissionService.get().isAdmin(playerId)) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("pwarpNotFound", "name", warpName), "#FF5555"));
            return;
        }

        CostService costService = EliteEssentials.getInstance().getCostService();
        double cost = config.playerWarps.cost;
        if (costService != null && !costService.checkCanAfford(ctx, player, "pwarp", cost, false)) {
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
        Location currentLoc = new Location(world.getName(), currentPos.getX(), currentPos.getY(), currentPos.getZ(), rotation.y, 0f);

        Location loc = warp.getLocation();
        World targetWorld = Universe.get().getWorld(loc.getWorld());
        if (targetWorld == null) targetWorld = world;
        final World finalWorld = targetWorld;
        final String finalWarpName = warp.getName();
        final String finalOwnerName = warp.getOwnerName();
        final CostService finalCostService = costService;
        final double finalCost = cost;
        final int finalCooldown = effectiveCooldown;

        Runnable doTeleport = () -> {
            backService.pushLocation(playerId, currentLoc);
            Vector3d targetPos = new Vector3d(loc.getX(), loc.getY(), loc.getZ());
            Vector3f targetRot = new Vector3f(0, loc.getYaw(), 0);
            TeleportUtil.safeTeleport(world, finalWorld, targetPos, targetRot, player,
                () -> {
                    if (finalCostService != null) finalCostService.charge(ctx, player, "pwarp", finalCost);
                    if (finalCooldown > 0) cooldownService.setCooldown(COMMAND_NAME, playerId, finalCooldown);
                    player.sendMessage(MessageFormatter.formatWithFallback(
                        configManager.getMessage("pwarpTeleported", "name", finalWarpName, "owner", finalOwnerName), "#55FF55"));
                },
                () -> player.sendMessage(MessageFormatter.formatWithFallback(
                    "&cTeleport failed - destination chunk could not be loaded.", "#FF5555"))
            );
        };

        int warmupSeconds = CommandPermissionUtil.getEffectiveWarmup(playerId, COMMAND_NAME, config.playerWarps.warmupSeconds);
        if (warmupSeconds > 0) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("pwarpWarmup", "name", finalWarpName, "seconds", String.valueOf(warmupSeconds)), "#FFAA00"));
        }
        warmupService.startWarmup(player, currentPos, warmupSeconds, doTeleport, COMMAND_NAME, world, store, ref, false);
    }
}
