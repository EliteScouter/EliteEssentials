package com.eliteessentials.services;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing private messaging between players.
 * Tracks last conversation partners for /reply functionality.
 */
public class MessageService {

    // UUID -> Last message partner UUID
    private final Map<UUID, UUID> lastMessagePartner = new ConcurrentHashMap<>();

    /**
     * Record a message between two players.
     * Updates last partner for both sender and receiver.
     */
    public void recordMessage(UUID sender, UUID receiver) {
        lastMessagePartner.put(sender, receiver);
        lastMessagePartner.put(receiver, sender);
    }

    /**
     * Get the last message partner for a player.
     * @return Partner UUID or null if no recent conversation
     */
    public UUID getLastPartner(UUID playerId) {
        return lastMessagePartner.get(playerId);
    }

    /**
     * Remove a player from tracking (on disconnect).
     */
    public void removePlayer(UUID playerId) {
        lastMessagePartner.remove(playerId);
    }

    /**
     * Clear all message tracking.
     */
    public void clearAll() {
        lastMessagePartner.clear();
    }
}
