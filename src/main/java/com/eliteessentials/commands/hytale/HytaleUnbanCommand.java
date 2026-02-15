package com.eliteessentials.commands.hytale;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.services.BanService;
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

public class HytaleUnbanCommand extends AbstractPlayerCommand {

    private final BanService banService;
    private final ConfigManager configManager;

    public HytaleUnbanCommand(BanService banService, ConfigManager configManager) {
        super("unban", "Unban a player");
        this.banService = banService;
        this.configManager = configManager;
        setAllowsExtraArguments(true);
    }

    @Override
    protected boolean canGeneratePermission() { return false; }

    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef player, @Nonnull World world) {
        if (!CommandPermissionUtil.canExecuteAdmin(ctx, player, Permissions.ADMIN_UNBAN,
                configManager.getConfig().ban.enabled)) {
            return;
        }
        String rawInput = ctx.getInputString();
        String[] parts = rawInput.split("\\s+", 2);
        if (parts.length < 2) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("unbanUsage"), "#FF5555"));
            return;
        }
        String targetName = parts[1];

        // Try to find online player first
        PlayerRef target = null;
        for (PlayerRef p : Universe.get().getPlayers()) {
            if (p.getUsername().equalsIgnoreCase(targetName)) {
                target = p;
                break;
            }
        }

        boolean unbanned;
        if (target != null) {
            unbanned = banService.unban(target.getUuid());
        } else {
            // Offline player - try by name
            UUID unbannedUuid = banService.unbanByName(targetName);
            unbanned = unbannedUuid != null;
        }

        if (unbanned) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("unbanSuccess", "player", targetName), "#55FF55"));
        } else {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("unbanNotBanned", "player", targetName), "#FF5555"));
        }
    }
}
