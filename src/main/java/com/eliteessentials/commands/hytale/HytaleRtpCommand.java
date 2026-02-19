package com.eliteessentials.commands.hytale;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.config.PluginConfig;
import com.eliteessentials.model.Location;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.permissions.PermissionService;
import com.eliteessentials.services.BackService;
import com.eliteessentials.services.RtpService;
import com.eliteessentials.services.WarmupService;
import com.eliteessentials.util.CommandPermissionUtil;
import com.eliteessentials.util.MessageFormatter;
import com.eliteessentials.util.TeleportUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.util.MathUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.Random;
import java.util.logging.Logger;

import javax.annotation.Nonnull;

/**
 * Command: /rtp [player] [world]
 * Teleports a player to a random location within the configured range.
 * 
 * Usage:
 * - /rtp                    - RTP yourself in current world
 * - /rtp <player>           - Admin: RTP player in their current world
 * - /rtp <player> <world>   - Admin: RTP player to a specific world
 * 
 * Can be executed from console for automation (e.g., portal NPCs).
 * 
 * Permissions:
 * - eliteessentials.command.rtp - Use /rtp command (self)
 * - eliteessentials.admin.rtp - RTP other players / use from console
 * - eliteessentials.bypass.warmup.rtp - Skip warmup
 * - eliteessentials.bypass.cooldown.rtp - Skip cooldown
 */
public class HytaleRtpCommand extends CommandBase {

    private static final Logger logger = Logger.getLogger("EliteEssentials");
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
        
