package com.eliteessentials.commands.hytale;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.services.TempBanService;
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

public class HytaleTempBanCommand extends AbstractPlayerCommand {

    private final TempBanService tempBanService;
    private final ConfigManager configManager;

    public HytaleTempBanCommand(TempBanService tempBanService, ConfigManager configManager) {
        super("tempban", "Temporarily ban a player");
        this.tempBanService = tempBanService;
        this.configManager = configManager;
        setAllowsExtraArguments(true);
    }

    @Override
    protected boolean canGeneratePermission() { return false; }

    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef player, @Nonnull World world) {
        if (!CommandPermissionUtil.canExecuteAdmin(ctx, player, Permissions.ADMIN_TEMPBAN,
                configManager.getConfig().ban.enabled)) {
            return;
        }
        // Usage: /tempban <player> <time> [reason]
        String rawInput = ctx.getInputString();
        String[] parts = rawInput.split("\\s+", 4);
        if (parts.length < 3) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("tempbanUsage"), "#FF5555"));
            return;
        }
        String targetName = parts[1];
        String timeStr = parts[2];
        String reason = parts.length >= 4 ? parts[3] : null;

        long durationMs = TempBanService.parseTime(timeStr);
        if (durationMs <= 0) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("tempbanInvalidTime"), "#FF5555"));
            return;
        }

        PlayerRef target = null;
        for (PlayerRef p : Universe.get().getPlayers()) {
            if (p.getUsername().equalsIgnoreCase(targetName)) {
                target = p;
                break;
            }
        }
        if (target == null) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("playerNotFound", "player", targetName), "#FF5555"));
            return;
        }
        if (target.getUuid().equals(player.getUuid())) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("tempbanSelf"), "#FF5555"));
            return;
        }

        boolean banned = tempBanService.tempBan(target.getUuid(), target.getUsername(),
                player.getUsername(), reason, durationMs);
        if (banned) {
            String durationFormatted = TempBanService.formatDuration(durationMs);
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("tempbanSuccess", "player", target.getUsername(),
                    "time", durationFormatted), "#55FF55"));
            // Kick the player
            String kickMsg = reason != null
                ? configManager.getMessage("tempbanKickReason", "reason", reason,
                    "time", durationFormatted, "bannedBy", player.getUsername())
                : configManager.getMessage("tempbanKick", "time", durationFormatted,
                    "bannedBy", player.getUsername());
            try {
                target.getPacketHandler().disconnect(MessageFormatter.stripColorCodes(kickMsg));
            } catch (Exception e) {
                // Player may have already disconnected
            }
        } else {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("tempbanAlready", "player", target.getUsername()), "#FF5555"));
        }
    }
}
