package com.eliteessentials.commands.hytale;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.config.PluginConfig;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.permissions.PermissionService;
import com.eliteessentials.services.CooldownService;
import com.eliteessentials.util.CommandPermissionUtil;
import com.eliteessentials.util.MessageFormatter;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.UUID;
import java.util.logging.Logger;

import javax.annotation.Nonnull;

/**
 * /clearinv - Clear all items from player's inventory.
 * 
 * Usage: /clearinv
 * Aliases: /clearinventory, /ci
 * 
 * Permissions:
 * - eliteessentials.command.misc.clearinv - Use /clearinv command (Admin only)
 * - eliteessentials.command.misc.clearinv.bypass.cooldown - Skip cooldown
 * - eliteessentials.command.misc.clearinv.cooldown.<seconds> - Set specific cooldown
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
    private static final String COMMAND_NAME = "clearinv";
    
    private final ConfigManager configManager;
    private final CooldownService cooldownService;
    
    public HytaleClearInvCommand(ConfigManager configManager, CooldownService cooldownService) {
        super(COMMAND_NAME, "Clear all items from your inventory");
        this.configManager = configManager;
        this.cooldownService = cooldownService;
        
        // Add aliases
        addAliases("clearinventory", "ci");
    }
    
    @Override
    protected boolean canGeneratePermission() {
        return false;
    }
    
    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store, 
                          @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        UUID playerId = playerRef.getUuid();
        PluginConfig.ClearInvConfig clearInvConfig = configManager.getConfig().clearInv;
        
        // Permission check - admin only
        if (!CommandPermissionUtil.canExecuteAdmin(ctx, playerRef, Permissions.CLEARINV, clearInvConfig.enabled)) {
            return;
        }
        
        // Get effective cooldown from permissions
        int effectiveCooldown = PermissionService.get().getCommandCooldown(playerId, COMMAND_NAME, clearInvConfig.cooldownSeconds);
        
        // Check cooldown if player has one
        if (effectiveCooldown > 0) {
            int cooldownRemaining = cooldownService.getCooldownRemaining(COMMAND_NAME, playerId);
            if (cooldownRemaining > 0) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("onCooldown", "seconds", String.valueOf(cooldownRemaining)), "#FF5555"));
                return;
            }
        }
        
        // Get player component
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("clearInvFailed"), "#FF5555"));
            return;
        }
        
        // Get inventory
        Inventory inventory = player.getInventory();
        if (inventory == null) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("clearInvFailed"), "#FF5555"));
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
        ctx.sendMessage(MessageFormatter.formatWithFallback(message, "#55FF55"));
        
        // Set cooldown after successful clear
        if (effectiveCooldown > 0) {
            cooldownService.setCooldown(COMMAND_NAME, playerId, effectiveCooldown);
        }
        
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
