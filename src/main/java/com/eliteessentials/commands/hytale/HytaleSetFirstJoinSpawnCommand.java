package com.eliteessentials.commands.hytale;

import com.eliteessentials.EliteEssentials;
import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.storage.SpawnStorage;
import com.eliteessentials.util.CommandPermissionUtil;
import com.eliteessentials.util.MessageFormatter;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * Command: /setfirstjoinspawn
 * Sets the first-join spawn point at the player's current location.
 * New players will be teleported here on their first join.
 * 
 * Permission: eliteessentials.command.spawn.setfirstjoin (OP only by default)
 */
public class HytaleSetFirstJoinSpawnCommand extends AbstractPlayerCommand {

    private final SpawnStorage spawnStorage;

    public HytaleSetFirstJoinSpawnCommand(SpawnStorage spawnStorage) {
        super("setfirstjoinspawn", "Set the first-join spawn location");
        this.spawnStorage = spawnStorage;
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                          @Nonnull PlayerRef player, @Nonnull World world) {
        if (!CommandPermissionUtil.canExecuteAdmin(ctx, player, Permissions.SETFIRSTJOINSPAWN, true)) {
            return;
        }

        ConfigManager configManager = EliteEssentials.getInstance().getConfigManager();

        TransformComponent transform = (TransformComponent) store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            ctx.sendMessage(MessageFormatter.formatWithFallback("Could not get your position.", "#FF5555"));
            return;
        }

        Vector3d pos = transform.getPosition();
        HeadRotation headRotation = (HeadRotation) store.getComponent(ref, HeadRotation.getComponentType());
        Vector3f rot = headRotation != null ? headRotation.getRotation() : new Vector3f(0, 0, 0);

        String worldName = world.getName();
        String location = String.format("%.1f, %.1f, %.1f", pos.getX(), pos.getY(), pos.getZ());

        spawnStorage.setFirstJoinSpawn(worldName, pos.getX(), pos.getY(), pos.getZ(), rot.y, rot.x);

        String message = configManager.getMessage("firstJoinSpawnSet", "location", location, "world", worldName);
        ctx.sendMessage(MessageFormatter.format(message));
    }
}
