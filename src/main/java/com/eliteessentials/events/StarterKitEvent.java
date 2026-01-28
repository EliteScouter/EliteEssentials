package com.eliteessentials.events;

import com.eliteessentials.model.Kit;
import com.eliteessentials.model.KitItem;
import com.eliteessentials.services.KitService;
import com.eliteessentials.storage.PlayerFileStorage;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.logging.Logger;

/**
 * Handles giving starter kits to new players on first join.
 * Uses PlayerFileStorage to determine if player is new (no existing player file).
 */
public class StarterKitEvent {
    private static final Logger logger = Logger.getLogger("EliteEssentials");
    
    private final KitService kitService;
    private PlayerFileStorage playerFileStorage;

    public StarterKitEvent(@Nonnull KitService kitService) {
        this.kitService = kitService;
    }
    
    /**
     * Set the player file storage (called after initialization).
     */
    public void setPlayerFileStorage(PlayerFileStorage storage) {
        this.playerFileStorage = storage;
    }

    public void registerEvents(@Nonnull EventRegistry eventRegistry) {
        logger.info("Registering StarterKitEvent for PlayerReadyEvent...");
        
        // Use PlayerReadyEvent - fires after player is fully loaded in world
        eventRegistry.registerGlobal(PlayerReadyEvent.class, event -> {
            logger.info("PlayerReadyEvent fired!");
            
            Ref<EntityStore> ref = event.getPlayerRef();
            if (!ref.isValid()) {
                logger.warning("PlayerReadyEvent: ref is not valid");
                return;
            }
            
            // Get the player from the event directly (doesn't require store access)
            Player eventPlayer = event.getPlayer();
            if (eventPlayer == null) {
                logger.warning("PlayerReadyEvent: player is null");
                return;
            }
            
            PlayerRef playerRef = eventPlayer.getPlayerRef();
            if (playerRef == null) {
                logger.warning("PlayerReadyEvent: playerRef is null");
                return;
            }
            
            UUID uuid = playerRef.getUuid();
            String username = playerRef.getUsername();
            logger.info("PlayerReadyEvent for: " + username + " (" + uuid + ")");
            
            // Check if this is a new player using PlayerFileStorage
            if (playerFileStorage == null) {
                logger.warning("PlayerFileStorage not set, cannot check first join");
                return;
            }
            
            // Check if player file exists on disk
            java.io.File playerFile = new java.io.File(playerFileStorage.getPlayersFolder(), uuid.toString() + ".json");
            boolean isNewPlayer = !playerFile.exists();
            
            if (!isNewPlayer) {
                logger.info("Player " + username + " has joined before, skipping starter kit");
                return;
            }
            
            logger.info("New player detected: " + username + " (first join)");
            
            // Get starter kits
            List<Kit> starterKits = kitService.getStarterKits();
            logger.info("Found " + starterKits.size() + " starter kits");
            
            if (starterKits.isEmpty()) {
                logger.info("No starter kits configured");
                return;
            }
            
            // Execute on the world thread to safely access store/inventory
            eventPlayer.getWorld().execute(() -> {
                if (!ref.isValid()) {
                    logger.warning("Ref became invalid before kit application");
                    return;
                }
                
                Inventory inventory = eventPlayer.getInventory();
                if (inventory == null) {
                    logger.warning("Could not get inventory for new player " + username);
                    return;
                }
                
                for (Kit kit : starterKits) {
                    logger.info("Applying starter kit '" + kit.getDisplayName() + "' to " + username);
                    applyKit(kit, inventory);
                    
                    // Mark starter kits as claimed to prevent re-claiming via /kit
                    kitService.setOnetimeClaimed(uuid, kit.getId());
                    
                    logger.info("Applied starter kit '" + kit.getDisplayName() + "' to new player " + username);
                }
                
                // Sync inventory to client
                eventPlayer.sendInventory();
                logger.info("Sent inventory update to " + username);
            });
        });
        
        logger.info("StarterKitEvent registered successfully");
    }

    private void applyKit(Kit kit, Inventory inventory) {
        for (KitItem kitItem : kit.getItems()) {
            ItemStack itemStack = new ItemStack(kitItem.itemId(), kitItem.quantity());
            ItemContainer container = getContainer(inventory, kitItem.section());
            
            if (container != null) {
                short slot = (short) kitItem.slot();
                if (slot >= 0 && slot < container.getCapacity()) {
                    ItemStack existing = container.getItemStack(slot);
                    if (existing == null || existing.isEmpty()) {
                        container.setItemStackForSlot(slot, itemStack);
                        continue;
                    }
                }
                // Slot occupied, add anywhere
                container.addItemStack(itemStack);
            }
        }
    }

    private ItemContainer getContainer(Inventory inventory, String section) {
        return switch (section.toLowerCase()) {
            case "hotbar" -> inventory.getHotbar();
            case "storage" -> inventory.getStorage();
            case "armor" -> inventory.getArmor();
            case "utility" -> inventory.getUtility();
            case "tools" -> inventory.getTools();
            default -> inventory.getHotbar();
        };
    }

    /**
     * Reload - no longer needed since we use PlayerFileStorage.
     */
    public void reload() {
        // No-op - PlayerFileStorage handles its own reloading
        logger.info("StarterKitEvent reload (no action needed - uses PlayerFileStorage)");
    }
}
