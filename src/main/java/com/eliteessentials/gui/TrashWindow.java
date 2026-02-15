package com.eliteessentials.gui;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ContainerWindow;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * A disposal window that deletes all items placed in it when closed.
 * Uses ContainerWindow with a SimpleItemContainer - players can drag items
 * into the window, and everything gets cleared on close.
 */
public class TrashWindow extends ContainerWindow {

    public TrashWindow(short capacity) {
        super(new SimpleItemContainer(capacity));
    }

    @Override
    public void onClose0(@Nonnull Ref<EntityStore> ref, @Nonnull ComponentAccessor<EntityStore> componentAccessor) {
        // Delete everything that was placed in the trash
        ItemContainer container = getItemContainer();
        if (container != null) {
            container.clear();
        }
    }
}
