package com.eliteessentials.commands.hytale;

import com.eliteessentials.commands.args.SimpleBoolArg;
import com.eliteessentials.commands.args.SimpleIntArg;
import com.eliteessentials.commands.args.SimpleStringArg;
import com.eliteessentials.model.Kit;
import com.eliteessentials.model.KitItem;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.services.KitService;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * Subcommand to create a kit from the player's current inventory.
 * Usage: /kit create <name> [cooldown] [onetime]
 * Example: /kit create Starter 0 yes
 * Note: A kit named "Starter" will automatically be given to new players on join.
 */
public class HytaleKitCreateCommand extends AbstractPlayerCommand {
    private final KitService kitService;
    private final RequiredArg<String> nameArg;
    private final RequiredArg<Integer> cooldownArg;
    private final RequiredArg<Boolean> onetimeArg;

    public HytaleKitCreateCommand(@Nonnull KitService kitService) {
        super("create", "Create a kit from your current inventory");
        this.kitService = kitService;

        requirePermission(Permissions.KIT_CREATE);
        this.nameArg = withRequiredArg("name", "Kit name", SimpleStringArg.KIT_NAME);
        this.cooldownArg = withRequiredArg("cooldown", "Cooldown in seconds (0 = none)", SimpleIntArg.COOLDOWN);
        this.onetimeArg = withRequiredArg("onetime", "One-time use only", SimpleBoolArg.YES_NO);
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        String kitName = context.get(nameArg);

        // Validate kit name
        if (!kitName.matches("^[a-zA-Z0-9_-]+$")) {
            context.sendMessage(Message.raw("Kit name can only contain letters, numbers, underscores, and hyphens.").color("#FF5555"));
            return;
        }

        // Prevent reserved names
        if (kitName.equalsIgnoreCase("create") || kitName.equalsIgnoreCase("delete")) {
            context.sendMessage(Message.raw("Cannot create a kit named '" + kitName + "'. This is a reserved command.").color("#FF5555"));
            return;
        }

        // Check if kit already exists
        if (kitService.getKit(kitName) != null) {
            context.sendMessage(Message.raw("A kit named '" + kitName + "' already exists.").color("#FF5555"));
            return;
        }

        // Get player's inventory
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            context.sendMessage(Message.raw("Could not access your inventory.").color("#FF5555"));
            return;
        }

        Inventory inventory = player.getInventory();
        if (inventory == null) {
            context.sendMessage(Message.raw("Could not access your inventory.").color("#FF5555"));
            return;
        }

        List<KitItem> items = new ArrayList<>();

        // Collect items from all inventory sections
        collectItems(inventory.getHotbar(), "hotbar", items);
        collectItems(inventory.getStorage(), "storage", items);
        collectItems(inventory.getArmor(), "armor", items);
        collectItems(inventory.getUtility(), "utility", items);
        collectItems(inventory.getTools(), "tools", items);

        if (items.isEmpty()) {
            context.sendMessage(Message.raw("Your inventory is empty. Add some items before creating a kit.").color("#FF5555"));
            return;
        }

        // Get parameters - all positional now
        int cooldown = context.get(cooldownArg);
        boolean onetime = context.get(onetimeArg);
        
        // Auto-detect starter kit based on name "Starter" (case-insensitive)
        boolean isStarter = kitName.equalsIgnoreCase("starter");

        // Use kit name as display name (capitalize first letter only)
        String displayName = kitName.substring(0, 1).toUpperCase() + kitName.substring(1);
        String description = isStarter ? "Starter kit for new players" : "Custom kit";
        String icon = items.get(0).itemId();

        // Create and save the kit
        Kit newKit = new Kit(kitName, displayName, description, icon, cooldown, false, onetime, isStarter, items);
        kitService.saveKit(newKit);

        // Build confirmation message
        StringBuilder msg = new StringBuilder();
        msg.append("Kit '").append(displayName).append("' created with ").append(items.size()).append(" items");
        if (cooldown > 0) {
            msg.append(", cooldown: ").append(formatCooldown(cooldown));
        }
        if (onetime) {
            msg.append(", one-time use");
        }
        if (isStarter) {
            msg.append(" (auto-given to new players)");
        }
        
        context.sendMessage(Message.raw(msg.toString()).color("#55FF55"));
    }

    private String formatCooldown(int seconds) {
        if (seconds < 60) return seconds + "s";
        if (seconds < 3600) return (seconds / 60) + "m";
        return (seconds / 3600) + "h";
    }

    private void collectItems(@Nonnull ItemContainer container, @Nonnull String section, @Nonnull List<KitItem> items) {
        if (container == null) return;
        
        short capacity = container.getCapacity();
        for (short slot = 0; slot < capacity; slot++) {
            ItemStack itemStack = container.getItemStack(slot);
            if (itemStack != null && !itemStack.isEmpty()) {
                String itemId = itemStack.getItemId();
                
                // Skip editor tools
                if (isEditorItem(itemId)) {
                    continue;
                }
                
                items.add(new KitItem(
                    itemId,
                    itemStack.getQuantity(),
                    section,
                    slot
                ));
            }
        }
    }

    private boolean isEditorItem(@Nonnull String itemId) {
        return itemId.startsWith("EditorTool_") ||
               itemId.startsWith("Editor_") ||
               itemId.startsWith("Debug_") ||
               itemId.startsWith("Admin_");
    }
}
