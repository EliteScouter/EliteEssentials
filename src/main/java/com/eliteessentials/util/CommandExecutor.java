package com.eliteessentials.util;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandManager;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Shared utility for executing server commands as console.
 * Used by kits, playtime rewards, and any future feature that needs to run
 * arbitrary commands with placeholder support.
 *
 * Supported placeholders:
 * - {player} or %player% - replaced with player's username
 */
public final class CommandExecutor {

    private static final Logger logger = Logger.getLogger("EliteEssentials");
    private static boolean debugEnabled = false;

    private CommandExecutor() {}

    /**
     * Set whether debug logging is enabled.
     */
    public static void setDebugEnabled(boolean enabled) {
        debugEnabled = enabled;
    }

    /**
     * Execute a list of commands as console for a given player.
     */
    public static void executeCommands(List<String> commands, String playerName, UUID playerId, String source) {
        if (commands == null || commands.isEmpty()) return;
        for (String command : commands) {
            executeCommand(command, playerName, playerId, source);
        }
    }

    /**
     * Execute a single command as console with placeholder replacement.
     */
    public static void executeCommand(String command, String playerName, UUID playerId, String source) {
        try {
            String processedCommand = command
                    .replace("{player}", playerName)
                    .replace("%player%", playerName)
                    .trim();

            if (processedCommand.startsWith("/")) {
                processedCommand = processedCommand.substring(1);
            }

            if (processedCommand.startsWith("\"") && processedCommand.endsWith("\"")) {
                processedCommand = processedCommand.substring(1, processedCommand.length() - 1);
            }

            if (processedCommand.isEmpty()) {
                logger.warning("[" + source + "] Empty command after processing: " + command);
                return;
            }

            PlayerRef playerRef = Universe.get().getPlayer(playerId);
            World world = null;

            if (playerRef != null && playerRef.isValid()) {
                world = Universe.get().getWorld(playerRef.getWorldUuid());
            }

            if (world == null) {
                world = Universe.get().getDefaultWorld();
            }

            if (world == null) {
                logger.warning("[" + source + "] Could not find a valid world to execute command for " + playerName);
                return;
            }

            final String finalCommand = processedCommand;
            final String logSource = source;
            world.execute(() -> {
                try {
                    CommandManager cm = CommandManager.get();

                    CommandSender consoleSender = new CommandSender() {
                        @Override
                        public String getDisplayName() {
                            return "Console";
                        }

                        @Override
                        public UUID getUuid() {
                            return new UUID(0, 0);
                        }

                        @Override
                        public void sendMessage(@Nonnull Message message) {
                            if (debugEnabled) {
                                logger.info("[" + logSource + "-Console] " + message.toString());
                            }
                        }

                        @Override
                        public boolean hasPermission(@Nonnull String permission) {
                            return true;
                        }

                        @Override
                        public boolean hasPermission(@Nonnull String permission, boolean defaultValue) {
                            return true;
                        }
                    };

                    if (debugEnabled) {
                        logger.info("[" + logSource + "] Executing command: " + finalCommand);
                    }

                    cm.handleCommand(consoleSender, finalCommand);

                } catch (Exception e) {
                    logger.warning("[" + logSource + "] Failed to execute command '" + finalCommand + "': " + e.getMessage());
                }
            });

        } catch (Exception e) {
            logger.warning("[" + source + "] Failed to process command '" + command + "': " + e.getMessage());
        }
    }
}
