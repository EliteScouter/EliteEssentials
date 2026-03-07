package com.eliteessentials.interactions;

import com.eliteessentials.EliteEssentials;
import com.eliteessentials.services.SpawnProtectionService;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionSyncData;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.interaction.Interactions;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * Custom UseEntity interaction that enforces spawn protection for decoration pickups.
 * Prevents picking up entity-based decorations (mushrooms, flowers, etc.) in protected spawn areas.
 *
 * Extends SimpleInstantInteraction directly because UseEntityInteraction.firstRun() is final.
 * Reimplements the UseEntity logic (resolve target entity, look up its interaction, execute it)
 * with a spawn protection check inserted before execution.
 */
public class SpawnUseEntityInteraction extends SimpleInstantInteraction {

    private static final String PICKUP_MSG = "Item pickups are disabled in spawn.";
    private static final String INTERACTION_MSG = "Interactions are disabled in spawn.";
    private static final String MSG_COLOR = "#FF5555";

    public static final BuilderCodec<SpawnUseEntityInteraction> CUSTOM_CODEC =
        BuilderCodec.builder(SpawnUseEntityInteraction.class, SpawnUseEntityInteraction::new, SimpleInstantInteraction.CODEC)
            .build();

    @Override
    public com.hypixel.hytale.protocol.WaitForDataFrom getWaitForDataFrom() {
        return com.hypixel.hytale.protocol.WaitForDataFrom.Client;
    }

    @Override
    protected void firstRun(@Nonnull InteractionType type, @Nonnull InteractionContext context,
                            @Nonnull CooldownHandler cooldownHandler) {
        InteractionSyncData clientState = context.getClientState();
        if (clientState == null) {
            failInteraction(context);
            return;
        }

        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
        if (commandBuffer == null) {
            failInteraction(context);
            return;
        }

        Store<EntityStore> store = commandBuffer.getStore();
        EntityStore entityStore = (EntityStore) store.getExternalData();
        Ref<EntityStore> targetRef = entityStore.getRefFromNetworkId(clientState.entityId);

        if (targetRef == null || !targetRef.isValid()) {
            failInteraction(context);
            return;
        }

        // --- Spawn protection check ---
        if (type != InteractionType.Primary && type != InteractionType.Secondary) {
            if (shouldBlockInteraction(context, store, targetRef, entityStore)) {
                failInteraction(context);
                return;
            }
        }

        Interactions interactions = commandBuffer.getComponent(targetRef, Interactions.getComponentType());
        if (interactions == null) {
            failInteraction(context);
            return;
        }

        String interactionId = interactions.getInteractionId(type);
        if (interactionId == null) {
            failInteraction(context);
            return;
        }

        context.execute(RootInteraction.getRootInteractionOrUnknown(interactionId));
    }

    @Override
    protected com.hypixel.hytale.protocol.Interaction generatePacket() {
        return new com.hypixel.hytale.protocol.UseEntityInteraction();
    }

    @Override
    public boolean needsRemoteSync() {
        return true;
    }

    private void failInteraction(InteractionContext context) {
        context.getState().state = InteractionState.Failed;
    }

    private boolean shouldBlockInteraction(InteractionContext context, Store<EntityStore> store,
                                           Ref<EntityStore> targetRef, EntityStore entityStore) {
        SpawnProtectionService service = EliteEssentials.getInstance().getSpawnProtectionService();
        if (service == null || !service.isEnabled()) return false;

        boolean isPickupProtection = service.isItemPickupProtectionEnabled();
        boolean isInteractionProtection = service.isInteractionProtectionEnabled();
        if (!isPickupProtection && !isInteractionProtection) return false;

        String worldName = entityStore.getWorld() != null ? entityStore.getWorld().getName() : null;
        if (worldName == null || !service.hasSpawnInWorld(worldName)) return false;

        // Check target entity position
        TransformComponent transform = store.getComponent(targetRef, TransformComponent.getComponentType());
        if (transform == null) return false;
        if (!service.isInProtectedArea(worldName, transform.getPosition())) return false;

        // Check player bypass
        Ref<EntityStore> playerEntityRef = context.getEntity();
        PlayerRef player = store.getComponent(playerEntityRef, PlayerRef.getComponentType());
        if (player == null) return false;
        if (service.canBypass(player.getUuid())) return false;

        String msg = isInteractionProtection ? INTERACTION_MSG : PICKUP_MSG;
        player.sendMessage(Message.raw(msg).color(MSG_COLOR));
        return true;
    }
}
