package com.eliteessentials.commands.hytale;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.services.WarnService;
import com.eliteessentials.storage.PlayerStorageProvider;
import com.eliteessentials.util.CommandPermissionUtil;
import com.eliteessentials.util.MessageFormatter;
import com.eliteessentials.util.PlayerSuggestionProvider;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.eliteessentials.commands.base.ElitePlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.Optional;
import java.util.UUID;
import com.eliteessentials.util.CommandSpyUtil;

/**
 * Command: /clearwarnings <player>
 * Clear all warnings for a player. Admin only.
 */
public class HytaleClearWarningsCommand extends ElitePlayerCommand {

    private final WarnService warnService;
    private final ConfigManager configManager;
    private final PlayerStorageProvider playerFileStorage;

    public HytaleClearWarningsCommand(WarnService warnService, ConfigManager configManager,
                                       PlayerStorageProvider playerFileStorage) {
        super("clearwarnings", "Clear a player's warnings");
        this.warnService = warnService;
        this.configManager = configManager;
        this.playerFileStorage = playerFileStorage;
        withRequiredArg("player", "Target player", ArgTypes.STRING)
            .suggest(PlayerSuggestionProvider.INSTANCE);
    }

    @Override
    protected boolean canGeneratePermission() { return false; }

    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef player, @Nonnull World world) {
        CommandSpyUtil.notify(ctx);
        if (!CommandPermissionUtil.canExecuteAdmin(ctx, player, Permissions.ADMIN_CLEARWARNINGS,
                configManager.getConfig().warn.enabled)) {
            return;
        }

        String rawInput = ctx.getInputString();
        String[] parts = rawInput.split("\\s+", 2);
        if (parts.length < 2) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("clearwarningsUsage"), "#FF5555"));
            return;
        }

        String targetName = parts[1];

        // Resolve player UUID
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

        int cleared = warnService.clearWarnings(targetId);
        if (cleared > 0) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("clearwarningsSuccess", "player", resolvedName,
                    "count", String.valueOf(cleared)), "#55FF55"));
        } else {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("warningsNone", "player", resolvedName), "#FFAA00"));
        }
    }
}
