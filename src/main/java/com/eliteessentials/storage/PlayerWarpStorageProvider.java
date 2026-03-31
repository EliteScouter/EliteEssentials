package com.eliteessentials.storage;

import com.eliteessentials.model.PlayerWarp;

import java.util.*;

/**
 * Abstraction interface for player warp data storage.
 * Implemented by JSON file storage (PlayerWarpStorage) and SQL storage (SqlPlayerWarpStorage).
 */
public interface PlayerWarpStorageProvider {

    /** Get all player warps. */
    Map<String, PlayerWarp> getAllWarps();

    /** Get a player warp by name (case-insensitive). */
    Optional<PlayerWarp> getWarp(String name);

    /** Create or update a player warp. */
    void setWarp(PlayerWarp warp);

    /** Delete a player warp by name. Returns true if it existed. */
    boolean deleteWarp(String name);

    /** Check if a player warp exists by name. */
    boolean hasWarp(String name);

    /** Get all warps owned by a specific player. */
    List<PlayerWarp> getWarpsByOwner(UUID ownerId);

    /** Get the count of warps owned by a specific player. */
    int getWarpCountByOwner(UUID ownerId);

    /** Get all public warps. */
    List<PlayerWarp> getPublicWarps();

    /** Get warps accessible to a player (their own + all public). */
    List<PlayerWarp> getAccessibleWarps(UUID playerId);

    /** Lifecycle methods. */
    void load();
    void save();
    void shutdown();
}
