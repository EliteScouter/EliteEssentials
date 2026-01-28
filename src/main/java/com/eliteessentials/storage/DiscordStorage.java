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
 * Handles loading and saving discord information from discord.json.
 * Stores multi-line discord info with color code support and clickable links.
 */
public class DiscordStorage {
    
    private static final Logger logger = Logger.getLogger("EliteEssentials");
    private static final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();
    
    private final File discordFile;
    private final Object fileLock = new Object();
    private List<String> discordLines;
    
    public DiscordStorage(File dataFolder) {
        this.discordFile = new File(dataFolder, "discord.json");
        this.discordLines = new ArrayList<>();
        load();
    }
    
    /**
     * Load discord info from file or create default.
     */
    public void load() {
        if (!discordFile.exists()) {
            createDefaultDiscord();
            save();
            return;
        }
        
        synchronized (fileLock) {
            try (FileReader reader = new FileReader(discordFile, StandardCharsets.UTF_8)) {
                DiscordData data = gson.fromJson(reader, DiscordData.class);
                if (data != null && data.lines != null) {
                    discordLines = data.lines;
                } else {
                    createDefaultDiscord();
                }
            } catch (IOException e) {
                logger.warning("Could not load discord.json: " + e.getMessage());
                createDefaultDiscord();
            }
        }
    }
    
    /**
     * Save discord info to file.
     */
    public void save() {
        synchronized (fileLock) {
            try (FileWriter writer = new FileWriter(discordFile, StandardCharsets.UTF_8)) {
                DiscordData data = new DiscordData();
                data.lines = discordLines;
                gson.toJson(data, writer);
            } catch (IOException e) {
                logger.severe("Could not save discord.json: " + e.getMessage());
            }
        }
    }
    
    /**
     * Get discord lines.
     */
    public List<String> getDiscordLines() {
        return new ArrayList<>(discordLines);
    }
    
    /**
     * Set discord lines.
     */
    public void setDiscordLines(List<String> lines) {
        this.discordLines = new ArrayList<>(lines);
        save();
    }
    
    /**
     * Create default discord info with attractive formatting.
     * THIS IS AN EXAMPLE - server owners should customize this!
     */
    private void createDefaultDiscord() {
        discordLines = new ArrayList<>();
        discordLines.add("");
        discordLines.add("&b&l========================================");
        discordLines.add("&e&l         JOIN OUR DISCORD!");
        discordLines.add("&b&l========================================");
        discordLines.add("");
        discordLines.add("&7THIS IS AN EXAMPLE - Edit discord.json to customize!");
        discordLines.add("");
        discordLines.add("&aWELCOME TO EliteEssentials!");
        discordLines.add("");
        discordLines.add("&7Please join our discord at:");
        discordLines.add("&bhttps://discord.gg/CEP7XuH2D2");
        discordLines.add("");
        discordLines.add("&7Click the link above to join!");
        discordLines.add("");
    }
    
    /**
     * POJO for JSON serialization.
     */
    private static class DiscordData {
        public List<String> lines;
    }
}
