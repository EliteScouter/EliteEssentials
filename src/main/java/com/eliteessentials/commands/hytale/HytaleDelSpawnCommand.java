package com.eliteessentials.commands.hytale;

import com.eliteessentials.EliteEssentials;
import com.eliteessentials.commands.args.SimpleStringArg;
import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.storage.SpawnStorage;
import com.eliteessentials.util.CommandPermissionUtil;
import com.eliteessentials.util.MessageFormatter;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.eliteessentials.commands.base.ElitePlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import com.eliteessentials.util.CommandSpyUtil;

/**
 * Command: /delspawn <name>
 * Deletes a named spawn point from the player's current world.
 * 
 * Permission: eliteessentials.command.spawn.delete (OP only by default)
 */
public class HytaleDelSpawnCommand extends ElitePlayerCommand {

    private final SpawnStorage spawnStorage;
    private final RequiredArg<String> nameArg;

    public HytaleDelSpawnCommand(SpawnStorage spawnStorage) {
        super("delspawn", "Delete a spawn point");
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
        if (!CommandPermissionUtil.canExecuteAdmin(ctx, player, Permissions.DELSPAWN, true)) {
            return;
        }

        ConfigManager configManager = EliteEssentials.getInstance().getConfigManager();
        String name = ctx.get(nameArg);
        String worldName = world.getName();

        boolean removed = spawnStorage.removeSpawn(worldName, name);

        if (removed) {
            // Refresh spawn protection
            EliteEssentials.getInstance().getSpawnProtectionService()
                .loadFromStorage(spawnStorage);
            
            // Re-sync to native provider
            SpawnStorage.SpawnData primary = spawnStorage.getPrimarySpawn(worldName);
            if (primary != null) {
                var config = EliteEssentials.getInstance().getConfigManager().getConfig();
                var cache = (config.spawn.multiNearbySpawn || config.spawn.multiRandomSpawn) ? EliteEssentials.getInstance().getDeathPositionCache() : null;
                spawnStorage.syncSpawnToWorld(world, primary, cache, config.spawn.multiRandomSpawn);
            }
            
            ctx.sendMessage(MessageFormatter.format(
                configManager.getMessage("spawnDeleted", "name", name, "world", worldName)));
        } else {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("spawnDeleteNotFound", "name", name), "#FF5555"));
        }
    }
}
