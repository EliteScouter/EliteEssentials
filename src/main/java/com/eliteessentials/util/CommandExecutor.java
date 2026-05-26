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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
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
    /** Delay (ms) between each command when running multiple; 0 = no delay. Workaround for Hytale CommandManager parser bug. */
    private static volatile int delayBetweenCommandsMs = 0;
    private static volatile ScheduledExecutorService delayScheduler;

    private CommandExecutor() {}

    /**
     * Set whether debug logging is enabled.
     */
    public static void setDebugEnabled(boolean enabled) {
        debugEnabled = enabled;
    }

    /**
     * Set delay (ms) between console command executions when running multiple commands.
     * Workaround for native Hytale CommandManager bug (No match found). 0 = no delay.
     */
    public static void setDelayBetweenCommandsMs(int ms) {
        delayBetweenCommandsMs = Math.max(0, ms);
    }

    /**
     * Execute a list of commands as console for a given player.
     * When {@link #setDelayBetweenCommandsMs(int)} is &gt; 0, commands are staggered to work around
     * a native Hytale CommandManager parser bug (No match found when run back-to-back).
     */
    public static void executeCommands(List<String> commands, String playerName, UUID playerId, String source) {
        executeCommands(commands, playerName, playerId, source, 0);
    }

    /**
     * Execute a list of commands with an optional initial delay (ms). Use when multiple batches
     * (e.g. multiple playtime rewards in one check) need to be staggered so commands don't overlap.
     */
    public static void executeCommands(List<String> commands, String playerName, UUID playerId, String source, int initialDelayMs) {
        if (commands == null || commands.isEmpty()) return;
        int delayMs = delayBetweenCommandsMs;
        if (delayMs <= 0 && initialDelayMs <= 0) {
            for (String command : commands) {
                executeCommand(command, playerName, playerId, source);
            }
            return;
        }
        ScheduledExecutorService scheduler = getDelayScheduler();
        for (int i = 0; i < commands.size(); i++) {
            String command = commands.get(i);
            long delay = (long) initialDelayMs + (long) i * delayMs;
            if (delay == 0) {
                executeCommand(command, playerName, playerId, source);
            } else {
                scheduler.schedule(() -> executeCommand(command, playerName, playerId, source), delay, TimeUnit.MILLISECONDS);
            }
        }
    }

    private static ScheduledExecutorService getDelayScheduler() {
        if (delayScheduler == null) {
            synchronized (CommandExecutor.class) {
                if (delayScheduler == null) {
                    delayScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                        Thread t = new Thread(r, "EliteEssentials-CommandDelay");
                        t.setDaemon(true);
                        return t;
                    });
                }
            }
        }
        return delayScheduler;
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
                        public String getUsername() {
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
