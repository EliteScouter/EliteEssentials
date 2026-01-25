package com.eliteessentials.gui;

import com.eliteessentials.EliteEssentials;
import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.config.PluginConfig;
import com.eliteessentials.model.Location;
import com.eliteessentials.model.Warp;
import com.eliteessentials.permissions.PermissionService;
import com.eliteessentials.services.BackService;
import com.eliteessentials.services.CooldownService;
import com.eliteessentials.services.CostService;
import com.eliteessentials.services.WarpService;
import com.eliteessentials.services.WarmupService;
import com.eliteessentials.util.CommandPermissionUtil;
import com.eliteessentials.util.MessageFormatter;
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
 * Displays all accessible warps for the player to click and teleport to.
 * Uses the same warmup/cooldown/cost logic as the /warp command.
 */
public class WarpSelectionPage extends InteractiveCustomUIPage<WarpSelectionPage.WarpPageData> {

    private static final Logger logger = Logger.getLogger("EliteEssentials");
    private static final String COMMAND_NAME = "warp";
    
    private final WarpService warpService;
    private final BackService backService;
    private final ConfigManager configManager;
    private final World world;

    public WarpSelectionPage(PlayerRef playerRef, WarpService warpService, BackService backService, 
                             ConfigManager configManager, World world) {
        super(playerRef, CustomPageLifetime.CanDismiss, WarpPageData.CODEC);
        this.warpService = warpService;
        this.backService = backService;
        this.configManager = configManager;
        this.world = world;
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder commandBuilder,
                      UIEventBuilder eventBuilder, Store<EntityStore> store) {
        commandBuilder.append("Pages/EliteEssentials_WarpPage.ui");

        UUID playerId = playerRef.getUuid();
        PermissionService perms = PermissionService.get();
        
        // Get all warps the player can access
        List<Warp> accessibleWarps = new ArrayList<>();
        for (Warp warp : warpService.getAllWarps().values()) {
            if (perms.canAccessWarp(playerId, warp.getName(), warp.getPermission())) {
                accessibleWarps.add(warp);
            }
        }
        accessibleWarps.sort(Comparator.comparing(Warp::getName));

        if (accessibleWarps.isEmpty()) {
            return;
        }

        // Build warp entries
        for (int i = 0; i < accessibleWarps.size(); i++) {
            Warp warp = accessibleWarps.get(i);
            String selector = "#WarpCards[" + i + "]";

            commandBuilder.append("#WarpCards", "Pages/EliteEssentials_WarpEntry.ui");
            commandBuilder.set(selector + " #WarpName.Text", warp.getName());
            
            // Show description (or empty if none)
            String description = warp.getDescription();
            commandBuilder.set(selector + " #WarpDescription.Text", description);

            // Show permission status for admins
            boolean isAdmin = perms.isAdmin(playerId);
            if (isAdmin && warp.isOpOnly()) {
                commandBuilder.set(selector + " #WarpStatus.Text", configManager.getMessage("guiWarpStatusOpOnly"));
            } else {
                commandBuilder.set(selector + " #WarpStatus.Text", "");
            }

            // Bind click event
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                selector,
                EventData.of("Warp", warp.getName())
            );
        }
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, WarpPageData data) {
        if (data.warp == null || data.warp.isEmpty()) {
            return;
        }

        UUID playerId = playerRef.getUuid();
        PluginConfig config = configManager.getConfig();
        PermissionService perms = PermissionService.get();
        
        Optional<Warp> warpOpt = warpService.getWarp(data.warp);
        
        if (warpOpt.isEmpty()) {
            sendMessage(configManager.getMessage("warpNotFound", "name", data.warp), "#FF5555");
            this.close();
            return;
        }

        Warp warp = warpOpt.get();

        // Check permission
        if (!perms.canAccessWarp(playerId, warp.getName(), warp.getPermission())) {
            sendMessage(configManager.getMessage("warpNoPermission"), "#FF5555");
            this.close();
            return;
        }

        // Close the GUI first
        this.close();
        
        CooldownService cooldownService = EliteEssentials.getInstance().getCooldownService();
        
        // Check cooldown (with bypass check)
        if (!CommandPermissionUtil.canBypassCooldown(playerId, COMMAND_NAME)) {
            int cooldownRemaining = cooldownService.getCooldownRemaining(COMMAND_NAME, playerId);
            if (cooldownRemaining > 0) {
                sendMessage(configManager.getMessage("onCooldown", "seconds", String.valueOf(cooldownRemaining)), "#FF5555");
                return;
            }
        }
        
        // Check if player can afford (but don't charge yet)
        CostService costService = EliteEssentials.getInstance().getCostService();
        double cost = config.warps.cost;
        if (costService != null && cost > 0) {
            if (!costService.canAfford(playerId, cost)) {
                sendMessage(configManager.getMessage("notEnoughMoney", "cost", costService.formatCost(cost)), "#FF5555");
                return;
            }
        }
        
        WarmupService warmupService = EliteEssentials.getInstance().getWarmupService();
        
        // Check if already warming up
        if (warmupService.hasActiveWarmup(playerId)) {
            sendMessage(configManager.getMessage("teleportInProgress"), "#FF5555");
            return;
        }
        
        // Get current position for warmup and /back
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            sendMessage(configManager.getMessage("couldNotGetPosition"), "#FF5555");
            return;
        }
        
        Vector3d currentPos = transform.getPosition();
        HeadRotation headRotation = store.getComponent(ref, HeadRotation.getComponentType());
        Vector3f rotation = headRotation != null ? headRotation.getRotation() : new Vector3f(0, 0, 0);
        
        Location currentLoc = new Location(
            world.getName(),
            currentPos.getX(), currentPos.getY(), currentPos.getZ(),
            rotation.y, rotation.x  // yaw=rotation.y, pitch=rotation.x
        );
        
        // Get target world
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
        
        // Define the teleport action
        Runnable doTeleport = () -> {
            backService.pushLocation(playerId, currentLoc);
            
            world.execute(() -> {
                Vector3d targetPos = new Vector3d(loc.getX(), loc.getY(), loc.getZ());
                Vector3f targetRot = new Vector3f(0, loc.getYaw(), 0);
                
                Teleport teleport = new Teleport(finalWorld, targetPos, targetRot);
                store.putComponent(ref, Teleport.getComponentType(), teleport);
                
                // Charge cost after successful teleport
                if (finalCostService != null && finalCost > 0) {
                    EliteEssentials.getInstance().getPlayerService().removeMoney(playerId, finalCost);
                }
                
                // Set cooldown after successful teleport
                cooldownService.setCooldown(COMMAND_NAME, playerId, configCooldown);
                
                sendMessage(configManager.getMessage("warpTeleported", "name", finalWarpName), "#55FF55");
            });
        };

        // Get effective warmup (check bypass permission)
        int warmupSeconds = CommandPermissionUtil.getEffectiveWarmup(playerId, COMMAND_NAME, config.warps.warmupSeconds);
        
        if (warmupSeconds > 0) {
            sendMessage(configManager.getMessage("warpWarmup", "name", finalWarpName, "seconds", String.valueOf(warmupSeconds)), "#FFAA00");
        }
        
        warmupService.startWarmup(playerRef, currentPos, warmupSeconds, doTeleport, COMMAND_NAME, world, store, ref, false);
    }

    private void sendMessage(String message, String color) {
        playerRef.sendMessage(MessageFormatter.formatWithFallback(message, color));
    }

    /**
     * Event data for warp selection.
     */
    public static class WarpPageData {
        public static final BuilderCodec<WarpPageData> CODEC = BuilderCodec.builder(WarpPageData.class, WarpPageData::new)
                .append(new KeyedCodec<>("Warp", Codec.STRING), (data, s) -> data.warp = s, data -> data.warp)
                .add()
                .build();

        private String warp;

        public String getWarp() {
            return warp;
        }
    }
}
