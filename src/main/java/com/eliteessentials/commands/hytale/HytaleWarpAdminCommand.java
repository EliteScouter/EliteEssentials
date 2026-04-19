package com.eliteessentials.commands.hytale;

import com.eliteessentials.EliteEssentials;
import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.config.PluginConfig;
import com.eliteessentials.model.Location;
import com.eliteessentials.model.Warp;
import com.eliteessentials.permissions.PermissionService;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.services.WarpService;
import com.eliteessentials.util.MessageFormatter;
import com.eliteessentials.commands.args.SimpleStringArg;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nonnull;
import com.eliteessentials.util.CommandSpyUtil;

/**
 * Command: /warpadmin [subcommand] [args...]
 * Admin command for managing warps.
 * 
 * Usage:
 * - /warpadmin - Show help and warp list
 * - /warpadmin create <name> - Create warp at current location (default: all)
 * - /warpadmin create <name> <all|op> - Create warp with permission
 * - /warpadmin delete <name> - Delete a warp
 * - /warpadmin info <name> - Show warp details
 * 
 * Note: setperm and setdesc are separate commands due to Hytale API limitations:
 * - /warpsetperm <name> <all|op>
 * - /warpsetdesc <name> <description>
 */
public class HytaleWarpAdminCommand extends AbstractPlayerCommand {

    private static final String COMMAND_NAME = "warpadmin";
    
    private final WarpService warpService;

