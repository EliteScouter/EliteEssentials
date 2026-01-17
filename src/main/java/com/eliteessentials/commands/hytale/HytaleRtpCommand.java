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
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
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

import java.lang.reflect.Method;
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

        // Get player's current position for /back
        TransformComponent transform = (TransformComponent) store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            ctx.sendMessage(Message.raw(configManager.getMessage("rtpCouldNotDeterminePosition")).color("#FF5555"));
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
            ctx.sendMessage(Message.raw("Searching for a safe location...").color("#AAAAAA"));
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
            if (debug) {
                logger.info("[RTP] Failed after " + maxAttempts + " attempts - no safe location found");
            }
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
            // Already loaded - process immediately
            processChunk(ctx, store, ref, player, world, playerId, centerX, centerZ, 
                        currentLoc, rtpConfig, attempt, targetX, targetZ, chunk);
        } else {
            // Chunk not loaded - load it async and wait for it to be fully ready
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
                    // Try next location on game thread
                    world.execute(() -> {
                        tryNextLocation(ctx, store, ref, player, world, playerId, centerX, centerZ, 
                                       currentLoc, rtpConfig, currentAttempt + 1);
                    });
                } else {
                    if (debug) {
                        logger.info("[RTP] Chunk loaded successfully");
                    }
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
        
        // Find the actual highest solid block (like /top command does)
        Integer groundY = findHighestSolidBlock(chunk, blockX, blockZ, rtpConfig.minSurfaceY);
        
        if (groundY == null) {
            if (debug) {
                logger.info("[RTP] No solid ground found at (" + blockX + ", " + blockZ + "), trying next location");
            }
            ctx.sendMessage(Message.raw(configManager.getMessage("rtpSearching")).color("#AAAAAA"));
            tryNextLocation(ctx, store, ref, player, world, playerId, centerX, centerZ, 
                           currentLoc, rtpConfig, attempt + 1);
            return;
        }
        
        if (debug) {
            logger.info("[RTP] Found solid ground at Y=" + groundY);
        }
        
        // Teleport 1 block above the ground (player's feet at groundY + 1)
        double teleportY = groundY + 1;
        
        if (debug) {
            logger.info("[RTP] Final teleport Y: " + teleportY);
            
            // DEBUG: Inspect blocks at and around the teleport location
            debugBlocksAtLocation(chunk, blockX, (int) teleportY, blockZ);
        }
        
        // SAFETY CHECK: Verify location is safe (no water/lava)
        if (!isSafeLocation(chunk, blockX, (int) teleportY, blockZ, debug)) {
            if (debug) {
                logger.info("[RTP] Location rejected - unsafe (water/lava detected)");
            }
            ctx.sendMessage(Message.raw(configManager.getMessage("rtpSearching")).color("#AAAAAA"));
            
            // Try next location
            tryNextLocation(ctx, store, ref, player, world, playerId, centerX, centerZ, 
                           currentLoc, rtpConfig, attempt + 1);
            return;
        }
        
        if (debug) {
            logger.info("[RTP] Location verified as safe");
        }
        
        executeTeleport(ctx, store, ref, world, playerId, currentLoc, rtpConfig, 
                       targetX, teleportY, targetZ);
    }
    
    /**
     * Finds the highest solid block at the given X/Z position.
     * Scans from top to bottom like the /top command.
     * 
     * @return Y coordinate of highest solid block, or null if none found
     */
    private Integer findHighestSolidBlock(WorldChunk chunk, int x, int z, int minY) {
        // Start from a reasonable max height and scan down
        for (int y = 255; y >= minY; y--) {
            try {
                BlockType blockType = chunk.getBlockType(x, y, z);
                if (blockType != null && blockType.getMaterial() == BlockMaterial.Solid) {
                    return y;
                }
            } catch (Exception e) {
                // Continue scanning if we hit an error
            }
        }
        return null;
    }
    
    /**
     * Check if a location is safe for teleportation (no water/lava).
     * Uses the chunk's getFluidId method to detect fluids.
     * 
     * FluidId 0 = No fluid (safe)
     * FluidId 6 = Lava (unsafe)
     * FluidId 7 = Water (unsafe)
     * 
     * @return true if safe, false if unsafe (has water/lava)
     */
    private boolean isSafeLocation(WorldChunk chunk, int x, int y, int z, boolean debug) {
        try {
            // Use reflection to call getFluidId
            Method getFluidIdMethod = chunk.getClass().getMethod("getFluidId", int.class, int.class, int.class);
            
            // Check a wider vertical range to catch lava above water, etc.
            // Check from 2 blocks below to 3 blocks above the teleport point
            for (int yOffset = -2; yOffset <= 3; yOffset++) {
                int checkY = y + yOffset;
                if (checkY < 0 || checkY >= 256) continue;
                
                Object fluidIdObj = getFluidIdMethod.invoke(chunk, x, checkY, z);
                if (fluidIdObj instanceof Integer) {
                    int fluidId = (Integer) fluidIdObj;
                    
                    // FluidId 0 = no fluid (safe)
                    // FluidId 6 = lava (unsafe)
                    // FluidId 7 = water (unsafe)
                    if (fluidId == 6 || fluidId == 7) {
                        if (debug) {
                            String fluidType = (fluidId == 6) ? "LAVA" : "WATER";
                            logger.info("[RTP-SAFETY] " + fluidType + " detected at Y" + (yOffset >= 0 ? "+" : "") + yOffset + " (" + checkY + "): FluidId=" + fluidId);
                        }
                        return false; // Unsafe - has lava or water in the area
                    }
                }
            }
            
            // Also check horizontally adjacent blocks at the teleport level
            // This catches pools/lakes next to the spawn point
            int[][] offsets = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] offset : offsets) {
                int checkX = x + offset[0];
                int checkZ = z + offset[1];
                
                Object fluidIdObj = getFluidIdMethod.invoke(chunk, checkX, y, checkZ);
                if (fluidIdObj instanceof Integer) {
                    int fluidId = (Integer) fluidIdObj;
                    if (fluidId == 6 || fluidId == 7) {
                        if (debug) {
                            String fluidType = (fluidId == 6) ? "LAVA" : "WATER";
                            logger.info("[RTP-SAFETY] " + fluidType + " detected adjacent: FluidId=" + fluidId);
                        }
                        return false; // Unsafe - fluid right next to spawn
                    }
                }
            }
            
            return true; // Safe - no dangerous fluids detected
            
        } catch (Exception e) {
            if (debug) {
                logger.warning("[RTP-SAFETY] Could not check fluids (assuming safe): " + e.getMessage());
            }
            // If we can't check fluids, assume safe (fallback to old behavior)
            return true;
        }
    }
    
    /**
     * Debug helper to inspect blocks at a location.
     * This helps us discover BlockMaterial values for water, lava, and other blocks.
     */
    private void debugBlocksAtLocation(WorldChunk chunk, int worldX, int worldY, int worldZ) {
        logger.info("[RTP-DEBUG] ========== Block Analysis at (" + worldX + ", " + worldY + ", " + worldZ + ") ==========");
        
        // Get the height map value for comparison
        int localX = worldX & 15;
        int localZ = worldZ & 15;
        try {
            short heightMapY = chunk.getHeight(localX, localZ);
            logger.info("[RTP-DEBUG] Height map says surface is at Y=" + heightMapY);
        } catch (Exception e) {
            logger.info("[RTP-DEBUG] Could not read height map: " + e.getMessage());
        }
        
        // Check blocks in a vertical column (wider range to catch surface)
        for (int yOffset = -5; yOffset <= 5; yOffset++) {
            int checkY = worldY + yOffset;
            if (checkY < 0 || checkY >= 256) continue;
            
            try {
                BlockType blockType = chunk.getBlockType(worldX, checkY, worldZ);
                
                String position = "Y" + (yOffset >= 0 ? "+" : "") + yOffset + " (" + checkY + ")";
                
                if (blockType == null) {
                    logger.info("[RTP-DEBUG] " + position + ": NULL (air or unloaded)");
                } else {
                    // Get all available information about the block
                    String blockInfo = position + ": ";
                    
                    // Try to get block ID/name
                    try {
                        String id = blockType.getId();
                        blockInfo += "ID='" + id + "' ";
                        
                        // Flag water/lava specifically
                        if (id.toLowerCase().contains("water")) {
                            blockInfo += "[WATER!] ";
                        }
                        if (id.toLowerCase().contains("lava")) {
                            blockInfo += "[LAVA!] ";
                        }
                    } catch (Exception e) {
                        blockInfo += "ID=<error> ";
                    }
                    
                    // Get material type
                    try {
                        BlockMaterial material = blockType.getMaterial();
                        blockInfo += "Material=" + material;
                        
                        // Check if it's solid (we know this works from /top command)
                        if (material == BlockMaterial.Solid) {
                            blockInfo += " [SOLID]";
                        } else if (material == BlockMaterial.Empty) {
                            blockInfo += " [EMPTY/AIR]";
                        } else {
                            blockInfo += " [" + material.name() + "]";
                        }
                    } catch (Exception e) {
                        blockInfo += "Material=<error>";
                    }
                    
                    // Try to check if it's a fluid using other methods
                    try {
                        // Check class name for clues
                        String className = blockType.getClass().getSimpleName();
                        if (className.toLowerCase().contains("fluid") || 
                            className.toLowerCase().contains("water") || 
                            className.toLowerCase().contains("lava")) {
                            blockInfo += " Class=" + className + " [FLUID CLASS!]";
                        }
                    } catch (Exception e) {
                        // Ignore
                    }
                    
                    logger.info("[RTP-DEBUG] " + blockInfo);
                }
            } catch (Exception e) {
                logger.info("[RTP-DEBUG] Y" + (yOffset >= 0 ? "+" : "") + yOffset + ": ERROR - " + e.getMessage());
            }
        }
        
        // Check the actual surface level from height map
        try {
            short surfaceY = chunk.getHeight(localX, localZ);
            logger.info("[RTP-DEBUG] --- Checking surface level (Y=" + surfaceY + ") ---");
            
            for (int yOffset = -2; yOffset <= 2; yOffset++) {
                int checkY = surfaceY + yOffset;
                if (checkY < 0 || checkY >= 256) continue;
                
                BlockType blockType = chunk.getBlockType(worldX, checkY, worldZ);
                if (blockType != null) {
                    String id = blockType.getId();
                    String material = blockType.getMaterial().toString();
                    logger.info("[RTP-DEBUG] Surface" + (yOffset >= 0 ? "+" : "") + yOffset + " (" + checkY + "): ID='" + id + "' Material=" + material);
                }
            }
        } catch (Exception e) {
            logger.info("[RTP-DEBUG] Could not check surface level: " + e.getMessage());
        }
        
        // Also check horizontally for nearby fluids
        logger.info("[RTP-DEBUG] --- Checking adjacent blocks (horizontal at Y=" + worldY + ") ---");
        int[][] offsets = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        String[] directions = {"East", "West", "South", "North"};
        
        for (int i = 0; i < offsets.length; i++) {
            int checkX = worldX + offsets[i][0];
            int checkZ = worldZ + offsets[i][1];
            
            try {
                BlockType blockType = chunk.getBlockType(checkX, worldY, checkZ);
                if (blockType != null) {
                    String id = "unknown";
                    String material = "unknown";
                    
                    try {
                        id = blockType.getId();
                    } catch (Exception e) {
                        // Ignore
                    }
                    
                    try {
                        material = blockType.getMaterial().toString();
                    } catch (Exception e) {
                        // Ignore
                    }
                    
                    String flag = "";
                    if (id.toLowerCase().contains("water")) flag = " [WATER!]";
                    if (id.toLowerCase().contains("lava")) flag = " [LAVA!]";
                    
                    logger.info("[RTP-DEBUG] " + directions[i] + ": ID='" + id + "' Material=" + material + flag);
                }
            } catch (Exception e) {
                // Ignore - might be outside chunk bounds
            }
        }
        
        // Try to check fluid section if it exists
        logger.info("[RTP-DEBUG] --- Attempting to check fluid data ---");
        try {
            // This is exploratory - we don't know if these methods exist
            logger.info("[RTP-DEBUG] Chunk class: " + chunk.getClass().getName());
            
            // Look for fluid-related methods
            Method[] methods = chunk.getClass().getMethods();
            logger.info("[RTP-DEBUG] Looking for fluid-related methods...");
            for (Method method : methods) {
                String methodName = method.getName().toLowerCase();
                if (methodName.contains("fluid") || methodName.contains("water") || methodName.contains("lava")) {
                    logger.info("[RTP-DEBUG] Found method: " + method.getName() + " returns " + method.getReturnType().getSimpleName());
                }
            }
            
            // Try to call getFluidId - this is the key method!
            try {
                Method getFluidIdMethod = chunk.getClass().getMethod("getFluidId", int.class, int.class, int.class);
                Object fluidIdResult = getFluidIdMethod.invoke(chunk, worldX, worldY, worldZ);
                logger.info("[RTP-DEBUG] getFluidId(" + worldX + ", " + worldY + ", " + worldZ + ") = " + fluidIdResult);
                
                // Also check the surface level
                short surfaceY = chunk.getHeight(localX, localZ);
                Object surfaceFluidId = getFluidIdMethod.invoke(chunk, worldX, (int)surfaceY, worldZ);
                logger.info("[RTP-DEBUG] getFluidId at surface (Y=" + surfaceY + ") = " + surfaceFluidId);
                
                // Check a few blocks around the teleport Y
                for (int yOffset = -2; yOffset <= 2; yOffset++) {
                    int checkY = worldY + yOffset;
                    if (checkY >= 0 && checkY < 256) {
                        Object fluidId = getFluidIdMethod.invoke(chunk, worldX, checkY, worldZ);
                        if (fluidId != null && !fluidId.equals(0)) {
                            logger.info("[RTP-DEBUG] FLUID DETECTED at Y" + (yOffset >= 0 ? "+" : "") + yOffset + " (" + checkY + "): FluidId=" + fluidId);
                        }
                    }
                }
            } catch (NoSuchMethodException e) {
                logger.info("[RTP-DEBUG] No getFluidId(x,y,z) method found");
            } catch (Exception e) {
                logger.info("[RTP-DEBUG] Error calling getFluidId: " + e.getMessage());
            }
            
            // Try to call getFluidLevel
            try {
                Method getFluidLevelMethod = chunk.getClass().getMethod("getFluidLevel", int.class, int.class, int.class);
                Object fluidLevelResult = getFluidLevelMethod.invoke(chunk, worldX, worldY, worldZ);
                logger.info("[RTP-DEBUG] getFluidLevel(" + worldX + ", " + worldY + ", " + worldZ + ") = " + fluidLevelResult);
            } catch (NoSuchMethodException e) {
                logger.info("[RTP-DEBUG] No getFluidLevel(x,y,z) method found");
            } catch (Exception e) {
                logger.info("[RTP-DEBUG] Error calling getFluidLevel: " + e.getMessage());
            }
            
        } catch (Exception e) {
            logger.info("[RTP-DEBUG] Could not inspect chunk class: " + e.getMessage());
        }
        
        logger.info("[RTP-DEBUG] ========================================");
    }
    
    private void executeTeleport(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref,
                                  World world, UUID playerId, Location currentLoc,
                                  PluginConfig.RtpConfig rtpConfig,
                                  double teleportX, double teleportY, double teleportZ) {
        boolean debug = configManager.isDebugEnabled();
        
        if (debug) {
            logger.info("[RTP] ========== EXECUTING TELEPORT ==========");
            logger.info("[RTP] Teleporting to: " + String.format("%.1f, %.1f, %.1f", teleportX, teleportY, teleportZ));
            logger.info("[RTP] From: " + String.format("%.1f, %.1f, %.1f", currentLoc.getX(), currentLoc.getY(), currentLoc.getZ()));
            double distance = Math.sqrt(
                Math.pow(teleportX - currentLoc.getX(), 2) + 
                Math.pow(teleportZ - currentLoc.getZ(), 2)
            );
            logger.info("[RTP] Distance traveled: " + String.format("%.1f", distance) + " blocks");
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
            
            if (debug) {
                logger.info("[RTP] Applied " + invulnerabilitySeconds + "s invulnerability");
            }
            
            scheduler.schedule(() -> {
                world.execute(() -> {
                    try {
                        store.removeComponent(ref, Invulnerable.getComponentType());
                        if (debug) {
                            logger.info("[RTP] Removed invulnerability");
                        }
                    } catch (Exception e) {
                        // Ignore - component might already be removed or player disconnected
                    }
                });
            }, invulnerabilitySeconds, TimeUnit.SECONDS);
        }
        
        String location = String.format("%.0f, %.0f, %.0f", teleportX, teleportY, teleportZ);
        ctx.sendMessage(Message.raw(configManager.getMessage("rtpTeleported", "location", location)).color("#55FF55"));
        
        rtpService.setCooldown(playerId);
        
        if (debug) {
            logger.info("[RTP] Teleport complete, cooldown set");
            logger.info("[RTP] ========================================");
        }
    }
}
