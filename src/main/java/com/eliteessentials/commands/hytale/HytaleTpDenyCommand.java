package com.eliteessentials.commands.hytale;

import com.eliteessentials.EliteEssentials;
import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.config.PluginConfig;
import com.eliteessentials.model.TpaRequest;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.services.TpaService;
import com.eliteessentials.util.CommandPermissionUtil;
import com.eliteessentials.util.MessageFormatter;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nonnull;

/**
 * Command: /tpdeny
 * Denies a pending teleport request.
 * 
 * Permissions:
 * - eliteessentials.command.tpdeny - Deny teleport requests
 */
public class HytaleTpDenyCommand extends AbstractPlayerCommand {

    private static final String COMMAND_NAME = "tpdeny";
    
    private final TpaService tpaService;

    public HytaleTpDenyCommand(TpaService tpaService) {
        super(COMMAND_NAME, "Deny a teleport request");
        this.tpaService = tpaService;
        
        // Permission check handled in execute() via CommandPermissionUtil
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, 
                          @Nonnull PlayerRef player, @Nonnull World world) {
        ConfigManager configManager = EliteEssentials.getInstance().getConfigManager();
        PluginConfig config = configManager.getConfig();
        if (!CommandPermissionUtil.canExecute(ctx, player, Permissions.TPDENY, config.tpa.enabled)) {
            return;
        }
        
        UUID playerId = player.getUuid();
        
        Optional<TpaRequest> requestOpt = tpaService.denyRequest(playerId);

        if (requestOpt.isEmpty()) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("tpaNoPending"), "#FF5555"));
            return;
        }

        TpaRequest request = requestOpt.get();
        
        ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("tpaDenied", "player", request.getRequesterName()), "#FF5555"));
        
        // Notify the requester that their request was denied
        PlayerRef requester = Universe.get().getPlayer(request.getRequesterId());
        if (requester != null && requester.isValid()) {
            requester.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("tpaDeniedRequester", "player", player.getUsername()), "#FF5555"));
        }
    }
}
