package com.eliteessentials.commands.hytale;

import com.eliteessentials.EliteEssentials;
import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.gui.AdminDashboardPage;
import com.eliteessentials.permissions.Permissions;
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
 * Command: /admin
 * Opens the Admin UI dashboard panel.
 * Admin-only command.
 */
public class HytaleAdminUICommand extends AbstractPlayerCommand {

    private static final String COMMAND_NAME = "admin";

    public HytaleAdminUICommand() {
        super(COMMAND_NAME, "Open the Admin UI panel");
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                          @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef player, @Nonnull World world) {
        ConfigManager configManager = EliteEssentials.getInstance().getConfigManager();

        // Admin-only permission check
        if (!CommandPermissionUtil.canExecuteAdmin(ctx, player, Permissions.ADMIN_UI, true)) {
            return;
        }

        Player playerComponent = store.getComponent(ref, Player.getComponentType());
        if (playerComponent == null) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("couldNotGetPosition"), "#FF5555"));
            return;
        }

        AdminDashboardPage page = new AdminDashboardPage(player, configManager);
        playerComponent.getPageManager().openCustomPage(ref, store, page);
    }
}
