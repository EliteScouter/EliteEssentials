package com.eliteessentials.commands.args;

import com.hypixel.hytale.server.core.command.system.ParseResult;
import com.hypixel.hytale.server.core.command.system.arguments.types.SingleArgumentType;

import javax.annotation.Nonnull;

/**
 * Custom integer argument types with clean, relevant examples.
 */
public class SimpleIntArg extends SingleArgumentType<Integer> {

    // Cooldown in seconds
    public static final SimpleIntArg COOLDOWN = new SimpleIntArg("Seconds", "Cooldown in seconds (0 = none)", new String[]{"0", "60", "3600"});
    
    // Percentage (0-100)
    public static final SimpleIntArg PERCENTAGE = new SimpleIntArg("Percent", "A percentage (0-100)", new String[]{"0", "50", "100"});
    
    // Generic number
    public static final SimpleIntArg NUMBER = new SimpleIntArg("Number", "A whole number", new String[]{"1", "10", "100"});

    private final String[] examples;

    private SimpleIntArg(String typeName, String description, String[] examples) {
        super(typeName, description);
        this.examples = examples;
    }

    @Override
    @Nonnull
    public Integer parse(@Nonnull String input, @Nonnull ParseResult parseResult) {
        return Integer.parseInt(input);
    }

    @Override
    @Nonnull
    public String[] getExamples() {
        return examples;
    }
}
