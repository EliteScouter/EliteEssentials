package com.eliteessentials.integration.papi;

import at.helpch.placeholderapi.PlaceholderAPI;
import at.helpch.placeholderapi.expansion.PlaceholderExpansion;
import com.eliteessentials.EliteEssentials;
import com.eliteessentials.api.EconomyAPI;
import com.eliteessentials.model.Home;
import com.eliteessentials.model.Kit;
import com.eliteessentials.model.PlayerFile;
import com.eliteessentials.model.Warp;
import com.eliteessentials.permissions.PermissionService;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.services.DeathTrackingService;
import com.eliteessentials.services.HomeService;
import com.eliteessentials.services.KitService;
import com.eliteessentials.services.WarpService;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.DecimalFormat;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class EliteEssentialsExpansion extends PlaceholderExpansion {
    private static final PermissionService PERMISSIONS = PermissionService.get();
    private static final DecimalFormat TWO_DECIMAL = new DecimalFormat("0.00");

    private static final Pattern ARGUMENT_DELIMITER = Pattern.compile("_");

    private final EliteEssentials main;

    public EliteEssentialsExpansion(@NotNull final EliteEssentials main) {
        this.main = main;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "eliteessentials";
    }

    @Override
    public @NotNull String getAuthor() {
        return "EliteScouter";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.1.5";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onPlaceholderRequest(final PlayerRef playerRef, @NotNull final String input) {
        final HomeService homes = main.getHomeService();
        final KitService kits = main.getKitService();
        final WarpService warps = main.getWarpService();

        switch (input) {
            case "economy_enabled":
                return PlaceholderAPI.booleanValue(EconomyAPI.isEnabled());
            case "using_external_economy":
                return PlaceholderAPI.booleanValue(EconomyAPI.isUsingExternalEconomy());
            case "currency_name":
                return EconomyAPI.getCurrencyName();
            case "currency_name_plural":
                return EconomyAPI.getCurrencyNamePlural();
            case "currency_symbol":
                return EconomyAPI.getCurrencySymbol();
            case "balance":
                return String.valueOf(EconomyAPI.getBalance(playerRef.getUuid()));
            case "god":
                return PlaceholderAPI.booleanValue(main.getGodService().isGodMode(playerRef.getUuid()));
            case "vanished":
                return PlaceholderAPI.booleanValue(main.getVanishService().isVanished(playerRef.getUuid()));
            case "homes_num":
                return String.valueOf(homes.getHomeCount(playerRef.getUuid()));
            case "homes_max":
                return String.valueOf(homes.getMaxHomes(playerRef.getUuid()));
            case "homes_names":
                return String.join(", ", homes.getHomeNames(playerRef.getUuid()));
            case "all_kits_num":
                return String.valueOf(kits.getAllKits().size());
            case "all_kits_names":
                return kits.getAllKits().stream().map(Kit::getDisplayName).collect(Collectors.joining(", "));
            case "allowed_kits_num":
                return String.valueOf(kits.getAllKits().stream()
                        .filter(kit -> PERMISSIONS.hasPermission(playerRef.getUuid(), Permissions.kitAccess(kit.getId())))
                        .count());
            case "allowed_kits_names":
                return kits.getAllKits().stream()
                        .filter(kit -> PERMISSIONS.hasPermission(playerRef.getUuid(), Permissions.kitAccess(kit.getId())))
                        .map(Kit::getDisplayName)
                        .collect(Collectors.joining(", "));
            case "all_warps_num":
                return String.valueOf(warps.getWarpCount());
            case "all_warps_names":
                return String.join(", ", warps.getAllWarps().keySet());
            case "allowed_warps_num":
                return String.valueOf(warps.getAllWarpsList().stream()
                        .filter(warp -> PermissionService.get().canAccessWarp(playerRef.getUuid(), warp.getName(), warp.getPermission()))
                        .count());
            case "allowed_warps_names":
                return String.valueOf(warps.getAllWarpsList().stream()
                        .filter(warp -> PermissionService.get().canAccessWarp(playerRef.getUuid(), warp.getName(), warp.getPermission()))
                        .map(Warp::getName)
                        .collect(Collectors.joining(", ")));
        }

        final String[] args = ARGUMENT_DELIMITER.split(input);

        if (args.length <= 2) {
            return null;
        }

        switch (args[0]) {
            case "warp":
                final Warp warp = warps.getWarp(args[1]).orElse(null);

                if (warp == null) {
                    return null;
                }

                switch (args[2]) {
                    case "name": return warp.getName();
                    case "description": return warp.getDescription();
                    case "permission": return warp.getPermission().toString();
                    case "createdat": return String.valueOf(warp.getCreatedBy());
                    case "createdby": return warp.getCreatedBy();
                    case "coords": return warp.getLocation().getBlockX() + " " + warp.getLocation().getBlockY() + " " + warp.getLocation().getBlockZ();
                    case "x": return twoDec(warp.getLocation().getX());
                    case "y": return twoDec(warp.getLocation().getY());
                    case "z": return twoDec(warp.getLocation().getZ());
                    case "yaw": return twoDec(warp.getLocation().getYaw());
                    case "pitch": return twoDec(warp.getLocation().getPitch());
                    case "world": return warp.getLocation().getWorld();
                }

            case "kit":
                final Kit kit = kits.getKit(args[1]);

                if (kit == null) {
                    return null;
                }

                switch (args[2]) {
                    case "name": return kit.getDisplayName();
                    case "id": return kit.getId();
                    case "description": return kit.getDescription();
                    case "icon": return kit.getIcon();
                    case "cooldown": return String.valueOf(kit.getCooldown());
                    case "remainingcooldown": return String.valueOf(kits.getRemainingCooldown(playerRef.getUuid(), kit.getId()));
                    case "items": return String.valueOf(kit.getItems().size());
                }

            case "home":
                final Home home = homes.getHome(playerRef.getUuid(), args[1]).orElse(null);

                if (home == null) {
                    return null;
                }

                switch (args[2]) {
                    case "name": return home.getName();
                    case "createdat": return String.valueOf(home.getCreatedAt());
                    case "coords": return home.getLocation().getBlockX() + " " + home.getLocation().getBlockY() + " " + home.getLocation().getBlockZ();
                    case "x": return twoDec(home.getLocation().getX());
                    case "y": return twoDec(home.getLocation().getY());
                    case "z": return twoDec(home.getLocation().getZ());
                    case "yaw": return twoDec(home.getLocation().getYaw());
                    case "pitch": return twoDec(home.getLocation().getPitch());
                    case "world": return home.getLocation().getWorld();
                }
        }

        return null;
    }

    @NotNull
    private static String twoDec(final double num) {
        return TWO_DECIMAL.format(num);
    }
}
