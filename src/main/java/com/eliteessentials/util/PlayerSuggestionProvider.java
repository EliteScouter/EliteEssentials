package com.eliteessentials.util;

import com.hypixel.hytale.server.core.NameMatching;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.suggestion.SuggestionProvider;
import com.hypixel.hytale.server.core.command.system.suggestion.SuggestionResult;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;

import java.util.Collection;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Reusable suggestion provider that suggests online player names with fuzzy matching.
 * Also provides a static findPlayer() helper that uses NameMatching.DEFAULT
 * (STARTS_WITH_IGNORE_CASE) so partial names like "eli" resolve to "EliteAdna".
 * 
 * Usage:
 *   withRequiredArg("player", "desc", ArgTypes.STRING)
 *       .suggest(PlayerSuggestionProvider.INSTANCE);
 * 
 *   PlayerRef target = PlayerSuggestionProvider.findPlayer(name);
 */
public final class PlayerSuggestionProvider implements SuggestionProvider {

    public static final PlayerSuggestionProvider INSTANCE = new PlayerSuggestionProvider();

    private PlayerSuggestionProvider() {}

    @Override
    public void suggest(@Nonnull CommandSender sender, @Nonnull String input, int numParams,
                        @Nonnull SuggestionResult result) {
        // Server 0.5.0+ removed SuggestionResult#fuzzySuggest. Filter manually
        // and add each match via #suggest. Keep behaviour close to the old
        // fuzzy match: case-insensitive contains.
        String needle = input == null ? "" : input.toLowerCase(java.util.Locale.ROOT);
        for (PlayerRef p : Universe.get().getPlayers()) {
            String name = p.getUsername();
            if (name == null) continue;
            if (needle.isEmpty() || name.toLowerCase(java.util.Locale.ROOT).contains(needle)) {
                result.suggest(name);
            }
        }
    }

    /**
     * Find an online player by partial or full name using NameMatching.DEFAULT
     * (STARTS_WITH_IGNORE_CASE). Typing "eli" will match "EliteAdna".
     */
    @Nullable
    public static PlayerRef findPlayer(@Nonnull String name) {
        Collection<PlayerRef> players = Universe.get().getPlayers();
        return NameMatching.DEFAULT.find(players, name, PlayerRef::getUsername);
    }
}
