package com.eliteessentials.systems;

import com.eliteessentials.services.SpawnProtectionService;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentRegistry;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.RootDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.DamageBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.DropItemEvent;
import com.hypixel.hytale.server.core.event.events.ecs.InteractivelyPickupItemEvent;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Collections;
import java.util.Set;

/**
 * ECS systems for spawn protection.
 * Prevents block breaking/placing and PvP in the spawn area.
 */
public class SpawnProtectionSystem {

    private static final String PROTECTED_MSG = "This area is protected.";
    private static final String PVP_MSG = "PvP is disabled in spawn.";
    private static final String INTERACTION_MSG = "Interactions are disabled in spawn.";
    private static final String PICKUP_MSG = "Item pickups are disabled in spawn.";
    private static final String DROP_MSG = "Item drops are disabled in spawn.";
    private static final String MSG_COLOR = "#FF5555";

    private final SpawnProtectionService protectionService;

    public SpawnProtectionSystem(SpawnProtectionService protectionService) {
        this.protectionService = protectionService;
    }

    /**
     * Register all spawn protection systems.
     */
    public void register(ComponentRegistry<EntityStore> registry) {
        registry.registerSystem(new BreakBlockProtection(protectionService));
        registry.registerSystem(new PlaceBlockProtection(protectionService));
        registry.registerSystem(new DamageBlockProtection(protectionService));
        registry.registerSystem(new PvpProtection(protectionService));
        registry.registerSystem(new InteractionProtection(protectionService));
        registry.registerSystem(new ItemPickupProtection(protectionService));
        registry.registerSystem(new ItemDropProtection(protectionService));
    }

    // ==================== BLOCK BREAK PROTECTION ====================
    
    private static class BreakBlockProtection extends EntityEventSystem<EntityStore, BreakBlockEvent> {
        private final SpawnProtectionService service;

        BreakBlockProtection(SpawnProtectionService service) {
            super(BreakBlockEvent.class);
            this.service = service;
        }

        @Override
        public Query<EntityStore> getQuery() {
            return PlayerRef.getComponentType();
        }

