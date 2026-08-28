package com.eliteessentials.commands.hytale;

import com.eliteessentials.api.EconomyAPI;
import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.config.PluginConfig;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.permissions.PermissionService;
import com.eliteessentials.services.CooldownService;
import com.eliteessentials.services.CostService;
import com.eliteessentials.services.FlyService;
import com.eliteessentials.util.CommandPermissionUtil;
import com.eliteessentials.util.MessageFormatter;
import com.eliteessentials.util.PlayerSuggestionProvider;
import com.eliteessentials.util.WorldBlacklistUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import org.joml.Vector3d;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.protocol.SavedMovementStates;
import com.hypixel.hytale.protocol.packets.player.SetMovementStates;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.eliteessentials.commands.base.EliteCommandBase;
import com.hypixel.hytale.protocol.FlyMode;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.logging.Logger;
import com.eliteessentials.util.CommandSpyUtil;

/**
 * Command: /fly [player]
 * Toggles flight mode for self or a target player.
 * Can be executed from console to toggle flight on a specific player.
 * 
 * Usage:
 *   /fly           - Toggle flight on self (player only)
 *   /fly <player>  - Toggle flight on target (admin/console)
 * 
 * Permissions:
 * - eliteessentials.command.misc.fly - Use /fly command
 * - eliteessentials.command.misc.fly.bypass.cooldown - Skip cooldown
 * - eliteessentials.command.misc.fly.cooldown.<seconds> - Set specific cooldown
 */
public class HytaleFlyCommand extends EliteCommandBase {

    private static final String COMMAND_NAME = "fly";
    private static final int MAX_HEIGHT = 256;
    private static final Logger logger = Logger.getLogger("EliteEssentials");

    private final ConfigManager configManager;
    private final CooldownService cooldownService;
    private final CostService costService;
    private final FlyService flyService;