    public HytaleWarpAdminCommand(WarpService warpService) {
        super(COMMAND_NAME, "Manage warps (Admin)");
        this.warpService = warpService;
        
        addAliases("setwarp");
        
        // Only register subcommands with DIFFERENT parameter counts
        // 1 param: create <name> (default permission)
        addUsageVariant(new WarpSubCommand1Arg(warpService));
        // 2 params: create <name> <perm> OR delete/info handled by parsing
        addUsageVariant(new WarpSubCommand2Args(warpService));
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                          @Nonnull PlayerRef player, @Nonnull World world) {
        CommandSpyUtil.notify(ctx);
        ConfigManager configManager = EliteEssentials.getInstance().getConfigManager();
        
        if (!PermissionService.get().canUseAdminCommand(ctx.sender(), Permissions.WARPADMIN, true)) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("noPermission"), "#FF5555"));
            return;
        }
        
        showHelpStatic(ctx, configManager, warpService);
    }
    
    static void showHelpStatic(CommandContext ctx, ConfigManager configManager, WarpService warpService) {
        Map<String, Warp> warps = warpService.getAllWarps();
        
        ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("warpAdminTitle"), "#55FFFF"));
        ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("warpAdminTotal", "count", String.valueOf(warps.size())), "#AAAAAA"));
        ctx.sendMessage(Message.raw("").color("#FFFFFF"));
        
        if (!warps.isEmpty()) {
            List<Warp> sortedWarps = warps.values().stream()
                    .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                    .toList();
            
            for (Warp warp : sortedWarps) {
                String permColor = warp.isOpOnly() ? "#FF5555" : "#55FF55";
                String permText = warp.isOpOnly() ? "[OP]" : "[ALL]";
                
                ctx.sendMessage(Message.join(
                    Message.raw("  " + warp.getName()).color("#FFFFFF"),
                    Message.raw(" " + permText).color(permColor),
                    Message.raw(" - " + warp.getLocation().getWorld()).color("#AAAAAA")
                ));
            }
            ctx.sendMessage(Message.raw("").color("#FFFFFF"));
        }
        
        ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("warpAdminCommands"), "#FFAA00"));
        ctx.sendMessage(Message.raw("  /warpadmin create <name> [all|op]").color("#AAAAAA"));
        ctx.sendMessage(Message.raw("  /warpadmin delete <name>").color("#AAAAAA"));
        ctx.sendMessage(Message.raw("  /warpadmin info <name>").color("#AAAAAA"));
        ctx.sendMessage(Message.raw("  /warpsetperm <name> <all|op>").color("#AAAAAA"));
        ctx.sendMessage(Message.raw("  /warpsetdesc <name> <desc>").color("#AAAAAA"));
    }


    /**
     * Subcommand variant with 1 argument: /warpadmin <subcommand> <arg>
     * Handles: create <name>, delete <name>, info <name>
     */
    private static class WarpSubCommand1Arg extends AbstractPlayerCommand {
        private final WarpService warpService;
        private final RequiredArg<String> subcommandArg;
        private final RequiredArg<String> arg1;
        
        WarpSubCommand1Arg(WarpService warpService) {
            super(COMMAND_NAME);
            this.warpService = warpService;
            this.subcommandArg = withRequiredArg("subcommand", "create/delete/info", SimpleStringArg.WARP_SUBCOMMAND);
            this.arg1 = withRequiredArg("name", "Warp name", SimpleStringArg.WARP_NAME);
        }
        
        @Override
        protected boolean canGeneratePermission() {
            return false;
        }
        
        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                              @Nonnull PlayerRef player, @Nonnull World world) {
        CommandSpyUtil.notify(ctx);
            ConfigManager configManager = EliteEssentials.getInstance().getConfigManager();
            
            if (!PermissionService.get().canUseAdminCommand(ctx.sender(), Permissions.WARPADMIN, true)) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("noPermission"), "#FF5555"));
                return;
            }
            
            String subcommand = ctx.get(subcommandArg).toLowerCase();
            String warpName = ctx.get(arg1);
            
            switch (subcommand) {
                case "create" -> doCreate(ctx, store, ref, player, world, warpName, "all", warpService);
                case "delete" -> doDelete(ctx, warpName, warpService, configManager);
                case "info" -> doInfo(ctx, warpName, warpService, configManager);
                default -> {
                    ctx.sendMessage(Message.raw("Unknown subcommand: " + subcommand).color("#FF5555"));
                    ctx.sendMessage(Message.raw("").color("#FFFFFF"));
                    ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("warpAdminCommands"), "#FFAA00"));
                    ctx.sendMessage(Message.raw("  /warpadmin create <name> [all|op]").color("#AAAAAA"));
                    ctx.sendMessage(Message.raw("  /warpadmin delete <name>").color("#AAAAAA"));
                    ctx.sendMessage(Message.raw("  /warpadmin info <name>").color("#AAAAAA"));
                    ctx.sendMessage(Message.raw("  /warpsetperm <name> <all|op>").color("#AAAAAA"));
                    ctx.sendMessage(Message.raw("  /warpsetdesc <name> <desc>").color("#AAAAAA"));
                }
            }
        }
    }
    
    /**
     * Subcommand variant with 2 arguments: /warpadmin create <name> <perm>
     */
    private static class WarpSubCommand2Args extends AbstractPlayerCommand {
        private final WarpService warpService;
        private final RequiredArg<String> subcommandArg;
        private final RequiredArg<String> arg1;
        private final RequiredArg<String> arg2;
        
        WarpSubCommand2Args(WarpService warpService) {
            super(COMMAND_NAME);
            this.warpService = warpService;
            this.subcommandArg = withRequiredArg("subcommand", "create", SimpleStringArg.WARP_SUBCOMMAND);
            this.arg1 = withRequiredArg("name", "Warp name", SimpleStringArg.WARP_NAME);
            this.arg2 = withRequiredArg("permission", "all or op", SimpleStringArg.PERMISSION);
        }
        
        @Override
        protected boolean canGeneratePermission() {
            return false;
        }
        
        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                              @Nonnull PlayerRef player, @Nonnull World world) {
        CommandSpyUtil.notify(ctx);
            ConfigManager configManager = EliteEssentials.getInstance().getConfigManager();
            
            if (!PermissionService.get().canUseAdminCommand(ctx.sender(), Permissions.WARPADMIN, true)) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("noPermission"), "#FF5555"));
                return;
            }
            
            String subcommand = ctx.get(subcommandArg).toLowerCase();
            String warpName = ctx.get(arg1);
            String perm = ctx.get(arg2);
            
            if ("create".equals(subcommand)) {
                doCreate(ctx, store, ref, player, world, warpName, perm, warpService);
            } else {
                ctx.sendMessage(Message.raw("Unknown subcommand: " + subcommand).color("#FF5555"));
                ctx.sendMessage(Message.raw("").color("#FFFFFF"));
                ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("warpAdminCommands"), "#FFAA00"));
                ctx.sendMessage(Message.raw("  /warpadmin create <name> [all|op]").color("#AAAAAA"));
                ctx.sendMessage(Message.raw("  /warpadmin delete <name>").color("#AAAAAA"));
                ctx.sendMessage(Message.raw("  /warpadmin info <name>").color("#AAAAAA"));
                ctx.sendMessage(Message.raw("  /warpsetperm <name> <all|op>").color("#AAAAAA"));
                ctx.sendMessage(Message.raw("  /warpsetdesc <name> <desc>").color("#AAAAAA"));
            }
        }
    }
    
    static void doCreate(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref,
                         PlayerRef player, World world, String warpName, String permStr, 
                         WarpService warpService) {
        ConfigManager configManager = EliteEssentials.getInstance().getConfigManager();
        
        String worldName = world.getName();
        if (worldName.startsWith("instance-")) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("cannotSetWarpInInstance"), "#FF5555"));
            return;
        }
        
        Warp.Permission permission;
        String permLower = permStr.toLowerCase();
        if ("op".equals(permLower) || "admin".equals(permLower)) {
            permission = Warp.Permission.OP;
        } else {
            permission = Warp.Permission.ALL;
        }
        
        TransformComponent transform = (TransformComponent) store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("couldNotGetPosition"), "#FF5555"));
            return;
        }
        
        Vector3d pos = transform.getPosition();
        HeadRotation headRotation = (HeadRotation) store.getComponent(ref, HeadRotation.getComponentType());
        Vector3f rotation = headRotation != null ? headRotation.getRotation() : new Vector3f(0, 0, 0);
        
        Location location = new Location(
            world.getName(),
            pos.getX(), pos.getY(), pos.getZ(),
            rotation.y, 0f
        );
        
        boolean isUpdate = warpService.warpExists(warpName);
        
        if (!isUpdate && !warpService.canCreateWarp(player.getUuid())) {
            int limit = warpService.getWarpLimit(player.getUuid());
            int count = warpService.getWarpCount();
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                configManager.getMessage("warpLimitReached", "count", String.valueOf(count), "max", String.valueOf(limit)), 
                "#FF5555"));
            return;
        }
        
        String error = warpService.setWarp(warpName, location, permission, player.getUsername());
        
        if (error != null) {
            ctx.sendMessage(Message.raw(error).color("#FF5555"));
            return;
        }
        
        String permDisplay = permission == Warp.Permission.OP ? "OP only" : "everyone";
        String locationStr = String.format("%.1f, %.1f, %.1f", pos.getX(), pos.getY(), pos.getZ());
        
        if (isUpdate) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("warpUpdated", "name", warpName, "permission", permDisplay, "location", locationStr), "#55FF55"));
        } else {
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("warpCreated", "name", warpName, "permission", permDisplay, "location", locationStr), "#55FF55"));
        }
    }
    
    static void doDelete(CommandContext ctx, String warpName, WarpService warpService, ConfigManager configManager) {
        boolean deleted = warpService.deleteWarp(warpName);
        
        if (!deleted) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("warpNotFound", "name", warpName, "list", ""), "#FF5555"));
            return;
        }
        
        ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("warpDeleted", "name", warpName), "#55FF55"));
    }
    
    static void doInfo(CommandContext ctx, String warpName, WarpService warpService, ConfigManager configManager) {
        Optional<Warp> warpOpt = warpService.getWarp(warpName);
        
        if (warpOpt.isEmpty()) {
            ctx.sendMessage(Message.join(
                Message.raw("Warp '").color("#FF5555"),
                Message.raw(warpName).color("#FFFFFF"),
                Message.raw("' not found.").color("#FF5555")
            ));
            return;
        }
        
        Warp warp = warpOpt.get();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        String createdDate = sdf.format(new Date(warp.getCreatedAt()));
        
        ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("warpAdminInfoTitle", "name", warp.getName()), "#55FFFF"));
        ctx.sendMessage(Message.join(
            Message.raw("  Description: ").color("#AAAAAA"),
            Message.raw(warp.getDescription().isEmpty() ? "(none)" : warp.getDescription()).color("#FFFFFF")
        ));
        ctx.sendMessage(Message.join(
            Message.raw("  Permission: ").color("#AAAAAA"),
            Message.raw(warp.isOpOnly() ? "OP Only" : "Everyone").color(warp.isOpOnly() ? "#FF5555" : "#55FF55")
        ));
        ctx.sendMessage(Message.join(
            Message.raw("  World: ").color("#AAAAAA"),
            Message.raw(warp.getLocation().getWorld()).color("#FFFFFF")
        ));
        ctx.sendMessage(Message.join(
            Message.raw("  Position: ").color("#AAAAAA"),
            Message.raw(String.format("%.2f, %.2f, %.2f", 
                warp.getLocation().getX(),
                warp.getLocation().getY(),
                warp.getLocation().getZ())).color("#FFFFFF")
        ));
        ctx.sendMessage(Message.join(
            Message.raw("  Created by: ").color("#AAAAAA"),
            Message.raw(warp.getCreatedBy() != null ? warp.getCreatedBy() : "Unknown").color("#FFFFFF")
        ));
        ctx.sendMessage(Message.join(
            Message.raw("  Created at: ").color("#AAAAAA"),
            Message.raw(createdDate).color("#FFFFFF")
        ));
    }
}
