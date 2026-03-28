package com.eliteessentials.listeners;

import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.interface_.ServerMessage;
import com.hypixel.hytale.server.core.io.adapter.PacketAdapters;
import com.hypixel.hytale.server.core.io.adapter.PacketFilter;
import com.hypixel.hytale.server.core.io.adapter.PlayerPacketFilter;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.util.Set;
import java.util.logging.Logger;

/**
 * Packet filter to suppress default Hytale join and leave messages.
 * Intercepts outbound ServerMessage packets and blocks those with
 * translation keys for the built-in join/leave broadcasts.
 *
 * This is more reliable than event-based suppression because it
 * catches messages regardless of when the server sends them.
 */
public final class DefaultMessagePacketFilter implements PlayerPacketFilter {

    private static final Logger logger = Logger.getLogger("EliteEssentials");

    // Translation keys for default Hytale join/leave messages
    private static final Set<String> SUPPRESSED_MESSAGE_IDS = Set.of(
            "server.general.playerJoinedWorld",
            "server.general.playerLeftWorld"
    );

    private static PacketFilter registeredFilter = null;
    private static volatile boolean debugEnabled = false;

    /**
     * Register this filter globally for all players.
     */
    public static void register() {
        if (registeredFilter == null) {
            registeredFilter = PacketAdapters.registerOutbound(new DefaultMessagePacketFilter());
            logger.info("Registered default message packet filter (join + leave)");
        }
    }

    /**
     * Deregister this filter.
     */
    public static void deregister() {
        if (registeredFilter != null) {
            PacketAdapters.deregisterOutbound(registeredFilter);
            registeredFilter = null;
            logger.info("Deregistered default message packet filter");
        }
    }

    /**
     * Enable or disable debug logging for this filter.
     * When enabled, logs all ServerMessage packets to help identify translation keys.
     */
    public static void setDebugEnabled(boolean enabled) {
        debugEnabled = enabled;
    }

    @Override
    public boolean test(PlayerRef playerRef, Packet packet) {
        if (!(packet instanceof ServerMessage serverMessage)) {
            return false;
        }

        if (serverMessage.message == null) {
            return false;
        }

        String messageId = serverMessage.message.messageId;
        String rawText = serverMessage.message.rawText;

        // Debug: log all ServerMessage packets so we can identify translation keys
        if (debugEnabled) {
            logger.info("[PacketFilter] ServerMessage - messageId: " + messageId
                    + ", rawText: " + rawText);
        }

        // Primary: block by translation key
        if (messageId != null && SUPPRESSED_MESSAGE_IDS.contains(messageId)) {
            return true;
        }

        return false;
    }
}
