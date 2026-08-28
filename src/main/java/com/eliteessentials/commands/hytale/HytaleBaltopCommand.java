package com.eliteessentials.commands.hytale;

import com.eliteessentials.api.EconomyAPI;
import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.config.PluginConfig;
import com.eliteessentials.model.PlayerFile;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.services.PlayerService;
import com.eliteessentials.util.CommandPermissionUtil;
import com.eliteessentials.util.MessageFormatter;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.eliteessentials.commands.base.ElitePlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import com.eliteessentials.util.CommandSpyUtil;

/**
 * Command: /baltop
 * Shows the richest players on the server.
 * 
 * Usage: /baltop
 * 
 * Displays a leaderboard of players with the highest balances.
 * The number of entries shown is configurable (default: 10).
 */
public class HytaleBaltopCommand extends ElitePlayerCommand {

    private static final String COMMAND_NAME = "baltop";
    
    private final ConfigManager configManager;
    private final PlayerService playerService;

    public HytaleBaltopCommand(ConfigManager configManager, PlayerService playerService) {
        super(COMMAND_NAME, "View richest players leaderboard");
        this.configManager = configManager;
        this.playerService = playerService;
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                          @Nonnull PlayerRef player, @Nonnull World world) {
        CommandSpyUtil.notify(ctx);
        PluginConfig.EconomyConfig economyConfig = configManager.getConfig().economy;
        UUID playerId = player.getUuid();
        
        // Check if economy is enabled
        if (!economyConfig.enabled) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("commandDisabled"), "#FF5555"));
            return;
        }
        
        // Check permission
        if (!CommandPermissionUtil.canExecute(ctx, player, Permissions.BALTOP, economyConfig.enabled)) {
            return;
        }
        
        // Get top players
        List<PlayerFile> topPlayers = playerService.getTopByBalance(economyConfig.baltopLimit);
        
        if (topPlayers.isEmpty()) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("baltopEmpty"), "#FFAA00"));
            return;
        }
        
        // Header
        ctx.sendMessage(MessageFormatter.formatWithFallback(
            configManager.getMessage("baltopHeader", "currency", EconomyAPI.getCurrencyNamePlural()), "#55FFFF"));
        
        // List entries
        int rank = 1;
        for (PlayerFile data : topPlayers) {
            String entry = configManager.getMessage("baltopEntry",
                "rank", String.valueOf(rank),
                "player", data.getName(),
                "balance", EconomyAPI.format(data.getWallet()));
            ctx.sendMessage(MessageFormatter.formatWithFallback(entry, "#FFFFFF"));
            rank++;
        }
        
        // Show player's own rank if not in top
        double playerBalance = playerService.getBalance(playerId);
        ctx.sendMessage(MessageFormatter.formatWithFallback(
            configManager.getMessage("baltopYourBalance", "balance", EconomyAPI.format(playerBalance)), "#AAAAAA"));
    }
}
