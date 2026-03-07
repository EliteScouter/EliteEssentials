package com.eliteessentials.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads position (and world) from a player's Hytale save file (universe/players/{uuid}.json).
 * Used for /playerinfo to show "last saved" coordinates when the player is offline.
 * The same file is rewritten by spawn-on-logout to set spawn coordinates.
 */
public final class HytaleSaveFileReader {

    private HytaleSaveFileReader() {}

    /**
     * Result of reading position from a Hytale save file.
     */
    public static final class SavedPosition {
        public final double x;
        public final double y;
        public final double z;
        public final String world;

        public SavedPosition(double x, double y, double z, String world) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.world = world != null && !world.isEmpty() ? world : "?";
        }
    }

    /**
     * Read the last saved position from the player's Hytale save file.
     * Path: universe/players/{uuid}.json. Reads Components.Transform.Position and
     * optionally Components.Player.PlayerData.World.
     *
     * @param playerId player UUID
     * @return the saved position and world, or empty if file missing or invalid
     */
    public static Optional<SavedPosition> readPosition(UUID playerId) {
        if (playerId == null) return Optional.empty();
        Path path = Path.of("universe", "players", playerId.toString() + ".json");
        File file = path.toFile();
        if (!file.exists()) return Optional.empty();

        try (var reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonObject components = root.getAsJsonObject("Components");
            if (components == null) return Optional.empty();

            JsonObject transform = components.getAsJsonObject("Transform");
            if (transform == null) return Optional.empty();
            JsonObject position = transform.getAsJsonObject("Position");
            if (position == null) return Optional.empty();

            double x = getDouble(position, "X", 0);
            double y = getDouble(position, "Y", 0);
            double z = getDouble(position, "Z", 0);

            String world = "?";
            JsonObject player = components.getAsJsonObject("Player");
            if (player != null) {
                JsonObject playerData = player.getAsJsonObject("PlayerData");
                if (playerData != null && playerData.has("World")) {
                    world = playerData.get("World").getAsString();
                }
            }

            return Optional.of(new SavedPosition(x, y, z, world));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static double getDouble(JsonObject obj, String key, double defaultValue) {
        if (!obj.has(key)) return defaultValue;
        try {
            return obj.get(key).getAsDouble();
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
