package com.eliteessentials.commands.hytale;

import com.eliteessentials.EliteEssentials;
import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.services.TpaService;
import com.eliteessentials.util.CommandPermissionUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Command: /tpa <player>
 * Sends a teleport request to another player.
 */
public class HytaleTpaCommand extends AbstractPlayerCommand {

    private final TpaService tpaService;
    private final RequiredArg<PlayerRef> targetArg;

    public HytaleTpaCommand(TpaService tpaService) {
        super("tpa", "Request to teleport to a player");
        this.tpaService = tpaService;
        this.targetArg = withRequiredArg("player", "Target player", ArgTypes.PLAYER_REF);
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
        PlayerRef target = ctx.get(targetArg);
        
        if (target == null) {
            ctx.sendMessage(Message.raw(configManager.getMessage("playerNotFound")).color("#FF5555"));
            return;
        }
        
        // Create teleport request
        TpaService.Result result = tpaService.createRequest(
            player.getUuid(),
            player.getUsername(),
            target.getUuid(),
            target.getUsername()
        );
        
        switch (result) {
            case REQUEST_SENT -> {
                ctx.sendMessage(Message.raw(configManager.getMessage("tpaRequestSent", "player", target.getUsername())).color("#55FF55"));
                
                // Send notification to target player with instructions
                target.sendMessage(Message.raw(configManager.getMessage("tpaRequestReceived", "player", player.getUsername())).color("#FFFF55"));
                target.sendMessage(Message.raw(configManager.getMessage("tpaRequestInstructions")).color("#AAAAAA"));
            }
            case SELF_REQUEST -> ctx.sendMessage(Message.raw(configManager.getMessage("tpaSelfRequest")).color("#FF5555"));
            case ALREADY_PENDING -> ctx.sendMessage(Message.raw(configManager.getMessage("tpaAlreadyPending")).color("#FF5555"));
            default -> ctx.sendMessage(Message.raw(configManager.getMessage("tpaRequestFailed")).color("#FF5555"));
        }
    }
}
