package com.eliteessentials.commands.hytale;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.gui.InventoryViewWindow;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.util.CommandPermissionUtil;
import com.eliteessentials.util.MessageFormatter;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.windows.Window;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.List;
import java.util.logging.Logger;
import javax.annotation.Nonnull;

/**
 * /invsee <player> - View another player's inventory.
 * Parses args manually (no OptionalArg) to avoid Hytale's "Expected: 0, actual: 1" error.
 */
public class HytaleInvseeCommand extends AbstractPlayerCommand {

    private static final Logger logger = Logger.getLogger("EliteEssentials");
    private static final String COMMAND_NAME = "invsee";

    private final ConfigManager configManager;

    public HytaleInvseeCommand(ConfigManager configManager) {
        super(COMMAND_NAME, "View another player's inventory");
        this.configManager = configManager;
        this.setAllowsExtraArguments(true);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                          @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        // Permission check - admin only
        if (!CommandPermissionUtil.canExecuteAdmin(ctx, playerRef, Permissions.INVSEE, true)) {
            return;
        }

        // Parse player name manually from raw input to avoid OptionalArg issues
        String rawInput = ctx.getInputString();
        String[] parts = rawInput.split("\\s+");

        // parts[0] = "invsee", parts[1] = player name (if provided)
        if (parts.length < 2 || parts[1].isEmpty()) {
            showUsage(ctx);
            return;
        }

        String targetPlayerName = parts[1];

        // Look up target player
        PlayerRef targetPlayerRef = findPlayerByName(targetPlayerName);
        if (targetPlayerRef == null) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("invseePlayerNotFound", "player", targetPlayerName), "#FF5555"));
            return;
        }

        Ref<EntityStore> targetRef = targetPlayerRef.getReference();
        if (targetRef == null || !targetRef.isValid()) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("invseePlayerNotFound", "player", targetPlayerName), "#FF5555"));
            return;
        }

        Store<EntityStore> targetStore = targetRef.getStore();
        World targetWorld = ((EntityStore) targetStore.getExternalData()).getWorld();

        // Get the executing player's Player component (needed for PageManager)
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("invseeError"), "#FF5555"));
            return;
        }

        // Run on the target player's world thread, just like Hytale's built-in /inv see
        targetWorld.execute(() -> {
            Player targetPlayer = targetStore.getComponent(targetRef, Player.getComponentType());
            if (targetPlayer == null) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("invseePlayerNotFound", "player", targetPlayerName), "#FF5555"));
                return;
            }

            InventoryViewWindow inventoryWindow = new InventoryViewWindow(targetPlayer);

            // Use Page.Bench - this is what Hytale's /inv see uses
            boolean opened = player.getPageManager().setPageWithWindows(
                ref, store, Page.Bench, true, new Window[]{ inventoryWindow });

            if (!opened) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("invseeError"), "#FF5555"));
                return;
            }

            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("invseeOpened", "player", targetPlayerName), "#55FF55"));

            if (configManager.isDebugEnabled()) {
                logger.info(playerRef.getUsername() + " opened inventory view of " + targetPlayerName);
            }
        });
    }

    /**
     * Compact usage help when no args provided.
     */
    private void showUsage(@Nonnull CommandContext ctx) {
        ctx.sendMessage(Message.join(
            Message.raw("Usage: ").color("#AAAAAA"),
            Message.raw("/invsee <player>").color("#55FF55"),
            Message.raw(" - View and edit a player's inventory").color("#777777")
        ));
    }

    private PlayerRef findPlayerByName(String playerName) {
        List<PlayerRef> players = Universe.get().getPlayers();
        for (PlayerRef ref : players) {
            if (ref == null) continue;
            String username = ref.getUsername();
            if (username != null && username.equalsIgnoreCase(playerName)) {
                return ref;
            }
        }
        return null;
    }
}
