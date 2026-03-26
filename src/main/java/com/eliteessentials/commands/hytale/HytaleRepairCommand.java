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
import com.eliteessentials.util.PlayerSuggestionProvider;
import com.eliteessentials.util.WorldBlacklistUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.UUID;
import java.util.logging.Logger;
import javax.annotation.Nonnull;

/**
 * Command: /repair [all] [player]
 * Repairs the item in hand, or all items in inventory if "all" is specified.
 * Can target another player from console or with admin permission.
 * Admin command only.
 * 
 * Usage:
 *   /repair              - Repair item in hand (player only)
 *   /repair all          - Repair all items (player only)
 *   /repair <player>     - Repair target's held item (admin/console)
 *   /repair all <player> - Repair all of target's items (admin/console)
 *   /repair <player> all - Repair all of target's items (admin/console)
 * 
 * Permissions:
 * - eliteessentials.command.misc.repair - Use /repair (single item)
 * - eliteessentials.command.misc.repair.all - Use /repair all
 * - eliteessentials.command.misc.repair.bypass.cooldown - Skip cooldown
 * - eliteessentials.command.misc.repair.cooldown.<seconds> - Cooldown for single repair
 * - eliteessentials.command.misc.repair.all.cooldown.<seconds> - Cooldown for repair all
 * Cost bypass: eliteessentials.bypass.cost.repair / eliteessentials.bypass.cost.repair.all
 */
public class HytaleRepairCommand extends CommandBase {

    private static final Logger logger = Logger.getLogger("EliteEssentials");
    private static final String COMMAND_NAME = "repair";
    private static final String COMMAND_NAME_ALL = "repair.all";

    private final ConfigManager configManager;
    private final CooldownService cooldownService;

