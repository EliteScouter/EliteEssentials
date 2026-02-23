package com.eliteessentials.commands.hytale;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.services.NickService;
import com.eliteessentials.util.CommandPermissionUtil;
import com.eliteessentials.util.MessageFormatter;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * Command: /realname <nickname>
 *
 * Looks up the real username of a player who is currently using a nickname.
 * Searches online players only.
 *
 * Permission: eliteessentials.command.misc.realname (Admin)
 */
public class HytaleRealNameCommand extends AbstractPlayerCommand {

    private static final String COMMAND_NAME = "realname";

    private final NickService nickService;
    private final ConfigManager configManager;

    public HytaleRealNameCommand(NickService nickService, ConfigManager configManager) {
        super(COMMAND_NAME, "Look up the real name of a nicknamed player");
        this.nickService = nickService;
        this.configManager = configManager;
        setAllowsExtraArguments(true);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef player,
                           @Nonnull World world) {

        if (!configManager.getConfig().nick.enabled) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("commandDisabled"), "#FF5555"));
            return;
        }

        if (!CommandPermissionUtil.canExecuteAdmin(ctx, player, Permissions.NICK_LOOKUP, true)) {
            return;
        }

        String rawInput = ctx.getInputString().trim();
        String[] parts = rawInput.split("\\s+", 2);
        if (parts.length < 2) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("realnameUsage"), "#FF5555"));
            return;
        }

        String query = parts[1];

        // Search online players: match against both real name and nickname
        for (PlayerRef online : Universe.get().getPlayers()) {
            String realName = online.getUsername();
            String nick = nickService.getNickname(online.getUuid());
            String strippedNick = nick != null ? MessageFormatter.toRawString(nick) : null;

            boolean matchesNick = strippedNick != null && strippedNick.equalsIgnoreCase(query);
            boolean matchesReal = realName.equalsIgnoreCase(query);

            if (matchesNick || matchesReal) {
                if (nick != null) {
                    ctx.sendMessage(MessageFormatter.formatWithFallback(
                            configManager.getMessage("realnameResult",
                                    "nick", strippedNick,
                                    "player", realName), "#55FFFF"));
                } else {
                    ctx.sendMessage(MessageFormatter.formatWithFallback(
                            configManager.getMessage("realnameNoNick", "player", realName), "#AAAAAA"));
                }
                return;
            }
        }

        ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("realnameNotFound", "player", query), "#FF5555"));
    }
}
