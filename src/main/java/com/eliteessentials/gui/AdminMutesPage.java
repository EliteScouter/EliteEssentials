package com.eliteessentials.gui;

import com.eliteessentials.EliteEssentials;
import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.permissions.PermissionService;
import com.eliteessentials.services.MuteService;
import com.eliteessentials.util.PlayerSuggestionProvider;
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
 * Admin UI page for mute management.
 */
public class AdminMutesPage extends InteractiveCustomUIPage<AdminMutesPage.MuteEventData> {

    private static final Logger logger = Logger.getLogger("EliteEssentials");
    private final ConfigManager configManager;

    public AdminMutesPage(PlayerRef playerRef, ConfigManager configManager) {
        super(playerRef, CustomPageLifetime.CanDismiss, MuteEventData.CODEC);
        this.configManager = configManager;
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder cmd,
                      UIEventBuilder events, Store<EntityStore> store) {
        cmd.append("Pages/EliteEssentials_AdminMutes.ui");

        cmd.set("#PageTitle.Text", configManager.getMessage("adminui.mutes.title"));
        cmd.set("#MuteLabel.Text", configManager.getMessage("adminui.mutes.mutePlayer"));
        cmd.set("#UnmuteLabel.Text", configManager.getMessage("adminui.mutes.unmutePlayer"));
        cmd.set("#ActiveLabel.Text", configManager.getMessage("adminui.mutes.activeMutes"));
        cmd.set("#MuteButton.Text", configManager.getMessage("adminui.mutes.mute"));
        cmd.set("#UnmuteButton.Text", configManager.getMessage("adminui.mutes.unmute"));

        populateMuteList(cmd);

        events.addEventBinding(CustomUIEventBindingType.Activating, "#BackButton",
            EventData.of("Action", "back"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
            EventData.of("Action", "close"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#MuteButton",
            EventData.of("Action", "mute"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#UnmuteButton",
            EventData.of("Action", "unmute"));
    }

    private void populateMuteList(UICommandBuilder cmd) {
        MuteService muteService = EliteEssentials.getInstance().getMuteService();
        if (muteService == null) {
            cmd.set("#MuteList.Text", configManager.getMessage("adminui.mutes.noMutes"));
            return;
        }

        Map<String, MuteService.MuteEntry> mutes = muteService.getAllMutes();
        if (mutes.isEmpty()) {
            cmd.set("#MuteList.Text", configManager.getMessage("adminui.mutes.noMutes"));
            return;
        }

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, MuteService.MuteEntry> entry : mutes.entrySet()) {
            String name = entry.getValue().playerName != null ? entry.getValue().playerName : entry.getKey();
            String reason = entry.getValue().reason != null ? entry.getValue().reason : "No reason";
            sb.append(name).append(" - ").append(reason).append("\n");
        }
        cmd.set("#MuteList.Text", sb.toString().trim());
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, MuteEventData data) {
        if (data.getAction() == null) return;

        // Permission check - require admin mute permission
        if (!"back".equals(data.getAction()) && !"close".equals(data.getAction())) {
            PermissionService perms = PermissionService.get();
            UUID playerId = playerRef.getUuid();
            if (!perms.isAdmin(playerId) && !perms.hasPermission(playerId, Permissions.ADMIN_MUTE)) {
                setStatus(configManager.getMessage("noPermission"));
                return;
            }
        }

        switch (data.getAction()) {
            case "back":
            case "close":
                this.close();
                break;
            case "mute":
                handleMute(data);
                break;
            case "unmute":
                handleUnmute(data);
                break;
        }
    }

    private void handleMute(MuteEventData data) {
        if (data.getMutePlayer() == null || data.getMutePlayer().isEmpty()) {
            setStatus(configManager.getMessage("adminui.mutes.enterName"));
            return;
        }

        MuteService muteService = EliteEssentials.getInstance().getMuteService();
        if (muteService == null) return;

        PlayerRef target = PlayerSuggestionProvider.findPlayer(data.getMutePlayer());
        if (target != null && target.isValid()) {
            String reason = data.getMuteReason() != null ? data.getMuteReason() : "Muted via Admin UI";
            boolean success = muteService.mute(target.getUuid(), target.getUsername(),
                playerRef.getUsername(), reason);
            if (success) {
                setStatus(configManager.getMessage("adminui.mutes.muted", "player", target.getUsername()));
            } else {
                setStatus(configManager.getMessage("adminui.mutes.alreadyMuted", "player", target.getUsername()));
            }
        } else {
            setStatus(configManager.getMessage("playerNotFound", "player", data.getMutePlayer()));
        }
        refreshMuteList();
    }

    private void handleUnmute(MuteEventData data) {
        String name = data.getUnmutePlayer();
        if (name == null || name.isEmpty()) {
            setStatus(configManager.getMessage("adminui.mutes.enterName"));
            return;
        }

        MuteService muteService = EliteEssentials.getInstance().getMuteService();
        if (muteService == null) return;

        UUID unmuted = muteService.unmuteByName(name);
        if (unmuted != null) {
            setStatus(configManager.getMessage("adminui.mutes.unmuted", "player", name));
        } else {
            setStatus(configManager.getMessage("adminui.mutes.notMuted", "player", name));
        }
        refreshMuteList();
    }

    private void refreshMuteList() {
        UICommandBuilder cmd = new UICommandBuilder();
        populateMuteList(cmd);
        sendUpdate(cmd, null, false);
    }

    private void setStatus(String message) {
        UICommandBuilder cmd = new UICommandBuilder();
        cmd.set("#MuteStatusMsg.Text", message);
        sendUpdate(cmd, null, false);
    }

    public static class MuteEventData {
        public static final BuilderCodec<MuteEventData> CODEC = BuilderCodec.builder(MuteEventData.class, MuteEventData::new)
            .append(new KeyedCodec<>("Action", Codec.STRING), (d, s) -> d.action = s, d -> d.action).add()
            .append(new KeyedCodec<>("@MutePlayer", Codec.STRING), (d, s) -> d.mutePlayer = s, d -> d.mutePlayer).add()
            .append(new KeyedCodec<>("@MuteReason", Codec.STRING), (d, s) -> d.muteReason = s, d -> d.muteReason).add()
            .append(new KeyedCodec<>("@UnmutePlayer", Codec.STRING), (d, s) -> d.unmutePlayer = s, d -> d.unmutePlayer).add()
            .build();

        private String action, mutePlayer, muteReason, unmutePlayer;

        public String getAction() { return action; }
        public void setAction(String v) { this.action = v; }
        public String getMutePlayer() { return mutePlayer; }
        public void setMutePlayer(String v) { this.mutePlayer = v; }
        public String getMuteReason() { return muteReason; }
        public void setMuteReason(String v) { this.muteReason = v; }
        public String getUnmutePlayer() { return unmutePlayer; }
        public void setUnmutePlayer(String v) { this.unmutePlayer = v; }
    }
}
