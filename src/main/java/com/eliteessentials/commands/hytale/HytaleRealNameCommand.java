package com.eliteessentials.commands.hytale;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.permissions.PermissionService;
import com.eliteessentials.services.NickService;
import com.eliteessentials.util.MessageFormatter;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * Command: /realname <nickname>
 *
 * Looks up the real username of a player who is currently using a nickname.
 * Searches online players only.
 *
 * Permission: eliteessentials.command.misc.nickname.lookup (Admin)
 */
public class HytaleRealNameCommand extends CommandBase {

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
    protected void executeSync(@Nonnull CommandContext ctx) {
        // Resolve player sender
        Ref<EntityStore> ref = ctx.senderAsPlayerRef();
        if (ref == null || !ref.isValid()) {
            ctx.sendMessage(Message.raw("This command can only be used by players.").color("#FF5555"));
            return;
        }
        Store<EntityStore> store = ref.getStore();
        PlayerRef player = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null) {
            ctx.sendMessage(Message.raw("Could not resolve player.").color("#FF5555"));
            return;
        }

        if (!configManager.getConfig().nick.enabled) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("commandDisabled"), "#FF5555"));
            return;
        }

        // Permission: misc.nickname.lookup (admin only in simple mode)
        if (!hasLookupPermission(player.getUuid())) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("noPermission"), "#FF5555"));
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

    private boolean hasLookupPermission(java.util.UUID playerId) {
        if (!configManager.getConfig().advancedPermissions) {
            return PermissionService.get().isAdmin(playerId);
        }
        return PermissionService.get().hasPermission(playerId, Permissions.NICK_LOOKUP)
                || PermissionService.get().isAdmin(playerId);
    }
}
