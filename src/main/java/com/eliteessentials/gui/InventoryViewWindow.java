package com.eliteessentials.gui;

import com.hypixel.hytale.server.core.entity.entities.player.windows.ContainerWindow;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;

import javax.annotation.Nonnull;

/**
 * A window that displays another player's live inventory for viewing and editing.
 * Uses the same approach as Hytale's built-in /inv see command.
 */
public class InventoryViewWindow extends ContainerWindow {

    public InventoryViewWindow(@Nonnull CombinedItemContainer targetInventory) {
        super(targetInventory);
    }
}
