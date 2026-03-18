package com.eliteessentials.commands.hytale;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.model.PlayerFile;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.services.BanService;
import com.eliteessentials.services.FreezeService;
import com.eliteessentials.services.MuteService;
import com.eliteessentials.services.PlayerService;
import com.eliteessentials.services.TempBanService;
import com.eliteessentials.services.WarnService;
import com.eliteessentials.util.CommandPermissionUtil;
import com.eliteessentials.util.HytaleSaveFileReader;
import com.eliteessentials.util.MessageFormatter;
import com.eliteessentials.util.PlayerSuggestionProvider;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
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
    private final MuteService muteService;
    private final BanService banService;
    private final TempBanService tempBanService;
    private final FreezeService freezeService;
    private final WarnService warnService;

    public HytalePlayerInfoCommand(ConfigManager configManager, PlayerService playerService,
                                    MuteService muteService, BanService banService,
                                    TempBanService tempBanService, FreezeService freezeService,
                                    WarnService warnService) {
        super(COMMAND_NAME, "View detailed player information");
        this.configManager = configManager;
        this.playerService = playerService;
        this.muteService = muteService;
        this.banService = banService;
        this.tempBanService = tempBanService;
        this.freezeService = freezeService;
        this.warnService = warnService;
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

        boolean enabled = configManager.getConfig().playerinfo.enabled;

        // /playerinfo - self (simple mode: everyone when enabled; advanced: misc.playerinfo)
        if (parts.length < 2 || parts[1].isEmpty()) {
            if (!CommandPermissionUtil.canExecute(ctx, player, Permissions.PLAYERINFO, enabled)) {
                return;
            }
            Optional<PlayerFile> self = playerService.getPlayer(player.getUuid());
            if (self.isEmpty()) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("playerinfoNoData"), "#FF5555"));
                return;
            }
            sendPlayerInfo(ctx, self.get(), player.getUsername(), true, player.getUuid());
            return;
        }

        // /playerinfo <name> - other (simple mode: OP only when enabled; advanced: misc.playerinfo.others)
        if (!CommandPermissionUtil.canExecuteAdmin(ctx, player, Permissions.PLAYERINFO_OTHERS, enabled)) {
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
            boolean isSelf = onlineRef.getUuid().equals(player.getUuid());
            sendPlayerInfo(ctx, data, onlineRef.getUsername(), isSelf, player.getUuid());
            return;
        }

        Optional<PlayerFile> opt = playerService.getPlayerByName(targetName);
        if (opt.isEmpty()) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("seenNeverJoined", "player", Objects.requireNonNullElse(targetName, "")), "#FF5555"));
            return;
        }
        data = opt.get();
        sendPlayerInfo(ctx, data, Objects.requireNonNullElse(data.getName(), targetName), false, player.getUuid());
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
            // Show coordinates for online players - must run on the target player's world thread
            sendPlayerCoordinates(ctx, data.getUuid());
        } else {
            ctx.sendMessage(Message.join(
                MessageFormatter.formatWithFallback(configManager.getMessage("playerinfoLabelLastSeen"), "#AAAAAA"),
                Message.raw(formatRelativeTime(lastSeen)).color("#FFFFFF")
            ));
            // Show last saved coordinates from Hytale save file (e.g. spawn-on-logout position)
            HytaleSaveFileReader.readPosition(data.getUuid()).ifPresent(saved -> {
                String coords = String.format("%.1f, %.1f, %.1f (%s)%s",
                    saved.x, saved.y, saved.z, saved.world,
                    configManager.getMessage("playerinfoCoordinatesLastSaved"));
                ctx.sendMessage(Message.join(
                    MessageFormatter.formatWithFallback(configManager.getMessage("playerinfoLabelCoordinates"), "#AAAAAA"),
                    Message.raw(coords).color("#AAAAAA")
                ));
            });
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

        // Punishment status section (only shown when viewing other players, i.e. admin view)
        if (!isSelf) {
            sendPunishmentInfo(ctx, data.getUuid());
        }
    }

    /**
     * Display punishment status: mute, ban, tempban, freeze, and warning count.
     */
    private void sendPunishmentInfo(CommandContext ctx, UUID targetId) {
        ctx.sendMessage(MessageFormatter.formatWithFallback(
            configManager.getMessage("playerinfoLabelPunishments"), "#FFAA00"));

        // Mute status
        if (muteService.isMuted(targetId)) {
            MuteService.MuteEntry mute = muteService.getMuteEntry(targetId);
            String reasonPart = mute.reason != null
                ? configManager.getMessage("playerinfoPunishmentReason", "reason", mute.reason) : "";
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("playerinfoMuted", "by", mute.mutedBy, "reason", reasonPart), "#FFFFFF"));
        } else {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("playerinfoMutedNone"), "#FFFFFF"));
        }

        // Ban status (permanent or temp)
        if (banService.isBanned(targetId)) {
            BanService.BanEntry ban = banService.getBanEntry(targetId);
            String reasonPart = ban.reason != null
                ? configManager.getMessage("playerinfoPunishmentReason", "reason", ban.reason) : "";
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("playerinfoBanned", "by", ban.bannedBy, "reason", reasonPart), "#FFFFFF"));
        } else if (tempBanService.isTempBanned(targetId)) {
            TempBanService.TempBanEntry tempBan = tempBanService.getTempBanEntry(targetId);
            String remaining = formatRemainingTime(tempBan.banEndTimestamp);
            String reasonPart = tempBan.reason != null
                ? configManager.getMessage("playerinfoPunishmentReason", "reason", tempBan.reason) : "";
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("playerinfoTempBanned", "by", tempBan.bannedBy,
                    "time", remaining, "reason", reasonPart), "#FFFFFF"));
        } else {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("playerinfoBannedNone"), "#FFFFFF"));
        }

        // Freeze status
        if (freezeService.isFrozen(targetId)) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("playerinfoFrozen"), "#FFFFFF"));
        } else {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("playerinfoFrozenNone"), "#FFFFFF"));
        }

        // Warning count
        int warnCount = warnService.getWarningCount(targetId);
        if (warnCount > 0) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("playerinfoWarnings", "count", String.valueOf(warnCount)), "#FFFFFF"));
        } else {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("playerinfoWarningsNone"), "#FFFFFF"));
        }
    }

    /**
     * Format remaining time until a timestamp (e.g. "2h 15m").
     */
    private static String formatRemainingTime(long endTimestamp) {
        long diff = endTimestamp - System.currentTimeMillis();
        if (diff <= 0) return "expired";
        long seconds = diff / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        if (days > 0) {
            long h = hours % 24;
            return h > 0 ? days + "d " + h + "h" : days + "d";
        }
        if (hours > 0) {
            long m = minutes % 60;
            return m > 0 ? hours + "h " + m + "m" : hours + "h";
        }
        if (minutes > 0) return minutes + "m";
        return seconds + "s";
    }

    private boolean isPlayerOnline(@Nonnull UUID uuid) {
        return Universe.get().getPlayer(uuid) != null;
    }

    /**
     * Sends formatted coordinates "x, y, z (world)" for an online player.
     * Dispatches the store read to the player's world thread to avoid
     * IllegalStateException when the command executor is on a different world.
     */
    private void sendPlayerCoordinates(@Nonnull CommandContext ctx, @Nonnull UUID playerUuid) {
        PlayerRef targetRef = Universe.get().getPlayer(playerUuid);
        if (targetRef == null) return;

        // Resolve the world that owns the target player's store
        UUID worldUuid = targetRef.getWorldUuid();
        if (worldUuid == null) return;
        World targetWorld = Universe.get().getWorld(worldUuid);
        if (targetWorld == null) return;

        // Run the component read on the correct world thread
        targetWorld.execute(() -> {
            Ref<EntityStore> entityRef = targetRef.getReference();
            if (entityRef == null || !entityRef.isValid()) return;
            Store<EntityStore> store = entityRef.getStore();
            if (store == null) return;
            TransformComponent transform = store.getComponent(entityRef, TransformComponent.getComponentType());
            if (transform == null) return;
            Vector3d pos = transform.getPosition();
            String worldName = targetWorld.getName();
            String coords = String.format("%.1f, %.1f, %.1f (%s)", pos.getX(), pos.getY(), pos.getZ(), worldName);
            ctx.sendMessage(Message.join(
                MessageFormatter.formatWithFallback(configManager.getMessage("playerinfoLabelCoordinates"), "#AAAAAA"),
                Message.raw(coords).color("#55FF55")
            ));
        });
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
