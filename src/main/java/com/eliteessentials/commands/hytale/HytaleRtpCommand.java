package com.eliteessentials.commands.hytale;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.config.PluginConfig;
import com.eliteessentials.model.Location;
import com.eliteessentials.permissions.Permissions;
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
import com.hypixel.hytale.server.core.modules.entity.component.Invulnerable;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.UUID;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Command: /rtp
 * Teleports the player to a random location within the configured range.
 * 
 * Permissions:
 * - eliteessentials.command.rtp - Use /rtp command
 * - eliteessentials.bypass.warmup.rtp - Skip warmup
 * - eliteessentials.bypass.cooldown.rtp - Skip cooldown
 */
public class HytaleRtpCommand extends AbstractPlayerCommand {

    private static final Logger logger = Logger.getLogger("EliteEssentials");
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private static final String COMMAND_NAME = "rtp";
    
    private final RtpService rtpService;
    private final BackService backService;
    private final ConfigManager configManager;
    private final WarmupService warmupService;

    public HytaleRtpCommand(RtpService rtpService, BackService backService, ConfigManager configManager, WarmupService warmupService) {
        super(COMMAND_NAME, "Teleport to a random location");
        this.rtpService = rtpService;
        this.backService = backService;
        this.configManager = configManager;
        this.warmupService = warmupService;
        
        // Permission check handled in execute() via CommandPermissionUtil
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
        
        // Check permission and enabled state
        if (!CommandPermissionUtil.canExecute(ctx, player, Permissions.RTP, rtpConfig.enabled)) {
            return;
        }
        
        // Check if player already has a warmup in progress
        if (warmupService.hasActiveWarmup(playerId)) {
            ctx.sendMessage(Message.raw(configManager.getMessage("teleportInProgress")).color("#FF5555"));
            return;
        }
        
        // Check cooldown (with bypass check)
        if (!CommandPermissionUtil.canBypassCooldown(playerId, COMMAND_NAME)) {
            int cooldownRemaining = rtpService.getCooldownRemaining(playerId);
            if (cooldownRemaining > 0) {
                ctx.sendMessage(Message.raw(configManager.getMessage("onCooldown", "seconds", String.valueOf(cooldownRemaining))).color("#FF5555"));
                return;
            }
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

        // Get effective warmup (check bypass permission)
        int warmupSeconds = CommandPermissionUtil.getEffectiveWarmup(playerId, COMMAND_NAME, rtpConfig.warmupSeconds);
        
        if (configManager.isDebugEnabled()) {
            logger.info("[RTP] Config warmup: " + rtpConfig.warmupSeconds + ", effective: " + warmupSeconds + 
                       ", min: " + rtpConfig.minRange + ", max: " + rtpConfig.maxRange);
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
            warmupService.startWarmup(player, currentPos, warmupSeconds, afterWarmup, COMMAND_NAME, world, store, ref);
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
        
        // Start the async search
        tryNextLocation(ctx, store, ref, player, world, playerId, centerX, centerZ, currentLoc, rtpConfig, 0);
    }
    
    private void tryNextLocation(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref,
                                  PlayerRef player, World world, UUID playerId, 
                                  double centerX, double centerZ, Location currentLoc,
                                  PluginConfig.RtpConfig rtpConfig, int attempt) {
        
        int maxAttempts = rtpConfig.maxAttempts;
        boolean debug = configManager.isDebugEnabled();
        
        if (attempt >= maxAttempts) {
            ctx.sendMessage(Message.raw(configManager.getMessage("rtpFailed", "attempts", String.valueOf(maxAttempts))).color("#FF5555"));
            return;
        }
        
        if (attempt == 0 && debug) {
            logger.info("[RTP] Starting search: minRange=" + rtpConfig.minRange + ", maxRange=" + rtpConfig.maxRange + 
                       ", center=" + String.format("%.1f, %.1f", centerX, centerZ));
        }
        
        Random random = new Random();
        double angle = random.nextDouble() * 2 * Math.PI;
        double distance = rtpConfig.minRange + random.nextDouble() * (rtpConfig.maxRange - rtpConfig.minRange);
        double targetX = centerX + Math.cos(angle) * distance;
        double targetZ = centerZ + Math.sin(angle) * distance;
        
        if (debug) {
            logger.info("[RTP] Attempt " + (attempt + 1) + ": trying " + 
                       String.format("%.1f, %.1f", targetX, targetZ) + 
                       " (distance: " + String.format("%.0f", distance) + ")");
        }
        
        long chunkIndex = ChunkUtil.indexChunkFromBlock(targetX, targetZ);
        
        // Check if already loaded first (fast path)
        WorldChunk chunk = world.getChunkIfLoaded(chunkIndex);
        if (chunk == null) {
            chunk = world.getChunkIfInMemory(chunkIndex);
        }
        
        if (chunk != null) {
            // Already loaded - process immediately
            processChunk(ctx, store, ref, player, world, playerId, centerX, centerZ, 
                        currentLoc, rtpConfig, attempt, targetX, targetZ, chunk);
        } else {
            // Chunk not loaded - load it async and wait for it to be fully ready
            final int currentAttempt = attempt;
            final double finalTargetX = targetX;
            final double finalTargetZ = targetZ;
            
            world.getChunkAsync(chunkIndex).whenComplete((loadedChunk, error) -> {
                if (error != null || loadedChunk == null) {
                    if (debug) {
                        logger.info("[RTP] Failed to load chunk: " + (error != null ? error.getMessage() : "null"));
                    }
                    // Try next location on game thread
                    world.execute(() -> {
                        tryNextLocation(ctx, store, ref, player, world, playerId, centerX, centerZ, 
                                       currentLoc, rtpConfig, currentAttempt + 1);
                    });
                } else {
                    // Chunk loaded - process on game thread
                    world.execute(() -> {
                        processChunk(ctx, store, ref, player, world, playerId, centerX, centerZ, 
                                    currentLoc, rtpConfig, currentAttempt, finalTargetX, finalTargetZ, loadedChunk);
                    });
                }
            });
        }
    }
    
    private void processChunk(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref,
                               PlayerRef player, World world, UUID playerId,
                               double centerX, double centerZ, Location currentLoc,
                               PluginConfig.RtpConfig rtpConfig, int attempt,
                               double targetX, double targetZ, WorldChunk chunk) {
        boolean debug = configManager.isDebugEnabled();
        int blockX = MathUtil.floor(targetX);
        int blockZ = MathUtil.floor(targetZ);
        
        // Try to find safe Y using chunk's height map
        Integer safeY = null;
        
        try {
            // Try getHeight with local coordinates
            int localX = blockX & 15;
            int localZ = blockZ & 15;
            short height = chunk.getHeight(localX, localZ);
            
            if (debug) {
                logger.info("[RTP] Chunk height at world(" + blockX + ", " + blockZ + ") local(" + localX + ", " + localZ + ") = " + height);
            }
            
            // Only use height if it's reasonable (between minSurfaceY and 256)
            if (height >= rtpConfig.minSurfaceY && height < 256) {
                safeY = (int) height;
            }
        } catch (Exception e) {
            if (debug) {
                logger.info("[RTP] getHeight failed: " + e.getMessage());
            }
        }
        
        // If getHeight didn't give a valid result, use player's current Y as reference
        // This assumes the player is standing on valid ground
        if (safeY == null) {
            safeY = (int) currentLoc.getY();
            if (debug) {
                logger.info("[RTP] Using player's current Y as fallback: " + safeY);
            }
        }
        
        // Ensure we're above minimum surface level
        if (safeY < rtpConfig.minSurfaceY) {
            safeY = rtpConfig.minSurfaceY;
        }
        
        // Teleport 2 blocks above the ground
        double teleportY = safeY + 2;
        
        if (debug) {
            logger.info("[RTP] Final teleport Y: " + teleportY);
        }
        
        executeTeleport(ctx, store, ref, world, playerId, currentLoc, rtpConfig, 
                       targetX, teleportY, targetZ);
    }
    
    private void executeTeleport(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref,
                                  World world, UUID playerId, Location currentLoc,
                                  PluginConfig.RtpConfig rtpConfig,
                                  double teleportX, double teleportY, double teleportZ) {
        boolean debug = configManager.isDebugEnabled();
        
        if (debug) {
            logger.info("[RTP] Teleporting to: " + String.format("%.1f, %.1f, %.1f", teleportX, teleportY, teleportZ));
        }
        
        // Save location for /back
        backService.pushLocation(playerId, currentLoc);
        
        int invulnerabilitySeconds = rtpConfig.invulnerabilitySeconds;
        
        // Create teleport - use putComponent to avoid "already exists" error
        Vector3d targetPos = new Vector3d(teleportX, teleportY, teleportZ);
        Teleport teleport = new Teleport(targetPos, Vector3f.NaN);
        store.putComponent(ref, Teleport.getComponentType(), teleport);
        
        if (invulnerabilitySeconds > 0) {
            // Use putComponent instead of addComponent to handle case where component already exists
            store.putComponent(ref, Invulnerable.getComponentType(), Invulnerable.INSTANCE);
            
            scheduler.schedule(() -> {
                world.execute(() -> {
                    try {
                        store.removeComponent(ref, Invulnerable.getComponentType());
                    } catch (Exception e) {
                        // Ignore - component might already be removed or player disconnected
                    }
                });
            }, invulnerabilitySeconds, TimeUnit.SECONDS);
        }
        
        String location = String.format("%.0f, %.0f, %.0f", teleportX, teleportY, teleportZ);
        ctx.sendMessage(Message.raw(configManager.getMessage("rtpTeleported", "location", location)).color("#55FF55"));
        
        rtpService.setCooldown(playerId);
    }
}
