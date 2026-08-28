package com.eliteessentials.services;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.config.PluginConfig;
import com.eliteessentials.model.PlayerFile;
import com.eliteessentials.storage.PlayerStorageProvider;
import com.eliteessentials.util.MessageFormatter;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.Invulnerable;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldMapTracker;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.protocol.packets.interface_.RemoveFromServerPlayerList;
import com.hypixel.hytale.protocol.packets.interface_.AddToServerPlayerList;
import com.hypixel.hytale.protocol.packets.interface_.ServerPlayerListPlayer;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Service for managing player vanish state.
 */
public class VanishService {

    private static final Logger logger = Logger.getLogger("EliteEssentials");

    /**
     * Minimum time (ms) between successful /v toggles per player.
     * Defense-in-depth against rapid toggles corrupting ECS archetype.
     * Together with the toggleInProgress guard (which now spans the deferred
     * world.execute() mutation), this makes double-toggle races impossible.
     */
    private static final long TOGGLE_COOLDOWN_MS = 500L;

    private final ConfigManager configManager;
    private PlayerStorageProvider playerFileStorage;

    private final Set<UUID> vanishedPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, PlayerStoreRef> playerStoreRefs = new ConcurrentHashMap<>();

    /**
     * Guard against concurrent vanish toggles for the same player.
     *
     * CRITICAL: This guard is now held for the FULL LIFETIME of the toggle — including
     * the deferred world.execute() component mutation. The previous version released
     * the guard in a finally block inside toggleVanish() before world.execute() ran
     * the Invulnerable component mutation on the world thread. Two /v commands fired
     * within a second could both return, then both their deferred tasks would race
     * on the world thread — corrupting the ECS archetype and nulling the Player
     * component. The NPE cascade at 20:13:36 on 2026-04-12 (3414 failed tasks in 90s)
     * was caused by exactly this: EZpz executed /v twice in 1 second.
     *
     * Now the guard is only released after applyMobImmunitySync() completes on the
     * world thread, so a second /v for the same player is rejected until the first
     * has fully finished mutating components.
     */
    private final Set<UUID> toggleInProgress = ConcurrentHashMap.newKeySet();

    /** Last successful toggle timestamp per player (for cooldown debounce). */
    private final Map<UUID, Long> lastToggleMs = new ConcurrentHashMap<>();

    private static class PlayerStoreRef {
        final Store<EntityStore> store;
        final Ref<EntityStore> ref;

        PlayerStoreRef(Store<EntityStore> store, Ref<EntityStore> ref) {
            this.store = store;
            this.ref = ref;
        }
    }

    public VanishService(ConfigManager configManager) {
        this.configManager = configManager;
    }

    public void setPlayerFileStorage(PlayerStorageProvider storage) {
        this.playerFileStorage = storage;
    }

    /** Public entry — backwards-compatible no-store-ref variant. */
    public void setVanished(UUID playerId, String playerName, boolean vanished) {
        setVanished(playerId, playerName, vanished, null, null);
    }

    /**
     * Public setVanished — caller does NOT hold the toggleInProgress guard, so we
     * do not release anything here. The async world.execute() inside updateMobImmunity
     * can run after this returns, which is fine for this entry point because it's
     * called from places like onPlayerJoin() restoration, not from toggleVanish()
     * where the race matters.
     */
    public void setVanished(UUID playerId, String playerName, boolean vanished,
                            Store<EntityStore> store, Ref<EntityStore> ref) {
        setVanishedInternal(playerId, playerName, vanished, store, ref, null);
    }

