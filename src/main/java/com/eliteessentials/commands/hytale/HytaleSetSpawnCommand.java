package com.eliteessentials.commands.hytale;

import com.eliteessentials.EliteEssentials;
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
 * Command: /setspawn
 * Sets the server spawn point at the player's current location.
 * This location is used for /spawn teleports, spawn protection, and player respawns after death.
 * 
 * Permission: eliteessentials.command.spawn.set (OP only by default)
 */
public class HytaleSetSpawnCommand extends AbstractPlayerCommand {

    private final SpawnStorage spawnStorage;

    public HytaleSetSpawnCommand(SpawnStorage spawnStorage) {
        super("setspawn", "Set the server spawn location");
        this.spawnStorage = spawnStorage;
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                          @Nonnull PlayerRef player, @Nonnull World world) {
        
        // Check permission (Admin only)
        if (!CommandPermissionUtil.canExecuteAdmin(ctx, player, Permissions.SETSPAWN, true)) {
            return;
        }

        // Get player position
        TransformComponent transform = (TransformComponent) store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            ctx.sendMessage(MessageFormatter.formatWithFallback("Could not get your position.", "#FF5555"));
            return;
        }

        Vector3d pos = transform.getPosition();
        HeadRotation headRotation = (HeadRotation) store.getComponent(ref, HeadRotation.getComponentType());
        Vector3f rot = headRotation != null ? headRotation.getRotation() : new Vector3f(0, 0, 0);

        // Save spawn
        spawnStorage.setSpawn(world.getName(), pos.getX(), pos.getY(), pos.getZ(), rot.y, rot.x);

        // Update spawn protection service
        EliteEssentials.getInstance().getSpawnProtectionService().setSpawnLocation(pos.getX(), pos.getY(), pos.getZ());

        // NOTE: Respawn behavior is handled by RespawnListener system:
        // - Players with bed spawns will respawn at their bed (vanilla behavior)
        // - Players without bed spawns will respawn at this /setspawn location

        ctx.sendMessage(MessageFormatter.formatWithFallback("Spawn set at " + 
            String.format("%.1f, %.1f, %.1f", pos.getX(), pos.getY(), pos.getZ()) + 
            " (Players without beds will respawn here)", "#55FF55"));
    }
}
