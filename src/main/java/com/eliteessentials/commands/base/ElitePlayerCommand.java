package com.eliteessentials.commands.base;

import com.hypixel.hytale.server.core.command.system.CommandOwner;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;

/**
 * Player-only command base with the {@code canGeneratePermission()} hook.
 *
 * <p>Hytale 0.5.x let a command opt out of the auto-generated permission node by
 * overriding {@code canGeneratePermission()} to return false. Hytale 0.6.0 removed
 * that hook and replaced it with {@link #requireNoPermission()}, which must be called
 * before the command finishes registration.
 *
 * <p>EliteEssentials does its own permission checks (simple vs advanced mode) through
 * {@code CommandPermissionUtil}, so most commands opt out of the engine-generated node.
 * This class keeps the old hook working by translating it into a
 * {@code requireNoPermission()} call at the moment the owner is assigned, which is when
 * the engine would otherwise generate the node.
 */
public abstract class ElitePlayerCommand extends AbstractPlayerCommand {

    protected ElitePlayerCommand(String name, String description) {
        super(name, description);
    }

    protected ElitePlayerCommand(String name, String description, boolean requiresConfirmation) {
        super(name, description, requiresConfirmation);
    }

    protected ElitePlayerCommand(String name) {
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