    public HytaleRepairCommand(ConfigManager configManager, CooldownService cooldownService) {
        super(COMMAND_NAME, "Repair the item in your hand");
        this.configManager = configManager;
        this.cooldownService = cooldownService;
        this.addAliases("fix");
        setAllowsExtraArguments(true);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void executeSync(@Nonnull CommandContext ctx) {
        PluginConfig config = configManager.getConfig();

        // Parse arguments: /repair [all] [player] or /repair [player] [all]
        String rawInput = ctx.getInputString();
        String[] parts = rawInput.trim().split("\\s+");

        // Determine sender type
        Object sender = ctx.sender();
        boolean isConsoleSender = !(sender instanceof PlayerRef) && !(sender instanceof Player);

        PlayerRef senderPlayerRef = null;
        if (sender instanceof PlayerRef) {
            senderPlayerRef = (PlayerRef) sender;
        } else if (sender instanceof Player player) {
            @SuppressWarnings("removal")
            UUID playerUuid = player.getUuid();
            senderPlayerRef = playerUuid != null ? Universe.get().getPlayer(playerUuid) : null;
        }

        // Parse "all" and target player name from arguments
        boolean repairAll = false;
        String targetPlayerName = null;

        for (int i = 1; i < parts.length; i++) {
            if (parts[i].equalsIgnoreCase("all")) {
                repairAll = true;
            } else {
                targetPlayerName = parts[i];
            }
        }

        boolean isTargetingOther = (targetPlayerName != null);

        // Console must specify a target player
        if (isConsoleSender && !isTargetingOther) {
            ctx.sendMessage(Message.raw("Console usage: /repair [all] <player>").color("#FF5555"));
            return;
        }

        // Permission check - admin command
        if (!isConsoleSender) {
            if (senderPlayerRef == null) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("noPermission"), "#FF5555"));
                return;
            }
            if (!CommandPermissionUtil.canExecuteAdmin(ctx, senderPlayerRef, Permissions.REPAIR, config.repair.enabled)) {
                return;
            }
            // If targeting another player, require admin permission
            if (isTargetingOther && !PermissionService.get().isAdmin(senderPlayerRef.getUuid())) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("noPermission"), "#FF5555"));
                return;
            }
        }

        // Resolve the target player
        PlayerRef targetPlayer;
        if (isTargetingOther) {
            targetPlayer = PlayerSuggestionProvider.findPlayer(targetPlayerName);
            if (targetPlayer == null) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("playerNotFound", "player", targetPlayerName), "#FF5555"));
                return;
            }
        } else {
            targetPlayer = senderPlayerRef;
            if (targetPlayer == null) {
                ctx.sendMessage(Message.raw("Could not determine player.").color("#FF5555"));
                return;
            }
        }

        // Find the target player's world for world-thread execution
        World targetWorld = findPlayerWorld(targetPlayer);
        if (targetWorld == null) {
            ctx.sendMessage(Message.raw("Could not determine player's world.").color("#FF5555"));
            return;
        }

        // World blacklist check (skip for console targeting others)
        if (!isConsoleSender && !isTargetingOther) {
            if (WorldBlacklistUtil.isWorldBlacklisted(targetWorld.getName(), config.repair.blacklistedWorlds)) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("commandBlacklistedWorld"), "#FF5555"));
                return;
            }
        }

        UUID targetId = targetPlayer.getUuid();
        final boolean finalRepairAll = repairAll;
        final boolean finalIsTargetingOther = isTargetingOther;
        final PlayerRef finalSenderRef = senderPlayerRef;

        // Execute on the target player's world thread for ECS safety
        targetWorld.execute(() -> {
            doRepair(ctx, targetPlayer, targetId, finalRepairAll, finalIsTargetingOther, finalSenderRef, config);
        });
    }

    /**
     * Perform the repair logic on the world thread.
     */
    private void doRepair(CommandContext ctx, PlayerRef targetPlayer, UUID targetId,
                          boolean repairAll, boolean isTargetingOther, PlayerRef senderRef,
                          PluginConfig config) {
        // Get fresh ref for the target player
        Ref<EntityStore> ref = targetPlayer.getReference();
        if (ref == null || !ref.isValid()) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("playerNotFound", "player", targetPlayer.getUsername()), "#FF5555"));
            return;
        }
        Store<EntityStore> store = ref.getStore();

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("repairNoItem"), "#FF5555"));
            return;
        }

        // For self-repair, apply cooldown and cost checks using the sender's UUID
        UUID costPlayerId = (senderRef != null && !isTargetingOther) ? senderRef.getUuid() : targetId;
        CostService costService = EliteEssentials.getInstance().getCostService();

        if (repairAll) {
            // Permission for repair all (skip for console)
            if (senderRef != null && !isTargetingOther) {
                if (!PermissionService.get().hasPermission(costPlayerId, Permissions.REPAIR_ALL)
                    && !PermissionService.get().isAdmin(costPlayerId)) {
                    ctx.sendMessage(MessageFormatter.formatWithFallback(
                        configManager.getMessage("repairNoPermissionAll"), "#FF5555"));
                    return;
                }
            }
            // Cost check (only for self-repair)
            if (!isTargetingOther && costService != null && senderRef != null) {
                if (!costService.checkCanAfford(ctx, senderRef, COMMAND_NAME_ALL, config.repair.costAll)) {
                    return;
                }
            }
            // Cooldown (only for self-repair)
            int effectiveCooldown = 0;
            if (!isTargetingOther) {
                int cooldownAll = config.repair.cooldownAllSeconds > 0 ? config.repair.cooldownAllSeconds : config.repair.cooldownSeconds;
                effectiveCooldown = PermissionService.get().getCommandCooldown(costPlayerId, COMMAND_NAME_ALL, cooldownAll);
                if (effectiveCooldown > 0) {
                    int cooldownRemaining = cooldownService.getCooldownRemaining(COMMAND_NAME_ALL, costPlayerId);
                    if (cooldownRemaining > 0) {
                        ctx.sendMessage(MessageFormatter.formatWithFallback(
                            configManager.getMessage("onCooldown", "seconds", String.valueOf(cooldownRemaining)), "#FF5555"));
                        return;
                    }
                }
            }

            int repairedCount = repairAllItems(store, ref);
            if (repairedCount > 0) {
                if (!isTargetingOther && senderRef != null) {
                    CommandPermissionUtil.chargeCost(ctx, senderRef, COMMAND_NAME_ALL, config.repair.costAll);
                    if (effectiveCooldown > 0) {
                        cooldownService.setCooldown(COMMAND_NAME_ALL, costPlayerId, effectiveCooldown);
                    }
                }
                if (isTargetingOther) {
                    ctx.sendMessage(MessageFormatter.formatWithFallback(
                        configManager.getMessage("repairAllSuccess", "count", String.valueOf(repairedCount))
                            + " &7(for " + targetPlayer.getUsername() + ")", "#55FF55"));
                    targetPlayer.sendMessage(MessageFormatter.formatWithFallback(
                        configManager.getMessage("repairAllSuccess", "count", String.valueOf(repairedCount)), "#55FF55"));
                } else {
                    ctx.sendMessage(MessageFormatter.formatWithFallback(
                        configManager.getMessage("repairAllSuccess", "count", String.valueOf(repairedCount)), "#55FF55"));
                }
            } else {
                ctx.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("repairNothingToRepair"), "#FF5555"));
            }
        } else {
            // Single item repair
            // Cost check (only for self-repair)
            if (!isTargetingOther && costService != null && senderRef != null) {
                if (!costService.checkCanAfford(ctx, senderRef, COMMAND_NAME, config.repair.cost)) {
                    return;
                }
            }
            // Cooldown (only for self-repair)
            int effectiveCooldown = 0;
            if (!isTargetingOther) {
                effectiveCooldown = PermissionService.get().getCommandCooldown(costPlayerId, COMMAND_NAME, config.repair.cooldownSeconds);
                if (effectiveCooldown > 0) {
                    int cooldownRemaining = cooldownService.getCooldownRemaining(COMMAND_NAME, costPlayerId);
                    if (cooldownRemaining > 0) {
                        ctx.sendMessage(MessageFormatter.formatWithFallback(
                            configManager.getMessage("onCooldown", "seconds", String.valueOf(cooldownRemaining)), "#FF5555"));
                        return;
                    }
                }
            }

            boolean repaired = repairItemInHand(store, ref);
            if (repaired) {
                if (!isTargetingOther && senderRef != null) {
                    CommandPermissionUtil.chargeCost(ctx, senderRef, COMMAND_NAME, config.repair.cost);
                    if (effectiveCooldown > 0) {
                        cooldownService.setCooldown(COMMAND_NAME, costPlayerId, effectiveCooldown);
                    }
                }
                if (isTargetingOther) {
                    ctx.sendMessage(MessageFormatter.formatWithFallback(
                        configManager.getMessage("repairSuccess") + " &7(for " + targetPlayer.getUsername() + ")", "#55FF55"));
                    targetPlayer.sendMessage(MessageFormatter.formatWithFallback(
                        configManager.getMessage("repairSuccess"), "#55FF55"));
                } else {
                    ctx.sendMessage(MessageFormatter.formatWithFallback(
                        configManager.getMessage("repairSuccess"), "#55FF55"));
                }
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
    private boolean repairItemInHand(Store<EntityStore> store, Ref<EntityStore> ref) {
        InventoryComponent.Hotbar hotbarComp = store.getComponent(ref, InventoryComponent.Hotbar.getComponentType());
        if (hotbarComp == null) return false;
        
        ItemContainer hotbar = hotbarComp.getInventory();
        short activeSlot = (short) hotbarComp.getActiveSlot();
        
        if (activeSlot < 0 || activeSlot >= hotbar.getCapacity()) {
            return false;
        }
        
        ItemStack item = hotbar.getItemStack(activeSlot);
        if (item == null || ItemStack.isEmpty(item)) {
            return false;
        }
        
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
    private int repairAllItems(Store<EntityStore> store, Ref<EntityStore> ref) {
        int count = 0;
        
        count += repairComponent(store, ref, InventoryComponent.Armor.getComponentType());
        count += repairComponent(store, ref, InventoryComponent.Hotbar.getComponentType());
        count += repairComponent(store, ref, InventoryComponent.Storage.getComponentType());
        count += repairComponent(store, ref, InventoryComponent.Utility.getComponentType());
        
        try {
            count += repairComponent(store, ref, InventoryComponent.Backpack.getComponentType());
        } catch (Exception e) {
            // Backpack may not be available
        }
        
        return count;
    }

    /**
     * Repair all items in an inventory component.
     */
    private <T extends InventoryComponent> int repairComponent(
            Store<EntityStore> store, Ref<EntityStore> ref,
            com.hypixel.hytale.component.ComponentType<EntityStore, T> type) {
        T component = store.getComponent(ref, type);
        if (component == null) return 0;
        return repairContainer(component.getInventory());
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
            
            if (item.getDurability() < item.getMaxDurability()) {
                ItemStack repairedItem = item.withDurability(item.getMaxDurability());
                container.replaceItemStackInSlot(slot, item, repairedItem);
                count++;
            }
        }
        return count;
    }

    /**
     * Find which world a player is currently in.
     */
    private World findPlayerWorld(PlayerRef player) {
        Universe universe = Universe.get();
        if (universe == null) return null;
        
        for (var entry : universe.getWorlds().entrySet()) {
            if (entry.getValue().getPlayerRefs().contains(player)) {
                return entry.getValue();
            }
        }
        return null;
    }
}
