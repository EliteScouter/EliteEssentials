package com.eliteessentials.commands.hytale;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.services.MuteService;
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

public class HytaleUnmuteCommand extends AbstractPlayerCommand {

    private final MuteService muteService;
    private final ConfigManager configManager;

    public HytaleUnmuteCommand(MuteService muteService, ConfigManager configManager) {
        super("unmute", "Unmute a player");
        this.muteService = muteService;
        this.configManager = configManager;
        withRequiredArg("player", "Target player", ArgTypes.STRING)
            .suggest(PlayerSuggestionProvider.INSTANCE);
        setAllowsExtraArguments(true);
    }

    @Override
    protected boolean canGeneratePermission() { return false; }

    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef player, @Nonnull World world) {
        if (!CommandPermissionUtil.canExecuteAdmin(ctx, player, Permissions.ADMIN_UNMUTE,
                configManager.getConfig().mute.enabled)) {
            return;
        }
        String rawInput = ctx.getInputString();
        String[] parts = rawInput.split("\\s+", 2);
        if (parts.length < 2) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("unmuteUsage"), "#FF5555"));
            return;
        }
        String targetName = parts[1];

        // Try online first
        PlayerRef target = PlayerSuggestionProvider.findPlayer(targetName);
        boolean unmuted;

        if (target != null) {
            unmuted = muteService.unmute(target.getUuid());
            if (unmuted) {
                target.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("unmutedNotify"), "#55FF55"));
            }
        } else {
            // Offline - try by name
            unmuted = muteService.unmuteByName(targetName) != null;
        }

        if (unmuted) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("unmuteSuccess", "player", targetName), "#55FF55"));
        } else {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("unmuteNotMuted", "player", targetName), "#FF5555"));
        }
    }
}
