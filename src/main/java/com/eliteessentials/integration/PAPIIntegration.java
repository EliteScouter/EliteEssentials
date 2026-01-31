package com.eliteessentials.integration;

import com.hypixel.hytale.server.core.universe.PlayerRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.logging.Logger;

public final class PAPIIntegration {
    private static final Logger LOGGER = Logger.getLogger("EliteEssentials");
    private static Method SET_PLACEHOLDERS;
    private static boolean available = false;

    private PAPIIntegration() {
        throw new AssertionError("This class cannot be instantiated.");
    }

    public static void register() {
        try {
            final Class<?> clazz = Class.forName("at.helpch.placeholderapi.PlaceholderAPI");
            SET_PLACEHOLDERS = clazz.getMethod("setPlaceholders", PlayerRef.class, String.class);
            available = true;
        } catch (Exception e) {
            LOGGER.info("[PlaceholderAPI] not found, placeholders will not be replaced in chat.");
            available = false;
        }
    }

    public static boolean available() {
        return available;
    }

    @Nullable
    public static String setPlaceholders(@Nullable final PlayerRef player, @NotNull final String text) {
        try {
            final Object result = SET_PLACEHOLDERS.invoke(null, player, text);

            if (result instanceof String replaced) {
                return replaced;
            }
        } catch (Exception ignored) {
        }

        return null;
    }
}
