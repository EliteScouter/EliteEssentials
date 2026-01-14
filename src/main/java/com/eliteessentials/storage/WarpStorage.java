package com.eliteessentials.storage;

import com.eliteessentials.model.Warp;
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
 * Handles persistent storage of server warps.
 * Data is stored in warps.json keyed by warp name.
 */
public class WarpStorage {

    private static final Logger logger = Logger.getLogger("EliteEssentials");
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static final Type DATA_TYPE = new TypeToken<Map<String, Warp>>() {}.getType();

    private final File dataFolder;
    private final File warpsFile;
    
    // WarpName (lowercase) -> Warp
    private final Map<String, Warp> warps = new ConcurrentHashMap<>();

    public WarpStorage(File dataFolder) {
        this.dataFolder = dataFolder;
        this.warpsFile = new File(dataFolder, "warps.json");
    }

    public void load() {
        if (!warpsFile.exists()) {
            logger.info("No warps.json found, starting fresh.");
            return;
        }

        try (Reader reader = new InputStreamReader(new FileInputStream(warpsFile), StandardCharsets.UTF_8)) {
            Map<String, Warp> loaded = gson.fromJson(reader, DATA_TYPE);
            if (loaded != null) {
                warps.clear();
                warps.putAll(loaded);
                logger.info("Loaded " + warps.size() + " warps.");
            }
        } catch (Exception e) {
            logger.severe("Failed to load warps.json: " + e.getMessage());
        }
    }

    public void save() {
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(warpsFile), StandardCharsets.UTF_8)) {
            gson.toJson(warps, DATA_TYPE, writer);
            logger.info("Saved warps data.");
        } catch (Exception e) {
            logger.severe("Failed to save warps.json: " + e.getMessage());
        }
    }

    public Map<String, Warp> getAllWarps() {
        return new HashMap<>(warps);
    }

    public Optional<Warp> getWarp(String name) {
        return Optional.ofNullable(warps.get(name.toLowerCase()));
    }

    public void setWarp(Warp warp) {
        warps.put(warp.getName().toLowerCase(), warp);
        save();
    }

    public boolean deleteWarp(String name) {
        boolean removed = warps.remove(name.toLowerCase()) != null;
        if (removed) {
            save();
        }
        return removed;
    }

    public boolean hasWarp(String name) {
        return warps.containsKey(name.toLowerCase());
    }

    public Set<String> getWarpNames() {
        Set<String> names = new HashSet<>();
        for (Warp warp : warps.values()) {
            names.add(warp.getName());
        }
        return names;
    }

    public int getWarpCount() {
        return warps.size();
    }
}
