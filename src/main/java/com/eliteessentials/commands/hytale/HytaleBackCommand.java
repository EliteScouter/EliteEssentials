package com.eliteessentials.commands.hytale;

import com.eliteessentials.model.Location;
import com.eliteessentials.services.BackService;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Optional;
import java.util.UUID;

/**
 * Command: /back
 * Teleports the player to their previous location.
 */
public class HytaleBackCommand extends AbstractPlayerCommand {

    private final BackService backService;

    public HytaleBackCommand(BackService backService) {
        super("back", "Return to your previous location");
        this.backService = backService;
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref, 
                          PlayerRef player, World world) {
        UUID playerId = player.getUuid();
        
        Optional<Location> previousLocation = backService.popLocation(playerId);
        
        if (previousLocation.isEmpty()) {
            ctx.sendMessage(Message.raw("No previous location to go back to.").color("#FF5555"));
            return;
        }

        Location destination = previousLocation.get();
        
        // NOTE: We intentionally do NOT save current location when using /back
        // This prevents ping-ponging between locations when using /back multiple times
        // The back history is a stack of teleport origins, not a bidirectional history

        // Get target world
        World targetWorld = Universe.get().getWorld(destination.getWorld());
        if (targetWorld == null) {
            targetWorld = world;
        }

        // Teleport player
        final World finalWorld = targetWorld;
        world.execute(() -> {
            Vector3d targetPos = new Vector3d(destination.getX(), destination.getY(), destination.getZ());
            Vector3f targetRot = new Vector3f(destination.getPitch(), destination.getYaw(), 0);
            
            Teleport teleport = new Teleport(finalWorld, targetPos, targetRot);
            store.addComponent(ref, Teleport.getComponentType(), teleport);
            
            ctx.sendMessage(Message.raw("Teleported to your previous location.").color("#55FF55"));
        });
    }
}
