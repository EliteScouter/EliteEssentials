package com.eliteessentials.commands.hytale;

import com.eliteessentials.EliteEssentials;
import com.eliteessentials.config.PluginConfig;
import com.eliteessentials.model.Warp;
import com.eliteessentials.services.WarpService;
import com.eliteessentials.util.CommandPermissionUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Command: /warpadmin [info|setperm|list]
 * Admin command for managing warps.
 * 
 * Subcommands:
 * - /warpadmin list - List all warps with details
 * - /warpadmin info <name> - Show detailed info about a warp
 * - /warpadmin setperm <name> <all|op> - Change warp permission
 */
public class HytaleWarpAdminCommand extends AbstractPlayerCommand {

    private static final String ADMIN_PERMISSION = "eliteessentials.admin";
    
    private final WarpService warpService;

    public HytaleWarpAdminCommand(WarpService warpService) {
        super("warpadmin", "Manage warps (Admin)");
        this.warpService = warpService;
        
        addUsageVariant(new WarpInfoCommand(warpService));
        addUsageVariant(new WarpSetPermCommand(warpService));
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref,
                          PlayerRef player, World world) {
        // Admin only
        if (ctx.sender() == null || !ctx.sender().hasPermission(ADMIN_PERMISSION)) {
            ctx.sendMessage(Message.raw("You don't have permission to use this command.").color("#FF5555"));
            return;
        }
        
        // Default: show help/list
        showWarpList(ctx);
    }
    
    private void showWarpList(CommandContext ctx) {
        Map<String, Warp> warps = warpService.getAllWarps();
        
        if (warps.isEmpty()) {
            ctx.sendMessage(Message.raw("No warps configured.").color("#FF5555"));
            ctx.sendMessage(Message.join(
                Message.raw("Use ").color("#AAAAAA"),
                Message.raw("/setwarp <name> [all|op]").color("#FFAA00"),
                Message.raw(" to create one.").color("#AAAAAA")
            ));
            return;
        }
        
        ctx.sendMessage(Message.raw("=== Warp Admin Panel ===").color("#55FFFF"));
        ctx.sendMessage(Message.raw("Total warps: " + warps.size()).color("#AAAAAA"));
        ctx.sendMessage(Message.raw("").color("#FFFFFF"));
        
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
        ctx.sendMessage(Message.raw("Commands:").color("#FFAA00"));
        ctx.sendMessage(Message.raw("  /warpadmin info <name>").color("#AAAAAA"));
        ctx.sendMessage(Message.raw("  /warpadmin setperm <name> <all|op>").color("#AAAAAA"));
        ctx.sendMessage(Message.raw("  /setwarp <name> [all|op]").color("#AAAAAA"));
        ctx.sendMessage(Message.raw("  /delwarp <name>").color("#AAAAAA"));
    }
    
    /**
     * Subcommand: /warpadmin info <name>
     */
    private static class WarpInfoCommand extends AbstractPlayerCommand {
        private final WarpService warpService;
        private final RequiredArg<String> nameArg;
        
        WarpInfoCommand(WarpService warpService) {
            super("info");
            this.warpService = warpService;
            this.nameArg = withRequiredArg("name", "Warp name", ArgTypes.STRING);
        }
        
        @Override
        protected boolean canGeneratePermission() {
            return false;
        }
        
        @Override
        protected void execute(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref,
                              PlayerRef player, World world) {
            if (ctx.sender() == null || !ctx.sender().hasPermission(ADMIN_PERMISSION)) {
                ctx.sendMessage(Message.raw("You don't have permission to use this command.").color("#FF5555"));
                return;
            }
            
            String warpName = ctx.get(nameArg);
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
            
            ctx.sendMessage(Message.raw("=== Warp: " + warp.getName() + " ===").color("#55FFFF"));
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
                Message.raw("  Rotation: ").color("#AAAAAA"),
                Message.raw(String.format("Yaw: %.1f, Pitch: %.1f",
                    warp.getLocation().getYaw(),
                    warp.getLocation().getPitch())).color("#FFFFFF")
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
    
    /**
     * Subcommand: /warpadmin setperm <name> <all|op>
     */
    private static class WarpSetPermCommand extends AbstractPlayerCommand {
        private final WarpService warpService;
        private final RequiredArg<String> nameArg;
        private final RequiredArg<String> permArg;
        
        WarpSetPermCommand(WarpService warpService) {
            super("setperm");
            this.warpService = warpService;
            this.nameArg = withRequiredArg("name", "Warp name", ArgTypes.STRING);
            this.permArg = withRequiredArg("permission", "all or op", ArgTypes.STRING);
        }
        
        @Override
        protected boolean canGeneratePermission() {
            return false;
        }
        
        @Override
        protected void execute(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref,
                              PlayerRef player, World world) {
            if (ctx.sender() == null || !ctx.sender().hasPermission(ADMIN_PERMISSION)) {
                ctx.sendMessage(Message.raw("You don't have permission to use this command.").color("#FF5555"));
                return;
            }
            
            String warpName = ctx.get(nameArg);
            String permStr = ctx.get(permArg).toLowerCase();
            
            // Parse permission
            Warp.Permission permission;
            if ("op".equals(permStr) || "admin".equals(permStr)) {
                permission = Warp.Permission.OP;
            } else if ("all".equals(permStr) || "everyone".equals(permStr)) {
                permission = Warp.Permission.ALL;
            } else {
                ctx.sendMessage(Message.join(
                    Message.raw("Invalid permission '").color("#FF5555"),
                    Message.raw(permStr).color("#FFFFFF"),
                    Message.raw("'. Use 'all' or 'op'.").color("#FF5555")
                ));
                return;
            }
            
            boolean updated = warpService.updateWarpPermission(warpName, permission);
            
            if (!updated) {
                ctx.sendMessage(Message.join(
                    Message.raw("Warp '").color("#FF5555"),
                    Message.raw(warpName).color("#FFFFFF"),
                    Message.raw("' not found.").color("#FF5555")
                ));
                return;
            }
            
            String permDisplay = permission == Warp.Permission.OP ? "OP only" : "everyone";
            ctx.sendMessage(Message.join(
                Message.raw("Updated warp '").color("#55FF55"),
                Message.raw(warpName).color("#FFFFFF"),
                Message.raw("' permission to ").color("#55FF55"),
                Message.raw(permDisplay).color("#FFAA00")
            ));
        }
    }
}
