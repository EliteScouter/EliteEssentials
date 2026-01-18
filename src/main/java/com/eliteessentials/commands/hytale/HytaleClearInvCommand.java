package com.eliteessentials.commands.hytale;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.util.CommandPermissionUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.logging.Logger;

/**
 * /clearinv - Clear all items from player's inventory.
 * 
 * Usage: /clearinv
 * Aliases: /clearinventory, /ci
 * Permission: eliteessentials.command.misc.clearinv (Admin only)
 * 
 * Clears all items from:
 * - Hotbar
 * - Storage (main inventory)
 * - Armor slots
 * - Utility slots
 * - Tool slots
 */
public class HytaleClearInvCommand extends AbstractPlayerCommand {
    
    private static final Logger logger = Logger.getLogger("EliteEssentials");
    private final ConfigManager configManager;
    
    public HytaleClearInvCommand(ConfigManager configManager) {
        super("clearinv", "Clear all items from your inventory");
        this.configManager = configManager;
        
        // Add aliases
        addAliases("clearinventory", "ci");
    }
    
    @Override
    protected boolean canGeneratePermission() {
        return false;
    }
    
    @Override
    protected void execute(CommandContext ctx, Store<EntityStore> store, 
                          Ref<EntityStore> ref, PlayerRef playerRef, World world) {
        // Permission check - admin only
        if (!CommandPermissionUtil.canExecuteAdmin(ctx, playerRef, Permissions.CLEARINV, 
                configManager.getConfig().clearInv.enabled)) {
            return;
        }
        
        // Get player component
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            ctx.sendMessage(Message.raw(configManager.getMessage("clearInvFailed")).color("#FF5555"));
            return;
        }
        
        // Get inventory
        Inventory inventory = player.getInventory();
        if (inventory == null) {
            ctx.sendMessage(Message.raw(configManager.getMessage("clearInvFailed")).color("#FF5555"));
            return;
        }
        
        // Clear all inventory sections
        int totalCleared = 0;
        
        // Clear hotbar
        ItemContainer hotbar = inventory.getHotbar();
        if (hotbar != null) {
            totalCleared += clearContainer(hotbar);
        }
        
        // Clear storage (main inventory)
        ItemContainer storage = inventory.getStorage();
        if (storage != null) {
            totalCleared += clearContainer(storage);
        }
        
        // Clear armor
        ItemContainer armor = inventory.getArmor();
        if (armor != null) {
            totalCleared += clearContainer(armor);
        }
        
        // Clear utility
        ItemContainer utility = inventory.getUtility();
        if (utility != null) {
            totalCleared += clearContainer(utility);
        }
        
        // Clear tools
        ItemContainer tools = inventory.getTools();
        if (tools != null) {
            totalCleared += clearContainer(tools);
        }
        
        // Sync inventory to client
        player.sendInventory();
        
        // Send success message
        String message = configManager.getMessage("clearInvSuccess", "count", String.valueOf(totalCleared));
        ctx.sendMessage(Message.raw(message).color("#55FF55"));
        
        if (configManager.isDebugEnabled()) {
            logger.info("Cleared " + totalCleared + " items from " + 
                playerRef.getUsername() + "'s inventory");
        }
    }
    
    /**
     * Clear all items from a container.
     * @param container The container to clear
     * @return Number of items cleared
     */
    private int clearContainer(ItemContainer container) {
        int cleared = 0;
        int capacity = container.getCapacity();
        
        for (short slot = 0; slot < capacity; slot++) {
            var itemStack = container.getItemStack(slot);
            if (itemStack != null && !itemStack.isEmpty()) {
                container.setItemStackForSlot(slot, null);
                cleared++;
            }
        }
        
        return cleared;
    }
}
