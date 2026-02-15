package com.eliteessentials.services;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Manages frozen players.
 * Freeze works by setting all movement speeds to 0 via MovementSettings.
 * State persists across restarts via freezes.json.
 */
public class FreezeService {

    private static final Logger logger = Logger.getLogger("EliteEssentials");
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Type FREEZE_MAP_TYPE = new TypeToken<Map<String, FreezeEntry>>(){}.getType();

    private final File freezeFile;
    private final Object fileLock = new Object();
    private final Map<String, FreezeEntry> frozenPlayers = new ConcurrentHashMap<>();

    public FreezeService(File dataFolder) {
        this.freezeFile = new File(dataFolder, "freezes.json");
        load();
    }

    public void load() {
        if (!freezeFile.exists()) {
            return;
        }
        synchronized (fileLock) {
            try (Reader reader = new InputStreamReader(new FileInputStream(freezeFile), StandardCharsets.UTF_8)) {
                Map<String, FreezeEntry> loaded = gson.fromJson(reader, FREEZE_MAP_TYPE);
                frozenPlayers.clear();
                if (loaded != null) {
                    frozenPlayers.putAll(loaded);
                }
                logger.info("[FreezeService] Loaded " + frozenPlayers.size() + " frozen players.");
            } catch (IOException e) {
                logger.severe("Could not load freezes.json: " + e.getMessage());
            }
        }
    }

    private void save() {
        synchronized (fileLock) {
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(freezeFile), StandardCharsets.UTF_8)) {
                gson.toJson(frozenPlayers, FREEZE_MAP_TYPE, writer);
            } catch (IOException e) {
                logger.severe("Could not save freezes.json: " + e.getMessage());
            }
        }
    }

    public boolean freeze(UUID playerId, String playerName, String frozenBy) {
        String key = playerId.toString();
        if (frozenPlayers.containsKey(key)) {
            return false;
        }
        FreezeEntry entry = new FreezeEntry();
        entry.playerName = playerName;
        entry.frozenBy = frozenBy;
        entry.frozenAt = System.currentTimeMillis();
        frozenPlayers.put(key, entry);
        save();
        return true;
    }

    public boolean unfreeze(UUID playerId) {
        if (frozenPlayers.remove(playerId.toString()) != null) {
            save();
            return true;
        }
        return false;
    }

    public boolean isFrozen(UUID playerId) {
        return frozenPlayers.containsKey(playerId.toString());
    }

    public FreezeEntry getFreezeEntry(UUID playerId) {
        return frozenPlayers.get(playerId.toString());
    }

    public void reload() {
        load();
    }

    public static class FreezeEntry {
        public String playerName;
        public String frozenBy;
        public long frozenAt;
    }
}
