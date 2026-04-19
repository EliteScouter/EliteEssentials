package com.eliteessentials.commands.hytale;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.config.PluginConfig;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.permissions.PermissionService;
import com.eliteessentials.services.CooldownService;
import com.eliteessentials.services.GodService;
import com.eliteessentials.util.CommandPermissionUtil;
import com.eliteessentials.util.MessageFormatter;
import com.eliteessentials.util.PlayerSuggestionProvider;
import com.eliteessentials.util.WorldBlacklistUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.Invulnerable;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import com.eliteessentials.util.CommandSpyUtil;

/**
 * Command: /god [player]
 * Toggles invulnerability (god mode) for self or a target player.
 * Can be executed from console to toggle god mode on a specific player.
 * 
 * Usage:
 *   /god           - Toggle god mode on self (player only)
 *   /god <player>  - Toggle god mode on target (admin/console)
 * 
 * Permissions:
 * - eliteessentials.command.misc.god - Use /god command
 * - eliteessentials.command.misc.god.bypass.cooldown - Skip cooldown
 * - eliteessentials.command.misc.god.cooldown.<seconds> - Set specific cooldown
 */
public class HytaleGodCommand extends CommandBase {

    private static final String COMMAND_NAME = "god";
    private static final Logger logger = Logger.getLogger("EliteEssentials");

    // Rate limiting: minimum milliseconds between command executions per player
    private static final long RATE_LIMIT_MS = 500;
    private final Map<UUID, Long> lastExecutionTime = new ConcurrentHashMap<>();
    
    private final GodService godService;
    private final ConfigManager configManager;
    private final CooldownService cooldownService;

    public HytaleGodCommand(GodService godService, ConfigManager configManager, CooldownService cooldownService) {
        super(COMMAND_NAME, "Toggle god mode (invincibility)");
        this.godService = godService;
        this.configManager = configManager;
        this.cooldownService = cooldownService;
        setAllowsExtraArguments(true);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void executeSync(@Nonnull CommandContext ctx) {
        CommandSpyUtil.notify(ctx);
        PluginConfig.GodConfig godConfig = configManager.getConfig().god;

        String rawInput = ctx.getInputString().trim();
        String[] parts = rawInput.split("\\s+", 3);

        // Determine sender type
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

        // Console must specify a target
        if (isConsoleSender && parts.length < 2) {
            ctx.sendMessage(Message.raw("Console usage: /god <player>").color("#FF5555"));
            return;
        }

        boolean isTargetingOther = (parts.length >= 2 && !parts[1].isEmpty());
        PlayerRef targetPlayer;

        if (isTargetingOther) {
            // Admin/console targeting another player
            if (!isConsoleSender) {
                if (senderPlayerRef == null) {
                    ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("noPermission"), "#FF5555"));
                    return;
                }
                if (!CommandPermissionUtil.canExecuteAdmin(ctx, senderPlayerRef, Permissions.GOD, godConfig.enabled)) {
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
            // Self-targeting
            if (senderPlayerRef == null) {
                ctx.sendMessage(Message.raw("Could not determine player.").color("#FF5555"));
                return;
            }

            // World blacklist check for self
            World senderWorld = findPlayerWorld(senderPlayerRef);
            if (senderWorld != null && WorldBlacklistUtil.isWorldBlacklisted(senderWorld.getName(), godConfig.blacklistedWorlds)) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("commandBlacklistedWorld"), "#FF5555"));
                return;
            }

            if (!CommandPermissionUtil.canExecuteAdmin(ctx, senderPlayerRef, Permissions.GOD, godConfig.enabled)) {
                return;
            }
            targetPlayer = senderPlayerRef;
        }

        UUID targetId = targetPlayer.getUuid();

        // Rate limiting (only for player senders)
        if (!isConsoleSender) {
            long now = System.currentTimeMillis();
            Long lastExec = lastExecutionTime.get(targetId);
            if (lastExec != null && (now - lastExec) < RATE_LIMIT_MS) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("commandTooFast", "Please wait a moment before using this command again."), "#FF5555"));
                return;
            }
            lastExecutionTime.put(targetId, now);
        }

        // Cooldown (only for self-use)
        int effectiveCooldown = 0;
        if (!isTargetingOther) {
            effectiveCooldown = PermissionService.get().getCommandCooldown(targetId, COMMAND_NAME, godConfig.cooldownSeconds);
            if (effectiveCooldown > 0) {
                int cooldownRemaining = cooldownService.getCooldownRemaining(COMMAND_NAME, targetId);
                if (cooldownRemaining > 0) {
                    ctx.sendMessage(MessageFormatter.formatWithFallback(
                        configManager.getMessage("onCooldown", "seconds", String.valueOf(cooldownRemaining)), "#FF5555"));
                    return;
                }
            }
        }

        // Execute on target's world thread for ECS safety
        World targetWorld = findPlayerWorld(targetPlayer);
        if (targetWorld == null) {
            ctx.sendMessage(Message.raw("Could not determine player's world.").color("#FF5555"));
            return;
        }

        final int finalEffectiveCooldown = effectiveCooldown;
        final boolean finalIsTargetingOther = isTargetingOther;
        final PlayerRef finalTarget = targetPlayer;

        targetWorld.execute(() -> {
            Ref<EntityStore> ref = finalTarget.getReference();
            if (ref == null || !ref.isValid()) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("playerNotFound", "player", finalTarget.getUsername()), "#FF5555"));
                return;
            }
            Store<EntityStore> store = ref.getStore();

            // Check actual component state to stay in sync with game state
            boolean hasInvulnerable = false;
            try {
                Invulnerable existing = store.getComponent(ref, Invulnerable.getComponentType());
                hasInvulnerable = (existing != null);
            } catch (Exception e) {
                hasInvulnerable = false;
            }

            godService.setGodMode(targetId, hasInvulnerable);
            boolean nowEnabled = godService.toggleGodMode(targetId);

            try {
                if (nowEnabled) {
                    store.putComponent(ref, Invulnerable.getComponentType(), Invulnerable.INSTANCE);
                } else {
                    if (hasInvulnerable) {
                        store.removeComponent(ref, Invulnerable.getComponentType());
                    }
                }
            } catch (IllegalArgumentException e) {
                if (configManager.isDebugEnabled()) {
                    logger.info("[God] Component operation failed (likely creative mode interaction): " + e.getMessage());
                }
            }

            // Send messages
            if (nowEnabled) {
                if (finalIsTargetingOther) {
                    ctx.sendMessage(MessageFormatter.formatWithFallback(
                        configManager.getMessage("godEnabled") + " &7(for " + finalTarget.getUsername() + ")", "#55FF55"));
                    finalTarget.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("godEnabled"), "#55FF55"));
                } else {
                    ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("godEnabled"), "#55FF55"));
                }
            } else {
                if (finalIsTargetingOther) {
                    ctx.sendMessage(MessageFormatter.formatWithFallback(
                        configManager.getMessage("godDisabled") + " &7(for " + finalTarget.getUsername() + ")", "#FFAA00"));
                    finalTarget.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("godDisabled"), "#FFAA00"));
                } else {
                    ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("godDisabled"), "#FFAA00"));
                }
            }

            if (finalEffectiveCooldown > 0) {
                cooldownService.setCooldown(COMMAND_NAME, targetId, finalEffectiveCooldown);
            }
        });
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
