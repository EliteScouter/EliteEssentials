package com.eliteessentials.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Handles loading and saving MOTD content from motd.json.
 * Stores multi-line MOTD messages with color code and placeholder support.
 */
public class MotdStorage {
    
    private static final Logger logger = Logger.getLogger("EliteEssentials");
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    
    private final File motdFile;
    private final Object fileLock = new Object();
    private List<String> motdLines;
    
    public MotdStorage(File dataFolder) {
        this.motdFile = new File(dataFolder, "motd.json");
        this.motdLines = new ArrayList<>();
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
                if (data != null && data.lines != null) {
                    motdLines = data.lines;
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
                gson.toJson(data, writer);
            } catch (IOException e) {
                logger.severe("Could not save motd.json: " + e.getMessage());
            }
        }
    }
    
    /**
     * Get MOTD lines.
     */
    public List<String> getMotdLines() {
        return new ArrayList<>(motdLines);
    }
    
    /**
     * Set MOTD lines.
     */
    public void setMotdLines(List<String> lines) {
        this.motdLines = new ArrayList<>(lines);
        save();
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
    }
    
    /**
     * POJO for JSON serialization.
     */
    private static class MotdData {
        public List<String> lines;
    }
}
