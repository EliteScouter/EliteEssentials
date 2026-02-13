package com.eliteessentials.commands.hytale;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.model.PlayerFile;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.services.PlayerService;
import com.eliteessentials.util.CommandPermissionUtil;
import com.eliteessentials.util.MessageFormatter;
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
import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * Command: /playtime [player]
 * View total play time on the server.
 * 
 * Usage:
 *   /playtime        - Shows your own playtime (includes current session)
 *   /playtime <name> - Shows another player's playtime (requires .others permission)
 */
public class HytalePlaytimeCommand extends AbstractPlayerCommand {

    private static final String COMMAND_NAME = "playtime";

    private final ConfigManager configManager;
    private final PlayerService playerService;

    public HytalePlaytimeCommand(ConfigManager configManager, PlayerService playerService) {
        super(COMMAND_NAME, "View total play time on the server");
        this.configManager = configManager;
        this.playerService = playerService;

        addUsageVariant(new PlaytimeOtherCommand(configManager, playerService));
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                          @Nonnull PlayerRef player, @Nonnull World world) {
        if (!CommandPermissionUtil.canExecute(ctx, player, Permissions.PLAYTIME, configManager.getConfig().playtime.enabled)) {
            return;
        }

        Optional<PlayerFile> dataOpt = playerService.getPlayer(player.getUuid());
        if (dataOpt.isEmpty()) {
            return;
        }

        // Include current session for accurate live total
        long totalSeconds = dataOpt.get().getPlayTime() + playerService.getCurrentSessionSeconds(player.getUuid());
        String time = PlayerService.formatPlayTime(totalSeconds);
        ctx.sendMessage(MessageFormatter.formatWithFallback(
            configManager.getMessage("playtimeSelf", "time", time), "#AAAAAA"));
    }

    /**
     * /playtime <player> - View another player's playtime.
     * Requires eliteessentials.command.misc.playtime.others permission.
     */
    private static class PlaytimeOtherCommand extends AbstractPlayerCommand {
        private final ConfigManager configManager;
        private final PlayerService playerService;
        private final RequiredArg<String> targetArg;

        PlaytimeOtherCommand(ConfigManager configManager, PlayerService playerService) {
            super(COMMAND_NAME);
            this.configManager = configManager;
            this.playerService = playerService;
            this.targetArg = withRequiredArg("player", "Player name to look up", ArgTypes.STRING);
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                              @Nonnull PlayerRef player, @Nonnull World world) {
            if (!CommandPermissionUtil.canExecute(ctx, player, Permissions.PLAYTIME_OTHERS, configManager.getConfig().playtime.enabled)) {
                return;
            }

            String targetName = ctx.get(targetArg);

            Optional<PlayerFile> dataOpt = playerService.getPlayerByName(targetName);
            if (dataOpt.isEmpty()) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("playtimeNeverJoined", "player", targetName), "#FF5555"));
                return;
            }

            PlayerFile data = dataOpt.get();
            long totalSeconds = data.getPlayTime();

            // If target is online, include their current session
            UUID targetId = data.getUuid();
            for (PlayerRef onlinePlayer : Universe.get().getPlayers()) {
                if (onlinePlayer.getUuid().equals(targetId)) {
                    totalSeconds += playerService.getCurrentSessionSeconds(targetId);
                    break;
                }
            }

            String time = PlayerService.formatPlayTime(totalSeconds);
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("playtimeOther", "player", data.getName(), "time", time), "#AAAAAA"));
        }
    }
}
