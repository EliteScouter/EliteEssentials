package com.eliteessentials.commands.hytale;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.config.PluginConfig;
import com.eliteessentials.model.Location;
import com.eliteessentials.services.BackService;
import com.eliteessentials.services.RtpService;
import com.eliteessentials.services.WarmupService;
import com.eliteessentials.util.CommandPermissionUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.util.MathUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.Message;
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
import java.util.Random;
import java.util.logging.Logger;

/**
 * Command: /rtp
 * Teleports the player to a random location within the configured range.
 */
public class HytaleRtpCommand extends AbstractPlayerCommand {

    private static final Logger logger = Logger.getLogger("EliteEssentials");
    
    private final RtpService rtpService;
    private final BackService backService;
    private final ConfigManager configManager;
    private final WarmupService warmupService;

    public HytaleRtpCommand(RtpService rtpService, BackService backService, ConfigManager configManager, WarmupService warmupService) {
        super("rtp", "Teleport to a random location");
        this.rtpService = rtpService;
        this.backService = backService;
        this.configManager = configManager;
        this.warmupService = warmupService;
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref, 
                          PlayerRef player, World world) {
        UUID playerId = player.getUuid();
        PluginConfig.RtpConfig rtpConfig = configManager.getConfig().rtp;
        
        // Register player for death tracking
        com.eliteessentials.EliteEssentials plugin = com.eliteessentials.EliteEssentials.getInstance();
        if (plugin != null && plugin.getDeathTrackingService() != null) {
            plugin.getDeathTrackingService().trackPlayer(playerId);
        }
        
        // Check if command is enabled (disabled = OP only)
        if (!CommandPermissionUtil.canExecute(ctx, player, rtpConfig.enabled)) {
            return;
        }
        
        // Check if player already has a warmup in progress
        if (warmupService.hasActiveWarmup(playerId)) {
            ctx.sendMessage(Message.raw(configManager.getMessage("teleportInProgress")).color("#FF5555"));
            return;
        }
        
        // Check cooldown
        int cooldownRemaining = rtpService.getCooldownRemaining(playerId);
        if (cooldownRemaining > 0) {
            ctx.sendMessage(Message.raw(configManager.getMessage("onCooldown", "seconds", String.valueOf(cooldownRemaining))).color("#FF5555"));
            return;
        }

        // Get player's current position
        TransformComponent transform = (TransformComponent) store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            ctx.sendMessage(Message.raw(configManager.getMessage("rtpCouldNotDeterminePosition")).color("#FF5555"));
            return;
        }
        
        Vector3d currentPos = transform.getPosition();
        double centerX = currentPos.getX();
        double centerZ = currentPos.getZ();

        // Save current location for /back
        HeadRotation headRotation = (HeadRotation) store.getComponent(ref, HeadRotation.getComponentType());
        Vector3f rotation = headRotation != null ? headRotation.getRotation() : new Vector3f(0, 0, 0);
        Location currentLoc = new Location(
            world.getName(),
            currentPos.getX(), currentPos.getY(), currentPos.getZ(),
            rotation.x, rotation.y
        );

        int warmupSeconds = rtpConfig.warmupSeconds;
        if (configManager.isDebugEnabled()) {
            logger.info("[RTP] Config warmup: " + warmupSeconds + ", min: " + rtpConfig.minRange + ", max: " + rtpConfig.maxRange);
        }
        
