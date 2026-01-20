package com.eliteessentials.commands.hytale;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.util.CommandPermissionUtil;
import com.eliteessentials.util.MessageFormatter;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * Command: /heal
 * Restores the player's health to full.
 * 
 * Permissions:
 * - eliteessentials.command.misc.heal - Use /heal command
 */
public class HytaleHealCommand extends AbstractPlayerCommand {

    private final ConfigManager configManager;

    public HytaleHealCommand(ConfigManager configManager) {
        super("heal", "Restore your health to full");
        this.configManager = configManager;
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                          @Nonnull PlayerRef player, @Nonnull World world) {
        // Check permission (Admin command)
        if (!CommandPermissionUtil.canExecuteAdmin(ctx, player, Permissions.HEAL, 
                configManager.getConfig().heal.enabled)) {
            return;
        }

        // Get player's stat map
        EntityStatMap statMap = store.getComponent(ref, EntityStatMap.getComponentType());
        if (statMap == null) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("healFailed"), "#FF5555"));
            return;
        }

        // Maximize health
        int healthStatIndex = DefaultEntityStatTypes.getHealth();
        statMap.maximizeStatValue(healthStatIndex);

        ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("healSuccess"), "#55FF55"));
    }
}
