package com.eliteessentials.integration;

import com.eliteessentials.permissions.Permissions;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Integration with LuckPerms to register all EliteEssentials permissions
 * for autocomplete/discovery in the LuckPerms web editor and commands.
 * 
 * LuckPerms discovers permissions when they are checked at runtime.
 * This class "offers" all our permissions to LuckPerms on startup so they
 * appear in the dropdown immediately without needing to be used first.
 */
public class LuckPermsIntegration {

    private static final Logger logger = Logger.getLogger("EliteEssentials");
    private static boolean registered = false;
    
    private LuckPermsIntegration() {}
    
    /**
     * Schedule permission registration with LuckPerms.
     * Since LuckPerms may load after EliteEssentials, we delay registration.
     */
    public static void registerPermissions() {
        // Schedule registration with a delay to ensure LuckPerms is loaded
        Thread registrationThread = new Thread(() -> {
            // Wait for LuckPerms to load (try multiple times with delays, silently)
            for (int attempt = 1; attempt <= 10; attempt++) {
                try {
                    Thread.sleep(1000 * attempt); // Increasing delay: 1s, 2s, 3s...
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                
                if (tryRegisterPermissions()) {
                    return; // Success
                }
            }
            
            logger.info("[LuckPerms] LuckPerms not detected, skipping permission registration.");
        }, "EliteEssentials-LuckPerms-Registration");
        
        registrationThread.setDaemon(true);
        registrationThread.start();
    }
    
    /**
     * Try to register permissions with LuckPerms.
     * @return true if successful, false if LuckPerms not ready
     */
    private static boolean tryRegisterPermissions() {
        if (registered) {
            return true;
        }
        
        try {
            // Try to get LuckPerms API
            Class<?> providerClass = Class.forName("net.luckperms.api.LuckPermsProvider");
            Method getMethod = providerClass.getMethod("get");
            Object luckPerms = getMethod.invoke(null);
            
            if (luckPerms == null) {
                return false;
            }
            
            // Get all our permissions
            List<String> allPermissions = getAllPermissions();
            
            // Try to register via the internal permission registry
            boolean success = tryRegisterViaTreeView(allPermissions);
            
            if (success) {
                logger.info("[LuckPerms] Registered " + allPermissions.size() + " permissions for autocomplete.");
                registered = true;
                return true;
            } else {
                logger.info("[LuckPerms] Could not register permissions directly. They will appear after first use.");
                registered = true; // Don't keep trying
                return true;
            }
            
        } catch (ClassNotFoundException e) {
            // LuckPerms not installed yet
            return false;
        } catch (IllegalStateException e) {
            // LuckPerms not ready yet
            return false;
        } catch (Exception e) {
            logger.warning("[LuckPerms] Error registering permissions: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Try to register permissions via LuckPerms' internal PermissionRegistry.
     */
    private static boolean tryRegisterViaTreeView(List<String> permissions) {
        try {
            // Access the internal plugin instance
            Class<?> providerClass = Class.forName("net.luckperms.api.LuckPermsProvider");
            Method getMethod = providerClass.getMethod("get");
            Object luckPermsApi = getMethod.invoke(null);
            
            // The API object wraps the internal plugin
            // Try to get the permission registry through reflection
            // LuckPerms stores permissions in AsyncPermissionRegistry which has an offer() method
            
            // First, try to find the plugin instance
            Object plugin = findLuckPermsPlugin(luckPermsApi);
            if (plugin == null) {
                return false;
            }
            
            // Get the permission registry
            Method getPermissionRegistryMethod = findMethod(plugin.getClass(), "getPermissionRegistry");
            if (getPermissionRegistryMethod == null) {
                return false;
            }
            
            Object permissionRegistry = getPermissionRegistryMethod.invoke(plugin);
            if (permissionRegistry == null) {
                return false;
            }
            
            // Find the offer method - it takes a String permission
            Method offerMethod = findMethod(permissionRegistry.getClass(), "offer");
            if (offerMethod == null) {
                // Try insert method
                offerMethod = findMethod(permissionRegistry.getClass(), "insert");
            }
            
            if (offerMethod == null) {
                return false;
            }
            
            // Register all permissions
            for (String permission : permissions) {
                try {
                    offerMethod.invoke(permissionRegistry, permission);
                } catch (Exception e) {
                    // Ignore individual failures
                }
            }
            
            return true;
            
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Find the internal LuckPerms plugin instance from the API.
     */
    private static Object findLuckPermsPlugin(Object api) {
        try {
            // The API implementation usually has a reference to the plugin
            // Try common field/method names
            for (String fieldName : new String[]{"plugin", "luckPerms", "impl"}) {
                try {
                    java.lang.reflect.Field field = api.getClass().getDeclaredField(fieldName);
                    field.setAccessible(true);
                    Object result = field.get(api);
                    if (result != null) {
                        return result;
                    }
                } catch (NoSuchFieldException ignored) {}
            }
            
            // Try method access
            for (String methodName : new String[]{"getPlugin", "getImpl"}) {
                Method method = findMethod(api.getClass(), methodName);
                if (method != null) {
                    Object result = method.invoke(api);
                    if (result != null) {
                        return result;
                    }
                }
            }
            
        } catch (Exception ignored) {}
        
        return null;
    }
    
    /**
     * Find a method by name (ignoring parameters).
     */
    private static Method findMethod(Class<?> clazz, String name) {
        for (Method method : clazz.getMethods()) {
            if (method.getName().equals(name)) {
                return method;
            }
        }
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.getName().equals(name)) {
                method.setAccessible(true);
                return method;
            }
        }
        return null;
    }
    
    /**
     * Get all EliteEssentials permission nodes.
     */
    public static List<String> getAllPermissions() {
        List<String> perms = new ArrayList<>();
        
        // Wildcards
        perms.add("eliteessentials.*");
        perms.add("eliteessentials.command.*");
        perms.add("eliteessentials.admin.*");
        
        // Home commands
        perms.add(Permissions.HOME);
        perms.add(Permissions.SETHOME);
        perms.add(Permissions.DELHOME);
        perms.add(Permissions.HOMES);
        perms.add("eliteessentials.command.home.*");
        perms.add(Permissions.HOME_BYPASS_COOLDOWN);
        perms.add(Permissions.HOME_BYPASS_WARMUP);
        perms.add(Permissions.HOME_LIMIT_UNLIMITED);
        // Common home limits
        for (int limit : new int[]{1, 2, 3, 5, 10, 15, 20, 25, 50, 100}) {
            perms.add(Permissions.homeLimit(limit));
        }
        
        // Teleport commands
        perms.add(Permissions.TPA);
        perms.add(Permissions.TPACCEPT);
        perms.add(Permissions.TPDENY);
        perms.add(Permissions.RTP);
        perms.add(Permissions.BACK);
        perms.add(Permissions.BACK_ONDEATH);
        perms.add("eliteessentials.command.tp.*");
        perms.add(Permissions.TP_BYPASS_COOLDOWN);
        perms.add(Permissions.TP_BYPASS_WARMUP);
        perms.add(Permissions.tpBypassCooldown("rtp"));
        perms.add(Permissions.tpBypassCooldown("back"));
        perms.add(Permissions.tpBypassCooldown("tpa"));
        perms.add(Permissions.tpBypassWarmup("rtp"));
        perms.add(Permissions.tpBypassWarmup("back"));
        perms.add(Permissions.tpBypassWarmup("tpa"));
        
        // Warp commands
        perms.add(Permissions.WARP);
        perms.add(Permissions.WARPS);
        perms.add(Permissions.SETWARP);
        perms.add(Permissions.DELWARP);
        perms.add(Permissions.WARPADMIN);
        perms.add("eliteessentials.command.warp.*");
        perms.add(Permissions.WARP_BYPASS_COOLDOWN);
        perms.add(Permissions.WARP_BYPASS_WARMUP);
        
        // Spawn commands
        perms.add(Permissions.SPAWN);
        perms.add(Permissions.SETSPAWN);
        perms.add("eliteessentials.command.spawn.*");
        perms.add(Permissions.SPAWN_BYPASS_COOLDOWN);
        perms.add(Permissions.SPAWN_BYPASS_WARMUP);
        
        // Misc commands
        perms.add(Permissions.SLEEPPERCENT);
        perms.add(Permissions.GOD);
        perms.add(Permissions.HEAL);
        perms.add(Permissions.MSG);
        perms.add(Permissions.FLY);
        perms.add(Permissions.TOP);
        perms.add("eliteessentials.command.misc.*");
        
        // Kit commands
        perms.add(Permissions.KIT);
        perms.add(Permissions.KIT_CREATE);
        perms.add(Permissions.KIT_DELETE);
        perms.add(Permissions.KIT_BYPASS_COOLDOWN);
        perms.add("eliteessentials.command.kit.*");
        // Note: Kit-specific permissions (e.g., eliteessentials.command.kit.starter) 
        // are registered dynamically when kits are loaded from kits.json
        
        // Spawn protection
        perms.add(Permissions.SPAWN_PROTECTION_BYPASS);
        
        // Admin
        perms.add(Permissions.ADMIN);
        perms.add(Permissions.ADMIN_RELOAD);
        
        return perms;
    }
}
