package com.eliteessentials.commands.hytale;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.gui.KitSelectionPage;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.services.KitService;
import com.eliteessentials.util.CommandPermissionUtil;
import com.eliteessentials.util.MessageFormatter;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

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
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                          @Nonnull PlayerRef player, @Nonnull World world) {
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
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("kitOpenFailed"), "#FF5555"));
            return;
        }
        
        if (playerComponent == null) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("kitOpenFailed"), "#FF5555"));
            return;
        }

        // Check if there are any kits
        if (kitService.getAllKits().isEmpty()) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("kitNoKits"), "#FFAA00"));
            return;
        }

        // Create and open the kit selection page
        KitSelectionPage kitPage = new KitSelectionPage(player, kitService, configManager);
        playerComponent.getPageManager().openCustomPage(ref, store, kitPage);
    }
}
