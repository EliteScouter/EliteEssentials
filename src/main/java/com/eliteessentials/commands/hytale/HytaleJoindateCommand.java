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
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Optional;
import javax.annotation.Nonnull;

/**
 * Command: /joindate [player]
 * View when a player first joined the server.
 * 
 * Usage:
 *   /joindate        - Shows your own join date
 *   /joindate <name> - Shows another player's join date (requires .others permission)
 */
public class HytaleJoindateCommand extends AbstractPlayerCommand {

    private static final String COMMAND_NAME = "joindate";
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MMM dd, yyyy 'at' HH:mm");

    private final ConfigManager configManager;
    private final PlayerService playerService;

    public HytaleJoindateCommand(ConfigManager configManager, PlayerService playerService) {
        super(COMMAND_NAME, "View when a player first joined");
        this.configManager = configManager;
        this.playerService = playerService;

        addUsageVariant(new JoindateOtherCommand(configManager, playerService));
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                          @Nonnull PlayerRef player, @Nonnull World world) {
        if (!CommandPermissionUtil.canExecute(ctx, player, Permissions.JOINDATE, configManager.getConfig().joindate.enabled)) {
            return;
        }

        Optional<PlayerFile> dataOpt = playerService.getPlayer(player.getUuid());
        if (dataOpt.isEmpty()) {
            return;
        }

        String date = formatDate(dataOpt.get().getFirstJoin());
        ctx.sendMessage(MessageFormatter.formatWithFallback(
            configManager.getMessage("joindateSelf", "date", date), "#AAAAAA"));
    }

    static String formatDate(long timestamp) {
        synchronized (DATE_FORMAT) {
            return DATE_FORMAT.format(new Date(timestamp));
        }
    }

    /**
     * /joindate <player> - View another player's join date.
     * Requires eliteessentials.command.misc.joindate.others permission.
     */
    private static class JoindateOtherCommand extends AbstractPlayerCommand {
        private final ConfigManager configManager;
        private final PlayerService playerService;
        private final RequiredArg<String> targetArg;

        JoindateOtherCommand(ConfigManager configManager, PlayerService playerService) {
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
            if (!CommandPermissionUtil.canExecute(ctx, player, Permissions.JOINDATE_OTHERS, configManager.getConfig().joindate.enabled)) {
                return;
            }

            String targetName = ctx.get(targetArg);

            Optional<PlayerFile> dataOpt = playerService.getPlayerByName(targetName);
            if (dataOpt.isEmpty()) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("joindateNeverJoined", "player", targetName), "#FF5555"));
                return;
            }

            PlayerFile data = dataOpt.get();
            String date = formatDate(data.getFirstJoin());
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("joindateOther", "player", data.getName(), "date", date), "#AAAAAA"));
        }
    }
}
