package com.eliteessentials.gui;

import com.eliteessentials.EliteEssentials;
import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.config.PluginConfig;
import com.eliteessentials.model.Location;
import com.eliteessentials.model.PlayerWarp;
import com.eliteessentials.permissions.PermissionService;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.services.BackService;
import com.eliteessentials.services.CooldownService;
import com.eliteessentials.services.CostService;
import com.eliteessentials.services.PlayerWarpService;
import com.eliteessentials.services.WarmupService;
import com.eliteessentials.util.CommandPermissionUtil;
import com.eliteessentials.util.MessageFormatter;
import com.eliteessentials.gui.components.PaginationControl;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.*;
import java.util.logging.Logger;

/**
 * Player Warp Selection GUI.
 * Shows player warps with Public/Private/All filter buttons.
 * Owners can delete their own warps from the GUI.
 */
public class PlayerWarpSelectionPage extends InteractiveCustomUIPage<PlayerWarpSelectionPage.PageData> {

    private static final Logger logger = Logger.getLogger("EliteEssentials");
    private static final String COMMAND_NAME = "pwarp";
    private static final String ACTION_TELEPORT = "teleport";
    private static final String ACTION_DELETE = "delete";
    private static final String ACTION_CANCEL_DELETE = "cancelDelete";
    private static final String ACTION_FILTER = "filter";

    private enum FilterMode { ALL, PUBLIC, PRIVATE }

    private final PlayerWarpService playerWarpService;
    private final BackService backService;
    private final ConfigManager configManager;
    private final World world;
    private int pageIndex = 0;
    private FilterMode filterMode = FilterMode.ALL;
    private final DeleteConfirmState deleteConfirmState;

    public PlayerWarpSelectionPage(PlayerRef playerRef, PlayerWarpService playerWarpService,
                                   BackService backService, ConfigManager configManager, World world,
                                   Ref<EntityStore> ref, Store<EntityStore> store) {
        super(playerRef, CustomPageLifetime.CanDismiss, PageData.CODEC);
        this.playerWarpService = playerWarpService;
        this.backService = backService;
        this.configManager = configManager;
        this.world = world;
        this.deleteConfirmState = new DeleteConfirmState(
            configManager.getMessage("gui.PlayerWarpDeleteButton"),
            configManager.getMessage("gui.PlayerWarpDeleteConfirmButton"),
            250
        );
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder commandBuilder,
                      UIEventBuilder eventBuilder, Store<EntityStore> store) {
        commandBuilder.append("Pages/EliteEssentials_WarpPage.ui");

        String title = configManager.getMessage("gui.PlayerWarpsTitle");
        commandBuilder.set("#PageTitleLabel.Text", title);

        // Pagination
        commandBuilder.clear("#Pagination");
        commandBuilder.append("#Pagination", "Pages/EliteEssentials_Pagination.ui");
        PaginationControl.setButtonLabels(commandBuilder, "#Pagination",
            configManager.getMessage("gui.PaginationPrev"),
            configManager.getMessage("gui.PaginationNext"));
        PaginationControl.bind(eventBuilder, "#Pagination");

        buildWarpList(commandBuilder, eventBuilder);
    }

