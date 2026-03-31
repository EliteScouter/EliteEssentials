package com.eliteessentials.gui;

import com.eliteessentials.EliteEssentials;
import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.permissions.PermissionService;
import com.eliteessentials.services.*;
import com.eliteessentials.util.MessageFormatter;
import com.eliteessentials.util.PlayerSuggestionProvider;
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

import com.eliteessentials.model.Home;
import com.eliteessentials.model.Location;
import com.eliteessentials.model.PlayerFile;
import com.eliteessentials.model.Warp;
import com.eliteessentials.storage.SpawnStorage;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Logger;

/**
 * Single-page Admin UI that swaps content inside #ContentArea based on sidebar navigation.
 * All views (dashboard, players, bans, mutes, warns, stats) are rendered within this one page.
 */
public class AdminDashboardPage extends InteractiveCustomUIPage<AdminDashboardPage.AdminEventData> {

    private static final Logger logger = Logger.getLogger("EliteEssentials");
    private static final long START_TIME = System.currentTimeMillis();
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    private final ConfigManager configManager;
    private String currentView = "dashboard";
    private String selectedPlayer = null;
    private String warnLookupTarget = null;
    private String activityFilter = "all";
    private String pdTarget = null; // Player Data tab target player name
    private UUID pdTargetUuid = null; // Player Data tab target UUID

