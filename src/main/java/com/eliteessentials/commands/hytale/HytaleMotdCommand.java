package com.eliteessentials.commands.hytale;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.storage.MotdStorage;
import com.eliteessentials.util.CommandPermissionUtil;
import com.eliteessentials.util.MessageFormatter;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.List;

/**
 * /motd - Display the Message of the Day.
 * 
 * Supports color codes (&0-f, &l, &o, &r) and clickable URLs.
 * Placeholders: {player}, {server}, {world}, {playercount}
 * 
 * Usage: /motd
 * Permission: eliteessentials.command.misc.motd (Everyone)
 */
public class HytaleMotdCommand extends AbstractPlayerCommand {
    
    private final ConfigManager configManager;
    private final MotdStorage motdStorage;
    
    public HytaleMotdCommand(ConfigManager configManager, MotdStorage motdStorage) {
        super("motd", "Display the Message of the Day");
        this.configManager = configManager;
        this.motdStorage = motdStorage;
    }
    
    @Override
    protected boolean canGeneratePermission() {
        return false;
    }
    
    @Override
    protected void execute(CommandContext ctx, Store<EntityStore> store, 
                          Ref<EntityStore> ref, PlayerRef player, World world) {
        // Permission check - everyone can use
        if (!CommandPermissionUtil.canExecute(ctx, player, Permissions.MOTD, 
                configManager.getConfig().motd.enabled)) {
            return;
        }
        
        // Get MOTD lines
        List<String> motdLines = motdStorage.getMotdLines();
        if (motdLines.isEmpty()) {
            String message = configManager.getMessage("motdEmpty");
            player.sendMessage(Message.raw(message).color("#FF5555"));
            return;
        }
        
        // Replace placeholders
        String playerName = player.getUsername();
        String serverName = configManager.getConfig().motd.serverName;
        String worldName = world.getName();
        int playerCount = Universe.get().getPlayers().size();
        
        // Send each line with formatting
        for (String line : motdLines) {
            // Skip completely empty lines to avoid excessive spacing
            if (line.trim().isEmpty()) {
                continue;
            }
            
            String processedLine = line
                    .replace("{player}", playerName)
                    .replace("{server}", serverName)
                    .replace("{world}", worldName)
                    .replace("{playercount}", String.valueOf(playerCount));
            
            player.sendMessage(MessageFormatter.format(processedLine));
        }
    }
}
