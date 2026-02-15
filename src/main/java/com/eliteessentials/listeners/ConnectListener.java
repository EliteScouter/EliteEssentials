package com.eliteessentials.listeners;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.services.BanService;
import com.eliteessentials.services.FreezeService;
import com.eliteessentials.services.IpBanService;
import com.eliteessentials.services.TempBanService;
import com.eliteessentials.util.MessageFormatter;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.MovementSettings;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerSetupConnectEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.UUID;
import java.util.logging.Logger;

/**
 * Handles ban/tempban/ipban enforcement on player connect,
 * and re-applies freeze state on player join.
 * 
 * Uses PlayerSetupConnectEvent (pre-join) for bans - can cancel connection.
 * Uses PlayerReadyEvent (post-join) for freeze - needs world/components.
 */
public class ConnectListener {

    private static final Logger logger = Logger.getLogger("EliteEssentials");

    private final ConfigManager configManager;
    private BanService banService;
    private TempBanService tempBanService;
    private IpBanService ipBanService;
    private FreezeService freezeService;

    public ConnectListener(ConfigManager configManager) {
        this.configManager = configManager;
    }

    public void setBanService(BanService banService) { this.banService = banService; }
    public void setTempBanService(TempBanService tempBanService) { this.tempBanService = tempBanService; }
    public void setIpBanService(IpBanService ipBanService) { this.ipBanService = ipBanService; }
    public void setFreezeService(FreezeService freezeService) { this.freezeService = freezeService; }

    public void registerEvents(EventRegistry eventRegistry) {
        // Pre-join: check bans before player fully connects
        eventRegistry.registerGlobal(PlayerSetupConnectEvent.class, this::onPlayerSetupConnect);

        // Post-join: re-apply freeze state once player is in world
        eventRegistry.registerGlobal(PlayerReadyEvent.class, this::onPlayerReady);
    }

    /**
     * Check permanent bans, temp bans, and IP bans before the player joins.
     * Cancelling PlayerSetupConnectEvent prevents the connection entirely.
     */
    private void onPlayerSetupConnect(PlayerSetupConnectEvent event) {
        UUID playerId = event.getUuid();
        String username = event.getUsername();

        // Check permanent ban
        if (banService != null && configManager.getConfig().ban.enabled && banService.isBanned(playerId)) {
            BanService.BanEntry entry = banService.getBanEntry(playerId);
            String reason = entry != null && entry.reason != null ? entry.reason : "No reason specified";
            String bannedBy = entry != null && entry.bannedBy != null ? entry.bannedBy : "Server";
            String msg = configManager.getMessage("banConnectDenied", "reason", reason, "bannedBy", bannedBy);
            event.setReason(MessageFormatter.stripColorCodes(msg));
            event.setCancelled(true);
            logger.info("[BanService] Blocked banned player: " + username + " (" + playerId + ")");
            return;
        }

        // Check temp ban
        if (tempBanService != null && configManager.getConfig().ban.enabled && tempBanService.isTempBanned(playerId)) {
            TempBanService.TempBanEntry entry = tempBanService.getTempBanEntry(playerId);
            String reason = entry != null && entry.reason != null ? entry.reason : "No reason specified";
            String bannedBy = entry != null && entry.bannedBy != null ? entry.bannedBy : "Server";
            String remaining = TempBanService.formatDuration(tempBanService.getRemainingTime(playerId));
            String msg = configManager.getMessage("tempbanConnectDenied", "reason", reason,
                    "time", remaining, "bannedBy", bannedBy);
            event.setReason(MessageFormatter.stripColorCodes(msg));
            event.setCancelled(true);
            logger.info("[TempBanService] Blocked temp-banned player: " + username + " (" + remaining + " remaining)");
            return;
        }

        // Check IP ban
        if (ipBanService != null && configManager.getConfig().ban.enabled) {
            String ip = IpBanService.getIpFromPacketHandler(event.getPacketHandler());
            if (ip != null && ipBanService.isBanned(ip)) {
                IpBanService.IpBanEntry entry = ipBanService.getBanEntry(ip);
                String reason = entry != null && entry.reason != null ? entry.reason : "No reason specified";
                String bannedBy = entry != null && entry.bannedBy != null ? entry.bannedBy : "Server";
                String msg = configManager.getMessage("ipbanConnectDenied", "reason", reason, "bannedBy", bannedBy);
                event.setReason(MessageFormatter.stripColorCodes(msg));
                event.setCancelled(true);
                logger.info("[IpBanService] Blocked IP-banned player: " + username + " (IP: " + ip + ")");
            }
        }
    }

    /**
     * Re-apply freeze state when a frozen player joins.
     * Must run on the world thread to access MovementManager.
     */
    private void onPlayerReady(PlayerReadyEvent event) {
        if (freezeService == null || !configManager.getConfig().freeze.enabled) return;

        Ref<EntityStore> ref = event.getPlayerRef();
        if (ref == null || !ref.isValid()) return;
        Store<EntityStore> store = ref.getStore();

        EntityStore entityStore = store.getExternalData();
        World world = entityStore != null ? entityStore.getWorld() : null;
        if (world == null) return;

        world.execute(() -> {
            try {
                if (!ref.isValid()) return;

                PlayerRef pRef = store.getComponent(ref, PlayerRef.getComponentType());
                if (pRef == null || !pRef.isValid()) return;

                if (!freezeService.isFrozen(pRef.getUuid())) return;

                MovementManager movementManager = store.getComponent(ref, MovementManager.getComponentType());
                if (movementManager == null) return;

                // Re-apply freeze: zero out all movement
                MovementSettings settings = movementManager.getSettings();
                settings.baseSpeed = 0f;
                settings.jumpForce = 0f;
                settings.horizontalFlySpeed = 0f;
                settings.verticalFlySpeed = 0f;
                movementManager.update(pRef.getPacketHandler());

                pRef.sendMessage(MessageFormatter.formatWithFallback(
                    configManager.getMessage("freezeStillFrozen"), "#FF5555"));
                logger.info("[FreezeService] Re-applied freeze to " + pRef.getUsername() + " on join.");
            } catch (Exception e) {
                logger.warning("[FreezeService] Error re-applying freeze on join: " + e.getMessage());
            }
        });
    }
}
