package com.eliteessentials.commands.hytale;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.config.PluginConfig;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.permissions.PermissionService;
import com.eliteessentials.services.CooldownService;
import com.eliteessentials.util.CommandPermissionUtil;
import com.eliteessentials.util.MessageFormatter;
import com.eliteessentials.util.PlayerSuggestionProvider;
import com.eliteessentials.util.WorldBlacklistUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.eliteessentials.commands.base.EliteCommandBase;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.UUID;
import javax.annotation.Nonnull;
import com.eliteessentials.util.CommandSpyUtil;

/**
 * Command: /heal [player]
 * Restores health to full. Self or target player.
 * Can be executed from console to heal a specific player.
 *
 * Usage:
 *   /heal           - Heal self (player only)
 *   /heal <name>    - Heal another player (admin/console)
 *
 * Permissions:
 * - eliteessentials.command.misc.heal - Use /heal on self
 * - eliteessentials.command.misc.heal.others - Heal other players
 * - eliteessentials.command.misc.heal.bypass.cooldown - Skip cooldown
 * - eliteessentials.command.misc.heal.cooldown.<seconds> - Custom cooldown
 */
public class HytaleHealCommand extends EliteCommandBase {

    private static final String COMMAND_NAME = "heal";

    private final ConfigManager configManager;
    private final CooldownService cooldownService;

    public HytaleHealCommand(ConfigManager configManager, CooldownService cooldownService) {
        super(COMMAND_NAME, "Restore health to full");
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
        PluginConfig.HealConfig healConfig = configManager.getConfig().heal;
        boolean enabled = healConfig.enabled;

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
            ctx.sendMessage(Message.raw("Console usage: /heal <player>").color("#FF5555"));
            return;
        }

        // /heal - self (player only)
        if (parts.length < 2 || parts[1].isEmpty()) {
            if (senderPlayerRef == null) {
                ctx.sendMessage(Message.raw("Could not determine player.").color("#FF5555"));
                return;
            }

            // World blacklist check
            World senderWorld = findPlayerWorld(senderPlayerRef);
            if (senderWorld != null && WorldBlacklistUtil.isWorldBlacklisted(senderWorld.getName(), healConfig.blacklistedWorlds)) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("commandBlacklistedWorld"), "#FF5555"));
                return;
            }

            if (!CommandPermissionUtil.canExecute(ctx, senderPlayerRef, Permissions.HEAL, enabled)) {
                return;
            }
            doHealSelf(ctx, senderPlayerRef);
            return;
        }

        // /heal <name> - others (admin/console)
        if (!isConsoleSender) {
            if (senderPlayerRef == null) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("noPermission"), "#FF5555"));
                return;
            }
            if (!CommandPermissionUtil.canExecuteAdmin(ctx, senderPlayerRef, Permissions.HEAL_OTHERS, enabled)) {
                return;
            }
        }

        String targetName = parts[1];
        PlayerRef target = PlayerSuggestionProvider.findPlayer(targetName);
        if (target == null) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("playerNotFound", "player", targetName), "#FF5555"));
            return;
        }

        doHealOther(ctx, senderPlayerRef, target, isConsoleSender);
    }

    private void doHealSelf(@Nonnull CommandContext ctx, @Nonnull PlayerRef player) {
        UUID playerId = player.getUuid();

        int effectiveCooldown = PermissionService.get().getHealCooldown(playerId);
        if (effectiveCooldown > 0) {
            int cooldownRemaining = cooldownService.getCooldownRemaining(COMMAND_NAME, playerId);
            if (cooldownRemaining > 0) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("onCooldown", "seconds", String.valueOf(cooldownRemaining)), "#FF5555"));
                return;
            }
        }

        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("healFailed"), "#FF5555"));
            return;
        }

        Store<EntityStore> store = ref.getStore();
        World world = findPlayerWorld(player);
        if (world == null) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("healFailed"), "#FF5555"));
            return;
        }

        final int finalCooldown = effectiveCooldown;
        world.execute(() -> {
            EntityStatMap statMap = store.getComponent(ref, EntityStatMap.getComponentType());
            if (statMap == null) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("healFailed"), "#FF5555"));
                return;
            }
            statMap.maximizeStatValue(DefaultEntityStatTypes.getHealth());
            if (finalCooldown > 0) {
                cooldownService.setCooldown(COMMAND_NAME, playerId, finalCooldown);
            }
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("healSuccess"), "#55FF55"));
        });
    }

    private void doHealOther(@Nonnull CommandContext ctx, PlayerRef executor,
                             @Nonnull PlayerRef target, boolean isConsoleSender) {
        Ref<EntityStore> targetRef = target.getReference();
        if (targetRef == null || !targetRef.isValid()) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("playerNotFound", "player", target.getUsername()), "#FF5555"));
            return;
        }

        Store<EntityStore> targetStore = targetRef.getStore();
        World targetWorld = findPlayerWorld(target);
        if (targetWorld == null) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("healFailed"), "#FF5555"));
            return;
        }

        targetWorld.execute(() -> {
            EntityStatMap statMap = targetStore.getComponent(targetRef, EntityStatMap.getComponentType());
            if (statMap == null) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("healFailed"), "#FF5555"));
                return;
            }
            statMap.maximizeStatValue(DefaultEntityStatTypes.getHealth());

            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("healOthersSuccess", "player", target.getUsername()), "#55FF55"));
            // Notify the target (unless console is the executor, or executor is the target)
            if (executor == null || !target.getUuid().equals(executor.getUuid())) {
                String executorName = isConsoleSender ? "Console" : (executor != null ? executor.getUsername() : "Server");
                target.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("healTargetNotify", "player", executorName), "#55FF55"));
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
