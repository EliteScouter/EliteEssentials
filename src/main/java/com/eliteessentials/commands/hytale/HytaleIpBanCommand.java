package com.eliteessentials.commands.hytale;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.model.PlayerFile;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.services.IpBanService;
import com.eliteessentials.storage.PlayerStorageProvider;
import com.eliteessentials.util.CommandPermissionUtil;
import com.eliteessentials.util.MessageFormatter;
import com.eliteessentials.util.PlayerSuggestionProvider;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import com.eliteessentials.util.CommandSpyUtil;

public class HytaleIpBanCommand extends AbstractPlayerCommand {

    private final IpBanService ipBanService;
    private final ConfigManager configManager;

    public HytaleIpBanCommand(IpBanService ipBanService, ConfigManager configManager) {
        super("ipban", "Ban a player's IP address");
        this.ipBanService = ipBanService;
        this.configManager = configManager;
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

        PlayerRef target = PlayerSuggestionProvider.findPlayer(targetName);
        String ip;
        java.util.UUID targetId;
        String resolvedName;

        if (target != null) {
            targetId = target.getUuid();
            resolvedName = target.getUsername();
            ip = IpBanService.getIpFromPacketHandler(target.getPacketHandler());
        } else {
            // Offline lookup — resolve UUID then get last known IP from player file
            com.eliteessentials.EliteEssentials plugin = com.eliteessentials.EliteEssentials.getInstance();
            PlayerStorageProvider storage = plugin.getPlayerStorageProvider();
            java.util.Optional<java.util.UUID> offlineId = storage.getUuidByName(targetName);
            if (!offlineId.isPresent()) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("playerNotFound", "player", targetName), "#FF5555"));
                return;
            }
            targetId = offlineId.get();
            resolvedName = targetName;

            PlayerFile playerFile = storage.getPlayer(targetId);
            if (playerFile != null) {
                java.util.List<PlayerFile.IpHistoryEntry> history = playerFile.getIpHistory();
                ip = (history != null && !history.isEmpty())
                    ? history.stream()
                        .max((a, b) -> Long.compare(a.lastUsed, b.lastUsed))
                        .map(e -> e.ip)
                        .orElse(null)
                    : null;
            } else {
                ip = null;
            }
        }

        if (ip == null) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("ipbanNoIp", "player", resolvedName), "#FF5555"));
            return;
        }

        if (ipBanService.isBanned(ip)) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("ipbanAlready", "player", resolvedName, "ip", ip), "#FF5555"));
            return;
        }

        boolean banned = ipBanService.banIp(ip, targetId, resolvedName,
                player.getUsername(), reason);
        if (banned) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("ipbanSuccess", "player", resolvedName, "ip", ip), "#55FF55"));
            // Kick if online
            if (target != null) {
                String kickMsg = reason != null
                    ? configManager.getMessage("ipbanKickReason", "reason", reason, "bannedBy", player.getUsername())
                    : configManager.getMessage("ipbanKick", "bannedBy", player.getUsername());
                try {
                    target.getPacketHandler().disconnect(Message.raw(MessageFormatter.stripColorCodes(kickMsg)));
                } catch (Exception e) {
                    // Player may have already disconnected
                }
            }
        }
    }
}
