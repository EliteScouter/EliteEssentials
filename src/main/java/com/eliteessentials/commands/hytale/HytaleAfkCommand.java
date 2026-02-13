package com.eliteessentials.commands.hytale;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.config.PluginConfig;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.services.AfkService;
import com.eliteessentials.util.CommandPermissionUtil;
import com.eliteessentials.util.MessageFormatter;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Command: /afk
 * Toggles AFK (Away From Keyboard) status for the player.
 * 
 * Permission: eliteessentials.command.misc.afk (Everyone)
 */
public class HytaleAfkCommand extends AbstractPlayerCommand {

    private static final String COMMAND_NAME = "afk";
    
    private final AfkService afkService;
    private final ConfigManager configManager;

    public HytaleAfkCommand(AfkService afkService, ConfigManager configManager) {
        super(COMMAND_NAME, "Toggle AFK status");
        this.afkService = afkService;
        this.configManager = configManager;
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                          @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef player, @Nonnull World world) {
        UUID playerId = player.getUuid();
        PluginConfig.AfkConfig afkConfig = configManager.getConfig().afk;
        
        // Permission check (Everyone command)
        if (!CommandPermissionUtil.canExecute(ctx, player, Permissions.AFK, afkConfig.enabled)) {
            return;
        }
        
        boolean nowAfk = afkService.toggleAfk(playerId);
        
        // Send personal feedback (broadcast is handled by AfkService if enabled)
        if (!afkConfig.broadcastAfk) {
            // Only send personal message if broadcast is off (otherwise they'd see the broadcast)
            if (nowAfk) {
                String msg = configManager.getMessage("afkOnSelf");
                ctx.sendMessage(MessageFormatter.formatWithFallback(msg, "#AAAAAA"));
            } else {
                String msg = configManager.getMessage("afkOffSelf");
                ctx.sendMessage(MessageFormatter.formatWithFallback(msg, "#55FF55"));
            }
        }
    }
}
