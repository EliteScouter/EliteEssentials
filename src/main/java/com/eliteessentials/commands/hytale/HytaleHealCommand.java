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
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * Command: /heal [player]
 * Restores health to full. Self or target player.
 *
 * Usage:
 *   /heal           - Heal self (misc.heal)
 *   /heal <name>    - Heal another player (misc.heal.others)
 *
 * Permissions:
 * - eliteessentials.command.misc.heal - Use /heal on self
 * - eliteessentials.command.misc.heal.others - Heal other players
 * - eliteessentials.command.misc.heal.bypass.cooldown - Skip cooldown
 * - eliteessentials.command.misc.heal.cooldown.<seconds> - Custom cooldown
 */
public class HytaleHealCommand extends AbstractPlayerCommand {

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
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                          @Nonnull PlayerRef player, @Nonnull World world) {
        PluginConfig.HealConfig healConfig = configManager.getConfig().heal;
        boolean enabled = healConfig.enabled;

        if (WorldBlacklistUtil.isWorldBlacklisted(world.getName(), healConfig.blacklistedWorlds)) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("commandBlacklistedWorld"), "#FF5555"));
            return;
        }

        String rawInput = ctx.getInputString().trim();
        String[] parts = rawInput.split("\\s+", 3);

        // /heal - self (simple mode: everyone when enabled)
        if (parts.length < 2 || parts[1].isEmpty()) {
            if (!CommandPermissionUtil.canExecute(ctx, player, Permissions.HEAL, enabled)) {
                return;
            }
            doHealSelf(ctx, store, ref, player);
            return;
        }

        // /heal <name> - others (simple mode: OP when enabled)
        if (!CommandPermissionUtil.canExecuteAdmin(ctx, player, Permissions.HEAL_OTHERS, enabled)) {
            return;
        }

        String targetName = parts[1];
        PlayerRef target = PlayerSuggestionProvider.findPlayer(targetName);
        if (target == null) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("playerNotFound", "player", targetName), "#FF5555"));
            return;
        }

        doHealOther(ctx, player, target);
    }

    private void doHealSelf(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                            @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef player) {
        UUID playerId = player.getUuid();

        // Check cooldown
        int effectiveCooldown = PermissionService.get().getHealCooldown(playerId);
        if (effectiveCooldown > 0) {
            int cooldownRemaining = cooldownService.getCooldownRemaining(COMMAND_NAME, playerId);
            if (cooldownRemaining > 0) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("onCooldown", "seconds", String.valueOf(cooldownRemaining)), "#FF5555"));
                return;
            }
        }

        EntityStatMap statMap = store.getComponent(ref, EntityStatMap.getComponentType());
        if (statMap == null) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("healFailed"), "#FF5555"));
            return;
        }

        statMap.maximizeStatValue(DefaultEntityStatTypes.getHealth());
        if (effectiveCooldown > 0) {
            cooldownService.setCooldown(COMMAND_NAME, playerId, effectiveCooldown);
        }

        ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("healSuccess"), "#55FF55"));
    }

    private void doHealOther(@Nonnull CommandContext ctx, @Nonnull PlayerRef executor, @Nonnull PlayerRef target) {
        Ref<EntityStore> targetRef = target.getReference();
        if (targetRef == null || !targetRef.isValid()) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("playerNotFound", "player", target.getUsername()), "#FF5555"));
            return;
        }

        Store<EntityStore> targetStore = targetRef.getStore();
        EntityStore targetEntityStore = targetStore.getExternalData();
        World targetWorld = targetEntityStore != null ? targetEntityStore.getWorld() : null;
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
            if (!target.getUuid().equals(executor.getUuid())) {
                target.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("healTargetNotify", "player", executor.getUsername()), "#55FF55"));
            }
        });
    }
}
