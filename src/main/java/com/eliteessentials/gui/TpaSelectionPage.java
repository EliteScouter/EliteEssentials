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
 */
public class TpaSelectionPage extends InteractiveCustomUIPage<TpaSelectionPage.TpaPageData> {

    public enum Mode {
        TPA,
        TPAHERE
    }

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

    private void buildPlayerList(UICommandBuilder commandBuilder, UIEventBuilder eventBuilder) {
        commandBuilder.clear("#PlayerCards");

        List<PlayerRef> players = new ArrayList<>(Universe.get().getPlayers());
        UUID selfId = playerRef.getUuid();
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

        if (players.isEmpty()) {
            commandBuilder.appendInline("#PlayerCards",
                "Label { Text: \"" + configManager.getMessage("gui.TpaEmpty") + "\"; Style: (Alignment: Center); }");
            PaginationControl.setEmptyAndHide(commandBuilder, "#Pagination", configManager.getMessage("gui.PaginationLabel"));
            return;
        }

        int pageSize = Math.max(1, configManager.getConfig().gui.playersPerTpaPage);
        int totalPages = (int) Math.ceil(players.size() / (double) pageSize);
        if (pageIndex >= totalPages) {
            pageIndex = totalPages - 1;
        }

        int start = pageIndex * pageSize;
        int end = Math.min(start + pageSize, players.size());

        for (int i = start; i < end; i++) {
            PlayerRef target = players.get(i);
            int entryIndex = i - start;
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

        PaginationControl.updateOrHide(commandBuilder, "#Pagination", pageIndex, totalPages, configManager.getMessage("gui.PaginationLabel"));
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
            .build();

        private String targetId;
        private String targetName;
        private String pageAction;
        private String searchQuery;

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
    }
}
