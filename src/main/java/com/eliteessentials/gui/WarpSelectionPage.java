package com.eliteessentials.gui;

import com.eliteessentials.EliteEssentials;
import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.config.PluginConfig;
import com.eliteessentials.model.Location;
import com.eliteessentials.model.Warp;
import com.eliteessentials.permissions.PermissionService;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.services.BackService;
import com.eliteessentials.services.CooldownService;
import com.eliteessentials.services.CostService;
import com.eliteessentials.services.WarpService;
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
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * EliteEssentials Warp Selection GUI.
 * Shows NAME - Description with optional delete button for admins.
 */
public class WarpSelectionPage extends InteractiveCustomUIPage<WarpSelectionPage.WarpPageData> {

    private static final Logger logger = Logger.getLogger("EliteEssentials");
    private static final String COMMAND_NAME = "warp";
    private static final String ACTION_TELEPORT = "teleport";
    private static final String ACTION_DELETE = "delete";
    private static final String ACTION_CANCEL_DELETE = "cancelDelete";
    
    private final WarpService warpService;
    private final BackService backService;
    private final ConfigManager configManager;
    private final World world;
    private final boolean canDelete;
    private int pageIndex = 0;
    private final DeleteConfirmState deleteConfirmState;

    public WarpSelectionPage(PlayerRef playerRef, WarpService warpService, BackService backService, 
                             ConfigManager configManager, World world,
                             Ref<EntityStore> ref, Store<EntityStore> store) {
        super(playerRef, CustomPageLifetime.CanDismiss, WarpPageData.CODEC);
        this.warpService = warpService;
        this.backService = backService;
        this.configManager = configManager;
        this.world = world;
        this.deleteConfirmState = new DeleteConfirmState(
            configManager.getMessage("gui.WarpDeleteButton"),
            configManager.getMessage("gui.WarpDeleteConfirmButton"),
            250
        );
        
        // Check if player can delete warps
        PermissionService perms = PermissionService.get();
        this.canDelete = perms.canUseAdminCommand(playerRef.getUuid(), Permissions.DELWARP, true);
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder commandBuilder,
                      UIEventBuilder eventBuilder, Store<EntityStore> store) {
        commandBuilder.append("Pages/EliteEssentials_WarpPage.ui");
        
        String title = configManager.getMessage("gui.WarpsTitle");
        commandBuilder.set("#PageTitleLabel.Text", title);

        commandBuilder.clear("#Pagination");
        commandBuilder.append("#Pagination", "Pages/EliteEssentials_Pagination.ui");
        PaginationControl.setButtonLabels(
            commandBuilder,
            "#Pagination",
            configManager.getMessage("gui.PaginationPrev"),
            configManager.getMessage("gui.PaginationNext")
        );
        PaginationControl.bind(eventBuilder, "#Pagination");

        buildWarpList(commandBuilder, eventBuilder);
    }
    
