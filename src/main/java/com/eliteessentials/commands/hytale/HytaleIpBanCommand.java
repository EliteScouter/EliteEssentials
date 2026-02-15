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
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

public class HytaleIpBanCommand extends AbstractPlayerCommand {

    private final IpBanService ipBanService;
    private final ConfigManager configManager;

    public HytaleIpBanCommand(IpBanService ipBanService, ConfigManager configManager) {
        super("ipban", "Ban a player's IP address");
        this.ipBanService = ipBanService;
        this.configManager = configManager;
        setAllowsExtraArguments(true);
    }

    @Override
    protected boolean canGeneratePermission() { return false; }

    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef player, @Nonnull World world) {
        if (!CommandPermissionUtil.canExecuteAdmin(ctx, player, Permissions.ADMIN_IPBAN,
                configManager.getConfig().ban.enabled)) {
            return;
        }
        String rawInput = ctx.getInputString();
        String[] parts = rawInput.split("\\s+", 3);
        if (parts.length < 2) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("ipbanUsage"), "#FF5555"));
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

        // Extract IP from the target's PacketHandler
        String ip = IpBanService.getIpFromPacketHandler(target.getPacketHandler());
        if (ip == null) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("ipbanNoIp", "player", target.getUsername()), "#FF5555"));
            return;
        }

        if (ipBanService.isBanned(ip)) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("ipbanAlready", "player", target.getUsername(), "ip", ip), "#FF5555"));
            return;
        }

        boolean banned = ipBanService.banIp(ip, target.getUuid(), target.getUsername(),
                player.getUsername(), reason);
        if (banned) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("ipbanSuccess", "player", target.getUsername(), "ip", ip), "#55FF55"));
            // Kick the player
            String kickMsg = reason != null
                ? configManager.getMessage("ipbanKickReason", "reason", reason, "bannedBy", player.getUsername())
                : configManager.getMessage("ipbanKick", "bannedBy", player.getUsername());
            try {
                target.getPacketHandler().disconnect(MessageFormatter.stripColorCodes(kickMsg));
            } catch (Exception e) {
                // Player may have already disconnected
            }
        }
    }
}
