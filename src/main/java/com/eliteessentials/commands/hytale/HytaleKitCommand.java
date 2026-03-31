package com.eliteessentials.commands.hytale;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.gui.KitSelectionPage;
import com.eliteessentials.model.Kit;
import com.eliteessentials.model.KitItem;
import com.eliteessentials.permissions.PermissionService;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.services.KitService;
import com.eliteessentials.util.MessageFormatter;
import com.eliteessentials.util.WorldBlacklistUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.ItemUtils;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.eliteessentials.commands.args.SimpleStringArg;
import com.eliteessentials.util.CommandExecutor;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Command: /kit [name]
 * - /kit - Opens the kit selection GUI (requires eliteessentials.command.kit.gui)
 * - /kit <name> - Claims a specific kit directly (requires eliteessentials.command.kit.<name>)
 * 
 * Permissions:
 * - eliteessentials.command.kit.use - Base permission for /kit command
 * - eliteessentials.command.kit.gui - Permission to open the kit GUI
 * - eliteessentials.command.kit.<kitname> - Access specific kit
 * - eliteessentials.command.kit.bypass.cooldown - Bypass kit cooldowns
 * - eliteessentials.command.kit.bypass.onetime - Bypass one-time kit restrictions
 */
public class HytaleKitCommand extends AbstractPlayerCommand {

    private static final Logger logger = Logger.getLogger("EliteEssentials");
    private static final String COMMAND_NAME = "kit";
    
    private final KitService kitService;
    private final ConfigManager configManager;

