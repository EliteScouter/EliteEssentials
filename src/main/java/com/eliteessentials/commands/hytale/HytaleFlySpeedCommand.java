package com.eliteessentials.commands.hytale;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.util.CommandPermissionUtil;
import com.eliteessentials.util.MessageFormatter;
import com.eliteessentials.util.PlayerSuggestionProvider;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.UUID;
import com.eliteessentials.util.CommandSpyUtil;

/**
 * Command: /flyspeed <speed> [player]
 * Sets the player's fly speed multiplier. Can target another player from console.
 * 
 * Usage:
 *   /flyspeed <speed>          - Set own fly speed (player only)
 *   /flyspeed <speed> <player> - Set target's fly speed (admin/console)
 * 
 * Speed Range: 10 to 100, or "reset" to restore default (10)
 * 
 * Permissions:
 * - Simple Mode: Admin only
 * - Advanced Mode: eliteessentials.command.misc.flyspeed
 */
public class HytaleFlySpeedCommand extends CommandBase {

    private final ConfigManager configManager;

    public HytaleFlySpeedCommand(ConfigManager configManager) {
        super("flyspeed", "Set fly speed (10-100 or 'reset')");
        this.configManager = configManager;
        setAllowsExtraArguments(true);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void executeSync(@Nonnull CommandContext ctx) {
        CommandSpyUtil.notify(ctx);
        String rawInput = ctx.getInputString().trim();
        String[] parts = rawInput.split("\\s+");

        Object sender = ctx.sender();
        boolean isConsoleSender = !(sender instanceof PlayerRef) && !(sender instanceof Player);

        PlayerRef senderPlayerRef = null;
        if (sender instanceof PlayerRef) {
            senderPlayerRef = (PlayerRef) sender;
        } else if (sender instanceof Player player) {
            @SuppressWarnings("removal")
            UUID playerUuid = player.getUuid();
            senderPlayerRef = playerUuid != null ? Universe.get().getPlayer(playerUuid) : null;
        }

        // Need at least speed arg; console needs speed + player
        if (parts.length < 2) {
            String usage = isConsoleSender ? "Console usage: /flyspeed <speed|reset> <player>" : "Usage: /flyspeed <speed|reset>";
            ctx.sendMessage(Message.raw(usage).color("#FF5555"));
            return;
        }
        if (isConsoleSender && parts.length < 3) {
            ctx.sendMessage(Message.raw("Console usage: /flyspeed <speed|reset> <player>").color("#FF5555"));
            return;
        }

        String speedStr = parts[1];
        String targetPlayerName = parts.length >= 3 ? parts[2] : null;
        boolean isTargetingOther = (targetPlayerName != null);

        // Permission check
        if (!isConsoleSender) {
            if (senderPlayerRef == null) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("noPermission"), "#FF5555"));
                return;
            }
            if (!CommandPermissionUtil.canExecuteAdmin(ctx, senderPlayerRef, Permissions.FLYSPEED,
                    configManager.getConfig().fly.enabled)) {
                return;
            }
        }

        // Resolve target
        PlayerRef targetPlayer;
        if (isTargetingOther) {
            targetPlayer = PlayerSuggestionProvider.findPlayer(targetPlayerName);
            if (targetPlayer == null) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("playerNotFound", "player", targetPlayerName), "#FF5555"));
                return;
            }
        } else {
            targetPlayer = senderPlayerRef;
            if (targetPlayer == null) {
                ctx.sendMessage(Message.raw("Could not determine player.").color("#FF5555"));
                return;
            }
        }

        // Parse speed
        boolean isReset = speedStr.equalsIgnoreCase("reset") || speedStr.equalsIgnoreCase("default");
        float speed = 0;
        if (!isReset) {
            try {
                speed = Float.parseFloat(speedStr);
            } catch (NumberFormatException e) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("flySpeedInvalid"), "#FF5555"));
                return;
            }
            if (speed < 10.0f || speed > 100.0f) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("flySpeedOutOfRange"), "#FF5555"));
                return;
            }
        }

        World targetWorld = findPlayerWorld(targetPlayer);
        if (targetWorld == null) {
            ctx.sendMessage(Message.raw("Could not determine player's world.").color("#FF5555"));
            return;
        }

        final boolean finalIsReset = isReset;
        final float finalSpeed = speed;
        final PlayerRef finalTarget = targetPlayer;
        final boolean finalIsTargetingOther = isTargetingOther;

        targetWorld.execute(() -> {
            Ref<EntityStore> ref = finalTarget.getReference();
            if (ref == null || !ref.isValid()) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("playerNotFound", "player", finalTarget.getUsername()), "#FF5555"));
                return;
            }
            Store<EntityStore> store = ref.getStore();

            MovementManager movementManager = store.getComponent(ref, MovementManager.getComponentType());
            if (movementManager == null) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("flyFailed"), "#FF5555"));
                return;
            }

            var settings = movementManager.getSettings();
            try {
                java.lang.reflect.Field horizontalField = settings.getClass().getField("horizontalFlySpeed");
                java.lang.reflect.Field verticalField = settings.getClass().getField("verticalFlySpeed");

                String suffix = finalIsTargetingOther ? " &7(for " + finalTarget.getUsername() + ")" : "";
                if (finalIsReset) {
                    horizontalField.setFloat(settings, 10.0f);
                    verticalField.setFloat(settings, 10.0f);
                    movementManager.update(finalTarget.getPacketHandler());
                    ctx.sendMessage(MessageFormatter.formatWithFallback(
                        configManager.getMessage("flySpeedReset") + suffix, "#55FF55"));
                } else {
                    horizontalField.setFloat(settings, finalSpeed);
                    verticalField.setFloat(settings, finalSpeed);
                    movementManager.update(finalTarget.getPacketHandler());
                    ctx.sendMessage(MessageFormatter.formatWithFallback(
                        configManager.getMessage("flySpeedSet", "speed", String.format("%.1f", finalSpeed)) + suffix, "#55FF55"));
                }

                // Notify target if targeting another player
                if (finalIsTargetingOther) {
                    if (finalIsReset) {
                        finalTarget.sendMessage(MessageFormatter.formatWithFallback(
                            configManager.getMessage("flySpeedReset"), "#55FF55"));
                    } else {
                        finalTarget.sendMessage(MessageFormatter.formatWithFallback(
                            configManager.getMessage("flySpeedSet", "speed", String.format("%.1f", finalSpeed)), "#55FF55"));
                    }
                }
            } catch (NoSuchFieldException e) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(
                    "Fly speed control is not available in the current Hytale API.", "#FFAA00"));
            } catch (Exception e) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(
                    "Error setting fly speed: " + e.getMessage(), "#FF5555"));
                if (configManager.isDebugEnabled()) {
                    e.printStackTrace();
                }
            }
        });
    }

    private World findPlayerWorld(PlayerRef player) {
        Universe universe = Universe.get();
        if (universe == null) return null;
        for (var entry : universe.getWorlds().entrySet()) {
            if (entry.getValue().getPlayerRefs().contains(player)) {
                return entry.getValue();
            }
        }
        return null;
    }
}
