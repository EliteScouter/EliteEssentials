package com.eliteessentials.commands.hytale;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.gui.KitSelectionPage;
import com.eliteessentials.model.Kit;
import com.eliteessentials.model.KitItem;
import com.eliteessentials.permissions.PermissionService;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.services.KitService;
import com.eliteessentials.util.CommandPermissionUtil;
import com.eliteessentials.util.MessageFormatter;
import com.eliteessentials.util.PlayerSuggestionProvider;
import com.eliteessentials.util.WorldBlacklistUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.entity.ItemUtils;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.eliteessentials.util.CommandExecutor;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.logging.Logger;
import com.eliteessentials.util.CommandSpyUtil;

/**
 * Command: /kit [name] | /kit &lt;player&gt; &lt;kit&gt;
 * - /kit                 - Opens the kit selection GUI (player; requires eliteessentials.command.kit.gui)
 * - /kit &lt;name&gt;          - Claims a specific kit directly (player; requires eliteessentials.command.kit.&lt;name&gt;)
 * - /kit &lt;player&gt; &lt;kit&gt;  - Gives a kit to another player (admin / console; bypasses kit permission,
 *                          cooldown, and one-time restrictions)
 *
 * Extends {@link CommandBase} (not AbstractPlayerCommand) so the server console can apply kits to
 * players for automation, matching the /rtp, /spawn, /warp, /heal, /god, /fly, /clearinv pattern.
 *
 * Permissions:
 * - eliteessentials.command.kit.use - Base permission for /kit command
 * - eliteessentials.command.kit.gui - Permission to open the kit GUI
 * - eliteessentials.command.kit.&lt;kitname&gt; - Access specific kit
 * - eliteessentials.command.kit.give - Give a kit to another player (admin)
 * - eliteessentials.command.kit.bypass.cooldown - Bypass kit cooldowns
 * - eliteessentials.command.kit.bypass.onetime - Bypass one-time kit restrictions
 */
public class HytaleKitCommand extends CommandBase {

    private static final Logger logger = Logger.getLogger("EliteEssentials");
    private static final String COMMAND_NAME = "kit";
    
    private final KitService kitService;
    private final ConfigManager configManager;

