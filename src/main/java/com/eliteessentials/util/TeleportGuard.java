package com.eliteessentials.util;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Prevents multiple Teleport components from being applied to the same player
 * in rapid succession, which causes client/server desync and "Incorrect teleport ID" kicks.
 *
 * The guard tracks when a teleport was last queued for each player. Automatic teleports
 * (respawn, join-to-spawn) will be skipped if another teleport is already in-flight.
 * User-initiated teleports (commands, GUI) always go through but still update the timestamp.
 */
public class TeleportGuard {

    private static final Logger logger = Logger.getLogger("EliteEssentials");

    /** How long (ms) a teleport is considered "in-flight" before another automatic one is allowed. */
    private static final long GUARD_WINDOW_MS = 3000;

    /** Singleton instance */
    private static final TeleportGuard INSTANCE = new TeleportGuard();

    /** playerId -> timestamp of last teleport component application */
    private final ConcurrentHashMap<UUID, Long> pendingTeleports = new ConcurrentHashMap<>();

    private TeleportGuard() {}

    public static TeleportGuard get() {
        return INSTANCE;
    }

    /**
     * Try to acquire the teleport guard for an automatic (non-user-initiated) teleport.
     * Returns true if the teleport should proceed, false if another teleport is already in-flight.
     *
     * @param playerId The player UUID
     * @param source   A label for logging (e.g. "Respawn", "SpawnOnLogin")
     * @param debug    Whether debug logging is enabled
     * @return true if the automatic teleport may proceed
     */
    public boolean tryAcquireAutomatic(UUID playerId, String source, boolean debug) {
        long now = System.currentTimeMillis();
        Long lastTeleport = pendingTeleports.get(playerId);

        if (lastTeleport != null && (now - lastTeleport) < GUARD_WINDOW_MS) {
            if (debug) {
                long elapsed = now - lastTeleport;
                logger.info("[TeleportGuard] BLOCKED automatic teleport for " + playerId +
                        " from [" + source + "] - another teleport was queued " + elapsed + "ms ago (window=" + GUARD_WINDOW_MS + "ms)");
            }
            return false;
        }

        pendingTeleports.put(playerId, now);
        if (debug) {
            logger.info("[TeleportGuard] ALLOWED automatic teleport for " + playerId + " from [" + source + "]");
        }
        return true;
    }

    /**
     * Record a user-initiated teleport. These always proceed but update the timestamp
     * so that subsequent automatic teleports are blocked during the guard window.
     *
     * @param playerId The player UUID
     * @param source   A label for logging (e.g. "HomeCommand", "WarpGUI")
     * @param debug    Whether debug logging is enabled
     */
    public void recordUserTeleport(UUID playerId, String source, boolean debug) {
        pendingTeleports.put(playerId, System.currentTimeMillis());
        if (debug) {
            logger.info("[TeleportGuard] Recorded user-initiated teleport for " + playerId + " from [" + source + "]");
        }
    }

    /**
     * Clear the guard for a player (e.g. on disconnect).
     */
    public void clear(UUID playerId) {
        pendingTeleports.remove(playerId);
    }
}
