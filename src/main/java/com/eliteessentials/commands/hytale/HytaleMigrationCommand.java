package com.eliteessentials.commands.hytale;

import com.eliteessentials.EliteEssentials;
import com.eliteessentials.config.PluginConfig;
import com.eliteessentials.permissions.PermissionService;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.services.EssentialsCoreMigrationService;
import com.eliteessentials.services.EssentialsPlusMigrationService;
import com.eliteessentials.services.HomesPlusMigrationService;
import com.eliteessentials.services.HyssentialsMigrationService;
import com.eliteessentials.services.SqlMigrationService;
import com.eliteessentials.util.MessageFormatter;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.logging.Logger;
import com.eliteessentials.util.CommandSpyUtil;

/**
 * Command: /eemigration <source> [force]
 * Migrates data from other essentials plugins or between storage backends.
 * 
 * Sources:
 * - essentialscore: Migrate from nhulston's EssentialsCore
 * - hyssentials: Migrate from leclowndu93150's Hyssentials
 * - essentialsplus: Migrate from fof1092's EssentialsPlus
 * - homesplus: Migrate from HomesPlus
 * - sql: Migrate JSON file data into the configured SQL database
 * 
 * Options:
 * - force: Overwrite existing data
 * 
 * Permissions:
 * - Admin only (simple mode)
 * - eliteessentials.admin.reload (advanced mode)
 */
public class HytaleMigrationCommand extends CommandBase {

    private static final Logger logger = Logger.getLogger("EliteEssentials");

