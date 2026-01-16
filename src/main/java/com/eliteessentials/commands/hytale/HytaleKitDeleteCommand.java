package com.eliteessentials.commands.hytale;

import com.eliteessentials.permissions.Permissions;
import com.eliteessentials.services.KitService;
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

import javax.annotation.Nonnull;

/**
 * Subcommand to delete a kit.
 * Usage: /kit delete <name>
 */
public class HytaleKitDeleteCommand extends AbstractPlayerCommand {
    private final KitService kitService;
    private final RequiredArg<String> nameArg;

    public HytaleKitDeleteCommand(@Nonnull KitService kitService) {
        super("delete", "Delete a kit");
        this.kitService = kitService;

        requirePermission(Permissions.KIT_DELETE);
        this.nameArg = withRequiredArg("name", "Kit name", SimpleStringArg.KIT_NAME);
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        String kitName = context.get(nameArg);

        // Check if kit exists
        if (kitService.getKit(kitName) == null) {
            context.sendMessage(Message.raw("Kit '" + kitName + "' does not exist.").color("#FF5555"));
            return;
        }

        // Delete the kit
        if (kitService.deleteKit(kitName)) {
            context.sendMessage(Message.raw("Kit '" + kitName + "' has been deleted.").color("#55FF55"));
        } else {
            context.sendMessage(Message.raw("Failed to delete kit '" + kitName + "'.").color("#FF5555"));
        }
    }
}
