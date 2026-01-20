package com.eliteessentials.services;

import com.eliteessentials.EliteEssentials;
import com.eliteessentials.model.Warp;
import com.eliteessentials.storage.AliasStorage;
import com.eliteessentials.storage.AliasStorage.AliasData;
import com.eliteessentials.storage.SpawnStorage;
import com.eliteessentials.permissions.PermissionService;
import com.eliteessentials.util.MessageFormatter;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandRegistry;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.Invulnerable;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.io.File;
import java.util.*;
import java.util.logging.Logger;

public class AliasService {
    private static final Logger logger = Logger.getLogger("EliteEssentials");
    private final AliasStorage storage;
    private final CommandRegistry commandRegistry;
    private final Map<String, AbstractPlayerCommand> registeredCommands = new HashMap<>();

    public AliasService(File dataFolder, CommandRegistry commandRegistry) {
        this.storage = new AliasStorage(dataFolder);
        this.commandRegistry = commandRegistry;
    }

    public void load() { storage.load(); registerAllAliases(); }
    public void reload() { storage.load(); registerAllAliases(); }

    private void registerAllAliases() {
        int count = 0;
        for (Map.Entry<String, AliasData> entry : storage.getAllAliases().entrySet()) {
            if (!registeredCommands.containsKey(entry.getKey())) {
                try {
                    AliasPlayerCommand cmd = new AliasPlayerCommand(entry.getKey(), entry.getValue());
                    commandRegistry.registerCommand(cmd);
                    registeredCommands.put(entry.getKey(), cmd);
                    count++;
                } catch (Exception e) { logger.warning("Failed to register alias: " + e.getMessage()); }
            }
        }
        if (count > 0) logger.info("Registered " + count + " alias commands");
    }

    public boolean createAlias(String name, String command, String permission) {
        boolean isNew = storage.createAlias(name, command, permission);
        if (isNew && !registeredCommands.containsKey(name.toLowerCase())) {
            AliasData data = storage.getAlias(name);
            if (data != null) {
                try {
                    AliasPlayerCommand cmd = new AliasPlayerCommand(name.toLowerCase(), data);
                    commandRegistry.registerCommand(cmd);
                    registeredCommands.put(name.toLowerCase(), cmd);
                } catch (Exception e) { logger.warning("Failed to register alias: " + e.getMessage()); }
            }
        }
        return isNew;
    }

    public boolean deleteAlias(String name) { return storage.deleteAlias(name); }
    public Map<String, AliasData> getAllAliases() { return storage.getAllAliases(); }
    public boolean hasAlias(String name) { return storage.hasAlias(name); }
    public AliasStorage getStorage() { return storage; }

    private static class AliasPlayerCommand extends AbstractPlayerCommand {
        private final String aliasName;
        public AliasPlayerCommand(String name, AliasData data) { super(name, "Alias: " + data.command); this.aliasName = name; }
        @Override protected boolean canGeneratePermission() { return false; }

        @Override
        protected void execute(@Nonnull CommandContext ctx, @Nonnull Store<EntityStore> store,
                              @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef player, @Nonnull World world) {
            AliasData data = EliteEssentials.getInstance().getAliasService().getStorage().getAlias(aliasName);
            if (data == null) { ctx.sendMessage(Message.raw("Alias no longer exists.").color("#FF5555")); return; }
            if (!checkPerm(player.getUuid(), data.permission)) {
                ctx.sendMessage(MessageFormatter.formatWithFallback(EliteEssentials.getInstance().getConfigManager().getMessage("noPermission"), "#FF5555"));
                return;
            }
            boolean backSaved = false;
            for (String cmd : data.command.split(";")) {
                cmd = cmd.trim(); if (cmd.isEmpty()) continue; if (cmd.startsWith("/")) cmd = cmd.substring(1);
                String[] p = cmd.split(" ", 2); String cn = p[0].toLowerCase(); String args = p.length > 1 ? p[1].trim() : "";
                if (!backSaved && (cn.equals("warp") || cn.equals("spawn") || cn.equals("home"))) { saveBack(store, ref, player, world); backSaved = true; }
                runCmd(ctx, store, ref, player, world, cn, args);
            }
        }

        private void runCmd(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef player, World world, String cn, String args) {
            try {
                switch (cn) {
                    case "warp": doWarp(ctx, store, ref, args); break;
                    case "spawn": doSpawn(ctx, store, ref, world); break;
                    case "home": doHome(ctx, store, ref, player, args); break;
                    case "heal": doHeal(ctx, store, ref); break;
                    case "god": doGod(ctx, store, ref, player); break;
                    case "fly": doFly(ctx, store, ref, player); break;
                    default: ctx.sendMessage(Message.raw("Unknown: " + cn).color("#FF5555"));
                }
            } catch (Exception e) { logger.warning("[Alias] " + cn + ": " + e.getMessage()); }
        }

