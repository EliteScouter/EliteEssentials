package com.eliteessentials.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a kit configuration with items, cooldown, and display settings.
 */
public class Kit {
    private final String id;
    private final String displayName;
    private final String description;
    private final String icon;
    private final int cooldown; // in seconds, 0 = no cooldown
    private final boolean replaceInventory;
    private final boolean onetime; // true = can only be claimed once ever
    private final boolean starterKit; // true = auto-given to new players
    private final List<KitItem> items;

    public Kit(String id, String displayName, String description, String icon, 
               int cooldown, boolean replaceInventory, List<KitItem> items) {
        this(id, displayName, description, icon, cooldown, replaceInventory, false, false, items);
    }

    public Kit(String id, String displayName, String description, String icon, 
               int cooldown, boolean replaceInventory, boolean onetime, boolean starterKit, List<KitItem> items) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.icon = icon;
        this.cooldown = cooldown;
        this.replaceInventory = replaceInventory;
        this.onetime = onetime;
        this.starterKit = starterKit;
        this.items = new ArrayList<>(items);
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public String getIcon() { return icon; }
    public int getCooldown() { return cooldown; }
    public boolean isReplaceInventory() { return replaceInventory; }
    public boolean isOnetime() { return onetime; }
    public boolean isStarterKit() { return starterKit; }
    public List<KitItem> getItems() { return items; }
}
