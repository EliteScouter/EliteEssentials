package com.eliteessentials.commands.hytale;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.config.PluginConfig;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.permissions.PermissionService;
import com.eliteessentials.util.CommandPermissionUtil;
import com.eliteessentials.util.MessageFormatter;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * Command: /repair [all]
 * Repairs the item in hand, or all items in inventory if "all" is specified.
 * Admin command only.
 */
public class HytaleRepairCommand extends AbstractPlayerCommand {

    private static final String COMMAND_NAME = "repair";
    
    private final ConfigManager configManager;
    private final OptionalArg<String> allFlag;

    public HytaleRepairCommand(ConfigManager configManager) {
        super(COMMAND_NAME, "Repair the item in your hand");
        this.configManager = configManager;
        this.allFlag = withOptionalArg("all", "Repair all items in inventory", ArgTypes.STRING);
        this.addAliases("fix");
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                          @Nonnull PlayerRef playerRef, @Nonnull World world) {
        PluginConfig config = configManager.getConfig();
        UUID playerId = playerRef.getUuid();
        
        // Check permission (Admin command)
        if (!CommandPermissionUtil.canExecuteAdmin(ctx, playerRef, Permissions.REPAIR, config.repair.enabled)) {
            return;
        }
        
        // Get player component
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("repairNoItem"), "#FF5555"));
            return;
        }
        
        // Check if "all" flag is provided
        boolean repairAll = allFlag.provided(ctx);
        
        if (repairAll) {
            // Check permission for repair all
            if (!PermissionService.get().hasPermission(playerId, Permissions.REPAIR_ALL) 
                && !PermissionService.get().isAdmin(playerId)) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("repairNoPermissionAll"), "#FF5555"));
                return;
            }
            
            int repairedCount = repairAllItems(player);
            if (repairedCount > 0) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("repairAllSuccess", "count", String.valueOf(repairedCount)), "#55FF55"));
            } else {
                ctx.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("repairNothingToRepair"), "#FF5555"));
            }
        } else {
            // Repair item in hand
            boolean repaired = repairItemInHand(player);
            if (repaired) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("repairSuccess"), "#55FF55"));
            } else {
                ctx.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("repairNotDamaged"), "#FF5555"));
            }
        }
    }
    
    /**
     * Repair the item in the player's active hotbar slot.
     * @return true if an item was repaired, false if no item or not damaged
     */
    private boolean repairItemInHand(Player player) {
        Inventory inventory = player.getInventory();
        ItemContainer hotbar = inventory.getHotbar();
        short activeSlot = (short) inventory.getActiveHotbarSlot();
        
        // Validate slot
        if (activeSlot < 0 || activeSlot >= hotbar.getCapacity()) {
            return false;
        }
        
        ItemStack item = hotbar.getItemStack(activeSlot);
        if (item == null || ItemStack.isEmpty(item)) {
            return false;
        }
        
        // Check if item is damaged (durability < max)
        if (item.getDurability() < item.getMaxDurability()) {
            ItemStack repairedItem = item.withDurability(item.getMaxDurability());
            hotbar.replaceItemStackInSlot(activeSlot, item, repairedItem);
            return true;
        }
        
        return false;
    }
    
    /**
     * Repair all items in the player's inventory.
     * @return count of items repaired
     */
    private int repairAllItems(Player player) {
        int count = 0;
        Inventory inventory = player.getInventory();
        
        // Repair armor
        count += repairContainer(inventory.getArmor());
        
        // Repair hotbar
        count += repairContainer(inventory.getHotbar());
        
        // Repair storage (main inventory)
        count += repairContainer(inventory.getStorage());
        
        // Repair utility slots
        count += repairContainer(inventory.getUtility());
        
        // Repair backpack if available
        try {
            ItemContainer backpack = inventory.getBackpack();
            if (backpack != null) {
                count += repairContainer(backpack);
            }
        } catch (Exception e) {
            // Backpack may not be available
        }
        
        return count;
    }
    
    /**
     * Repair all items in a container.
     * @return count of items repaired
     */
    private int repairContainer(ItemContainer container) {
        if (container == null) {
            return 0;
        }
        
        int count = 0;
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            ItemStack item = container.getItemStack(slot);
            if (item == null || ItemStack.isEmpty(item)) {
                continue;
            }
            
            // Check if item is damaged
            if (item.getDurability() < item.getMaxDurability()) {
                ItemStack repairedItem = item.withDurability(item.getMaxDurability());
                container.replaceItemStackInSlot(slot, item, repairedItem);
                count++;
            }
        }
        return count;
    }
}