        private void doWarp(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref, String n) {
            if (n.isEmpty()) return;
            Optional<Warp> o = EliteEssentials.getInstance().getWarpService().getWarp(n);
            if (o.isEmpty()) { ctx.sendMessage(MessageFormatter.formatWithFallback(EliteEssentials.getInstance().getConfigManager().getMessage("warpNotFound", "name", n, "list", ""), "#FF5555")); return; }
            Warp w = o.get();
            World targetWorld = Universe.get().getWorld(w.getLocation().getWorld());
            if (targetWorld == null) { ctx.sendMessage(MessageFormatter.formatWithFallback("&cWorld not found: " + w.getLocation().getWorld(), "#FF5555")); return; }
            store.putComponent(ref, Teleport.getComponentType(), new Teleport(targetWorld, new Vector3d(w.getLocation().getX(), w.getLocation().getY(), w.getLocation().getZ()), new Vector3f(0, w.getLocation().getYaw(), 0)));
            ctx.sendMessage(MessageFormatter.formatWithFallback(EliteEssentials.getInstance().getConfigManager().getMessage("warpTeleported", "name", n), "#55FF55"));
        }

        private void doSpawn(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref, World world) {
            var config = EliteEssentials.getInstance().getConfigManager().getConfig();
            String targetWorldName = config.spawn.perWorld ? world.getName() : config.spawn.mainWorld;
            SpawnStorage.SpawnData s = EliteEssentials.getInstance().getSpawnStorage().getSpawn(targetWorldName);
            if (s == null) { ctx.sendMessage(MessageFormatter.formatWithFallback(EliteEssentials.getInstance().getConfigManager().getMessage("spawnNoSpawn"), "#FF5555")); return; }
            World targetWorld = Universe.get().getWorld(targetWorldName);
            if (targetWorld == null) targetWorld = world;
            store.putComponent(ref, Teleport.getComponentType(), new Teleport(targetWorld, new Vector3d(s.x, s.y, s.z), new Vector3f(0, s.yaw, 0)));
            ctx.sendMessage(MessageFormatter.formatWithFallback(EliteEssentials.getInstance().getConfigManager().getMessage("spawnTeleported"), "#55FF55"));
        }

        private void doHome(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef player, String n) {
            if (n.isEmpty()) n = "home";
            var o = EliteEssentials.getInstance().getHomeService().getHome(player.getUuid(), n);
            if (o.isEmpty()) { ctx.sendMessage(MessageFormatter.formatWithFallback(EliteEssentials.getInstance().getConfigManager().getMessage("homeNotFound", "name", n), "#FF5555")); return; }
            var h = o.get();
            store.putComponent(ref, Teleport.getComponentType(), new Teleport(new Vector3d(h.getLocation().getX(), h.getLocation().getY(), h.getLocation().getZ()), new Vector3f(0, h.getLocation().getYaw(), 0)));
            ctx.sendMessage(MessageFormatter.formatWithFallback(EliteEssentials.getInstance().getConfigManager().getMessage("homeTeleported", "name", n), "#55FF55"));
        }

        private void doHeal(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref) {
            EntityStatMap m = store.getComponent(ref, EntityStatMap.getComponentType());
            if (m != null) { m.maximizeStatValue(DefaultEntityStatTypes.getHealth()); ctx.sendMessage(MessageFormatter.formatWithFallback(EliteEssentials.getInstance().getConfigManager().getMessage("healSuccess"), "#55FF55")); }
        }

        private void doGod(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef player) {
            GodService gs = EliteEssentials.getInstance().getGodService();
            boolean on = gs.toggleGodMode(player.getUuid());
            if (on) { store.putComponent(ref, Invulnerable.getComponentType(), Invulnerable.INSTANCE); ctx.sendMessage(MessageFormatter.formatWithFallback(EliteEssentials.getInstance().getConfigManager().getMessage("godEnabled"), "#55FF55")); }
            else { store.removeComponent(ref, Invulnerable.getComponentType()); ctx.sendMessage(MessageFormatter.formatWithFallback(EliteEssentials.getInstance().getConfigManager().getMessage("godDisabled"), "#FF5555")); }
        }

        private void doFly(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef player) {
            MovementManager mm = store.getComponent(ref, MovementManager.getComponentType());
            if (mm == null) return;
            var s = mm.getSettings(); s.canFly = !s.canFly; mm.update(player.getPacketHandler());
            ctx.sendMessage(MessageFormatter.formatWithFallback(EliteEssentials.getInstance().getConfigManager().getMessage(s.canFly ? "flyEnabled" : "flyDisabled"), s.canFly ? "#55FF55" : "#FF5555"));
        }

        private void saveBack(Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef player, World world) {
            try {
                TransformComponent t = store.getComponent(ref, TransformComponent.getComponentType());
                if (t != null) {
                    Vector3d p = t.getPosition(); HeadRotation hr = store.getComponent(ref, HeadRotation.getComponentType()); float y = hr != null ? hr.getRotation().y : 0;
                    EliteEssentials.getInstance().getBackService().pushLocation(player.getUuid(), new com.eliteessentials.model.Location(world.getName(), p.getX(), p.getY(), p.getZ(), y, 0));
                }
            } catch (Exception e) {}
        }

        private boolean checkPerm(UUID id, String perm) {
            PermissionService ps = PermissionService.get();
            if ("everyone".equalsIgnoreCase(perm)) return true;
            if ("op".equalsIgnoreCase(perm)) return ps.isAdmin(id);
            return EliteEssentials.getInstance().getConfigManager().isAdvancedPermissions() ? ps.hasPermission(id, perm) : ps.isAdmin(id);
        }
    }
}