        // If warmup is configured, do warmup FIRST, then find location
        if (warmupSeconds > 0) {
            ctx.sendMessage(Message.raw(configManager.getMessage("rtpPreparing", "seconds", String.valueOf(warmupSeconds))).color("#FFAA00"));
            
            // Create action that runs AFTER warmup completes
            Runnable afterWarmup = () -> {
                // Now search and teleport
                findAndTeleport(ctx, store, ref, player, world, playerId, centerX, centerZ, currentLoc, rtpConfig);
            };
            
            // Pass current position and world context for movement checking
            warmupService.startWarmup(player, currentPos, warmupSeconds, afterWarmup, "rtp", world, store, ref);
        } else {
            // No warmup, search and teleport immediately
            ctx.sendMessage(Message.raw(configManager.getMessage("rtpSearching")).color("#AAAAAA"));
            findAndTeleport(ctx, store, ref, player, world, playerId, centerX, centerZ, currentLoc, rtpConfig);
        }
    }
    
    private void findAndTeleport(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref,
                                  PlayerRef player, World world, UUID playerId, 
                                  double centerX, double centerZ, Location currentLoc,
                                  PluginConfig.RtpConfig rtpConfig) {
        
        Random random = new Random();
        int minRange = rtpConfig.minRange;
        int maxRange = rtpConfig.maxRange;
        int maxAttempts = rtpConfig.maxAttempts;
        int minSurfaceY = rtpConfig.minSurfaceY;
        boolean debug = configManager.isDebugEnabled();
        
        if (debug) {
            logger.info("[RTP] Starting search: minRange=" + minRange + ", maxRange=" + maxRange + 
                       ", center=" + String.format("%.1f, %.1f", centerX, centerZ));
        }
        
        // Find a valid location
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = minRange + random.nextDouble() * (maxRange - minRange);
            double targetX = centerX + Math.cos(angle) * distance;
            double targetZ = centerZ + Math.sin(angle) * distance;
            
            if (debug) {
                logger.info("[RTP] Attempt " + (attempt + 1) + ": trying " + 
                           String.format("%.1f, %.1f", targetX, targetZ) + 
                           " (distance: " + String.format("%.0f", distance) + ")");
            }
            
            try {
                long chunkIndex = ChunkUtil.indexChunkFromBlock(targetX, targetZ);
                
                // Try to get chunk - first check if loaded, then try async
                WorldChunk chunk = world.getChunkIfLoaded(chunkIndex);
                
                if (chunk == null) {
                    chunk = world.getChunkIfInMemory(chunkIndex);
                }
                
                if (chunk == null) {
                    // Try to load chunk asynchronously and wait briefly
                    try {
                        chunk = world.getChunkAsync(chunkIndex).get(500, java.util.concurrent.TimeUnit.MILLISECONDS);
                    } catch (Exception e) {
                        if (debug) {
                            logger.info("[RTP] Could not load chunk at " + targetX + ", " + targetZ + ": " + e.getMessage());
                        }
                        continue;
                    }
                }
                
                if (chunk == null) {
                    if (debug) {
                        logger.info("[RTP] Chunk null at " + targetX + ", " + targetZ);
                    }
                    continue;
                }
                
                int blockX = MathUtil.floor(targetX);
                int blockZ = MathUtil.floor(targetZ);
                short surfaceHeight = chunk.getHeight(blockX, blockZ);
                
                if (debug) {
                    logger.info("[RTP] Surface height at " + blockX + ", " + blockZ + " = " + surfaceHeight);
                }
                
                if (surfaceHeight >= minSurfaceY) {
                    // Found valid location!
                    final double finalX = targetX;
                    final double finalY = surfaceHeight + 2;
                    final double finalZ = targetZ;
                    
                    if (debug) {
                        logger.info("[RTP] Found valid location: " + String.format("%.1f, %.1f, %.1f", finalX, finalY, finalZ));
                    }
                    
                    // Save location for /back
                    backService.pushLocation(playerId, currentLoc);
                    
                    // Teleport
                    world.execute(() -> {
                        Vector3d targetPos = new Vector3d(finalX, finalY, finalZ);
                        Teleport teleport = new Teleport(targetPos, Vector3f.NaN);
                        store.addComponent(ref, Teleport.getComponentType(), teleport);
                        
                        String location = String.format("%.0f, %.0f, %.0f", finalX, finalY, finalZ);
                        ctx.sendMessage(Message.raw(configManager.getMessage("rtpTeleported", "location", location)).color("#55FF55"));
                    });
                    
                    rtpService.setCooldown(playerId);
                    return;
                } else if (debug) {
                    logger.info("[RTP] Surface too low: " + surfaceHeight + " < " + minSurfaceY);
                }
            } catch (Exception e) {
                if (debug) {
                    logger.warning("[RTP] Error on attempt " + (attempt + 1) + ": " + e.getMessage());
                }
                continue;
            }
        }
        
        ctx.sendMessage(Message.raw(configManager.getMessage("rtpFailed", "attempts", String.valueOf(maxAttempts))).color("#FF5555"));
    }
}
