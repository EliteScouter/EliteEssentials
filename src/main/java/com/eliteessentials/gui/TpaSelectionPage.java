package com.eliteessentials.gui;

import com.eliteessentials.EliteEssentials;
import com.eliteessentials.api.EconomyAPI;
import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.config.PluginConfig;
import com.eliteessentials.model.TpaRequest;
import com.eliteessentials.permissions.PermissionService;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.services.CostService;
import com.eliteessentials.services.TpaService;
import com.eliteessentials.services.VanishService;
import com.eliteessentials.util.MessageFormatter;
import com.eliteessentials.gui.components.PaginationControl;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * UI page for selecting a player to send TPA/TPAHERE requests.
 * Also shows pending incoming requests at the top for easy accept/deny.
 */
public class TpaSelectionPage extends InteractiveCustomUIPage<TpaSelectionPage.TpaPageData> {

    public enum Mode {
        TPA,
        TPAHERE
    }

    private static final String ACTION_ACCEPT = "accept";
    private static final String ACTION_DENY = "deny";

    private final Mode mode;
    private final TpaService tpaService;
    private final ConfigManager configManager;
    private String searchQuery = "";
    private int pageIndex = 0;

    public TpaSelectionPage(PlayerRef playerRef, Mode mode, TpaService tpaService, ConfigManager configManager) {
        super(playerRef, CustomPageLifetime.CanDismiss, TpaPageData.CODEC);
        this.mode = mode;
        this.tpaService = tpaService;
        this.configManager = configManager;
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder commandBuilder,
                      UIEventBuilder eventBuilder, Store<EntityStore> store) {
        commandBuilder.append("Pages/EliteEssentials_TpaPage.ui");

        String title = mode == Mode.TPA
            ? configManager.getMessage("gui.TpaTitle")
            : configManager.getMessage("gui.TpahereTitle");
        commandBuilder.set("#PageTitleLabel.Text", title);

        commandBuilder.clear("#Pagination");
        commandBuilder.append("#Pagination", "Pages/EliteEssentials_Pagination.ui");
        PaginationControl.setButtonLabels(
            commandBuilder,
            "#Pagination",
            configManager.getMessage("gui.PaginationPrev"),
            configManager.getMessage("gui.PaginationNext")
        );

        eventBuilder.addEventBinding(
            CustomUIEventBindingType.ValueChanged,
            "#SearchInput",
            EventData.of("@SearchQuery", "#SearchInput.Value")
        );
        PaginationControl.bind(eventBuilder, "#Pagination");

        buildPlayerList(commandBuilder, eventBuilder);
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, TpaPageData data) {
        // Handle accept/deny actions for pending requests
        if (data.action != null && data.requesterId != null) {
            UUID requesterId;
            try {
                requesterId = UUID.fromString(data.requesterId);
            } catch (IllegalArgumentException e) {
                return;
            }
            
            if (ACTION_ACCEPT.equals(data.action)) {
                handleAcceptRequest(requesterId, ref, store);
                return;
            } else if (ACTION_DENY.equals(data.action)) {
                handleDenyRequest(requesterId);
                return;
            }
        }

        if (data.targetId != null && !data.targetId.isEmpty()) {
            handleRequest(data.targetId, data.targetName);
            return;
        }

        if (data.searchQuery != null) {
            searchQuery = data.searchQuery.trim().toLowerCase();
            pageIndex = 0;
            updateList();
            return;
        }

        if (data.pageAction != null) {
            if ("Next".equalsIgnoreCase(data.pageAction)) {
                pageIndex++;
            } else if ("Prev".equalsIgnoreCase(data.pageAction)) {
                pageIndex = Math.max(0, pageIndex - 1);
            }
            updateList();
        }
    }

