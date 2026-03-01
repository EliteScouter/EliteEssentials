package com.eliteessentials.commands.hytale;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.services.FreezeService;
import com.eliteessentials.storage.PlayerFileStorage;
import com.eliteessentials.util.CommandPermissionUtil;
import com.eliteessentials.util.MessageFormatter;
import com.eliteessentials.util.PlayerSuggestionProvider;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.MovementSettings;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementConfig;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Toggle freeze on a player. Frozen players cannot move, jump, or fly.
 * Works by setting all movement speeds to 0 via MovementSettings.
 * Persists across restarts. Supports offline players (freeze applied on join).
 */
public class HytaleFreezeCommand extends AbstractPlayerCommand {

    private static final Logger logger = Logger.getLogger("EliteEssentials");
    private final FreezeService freezeService;
    private final ConfigManager configManager;
    private final PlayerFileStorage playerFileStorage;

    public HytaleFreezeCommand(FreezeService freezeService, ConfigManager configManager,
                                PlayerFileStorage playerFileStorage) {
        super("freeze", "Toggle freeze on a player");
        this.freezeService = freezeService;
        this.configManager = configManager;
        this.playerFileStorage = playerFileStorage;
        withRequiredArg("player", "Target player", ArgTypes.STRING)
            .suggest(PlayerSuggestionProvider.INSTANCE);
        setAllowsExtraArguments(true);
    }

    @Override
    protected boolean canGeneratePermission() { return false; }

    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef player, @Nonnull World world) {
        if (!CommandPermissionUtil.canExecuteAdmin(ctx, player, Permissions.ADMIN_FREEZE,
                configManager.getConfig().freeze.enabled)) {
            return;
        }
        String rawInput = ctx.getInputString();
        String[] parts = rawInput.split("\\s+", 2);
        if (parts.length < 2) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("freezeUsage"), "#FF5555"));
            return;
        }
        String targetName = parts[1];

        // Try online first
        PlayerRef target = PlayerSuggestionProvider.findPlayer(targetName);

        if (target != null) {
            // Online player - apply movement changes immediately
            handleOnlineFreeze(ctx, player, target);
        } else {
            // Offline player - toggle freeze state in data only (applied on join)
            handleOfflineFreeze(ctx, player, targetName);
        }
    }

    private void handleOnlineFreeze(CommandContext ctx, PlayerRef player, PlayerRef target) {
        Ref<EntityStore> tRef = target.getReference();
        if (tRef == null || !tRef.isValid()) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("freezeError"), "#FF5555"));
            return;
        }

        Store<EntityStore> tStore = tRef.getStore();
        EntityStore targetEntityStore = tStore.getExternalData();
        World targetWorld = targetEntityStore != null ? targetEntityStore.getWorld() : null;
        if (targetWorld == null) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("freezeError"), "#FF5555"));
            return;
        }

        final PlayerRef finalTarget = target;
        targetWorld.execute(() -> {
            try {
                MovementManager movementManager = tStore.getComponent(tRef, MovementManager.getComponentType());
                if (movementManager == null) {
                    ctx.sendMessage(MessageFormatter.formatWithFallback(
                        configManager.getMessage("freezeError"), "#FF5555"));
                    return;
                }

                MovementSettings settings = movementManager.getSettings();

                if (freezeService.isFrozen(finalTarget.getUuid())) {
                    // Unfreeze
                    freezeService.unfreeze(finalTarget.getUuid());
                    settings.baseSpeed = MovementConfig.DEFAULT_MOVEMENT.getBaseSpeed();
                    settings.jumpForce = MovementConfig.DEFAULT_MOVEMENT.getJumpForce();
                    settings.horizontalFlySpeed = MovementConfig.DEFAULT_MOVEMENT.getHorizontalFlySpeed();
                    settings.verticalFlySpeed = MovementConfig.DEFAULT_MOVEMENT.getVerticalFlySpeed();
                    movementManager.update(finalTarget.getPacketHandler());

                    ctx.sendMessage(MessageFormatter.formatWithFallback(
                        configManager.getMessage("unfreezeSuccess", "player", finalTarget.getUsername()), "#55FF55"));
                    finalTarget.sendMessage(MessageFormatter.formatWithFallback(
                        configManager.getMessage("unfreezeNotify"), "#55FF55"));
                } else {
                    // Freeze
                    freezeService.freeze(finalTarget.getUuid(), finalTarget.getUsername(), player.getUsername());
                    settings.baseSpeed = 0f;
                    settings.jumpForce = 0f;
                    settings.horizontalFlySpeed = 0f;
                    settings.verticalFlySpeed = 0f;
                    movementManager.update(finalTarget.getPacketHandler());

                    ctx.sendMessage(MessageFormatter.formatWithFallback(
                        configManager.getMessage("freezeSuccess", "player", finalTarget.getUsername()), "#55FF55"));
                    finalTarget.sendMessage(MessageFormatter.formatWithFallback(
                        configManager.getMessage("freezeNotify"), "#FF5555"));
                }
            } catch (Exception e) {
                logger.severe("[FreezeCommand] Error toggling freeze: " + e.getMessage());
                ctx.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("freezeError"), "#FF5555"));
            }
        });
    }

    private void handleOfflineFreeze(CommandContext ctx, PlayerRef player, String targetName) {
        Optional<UUID> offlineId = playerFileStorage.getUuidByName(targetName);
        if (!offlineId.isPresent()) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("playerNeverJoined", "player", targetName), "#FF5555"));
            return;
        }
        UUID targetId = offlineId.get();

        if (freezeService.isFrozen(targetId)) {
            // Unfreeze offline player
            freezeService.unfreeze(targetId);
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("unfreezeSuccess", "player", targetName), "#55FF55"));
        } else {
            // Freeze offline player - movement will be zeroed on join via ConnectListener
            freezeService.freeze(targetId, targetName, player.getUsername());
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("freezeSuccess", "player", targetName), "#55FF55"));
        }
    }
}
