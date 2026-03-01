package com.eliteessentials.commands.hytale;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.services.IgnoreService;
import com.eliteessentials.storage.PlayerFileStorage;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class HytaleIgnoreCommand extends AbstractPlayerCommand {

    private final IgnoreService ignoreService;
    private final ConfigManager configManager;
    private final PlayerFileStorage playerFileStorage;

    public HytaleIgnoreCommand(IgnoreService ignoreService, ConfigManager configManager,
                                PlayerFileStorage playerFileStorage) {
        super("ignore", "Ignore a player's messages");
        this.ignoreService = ignoreService;
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
        UUID playerId = player.getUuid();
        if (!CommandPermissionUtil.canExecute(ctx, player, Permissions.IGNORE,
                configManager.getConfig().ignore.enabled)) {
            return;
        }
        String rawInput = ctx.getInputString();
        String[] parts = rawInput.split("\\s+", 2);
        if (parts.length < 2) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("ignoreUsage"), "#FF5555"));
            return;
        }
        String arg = parts[1];

        if (arg.equalsIgnoreCase("list")) {
            List<String> names = ignoreService.getIgnoredPlayerNames(playerId);
            if (names.isEmpty()) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("ignoreListEmpty"), "#AAAAAA"));
                return;
            }
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("ignoreListHeader", "count", String.valueOf(names.size())), "#55FFFF"));
            ctx.sendMessage(MessageFormatter.formatWithFallback("&f" + String.join(", ", names), "#FFFFFF"));
            return;
        }

        // Try online first, then offline
        PlayerRef target = PlayerSuggestionProvider.findPlayer(arg);
        UUID targetId;
        String resolvedName;

        if (target != null) {
            targetId = target.getUuid();
            resolvedName = target.getUsername();
        } else {
            Optional<UUID> offlineId = playerFileStorage.getUuidByName(arg);
            if (!offlineId.isPresent()) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("playerNeverJoined", "player", arg), "#FF5555"));
                return;
            }
            targetId = offlineId.get();
            resolvedName = arg;
        }

        if (targetId.equals(playerId)) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("ignoreSelf"), "#FF5555"));
            return;
        }

        boolean added = ignoreService.addIgnore(playerId, player.getUsername(), targetId);
        if (added) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("ignoreAdded", "player", resolvedName), "#55FF55"));
        } else {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("ignoreAlready", "player", resolvedName), "#FF5555"));
        }
    }
}
