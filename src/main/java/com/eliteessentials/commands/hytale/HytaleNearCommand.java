package com.eliteessentials.commands.hytale;

import com.eliteessentials.EliteEssentials;
import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.config.PluginConfig;
import com.eliteessentials.permissions.PermissionService;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.services.NickService;
import com.eliteessentials.services.VanishService;
import com.eliteessentials.util.CommandPermissionUtil;
import com.eliteessentials.util.CommandSpyUtil;
import com.eliteessentials.util.MessageFormatter;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import org.joml.Vector3d;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nonnull;

/**
 * Command: /near
 * Lists other online players within a configurable distance of the sender,
 * sorted from closest to farthest, with the distance to each shown.
 *
 * Only players in the same world are considered (cross-world distance is
 * meaningless and reading a player's components off their world thread is
 * unsafe in the ECS).
 *
 * Aliases: /nearby
 *
 * Usage: /near
 * Permission: eliteessentials.command.misc.near (Everyone)
 */
public class HytaleNearCommand extends AbstractPlayerCommand {

    private static final String COMMAND_NAME = "near";

    private final ConfigManager configManager;

    public HytaleNearCommand(ConfigManager configManager) {
        super(COMMAND_NAME, "List nearby players");
        this.configManager = configManager;
        addAliases("nearby");
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                          @Nonnull PlayerRef player, @Nonnull World world) {
        CommandSpyUtil.notify(ctx);

        PluginConfig.NearConfig nearConfig = configManager.getConfig().near;

        // Permission check - everyone can use (with fallback to simple mode)
        if (!CommandPermissionUtil.canExecute(ctx, player, Permissions.NEAR, nearConfig.enabled)) {
            return;
        }

        // Get the sender's current position (safe - we're on their world thread)
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("couldNotGetPosition"), "#FF5555"));
            return;
        }
        Vector3d origin = transform.getPosition();

        int distance = nearConfig.distance;
        double maxDistanceSq = (double) distance * (double) distance;

        UUID senderId = player.getUuid();
        PermissionService perms = PermissionService.get();
        VanishService vanishService = EliteEssentials.getInstance().getVanishService();
        NickService nickService = EliteEssentials.getInstance().getNickService();

        // Whether the sender is allowed to see vanished players
        boolean canSeeVanished = perms.isAdmin(senderId) || perms.hasPermission(senderId, Permissions.VANISH);

        // Collect nearby players (same world only) with their distance
        List<NearbyPlayer> nearby = new ArrayList<>();
        Collection<PlayerRef> players = Universe.get().getPlayers();
        for (PlayerRef other : players) {
            if (other.getUuid().equals(senderId)) {
                continue; // Skip self
            }

            // Hide vanished players unless the sender may see them
            if (vanishService != null && vanishService.isVanished(other.getUuid()) && !canSeeVanished) {
                continue;
            }

            Ref<EntityStore> otherRef = other.getReference();
            if (otherRef == null || !otherRef.isValid()) {
                continue;
            }
            Store<EntityStore> otherStore = otherRef.getStore();
            if (otherStore == null) {
                continue;
            }

            // Only consider players in the same world. This keeps component
            // access on the current world thread (safe) and avoids meaningless
            // cross-world distances.
            EntityStore otherEntityStore = otherStore.getExternalData();
            World otherWorld = otherEntityStore != null ? otherEntityStore.getWorld() : null;
            if (otherWorld == null || !otherWorld.getName().equals(world.getName())) {
                continue;
            }

            TransformComponent otherTransform = otherStore.getComponent(otherRef, TransformComponent.getComponentType());
            if (otherTransform == null) {
                continue;
            }

            Vector3d pos = otherTransform.getPosition();
            double dx = pos.x - origin.x;
            double dy = pos.y - origin.y;
            double dz = pos.z - origin.z;
            double distSq = dx * dx + dy * dy + dz * dz;

            if (distSq <= maxDistanceSq) {
                String displayName = nickService != null
                        ? nickService.getDisplayName(other.getUuid(), other.getUsername())
                        : other.getUsername();
                nearby.add(new NearbyPlayer(displayName, (int) Math.round(Math.sqrt(distSq))));
            }
        }

        if (nearby.isEmpty()) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("nearNoPlayers", "distance", String.valueOf(distance)), "#AAAAAA"));
            return;
        }

        // Sort closest first
        nearby.sort((a, b) -> Integer.compare(a.distance, b.distance));

        // Build the comma-separated list of "name (distance)" entries
        List<String> entries = new ArrayList<>(nearby.size());
        for (NearbyPlayer np : nearby) {
            entries.add(configManager.getMessage("nearEntry",
                "player", np.name,
                "distance", String.valueOf(np.distance)));
        }

        String header = configManager.getMessage("nearHeader",
            "count", String.valueOf(nearby.size()),
            "distance", String.valueOf(distance));
        ctx.sendMessage(MessageFormatter.formatWithFallback(header, "#55FF55"));

        String list = configManager.getMessage("nearPlayers", "players", String.join("&7, ", entries));
        ctx.sendMessage(MessageFormatter.formatWithFallback(list, "#FFFFFF"));
    }

    /** Simple holder for a nearby player's display name and rounded distance. */
    private static final class NearbyPlayer {
        final String name;
        final int distance;

        NearbyPlayer(String name, int distance) {
            this.name = name;
            this.distance = distance;
        }
    }
}
