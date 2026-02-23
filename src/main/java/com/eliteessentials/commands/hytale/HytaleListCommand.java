package com.eliteessentials.commands.hytale;

import com.eliteessentials.EliteEssentials;
import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.permissions.PermissionService;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.services.AfkService;
import com.eliteessentials.services.NickService;
import com.eliteessentials.services.VanishService;
import com.eliteessentials.util.MessageFormatter;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

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
    protected void executeSync(@Nonnull CommandContext ctx) {
        // Permission check - everyone can use
        PermissionService perms = PermissionService.get();
        if (!perms.canUseEveryoneCommand(ctx.sender(), Permissions.LIST, 
                configManager.getConfig().list.enabled)) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("noPermission"), "#FF5555"));
            return;
        }
        
        // Get all online players
        List<PlayerRef> players = Universe.get().getPlayers();
        
        // Check if the command sender can see vanished players
        // Get sender UUID - sender can be PlayerRef or Player
        UUID senderId = null;
        Object sender = ctx.sender();
        if (sender instanceof PlayerRef playerRef) {
            senderId = playerRef.getUuid();
        } else if (sender instanceof Player player) {
            PlayerRef playerRef = player.getPlayerRef();
            if (playerRef != null) {
                senderId = playerRef.getUuid();
            }
        }
        
        boolean canSeeVanished = senderId != null && 
            (perms.isAdmin(senderId) || perms.hasPermission(senderId, Permissions.VANISH));
        
        // Filter out vanished players if the sender can't see them
        VanishService vanishService = EliteEssentials.getInstance().getVanishService();
        List<PlayerRef> visiblePlayers = players;
        if (vanishService != null && !canSeeVanished) {
            visiblePlayers = players.stream()
                .filter(p -> !vanishService.isVanished(p.getUuid()))
                .collect(Collectors.toList());
        }
        
        int playerCount = visiblePlayers.size();
        
        // Get player names sorted alphabetically, with configurable AFK prefix
        AfkService afkService = EliteEssentials.getInstance().getAfkService();
        NickService nickService = EliteEssentials.getInstance().getNickService();
        List<String> playerNames = visiblePlayers.stream()
            .map(p -> {
                String displayName = nickService != null
                        ? nickService.getDisplayName(p.getUuid(), p.getUsername())
                        : p.getUsername();
                if (afkService != null && afkService.isAfk(p.getUuid())) {
                    return configManager.getMessage("afkListPrefix", "player", displayName);
                }
                return displayName;
            })
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .collect(Collectors.toList());
        
        // Send header
        String header = configManager.getMessage("listHeader", 
            "count", String.valueOf(playerCount),
            "max", String.valueOf(configManager.getConfig().list.maxPlayers));
        ctx.sendMessage(MessageFormatter.formatWithFallback(header, "#55FF55"));
        
        // Send player list
        if (playerCount == 0) {
            String noPlayers = configManager.getMessage("listNoPlayers");
            ctx.sendMessage(MessageFormatter.formatWithFallback(noPlayers, "#AAAAAA"));
        } else {
            // Join names with comma and space
            String playerList = String.join(", ", playerNames);
            String listMessage = configManager.getMessage("listPlayers", "players", playerList);
            ctx.sendMessage(MessageFormatter.formatWithFallback(listMessage, "#FFFFFF"));
        }
    }
}
