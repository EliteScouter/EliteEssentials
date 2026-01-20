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
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.List;

import javax.annotation.Nonnull;

/**
 * Command: /tpahere <player>
 * Requests a player to teleport to you (opposite of /tpa).
 * 
 * Permissions:
 * - eliteessentials.command.tpahere - Send tpahere requests
 */
public class HytaleTpahereCommand extends AbstractPlayerCommand {

    private static final String COMMAND_NAME = "tpahere";
    
    private final TpaService tpaService;
    private final RequiredArg<String> targetArg;

    public HytaleTpahereCommand(TpaService tpaService) {
        super(COMMAND_NAME, "Request a player to teleport to you");
        this.tpaService = tpaService;
        // Use STRING instead of PLAYER_REF to show custom error message
        this.targetArg = withRequiredArg("player", "Target player", ArgTypes.STRING);
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
        if (!CommandPermissionUtil.canExecute(ctx, player, Permissions.TPAHERE, config.tpa.enabled)) {
            return;
        }
        
        String targetName = ctx.get(targetArg);
        
        // Find target player by name
        PlayerRef target = findPlayer(targetName);
        
        if (target == null) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("playerNotFound", "player", targetName), "#FF5555"));
            return;
        }
        
        // Create teleport request with TPAHERE type
        TpaService.Result result = tpaService.createRequest(
            player.getUuid(),
            player.getUsername(),
            target.getUuid(),
            target.getUsername(),
            TpaRequest.Type.TPAHERE
        );
        
        switch (result) {
            case REQUEST_SENT -> {
                ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("tpahereRequestSent", "player", target.getUsername()), "#55FF55"));
                
                // Send notification to target player with instructions
                target.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("tpahereRequestReceived", "player", player.getUsername()), "#FFFF55"));
                target.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("tpaRequestInstructions"), "#AAAAAA"));
            }
            case SELF_REQUEST -> ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("tpaSelfRequest"), "#FF5555"));
            case ALREADY_PENDING -> ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("tpaAlreadyPending"), "#FF5555"));
            default -> ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("tpaRequestFailed"), "#FF5555"));
        }
    }
    
    /**
     * Find a player by name (case-insensitive).
     */
    private PlayerRef findPlayer(String name) {
        List<PlayerRef> players = Universe.get().getPlayers();
        for (PlayerRef p : players) {
            if (p.getUsername().equalsIgnoreCase(name)) {
                return p;
            }
        }
        return null;
    }
}
