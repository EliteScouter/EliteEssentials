package com.eliteessentials.commands.base;

import com.hypixel.hytale.server.core.command.system.AbstractCommand;

/**
 * Bridges the pre-0.6.0 {@code canGeneratePermission()} hook onto the current
 * {@code requireNoPermission()} API.
 */
final class CommandPermissionCompat {

    private CommandPermissionCompat() {
    }

    /**
     * Opts a command out of the engine-generated permission node.
     *
     * <p>Skipped when the command asked for generation, when an explicit permission was
     * already set with {@code requirePermission(...)}, or when registration already
     * completed (the engine rejects permission changes at that point).
     */
    static void applyNoPermission(AbstractCommand command, boolean canGeneratePermission) {
        if (canGeneratePermission) {
            return;
        }
        if (command.getPermission() != null || command.hasBeenRegistered()) {
            return;
        }
        command.requireNoPermission();
    }
}
