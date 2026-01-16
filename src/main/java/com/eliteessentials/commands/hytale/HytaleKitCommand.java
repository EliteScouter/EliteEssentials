package com.eliteessentials.commands.hytale;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.gui.KitSelectionPage;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.services.KitService;
import com.eliteessentials.util.CommandPermissionUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Command: /kit
 * Opens the kit selection GUI.
 * 
 * Permissions:
 * - eliteessentials.command.kit.use - Use /kit command
 * - eliteessentials.command.kit.<kitname> - Access specific kit
 * - eliteessentials.command.kit.bypass.cooldown - Bypass kit cooldowns
 */
public class HytaleKitCommand extends AbstractPlayerCommand {

    private final KitService kitService;
    private final ConfigManager configManager;

    public HytaleKitCommand(KitService kitService, ConfigManager configManager) {
        super("kit", "Open the kit selection menu");
        this.kitService = kitService;
        this.configManager = configManager;
        
        addAliases("kits");
        
        // Add subcommands
        addSubCommand(new HytaleKitCreateCommand(kitService));
        addSubCommand(new HytaleKitDeleteCommand(kitService));
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref,
                          PlayerRef player, World world) {
        // Check permission
        if (!CommandPermissionUtil.canExecute(ctx, player, Permissions.KIT, 
                configManager.getConfig().kits.enabled)) {
            return;
        }

        // Get the Player component to access PageManager
        // Wrap in try-catch to handle potential component access issues (e.g., creative mode)
        Player playerComponent;
        try {
            playerComponent = store.getComponent(ref, Player.getComponentType());
        } catch (Exception e) {
            ctx.sendMessage(Message.raw(configManager.getMessage("kitOpenFailed")).color("#FF5555"));
            return;
        }
        
        if (playerComponent == null) {
            ctx.sendMessage(Message.raw(configManager.getMessage("kitOpenFailed")).color("#FF5555"));
            return;
        }

        // Check if there are any kits
        if (kitService.getAllKits().isEmpty()) {
            ctx.sendMessage(Message.raw(configManager.getMessage("kitNoKits")).color("#FFAA00"));
            return;
        }

        // Create and open the kit selection page
        KitSelectionPage kitPage = new KitSelectionPage(player, kitService, configManager);
        playerComponent.getPageManager().openCustomPage(ref, store, kitPage);
    }
}
