package com.eliteessentials.commands.hytale;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.model.PlayerFile;
import com.eliteessentials.permissions.PermissionService;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.services.PlayerService;
import com.eliteessentials.util.MessageFormatter;
import com.eliteessentials.util.PlayerSuggestionProvider;
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
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Command: /playerinfo [player]
 * Display detailed player info (UUID, nickname, first join, last seen, wallet, playtime, kit claims, milestones).
 *
 * Usage:
 *   /playerinfo           - Your own info (misc.playerinfo)
 *   /playerinfo <name>    - Another player's info (misc.playerinfo.others)
 *
 * Parameter handling matches /nick: single optional name, no fancy brackets.
 */
public class HytalePlayerInfoCommand extends AbstractPlayerCommand {

    private static final String COMMAND_NAME = "playerinfo";

    private final ConfigManager configManager;
    private final PlayerService playerService;

    public HytalePlayerInfoCommand(ConfigManager configManager, PlayerService playerService) {
        super(COMMAND_NAME, "View detailed player information");
        this.configManager = configManager;
        this.playerService = playerService;
        setAllowsExtraArguments(true);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                          @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef player, @Nonnull World world) {
        String rawInput = ctx.getInputString().trim();
        String[] parts = rawInput.split("\\s+", 3);

        UUID senderId = player.getUuid();

        // /playerinfo - self
        if (parts.length < 2 || parts[1].isEmpty()) {
            if (!PermissionService.get().canUseAdminCommand(senderId, Permissions.PLAYERINFO, true)) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("noPermission"), "#FF5555"));
                return;
            }
            Optional<PlayerFile> self = playerService.getPlayer(senderId);
            if (self.isEmpty()) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("playerinfoNoData"), "#FF5555"));
                return;
            }
            sendPlayerInfo(ctx, self.get(), player.getUsername(), true, senderId);
            return;
        }

        // /playerinfo <name> - other (or self if name matches)
        if (!PermissionService.get().hasPermission(senderId, Permissions.PLAYERINFO_OTHERS)
                && !PermissionService.get().isAdmin(senderId)) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("noPermission"), "#FF5555"));
            return;
        }

        String targetName = parts[1];
        PlayerRef onlineRef = PlayerSuggestionProvider.findPlayer(targetName);
        PlayerFile data;

        if (onlineRef != null) {
            Optional<PlayerFile> opt = playerService.getPlayer(onlineRef.getUuid());
            if (opt.isEmpty()) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("playerinfoNoDataFor", "player", onlineRef.getUsername()), "#FF5555"));
                return;
            }
            data = opt.get();
            boolean isSelf = onlineRef.getUuid().equals(senderId);
            sendPlayerInfo(ctx, data, onlineRef.getUsername(), isSelf, senderId);
            return;
        }

        Optional<PlayerFile> opt = playerService.getPlayerByName(targetName);
        if (opt.isEmpty()) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("seenNeverJoined", "player", Objects.requireNonNullElse(targetName, "")), "#FF5555"));
            return;
        }
        data = opt.get();
        sendPlayerInfo(ctx, data, Objects.requireNonNullElse(data.getName(), targetName), false, senderId);
    }

    private void sendPlayerInfo(CommandContext ctx, PlayerFile data, String displayName,
                               boolean isSelf, UUID viewerId) {
        String headerMsg = isSelf
            ? configManager.getMessage("playerinfoHeaderSelf")
            : configManager.getMessage("playerinfoHeaderOther", "player", displayName);
        ctx.sendMessage(MessageFormatter.formatWithFallback(headerMsg, "#55FFFF"));

        String uuidStr = data.getUuid().toString();
        String uuidUrl = "https://hytaleid.com/?q=" + uuidStr;
        ctx.sendMessage(Message.join(
            MessageFormatter.formatWithFallback(configManager.getMessage("playerinfoLabelUuid"), "#AAAAAA"),
            Message.raw(uuidStr).color("#55FF55").link(uuidUrl)
        ));
        String name = Objects.requireNonNullElse(data.getName(), "?");
        ctx.sendMessage(Message.join(
            MessageFormatter.formatWithFallback(configManager.getMessage("playerinfoLabelUsername"), "#AAAAAA"),
            Message.raw(name).color("#FFFFFF")
        ));

        if (data.hasNickname()) {
            String nick = data.getNickname();
            if (nick != null && !nick.isEmpty()) {
                ctx.sendMessage(Message.join(
                    MessageFormatter.formatWithFallback(configManager.getMessage("playerinfoLabelNickname"), "#AAAAAA"),
                    Message.raw(nick).color("#FFAA00")
                ));
            }
        }

        long firstJoin = data.getFirstJoin();
        long lastSeen = data.getLastSeen();
        ctx.sendMessage(Message.join(
            MessageFormatter.formatWithFallback(configManager.getMessage("playerinfoLabelFirstJoin"), "#AAAAAA"),
            Message.raw(formatTimestamp(firstJoin)).color("#FFFFFF")
        ));

        boolean online = isPlayerOnline(data.getUuid());
        if (online) {
            ctx.sendMessage(Message.join(
                MessageFormatter.formatWithFallback(configManager.getMessage("playerinfoLabelLastSeen"), "#AAAAAA"),
                MessageFormatter.formatWithFallback(configManager.getMessage("playerinfoOnlineNow"), "#55FF55")
            ));
        } else {
            ctx.sendMessage(Message.join(
                MessageFormatter.formatWithFallback(configManager.getMessage("playerinfoLabelLastSeen"), "#AAAAAA"),
                Message.raw(formatRelativeTime(lastSeen)).color("#FFFFFF")
            ));
        }

        ctx.sendMessage(Message.join(
            MessageFormatter.formatWithFallback(configManager.getMessage("playerinfoLabelWallet"), "#AAAAAA"),
            Message.raw(String.format("%.2f", data.getWallet())).color("#55FF55")
        ));

        long playTimeSeconds = data.getPlayTime();
        if (online) {
            playTimeSeconds += playerService.getCurrentSessionSeconds(data.getUuid());
        }
        ctx.sendMessage(Message.join(
            MessageFormatter.formatWithFallback(configManager.getMessage("playerinfoLabelPlaytime"), "#AAAAAA"),
            Message.raw(PlayerService.formatPlayTime(playTimeSeconds)).color("#FFFFFF")
        ));

        Set<String> kitClaims = data.getKitClaims();
        if (kitClaims != null && !kitClaims.isEmpty()) {
            String kits = kitClaims.stream().sorted().collect(Collectors.joining(", "));
            ctx.sendMessage(Message.join(
                MessageFormatter.formatWithFallback(configManager.getMessage("playerinfoLabelKitClaims"), "#AAAAAA"),
                Message.raw(kits).color("#FFFFFF")
            ));
        }

        Set<String> milestones = data.getPlaytimeClaims().claimedMilestones;
        if (milestones != null && !milestones.isEmpty()) {
            String list = milestones.stream().sorted().collect(Collectors.joining(", "));
            ctx.sendMessage(Message.join(
                MessageFormatter.formatWithFallback(configManager.getMessage("playerinfoLabelClaimedMilestones"), "#AAAAAA"),
                Message.raw(list).color("#FFFFFF")
            ));
        }

        int homeCount = data.getHomeCount();
        ctx.sendMessage(Message.join(
            MessageFormatter.formatWithFallback(configManager.getMessage("playerinfoLabelHomes"), "#AAAAAA"),
            Message.raw(String.valueOf(homeCount)).color("#FFFFFF")
        ));

        String defaultChat = data.getDefaultGroupChat();
        if (defaultChat != null && !defaultChat.isEmpty()) {
            ctx.sendMessage(Message.join(
                MessageFormatter.formatWithFallback(configManager.getMessage("playerinfoLabelDefaultGroupChat"), "#AAAAAA"),
                Message.raw(defaultChat).color("#FFFFFF")
            ));
        }
    }

    private boolean isPlayerOnline(@Nonnull UUID uuid) {
        return Universe.get().getPlayer(uuid) != null;
    }

    private static String formatTimestamp(long millis) {
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("MMM dd, yyyy HH:mm");
        synchronized (f) {
            return f.format(new java.util.Date(millis));
        }
    }

    private static String formatRelativeTime(long timestamp) {
        long diff = System.currentTimeMillis() - timestamp;
        if (diff < 0) return "just now";
        long seconds = diff / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        if (days > 0) {
            long h = hours % 24;
            if (h > 0) return days + "d " + h + "h ago";
            return days + " day(s) ago";
        }
        if (hours > 0) {
            long m = minutes % 60;
            if (m > 0) return hours + "h " + m + "m ago";
            return hours + " hour(s) ago";
        }
        if (minutes > 0) return minutes + " minute(s) ago";
        return "just now";
    }
}
