package com.eliteessentials.services;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.config.PluginConfig;
import com.eliteessentials.integration.LuckPermsIntegration;
import com.eliteessentials.services.NickService;
import com.eliteessentials.util.MessageFormatter;
import com.hypixel.hytale.protocol.packets.interface_.AddToServerPlayerList;
import com.hypixel.hytale.protocol.packets.interface_.RemoveFromServerPlayerList;
import com.hypixel.hytale.protocol.packets.interface_.ServerPlayerListPlayer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;

import java.util.UUID;
import java.util.logging.Logger;

/**
 * Manages tab list (player list) display names.
 * 
 * Builds display names by combining optional prefixes (AFK, LuckPerms)
 * with the player's username. Other services (like AfkService) call into
 * this to ensure consistent tab list formatting.
 */
public class TabListService {

    private static final Logger logger = Logger.getLogger("EliteEssentials");

    private final ConfigManager configManager;
    private AfkService afkService;
    private NickService nickService;

    public TabListService(ConfigManager configManager) {
        this.configManager = configManager;
    }

    public void setAfkService(AfkService afkService) {
        this.afkService = afkService;
    }

    public void setNickService(NickService nickService) {
        this.nickService = nickService;
    }

    /**
     * Build the full display name for a player's tab list entry.
     * Combines AFK prefix (from messages.json) and LuckPerms prefix as configured.
     *
     * @param playerId Player UUID
     * @param username Player's raw username
     * @return The formatted display name
     */
    public String buildDisplayName(UUID playerId, String username) {
        PluginConfig config = configManager.getConfig();

        // Use nickname as the display base if one is set (strip color codes - tab list is plain text)
        String baseName = username;
        if (nickService != null) {
            String nick = nickService.getNickname(playerId);
            if (nick != null && !nick.isEmpty()) {
                baseName = MessageFormatter.stripColorCodes(nick);
            }
        }

        // Get LuckPerms prefix if enabled — strip color codes since tab list is plain text
        String lpPrefix = "";
        if (config.tabList.showLuckPermsPrefix && LuckPermsIntegration.isAvailable()) {
            try {
                String rawPrefix = LuckPermsIntegration.getPrefix(playerId);
                lpPrefix = MessageFormatter.stripColorCodes(rawPrefix);
            } catch (Exception e) {
                if (configManager.isDebugEnabled()) {
                    logger.info("[TabList] Failed to get LuckPerms prefix for " + playerId + ": " + e.getMessage());
                }
            }
        }

        // Check AFK state
        boolean isAfk = afkService != null && afkService.isAfk(playerId);
        boolean showAfkInTab = config.afk.showInTabList;

        if (isAfk && showAfkInTab) {
            String afkName = MessageFormatter.stripColorCodes(
                configManager.getMessage("afkPrefix", "player", baseName));
            if (!lpPrefix.isEmpty()) {
                return afkName.replace(baseName, lpPrefix + baseName);
            }
            return afkName;
        }

        return lpPrefix.isEmpty() ? baseName : lpPrefix + baseName;
    }

    /**
     * Update a player's tab list entry for all online players.
     * Call this whenever something that affects the display name changes
     * (AFK toggle, join, LuckPerms group change, etc.)
     */
    public void updatePlayer(UUID targetId) {
        try {
            Universe universe = Universe.get();
            if (universe == null) return;

            PlayerRef targetPlayer = universe.getPlayer(targetId);
            if (targetPlayer == null || !targetPlayer.isValid()) return;

            String displayName = buildDisplayName(targetId, targetPlayer.getUsername());

            RemoveFromServerPlayerList removePacket = new RemoveFromServerPlayerList(new UUID[] { targetId });
            ServerPlayerListPlayer listPlayer = new ServerPlayerListPlayer(
                targetPlayer.getUuid(),
                displayName,
                targetPlayer.getWorldUuid(),
                0
            );
            AddToServerPlayerList addPacket = new AddToServerPlayerList(new ServerPlayerListPlayer[] { listPlayer });

            for (PlayerRef player : universe.getPlayers()) {
                try {
                    player.getPacketHandler().write(removePacket);
                    player.getPacketHandler().write(addPacket);
                } catch (Exception e) {
                    // Skip players with packet issues
                }
            }
        } catch (Exception e) {
            logger.warning("[TabList] Failed to update tab list for " + targetId + ": " + e.getMessage());
        }
    }

    /**
     * Called when a player joins. Sets their initial tab list display name
     * if LuckPerms prefix or a nickname is active.
     */
    public void onPlayerJoin(UUID playerId) {
        boolean hasNick = nickService != null && nickService.hasNick(playerId);
        if (configManager.getConfig().tabList.showLuckPermsPrefix || hasNick) {
            updatePlayer(playerId);
        }
    }
}
