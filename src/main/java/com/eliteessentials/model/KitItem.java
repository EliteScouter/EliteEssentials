package com.eliteessentials.model;

/**
 * Represents an item in a kit.
 */
public record KitItem(
    String itemId,
    int quantity,
    String section, // hotbar, storage, armor, utility, tools
    int slot
) {
    public KitItem {
        if (quantity < 1) quantity = 1;
        if (section == null) section = "hotbar";
        if (slot < 0) slot = 0;
    }
}
