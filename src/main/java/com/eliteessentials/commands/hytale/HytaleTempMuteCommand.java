package com.eliteessentials.commands.hytale;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.services.MuteService;
import com.eliteessentials.services.TempBanService;
import com.eliteessentials.storage.PlayerStorageProvider;
import com.eliteessentials.util.CommandPermissionUtil;
import com.eliteessentials.util.MessageFormatter;
import com.eliteessentials.util.PlayerSuggestionProvider;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.eliteessentials.commands.base.EliteCommandBase;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import javax.annotation.Nonnull;
import java.util.Optional;
import java.util.UUID;
import com.eliteessentials.util.CommandSpyUtil;

/**
 * Command: /tempmute &lt;player&gt; &lt;time&gt; [reason]
 * Mutes a player for a set duration. The mute expires on its own, and is dropped
 * on load if it expired while the server was down.
 *
 * Time format matches /tempban: 1d, 2h, 30m, 1d12h. A bare number means minutes.
 *
 * Runnable from the console for automation. Works on offline players.
 *
 * Permissions:
 * - eliteessentials.admin.tempmute - Use /tempmute (console is always allowed)
 */
public class HytaleTempMuteCommand extends EliteCommandBase {

    private final MuteService muteService;
    private final ConfigManager configManager;
    private final PlayerStorageProvider playerFileStorage;

    public HytaleTempMuteCommand(MuteService muteService, ConfigManager configManager,
                                  PlayerStorageProvider playerFileStorage) {
        super("tempmute", "Temporarily mute a player");
        this.muteService = muteService;
        this.configManager = configManager;
        this.playerFileStorage = playerFileStorage;
        setAllowsExtraArguments(true);
    }

    @Override
    protected boolean canGeneratePermission() { return false; }

    @Override
    protected void executeSync(@Nonnull CommandContext ctx) {
        CommandSpyUtil.notify(ctx);

        // Determine sender type - console has neither a PlayerRef nor a Player
        Object sender = ctx.sender();
        boolean isConsoleSender = !(sender instanceof PlayerRef) && !(sender instanceof Player);

        PlayerRef senderPlayerRef = null;
        if (sender instanceof PlayerRef) {
            senderPlayerRef = (PlayerRef) sender;
        } else if (sender instanceof Player player) {
            @SuppressWarnings("removal") // Entity.getUuid() deprecated; executeSync has no store/ref
            UUID playerUuid = player.getUuid();
            senderPlayerRef = playerUuid != null ? Universe.get().getPlayer(playerUuid) : null;
        }

        // Players need the admin permission; the console is always allowed
        if (!isConsoleSender) {
            if (senderPlayerRef == null) {
                CommandPermissionUtil.sendNoPermission(ctx);
                return;
            }
            if (!CommandPermissionUtil.canExecuteAdmin(ctx, senderPlayerRef, Permissions.ADMIN_TEMPMUTE,
                    configManager.getConfig().mute.enabled)) {
                return;
            }
        }

        String rawInput = ctx.getInputString();
        String[] parts = rawInput.split("\\s+", 4);
        if (parts.length < 3) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("tempmuteUsage"), "#FF5555"));
            return;
        }
        String targetName = parts[1];
        String timeStr = parts[2];
        String reason = parts.length >= 4 ? parts[3] : null;

        // Same parser as /tempban so both commands accept identical durations
        long durationMs = TempBanService.parseTime(timeStr);
        if (durationMs <= 0) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("tempmuteInvalidTime"), "#FF5555"));
            return;
        }

        // Try online first, then offline
        PlayerRef target = PlayerSuggestionProvider.findPlayer(targetName);
        UUID targetId;
        String resolvedName;

        if (target != null) {
            targetId = target.getUuid();
            resolvedName = target.getUsername();
        } else {
            Optional<UUID> offlineId = playerFileStorage.getUuidByName(targetName);
            if (!offlineId.isPresent()) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("playerNeverJoined", "player", targetName), "#FF5555"));
                return;
            }
            targetId = offlineId.get();
            resolvedName = targetName;
        }

        if (senderPlayerRef != null && targetId.equals(senderPlayerRef.getUuid())) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("tempmuteSelf"), "#FF5555"));
            return;
        }

        String mutedBy = isConsoleSender || senderPlayerRef == null
            ? "Console" : senderPlayerRef.getUsername();

        boolean muted = muteService.tempMute(targetId, resolvedName, mutedBy, reason, durationMs);
        if (muted) {
            String durationFormatted = TempBanService.formatDuration(durationMs);
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("tempmuteSuccess", "player", resolvedName,
                    "time", durationFormatted), "#55FF55"));
            // Notify if online
            if (target != null) {
                String muteMsg = reason != null
                    ? configManager.getMessage("tempmutedNotifyReason", "reason", reason,
                        "time", durationFormatted)
                    : configManager.getMessage("tempmutedNotify", "time", durationFormatted);
                target.sendMessage(MessageFormatter.formatWithFallback(muteMsg, "#FF5555"));
            }
        } else {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("tempmuteAlready", "player", resolvedName), "#FF5555"));
        }
    }
}
