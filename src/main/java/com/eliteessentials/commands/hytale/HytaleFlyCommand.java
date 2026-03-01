package com.eliteessentials.commands.hytale;

import com.eliteessentials.api.EconomyAPI;
import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.config.PluginConfig;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.permissions.PermissionService;
import com.eliteessentials.services.CooldownService;
import com.eliteessentials.services.CostService;
import com.eliteessentials.services.FlyService;
import com.eliteessentials.util.CommandPermissionUtil;
import com.eliteessentials.util.MessageFormatter;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.protocol.SavedMovementStates;
import com.hypixel.hytale.protocol.packets.player.SetMovementStates;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
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
    private final CostService costService;
    private final FlyService flyService;

    public HytaleFlyCommand(ConfigManager configManager, CooldownService cooldownService,
                            CostService costService, FlyService flyService) {
        super(COMMAND_NAME, "Toggle flight mode");
        this.configManager = configManager;
        this.cooldownService = cooldownService;
        this.costService = costService;
        this.flyService = flyService;
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

        // Cost per minute: charge upfront and schedule auto-disable (unless player bypasses cost)
        double costPerMinute = flyConfig.costPerMinute;
        int durationSeconds = flyConfig.costPerMinuteDurationSeconds;
        boolean useCostPerMinute = costPerMinute > 0 && EconomyAPI.isEnabled();
        boolean bypassCostPerMinute = costService.canBypassCost(playerId, COMMAND_NAME);
        // Only charge and apply timer when cost-per-minute is on and player does NOT bypass
        boolean applyCostPerMinuteToPlayer = useCostPerMinute && !bypassCostPerMinute;
        if (applyCostPerMinuteToPlayer) {
            if (!costService.chargeIfNeeded(ctx, player, COMMAND_NAME, costPerMinute)) {
                return;
            }
        }

        // Execute on world thread like simple-fly does
        final int finalEffectiveCooldown = effectiveCooldown;
        final boolean applyTimerAndTimedMessage = applyCostPerMinuteToPlayer;
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
                if (applyTimerAndTimedMessage) {
                    String timeStr = durationSeconds == 60 ? "1 minute" : durationSeconds + " seconds";
                    ctx.sendMessage(MessageFormatter.formatWithFallback(
                        configManager.getMessage("flyEnabledTimed", "time", timeStr), "#55FF55"));
                    flyService.scheduleExpiry(playerId, durationSeconds);
                } else {
                    ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("flyEnabled"), "#55FF55"));
                }
            } else {
                flyService.cancelExpiry(playerId);
                ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("flyDisabled"), "#FFAA00"));
                
                // When disabling flight, also stop the player from flying if they're currently in the air
                MovementStatesComponent movementStatesComponent = store.getComponent(ref, MovementStatesComponent.getComponentType());
                if (movementStatesComponent != null) {
                    MovementStates movementStates = movementStatesComponent.getMovementStates();
                    if (movementStates.flying) {
                        movementStates.flying = false;
                        player.getPacketHandler().writeNoCache(new SetMovementStates(new SavedMovementStates(false)));
                    }
                }
            }
            
            // Set cooldown after successful toggle
            if (finalEffectiveCooldown > 0) {
                cooldownService.setCooldown(COMMAND_NAME, playerId, finalEffectiveCooldown);
            }
        });
    }
}
