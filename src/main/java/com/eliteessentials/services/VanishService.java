package com.eliteessentials.services;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.config.PluginConfig;
import com.eliteessentials.model.PlayerFile;
import com.eliteessentials.storage.PlayerFileStorage;
import com.eliteessentials.util.MessageFormatter;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.Invulnerable;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
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
 * 
 * NOTE: The mobImmunity config option provides damage immunity (Invulnerable component)
 * but NOT true NPC detection immunity. In Hytale, NPCs can only be made to ignore
 * players in Creative mode with "Allow NPC Detection" disabled. Since we don't want
 * to force players into Creative mode (security concern), vanished players in Adventure
 * mode will still be detected by mobs but won't take damage.
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
        
        // Update visibility + player list in a single pass over all players
        // This avoids multiple separate iterations which caused lag on 50+ player servers
        updateAllPlayersInSinglePass(playerId, vanished, config);
        
        // Map filters do NOT need updating here - the filter lambda already references
        // the live vanishedPlayers set, so it picks up changes automatically each tick.
        // The filter is only set once per player in onPlayerReady().
        
        // Update mob immunity (invulnerability) if enabled
        if (config.vanish.mobImmunity) {
            updateMobImmunity(playerId, vanished);
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
     * Also applies invulnerability if the player is vanished and mobImmunity is enabled.
     * 
     * NOTE: True mob immunity (NPCs not detecting player) only works in Creative mode.
     * In Adventure mode, we can only provide damage immunity via Invulnerable component.
     */
    public void onPlayerReady(Store<EntityStore> store, Ref<EntityStore> ref) {
        if (store == null || ref == null || !ref.isValid()) return;
        
        PluginConfig config = configManager.getConfig();
        
        try {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) return;
            
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            if (playerRef != null) {
                UUID playerId = playerRef.getUuid();
                
                // Store the player's store/ref for later map filter updates
                playerStoreRefs.put(playerId, new PlayerStoreRef(store, ref));
                
                // Apply invulnerability if player is vanished and mobImmunity is enabled
                // Note: This only provides damage immunity, not true NPC detection immunity
                // (that would require Creative mode which we don't want to force)
                if (config.vanish.mobImmunity && vanishedPlayers.contains(playerId)) {
                    try {
                        store.putComponent(ref, Invulnerable.getComponentType(), Invulnerable.INSTANCE);
                        
                        if (configManager.isDebugEnabled()) {
                            logger.info("[Vanish] Applied invulnerability for reconnecting vanished player: " + playerRef.getUsername());
                        }
                    } catch (Exception e) {
                        logger.warning("[Vanish] Failed to apply invulnerability on reconnect: " + e.getMessage());
                    }
                }
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
            
            // Get the appropriate message (use standard join/leave messages)
            String messageKey = vanished ? "quitMessage" : "joinMessage";
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
     * Combined single-pass update for visibility, player list, and fake message.
     * Instead of iterating all players 3-5 separate times, we do it once.
     * This is critical for servers with 50+ players where multiple iterations
     * caused noticeable lag spikes when an admin toggled vanish.
     */
    private void updateAllPlayersInSinglePass(UUID targetId, boolean hide, PluginConfig config) {
        try {
            Universe universe = Universe.get();
            if (universe == null) return;
            
            // Pre-build packets once (not per-player)
            RemoveFromServerPlayerList removePacket = config.vanish.hideFromList && hide
                ? new RemoveFromServerPlayerList(new UUID[] { targetId })
                : null;
            
            // For unhide, we need the target player info - find them while iterating
            PlayerRef targetPlayer = null;
            
            for (PlayerRef player : universe.getPlayers()) {
                UUID pid = player.getUuid();
                
                if (pid.equals(targetId)) {
                    targetPlayer = player;
                    continue; // Don't hide/remove player from themselves
                }
                
                try {
                    // 1. Update in-world visibility
                    if (hide) {
                        player.getHiddenPlayersManager().hidePlayer(targetId);
                    } else {
                        player.getHiddenPlayersManager().showPlayer(targetId);
                    }
                    
                    // 2. Update player list (reuse pre-built packet for hide)
                    if (config.vanish.hideFromList) {
                        if (hide && removePacket != null) {
                            player.getPacketHandler().write(removePacket);
                        }
                        // For unhide, we handle after the loop once we have targetPlayer
                    }
                } catch (Exception e) {
                    logger.warning("Failed to update vanish state for " + player.getUsername() + ": " + e.getMessage());
                }
            }
            
            // Handle unhide player list in a second pass only when needed (unvanishing)
            if (config.vanish.hideFromList && !hide && targetPlayer != null) {
                ServerPlayerListPlayer listPlayer = new ServerPlayerListPlayer(
                    targetPlayer.getUuid(),
                    targetPlayer.getUsername(),
                    targetPlayer.getWorldUuid(),
                    0
                );
                AddToServerPlayerList addPacket = new AddToServerPlayerList(new ServerPlayerListPlayer[] { listPlayer });
                
                for (PlayerRef player : universe.getPlayers()) {
                    if (player.getUuid().equals(targetId)) continue;
                    try {
                        player.getPacketHandler().write(addPacket);
                    } catch (Exception e) {
                        logger.warning("Failed to re-add to player list for " + player.getUsername() + ": " + e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            logger.warning("Failed to update vanish state for all players: " + e.getMessage());
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
    
    /**
     * Update invulnerability for a vanished player.
     * 
     * NOTE: True mob immunity (NPCs not detecting player) only works in Creative mode
     * with allowNPCDetection=false. Since we don't want to force players into Creative
     * mode (security concern for mods), we only provide damage immunity via the
     * Invulnerable component. Mobs will still detect and attack vanished players,
     * but they won't take damage.
     * 
     * If you're already in Creative mode, you can manually disable "Allow NPC Detection"
     * in your settings for full mob immunity.
     */
    private void updateMobImmunity(UUID playerId, boolean vanished) {
        try {
            // Use stored ref instead of iterating all players to find the target
            PlayerStoreRef psr = playerStoreRefs.get(playerId);
            if (psr == null || psr.ref == null || !psr.ref.isValid()) return;
            
            Store<EntityStore> store = psr.ref.getStore();
            EntityStore entityStore = store.getExternalData();
            if (entityStore == null) return;
            
            World world = entityStore.getWorld();
            if (world == null) return;
            
            final Ref<EntityStore> storedRef = psr.ref;
            
            world.execute(() -> {
                try {
                    if (!storedRef.isValid()) return;
                    
                    Store<EntityStore> currentStore = storedRef.getStore();
                    
                    if (vanished) {
                        currentStore.putComponent(storedRef, Invulnerable.getComponentType(), Invulnerable.INSTANCE);
                        
                        if (configManager.isDebugEnabled()) {
                            logger.info("[Vanish] Applied invulnerability (damage immunity) for vanished player");
                        }
                    } else {
                        // Remove invulnerability (unless they have god mode)
                        GodService godService = com.eliteessentials.EliteEssentials.getInstance().getGodService();
                        if (godService == null || !godService.isGodMode(playerId)) {
                            try {
                                currentStore.removeComponent(storedRef, Invulnerable.getComponentType());
                                if (configManager.isDebugEnabled()) {
                                    logger.info("[Vanish] Removed invulnerability for unvanished player");
                                }
                            } catch (IllegalArgumentException e) {
                                // Component not present, ignore
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.warning("[Vanish] Failed to update invulnerability: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            logger.warning("[Vanish] Failed to update invulnerability: " + e.getMessage());
        }
    }
}
