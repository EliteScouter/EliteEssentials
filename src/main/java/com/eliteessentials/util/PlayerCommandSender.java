package com.eliteessentials.util;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * A CommandSender wrapper that executes commands in the player's security context.
 * Unlike the console sender used by CommandExecutor (which has all permissions),
 * this delegates permission checks to the actual player via PermissionsModule,
 * preventing privilege escalation.
 * 
 * Used by the alias system so aliased commands respect the player's own permissions.
 * The target command's permission check acts as a second gate on top of the alias permission.
 */
public class PlayerCommandSender implements CommandSender {

    private final PlayerRef playerRef;
    private final UUID playerId;
    private final String displayName;

    public PlayerCommandSender(@Nonnull PlayerRef playerRef) {
        this.playerRef = playerRef;
        this.playerId = playerRef.getUuid();
        this.displayName = playerRef.getUsername();
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public UUID getUuid() {
        return playerId;
    }

    @Override
    public void sendMessage(@Nonnull Message message) {
        if (playerRef.isValid()) {
            playerRef.sendMessage(message);
        }
    }

    @Override
    public boolean hasPermission(@Nonnull String permission) {
        try {
            return PermissionsModule.get().hasPermission(playerId, permission, false);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean hasPermission(@Nonnull String permission, boolean defaultValue) {
        try {
            return PermissionsModule.get().hasPermission(playerId, permission, defaultValue);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * Get the underlying PlayerRef.
     */
    public PlayerRef getPlayerRef() {
        return playerRef;
    }
}