    private void buildWarpList(UICommandBuilder cmd, UIEventBuilder events) {
        UUID playerId = playerRef.getUuid();
        int pageSize = Math.max(1, configManager.getConfig().gui.playerWarpsPerPage);
        String pageLabelFormat = configManager.getMessage("gui.PaginationLabel");

        List<PlayerWarp> warps = getFilteredWarps(playerId);
        cmd.clear("#WarpCards");

        if (warps.isEmpty()) {
            PaginationControl.setEmptyAndHide(cmd, "#Pagination", pageLabelFormat);
            return;
        }

        int totalPages = (int) Math.ceil(warps.size() / (double) pageSize);
        if (pageIndex >= totalPages) pageIndex = totalPages - 1;
        int start = pageIndex * pageSize;
        int end = Math.min(start + pageSize, warps.size());

        for (int i = start; i < end; i++) {
            PlayerWarp warp = warps.get(i);
            int entryIndex = i - start;
            String selector = "#WarpCards[" + entryIndex + "]";
            boolean isOwner = warp.getOwnerId().equals(playerId);

            if (isOwner) {
                cmd.append("#WarpCards", "Pages/EliteEssentials_WarpEntry.ui");
                cmd.set(selector + " #DeleteButton.Text", getDeleteButtonText(warp.getName()));
                events.addEventBinding(CustomUIEventBindingType.Activating, selector + " #DeleteButton",
                    new EventData().append("Action", ACTION_DELETE).append("Warp", warp.getName()), false);
                events.addEventBinding(CustomUIEventBindingType.MouseExited, selector + " #DeleteButton",
                    new EventData().append("Action", ACTION_CANCEL_DELETE).append("Warp", warp.getName()), false);
            } else {
                cmd.append("#WarpCards", "Pages/EliteEssentials_WarpEntryNoDelete.ui");
            }

            // Name field shows description (big blue text), description field shows "[Visibility] - PlayerName" (small gray text)
            String warpDesc = warp.getDescription();
            String nameText = (warpDesc != null && !warpDesc.isEmpty()) ? warpDesc : warp.getName();
            cmd.set(selector + " #WarpName.Text", nameText);
            String visTag = warp.isPublic()
                ? configManager.getMessage("gui.PlayerWarpPublicTag")
                : configManager.getMessage("gui.PlayerWarpPrivateTag");
            cmd.set(selector + " #WarpDescription.Text", visTag + " - " + warp.getOwnerName());
            cmd.set(selector + " #WarpButton.Text", configManager.getMessage("gui.PlayerWarpButton"));

            events.addEventBinding(CustomUIEventBindingType.Activating, selector + " #WarpButton",
                new EventData().append("Action", ACTION_TELEPORT).append("Warp", warp.getName()), false);
        }

        PaginationControl.updateOrHide(cmd, "#Pagination", pageIndex, totalPages, pageLabelFormat);
    }

    private List<PlayerWarp> getFilteredWarps(UUID playerId) {
        List<PlayerWarp> result;
        switch (filterMode) {
            case PUBLIC -> result = playerWarpService.getPublicWarps();
            case PRIVATE -> result = playerWarpService.getWarpsByOwner(playerId);
            default -> result = playerWarpService.getAccessibleWarps(playerId);
        }
        result.sort(Comparator.comparing(PlayerWarp::getName));
        return result;
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, PageData data) {
        if (data.pageAction != null) {
            if ("Next".equalsIgnoreCase(data.pageAction)) pageIndex++;
            else if ("Prev".equalsIgnoreCase(data.pageAction)) pageIndex = Math.max(0, pageIndex - 1);
            updateList();
            return;
        }
        if (data.action == null || data.action.isEmpty()) return;

        UUID playerId = playerRef.getUuid();
        PluginConfig config = configManager.getConfig();

        switch (data.action) {
            case ACTION_DELETE -> {
                if (!deleteConfirmState.request(data.warp)) { updateDeleteButtons(); return; }
                deleteConfirmState.clear();
                handleDeleteWarp(playerId, data.warp);
                return;
            }
            case ACTION_CANCEL_DELETE -> {
                if (deleteConfirmState.cancel(data.warp)) updateDeleteButtons();
                return;
            }
            case ACTION_FILTER -> {
                handleFilter(data.warp);
                return;
            }
            case ACTION_TELEPORT -> deleteConfirmState.clear();
            default -> { return; }
        }

        // Teleport logic
        handleGuiTeleport(ref, store, playerId, data.warp, config);
    }

    private void handleFilter(String filter) {
        if ("public".equalsIgnoreCase(filter)) filterMode = FilterMode.PUBLIC;
        else if ("private".equalsIgnoreCase(filter)) filterMode = FilterMode.PRIVATE;
        else filterMode = FilterMode.ALL;
        pageIndex = 0;
        updateList();
    }