    public HytaleKitCommand(KitService kitService, ConfigManager configManager) {
        super(COMMAND_NAME, "Open the kit selection menu or claim a specific kit");
        this.kitService = kitService;
        this.configManager = configManager;
        
        addAliases("kits");
        
        // Add variant for /kit <name> - direct kit claiming
        addUsageVariant(new KitWithNameCommand(kitService, configManager));
        
        // Add subcommands for admin operations
        addSubCommand(new HytaleKitCreateCommand(kitService));
        addSubCommand(new HytaleKitDeleteCommand(kitService));
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                          @Nonnull PlayerRef player, @Nonnull World world) {
        UUID playerId = player.getUuid();
        
        if (WorldBlacklistUtil.isWorldBlacklisted(world.getName(), configManager.getConfig().kits.blacklistedWorlds)) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("commandBlacklistedWorld"), "#FF5555"));
            return;
        }
        
        if (configManager.isDebugEnabled()) {
            logger.info("[Kit] Player " + player.getUsername() + " executing /kit");
            logger.info("[Kit] Checking KIT permission: " + Permissions.KIT);
        }
        
        // Check base kit permission
        if (!PermissionService.get().canUseEveryoneCommand(playerId, Permissions.KIT, 
                configManager.getConfig().kits.enabled)) {
            if (configManager.isDebugEnabled()) {
                logger.info("[Kit] Player " + player.getUsername() + " FAILED base kit permission check");
            }
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("noPermission"), "#FF5555"));
            return;
        }

        if (configManager.isDebugEnabled()) {
            logger.info("[Kit] Player " + player.getUsername() + " PASSED base kit permission, checking GUI...");
            logger.info("[Kit] Checking KIT_GUI permission: " + Permissions.KIT_GUI);
        }

        // Check GUI permission - use hasPermission directly for strict check
        boolean hasGuiPerm = PermissionService.get().hasPermission(playerId, Permissions.KIT_GUI);
        boolean isAdmin = PermissionService.get().isAdmin(playerId);
        
        if (configManager.isDebugEnabled()) {
            logger.info("[Kit] Player " + player.getUsername() + " GUI check: hasGuiPerm=" + hasGuiPerm + ", isAdmin=" + isAdmin);
        }
        
        if (!hasGuiPerm && !isAdmin) {
            if (configManager.isDebugEnabled()) {
                logger.info("[Kit] Player " + player.getUsername() + " FAILED GUI permission check");
            }
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("noPermission"), "#FF5555"));
            return;
        }

        // Get the Player component to access PageManager
        Player playerComponent;
        try {
            playerComponent = store.getComponent(ref, Player.getComponentType());
        } catch (Exception e) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("kitOpenFailed"), "#FF5555"));
            return;
        }
        
        if (playerComponent == null) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("kitOpenFailed"), "#FF5555"));
            return;
        }

        // Check if there are any kits
        if (kitService.getAllKits().isEmpty()) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("kitNoKits"), "#FFAA00"));
            return;
        }

        // Create and open the kit selection page
        KitSelectionPage kitPage = new KitSelectionPage(player, kitService, configManager);
        playerComponent.getPageManager().openCustomPage(ref, store, kitPage);
    }

    /**
     * Claim a kit directly by name.
     * This is a static method so it can be called from the variant command and potentially NPCs.
     */
    public static void claimKit(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref,
                                PlayerRef player, String kitName, KitService kitService, ConfigManager configManager) {
        UUID playerId = player.getUuid();
        
        // Get the kit
        Kit kit = kitService.getKit(kitName);
        if (kit == null) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("kitNotFound", "kit", kitName), "#FF5555"));
            return;
        }

        // Check kit-specific permission
        String kitPermission = Permissions.kitAccess(kit.getId());
        if (!PermissionService.get().canUseEveryoneCommand(playerId, kitPermission, true) &&
            !PermissionService.get().isAdmin(playerId)) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("kitNoPermission"), "#FF5555"));
            return;
        }

        // Check bypass permissions
        boolean canBypassOnetime = PermissionService.get().hasPermission(playerId, Permissions.KIT_BYPASS_ONETIME);

        // Check one-time kit (unless player has bypass)
        if (kit.isOnetime() && !canBypassOnetime && kitService.hasClaimedOnetime(playerId, kit.getId())) {
            if (configManager.isDebugEnabled()) {
                logger.info("Player " + playerId + " tried to claim one-time kit '" + kit.getId() + "' but already claimed it");
            }
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("kitAlreadyClaimed"), "#FF5555"));
            return;
        }

        // Check cooldown using permission-based effective cooldown (per-rank via LuckPerms)
        // getRemainingCooldown already factors in bypass and per-rank overrides
        long remaining = kitService.getRemainingCooldown(playerId, kit.getId());
        if (remaining > 0) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("kitOnCooldown", "time", formatCooldown(remaining)), "#FF5555"));
            return;
        }

        // Get player component and inventory
        Player playerComponent;
        try {
            playerComponent = store.getComponent(ref, Player.getComponentType());
        } catch (Exception e) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("kitClaimFailed"), "#FF5555"));
            return;
        }

        if (playerComponent == null) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("kitClaimFailed"), "#FF5555"));
            return;
        }

        // Check inventory space (skip for replace-inventory kits since they clear first)
        if (!kit.isReplaceInventory() && !hasInventorySpace(kit, store, ref)) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("kitInventoryFull"), "#FF5555"));
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
            CommandExecutor.executeCommands(kit.getCommands(), player.getUsername(), playerId, "Kit-" + kit.getId());
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

        ctx.sendMessage(MessageFormatter.formatWithFallback(
            configManager.getMessage("kitClaimed", "kit", kit.getDisplayName()), "#55FF55"));
    }

    /**
     * Check if the player has enough inventory space for a kit's items.
     * Counts items that can't fit in their target slot and checks if hotbar+storage
     * has enough empty slots for the overflow.
     */
    private static boolean hasInventorySpace(Kit kit, Store<EntityStore> store, Ref<EntityStore> ref) {
        int slotsNeeded = 0;
        
        for (KitItem kitItem : kit.getItems()) {
            ItemContainer container = getContainer(store, ref, kitItem.section());
            String section = kitItem.section().toLowerCase();
            
            if (container != null) {
                short slot = (short) kitItem.slot();
                if (slot >= 0 && slot < container.getCapacity()) {
                    ItemStack existing = container.getItemStack(slot);
                    if (existing == null || existing.isEmpty()) {
                        // Target slot is free for hotbar/storage items
                        if (section.equals("hotbar") || section.equals("storage")) {
                            continue;
                        }
                        // Armor/utility/tools target slot is free
                        continue;
                    }
                }
                // Target slot occupied; armor/utility/tools overflow to hotbar/storage
                // hotbar/storage items also try addItemStack which needs a free slot
            }
            // Item needs a general inventory slot
            slotsNeeded++;
        }
        
        if (slotsNeeded == 0) return true;
        
        // Count empty slots in hotbar + storage
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
    private static void applyKit(Kit kit, Store<EntityStore> store, Ref<EntityStore> ref) {
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
    private static ItemStack addItemToInventory(Store<EntityStore> store, Ref<EntityStore> ref,
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

    private static ItemContainer getContainer(Store<EntityStore> store, Ref<EntityStore> ref, String section) {
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
    private static void clearAllInventory(Store<EntityStore> store, Ref<EntityStore> ref) {
        for (String section : new String[]{"hotbar", "storage", "armor", "utility", "tools"}) {
            ItemContainer container = getContainer(store, ref, section);
            if (container != null) {
                for (short slot = 0; slot < container.getCapacity(); slot++) {
                    container.setItemStackForSlot(slot, null);
                }
            }
        }
    }

    /**
     * Format cooldown seconds into readable string.
     */
    private static String formatCooldown(long seconds) {
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
     * Variant: /kit <name>
     * Claims a specific kit directly without opening the GUI.
     */
    private static class KitWithNameCommand extends AbstractPlayerCommand {
        private final KitService kitService;
        private final ConfigManager configManager;
        private final RequiredArg<String> nameArg;
        
        KitWithNameCommand(KitService kitService, ConfigManager configManager) {
            super(COMMAND_NAME);
            this.kitService = kitService;
            this.configManager = configManager;
            this.nameArg = withRequiredArg("name", "Kit name to claim", SimpleStringArg.KIT_NAME);
        }
        
        @Override
        protected boolean canGeneratePermission() {
            return false;
        }
        
        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                              @Nonnull PlayerRef player, @Nonnull World world) {
            UUID playerId = player.getUuid();
            
            if (WorldBlacklistUtil.isWorldBlacklisted(world.getName(), configManager.getConfig().kits.blacklistedWorlds)) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("commandBlacklistedWorld"), "#FF5555"));
                return;
            }
            
            // Check base kit permission
            if (!PermissionService.get().canUseEveryoneCommand(playerId, Permissions.KIT, 
                    configManager.getConfig().kits.enabled)) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("noPermission"), "#FF5555"));
                return;
            }
            
            String kitName = ctx.get(nameArg);
            HytaleKitCommand.claimKit(ctx, store, ref, player, kitName, kitService, configManager);
        }
    }
}
