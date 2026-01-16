package com.eliteessentials.commands.hytale;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.services.BackService;
import com.eliteessentials.model.Location;
import com.eliteessentials.util.CommandPermissionUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.UUID;

/**
 * Command: /top
 * Teleports the player to the highest block at their current X/Z position.
 * 
 * Permissions:
 * - eliteessentials.command.tp.top - Use /top command
 */
public class HytaleTopCommand extends AbstractPlayerCommand {

    private static final int MAX_HEIGHT = 256;
    
    private final BackService backService;
    private final ConfigManager configManager;

    public HytaleTopCommand(BackService backService, ConfigManager configManager) {
        super("top", "Teleport to the highest block above you");
        this.backService = backService;
        this.configManager = configManager;
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref,
                          PlayerRef player, World world) {
        UUID playerId = player.getUuid();
        
        // Check permission
        if (!CommandPermissionUtil.canExecute(ctx, player, Permissions.TOP, 
                configManager.getConfig().top.enabled)) {
            return;
        }

        // Get player's current position
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            ctx.sendMessage(Message.raw(configManager.getMessage("couldNotGetPosition")).color("#FF5555"));
            return;
        }

        Vector3d pos = transform.getPosition();
        int blockX = (int) Math.floor(pos.x);
        int blockZ = (int) Math.floor(pos.z);

        // Get chunk at player's position
        long chunkIndex = ChunkUtil.indexChunkFromBlock(blockX, blockZ);
        WorldChunk chunk = world.getChunk(chunkIndex);
        if (chunk == null) {
            ctx.sendMessage(Message.raw(configManager.getMessage("topChunkNotLoaded")).color("#FF5555"));
            return;
        }

        // Find highest solid block from top down
        Integer topY = findHighestSolidBlock(chunk, blockX, blockZ);
        if (topY == null) {
            ctx.sendMessage(Message.raw(configManager.getMessage("topNoGround")).color("#FF5555"));
            return;
        }

        // Save current location for /back
        HeadRotation headRotation = store.getComponent(ref, HeadRotation.getComponentType());
        Vector3f rotation = headRotation != null ? headRotation.getRotation() : new Vector3f(0, 0, 0);
        Location currentLoc = new Location(
            world.getName(),
            pos.getX(), pos.getY(), pos.getZ(),
            rotation.x, rotation.y
        );
        backService.pushLocation(playerId, currentLoc);

        // Teleport to one block above the highest solid block, centered
        double targetY = topY + 1;
        double centerX = Math.floor(pos.x) + 0.5;
        double centerZ = Math.floor(pos.z) + 0.5;
        Vector3d targetPos = new Vector3d(centerX, targetY, centerZ);

        // Round yaw to cardinal direction and zero pitch
        float yaw = rotation.y;
        float cardinalYaw = roundToCardinalYaw(yaw);
        Vector3f targetRotation = new Vector3f(0, cardinalYaw, 0);

        Teleport teleport = new Teleport(world, targetPos, targetRotation);
        store.putComponent(ref, Teleport.getComponentType(), teleport);

        ctx.sendMessage(Message.raw(configManager.getMessage("topTeleported")).color("#55FF55"));
    }

    /**
     * Finds the highest solid block at the given X/Z position.
     * @return Y coordinate of highest solid block, or null if none found
     */
    private Integer findHighestSolidBlock(WorldChunk chunk, int x, int z) {
        for (int y = MAX_HEIGHT; y >= 0; y--) {
            BlockType blockType = chunk.getBlockType(x, y, z);
            if (blockType != null && blockType.getMaterial() == BlockMaterial.Solid) {
                return y;
            }
        }
        return null;
    }

    /**
     * Round yaw to nearest cardinal direction (0, 90, 180, 270).
     */
    private float roundToCardinalYaw(float yaw) {
        yaw = yaw % 360;
        if (yaw < 0) yaw += 360;
        
        if (yaw < 45 || yaw >= 315) return 0;
        if (yaw < 135) return 90;
        if (yaw < 225) return 180;
        return 270;
    }
}