    private void handleAcceptRequest(UUID requesterId, Ref<EntityStore> ref, Store<EntityStore> store) {
        UUID playerId = playerRef.getUuid();
        
        // Accept the request through the service
        var requestOpt = tpaService.acceptRequestFrom(playerId, requesterId);
        
        if (requestOpt.isEmpty()) {
            sendMessage(configManager.getMessage("tpaNoPending"), "#FF5555");
            updateList();
            return;
        }
        
        TpaRequest request = requestOpt.get();
        PlayerRef requester = Universe.get().getPlayer(requesterId);
        
        if (requester == null || !requester.isValid()) {
            sendMessage(configManager.getMessage("tpaPlayerOffline", "player", request.getRequesterName()), "#FF5555");
            updateList();
            return;
        }
        
        // Close GUI first
        this.close();
        
        // Notify both players
        sendMessage(configManager.getMessage("tpaAccepted", "player", request.getRequesterName()), "#55FF55");
        requester.sendMessage(MessageFormatter.formatWithFallback(
            configManager.getMessage("tpaRequestAccepted", "player", playerRef.getUsername()), "#55FF55"));
        
        // Execute the teleport using the TpaAcceptHelper
        TpaAcceptHelper.executeTeleport(playerRef, ref, store, requester, request, 
            configManager, EliteEssentials.getInstance().getBackService());
    }

