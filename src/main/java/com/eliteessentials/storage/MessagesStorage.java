package com.eliteessentials.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Storage for plugin messages.
 * Messages are stored in messages.json separate from config.json.
 * 
 * On first load, if messages.json doesn't exist but config.json has messages,
 * the messages are migrated from config.json to messages.json.
 */
public class MessagesStorage {

    private static final Logger logger = Logger.getLogger("EliteEssentials");
    private static final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()  // Keep & as & instead of \u0026
            .create();
    private static final Type MESSAGES_TYPE = new TypeToken<Map<String, String>>(){}.getType();

    private final File dataFolder;
    private final Object fileLock = new Object();
    private Map<String, String> messages = new HashMap<>();

    public MessagesStorage(File dataFolder) {
        this.dataFolder = dataFolder;
    }

    /**
     * Load messages from messages.json.
     * If file doesn't exist, returns false (caller should check for migration).
     */
    public boolean load() {
        File file = new File(dataFolder, "messages.json");
        if (!file.exists()) {
            return false;
        }

        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            Map<String, String> loaded = gson.fromJson(reader, MESSAGES_TYPE);
            if (loaded != null) {
                messages = loaded;
                logger.info("Loaded " + messages.size() + " messages from messages.json");
                return true;
            }
        } catch (Exception e) {
            logger.warning("Failed to load messages.json: " + e.getMessage());
        }
        return false;
    }

    /**
     * Save messages to messages.json.
     */
    public void save() {
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        File file = new File(dataFolder, "messages.json");
        synchronized (fileLock) {
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
                gson.toJson(messages, writer);
                logger.info("Saved " + messages.size() + " messages to messages.json");
            } catch (Exception e) {
                logger.warning("Failed to save messages.json: " + e.getMessage());
            }
        }
    }

    /**
     * Set all messages (used during migration or initialization).
     */
    public void setMessages(Map<String, String> messages) {
        this.messages = new HashMap<>(messages);
    }

    /**
     * Get all messages.
     */
    public Map<String, String> getMessages() {
        return messages;
    }

    /**
     * Get a specific message by key.
     */
    public String getMessage(String key) {
        return messages.getOrDefault(key, "&cMissing message: " + key);
    }

    /**
     * Get a message with placeholder replacements.
     */
    public String getMessage(String key, String... replacements) {
        String message = getMessage(key);
        if (replacements.length % 2 != 0) {
            return message;
        }
        for (int i = 0; i < replacements.length; i += 2) {
            message = message.replace("{" + replacements[i] + "}", replacements[i + 1]);
        }
        return message;
    }

    /**
     * Set a specific message.
     */
    public void setMessage(String key, String value) {
        messages.put(key, value);
    }

    /**
     * Check if messages.json exists.
     */
    public boolean exists() {
        return new File(dataFolder, "messages.json").exists();
    }

    /**
     * Merge with default messages (add any missing keys).
     * Returns true if new messages were added.
     */
    public boolean mergeWithDefaults(Map<String, String> defaults) {
        boolean added = false;
        for (Map.Entry<String, String> entry : defaults.entrySet()) {
            if (!messages.containsKey(entry.getKey())) {
                messages.put(entry.getKey(), entry.getValue());
                added = true;
            }
        }
        return added;
    }
}
