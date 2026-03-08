package com.eliteessentials.commands.hytale;

import com.eliteessentials.EliteEssentials;
import com.eliteessentials.permissions.PermissionService;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.services.EssentialsCoreMigrationService;
import com.eliteessentials.services.EssentialsPlusMigrationService;
import com.eliteessentials.services.HomesPlusMigrationService;
import com.eliteessentials.services.HyssentialsMigrationService;
import com.eliteessentials.util.MessageFormatter;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;

import javax.annotation.Nonnull;

/**
 * Command: /eemigration <source> [force]
 * Migrates data from other essentials plugins.
 * 
 * Sources:
 * - essentialscore: Migrate from nhulston's EssentialsCore
 * - hyssentials: Migrate from leclowndu93150's Hyssentials
 * - essentialsplus: Migrate from fof1092's EssentialsPlus
 * - homesplus: Migrate from HomesPlus
 * 
 * Options:
 * - force: Overwrite existing homes/cooldowns with EssentialsCore data
 * 
 * Permissions:
 * - Admin only (simple mode)
 * - eliteessentials.admin.reload (advanced mode)
 */
public class HytaleMigrationCommand extends CommandBase {

    public HytaleMigrationCommand() {
        super("eemigration", "Migrate data from other essentials plugins");
        setAllowsExtraArguments(true);
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
        
        // Parse raw input: /eemigration <source> [force]
        String rawInput = ctx.getInputString();
        String[] parts = rawInput.split("\\s+");
        
        if (parts.length < 2 || parts[1].isEmpty()) {
            showUsage(ctx);
            return;
        }
        
        String source = parts[1];
        boolean force = parts.length >= 3 && "force".equalsIgnoreCase(parts[2]);
        
        if ("essentialscore".equalsIgnoreCase(source)) {
            handleEssentialsCoreMigration(ctx, force);
        } else if ("hyssentials".equalsIgnoreCase(source)) {
            handleHyssentialsMigration(ctx);
        } else if ("essentialsplus".equalsIgnoreCase(source)) {
            handleEssentialsPlusMigration(ctx);
        } else if ("homesplus".equalsIgnoreCase(source)) {
            handleHomesPlusMigration(ctx);
        } else {
            showUsage(ctx);
        }
    }
    
    private void showUsage(CommandContext ctx) {
        ctx.sendMessage(Message.raw("Usage: /eemigration <source> [force]").color("#FFAA00"));
        ctx.sendMessage(Message.raw("Sources:").color("#AAAAAA"));
        ctx.sendMessage(Message.raw("  essentialscore - Import from nhulston's EssentialsCore").color("#AAAAAA"));
        ctx.sendMessage(Message.raw("  hyssentials - Import from Hyssentials").color("#AAAAAA"));
        ctx.sendMessage(Message.raw("  essentialsplus - Import from EssentialsPlus").color("#AAAAAA"));
        ctx.sendMessage(Message.raw("  homesplus - Import from HomesPlus").color("#AAAAAA"));
        ctx.sendMessage(Message.raw("Options:").color("#AAAAAA"));
        ctx.sendMessage(Message.raw("  force - Overwrite existing homes/cooldowns (use if re-migrating)").color("#AAAAAA"));
        ctx.sendMessage(Message.raw("Example: /eemigration essentialscore force").color("#777777"));
    }
    
