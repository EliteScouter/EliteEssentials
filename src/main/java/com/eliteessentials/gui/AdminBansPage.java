package com.eliteessentials.gui;

import com.eliteessentials.EliteEssentials;
import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.permissions.PermissionService;
import com.eliteessentials.services.BanService;
import com.eliteessentials.services.IpBanService;
import com.eliteessentials.services.TempBanService;
import com.eliteessentials.util.PlayerSuggestionProvider;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Admin UI page for ban management.
 * Supports permanent bans, temp bans, IP bans, and unbanning.
 */
public class AdminBansPage extends InteractiveCustomUIPage<AdminBansPage.BanEventData> {

    private static final Logger logger = Logger.getLogger("EliteEssentials");
    private final ConfigManager configManager;

    public AdminBansPage(PlayerRef playerRef, ConfigManager configManager) {
        super(playerRef, CustomPageLifetime.CanDismiss, BanEventData.CODEC);
        this.configManager = configManager;
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder cmd,
                      UIEventBuilder events, Store<EntityStore> store) {
        cmd.append("Pages/EliteEssentials_AdminBans.ui");

        // Translatable labels
        cmd.set("#PageTitle.Text", configManager.getMessage("adminui.bans.title"));
        cmd.set("#BanLabel.Text", configManager.getMessage("adminui.bans.banPlayer"));
        cmd.set("#UnbanLabel.Text", configManager.getMessage("adminui.bans.unbanPlayer"));
        cmd.set("#ActiveLabel.Text", configManager.getMessage("adminui.bans.activeBans"));

        // Button labels
        cmd.set("#BanButton.Text", configManager.getMessage("adminui.bans.ban"));
        cmd.set("#TempBanButton.Text", configManager.getMessage("adminui.bans.tempBan"));
        cmd.set("#IpBanButton.Text", configManager.getMessage("adminui.bans.ipBan"));
        cmd.set("#UnbanButton.Text", configManager.getMessage("adminui.bans.unban"));

        // Populate active bans list
        populateBanList(cmd);

        // Bind events
        events.addEventBinding(CustomUIEventBindingType.Activating, "#BackButton",
            EventData.of("Action", "back"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
            EventData.of("Action", "close"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#BanButton",
            EventData.of("Action", "ban"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#TempBanButton",
            EventData.of("Action", "tempban"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#IpBanButton",
            EventData.of("Action", "ipban"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#UnbanButton",
            EventData.of("Action", "unban"));
    }

    private void populateBanList(UICommandBuilder cmd) {
        EliteEssentials plugin = EliteEssentials.getInstance();
        StringBuilder banText = new StringBuilder();

        // Permanent bans
        if (plugin.getBanService() != null) {
            Map<String, BanService.BanEntry> bans = plugin.getBanService().getAllBans();
            for (Map.Entry<String, BanService.BanEntry> entry : bans.entrySet()) {
                String name = entry.getValue().playerName != null ? entry.getValue().playerName : entry.getKey();
                banText.append(name).append(" (perm) - ").append(
                    entry.getValue().reason != null ? entry.getValue().reason : "No reason").append("\n");
            }
        }

        // Temp bans
        if (plugin.getTempBanService() != null) {
            Map<String, TempBanService.TempBanEntry> tempBans = plugin.getTempBanService().getAllTempBans();
            for (Map.Entry<String, TempBanService.TempBanEntry> entry : tempBans.entrySet()) {
                String name = entry.getValue().playerName != null ? entry.getValue().playerName : entry.getKey();
                long remaining = entry.getValue().getRemainingTime();
                String timeStr = formatDuration(remaining);
                banText.append(name).append(" (").append(timeStr).append(") - ").append(
                    entry.getValue().reason != null ? entry.getValue().reason : "No reason").append("\n");
            }
        }

        if (banText.length() == 0) {
            cmd.set("#BanList.Text", configManager.getMessage("adminui.bans.noBans"));
        } else {
            cmd.set("#BanList.Text", banText.toString().trim());
        }
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, BanEventData data) {
        if (data.getAction() == null) return;

        // Permission check - require admin ban permission
        if (!"back".equals(data.getAction()) && !"close".equals(data.getAction())) {
            PermissionService perms = PermissionService.get();
            UUID playerId = playerRef.getUuid();
            if (!perms.isAdmin(playerId) && !perms.hasPermission(playerId, Permissions.ADMIN_BAN)) {
                setStatus(configManager.getMessage("noPermission"));
                return;
            }
        }

        EliteEssentials plugin = EliteEssentials.getInstance();
        String adminName = playerRef.getUsername();

        switch (data.getAction()) {
            case "back":
            case "close":
                this.close();
                break;
            case "ban":
                handleBan(data, plugin, adminName);
                break;
            case "tempban":
                handleTempBan(data, plugin, adminName);
                break;
            case "ipban":
                handleIpBan(data, plugin, adminName);
                break;
            case "unban":
                handleUnban(data, plugin);
                break;
        }
    }

    private void handleBan(BanEventData data, EliteEssentials plugin, String adminName) {
        if (data.getBanPlayer() == null || data.getBanPlayer().isEmpty()) {
            setStatus(configManager.getMessage("adminui.bans.enterName"));
            return;
        }

        PlayerRef target = PlayerSuggestionProvider.findPlayer(data.getBanPlayer());
        if (target != null && target.isValid()) {
            boolean success = plugin.getBanService().ban(target.getUuid(), target.getUsername(),
                adminName, data.getBanReason() != null ? data.getBanReason() : "Banned via Admin UI");
            if (success) {
                try {
                    target.getPacketHandler().disconnect(
                        Message.raw(com.eliteessentials.util.MessageFormatter.stripColorCodes(
                            configManager.getMessage("adminui.bans.banKickMsg"))));
                } catch (Exception ignored) {}
                setStatus(configManager.getMessage("adminui.bans.banned", "player", target.getUsername()));
            } else {
                setStatus(configManager.getMessage("adminui.bans.alreadyBanned", "player", target.getUsername()));
            }
        } else {
            setStatus(configManager.getMessage("playerNotFound", "player", data.getBanPlayer()));
        }
        refreshBanList();
    }

    private void handleTempBan(BanEventData data, EliteEssentials plugin, String adminName) {
        if (data.getBanPlayer() == null || data.getBanPlayer().isEmpty()) {
            setStatus(configManager.getMessage("adminui.bans.enterName"));
            return;
        }

        long durationMs = parseDuration(data.getBanDuration());
        if (durationMs <= 0) {
            setStatus(configManager.getMessage("adminui.bans.invalidDuration"));
            return;
        }

        PlayerRef target = PlayerSuggestionProvider.findPlayer(data.getBanPlayer());
        if (target != null && target.isValid()) {
            boolean success = plugin.getTempBanService().tempBan(target.getUuid(), target.getUsername(),
                adminName, data.getBanReason() != null ? data.getBanReason() : "Temp banned via Admin UI", durationMs);
            if (success) {
                try {
                    target.getPacketHandler().disconnect(
                        Message.raw(com.eliteessentials.util.MessageFormatter.stripColorCodes(
                            configManager.getMessage("adminui.bans.banKickMsg"))));
                } catch (Exception ignored) {}
                setStatus(configManager.getMessage("adminui.bans.tempBanned",
                    "player", target.getUsername(), "duration", data.getBanDuration()));
            } else {
                setStatus(configManager.getMessage("adminui.bans.alreadyBanned", "player", target.getUsername()));
            }
        } else {
            setStatus(configManager.getMessage("playerNotFound", "player", data.getBanPlayer()));
        }
        refreshBanList();
    }

    private void handleIpBan(BanEventData data, EliteEssentials plugin, String adminName) {
        if (data.getBanPlayer() == null || data.getBanPlayer().isEmpty()) {
            setStatus(configManager.getMessage("adminui.bans.enterName"));
            return;
        }

        PlayerRef target = PlayerSuggestionProvider.findPlayer(data.getBanPlayer());
        if (target != null && target.isValid()) {
            try {
                String ip = IpBanService.getIpFromPacketHandler(target.getPacketHandler());
                if (ip != null) {
                    plugin.getIpBanService().banIp(ip, target.getUuid(), target.getUsername(),
                        adminName, data.getBanReason() != null ? data.getBanReason() : "IP banned via Admin UI");
                    try {
                        target.getPacketHandler().disconnect(
                            Message.raw(com.eliteessentials.util.MessageFormatter.stripColorCodes(
                                configManager.getMessage("adminui.bans.banKickMsg"))));
                    } catch (Exception ignored) {}
                    setStatus(configManager.getMessage("adminui.bans.ipBanned", "player", target.getUsername()));
                } else {
                    setStatus(configManager.getMessage("adminui.bans.ipFailed"));
                }
            } catch (Exception e) {
                setStatus(configManager.getMessage("adminui.bans.ipFailed"));
            }
        } else {
            setStatus(configManager.getMessage("playerNotFound", "player", data.getBanPlayer()));
        }
        refreshBanList();
    }

    private void handleUnban(BanEventData data, EliteEssentials plugin) {
        String name = data.getUnbanPlayer();
        if (name == null || name.isEmpty()) {
            setStatus(configManager.getMessage("adminui.bans.enterName"));
            return;
        }

        // Try permanent ban first
        UUID unbanned = plugin.getBanService().unbanByName(name);
        if (unbanned != null) {
            setStatus(configManager.getMessage("adminui.bans.unbanned", "player", name));
            refreshBanList();
            return;
        }

        // Try temp ban
        unbanned = plugin.getTempBanService().unbanByName(name);
        if (unbanned != null) {
            setStatus(configManager.getMessage("adminui.bans.unbanned", "player", name));
            refreshBanList();
            return;
        }

        setStatus(configManager.getMessage("adminui.bans.notBanned", "player", name));
    }

    private void refreshBanList() {
        UICommandBuilder cmd = new UICommandBuilder();
        populateBanList(cmd);
        sendUpdate(cmd, null, false);
    }

    private void setStatus(String message) {
        UICommandBuilder cmd = new UICommandBuilder();
        cmd.set("#BanStatusMsg.Text", message);
        sendUpdate(cmd, null, false);
    }

    private static long parseDuration(String input) {
        if (input == null || input.isEmpty()) return 0;
        input = input.trim().toLowerCase();
        try {
            if (input.endsWith("d")) return Long.parseLong(input.replace("d", "")) * 86_400_000L;
            if (input.endsWith("h")) return Long.parseLong(input.replace("h", "")) * 3_600_000L;
            if (input.endsWith("m")) return Long.parseLong(input.replace("m", "")) * 60_000L;
            return Long.parseLong(input) * 60_000L; // Default to minutes
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String formatDuration(long ms) {
        if (ms <= 0) return "expired";
        long hours = ms / 3_600_000;
        long minutes = (ms % 3_600_000) / 60_000;
        if (hours > 24) {
            long days = hours / 24;
            return days + "d " + (hours % 24) + "h";
        }
        return hours + "h " + minutes + "m";
    }

    public static class BanEventData {
        public static final BuilderCodec<BanEventData> CODEC = BuilderCodec.builder(BanEventData.class, BanEventData::new)
            .append(new KeyedCodec<>("Action", Codec.STRING), (d, s) -> d.action = s, d -> d.action).add()
            .append(new KeyedCodec<>("@BanPlayer", Codec.STRING), (d, s) -> d.banPlayer = s, d -> d.banPlayer).add()
            .append(new KeyedCodec<>("@BanDuration", Codec.STRING), (d, s) -> d.banDuration = s, d -> d.banDuration).add()
            .append(new KeyedCodec<>("@BanReason", Codec.STRING), (d, s) -> d.banReason = s, d -> d.banReason).add()
            .append(new KeyedCodec<>("@UnbanPlayer", Codec.STRING), (d, s) -> d.unbanPlayer = s, d -> d.unbanPlayer).add()
            .build();

        private String action, banPlayer, banDuration, banReason, unbanPlayer;

        public String getAction() { return action; }
        public void setAction(String v) { this.action = v; }
        public String getBanPlayer() { return banPlayer; }
        public void setBanPlayer(String v) { this.banPlayer = v; }
        public String getBanDuration() { return banDuration; }
        public void setBanDuration(String v) { this.banDuration = v; }
        public String getBanReason() { return banReason; }
        public void setBanReason(String v) { this.banReason = v; }
        public String getUnbanPlayer() { return unbanPlayer; }
        public void setUnbanPlayer(String v) { this.unbanPlayer = v; }
    }
}
