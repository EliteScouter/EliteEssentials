package com.eliteessentials.commands.hytale;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.services.BanService;
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
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import java.util.Optional;
import java.util.UUID;

public class HytaleBanCommand extends AbstractPlayerCommand {

    private final BanService banService;
    private final ConfigManager configManager;
    private final PlayerStorageProvider playerFileStorage;

    public HytaleBanCommand(BanService banService, ConfigManager configManager, PlayerStorageProvider playerFileStorage) {
        super("ban", "Permanently ban a player");
        this.banService = banService;
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
        if (!CommandPermissionUtil.canExecuteAdmin(ctx, player, Permissions.ADMIN_BAN,
                configManager.getConfig().ban.enabled)) {
            return;
        }
        String rawInput = ctx.getInputString();
        String[] parts = rawInput.split("\\s+", 3);
        if (parts.length < 2) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("banUsage"), "#FF5555"));
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
            // Offline lookup
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
                configManager.getMessage("banSelf"), "#FF5555"));
            return;
        }

        boolean banned = banService.ban(targetId, resolvedName, player.getUsername(), reason);
        if (banned) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("banSuccess", "player", resolvedName), "#55FF55"));
            // Kick if online
            if (target != null) {
                String kickMsg = reason != null
                    ? configManager.getMessage("banKickReason", "reason", reason, "bannedBy", player.getUsername())
                    : configManager.getMessage("banKick", "bannedBy", player.getUsername());
                try {
                    target.getPacketHandler().disconnect(Message.raw(MessageFormatter.stripColorCodes(kickMsg)));
                } catch (Exception e) {
                    // Player may have already disconnected
                }
            }
        } else {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("banAlready", "player", resolvedName), "#FF5555"));
        }
    }
}