    /**
     * Internal setVanished with an optional guardRelease callback. When non-null,
     * the callback is invoked after the deferred Invulnerable mutation completes
     * (or is skipped). Used by toggleVanish() to keep toggleInProgress held until
     * all side-effects of the toggle are done.
     */
    private void setVanishedInternal(UUID playerId, String playerName, boolean vanished,
                                     Store<EntityStore> store, Ref<EntityStore> ref,
                                     Runnable guardRelease) {
        if (vanished) {
            vanishedPlayers.add(playerId);
        } else {
            vanishedPlayers.remove(playerId);
        }

        PluginConfig config = configManager.getConfig();

        if (config.vanish.persistOnReconnect && playerFileStorage != null) {
            PlayerFile playerFile = playerFileStorage.getPlayer(playerId);
            if (playerFile != null) {
                playerFile.setVanished(vanished);
                playerFileStorage.saveAndMarkDirty(playerId);
            }
        }

        updateAllPlayersInSinglePass(playerId, vanished, config);

        // mobImmunity path: defer guard release until world.execute() runs.
        // If mobImmunity is disabled or no valid refs, release the guard immediately.
        boolean deferredMutationScheduled = false;
        if (config.vanish.mobImmunity) {
            deferredMutationScheduled = updateMobImmunity(playerId, vanished, store, ref, guardRelease);
        }
        if (!deferredMutationScheduled && guardRelease != null) {
            guardRelease.run();
        }

        if (config.vanish.mimicJoinLeave) {
            broadcastFakeMessage(playerName, vanished);
        }
    }

    public boolean isVanished(UUID playerId) {
        return vanishedPlayers.contains(playerId);
    }

    public boolean hasPersistedVanish(UUID playerId) {
        if (playerFileStorage == null) return false;
        PlayerFile playerFile = playerFileStorage.getPlayer(playerId);
        return playerFile != null && playerFile.isVanished();
    }

    public boolean toggleVanish(UUID playerId, String playerName) {
        return toggleVanish(playerId, playerName, null, null);
    }

    /**
     * Toggle vanish with store/ref for deferred world-thread component updates.
     *
     * Rejection cases (order matters):
     *   1. Cooldown: if TOGGLE_COOLDOWN_MS hasn't elapsed since last successful toggle, reject
     *   2. In-progress: if a previous toggle's deferred world.execute() hasn't finished, reject
     *
     * The toggleInProgress guard is CLAIMED here and RELEASED only inside the
     * world.execute() lambda after the Invulnerable mutation completes (or
     * immediately if no mutation was needed).
     */
    public boolean toggleVanish(UUID playerId, String playerName,
                               Store<EntityStore> store, Ref<EntityStore> ref) {
        long now = System.currentTimeMillis();
        Long last = lastToggleMs.get(playerId);
        if (last != null && now - last < TOGGLE_COOLDOWN_MS) {
            logger.fine("[Vanish] Toggle cooldown active for " + playerName +
                " (" + (TOGGLE_COOLDOWN_MS - (now - last)) + "ms remaining), ignoring");
            return isVanished(playerId);
        }

        if (!toggleInProgress.add(playerId)) {
            logger.fine("[Vanish] Toggle already in progress for " + playerName + ", ignoring");
            return isVanished(playerId);
        }

        // Guard is claimed — from this point, release MUST happen exactly once.
        final AtomicBoolean released = new AtomicBoolean(false);
        final Runnable guardRelease = () -> {
            if (released.compareAndSet(false, true)) {
                toggleInProgress.remove(playerId);
            }
        };

        try {
            boolean nowVanished = !isVanished(playerId);
            lastToggleMs.put(playerId, now);
            setVanishedInternal(playerId, playerName, nowVanished, store, ref, guardRelease);
            return nowVanished;
        } catch (Throwable t) {
            // Any exception: release guard so future toggles aren't blocked forever.
            guardRelease.run();
            throw t;
        }
    }

