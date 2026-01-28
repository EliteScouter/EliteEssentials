package com.eliteessentials.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Handles loading and saving MOTD content from motd.json.
 * Supports:
 * - Global MOTD (shown on server join)
 * - Per-world MOTDs (shown when entering specific worlds)
 * - Options for showing world MOTD always or once per session
 */
public class MotdStorage {
    
    private static final Logger logger = Logger.getLogger("EliteEssentials");
    private static final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();
    
    private final File motdFile;
    private final Object fileLock = new Object();
    private List<String> motdLines;
    private Map<String, WorldMotd> worldMotds;
    
    public MotdStorage(File dataFolder) {
        this.motdFile = new File(dataFolder, "motd.json");
        this.motdLines = new ArrayList<>();
        this.worldMotds = new HashMap<>();
        load();
    }
    
    /**
     * Load MOTD from file or create default.
     */
    public void load() {
        if (!motdFile.exists()) {
            createDefaultMotd();
            save();
            return;
        }
        
        synchronized (fileLock) {
            try (FileReader reader = new FileReader(motdFile, StandardCharsets.UTF_8)) {
                MotdData data = gson.fromJson(reader, MotdData.class);
                if (data != null) {
                    if (data.lines != null) {
                        motdLines = data.lines;
                    } else {
                        createDefaultMotd();
                    }
                    if (data.worldMotds != null) {
                        worldMotds = data.worldMotds;
                    } else {
                        worldMotds = new HashMap<>();
                    }
                } else {
                    createDefaultMotd();
                }
            } catch (IOException e) {
                logger.warning("Could not load motd.json: " + e.getMessage());
                createDefaultMotd();
            }
        }
    }
    
    /**
     * Save MOTD to file.
     */
    public void save() {
        synchronized (fileLock) {
            try (FileWriter writer = new FileWriter(motdFile, StandardCharsets.UTF_8)) {
                MotdData data = new MotdData();
                data.lines = motdLines;
                data.worldMotds = worldMotds;
                gson.toJson(data, writer);
            } catch (IOException e) {
                logger.severe("Could not save motd.json: " + e.getMessage());
            }
        }
    }
    
    /**
     * Get global MOTD lines (shown on server join).
     */
    public List<String> getMotdLines() {
        return new ArrayList<>(motdLines);
    }
    
    /**
     * Set global MOTD lines.
     */
    public void setMotdLines(List<String> lines) {
        this.motdLines = new ArrayList<>(lines);
        save();
    }
    
    /**
     * Get world-specific MOTD.
     * @param worldName The world name
     * @return WorldMotd or null if not configured
     */
    public WorldMotd getWorldMotd(String worldName) {
        if (worldName == null) return null;
        // Try exact match first, then case-insensitive
        WorldMotd motd = worldMotds.get(worldName);
        if (motd != null) return motd;
        
        for (Map.Entry<String, WorldMotd> entry : worldMotds.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(worldName)) {
                return entry.getValue();
            }
        }
        return null;
    }
    
    /**
     * Check if a world has a configured MOTD.
     */
    public boolean hasWorldMotd(String worldName) {
        return getWorldMotd(worldName) != null;
    }
    
    /**
     * Get all world MOTDs.
     */
    public Map<String, WorldMotd> getWorldMotds() {
        return new HashMap<>(worldMotds);
    }
    
    /**
     * Create default MOTD with attractive formatting.
     */
    private void createDefaultMotd() {
        motdLines = new ArrayList<>();
        motdLines.add("");
        motdLines.add("&6&l========================================");
        motdLines.add("&b&l     Welcome to {server}, &f{player}&b&l!");
        motdLines.add("&6&l========================================");
        motdLines.add("");
        motdLines.add("&7There are &e{playercount} &7players online.");
        motdLines.add("&7You are in world &a{world}&7.");
        motdLines.add("");
        motdLines.add("&6&l> &eServer Resources:");
        motdLines.add("  &7* Type &a/help&7 for commands");
        motdLines.add("  &7* Type &a/rules&7 for rules");
        motdLines.add("");
        motdLines.add("&7EliteEssentials is brought to you by: &bEliteScouter");
        motdLines.add("&bhttps://github.com/EliteScouter/EliteEssentials");
        motdLines.add("");
        
        // Create example world MOTD
        worldMotds = new HashMap<>();
        WorldMotd exploreMotd = new WorldMotd();
        exploreMotd.enabled = false; // Disabled by default as example
        exploreMotd.showAlways = false;
        exploreMotd.lines = new ArrayList<>();
        exploreMotd.lines.add("");
        exploreMotd.lines.add("&a&l=== Welcome to Explore! ===");
        exploreMotd.lines.add("&7This is the exploration world.");
        exploreMotd.lines.add("&7Be careful out there, &f{player}&7!");
        exploreMotd.lines.add("");
        worldMotds.put("explore", exploreMotd);
    }
    
    /**
     * POJO for JSON serialization.
     */
    private static class MotdData {
        /** Global MOTD lines shown on server join */
        public List<String> lines;
        
        /** Per-world MOTDs */
        public Map<String, WorldMotd> worldMotds;
    }
    
    /**
     * World-specific MOTD configuration.
     */
    public static class WorldMotd {
        /** Enable this world's MOTD */
        public boolean enabled = true;
        
        /** 
         * Show MOTD every time player enters this world.
         * When false, only shows once per session (first time entering the world).
         */
        public boolean showAlways = false;
        
        /** MOTD lines for this world */
        public List<String> lines = new ArrayList<>();
    }
}
