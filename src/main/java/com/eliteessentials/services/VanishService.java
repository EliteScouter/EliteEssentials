package com.eliteessentials.services;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.config.PluginConfig;
import com.eliteessentials.model.PlayerFile;
import com.eliteessentials.storage.PlayerFileStorage;
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

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Service for managing player vanish state.
 * Vanished players are invisible to other players, hidden from the map,
 * and hidden from the Server Players list.
 * 
 * Vanish state is persisted in each player's JSON file (players/{uuid}.json).
 */
public class VanishService {
    
    private static final Logger logger = Logger.getLogger("EliteEssentials");
    
    private final ConfigManager configManager;
    private PlayerFileStorage playerFileStorage;
    
    // Track currently vanished players (in-memory for quick lookups)
    private final Set<UUID> vanishedPlayers = ConcurrentHashMap.newKeySet();
    
    // Track player store/ref pairs for map filter updates
    private final Map<UUID, PlayerStoreRef> playerStoreRefs = new ConcurrentHashMap<>();
    
    /**
     * Helper class to store player's store and ref for map filter updates.
     */
    private static class PlayerStoreRef {
        final Store<EntityStore> store;
        final Ref<EntityStore> ref;
        
        PlayerStoreRef(Store<EntityStore> store, Ref<EntityStore> ref) {
            this.store = store;
            this.ref = ref;
        }
    }
    
    public VanishService(ConfigManager configManager) {
        this.configManager = configManager;
    }
    
    /**
     * Set the player file storage (called after initialization).
     */
    public void setPlayerFileStorage(PlayerFileStorage storage) {
        this.playerFileStorage = storage;
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
        
        // Persist to player file if enabled
        if (config.vanish.persistOnReconnect && playerFileStorage != null) {
            PlayerFile playerFile = playerFileStorage.getPlayer(playerId);
            if (playerFile != null) {
                playerFile.setVanished(vanished);
                playerFileStorage.saveAndMarkDirty(playerId);
            }
        }
        
        // Update in-world visibility
        updateVisibilityForAll(playerId, vanished);
        
        // Update player list if enabled
        if (config.vanish.hideFromList) {
            updatePlayerListForAll(playerId, vanished);
        }
        
        // Update map filters - NOTE: This currently only affects NEW players joining
        // To properly hide already-tracked players from the map, need to find the 
        // correct WorldMapTracker method. See updateMapFiltersForAll() comments.
        if (config.vanish.hideFromMap) {
            updateMapFiltersForAll();
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
     * Check if a player has persisted vanish state (for reconnect handling).
     */
    public boolean hasPersistedVanish(UUID playerId) {
        if (playerFileStorage == null) return false;
        
        PlayerFile playerFile = playerFileStorage.getPlayer(playerId);
        return playerFile != null && playerFile.isVanished();
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
     * Also restores vanish state if player was vanished before disconnect.
     * @return true if the joining player is vanished (for suppressing join message)
     */
    public boolean onPlayerJoin(PlayerRef joiningPlayer) {
        if (joiningPlayer == null) return false;
        
        UUID playerId = joiningPlayer.getUuid();
        PluginConfig config = configManager.getConfig();
        
        // Check if player should be restored to vanish state from their player file
        boolean wasVanished = false;
        if (config.vanish.persistOnReconnect && playerFileStorage != null) {
            PlayerFile playerFile = playerFileStorage.getPlayer(playerId);
            if (playerFile != null && playerFile.isVanished()) {
                // Restore vanish state (without fake messages - they're reconnecting)
                vanishedPlayers.add(playerId);
                wasVanished = true;
                logger.info("Restored vanish state for " + joiningPlayer.getUsername() + " (was vanished before disconnect)");
                
                // Update visibility for all other players
                updateVisibilityForAll(playerId, true);
                
                // Update player list
                if (config.vanish.hideFromList) {
                    updatePlayerListForAll(playerId, true);
                }
                
                // Map visibility will be handled via filter in onPlayerReady when 
                // other players have their map filters set up
            }
        }
        
        // Hide all vanished players from the joining player's view
        for (UUID vanishedId : vanishedPlayers) {
            if (vanishedId.equals(playerId)) continue; // Don't hide from self
            
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
        
        return wasVanished;
    }
    
    /**
     * Send vanish reminder to a player who reconnected while vanished.
     */
    public void sendVanishReminder(PlayerRef playerRef) {
        if (playerRef == null) return;
        
        PluginConfig config = configManager.getConfig();
        if (!config.vanish.showReminderOnJoin) return;
        
        String message = configManager.getMessage("vanishReminder");
        playerRef.sendMessage(MessageFormatter.format(message));
        logger.fine("Sent vanish reminder to " + playerRef.getUsername());
    }
    
    /**
     * Called when a player is fully loaded into a world (has Player component).
     * Sets up the map filter to hide vanished players.
     */
    public void onPlayerReady(Store<EntityStore> store, Ref<EntityStore> ref) {
        if (store == null || ref == null || !ref.isValid()) return;
        
        PluginConfig config = configManager.getConfig();
        
        try {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) return;
            
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            if (playerRef != null) {
                // Store the player's store/ref for later map filter updates
                playerStoreRefs.put(playerRef.getUuid(), new PlayerStoreRef(store, ref));
            }
            
            if (!config.vanish.hideFromMap) return;
            
            WorldMapTracker tracker = player.getWorldMapTracker();
            if (tracker != null) {
                // Set filter to hide vanished players from this player's map
                // Filter returns TRUE for players who should be SKIPPED/HIDDEN
                // Filter returns FALSE for players who should be SHOWN
                tracker.setPlayerMapFilter(pRef -> {
                    // Return TRUE to HIDE vanished players, FALSE to show others
                    return vanishedPlayers.contains(pRef.getUuid());
                });
            }
        } catch (Exception e) {
            logger.warning("Failed to set map filter for player: " + e.getMessage());
        }
    }
    
    /**
     * Called when a player leaves the server.
     * Removes them from active vanish tracking but preserves persisted state.
     * @return true if the player was vanished (for suppressing quit message)
     */
    public boolean onPlayerLeave(UUID playerId) {
        boolean wasVanished = vanishedPlayers.remove(playerId);
        // Clean up stored ref
        playerStoreRefs.remove(playerId);
        // Note: We don't clear the vanished flag in PlayerFile here
        // That's intentional - the player should remain vanished when they reconnect
        return wasVanished;
    }
    
    /**
     * Update map filters for all online players.
     * Called when vanish state changes to refresh player marker visibility.
     * 
     * The filter is checked every tick by PlayerIconMarkerProvider.update().
     * Return TRUE to HIDE/skip a player, FALSE to SHOW them.
     */
    private void updateMapFiltersForAll() {
        for (Map.Entry<UUID, PlayerStoreRef> entry : playerStoreRefs.entrySet()) {
            PlayerStoreRef psr = entry.getValue();
            if (psr.ref == null || !psr.ref.isValid()) {
                continue;
            }
            
            try {
                Player player = psr.store.getComponent(psr.ref, Player.getComponentType());
                if (player == null) continue;
                
                WorldMapTracker tracker = player.getWorldMapTracker();
                if (tracker != null) {
                    // Re-apply the filter with current vanished players set
                    // TRUE = skip/hide, FALSE = show
                    tracker.setPlayerMapFilter(playerRef -> {
                        return vanishedPlayers.contains(playerRef.getUuid());
                    });
                }
            } catch (Exception e) {
                // Player may have disconnected, ignore
            }
        }
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
