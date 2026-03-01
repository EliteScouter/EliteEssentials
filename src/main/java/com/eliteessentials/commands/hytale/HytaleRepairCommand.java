package com.eliteessentials.commands.hytale;

import com.eliteessentials.EliteEssentials;
import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.config.PluginConfig;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.permissions.PermissionService;
import com.eliteessentials.services.CooldownService;
import com.eliteessentials.services.CostService;
import com.eliteessentials.util.CommandPermissionUtil;
import com.eliteessentials.util.MessageFormatter;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
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
 * 
 * Permissions:
 * - eliteessentials.command.misc.repair - Use /repair (single item)
 * - eliteessentials.command.misc.repair.all - Use /repair all
 * - eliteessentials.command.misc.repair.bypass.cooldown - Skip cooldown
 * - eliteessentials.command.misc.repair.cooldown.<seconds> - Cooldown for single repair
 * - eliteessentials.command.misc.repair.all.cooldown.<seconds> - Cooldown for repair all
 * Cost bypass: eliteessentials.bypass.cost.repair / eliteessentials.bypass.cost.repair.all
 */
public class HytaleRepairCommand extends AbstractPlayerCommand {

    private static final String COMMAND_NAME = "repair";
    private static final String COMMAND_NAME_ALL = "repair.all";

    private final ConfigManager configManager;
    private final CooldownService cooldownService;

    public HytaleRepairCommand(ConfigManager configManager, CooldownService cooldownService) {
        super(COMMAND_NAME, "Repair the item in your hand");
        this.configManager = configManager;
        this.cooldownService = cooldownService;
        this.addAliases("fix");
        // Allow extra arguments so we can parse "all" manually
        setAllowsExtraArguments(true);
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

        // Get player component (needed for repair)
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("repairNoItem"), "#FF5555"));
            return;
        }

        String rawInput = ctx.getInputString();
        String[] parts = rawInput.trim().split("\\s+");
        boolean repairAll = parts.length >= 2 && parts[1].equalsIgnoreCase("all");

        CostService costService = EliteEssentials.getInstance().getCostService();

        if (repairAll) {
            // Permission for repair all
            if (!PermissionService.get().hasPermission(playerId, Permissions.REPAIR_ALL)
                && !PermissionService.get().isAdmin(playerId)) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("repairNoPermissionAll"), "#FF5555"));
                return;
            }
            // Cost check
            if (costService != null && !costService.checkCanAfford(ctx, playerRef, COMMAND_NAME_ALL, config.repair.costAll)) {
                return;
            }
            int cooldownAll = config.repair.cooldownAllSeconds > 0 ? config.repair.cooldownAllSeconds : config.repair.cooldownSeconds;
            int effectiveCooldown = PermissionService.get().getCommandCooldown(playerId, COMMAND_NAME_ALL, cooldownAll);
            if (effectiveCooldown > 0) {
                int cooldownRemaining = cooldownService.getCooldownRemaining(COMMAND_NAME_ALL, playerId);
                if (cooldownRemaining > 0) {
                    ctx.sendMessage(MessageFormatter.formatWithFallback(
                        configManager.getMessage("onCooldown", "seconds", String.valueOf(cooldownRemaining)), "#FF5555"));
                    return;
                }
            }

            int repairedCount = repairAllItems(player);
            if (repairedCount > 0) {
                CommandPermissionUtil.chargeCost(ctx, playerRef, COMMAND_NAME_ALL, config.repair.costAll);
                if (effectiveCooldown > 0) {
                    cooldownService.setCooldown(COMMAND_NAME_ALL, playerId, effectiveCooldown);
                }
                ctx.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("repairAllSuccess", "count", String.valueOf(repairedCount)), "#55FF55"));
            } else {
                ctx.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("repairNothingToRepair"), "#FF5555"));
            }
        } else {
            // Single repair: cost check
            if (costService != null && !costService.checkCanAfford(ctx, playerRef, COMMAND_NAME, config.repair.cost)) {
                return;
            }
            int effectiveCooldown = PermissionService.get().getCommandCooldown(playerId, COMMAND_NAME, config.repair.cooldownSeconds);
            if (effectiveCooldown > 0) {
                int cooldownRemaining = cooldownService.getCooldownRemaining(COMMAND_NAME, playerId);
                if (cooldownRemaining > 0) {
                    ctx.sendMessage(MessageFormatter.formatWithFallback(
                        configManager.getMessage("onCooldown", "seconds", String.valueOf(cooldownRemaining)), "#FF5555"));
                    return;
                }
            }

            boolean repaired = repairItemInHand(player);
            if (repaired) {
                CommandPermissionUtil.chargeCost(ctx, playerRef, COMMAND_NAME, config.repair.cost);
                if (effectiveCooldown > 0) {
                    cooldownService.setCooldown(COMMAND_NAME, playerId, effectiveCooldown);
                }
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
