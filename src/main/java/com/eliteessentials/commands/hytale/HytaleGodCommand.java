package com.eliteessentials.commands.hytale;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.config.PluginConfig;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.permissions.PermissionService;
import com.eliteessentials.services.CooldownService;
import com.eliteessentials.services.GodService;
import com.eliteessentials.util.CommandPermissionUtil;
import com.eliteessentials.util.MessageFormatter;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.Invulnerable;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Command: /god
 * Toggles invulnerability (god mode) for the player.
 * 
 * Permissions:
 * - eliteessentials.command.misc.god - Use /god command
 * - eliteessentials.command.misc.god.bypass.cooldown - Skip cooldown
 * - eliteessentials.command.misc.god.cooldown.<seconds> - Set specific cooldown
 */
public class HytaleGodCommand extends AbstractPlayerCommand {

    private static final String COMMAND_NAME = "god";
    
    private final GodService godService;
    private final ConfigManager configManager;
    private final CooldownService cooldownService;

    public HytaleGodCommand(GodService godService, ConfigManager configManager, CooldownService cooldownService) {
        super(COMMAND_NAME, "Toggle god mode (invincibility)");
        this.godService = godService;
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
        PluginConfig.GodConfig godConfig = configManager.getConfig().god;
        
        // Check permission (Admin command)
        if (!CommandPermissionUtil.canExecuteAdmin(ctx, player, Permissions.GOD, 
                godConfig.enabled)) {
            return;
        }
        
        // Get effective cooldown from permissions
        int effectiveCooldown = PermissionService.get().getCommandCooldown(playerId, COMMAND_NAME, godConfig.cooldownSeconds);
        
        // Check cooldown if player has one
        if (effectiveCooldown > 0) {
            int cooldownRemaining = cooldownService.getCooldownRemaining(COMMAND_NAME, playerId);
            if (cooldownRemaining > 0) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("onCooldown", "seconds", String.valueOf(cooldownRemaining)), "#FF5555"));
                return;
            }
        }

        // Toggle god mode state
        boolean nowEnabled = godService.toggleGodMode(playerId);

        if (nowEnabled) {
            // Enable invulnerability
            store.putComponent(ref, Invulnerable.getComponentType(), Invulnerable.INSTANCE);
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("godEnabled"), "#55FF55"));
        } else {
            // Disable invulnerability
            store.removeComponent(ref, Invulnerable.getComponentType());
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("godDisabled"), "#FFAA00"));
        }
        
        // Set cooldown based on effective cooldown
        if (effectiveCooldown > 0) {
            cooldownService.setCooldown(COMMAND_NAME, playerId, effectiveCooldown);
        }
    }
}