    public AdminDashboardPage(PlayerRef playerRef, ConfigManager configManager) {
        super(playerRef, CustomPageLifetime.CanDismiss, AdminEventData.CODEC);
        this.configManager = configManager;
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder cmd,
                      UIEventBuilder events, Store<EntityStore> store) {
        cmd.append("Pages/EliteEssentials_AdminDashboard.ui");

        // Set sidebar labels (dashboard is active initially)
        cmd.set("#NavHeader.Text", configManager.getMessage("adminui.nav.header"));
        cmd.set("#NavDashboard.Text", "  " + configManager.getMessage("adminui.nav.dashboard"));
        cmd.set("#NavPlayers.Text", "  " + configManager.getMessage("adminui.nav.players"));
        cmd.set("#NavBans.Text", "  " + configManager.getMessage("adminui.nav.bans"));
        cmd.set("#NavMutes.Text", "  " + configManager.getMessage("adminui.nav.mutes"));
        cmd.set("#NavWarns.Text", "  " + configManager.getMessage("adminui.nav.warnings"));
        cmd.set("#NavStats.Text", "  " + configManager.getMessage("adminui.nav.stats"));
        cmd.set("#NavActivity.Text", "  " + configManager.getMessage("adminui.nav.activity"));
        cmd.set("#NavEconomy.Text", "  " + configManager.getMessage("adminui.nav.economy"));
        cmd.set("#NavTeleports.Text", "  " + configManager.getMessage("adminui.nav.teleports"));
        cmd.set("#NavPlayerData.Text", "  " + configManager.getMessage("adminui.nav.playerdata"));
        cmd.set("#NavToolsHeader.Text", configManager.getMessage("adminui.nav.tools"));
        cmd.set("#VersionLabel.Text", "EliteEssentials");

        // Bind sidebar nav + close
        events.addEventBinding(CustomUIEventBindingType.Activating, "#NavDashboard", EventData.of("Nav", "dashboard"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#NavPlayers", EventData.of("Nav", "players"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#NavBans", EventData.of("Nav", "bans"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#NavMutes", EventData.of("Nav", "mutes"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#NavWarns", EventData.of("Nav", "warns"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#NavStats", EventData.of("Nav", "stats"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#NavActivity", EventData.of("Nav", "activity"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#NavEconomy", EventData.of("Nav", "economy"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#NavTeleports", EventData.of("Nav", "teleports"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#NavPlayerData", EventData.of("Nav", "playerdata"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton", EventData.of("Nav", "close"), false);

        // Build initial dashboard view
        buildDashboardView(cmd, events);
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, AdminEventData data) {
        // Navigation
        if (data.nav != null) {
            switch (data.nav) {
                case "close": this.close(); return;
                case "reload":
                    EliteEssentials.getInstance().reloadConfig();
                    switchView("dashboard");
                    return;
                default:
                    switchView(data.nav);
                    return;
            }
        }

        // Action handling (routed by current view)
        if (data.action != null) {
            switch (currentView) {
                case "players": handlePlayersAction(data, ref, store); break;
                case "bans": handleBansAction(data); break;
                case "mutes": handleMutesAction(data); break;
                case "warns": handleWarnsAction(data); break;
                case "stats": handleStatsAction(data); break;
                case "activity": handleActivityAction(data); break;
                case "economy": handleEconomyAction(data); break;
                case "teleports": handleTeleportsAction(data, ref, store); break;
                case "playerdata": handlePlayerDataAction(data, ref, store); break;
            }
        }
    }

    // ==================== VIEW SWITCHING ====================

    private void switchView(String view) {
        currentView = view;
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder events = new UIEventBuilder();

        // Clear content area
        cmd.clear("#ContentArea");

        // Update page title and active nav highlight
        String title;
        switch (view) {
            case "players": title = configManager.getMessage("adminui.players.title"); break;
            case "bans": title = configManager.getMessage("adminui.bans.title"); break;
            case "mutes": title = configManager.getMessage("adminui.mutes.title"); break;
            case "warns": title = configManager.getMessage("adminui.warns.title"); break;
            case "stats": title = configManager.getMessage("adminui.stats.title"); break;
            case "activity": title = configManager.getMessage("adminui.activity.title"); break;
            case "economy": title = configManager.getMessage("adminui.economy.title"); break;
            case "teleports": title = configManager.getMessage("adminui.teleports.title"); break;
            case "playerdata": title = configManager.getMessage("adminui.playerdata.title"); break;
            default: title = configManager.getMessage("adminui.dashboard.title"); break;
        }
        cmd.set("#PageTitle.Text", title);

        // Highlight active nav button (active = brighter background, inactive = reset)
        updateNavHighlight(cmd, view);

        // Build the view content
        switch (view) {
            case "players": buildPlayersView(cmd, events); break;
            case "bans": buildBansView(cmd, events); break;
            case "mutes": buildMutesView(cmd, events); break;
            case "warns": buildWarnsView(cmd, events); break;
            case "stats": buildStatsView(cmd, events); break;
            case "activity": buildActivityView(cmd, events); break;
            case "economy": buildEconomyView(cmd, events); break;
            case "teleports": buildTeleportsView(cmd, events); break;
            case "playerdata": buildPlayerDataView(cmd, events); break;
            default: buildDashboardView(cmd, events); break;
        }

        sendUpdate(cmd, events, false);
    }

    // ==================== DASHBOARD VIEW ====================

    private void buildDashboardView(UICommandBuilder cmd, UIEventBuilder events) {
        cmd.set("#PageTitle.Text", configManager.getMessage("adminui.dashboard.title"));
        cmd.append("#ContentArea", "Pages/EliteEssentials_AdminDashContent.ui");

        cmd.set("#WelcomeMsg.Text", configManager.getMessage("adminui.dashboard.welcome", "player", playerRef.getUsername()));
        cmd.set("#QuickActionsLabel.Text", configManager.getMessage("adminui.dashboard.quickActions"));
        cmd.set("#RecentLabel.Text", configManager.getMessage("adminui.dashboard.serverInfo"));
        cmd.set("#QuickPlayers.Text", configManager.getMessage("adminui.nav.players").toUpperCase());
        cmd.set("#QuickBans.Text", configManager.getMessage("adminui.nav.bans").toUpperCase());
        cmd.set("#QuickStats.Text", configManager.getMessage("adminui.nav.stats").toUpperCase());
        cmd.set("#QuickReload.Text", configManager.getMessage("adminui.dashboard.reload").toUpperCase());
        cmd.set("#StatusBar.Text", configManager.getMessage("adminui.dashboard.ready"));

        // Quick action events
        events.addEventBinding(CustomUIEventBindingType.Activating, "#QuickPlayers", EventData.of("Nav", "players"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#QuickBans", EventData.of("Nav", "bans"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#QuickStats", EventData.of("Nav", "stats"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#QuickReload", EventData.of("Nav", "reload"), false);

        populateDashboardStats(cmd);
    }

    private void populateDashboardStats(UICommandBuilder cmd) {
        EliteEssentials plugin = EliteEssentials.getInstance();
        int online = 0;
        try { for (PlayerRef p : Universe.get().getPlayers()) online++; } catch (Exception ignored) {}
        cmd.set("#StatPlayers.Text", String.valueOf(online));
        cmd.set("#StatPlayersLabel.Text", configManager.getMessage("adminui.stats.online"));

        // TPS from real measurement
        cmd.set("#StatTPS.Text", TpsTracker.get().getTpsFormatted());
        cmd.set("#StatTpsLabel.Text", configManager.getMessage("adminui.stats.tps"));

        Runtime rt = Runtime.getRuntime();
        long usedMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        long maxMb = rt.maxMemory() / (1024 * 1024);
        cmd.set("#StatMemory.Text", usedMb + " MB");
        cmd.set("#StatMemMax.Text", "/ " + maxMb + " MB");
        cmd.set("#StatMemLabel.Text", configManager.getMessage("adminui.stats.memory"));

        long uptimeMs = System.currentTimeMillis() - START_TIME;
        cmd.set("#StatUptime.Text", (uptimeMs / 3_600_000) + "h " + ((uptimeMs % 3_600_000) / 60_000) + "m");
        cmd.set("#StatUptimeLabel.Text", configManager.getMessage("adminui.stats.uptime"));

        int banCount = 0, muteCount = 0, frozenCount = 0, warpCount = 0, kitCount = 0;
        try { if (plugin.getBanService() != null) banCount = plugin.getBanService().getBanCount(); } catch (Exception ignored) {}
        try { if (plugin.getTempBanService() != null) banCount += plugin.getTempBanService().getBanCount(); } catch (Exception ignored) {}
        try { if (plugin.getMuteService() != null) muteCount = plugin.getMuteService().getMuteCount(); } catch (Exception ignored) {}
        try { if (plugin.getFreezeService() != null) frozenCount = plugin.getFreezeService().getFrozenCount(); } catch (Exception ignored) {}
        try { if (plugin.getWarpService() != null) warpCount = plugin.getWarpService().getAllWarps().size(); } catch (Exception ignored) {}
        try { if (plugin.getKitService() != null) kitCount = plugin.getKitService().getAllKits().size(); } catch (Exception ignored) {}
        cmd.set("#InfoBans.Text", String.valueOf(banCount));
        cmd.set("#InfoMutes.Text", String.valueOf(muteCount));
        cmd.set("#InfoFrozen.Text", String.valueOf(frozenCount));
        cmd.set("#InfoWarps.Text", String.valueOf(warpCount));
        cmd.set("#InfoKits.Text", String.valueOf(kitCount));
    }

    // ==================== PLAYERS VIEW ====================

    private void buildPlayersView(UICommandBuilder cmd, UIEventBuilder events) {
        cmd.append("#ContentArea", "Pages/EliteEssentials_AdminPlayersContent.ui");
        cmd.set("#LookupLabel.Text", configManager.getMessage("adminui.players.lookup"));
        cmd.set("#ActionsLabel.Text", configManager.getMessage("adminui.players.actions"));
        cmd.set("#OnlineLabel.Text", configManager.getMessage("adminui.players.online"));
        cmd.set("#TeleportToButton.Text", configManager.getMessage("adminui.players.tpTo"));
        cmd.set("#TeleportHereButton.Text", configManager.getMessage("adminui.players.tpHere"));
        cmd.set("#HealButton.Text", configManager.getMessage("adminui.players.heal"));
        cmd.set("#FreezeButton.Text", configManager.getMessage("adminui.players.freeze"));
        cmd.set("#KickButton.Text", configManager.getMessage("adminui.players.kick"));
        cmd.set("#LookupButton.Text", configManager.getMessage("adminui.players.lookup"));

        // Player list
        int index = 0;
        for (PlayerRef p : Universe.get().getPlayers()) {
            if (!p.isValid()) continue;
            String sel = "#PlayerListCards[" + index + "]";
            cmd.append("#PlayerListCards", "Pages/EliteEssentials_AdminPlayerEntry.ui");
            cmd.set(sel + " #EntryName.Text", p.getUsername());
            cmd.set(sel + " #EntrySelectButton.Text", configManager.getMessage("adminui.players.select"));
            events.addEventBinding(CustomUIEventBindingType.Activating, sel + " #EntrySelectButton",
                new EventData().append("Action", "select").append("Player", p.getUsername()), false);
            index++;
        }
        if (index == 0) cmd.set("#PlayerStatusMsg.Text", configManager.getMessage("adminui.players.noPlayers"));

        // Action events
        events.addEventBinding(CustomUIEventBindingType.Activating, "#LookupButton",
            new EventData().append("Action", "lookup").append("@LookupInput", "#LookupInput.Value"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#TeleportToButton", EventData.of("Action", "tpto"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#TeleportHereButton", EventData.of("Action", "tphere"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#HealButton", EventData.of("Action", "heal"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#FreezeButton", EventData.of("Action", "freeze"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#KickButton", EventData.of("Action", "kick"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#InfoUuidButton", EventData.of("Action", "uuidlink"), false);
    }

    private void handlePlayersAction(AdminEventData data, Ref<EntityStore> ref, Store<EntityStore> store) {
        switch (data.action) {
            case "select":
                if (data.player != null) { selectedPlayer = data.player; updatePlayerInfo(); }
                break;
            case "lookup":
                if (data.lookupInput != null && !data.lookupInput.isEmpty()) selectedPlayer = data.lookupInput;
                updatePlayerInfo();
                break;
            case "tpto": handleTeleportTo(ref, store); break;
            case "tphere": handleTeleportHere(ref, store); break;
            case "heal": handleHeal(); break;
            case "freeze": handleFreeze(); break;
            case "kick": handleKick(); break;
            case "uuidlink": handleUuidLink(); break;
        }
    }

    private void updatePlayerInfo() {
        UICommandBuilder cmd = new UICommandBuilder();
        if (selectedPlayer == null) return;
        PlayerRef target = PlayerSuggestionProvider.findPlayer(selectedPlayer);
        if (target == null || !target.isValid()) {
            cmd.set("#PlayerInfoPanel.Visible", false);
            cmd.set("#PlayerStatusMsg.Text", configManager.getMessage("playerNotFound", "player", selectedPlayer));
            sendUpdate(cmd, null, false);
            return;
        }

        cmd.set("#PlayerInfoPanel.Visible", true);
        cmd.set("#InfoName.Text", target.getUsername());
        cmd.set("#InfoUuidButton.Text", target.getUuid().toString());

        // Location from ECS
        try {
            Ref<EntityStore> tRef = target.getReference();
            if (tRef != null && tRef.isValid()) {
                Store<EntityStore> tStore = tRef.getStore();
                TransformComponent transform = tStore.getComponent(tRef, TransformComponent.getComponentType());
                if (transform != null) {
                    Vector3d pos = transform.getPosition();
                    String worldName = tStore.getExternalData().getWorld().getName();
                    cmd.set("#InfoLocation.Text", String.format("%.0f, %.0f, %.0f (%s)", pos.x, pos.y, pos.z, worldName));
                }
            }
        } catch (Exception e) {
            cmd.set("#InfoLocation.Text", "-");
        }

        // Player file data (first join, last seen, playtime, wallet, homes, etc.)
        EliteEssentials plugin = EliteEssentials.getInstance();
        com.eliteessentials.model.PlayerFile playerFile = plugin.getPlayerStorageProvider().getPlayer(target.getUuid());
        if (playerFile != null) {
            cmd.set("#InfoFirstJoin.Text", formatTimestamp(playerFile.getFirstJoin()));
            cmd.set("#InfoLastSeen.Text", "Online now");
            
            long playTimeSec = playerFile.getPlayTime();
            if (plugin.getPlayerService() != null) {
                playTimeSec += plugin.getPlayerService().getCurrentSessionSeconds(target.getUuid());
            }
            cmd.set("#InfoPlayTime.Text", PlayerService.formatPlayTime(playTimeSec));
            cmd.set("#InfoWallet.Text", String.format("%.2f", playerFile.getWallet()));
            cmd.set("#InfoHomes.Text", String.valueOf(playerFile.getHomeCount()));

            // Nickname
            if (playerFile.hasNickname()) {
                cmd.set("#InfoNickname.Text", playerFile.getNickname());
            } else {
                cmd.set("#InfoNickname.Text", "None");
            }

            // Kit claims
            java.util.Set<String> kitClaims = playerFile.getKitClaims();
            if (kitClaims != null && !kitClaims.isEmpty()) {
                cmd.set("#InfoKitClaims.Text", String.join(", ", kitClaims));
            } else {
                cmd.set("#InfoKitClaims.Text", "None");
            }
        }

        // Status flags
        StringBuilder status = new StringBuilder();
        if (plugin.getFreezeService() != null && plugin.getFreezeService().isFrozen(target.getUuid())) status.append("FROZEN ");
        if (plugin.getGodService() != null && plugin.getGodService().isGodMode(target.getUuid())) status.append("GOD ");
        if (plugin.getVanishService() != null && plugin.getVanishService().isVanished(target.getUuid())) status.append("VANISHED ");
        if (plugin.getMuteService() != null && plugin.getMuteService().isMuted(target.getUuid())) status.append("MUTED ");
        cmd.set("#InfoStatus.Text", status.length() > 0 ? status.toString().trim() : "Normal");

        // Punishment summary
        StringBuilder punishments = new StringBuilder();
        if (plugin.getMuteService() != null && plugin.getMuteService().isMuted(target.getUuid())) punishments.append("Muted ");
        if (plugin.getBanService() != null && plugin.getBanService().isBanned(target.getUuid())) punishments.append("Banned ");
        if (plugin.getTempBanService() != null && plugin.getTempBanService().isTempBanned(target.getUuid())) punishments.append("TempBanned ");
        if (plugin.getFreezeService() != null && plugin.getFreezeService().isFrozen(target.getUuid())) punishments.append("Frozen ");
        int warnCount = plugin.getWarnService() != null ? plugin.getWarnService().getWarningCount(target.getUuid()) : 0;
        if (warnCount > 0) punishments.append(warnCount).append(" warning(s)");
        cmd.set("#InfoPunishments.Text", punishments.length() > 0 ? punishments.toString().trim() : "None");
        cmd.set("#InfoWarnings.Text", String.valueOf(warnCount));

        cmd.set("#PlayerStatusMsg.Text", configManager.getMessage("adminui.players.selected", "player", target.getUsername()));
        sendUpdate(cmd, null, false);
    }

    private static String formatTimestamp(long millis) {
        if (millis <= 0) return "Unknown";
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(new java.util.Date(millis));
    }

    private void handleTeleportTo(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (!hasAdminPermission(Permissions.ADMIN_TELEPORT)) {
            setStatus("#PlayerStatusMsg", configManager.getMessage("noPermission"));
            return;
        }
        if (selectedPlayer == null) return;
        PlayerRef target = PlayerSuggestionProvider.findPlayer(selectedPlayer);
        if (target == null || !target.isValid()) { setStatus("#PlayerStatusMsg", configManager.getMessage("playerNotFound", "player", selectedPlayer)); return; }
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
        } catch (Exception e) { setStatus("#PlayerStatusMsg", "Teleport failed"); }
    }

    private void handleTeleportHere(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (!hasAdminPermission(Permissions.ADMIN_TELEPORT)) {
            setStatus("#PlayerStatusMsg", configManager.getMessage("noPermission"));
            return;
        }
        if (selectedPlayer == null) return;
        PlayerRef target = PlayerSuggestionProvider.findPlayer(selectedPlayer);
        if (target == null || !target.isValid()) { setStatus("#PlayerStatusMsg", configManager.getMessage("playerNotFound", "player", selectedPlayer)); return; }
        try {
            TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
            if (transform != null) {
                Vector3d pos = transform.getPosition();
                World world = store.getExternalData().getWorld();
                TeleportUtil.safeTeleport(world, world, pos, new Vector3f(0, 0, 0), target, () -> {}, () -> {});
                setStatus("#PlayerStatusMsg", configManager.getMessage("adminui.players.tpHereSuccess", "player", target.getUsername()));
            }
        } catch (Exception e) { setStatus("#PlayerStatusMsg", "Teleport failed"); }
    }

    private void handleHeal() {
        if (!hasAdminPermission(Permissions.ADMIN_HEAL)) {
            setStatus("#PlayerStatusMsg", configManager.getMessage("noPermission"));
            return;
        }
        if (selectedPlayer == null) return;
        PlayerRef target = PlayerSuggestionProvider.findPlayer(selectedPlayer);
        if (target == null || !target.isValid()) { setStatus("#PlayerStatusMsg", configManager.getMessage("playerNotFound", "player", selectedPlayer)); return; }
        try {
            Ref<EntityStore> tRef = target.getReference();
            Store<EntityStore> tStore = tRef.getStore();
            World tWorld = tStore.getExternalData().getWorld();
            tWorld.execute(() -> {
                EntityStatMap statMap = tStore.getComponent(tRef, EntityStatMap.getComponentType());
                if (statMap != null) statMap.maximizeStatValue(DefaultEntityStatTypes.getHealth());
            });
            setStatus("#PlayerStatusMsg", configManager.getMessage("adminui.players.healed", "player", target.getUsername()));
        } catch (Exception e) { setStatus("#PlayerStatusMsg", "Heal failed"); }
    }

    private void handleFreeze() {
        if (!hasAdminPermission(Permissions.ADMIN_FREEZE)) {
            setStatus("#PlayerStatusMsg", configManager.getMessage("noPermission"));
            return;
        }
        if (selectedPlayer == null) return;
        PlayerRef target = PlayerSuggestionProvider.findPlayer(selectedPlayer);
        if (target == null || !target.isValid()) { setStatus("#PlayerStatusMsg", configManager.getMessage("playerNotFound", "player", selectedPlayer)); return; }
        FreezeService fs = EliteEssentials.getInstance().getFreezeService();
        if (fs == null) return;

        Ref<EntityStore> tRef = target.getReference();
        if (tRef == null || !tRef.isValid()) return;
        Store<EntityStore> tStore = tRef.getStore();
        EntityStore entityStore = tStore.getExternalData();
        World targetWorld = entityStore != null ? entityStore.getWorld() : null;

        if (fs.isFrozen(target.getUuid())) {
            fs.unfreeze(target.getUuid());
            if (targetWorld != null) {
                final PlayerRef ft = target;
                targetWorld.execute(() -> FreezeService.removeFreeze(tStore, tRef, ft));
            }
            setStatus("#PlayerStatusMsg", configManager.getMessage("adminui.players.unfrozen", "player", target.getUsername()));
            logActivity("UNFREEZE", playerRef.getUsername(), target.getUsername(), "");
        } else {
            fs.freeze(target.getUuid(), target.getUsername(), playerRef.getUsername());
            if (targetWorld != null) {
                final PlayerRef ft = target;
                targetWorld.execute(() -> FreezeService.applyFreeze(tStore, tRef, ft));
            }
            setStatus("#PlayerStatusMsg", configManager.getMessage("adminui.players.frozen", "player", target.getUsername()));
            logActivity("FREEZE", playerRef.getUsername(), target.getUsername(), "");
        }
        updatePlayerInfo();
    }

    private void handleKick() {
        if (!hasAdminPermission(Permissions.ADMIN_KICK)) {
            setStatus("#PlayerStatusMsg", configManager.getMessage("noPermission"));
            return;
        }
        if (selectedPlayer == null) return;
        PlayerRef target = PlayerSuggestionProvider.findPlayer(selectedPlayer);
        if (target == null || !target.isValid()) { setStatus("#PlayerStatusMsg", configManager.getMessage("playerNotFound", "player", selectedPlayer)); return; }
        try {
            target.getPacketHandler().disconnect(com.hypixel.hytale.server.core.Message.raw(MessageFormatter.stripColorCodes(configManager.getMessage("adminui.players.kickReason"))));
            setStatus("#PlayerStatusMsg", configManager.getMessage("adminui.players.kicked", "player", target.getUsername()));
            logActivity("KICK", playerRef.getUsername(), target.getUsername(), "via Admin UI");
            selectedPlayer = null;
        } catch (Exception e) { setStatus("#PlayerStatusMsg", "Kick failed"); }
    }

    private void handleUuidLink() {
        if (selectedPlayer == null) return;
        PlayerRef target = PlayerSuggestionProvider.findPlayer(selectedPlayer);
        if (target == null) return;
        String uuid = target.getUuid().toString();
        String url = "https://hytaleid.com/?q=" + uuid;
        // Close the UI so the admin sees the clickable link in chat
        this.close();
        playerRef.sendMessage(
            com.hypixel.hytale.server.core.Message.join(
                com.hypixel.hytale.server.core.Message.raw("Click to view profile: ").color("#AAAAAA"),
                com.hypixel.hytale.server.core.Message.raw(uuid).color("#55AAFF").link(url)
            )
        );
    }

    // ==================== BANS VIEW ====================

    private void buildBansView(UICommandBuilder cmd, UIEventBuilder events) {
        cmd.append("#ContentArea", "Pages/EliteEssentials_AdminBansContent.ui");
        cmd.set("#BanLabel.Text", configManager.getMessage("adminui.bans.banPlayer"));
        cmd.set("#UnbanLabel.Text", configManager.getMessage("adminui.bans.unbanPlayer"));
        cmd.set("#ActiveLabel.Text", configManager.getMessage("adminui.bans.activeBans"));
        cmd.set("#BanButton.Text", configManager.getMessage("adminui.bans.ban"));
        cmd.set("#TempBanButton.Text", configManager.getMessage("adminui.bans.tempBan"));
        cmd.set("#IpBanButton.Text", configManager.getMessage("adminui.bans.ipBan"));
        cmd.set("#UnbanButton.Text", configManager.getMessage("adminui.bans.unban"));

        // Pre-fill player name if one was selected on the Players page
        if (selectedPlayer != null) {
            cmd.set("#BanPlayerInput.Value", selectedPlayer);
        }

        populateBanList(cmd);

        events.addEventBinding(CustomUIEventBindingType.Activating, "#BanButton",
            new EventData().append("Action", "ban").append("@BanPlayerInput", "#BanPlayerInput.Value").append("@BanReasonInput", "#BanReasonInput.Value"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#TempBanButton",
            new EventData().append("Action", "tempban").append("@BanPlayerInput", "#BanPlayerInput.Value").append("@BanDurationInput", "#BanDurationInput.Value").append("@BanReasonInput", "#BanReasonInput.Value"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#IpBanButton",
            new EventData().append("Action", "ipban").append("@BanPlayerInput", "#BanPlayerInput.Value").append("@BanReasonInput", "#BanReasonInput.Value"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#UnbanButton",
            new EventData().append("Action", "unban").append("@UnbanPlayerInput", "#UnbanPlayerInput.Value"), false);
    }

    private void populateBanList(UICommandBuilder cmd) {
        EliteEssentials plugin = EliteEssentials.getInstance();
        StringBuilder banText = new StringBuilder();
        if (plugin.getBanService() != null) {
            for (Map.Entry<String, BanService.BanEntry> e : plugin.getBanService().getAllBans().entrySet()) {
                String name = e.getValue().playerName != null ? e.getValue().playerName : e.getKey();
                banText.append(name).append(" (perm) - ").append(e.getValue().reason != null ? e.getValue().reason : "No reason").append("\n");
            }
        }
        if (plugin.getTempBanService() != null) {
            for (Map.Entry<String, TempBanService.TempBanEntry> e : plugin.getTempBanService().getAllTempBans().entrySet()) {
                String name = e.getValue().playerName != null ? e.getValue().playerName : e.getKey();
                long remaining = e.getValue().getRemainingTime();
                banText.append(name).append(" (").append(formatDuration(remaining)).append(") - ").append(e.getValue().reason != null ? e.getValue().reason : "No reason").append("\n");
            }
        }
        cmd.set("#BanList.Text", banText.length() > 0 ? banText.toString().trim() : configManager.getMessage("adminui.bans.noBans"));
    }

    private void handleBansAction(AdminEventData data) {
        if (!hasAdminPermission(Permissions.ADMIN_BAN)) {
            setStatus("#BanStatusMsg", configManager.getMessage("noPermission"));
            return;
        }
        EliteEssentials plugin = EliteEssentials.getInstance();
        String adminName = playerRef.getUsername();
        switch (data.action) {
            case "ban": {
                if (data.banPlayer == null || data.banPlayer.isEmpty()) { setStatus("#BanStatusMsg", configManager.getMessage("adminui.bans.enterName")); return; }
                PlayerRef target = PlayerSuggestionProvider.findPlayer(data.banPlayer);
                if (target != null && target.isValid()) {
                    boolean ok = plugin.getBanService().ban(target.getUuid(), target.getUsername(), adminName, data.banReason != null ? data.banReason : "Banned via Admin UI");
                    if (ok) {
                        try { target.getPacketHandler().disconnect(com.hypixel.hytale.server.core.Message.raw(MessageFormatter.stripColorCodes(configManager.getMessage("adminui.bans.banKickMsg")))); } catch (Exception ignored) {}
                        setStatus("#BanStatusMsg", configManager.getMessage("adminui.bans.banned", "player", target.getUsername()));
                        logActivity("BAN", adminName, target.getUsername(), data.banReason != null ? data.banReason : "No reason");
                    } else { setStatus("#BanStatusMsg", configManager.getMessage("adminui.bans.alreadyBanned", "player", target.getUsername())); }
                } else { setStatus("#BanStatusMsg", configManager.getMessage("playerNotFound", "player", data.banPlayer)); }
                refreshBanList();
                break;
            }
            case "tempban": {
                if (data.banPlayer == null || data.banPlayer.isEmpty()) { setStatus("#BanStatusMsg", configManager.getMessage("adminui.bans.enterName")); return; }
                long durationMs = parseDuration(data.banDuration);
                if (durationMs <= 0) { setStatus("#BanStatusMsg", configManager.getMessage("adminui.bans.invalidDuration")); return; }
                PlayerRef target = PlayerSuggestionProvider.findPlayer(data.banPlayer);
                if (target != null && target.isValid()) {
                    boolean ok = plugin.getTempBanService().tempBan(target.getUuid(), target.getUsername(), adminName, data.banReason != null ? data.banReason : "Temp banned via Admin UI", durationMs);
                    if (ok) {
                        try { target.getPacketHandler().disconnect(com.hypixel.hytale.server.core.Message.raw(MessageFormatter.stripColorCodes(configManager.getMessage("adminui.bans.banKickMsg")))); } catch (Exception ignored) {}
                        setStatus("#BanStatusMsg", configManager.getMessage("adminui.bans.tempBanned", "player", target.getUsername(), "duration", data.banDuration));
                        logActivity("TEMPBAN", adminName, target.getUsername(), data.banDuration + (data.banReason != null ? " - " + data.banReason : ""));
                    } else { setStatus("#BanStatusMsg", configManager.getMessage("adminui.bans.alreadyBanned", "player", target.getUsername())); }
                } else { setStatus("#BanStatusMsg", configManager.getMessage("playerNotFound", "player", data.banPlayer)); }
                refreshBanList();
                break;
            }
            case "ipban": {
                if (data.banPlayer == null || data.banPlayer.isEmpty()) { setStatus("#BanStatusMsg", configManager.getMessage("adminui.bans.enterName")); return; }
                PlayerRef target = PlayerSuggestionProvider.findPlayer(data.banPlayer);
                if (target != null && target.isValid()) {
                    try {
                        String ip = IpBanService.getIpFromPacketHandler(target.getPacketHandler());
                        if (ip != null) {
                            plugin.getIpBanService().banIp(ip, target.getUuid(), target.getUsername(), adminName, data.banReason != null ? data.banReason : "IP banned via Admin UI");
                            try { target.getPacketHandler().disconnect(com.hypixel.hytale.server.core.Message.raw(MessageFormatter.stripColorCodes(configManager.getMessage("adminui.bans.banKickMsg")))); } catch (Exception ignored) {}
                            setStatus("#BanStatusMsg", configManager.getMessage("adminui.bans.ipBanned", "player", target.getUsername()));
                            logActivity("IPBAN", adminName, target.getUsername(), data.banReason != null ? data.banReason : "No reason");
                        } else { setStatus("#BanStatusMsg", configManager.getMessage("adminui.bans.ipFailed")); }
                    } catch (Exception e) { setStatus("#BanStatusMsg", configManager.getMessage("adminui.bans.ipFailed")); }
                } else { setStatus("#BanStatusMsg", configManager.getMessage("playerNotFound", "player", data.banPlayer)); }
                refreshBanList();
                break;
            }
            case "unban": {
                if (data.unbanPlayer == null || data.unbanPlayer.isEmpty()) { setStatus("#BanStatusMsg", configManager.getMessage("adminui.bans.enterName")); return; }
                UUID unbanned = plugin.getBanService().unbanByName(data.unbanPlayer);
                if (unbanned == null) unbanned = plugin.getTempBanService().unbanByName(data.unbanPlayer);
                if (unbanned != null) logActivity("UNBAN", adminName, data.unbanPlayer, "");
                setStatus("#BanStatusMsg", unbanned != null
                    ? configManager.getMessage("adminui.bans.unbanned", "player", data.unbanPlayer)
                    : configManager.getMessage("adminui.bans.notBanned", "player", data.unbanPlayer));
                refreshBanList();
                break;
            }
        }
    }

    private void refreshBanList() {
        UICommandBuilder cmd = new UICommandBuilder();
        populateBanList(cmd);
        sendUpdate(cmd, null, false);
    }

    // ==================== MUTES VIEW ====================

    private void buildMutesView(UICommandBuilder cmd, UIEventBuilder events) {
        cmd.append("#ContentArea", "Pages/EliteEssentials_AdminMutesContent.ui");
        cmd.set("#MuteLabel.Text", configManager.getMessage("adminui.mutes.mutePlayer"));
        cmd.set("#UnmuteLabel.Text", configManager.getMessage("adminui.mutes.unmutePlayer"));
        cmd.set("#ActiveLabel.Text", configManager.getMessage("adminui.mutes.activeMutes"));
        cmd.set("#MuteButton.Text", configManager.getMessage("adminui.mutes.mute"));
        cmd.set("#UnmuteButton.Text", configManager.getMessage("adminui.mutes.unmute"));

        // Pre-fill player name if one was selected on the Players page
        if (selectedPlayer != null) {
            cmd.set("#MutePlayerInput.Value", selectedPlayer);
        }

        populateMuteList(cmd);

        events.addEventBinding(CustomUIEventBindingType.Activating, "#MuteButton",
            new EventData().append("Action", "mute").append("@MutePlayerInput", "#MutePlayerInput.Value").append("@MuteReasonInput", "#MuteReasonInput.Value"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#UnmuteButton",
            new EventData().append("Action", "unmute").append("@UnmutePlayerInput", "#UnmutePlayerInput.Value"), false);
    }

    private void populateMuteList(UICommandBuilder cmd) {
        MuteService ms = EliteEssentials.getInstance().getMuteService();
        if (ms == null || ms.getAllMutes().isEmpty()) {
            cmd.set("#MuteList.Text", configManager.getMessage("adminui.mutes.noMutes"));
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, MuteService.MuteEntry> e : ms.getAllMutes().entrySet()) {
            String name = e.getValue().playerName != null ? e.getValue().playerName : e.getKey();
            sb.append(name).append(" - ").append(e.getValue().reason != null ? e.getValue().reason : "No reason").append("\n");
        }
        cmd.set("#MuteList.Text", sb.toString().trim());
    }

    private void handleMutesAction(AdminEventData data) {
        if (!hasAdminPermission(Permissions.ADMIN_MUTE)) {
            setStatus("#MuteStatusMsg", configManager.getMessage("noPermission"));
            return;
        }
        MuteService ms = EliteEssentials.getInstance().getMuteService();
        if (ms == null) return;
        switch (data.action) {
            case "mute": {
                if (data.mutePlayer == null || data.mutePlayer.isEmpty()) { setStatus("#MuteStatusMsg", configManager.getMessage("adminui.mutes.enterName")); return; }
                PlayerRef target = PlayerSuggestionProvider.findPlayer(data.mutePlayer);
                if (target != null && target.isValid()) {
                    boolean ok = ms.mute(target.getUuid(), target.getUsername(), playerRef.getUsername(), data.muteReason != null ? data.muteReason : "Muted via Admin UI");
                    setStatus("#MuteStatusMsg", ok ? configManager.getMessage("adminui.mutes.muted", "player", target.getUsername()) : configManager.getMessage("adminui.mutes.alreadyMuted", "player", target.getUsername()));
                    if (ok) logActivity("MUTE", playerRef.getUsername(), target.getUsername(), data.muteReason != null ? data.muteReason : "No reason");
                } else { setStatus("#MuteStatusMsg", configManager.getMessage("playerNotFound", "player", data.mutePlayer)); }
                UICommandBuilder cmd = new UICommandBuilder(); populateMuteList(cmd); sendUpdate(cmd, null, false);
                break;
            }
            case "unmute": {
                if (data.unmutePlayer == null || data.unmutePlayer.isEmpty()) { setStatus("#MuteStatusMsg", configManager.getMessage("adminui.mutes.enterName")); return; }
                UUID unmuted = ms.unmuteByName(data.unmutePlayer);
                if (unmuted != null) logActivity("UNMUTE", playerRef.getUsername(), data.unmutePlayer, "");
                setStatus("#MuteStatusMsg", unmuted != null ? configManager.getMessage("adminui.mutes.unmuted", "player", data.unmutePlayer) : configManager.getMessage("adminui.mutes.notMuted", "player", data.unmutePlayer));
                UICommandBuilder cmd = new UICommandBuilder(); populateMuteList(cmd); sendUpdate(cmd, null, false);
                break;
            }
        }
    }

    // ==================== WARNS VIEW ====================

    private void buildWarnsView(UICommandBuilder cmd, UIEventBuilder events) {
        cmd.append("#ContentArea", "Pages/EliteEssentials_AdminWarnsContent.ui");
        cmd.set("#WarnLabel.Text", configManager.getMessage("adminui.warns.warnPlayer"));
        cmd.set("#LookupLabel.Text", configManager.getMessage("adminui.warns.lookupWarnings"));
        cmd.set("#WarnButton.Text", configManager.getMessage("adminui.warns.warn"));
        cmd.set("#ClearWarningsButton.Text", configManager.getMessage("adminui.warns.clearWarnings"));
        cmd.set("#WarnLookupButton.Text", configManager.getMessage("adminui.warns.lookupBtn"));
        cmd.set("#WarnList.Text", configManager.getMessage("adminui.warns.noWarnings"));

        // Pre-fill player name if one was selected on the Players page
        if (selectedPlayer != null) {
            cmd.set("#WarnPlayerInput.Value", selectedPlayer);
        }

        events.addEventBinding(CustomUIEventBindingType.Activating, "#WarnButton",
            new EventData().append("Action", "warn").append("@WarnPlayerInput", "#WarnPlayerInput.Value").append("@WarnReasonInput", "#WarnReasonInput.Value"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ClearWarningsButton",
            new EventData().append("Action", "clearwarns").append("@WarnPlayerInput", "#WarnPlayerInput.Value"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#WarnLookupButton",
            new EventData().append("Action", "warnlookup").append("@LookupPlayerInput", "#LookupPlayerInput.Value"), false);
    }

    private void handleWarnsAction(AdminEventData data) {
        if (!hasAdminPermission(Permissions.ADMIN_WARN)) {
            setStatus("#WarnStatusMsg", configManager.getMessage("noPermission"));
            return;
        }
        WarnService ws = EliteEssentials.getInstance().getWarnService();
        if (ws == null) return;
        switch (data.action) {
            case "warn": {
                if (data.warnPlayer == null || data.warnPlayer.isEmpty()) { setStatus("#WarnStatusMsg", configManager.getMessage("adminui.warns.enterName")); return; }
                PlayerRef target = PlayerSuggestionProvider.findPlayer(data.warnPlayer);
                if (target != null && target.isValid()) {
                    int count = ws.warn(target.getUuid(), target.getUsername(), playerRef.getUsername(), data.warnReason != null ? data.warnReason : "Warned via Admin UI");
                    setStatus("#WarnStatusMsg", configManager.getMessage("adminui.warns.warned", "player", target.getUsername(), "count", String.valueOf(count)));
                    logActivity("WARN", playerRef.getUsername(), target.getUsername(), data.warnReason != null ? data.warnReason : "No reason");
                    warnLookupTarget = data.warnPlayer;
                    refreshWarnList(target.getUuid());
                } else { setStatus("#WarnStatusMsg", configManager.getMessage("playerNotFound", "player", data.warnPlayer)); }
                break;
            }
            case "clearwarns": {
                String name = (data.warnPlayer != null && !data.warnPlayer.isEmpty()) ? data.warnPlayer : warnLookupTarget;
                if (name == null || name.isEmpty()) { setStatus("#WarnStatusMsg", configManager.getMessage("adminui.warns.enterName")); return; }
                PlayerRef target = PlayerSuggestionProvider.findPlayer(name);
                if (target != null) {
                    int cleared = ws.clearWarnings(target.getUuid());
                    setStatus("#WarnStatusMsg", configManager.getMessage("adminui.warns.cleared", "player", target.getUsername(), "count", String.valueOf(cleared)));
                    if (cleared > 0) logActivity("CLEARWARNS", playerRef.getUsername(), target.getUsername(), cleared + " warnings cleared");
                    refreshWarnList(target.getUuid());
                } else { setStatus("#WarnStatusMsg", configManager.getMessage("playerNotFound", "player", name)); }
                break;
            }
            case "warnlookup": {
                if (data.lookupPlayer == null || data.lookupPlayer.isEmpty()) { setStatus("#WarnStatusMsg", configManager.getMessage("adminui.warns.enterName")); return; }
                warnLookupTarget = data.lookupPlayer;
                PlayerRef target = PlayerSuggestionProvider.findPlayer(data.lookupPlayer);
                if (target != null) { refreshWarnList(target.getUuid()); }
                else { setStatus("#WarnStatusMsg", configManager.getMessage("playerNotFound", "player", data.lookupPlayer)); }
                break;
            }
        }
    }

    private void refreshWarnList(UUID playerId) {
        WarnService ws = EliteEssentials.getInstance().getWarnService();
        if (ws == null) return;
        UICommandBuilder cmd = new UICommandBuilder();
        List<WarnService.WarnEntry> warnings = ws.getWarnings(playerId);
        if (warnings.isEmpty()) {
            cmd.set("#WarnList.Text", configManager.getMessage("adminui.warns.noWarnings"));
        } else {
            StringBuilder sb = new StringBuilder();
            int i = 1;
            for (WarnService.WarnEntry w : warnings) {
                sb.append("#").append(i).append(" [").append(DATE_FORMAT.format(new Date(w.warnedAt))).append("] by ").append(w.warnedBy).append(": ").append(w.reason).append("\n");
                i++;
            }
            cmd.set("#WarnList.Text", sb.toString().trim());
        }
        sendUpdate(cmd, null, false);
    }

    // ==================== STATS VIEW ====================

    private void buildStatsView(UICommandBuilder cmd, UIEventBuilder events) {
        cmd.append("#ContentArea", "Pages/EliteEssentials_AdminStatsContent.ui");
        cmd.set("#StPlayersLabel.Text", configManager.getMessage("adminui.stats.playersOnline"));
        cmd.set("#StTpsLabel.Text", configManager.getMessage("adminui.stats.serverTps"));
        cmd.set("#StMemLabel.Text", configManager.getMessage("adminui.stats.memoryUsed"));
        cmd.set("#StFreeLabel.Text", configManager.getMessage("adminui.stats.freeMemory"));
        cmd.set("#StUptimeLabel.Text", configManager.getMessage("adminui.stats.uptime"));
        cmd.set("#StJavaLabel.Text", configManager.getMessage("adminui.stats.javaVersion"));
        cmd.set("#RefreshStatsButton.Text", configManager.getMessage("adminui.stats.refresh"));
        populateStatsView(cmd);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#RefreshStatsButton", EventData.of("Action", "refreshstats"), false);
    }

    private void populateStatsView(UICommandBuilder cmd) {
        int online = 0;
        try { for (PlayerRef p : Universe.get().getPlayers()) online++; } catch (Exception ignored) {}
        cmd.set("#StPlayers.Text", String.valueOf(online));
        cmd.set("#StPlayersMax.Text", online + " online");

        // TPS
        String tpsFormatted = TpsTracker.get().getTpsFormatted();
        int targetTps = 30;
        try {
            World w = Universe.get().getWorld("default");
            if (w != null) targetTps = w.getTps();
        } catch (Exception ignored) {}
        cmd.set("#StTPS.Text", tpsFormatted);
        cmd.set("#StTpsTarget.Text", "/ " + targetTps + ".0 target");

        // Memory - use maxMemory for the real picture
        Runtime rt = Runtime.getRuntime();
        long usedMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        long maxMb = rt.maxMemory() / (1024 * 1024);
        long freeMb = maxMb - usedMb;
        cmd.set("#StMemory.Text", usedMb + " MB");
        cmd.set("#StMemMax.Text", "/ " + maxMb + " MB max");
        cmd.set("#StFreeMem.Text", freeMb + " MB");

        long uptimeMs = System.currentTimeMillis() - START_TIME;
        cmd.set("#StUptime.Text", (uptimeMs / 3_600_000) + "h " + ((uptimeMs % 3_600_000) / 60_000) + "m");
        cmd.set("#StJava.Text", System.getProperty("java.version", "?"));
        cmd.set("#StOS.Text", System.getProperty("os.name", "") + " " + System.getProperty("os.arch", ""));
        cmd.set("#StStatusMsg.Text", configManager.getMessage("adminui.stats.loaded"));
    }

    private void handleStatsAction(AdminEventData data) {
        if ("refreshstats".equals(data.action)) {
            UICommandBuilder cmd = new UICommandBuilder();
            populateStatsView(cmd);
            sendUpdate(cmd, null, false);
        }
    }

    // ==================== ACTIVITY LOG VIEW ====================

    private void buildActivityView(UICommandBuilder cmd, UIEventBuilder events) {
        cmd.append("#ContentArea", "Pages/EliteEssentials_AdminActivityContent.ui");
        cmd.set("#ActivityHeader.Text", configManager.getMessage("adminui.activity.header"));
        cmd.set("#RefreshActivityButton.Text", configManager.getMessage("adminui.stats.refresh"));
        populateActivityLog(cmd);

        events.addEventBinding(CustomUIEventBindingType.Activating, "#FilterAll", EventData.of("Action", "filter_all"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#FilterBans", EventData.of("Action", "filter_ban"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#FilterMutes", EventData.of("Action", "filter_mute"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#FilterWarns", EventData.of("Action", "filter_warn"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#FilterKicks", EventData.of("Action", "filter_kick"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#RefreshActivityButton", EventData.of("Action", "refreshactivity"), false);
    }

    private void populateActivityLog(UICommandBuilder cmd) {
        ActivityLogService als = ActivityLogService.get();
        if (als == null || als.getCount() == 0) {
            cmd.set("#ActivityLog.Text", configManager.getMessage("adminui.activity.empty"));
            cmd.set("#ActivityCount.Text", "0 entries");
            return;
        }
        List<ActivityLogService.LogEntry> entries = als.getByType(activityFilter);
        if (entries.isEmpty()) {
            cmd.set("#ActivityLog.Text", configManager.getMessage("adminui.activity.emptyFilter"));
            cmd.set("#ActivityCount.Text", "0 matching");
            return;
        }
        StringBuilder sb = new StringBuilder();
        int shown = Math.min(entries.size(), 50);
        for (int i = 0; i < shown; i++) {
            ActivityLogService.LogEntry e = entries.get(i);
            sb.append("[").append(DATE_FORMAT.format(new Date(e.timestamp))).append("] ");
            sb.append(e.type).append(" | ").append(e.admin).append(" -> ").append(e.target);
            if (e.detail != null && !e.detail.isEmpty()) sb.append(" (").append(e.detail).append(")");
            sb.append("\n");
        }
        cmd.set("#ActivityLog.Text", sb.toString().trim());
        cmd.set("#ActivityCount.Text", entries.size() + " entries" + (!"all".equals(activityFilter) ? " (filtered: " + activityFilter + ")" : ""));
    }

    private void handleActivityAction(AdminEventData data) {
        if (data.action.startsWith("filter_")) {
            String filter = data.action.substring(7);
            activityFilter = "all".equals(filter) ? "all" : filter;
            UICommandBuilder cmd = new UICommandBuilder();
            populateActivityLog(cmd);
            updateActivityFilterHighlight(cmd);
            sendUpdate(cmd, null, false);
        } else if ("refreshactivity".equals(data.action)) {
            UICommandBuilder cmd = new UICommandBuilder();
            populateActivityLog(cmd);
            sendUpdate(cmd, null, false);
        }
    }

    /** Highlight the active filter button using Disabled state (gold via @AdminFilterBtn). */
    private void updateActivityFilterHighlight(UICommandBuilder cmd) {
        cmd.set("#FilterAll.Disabled", "all".equals(activityFilter));
        cmd.set("#FilterBans.Disabled", "ban".equals(activityFilter));
        cmd.set("#FilterMutes.Disabled", "mute".equals(activityFilter));
        cmd.set("#FilterWarns.Disabled", "warn".equals(activityFilter));
        cmd.set("#FilterKicks.Disabled", "kick".equals(activityFilter));
    }

    // ==================== ECONOMY VIEW ====================

    private void buildEconomyView(UICommandBuilder cmd, UIEventBuilder events) {
        cmd.append("#ContentArea", "Pages/EliteEssentials_AdminEconomyContent.ui");
        cmd.set("#ManageLabel.Text", configManager.getMessage("adminui.economy.manage"));
        cmd.set("#TopLabel.Text", configManager.getMessage("adminui.economy.topBalances"));
        cmd.set("#EcoSetButton.Text", configManager.getMessage("adminui.economy.set"));
        cmd.set("#EcoAddButton.Text", configManager.getMessage("adminui.economy.add"));
        cmd.set("#EcoRemoveButton.Text", configManager.getMessage("adminui.economy.remove"));
        cmd.set("#EcoCheckButton.Text", configManager.getMessage("adminui.economy.check"));

        // Pre-fill player name if one was selected on the Players page
        if (selectedPlayer != null) {
            cmd.set("#EcoPlayerInput.Value", selectedPlayer);
        }

        populateEconomyStats(cmd);
        populateEconomyTop(cmd);

        EventData setData = new EventData().append("Action", "ecoset").append("@EcoPlayerInput", "#EcoPlayerInput.Value").append("@EcoAmountInput", "#EcoAmountInput.Value");
        EventData addData = new EventData().append("Action", "ecoadd").append("@EcoPlayerInput", "#EcoPlayerInput.Value").append("@EcoAmountInput", "#EcoAmountInput.Value");
        EventData removeData = new EventData().append("Action", "ecoremove").append("@EcoPlayerInput", "#EcoPlayerInput.Value").append("@EcoAmountInput", "#EcoAmountInput.Value");
        EventData checkData = new EventData().append("Action", "ecocheck").append("@EcoPlayerInput", "#EcoPlayerInput.Value");
        events.addEventBinding(CustomUIEventBindingType.Activating, "#EcoSetButton", setData, false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#EcoAddButton", addData, false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#EcoRemoveButton", removeData, false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#EcoCheckButton", checkData, false);
    }

    private void populateEconomyStats(UICommandBuilder cmd) {
        EliteEssentials plugin = EliteEssentials.getInstance();
        PlayerService ps = plugin.getPlayerService();
        if (ps == null) return;
        List<PlayerFile> topPlayers = ps.getTopByBalance(999);
        double total = 0;
        int count = 0;
        for (PlayerFile pf : topPlayers) {
            total += pf.getWallet();
            count++;
        }
        String currency = plugin.getConfigManager().getConfig().economy.currencySymbol;
        cmd.set("#EcoTotal.Text", String.format("%.2f %s", total, currency));
        cmd.set("#EcoAverage.Text", count > 0 ? String.format("%.2f %s", total / count, currency) : "0.00");
        cmd.set("#EcoPlayerCount.Text", String.valueOf(count));
    }

    private void populateEconomyTop(UICommandBuilder cmd) {
        EliteEssentials plugin = EliteEssentials.getInstance();
        PlayerService ps = plugin.getPlayerService();
        if (ps == null) { cmd.set("#EcoTopList.Text", "Economy disabled"); return; }
        List<PlayerFile> top = ps.getTopByBalance(10);
        if (top.isEmpty()) { cmd.set("#EcoTopList.Text", "No balance data"); return; }
        String currency = plugin.getConfigManager().getConfig().economy.currencySymbol;
        StringBuilder sb = new StringBuilder();
        int rank = 1;
        for (PlayerFile pf : top) {
            sb.append("#").append(rank).append("  ").append(pf.getName())
              .append("  -  ").append(String.format("%.2f %s", pf.getWallet(), currency)).append("\n");
            rank++;
        }
        cmd.set("#EcoTopList.Text", sb.toString().trim());
    }

    private void handleEconomyAction(AdminEventData data) {
        if (!hasAdminPermission(Permissions.ADMIN_ECONOMY)) {
            setStatus("#EcoStatusMsg", configManager.getMessage("noPermission"));
            return;
        }
        EliteEssentials plugin = EliteEssentials.getInstance();
        PlayerService ps = plugin.getPlayerService();
        if (ps == null) return;
        String adminName = playerRef.getUsername();

        switch (data.action) {
            case "ecocheck": {
                if (data.ecoPlayer == null || data.ecoPlayer.isEmpty()) { setStatus("#EcoStatusMsg", configManager.getMessage("adminui.economy.enterName")); return; }
                Optional<PlayerFile> pf = ps.getPlayerByName(data.ecoPlayer);
                if (pf.isPresent()) {
                    String currency = plugin.getConfigManager().getConfig().economy.currencySymbol;
                    setStatus("#EcoStatusMsg", pf.get().getName() + ": " + String.format("%.2f %s", pf.get().getWallet(), currency));
                } else { setStatus("#EcoStatusMsg", configManager.getMessage("playerNotFound", "player", data.ecoPlayer)); }
                break;
            }
            case "ecoset": case "ecoadd": case "ecoremove": {
                if (data.ecoPlayer == null || data.ecoPlayer.isEmpty()) { setStatus("#EcoStatusMsg", configManager.getMessage("adminui.economy.enterName")); return; }
                double amount;
                try { amount = Double.parseDouble(data.ecoAmount); } catch (Exception e) { setStatus("#EcoStatusMsg", configManager.getMessage("adminui.economy.invalidAmount")); return; }
                if (amount < 0) { setStatus("#EcoStatusMsg", configManager.getMessage("adminui.economy.invalidAmount")); return; }

                Optional<PlayerFile> pf = ps.getPlayerByName(data.ecoPlayer);
                if (pf.isEmpty()) { setStatus("#EcoStatusMsg", configManager.getMessage("playerNotFound", "player", data.ecoPlayer)); return; }
                UUID targetId = pf.get().getUuid();
                String targetName = pf.get().getName();
                String currency = plugin.getConfigManager().getConfig().economy.currencySymbol;

                boolean ok;
                String msg;
                if ("ecoset".equals(data.action)) {
                    ok = ps.setBalance(targetId, amount);
                    msg = "Set " + targetName + " balance to " + String.format("%.2f %s", amount, currency);
                    logActivity("ECONOMY", adminName, targetName, "set to " + String.format("%.2f", amount));
                } else if ("ecoadd".equals(data.action)) {
                    ok = ps.addMoney(targetId, amount);
                    msg = "Added " + String.format("%.2f %s", amount, currency) + " to " + targetName;
                    logActivity("ECONOMY", adminName, targetName, "added " + String.format("%.2f", amount));
                } else {
                    ok = ps.removeMoney(targetId, amount);
                    msg = ok ? "Removed " + String.format("%.2f %s", amount, currency) + " from " + targetName : "Insufficient funds";
                    if (ok) logActivity("ECONOMY", adminName, targetName, "removed " + String.format("%.2f", amount));
                }
                setStatus("#EcoStatusMsg", ok ? msg : "Operation failed");
                UICommandBuilder cmd = new UICommandBuilder();
                populateEconomyStats(cmd);
                populateEconomyTop(cmd);
                sendUpdate(cmd, null, false);
                break;
            }
        }
    }

    // ==================== TELEPORTS VIEW ====================

    private void buildTeleportsView(UICommandBuilder cmd, UIEventBuilder events) {
        cmd.append("#ContentArea", "Pages/EliteEssentials_AdminTeleportsContent.ui");
        cmd.set("#WarpsLabel.Text", configManager.getMessage("adminui.teleports.warps"));
        cmd.set("#SpawnsLabel.Text", configManager.getMessage("adminui.teleports.spawns"));
        cmd.set("#RefreshTpButton.Text", configManager.getMessage("adminui.stats.refresh"));
        populateWarpList(cmd, events);
        populateSpawnList(cmd, events);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#RefreshTpButton", EventData.of("Action", "refreshtp"), false);
    }

    private void populateWarpList(UICommandBuilder cmd, UIEventBuilder events) {
        EliteEssentials plugin = EliteEssentials.getInstance();
        WarpService ws = plugin.getWarpService();
        if (ws == null) return;
        List<Warp> warps = ws.getAllWarpsList();
        if (warps.isEmpty()) {
            cmd.append("#WarpListCards", "Pages/EliteEssentials_AdminTpEntry.ui");
            cmd.set("#WarpListCards[0] #TpEntryName.Text", "No warps");
            cmd.set("#WarpListCards[0] #TpEntryInfo.Text", "");
            cmd.set("#WarpListCards[0] #TpEntryGoButton.Visible", false);
            cmd.set("#WarpListCards[0] #TpEntryDelButton.Visible", false);
            return;
        }
        int idx = 0;
        for (Warp w : warps) {
            String sel = "#WarpListCards[" + idx + "]";
            cmd.append("#WarpListCards", "Pages/EliteEssentials_AdminTpEntry.ui");
            cmd.set(sel + " #TpEntryName.Text", w.getName());
            Location loc = w.getLocation();
            String info = loc != null ? String.format("%.0f, %.0f, %.0f (%s) [%s]", loc.getX(), loc.getY(), loc.getZ(), loc.getWorld(), w.isOpOnly() ? "OP" : "ALL") : "?";
            cmd.set(sel + " #TpEntryInfo.Text", info);
            cmd.set(sel + " #TpEntryGoButton.Text", "GO");
            cmd.set(sel + " #TpEntryDelButton.Text", "DEL");
            events.addEventBinding(CustomUIEventBindingType.Activating, sel + " #TpEntryGoButton",
                new EventData().append("Action", "tpwarp").append("Player", w.getName()), false);
            events.addEventBinding(CustomUIEventBindingType.Activating, sel + " #TpEntryDelButton",
                new EventData().append("Action", "delwarp").append("Player", w.getName()), false);
            idx++;
        }
    }

    private void populateSpawnList(UICommandBuilder cmd, UIEventBuilder events) {
        EliteEssentials plugin = EliteEssentials.getInstance();
        SpawnStorage ss = plugin.getSpawnStorage();
        if (ss == null) return;
        Set<String> worlds = ss.getWorldsWithSpawn();
        if (worlds.isEmpty()) {
            cmd.append("#SpawnListCards", "Pages/EliteEssentials_AdminTpEntry.ui");
            cmd.set("#SpawnListCards[0] #TpEntryName.Text", "No spawns");
            cmd.set("#SpawnListCards[0] #TpEntryInfo.Text", "");
            cmd.set("#SpawnListCards[0] #TpEntryGoButton.Visible", false);
            cmd.set("#SpawnListCards[0] #TpEntryDelButton.Visible", false);
            return;
        }
        int idx = 0;
        for (String worldName : worlds) {
            List<SpawnStorage.SpawnData> spawns = ss.getSpawns(worldName);
            for (SpawnStorage.SpawnData sp : spawns) {
                String sel = "#SpawnListCards[" + idx + "]";
                cmd.append("#SpawnListCards", "Pages/EliteEssentials_AdminTpEntry.ui");
                String label = (sp.name != null && !sp.name.isEmpty()) ? sp.name : "primary";
                cmd.set(sel + " #TpEntryName.Text", label);
                String info = String.format("%.0f, %.0f, %.0f (%s)%s", sp.x, sp.y, sp.z, sp.world, sp.primary ? " [primary]" : "");
                cmd.set(sel + " #TpEntryInfo.Text", info);
                cmd.set(sel + " #TpEntryGoButton.Text", "GO");
                cmd.set(sel + " #TpEntryDelButton.Text", "DEL");
                String spawnKey = sp.world + ":" + (sp.name != null ? sp.name : "");
                events.addEventBinding(CustomUIEventBindingType.Activating, sel + " #TpEntryGoButton",
                    new EventData().append("Action", "tpspawn").append("Player", spawnKey), false);
                events.addEventBinding(CustomUIEventBindingType.Activating, sel + " #TpEntryDelButton",
                    new EventData().append("Action", "delspawn").append("Player", spawnKey), false);
                idx++;
            }
        }
    }

    private void handleTeleportsAction(AdminEventData data, Ref<EntityStore> ref, Store<EntityStore> store) {
        if (!hasAdminPermission(Permissions.ADMIN_TELEPORT)) {
            setStatus("#TpStatusMsg", configManager.getMessage("noPermission"));
            return;
        }
        EliteEssentials plugin = EliteEssentials.getInstance();
        String adminName = playerRef.getUsername();
        switch (data.action) {
            case "tpwarp": {
                if (data.player == null) return;
                Optional<Warp> warpOpt = plugin.getWarpService().getWarp(data.player);
                if (warpOpt.isEmpty()) { setStatus("#TpStatusMsg", "Warp not found"); return; }
                Location loc = warpOpt.get().getLocation();
                teleportToLocation(loc);
                break;
            }
            case "tpspawn": {
                if (data.player == null) return;
                String[] parts = data.player.split(":", 2);
                String worldName = parts[0];
                String spawnName = parts.length > 1 ? parts[1] : "";
                SpawnStorage ss = plugin.getSpawnStorage();
                SpawnStorage.SpawnData sp = null;
                if (!spawnName.isEmpty()) {
                    sp = ss.getSpawnByName(worldName, spawnName);
                }
                if (sp == null) sp = ss.getPrimarySpawn(worldName);
                if (sp == null) { setStatus("#TpStatusMsg", "Spawn not found"); return; }
                Location loc = new Location(sp.world, sp.x, sp.y, sp.z, sp.yaw, 0f);
                teleportToLocation(loc);
                break;
            }
            case "delwarp": {
                if (data.player == null) return;
                boolean deleted = plugin.getWarpService().deleteWarp(data.player);
                if (deleted) {
                    logActivity("DELWARP", adminName, data.player, "deleted warp");
                    setStatus("#TpStatusMsg", "Deleted warp '" + data.player + "'");
                } else {
                    setStatus("#TpStatusMsg", "Warp not found: " + data.player);
                }
                switchView("teleports");
                break;
            }
            case "delspawn": {
                if (data.player == null) return;
                String[] parts = data.player.split(":", 2);
                String worldName = parts[0];
                String spawnName = parts.length > 1 ? parts[1] : "";
                SpawnStorage ss = plugin.getSpawnStorage();
                if (!spawnName.isEmpty()) {
                    boolean removed = ss.removeSpawn(worldName, spawnName);
                    if (removed) {
                        logActivity("DELSPAWN", adminName, spawnName, "deleted spawn in " + worldName);
                        setStatus("#TpStatusMsg", "Deleted spawn '" + spawnName + "' in " + worldName);
                    } else {
                        setStatus("#TpStatusMsg", "Could not delete spawn");
                    }
                } else {
                    setStatus("#TpStatusMsg", "Cannot delete unnamed primary spawn from UI");
                }
                switchView("teleports");
                break;
            }
            case "refreshtp": {
                switchView("teleports");
                break;
            }
        }
    }

    /** Teleport the admin to a Location model and close the UI. */
    private void teleportToLocation(Location loc) {
        try {
            World targetWorld = Universe.get().getWorld(loc.getWorld());
            if (targetWorld == null) { setStatus("#TpStatusMsg", "World not found: " + loc.getWorld()); return; }
            Vector3d pos = new Vector3d(loc.getX(), loc.getY(), loc.getZ());
            Vector3f rot = new Vector3f(0, loc.getYaw(), 0);
            TeleportUtil.safeTeleport(targetWorld, targetWorld, pos, rot, playerRef, () -> {}, () -> {});
            this.close();
        } catch (Exception e) {
            setStatus("#TpStatusMsg", "Teleport failed");
        }
    }

    // ==================== PLAYER DATA VIEW ====================

    private void buildPlayerDataView(UICommandBuilder cmd, UIEventBuilder events) {
        cmd.append("#ContentArea", "Pages/EliteEssentials_AdminPlayerDataContent.ui");
        cmd.set("#PdLookupLabel.Text", configManager.getMessage("adminui.playerdata.lookup"));
        cmd.set("#PdHomesLabel.Text", configManager.getMessage("adminui.playerdata.homes"));
        cmd.set("#PdBackLabel.Text", configManager.getMessage("adminui.playerdata.backHistory"));
        cmd.set("#PdLookupButton.Text", configManager.getMessage("adminui.players.lookup"));
        cmd.set("#PdClearBackButton.Text", configManager.getMessage("adminui.playerdata.clearBack"));
        cmd.set("#PdClearKitsButton.Text", configManager.getMessage("adminui.playerdata.resetKits"));

        // Pre-fill if a player was selected on the Players page
        if (selectedPlayer != null) {
            cmd.set("#PdPlayerInput.Value", selectedPlayer);
        }

        events.addEventBinding(CustomUIEventBindingType.Activating, "#PdLookupButton",
            new EventData().append("Action", "pdlookup").append("@PdPlayerInput", "#PdPlayerInput.Value"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#PdClearBackButton", EventData.of("Action", "pdclearback"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#PdClearKitsButton", EventData.of("Action", "pdresetkits"), false);
    }

    private void handlePlayerDataAction(AdminEventData data, Ref<EntityStore> ref, Store<EntityStore> store) {
        if (!hasAdminPermission(Permissions.ADMIN_PLAYERDATA)) {
            setStatus("#PdStatusMsg", configManager.getMessage("noPermission"));
            return;
        }
        EliteEssentials plugin = EliteEssentials.getInstance();
        String adminName = playerRef.getUsername();

        switch (data.action) {
            case "pdlookup": {
                if (data.pdPlayer == null || data.pdPlayer.isEmpty()) { setStatus("#PdStatusMsg", configManager.getMessage("adminui.warns.enterName")); return; }
                Optional<PlayerFile> pfOpt = plugin.getPlayerService().getPlayerByName(data.pdPlayer);
                if (pfOpt.isEmpty()) { setStatus("#PdStatusMsg", configManager.getMessage("playerNotFound", "player", data.pdPlayer)); return; }
                PlayerFile pf = pfOpt.get();
                pdTarget = pf.getName();
                pdTargetUuid = pf.getUuid();
                refreshPlayerData();
                break;
            }
            case "pdclearback": {
                if (pdTargetUuid == null) { setStatus("#PdStatusMsg", "Lookup a player first"); return; }
                plugin.getBackService().clearHistory(pdTargetUuid);
                logActivity("CLEARBACK", adminName, pdTarget, "cleared back history");
                setStatus("#PdStatusMsg", "Cleared back history for " + pdTarget);
                refreshPlayerData();
                break;
            }
            case "pdresetkits": {
                if (pdTargetUuid == null) { setStatus("#PdStatusMsg", "Lookup a player first"); return; }
                PlayerFile pf = plugin.getPlayerStorageProvider().getPlayer(pdTargetUuid);
                if (pf != null) {
                    pf.getKitClaims().clear();
                    pf.clearKitCooldowns();
                    plugin.getPlayerStorageProvider().saveAndMarkDirty(pdTargetUuid);
                    logActivity("RESETKITS", adminName, pdTarget, "reset kit claims and cooldowns");
                    setStatus("#PdStatusMsg", "Reset kit claims for " + pdTarget);
                }
                refreshPlayerData();
                break;
            }
            case "pddelhome": {
                if (pdTargetUuid == null || data.player == null) return;
                HomeService.Result result = plugin.getHomeService().deleteHome(pdTargetUuid, data.player);
                if (result == HomeService.Result.SUCCESS) {
                    logActivity("DELHOME", adminName, pdTarget, "deleted home '" + data.player + "'");
                    setStatus("#PdStatusMsg", "Deleted home '" + data.player + "' for " + pdTarget);
                } else {
                    setStatus("#PdStatusMsg", "Failed to delete home");
                }
                refreshPlayerData();
                break;
            }
            case "pdtphome": {
                if (pdTargetUuid == null || data.player == null) return;
                Optional<Home> homeOpt = plugin.getHomeService().getHome(pdTargetUuid, data.player);
                if (homeOpt.isPresent()) {
                    teleportToLocation(homeOpt.get().getLocation());
                } else {
                    setStatus("#PdStatusMsg", "Home not found");
                }
                break;
            }
            case "pdtpback": {
                if (pdTargetUuid == null || data.player == null) return;
                try {
                    int index = Integer.parseInt(data.player);
                    PlayerFile pf = plugin.getPlayerStorageProvider().getPlayer(pdTargetUuid);
                    if (pf != null) {
                        List<Location> history = pf.getBackHistory();
                        if (index >= 0 && index < history.size()) {
                            teleportToLocation(history.get(index));
                        } else {
                            setStatus("#PdStatusMsg", "Back location not found");
                        }
                    }
                } catch (NumberFormatException e) {
                    setStatus("#PdStatusMsg", "Invalid back index");
                }
                break;
            }
        }
    }

    private void refreshPlayerData() {
        if (pdTargetUuid == null) return;
        EliteEssentials plugin = EliteEssentials.getInstance();
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder events = new UIEventBuilder();

        PlayerFile pf = plugin.getPlayerStorageProvider().getPlayer(pdTargetUuid);
        if (pf == null) { setStatus("#PdStatusMsg", "Player data not found"); return; }

        // Summary panel
        cmd.set("#PdSummaryPanel.Visible", true);
        cmd.set("#PdName.Text", pf.getName());
        cmd.set("#PdHomeCount.Text", String.valueOf(pf.getHomeCount()));
        cmd.set("#PdBackCount.Text", String.valueOf(pf.getBackHistorySize()));
        Set<String> kitClaims = pf.getKitClaims();
        cmd.set("#PdKitCount.Text", String.valueOf(kitClaims != null ? kitClaims.size() : 0));

        // Homes list
        cmd.clear("#PdHomeListCards");
        Map<String, Home> homes = pf.getHomes();
        if (homes.isEmpty()) {
            cmd.append("#PdHomeListCards", "Pages/EliteEssentials_AdminPdHomeEntry.ui");
            cmd.set("#PdHomeListCards[0] #PdHomeName.Text", "No homes");
            cmd.set("#PdHomeListCards[0] #PdHomeCoords.Text", "");
            cmd.set("#PdHomeListCards[0] #PdHomeGoButton.Visible", false);
            cmd.set("#PdHomeListCards[0] #PdHomeDeleteButton.Visible", false);
        } else {
            int idx = 0;
            for (Map.Entry<String, Home> entry : homes.entrySet()) {
                String sel = "#PdHomeListCards[" + idx + "]";
                cmd.append("#PdHomeListCards", "Pages/EliteEssentials_AdminPdHomeEntry.ui");
                cmd.set(sel + " #PdHomeName.Text", entry.getKey());
                Location loc = entry.getValue().getLocation();
                cmd.set(sel + " #PdHomeCoords.Text", loc != null ? String.format("%.0f, %.0f, %.0f (%s)", loc.getX(), loc.getY(), loc.getZ(), loc.getWorld()) : "?");
                events.addEventBinding(CustomUIEventBindingType.Activating, sel + " #PdHomeGoButton",
                    new EventData().append("Action", "pdtphome").append("Player", entry.getKey()), false);
                events.addEventBinding(CustomUIEventBindingType.Activating, sel + " #PdHomeDeleteButton",
                    new EventData().append("Action", "pddelhome").append("Player", entry.getKey()), false);
                idx++;
            }
        }

        // Back history - card entries with TP buttons
        cmd.clear("#PdBackListCards");
        List<Location> backHistory = pf.getBackHistory();
        if (backHistory.isEmpty()) {
            cmd.append("#PdBackListCards", "Pages/EliteEssentials_AdminPdBackEntry.ui");
            cmd.set("#PdBackListCards[0] #PdBackIndex.Text", "");
            cmd.set("#PdBackListCards[0] #PdBackCoords.Text", "No back locations");
            cmd.set("#PdBackListCards[0] #PdBackGoButton.Visible", false);
        } else {
            int i = 0;
            for (Location loc : backHistory) {
                String sel = "#PdBackListCards[" + i + "]";
                cmd.append("#PdBackListCards", "Pages/EliteEssentials_AdminPdBackEntry.ui");
                cmd.set(sel + " #PdBackIndex.Text", "#" + (i + 1));
                cmd.set(sel + " #PdBackCoords.Text", String.format("%.0f, %.0f, %.0f (%s)", loc.getX(), loc.getY(), loc.getZ(), loc.getWorld()));
                events.addEventBinding(CustomUIEventBindingType.Activating, sel + " #PdBackGoButton",
                    new EventData().append("Action", "pdtpback").append("Player", String.valueOf(i)), false);
                i++;
            }
        }

        // Re-bind the action buttons for the refreshed view
        events.addEventBinding(CustomUIEventBindingType.Activating, "#PdClearBackButton", EventData.of("Action", "pdclearback"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#PdClearKitsButton", EventData.of("Action", "pdresetkits"), false);

        sendUpdate(cmd, events, false);
    }

    /** Helper to log admin actions to the ActivityLogService. */
    private void logActivity(String type, String admin, String target, String detail) {
        ActivityLogService als = ActivityLogService.get();
        if (als != null) als.log(type, admin, target, detail);
    }

    // ==================== UTILITIES ====================

    /**
     * Check if the admin has a specific permission in advanced mode.
     * In simple mode, all admins have full access (they already passed the /eeadmin gate).
     * In advanced mode, checks the granular permission node.
     */
    private boolean hasAdminPermission(String permission) {
        PermissionService perms = PermissionService.get();
        UUID playerId = playerRef.getUuid();
        // Admins with wildcard always pass
        if (perms.isAdmin(playerId)) return true;
        // In advanced mode, check the specific permission
        return perms.hasPermission(playerId, permission);
    }

    private void setStatus(String selector, String message) {
        UICommandBuilder cmd = new UICommandBuilder();
        cmd.set(selector + ".Text", message);
        sendUpdate(cmd, null, false);
    }

    /**
     * Toggles Disabled state on nav buttons to show active selection.
     * The Disabled style is configured to look like the active/selected state
     * (lighter background, gold text, bold) in @AdminNavButtonStyle.
     */
    private void updateNavHighlight(UICommandBuilder cmd, String activeView) {
        cmd.set("#NavDashboard.Disabled", "dashboard".equals(activeView));
        cmd.set("#NavPlayers.Disabled", "players".equals(activeView));
        cmd.set("#NavBans.Disabled", "bans".equals(activeView));
        cmd.set("#NavMutes.Disabled", "mutes".equals(activeView));
        cmd.set("#NavWarns.Disabled", "warns".equals(activeView));
        cmd.set("#NavStats.Disabled", "stats".equals(activeView));
        cmd.set("#NavActivity.Disabled", "activity".equals(activeView));
        cmd.set("#NavEconomy.Disabled", "economy".equals(activeView));
        cmd.set("#NavTeleports.Disabled", "teleports".equals(activeView));
        cmd.set("#NavPlayerData.Disabled", "playerdata".equals(activeView));
    }

    private static long parseDuration(String input) {
        if (input == null || input.isEmpty()) return 0;
        input = input.trim().toLowerCase();
        try {
            if (input.endsWith("d")) return Long.parseLong(input.replace("d", "")) * 86_400_000L;
            if (input.endsWith("h")) return Long.parseLong(input.replace("h", "")) * 3_600_000L;
            if (input.endsWith("m")) return Long.parseLong(input.replace("m", "")) * 60_000L;
            return Long.parseLong(input) * 60_000L;
        } catch (NumberFormatException e) { return 0; }
    }

    private static String formatDuration(long ms) {
        if (ms <= 0) return "expired";
        long hours = ms / 3_600_000;
        long minutes = (ms % 3_600_000) / 60_000;
        if (hours > 24) return (hours / 24) + "d " + (hours % 24) + "h";
        return hours + "h " + minutes + "m";
    }

    // ==================== EVENT DATA ====================

    public static class AdminEventData {
        public static final BuilderCodec<AdminEventData> CODEC = BuilderCodec.builder(AdminEventData.class, AdminEventData::new)
            .append(new KeyedCodec<>("Nav", Codec.STRING), (d, s) -> d.nav = s, d -> d.nav).add()
            .append(new KeyedCodec<>("Action", Codec.STRING), (d, s) -> d.action = s, d -> d.action).add()
            .append(new KeyedCodec<>("Player", Codec.STRING), (d, s) -> d.player = s, d -> d.player).add()
            .append(new KeyedCodec<>("@LookupInput", Codec.STRING), (d, s) -> d.lookupInput = s, d -> d.lookupInput).add()
            .append(new KeyedCodec<>("@BanPlayerInput", Codec.STRING), (d, s) -> d.banPlayer = s, d -> d.banPlayer).add()
            .append(new KeyedCodec<>("@BanDurationInput", Codec.STRING), (d, s) -> d.banDuration = s, d -> d.banDuration).add()
            .append(new KeyedCodec<>("@BanReasonInput", Codec.STRING), (d, s) -> d.banReason = s, d -> d.banReason).add()
            .append(new KeyedCodec<>("@UnbanPlayerInput", Codec.STRING), (d, s) -> d.unbanPlayer = s, d -> d.unbanPlayer).add()
            .append(new KeyedCodec<>("@MutePlayerInput", Codec.STRING), (d, s) -> d.mutePlayer = s, d -> d.mutePlayer).add()
            .append(new KeyedCodec<>("@MuteReasonInput", Codec.STRING), (d, s) -> d.muteReason = s, d -> d.muteReason).add()
            .append(new KeyedCodec<>("@UnmutePlayerInput", Codec.STRING), (d, s) -> d.unmutePlayer = s, d -> d.unmutePlayer).add()
            .append(new KeyedCodec<>("@WarnPlayerInput", Codec.STRING), (d, s) -> d.warnPlayer = s, d -> d.warnPlayer).add()
            .append(new KeyedCodec<>("@WarnReasonInput", Codec.STRING), (d, s) -> d.warnReason = s, d -> d.warnReason).add()
            .append(new KeyedCodec<>("@LookupPlayerInput", Codec.STRING), (d, s) -> d.lookupPlayer = s, d -> d.lookupPlayer).add()
            .append(new KeyedCodec<>("@EcoPlayerInput", Codec.STRING), (d, s) -> d.ecoPlayer = s, d -> d.ecoPlayer).add()
            .append(new KeyedCodec<>("@EcoAmountInput", Codec.STRING), (d, s) -> d.ecoAmount = s, d -> d.ecoAmount).add()
            .append(new KeyedCodec<>("@PdPlayerInput", Codec.STRING), (d, s) -> d.pdPlayer = s, d -> d.pdPlayer).add()
            .build();

        String nav, action, player, lookupInput;
        String banPlayer, banDuration, banReason, unbanPlayer;
        String mutePlayer, muteReason, unmutePlayer;
        String warnPlayer, warnReason, lookupPlayer;
        String ecoPlayer, ecoAmount;
        String pdPlayer;
    }
}
