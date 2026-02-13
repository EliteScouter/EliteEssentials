package com.eliteessentials.commands.hytale;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.services.IgnoreService;
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
import java.util.List;
import java.util.UUID;

public class HytaleIgnoreCommand extends AbstractPlayerCommand {

    private final IgnoreService ignoreService;
    private final ConfigManager configManager;

    public HytaleIgnoreCommand(IgnoreService ignoreService, ConfigManager configManager) {
        super("ignore", "Ignore a player's messages");
        this.ignoreService = ignoreService;
        this.configManager = configManager;
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
        PlayerRef target = findPlayer(arg);
        if (target == null) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("playerNotFound", "player", arg), "#FF5555"));
            return;
        }
        if (target.getUuid().equals(playerId)) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("ignoreSelf"), "#FF5555"));
            return;
        }
        boolean added = ignoreService.addIgnore(playerId, player.getUsername(), target.getUuid());
        if (added) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("ignoreAdded", "player", target.getUsername()), "#55FF55"));
        } else {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("ignoreAlready", "player", target.getUsername()), "#FF5555"));
        }
    }

    private PlayerRef findPlayer(String name) {
        for (PlayerRef p : Universe.get().getPlayers()) {
            if (p.getUsername().equalsIgnoreCase(name)) return p;
        }
        return null;
    }
}
