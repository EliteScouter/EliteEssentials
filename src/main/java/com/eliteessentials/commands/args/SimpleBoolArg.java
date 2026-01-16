package com.eliteessentials.commands.args;

import com.hypixel.hytale.server.core.command.system.ParseResult;
import com.hypixel.hytale.server.core.command.system.arguments.types.SingleArgumentType;

import javax.annotation.Nonnull;

/**
 * Custom boolean argument type with clean examples.
 * Accepts: yes/no, true/false, 1/0
 */
public class SimpleBoolArg extends SingleArgumentType<Boolean> {

    public static final SimpleBoolArg YES_NO = new SimpleBoolArg();

    private SimpleBoolArg() {
        super("Yes/No", "yes or no");
    }

    @Override
    @Nonnull
    public Boolean parse(@Nonnull String input, @Nonnull ParseResult parseResult) {
        String lower = input.toLowerCase();
        return lower.equals("yes") || lower.equals("true") || lower.equals("1");
    }

    @Override
    @Nonnull
    public String[] getExamples() {
        return new String[]{"yes", "no"};
    }
}
