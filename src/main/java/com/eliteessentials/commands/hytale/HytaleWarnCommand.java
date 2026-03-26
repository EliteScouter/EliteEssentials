package com.eliteessentials.commands.hytale;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.services.BanService;
import com.eliteessentials.services.TempBanService;
import com.eliteessentials.services.WarnService;
import com.eliteessentials.storage.PlayerStorageProvider;
import com.eliteessentials.util.CommandPermissionUtil;
import com.eliteessentials.util.MessageFormatter;
import com.eliteessentials.util.PlayerSuggestionProvider;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Command: /warn <player> [reason]
 * Issues a warning to a player. When the threshold is reached, auto-punishes.
 */
public class HytaleWarnCommand extends AbstractPlayerCommand {

    private static final Logger logger = Logger.getLogger("EliteEssentials");

    private final WarnService warnService;
    private final BanService banService;
    private final TempBanService tempBanService;
    private final ConfigManager configManager;
    private final PlayerStorageProvider playerFileStorage;

    public HytaleWarnCommand(WarnService warnService, BanService banService,
                             TempBanService tempBanService, ConfigManager configManager,
                             PlayerStorageProvider playerFileStorage) {
        super("warn", "Warn a player");
        this.warnService = warnService;
        this.banService = banService;
        this.tempBanService = tempBanService;
        this.configManager = configManager;
        this.playerFileStorage = playerFileStorage;
        withRequiredArg("player", "Target player", ArgTypes.STRING)
            .suggest(PlayerSuggestionProvider.INSTANCE);
        setAllowsExtraArguments(true);
    }

    @Override
    protected boolean canGeneratePermission() { return false; }

    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef player, @Nonnull World world) {
        if (!CommandPermissionUtil.canExecuteAdmin(ctx, player, Permissions.ADMIN_WARN,
                configManager.getConfig().warn.enabled)) {
            return;
        }

        String rawInput = ctx.getInputString();
        String[] parts = rawInput.split("\\s+", 3);
        if (parts.length < 2) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("warnUsage"), "#FF5555"));
            return;
        }

        String targetName = parts[1];
        String reason = parts.length >= 3 ? parts[2] : null;

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

        if (targetId.equals(player.getUuid())) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("warnSelf"), "#FF5555"));
            return;
        }

        int newCount = warnService.warn(targetId, resolvedName, player.getUsername(), reason);

        // Notify the issuer
        ctx.sendMessage(MessageFormatter.formatWithFallback(
            configManager.getMessage("warnSuccess", "player", resolvedName, "count", String.valueOf(newCount)), "#55FF55"));

        // Notify the target if online
        if (target != null) {
            String warnMsg = reason != null
                ? configManager.getMessage("warnNotifyReason", "reason", reason, "count", String.valueOf(newCount))
                : configManager.getMessage("warnNotify", "count", String.valueOf(newCount));
            target.sendMessage(MessageFormatter.formatWithFallback(warnMsg, "#FF5555"));
        }

        // Check auto-punishment threshold
        int threshold = configManager.getConfig().warn.autoPunishThreshold;
        if (threshold > 0 && newCount >= threshold) {
            applyAutoPunishment(ctx, targetId, resolvedName, player.getUsername(), newCount);
        }
    }

    /**
     * Apply automatic punishment when warning threshold is reached.
     */
    private void applyAutoPunishment(CommandContext ctx, UUID targetId, String targetName,
                                     String issuedBy, int warnCount) {
        String action = configManager.getConfig().warn.autoPunishAction;
        String autoReason = configManager.getMessage("warnAutoPunishReason",
            "count", String.valueOf(warnCount), "threshold", String.valueOf(configManager.getConfig().warn.autoPunishThreshold));

        boolean applied = false;
        if ("ban".equalsIgnoreCase(action)) {
            applied = banService.ban(targetId, targetName, "AutoWarn", autoReason);
        } else if ("tempban".equalsIgnoreCase(action)) {
            long durationMs = configManager.getConfig().warn.autoPunishTempbanMinutes * 60L * 1000L;
            applied = tempBanService.tempBan(targetId, targetName, "AutoWarn", autoReason, durationMs);
        }

        if (applied) {
            // Kick the player if online
            PlayerRef target = Universe.get().getPlayer(targetId);
            if (target != null) {
                try {
                    target.getPacketHandler().disconnect(
                        Message.raw(com.eliteessentials.util.MessageFormatter.stripColorCodes(autoReason)));
                } catch (Exception e) {
                    // Player may have already disconnected
                }
            }

            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("warnThresholdReached", "player", targetName,
                    "count", String.valueOf(warnCount), "action", action), "#FFAA00"));

            // Clear warnings after auto-punishment if configured
            if (configManager.getConfig().warn.clearAfterPunishment) {
                warnService.clearWarnings(targetId);
            }

            logger.info("[WarnService] Auto-" + action + " applied to " + targetName
                + " after " + warnCount + " warnings.");
        }
    }
}
