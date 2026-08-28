package com.eliteessentials.services;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.config.PluginConfig;
import com.eliteessentials.integration.HyperPermsIntegration;
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

        // Get prefix from permission plugin if enabled — strip color codes since tab list is plain text
        String lpPrefix = "";
        if (config.tabList.showLuckPermsPrefix) {
            try {
                String rawPrefix = "";
                if (LuckPermsIntegration.isAvailable()) {
                    rawPrefix = LuckPermsIntegration.getPrefix(playerId);
                } else if (HyperPermsIntegration.isAvailable()) {
                    rawPrefix = HyperPermsIntegration.getPrefix(playerId);
                }
                lpPrefix = MessageFormatter.stripColorCodes(rawPrefix);
            } catch (Exception e) {
                if (configManager.isDebugEnabled()) {
                    logger.info("[TabList] Failed to get prefix for " + playerId + ": " + e.getMessage());
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
                0,
                false,
                null
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
     * Called when a player joins. Does two things:
     * 1. Updates the joining player's own tab entry for everyone (if they have a nick/prefix).
     * 2. Sends all other online players' nicked/prefixed entries to the new joiner,
     *    so they see correct display names for players who were already online.
     */
    public void onPlayerJoin(UUID playerId) {
        PluginConfig config = configManager.getConfig();
        boolean hasNick = nickService != null && nickService.hasNick(playerId);

        // Update this player's entry for everyone already online
        if (config.tabList.showLuckPermsPrefix || hasNick) {
            updatePlayer(playerId);
        }

        // Push all already-online nicked/prefixed players to the new joiner.
        // Without this, the new joiner receives Hytale's native player list which
        // uses real usernames, so they'd see real names instead of nicks.
        if (nickService == null && !config.tabList.showLuckPermsPrefix) {
            return;
        }

        Universe universe = Universe.get();
        if (universe == null) return;

        PlayerRef joiningPlayer = universe.getPlayer(playerId);
        if (joiningPlayer == null || !joiningPlayer.isValid()) return;

        for (PlayerRef online : universe.getPlayers()) {
            UUID onlineId = online.getUuid();
            // Skip the joining player themselves - already handled above
            if (onlineId.equals(playerId)) continue;

            boolean onlineHasNick = nickService != null && nickService.hasNick(onlineId);
            if (!config.tabList.showLuckPermsPrefix && !onlineHasNick) continue;

            try {
                String displayName = buildDisplayName(onlineId, online.getUsername());
                RemoveFromServerPlayerList removePacket = new RemoveFromServerPlayerList(new UUID[]{onlineId});
                ServerPlayerListPlayer listPlayer = new ServerPlayerListPlayer(
                    onlineId,
                    displayName,
                    online.getWorldUuid(),
                    0,
                    false,
                    null
                );
                AddToServerPlayerList addPacket = new AddToServerPlayerList(new ServerPlayerListPlayer[]{listPlayer});
                joiningPlayer.getPacketHandler().write(removePacket);
                joiningPlayer.getPacketHandler().write(addPacket);
            } catch (Exception e) {
                // Skip players with packet issues
            }
        }
    }
}
