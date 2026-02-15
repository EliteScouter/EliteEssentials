package com.eliteessentials.interactions;

import com.eliteessentials.EliteEssentials;
import com.eliteessentials.services.SpawnProtectionService;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.UseBlockInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Locale;

/**
 * Custom UseBlock interaction that enforces spawn protection for F-key pickups.
 * Prevents picking up flowers, pebbles, mushrooms, etc. in protected spawn areas.
 * 
 * This replaces the vanilla UseBlockInteraction via codec registry, intercepting 
 * at the interaction level BEFORE any event fires. This is necessary because 
 * InteractivelyPickupItemEvent.setCancelled(true) is broken in Hytale's API.
 * 
 * Based on SimpleClaims' ClaimUseBlockInteraction approach.
 */
public class SpawnUseBlockInteraction extends UseBlockInteraction {

    private static final String PICKUP_MSG = "Item pickups are disabled in spawn.";
    private static final String INTERACTION_MSG = "Interactions are disabled in spawn.";
    private static final String MSG_COLOR = "#FF5555";

    public static final BuilderCodec<SpawnUseBlockInteraction> CUSTOM_CODEC =
        BuilderCodec.builder(SpawnUseBlockInteraction.class, SpawnUseBlockInteraction::new, SimpleBlockInteraction.CODEC)
            .build();

    @Override
    protected void interactWithBlock(World world, CommandBuffer<EntityStore> commandBuffer,
                                     InteractionType type, InteractionContext context,
                                     ItemStack itemInHand, Vector3i targetBlock,
                                     CooldownHandler cooldownHandler) {
        // Primary/Secondary (left/right click) always pass through
        if (type == InteractionType.Primary || type == InteractionType.Secondary) {
            super.interactWithBlock(world, commandBuffer, type, context, itemInHand, targetBlock, cooldownHandler);
            return;
        }

        // Check spawn protection for F-key interactions
        if (shouldBlockInteraction(world, context, targetBlock, true)) {
            return; // Block - don't call super, so the pickup/interaction never happens
        }

        super.interactWithBlock(world, commandBuffer, type, context, itemInHand, targetBlock, cooldownHandler);
    }

    @Override
    protected void simulateInteractWithBlock(InteractionType type, InteractionContext context,
                                             ItemStack itemInHand, World world,
                                             Vector3i targetBlock) {
        // Primary/Secondary always pass through
        if (type == InteractionType.Primary || type == InteractionType.Secondary) {
            super.simulateInteractWithBlock(type, context, itemInHand, world, targetBlock);
            return;
        }

        // Block simulation too (prevents client-side visual glitches)
        if (shouldBlockInteraction(world, context, targetBlock, false)) {
            return;
        }

        super.simulateInteractWithBlock(type, context, itemInHand, world, targetBlock);
    }

    /**
     * Check if this F-key interaction should be blocked by spawn protection.
     * 
     * @param sendMessage whether to send a denial message to the player
     * @return true if the interaction should be blocked
     */
    private boolean shouldBlockInteraction(World world, InteractionContext context,
                                           Vector3i targetBlock, boolean sendMessage) {
        SpawnProtectionService service = EliteEssentials.getInstance().getSpawnProtectionService();
        if (service == null || !service.isEnabled()) return false;

        // Fast world check - skip worlds that have no spawn protection configured
        // This is critical for arena worlds where spawn protection doesn't apply
        String worldName = world.getName();
        if (worldName == null || !service.hasSpawnInWorld(worldName)) return false;

        boolean isPickupProtection = service.isItemPickupProtectionEnabled();
        boolean isInteractionProtection = service.isInteractionProtectionEnabled();

        // Need at least one of these enabled
        if (!isPickupProtection && !isInteractionProtection) return false;

        // When only pickup protection is enabled (not full interaction protection),
        // allow functional blocks (doors, chests, benches, etc.) to still work
        if (isPickupProtection && !isInteractionProtection) {
            if (isFunctionalBlock(world, targetBlock)) return false;
        }

        // Check if player is in a protected area
        Ref<EntityStore> ref = context.getEntity();
        Store<EntityStore> store = ref.getStore();
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) return false;

        if (!service.isInProtectedArea(worldName, targetBlock)) return false;
        if (service.canBypass(playerRef.getUuid())) return false;

        // Block the interaction
        if (sendMessage) {
            String msg = isInteractionProtection ? INTERACTION_MSG : PICKUP_MSG;
            playerRef.sendMessage(Message.raw(msg).color(MSG_COLOR));
        }
        return true;
    }

    /**
     * Check if a block is a "functional" block that should allow F-key interaction
     * even when item pickup protection is enabled.
     * 
     * Functional blocks include doors, chests, benches, etc. - things that open UIs 
     * or toggle states, rather than being "picked up" into inventory.
     */
    private boolean isFunctionalBlock(World world, Vector3i targetBlock) {
        var blockType = world.getBlockType(targetBlock);
        if (blockType == null) return false;
        String blockName = blockType.getId().toLowerCase(Locale.ROOT);
        return blockName.contains("chest") ||
               blockName.contains("door") ||
               blockName.contains("bench") ||
               blockName.contains("furnace") ||
               blockName.contains("portal") ||
               blockName.contains("teleporter") ||
               blockName.contains("chair") ||
               blockName.contains("stool") ||
               blockName.contains("table") ||
               blockName.contains("sign") ||
               blockName.contains("bed") ||
               blockName.contains("lever") ||
               blockName.contains("button") ||
               blockName.contains("switch") ||
               blockName.contains("gate") ||
               blockName.contains("anvil");
    }
}
