package com.eliteessentials.commands.hytale;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.services.MuteService;
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

public class HytaleUnmuteCommand extends AbstractPlayerCommand {

    private final MuteService muteService;
    private final ConfigManager configManager;

    public HytaleUnmuteCommand(MuteService muteService, ConfigManager configManager) {
        super("unmute", "Unmute a player");
        this.muteService = muteService;
        this.configManager = configManager;
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
        boolean unmuted = muteService.unmute(target.getUuid());
        if (unmuted) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("unmuteSuccess", "player", target.getUsername()), "#55FF55"));
            target.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("unmutedNotify"), "#55FF55"));
        } else {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("unmuteNotMuted", "player", target.getUsername()), "#FF5555"));
        }
    }
}
