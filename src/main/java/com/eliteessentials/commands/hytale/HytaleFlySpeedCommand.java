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
 * - /flyspeed reset - Reset to default fly speed (10)
 * - /flyspeed 10 - Set fly speed to 10x (default)
 * - /flyspeed 50 - Set fly speed to 50x (very fast!)
 * - /flyspeed 100 - Set fly speed to 100x (insane!)
 * 
 * Speed Range: 10 to 100, or "reset" to restore default (10)
 * 
 * Permissions:
 * - Simple Mode: Admin only
 * - Advanced Mode: eliteessentials.command.misc.flyspeed
 */
public class HytaleFlySpeedCommand extends AbstractPlayerCommand {

    private final ConfigManager configManager;
    private final RequiredArg<String> speedArg;

    public HytaleFlySpeedCommand(ConfigManager configManager) {
        super("flyspeed", "Set your fly speed (10-100 or 'reset')");
        this.configManager = configManager;
        this.speedArg = withRequiredArg("speed", "Fly speed (10-100 or 'reset')", SimpleStringArg.FLY_SPEED);
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
        
        // Check for reset command
        boolean isReset = speedStr.equalsIgnoreCase("reset") || speedStr.equalsIgnoreCase("default");
        float speed = 0;
        
        if (!isReset) {
            try {
                speed = Float.parseFloat(speedStr);
            } catch (NumberFormatException e) {
                ctx.sendMessage(Message.raw(configManager.getMessage("flySpeedInvalid")).color("#FF5555"));
                return;
            }

            // Validate speed range (10 to 100)
            if (speed < 10.0f || speed > 100.0f) {
                ctx.sendMessage(Message.raw(configManager.getMessage("flySpeedOutOfRange")).color("#FF5555"));
                return;
            }
        }

        // Get movement manager
        MovementManager movementManager = store.getComponent(ref, MovementManager.getComponentType());
        if (movementManager == null) {
            ctx.sendMessage(Message.raw(configManager.getMessage("flyFailed")).color("#FF5555"));
            return;
        }

        // Set fly speed in movement settings
        var settings = movementManager.getSettings();
        
        try {
            // Set both horizontal and vertical fly speed
            // These are the actual fields used by the Hytale API
            java.lang.reflect.Field horizontalField = settings.getClass().getField("horizontalFlySpeed");
            java.lang.reflect.Field verticalField = settings.getClass().getField("verticalFlySpeed");
            
            if (isReset) {
                // Reset to default speed of 10
                horizontalField.setFloat(settings, 10.0f);
                verticalField.setFloat(settings, 10.0f);
                movementManager.update(player.getPacketHandler());
                
                ctx.sendMessage(Message.raw(configManager.getMessage("flySpeedReset")).color("#55FF55"));
                if (configManager.isDebugEnabled()) {
                    ctx.sendMessage(Message.raw("Speed reset to default: 10.0").color("#AAAAAA"));
                }
            } else {
                horizontalField.setFloat(settings, speed);
                verticalField.setFloat(settings, speed);
                movementManager.update(player.getPacketHandler());
                
                ctx.sendMessage(Message.raw(configManager.getMessage("flySpeedSet", "speed", String.format("%.1f", speed))).color("#55FF55"));
            }
        } catch (NoSuchFieldException e) {
            ctx.sendMessage(Message.raw("Fly speed control is not available in the current Hytale API.").color("#FFAA00"));
            if (configManager.isDebugEnabled()) {
                ctx.sendMessage(Message.raw("Error: " + e.getMessage()).color("#AAAAAA"));
            }
        } catch (Exception e) {
            ctx.sendMessage(Message.raw("Error setting fly speed: " + e.getMessage()).color("#FF5555"));
            if (configManager.isDebugEnabled()) {
                e.printStackTrace();
            }
        }
    }
}
