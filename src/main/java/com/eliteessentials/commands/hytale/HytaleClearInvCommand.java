package com.eliteessentials.commands.hytale;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.config.PluginConfig;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.permissions.PermissionService;
import com.eliteessentials.services.CooldownService;
import com.eliteessentials.util.CommandPermissionUtil;
import com.eliteessentials.util.MessageFormatter;
import com.eliteessentials.util.PlayerSuggestionProvider;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.UUID;
import java.util.logging.Logger;
import javax.annotation.Nonnull;

/**
 * /clearinv [player] - Clear all items from player's inventory.
 * Can be executed from console to clear a specific player's inventory.
 * 
 * Usage:
 *   /clearinv           - Clear own inventory (player only)
 *   /clearinv <player>  - Clear target's inventory (admin/console)
 * 
 * Aliases: /clearinventory, /ci
 * 
 * Permissions:
 * - eliteessentials.command.misc.clearinv - Use /clearinv command (Admin only)
 * - eliteessentials.command.misc.clearinv.bypass.cooldown - Skip cooldown
 * - eliteessentials.command.misc.clearinv.cooldown.<seconds> - Set specific cooldown
 */
public class HytaleClearInvCommand extends CommandBase {
    
    private static final Logger logger = Logger.getLogger("EliteEssentials");
    private static final String COMMAND_NAME = "clearinv";

    private final ConfigManager configManager;
    private final CooldownService cooldownService;
    
    public HytaleClearInvCommand(ConfigManager configManager, CooldownService cooldownService) {
        super(COMMAND_NAME, "Clear all items from your inventory");
        this.configManager = configManager;
        this.cooldownService = cooldownService;
        addAliases("clearinventory", "ci");
        setAllowsExtraArguments(true);
    }
    
    @Override
    protected boolean canGeneratePermission() {
        return false;
    }
    
    @Override
    protected void executeSync(@Nonnull CommandContext ctx) {
        PluginConfig.ClearInvConfig clearInvConfig = configManager.getConfig().clearInv;

        String rawInput = ctx.getInputString().trim();
        String[] parts = rawInput.split("\\s+", 3);

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

        if (isConsoleSender && parts.length < 2) {
            ctx.sendMessage(Message.raw("Console usage: /clearinv <player>").color("#FF5555"));
            return;
        }

        boolean isTargetingOther = (parts.length >= 2 && !parts[1].isEmpty());
        PlayerRef targetPlayer;

        if (isTargetingOther) {
            if (!isConsoleSender) {
                if (senderPlayerRef == null) {
                    ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("noPermission"), "#FF5555"));
                    return;
                }
                if (!CommandPermissionUtil.canExecuteAdmin(ctx, senderPlayerRef, Permissions.CLEARINV, clearInvConfig.enabled)) {
                    return;
                }
            }
            String targetName = parts[1];
            targetPlayer = PlayerSuggestionProvider.findPlayer(targetName);
            if (targetPlayer == null) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("playerNotFound", "player", targetName), "#FF5555"));
                return;
            }
        } else {
            if (senderPlayerRef == null) {
                ctx.sendMessage(Message.raw("Could not determine player.").color("#FF5555"));
                return;
            }
            if (!CommandPermissionUtil.canExecuteAdmin(ctx, senderPlayerRef, Permissions.CLEARINV, clearInvConfig.enabled)) {
                return;
            }
            targetPlayer = senderPlayerRef;
        }

        UUID targetId = targetPlayer.getUuid();

        // Cooldown (only for self-use)
        int effectiveCooldown = 0;
        if (!isTargetingOther) {
            effectiveCooldown = PermissionService.get().getCommandCooldown(targetId, COMMAND_NAME, clearInvConfig.cooldownSeconds);
            if (effectiveCooldown > 0) {
                int cooldownRemaining = cooldownService.getCooldownRemaining(COMMAND_NAME, targetId);
                if (cooldownRemaining > 0) {
                    ctx.sendMessage(MessageFormatter.formatWithFallback(
                        configManager.getMessage("onCooldown", "seconds", String.valueOf(cooldownRemaining)), "#FF5555"));
                    return;
                }
            }
        }

        World targetWorld = findPlayerWorld(targetPlayer);
        if (targetWorld == null) {
            ctx.sendMessage(Message.raw("Could not determine player's world.").color("#FF5555"));
            return;
        }

        final int finalEffectiveCooldown = effectiveCooldown;
        final boolean finalIsTargetingOther = isTargetingOther;
        final PlayerRef finalTarget = targetPlayer;

        targetWorld.execute(() -> {
            Ref<EntityStore> ref = finalTarget.getReference();
            if (ref == null || !ref.isValid()) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("playerNotFound", "player", finalTarget.getUsername()), "#FF5555"));
                return;
            }
            Store<EntityStore> store = ref.getStore();

            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("clearInvFailed"), "#FF5555"));
                return;
            }

            int totalCleared = 0;
            ItemContainer hotbar = getContainer(store, ref, InventoryComponent.Hotbar.getComponentType());
            if (hotbar != null) totalCleared += clearContainer(hotbar);
            ItemContainer storage = getContainer(store, ref, InventoryComponent.Storage.getComponentType());
            if (storage != null) totalCleared += clearContainer(storage);
            ItemContainer armor = getContainer(store, ref, InventoryComponent.Armor.getComponentType());
            if (armor != null) totalCleared += clearContainer(armor);
            ItemContainer utility = getContainer(store, ref, InventoryComponent.Utility.getComponentType());
            if (utility != null) totalCleared += clearContainer(utility);
            ItemContainer tools = getContainer(store, ref, InventoryComponent.Tool.getComponentType());
            if (tools != null) totalCleared += clearContainer(tools);

            String message = configManager.getMessage("clearInvSuccess", "count", String.valueOf(totalCleared));
            if (finalIsTargetingOther) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(message + " &7(for " + finalTarget.getUsername() + ")", "#55FF55"));
                finalTarget.sendMessage(MessageFormatter.formatWithFallback(message, "#55FF55"));
            } else {
                ctx.sendMessage(MessageFormatter.formatWithFallback(message, "#55FF55"));
            }

            if (finalEffectiveCooldown > 0) {
                cooldownService.setCooldown(COMMAND_NAME, targetId, finalEffectiveCooldown);
            }

            if (configManager.isDebugEnabled()) {
                logger.info("Cleared " + totalCleared + " items from " + finalTarget.getUsername() + "'s inventory");
            }
        });
    }
    
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

    /**
     * Get an ItemContainer from an InventoryComponent on the entity.
     */
    private <T extends InventoryComponent> ItemContainer getContainer(
            Store<EntityStore> store, Ref<EntityStore> ref, ComponentType<EntityStore, T> type) {
        T component = store.getComponent(ref, type);
        return component != null ? component.getInventory() : null;
    }

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