    private void handleDeleteWarp(UUID playerId, String warpName) {
        boolean isAdmin = PermissionService.get().canUseAdminCommand(playerId, Permissions.PWARP_ADMIN_DELETE, true);
        PlayerWarpService.Result result = playerWarpService.deleteWarp(warpName, playerId, isAdmin);
        if (result == PlayerWarpService.Result.SUCCESS) {
            sendMessage(configManager.getMessage("pwarpDeleted", "name", warpName), "#55FF55");
            refreshList();
        } else if (result == PlayerWarpService.Result.NOT_OWNER) {
            sendMessage(configManager.getMessage("pwarpNotOwner"), "#FF5555");
        } else {
            sendMessage(configManager.getMessage("pwarpNotFound", "name", warpName), "#FF5555");
        }
    }

    private void handleGuiTeleport(Ref<EntityStore> ref, Store<EntityStore> store,
                                    UUID playerId, String warpName, PluginConfig config) {
        Optional<PlayerWarp> warpOpt = playerWarpService.getWarp(warpName);
        if (warpOpt.isEmpty()) {
            sendMessage(configManager.getMessage("pwarpNotFound", "name", warpName), "#FF5555");
            this.close();
            return;
        }
        PlayerWarp warp = warpOpt.get();
        if (!warp.canAccess(playerId) && !PermissionService.get().isAdmin(playerId)) {
            sendMessage(configManager.getMessage("pwarpNotFound", "name", warpName), "#FF5555");
            this.close();
            return;
        }
        this.close();

        CooldownService cooldownService = EliteEssentials.getInstance().getCooldownService();
        int effectiveCooldown = CommandPermissionUtil.getEffectiveTpCooldown(playerId, COMMAND_NAME, config.playerWarps.cooldownSeconds);
        if (effectiveCooldown > 0) {
            int remaining = cooldownService.getCooldownRemaining(COMMAND_NAME, playerId);
            if (remaining > 0) {
                sendMessage(configManager.getMessage("onCooldown", "seconds", String.valueOf(remaining)), "#FF5555");
                return;
            }
        }

        CostService costService = EliteEssentials.getInstance().getCostService();
        double cost = config.playerWarps.cost;
        if (costService != null && cost > 0 && !costService.canAfford(playerId, "pwarp", cost)) {
            double effectiveCost = costService.getEffectiveCost(playerId, "pwarp", cost);
            sendMessage(configManager.getMessage("notEnoughMoney", "cost", costService.formatCost(effectiveCost)), "#FF5555");
            return;
        }

        WarmupService warmupService = EliteEssentials.getInstance().getWarmupService();
        if (warmupService.hasActiveWarmup(playerId)) {
            sendMessage(configManager.getMessage("teleportInProgress"), "#FF5555");
            return;
        }

        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            sendMessage(configManager.getMessage("couldNotGetPosition"), "#FF5555");
            return;
        }

        Vector3d currentPos = transform.getPosition();
        HeadRotation headRotation = store.getComponent(ref, HeadRotation.getComponentType());
        Vector3f rotation = headRotation != null ? headRotation.getRotation() : new Vector3f(0, 0, 0);
        Location currentLoc = new Location(world.getName(), currentPos.getX(), currentPos.getY(), currentPos.getZ(), rotation.y, 0f);

        Location loc = warp.getLocation();
        World targetWorld = Universe.get().getWorld(loc.getWorld());
        if (targetWorld == null) targetWorld = world;
        final World finalWorld = targetWorld;
        final String finalName = warp.getName();
        final String finalOwner = warp.getOwnerName();
        final CostService fCostService = costService;
        final double fCost = cost;
        final int fCooldown = effectiveCooldown;