    public HytaleKitCommand(KitService kitService, ConfigManager configManager) {
        super(COMMAND_NAME, "Open the kit selection menu or claim a specific kit");
        this.kitService = kitService;
        this.configManager = configManager;
        
        addAliases("kits");
        // Trailing arguments: kit name (self) or "<player> <kit>" (admin/console give)
        setAllowsExtraArguments(true);
        
        // Add subcommands for admin operations
        addSubCommand(new HytaleKitCreateCommand(kitService));
        addSubCommand(new HytaleKitDeleteCommand(kitService));
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void executeSync(@Nonnull CommandContext ctx) {
        CommandSpyUtil.notify(ctx);

        // Parse: /kit [name] | /kit <player> <kit>
        String[] parts = ctx.getInputString().trim().split("\\s+");

        Object sender = ctx.sender();
        boolean isConsole = !(sender instanceof PlayerRef) && !(sender instanceof Player);

        // /kit <player> <kit> - admin/console give (two trailing arguments)
        if (parts.length >= 3) {
            handleGive(ctx, sender, isConsole, parts[1], parts[2]);
            return;
        }

        // Anything below requires a player sender
        if (isConsole) {
            ctx.sendMessage(Message.raw("Console usage: /kit <player> <kit>").color("#FF5555"));
            return;
        }

        PlayerRef senderRef = resolveSenderPlayer(sender);
        if (senderRef == null) {
            ctx.sendMessage(Message.raw("Could not determine player.").color("#FF5555"));
            return;
        }

        World world = Universe.get().getWorld(senderRef.getWorldUuid());
        if (world == null) {
            ctx.sendMessage(Message.raw("Could not determine your world.").color("#FF5555"));
            return;
        }

        if (parts.length == 2) {
            // /kit <name> - self-claim
            handleSelfClaim(ctx, senderRef, world, parts[1]);
        } else {
            // /kit - open GUI
            handleGui(ctx, senderRef, world);
        }
    }

    /**
     * Resolve the sender to a live PlayerRef (handles both PlayerRef and Player sender types).
     */
    @SuppressWarnings("removal") // Entity.getUuid() deprecated; no alternative in CommandContext for executeSync
    private static PlayerRef resolveSenderPlayer(Object sender) {
        if (sender instanceof PlayerRef playerRef) {
            return playerRef;
        }
        if (sender instanceof Player player) {
            UUID uuid = player.getUuid();
            return uuid != null ? Universe.get().getPlayer(uuid) : null;
        }
        return null;
    }

    /**
     * /kit &lt;player&gt; &lt;kit&gt; - give a kit to another player. Console is always allowed;
     * in-game senders need the kit give admin permission. Bypasses the target's kit
     * permission, cooldown, and one-time restrictions.
     */
    private void handleGive(CommandContext ctx, Object sender, boolean isConsole, String targetName, String kitName) {
        // Defensive: admin subcommands normally intercept these tokens before executeSync.
        if (targetName.equalsIgnoreCase("create") || targetName.equalsIgnoreCase("delete")) {
            ctx.sendMessage(Message.raw("Usage: /kit " + targetName.toLowerCase() + " <name> ...").color("#FF5555"));
            return;
        }

        if (!isConsole) {
            PlayerRef senderRef = resolveSenderPlayer(sender);
            if (senderRef == null) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("noPermission"), "#FF5555"));
                return;
            }
            if (!CommandPermissionUtil.canExecuteAdmin(ctx, senderRef, Permissions.KIT_GIVE,
                    configManager.getConfig().kits.enabled)) {
                return;
            }
        }

        PlayerRef target = PlayerSuggestionProvider.findPlayer(targetName);
        if (target == null) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("playerNotFound", "player", targetName), "#FF5555"));
            return;
        }

        World targetWorld = Universe.get().getWorld(target.getWorldUuid());
        if (targetWorld == null) {
            ctx.sendMessage(Message.raw("Could not determine the player's world.").color("#FF5555"));
            return;
        }

        final PlayerRef finalTarget = target;
        final String finalKitName = kitName;
        targetWorld.execute(() -> {
            Ref<EntityStore> ref = finalTarget.getReference();
            if (ref == null || !ref.isValid()) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("playerNotFound", "player", finalTarget.getUsername()), "#FF5555"));
                return;
            }
            Store<EntityStore> store = ref.getStore();
            if (store == null) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("kitClaimFailed"), "#FF5555"));
                return;
            }
            giveKit(ctx, store, ref, finalTarget, finalKitName, kitService, configManager);
        });
    }

    /**
     * /kit &lt;name&gt; - player claims a kit for themselves (full permission/cooldown/one-time checks).
     */
    private void handleSelfClaim(CommandContext ctx, PlayerRef senderRef, World world, String kitName) {
        final PlayerRef finalSender = senderRef;
        final World finalWorld = world;
        final String finalKitName = kitName;
        world.execute(() -> {
            Ref<EntityStore> ref = finalSender.getReference();
            if (ref == null || !ref.isValid()) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("kitClaimFailed"), "#FF5555"));
                return;
            }
            Store<EntityStore> store = ref.getStore();
            if (store == null) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("kitClaimFailed"), "#FF5555"));
                return;
            }

            if (WorldBlacklistUtil.isWorldBlacklisted(finalWorld.getName(), configManager.getConfig().kits.blacklistedWorlds)) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("commandBlacklistedWorld"), "#FF5555"));
                return;
            }

            // Base kit permission
            if (!PermissionService.get().canUseEveryoneCommand(finalSender.getUuid(), Permissions.KIT,
                    configManager.getConfig().kits.enabled)) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("noPermission"), "#FF5555"));
                return;
            }

            claimKit(ctx, store, ref, finalSender, finalKitName, kitService, configManager);
        });
    }

    /**
     * /kit - open the kit selection GUI for the sender.
     */
    private void handleGui(CommandContext ctx, PlayerRef senderRef, World world) {
        final PlayerRef finalSender = senderRef;
        final World finalWorld = world;
        world.execute(() -> {
            UUID playerId = finalSender.getUuid();

            // Self-heal: ensure a PlayerFile exists before any kit operation. Prevents the
            // infinite-kit exploit where a player whose PlayerReadyEvent never fired (observed
            // on abnormally slow logins) has no PlayerFile, causing all KitService reads to
            // return 0 (no cooldown) and writes to silently no-op.
            kitService.ensurePlayerFile(playerId, finalSender.getUsername());

            if (WorldBlacklistUtil.isWorldBlacklisted(finalWorld.getName(), configManager.getConfig().kits.blacklistedWorlds)) {
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

            // Check GUI permission - use hasPermission directly for strict check
            boolean hasGuiPerm = PermissionService.get().hasPermission(playerId, Permissions.KIT_GUI);
            boolean isAdmin = PermissionService.get().isAdmin(playerId);
            if (!hasGuiPerm && !isAdmin) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("noPermission"), "#FF5555"));
                return;
            }

            Ref<EntityStore> ref = finalSender.getReference();
            if (ref == null || !ref.isValid()) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("kitOpenFailed"), "#FF5555"));
                return;
            }
            Store<EntityStore> store = ref.getStore();
            if (store == null) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("kitOpenFailed"), "#FF5555"));
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
            KitSelectionPage kitPage = new KitSelectionPage(finalSender, kitService, configManager);
            playerComponent.getPageManager().openCustomPage(ref, store, kitPage);
        });
    }

    /**
     * Give a kit to a player as an admin/console action.
     * Bypasses the kit permission, cooldown, and one-time restrictions. Does not record a
     * cooldown or mark a one-time kit as claimed against the receiving player.
     */
    public static void giveKit(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref,
                               PlayerRef target, String kitName, KitService kitService, ConfigManager configManager) {
        UUID targetId = target.getUuid();
        kitService.ensurePlayerFile(targetId, target.getUsername());

        Kit kit = kitService.getKit(kitName);
        if (kit == null) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("kitNotFound", "kit", kitName), "#FF5555"));
            return;
        }

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

        // Inventory space check (skip for replace-inventory kits since they clear first)
        if (!kit.isReplaceInventory() && !hasInventorySpace(kit, store, ref)) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("kitInventoryFull"), "#FF5555"));
            return;
        }

        if (kit.isReplaceInventory()) {
            clearAllInventory(store, ref);
        }

        applyKit(kit, store, ref);

        // Execute kit commands (run ANY server command as console), targeting the receiving player
        if (kit.hasCommands()) {
            CommandExecutor.setDebugEnabled(configManager.isDebugEnabled());
            CommandExecutor.executeCommands(kit.getCommands(), target.getUsername(), targetId, "Kit-" + kit.getId());
        }

        // Notify the receiving player and the executor. No cooldown/one-time bookkeeping on an admin give.
        target.sendMessage(MessageFormatter.formatWithFallback(
            configManager.getMessage("kitClaimed", "kit", kit.getDisplayName()), "#55FF55"));
        ctx.sendMessage(MessageFormatter.formatWithFallback(
            configManager.getMessage("kitClaimed", "kit", kit.getDisplayName()) + " &7(for " + target.getUsername() + ")", "#55FF55"));

        if (configManager.isDebugEnabled()) {
            logger.info("[Kit] Gave kit '" + kit.getId() + "' to " + target.getUsername() + " (admin/console give)");
        }
    }

    /**
     * Claim a kit directly by name.
     * This is a static method so it can be called from the variant command and potentially NPCs.
     */
    public static void claimKit(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref,
                                PlayerRef player, String kitName, KitService kitService, ConfigManager configManager) {
        UUID playerId = player.getUuid();
        
        // Self-heal: ensure a PlayerFile exists before any kit operation. See note in execute().
        kitService.ensurePlayerFile(playerId, player.getUsername());
        
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
}