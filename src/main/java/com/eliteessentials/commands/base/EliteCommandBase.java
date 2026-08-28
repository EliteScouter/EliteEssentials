package com.eliteessentials.commands.base;

import com.hypixel.hytale.server.core.command.system.CommandOwner;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;

/**
 * General command base with the {@code canGeneratePermission()} hook.
 *
 * <p>See {@link ElitePlayerCommand} for why this hook is emulated instead of used
 * directly: Hytale 0.6.0 dropped {@code canGeneratePermission()} in favour of
 * {@link #requireNoPermission()}.
 */
public abstract class EliteCommandBase extends CommandBase {

    protected EliteCommandBase(String name, String description) {
        super(name, description);
    }

    protected EliteCommandBase(String name, String description, boolean requiresConfirmation) {
        super(name, description, requiresConfirmation);
    }

    protected EliteCommandBase(String name) {
        super(name);
    }

    /**
     * Whether the engine should generate a permission node for this command.
     * Return false to leave the command open and rely on the plugin's own checks.
     */
    protected boolean canGeneratePermission() {
        return true;
    }

    @Override
    public void setOwner(CommandOwner owner) {
        CommandPermissionCompat.applyNoPermission(this, canGeneratePermission());
        super.setOwner(owner);
    }
}