        Runnable doTeleport = () -> {
            backService.pushLocation(playerId, currentLoc);
            Vector3d targetPos = new Vector3d(loc.getX(), loc.getY(), loc.getZ());
            Vector3f targetRot = new Vector3f(0, loc.getYaw(), 0);
            com.eliteessentials.util.TeleportUtil.safeTeleport(world, finalWorld, targetPos, targetRot, playerRef,
                () -> {
                    if (fCostService != null) EliteEssentials.getInstance().getPlayerService().removeMoney(playerId, fCost);
                    if (fCooldown > 0) cooldownService.setCooldown(COMMAND_NAME, playerId, fCooldown);
                    sendMessage(configManager.getMessage("pwarpTeleported", "name", finalName, "owner", finalOwner), "#55FF55");
                },
                () -> sendMessage("&cTeleport failed - destination chunk could not be loaded.", "#FF5555")
            );
        };

        int warmupSeconds = CommandPermissionUtil.getEffectiveWarmup(playerId, COMMAND_NAME, config.playerWarps.warmupSeconds);
        if (warmupSeconds > 0) {
            sendMessage(configManager.getMessage("pwarpWarmup", "name", finalName, "seconds", String.valueOf(warmupSeconds)), "#FFAA00");
        }
        warmupService.startWarmup(playerRef, currentPos, warmupSeconds, doTeleport, COMMAND_NAME, world, store, ref, false);
    }

    private void refreshList() {
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder events = new UIEventBuilder();
        cmd.set("#PageTitleLabel.Text", configManager.getMessage("gui.PlayerWarpsTitle"));
        PaginationControl.setButtonLabels(cmd, "#Pagination",
            configManager.getMessage("gui.PaginationPrev"), configManager.getMessage("gui.PaginationNext"));
        PaginationControl.bind(events, "#Pagination");
        buildWarpList(cmd, events);
        this.sendUpdate(cmd, events, false);
    }

    private void updateList() {
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder events = new UIEventBuilder();
        PaginationControl.setButtonLabels(cmd, "#Pagination",
            configManager.getMessage("gui.PaginationPrev"), configManager.getMessage("gui.PaginationNext"));
        PaginationControl.bind(events, "#Pagination");
        buildWarpList(cmd, events);
        sendUpdate(cmd, events, false);
    }

    private void updateDeleteButtons() {
        UICommandBuilder cmd = new UICommandBuilder();
        UUID playerId = playerRef.getUuid();
        List<PlayerWarp> warps = getFilteredWarps(playerId);
        int pageSize = Math.max(1, configManager.getConfig().gui.playerWarpsPerPage);
        int totalPages = (int) Math.ceil(warps.size() / (double) pageSize);
        if (totalPages <= 0) return;
        if (pageIndex >= totalPages) pageIndex = totalPages - 1;
        int start = pageIndex * pageSize;
        int end = Math.min(start + pageSize, warps.size());
        for (int i = start; i < end; i++) {
            PlayerWarp warp = warps.get(i);
            if (warp.getOwnerId().equals(playerId)) {
                int entryIndex = i - start;
                cmd.set("#WarpCards[" + entryIndex + "] #DeleteButton.Text", getDeleteButtonText(warp.getName()));
            }
        }
        sendUpdate(cmd, false);
    }

    private void sendMessage(String message, String color) {
        playerRef.sendMessage(MessageFormatter.formatWithFallback(message, color));
    }

    private String getDeleteButtonText(String warpName) {
        return deleteConfirmState.getLabel(warpName);
    }

    public static class PageData {
        public static final BuilderCodec<PageData> CODEC = BuilderCodec.builder(PageData.class, PageData::new)
                .append(new KeyedCodec<>("Warp", Codec.STRING), (data, s) -> data.warp = s, data -> data.warp)
                .add()
                .append(new KeyedCodec<>("Action", Codec.STRING), (data, s) -> data.action = s, data -> data.action)
                .add()
                .append(new KeyedCodec<>("PageAction", Codec.STRING), (data, s) -> data.pageAction = s, data -> data.pageAction)
                .add()
                .build();

        private String warp;
        private String action;
        private String pageAction;

        public String getWarp() { return warp; }
        public String getAction() { return action; }
        public String getPageAction() { return pageAction; }
    }
}
