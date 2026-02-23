package com.eliteessentials.services;

import com.eliteessentials.model.PlayerFile;
import com.eliteessentials.storage.PlayerFileStorage;
import com.eliteessentials.util.MessageFormatter;

import java.util.UUID;
import java.util.logging.Logger;

/**
 * Manages player nicknames.
 *
 * Nicknames are stored in the player's PlayerFile and persist across restarts.
 * The display name (nick or real name) is used everywhere player names appear:
 * chat, tab list, /msg, /list, join/quit messages, group chat, etc.
 *
 * Admins can set/clear nicks for other players via /nick <player> [nick].
 * Players can set their own nick if they have the permission.
 */
public class NickService {

    private static final Logger logger = Logger.getLogger("EliteEssentials");

    /** Maximum allowed length for a nickname (color codes stripped). */
    public static final int MAX_NICK_LENGTH = 32;

    private final PlayerFileStorage storage;

    public NickService(PlayerFileStorage storage) {
        this.storage = storage;
    }

    /**
     * Get the display name for a player (nickname if set, otherwise real name).
     * Returns the real username if no player file exists.
     */
    public String getDisplayName(UUID playerId, String fallbackUsername) {
        PlayerFile file = storage.getPlayer(playerId);
        if (file == null) {
            return fallbackUsername;
        }
        return file.getDisplayName();
    }

    /**
     * Get the raw nickname for a player, or null if none is set.
     */
    public String getNickname(UUID playerId) {
        PlayerFile file = storage.getPlayer(playerId);
        return file != null ? file.getNickname() : null;
    }

    /**
     * Set a nickname for a player. Saves immediately.
     *
     * @param playerId Target player UUID
     * @param nick     Nickname string (may contain color codes). Null or blank clears it.
     * @return SetNickResult describing the outcome
     */
    public SetNickResult setNick(UUID playerId, String nick) {
        PlayerFile file = storage.getPlayer(playerId);
        if (file == null) {
            return SetNickResult.PLAYER_NOT_FOUND;
        }

        if (nick == null || nick.isBlank()) {
            file.setNickname(null);
            storage.saveAndMarkDirty(playerId);
            return SetNickResult.CLEARED;
        }

        // Validate length (strip color codes before measuring)
        String stripped = MessageFormatter.toRawString(nick);
        if (stripped.isBlank()) {
            return SetNickResult.INVALID;
        }
        if (stripped.length() > MAX_NICK_LENGTH) {
            return SetNickResult.TOO_LONG;
        }

        file.setNickname(nick);
        storage.saveAndMarkDirty(playerId);

        if (logger.isLoggable(java.util.logging.Level.FINE)) {
            logger.fine("[NickService] Set nick for " + playerId + " -> " + nick);
        }

        return SetNickResult.SET;
    }

    /**
     * Clear a player's nickname. Saves immediately.
     */
    public boolean clearNick(UUID playerId) {
        PlayerFile file = storage.getPlayer(playerId);
        if (file == null) {
            return false;
        }
        file.setNickname(null);
        storage.saveAndMarkDirty(playerId);
        return true;
    }

    /**
     * Check whether a player currently has a nickname set.
     */
    public boolean hasNick(UUID playerId) {
        PlayerFile file = storage.getPlayer(playerId);
        return file != null && file.hasNickname();
    }

    public enum SetNickResult {
        SET,
        CLEARED,
        TOO_LONG,
        INVALID,
        PLAYER_NOT_FOUND
    }
}
