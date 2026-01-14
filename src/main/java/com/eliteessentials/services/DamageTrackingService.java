package com.eliteessentials.services;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service that tracks the last damage source for each player.
 * Used to determine death messages (who/what killed the player).
 */
public class DamageTrackingService {

    // Track last attacker UUID for each victim UUID
    private final Map<UUID, UUID> lastAttacker = new ConcurrentHashMap<>();
    // Track last damage cause string for each victim
    private final Map<UUID, String> lastDamageCause = new ConcurrentHashMap<>();
    // Track timestamp of last damage to expire old data
    private final Map<UUID, Long> lastDamageTime = new ConcurrentHashMap<>();
    
    // Damage expires after 10 seconds (player died from something else)
    private static final long DAMAGE_EXPIRY_MS = 10000;

    /**
     * Record that a player was attacked by another player.
     */
    public void recordPlayerAttack(UUID victimId, UUID attackerId) {
        if (victimId != null && attackerId != null) {
            lastAttacker.put(victimId, attackerId);
            lastDamageCause.put(victimId, "PLAYER");
            lastDamageTime.put(victimId, System.currentTimeMillis());
        }
    }

    /**
     * Record that a player was damaged by an entity (mob).
     */
    public void recordEntityAttack(UUID victimId, String entityType) {
        if (victimId != null) {
            lastAttacker.remove(victimId); // Not a player
            lastDamageCause.put(victimId, "ENTITY:" + (entityType != null ? entityType : "unknown"));
            lastDamageTime.put(victimId, System.currentTimeMillis());
        }
    }

    /**
     * Record environmental damage (fall, fire, drowning, etc).
     */
    public void recordEnvironmentDamage(UUID victimId, String cause) {
        if (victimId != null) {
            lastAttacker.remove(victimId);
            lastDamageCause.put(victimId, cause != null ? cause : "ENVIRONMENT");
            lastDamageTime.put(victimId, System.currentTimeMillis());
        }
    }

    /**
     * Get the UUID of the player who last attacked this player.
     * Returns null if no recent player attack or if damage expired.
     */
    public UUID getLastAttacker(UUID victimId) {
        if (victimId == null) return null;
        
        Long time = lastDamageTime.get(victimId);
        if (time == null || System.currentTimeMillis() - time > DAMAGE_EXPIRY_MS) {
            return null;
        }
        return lastAttacker.get(victimId);
    }

    /**
     * Get the last damage cause for this player.
     * Returns null if no recent damage or if damage expired.
     */
    public String getLastDamageCause(UUID victimId) {
        if (victimId == null) return null;
        
        Long time = lastDamageTime.get(victimId);
        if (time == null || System.currentTimeMillis() - time > DAMAGE_EXPIRY_MS) {
            return null;
        }
        return lastDamageCause.get(victimId);
    }

    /**
     * Clear tracking data for a player (e.g., on respawn).
     */
    public void clearPlayer(UUID playerId) {
        if (playerId != null) {
            lastAttacker.remove(playerId);
            lastDamageCause.remove(playerId);
            lastDamageTime.remove(playerId);
        }
    }
}
