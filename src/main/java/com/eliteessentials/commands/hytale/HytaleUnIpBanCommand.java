package com.eliteessentials.commands.hytale;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.services.IpBanService;
import com.eliteessentials.util.CommandPermissionUtil;
import com.eliteessentials.util.MessageFormatter;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

public class HytaleUnIpBanCommand extends AbstractPlayerCommand {

    private final IpBanService ipBanService;
    private final ConfigManager configManager;

    public HytaleUnIpBanCommand(IpBanService ipBanService, ConfigManager configManager) {
        super("unipban", "Unban an IP address");
        this.ipBanService = ipBanService;
        this.configManager = configManager;
        setAllowsExtraArguments(true);
    }

    @Override
    protected boolean canGeneratePermission() { return false; }

    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef player, @Nonnull World world) {
        if (!CommandPermissionUtil.canExecuteAdmin(ctx, player, Permissions.ADMIN_UNIPBAN,
                configManager.getConfig().ban.enabled)) {
            return;
        }
        String rawInput = ctx.getInputString();
        String[] parts = rawInput.split("\\s+", 2);
        if (parts.length < 2) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("unipbanUsage"), "#FF5555"));
            return;
        }
        String target = parts[1];

        // Try as IP address first, then as player name
        boolean unbanned;
        if (target.contains(".") || target.contains(":")) {
            // Looks like an IP address
            unbanned = ipBanService.unbanIp(target);
            if (unbanned) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("unipbanSuccess", "ip", target), "#55FF55"));
            } else {
                ctx.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("unipbanNotBanned", "ip", target), "#FF5555"));
            }
        } else {
            // Try as player name
            String ip = ipBanService.unbanByName(target);
            if (ip != null) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("unipbanSuccessName", "player", target, "ip", ip), "#55FF55"));
            } else {
                ctx.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("unipbanNotBannedName", "player", target), "#FF5555"));
            }
        }
    }
}
