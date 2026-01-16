package com.eliteessentials.commands.args;

import com.hypixel.hytale.server.core.command.system.ParseResult;
import com.hypixel.hytale.server.core.command.system.arguments.types.SingleArgumentType;

import javax.annotation.Nonnull;

/**
 * Custom string argument types with clean, relevant examples for each command type.
 */
public class SimpleStringArg extends SingleArgumentType<String> {

    // ===== HOME COMMANDS =====
    // Home name for /home, /sethome, /delhome
    public static final SimpleStringArg HOME_NAME = new SimpleStringArg("HomeName", "Home name", new String[]{"home", "base", "farm"});
    
    // ===== WARP COMMANDS =====
    // Warp name for /warp, /setwarp, /delwarp
    public static final SimpleStringArg WARP_NAME = new SimpleStringArg("WarpName", "Warp name", new String[]{"spawn", "shop", "arena"});
    
    // ===== KIT COMMANDS =====
    // Kit name for /kit create, /kit delete
    public static final SimpleStringArg KIT_NAME = new SimpleStringArg("KitName", "Kit name", new String[]{"Starter", "Warrior", "Builder"});
    
    // ===== GENERIC (fallback) =====
    // Generic name arg - use specific ones above when possible
    public static final SimpleStringArg NAME = new SimpleStringArg("String", "A name (letters, numbers, underscores)", new String[]{"MyName", "test_1"});
    
    // ===== PERMISSION =====
    // Permission arg for warp permissions
    public static final SimpleStringArg PERMISSION = new SimpleStringArg("all|op", "all or op", new String[]{"all", "op"});
    
    // ===== ADMIN =====
    // Action arg (for reload, etc.)
    public static final SimpleStringArg ACTION = new SimpleStringArg("Action", "Command action", new String[]{"reload"});

    private final String[] examples;

    private SimpleStringArg(String typeName, String description, String[] examples) {
        super(typeName, description);
        this.examples = examples;
    }

    @Override
    @Nonnull
    public String parse(@Nonnull String input, @Nonnull ParseResult parseResult) {
        return input;
    }

    @Override
    @Nonnull
    public String[] getExamples() {
        return examples;
    }
}
