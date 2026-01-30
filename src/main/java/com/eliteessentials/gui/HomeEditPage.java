package com.eliteessentials.gui;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.model.Home;
import com.eliteessentials.permissions.PermissionService;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.services.BackService;
import com.eliteessentials.services.HomeService;
import com.eliteessentials.util.MessageFormatter;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Optional;
import java.util.UUID;

/**
 * EliteEssentials Home Edit GUI.
 * Allows renaming or deleting a home, then returns to the homes list.
 */
public class HomeEditPage extends InteractiveCustomUIPage<HomeEditPage.HomeEditData> {

    private static final String ACTION_DONE = "done";
    private static final String ACTION_DELETE = "delete";
    private static final String ACTION_CANCEL = "cancel";
    private static final String ACTION_CANCEL_DELETE = "cancelDelete";

    private final HomeService homeService;
    private final BackService backService;
    private final ConfigManager configManager;
    private final World world;
    private final String homeName;
    private String pendingNewName;
    private boolean deleteConfirm;

    public HomeEditPage(PlayerRef playerRef, HomeService homeService, BackService backService,
                        ConfigManager configManager, World world, String homeName) {
        super(playerRef, CustomPageLifetime.CanDismiss, HomeEditData.CODEC);
        this.homeService = homeService;
        this.backService = backService;
        this.configManager = configManager;
        this.world = world;
        this.homeName = homeName;
    }