    public HytaleFlyCommand(ConfigManager configManager, CooldownService cooldownService,
                            CostService costService, FlyService flyService) {
        super(COMMAND_NAME, "Toggle flight mode");
        this.configManager = configManager;
        this.cooldownService = cooldownService;
        this.costService = costService;
        this.flyService = flyService;
        setAllowsExtraArguments(true);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void executeSync(@Nonnull CommandContext ctx) {
        CommandSpyUtil.notify(ctx);
        PluginConfig.FlyConfig flyConfig = configManager.getConfig().fly;

        String rawInput = ctx.getInputString().trim();
        String[] parts = rawInput.split("\\s+", 3);

        Object sender = ctx.sender();
        boolean isConsoleSender = !(sender instanceof PlayerRef) && !(sender instanceof Player);

        PlayerRef senderPlayerRef = null;
        if (sender instanceof PlayerRef) {
            senderPlayerRef = (PlayerRef) sender;
        } else if (sender instanceof Player player) {
            @SuppressWarnings("removal")
            UUID playerUuid = player.getUuid();
            senderPlayerRef = playerUuid != null ? Universe.get().getPlayer(playerUuid) : null;
        }

        if (isConsoleSender && parts.length < 2) {
            ctx.sendMessage(Message.raw("Console usage: /fly <player>").color("#FF5555"));
            return;
        }

        boolean isTargetingOther = (parts.length >= 2 && !parts[1].isEmpty());
        PlayerRef targetPlayer;

        if (isTargetingOther) {
            if (!isConsoleSender) {
                if (senderPlayerRef == null) {
                    ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("noPermission"), "#FF5555"));
                    return;
                }
                if (!CommandPermissionUtil.canExecuteAdmin(ctx, senderPlayerRef, Permissions.FLY, flyConfig.enabled)) {
                    return;
                }
            }
            String targetName = parts[1];
            targetPlayer = PlayerSuggestionProvider.findPlayer(targetName);
            if (targetPlayer == null) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("playerNotFound", "player", targetName), "#FF5555"));
                return;
            }
        } else {
            if (senderPlayerRef == null) {
                ctx.sendMessage(Message.raw("Could not determine player.").color("#FF5555"));
                return;
            }
            World senderWorld = findPlayerWorld(senderPlayerRef);
            if (senderWorld != null && WorldBlacklistUtil.isWorldBlacklisted(senderWorld.getName(), flyConfig.blacklistedWorlds)) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("commandBlacklistedWorld"), "#FF5555"));
                return;
            }
            if (!CommandPermissionUtil.canExecuteAdmin(ctx, senderPlayerRef, Permissions.FLY, flyConfig.enabled)) {
                return;
            }
            targetPlayer = senderPlayerRef;
        }

        UUID targetId = targetPlayer.getUuid();

        // Cooldown (only for self-use)
        int effectiveCooldown = 0;
        if (!isTargetingOther) {
            effectiveCooldown = PermissionService.get().getCommandCooldown(targetId, COMMAND_NAME, flyConfig.cooldownSeconds);
            if (effectiveCooldown > 0) {
                int cooldownRemaining = cooldownService.getCooldownRemaining(COMMAND_NAME, targetId);
                if (cooldownRemaining > 0) {
                    ctx.sendMessage(MessageFormatter.formatWithFallback(
                        configManager.getMessage("onCooldown", "seconds", String.valueOf(cooldownRemaining)), "#FF5555"));
                    return;
                }
            }
        }

        // Cost per minute: only for self-use, not when admin/console targets another player
        double costPerMinute = flyConfig.costPerMinute;
        int durationSeconds = flyConfig.costPerMinuteDurationSeconds;
        boolean useCostPerMinute = costPerMinute > 0 && EconomyAPI.isEnabled();
        boolean bypassCostPerMinute = !isTargetingOther && costService.canBypassCost(targetId, COMMAND_NAME);
        boolean applyCostPerMinuteToPlayer = useCostPerMinute && !isTargetingOther && !bypassCostPerMinute;
        if (applyCostPerMinuteToPlayer && senderPlayerRef != null) {
            if (!costService.chargeIfNeeded(ctx, senderPlayerRef, COMMAND_NAME, costPerMinute)) {
                return;
            }
        }

        World targetWorld = findPlayerWorld(targetPlayer);
        if (targetWorld == null) {
            ctx.sendMessage(Message.raw("Could not determine player's world.").color("#FF5555"));
            return;
        }

        final int finalEffectiveCooldown = effectiveCooldown;
        final boolean finalIsTargetingOther = isTargetingOther;
        final PlayerRef finalTarget = targetPlayer;
        final boolean applyTimerAndTimedMessage = applyCostPerMinuteToPlayer;

        targetWorld.execute(() -> {
            Ref<EntityStore> ref = finalTarget.getReference();
            if (ref == null || !ref.isValid()) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("playerNotFound", "player", finalTarget.getUsername()), "#FF5555"));
                return;
            }
            Store<EntityStore> store = ref.getStore();

            MovementManager movementManager = store.getComponent(ref, MovementManager.getComponentType());
            if (movementManager == null) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("flyFailed"), "#FF5555"));
                return;
            }

            var settings = movementManager.getSettings();
            boolean newState = settings.fly == FlyMode.Disabled;
            settings.fly = newState ? FlyMode.Allowed : FlyMode.Disabled;
            movementManager.update(finalTarget.getPacketHandler());

            String suffix = finalIsTargetingOther ? " &7(for " + finalTarget.getUsername() + ")" : "";
            if (newState) {
                if (applyTimerAndTimedMessage) {
                    String timeStr = durationSeconds == 60 ? "1 minute" : durationSeconds + " seconds";
                    ctx.sendMessage(MessageFormatter.formatWithFallback(
                        configManager.getMessage("flyEnabledTimed", "time", timeStr) + suffix, "#55FF55"));
                    flyService.scheduleExpiry(targetId, durationSeconds);
                } else {
                    ctx.sendMessage(MessageFormatter.formatWithFallback(
                        configManager.getMessage("flyEnabled") + suffix, "#55FF55"));
                }
                if (finalIsTargetingOther) {
                    finalTarget.sendMessage(MessageFormatter.formatWithFallback(
                        configManager.getMessage("flyEnabled"), "#55FF55"));
                }
            } else {
                flyService.cancelExpiry(targetId);
                ctx.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("flyDisabled") + suffix, "#FFAA00"));
                if (finalIsTargetingOther) {
                    finalTarget.sendMessage(MessageFormatter.formatWithFallback(
                        configManager.getMessage("flyDisabled"), "#FFAA00"));
                }

                // Stop flying if currently in the air
                MovementStatesComponent movementStatesComponent = store.getComponent(ref, MovementStatesComponent.getComponentType());
                if (movementStatesComponent != null) {
                    MovementStates movementStates = movementStatesComponent.getMovementStates();
                    if (movementStates.flying) {
                        movementStates.flying = false;
                        finalTarget.getPacketHandler().writeNoCache(new SetMovementStates(new SavedMovementStates(false)));
                    }
                }

                // Safe landing: teleport player to ground to prevent fall damage
                safeLandPlayer(store, ref, finalTarget, targetWorld);
            }

            if (finalEffectiveCooldown > 0) {
                cooldownService.setCooldown(COMMAND_NAME, targetId, finalEffectiveCooldown);
            }
        });
    }

    /**
     * Teleports the player to the highest solid block below them to prevent fall damage
     * when flight is disabled. Uses the same ground detection as /top.
     */
    private void safeLandPlayer(Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef player, World world) {
        try {
            TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
            if (transform == null) return;

            Vector3d pos = transform.getPosition();
            int blockX = (int) Math.floor(pos.x);
            int blockZ = (int) Math.floor(pos.z);

            long chunkIndex = ChunkUtil.indexChunkFromBlock(blockX, blockZ);
            WorldChunk chunk = world.getChunk(chunkIndex);
            if (chunk == null) return;

            Integer groundY = findHighestSolidBlock(chunk, blockX, blockZ, (int) pos.y);
            if (groundY == null) return;

            // Only teleport if player is above the ground (would actually fall)
            double targetY = groundY + 1;
            if (pos.y - targetY < 3.0) return;

            double centerX = Math.floor(pos.x) + 0.5;
            double centerZ = Math.floor(pos.z) + 0.5;
            Vector3d targetPos = new Vector3d(centerX, targetY, centerZ);
            Rotation3f targetRot = new Rotation3f(0, 0, 0);

            // Preserve player's current yaw
            HeadRotation headRotation =
                store.getComponent(ref, HeadRotation.getComponentType());
            if (headRotation != null) {
                targetRot = new Rotation3f(0, headRotation.getRotation().y, 0);
            }

            Teleport teleport = Teleport.createForPlayer(world, targetPos, targetRot);
            store.addComponent(ref, Teleport.getComponentType(), teleport);
        } catch (Exception e) {
            logger.warning("[Fly] Safe landing failed: " + e.getMessage());
        }
    }

    /**
     * Finds the highest solid block at the given X/Z position, starting from the player's Y level downward.
     */
    private Integer findHighestSolidBlock(WorldChunk chunk, int x, int z, int startY) {
        int scanFrom = Math.min(startY, MAX_HEIGHT);
        for (int y = scanFrom; y >= 0; y--) {
            BlockType blockType = chunk.getBlockType(x, y, z);
            if (blockType != null && blockType.getMaterial() == BlockMaterial.Solid) {
                return y;
            }
        }
        return null;
    }

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