    private void handleEssentialsCoreMigration(CommandContext ctx, boolean force) {
        EliteEssentials plugin = EliteEssentials.getInstance();
        
        EssentialsCoreMigrationService migrationService = new EssentialsCoreMigrationService(
            plugin.getDataFolder(),
            plugin.getWarpStorage(),
            plugin.getSpawnStorage(),
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
        if (force) {
            ctx.sendMessage(Message.raw("Force mode: existing homes/cooldowns will be overwritten.").color("#FFAA00"));
        }
        ctx.sendMessage(Message.raw("Source: " + migrationService.getEssentialsCoreFolder().getAbsolutePath()).color("#AAAAAA"));
        
        // Run migration
        EssentialsCoreMigrationService.MigrationResult result = migrationService.migrate(force);
        
        // Report results
        if (result.isSuccess()) {
            ctx.sendMessage(Message.raw("Migration complete!").color("#55FF55"));
        } else {
            ctx.sendMessage(Message.raw("Migration completed with errors.").color("#FFAA00"));
        }
        
        ctx.sendMessage(Message.raw("- Warps imported: " + result.getWarpsImported()).color("#AAAAAA"));
        ctx.sendMessage(Message.raw("- Spawns imported: " + result.getSpawnsImported()).color("#AAAAAA"));
        ctx.sendMessage(Message.raw("- Kits imported: " + result.getKitsImported()).color("#AAAAAA"));
        ctx.sendMessage(Message.raw("- Player files found: " + result.getPlayerFilesFound()).color("#AAAAAA"));
        ctx.sendMessage(Message.raw("- Players migrated: " + result.getPlayersImported()).color("#AAAAAA"));
        if (result.getPlayersSkippedExist() > 0) {
            ctx.sendMessage(Message.raw("- Players skipped (already migrated): " + result.getPlayersSkippedExist()).color("#AAAAAA"));
        }
        ctx.sendMessage(Message.raw("- Total homes imported: " + result.getHomesImported()).color("#AAAAAA"));
        ctx.sendMessage(Message.raw("- Kit cooldowns imported: " + result.getKitCooldownsImported()).color("#AAAAAA"));
        
        if (!result.getErrors().isEmpty()) {
            ctx.sendMessage(Message.raw("Errors (" + result.getErrors().size() + "):").color("#FF5555"));
            for (String error : result.getErrors()) {
                ctx.sendMessage(Message.raw("  - " + error).color("#FF7777"));
            }
        }
        
        if (result.getTotalImported() == 0) {
            if (result.getPlayersSkippedExist() > 0) {
                ctx.sendMessage(Message.raw("No new data imported. All player data was already migrated.").color("#AAAAAA"));
            } else if (result.getPlayerFilesFound() == 0) {
                ctx.sendMessage(Message.raw("No new data imported. No player files found in players/ folder.").color("#AAAAAA"));
            } else {
                ctx.sendMessage(Message.raw("No new data imported. Existing data was preserved.").color("#AAAAAA"));
            }
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
    
    private void handleEssentialsPlusMigration(CommandContext ctx) {
        EliteEssentials plugin = EliteEssentials.getInstance();
        
        EssentialsPlusMigrationService migrationService = new EssentialsPlusMigrationService(
            plugin.getDataFolder(),
            plugin.getWarpStorage(),
            plugin.getKitService(),
            plugin.getPlayerFileStorage()
        );
        
        // Check if source data exists
        if (!migrationService.hasEssentialsPlusData()) {
            ctx.sendMessage(Message.raw("EssentialsPlus data not found!").color("#FF5555"));
            ctx.sendMessage(Message.raw("Expected folder: mods/fof1092_EssentialsPlus/").color("#AAAAAA"));
            return;
        }
        
        ctx.sendMessage(Message.raw("Starting EssentialsPlus migration...").color("#FFAA00"));
        ctx.sendMessage(Message.raw("Source: " + migrationService.getEssentialsPlusFolder().getAbsolutePath()).color("#AAAAAA"));
        
        // Run migration
        EssentialsPlusMigrationService.MigrationResult result = migrationService.migrate();
        
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
    
    private void handleHomesPlusMigration(CommandContext ctx) {
        EliteEssentials plugin = EliteEssentials.getInstance();
        
        HomesPlusMigrationService migrationService = new HomesPlusMigrationService(
            plugin.getDataFolder(),
            plugin.getPlayerFileStorage()
        );
        
        // Check if source data exists
        if (!migrationService.hasHomesPlusData()) {
            ctx.sendMessage(Message.raw("HomesPlus data not found!").color("#FF5555"));
            ctx.sendMessage(Message.raw("Expected folder: mods/HomesPlus_HomesPlus/").color("#AAAAAA"));
            return;
        }
        
        ctx.sendMessage(Message.raw("Starting HomesPlus migration...").color("#FFAA00"));
        ctx.sendMessage(Message.raw("Source: " + migrationService.getHomesPlusFolder().getAbsolutePath()).color("#AAAAAA"));
        
        // Run migration
        HomesPlusMigrationService.MigrationResult result = migrationService.migrate();
        
        // Report results
        if (result.isSuccess()) {
            ctx.sendMessage(Message.raw("Migration complete!").color("#55FF55"));
        } else {
            ctx.sendMessage(Message.raw("Migration completed with errors.").color("#FFAA00"));
        }
        
        ctx.sendMessage(Message.raw("- Players with homes: " + result.getPlayersImported()).color("#AAAAAA"));
        ctx.sendMessage(Message.raw("- Total homes imported: " + result.getHomesImported()).color("#AAAAAA"));
        
        if (!result.getErrors().isEmpty()) {
            ctx.sendMessage(Message.raw("Errors (" + result.getErrors().size() + "):").color("#FF5555"));
            for (String error : result.getErrors()) {
                ctx.sendMessage(Message.raw("  - " + error).color("#FF7777"));
            }
        }
        
        // Remind about existing data
        if (result.getHomesImported() == 0) {
            ctx.sendMessage(Message.raw("No new data imported. Existing data was preserved.").color("#AAAAAA"));
        }
    }
}