    @Override
    public void build(Ref<EntityStore> ref, UICommandBuilder commandBuilder,
                      UIEventBuilder eventBuilder, Store<EntityStore> store) {
        commandBuilder.append("Pages/EliteEssentials_HomeEditPage.ui");

        commandBuilder.set("#PageTitleLabel.Text", configManager.getMessage("gui.HomeEditTitle"));
        commandBuilder.set("#HomeNameLabel.Text", configManager.getMessage("gui.HomeEditNameLabel"));
        commandBuilder.set("#HomeNameInput.PlaceholderText", configManager.getMessage("gui.HomeEditNamePlaceholder"));
        commandBuilder.set("#CancelButton.Text", configManager.getMessage("gui.HomeEditCancelButton"));
        commandBuilder.set("#DoneButton.Text", configManager.getMessage("gui.HomeEditRenameButton"));
        commandBuilder.set("#DangerZoneLabel.Text", configManager.getMessage("gui.HomeEditDangerTitle"));
        commandBuilder.set("#DangerZoneInfo.Text", configManager.getMessage("gui.HomeEditDangerBody"));

        Optional<Home> homeOpt = homeService.getHome(playerRef.getUuid(), homeName);
        if (homeOpt.isPresent()) {
            String existingName = homeOpt.get().getName();
            pendingNewName = existingName;
            commandBuilder.set("#HomeNameInput.Value", existingName);
        }
        commandBuilder.set("#DeleteButton.Text",
            deleteConfirm
                ? configManager.getMessage("gui.HomeEditDeleteConfirmButton")
                : configManager.getMessage("gui.HomeEditDeleteButton"));

        Player playerEntity = store.getComponent(ref, Player.getComponentType());
        if (playerEntity != null) {
            playerEntity.getPageManager().clearCustomPageAcknowledgements();
        }

        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#DoneButton",
            new EventData()
                .append("Action", ACTION_DONE)
                .append("@NewName", "#HomeNameInput.Value"),
            false
        );
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#DeleteButton",
            EventData.of("Action", ACTION_DELETE),
            false
        );
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.MouseExited,
            "#DeleteButton",
            EventData.of("Action", ACTION_CANCEL_DELETE),
            false
        );
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#CancelButton",
            EventData.of("Action", ACTION_CANCEL),
            false
        );
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, HomeEditData data) {
        if (data.newName != null) {
            pendingNewName = data.newName;
        }

        if (data.action == null || data.action.isEmpty()) {
            return;
        }

        switch (data.action) {
            case ACTION_DONE -> {
                deleteConfirm = false;
                HomeService.Result result = handleRename();
                if (result == HomeService.Result.NAME_TAKEN) {
                    this.close();
                    return;
                }
            }
            case ACTION_DELETE -> {
                if (!deleteConfirm) {
                    deleteConfirm = true;
                    updateDeleteButton();
                    return;
                }
                deleteConfirm = false;
                handleDelete();
            }
            case ACTION_CANCEL -> {
                deleteConfirm = false;
            }
            case ACTION_CANCEL_DELETE -> {
                if (deleteConfirm) {
                    deleteConfirm = false;
                    updateDeleteButton();
                }
                return;
            }
            default -> {
                return;
            }
        }

        openHomesPage(ref, store);
    }

    private HomeService.Result handleRename() {
        UUID playerId = playerRef.getUuid();
        boolean enabled = configManager.getConfig().homes.enabled;
        if (!PermissionService.get().canUseEveryoneCommand(playerId, Permissions.SETHOME, enabled)) {
            sendMessage(configManager.getMessage("noPermission"), "#FF5555");
            return HomeService.Result.INVALID_NAME;
        }

        String newName = pendingNewName == null ? "" : pendingNewName.trim();
        HomeService.Result result = homeService.renameHome(playerId, homeName, newName);
        switch (result) {
            case SUCCESS -> sendMessage(configManager.getMessage("homeRenamed", "name", newName), "#55FF55");
            case NAME_TAKEN -> sendMessage(configManager.getMessage("homeNameTaken", "name", newName), "#FF5555");
            case HOME_NOT_FOUND -> sendMessage(configManager.getMessage("homeNotFound", "name", homeName), "#FF5555");
            case INVALID_NAME -> sendMessage(configManager.getMessage("homeInvalidName"), "#FF5555");
            default -> sendMessage(configManager.getMessage("homeRenameFailed"), "#FF5555");
        }
        return result;
    }

    private void handleDelete() {
        UUID playerId = playerRef.getUuid();
        boolean enabled = configManager.getConfig().homes.enabled;
        if (!PermissionService.get().canUseEveryoneCommand(playerId, Permissions.DELHOME, enabled)) {
            sendMessage(configManager.getMessage("noPermission"), "#FF5555");
            return;
        }

        HomeService.Result result = homeService.deleteHome(playerId, homeName);
        switch (result) {
            case SUCCESS -> sendMessage(configManager.getMessage("homeDeleted", "name", homeName), "#55FF55");
            case HOME_NOT_FOUND -> sendMessage(configManager.getMessage("homeNotFound", "name", homeName), "#FF5555");
            default -> sendMessage(configManager.getMessage("homeDeleteFailed"), "#FF5555");
        }
    }

    private void openHomesPage(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player playerEntity = store.getComponent(ref, Player.getComponentType());
        if (playerEntity == null) {
            sendMessage(configManager.getMessage("homeEditOpenFailed"), "#FF5555");
            return;
        }
        HomeSelectionPage page = new HomeSelectionPage(playerRef, homeService, backService, configManager, world);
        playerEntity.getPageManager().openCustomPage(ref, store, page);
    }

    private void updateDeleteButton() {
        UICommandBuilder commandBuilder = new UICommandBuilder();
        commandBuilder.set("#DeleteButton.Text",
            deleteConfirm
                ? configManager.getMessage("gui.HomeEditDeleteConfirmButton")
                : configManager.getMessage("gui.HomeEditDeleteButton"));
        sendUpdate(commandBuilder, false);
    }

    private void sendMessage(String message, String color) {
        playerRef.sendMessage(MessageFormatter.formatWithFallback(message, color));
    }

    /**
     * Event data for home edit.
     */
    public static class HomeEditData {
        public static final BuilderCodec<HomeEditData> CODEC = BuilderCodec.builder(HomeEditData.class, HomeEditData::new)
            .append(new KeyedCodec<>("Action", Codec.STRING), (data, s) -> data.action = s, data -> data.action)
            .add()
            .append(new KeyedCodec<>("@NewName", Codec.STRING), (data, s) -> data.newName = s, data -> data.newName)
            .add()
            .build();

        private String action;
        private String newName;

        public String getAction() {
            return action;
        }

        public String getNewName() {
            return newName;
        }
    }
}
