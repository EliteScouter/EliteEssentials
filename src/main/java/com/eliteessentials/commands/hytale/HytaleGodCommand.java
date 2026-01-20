package com.eliteessentials.commands.hytale;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.permissions.Permissions;
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
 */
public class HytaleGodCommand extends AbstractPlayerCommand {

    private final GodService godService;
    private final ConfigManager configManager;

    public HytaleGodCommand(GodService godService, ConfigManager configManager) {
        super("god", "Toggle god mode (invincibility)");
        this.godService = godService;
        this.configManager = configManager;
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                          @Nonnull PlayerRef player, @Nonnull World world) {
        UUID playerId = player.getUuid();
        
        // Check permission (Admin command)
        if (!CommandPermissionUtil.canExecuteAdmin(ctx, player, Permissions.GOD, 
                configManager.getConfig().god.enabled)) {
            return;
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
    }
}
