package com.eliteessentials.commands.hytale;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.config.PluginConfig;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.services.VanishService;
import com.eliteessentials.util.CommandPermissionUtil;
import com.eliteessentials.util.MessageFormatter;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * Command: /vanish (alias: /v)
 * Toggle vanish mode - makes the player invisible to other players.
 * Admin command only.
 */
public class HytaleVanishCommand extends AbstractPlayerCommand {

    private static final String COMMAND_NAME = "vanish";
    
    private final ConfigManager configManager;
    private final VanishService vanishService;

    public HytaleVanishCommand(ConfigManager configManager, VanishService vanishService) {
        super(COMMAND_NAME, "Toggle vanish mode");
        this.configManager = configManager;
        this.vanishService = vanishService;
        this.addAliases("v");
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                          @Nonnull PlayerRef player, @Nonnull World world) {
        PluginConfig config = configManager.getConfig();
        UUID playerId = player.getUuid();
        
        // Check permission (Admin command)
        if (!CommandPermissionUtil.canExecuteAdmin(ctx, player, Permissions.VANISH, config.vanish.enabled)) {
            return;
        }
        
        // Toggle vanish
        boolean nowVanished = vanishService.toggleVanish(playerId, player.getUsername());
        
        if (nowVanished) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("vanishEnabled"), "#55FF55"));
        } else {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("vanishDisabled"), "#FF5555"));
        }
    }
}
