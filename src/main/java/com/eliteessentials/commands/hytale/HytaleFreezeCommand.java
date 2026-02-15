package com.eliteessentials.commands.hytale;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.services.FreezeService;
import com.eliteessentials.util.CommandPermissionUtil;
import com.eliteessentials.util.MessageFormatter;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.MovementSettings;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementConfig;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import java.util.logging.Logger;

/**
 * Toggle freeze on a player. Frozen players cannot move, jump, or fly.
 * Works by setting all movement speeds to 0 via MovementSettings.
 * Persists across restarts.
 */
public class HytaleFreezeCommand extends AbstractPlayerCommand {

    private static final Logger logger = Logger.getLogger("EliteEssentials");
    private final FreezeService freezeService;
    private final ConfigManager configManager;

    public HytaleFreezeCommand(FreezeService freezeService, ConfigManager configManager) {
        super("freeze", "Toggle freeze on a player");
        this.freezeService = freezeService;
        this.configManager = configManager;
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

        PlayerRef target = null;
        for (PlayerRef p : Universe.get().getPlayers()) {
            if (p.getUsername().equalsIgnoreCase(targetName)) {
                target = p;
                break;
            }
        }
        if (target == null) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("playerNotFound", "player", targetName), "#FF5555"));
            return;
        }

        // Get the target's entity ref
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

        // Execute on the target's world thread to safely access components
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
                    // Unfreeze - restore default movement
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
                    // Freeze - zero out all movement
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
}
