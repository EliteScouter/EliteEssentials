package com.eliteessentials.commands.hytale;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.config.PluginConfig;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.permissions.PermissionService;
import com.eliteessentials.services.BackService;
import com.eliteessentials.services.CooldownService;
import com.eliteessentials.model.Location;
import com.eliteessentials.util.CommandPermissionUtil;
import com.eliteessentials.util.MessageFormatter;
import com.eliteessentials.util.TeleportUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;

import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.UUID;

import javax.annotation.Nonnull;

/**
 * Command: /top
 * Teleports the player to the highest block at their current X/Z position.
 * 
 * Permissions:
 * - eliteessentials.command.tp.top - Use /top command
 * - eliteessentials.command.tp.top.bypass.cooldown - Skip cooldown
 * - eliteessentials.command.tp.top.cooldown.<seconds> - Set specific cooldown
 */
public class HytaleTopCommand extends AbstractPlayerCommand {

    private static final String COMMAND_NAME = "top";
    private static final int MAX_HEIGHT = 256;
    
    private final BackService backService;
    private final ConfigManager configManager;
    private final CooldownService cooldownService;

    public HytaleTopCommand(BackService backService, ConfigManager configManager, CooldownService cooldownService) {
        super(COMMAND_NAME, "Teleport to the highest block above you");
        this.backService = backService;
        this.configManager = configManager;
        this.cooldownService = cooldownService;
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                          @Nonnull PlayerRef player, @Nonnull World world) {
        UUID playerId = player.getUuid();
        PluginConfig.TopConfig topConfig = configManager.getConfig().top;
        
        // Check permission (Admin command)
        if (!CommandPermissionUtil.canExecuteAdmin(ctx, player, Permissions.TOP, topConfig.enabled)) {
            return;
        }
        
        // Get effective cooldown from permissions
        int effectiveCooldown = PermissionService.get().getCommandCooldown(playerId, COMMAND_NAME, topConfig.cooldownSeconds);
        
        // Check cooldown if player has one
        if (effectiveCooldown > 0) {
            int cooldownRemaining = cooldownService.getCooldownRemaining(COMMAND_NAME, playerId);
            if (cooldownRemaining > 0) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("onCooldown", "seconds", String.valueOf(cooldownRemaining)), "#FF5555"));
                return;
            }
        }

        // Get player's current position
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("couldNotGetPosition"), "#FF5555"));
            return;
        }

        Vector3d pos = transform.getPosition();
        int blockX = (int) Math.floor(pos.x);
        int blockZ = (int) Math.floor(pos.z);

        // Get chunk at player's position
        long chunkIndex = ChunkUtil.indexChunkFromBlock(blockX, blockZ);
        WorldChunk chunk = world.getChunk(chunkIndex);
        if (chunk == null) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("topChunkNotLoaded"), "#FF5555"));
            return;
        }

        // Find highest solid block from top down
        Integer topY = findHighestSolidBlock(chunk, blockX, blockZ);
        if (topY == null) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("topNoGround"), "#FF5555"));
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

        // Use safe teleport with PlayerRef for fresh refs at teleport time
        TeleportUtil.safeTeleport(world, world, targetPos, targetRotation, player,
            () -> {
                ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("topTeleported"), "#55FF55"));
                
                // Set cooldown after successful teleport
                if (effectiveCooldown > 0) {
                    cooldownService.setCooldown(COMMAND_NAME, playerId, effectiveCooldown);
                }
            },
            () -> {
                ctx.sendMessage(MessageFormatter.formatWithFallback("&cTeleport failed - chunk could not be loaded.", "#FF5555"));
            }
        );
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
