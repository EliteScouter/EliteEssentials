package com.eliteessentials.gui;

import com.eliteessentials.config.ConfigManager;
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
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.logging.Logger;

/**
 * Admin UI page showing live server statistics.
 * Displays players, TPS, memory, uptime, and Java info.
 */
public class AdminStatsPage extends InteractiveCustomUIPage<AdminStatsPage.StatsEventData> {

    private static final Logger logger = Logger.getLogger("EliteEssentials");
    private static final long START_TIME = System.currentTimeMillis();
    private final ConfigManager configManager;

    public AdminStatsPage(PlayerRef playerRef, ConfigManager configManager) {
        super(playerRef, CustomPageLifetime.CanDismiss, StatsEventData.CODEC);
        this.configManager = configManager;
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder cmd,
                      UIEventBuilder events, Store<EntityStore> store) {
        cmd.append("Pages/EliteEssentials_AdminStats.ui");

        cmd.set("#PageTitle.Text", configManager.getMessage("adminui.stats.title"));
        cmd.set("#RefreshButton.Text", configManager.getMessage("adminui.stats.refresh"));

        // Translatable stat labels
        cmd.set("#StPlayersLabel.Text", configManager.getMessage("adminui.stats.playersOnline"));
        cmd.set("#StTpsLabel.Text", configManager.getMessage("adminui.stats.serverTps"));
        cmd.set("#StMemLabel.Text", configManager.getMessage("adminui.stats.memoryUsed"));
        cmd.set("#StFreeLabel.Text", configManager.getMessage("adminui.stats.freeMemory"));
        cmd.set("#StUptimeLabel.Text", configManager.getMessage("adminui.stats.uptime"));
        cmd.set("#StJavaLabel.Text", configManager.getMessage("adminui.stats.javaVersion"));

        populateStats(cmd);

        events.addEventBinding(CustomUIEventBindingType.Activating, "#BackButton",
            EventData.of("Action", "back"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
            EventData.of("Action", "close"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#RefreshButton",
            EventData.of("Action", "refresh"));
    }

    private void populateStats(UICommandBuilder cmd) {
        // Players
        int online = 0;
        try {
            for (PlayerRef p : Universe.get().getPlayers()) {
                online++;
            }
        } catch (Exception ignored) {}
        cmd.set("#StPlayers.Text", String.valueOf(online));
        cmd.set("#StPlayersMax.Text", online + " online");

        // TPS - not directly exposed by Hytale API
        cmd.set("#StTPS.Text", "--");

        // Memory
        Runtime rt = Runtime.getRuntime();
        long usedMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        long maxMb = rt.maxMemory() / (1024 * 1024);
        long freeMb = rt.freeMemory() / (1024 * 1024);
        cmd.set("#StMemory.Text", usedMb + " MB");
        cmd.set("#StMemMax.Text", "/ " + maxMb + " MB allocated");
        cmd.set("#StFreeMem.Text", freeMb + " MB");

        // Uptime
        long uptimeMs = System.currentTimeMillis() - START_TIME;
        long hours = uptimeMs / 3_600_000;
        long minutes = (uptimeMs % 3_600_000) / 60_000;
        cmd.set("#StUptime.Text", hours + "h " + minutes + "m");

        // Java info
        cmd.set("#StJava.Text", System.getProperty("java.version", "?"));
        cmd.set("#StOS.Text", System.getProperty("os.name", "") + " " + System.getProperty("os.arch", ""));

        cmd.set("#StStatusMsg.Text", configManager.getMessage("adminui.stats.loaded"));
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, StatsEventData data) {
        if (data.getAction() == null) return;

        switch (data.getAction()) {
            case "back":
            case "close":
                this.close();
                break;
            case "refresh":
                this.rebuild();
                break;
        }
    }

    public static class StatsEventData {
        public static final BuilderCodec<StatsEventData> CODEC = BuilderCodec.builder(StatsEventData.class, StatsEventData::new)
            .append(new KeyedCodec<>("Action", Codec.STRING), (d, s) -> d.action = s, d -> d.action).add()
            .build();

        private String action;

        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }
    }
}
