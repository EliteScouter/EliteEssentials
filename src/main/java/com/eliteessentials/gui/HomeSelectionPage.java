package com.eliteessentials.gui;

import com.eliteessentials.EliteEssentials;
import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.config.PluginConfig;
import com.eliteessentials.model.Home;
import com.eliteessentials.model.Location;
import com.eliteessentials.services.BackService;
import com.eliteessentials.services.CooldownService;
import com.eliteessentials.services.CostService;
import com.eliteessentials.services.HomeService;
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
import com.hypixel.hytale.server.core.entity.entities.Player;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * EliteEssentials Home Selection GUI.
 * Displays all player homes for clicking to teleport.
 * Uses the same warmup/cooldown/cost logic as the /home command.
 */
public class HomeSelectionPage extends InteractiveCustomUIPage<HomeSelectionPage.HomePageData> {

    private static final String COMMAND_NAME = "home";
    private static final String ACTION_TELEPORT = "Teleport";
    private static final String ACTION_EDIT = "Edit";
    
    private final HomeService homeService;
    private final BackService backService;
    private final ConfigManager configManager;
    private final World world;
    private int pageIndex = 0;

    public HomeSelectionPage(PlayerRef playerRef, HomeService homeService, BackService backService, 
                             ConfigManager configManager, World world) {
        super(playerRef, CustomPageLifetime.CanDismiss, HomePageData.CODEC);
        this.homeService = homeService;
        this.backService = backService;
        this.configManager = configManager;
        this.world = world;
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder commandBuilder,
                      UIEventBuilder eventBuilder, Store<EntityStore> store) {
        commandBuilder.append("Pages/EliteEssentials_HomePage.ui");

        UUID playerId = playerRef.getUuid();

        Map<String, Home> homes = homeService.getHomes(playerId);
        int maxHomes = homeService.getMaxHomes(playerId);
        String maxDisplay = maxHomes == Integer.MAX_VALUE ? "*" : String.valueOf(maxHomes);
        String title = configManager.getMessage("gui.HomesTitle",
            "count", String.valueOf(homes.size()),
            "max", maxDisplay);
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

        buildHomeList(commandBuilder, eventBuilder);
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, HomePageData data) {
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

        if (ACTION_EDIT.equals(data.action)) {
            if (data.home == null || data.home.isEmpty()) {
                return;
            }
            openEditPage(ref, store, data.home);
            return;
        }

        if (!ACTION_TELEPORT.equals(data.action)) {
            return;
        }

        if (data.home == null || data.home.isEmpty()) {
            return;
        }

        UUID playerId = playerRef.getUuid();
        PluginConfig config = configManager.getConfig();
        
        // Check if this is a delete action (D: prefix) or teleport (T: prefix)
        if (data.home.startsWith("D:")) {
            String homeName = data.home.substring(2);
            handleDeleteHome(playerId, homeName);
            return;
        }
        
        String homeName = data.home.startsWith("T:") ? data.home.substring(2) : data.home;
        
        Optional<Home> homeOpt = homeService.getHome(playerId, homeName);
        
        if (homeOpt.isEmpty()) {
            sendMessage(configManager.getMessage("homeNotFound", "name", homeName), "#FF5555");
            this.close();
            return;
        }

        Home home = homeOpt.get();
        
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
        double cost = config.homes.cost;
        if (costService != null && cost > 0) {
            if (!costService.canAfford(playerId, "home", cost)) {
                double effectiveCost = costService.getEffectiveCost(playerId, "home", cost);
                sendMessage(configManager.getMessage("notEnoughMoney", "cost", costService.formatCost(effectiveCost)), "#FF5555");
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
        Location loc = home.getLocation();
        World targetWorld = Universe.get().getWorld(loc.getWorld());
        if (targetWorld == null) {
            targetWorld = world;
        }
        final World finalWorld = targetWorld;
        final String finalHomeName = home.getName();
        final CostService finalCostService = costService;
        final double finalCost = cost;
        final int configCooldown = config.homes.cooldownSeconds;
        
        // For cross-world teleports, we must execute on the TARGET world's thread
        final boolean isCrossWorld = !world.getName().equals(finalWorld.getName());
        
        // Define the teleport action
        Runnable doTeleport = () -> {
            backService.pushLocation(playerId, currentLoc);
            
            Vector3d targetPos = new Vector3d(loc.getX(), loc.getY(), loc.getZ());
            Vector3f targetRot = new Vector3f(0, loc.getYaw(), 0);
            
            // Execute on the CURRENT world's thread (where player is now)
            world.execute(() -> {
                if (!ref.isValid()) return;
                
                // ALWAYS include world in Teleport constructor (even for same-world)
                Teleport teleport = new Teleport(finalWorld, targetPos, targetRot);
                
                store.putComponent(ref, Teleport.getComponentType(), teleport);
                
                // Charge cost after successful teleport
                if (finalCostService != null && finalCost > 0) {
                    EliteEssentials.getInstance().getPlayerService().removeMoney(playerId, finalCost);
                }
                
                // Set cooldown after successful teleport
                cooldownService.setCooldown(COMMAND_NAME, playerId, configCooldown);
                
                sendMessage(configManager.getMessage("homeTeleported", "name", finalHomeName), "#55FF55");
            });
        };

        // Get effective warmup (check bypass permission)
        int warmupSeconds = CommandPermissionUtil.getEffectiveWarmup(playerId, COMMAND_NAME, config.homes.warmupSeconds);
        
        if (warmupSeconds > 0) {
            sendMessage(configManager.getMessage("homeWarmup", "name", finalHomeName, "seconds", String.valueOf(warmupSeconds)), "#FFAA00");
        }
        
        warmupService.startWarmup(playerRef, currentPos, warmupSeconds, doTeleport, COMMAND_NAME, world, store, ref, false);
    }
    
    private void handleDeleteHome(UUID playerId, String homeName) {
        Optional<Home> homeOpt = homeService.getHome(playerId, homeName);
        
        if (homeOpt.isEmpty()) {
            sendMessage(configManager.getMessage("homeNotFound", "name", homeName), "#FF5555");
            return;
        }
        
        // Delete the home
        HomeService.Result result = homeService.deleteHome(playerId, homeName);
        
        if (result == HomeService.Result.SUCCESS) {
            sendMessage(configManager.getMessage("homeDeleted", "name", homeName), "#55FF55");
            // Refresh the GUI to show updated list
            refreshHomeList();
        } else {
            sendMessage(configManager.getMessage("homeDeleteFailed"), "#FF5555");
        }
    }
    
    private void refreshHomeList() {
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder events = new UIEventBuilder();
        
        UUID playerId = playerRef.getUuid();
        
        // Update title with new count
        Map<String, Home> homes = homeService.getHomes(playerId);
        int maxHomes = homeService.getMaxHomes(playerId);
        String maxDisplay = maxHomes == Integer.MAX_VALUE ? "*" : String.valueOf(maxHomes);
        String title = configManager.getMessage("gui.HomesTitle",
            "count", String.valueOf(homes.size()),
            "max", maxDisplay);
        cmd.set("#PageTitleLabel.Text", title);
        
        PaginationControl.setButtonLabels(
            cmd,
            "#Pagination",
            configManager.getMessage("gui.PaginationPrev"),
            configManager.getMessage("gui.PaginationNext")
        );
        PaginationControl.bind(events, "#Pagination");
        buildHomeList(cmd, events);
        
        this.sendUpdate(cmd, events, false);
    }

    private void openEditPage(Ref<EntityStore> ref, Store<EntityStore> store, String homeName) {
        Player playerEntity = store.getComponent(ref, Player.getComponentType());
        if (playerEntity == null) {
            sendMessage(configManager.getMessage("homeEditOpenFailed"), "#FF5555");
            return;
        }
        playerEntity.getPageManager().clearCustomPageAcknowledgements();
        HomeEditPage page = new HomeEditPage(playerRef, homeService, backService, configManager, world, homeName);
        playerEntity.getPageManager().openCustomPage(ref, store, page);
    }

    private void buildHomeList(UICommandBuilder commandBuilder, UIEventBuilder eventBuilder) {
        Map<String, Home> homes = homeService.getHomes(playerRef.getUuid());
        List<Home> homeList = new ArrayList<>(homes.values());
        homeList.sort(Comparator.comparing(Home::getName));

        if (homeList.isEmpty()) {
            commandBuilder.clear("#HomeCards");
            PaginationControl.setEmptyAndHide(commandBuilder, "#Pagination", configManager.getMessage("gui.PaginationLabel"));
            return;
        }
        int pageSize = Math.max(1, configManager.getConfig().gui.homesPerPage);
        String pageLabelFormat = configManager.getMessage("gui.PaginationLabel");
        int totalPages = (int) Math.ceil(homeList.size() / (double) pageSize);
        if (pageIndex >= totalPages) {
            pageIndex = totalPages - 1;
        }
        int start = pageIndex * pageSize;
        int end = Math.min(start + pageSize, homeList.size());

        commandBuilder.clear("#HomeCards");

        for (int i = start; i < end; i++) {
            Home home = homeList.get(i);
            int entryIndex = i - start;
            String selector = "#HomeCards[" + entryIndex + "]";

            commandBuilder.append("#HomeCards", "Pages/EliteEssentials_HomeEntry.ui");
            commandBuilder.set(selector + " #HomeName.Text", home.getName());

            String coords = String.format("%.0f, %.0f, %.0f",
                home.getLocation().getX(),
                home.getLocation().getY(),
                home.getLocation().getZ());
            commandBuilder.set(
                selector + " #HomeWorld.Text",
                configManager.getMessage(
                    "gui.HomeEntryWorld",
                    "world", home.getLocation().getWorld(),
                    "coords", coords
                )
            );
            commandBuilder.set(selector + " #HomeEditButton.Text", configManager.getMessage("gui.HomeEntryEdit"));
            commandBuilder.set(selector + " #HomeTeleportButton.Text", configManager.getMessage("gui.HomeEntryGo"));

            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                selector + " #HomeTeleportButton",
                new EventData()
                    .append("Action", ACTION_TELEPORT)
                    .append("Home", home.getName()),
                false
            );
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                selector + " #HomeEditButton",
                new EventData()
                    .append("Action", ACTION_EDIT)
                    .append("Home", home.getName()),
                false
            );

        }

        PaginationControl.updateOrHide(commandBuilder, "#Pagination", pageIndex, totalPages, pageLabelFormat);
    }