    public HytaleMigrationCommand() {
        super("eemigration", "Migrate data from other plugins or storage backends");
        setAllowsExtraArguments(true);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void executeSync(@Nonnull CommandContext ctx) {
        CommandSpyUtil.notify(ctx);
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
            handleEssentialsPlusMigration(ctx, force);
        } else if ("homesplus".equalsIgnoreCase(source)) {
            handleHomesPlusMigration(ctx);
        } else if ("sql".equalsIgnoreCase(source)) {
            handleSqlMigration(ctx, force);
        } else if ("cleanup".equalsIgnoreCase(source)) {
            handleCleanup(ctx);
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
        ctx.sendMessage(Message.raw("  sql - Migrate JSON data into configured SQL database").color("#AAAAAA"));
        ctx.sendMessage(Message.raw("  cleanup - Move migrated JSON files into backup/ folder").color("#AAAAAA"));
        ctx.sendMessage(Message.raw("Options:").color("#AAAAAA"));
        ctx.sendMessage(Message.raw("  force - Overwrite existing data (use if re-migrating)").color("#AAAAAA"));
        ctx.sendMessage(Message.raw("Examples: /eemigration essentialscore force | /eemigration sql force").color("#777777"));
    }
    
    private void handleEssentialsCoreMigration(CommandContext ctx, boolean force) {
        EliteEssentials plugin = EliteEssentials.getInstance();
        
        EssentialsCoreMigrationService migrationService = new EssentialsCoreMigrationService(
            plugin.getDataFolder(),
            plugin.getWarpStorage(),
            plugin.getSpawnStorage(),
            plugin.getKitService(),
            plugin.getPlayerStorageProvider()
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
            plugin.getPlayerStorageProvider()
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
    
    private void handleEssentialsPlusMigration(CommandContext ctx, boolean force) {
        EliteEssentials plugin = EliteEssentials.getInstance();
        
        EssentialsPlusMigrationService migrationService = new EssentialsPlusMigrationService(
            plugin.getDataFolder(),
            plugin.getWarpStorage(),
            plugin.getSpawnStorage(),
            plugin.getKitService(),
            plugin.getPlayerStorageProvider()
        );
        
        // Check if source data exists
        if (!migrationService.hasEssentialsPlusData()) {
            ctx.sendMessage(Message.raw("EssentialsPlus data not found!").color("#FF5555"));
            ctx.sendMessage(Message.raw("Expected folder: mods/fof1092_EssentialsPlus/").color("#AAAAAA"));
            return;
        }
        
        ctx.sendMessage(Message.raw("Starting EssentialsPlus migration...").color("#FFAA00"));
        if (force) {
            ctx.sendMessage(Message.raw("Force mode: existing data will be overwritten.").color("#FFAA00"));
        }
        ctx.sendMessage(Message.raw("Source: " + migrationService.getEssentialsPlusFolder().getAbsolutePath()).color("#AAAAAA"));
        
        // Run migration
        EssentialsPlusMigrationService.MigrationResult result = migrationService.migrate(force);
        
        // Report results
        if (result.isSuccess()) {
            ctx.sendMessage(Message.raw("Migration complete!").color("#55FF55"));
        } else {
            ctx.sendMessage(Message.raw("Migration completed with errors.").color("#FFAA00"));
        }
        
        ctx.sendMessage(Message.raw("- Warps imported: " + result.getWarpsImported()).color("#AAAAAA"));
        ctx.sendMessage(Message.raw("- Kits imported: " + result.getKitsImported()).color("#AAAAAA"));
        ctx.sendMessage(Message.raw("- Spawns imported: " + result.getSpawnsImported()).color("#AAAAAA"));
        ctx.sendMessage(Message.raw("- Players with homes: " + result.getPlayersImported()).color("#AAAAAA"));
        ctx.sendMessage(Message.raw("- Total homes imported: " + result.getHomesImported()).color("#AAAAAA"));
        ctx.sendMessage(Message.raw("- User profiles migrated: " + result.getUsersImported()).color("#AAAAAA"));
        
        if (!result.getErrors().isEmpty()) {
            ctx.sendMessage(Message.raw("Errors (" + result.getErrors().size() + "):").color("#FF5555"));
            for (String error : result.getErrors()) {
                ctx.sendMessage(Message.raw("  - " + error).color("#FF7777"));
            }
        }
        
        // Remind about existing data
        if (result.getTotalImported() == 0) {
            ctx.sendMessage(Message.raw("No new data imported. Existing data was preserved.").color("#AAAAAA"));
        }
    }
    
    private void handleHomesPlusMigration(CommandContext ctx) {
        EliteEssentials plugin = EliteEssentials.getInstance();
        
        HomesPlusMigrationService migrationService = new HomesPlusMigrationService(
            plugin.getDataFolder(),
            plugin.getPlayerStorageProvider()
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
    
    private void handleSqlMigration(CommandContext ctx, boolean force) {
        EliteEssentials plugin = EliteEssentials.getInstance();
        PluginConfig.StorageConfig storageConfig = plugin.getConfigManager().getConfig().storage;
        String storageType = storageConfig.storageType != null
                ? storageConfig.storageType.toLowerCase().trim() : "json";

        // Refuse if storage type is JSON
        if ("json".equals(storageType)) {
            ctx.sendMessage(Message.raw("Cannot migrate: storageType is set to \"json\".").color("#FF5555"));
            ctx.sendMessage(Message.raw("Set storageType to \"sqlite\" or \"mysql\" in config.json, reload, then run this command.").color("#AAAAAA"));
            return;
        }

        SqlMigrationService migrationService = new SqlMigrationService(
                plugin.getPlayerStorageProvider(),
                plugin.getGlobalStorageProvider(),
                plugin.getDataFolder()
        );

        // Check if SQL tables already have data
        if (!force && migrationService.hasExistingData()) {
            ctx.sendMessage(Message.raw("SQL database already contains data!").color("#FFAA00"));
            ctx.sendMessage(Message.raw("Run /eemigration sql force to overwrite existing SQL data.").color("#AAAAAA"));
            return;
        }

        ctx.sendMessage(Message.raw("Starting JSON to SQL migration (" + storageType + ")...").color("#FFAA00"));
        if (force) {
            ctx.sendMessage(Message.raw("Force mode: existing SQL data may be overwritten.").color("#FFAA00"));
        }

        // Run migration with progress reporting
        SqlMigrationService.MigrationResult result = migrationService.migrate(
                msg -> ctx.sendMessage(Message.raw(msg).color("#AAAAAA"))
        );

        // Reload in-memory caches so migrated data is immediately available
        plugin.getPlayerStorageProvider().reload();
        plugin.getGlobalStorageProvider().load();
        plugin.getSpawnStorage().load();

        // Report summary
        if (result.isSuccess()) {
            ctx.sendMessage(Message.raw("Migration complete!").color("#55FF55"));
        } else {
            ctx.sendMessage(Message.raw("Migration completed with errors.").color("#FFAA00"));
        }

        ctx.sendMessage(Message.raw("- Players migrated: " + result.getPlayersMigrated()).color("#AAAAAA"));
        if (result.getPlayersFailed() > 0) {
            ctx.sendMessage(Message.raw("- Players failed: " + result.getPlayersFailed()).color("#FF7777"));
        }
        ctx.sendMessage(Message.raw("- Warps migrated: " + result.getWarpsMigrated()).color("#AAAAAA"));
        ctx.sendMessage(Message.raw("- Spawns migrated: " + result.getSpawnsMigrated()).color("#AAAAAA"));
        if (result.getFirstJoinSpawnMigrated() > 0) {
            ctx.sendMessage(Message.raw("- First-join spawn migrated: yes").color("#AAAAAA"));
        }
        ctx.sendMessage(Message.raw("- Time elapsed: " + result.getElapsedFormatted()).color("#AAAAAA"));

        if (!result.getErrors().isEmpty()) {
            ctx.sendMessage(Message.raw("Errors (" + result.getErrors().size() + "):").color("#FF5555"));
            int shown = Math.min(result.getErrors().size(), 10);
            for (int i = 0; i < shown; i++) {
                ctx.sendMessage(Message.raw("  - " + result.getErrors().get(i)).color("#FF7777"));
            }
            if (result.getErrors().size() > 10) {
                ctx.sendMessage(Message.raw("  ... and " + (result.getErrors().size() - 10) + " more (see server log)").color("#FF7777"));
            }
        }

        if (result.getTotalMigrated() == 0 && result.getPlayersFailed() == 0) {
            ctx.sendMessage(Message.raw("No JSON data found to migrate.").color("#AAAAAA"));
        }
    }
    
    /**
     * Moves migrated JSON data files into a backup/ subfolder so they don't
     * cause confusion when the server is running on SQL storage.
     */
    private void handleCleanup(CommandContext ctx) {
        EliteEssentials plugin = EliteEssentials.getInstance();
        PluginConfig.StorageConfig storageConfig = plugin.getConfigManager().getConfig().storage;
        String storageType = storageConfig.storageType != null
                ? storageConfig.storageType.toLowerCase().trim() : "json";

        if ("json".equals(storageType)) {
            ctx.sendMessage(Message.raw("Cannot cleanup: storageType is still \"json\".").color("#FF5555"));
            ctx.sendMessage(Message.raw("Switch to \"sqlite\" or \"mysql\" first, then run /eemigration sql, then cleanup.").color("#AAAAAA"));
            return;
        }

        File dataFolder = plugin.getDataFolder();
        File backupFolder = new File(dataFolder, "backup");

        // Files and folders that the SQL migration reads from
        String[] jsonFiles = {"warps.json", "spawn.json", "firstjoinspawn.json", "player_index.json"};
        String playersDir = "players";

        int moved = 0;
        int skipped = 0;

        // Create backup folder
        if (!backupFolder.exists() && !backupFolder.mkdirs()) {
            ctx.sendMessage(Message.raw("Failed to create backup/ folder.").color("#FF5555"));
            return;
        }

        // Move individual JSON files
        for (String fileName : jsonFiles) {
            File source = new File(dataFolder, fileName);
            if (!source.exists()) {
                skipped++;
                continue;
            }
            File dest = new File(backupFolder, fileName);
            if (dest.exists()) {
                ctx.sendMessage(Message.raw("  Skipped " + fileName + " (already in backup/)").color("#AAAAAA"));
                skipped++;
                continue;
            }
            try {
                Files.move(source.toPath(), dest.toPath(), StandardCopyOption.ATOMIC_MOVE);
                ctx.sendMessage(Message.raw("  Moved " + fileName).color("#AAAAAA"));
                moved++;
            } catch (IOException e) {
                logger.severe("[Migration Cleanup] Failed to move " + fileName + ": " + e.getMessage());
                ctx.sendMessage(Message.raw("  Failed to move " + fileName + ": " + e.getMessage()).color("#FF7777"));
            }
        }

        // Move players/ folder
        File playersFolder = new File(dataFolder, playersDir);
        if (playersFolder.exists() && playersFolder.isDirectory()) {
            File destPlayersFolder = new File(backupFolder, playersDir);
            if (destPlayersFolder.exists()) {
                ctx.sendMessage(Message.raw("  Skipped players/ (already in backup/)").color("#AAAAAA"));
                skipped++;
            } else {
                try {
                    // Try atomic move first (works if same filesystem)
                    Files.move(playersFolder.toPath(), destPlayersFolder.toPath(), StandardCopyOption.ATOMIC_MOVE);
                    ctx.sendMessage(Message.raw("  Moved players/ folder").color("#AAAAAA"));
                    moved++;
                } catch (IOException e) {
                    // Atomic move can fail across filesystems or on some OS — fall back to copy+delete
                    try {
                        copyDirectory(playersFolder.toPath(), destPlayersFolder.toPath());
                        deleteDirectory(playersFolder.toPath());
                        ctx.sendMessage(Message.raw("  Moved players/ folder").color("#AAAAAA"));
                        moved++;
                    } catch (IOException ex) {
                        logger.severe("[Migration Cleanup] Failed to move players/: " + ex.getMessage());
                        ctx.sendMessage(Message.raw("  Failed to move players/: " + ex.getMessage()).color("#FF7777"));
                    }
                }
            }
        } else {
            skipped++;
        }

        // Summary
        if (moved == 0 && skipped > 0) {
            ctx.sendMessage(Message.raw("No JSON files to move. Already cleaned up or no data found.").color("#AAAAAA"));
        } else if (moved > 0) {
            ctx.sendMessage(Message.raw("Cleanup complete! Moved " + moved + " item(s) to backup/.").color("#55FF55"));
            ctx.sendMessage(Message.raw("You can delete backup/ once you've confirmed SQL is working.").color("#AAAAAA"));
        }
    }

    private static void copyDirectory(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(target.resolve(source.relativize(dir)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, target.resolve(source.relativize(file)), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void deleteDirectory(Path dir) throws IOException {
        Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                Files.delete(d);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
