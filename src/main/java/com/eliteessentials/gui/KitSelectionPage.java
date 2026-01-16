package com.eliteessentials.gui;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.model.Kit;
import com.eliteessentials.model.KitItem;
import com.eliteessentials.permissions.PermissionService;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.services.KitService;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.ItemUtils;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * EliteEssentials Kit Selection GUI.
 * A unique, stylish interface for selecting and claiming kits.
 */
public class KitSelectionPage extends InteractiveCustomUIPage<KitSelectionPage.KitPageData> {

    private final KitService kitService;
    private final ConfigManager configManager;

    public KitSelectionPage(PlayerRef playerRef, KitService kitService, ConfigManager configManager) {
        super(playerRef, CustomPageLifetime.CanDismiss, KitPageData.CODEC);
        this.kitService = kitService;
        this.configManager = configManager;
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder commandBuilder,
                      UIEventBuilder eventBuilder, Store<EntityStore> store) {
        // Load the custom UI page
        commandBuilder.append("Pages/EliteEssentials_KitPage.ui");

        List<Kit> allKits = new ArrayList<>(kitService.getAllKits());
        UUID playerId = playerRef.getUuid();
        
        if (allKits.isEmpty()) {
            return;
        }

        // Build kit entries
        for (int i = 0; i < allKits.size(); i++) {
            Kit kit = allKits.get(i);
            String selector = "#KitCards[" + i + "]";

            // Add kit entry UI element
            commandBuilder.append("#KitCards", "Pages/EliteEssentials_KitEntry.ui");
            
            // Set kit name with gradient effect
            commandBuilder.set(selector + " #KitName.Text", kit.getDisplayName());
            
            // Set description
            commandBuilder.set(selector + " #KitDescription.Text", kit.getDescription());

            // Check permission and cooldown
            String kitPermission = Permissions.kitAccess(kit.getId());
            boolean hasPermission = PermissionService.get().hasPermission(playerId, kitPermission) ||
                                   PermissionService.get().isAdmin(playerId);

            String statusText;
            
            if (!hasPermission) {
                statusText = "[Locked]";
            } else if (kit.isOnetime() && kitService.hasClaimedOnetime(playerId, kit.getId())) {
                statusText = "Claimed";
            } else {
                long remainingCooldown = kitService.getRemainingCooldown(playerId, kit.getId());
                if (remainingCooldown > 0) {
                    statusText = formatCooldown(remainingCooldown);
                } else {
                    statusText = "Ready";
                }
            }
            
            commandBuilder.set(selector + " #KitStatus.Text", statusText);

            // Bind click event
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                selector,
                EventData.of("Kit", kit.getId())
            );
        }
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, KitPageData data) {
        if (data.kit == null || data.kit.isEmpty()) {
            return;
        }

        UUID playerId = playerRef.getUuid();
        Kit kit = kitService.getKit(data.kit);
        
        if (kit == null) {
            sendMessage(configManager.getMessage("kitNotFound"), "#FF5555");
            this.close();
            return;
        }

        // Check permission
        String kitPermission = Permissions.kitAccess(kit.getId());
        if (!PermissionService.get().hasPermission(playerId, kitPermission) &&
            !PermissionService.get().isAdmin(playerId)) {
            sendMessage(configManager.getMessage("kitNoPermission"), "#FF5555");
            this.close();
            return;
        }

        // Check cooldown (unless player has bypass)
        boolean canBypass = PermissionService.get().hasPermission(playerId, Permissions.KIT_BYPASS_COOLDOWN);
        if (!canBypass) {
            // Check one-time kit
            if (kit.isOnetime() && kitService.hasClaimedOnetime(playerId, kit.getId())) {
                sendMessage(configManager.getMessage("kitAlreadyClaimed"), "#FF5555");
                this.close();
                return;
            }
            
            long remaining = kitService.getRemainingCooldown(playerId, kit.getId());
            if (remaining > 0) {
                sendMessage(configManager.getMessage("kitOnCooldown", 
                    "time", formatCooldown(remaining)), "#FF5555");
                this.close();
                return;
            }
        }

        // Get player inventory
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            sendMessage(configManager.getMessage("kitClaimFailed"), "#FF5555");
            this.close();
            return;
        }

        Inventory inventory = player.getInventory();
        if (inventory == null) {
            sendMessage(configManager.getMessage("kitClaimFailed"), "#FF5555");
            this.close();
            return;
        }

        // Clear inventory if replace mode
        if (kit.isReplaceInventory()) {
            inventory.clear();
        }

        // Apply kit items
        applyKit(kit, inventory, ref, store);

        // Sync inventory
        player.sendInventory();

        // Set cooldown or mark as claimed
        if (kit.isOnetime()) {
            kitService.setOnetimeClaimed(playerId, kit.getId());
        } else if (kit.getCooldown() > 0) {
            kitService.setKitUsed(playerId, kit.getId());
        }

        sendMessage(configManager.getMessage("kitClaimed", 
            "kit", kit.getDisplayName()), "#55FF55");
        this.close();
    }

    /**
     * Apply kit items to player inventory.
     */
    private void applyKit(Kit kit, Inventory inventory, Ref<EntityStore> ref, Store<EntityStore> store) {
        for (KitItem kitItem : kit.getItems()) {
            ItemStack itemStack = new ItemStack(kitItem.itemId(), kitItem.quantity());
            ItemStack remainder = addItemToInventory(inventory, kitItem, itemStack);
            
            // Drop overflow on ground
            if (remainder != null && !remainder.isEmpty()) {
                ItemUtils.dropItem(ref, remainder, store);
            }
        }
    }

    /**
     * Add item to inventory, returning any overflow.
     */
    private ItemStack addItemToInventory(Inventory inventory, KitItem kitItem, ItemStack itemStack) {
        ItemContainer container = getContainer(inventory, kitItem.section());
        
        if (container != null) {
            short slot = (short) kitItem.slot();
            if (slot >= 0 && slot < container.getCapacity()) {
                ItemStack existing = container.getItemStack(slot);
                if (existing == null || existing.isEmpty()) {
                    container.setItemStackForSlot(slot, itemStack);
                    return null;
                }
            }
            
            // Slot occupied - try adding anywhere
            String section = kitItem.section().toLowerCase();
            if (section.equals("armor") || section.equals("utility") || section.equals("tools")) {
                ItemStackTransaction tx = inventory.getCombinedHotbarFirst().addItemStack(itemStack);
                return tx.getRemainder();
            }
            
            ItemStackTransaction tx = container.addItemStack(itemStack);
            return tx.getRemainder();
        }
        
        // Unknown section - add to hotbar/storage
        ItemStackTransaction tx = inventory.getCombinedHotbarFirst().addItemStack(itemStack);
        return tx.getRemainder();
    }

    private ItemContainer getContainer(Inventory inventory, String section) {
        return switch (section.toLowerCase()) {
            case "hotbar" -> inventory.getHotbar();
            case "storage" -> inventory.getStorage();
            case "armor" -> inventory.getArmor();
            case "utility" -> inventory.getUtility();
            case "tools" -> inventory.getTools();
            default -> null;
        };
    }

    private void sendMessage(String message, String color) {
        playerRef.sendMessage(Message.raw(message).color(color));
    }

    /**
     * Format cooldown seconds into readable string.
     */
    private String formatCooldown(long seconds) {
        if (seconds < 60) {
            return seconds + "s";
        } else if (seconds < 3600) {
            return (seconds / 60) + "m " + (seconds % 60) + "s";
        } else {
            long hours = seconds / 3600;
            long mins = (seconds % 3600) / 60;
            return hours + "h " + mins + "m";
        }
    }

    /**
     * Event data for kit selection.
     */
    public static class KitPageData {
        public static final BuilderCodec<KitPageData> CODEC = BuilderCodec.builder(KitPageData.class, KitPageData::new)
                .append(new KeyedCodec<>("Kit", Codec.STRING), (data, s) -> data.kit = s, data -> data.kit)
                .add()
                .build();

        private String kit;

        public String getKit() {
            return kit;
        }
    }
}