        setAllowsExtraArguments(true);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void executeSync(@Nonnull CommandContext ctx) {
        PluginConfig.RtpConfig rtpConfig = configManager.getConfig().rtp;
        
        // Always log that we reached this point (helps diagnose if Hytale is intercepting)
        if (configManager.isDebugEnabled()) {
            logger.info("[RTP] Command executeSync reached");
            logger.info("[RTP] Config - forceWorldEnabled: " + rtpConfig.forceWorldEnabled + ", forceWorld: '" + rtpConfig.forceWorld + "'");
        }
        
        // Parse arguments: /rtp [player] [world]
        String rawInput = ctx.getInputString();
        String[] parts = rawInput.split("\\s+");
        
        String targetPlayerName = null;
        String targetWorldName = null;
        
        if (parts.length >= 2) {
            targetPlayerName = parts[1];
        }
        if (parts.length >= 3) {
            targetWorldName = parts[2];
        }
        
        // Determine if this is a self-RTP or admin RTP
        boolean isSelfRtp = (targetPlayerName == null);
        boolean isAdminRtp = !isSelfRtp;
        
        // Get the sender info - can be PlayerRef OR Player depending on context
        Object sender = ctx.sender();
        boolean isConsoleSender = !(sender instanceof PlayerRef) && !(sender instanceof Player);
        
        // Debug logging (can be removed after fix is confirmed)
        if (configManager.isDebugEnabled()) {
            logger.info("[RTP] Sender: " + (sender != null ? sender.getClass().getName() : "null") + 
                       ", isConsole: " + isConsoleSender + ", isSelfRtp: " + isSelfRtp + 
                       ", input: '" + rawInput + "'");
        }
        
        // If console is running without a target, show usage
        if (isConsoleSender && isSelfRtp) {
            ctx.sendMessage(Message.raw("Console usage: /rtp <player> [world]").color("#FF5555"));
            return;
        }
        
        // Get PlayerRef from sender (handles both PlayerRef and Player types)
        PlayerRef senderPlayerRef = null;
        if (sender instanceof PlayerRef) {
            senderPlayerRef = (PlayerRef) sender;
        } else if (sender instanceof Player) {
            senderPlayerRef = ((Player) sender).getPlayerRef();
        }
        
        // If it's a player running /rtp with no args, that's fine - self RTP
        // If it's a player running /rtp <player>, check admin permission
        
        // Permission check for admin RTP
        if (isAdminRtp) {
            // For console, always allow. For players, check admin permission
            if (!isConsoleSender && senderPlayerRef != null) {
                if (!PermissionService.get().canUseAdminCommand(senderPlayerRef.getUuid(), Permissions.ADMIN_RTP, true)) {
                    ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("noPermission"), "#FF5555"));
                    return;
                }
            }
        }
        
        // Find the target player
        PlayerRef targetPlayer;
        if (isSelfRtp) {
            targetPlayer = senderPlayerRef;
            if (targetPlayer == null) {
                ctx.sendMessage(Message.raw("Could not determine player.").color("#FF5555"));
                return;
            }
        } else {
            targetPlayer = findOnlinePlayer(targetPlayerName);
            if (targetPlayer == null) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("playerNotFound", "player", targetPlayerName), "#FF5555"));
                return;
            }
        }
        
        UUID playerId = targetPlayer.getUuid();
        
        // Register player for death tracking
        com.eliteessentials.EliteEssentials plugin = com.eliteessentials.EliteEssentials.getInstance();
        if (plugin != null && plugin.getDeathTrackingService() != null) {
            plugin.getDeathTrackingService().trackPlayer(playerId);
        }
        
        // Permission check for self-RTP (with cost)
        if (isSelfRtp) {
            if (!CommandPermissionUtil.canExecuteWithCost(ctx, targetPlayer, Permissions.RTP, 
                    rtpConfig.enabled, "rtp", rtpConfig.cost)) {
                return;
            }
        }
        
        // Find the target world
        World targetWorld;
        if (targetWorldName != null) {
            // Admin specified a world
            if (configManager.isDebugEnabled()) {
                logger.info("[RTP] Admin specified world: " + targetWorldName);
            }
            targetWorld = findWorld(targetWorldName);
            if (targetWorld == null) {
                ctx.sendMessage(Message.raw("World '" + targetWorldName + "' not found.").color("#FF5555"));
                return;
            }
        } else if (rtpConfig.forceWorldEnabled && !rtpConfig.forceWorld.isEmpty()) {
            // Force RTP to specific world (only for self-RTP, not admin RTP)
            if (configManager.isDebugEnabled()) {
                logger.info("[RTP] Force world check - enabled: " + rtpConfig.forceWorldEnabled + 
                           ", world: '" + rtpConfig.forceWorld + "', isSelfRtp: " + isSelfRtp);
            }
            if (isSelfRtp) {
                targetWorld = findWorld(rtpConfig.forceWorld);
                if (targetWorld == null) {
                    ctx.sendMessage(Message.raw("Configured RTP world '" + rtpConfig.forceWorld + "' not found. Contact an administrator.").color("#FF5555"));
                    if (configManager.isDebugEnabled()) {
                        logger.severe("[RTP] forceWorld is enabled but world '" + rtpConfig.forceWorld + "' does not exist!");
                    }
                    return;
                }
                if (configManager.isDebugEnabled()) {
                    logger.info("[RTP] Force world enabled - teleporting to: " + rtpConfig.forceWorld);
                }
            } else {
                // Admin RTP without world specified - use player's current world
                if (configManager.isDebugEnabled()) {
                    logger.info("[RTP] Admin RTP without world - using player's current world");
                }
                targetWorld = findPlayerWorld(targetPlayer);
                if (targetWorld == null) {
                    ctx.sendMessage(Message.raw("Could not determine player's world.").color("#FF5555"));
                    return;
                }
            }
        } else {
            // Use player's current world
            if (configManager.isDebugEnabled()) {
                logger.info("[RTP] Using player's current world (force world not enabled or empty)");
            }
            targetWorld = findPlayerWorld(targetPlayer);
            if (targetWorld == null) {
                ctx.sendMessage(Message.raw("Could not determine player's world.").color("#FF5555"));
                return;
            }
        }
        
        // Check if this is a cross-world RTP (player is in different world than target)
        World playerCurrentWorld = findPlayerWorld(targetPlayer);
        boolean isCrossWorld = playerCurrentWorld == null || !playerCurrentWorld.getName().equals(targetWorld.getName());
        
        final World finalTargetWorld = targetWorld;
        final boolean finalIsAdminRtp = isAdminRtp;
        final boolean finalIsCrossWorld = isCrossWorld;
        final World finalPlayerCurrentWorld = playerCurrentWorld;
        
        if (finalIsCrossWorld) {
            // Cross-world RTP - must start on player's current world thread
            if (finalPlayerCurrentWorld == null) {
                ctx.sendMessage(Message.raw("Could not determine player's current world.").color("#FF5555"));
                return;
            }
            finalPlayerCurrentWorld.execute(() -> {
                performCrossWorldRtp(ctx, targetPlayer, playerId, finalTargetWorld, rtpConfig, finalIsAdminRtp);
            });
        } else {
            // Same-world RTP - execute on the target world's thread
            finalTargetWorld.execute(() -> {
                executeRtpOnWorld(ctx, targetPlayer, playerId, finalTargetWorld, rtpConfig, finalIsAdminRtp);
            });
        }
    }
    
    /**
     * Execute RTP logic on the target world's thread.
     * Only called when player is already in the target world.
     */
    private void executeRtpOnWorld(CommandContext ctx, PlayerRef player, UUID playerId, 
                                    World world, PluginConfig.RtpConfig rtpConfig, boolean isAdminRtp) {
        
        // Check if player already has a warmup in progress (skip for admin RTP)
        if (!isAdminRtp && warmupService.hasActiveWarmup(playerId)) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("teleportInProgress"), "#FF5555"));
            return;
        }
        
        // Check cooldown (skip for admin RTP)
        if (!isAdminRtp && !CommandPermissionUtil.canBypassCooldown(playerId, COMMAND_NAME)) {
            int cooldownRemaining = rtpService.getCooldownRemaining(playerId);
            if (cooldownRemaining > 0) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("onCooldown", "seconds", String.valueOf(cooldownRemaining)), "#FF5555"));
                return;
            }
        }
        
        // Get player's store and ref - player should be in this world
        Ref<EntityStore> ref = player.getReference();
        
        if (ref == null || !ref.isValid()) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("rtpCouldNotDeterminePosition"), "#FF5555"));
            return;
        }
        
        Store<EntityStore> store = ref.getStore();
        if (store == null) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("rtpCouldNotDeterminePosition"), "#FF5555"));
            return;
        }

        // Get player's current position for /back
        TransformComponent transform = (TransformComponent) store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("rtpCouldNotDeterminePosition"), "#FF5555"));
            return;
        }
        
        Vector3d currentPos = transform.getPosition();
        
        // Use world center (1, 1) as the center for RTP range calculation
        double centerX = 1.0;
        double centerZ = 1.0;

        // Save current location for /back
        HeadRotation headRotation = (HeadRotation) store.getComponent(ref, HeadRotation.getComponentType());
        Vector3f rotation = headRotation != null ? headRotation.getRotation() : new Vector3f(0, 0, 0);
        Location currentLoc = new Location(
            world.getName(),
            currentPos.getX(), currentPos.getY(), currentPos.getZ(),
            rotation.x, rotation.y
        );

        // Get effective warmup (skip for admin RTP)
        int warmupSeconds = isAdminRtp ? 0 : CommandPermissionUtil.getEffectiveWarmup(playerId, COMMAND_NAME, rtpConfig.warmupSeconds);
        
        // Get world-specific range
        var worldRange = rtpConfig.getRangeForWorld(world.getName());
        
        if (configManager.isDebugEnabled()) {
            logger.info("[RTP] Config warmup: " + rtpConfig.warmupSeconds + ", effective: " + warmupSeconds + 
                       ", min: " + worldRange.minRange + ", max: " + worldRange.maxRange + ", world: " + world.getName() + ", isAdmin: " + isAdminRtp);
        }
        
        // If warmup is configured, do warmup FIRST, then find location
        if (warmupSeconds > 0) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("rtpPreparing", "seconds", String.valueOf(warmupSeconds)), "#FFAA00"));
            
            // Create action that runs AFTER warmup completes
            Runnable afterWarmup = () -> {
                findAndTeleport(ctx, store, ref, player, world, playerId, centerX, centerZ, currentLoc, rtpConfig, isAdminRtp);
            };
            
            warmupService.startWarmup(player, currentPos, warmupSeconds, afterWarmup, COMMAND_NAME, world, store, ref);
        } else {
            // No warmup, search and teleport immediately
            if (!isAdminRtp) {
                ctx.sendMessage(MessageFormatter.formatWithFallback("Searching for a safe location...", "#AAAAAA"));
            }
            findAndTeleport(ctx, store, ref, player, world, playerId, centerX, centerZ, currentLoc, rtpConfig, isAdminRtp);
        }
    }
    
    /**
     * Perform cross-world RTP when player is not in the target world.
     */
    private void performCrossWorldRtp(CommandContext ctx, PlayerRef player, UUID playerId, 
                                       World targetWorld, PluginConfig.RtpConfig rtpConfig, boolean isAdminRtp) {
        double centerX = 1.0;
        double centerZ = 1.0;
        
        // Search for safe location and teleport
        tryNextLocationCrossWorld(ctx, player, targetWorld, playerId, centerX, centerZ, rtpConfig, 0, isAdminRtp);
    }

    
    private void findAndTeleport(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref,
                                  PlayerRef player, World world, UUID playerId, 
                                  double centerX, double centerZ, Location currentLoc,
                                  PluginConfig.RtpConfig rtpConfig, boolean isAdminRtp) {
        
        // Start the async search
        tryNextLocation(ctx, store, ref, player, world, playerId, centerX, centerZ, currentLoc, rtpConfig, 0, isAdminRtp);
    }
    
    private void tryNextLocation(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref,
                                  PlayerRef player, World world, UUID playerId, 
                                  double centerX, double centerZ, Location currentLoc,
                                  PluginConfig.RtpConfig rtpConfig, int attempt, boolean isAdminRtp) {
        
        int maxAttempts = rtpConfig.maxAttempts;
        boolean debug = configManager.isDebugEnabled();
        
        if (attempt >= maxAttempts) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("rtpFailed", "attempts", String.valueOf(maxAttempts)), "#FF5555"));
            if (debug) {
                logger.info("[RTP] Failed after " + maxAttempts + " attempts - no safe location found");
            }
            return;
        }
        
        // Get world-specific range
        var worldRange = rtpConfig.getRangeForWorld(world.getName());
        
        if (attempt == 0 && debug) {
            logger.info("[RTP] Starting search: minRange=" + worldRange.minRange + ", maxRange=" + worldRange.maxRange + 
                       ", world=" + world.getName() + ", center=" + String.format("%.1f, %.1f", centerX, centerZ));
        }
        
        Random random = new Random();
        double angle = random.nextDouble() * 2 * Math.PI;
        double distance = worldRange.minRange + random.nextDouble() * (worldRange.maxRange - worldRange.minRange);
        double targetX = centerX + Math.cos(angle) * distance;
        double targetZ = centerZ + Math.sin(angle) * distance;
        
        if (debug) {
            logger.info("[RTP] Attempt " + (attempt + 1) + "/" + maxAttempts + ": trying " + 
                       String.format("%.1f, %.1f", targetX, targetZ) + 
                       " (distance: " + String.format("%.0f", distance) + 
                       ", angle: " + String.format("%.1f", Math.toDegrees(angle)) + "°)");
        }
        
        long chunkIndex = ChunkUtil.indexChunkFromBlock(targetX, targetZ);
        
        // Check if already loaded first (fast path)
        WorldChunk chunk = world.getChunkIfLoaded(chunkIndex);
        if (chunk == null) {
            chunk = world.getChunkIfInMemory(chunkIndex);
        }
        
        if (chunk != null) {
            if (debug) {
                logger.info("[RTP] Chunk already loaded, processing immediately");
            }
            processChunk(ctx, store, ref, player, world, playerId, centerX, centerZ, 
                        currentLoc, rtpConfig, attempt, targetX, targetZ, chunk, isAdminRtp);
        } else {
            if (debug) {
                logger.info("[RTP] Chunk not loaded, loading asynchronously...");
            }
            
            final int currentAttempt = attempt;
            final double finalTargetX = targetX;
            final double finalTargetZ = targetZ;
            
            world.getChunkAsync(chunkIndex).whenComplete((loadedChunk, error) -> {
                if (error != null || loadedChunk == null) {
                    if (debug) {
                        logger.info("[RTP] Failed to load chunk: " + (error != null ? error.getMessage() : "null"));
                    }
                    world.execute(() -> {
                        tryNextLocation(ctx, store, ref, player, world, playerId, centerX, centerZ, 
                                       currentLoc, rtpConfig, currentAttempt + 1, isAdminRtp);
                    });
                } else {
                    if (debug) {
                        logger.info("[RTP] Chunk loaded successfully");
                    }
                    world.execute(() -> {
                        processChunk(ctx, store, ref, player, world, playerId, centerX, centerZ, 
                                    currentLoc, rtpConfig, currentAttempt, finalTargetX, finalTargetZ, loadedChunk, isAdminRtp);
                    });
                }
            });
        }
    }
    
    /**
     * Cross-world version of tryNextLocation - doesn't require store/ref.
     */
    private void tryNextLocationCrossWorld(CommandContext ctx, PlayerRef player, World world, UUID playerId, 
                                            double centerX, double centerZ, PluginConfig.RtpConfig rtpConfig, 
                                            int attempt, boolean isAdminRtp) {
        
        int maxAttempts = rtpConfig.maxAttempts;
        boolean debug = configManager.isDebugEnabled();
        
        if (attempt >= maxAttempts) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("rtpFailed", "attempts", String.valueOf(maxAttempts)), "#FF5555"));
            return;
        }
        
        // Get world-specific range
        var worldRange = rtpConfig.getRangeForWorld(world.getName());
        
        Random random = new Random();
        double angle = random.nextDouble() * 2 * Math.PI;
        double distance = worldRange.minRange + random.nextDouble() * (worldRange.maxRange - worldRange.minRange);
        double targetX = centerX + Math.cos(angle) * distance;
        double targetZ = centerZ + Math.sin(angle) * distance;
        
        long chunkIndex = ChunkUtil.indexChunkFromBlock(targetX, targetZ);
        
        WorldChunk chunk = world.getChunkIfLoaded(chunkIndex);
        if (chunk == null) {
            chunk = world.getChunkIfInMemory(chunkIndex);
        }
        
        if (chunk != null) {
            processChunkCrossWorld(ctx, player, world, playerId, centerX, centerZ, rtpConfig, attempt, targetX, targetZ, chunk, isAdminRtp);
        } else {
            final int currentAttempt = attempt;
            final double finalTargetX = targetX;
            final double finalTargetZ = targetZ;
            
            world.getChunkAsync(chunkIndex).whenComplete((loadedChunk, error) -> {
                if (error != null || loadedChunk == null) {
                    world.execute(() -> {
                        tryNextLocationCrossWorld(ctx, player, world, playerId, centerX, centerZ, rtpConfig, currentAttempt + 1, isAdminRtp);
                    });
                } else {
                    world.execute(() -> {
                        processChunkCrossWorld(ctx, player, world, playerId, centerX, centerZ, rtpConfig, currentAttempt, finalTargetX, finalTargetZ, loadedChunk, isAdminRtp);
                    });
                }
            });
        }
    }
    
    private void processChunk(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref,
                               PlayerRef player, World world, UUID playerId,
                               double centerX, double centerZ, Location currentLoc,
                               PluginConfig.RtpConfig rtpConfig, int attempt,
                               double targetX, double targetZ, WorldChunk chunk, boolean isAdminRtp) {
        boolean debug = configManager.isDebugEnabled();
        int blockX = MathUtil.floor(targetX);
        int blockZ = MathUtil.floor(targetZ);
        
        Integer groundY = findHighestSolidBlock(chunk, blockX, blockZ, rtpConfig.minSurfaceY);
        
        if (groundY == null) {
            if (debug) {
                logger.info("[RTP] No solid ground found at (" + blockX + ", " + blockZ + "), trying next location");
            }
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("rtpSearching"), "#AAAAAA"));
            tryNextLocation(ctx, store, ref, player, world, playerId, centerX, centerZ, 
                           currentLoc, rtpConfig, attempt + 1, isAdminRtp);
            return;
        }
        
        double teleportY = groundY + 1;
        
        if (!isSafeLocation(chunk, blockX, (int) teleportY, blockZ, debug)) {
            if (debug) {
                logger.info("[RTP] Location rejected - unsafe (water/lava detected)");
            }
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("rtpSearching"), "#AAAAAA"));
            tryNextLocation(ctx, store, ref, player, world, playerId, centerX, centerZ, 
                           currentLoc, rtpConfig, attempt + 1, isAdminRtp);
            return;
        }
        
        executeTeleport(ctx, player, world, playerId, currentLoc, rtpConfig, targetX, teleportY, targetZ, isAdminRtp);
    }
    
    private void processChunkCrossWorld(CommandContext ctx, PlayerRef player, World world, UUID playerId,
                                         double centerX, double centerZ, PluginConfig.RtpConfig rtpConfig, 
                                         int attempt, double targetX, double targetZ, WorldChunk chunk, boolean isAdminRtp) {
        boolean debug = configManager.isDebugEnabled();
        int blockX = MathUtil.floor(targetX);
        int blockZ = MathUtil.floor(targetZ);
        
        Integer groundY = findHighestSolidBlock(chunk, blockX, blockZ, rtpConfig.minSurfaceY);
        
        if (groundY == null) {
            if (debug) {
                logger.info("[RTP] No solid ground found at (" + blockX + ", " + blockZ + "), trying next location");
            }
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("rtpSearching"), "#AAAAAA"));
            tryNextLocationCrossWorld(ctx, player, world, playerId, centerX, centerZ, rtpConfig, attempt + 1, isAdminRtp);
            return;
        }
        
        double teleportY = groundY + 1;
        
        if (!isSafeLocation(chunk, blockX, (int) teleportY, blockZ, debug)) {
            if (debug) {
                logger.info("[RTP] Location rejected - unsafe (water/lava detected)");
            }
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("rtpSearching"), "#AAAAAA"));
            tryNextLocationCrossWorld(ctx, player, world, playerId, centerX, centerZ, rtpConfig, attempt + 1, isAdminRtp);
            return;
        }
        
        executeCrossWorldTeleport(ctx, player, world, playerId, rtpConfig, targetX, teleportY, targetZ, isAdminRtp);
    }

    
    private Integer findHighestSolidBlock(WorldChunk chunk, int x, int z, int minY) {
        for (int y = 255; y >= minY; y--) {
            try {
                BlockType blockType = chunk.getBlockType(x, y, z);
                if (blockType != null && blockType.getMaterial() == BlockMaterial.Solid) {
                    return y;
                }
            } catch (Exception e) {
                // Continue scanning
            }
        }
        return null;
    }
    
    private boolean isSafeLocation(WorldChunk chunk, int x, int y, int z, boolean debug) {
        try {
            Method getFluidIdMethod = chunk.getClass().getMethod("getFluidId", int.class, int.class, int.class);
            
            // Check vertical range
            for (int yOffset = -2; yOffset <= 3; yOffset++) {
                int checkY = y + yOffset;
                if (checkY < 0 || checkY >= 256) continue;
                
                Object fluidIdObj = getFluidIdMethod.invoke(chunk, x, checkY, z);
                if (fluidIdObj instanceof Integer) {
                    int fluidId = (Integer) fluidIdObj;
                    if (fluidId == 6 || fluidId == 7) {
                        if (debug) {
                            String fluidType = (fluidId == 6) ? "LAVA" : "WATER";
                            logger.info("[RTP-SAFETY] " + fluidType + " detected at Y" + (yOffset >= 0 ? "+" : "") + yOffset);
                        }
                        return false;
                    }
                }
            }
            
            // Check adjacent blocks
            int[][] offsets = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] offset : offsets) {
                int checkX = x + offset[0];
                int checkZ = z + offset[1];
                
                Object fluidIdObj = getFluidIdMethod.invoke(chunk, checkX, y, checkZ);
                if (fluidIdObj instanceof Integer) {
                    int fluidId = (Integer) fluidIdObj;
                    if (fluidId == 6 || fluidId == 7) {
                        return false;
                    }
                }
            }
            
            return true;
        } catch (Exception e) {
            return true; // Assume safe if can't check
        }
    }
    
    private void executeTeleport(CommandContext ctx, PlayerRef player,
                                  World world, UUID playerId, Location currentLoc,
                                  PluginConfig.RtpConfig rtpConfig,
                                  double teleportX, double teleportY, double teleportZ, boolean isAdminRtp) {
        boolean debug = configManager.isDebugEnabled();
        
        if (debug) {
            logger.info("[RTP] Teleporting to: " + String.format("%.1f, %.1f, %.1f", teleportX, teleportY, teleportZ));
        }
        
        // Save location for /back
        backService.pushLocation(playerId, currentLoc);
        
        // Get current store/ref and yaw - we're on the world thread so these are valid
        Ref<EntityStore> currentRef = player.getReference();
        if (currentRef == null || !currentRef.isValid()) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("rtpChunkLoadFailed"), "#FF5555"));
            return;
        }
        Store<EntityStore> currentStore = currentRef.getStore();
        if (currentStore == null) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("rtpChunkLoadFailed"), "#FF5555"));
            return;
        }
        
        float currentYaw = 0;
        HeadRotation headRotation = (HeadRotation) currentStore.getComponent(currentRef, HeadRotation.getComponentType());
        currentYaw = headRotation != null ? headRotation.getRotation().y : 0;
        
        Vector3d targetPos = new Vector3d(teleportX, teleportY, teleportZ);
        Vector3f targetRot = new Vector3f(0, currentYaw, 0);
        
        TeleportUtil.safeTeleport(world, world, targetPos, targetRot, currentStore, currentRef,
            () -> {
                String location = String.format("%.0f, %.0f, %.0f", teleportX, teleportY, teleportZ);
                ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("rtpTeleported", "location", location), "#55FF55"));
                
                if (!isAdminRtp) {
                    CommandPermissionUtil.chargeCost(ctx, player, "rtp", rtpConfig.cost);
                    rtpService.setCooldown(playerId);
                }
            },
            () -> {
                ctx.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("rtpChunkLoadFailed"), "#FF5555"));
            }
        );
    }
    
    private void executeCrossWorldTeleport(CommandContext ctx, PlayerRef player, World targetWorld, UUID playerId,
                                            PluginConfig.RtpConfig rtpConfig,
                                            double teleportX, double teleportY, double teleportZ, boolean isAdminRtp) {
        boolean debug = configManager.isDebugEnabled();
        
        if (debug) {
            logger.info("[RTP] Cross-world teleport to " + targetWorld.getName() + " at " + 
                       String.format("%.1f, %.1f, %.1f", teleportX, teleportY, teleportZ));
        }
        
        // Find player's current world to save /back location
        World currentWorld = findPlayerWorld(player);
        if (currentWorld == null) {
            ctx.sendMessage(Message.raw("Could not find player's current world.").color("#FF5555"));
            return;
        }
        
        // First, gather player data on their current world thread
        currentWorld.execute(() -> {
            // Get fresh ref to save current location for /back
            Ref<EntityStore> ref = player.getReference();
            if (ref == null || !ref.isValid()) {
                ctx.sendMessage(Message.raw("Could not find player entity.").color("#FF5555"));
                return;
            }
            
            Store<EntityStore> store = ref.getStore();
            if (store == null) {
                ctx.sendMessage(Message.raw("Could not find player store.").color("#FF5555"));
                return;
            }
            
            // Save current location for /back
            TransformComponent transform = (TransformComponent) store.getComponent(ref, TransformComponent.getComponentType());
            if (transform != null) {
                Vector3d currentPos = transform.getPosition();
                HeadRotation headRotation = (HeadRotation) store.getComponent(ref, HeadRotation.getComponentType());
                Vector3f rotation = headRotation != null ? headRotation.getRotation() : new Vector3f(0, 0, 0);
                Location currentLoc = new Location(
                    currentWorld.getName(),
                    currentPos.getX(), currentPos.getY(), currentPos.getZ(),
                    rotation.x, rotation.y
                );
                backService.pushLocation(playerId, currentLoc);
            }
            
            Vector3d targetPos = new Vector3d(teleportX, teleportY, teleportZ);
            Vector3f targetRot = new Vector3f(0, 0, 0);
            
            // Use PlayerRef-based safeTeleport to get fresh store/ref at teleport time
            TeleportUtil.safeTeleport(currentWorld, targetWorld, targetPos, targetRot, player,
                () -> {
                    String location = String.format("%.0f, %.0f, %.0f", teleportX, teleportY, teleportZ);
                    String worldName = targetWorld.getName();
                    player.sendMessage(MessageFormatter.formatWithFallback(
                        configManager.getMessage("rtpTeleportedWorld", "location", location, "world", worldName), "#55FF55"));
                    
                    if (!isAdminRtp) {
                        CommandPermissionUtil.chargeCost(ctx, player, "rtp", rtpConfig.cost);
                        rtpService.setCooldown(playerId);
                    }
                },
                () -> {
                    player.sendMessage(MessageFormatter.formatWithFallback(
                        configManager.getMessage("rtpChunkLoadFailed"), "#FF5555"));
                }
            );
        });
    }
    
    /**
     * Find an online player by name (case-insensitive).
     */
    private PlayerRef findOnlinePlayer(String name) {
        Universe universe = Universe.get();
        if (universe == null) return null;
        
        for (PlayerRef p : universe.getPlayers()) {
            if (p.getUsername().equalsIgnoreCase(name)) {
                return p;
            }
        }
        return null;
    }
    
    /**
     * Find an online player by UUID.
     */
    private PlayerRef findPlayerByUuid(UUID uuid) {
        Universe universe = Universe.get();
        if (universe == null) return null;
        
        for (PlayerRef p : universe.getPlayers()) {
            if (p.getUuid().equals(uuid)) {
                return p;
            }
        }
        return null;
    }
    
    /**
     * Find a world by name (case-insensitive).
     */
    private World findWorld(String name) {
        Universe universe = Universe.get();
        if (universe == null) return null;
        
        for (var entry : universe.getWorlds().entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }
    
    /**
     * Find which world a player is currently in.
     */
    private World findPlayerWorld(PlayerRef player) {
        Universe universe = Universe.get();
        if (universe == null) return null;
        
        for (var entry : universe.getWorlds().entrySet()) {
            if (entry.getValue().getPlayerRefs().contains(player)) {
                return entry.getValue();
            }
        }
        return null;
    }
}