    public boolean onPlayerJoin(PlayerRef joiningPlayer) {
        if (joiningPlayer == null) return false;

        UUID playerId = joiningPlayer.getUuid();
        PluginConfig config = configManager.getConfig();

        boolean wasVanished = false;
        if (config.vanish.persistOnReconnect && playerFileStorage != null) {
            PlayerFile playerFile = playerFileStorage.getPlayer(playerId);
            if (playerFile != null && playerFile.isVanished()) {
                vanishedPlayers.add(playerId);
                wasVanished = true;
                logger.info("Restored vanish state for " + joiningPlayer.getUsername() + " (was vanished before disconnect)");
                updateVisibilityForAll(playerId, true);
                if (config.vanish.hideFromList) {
                    updatePlayerListForAll(playerId, true);
                }
            }
        }

        for (UUID vanishedId : vanishedPlayers) {
            if (vanishedId.equals(playerId)) continue;
            try {
                joiningPlayer.getHiddenPlayersManager().hidePlayer(vanishedId);
                if (config.vanish.hideFromList) {
                    RemoveFromServerPlayerList packet = new RemoveFromServerPlayerList(new UUID[] { vanishedId });
                    joiningPlayer.getPacketHandler().write(packet);
                }
            } catch (Exception e) {
                logger.warning("Failed to hide vanished player from " + joiningPlayer.getUsername() + ": " + e.getMessage());
            }
        }
        return wasVanished;
    }

    public void sendVanishReminder(PlayerRef playerRef) {
        if (playerRef == null) return;
        PluginConfig config = configManager.getConfig();
        if (!config.vanish.showReminderOnJoin) return;
        String message = configManager.getMessage("vanishReminder");
        playerRef.sendMessage(MessageFormatter.format(message));
        logger.fine("Sent vanish reminder to " + playerRef.getUsername());
    }

