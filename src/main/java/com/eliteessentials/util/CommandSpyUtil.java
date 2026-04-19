package com.eliteessentials.util;

import com.eliteessentials.EliteEssentials;
import com.eliteessentials.services.SpyService;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.entity.entities.Player;

import java.util.UUID;

/**
 * Utility for notifying command spies about command executions.
 * 
 * Since Hytale does not have a CommandEvent, command spy is triggered
 * by calling {@link #notify(CommandContext)} from individual commands
 * or from a chat event listener that intercepts command input.
 * 
 * Usage in commands (single line at the top of execute):
 * <pre>
 * CommandSpyUtil.notify(ctx);
 * </pre>
 */
public final class CommandSpyUtil {

    private CommandSpyUtil() {} // Utility class

    /**
     * Notify command spies about a command execution from a CommandContext.
     * Automatically extracts the player and command string from the context.
     * Safe to call from any thread. No-ops if spy service is unavailable
     * or if the sender is not a player (e.g., console).
     * 
     * @param ctx The command context
     */
    public static void notify(CommandContext ctx) {
        if (ctx == null) return;
        SpyService spyService = EliteEssentials.getInstance().getSpyService();
        if (spyService == null) return;

        // Extract player from sender
        Object sender = ctx.sender();
        UUID executorId = null;
        String executorName = null;

        if (sender instanceof PlayerRef playerRef) {
            executorId = playerRef.getUuid();
            executorName = playerRef.getUsername();
        } else if (sender instanceof Player player) {
            @SuppressWarnings("removal")
            UUID uuid = player.getUuid();
            executorId = uuid;
            if (executorId != null) {
                PlayerRef ref = Universe.get().getPlayer(executorId);
                executorName = ref != null ? ref.getUsername() : "Unknown";
            }
        }

        if (executorId == null || executorName == null) return;

        String rawCommand = ctx.getInputString();
        if (rawCommand == null || rawCommand.isEmpty()) return;

        String command = rawCommand.startsWith("/") ? rawCommand.substring(1) : rawCommand;
        spyService.notifyCommandSpy(executorId, executorName, command);
    }

    /**
     * Notify command spies about a command execution.
     * Safe to call from any thread. No-ops if spy service is unavailable.
     * 
     * @param executorId UUID of the player who executed the command
     * @param executorName Display name of the executor
     * @param rawCommand The full command string (with or without leading /)
     */
    public static void notify(UUID executorId, String executorName, String rawCommand) {
        SpyService spyService = EliteEssentials.getInstance().getSpyService();
        if (spyService == null) return;

        String command = rawCommand;
        if (command.startsWith("/")) {
            command = command.substring(1);
        }
        spyService.notifyCommandSpy(executorId, executorName, command);
    }
}
