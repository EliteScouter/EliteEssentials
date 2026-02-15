package com.eliteessentials.commands.hytale;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.config.PluginConfig;
import com.eliteessentials.gui.TrashWindow;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.permissions.PermissionService;
import com.eliteessentials.services.CooldownService;
import com.eliteessentials.util.CommandPermissionUtil;
import com.eliteessentials.util.MessageFormatter;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.PageManager;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.UUID;
import java.util.logging.Logger;

import javax.annotation.Nonnull;

/**
 * /trash [size] - Open a disposal window to trash unwanted items.
 * 
 * Players can drag items into the window. Everything placed inside
 * is deleted when the window is closed.
 * 
 * Usage:
 *   /trash        - Open trash window with default size
 *   /trash <size> - Open trash window with custom size (1-45)
 * 
 * Aliases: /dispose, /disposal
 * 
 * Permissions:
 * - eliteessentials.command.misc.trash - Use /trash command (Everyone)
 * - eliteessentials.command.misc.trash.bypass.cooldown - Skip cooldown
 * - eliteessentials.command.misc.trash.cooldown.<seconds> - Set specific cooldown
 */
public class HytaleTrashCommand extends AbstractPlayerCommand {

    private static final Logger logger = Logger.getLogger("EliteEssentials");
    private static final String COMMAND_NAME = "trash";

    private final ConfigManager configManager;
    private final CooldownService cooldownService;

    public HytaleTrashCommand(ConfigManager configManager, CooldownService cooldownService) {
        super(COMMAND_NAME, "Open a disposal window to trash items");
        this.configManager = configManager;
        this.cooldownService = cooldownService;
        addAliases("dispose", "disposal");
        // Allow extra arguments for optional size parameter
        setAllowsExtraArguments(true);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                          @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        PluginConfig config = configManager.getConfig();
        PluginConfig.TrashConfig trashConfig = config.trash;
        UUID playerId = playerRef.getUuid();

        // Permission check - Everyone command
        if (!CommandPermissionUtil.canExecute(ctx, playerRef, Permissions.TRASH, trashConfig.enabled)) {
            return;
        }

        // Get effective cooldown from permissions
        int effectiveCooldown = PermissionService.get().getCommandCooldown(playerId, COMMAND_NAME, trashConfig.cooldownSeconds);

        // Check cooldown
        if (effectiveCooldown > 0) {
            int cooldownRemaining = cooldownService.getCooldownRemaining(COMMAND_NAME, playerId);
            if (cooldownRemaining > 0) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("onCooldown", "seconds", String.valueOf(cooldownRemaining)), "#FF5555"));
                return;
            }
        }

        // Get player component
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("trashFailed"), "#FF5555"));
            return;
        }

        // Parse optional size argument
        int size = trashConfig.defaultSize;
        String rawInput = ctx.getInputString();
        String[] parts = rawInput.trim().split("\\s+");
        if (parts.length >= 2) {
            try {
                int requested = Integer.parseInt(parts[1]);
                size = Math.max(1, Math.min(requested, trashConfig.maxSize));
            } catch (NumberFormatException e) {
                // Not a number, just use default
            }
        }

        // Open the trash window using PageManager
        PageManager pageManager = player.getPageManager();
        TrashWindow trashWindow = new TrashWindow((short) size);

        boolean opened = pageManager.setPageWithWindows(ref, store, Page.Inventory, true, trashWindow);

        if (!opened) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("trashFailed"), "#FF5555"));
            return;
        }

        ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("trashOpened"), "#55FF55"));

        // Set cooldown after successful open
        if (effectiveCooldown > 0) {
            cooldownService.setCooldown(COMMAND_NAME, playerId, effectiveCooldown);
        }

        if (configManager.isDebugEnabled()) {
            logger.info("Player " + playerRef.getUsername() + " opened trash window (size: " + size + ")");
        }
    }
}
