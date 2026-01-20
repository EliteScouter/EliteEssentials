package com.eliteessentials.commands.hytale;

import com.eliteessentials.config.ConfigManager;
import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.storage.RulesStorage;
import com.eliteessentials.util.CommandPermissionUtil;
import com.eliteessentials.util.MessageFormatter;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.List;

import javax.annotation.Nonnull;

/**
 * /rules - Display the server rules.
 * 
 * Supports color codes (&0-f, &l, &o, &r) and clickable URLs.
 * 
 * Usage: /rules
 * Permission: eliteessentials.command.misc.rules (Everyone)
 */
public class HytaleRulesCommand extends AbstractPlayerCommand {
    
    private final ConfigManager configManager;
    private final RulesStorage rulesStorage;
    
    public HytaleRulesCommand(ConfigManager configManager, RulesStorage rulesStorage) {
        super("rules", "Display the server rules");
        this.configManager = configManager;
        this.rulesStorage = rulesStorage;
    }
    
    @Override
    protected boolean canGeneratePermission() {
        return false;
    }
    
    @Override
    protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store, 
                          @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef player, @Nonnull World world) {
        // Permission check - everyone can use
        if (!CommandPermissionUtil.canExecute(ctx, player, Permissions.RULES, 
                configManager.getConfig().rules.enabled)) {
            return;
        }
        
        // Get rules lines
        List<String> rulesLines = rulesStorage.getRulesLines();
        if (rulesLines.isEmpty()) {
            String message = configManager.getMessage("rulesEmpty");
            player.sendMessage(Message.raw(message).color("#FF5555"));
            return;
        }
        
        // Send each line with formatting
        for (String line : rulesLines) {
            // Skip completely empty lines to avoid excessive spacing
            if (line.trim().isEmpty()) {
                continue;
            }
            player.sendMessage(MessageFormatter.format(line));
        }
    }
}
