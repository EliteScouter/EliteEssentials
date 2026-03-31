package com.eliteessentials.storage;

import com.eliteessentials.model.PlayerWarp;
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
 * JSON file-based storage for player warps.
 * Data is stored in player_warps.json keyed by warp name (lowercase).
 */
public class PlayerWarpStorage implements PlayerWarpStorageProvider {

    private static final Logger logger = Logger.getLogger("EliteEssentials");
    private static final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();
    private static final Type DATA_TYPE = new TypeToken<Map<String, PlayerWarp>>() {}.getType();

    private final File dataFolder;
    private final File warpsFile;
    private final Map<String, PlayerWarp> warps = new ConcurrentHashMap<>();
    private final Object fileLock = new Object();

    public PlayerWarpStorage(File dataFolder) {
        this.dataFolder = dataFolder;
        this.warpsFile = new File(dataFolder, "player_warps.json");
    }

    @Override
    public void load() {
        if (!warpsFile.exists()) {
            logger.info("No player_warps.json found, starting fresh.");
            return;
        }
        try (Reader reader = new InputStreamReader(new FileInputStream(warpsFile), StandardCharsets.UTF_8)) {
            Map<String, PlayerWarp> loaded = gson.fromJson(reader, DATA_TYPE);
            if (loaded != null) {
                warps.clear();
                warps.putAll(loaded);
                logger.info("Loaded " + warps.size() + " player warps.");
            }
        } catch (Exception e) {
            logger.severe("Failed to load player_warps.json: " + e.getMessage());
        }
    }

    @Override
    public void save() {
        synchronized (fileLock) {
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(warpsFile), StandardCharsets.UTF_8)) {
                gson.toJson(warps, DATA_TYPE, writer);
            } catch (Exception e) {
                logger.severe("Failed to save player_warps.json: " + e.getMessage());
            }
        }
    }

    @Override
    public Map<String, PlayerWarp> getAllWarps() {
        return new HashMap<>(warps);
    }

    @Override
    public Optional<PlayerWarp> getWarp(String name) {
        return Optional.ofNullable(warps.get(name.toLowerCase()));
    }

    @Override
    public void setWarp(PlayerWarp warp) {
        warps.put(warp.getName().toLowerCase(), warp);
        save();
    }

    @Override
    public boolean deleteWarp(String name) {
        boolean removed = warps.remove(name.toLowerCase()) != null;
        if (removed) {
            save();
        }
        return removed;
    }

    @Override
    public boolean hasWarp(String name) {
        return warps.containsKey(name.toLowerCase());
    }

    @Override
    public List<PlayerWarp> getWarpsByOwner(UUID ownerId) {
        List<PlayerWarp> result = new ArrayList<>();
        for (PlayerWarp warp : warps.values()) {
            if (ownerId.equals(warp.getOwnerId())) {
                result.add(warp);
            }
        }
        return result;
    }

    @Override
    public int getWarpCountByOwner(UUID ownerId) {
        int count = 0;
        for (PlayerWarp warp : warps.values()) {
            if (ownerId.equals(warp.getOwnerId())) {
                count++;
            }
        }
        return count;
    }

    @Override
    public List<PlayerWarp> getPublicWarps() {
        List<PlayerWarp> result = new ArrayList<>();
        for (PlayerWarp warp : warps.values()) {
            if (warp.isPublic()) {
                result.add(warp);
            }
        }
        return result;
    }

    @Override
    public List<PlayerWarp> getAccessibleWarps(UUID playerId) {
        List<PlayerWarp> result = new ArrayList<>();
        for (PlayerWarp warp : warps.values()) {
            if (warp.canAccess(playerId)) {
                result.add(warp);
            }
        }
        return result;
    }

    @Override
    public void shutdown() {
        // No-op for JSON storage
    }
}
