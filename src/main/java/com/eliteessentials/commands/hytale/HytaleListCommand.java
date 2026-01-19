package com.eliteessentials.commands.hytale;

import com.eliteessentials.EliteEssentials;
import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.permissions.PermissionService;
import com.eliteessentials.permissions.Permissions;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Command: /list
 * Displays a list of all online players.
 * 
 * Aliases: /online, /who
 * 
 * Usage: /list
 * Permission: eliteessentials.command.misc.list (Everyone)
 */
public class HytaleListCommand extends CommandBase {
    
    private final ConfigManager configManager;
    
    public HytaleListCommand(ConfigManager configManager) {
        super("list", "Show all online players");
        this.configManager = configManager;
        
        // Add aliases
        addAliases("online", "who");
    }
    
    @Override
    protected boolean canGeneratePermission() {
        return false;
    }
    
    @Override
    protected void executeSync(CommandContext ctx) {
        // Permission check - everyone can use
        PermissionService perms = PermissionService.get();
        if (!perms.canUseEveryoneCommand(ctx.sender(), Permissions.LIST, 
                configManager.getConfig().list.enabled)) {
            ctx.sendMessage(Message.raw(configManager.getMessage("noPermission")).color("#FF5555"));
            return;
        }
        
        // Get all online players
        List<PlayerRef> players = Universe.get().getPlayers();
        int playerCount = players.size();
        
        // Get player names sorted alphabetically
        List<String> playerNames = players.stream()
            .map(PlayerRef::getUsername)
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .collect(Collectors.toList());
        
        // Send header
        String header = configManager.getMessage("listHeader", 
            "count", String.valueOf(playerCount),
            "max", String.valueOf(configManager.getConfig().list.maxPlayers));
        ctx.sendMessage(Message.raw(header).color("#55FF55"));
        
        // Send player list
        if (playerCount == 0) {
            String noPlayers = configManager.getMessage("listNoPlayers");
            ctx.sendMessage(Message.raw(noPlayers).color("#AAAAAA"));
        } else {
            // Join names with comma and space
            String playerList = String.join(", ", playerNames);
            String listMessage = configManager.getMessage("listPlayers", "players", playerList);
            ctx.sendMessage(Message.raw(listMessage).color("#FFFFFF"));
        }
    }
}
