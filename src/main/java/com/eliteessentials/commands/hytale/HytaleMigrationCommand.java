package com.eliteessentials.commands.hytale;

import com.eliteessentials.EliteEssentials;
import com.eliteessentials.commands.args.SimpleStringArg;
import com.eliteessentials.permissions.PermissionService;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.services.EssentialsCoreMigrationService;
import com.eliteessentials.services.HyssentialsMigrationService;
import com.eliteessentials.util.MessageFormatter;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;

import javax.annotation.Nonnull;

/**
 * Command: /eemigration <source>
 * Migrates data from other essentials plugins.
 * 
 * Sources:
 * - essentialscore: Migrate from nhulston's EssentialsCore
 * - hyssentials: Migrate from leclowndu93150's Hyssentials
 * 
 * Permissions:
 * - Admin only (simple mode)
 * - eliteessentials.admin.reload (advanced mode)
 */
public class HytaleMigrationCommand extends CommandBase {

    private final RequiredArg<String> sourceArg;

    public HytaleMigrationCommand() {
        super("eemigration", "Migrate data from other essentials plugins");
        
        this.sourceArg = withRequiredArg("source", "Source plugin (essentialscore, hyssentials)", SimpleStringArg.ACTION);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void executeSync(@Nonnull CommandContext ctx) {
        // Check admin permission
        PermissionService perms = PermissionService.get();
        if (!perms.canUseAdminCommand(ctx.sender(), Permissions.ADMIN_RELOAD, true)) {
            ctx.sendMessage(MessageFormatter.formatWithFallback(
                EliteEssentials.getInstance().getConfigManager().getMessage("noPermission"), "#FF5555"));
            return;
        }
        
        String source = ctx.get(sourceArg);
        
        if ("essentialscore".equalsIgnoreCase(source)) {
            handleEssentialsCoreMigration(ctx);
        } else if ("hyssentials".equalsIgnoreCase(source)) {
            handleHyssentialsMigration(ctx);
        } else {
            ctx.sendMessage(Message.raw("Unknown source. Available: essentialscore, hyssentials").color("#FF5555"));
            ctx.sendMessage(Message.raw("Usage: /eemigration <source>").color("#AAAAAA"));
        }
    }
    
    private void handleEssentialsCoreMigration(CommandContext ctx) {
        EliteEssentials plugin = EliteEssentials.getInstance();
        
        EssentialsCoreMigrationService migrationService = new EssentialsCoreMigrationService(
            plugin.getDataFolder(),
            plugin.getWarpStorage(),
            plugin.getKitService(),
            plugin.getPlayerFileStorage()
        );
        
        // Check if source data exists
        if (!migrationService.hasEssentialsCoreData()) {
            ctx.sendMessage(Message.raw("EssentialsCore data not found!").color("#FF5555"));
            ctx.sendMessage(Message.raw("Expected folder: mods/com.nhulston_Essentials/").color("#AAAAAA"));
            return;
        }
        
        ctx.sendMessage(Message.raw("Starting EssentialsCore migration...").color("#FFAA00"));
        ctx.sendMessage(Message.raw("Source: " + migrationService.getEssentialsCoreFolder().getAbsolutePath()).color("#AAAAAA"));
        
        // Run migration
        EssentialsCoreMigrationService.MigrationResult result = migrationService.migrate();
        
        // Report results
        if (result.isSuccess()) {
            ctx.sendMessage(Message.raw("Migration complete!").color("#55FF55"));
        } else {
            ctx.sendMessage(Message.raw("Migration completed with errors.").color("#FFAA00"));
        }
        
        ctx.sendMessage(Message.raw("- Warps imported: " + result.getWarpsImported()).color("#AAAAAA"));
        ctx.sendMessage(Message.raw("- Kits imported: " + result.getKitsImported()).color("#AAAAAA"));
        ctx.sendMessage(Message.raw("- Players with homes: " + result.getPlayersImported()).color("#AAAAAA"));
        ctx.sendMessage(Message.raw("- Total homes imported: " + result.getHomesImported()).color("#AAAAAA"));
        
        if (!result.getErrors().isEmpty()) {
            ctx.sendMessage(Message.raw("Errors (" + result.getErrors().size() + "):").color("#FF5555"));
            for (String error : result.getErrors()) {
                ctx.sendMessage(Message.raw("  - " + error).color("#FF7777"));
            }
        }
        
        // Remind about existing data
        if (result.getWarpsImported() == 0 && result.getKitsImported() == 0 && result.getHomesImported() == 0) {
            ctx.sendMessage(Message.raw("No new data imported. Existing data was preserved.").color("#AAAAAA"));
        }
    }
    
    private void handleHyssentialsMigration(CommandContext ctx) {
        EliteEssentials plugin = EliteEssentials.getInstance();
        
        HyssentialsMigrationService migrationService = new HyssentialsMigrationService(
            plugin.getDataFolder(),
            plugin.getWarpStorage(),
            plugin.getPlayerFileStorage()
        );
        
        // Check if source data exists
        if (!migrationService.hasHyssentialsData()) {
            ctx.sendMessage(Message.raw("Hyssentials data not found!").color("#FF5555"));
            ctx.sendMessage(Message.raw("Expected folder: mods/com.leclowndu93150_Hyssentials/").color("#AAAAAA"));
            return;
        }
        
        ctx.sendMessage(Message.raw("Starting Hyssentials migration...").color("#FFAA00"));
        ctx.sendMessage(Message.raw("Source: " + migrationService.getHyssentialsFolder().getAbsolutePath()).color("#AAAAAA"));
        
        // Run migration
        HyssentialsMigrationService.MigrationResult result = migrationService.migrate();
        
        // Report results
        if (result.isSuccess()) {
            ctx.sendMessage(Message.raw("Migration complete!").color("#55FF55"));
        } else {
            ctx.sendMessage(Message.raw("Migration completed with errors.").color("#FFAA00"));
        }
        
        ctx.sendMessage(Message.raw("- Warps imported: " + result.getWarpsImported()).color("#AAAAAA"));
        ctx.sendMessage(Message.raw("- Players with homes: " + result.getPlayersImported()).color("#AAAAAA"));
        ctx.sendMessage(Message.raw("- Total homes imported: " + result.getHomesImported()).color("#AAAAAA"));
        
        if (!result.getErrors().isEmpty()) {
            ctx.sendMessage(Message.raw("Errors (" + result.getErrors().size() + "):").color("#FF5555"));
            for (String error : result.getErrors()) {
                ctx.sendMessage(Message.raw("  - " + error).color("#FF7777"));
            }
        }
        
        // Remind about existing data
        if (result.getWarpsImported() == 0 && result.getHomesImported() == 0) {
            ctx.sendMessage(Message.raw("No new data imported. Existing data was preserved.").color("#AAAAAA"));
        }
    }
}
