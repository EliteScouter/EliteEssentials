package com.eliteessentials.systems;

import com.eliteessentials.services.SpawnProtectionService;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.RootDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.InteractivelyPickupItemEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Set;

/**
 * Exact copy of SimpleClaims PickupInteractEventSystem approach.
 */
public class PickupProtectionSystem extends EntityEventSystem<EntityStore, InteractivelyPickupItemEvent> {

    private final SpawnProtectionService service;

    public PickupProtectionSystem(SpawnProtectionService service) {
        super(InteractivelyPickupItemEvent.class);
        this.service = service;
    }

    @Override
    public void handle(final int index, @Nonnull final ArchetypeChunk<EntityStore> archetypeChunk, 
                       @Nonnull final Store<EntityStore> store, 
                       @Nonnull final CommandBuffer<EntityStore> commandBuffer, 
                       @Nonnull final InteractivelyPickupItemEvent event) {
        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        
        if (playerRef != null && !isAllowedToPickup(playerRef, player)) {
            event.setCancelled(true);
        }
    }

    private boolean isAllowedToPickup(PlayerRef playerRef, Player player) {
        if (!service.isEnabled() || !service.isItemPickupProtectionEnabled()) {
            return true;
        }
        
        if (player == null || player.getWorld() == null) {
            return true;
        }
        
        String worldName = player.getWorld().getName();
        int x = (int) playerRef.getTransform().getPosition().getX();
        int z = (int) playerRef.getTransform().getPosition().getZ();
        
        // Check if in protected area using player position
        if (!service.isInProtectedArea(worldName, x, z)) {
            return true;
        }
        
        // Check bypass
        if (service.canBypass(playerRef.getUuid())) {
            return true;
        }
        
        return false;
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return PlayerRef.getComponentType();
    }

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return Collections.singleton(RootDependency.first());
    }
}
