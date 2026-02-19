package com.eliteessentials.commands.hytale;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.util.CommandPermissionUtil;
import com.eliteessentials.util.MessageFormatter;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.List;
import java.util.logging.Logger;
import javax.annotation.Nonnull;

/**
 * /invsee PLAYER - View another player's inventory.
 * 
 * Usage: /invsee <player>
 * 
 * Permissions:
 * - eliteessentials.command.misc.invsee - Use /invsee command (Admin only)
 * 
 * Displays inventory contents from:
 * - Hotbar
 * - Storage (main inventory)
 * - Armor slots
 * - Utility slots
 * - Tool slots
 */
public class HytaleInvseeCommand extends AbstractPlayerCommand {
    
    private static final Logger logger = Logger.getLogger("EliteEssentials");
    private static final String COMMAND_NAME = "invsee";
    
    private final ConfigManager configManager;
    private final RequiredArg<String> targetArg;

    public HytaleInvseeCommand(ConfigManager configManager) {
        super(COMMAND_NAME, "View another player's inventory");
        this.configManager = configManager;
        this.targetArg = withRequiredArg("player", "Target player", ArgTypes.STRING);
    }
    
    @Override
    protected boolean canGeneratePermission() {
        return false;
    }
    
    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store, 
                          @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        // Permission check - admin only
        if (!CommandPermissionUtil.canExecuteAdmin(ctx, playerRef, Permissions.INVSEE, true)) {
            return;
        }
        
        // Get target player name from arguments
        String targetPlayerName = ctx.get(targetArg);
        
        if (targetPlayerName == null || targetPlayerName.isEmpty()) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("invseeUsage"), "#FF5555"));
            return;
        }
        
        // Look up target player by name
        PlayerRef targetPlayerRef = findPlayerByName(targetPlayerName);
        if (targetPlayerRef == null) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("invseePlayerNotFound", "player", targetPlayerName), "#FF5555"));
            return;
        }
        
        // Get target player's entity ref
        Ref<EntityStore> targetRef = targetPlayerRef.getReference();
        if (targetRef == null || !targetRef.isValid()) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("invseePlayerNotFound", "player", targetPlayerName), "#FF5555"));
            return;
        }
        
        Store<EntityStore> targetStore = targetRef.getStore();
        if (targetStore == null) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("invseePlayerNotFound", "player", targetPlayerName), "#FF5555"));
            return;
        }
        
        // Get target player component
        Player targetPlayer = targetStore.getComponent(targetRef, Player.getComponentType());
        if (targetPlayer == null) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("invseePlayerNotFound", "player", targetPlayerName), "#FF5555"));
            return;
        }
        
        // Get target inventory
        Inventory targetInventory = targetPlayer.getInventory();
        if (targetInventory == null) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("invseeError"), "#FF5555"));
            return;
        }
        
        // Display inventory header
        ctx.sendMessage(MessageFormatter.formatWithFallback(
            configManager.getMessage("invseeHeader", "player", targetPlayerName), "#55FF55"));
        
        // Display hotbar
        ItemContainer hotbar = targetInventory.getHotbar();
        if (hotbar != null) {
            displayContainer(ctx, configManager, "Hotbar", hotbar);
        }
        
        // Display storage
        ItemContainer storage = targetInventory.getStorage();
        if (storage != null) {
            displayContainer(ctx, configManager, "Storage", storage);
        }
        
        // Display armor
        ItemContainer armor = targetInventory.getArmor();
        if (armor != null) {
            displayContainer(ctx, configManager, "Armor", armor);
        }
        
        // Display utility
        ItemContainer utility = targetInventory.getUtility();
        if (utility != null) {
            displayContainer(ctx, configManager, "Utility", utility);
        }
        
        // Display tools
        ItemContainer tools = targetInventory.getTools();
        if (tools != null) {
            displayContainer(ctx, configManager, "Tools", tools);
        }
        
        // Display footer
        ctx.sendMessage(MessageFormatter.formatWithFallback(
            configManager.getMessage("invseeFooter"), "#AAAAAA"));
        
        if (configManager.isDebugEnabled()) {
            logger.info(playerRef.getUsername() + " viewed " + targetPlayerName + "'s inventory");
        }
    }
    
    /**
     * Find a player by name (case-insensitive).
     */
    private PlayerRef findPlayerByName(String playerName) {
        List<PlayerRef> players = Universe.get().getPlayers();
        
        for (PlayerRef playerRef : players) {
            if (playerRef == null) continue;
            
            String username = playerRef.getUsername();
            if (username != null && username.equalsIgnoreCase(playerName)) {
                return playerRef;
            }
        }
        
        return null;
    }
    
    /**
     * Display contents of a container.
     */
    private void displayContainer(CommandContext ctx, ConfigManager configManager, String containerName, ItemContainer container) {
        StringBuilder sb = new StringBuilder();
        sb.append("&7").append(containerName).append(": ");
        
        boolean hasItems = false;
        int capacity = container.getCapacity();
        
        for (short slot = 0; slot < capacity; slot++) {
            var itemStack = container.getItemStack(slot);
            if (itemStack != null && !itemStack.isEmpty()) {
                hasItems = true;
                
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                
                String itemId = itemStack.getItemId();
                int quantity = itemStack.getQuantity();
                
                if (quantity > 1) {
                    sb.append("&f").append(quantity).append("x &r").append(itemId);
                } else {
                    sb.append("&f").append(itemId);
                }
            }
        }
        
        if (!hasItems) {
            sb.append("&7Empty");
        }
        
        ctx.sendMessage(MessageFormatter.formatWithFallback(
            configManager.getMessage("invseeContainer", "content", sb.toString()), "#AAAAAA"));
    }
}