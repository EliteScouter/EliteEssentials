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

/**
 * Command: /delfirstjoinspawn
 * Removes the first-join spawn point.
 * 
 * Permission: eliteessentials.command.spawn.delfirstjoin (OP only by default)
 */
public class HytaleDelFirstJoinSpawnCommand extends AbstractPlayerCommand {

    private final SpawnStorage spawnStorage;

    public HytaleDelFirstJoinSpawnCommand(SpawnStorage spawnStorage) {
        super("delfirstjoinspawn", "Remove the first-join spawn point");
        this.spawnStorage = spawnStorage;
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                          @Nonnull PlayerRef player, @Nonnull World world) {
        if (!CommandPermissionUtil.canExecuteAdmin(ctx, player, Permissions.DELFIRSTJOINSPAWN, true)) {
            return;
        }

        ConfigManager configManager = EliteEssentials.getInstance().getConfigManager();

        if (spawnStorage.deleteFirstJoinSpawn()) {
            ctx.sendMessage(MessageFormatter.format(configManager.getMessage("firstJoinSpawnDeleted")));
        } else {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("firstJoinSpawnNotSet"), "#FF5555"));
        }
    }
}
