package com.eliteessentials.commands.hytale;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.permissions.PermissionService;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.services.NickService;
import com.eliteessentials.services.TabListService;
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

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Command: /nick [player] [nickname|off]
 *
 * Usage:
 *   /nick <nickname>          - Set your own nickname (admin only in simple mode)
 *   /nick off                 - Clear your own nickname
 *   /nick <player> <nickname> - Set another player's nickname (requires nick.others)
 *   /nick <player> off        - Clear another player's nickname (requires nick.others)
 *
 * Permissions:
 *   eliteessentials.command.misc.nick            - Set own nickname (admin only in simple mode)
 *   eliteessentials.command.misc.nick.color      - Use color codes in nickname
 *   eliteessentials.command.misc.nickname.others - Set/clear other players' nicknames
 */
public class HytaleNickCommand extends AbstractPlayerCommand {

    private static final String COMMAND_NAME = "nick";

    private final NickService nickService;
    private final ConfigManager configManager;
    private TabListService tabListService;

    public HytaleNickCommand(NickService nickService, ConfigManager configManager) {
        super(COMMAND_NAME, "Set your display nickname");
        this.nickService = nickService;
        this.configManager = configManager;
        setAllowsExtraArguments(true);
    }

    public void setTabListService(TabListService tabListService) {
        this.tabListService = tabListService;
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

        UUID senderId = player.getUuid();
        String rawInput = ctx.getInputString().trim();
        // rawInput = "nick ..." — split off the command name
        String[] parts = rawInput.split("\\s+", 3);
        // parts[0] = "nick", parts[1] = first arg, parts[2] = rest

        // /nick (no args) — check permission first, then show help
        if (parts.length < 2) {
            if (!PermissionService.get().canUseAdminCommand(senderId, Permissions.NICK,
                    configManager.getConfig().nick.enabled)) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(
                        configManager.getMessage("noPermission"), "#FF5555"));
                return;
            }
            sendUsage(ctx);
            return;
        }

        String firstArg = parts[1];

        // Determine if targeting another player.
        // Only treat as "other" if the first arg matches an online player who is NOT the sender.
        PlayerRef targetRef = findOnlinePlayer(firstArg);
        boolean firstArgIsPlayer = targetRef != null && !targetRef.getUuid().equals(senderId);

        if (firstArgIsPlayer) {
            // Targeting another player — requires misc.nickname.others
            if (!hasOthersPermission(senderId)) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(
                        configManager.getMessage("noPermission"), "#FF5555"));
                return;
            }
            if (parts.length < 3) {
                sendUsage(ctx);
                return;
            }
            handleSetNick(ctx, senderId, targetRef.getUuid(), targetRef.getUsername(), parts[2], true);
        } else {
            // Self-nick — admin only in simple mode
            if (!PermissionService.get().canUseAdminCommand(senderId, Permissions.NICK,
                    configManager.getConfig().nick.enabled)) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(
                        configManager.getMessage("noPermission"), "#FF5555"));
                return;
            }
            // Reconstruct the nick arg (could be multi-word if parts[2] exists)
            String nickArg = parts.length >= 3 ? parts[1] + " " + parts[2] : parts[1];
            handleSetNick(ctx, senderId, senderId, player.getUsername(), nickArg, false);
        }
    }

    private void handleSetNick(CommandContext ctx, UUID senderId, UUID targetId,
                                String targetRealName, String nickArg, boolean isOther) {

        boolean clearing = nickArg.equalsIgnoreCase("off") || nickArg.equalsIgnoreCase("reset");

        if (!clearing) {
            // Strip color codes if sender doesn't have color permission
            if (!hasColorPermission(senderId)) {
                nickArg = MessageFormatter.toRawString(nickArg);
            }
        }

        NickService.SetNickResult result = clearing
                ? NickService.SetNickResult.CLEARED
                : nickService.setNick(targetId, nickArg);

        if (clearing) {
            nickService.clearNick(targetId);
        }

        switch (result) {
            case SET -> {
                if (isOther) {
                    ctx.sendMessage(MessageFormatter.formatWithFallback(
                            configManager.getMessage("nickSetOther",
                                    "player", targetRealName, "nick", nickArg), "#55FF55"));
                    // Notify target if online
                    PlayerRef targetRef = findOnlinePlayer(targetRealName);
                    if (targetRef != null) {
                        targetRef.sendMessage(MessageFormatter.formatWithFallback(
                                configManager.getMessage("nickSetNotify", "nick", nickArg), "#55FF55"));
                    }
                } else {
                    ctx.sendMessage(MessageFormatter.formatWithFallback(
                            configManager.getMessage("nickSet", "nick", nickArg), "#55FF55"));
                }
                refreshTabList(targetId);
            }
            case CLEARED -> {
                if (isOther) {
                    ctx.sendMessage(MessageFormatter.formatWithFallback(
                            configManager.getMessage("nickClearedOther", "player", targetRealName), "#55FF55"));
                    PlayerRef targetRef = findOnlinePlayer(targetRealName);
                    if (targetRef != null) {
                        targetRef.sendMessage(MessageFormatter.formatWithFallback(
                                configManager.getMessage("nickCleared"), "#55FF55"));
                    }
                } else {
                    ctx.sendMessage(MessageFormatter.formatWithFallback(
                            configManager.getMessage("nickCleared"), "#55FF55"));
                }
                refreshTabList(targetId);
            }
            case TOO_LONG -> ctx.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("nickTooLong",
                            "max", String.valueOf(NickService.MAX_NICK_LENGTH)), "#FF5555"));
            case INVALID -> ctx.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("nickInvalid"), "#FF5555"));
            case PLAYER_NOT_FOUND -> ctx.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("playerNotFound"), "#FF5555"));
        }
    }

    private void sendUsage(CommandContext ctx) {
        ctx.sendMessage(Message.raw("=== Nick Commands ===").color("#55FFFF"));
        ctx.sendMessage(Message.join(
            Message.raw("/nick <nickname>").color("#55FF55"),
            Message.raw(" - Set your display nickname").color("#777777")
        ));
        ctx.sendMessage(Message.join(
            Message.raw("/nick off").color("#55FF55"),
            Message.raw(" - Clear your nickname").color("#777777")
        ));
        ctx.sendMessage(Message.join(
            Message.raw("/nick <player> <nickname>").color("#55FF55"),
            Message.raw(" - Set another player's nickname").color("#777777")
        ));
        ctx.sendMessage(Message.join(
            Message.raw("/nick <player> off").color("#55FF55"),
            Message.raw(" - Clear another player's nickname").color("#777777")
        ));
        ctx.sendMessage(Message.join(
            Message.raw("/realname <name>").color("#55FF55"),
            Message.raw(" - Look up real name behind a nickname").color("#777777")
        ));
        ctx.sendMessage(Message.raw("Example: /nick CoolPlayer").color("#AAAAAA"));
        ctx.sendMessage(Message.raw("Example: /nick Steve CoolPlayer").color("#AAAAAA"));
    }

    private boolean hasOthersPermission(UUID playerId) {
        return PermissionService.get().hasPermission(playerId, Permissions.NICK_OTHERS)
                || PermissionService.get().isAdmin(playerId);
    }

    private boolean hasColorPermission(UUID playerId) {
        if (!configManager.getConfig().advancedPermissions) {
            return PermissionService.get().isAdmin(playerId);
        }
        return PermissionService.get().hasPermission(playerId, Permissions.NICK_COLOR);
    }

    private PlayerRef findOnlinePlayer(String name) {
        for (PlayerRef p : Universe.get().getPlayers()) {
            if (p.getUsername().equalsIgnoreCase(name)) {
                return p;
            }
        }
        return null;
    }

    private void refreshTabList(UUID playerId) {
        if (tabListService != null) {
            tabListService.updatePlayer(playerId);
        }
    }
}
