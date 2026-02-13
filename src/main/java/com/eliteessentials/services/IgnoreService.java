package com.eliteessentials.services;

import com.eliteessentials.model.PlayerFile;
import com.eliteessentials.storage.PlayerFileStorage;

import java.util.*;
import java.util.logging.Logger;

public class IgnoreService {

    private static final Logger logger = Logger.getLogger("EliteEssentials");
    private final PlayerFileStorage playerFileStorage;

    public IgnoreService(PlayerFileStorage playerFileStorage) {
        this.playerFileStorage = playerFileStorage;
    }

    public boolean isIgnoring(UUID playerId, UUID targetId) {
        PlayerFile playerFile = playerFileStorage.getPlayer(playerId);
        if (playerFile == null) {
            return false;
        }
        return playerFile.isIgnoring(targetId);
    }

    public boolean addIgnore(UUID playerId, String playerName, UUID targetId) {
        PlayerFile playerFile = playerFileStorage.getPlayer(playerId, playerName);
        boolean added = playerFile.addIgnored(targetId);
        if (added) {
            playerFileStorage.saveAndMarkDirty(playerId);
        }
        return added;
    }

    public boolean removeIgnore(UUID playerId, UUID targetId) {
        PlayerFile playerFile = playerFileStorage.getPlayer(playerId);
        if (playerFile == null) {
            return false;
        }
        boolean removed = playerFile.removeIgnored(targetId);
        if (removed) {
            playerFileStorage.saveAndMarkDirty(playerId);
        }
        return removed;
    }

    public int clearAllIgnored(UUID playerId) {
        PlayerFile playerFile = playerFileStorage.getPlayer(playerId);
        if (playerFile == null) {
            return 0;
        }
        int count = playerFile.getIgnoredCount();
        if (count > 0) {
            playerFile.clearIgnored();
            playerFileStorage.saveAndMarkDirty(playerId);
        }
        return count;
    }

    public Set<UUID> getIgnoredPlayers(UUID playerId) {
        PlayerFile playerFile = playerFileStorage.getPlayer(playerId);
        if (playerFile == null) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(playerFile.getIgnoredPlayers());
    }

    public List<String> getIgnoredPlayerNames(UUID playerId) {
        Set<UUID> ignored = getIgnoredPlayers(playerId);
        List<String> names = new ArrayList<>();
        for (UUID uuid : ignored) {
            PlayerFile target = playerFileStorage.getPlayer(uuid);
            if (target != null && target.getName() != null) {
                names.add(target.getName());
            } else {
                names.add(uuid.toString().substring(0, 8) + "...");
            }
        }
        Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
        return names;
    }
}
