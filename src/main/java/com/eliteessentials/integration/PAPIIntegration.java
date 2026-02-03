package com.eliteessentials.integration;

import com.eliteessentials.integration.papi.PlaceholderAPI;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.logging.Logger;

public final class PAPIIntegration {
    private static final Logger LOGGER = Logger.getLogger("EliteEssentials");
    private static PlaceholderAPI placeholderapi = null;
    private static boolean available = false;

    private PAPIIntegration() {
        throw new AssertionError("This class cannot be instantiated.");
    }

    public static void register() {
        try {
            final Class<?> papiClass = Class.forName("at.helpch.placeholderapi.PlaceholderAPI");
            placeholderapi = ((Class<PlaceholderAPI>) Class.forName("com.eliteessentials.integration.papi.PAPIImplementation")).getConstructor().newInstance();
            available = true;

            final Class<?> expansionClass = Class.forName("com.eliteessentials.integration.papi.EliteEssentialsExpansion");
            final Object expansion = expansionClass.getConstructor().newInstance();
            final Method register = expansionClass.getMethod("register");
            register.invoke(expansion);
            LOGGER.info("[PlaceholderAPI] Found, placeholders will be replaced in chat.");
        } catch (Exception e) {
            LOGGER.info("[PlaceholderAPI] Not found, placeholders will not be replaced in chat.");
            available = false;
        }
    }

    public static boolean available() {
        return available;
    }

    @NotNull
    public static String setPlaceholders(@Nullable final PlayerRef player, @NotNull final String text) {
        if (placeholderapi == null) {
            return text;
        }

        return placeholderapi.setPlaceholders(player, text);
    }

    @NotNull
    public static String setRelationalPlaceholders(@Nullable final PlayerRef one, @Nullable final PlayerRef two, @NotNull final String text) {
        if (placeholderapi == null) {
            return text;
        }

        return placeholderapi.setRelationPlaceholders(one, two, text);
    }
}
