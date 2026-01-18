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
 * Handles loading and saving server rules from rules.json.
 * Stores multi-line rules with color code support.
 */
public class RulesStorage {
    
    private static final Logger logger = Logger.getLogger("EliteEssentials");
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    
    private final File rulesFile;
    private final Object fileLock = new Object();
    private List<String> rulesLines;
    
    public RulesStorage(File dataFolder) {
        this.rulesFile = new File(dataFolder, "rules.json");
        this.rulesLines = new ArrayList<>();
        load();
    }
    
    /**
     * Load rules from file or create default.
     */
    public void load() {
        if (!rulesFile.exists()) {
            createDefaultRules();
            save();
            return;
        }
        
        synchronized (fileLock) {
            try (FileReader reader = new FileReader(rulesFile, StandardCharsets.UTF_8)) {
                RulesData data = gson.fromJson(reader, RulesData.class);
                if (data != null && data.lines != null) {
                    rulesLines = data.lines;
                } else {
                    createDefaultRules();
                }
            } catch (IOException e) {
                logger.warning("Could not load rules.json: " + e.getMessage());
                createDefaultRules();
            }
        }
    }
    
    /**
     * Save rules to file.
     */
    public void save() {
        synchronized (fileLock) {
            try (FileWriter writer = new FileWriter(rulesFile, StandardCharsets.UTF_8)) {
                RulesData data = new RulesData();
                data.lines = rulesLines;
                gson.toJson(data, writer);
            } catch (IOException e) {
                logger.severe("Could not save rules.json: " + e.getMessage());
            }
        }
    }
    
    /**
     * Get rules lines.
     */
    public List<String> getRulesLines() {
        return new ArrayList<>(rulesLines);
    }
    
    /**
     * Set rules lines.
     */
    public void setRulesLines(List<String> lines) {
        this.rulesLines = new ArrayList<>(lines);
        save();
    }
    
    /**
     * Create default rules with attractive formatting.
     */
    private void createDefaultRules() {
        rulesLines = new ArrayList<>();
        rulesLines.add("");
        rulesLines.add("&c&l========================================");
        rulesLines.add("&e&l              SERVER RULES");
        rulesLines.add("&c&l========================================");
        rulesLines.add("");
        rulesLines.add("&a1. &7Be Respectful to others");
        rulesLines.add("&a2. &7No Cheating / Hacking");
        rulesLines.add("&a3. &7No Griefing");
        rulesLines.add("&a4. &7Have fun!");
        rulesLines.add("");
        rulesLines.add("&6Breaking these rules may result in a ban.");
        rulesLines.add("");
    }
    
    /**
     * POJO for JSON serialization.
     */
    private static class RulesData {
        public List<String> lines;
    }
}