        @Override
        public void handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                          CommandBuffer<EntityStore> buffer, BreakBlockEvent event) {
            if (!service.isEnabled() || event.isCancelled()) return;
            
            // Fast world check - skip worlds that have no spawn protection configured
            String worldName = getWorldName(store);
            if (worldName == null || !service.hasSpawnInWorld(worldName)) return;
            
            if (!service.isInProtectedArea(worldName, event.getTargetBlock())) return;

            PlayerRef player = chunk.getComponent(index, PlayerRef.getComponentType());
            if (player != null && service.canBypass(player.getUuid())) return;

            event.setCancelled(true);
            if (player != null) {
                player.sendMessage(Message.raw(PROTECTED_MSG).color(MSG_COLOR));
            }
        }
    }

    // ==================== BLOCK PLACE PROTECTION ====================
    
    private static class PlaceBlockProtection extends EntityEventSystem<EntityStore, PlaceBlockEvent> {
        private final SpawnProtectionService service;

        PlaceBlockProtection(SpawnProtectionService service) {
            super(PlaceBlockEvent.class);
            this.service = service;
        }

        @Override
        public Query<EntityStore> getQuery() {
            return PlayerRef.getComponentType();
        }

        @Override
        public void handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                          CommandBuffer<EntityStore> buffer, PlaceBlockEvent event) {
            if (!service.isEnabled() || event.isCancelled()) return;
            
            // Fast world check - skip worlds that have no spawn protection configured
            String worldName = getWorldName(store);
            if (worldName == null || !service.hasSpawnInWorld(worldName)) return;
            
            if (!service.isInProtectedArea(worldName, event.getTargetBlock())) return;

            PlayerRef player = chunk.getComponent(index, PlayerRef.getComponentType());
            if (player != null && service.canBypass(player.getUuid())) return;

            event.setCancelled(true);
            if (player != null) {
                player.sendMessage(Message.raw(PROTECTED_MSG).color(MSG_COLOR));
            }
        }
    }

    // ==================== BLOCK DAMAGE PROTECTION ====================
    
    private static class DamageBlockProtection extends EntityEventSystem<EntityStore, DamageBlockEvent> {
        private final SpawnProtectionService service;

        DamageBlockProtection(SpawnProtectionService service) {
            super(DamageBlockEvent.class);
            this.service = service;
        }

        @Override
        public Query<EntityStore> getQuery() {
            return PlayerRef.getComponentType();
        }

        @Override
        public void handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                          CommandBuffer<EntityStore> buffer, DamageBlockEvent event) {
            if (!service.isEnabled() || event.isCancelled()) return;
            
            // Fast world check - skip worlds that have no spawn protection configured
            String worldName = getWorldName(store);
            if (worldName == null || !service.hasSpawnInWorld(worldName)) return;
            
            if (!service.isInProtectedArea(worldName, event.getTargetBlock())) return;

            PlayerRef player = chunk.getComponent(index, PlayerRef.getComponentType());
            if (player != null && service.canBypass(player.getUuid())) return;

            event.setCancelled(true);
        }
    }

    // ==================== PVP AND ALL DAMAGE PROTECTION ====================
    
    private static class PvpProtection extends DamageEventSystem {
        private final SpawnProtectionService service;

        PvpProtection(SpawnProtectionService service) {
            super();
            this.service = service;
        }

        @Override
        public SystemGroup<EntityStore> getGroup() {
            return DamageModule.get().getFilterDamageGroup();
        }

        @Override
        public Query<EntityStore> getQuery() {
            return PlayerRef.getComponentType();
        }

        @Override
        public void handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                          CommandBuffer<EntityStore> buffer, Damage event) {
            // Early exit if already cancelled
            if (event.isCancelled()) {
                return;
            }
            
            // Check if spawn protection is enabled at all
            if (!service.isEnabled()) {
                return;
            }

            // Fast world check - skip worlds that have no spawn protection configured
            // This is critical for arena worlds where spawn protection doesn't apply
            String worldName = getWorldName(store);
            if (worldName == null || !service.hasSpawnInWorld(worldName)) {
                return;
            }

            // Check if victim is a player
            PlayerRef victim = chunk.getComponent(index, PlayerRef.getComponentType());
            if (victim == null) return;

            // Check if victim is in protected area (world-specific)
            if (!service.isInProtectedArea(worldName, victim.getTransform().getPosition())) {
                return;
            }
            
            // Check if ALL damage protection is enabled (blocks everything including NPC/mob damage)
            if (service.isAllDamageProtectionEnabled()) {
                // Use damage-specific bypass check (nobody bypasses by default - even admins are protected)
                if (!service.canBypassDamageProtection(victim.getUuid())) {
                    event.setCancelled(true);
                    event.setAmount(0);
                    return; // Block ALL damage - no need to check PvP
                }
            }

            // If all damage protection didn't block, check PvP protection
            if (!service.isPvpProtectionEnabled()) {
                return;
            }

            // Check if attacker is a player (for PvP-only protection)
            Damage.Source source = event.getSource();
            if (!(source instanceof Damage.EntitySource entitySource)) {
                return;
            }

            Ref<EntityStore> attackerRef = entitySource.getRef();
            if (!attackerRef.isValid()) return;

            PlayerRef attacker = store.getComponent(attackerRef, PlayerRef.getComponentType());
            if (attacker == null) return; // Not a player attack

            // Cancel PvP damage
            event.setCancelled(true);
            event.setAmount(0);
            attacker.sendMessage(Message.raw(PVP_MSG).color(MSG_COLOR));
        }
    }

    // ==================== BLOCK INTERACTION PROTECTION ====================
    
    private static class InteractionProtection extends EntityEventSystem<EntityStore, UseBlockEvent.Pre> {
        private final SpawnProtectionService service;

        InteractionProtection(SpawnProtectionService service) {
            super(UseBlockEvent.Pre.class);
            this.service = service;
        }

        @Override
        public Query<EntityStore> getQuery() {
            return PlayerRef.getComponentType();
        }

        @Override
        public void handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                          CommandBuffer<EntityStore> buffer, UseBlockEvent.Pre event) {
            if (!service.isEnabled() || event.isCancelled()) return;
            if (!service.isInteractionProtectionEnabled()) return;
            
            // Fast world check - skip worlds that have no spawn protection configured
            String worldName = getWorldName(store);
            if (worldName == null || !service.hasSpawnInWorld(worldName)) return;
            
            if (!service.isInProtectedArea(worldName, event.getTargetBlock())) return;

            PlayerRef player = chunk.getComponent(index, PlayerRef.getComponentType());
            if (player != null && service.canBypass(player.getUuid())) return;

            event.setCancelled(true);
            if (player != null) {
                player.sendMessage(Message.raw(INTERACTION_MSG).color(MSG_COLOR));
            }
        }
    }

    // ==================== ITEM PICKUP PROTECTION ====================
    
    /**
     * NOTE: Item pickup cancellation may not work properly in current Hytale API.
     * Uses RootDependency.first() to try to run before the pickup is processed.
     */
    private static class ItemPickupProtection extends EntityEventSystem<EntityStore, InteractivelyPickupItemEvent> {
        private final SpawnProtectionService service;

        ItemPickupProtection(SpawnProtectionService service) {
            super(InteractivelyPickupItemEvent.class);
            this.service = service;
        }

        @Override
        public Query<EntityStore> getQuery() {
            return PlayerRef.getComponentType();
        }
        
        @Override
        public Set<Dependency<EntityStore>> getDependencies() {
            return Collections.singleton(RootDependency.first());
        }

        @Override
        public void handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                          CommandBuffer<EntityStore> buffer, InteractivelyPickupItemEvent event) {
            if (!service.isEnabled() || event.isCancelled()) return;
            if (!service.isItemPickupProtectionEnabled()) return;

            // Fast world check - skip worlds that have no spawn protection configured
            String worldName = getWorldName(store);
            if (worldName == null || !service.hasSpawnInWorld(worldName)) return;

            PlayerRef player = chunk.getComponent(index, PlayerRef.getComponentType());
            if (player == null) return;
            
            if (!service.isInProtectedArea(worldName, player.getTransform().getPosition())) return;
            if (service.canBypass(player.getUuid())) return;

            event.setCancelled(true);
            player.sendMessage(Message.raw(PICKUP_MSG).color(MSG_COLOR));
        }
    }

    // ==================== ITEM DROP PROTECTION ====================
    
    /**
     * Uses PlayerRequest event which fires BEFORE the item is removed from inventory.
     * This properly prevents the drop without losing the item.
     */
    private static class ItemDropProtection extends EntityEventSystem<EntityStore, DropItemEvent.PlayerRequest> {
        private final SpawnProtectionService service;

        ItemDropProtection(SpawnProtectionService service) {
            super(DropItemEvent.PlayerRequest.class);
            this.service = service;
        }

        @Override
        public Query<EntityStore> getQuery() {
            return PlayerRef.getComponentType();
        }

        @Override
        public void handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                          CommandBuffer<EntityStore> buffer, DropItemEvent.PlayerRequest event) {
            if (!service.isEnabled() || event.isCancelled()) return;
            if (!service.isItemDropProtectionEnabled()) return;

            // Fast world check - skip worlds that have no spawn protection configured
            String worldName = getWorldName(store);
            if (worldName == null || !service.hasSpawnInWorld(worldName)) return;

            PlayerRef player = chunk.getComponent(index, PlayerRef.getComponentType());
            if (player == null) return;
            
            if (!service.isInProtectedArea(worldName, player.getTransform().getPosition())) return;
            if (service.canBypass(player.getUuid())) return;

            event.setCancelled(true);
            player.sendMessage(Message.raw(DROP_MSG).color(MSG_COLOR));
        }
    }
    
    // ==================== HELPER METHODS ====================
    
    /**
     * Get the world name from the store's external data.
     * Returns null if world cannot be determined.
     */
    private static String getWorldName(Store<EntityStore> store) {
        EntityStore entityStore = store.getExternalData();
        if (entityStore == null) return null;
        
        World world = entityStore.getWorld();
        if (world == null) return null;
        
        return world.getName();
    }
}
