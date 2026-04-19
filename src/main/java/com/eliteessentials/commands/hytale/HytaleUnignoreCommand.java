package com.eliteessentials.commands.hytale;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.services.IgnoreService;
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
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import java.util.Optional;
import java.util.UUID;
import com.eliteessentials.util.CommandSpyUtil;

public class HytaleUnignoreCommand extends AbstractPlayerCommand {

    private final IgnoreService ignoreService;
    private final ConfigManager configManager;
    private final PlayerStorageProvider playerFileStorage;

    public HytaleUnignoreCommand(IgnoreService ignoreService, ConfigManager configManager,
                                  PlayerStorageProvider playerFileStorage) {
        super("unignore", "Unignore a player's messages");
        this.ignoreService = ignoreService;
        this.configManager = configManager;
        this.playerFileStorage = playerFileStorage;
        // Register player arg for autocomplete suggestions (execution uses raw input parsing)
        withRequiredArg("player", "Target player", ArgTypes.STRING)
            .suggest(PlayerSuggestionProvider.INSTANCE);
        setAllowsExtraArguments(true);
    }

    @Override
    protected boolean canGeneratePermission() { return false; }

    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef player, @Nonnull World world) {
        CommandSpyUtil.notify(ctx);
        UUID playerId = player.getUuid();
        if (!CommandPermissionUtil.canExecute(ctx, player, Permissions.IGNORE,
                configManager.getConfig().ignore.enabled)) {
            return;
        }
        String rawInput = ctx.getInputString();
        String[] parts = rawInput.split("\\s+", 2);
        if (parts.length < 2) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("unignoreUsage"), "#FF5555"));
            return;
        }
        String arg = parts[1];
        if (arg.equalsIgnoreCase("all")) {
            int count = ignoreService.clearAllIgnored(playerId);
            if (count > 0) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("unignoreAll", "count", String.valueOf(count)), "#55FF55"));
            } else {
                ctx.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("ignoreListEmpty"), "#AAAAAA"));
            }
            return;
        }
        String targetName = arg;
        UUID targetId = null;
        PlayerRef onlineTarget = PlayerSuggestionProvider.findPlayer(arg);
        if (onlineTarget != null) {
            targetId = onlineTarget.getUuid();
            targetName = onlineTarget.getUsername();
        }
        if (targetId == null) {
            Optional<UUID> offlineId = playerFileStorage.getUuidByName(targetName);
            if (offlineId.isPresent()) {
                targetId = offlineId.get();
            }
        }
        if (targetId == null) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("playerNotFound", "player", targetName), "#FF5555"));
            return;
        }
        boolean removed = ignoreService.removeIgnore(playerId, targetId);
        if (removed) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("unignoreRemoved", "player", targetName), "#55FF55"));
        } else {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("unignoreNotIgnored", "player", targetName), "#FF5555"));
        }
    }
}
