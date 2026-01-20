package com.eliteessentials.commands.hytale;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.storage.DiscordStorage;
import com.eliteessentials.util.CommandPermissionUtil;
import com.eliteessentials.util.MessageFormatter;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.List;

import javax.annotation.Nonnull;

/**
 * /discord - Display the server's discord information.
 * 
 * Supports color codes (&0-f, &l, &o, &r) and clickable URLs.
 * 
 * Usage: /discord
 * Permission: eliteessentials.command.misc.discord (Everyone)
 */
public class HytaleDiscordCommand extends AbstractPlayerCommand {
    
    private final ConfigManager configManager;
    private final DiscordStorage discordStorage;
    
    public HytaleDiscordCommand(ConfigManager configManager, DiscordStorage discordStorage) {
        super("discord", "Display the server's discord information");
        this.configManager = configManager;
        this.discordStorage = discordStorage;
    }
    
    @Override
    protected boolean canGeneratePermission() {
        return false;
    }
    
    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store, 
                          @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef player, @Nonnull World world) {
        // Permission check - everyone can use
        if (!CommandPermissionUtil.canExecute(ctx, player, Permissions.DISCORD, 
                configManager.getConfig().discord.enabled)) {
            return;
        }
        
        // Get discord lines
        List<String> discordLines = discordStorage.getDiscordLines();
        if (discordLines.isEmpty()) {
            String message = configManager.getMessage("discordEmpty");
            player.sendMessage(Message.raw(message).color("#FF5555"));
            return;
        }
        
        // Send each line with formatting (URLs become clickable)
        for (String line : discordLines) {
            // Skip completely empty lines to avoid excessive spacing
            if (line.trim().isEmpty()) {
                continue;
            }
            player.sendMessage(MessageFormatter.format(line));
        }
    }
}
