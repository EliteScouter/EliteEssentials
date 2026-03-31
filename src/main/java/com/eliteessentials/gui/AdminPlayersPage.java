package com.eliteessentials.gui;

import com.eliteessentials.EliteEssentials;
import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.permissions.PermissionService;
import com.eliteessentials.services.FreezeService;
import com.eliteessentials.util.PlayerSuggestionProvider;
import com.eliteessentials.util.MessageFormatter;
import com.hypixel.hytale.server.core.Message;
import com.eliteessentials.util.TeleportUtil;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.logging.Logger;

/**
 * Admin UI page for managing online players.
 * Supports lookup, teleport, heal, freeze, and kick actions.
 */
public class AdminPlayersPage extends InteractiveCustomUIPage<AdminPlayersPage.PlayerEventData> {

    private static final Logger logger = Logger.getLogger("EliteEssentials");
    private final ConfigManager configManager;
    private String selectedPlayer = null;

    public AdminPlayersPage(PlayerRef playerRef, ConfigManager configManager) {
        super(playerRef, CustomPageLifetime.CanDismiss, PlayerEventData.CODEC);
        this.configManager = configManager;
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder cmd,
                      UIEventBuilder events, Store<EntityStore> store) {
        cmd.append("Pages/EliteEssentials_AdminPlayers.ui");

        cmd.set("#PageTitle.Text", configManager.getMessage("adminui.players.title"));
        cmd.set("#LookupLabel.Text", configManager.getMessage("adminui.players.lookup"));
        cmd.set("#ActionsLabel.Text", configManager.getMessage("adminui.players.actions"));
        cmd.set("#OnlineLabel.Text", configManager.getMessage("adminui.players.online"));
        cmd.set("#TeleportToButton.Text", configManager.getMessage("adminui.players.tpTo"));
        cmd.set("#TeleportHereButton.Text", configManager.getMessage("adminui.players.tpHere"));
        cmd.set("#HealButton.Text", configManager.getMessage("adminui.players.heal"));
        cmd.set("#FreezeButton.Text", configManager.getMessage("adminui.players.freeze"));
        cmd.set("#KickButton.Text", configManager.getMessage("adminui.players.kick"));
        cmd.set("#RefreshButton.Text", configManager.getMessage("adminui.players.refresh"));

        buildPlayerList(cmd, events);

        events.addEventBinding(CustomUIEventBindingType.Activating, "#BackButton", EventData.of("Action", "back"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#LookupButton", EventData.of("Action", "lookup"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#TeleportToButton", EventData.of("Action", "tpto"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#TeleportHereButton", EventData.of("Action", "tphere"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#HealButton", EventData.of("Action", "heal"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#FreezeButton", EventData.of("Action", "freeze"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#KickButton", EventData.of("Action", "kick"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#RefreshButton", EventData.of("Action", "refresh"));
    }

    private void buildPlayerList(UICommandBuilder cmd, UIEventBuilder events) {
        int index = 0;
        for (PlayerRef p : Universe.get().getPlayers()) {
            if (!p.isValid()) continue;
            String selector = "#PlayerListCards[" + index + "]";
            cmd.append("#PlayerListCards", "Pages/EliteEssentials_AdminPlayerEntry.ui");
            cmd.set(selector + " #EntryName.Text", p.getUsername());
            cmd.set(selector + " #EntrySelectButton.Text", configManager.getMessage("adminui.players.select"));
            events.addEventBinding(CustomUIEventBindingType.Activating, selector + " #EntrySelectButton",
                new EventData().append("Action", "select").append("Player", p.getUsername()), false);
            index++;
        }
        if (index == 0) {
            cmd.set("#PlayerStatusMsg.Text", configManager.getMessage("adminui.players.noPlayers"));
        }
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, PlayerEventData data) {
        if (data.action == null) return;
        switch (data.action) {
            case "back": this.close(); break;
            case "refresh": this.rebuild(); break;
            case "select":
                if (data.player != null) { selectedPlayer = data.player; updatePlayerInfo(); }
                break;
            case "lookup":
                if (data.player != null && !data.player.isEmpty()) selectedPlayer = data.player;
                updatePlayerInfo();
                break;
            case "tpto": handleTeleportTo(ref, store); break;
            case "tphere": handleTeleportHere(ref, store); break;
            case "heal": handleHeal(); break;
            case "freeze": handleFreeze(); break;
            case "kick": handleKick(); break;
        }
    }

    private void updatePlayerInfo() {
        UICommandBuilder cmd = new UICommandBuilder();
        if (selectedPlayer == null) return;
        PlayerRef target = PlayerSuggestionProvider.findPlayer(selectedPlayer);
        if (target == null || !target.isValid()) {
            cmd.set("#InfoName.Text", selectedPlayer);
            cmd.set("#InfoHealth.Text", configManager.getMessage("adminui.players.offline"));
            cmd.set("#InfoLocation.Text", "-");
            cmd.set("#InfoStatus.Text", "-");
            cmd.set("#PlayerStatusMsg.Text", configManager.getMessage("playerNotFound", "player", selectedPlayer));
            sendUpdate(cmd, null, false);
            return;
        }
        cmd.set("#InfoName.Text", target.getUsername());
        try {
            Ref<EntityStore> tRef = target.getReference();
            if (tRef != null && tRef.isValid()) {
                Store<EntityStore> tStore = tRef.getStore();
                TransformComponent transform = tStore.getComponent(tRef, TransformComponent.getComponentType());
                if (transform != null) {
                    Vector3d pos = transform.getPosition();
                    cmd.set("#InfoLocation.Text", String.format("%.0f, %.0f, %.0f", pos.x, pos.y, pos.z));
                }
            }
        } catch (Exception e) {
            logger.warning("[AdminUI] Error reading player data: " + e.getMessage());
        }
        EliteEssentials plugin = EliteEssentials.getInstance();
        StringBuilder status = new StringBuilder();
        if (plugin.getFreezeService() != null && plugin.getFreezeService().isFrozen(target.getUuid())) status.append("FROZEN ");
        if (plugin.getGodService() != null && plugin.getGodService().isGodMode(target.getUuid())) status.append("GOD ");
        if (plugin.getVanishService() != null && plugin.getVanishService().isVanished(target.getUuid())) status.append("VANISHED ");
        if (plugin.getMuteService() != null && plugin.getMuteService().isMuted(target.getUuid())) status.append("MUTED ");
        cmd.set("#InfoStatus.Text", status.length() > 0 ? status.toString().trim() : "Normal");
        cmd.set("#PlayerStatusMsg.Text", configManager.getMessage("adminui.players.selected", "player", target.getUsername()));
        sendUpdate(cmd, null, false);
    }

    private void handleTeleportTo(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (!checkPermission(Permissions.ADMIN_TELEPORT)) return;
        if (selectedPlayer == null) return;
        PlayerRef target = PlayerSuggestionProvider.findPlayer(selectedPlayer);
        if (target == null || !target.isValid()) { setStatus(configManager.getMessage("playerNotFound", "player", selectedPlayer)); return; }
        try {
            Ref<EntityStore> tRef = target.getReference();
            Store<EntityStore> tStore = tRef.getStore();
            TransformComponent transform = tStore.getComponent(tRef, TransformComponent.getComponentType());
            if (transform != null) {
                Vector3d pos = transform.getPosition();
                World world = tStore.getExternalData().getWorld();
                TeleportUtil.safeTeleport(world, world, pos, new Vector3f(0, 0, 0), playerRef, () -> {}, () -> {});
                this.close();
            }
        } catch (Exception e) { setStatus("Teleport failed: " + e.getMessage()); }
    }

    private void handleTeleportHere(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (!checkPermission(Permissions.ADMIN_TELEPORT)) return;
        if (selectedPlayer == null) return;
        PlayerRef target = PlayerSuggestionProvider.findPlayer(selectedPlayer);
        if (target == null || !target.isValid()) { setStatus(configManager.getMessage("playerNotFound", "player", selectedPlayer)); return; }
        try {
            TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
            if (transform != null) {
                Vector3d pos = transform.getPosition();
                World world = store.getExternalData().getWorld();
                TeleportUtil.safeTeleport(world, world, pos, new Vector3f(0, 0, 0), target, () -> {}, () -> {});
                setStatus(configManager.getMessage("adminui.players.tpHereSuccess", "player", target.getUsername()));
            }
        } catch (Exception e) { setStatus("Teleport failed: " + e.getMessage()); }
    }

    private void handleHeal() {
        if (!checkPermission(Permissions.ADMIN_HEAL)) return;
        if (selectedPlayer == null) return;
        PlayerRef target = PlayerSuggestionProvider.findPlayer(selectedPlayer);
        if (target == null || !target.isValid()) { setStatus(configManager.getMessage("playerNotFound", "player", selectedPlayer)); return; }
        try {
            Ref<EntityStore> tRef = target.getReference();
            Store<EntityStore> tStore = tRef.getStore();
            World tWorld = tStore.getExternalData().getWorld();
            tWorld.execute(() -> {
                EntityStatMap statMap = tStore.getComponent(tRef, EntityStatMap.getComponentType());
                if (statMap != null) {
                    statMap.maximizeStatValue(DefaultEntityStatTypes.getHealth());
                }
            });
            setStatus(configManager.getMessage("adminui.players.healed", "player", target.getUsername()));
        } catch (Exception e) { setStatus("Heal failed: " + e.getMessage()); }
    }

    private void handleFreeze() {
        if (!checkPermission(Permissions.ADMIN_FREEZE)) return;
        if (selectedPlayer == null) return;
        PlayerRef target = PlayerSuggestionProvider.findPlayer(selectedPlayer);
        if (target == null || !target.isValid()) { setStatus(configManager.getMessage("playerNotFound", "player", selectedPlayer)); return; }
        FreezeService freezeService = EliteEssentials.getInstance().getFreezeService();
        if (freezeService == null) return;

        Ref<EntityStore> tRef = target.getReference();
        if (tRef == null || !tRef.isValid()) return;
        Store<EntityStore> tStore = tRef.getStore();
        EntityStore entityStore = tStore.getExternalData();
        World targetWorld = entityStore != null ? entityStore.getWorld() : null;

        if (freezeService.isFrozen(target.getUuid())) {
            freezeService.unfreeze(target.getUuid());
            if (targetWorld != null) {
                final PlayerRef ft = target;
                targetWorld.execute(() -> FreezeService.removeFreeze(tStore, tRef, ft));
            }
            setStatus(configManager.getMessage("adminui.players.unfrozen", "player", target.getUsername()));
        } else {
            freezeService.freeze(target.getUuid(), target.getUsername(), playerRef.getUsername());
            if (targetWorld != null) {
                final PlayerRef ft = target;
                targetWorld.execute(() -> FreezeService.applyFreeze(tStore, tRef, ft));
            }
            setStatus(configManager.getMessage("adminui.players.frozen", "player", target.getUsername()));
        }
        updatePlayerInfo();
    }

    private void handleKick() {
        if (!checkPermission(Permissions.ADMIN_KICK)) return;
        if (selectedPlayer == null) return;
        PlayerRef target = PlayerSuggestionProvider.findPlayer(selectedPlayer);
        if (target == null || !target.isValid()) { setStatus(configManager.getMessage("playerNotFound", "player", selectedPlayer)); return; }
        try {
            target.getPacketHandler().disconnect(
                Message.raw(com.eliteessentials.util.MessageFormatter.stripColorCodes(configManager.getMessage("adminui.players.kickReason"))));
            setStatus(configManager.getMessage("adminui.players.kicked", "player", target.getUsername()));
            selectedPlayer = null;
        } catch (Exception e) { setStatus("Kick failed: " + e.getMessage()); }
    }

    /**
     * Check if the admin has a specific permission.
     * Admins with wildcard always pass; otherwise checks the granular node.
     */
    private boolean checkPermission(String permission) {
        PermissionService perms = PermissionService.get();
        java.util.UUID playerId = playerRef.getUuid();
        if (perms.isAdmin(playerId)) return true;
        if (!perms.hasPermission(playerId, permission)) {
            setStatus(configManager.getMessage("noPermission"));
            return false;
        }
        return true;
    }

    private void setStatus(String message) {
        UICommandBuilder cmd = new UICommandBuilder();
        cmd.set("#PlayerStatusMsg.Text", MessageFormatter.stripColorCodes(message));
        sendUpdate(cmd, null, false);
    }

    public static class PlayerEventData {
        public static final BuilderCodec<PlayerEventData> CODEC = BuilderCodec.builder(PlayerEventData.class, PlayerEventData::new)
            .append(new KeyedCodec<>("Action", Codec.STRING), (d, s) -> d.action = s, d -> d.action).add()
            .append(new KeyedCodec<>("Player", Codec.STRING), (d, s) -> d.player = s, d -> d.player).add()
            .build();

        private String action;
        private String player;

        public String getAction() { return action; }
        public String getPlayer() { return player; }
    }
}