    private void buildWarpList(UICommandBuilder commandBuilder, UIEventBuilder eventBuilder) {
        UUID playerId = playerRef.getUuid();
        PermissionService perms = PermissionService.get();
        int pageSize = Math.max(1, configManager.getConfig().gui.warpsPerPage);
        String pageLabelFormat = configManager.getMessage("gui.PaginationLabel");

        List<Warp> accessibleWarps = getAccessibleWarps(playerId, perms);
        commandBuilder.clear("#WarpCards");

        if (accessibleWarps.isEmpty()) {
            PaginationControl.setEmptyAndHide(commandBuilder, "#Pagination", pageLabelFormat);
            return;
        }

        int totalPages = (int) Math.ceil(accessibleWarps.size() / (double) pageSize);
        if (pageIndex >= totalPages) {
            pageIndex = totalPages - 1;
        }
        int start = pageIndex * pageSize;
        int end = Math.min(start + pageSize, accessibleWarps.size());

        for (int i = start; i < end; i++) {
            Warp warp = accessibleWarps.get(i);
            int entryIndex = i - start;
            String selector = "#WarpCards[" + entryIndex + "]";

            // Use different UI file based on delete permission
            if (canDelete) {
                commandBuilder.append("#WarpCards", "Pages/EliteEssentials_WarpEntry.ui");
                commandBuilder.set(selector + " #DeleteButton.Text", getDeleteButtonText(warp.getName()));
                eventBuilder.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    selector + " #DeleteButton",
                    new EventData()
                        .append("Action", ACTION_DELETE)
                        .append("Warp", warp.getName()),
                    false
                );
                eventBuilder.addEventBinding(
                    CustomUIEventBindingType.MouseExited,
                    selector + " #DeleteButton",
                    new EventData()
                        .append("Action", ACTION_CANCEL_DELETE)
                        .append("Warp", warp.getName()),
                    false
                );
            } else {
                commandBuilder.append("#WarpCards", "Pages/EliteEssentials_WarpEntryNoDelete.ui");
            }
            
            commandBuilder.set(selector + " #WarpName.Text", warp.getName());
            
            // Show description
            String description = warp.getDescription() != null ? warp.getDescription() : "";
            commandBuilder.set(selector + " #WarpDescription.Text", description);
            commandBuilder.set(selector + " #WarpButton.Text", configManager.getMessage("gui.WarpButton"));

            // Bind teleport button
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                selector + " #WarpButton",
                new EventData()
                    .append("Action", ACTION_TELEPORT)
                    .append("Warp", warp.getName()),
                false
            );
        }

        PaginationControl.updateOrHide(commandBuilder, "#Pagination", pageIndex, totalPages, pageLabelFormat);
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, WarpPageData data) {
        if (data.pageAction != null) {
            if ("Next".equalsIgnoreCase(data.pageAction)) {
                pageIndex++;
            } else if ("Prev".equalsIgnoreCase(data.pageAction)) {
                pageIndex = Math.max(0, pageIndex - 1);
            }
            updateList();
            return;
        }

        if (data.action == null || data.action.isEmpty()) {
            return;
        }

        UUID playerId = playerRef.getUuid();
        PluginConfig config = configManager.getConfig();
        PermissionService perms = PermissionService.get();
        String warpName = data.warp;

        switch (data.action) {
            case ACTION_DELETE -> {
                if (!deleteConfirmState.request(warpName)) {
                    updateDeleteButtons();
                    return;
                }
                deleteConfirmState.clear();
                handleDeleteWarp(playerId, warpName);
                return;
            }
            case ACTION_CANCEL_DELETE -> {
                if (deleteConfirmState.cancel(warpName)) {
                    updateDeleteButtons();
                }
                return;
            }
            case ACTION_TELEPORT -> {
                deleteConfirmState.clear();
            }
            default -> {
                return;
            }
        }
        
        Optional<Warp> warpOpt = warpService.getWarp(warpName);
        
        if (warpOpt.isEmpty()) {
            sendMessage(configManager.getMessage("warpNotFound", "name", warpName), "#FF5555");
            this.close();
            return;
        }

        Warp warp = warpOpt.get();

        if (!perms.canAccessWarp(playerId, warp.getName(), warp.getPermission())) {
            sendMessage(configManager.getMessage("warpNoPermission"), "#FF5555");
            this.close();
            return;
        }

        this.close();
        
        CooldownService cooldownService = EliteEssentials.getInstance().getCooldownService();
        
        if (!CommandPermissionUtil.canBypassCooldown(playerId, COMMAND_NAME)) {
            int cooldownRemaining = cooldownService.getCooldownRemaining(COMMAND_NAME, playerId);
            if (cooldownRemaining > 0) {
                sendMessage(configManager.getMessage("onCooldown", "seconds", String.valueOf(cooldownRemaining)), "#FF5555");
                return;
            }
        }
        
        CostService costService = EliteEssentials.getInstance().getCostService();
        double cost = config.warps.cost;
        if (costService != null && cost > 0) {
            if (!costService.canAfford(playerId, "warp", cost)) {
                double effectiveCost = costService.getEffectiveCost(playerId, "warp", cost);
                sendMessage(configManager.getMessage("notEnoughMoney", "cost", costService.formatCost(effectiveCost)), "#FF5555");
                return;
            }
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
        
        com.eliteessentials.model.Location currentLoc = new com.eliteessentials.model.Location(
            world.getName(),
            currentPos.getX(), currentPos.getY(), currentPos.getZ(),
            rotation.y, 0f
        );
        
        Location loc = warp.getLocation();
        World targetWorld = Universe.get().getWorld(loc.getWorld());
        if (targetWorld == null) {
            targetWorld = world;
        }
        final World finalWorld = targetWorld;
        final String finalWarpName = warp.getName();
        final CostService finalCostService = costService;
        final double finalCost = cost;
        final int configCooldown = config.warps.cooldownSeconds;
        
        Runnable doTeleport = () -> {
            backService.pushLocation(playerId, currentLoc);
            
            world.execute(() -> {
                Vector3d targetPos = new Vector3d(loc.getX(), loc.getY(), loc.getZ());
                Vector3f targetRot = new Vector3f(0, loc.getYaw(), 0);
                
                Teleport teleport = new Teleport(finalWorld, targetPos, targetRot);
                store.putComponent(ref, Teleport.getComponentType(), teleport);
                
                if (finalCostService != null && finalCost > 0) {
                    EliteEssentials.getInstance().getPlayerService().removeMoney(playerId, finalCost);
                }
                
                cooldownService.setCooldown(COMMAND_NAME, playerId, configCooldown);
                
                sendMessage(configManager.getMessage("warpTeleported", "name", finalWarpName), "#55FF55");
            });
        };

        int warmupSeconds = CommandPermissionUtil.getEffectiveWarmup(playerId, COMMAND_NAME, config.warps.warmupSeconds);
        
        if (warmupSeconds > 0) {
            sendMessage(configManager.getMessage("warpWarmup", "name", finalWarpName, "seconds", String.valueOf(warmupSeconds)), "#FFAA00");
        }
        
        warmupService.startWarmup(playerRef, currentPos, warmupSeconds, doTeleport, COMMAND_NAME, world, store, ref, false);
    }
    
    private void handleDeleteWarp(UUID playerId, String warpName) {
        PermissionService perms = PermissionService.get();
        if (!perms.canUseAdminCommand(playerId, Permissions.DELWARP, true)) {
            sendMessage(configManager.getMessage("noPermission"), "#FF5555");
            return;
        }
        
        Optional<Warp> warpOpt = warpService.getWarp(warpName);
        if (warpOpt.isEmpty()) {
            sendMessage(configManager.getMessage("warpNotFound", "name", warpName), "#FF5555");
            return;
        }
        
        boolean deleted = warpService.deleteWarp(warpName);
        
        if (deleted) {
            sendMessage(configManager.getMessage("warpDeleted", "name", warpName), "#55FF55");
            refreshWarpList();
        } else {
            sendMessage(configManager.getMessage("warpDeleteFailed"), "#FF5555");
        }
    }
    
    private void refreshWarpList() {
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder events = new UIEventBuilder();
        
        cmd.set("#PageTitleLabel.Text", configManager.getMessage("gui.WarpsTitle"));
        PaginationControl.setButtonLabels(
            cmd,
            "#Pagination",
            configManager.getMessage("gui.PaginationPrev"),
            configManager.getMessage("gui.PaginationNext")
        );
        PaginationControl.bind(events, "#Pagination");
        buildWarpList(cmd, events);
        
        this.sendUpdate(cmd, events, false);
    }

    private void sendMessage(String message, String color) {
        playerRef.sendMessage(MessageFormatter.formatWithFallback(message, color));
    }

    private String getDeleteButtonText(String warpName) {
        return deleteConfirmState.getLabel(warpName);
    }

    private List<Warp> getAccessibleWarps(UUID playerId, PermissionService perms) {
        List<Warp> accessibleWarps = new ArrayList<>();
        for (Warp warp : warpService.getAllWarps().values()) {
            if (perms.canAccessWarp(playerId, warp.getName(), warp.getPermission())) {
                accessibleWarps.add(warp);
            }
        }
        accessibleWarps.sort(Comparator.comparing(Warp::getName));
        return accessibleWarps;
    }

    private void updateDeleteButtons() {
        if (!canDelete) {
            return;
        }

        UICommandBuilder cmd = new UICommandBuilder();
        UUID playerId = playerRef.getUuid();
        PermissionService perms = PermissionService.get();
        List<Warp> accessibleWarps = getAccessibleWarps(playerId, perms);
        int pageSize = Math.max(1, configManager.getConfig().gui.warpsPerPage);
        int totalPages = (int) Math.ceil(accessibleWarps.size() / (double) pageSize);
        if (totalPages <= 0) {
            return;
        }
        if (pageIndex >= totalPages) {
            pageIndex = totalPages - 1;
        }
        int start = pageIndex * pageSize;
        int end = Math.min(start + pageSize, accessibleWarps.size());

        for (int i = start; i < end; i++) {
            Warp warp = accessibleWarps.get(i);
            int entryIndex = i - start;
            String selector = "#WarpCards[" + entryIndex + "] #DeleteButton.Text";
            cmd.set(selector, getDeleteButtonText(warp.getName()));
        }

        sendUpdate(cmd, false);
    }

    private void updateList() {
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder events = new UIEventBuilder();
        PaginationControl.setButtonLabels(
            cmd,
            "#Pagination",
            configManager.getMessage("gui.PaginationPrev"),
            configManager.getMessage("gui.PaginationNext")
        );
        PaginationControl.bind(events, "#Pagination");
        buildWarpList(cmd, events);
        sendUpdate(cmd, events, false);
    }

    public static class WarpPageData {
        public static final BuilderCodec<WarpPageData> CODEC = BuilderCodec.builder(WarpPageData.class, WarpPageData::new)
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

        public String getWarp() {
            return warp;
        }

        public String getAction() {
            return action;
        }

        public String getPageAction() {
            return pageAction;
        }
    }
}
