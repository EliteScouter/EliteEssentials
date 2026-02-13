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

public class MuteService {

    private static final Logger logger = Logger.getLogger("EliteEssentials");
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Type MUTE_MAP_TYPE = new TypeToken<Map<String, MuteEntry>>(){}.getType();

    private final File muteFile;
    private final Object fileLock = new Object();
    private final Map<String, MuteEntry> mutes = new ConcurrentHashMap<>();

    public MuteService(File dataFolder) {
        this.muteFile = new File(dataFolder, "mutes.json");
        load();
    }

    public void load() {
        if (!muteFile.exists()) {
            return;
        }
        synchronized (fileLock) {
            try (Reader reader = new InputStreamReader(new FileInputStream(muteFile), StandardCharsets.UTF_8)) {
                Map<String, MuteEntry> loaded = gson.fromJson(reader, MUTE_MAP_TYPE);
                mutes.clear();
                if (loaded != null) {
                    mutes.putAll(loaded);
                }
                logger.info("[MuteService] Loaded " + mutes.size() + " muted players.");
            } catch (IOException e) {
                logger.severe("Could not load mutes.json: " + e.getMessage());
            }
        }
    }

    private void save() {
        synchronized (fileLock) {
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(muteFile), StandardCharsets.UTF_8)) {
                gson.toJson(mutes, MUTE_MAP_TYPE, writer);
            } catch (IOException e) {
                logger.severe("Could not save mutes.json: " + e.getMessage());
            }
        }
    }

    public boolean mute(UUID playerId, String playerName, String mutedBy, String reason) {
        String key = playerId.toString();
        if (mutes.containsKey(key)) {
            return false;
        }
        MuteEntry entry = new MuteEntry();
        entry.playerName = playerName;
        entry.mutedBy = mutedBy;
        entry.reason = reason;
        entry.mutedAt = System.currentTimeMillis();
        mutes.put(key, entry);
        save();
        return true;
    }

    public boolean unmute(UUID playerId) {
        if (mutes.remove(playerId.toString()) != null) {
            save();
            return true;
        }
        return false;
    }

    public boolean isMuted(UUID playerId) {
        return mutes.containsKey(playerId.toString());
    }

    public MuteEntry getMuteEntry(UUID playerId) {
        return mutes.get(playerId.toString());
    }

    public void reload() {
        load();
    }

    public static class MuteEntry {
        public String playerName;
        public String mutedBy;
        public String reason;
        public long mutedAt;
    }
}
