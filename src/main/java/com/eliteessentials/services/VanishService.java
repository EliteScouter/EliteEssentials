package com.eliteessentials.services;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.config.PluginConfig;
import com.eliteessentials.util.MessageFormatter;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.WorldMapTracker;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.protocol.packets.interface_.RemoveFromServerPlayerList;
import com.hypixel.hytale.protocol.packets.interface_.AddToServerPlayerList;
import com.hypixel.hytale.protocol.packets.interface_.ServerPlayerListPlayer;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Service for managing player vanish state.
 * Vanished players are invisible to other players, hidden from the map,
 * and hidden from the Server Players list.
 */
public class VanishService {
    
    private static final Logger logger = Logger.getLogger("EliteEssentials");
    
    private final ConfigManager configManager;
    
    // Track vanished players (in-memory only, resets on restart)
    private final Set<UUID> vanishedPlayers = ConcurrentHashMap.newKeySet();
    
    public VanishService(ConfigManager configManager) {
        this.configManager = configManager;
    }
    
    /**
     * Set a player's vanish state.
     * @param playerName The player's username (for fake messages)
     */
    public void setVanished(UUID playerId, String playerName, boolean vanished) {
        if (vanished) {
            vanishedPlayers.add(playerId);
        } else {
            vanishedPlayers.remove(playerId);
        }
        
        PluginConfig config = configManager.getConfig();
        
        // Update in-world visibility
        updateVisibilityForAll(playerId, vanished);
        
        // Update player list if enabled
        if (config.vanish.hideFromList) {
            updatePlayerListForAll(playerId, vanished);
        }
        
        // Send fake join/leave message if enabled
        if (config.vanish.mimicJoinLeave) {
            broadcastFakeMessage(playerName, vanished);
        }
    }
    
    /**
     * Check if a player is vanished.
     */
    public boolean isVanished(UUID playerId) {
        return vanishedPlayers.contains(playerId);
    }
    
    /**
     * Toggle a player's vanish state.
     * @param playerName The player's username (for fake messages)
     * @return true if now vanished, false if now visible
     */
    public boolean toggleVanish(UUID playerId, String playerName) {
        boolean nowVanished = !isVanished(playerId);
        setVanished(playerId, playerName, nowVanished);
        return nowVanished;
    }
    
    /**
     * Called when a player joins the server.
     * Hides all vanished players from the joining player.
     */
    public void onPlayerJoin(PlayerRef joiningPlayer) {
        if (joiningPlayer == null) return;
        
        PluginConfig config = configManager.getConfig();
        
        // Hide all vanished players from the joining player's view
        for (UUID vanishedId : vanishedPlayers) {
            try {
                // Hide in-world
                joiningPlayer.getHiddenPlayersManager().hidePlayer(vanishedId);
                
                // Remove from player list if enabled
                if (config.vanish.hideFromList) {
                    RemoveFromServerPlayerList packet = new RemoveFromServerPlayerList(new UUID[] { vanishedId });
                    joiningPlayer.getPacketHandler().write(packet);
                }
            } catch (Exception e) {
                logger.warning("Failed to hide vanished player from " + joiningPlayer.getUsername() + ": " + e.getMessage());
            }
        }
    }
    
    /**
     * Called when a player is fully loaded into a world (has Player component).
     * Sets up the map filter to hide vanished players.
     */
    public void onPlayerReady(Store<EntityStore> store, Ref<EntityStore> ref) {
        if (store == null || ref == null || !ref.isValid()) return;
        
        PluginConfig config = configManager.getConfig();
        if (!config.vanish.hideFromMap) return;
        
        try {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) return;
            
            WorldMapTracker tracker = player.getWorldMapTracker();
            if (tracker != null) {
                // Set filter to hide vanished players from this player's map
                tracker.setPlayerMapFilter(playerRef -> !vanishedPlayers.contains(playerRef.getUuid()));
            }
        } catch (Exception e) {
            logger.warning("Failed to set map filter for player: " + e.getMessage());
        }
    }
    
    /**
     * Called when a player leaves the server.
     * Removes them from vanish state.
     */
    public void onPlayerLeave(UUID playerId) {
        vanishedPlayers.remove(playerId);
    }
    
    /**
     * Broadcast a fake join or leave message to all players.
     */
    private void broadcastFakeMessage(String playerName, boolean vanished) {
        try {
            Universe universe = Universe.get();
            if (universe == null) return;
            
            // Get the appropriate message
            String messageKey = vanished ? "vanishFakeLeave" : "vanishFakeJoin";
            String message = configManager.getMessage(messageKey, "player", playerName);
            
            // Broadcast to all players
            for (PlayerRef player : universe.getPlayers()) {
                try {
                    player.sendMessage(MessageFormatter.format(message));
                } catch (Exception e) {
                    // Ignore individual send failures
                }
            }
        } catch (Exception e) {
            logger.warning("Failed to broadcast fake vanish message: " + e.getMessage());
        }
    }
    
    /**
     * Update in-world visibility of a player for all online players.
     */
    private void updateVisibilityForAll(UUID targetId, boolean hide) {
        try {
            Universe universe = Universe.get();
            if (universe == null) return;
            
            for (PlayerRef player : universe.getPlayers()) {
                if (player.getUuid().equals(targetId)) {
                    continue; // Don't hide player from themselves
                }
                
                try {
                    if (hide) {
                        player.getHiddenPlayersManager().hidePlayer(targetId);
                    } else {
                        player.getHiddenPlayersManager().showPlayer(targetId);
                    }
                } catch (Exception e) {
                    logger.warning("Failed to update visibility for " + player.getUsername() + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.warning("Failed to update visibility for all players: " + e.getMessage());
        }
    }
    
    /**
     * Update Server Players list for all online players.
     */
    private void updatePlayerListForAll(UUID targetId, boolean hide) {
        try {
            Universe universe = Universe.get();
            if (universe == null) return;
            
            // Find the target player to get their info for AddToServerPlayerList
            PlayerRef targetPlayer = null;
            for (PlayerRef p : universe.getPlayers()) {
                if (p.getUuid().equals(targetId)) {
                    targetPlayer = p;
                    break;
                }
            }
            
            for (PlayerRef player : universe.getPlayers()) {
                if (player.getUuid().equals(targetId)) {
                    continue; // Don't remove player from their own list
                }
                
                try {
                    if (hide) {
                        RemoveFromServerPlayerList packet = new RemoveFromServerPlayerList(new UUID[] { targetId });
                        player.getPacketHandler().write(packet);
                    } else if (targetPlayer != null) {
                        ServerPlayerListPlayer listPlayer = new ServerPlayerListPlayer(
                            targetPlayer.getUuid(),
                            targetPlayer.getUsername(),
                            targetPlayer.getWorldUuid(),
                            0
                        );
                        AddToServerPlayerList packet = new AddToServerPlayerList(new ServerPlayerListPlayer[] { listPlayer });
                        player.getPacketHandler().write(packet);
                    }
                } catch (Exception e) {
                    logger.warning("Failed to update player list for " + player.getUsername() + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.warning("Failed to update player list for all players: " + e.getMessage());
        }
    }
    
    /**
     * Get all vanished player UUIDs.
     */
    public Set<UUID> getVanishedPlayers() {
        return Set.copyOf(vanishedPlayers);
    }
    
    /**
     * Get count of vanished players.
     */
    public int getVanishedCount() {
        return vanishedPlayers.size();
    }
}
