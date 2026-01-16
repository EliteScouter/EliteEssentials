package com.eliteessentials.services;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing god mode (invulnerability) state.
 * Tracks which players have god mode enabled.
 */
public class GodService {

    // UUID -> God mode enabled
    private final Map<UUID, Boolean> godModeState = new ConcurrentHashMap<>();

    /**
     * Check if a player has god mode enabled.
     */
    public boolean isGodMode(UUID playerId) {
        return godModeState.getOrDefault(playerId, false);
    }

    /**
     * Toggle god mode for a player.
     * @return true if god mode is now ON, false if OFF
     */
    public boolean toggleGodMode(UUID playerId) {
        boolean newState = !isGodMode(playerId);
        godModeState.put(playerId, newState);
        return newState;
    }

    /**
     * Set god mode state for a player.
     */
    public void setGodMode(UUID playerId, boolean enabled) {
        godModeState.put(playerId, enabled);
    }

    /**
     * Remove god mode tracking for a player (on disconnect).
     */
    public void removePlayer(UUID playerId) {
        godModeState.remove(playerId);
    }

    /**
     * Clear all god mode states.
     */
    public void clearAll() {
        godModeState.clear();
    }
}
