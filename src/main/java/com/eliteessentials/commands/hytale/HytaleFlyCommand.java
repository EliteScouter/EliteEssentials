package com.eliteessentials.commands.hytale;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.config.PluginConfig;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.permissions.PermissionService;
import com.eliteessentials.services.CooldownService;
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
import java.util.UUID;

/**
 * Command: /fly
 * Toggles flight mode for the player using MovementManager.
 * 
 * Permissions:
 * - eliteessentials.command.misc.fly - Use /fly command
 * - eliteessentials.command.misc.fly.bypass.cooldown - Skip cooldown
 * - eliteessentials.command.misc.fly.cooldown.<seconds> - Set specific cooldown
 */
public class HytaleFlyCommand extends AbstractPlayerCommand {

    private static final String COMMAND_NAME = "fly";
    
    private final ConfigManager configManager;
    private final CooldownService cooldownService;

    public HytaleFlyCommand(ConfigManager configManager, CooldownService cooldownService) {
        super(COMMAND_NAME, "Toggle flight mode");
        this.configManager = configManager;
        this.cooldownService = cooldownService;
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                          @Nonnull PlayerRef player, @Nonnull World world) {
        UUID playerId = player.getUuid();
        PluginConfig.FlyConfig flyConfig = configManager.getConfig().fly;
        
        // Check permission (Admin command)
        if (!CommandPermissionUtil.canExecuteAdmin(ctx, player, Permissions.FLY, flyConfig.enabled)) {
            return;
        }
        
        // Get effective cooldown from permissions
        int effectiveCooldown = PermissionService.get().getCommandCooldown(playerId, COMMAND_NAME, flyConfig.cooldownSeconds);
        
        // Check cooldown if player has one
        if (effectiveCooldown > 0) {
            int cooldownRemaining = cooldownService.getCooldownRemaining(COMMAND_NAME, playerId);
            if (cooldownRemaining > 0) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("onCooldown", "seconds", String.valueOf(cooldownRemaining)), "#FF5555"));
                return;
            }
        }

        // Execute on world thread like simple-fly does
        final int finalEffectiveCooldown = effectiveCooldown;
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
            }
            
            // Set cooldown after successful toggle
            if (finalEffectiveCooldown > 0) {
                cooldownService.setCooldown(COMMAND_NAME, playerId, finalEffectiveCooldown);
            }
        });
    }
}
