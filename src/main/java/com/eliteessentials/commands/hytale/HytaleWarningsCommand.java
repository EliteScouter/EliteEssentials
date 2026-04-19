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
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.text.SimpleDateFormat;
import java.util.*;
import com.eliteessentials.util.CommandSpyUtil;

/**
 * Command: /warnings <player>
 * View a player's warning history. Admin only.
 */
public class HytaleWarningsCommand extends AbstractPlayerCommand {

    private final WarnService warnService;
    private final ConfigManager configManager;
    private final PlayerStorageProvider playerFileStorage;

    public HytaleWarningsCommand(WarnService warnService, ConfigManager configManager,
                                  PlayerStorageProvider playerFileStorage) {
        super("warnings", "View a player's warnings");
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
        if (!CommandPermissionUtil.canExecuteAdmin(ctx, player, Permissions.ADMIN_WARN,
                configManager.getConfig().warn.enabled)) {
            return;
        }

        String rawInput = ctx.getInputString();
        String[] parts = rawInput.split("\\s+", 2);
        if (parts.length < 2) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("warningsUsage"), "#FF5555"));
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

        List<WarnService.WarnEntry> warnings = warnService.getWarnings(targetId);
        if (warnings.isEmpty()) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("warningsNone", "player", resolvedName), "#55FF55"));
            return;
        }

        ctx.sendMessage(MessageFormatter.formatWithFallback(
            configManager.getMessage("warningsHeader", "player", resolvedName,
                "count", String.valueOf(warnings.size())), "#55FFFF"));

        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy HH:mm");
        int index = 1;
        for (WarnService.WarnEntry entry : warnings) {
            String date = dateFormat.format(new Date(entry.warnedAt));
            String reason = entry.reason != null ? entry.reason : "No reason";
            String line = configManager.getMessage("warningsEntry",
                "number", String.valueOf(index),
                "date", date,
                "by", entry.warnedBy,
                "reason", reason);
            ctx.sendMessage(MessageFormatter.formatWithFallback(line, "#FFFFFF"));
            index++;
        }
    }
}
