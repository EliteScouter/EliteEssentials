package com.eliteessentials.gui;

import com.eliteessentials.EliteEssentials;
import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.permissions.PermissionService;
import com.eliteessentials.services.WarnService;
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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Admin UI page for warning management.
 */
public class AdminWarnsPage extends InteractiveCustomUIPage<AdminWarnsPage.WarnEventData> {

    private static final Logger logger = Logger.getLogger("EliteEssentials");
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm");
    private final ConfigManager configManager;
    private String lookupTarget = null;

    public AdminWarnsPage(PlayerRef playerRef, ConfigManager configManager) {
        super(playerRef, CustomPageLifetime.CanDismiss, WarnEventData.CODEC);
        this.configManager = configManager;
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder cmd,
                      UIEventBuilder events, Store<EntityStore> store) {
        cmd.append("Pages/EliteEssentials_AdminWarns.ui");

        cmd.set("#PageTitle.Text", configManager.getMessage("adminui.warns.title"));
        cmd.set("#WarnLabel.Text", configManager.getMessage("adminui.warns.warnPlayer"));
        cmd.set("#LookupLabel.Text", configManager.getMessage("adminui.warns.lookupWarnings"));
        cmd.set("#WarnButton.Text", configManager.getMessage("adminui.warns.warn"));
        cmd.set("#ClearWarningsButton.Text", configManager.getMessage("adminui.warns.clearWarnings"));
        cmd.set("#LookupButton.Text", configManager.getMessage("adminui.warns.lookupBtn"));
        cmd.set("#WarnList.Text", configManager.getMessage("adminui.warns.noWarnings"));

        events.addEventBinding(CustomUIEventBindingType.Activating, "#BackButton",
            EventData.of("Action", "back"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
            EventData.of("Action", "close"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#WarnButton",
            EventData.of("Action", "warn"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ClearWarningsButton",
            EventData.of("Action", "clear"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#LookupButton",
            EventData.of("Action", "lookup"));
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, WarnEventData data) {
        if (data.getAction() == null) return;

        // Permission check - require admin warn permission
        if (!"back".equals(data.getAction()) && !"close".equals(data.getAction())) {
            PermissionService perms = PermissionService.get();
            java.util.UUID playerId = playerRef.getUuid();
            if (!perms.isAdmin(playerId) && !perms.hasPermission(playerId, Permissions.ADMIN_WARN)) {
                setStatus(configManager.getMessage("noPermission"));
                return;
            }
        }

        switch (data.getAction()) {
            case "back":
            case "close":
                this.close();
                break;
            case "warn":
                handleWarn(data);
                break;
            case "clear":
                handleClear(data);
                break;
            case "lookup":
                handleLookup(data);
                break;
        }
    }

    private void handleWarn(WarnEventData data) {
        if (data.getWarnPlayer() == null || data.getWarnPlayer().isEmpty()) {
            setStatus(configManager.getMessage("adminui.warns.enterName"));
            return;
        }

        WarnService warnService = EliteEssentials.getInstance().getWarnService();
        if (warnService == null) return;

        PlayerRef target = PlayerSuggestionProvider.findPlayer(data.getWarnPlayer());
        if (target != null && target.isValid()) {
            String reason = data.getWarnReason() != null ? data.getWarnReason() : "Warned via Admin UI";
            int count = warnService.warn(target.getUuid(), target.getUsername(),
                playerRef.getUsername(), reason);
            setStatus(configManager.getMessage("adminui.warns.warned",
                "player", target.getUsername(), "count", String.valueOf(count)));

            // Auto-show warnings for the warned player
            lookupTarget = data.getWarnPlayer();
            refreshWarnList(target.getUuid());
        } else {
            setStatus(configManager.getMessage("playerNotFound", "player", data.getWarnPlayer()));
        }
    }

    private void handleClear(WarnEventData data) {
        // Clear warnings for the player in the warn input field
        String name = data.getWarnPlayer();
        if (name == null || name.isEmpty()) {
            // Fall back to lookup target
            if (lookupTarget != null) {
                name = lookupTarget;
            } else {
                setStatus(configManager.getMessage("adminui.warns.enterName"));
                return;
            }
        }

        WarnService warnService = EliteEssentials.getInstance().getWarnService();
        if (warnService == null) return;

        PlayerRef target = PlayerSuggestionProvider.findPlayer(name);
        if (target != null) {
            int cleared = warnService.clearWarnings(target.getUuid());
            setStatus(configManager.getMessage("adminui.warns.cleared",
                "player", target.getUsername(), "count", String.valueOf(cleared)));
            refreshWarnList(target.getUuid());
        } else {
            setStatus(configManager.getMessage("playerNotFound", "player", name));
        }
    }

    private void handleLookup(WarnEventData data) {
        String name = data.getLookupPlayer();
        if (name == null || name.isEmpty()) {
            setStatus(configManager.getMessage("adminui.warns.enterName"));
            return;
        }

        lookupTarget = name;
        PlayerRef target = PlayerSuggestionProvider.findPlayer(name);
        if (target != null) {
            refreshWarnList(target.getUuid());
        } else {
            setStatus(configManager.getMessage("playerNotFound", "player", name));
        }
    }

    private void refreshWarnList(UUID playerId) {
        WarnService warnService = EliteEssentials.getInstance().getWarnService();
        if (warnService == null) return;

        UICommandBuilder cmd = new UICommandBuilder();
        List<WarnService.WarnEntry> warnings = warnService.getWarnings(playerId);

        if (warnings.isEmpty()) {
            cmd.set("#WarnList.Text", configManager.getMessage("adminui.warns.noWarnings"));
        } else {
            StringBuilder sb = new StringBuilder();
            int i = 1;
            for (WarnService.WarnEntry w : warnings) {
                String date = DATE_FORMAT.format(new Date(w.warnedAt));
                sb.append("#").append(i).append(" [").append(date).append("] by ")
                  .append(w.warnedBy).append(": ").append(w.reason).append("\n");
                i++;
            }
            cmd.set("#WarnList.Text", sb.toString().trim());
        }
        sendUpdate(cmd, null, false);
    }

    private void setStatus(String message) {
        UICommandBuilder cmd = new UICommandBuilder();
        cmd.set("#WarnStatusMsg.Text", message);
        sendUpdate(cmd, null, false);
    }

    public static class WarnEventData {
        public static final BuilderCodec<WarnEventData> CODEC = BuilderCodec.builder(WarnEventData.class, WarnEventData::new)
            .append(new KeyedCodec<>("Action", Codec.STRING), (d, s) -> d.action = s, d -> d.action).add()
            .append(new KeyedCodec<>("@WarnPlayer", Codec.STRING), (d, s) -> d.warnPlayer = s, d -> d.warnPlayer).add()
            .append(new KeyedCodec<>("@WarnReason", Codec.STRING), (d, s) -> d.warnReason = s, d -> d.warnReason).add()
            .append(new KeyedCodec<>("@LookupPlayer", Codec.STRING), (d, s) -> d.lookupPlayer = s, d -> d.lookupPlayer).add()
            .build();

        private String action, warnPlayer, warnReason, lookupPlayer;

        public String getAction() { return action; }
        public void setAction(String v) { this.action = v; }
        public String getWarnPlayer() { return warnPlayer; }
        public void setWarnPlayer(String v) { this.warnPlayer = v; }
        public String getWarnReason() { return warnReason; }
        public void setWarnReason(String v) { this.warnReason = v; }
        public String getLookupPlayer() { return lookupPlayer; }
        public void setLookupPlayer(String v) { this.lookupPlayer = v; }
    }
}
