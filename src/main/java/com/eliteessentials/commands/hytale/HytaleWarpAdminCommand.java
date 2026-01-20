package com.eliteessentials.commands.hytale;

import com.eliteessentials.EliteEssentials;
import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.model.Warp;
import com.eliteessentials.services.WarpService;
import com.eliteessentials.util.MessageFormatter;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.eliteessentials.commands.args.SimpleStringArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nonnull;

/**
 * Command: /warpadmin [info|setperm|list]
 * Admin command for managing warps.
 * 
 * Permissions:
 * - eliteessentials.command.warpadmin - Manage warps (admin)
 */
public class HytaleWarpAdminCommand extends AbstractPlayerCommand {

    private static final String COMMAND_NAME = "warpadmin";
    
    private final WarpService warpService;

    public HytaleWarpAdminCommand(WarpService warpService) {
        super(COMMAND_NAME, "Manage warps (Admin)");
        this.warpService = warpService;
        
        // Permission check handled in execute() via CommandPermissionUtil
        
        addUsageVariant(new WarpInfoCommand(warpService));
        addUsageVariant(new WarpSetPermCommand(warpService));
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                          @Nonnull PlayerRef player, @Nonnull World world) {
        // Default: show help/list
        showWarpList(ctx);
    }
    
    private void showWarpList(CommandContext ctx) {
        ConfigManager configManager = EliteEssentials.getInstance().getConfigManager();
        Map<String, Warp> warps = warpService.getAllWarps();
        
        if (warps.isEmpty()) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("warpAdminNoWarps"), "#FF5555"));
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("warpAdminCreateHint"), "#AAAAAA"));
            return;
        }
        
        ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("warpAdminTitle"), "#55FFFF"));
        ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("warpAdminTotal", "count", String.valueOf(warps.size())), "#AAAAAA"));
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
        ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("warpAdminCommands"), "#FFAA00"));
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
            this.nameArg = withRequiredArg("name", "Warp name", SimpleStringArg.WARP_NAME);
            
            // Permission check handled in execute() via CommandPermissionUtil
        }
        
        @Override
        protected boolean canGeneratePermission() {
            return false;
        }
        
        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                              @Nonnull PlayerRef player, @Nonnull World world) {
            ConfigManager configManager = EliteEssentials.getInstance().getConfigManager();
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
            
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("warpAdminInfoTitle", "name", warp.getName()), "#55FFFF"));
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
            this.nameArg = withRequiredArg("name", "Warp name", SimpleStringArg.WARP_NAME);
            this.permArg = withRequiredArg("permission", "all or op", SimpleStringArg.PERMISSION);
            
            // Permission check handled in execute() via CommandPermissionUtil
        }
        
        @Override
        protected boolean canGeneratePermission() {
            return false;
        }
        
        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                              @Nonnull PlayerRef player, @Nonnull World world) {
            ConfigManager configManager = EliteEssentials.getInstance().getConfigManager();
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
            ctx.sendMessage(MessageFormatter.formatWithFallback(configManager.getMessage("warpAdminPermissionUpdated", "name", warpName, "permission", permDisplay), "#55FF55"));
        }
    }
}
