package com.eliteessentials.commands.hytale;

import com.eliteessentials.EliteEssentials;
import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.config.PluginConfig;
import com.eliteessentials.model.TpaRequest;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.permissions.PermissionService;
import com.eliteessentials.services.TpaService;
import com.eliteessentials.services.VanishService;
import com.eliteessentials.util.CommandPermissionUtil;
import com.eliteessentials.util.MessageFormatter;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.eliteessentials.gui.TpaSelectionPage;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nonnull;

/**
 * Command: /tpahere <player>
 * Requests a player to teleport to you (opposite of /tpa).
 * 
 * Permissions:
 * - eliteessentials.command.tpahere - Send tpahere requests
 */
public class HytaleTpahereCommand extends AbstractPlayerCommand {

    private static final String COMMAND_NAME = "tpahere";
    
    private final TpaService tpaService;

    public HytaleTpahereCommand(TpaService tpaService) {
        super(COMMAND_NAME, "Request a player to teleport to you");
        this.tpaService = tpaService;
        addUsageVariant(new TpahereWithTargetCommand(tpaService));
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
        // Only check affordability - cost will be charged when teleport actually happens in /tpaccept
        if (!CommandPermissionUtil.canExecuteWithCost(ctx, player, Permissions.TPAHERE,
                config.tpa.enabled, "tpahere", config.tpa.tpahereCost)) {
            return;
        }
        Player playerEntity = store.getComponent(ref, Player.getComponentType());
        if (playerEntity == null) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("tpaOpenFailed"), "#FF5555"));
            return;
        }
        TpaSelectionPage page = new TpaSelectionPage(player, TpaSelectionPage.Mode.TPAHERE, tpaService, configManager);
        playerEntity.getPageManager().openCustomPage(ref, store, page);
    }
    
    /**
     * Find a player by name (case-insensitive).
     */
    private PlayerRef findPlayer(String name, PlayerRef requester) {
        VanishService vanishService = EliteEssentials.getInstance().getVanishService();
        boolean canSeeVanished = canSeeVanishedPlayers(requester);
        List<PlayerRef> players = Universe.get().getPlayers();
        for (PlayerRef p : players) {
            if (p.getUsername().equalsIgnoreCase(name)) {
                if (vanishService != null && vanishService.isVanished(p.getUuid()) && !canSeeVanished) {
                    return null;
                }
                return p;
            }
        }
        return null;
    }

    private boolean canSeeVanishedPlayers(PlayerRef requester) {
        UUID playerId = requester.getUuid();
        PermissionService perms = PermissionService.get();
        return perms.isAdmin(playerId) || perms.hasPermission(playerId, Permissions.VANISH);
    }

    private void sendRequest(@Nonnull CommandContext ctx, @Nonnull PlayerRef player, @Nonnull String targetName) {
        ConfigManager configManager = EliteEssentials.getInstance().getConfigManager();
        PluginConfig config = configManager.getConfig();
        if (!CommandPermissionUtil.canExecuteWithCost(ctx, player, Permissions.TPAHERE,
                config.tpa.enabled, COMMAND_NAME, config.tpa.tpahereCost)) {
            return;
        }

        PlayerRef target = findPlayer(targetName, player);
        if (target == null) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("playerNotFound", "player", targetName), "#FF5555"));
            return;
        }

        TpaService.Result result = tpaService.createRequest(
            player.getUuid(),
            player.getUsername(),
            target.getUuid(),
            target.getUsername(),
            TpaRequest.Type.TPAHERE
        );

        switch (result) {
            case REQUEST_SENT -> {
                ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("tpahereRequestSent", "player", target.getUsername()), "#55FF55"));
                target.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("tpahereRequestReceived", "player", player.getUsername()), "#FFFF55"));
                target.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("tpaRequestInstructions"), "#AAAAAA"));
            }
            case SELF_REQUEST -> ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("tpaSelfRequest"), "#FF5555"));
            case ALREADY_PENDING -> ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("tpaAlreadyPending"), "#FF5555"));
            default -> ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("tpaRequestFailed"), "#FF5555"));
        }
    }

    /**
     * Variant: /tpahere <player>
     */
    private class TpahereWithTargetCommand extends AbstractPlayerCommand {
        private final RequiredArg<String> targetArg;

        TpahereWithTargetCommand(TpaService tpaService) {
            super(COMMAND_NAME);
            this.targetArg = withRequiredArg("player", "Target player", ArgTypes.STRING);
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                              @Nonnull PlayerRef player, @Nonnull World world) {
            sendRequest(ctx, player, ctx.get(targetArg));
        }
    }
}
