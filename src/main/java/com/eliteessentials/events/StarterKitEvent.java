package com.eliteessentials.events;

import com.eliteessentials.model.Kit;
import com.eliteessentials.model.KitItem;
import com.eliteessentials.services.KitService;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Logger;

/**
 * Handles giving starter kits to new players on first join.
 */
public class StarterKitEvent {
    private static final Logger logger = Logger.getLogger("EliteEssentials");
    
    private final KitService kitService;
    private final File dataFolder;
    private final Set<UUID> joinedPlayers = new HashSet<>();
    private final File joinedFile;

    public StarterKitEvent(@Nonnull KitService kitService, @Nonnull File dataFolder) {
        this.kitService = kitService;
        this.dataFolder = dataFolder;
        this.joinedFile = new File(dataFolder, "joined_players.txt");
        loadJoinedPlayers();
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
            
            Store<EntityStore> store = ref.getStore();
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            if (playerRef == null) {
                logger.warning("PlayerReadyEvent: playerRef is null");
                return;
            }
            
            UUID uuid = playerRef.getUuid();
            String username = playerRef.getUsername();
            logger.info("PlayerReadyEvent for: " + username + " (" + uuid + ")");
            
            // Check if this is a new player
            if (joinedPlayers.contains(uuid)) {
                logger.info("Player " + username + " has already joined before, skipping starter kit");
                return;
            }
            
            // Mark as joined
            joinedPlayers.add(uuid);
            saveJoinedPlayers();
            logger.info("Marked " + username + " as joined (first time)");
            
            // Get starter kits
            List<Kit> starterKits = kitService.getStarterKits();
            logger.info("Found " + starterKits.size() + " starter kits");
            
            if (starterKits.isEmpty()) {
                logger.info("No starter kits configured");
                return;
            }
            
            // Get player component
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) {
                logger.warning("Could not get player component for new player " + username);
                return;
            }
            
            Inventory inventory = player.getInventory();
            if (inventory == null) {
                logger.warning("Could not get inventory for new player " + username);
                return;
            }
            
            for (Kit kit : starterKits) {
                logger.info("Applying starter kit '" + kit.getDisplayName() + "' to " + username);
                applyKit(kit, inventory);
                
                // Mark as claimed if onetime
                if (kit.isOnetime()) {
                    kitService.setOnetimeClaimed(uuid, kit.getId());
                }
                
                logger.info("Applied starter kit '" + kit.getDisplayName() + "' to new player " + username);
            }
            
            // Sync inventory to client
            player.sendInventory();
            logger.info("Sent inventory update to " + username);
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
     * Reload joined players from file (clears in-memory cache and reloads)
     */
    public void reload() {
        joinedPlayers.clear();
        loadJoinedPlayers();
        logger.info("Reloaded joined players list (" + joinedPlayers.size() + " players)");
    }

    private void loadJoinedPlayers() {
        if (!joinedFile.exists()) {
            return;
        }
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(joinedFile), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    try {
                        joinedPlayers.add(UUID.fromString(line));
                    } catch (IllegalArgumentException e) {
                        // Skip invalid UUIDs
                    }
                }
            }
        } catch (IOException e) {
            logger.warning("Failed to load joined players: " + e.getMessage());
        }
    }

    private void saveJoinedPlayers() {
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        
        try (PrintWriter writer = new PrintWriter(
                new OutputStreamWriter(new FileOutputStream(joinedFile), StandardCharsets.UTF_8))) {
            for (UUID uuid : joinedPlayers) {
                writer.println(uuid.toString());
            }
        } catch (IOException e) {
            logger.warning("Failed to save joined players: " + e.getMessage());
        }
    }
}
