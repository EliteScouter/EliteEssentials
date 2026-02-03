package com.eliteessentials.integration.papi;

import com.hypixel.hytale.server.core.universe.PlayerRef;
import org.jetbrains.annotations.NotNull;

public interface PlaceholderAPI {
    @NotNull
    String setPlaceholders(final PlayerRef ref, @NotNull final String text);

    @NotNull
    String setRelationPlaceholders(final PlayerRef one, final PlayerRef two, @NotNull final String message);
}
