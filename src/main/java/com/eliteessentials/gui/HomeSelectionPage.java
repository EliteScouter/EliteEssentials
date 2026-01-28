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
import java.util.logging.Logger;

/**
 * EliteEssentials Home Selection GUI.
 * Displays all player homes for clicking to teleport.
 * Uses the same warmup/cooldown/cost logic as the /home command.
 */
public class HomeSelectionPage extends InteractiveCustomUIPage<HomeSelectionPage.HomePageData> {

    private static final Logger logger = Logger.getLogger("EliteEssentials");
    private static final String COMMAND_NAME = "home";
    private static final String ACTION_TELEPORT = "Teleport";
    private static final String ACTION_EDIT = "Edit";
    
    private final HomeService homeService;
    private final BackService backService;
    private final ConfigManager configManager;
    private final World world;

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
        int maxHomes = configManager.getConfig().homes.maxHomes;
        String title = configManager.getMessage("guiHomesTitle",
            "count", String.valueOf(homes.size()),
            "max", String.valueOf(maxHomes));
        commandBuilder.set("#TitleLabel.Text", title);

        buildHomeList(commandBuilder, eventBuilder);
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, HomePageData data) {
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
        int maxHomes = configManager.getConfig().homes.maxHomes;
        String title = configManager.getMessage("guiHomesTitle",
            "count", String.valueOf(homes.size()),
            "max", String.valueOf(maxHomes));
        cmd.set("#TitleLabel.Text", title);
        
        // Clear and rebuild home list
        cmd.clear("#HomeCards");
        
        List<Home> homeList = new ArrayList<>(homes.values());
        homeList.sort(Comparator.comparing(Home::getName));
        
        for (int i = 0; i < homeList.size(); i++) {
            Home home = homeList.get(i);
            String selector = "#HomeCards[" + i + "]";

            cmd.append("#HomeCards", "Pages/EliteEssentials_HomeEntry.ui");
            cmd.set(selector + " #HomeName.Text", home.getName());

            String coords = String.format("%.0f, %.0f, %.0f",
                home.getLocation().getX(),
                home.getLocation().getY(),
                home.getLocation().getZ());
            cmd.set(selector + " #HomeWorld.Text", "World: " + home.getLocation().getWorld() + " at " + coords);

            events.addEventBinding(
                CustomUIEventBindingType.Activating,
                selector + " #HomeTeleportButton",
                new EventData()
                    .append("Action", ACTION_TELEPORT)
                    .append("Home", home.getName()),
                false
            );
            
            events.addEventBinding(
                CustomUIEventBindingType.Activating,
                selector + " #HomeEditButton",
                new EventData()
                    .append("Action", ACTION_EDIT)
                    .append("Home", home.getName()),
                false
            );

        }
        
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
            return;
        }

        for (int i = 0; i < homeList.size(); i++) {
            Home home = homeList.get(i);
            String selector = "#HomeCards[" + i + "]";

            commandBuilder.append("#HomeCards", "Pages/EliteEssentials_HomeEntry.ui");
            commandBuilder.set(selector + " #HomeName.Text", home.getName());

            String coords = String.format("%.0f, %.0f, %.0f",
                home.getLocation().getX(),
                home.getLocation().getY(),
                home.getLocation().getZ());
            commandBuilder.set(selector + " #HomeWorld.Text", "World: " + home.getLocation().getWorld() + " at " + coords);

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
                .build();

        private String home;
        private String action;

        public String getHome() {
            return home;
        }
        
        public String getAction() {
            return action;
        }
    }
}
