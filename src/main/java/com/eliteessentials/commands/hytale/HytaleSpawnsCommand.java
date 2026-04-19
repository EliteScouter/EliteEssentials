package com.eliteessentials.commands.hytale;

import com.eliteessentials.EliteEssentials;
import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.storage.SpawnStorage;
import com.eliteessentials.util.CommandPermissionUtil;
import com.eliteessentials.util.MessageFormatter;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.List;
import com.eliteessentials.util.CommandSpyUtil;

/**
 * Command: /spawns
 * Lists all spawn points in the player's current world.
 * 
 * Permission: eliteessentials.command.spawn.list (OP only by default)
 */
public class HytaleSpawnsCommand extends AbstractPlayerCommand {

    private final SpawnStorage spawnStorage;

    public HytaleSpawnsCommand(SpawnStorage spawnStorage) {
        super("spawns", "List spawn points in this world");
        this.spawnStorage = spawnStorage;
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                          @Nonnull PlayerRef player, @Nonnull World world) {
        CommandSpyUtil.notify(ctx);
        if (!CommandPermissionUtil.canExecuteAdmin(ctx, player, Permissions.SPAWNS, true)) {
            return;
        }

        ConfigManager configManager = EliteEssentials.getInstance().getConfigManager();
        String worldName = world.getName();
        List<SpawnStorage.SpawnData> spawns = spawnStorage.getSpawns(worldName);

        ctx.sendMessage(MessageFormatter.format(
            configManager.getMessage("spawnListHeader", "world", worldName)));

        if (spawns.isEmpty()) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("spawnListEmpty"), "#AAAAAA"));
            return;
        }

        String primaryTag = configManager.getMessage("spawnListPrimary");
        String protectedTag = configManager.getMessage("spawnListProtected");

        for (SpawnStorage.SpawnData spawn : spawns) {
            String location = String.format("%.1f, %.1f, %.1f", spawn.x, spawn.y, spawn.z);
            String primary = spawn.primary ? primaryTag : "";
            String protection = spawn.protection ? protectedTag : "";
            
            String entry = configManager.getMessage("spawnListEntry",
                "name", spawn.name != null ? spawn.name : "unnamed",
                "location", location,
                "primary", primary,
                "protection", protection);
            ctx.sendMessage(MessageFormatter.format(entry));
        }
    }
}
