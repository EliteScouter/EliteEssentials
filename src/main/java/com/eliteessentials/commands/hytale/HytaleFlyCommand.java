package com.eliteessentials.commands.hytale;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.util.CommandPermissionUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Command: /fly
 * Toggles flight mode for the player using MovementManager.
 * 
 * Permissions:
 * - eliteessentials.command.misc.fly - Use /fly command
 */
public class HytaleFlyCommand extends AbstractPlayerCommand {

    private final ConfigManager configManager;

    public HytaleFlyCommand(ConfigManager configManager) {
        super("fly", "Toggle flight mode");
        this.configManager = configManager;
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref,
                          PlayerRef player, World world) {
        // Check permission
        if (!CommandPermissionUtil.canExecute(ctx, player, Permissions.FLY, 
                configManager.getConfig().fly.enabled)) {
            return;
        }

        // Get movement manager
        MovementManager movementManager = store.getComponent(ref, MovementManager.getComponentType());
        if (movementManager == null) {
            ctx.sendMessage(Message.raw(configManager.getMessage("flyFailed")).color("#FF5555"));
            return;
        }

        // Toggle canFly in movement settings
        var settings = movementManager.getSettings();
        boolean newState = !settings.canFly;
        settings.canFly = newState;

        // Update the client
        movementManager.update(player.getPacketHandler());

        // Send message
        if (newState) {
            ctx.sendMessage(Message.raw(configManager.getMessage("flyEnabled")).color("#55FF55"));
        } else {
            ctx.sendMessage(Message.raw(configManager.getMessage("flyDisabled")).color("#FFAA00"));
        }
    }
}