    private void handleDenyRequest(UUID requesterId) {
        UUID playerId = playerRef.getUuid();
        
        // Check if request still exists
        List<TpaRequest> pending = tpaService.getPendingRequests(playerId);
        TpaRequest request = pending.stream()
            .filter(r -> r.getRequesterId().equals(requesterId))
            .findFirst()
            .orElse(null);
        
        if (request == null) {
            sendMessage(configManager.getMessage("tpaNoPending"), "#FF5555");
            updateList();
            return;
        }
        
        // Actually deny the request
        tpaService.denyRequestFrom(playerId, requesterId);
        
        PlayerRef requester = Universe.get().getPlayer(requesterId);
        
        sendMessage(configManager.getMessage("tpaDenied", "player", request.getRequesterName()), "#FFAA00");
        
        if (requester != null) {
            requester.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("tpaRequestDenied", "player", playerRef.getUsername()), "#FF5555"));
        }
        
        // Refresh the list to remove the denied request
        updateList();
    }

    private void buildPlayerList(UICommandBuilder commandBuilder, UIEventBuilder eventBuilder) {
        commandBuilder.clear("#PlayerCards");

        UUID selfId = playerRef.getUuid();
        
        // First, show pending incoming requests at the top
        List<TpaRequest> pendingRequests = tpaService.getPendingRequests(selfId);
        int pendingCount = 0;
        
        for (TpaRequest request : pendingRequests) {
            String selector = "#PlayerCards[" + pendingCount + "]";
            commandBuilder.append("#PlayerCards", "Pages/EliteEssentials_TpaPendingEntry.ui");
            
            String label = configManager.getMessage("gui.TpaPendingFrom", "player", request.getRequesterName());
            commandBuilder.set(selector + " #RequesterName.Text", label);
            commandBuilder.set(selector + " #AcceptButton.Text", configManager.getMessage("gui.TpaAcceptButton"));
            commandBuilder.set(selector + " #DenyButton.Text", configManager.getMessage("gui.TpaDenyButton"));
            
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                selector + " #AcceptButton",
                new EventData()
                    .append("Action", ACTION_ACCEPT)
                    .append("RequesterId", request.getRequesterId().toString())
            );
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                selector + " #DenyButton",
                new EventData()
                    .append("Action", ACTION_DENY)
                    .append("RequesterId", request.getRequesterId().toString())
            );
            
            pendingCount++;
        }

        // Now show available players to send requests to
        List<PlayerRef> players = new ArrayList<>(Universe.get().getPlayers());
        if (!configManager.isDebugEnabled()) {
            players.removeIf(p -> p.getUuid().equals(selfId));
        }

        VanishService vanishService = EliteEssentials.getInstance().getVanishService();
        boolean canSeeVanished = canSeeVanishedPlayers();
        if (vanishService != null && !canSeeVanished) {
            players.removeIf(p -> vanishService.isVanished(p.getUuid()));
        }

        if (!searchQuery.isEmpty()) {
            players.removeIf(p -> !p.getUsername().toLowerCase().contains(searchQuery));
        }

        players.sort(Comparator.comparing(PlayerRef::getUsername, String.CASE_INSENSITIVE_ORDER));

        if (players.isEmpty() && pendingCount == 0) {
            commandBuilder.appendInline("#PlayerCards",
                "Label { Text: \"" + configManager.getMessage("gui.TpaEmpty") + "\"; Style: (Alignment: Center); }");
            PaginationControl.setEmptyAndHide(commandBuilder, "#Pagination", configManager.getMessage("gui.PaginationLabel"));
            return;
        }

        int pageSize = Math.max(1, configManager.getConfig().gui.playersPerTpaPage);
        // Adjust page size to account for pending requests shown
        int availableSlots = Math.max(1, pageSize - pendingCount);
        int totalPages = players.isEmpty() ? 1 : (int) Math.ceil(players.size() / (double) availableSlots);
        if (pageIndex >= totalPages) {
            pageIndex = totalPages - 1;
        }

        int start = pageIndex * availableSlots;
        int end = Math.min(start + availableSlots, players.size());

        for (int i = start; i < end; i++) {
            PlayerRef target = players.get(i);
            int entryIndex = pendingCount + (i - start);
            String selector = "#PlayerCards[" + entryIndex + "]";

            commandBuilder.append("#PlayerCards", "Pages/EliteEssentials_TpaEntry.ui");
            commandBuilder.set(selector + " #PlayerName.Text", target.getUsername());
            commandBuilder.set(selector + " #PlayerActionButton.Text", configManager.getMessage("gui.TpaRequestButton"));

            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                selector + " #PlayerActionButton",
                new EventData()
                    .append("TargetId", target.getUuid().toString())
                    .append("TargetName", target.getUsername())
            );
        }

        // Only show pagination if there are more players than fit on one page
        if (players.size() > availableSlots) {
            PaginationControl.updateOrHide(commandBuilder, "#Pagination", pageIndex, totalPages, configManager.getMessage("gui.PaginationLabel"));
        } else {
            PaginationControl.setEmptyAndHide(commandBuilder, "#Pagination", configManager.getMessage("gui.PaginationLabel"));
        }
    }

    private void updateList() {
        UICommandBuilder commandBuilder = new UICommandBuilder();
        UIEventBuilder eventBuilder = new UIEventBuilder();
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.ValueChanged,
            "#SearchInput",
            EventData.of("@SearchQuery", "#SearchInput.Value")
        );
        PaginationControl.setButtonLabels(
            commandBuilder,
            "#Pagination",
            configManager.getMessage("gui.PaginationPrev"),
            configManager.getMessage("gui.PaginationNext")
        );
        PaginationControl.bind(eventBuilder, "#Pagination");
        buildPlayerList(commandBuilder, eventBuilder);
        sendUpdate(commandBuilder, eventBuilder, false);
    }

    private void handleRequest(String targetIdStr, String targetName) {
        UUID targetId;
        try {
            targetId = UUID.fromString(targetIdStr);
        } catch (IllegalArgumentException e) {
            sendMessage(configManager.getMessage("playerNotFound", "player", targetIdStr), "#FF5555");
            return;
        }

        PlayerRef target = Universe.get().getPlayer(targetId);
        if (target == null) {
            String name = (targetName == null || targetName.isBlank()) ? targetIdStr : targetName;
            sendMessage(configManager.getMessage("tpaPlayerOffline", "player", name), "#FF5555");
            return;
        }

        PluginConfig config = configManager.getConfig();
        UUID playerId = playerRef.getUuid();
        PermissionService perms = PermissionService.get();
        boolean enabled = config.tpa.enabled;

        String permission = mode == Mode.TPA ? Permissions.TPA : Permissions.TPAHERE;
        if (!perms.canUseEveryoneCommand(playerId, permission, enabled)) {
            sendMessage(configManager.getMessage("noPermission"), "#FF5555");
            return;
        }

        String commandName = mode == Mode.TPA ? "tpa" : "tpahere";
        double cost = mode == Mode.TPA ? config.tpa.cost : config.tpa.tpahereCost;
        if (!chargeCostIfNeeded(commandName, cost)) {
            return;
        }

        TpaService.Result result = (mode == Mode.TPA)
            ? tpaService.createRequest(playerId, playerRef.getUsername(), targetId, target.getUsername())
            : tpaService.createRequest(playerId, playerRef.getUsername(), targetId, target.getUsername(), TpaRequest.Type.TPAHERE);

        switch (result) {
            case REQUEST_SENT -> {
                String sentMsg = mode == Mode.TPA
                    ? configManager.getMessage("tpaRequestSent", "player", target.getUsername())
                    : configManager.getMessage("tpahereRequestSent", "player", target.getUsername());
                sendMessage(sentMsg, "#55FF55");

                String receivedMsg = mode == Mode.TPA
                    ? configManager.getMessage("tpaRequestReceived", "player", playerRef.getUsername())
                    : configManager.getMessage("tpahereRequestReceived", "player", playerRef.getUsername());
                target.sendMessage(MessageFormatter.formatWithFallback(receivedMsg, "#FFFF55"));
                target.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("tpaRequestInstructions"), "#AAAAAA"));
            }
            case SELF_REQUEST -> sendMessage(configManager.getMessage("tpaSelfRequest"), "#FF5555");
            case ALREADY_PENDING -> sendMessage(configManager.getMessage("tpaAlreadyPending"), "#FF5555");
            default -> sendMessage(configManager.getMessage("tpaRequestFailed"), "#FF5555");
        }

        this.close();
    }

    private boolean chargeCostIfNeeded(String commandName, double cost) {
        if (cost <= 0 || !EconomyAPI.isEnabled()) {
            return true;
        }

        UUID playerId = playerRef.getUuid();
        CostService costService = EliteEssentials.getInstance().getCostService();
        if (costService != null && costService.canBypassCost(playerId, commandName)) {
            return true;
        }

        double balance = EconomyAPI.getBalance(playerId);
        if (balance < cost) {
            String message = configManager.getMessage("costInsufficientFunds",
                "cost", String.format("%.2f", cost),
                "balance", String.format("%.2f", balance),
                "currency", EconomyAPI.getCurrencyNamePlural());
            sendMessage(message, "#FF5555");
            return false;
        }

        if (!EconomyAPI.withdraw(playerId, cost)) {
            sendMessage(configManager.getMessage("costFailed"), "#FF5555");
            return false;
        }

        String message = configManager.getMessage("costCharged",
            "cost", String.format("%.2f", cost),
            "currency", cost == 1.0 ? EconomyAPI.getCurrencyName() : EconomyAPI.getCurrencyNamePlural());
        sendMessage(message, "#AAAAAA");
        return true;
    }

    private void sendMessage(String message, String color) {
        playerRef.sendMessage(MessageFormatter.formatWithFallback(message, color));
    }

    private boolean canSeeVanishedPlayers() {
        UUID playerId = playerRef.getUuid();
        PermissionService perms = PermissionService.get();
        return perms.isAdmin(playerId) || perms.hasPermission(playerId, Permissions.VANISH);
    }

    /**
     * Event data for TPA selection.
     */
    public static class TpaPageData {
        public static final BuilderCodec<TpaPageData> CODEC = BuilderCodec.builder(TpaPageData.class, TpaPageData::new)
            .append(new KeyedCodec<>("TargetId", Codec.STRING), (data, s) -> data.targetId = s, data -> data.targetId)
            .add()
            .append(new KeyedCodec<>("TargetName", Codec.STRING), (data, s) -> data.targetName = s, data -> data.targetName)
            .add()
            .append(new KeyedCodec<>("PageAction", Codec.STRING), (data, s) -> data.pageAction = s, data -> data.pageAction)
            .add()
            .append(new KeyedCodec<>("@SearchQuery", Codec.STRING), (data, s) -> data.searchQuery = s, data -> data.searchQuery)
            .add()
            .append(new KeyedCodec<>("Action", Codec.STRING), (data, s) -> data.action = s, data -> data.action)
            .add()
            .append(new KeyedCodec<>("RequesterId", Codec.STRING), (data, s) -> data.requesterId = s, data -> data.requesterId)
            .add()
            .build();

        private String targetId;
        private String targetName;
        private String pageAction;
        private String searchQuery;
        private String action;
        private String requesterId;

        public String getTargetId() {
            return targetId;
        }
        
        public String getTargetName() {
            return targetName;
        }

        public String getPageAction() {
            return pageAction;
        }

        public String getSearchQuery() {
            return searchQuery;
        }
        
        public String getAction() {
            return action;
        }
        
        public String getRequesterId() {
            return requesterId;
        }
    }
}