    public void onPlayerReady(Store<EntityStore> store, Ref<EntityStore> ref) {
        if (store == null || ref == null || !ref.isValid()) return;
        PluginConfig config = configManager.getConfig();
        try {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) return;
            PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            if (playerRef != null) {
                UUID playerId = playerRef.getUuid();
                playerStoreRefs.put(playerId, new PlayerStoreRef(store, ref));
                if (config.vanish.mobImmunity && vanishedPlayers.contains(playerId)) {
                    try {
                        store.putComponent(ref, Invulnerable.getComponentType(), Invulnerable.INSTANCE);
                        if (configManager.isDebugEnabled()) {
                            logger.info("[Vanish] Applied invulnerability for reconnecting vanished player: " + playerRef.getUsername());
                        }
                    } catch (Exception e) {
                        logger.warning("[Vanish] Failed to apply invulnerability on reconnect: " + e.getMessage());
                    }
                }
            }
            if (!config.vanish.hideFromMap) return;
            WorldMapTracker tracker = player.getWorldMapTracker();
            if (tracker != null) {
                tracker.setPlayerMapFilter(pRef -> vanishedPlayers.contains(pRef.getUuid()));
            }
        } catch (Exception e) {
            logger.warning("Failed to set map filter for player: " + e.getMessage());
        }
    }

    public boolean onPlayerLeave(UUID playerId) {
        boolean wasVanished = vanishedPlayers.remove(playerId);
        PluginConfig config = configManager.getConfig();

        // Defensive: if a toggle guard was somehow leaked for this player, clear it.
        // Toggle races on a disconnecting player must not poison future reconnects.
        toggleInProgress.remove(playerId);
        lastToggleMs.remove(playerId);

        updateVisibilityForAll(playerId, false);

        if (wasVanished) {
            if (config.vanish.mobImmunity) {
                PlayerStoreRef psr = playerStoreRefs.get(playerId);
                if (psr != null && psr.ref != null && psr.ref.isValid()) {
                    try {
                        GodService godService = com.eliteessentials.EliteEssentials.getInstance().getGodService();
                        if (godService == null || !godService.isGodMode(playerId)) {
                            psr.store.removeComponent(psr.ref, Invulnerable.getComponentType());
                        }
                    } catch (Exception e) {
                        // ignore
                    }
                }
            }
        }
        playerStoreRefs.remove(playerId);
        return wasVanished;
    }

    private void updateMapFiltersForAll() {
        for (Map.Entry<UUID, PlayerStoreRef> entry : playerStoreRefs.entrySet()) {
            PlayerStoreRef psr = entry.getValue();
            if (psr.ref == null || !psr.ref.isValid()) continue;
            try {
                Player player = psr.store.getComponent(psr.ref, Player.getComponentType());
                if (player == null) continue;
                WorldMapTracker tracker = player.getWorldMapTracker();
                if (tracker != null) {
                    tracker.setPlayerMapFilter(playerRef -> vanishedPlayers.contains(playerRef.getUuid()));
                }
            } catch (Exception e) {
                // ignore
            }
        }
    }

    private void broadcastFakeMessage(String playerName, boolean vanished) {
        try {
            Universe universe = Universe.get();
            if (universe == null) return;
            String messageKey = vanished ? "quitMessage" : "joinMessage";
            String message = configManager.getMessage(messageKey, "player", playerName);
            for (PlayerRef player : universe.getPlayers()) {
                try {
                    player.sendMessage(MessageFormatter.format(message));
                } catch (Exception e) {
                    // ignore
                }
            }
        } catch (Exception e) {
            logger.warning("Failed to broadcast fake vanish message: " + e.getMessage());
        }
    }

    private void updateAllPlayersInSinglePass(UUID targetId, boolean hide, PluginConfig config) {
        try {
            Universe universe = Universe.get();
            if (universe == null) return;
            RemoveFromServerPlayerList removePacket = config.vanish.hideFromList && hide
                ? new RemoveFromServerPlayerList(new UUID[] { targetId })
                : null;
            PlayerRef targetPlayer = null;
            for (PlayerRef player : universe.getPlayers()) {
                UUID pid = player.getUuid();
                if (pid.equals(targetId)) {
                    targetPlayer = player;
                    continue;
                }
                try {
                    if (hide) {
                        player.getHiddenPlayersManager().hidePlayer(targetId);
                    } else {
                        player.getHiddenPlayersManager().showPlayer(targetId);
                    }
                    if (config.vanish.hideFromList) {
                        if (hide && removePacket != null) {
                            player.getPacketHandler().write(removePacket);
                        }
                    }
                } catch (Exception e) {
                    logger.warning("Failed to update vanish state for " + player.getUsername() + ": " + e.getMessage());
                }
            }
            if (config.vanish.hideFromList && !hide && targetPlayer != null) {
                ServerPlayerListPlayer listPlayer = new ServerPlayerListPlayer(
                    targetPlayer.getUuid(),
                    targetPlayer.getUsername(),
                    targetPlayer.getWorldUuid(),
                    0,
                    false,
                    null
                );
                AddToServerPlayerList addPacket = new AddToServerPlayerList(new ServerPlayerListPlayer[] { listPlayer });
                for (PlayerRef player : universe.getPlayers()) {
                    if (player.getUuid().equals(targetId)) continue;
                    try {
                        player.getPacketHandler().write(addPacket);
                    } catch (Exception e) {
                        logger.warning("Failed to re-add to player list for " + player.getUsername() + ": " + e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            logger.warning("Failed to update vanish state for all players: " + e.getMessage());
        }
    }

    private void updateVisibilityForAll(UUID targetId, boolean hide) {
        try {
            Universe universe = Universe.get();
            if (universe == null) return;
            for (PlayerRef player : universe.getPlayers()) {
                if (player.getUuid().equals(targetId)) continue;
                try {
                    if (hide) {
                        player.getHiddenPlayersManager().hidePlayer(targetId);
                    } else {
                        player.getHiddenPlayersManager().showPlayer(targetId);
                    }
                } catch (Exception e) {
                    logger.warning("Failed to update visibility for " + player.getUsername() + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.warning("Failed to update visibility for all players: " + e.getMessage());
        }
    }

    private void updatePlayerListForAll(UUID targetId, boolean hide) {
        try {
            Universe universe = Universe.get();
            if (universe == null) return;
            PlayerRef targetPlayer = null;
            for (PlayerRef p : universe.getPlayers()) {
                if (p.getUuid().equals(targetId)) {
                    targetPlayer = p;
                    break;
                }
            }
            for (PlayerRef player : universe.getPlayers()) {
                if (player.getUuid().equals(targetId)) continue;
                try {
                    if (hide) {
                        RemoveFromServerPlayerList packet = new RemoveFromServerPlayerList(new UUID[] { targetId });
                        player.getPacketHandler().write(packet);
                    } else if (targetPlayer != null) {
                        ServerPlayerListPlayer listPlayer = new ServerPlayerListPlayer(
                            targetPlayer.getUuid(),
                            targetPlayer.getUsername(),
                            targetPlayer.getWorldUuid(),
                            0,
                            false,
                            null
                        );
                        AddToServerPlayerList packet = new AddToServerPlayerList(new ServerPlayerListPlayer[] { listPlayer });
                        player.getPacketHandler().write(packet);
                    }
                } catch (Exception e) {
                    logger.warning("Failed to update player list for " + player.getUsername() + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.warning("Failed to update player list for all players: " + e.getMessage());
        }
    }

    public Set<UUID> getVanishedPlayers() {
        return Set.copyOf(vanishedPlayers);
    }

    public int getVanishedCount() {
        return vanishedPlayers.size();
    }

    /**
     * Schedule the Invulnerable component mutation on the world thread.
     *
     * @param guardRelease callback to release toggleInProgress after the mutation
     *                     completes (may be null if caller doesn't hold the guard).
     * @return true if a deferred task was scheduled (guardRelease will fire from the
     *         lambda), false if not (caller must release the guard immediately).
     */
    private boolean updateMobImmunity(UUID playerId, boolean vanished,
                                      Store<EntityStore> callerStore, Ref<EntityStore> callerRef,
                                      Runnable guardRelease) {
        try {
            Store<EntityStore> store = callerStore;
            Ref<EntityStore> ref = callerRef;

            if (store == null || ref == null || !ref.isValid()) {
                PlayerStoreRef psr = playerStoreRefs.get(playerId);
                if (psr == null || psr.ref == null || !psr.ref.isValid()) return false;
                store = psr.store;
                ref = psr.ref;
            }

            EntityStore entityStore = store.getExternalData();
            if (entityStore == null) return false;

            World world = entityStore.getWorld();
            if (world == null) return false;

            final Store<EntityStore> fStore = store;
            final Ref<EntityStore> fRef = ref;

            // ALWAYS execute on the world thread — never mutate components cross-thread.
            // Guard is released inside this lambda (finally) so subsequent toggles are
            // blocked until THIS mutation has fully completed.
            world.execute(() -> {
                try {
                    if (!fRef.isValid()) return;
                    applyMobImmunitySync(fStore, fRef, playerId, vanished);
                } catch (Exception e) {
                    logger.warning("[Vanish] Failed to update invulnerability: " + e.getMessage());
                } finally {
                    if (guardRelease != null) guardRelease.run();
                }
            });
            return true;
        } catch (Exception e) {
            logger.warning("[Vanish] Failed to update invulnerability: " + e.getMessage());
            return false;
        }
    }

    private void applyMobImmunitySync(Store<EntityStore> store, Ref<EntityStore> ref,
                                      UUID playerId, boolean vanished) {
        if (vanished) {
            store.putComponent(ref, Invulnerable.getComponentType(), Invulnerable.INSTANCE);
            if (configManager.isDebugEnabled()) {
                logger.info("[Vanish] Applied invulnerability (damage immunity) for vanished player");
            }
        } else {
            GodService godService = com.eliteessentials.EliteEssentials.getInstance().getGodService();
            if (godService == null || !godService.isGodMode(playerId)) {
                try {
                    store.removeComponent(ref, Invulnerable.getComponentType());
                    if (configManager.isDebugEnabled()) {
                        logger.info("[Vanish] Removed invulnerability for unvanished player");
                    }
                } catch (IllegalArgumentException e) {
                    // Component not present, ignore
                }
            }
        }
    }
}
