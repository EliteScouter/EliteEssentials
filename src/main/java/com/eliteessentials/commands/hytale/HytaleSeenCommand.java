package com.eliteessentials.commands.hytale;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.model.PlayerFile;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.services.PlayerService;
import com.eliteessentials.util.CommandPermissionUtil;
import com.eliteessentials.util.MessageFormatter;
import com.eliteessentials.util.PlayerSuggestionProvider;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Optional;
import javax.annotation.Nonnull;
import com.eliteessentials.util.CommandSpyUtil;

/**
 * Command: /seen <player>
 * Check when a player was last online.
 * 
 * Usage: /seen <player>
 * Example: /seen Steve
 * 
 * Shows relative time (e.g., "2 days, 3 hours ago") to avoid timezone issues.
 * If the player is currently online, shows "online now".
 */
public class HytaleSeenCommand extends AbstractPlayerCommand {

    private static final String COMMAND_NAME = "seen";
    
    private final RequiredArg<String> targetArg;
    private final ConfigManager configManager;
    private final PlayerService playerService;

    public HytaleSeenCommand(ConfigManager configManager, PlayerService playerService) {
        super(COMMAND_NAME, "Check when a player was last online");
        this.configManager = configManager;
        this.playerService = playerService;
        
        this.targetArg = withRequiredArg("player", "Player name to look up", ArgTypes.STRING)
            .suggest(PlayerSuggestionProvider.INSTANCE);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                          @Nonnull PlayerRef player, @Nonnull World world) {
        CommandSpyUtil.notify(ctx);
        // Check permission (Everyone command)
        if (!CommandPermissionUtil.canExecute(ctx, player, Permissions.SEEN, true)) {
            return;
        }
        
        String targetName = ctx.get(targetArg);
        
        // Check if player is currently online
        PlayerRef onlinePlayer = PlayerSuggestionProvider.findPlayer(targetName);
        if (onlinePlayer != null) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("seenOnline", "player", onlinePlayer.getUsername()), "#55FF55"));
                return;
        }
        
        // Look up in player cache
        Optional<PlayerFile> dataOpt = playerService.getPlayerByName(targetName);
        
        if (dataOpt.isEmpty()) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("seenNeverJoined", "player", targetName), "#FF5555"));
            return;
        }
        
        PlayerFile data = dataOpt.get();
        long lastSeen = data.getLastSeen();
        String relativeTime = formatRelativeTime(lastSeen);
        
        ctx.sendMessage(MessageFormatter.formatWithFallback(
            configManager.getMessage("seenLastSeen", 
                "player", data.getName(),
                "time", relativeTime), "#AAAAAA"));
    }
    
    /**
     * Format a timestamp as relative time (e.g., "2 days, 3 hours ago").
     */
    private String formatRelativeTime(long timestamp) {
        long now = System.currentTimeMillis();
        long diff = now - timestamp;
        
        if (diff < 0) {
            return "just now";
        }
        
        long seconds = diff / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        
        if (days > 0) {
            long remainingHours = hours % 24;
            if (remainingHours > 0) {
                return days + " day" + (days != 1 ? "s" : "") + ", " + 
                       remainingHours + " hour" + (remainingHours != 1 ? "s" : "") + " ago";
            }
            return days + " day" + (days != 1 ? "s" : "") + " ago";
        }
        
        if (hours > 0) {
            long remainingMinutes = minutes % 60;
            if (remainingMinutes > 0) {
                return hours + " hour" + (hours != 1 ? "s" : "") + ", " + 
                       remainingMinutes + " minute" + (remainingMinutes != 1 ? "s" : "") + " ago";
            }
            return hours + " hour" + (hours != 1 ? "s" : "") + " ago";
        }
        
        if (minutes > 0) {
            return minutes + " minute" + (minutes != 1 ? "s" : "") + " ago";
        }
        
        return "just now";
    }
}
