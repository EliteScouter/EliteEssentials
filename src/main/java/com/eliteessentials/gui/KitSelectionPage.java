package com.eliteessentials.gui;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.model.Kit;
import com.eliteessentials.model.KitItem;
import com.eliteessentials.permissions.PermissionService;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.services.KitService;
import com.eliteessentials.util.MessageFormatter;
import com.eliteessentials.util.CommandExecutor;
import com.eliteessentials.gui.components.PaginationControl;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.ItemUtils;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
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
import java.util.logging.Logger;

/**
 * EliteEssentials Kit Selection GUI.
 * A unique, stylish interface for selecting and claiming kits.
 */
public class KitSelectionPage extends InteractiveCustomUIPage<KitSelectionPage.KitPageData> {

    private static final Logger logger = Logger.getLogger("EliteEssentials");
    private final KitService kitService;
    private final ConfigManager configManager;
    private int pageIndex = 0;

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

        commandBuilder.set("#PageTitleLabel.Text", configManager.getMessage("gui.KitTitle"));

        commandBuilder.clear("#Pagination");
        commandBuilder.append("#Pagination", "Pages/EliteEssentials_Pagination.ui");
        PaginationControl.setButtonLabels(
            commandBuilder,
            "#Pagination",
            configManager.getMessage("gui.PaginationPrev"),
            configManager.getMessage("gui.PaginationNext")
        );
        PaginationControl.bind(eventBuilder, "#Pagination");
        buildKitList(commandBuilder, eventBuilder);
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, KitPageData data) {
        if (data.pageAction != null) {
            if ("Next".equalsIgnoreCase(data.pageAction)) {
                pageIndex++;
            } else if ("Prev".equalsIgnoreCase(data.pageAction)) {
                pageIndex = Math.max(0, pageIndex - 1);
            }
            updateList();
            return;
        }

        if (data.kit == null || data.kit.isEmpty()) {
            return;
        }

        UUID playerId = playerRef.getUuid();
        
        // Self-heal: ensure a PlayerFile exists before any kit operation. Prevents the
        // infinite-kit exploit where a player whose PlayerReadyEvent never fired (observed
        // on abnormally slow logins) has no PlayerFile, causing KitService reads to return
        // 0 (no cooldown) and writes to silently no-op. Mirrors the guard in HytaleKitCommand.
        kitService.ensurePlayerFile(playerId, playerRef.getUsername());
        
        Kit kit = kitService.getKit(data.kit);
        
        if (kit == null) {
            sendMessage(configManager.getMessage("kitNotFound"), "#FF5555");
            this.close();
            return;
        }

        // Check permission
        String kitPermission = Permissions.kitAccess(kit.getId());
        if (!PermissionService.get().canUseEveryoneCommand(playerId, kitPermission, true) &&
            !PermissionService.get().isAdmin(playerId)) {
            sendMessage(configManager.getMessage("kitNoPermission"), "#FF5555");
            this.close();
            return;
        }

        // Check bypass permissions
        boolean canBypassOnetime = PermissionService.get().hasPermission(playerId, Permissions.KIT_BYPASS_ONETIME);
        
        // Check one-time kit (unless player has bypass)
        if (kit.isOnetime() && !canBypassOnetime && kitService.hasClaimedOnetime(playerId, kit.getId())) {
            if (configManager.isDebugEnabled()) {
                logger.info("Player " + playerId + " tried to claim one-time kit '" + kit.getId() + "' but already claimed it");
            }
            sendMessage(configManager.getMessage("kitAlreadyClaimed"), "#FF5555");
            this.close();
            return;
        }
        
        // Check cooldown using permission-based effective cooldown (per-rank via LuckPerms)
        // getRemainingCooldown already factors in bypass and per-rank overrides
        long remaining = kitService.getRemainingCooldown(playerId, kit.getId());
        if (remaining > 0) {
            sendMessage(configManager.getMessage("kitOnCooldown", 
                "time", formatCooldown(remaining)), "#FF5555");
            this.close();
            return;
        }

        // Get player component
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            sendMessage(configManager.getMessage("kitClaimFailed"), "#FF5555");
            this.close();
            return;
        }

        // Check inventory space (skip for replace-inventory kits since they clear first)
        if (!kit.isReplaceInventory() && !hasInventorySpace(kit, store, ref)) {
            sendMessage(configManager.getMessage("kitInventoryFull"), "#FF5555");
            this.close();
            return;
        }

        // Clear inventory if replace mode
        if (kit.isReplaceInventory()) {
            clearAllInventory(store, ref);
        }

        // Apply kit items
        applyKit(kit, store, ref);

        // Execute kit commands (run ANY server command as console)
        if (kit.hasCommands()) {
            CommandExecutor.setDebugEnabled(configManager.isDebugEnabled());
            CommandExecutor.executeCommands(kit.getCommands(), playerRef.getUsername(), playerId, "Kit-" + kit.getId());
        }

        // Set cooldown or mark as claimed (skip marking one-time when bypassing)
        if (kit.isOnetime() && !canBypassOnetime) {
            if (configManager.isDebugEnabled()) {
                logger.info("Marking kit '" + kit.getId() + "' as claimed for player " + playerId);
            }
            kitService.setOnetimeClaimed(playerId, kit.getId());
        } else {
            // Use effective cooldown (permission-based per-rank, or kit default)
            int effectiveCooldown = kitService.getEffectiveCooldown(playerId, kit.getId());
            if (effectiveCooldown > 0) {
                kitService.setKitUsed(playerId, kit.getId());
            }
        }

        sendMessage(configManager.getMessage("kitClaimed", 
            "kit", kit.getDisplayName()), "#55FF55");
        this.close();
    }

    /**
     * Check if the player has enough inventory space for a kit's items.
     */
    private boolean hasInventorySpace(Kit kit, Store<EntityStore> store, Ref<EntityStore> ref) {
        int slotsNeeded = 0;
        
        for (KitItem kitItem : kit.getItems()) {
            ItemContainer container = getContainer(store, ref, kitItem.section());
            
            if (container != null) {
                short slot = (short) kitItem.slot();
                if (slot >= 0 && slot < container.getCapacity()) {
                    ItemStack existing = container.getItemStack(slot);
                    if (existing == null || existing.isEmpty()) {
                        continue; // Target slot is free
                    }
                }
            }
            slotsNeeded++;
        }
        
        if (slotsNeeded == 0) return true;
        
        int emptySlots = 0;
        for (String section : new String[]{"hotbar", "storage"}) {
            ItemContainer container = getContainer(store, ref, section);
            if (container != null) {
                for (short slot = 0; slot < container.getCapacity(); slot++) {
                    ItemStack existing = container.getItemStack(slot);
                    if (existing == null || existing.isEmpty()) {
                        emptySlots++;
                    }
                }
            }
        }
        
        return emptySlots >= slotsNeeded;
    }

    /**
     * Apply kit items to player inventory using ECS InventoryComponent.
     */
    private void applyKit(Kit kit, Store<EntityStore> store, Ref<EntityStore> ref) {
        for (KitItem kitItem : kit.getItems()) {
            ItemStack itemStack = new ItemStack(kitItem.itemId(), kitItem.quantity());
            ItemStack remainder = addItemToInventory(store, ref, kitItem, itemStack);
            
            // Drop overflow on ground
            if (remainder != null && !remainder.isEmpty()) {
                ItemUtils.dropItem(ref, remainder, store);
            }
        }
    }

    /**
     * Add item to inventory, returning any overflow.
     */
    private ItemStack addItemToInventory(Store<EntityStore> store, Ref<EntityStore> ref,
                                          KitItem kitItem, ItemStack itemStack) {
        ItemContainer container = getContainer(store, ref, kitItem.section());
        
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
                CombinedItemContainer combined = InventoryComponent.getCombined(store, ref, InventoryComponent.HOTBAR_FIRST);
                ItemStackTransaction tx = combined.addItemStack(itemStack);
                return tx.getRemainder();
            }
            
            ItemStackTransaction tx = container.addItemStack(itemStack);
            return tx.getRemainder();
        }
        
        // Unknown section - add to hotbar/storage
        CombinedItemContainer combined = InventoryComponent.getCombined(store, ref, InventoryComponent.HOTBAR_FIRST);
        ItemStackTransaction tx = combined.addItemStack(itemStack);
        return tx.getRemainder();
    }

    private ItemContainer getContainer(Store<EntityStore> store, Ref<EntityStore> ref, String section) {
        InventoryComponent comp = switch (section.toLowerCase()) {
            case "hotbar" -> store.getComponent(ref, InventoryComponent.Hotbar.getComponentType());
            case "storage" -> store.getComponent(ref, InventoryComponent.Storage.getComponentType());
            case "armor" -> store.getComponent(ref, InventoryComponent.Armor.getComponentType());
            case "utility" -> store.getComponent(ref, InventoryComponent.Utility.getComponentType());
            case "tools" -> store.getComponent(ref, InventoryComponent.Tool.getComponentType());
            default -> null;
        };
        return comp != null ? comp.getInventory() : null;
    }

    /**
     * Clear all inventory sections.
     */
    private void clearAllInventory(Store<EntityStore> store, Ref<EntityStore> ref) {
        for (String section : new String[]{"hotbar", "storage", "armor", "utility", "tools"}) {
            ItemContainer container = getContainer(store, ref, section);
            if (container != null) {
                for (short slot = 0; slot < container.getCapacity(); slot++) {
                    container.setItemStackForSlot(slot, null);
                }
            }
        }
    }

    private void sendMessage(String message, String color) {
        playerRef.sendMessage(MessageFormatter.formatWithFallback(message, color));
    }

    private void updateList() {
        UICommandBuilder commandBuilder = new UICommandBuilder();
        UIEventBuilder eventBuilder = new UIEventBuilder();
        PaginationControl.setButtonLabels(
            commandBuilder,
            "#Pagination",
            configManager.getMessage("gui.PaginationPrev"),
            configManager.getMessage("gui.PaginationNext")
        );
        PaginationControl.bind(eventBuilder, "#Pagination");
        buildKitList(commandBuilder, eventBuilder);
        sendUpdate(commandBuilder, eventBuilder, false);
    }

    private void buildKitList(UICommandBuilder commandBuilder, UIEventBuilder eventBuilder) {
        List<Kit> allKits = new ArrayList<>(kitService.getAllKits());
        UUID playerId = playerRef.getUuid();
        
        // Self-heal before display-side storage reads, so hasClaimedOnetime / getRemainingCooldown
        // don't hit the fail-closed paths (which would incorrectly show "Claimed" or a full
        // cooldown for a legitimate player who simply has no PlayerFile yet).
        kitService.ensurePlayerFile(playerId, playerRef.getUsername());
        
        if (allKits.isEmpty()) {
            commandBuilder.clear("#KitCards");
            PaginationControl.setEmptyAndHide(commandBuilder, "#Pagination", configManager.getMessage("gui.PaginationLabel"));
            return;
        }

        int pageSize = Math.max(1, configManager.getConfig().gui.kitsPerPage);
        String pageLabelFormat = configManager.getMessage("gui.PaginationLabel");
        int totalPages = (int) Math.ceil(allKits.size() / (double) pageSize);
        if (pageIndex >= totalPages) {
            pageIndex = totalPages - 1;
        }
        int start = pageIndex * pageSize;
        int end = Math.min(start + pageSize, allKits.size());

        commandBuilder.clear("#KitCards");

        for (int i = start; i < end; i++) {
            Kit kit = allKits.get(i);
            int entryIndex = i - start;
            String selector = "#KitCards[" + entryIndex + "]";

            // Add kit entry UI element
            commandBuilder.append("#KitCards", "Pages/EliteEssentials_KitEntry.ui");
            
            // Check kit-specific permission
            String kitPermission = Permissions.kitAccess(kit.getId());
            boolean hasPermission = PermissionService.get().canUseEveryoneCommand(playerId, kitPermission, true) ||
                                   PermissionService.get().isAdmin(playerId);
            boolean canBypassOnetime = PermissionService.get().hasPermission(playerId, Permissions.KIT_BYPASS_ONETIME);

            String statusText;
            boolean canClaim = true;
            
            if (!hasPermission) {
                statusText = configManager.getMessage("gui.KitStatusLocked");
                canClaim = false;
            } else if (kit.isOnetime() && !canBypassOnetime && kitService.hasClaimedOnetime(playerId, kit.getId())) {
                statusText = configManager.getMessage("gui.KitStatusClaimed");
                canClaim = false;
            } else {
                // getRemainingCooldown uses permission-based effective cooldown (per-rank)
                long remainingCooldown = kitService.getRemainingCooldown(playerId, kit.getId());
                if (remainingCooldown > 0) {
                    statusText = formatCooldown(remainingCooldown);
                    canClaim = false;
                } else {
                    statusText = configManager.getMessage("gui.KitStatusReady");
                }
            }
            
            // Set kit name with status in the same line
            commandBuilder.set(selector + " #KitName.Text", kit.getDisplayName() + " (" + statusText + ")");

            // Set description
            commandBuilder.set(selector + " #KitDescription.Text", kit.getDescription());

            commandBuilder.set(selector + " #KitStatus.Text", "");
            commandBuilder.set(selector + " #KitClaimButton.Disabled", !canClaim);
            commandBuilder.set(selector + " #KitClaimButton.Text", configManager.getMessage("gui.KitClaimButton"));

            // Bind click event
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                selector + " #KitClaimButton",
                EventData.of("Kit", kit.getId())
            );
        }

        PaginationControl.updateOrHide(commandBuilder, "#Pagination", pageIndex, totalPages, pageLabelFormat);
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
                .append(new KeyedCodec<>("PageAction", Codec.STRING), (data, s) -> data.pageAction = s, data -> data.pageAction)
                .add()
                .build();

        private String kit;
        private String pageAction;

        public String getKit() {
            return kit;
        }

        public String getPageAction() {
            return pageAction;
        }
    }
}