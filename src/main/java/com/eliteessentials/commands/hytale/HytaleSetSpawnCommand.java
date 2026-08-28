package com.eliteessentials.commands.hytale;

import com.eliteessentials.EliteEssentials;
import com.eliteessentials.commands.args.SimpleStringArg;
import com.eliteessentials.config.PluginConfig;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.storage.SpawnStorage;
import com.eliteessentials.util.CommandPermissionUtil;
import com.eliteessentials.util.MessageFormatter;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import org.joml.Vector3d;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.eliteessentials.commands.base.ElitePlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import com.eliteessentials.util.CommandSpyUtil;

/**
 * Command: /setspawn [name]
 * Sets a server spawn point at the player's current location.
 * 
 * Without a name argument: sets/updates the primary spawn (backward compatible).
 * With a name argument (perWorld=true only): creates a named spawn point for multi-spawn.
 * 
 * Permission: eliteessentials.command.spawn.set (OP only by default)
 */
public class HytaleSetSpawnCommand extends ElitePlayerCommand {

    private final SpawnStorage spawnStorage;

    public HytaleSetSpawnCommand(SpawnStorage spawnStorage) {
        super("setspawn", "Set the server spawn location");
        this.spawnStorage = spawnStorage;
        
        addUsageVariant(new SetSpawnWithNameCommand(spawnStorage));
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                          @Nonnull PlayerRef player, @Nonnull World world) {
        CommandSpyUtil.notify(ctx);
        if (!CommandPermissionUtil.canExecuteAdmin(ctx, player, Permissions.SETSPAWN, true)) {
            return;
        }

        doSetSpawn(ctx, store, ref, player, world, null, spawnStorage);
    }
    
    /**
     * Core setspawn logic. Name=null means set/update the primary spawn.
     */
    static void doSetSpawn(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref,
                           PlayerRef player, World world, String name, SpawnStorage spawnStorage) {
        var configManager = EliteEssentials.getInstance().getConfigManager();
        PluginConfig config = configManager.getConfig();
        
        TransformComponent transform = (TransformComponent) store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            ctx.sendMessage(MessageFormatter.formatWithFallback("Could not get your position.", "#FF5555"));
            return;
        }

        Vector3d pos = transform.getPosition();
        HeadRotation headRotation = (HeadRotation) store.getComponent(ref, HeadRotation.getComponentType());
        Rotation3f rot = headRotation != null ? headRotation.getRotation() : new Rotation3f(0, 0, 0);

        String worldName = world.getName();
        String location = String.format("%.1f, %.1f, %.1f", pos.x, pos.y, pos.z);
        
        if (name == null) {
            // Set/update primary spawn (backward compatible path)
            spawnStorage.setSpawn(worldName, pos.x, pos.y, pos.z, rot.y, rot.x);
            
            SpawnStorage.SpawnData spawnData = spawnStorage.getSpawn(worldName);
            if (spawnData != null) {
                var cache = (config.spawn.multiNearbySpawn || config.spawn.multiRandomSpawn) ? EliteEssentials.getInstance().getDeathPositionCache() : null;
                spawnStorage.syncSpawnToWorld(world, spawnData, cache, config.spawn.multiRandomSpawn);
            }
            
            EliteEssentials.getInstance().getSpawnProtectionService()
                .loadFromStorage(spawnStorage);
            
            String message = configManager.getMessage("spawnSet", "world", worldName, "location", location);
            ctx.sendMessage(MessageFormatter.format(message));
        } else {
            // Named spawn - only allowed when perWorld=true
            if (!config.spawn.perWorld) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("spawnMultiSpawnDisabled"), "#FF5555"));
                return;
            }
            
            // Check if this is the first spawn (will auto-become primary)
            boolean isPrimary = !spawnStorage.hasSpawn(worldName);
            int maxPerWorld = config.spawn.maxSpawnsPerWorld;
            
            SpawnStorage.SpawnData result = spawnStorage.addSpawn(
                worldName, name, pos.x, pos.y, pos.z, rot.y, rot.x,
                isPrimary, true, maxPerWorld);
            
            if (result == null) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("spawnLimitReached", "max", String.valueOf(maxPerWorld)), "#FF5555"));
                return;
            }
            
            // Always sync - use NearestSpawnProvider or RandomSpawnProvider when multi-spawn mode enabled
            SpawnStorage.SpawnData primary = spawnStorage.getPrimarySpawn(worldName);
            if (primary != null) {
                var cache = (config.spawn.multiNearbySpawn || config.spawn.multiRandomSpawn) ? EliteEssentials.getInstance().getDeathPositionCache() : null;
                spawnStorage.syncSpawnToWorld(world, primary, cache, config.spawn.multiRandomSpawn);
            }
            
            EliteEssentials.getInstance().getSpawnProtectionService()
                .loadFromStorage(spawnStorage);
            
            String message = configManager.getMessage("spawnSetNamed", 
                "name", name, "world", worldName, "location", location);
            ctx.sendMessage(MessageFormatter.format(message));
        }
    }
    
    /**
     * Variant: /setspawn <name>
     */
    private static class SetSpawnWithNameCommand extends ElitePlayerCommand {
        private final SpawnStorage spawnStorage;
        private final RequiredArg<String> nameArg;
        
        SetSpawnWithNameCommand(SpawnStorage spawnStorage) {
            super("setspawn");
            this.spawnStorage = spawnStorage;
            this.nameArg = withRequiredArg("name", "Spawn point name", SimpleStringArg.SPAWN_NAME);
        }
        
        @Override
        protected boolean canGeneratePermission() {
            return false;
        }
        
        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                              @Nonnull PlayerRef player, @Nonnull World world) {
        CommandSpyUtil.notify(ctx);
            if (!CommandPermissionUtil.canExecuteAdmin(ctx, player, Permissions.SETSPAWN, true)) {
                return;
            }
            
            String name = ctx.get(nameArg);
            HytaleSetSpawnCommand.doSetSpawn(ctx, store, ref, player, world, name, spawnStorage);
        }
    }
}
