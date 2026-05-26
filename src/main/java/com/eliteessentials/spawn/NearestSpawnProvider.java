package com.eliteessentials.spawn;

import com.eliteessentials.storage.SpawnStorage;
import com.hypixel.hytale.math.vector.Transform;
import org.joml.Vector3d;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.spawn.ISpawnProvider;

import java.util.UUID;

/**
 * Custom spawn provider that returns the nearest spawn to the player's death location.
 * Used when perWorld=true and a world has multiple spawn points, so Hytale spawns
 * players directly at the correct location (single teleport, no flash to primary).
 */
public final class NearestSpawnProvider implements ISpawnProvider {

    private final SpawnStorage spawnStorage;
    private final DeathPositionCache deathPositionCache;
    private final String worldName;

    public NearestSpawnProvider(SpawnStorage spawnStorage, DeathPositionCache deathPositionCache, String worldName) {
        this.spawnStorage = spawnStorage;
        this.deathPositionCache = deathPositionCache;
        this.worldName = worldName;
    }

    @Override
    public Transform getSpawnPoint(World world, UUID playerId) {
        double x, z;
        double[] stored = deathPositionCache.peek(playerId);
        if (stored != null && stored.length >= 2) {
            x = stored[0];
            z = stored[1];
        } else {
            x = 0;
            z = 0;
        }

        SpawnStorage.SpawnData spawn = spawnStorage.getNearestSpawn(worldName, x, z);
        if (spawn == null) {
            spawn = spawnStorage.getPrimarySpawn(worldName);
        }
        if (spawn == null) {
            return new Transform(new Vector3d(0, 100, 0), new Rotation3f(0, 0, 0));
        }
        deathPositionCache.putChosenSpawn(playerId, spawn.name);
        return new Transform(
                new Vector3d(spawn.x, spawn.y, spawn.z),
                new Rotation3f(0, spawn.yaw, 0)
        );
    }

    @Override
    public Transform[] getSpawnPoints() {
        return spawnStorage.getSpawns(worldName).stream()
                .map(s -> new Transform(
                        new Vector3d(s.x, s.y, s.z),
                        new Rotation3f(0, s.yaw, 0)))
                .toArray(Transform[]::new);
    }

    @Override
    public boolean isWithinSpawnDistance(Vector3d position, double distance) {
        SpawnStorage.SpawnData primary = spawnStorage.getPrimarySpawn(worldName);
        if (primary == null) return false;
        double dx = primary.x - position.x;
        double dz = primary.z - position.z;
        return (dx * dx + dz * dz) <= distance * distance;
    }
}
