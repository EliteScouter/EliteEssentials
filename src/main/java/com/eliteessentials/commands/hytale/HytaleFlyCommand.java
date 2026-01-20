package com.eliteessentials.commands.hytale;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.util.CommandPermissionUtil;
import com.eliteessentials.util.MessageFormatter;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

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
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                          @Nonnull PlayerRef player, @Nonnull World world) {
        // Check permission (Admin command)
        if (!CommandPermissionUtil.canExecuteAdmin(ctx, player, Permissions.FLY, 
                configManager.getConfig().fly.enabled)) {
            return;
        }

        // Execute on world thread like simple-fly does
        world.execute(() -> {
            // Get movement manager
            MovementManager movementManager = store.getComponent(ref, MovementManager.getComponentType());
            if (movementManager == null) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("flyFailed"), "#FF5555"));
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
                ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("flyEnabled"), "#55FF55"));
            } else {
                ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("flyDisabled"), "#FFAA00"));
                // NOTE: Disabling flight while airborne will leave you floating
                // This is a Hytale API limitation - the active flying state is not exposed
                // Workaround: Land before disabling fly, or change gamemode to reset state
            }
        });
    }
}
