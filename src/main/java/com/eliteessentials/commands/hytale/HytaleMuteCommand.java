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
import java.util.UUID;

public class HytaleMuteCommand extends AbstractPlayerCommand {

    private final MuteService muteService;
    private final ConfigManager configManager;

    public HytaleMuteCommand(MuteService muteService, ConfigManager configManager) {
        super("mute", "Mute a player");
        this.muteService = muteService;
        this.configManager = configManager;
        setAllowsExtraArguments(true);
    }

    @Override
    protected boolean canGeneratePermission() { return false; }

    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef player, @Nonnull World world) {
        if (!CommandPermissionUtil.canExecuteAdmin(ctx, player, Permissions.ADMIN_MUTE,
                configManager.getConfig().mute.enabled)) {
            return;
        }
        String rawInput = ctx.getInputString();
        String[] parts = rawInput.split("\\s+", 3);
        if (parts.length < 2) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("muteUsage"), "#FF5555"));
            return;
        }
        String targetName = parts[1];
        String reason = parts.length >= 3 ? parts[2] : null;
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
                configManager.getMessage("muteSelf"), "#FF5555"));
            return;
        }
        boolean muted = muteService.mute(target.getUuid(), target.getUsername(), player.getUsername(), reason);
        if (muted) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("muteSuccess", "player", target.getUsername()), "#55FF55"));
            String muteMsg = reason != null
                ? configManager.getMessage("mutedNotifyReason", "reason", reason)
                : configManager.getMessage("mutedNotify");
            target.sendMessage(MessageFormatter.formatWithFallback(muteMsg, "#FF5555"));
        } else {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("muteAlready", "player", target.getUsername()), "#FF5555"));
        }
    }
}
