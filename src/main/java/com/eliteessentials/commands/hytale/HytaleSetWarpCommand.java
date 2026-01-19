package com.eliteessentials.commands.hytale;

import com.eliteessentials.EliteEssentials;
import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.config.PluginConfig;
import com.eliteessentials.model.Location;
import com.eliteessentials.model.Warp;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.services.WarpService;
import com.eliteessentials.util.CommandPermissionUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.eliteessentials.commands.args.SimpleStringArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Command: /setwarp <name> [all|op]
 * Creates a new warp at the player's current location.
 * 
 * Permissions:
 * - eliteessentials.command.setwarp - Create/update warps (admin)
 */
public class HytaleSetWarpCommand extends AbstractPlayerCommand {

    private static final String COMMAND_NAME = "setwarp";
    
    private final WarpService warpService;
    private final RequiredArg<String> nameArg;

    public HytaleSetWarpCommand(WarpService warpService) {
        super(COMMAND_NAME, "Create a warp at your location (Admin)");
        this.warpService = warpService;
        this.nameArg = withRequiredArg("name", "Warp name", SimpleStringArg.WARP_NAME);
        
        // Permission check handled in execute() via CommandPermissionUtil
        
        // Add variant with permission argument
        addUsageVariant(new SetWarpWithPermCommand(warpService));
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref,
                          PlayerRef player, World world) {
        // /setwarp <name> - creates warp with default "all" permission
        String warpName = ctx.get(nameArg);
        doSetWarp(ctx, store, ref, player, world, warpName, "all", warpService);
    }
    
    /**
     * Variant: /setwarp <name> <permission>
     */
    private static class SetWarpWithPermCommand extends AbstractPlayerCommand {
        private final WarpService warpService;
        private final RequiredArg<String> nameArg;
        private final RequiredArg<String> permArg;
        
        SetWarpWithPermCommand(WarpService warpService) {
            super(COMMAND_NAME);
            this.warpService = warpService;
            this.nameArg = withRequiredArg("name", "Warp name", SimpleStringArg.WARP_NAME);
            this.permArg = withRequiredArg("permission", "all or op", SimpleStringArg.PERMISSION);
            
            // Permission check handled in execute() via CommandPermissionUtil
        }
        
        @Override
        protected boolean canGeneratePermission() {
            return false;
        }
        
        @Override
        protected void execute(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref,
                              PlayerRef player, World world) {
            doSetWarp(ctx, store, ref, player, world, ctx.get(nameArg), ctx.get(permArg), warpService);
        }
    }
    
    static void doSetWarp(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref,
                          PlayerRef player, World world, String warpName, String permStr, 
                          WarpService warpService) {
        ConfigManager configManager = EliteEssentials.getInstance().getConfigManager();
        PluginConfig config = configManager.getConfig();
        
        if (!CommandPermissionUtil.canExecuteAdmin(ctx, player, Permissions.SETWARP, config.warps.enabled)) {
            return;
        }
        
        // Block setting warps in instance worlds (temporary worlds that close)
        String worldName = world.getName();
        if (worldName.startsWith("instance-")) {
            ctx.sendMessage(Message.raw(configManager.getMessage("cannotSetWarpInInstance")).color("#FF5555"));
            return;
        }
        
        // Parse permission
        Warp.Permission permission;
        String permLower = permStr.toLowerCase();
        if ("op".equals(permLower) || "admin".equals(permLower)) {
            permission = Warp.Permission.OP;
        } else if ("all".equals(permLower) || "everyone".equals(permLower)) {
            permission = Warp.Permission.ALL;
        } else {
            ctx.sendMessage(Message.raw(configManager.getMessage("warpInvalidPermission", "value", permStr)).color("#FF5555"));
            return;
        }
        
        // Get player position
        TransformComponent transform = (TransformComponent) store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            ctx.sendMessage(Message.raw(configManager.getMessage("couldNotGetPosition")).color("#FF5555"));
            return;
        }
        
        Vector3d pos = transform.getPosition();
        HeadRotation headRotation = (HeadRotation) store.getComponent(ref, HeadRotation.getComponentType());
        Vector3f rotation = headRotation != null ? headRotation.getRotation() : new Vector3f(0, 0, 0);
        
        Location location = new Location(
            world.getName(),
            pos.getX(), pos.getY(), pos.getZ(),
            rotation.y, rotation.x  // yaw=rotation.y, pitch=rotation.x
        );
        
        // Check if warp already exists
        boolean isUpdate = warpService.warpExists(warpName);
        
        // Create/update warp
        String error = warpService.setWarp(warpName, location, permission, player.getUsername());
        
        if (error != null) {
            ctx.sendMessage(Message.raw(error).color("#FF5555"));
            return;
        }
        
        String permDisplay = permission == Warp.Permission.OP ? "OP only" : "everyone";
        String locationStr = String.format("%.1f, %.1f, %.1f", pos.getX(), pos.getY(), pos.getZ());
        
        if (isUpdate) {
            ctx.sendMessage(Message.raw(configManager.getMessage("warpUpdated", "name", warpName, "permission", permDisplay, "location", locationStr)).color("#55FF55"));
        } else {
            ctx.sendMessage(Message.raw(configManager.getMessage("warpCreated", "name", warpName, "permission", permDisplay, "location", locationStr)).color("#55FF55"));
        }
    }
}
