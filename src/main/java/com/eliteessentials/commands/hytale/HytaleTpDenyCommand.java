package com.eliteessentials.commands.hytale;

import com.eliteessentials.EliteEssentials;
import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.model.TpaRequest;
import com.eliteessentials.services.TpaService;
import com.eliteessentials.util.CommandPermissionUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Optional;
import java.util.UUID;

/**
 * Command: /tpdeny
 * Denies a pending teleport request.
 */
public class HytaleTpDenyCommand extends AbstractPlayerCommand {

    private final TpaService tpaService;

    public HytaleTpDenyCommand(TpaService tpaService) {
        super("tpdeny", "Deny a teleport request");
        this.tpaService = tpaService;
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref, 
                          PlayerRef player, World world) {
        // Check if command is enabled (disabled = OP only)
        boolean enabled = EliteEssentials.getInstance().getConfigManager().getConfig().tpa.enabled;
        if (!CommandPermissionUtil.canExecute(ctx, player, enabled)) {
            return;
        }
        
        ConfigManager configManager = EliteEssentials.getInstance().getConfigManager();
        UUID playerId = player.getUuid();
        
        Optional<TpaRequest> requestOpt = tpaService.denyRequest(playerId);

        if (requestOpt.isEmpty()) {
            ctx.sendMessage(Message.raw(configManager.getMessage("tpaNoPending")).color("#FF5555"));
            return;
        }

        TpaRequest request = requestOpt.get();
        
        ctx.sendMessage(Message.raw(configManager.getMessage("tpaDenied", "player", request.getRequesterName())).color("#FF5555"));
        
        // Notify the requester that their request was denied
        PlayerRef requester = Universe.get().getPlayer(request.getRequesterId());
        if (requester != null && requester.isValid()) {
            requester.sendMessage(Message.raw(configManager.getMessage("tpaDeniedRequester", "player", player.getUsername())).color("#FF5555"));
        }
    }
}
