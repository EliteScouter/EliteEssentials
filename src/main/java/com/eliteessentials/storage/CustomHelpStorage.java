package com.eliteessentials.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Handles loading custom help entries from custom_help.json.
 * Allows server admins to add help entries for commands from other plugins
 * so they appear in /eehelp alongside EliteEssentials commands.
 */
public class CustomHelpStorage {

    private static final Logger logger = Logger.getLogger("EliteEssentials");
    private static final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private final File helpFile;
    private final Object fileLock = new Object();
    private List<CustomHelpEntry> entries;

    public CustomHelpStorage(File dataFolder) {
        this.helpFile = new File(dataFolder, "custom_help.json");
        this.entries = new ArrayList<>();
        load();
    }

    /**
     * Load custom help entries from file or create default example.
     */
    public void load() {
        if (!helpFile.exists()) {
            createDefault();
            save();
            return;
        }

        synchronized (fileLock) {
            try (FileReader reader = new FileReader(helpFile, StandardCharsets.UTF_8)) {
                Type type = new TypeToken<List<CustomHelpEntry>>(){}.getType();
                List<CustomHelpEntry> loaded = gson.fromJson(reader, type);
                if (loaded != null) {
                    entries = loaded;
                } else {
                    entries = new ArrayList<>();
                }
            } catch (IOException e) {
                logger.warning("Could not load custom_help.json: " + e.getMessage());
                entries = new ArrayList<>();
            }
        }
    }

    /**
     * Save custom help entries to file.
     */
    public void save() {
        synchronized (fileLock) {
            try (FileWriter writer = new FileWriter(helpFile, StandardCharsets.UTF_8)) {
                gson.toJson(entries, writer);
            } catch (IOException e) {
                logger.severe("Could not save custom_help.json: " + e.getMessage());
            }
        }
    }

    /**
     * Reload entries from disk.
     */
    public void reload() {
        load();
    }

    /**
     * Get all custom help entries.
     */
    public List<CustomHelpEntry> getEntries() {
        return new ArrayList<>(entries);
    }

    /**
     * Create default file with commented examples so admins know the format.
     */
    private void createDefault() {
        entries = new ArrayList<>();
        // Add disabled examples so admins can see the format
        entries.add(new CustomHelpEntry("/shop", "Open the server shop", "everyone", false));
        entries.add(new CustomHelpEntry("/crates", "View your crate keys", "everyone", false));
        entries.add(new CustomHelpEntry("/staffchat", "Toggle staff chat", "op", false));
    }

    /**
     * A single custom help entry for display in /eehelp.
     * 
     * Fields:
     * - command: The command usage string shown to players (e.g., "/shop [category]")
     * - description: Short description of what the command does
     * - permission: Who can see this entry:
     *     "everyone" = all players see it
     *     "op" = only admins/OPs see it
     *     any other string = treated as a permission node (advanced mode only)
     * - enabled: Set to false to hide this entry without deleting it
     */
    public static class CustomHelpEntry {
        public String command;
        public String description;
        public String permission;
        public boolean enabled;

        public CustomHelpEntry() {}

        public CustomHelpEntry(String command, String description, String permission, boolean enabled) {
            this.command = command;
            this.description = description;
            this.permission = permission;
            this.enabled = enabled;
        }
    }
}