    private void sendMessage(String message, String color) {
        playerRef.sendMessage(MessageFormatter.formatWithFallback(message, color));
    }

    /**
     * Event data for home selection.
     */
    public static class HomePageData {
        public static final BuilderCodec<HomePageData> CODEC = BuilderCodec.builder(HomePageData.class, HomePageData::new)
                .append(new KeyedCodec<>("Home", Codec.STRING), (data, s) -> data.home = s, data -> data.home)
                .add()
                .append(new KeyedCodec<>("Action", Codec.STRING), (data, s) -> data.action = s, data -> data.action)
                .add()
                .append(new KeyedCodec<>("PageAction", Codec.STRING), (data, s) -> data.pageAction = s, data -> data.pageAction)
                .add()
                .build();

        private String home;
        private String action;
        private String pageAction;

        public String getHome() {
            return home;
        }
        
        public String getAction() {
            return action;
        }

        public String getPageAction() {
            return pageAction;
        }
    }

    private void updateList() {
        UICommandBuilder commandBuilder = new UICommandBuilder();
        UIEventBuilder eventBuilder = new UIEventBuilder();
        PaginationControl.setButtonLabels(
            commandBuilder,
            "#Pagination",
            configManager.getMessage("gui.PaginationPrev"),
            configManager.getMessage("gui.PaginationNext")
        );
        PaginationControl.bind(eventBuilder, "#Pagination");
        buildHomeList(commandBuilder, eventBuilder);
        sendUpdate(commandBuilder, eventBuilder, false);
    }
}
