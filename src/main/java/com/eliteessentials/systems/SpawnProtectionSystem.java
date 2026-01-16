package com.eliteessentials.systems;

import com.eliteessentials.services.SpawnProtectionService;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentRegistry;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.DamageBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * ECS systems for spawn protection.
 * Prevents block breaking/placing and PvP in the spawn area.
 */
public class SpawnProtectionSystem {

    private static final String PROTECTED_MSG = "This area is protected.";
    private static final String PVP_MSG = "PvP is disabled in spawn.";
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
            return Query.any();
        }

        @Override
        public void handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                          CommandBuffer<EntityStore> buffer, BreakBlockEvent event) {
            if (!service.isEnabled() || event.isCancelled()) return;
            if (!service.isInProtectedArea(event.getTargetBlock())) return;

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
            return Query.any();
        }

        @Override
        public void handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                          CommandBuffer<EntityStore> buffer, PlaceBlockEvent event) {
            if (!service.isEnabled() || event.isCancelled()) return;
            if (!service.isInProtectedArea(event.getTargetBlock())) return;

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
            return Query.any();
        }

        @Override
        public void handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                          CommandBuffer<EntityStore> buffer, DamageBlockEvent event) {
            if (!service.isEnabled() || event.isCancelled()) return;
            if (!service.isInProtectedArea(event.getTargetBlock())) return;

            PlayerRef player = chunk.getComponent(index, PlayerRef.getComponentType());
            if (player != null && service.canBypass(player.getUuid())) return;

            event.setCancelled(true);
        }
    }

    // ==================== PVP PROTECTION ====================
    
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
            return Query.any();
        }

        @Override
        public void handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                          CommandBuffer<EntityStore> buffer, Damage event) {
            if (!service.isEnabled() || !service.isPvpProtectionEnabled() || event.isCancelled()) {
                return;
            }

            // Check if victim is a player
            PlayerRef victim = chunk.getComponent(index, PlayerRef.getComponentType());
            if (victim == null) return;

            // Check if victim is in protected area
            if (!service.isInProtectedArea(victim.getTransform().getPosition())) {
                return;
            }

            // Check if attacker is a player
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
}
