package com.eliteessentials.commands.hytale;

import com.eliteessentials.commands.args.SimpleStringArg;
import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.util.CommandPermissionUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Command: /flyspeed <speed>
 * Sets the player's fly speed multiplier.
 * 
 * Usage:
 * - /flyspeed 0 - Reset to default fly speed
 * - /flyspeed 2 - Set fly speed to 2x
 * - /flyspeed 5 - Set fly speed to 5x (fast!)
 * 
 * Speed Range: 0 (default) to 10 (maximum)
 * 
 * Permissions:
 * - Simple Mode: Admin only
 * - Advanced Mode: eliteessentials.command.misc.flyspeed
 */
public class HytaleFlySpeedCommand extends AbstractPlayerCommand {

    private final ConfigManager configManager;
    private final RequiredArg<String> speedArg;

    public HytaleFlySpeedCommand(ConfigManager configManager) {
        super("flyspeed", "Set your fly speed (0-10, 0 = default)");
        this.configManager = configManager;
        this.speedArg = withRequiredArg("speed", "Fly speed multiplier (0 = default)", SimpleStringArg.SIMPLE_STRING);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref,
                          PlayerRef player, World world) {
        // Check permission (Admin command)
        if (!CommandPermissionUtil.canExecuteAdmin(ctx, player, Permissions.FLYSPEED, 
                configManager.getConfig().fly.enabled)) {
            return;
        }

        // Parse speed argument
        String speedStr = ctx.get(speedArg);
        float speed;
        try {
            speed = Float.parseFloat(speedStr);
        } catch (NumberFormatException e) {
            ctx.sendMessage(Message.raw(configManager.getMessage("flySpeedInvalid")).color("#FF5555"));
            return;
        }

        // Validate speed range (0 to 10)
        if (speed < 0 || speed > 10) {
            ctx.sendMessage(Message.raw(configManager.getMessage("flySpeedOutOfRange")).color("#FF5555"));
            return;
        }

        // Get movement manager
        MovementManager movementManager = store.getComponent(ref, MovementManager.getComponentType());
        if (movementManager == null) {
            ctx.sendMessage(Message.raw(configManager.getMessage("flyFailed")).color("#FF5555"));
            return;
        }

        // Set fly speed in movement settings
        var settings = movementManager.getSettings();
        settings.flySpeed = speed;

        // Update the client
        movementManager.update(player.getPacketHandler());

        // Send message
        if (speed == 0) {
            ctx.sendMessage(Message.raw(configManager.getMessage("flySpeedReset")).color("#55FF55"));
        } else {
            ctx.sendMessage(Message.raw(configManager.getMessage("flySpeedSet", "speed", String.format("%.1f", speed))).color("#55FF55"));
        }
    }
}
