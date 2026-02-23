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
                logger.info("[LuckPerms] Registered permissions for autocomplete.");
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
        // Note: Custom home limit values (eliteessentials.command.home.limit.<number>) 
        // are dynamically read from LuckPerms - no predefined values needed
        
        // Teleport commands
        perms.add(Permissions.TPA);
        perms.add(Permissions.TPAHERE);
        perms.add(Permissions.TPACCEPT);
        perms.add(Permissions.TPDENY);
        perms.add(Permissions.TPHERE);
        perms.add(Permissions.RTP);
        perms.add(Permissions.BACK);
        perms.add(Permissions.BACK_ONDEATH);
        perms.add(Permissions.TOP);
        perms.add("eliteessentials.command.tp.*");
        perms.add(Permissions.TP_BYPASS_COOLDOWN);
        perms.add(Permissions.TP_BYPASS_WARMUP);
        perms.add(Permissions.tpBypassCooldown("rtp"));
        perms.add(Permissions.tpBypassCooldown("back"));
        perms.add(Permissions.tpBypassCooldown("tpa"));
        perms.add(Permissions.tpBypassCooldown("tpahere"));
        perms.add(Permissions.tpBypassCooldown("tphere"));
        perms.add(Permissions.tpBypassWarmup("rtp"));
        perms.add(Permissions.tpBypassWarmup("back"));
        perms.add(Permissions.tpBypassWarmup("tpa"));
        perms.add(Permissions.tpBypassWarmup("tpahere"));
        // Note: Custom cooldown values (eliteessentials.command.tp.cooldown.<cmd>.<seconds>) 
        // are dynamically read from LuckPerms - no predefined values needed
        // Note: Custom warmup values (eliteessentials.command.tp.warmup.<cmd>.<seconds>,
        // eliteessentials.command.home.warmup.<seconds>, eliteessentials.command.spawn.warmup.<seconds>,
        // eliteessentials.command.warp.warmup.<seconds>) are dynamically read from LuckPerms
        
        // Warp commands
        perms.add(Permissions.WARP);
        perms.add(Permissions.WARPS);
        perms.add(Permissions.SETWARP);
        perms.add(Permissions.DELWARP);
        perms.add(Permissions.WARPADMIN);
        perms.add("eliteessentials.command.warp.*");
        perms.add(Permissions.WARP_BYPASS_COOLDOWN);
        perms.add(Permissions.WARP_BYPASS_WARMUP);
        perms.add(Permissions.WARP_LIMIT_UNLIMITED);
        // Note: Custom warp limit values (eliteessentials.command.warp.limit.<number>) 
        // are dynamically read from LuckPerms - no predefined values needed
        
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
        perms.add(Permissions.HEAL_BYPASS_COOLDOWN);
        perms.add(Permissions.MSG);
        perms.add(Permissions.FLY);
        perms.add(Permissions.FLYSPEED);
        perms.add(Permissions.MOTD);
        perms.add(Permissions.RULES);
        perms.add(Permissions.BROADCAST);
        perms.add(Permissions.CLEARINV);
        perms.add(Permissions.LIST);
        perms.add(Permissions.DISCORD);
        perms.add(Permissions.SEEN);
        perms.add(Permissions.EEHELP);
        perms.add(Permissions.GROUP_CHAT);
        perms.add(Permissions.CHATS_LIST);
        perms.add("eliteessentials.command.misc.*");
        
        // Chat channel permissions (permission-based chats)
        perms.add("eliteessentials.chat.*");
        perms.add(Permissions.CHAT_COLOR);
        perms.add(Permissions.CHAT_FORMAT);
        // Common chat channel permissions
        perms.add(Permissions.chatAccess("trade"));
        perms.add(Permissions.chatAccess("help"));
        perms.add(Permissions.chatAccess("global"));
        
        // Kit commands
        perms.add(Permissions.KIT);
        perms.add(Permissions.KIT_GUI);
        perms.add(Permissions.KIT_CREATE);
        perms.add(Permissions.KIT_DELETE);
        perms.add(Permissions.KIT_BYPASS_COOLDOWN);
        perms.add("eliteessentials.command.kit.*");
        // Note: Kit-specific permissions (e.g., eliteessentials.command.kit.starter) 
        // are registered dynamically when kits are loaded from kits.json
        
        // Spawn protection
        perms.add(Permissions.SPAWN_PROTECTION_BYPASS);
        
        // Economy commands
        perms.add(Permissions.WALLET);
        perms.add(Permissions.WALLET_OTHERS);
        perms.add(Permissions.WALLET_ADMIN);
        perms.add(Permissions.PAY);
        perms.add(Permissions.BALTOP);
        perms.add("eliteessentials.command.economy.*");
        
        // Bypass permissions
        perms.add(Permissions.BYPASS_COST);
        perms.add(Permissions.bypassCost("home"));
        perms.add(Permissions.bypassCost("sethome"));
        perms.add(Permissions.bypassCost("spawn"));
        perms.add(Permissions.bypassCost("warp"));
        perms.add(Permissions.bypassCost("back"));
        perms.add(Permissions.bypassCost("rtp"));
        perms.add(Permissions.bypassCost("tpa"));
        perms.add(Permissions.bypassCost("tpahere"));
        perms.add("eliteessentials.bypass.*");
        perms.add("eliteessentials.bypass.cost.*");
        
        // Admin
        perms.add(Permissions.ADMIN);
        perms.add(Permissions.ADMIN_RELOAD);
        perms.add(Permissions.ADMIN_ALIAS);
        perms.add(Permissions.ADMIN_SENDMESSAGE);
        perms.add(Permissions.ADMIN_RTP);
        
        return perms;
    }
    
    // ==================== LUCKPERMS UTILITY METHODS ====================
    
    /**
     * Check if LuckPerms is available.
     */
    public static boolean isAvailable() {
        try {
            Class.forName("net.luckperms.api.LuckPermsProvider");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
    
    /**
     * Get the primary group for a player.
     * @param playerId Player UUID
     * @return Primary group name, or null if not found
     */
    public static String getPrimaryGroup(java.util.UUID playerId) {
        try {
            Class<?> providerClass = Class.forName("net.luckperms.api.LuckPermsProvider");
            Method getMethod = providerClass.getMethod("get");
            Object luckPerms = getMethod.invoke(null);
            
            // Get UserManager
            Method getUserManagerMethod = luckPerms.getClass().getMethod("getUserManager");
            Object userManager = getUserManagerMethod.invoke(luckPerms);
            
            // Get User
            Method getUserMethod = userManager.getClass().getMethod("getUser", java.util.UUID.class);
            Object user = getUserMethod.invoke(userManager, playerId);
            
            if (user == null) {
                return null;
            }
            
            // Get primary group
            Method getPrimaryGroupMethod = user.getClass().getMethod("getPrimaryGroup");
            return (String) getPrimaryGroupMethod.invoke(user);
            
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Get all groups for a player (including inherited groups).
     * @param playerId Player UUID
     * @return List of group names, or empty list if not found
     */
    public static List<String> getGroups(java.util.UUID playerId) {
        List<String> groups = new ArrayList<>();
        Logger logger = Logger.getLogger("EliteEssentials");
        
        try {
            Class<?> providerClass = Class.forName("net.luckperms.api.LuckPermsProvider");
            Method getMethod = providerClass.getMethod("get");
            Object luckPerms = getMethod.invoke(null);
            
            // Get UserManager
            Method getUserManagerMethod = luckPerms.getClass().getMethod("getUserManager");
            Object userManager = getUserManagerMethod.invoke(luckPerms);
            
            // Try to get user from cache first
            Method getUserMethod = userManager.getClass().getMethod("getUser", java.util.UUID.class);
            Object user = getUserMethod.invoke(userManager, playerId);
            
            // If user not in cache, try to load it synchronously (blocking, but necessary for chat)
            if (user == null) {
                try {
                    Method loadUserMethod = userManager.getClass().getMethod("loadUser", java.util.UUID.class);
                    Object completableFuture = loadUserMethod.invoke(userManager, playerId);
                    
                    // Wait for user to load (with timeout)
                    Method joinMethod = completableFuture.getClass().getMethod("join");
                    user = joinMethod.invoke(completableFuture);
                } catch (Exception loadEx) {
                    logger.warning("[LuckPerms] Failed to load user: " + loadEx.getMessage());
                    return groups;
                }
            }
            
            if (user == null) {
                logger.warning("[LuckPerms] User is null after load attempt");
                return groups;
            }
            
            // Get the user's nodes to find group memberships
            // Try different methods to get groups
            try {
                // Method 1: Try getNodes() and filter for group nodes
                Method getNodesMethod = user.getClass().getMethod("getNodes");
                Object nodesCollection = getNodesMethod.invoke(user);
                
                if (nodesCollection instanceof java.util.Collection) {
                    for (Object node : (java.util.Collection<?>) nodesCollection) {
                        // Check if this is a group node
                        Method getKeyMethod = node.getClass().getMethod("getKey");
                        String key = (String) getKeyMethod.invoke(node);
                        
                        // Group nodes start with "group."
                        if (key.startsWith("group.")) {
                            String groupName = key.substring(6); // Remove "group." prefix
                            groups.add(groupName);
                        }
                    }
                }
            } catch (Exception e1) {
                // Method 2: Try getPrimaryGroup() as fallback
                try {
                    Method getPrimaryGroupMethod = user.getClass().getMethod("getPrimaryGroup");
                    String primaryGroup = (String) getPrimaryGroupMethod.invoke(user);
                    if (primaryGroup != null) {
                        groups.add(primaryGroup);
                    }
                } catch (Exception e2) {
                    logger.warning("[LuckPerms] Could not get groups using any method");
                }
            }
            
        } catch (Exception e) {
            logger.warning("[LuckPerms] Error getting groups: " + e.getMessage());
        }
        
        return groups;
    }
    
    // ==================== LUCKPERMS MODIFICATION METHODS ====================
    
    /**
     * Set a player's primary group (removes all other groups and sets this one).
     * Equivalent to: /lp user <player> parent set <group>
     * 
     * @param playerId Player UUID
     * @param groupName Group to set
     * @return true if successful
     */
    public static boolean setGroup(java.util.UUID playerId, String groupName) {
        Logger logger = Logger.getLogger("EliteEssentials");
        
        try {
            Object[] lpObjects = getLuckPermsObjects(playerId);
            if (lpObjects == null) {
                logger.warning("[LuckPerms] Could not get LuckPerms objects for setGroup");
                return false;
            }
            
            Object luckPerms = lpObjects[0];
            Object userManager = lpObjects[1];
            Object user = lpObjects[2];
            
            // Create InheritanceNode for the new group
            Class<?> inheritanceNodeClass = Class.forName("net.luckperms.api.node.types.InheritanceNode");
            Method builderMethod = inheritanceNodeClass.getMethod("builder", String.class);
            Object nodeBuilder = builderMethod.invoke(null, groupName);
            Method buildMethod = nodeBuilder.getClass().getMethod("build");
            Object newGroupNode = buildMethod.invoke(nodeBuilder);
            
            // Get user's data
            Method dataMethod = user.getClass().getMethod("data");
            Object userData = dataMethod.invoke(user);
            
            // Clear existing group nodes - find and use the clear method with setAccessible
            Class<?> nodeClass = Class.forName("net.luckperms.api.node.Node");
            
            // Try to clear by NodeType first
            try {
                Class<?> nodeTypeClass = Class.forName("net.luckperms.api.node.NodeType");
                java.lang.reflect.Field inheritanceField = nodeTypeClass.getField("INHERITANCE");
                Object inheritanceType = inheritanceField.get(null);
                
                Method clearMethod = findMethodByParamType(userData.getClass(), "clear", nodeTypeClass);
                if (clearMethod != null) {
                    clearMethod.setAccessible(true);
                    clearMethod.invoke(userData, inheritanceType);
                }
            } catch (Exception clearEx) {
                // Fallback: remove existing group nodes manually
                Method getNodesMethod = user.getClass().getMethod("getNodes");
                Object nodesCollection = getNodesMethod.invoke(user);
                if (nodesCollection instanceof java.util.Collection) {
                    Method removeMethod = findMethodByParamType(userData.getClass(), "remove", nodeClass);
                    if (removeMethod != null) {
                        removeMethod.setAccessible(true);
                        for (Object node : new ArrayList<>((java.util.Collection<?>) nodesCollection)) {
                            Method getKeyMethod = node.getClass().getMethod("getKey");
                            String key = (String) getKeyMethod.invoke(node);
                            if (key.startsWith("group.")) {
                                removeMethod.invoke(userData, node);
                            }
                        }
                    }
                }
            }
            
            // Add the new group node
            Method addMethod = findMethodByParamType(userData.getClass(), "add", nodeClass);
            if (addMethod != null) {
                addMethod.setAccessible(true);
                addMethod.invoke(userData, newGroupNode);
            }
            
            // Save the user
            Class<?> userClass = Class.forName("net.luckperms.api.model.user.User");
            Method saveUserMethod = findMethodByParamType(userManager.getClass(), "saveUser", userClass);
            if (saveUserMethod != null) {
                saveUserMethod.setAccessible(true);
                Object saveFuture = saveUserMethod.invoke(userManager, user);
                Method joinMethod = saveFuture.getClass().getMethod("join");
                joinMethod.invoke(saveFuture);
            }
            
            return true;
            
        } catch (Exception e) {
            logger.warning("[LuckPerms] Error setting group: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Add a player to a group (keeps existing groups).
     * Equivalent to: /lp user <player> parent add <group>
     * 
     * @param playerId Player UUID
     * @param groupName Group to add
     * @return true if successful
     */
    public static boolean addGroup(java.util.UUID playerId, String groupName) {
        Logger logger = Logger.getLogger("EliteEssentials");
        
        try {
            Object[] lpObjects = getLuckPermsObjects(playerId);
            if (lpObjects == null) {
                logger.warning("[LuckPerms] Could not get LuckPerms objects for addGroup");
                return false;
            }
            
            Object luckPerms = lpObjects[0];
            Object userManager = lpObjects[1];
            Object user = lpObjects[2];
            
            // Create InheritanceNode for the group
            Class<?> inheritanceNodeClass = Class.forName("net.luckperms.api.node.types.InheritanceNode");
            Method builderMethod = inheritanceNodeClass.getMethod("builder", String.class);
            Object nodeBuilder = builderMethod.invoke(null, groupName);
            Method buildMethod = nodeBuilder.getClass().getMethod("build");
            Object newGroupNode = buildMethod.invoke(nodeBuilder);
            
            // Add to user's data
            Class<?> nodeClass = Class.forName("net.luckperms.api.node.Node");
            Method dataMethod = user.getClass().getMethod("data");
            Object userData = dataMethod.invoke(user);
            Method addMethod = findMethodByParamType(userData.getClass(), "add", nodeClass);
            if (addMethod != null) {
                addMethod.setAccessible(true);
                addMethod.invoke(userData, newGroupNode);
            }
            
            // Save the user
            Class<?> userClass = Class.forName("net.luckperms.api.model.user.User");
            Method saveUserMethod = findMethodByParamType(userManager.getClass(), "saveUser", userClass);
            if (saveUserMethod != null) {
                saveUserMethod.setAccessible(true);
                Object saveFuture = saveUserMethod.invoke(userManager, user);
                Method joinMethod = saveFuture.getClass().getMethod("join");
                joinMethod.invoke(saveFuture);
            }
            
            return true;
            
        } catch (Exception e) {
            logger.warning("[LuckPerms] Error adding group: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Remove a player from a group.
     * Equivalent to: /lp user <player> parent remove <group>
     * 
     * @param playerId Player UUID
     * @param groupName Group to remove
     * @return true if successful
     */
    public static boolean removeGroup(java.util.UUID playerId, String groupName) {
        Logger logger = Logger.getLogger("EliteEssentials");
        
        try {
            Object[] lpObjects = getLuckPermsObjects(playerId);
            if (lpObjects == null) {
                logger.warning("[LuckPerms] Could not get LuckPerms objects for removeGroup");
                return false;
            }
            
            Object luckPerms = lpObjects[0];
            Object userManager = lpObjects[1];
            Object user = lpObjects[2];
            
            // Create InheritanceNode for the group to remove
            Class<?> inheritanceNodeClass = Class.forName("net.luckperms.api.node.types.InheritanceNode");
            Method builderMethod = inheritanceNodeClass.getMethod("builder", String.class);
            Object nodeBuilder = builderMethod.invoke(null, groupName);
            Method buildMethod = nodeBuilder.getClass().getMethod("build");
            Object groupNode = buildMethod.invoke(nodeBuilder);
            
            // Remove from user's data
            Class<?> nodeClass = Class.forName("net.luckperms.api.node.Node");
            Method dataMethod = user.getClass().getMethod("data");
            Object userData = dataMethod.invoke(user);
            Method removeMethod = findMethodByParamType(userData.getClass(), "remove", nodeClass);
            if (removeMethod != null) {
                removeMethod.setAccessible(true);
                removeMethod.invoke(userData, groupNode);
            }
            
            // Save the user
            Class<?> userClass = Class.forName("net.luckperms.api.model.user.User");
            Method saveUserMethod = findMethodByParamType(userManager.getClass(), "saveUser", userClass);
            if (saveUserMethod != null) {
                saveUserMethod.setAccessible(true);
                Object saveFuture = saveUserMethod.invoke(userManager, user);
                Method joinMethod = saveFuture.getClass().getMethod("join");
                joinMethod.invoke(saveFuture);
            }
            
            return true;
            
        } catch (Exception e) {
            logger.warning("[LuckPerms] Error removing group: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Set a permission for a player.
     * Equivalent to: /lp user <player> permission set <permission> [true/false]
     * 
     * @param playerId Player UUID
     * @param permission Permission node
     * @param value true to grant, false to deny
     * @return true if successful
     */
    public static boolean setPermission(java.util.UUID playerId, String permission, boolean value) {
        Logger logger = Logger.getLogger("EliteEssentials");
        
        try {
            Object[] lpObjects = getLuckPermsObjects(playerId);
            if (lpObjects == null) {
                logger.warning("[LuckPerms] Could not get LuckPerms objects for setPermission");
                return false;
            }
            
            Object luckPerms = lpObjects[0];
            Object userManager = lpObjects[1];
            Object user = lpObjects[2];
            
            // Create PermissionNode
            Class<?> permissionNodeClass = Class.forName("net.luckperms.api.node.types.PermissionNode");
            Method builderMethod = permissionNodeClass.getMethod("builder", String.class);
            Object nodeBuilder = builderMethod.invoke(null, permission);
            
            // Set value (true/false)
            Method valueMethod = nodeBuilder.getClass().getMethod("value", boolean.class);
            nodeBuilder = valueMethod.invoke(nodeBuilder, value);
            
            Method buildMethod = nodeBuilder.getClass().getMethod("build");
            Object permNode = buildMethod.invoke(nodeBuilder);
            
            // Add to user's data
            Class<?> nodeClass = Class.forName("net.luckperms.api.node.Node");
            Method dataMethod = user.getClass().getMethod("data");
            Object userData = dataMethod.invoke(user);
            Method addMethod = findMethodByParamType(userData.getClass(), "add", nodeClass);
            if (addMethod != null) {
                addMethod.setAccessible(true);
                addMethod.invoke(userData, permNode);
            }
            
            // Save the user
            Class<?> userClass = Class.forName("net.luckperms.api.model.user.User");
            Method saveUserMethod = findMethodByParamType(userManager.getClass(), "saveUser", userClass);
            if (saveUserMethod != null) {
                saveUserMethod.setAccessible(true);
                Object saveFuture = saveUserMethod.invoke(userManager, user);
                Method joinMethod = saveFuture.getClass().getMethod("join");
                joinMethod.invoke(saveFuture);
            }
            
            return true;
            
        } catch (Exception e) {
            logger.warning("[LuckPerms] Error setting permission: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Remove a permission from a player.
     * Equivalent to: /lp user <player> permission unset <permission>
     * 
     * @param playerId Player UUID
     * @param permission Permission node to remove
     * @return true if successful
     */
    public static boolean unsetPermission(java.util.UUID playerId, String permission) {
        Logger logger = Logger.getLogger("EliteEssentials");
        
        try {
            Object[] lpObjects = getLuckPermsObjects(playerId);
            if (lpObjects == null) {
                logger.warning("[LuckPerms] Could not get LuckPerms objects for unsetPermission");
                return false;
            }
            
            Object luckPerms = lpObjects[0];
            Object userManager = lpObjects[1];
            Object user = lpObjects[2];
            
            // Create PermissionNode to remove
            Class<?> permissionNodeClass = Class.forName("net.luckperms.api.node.types.PermissionNode");
            Method builderMethod = permissionNodeClass.getMethod("builder", String.class);
            Object nodeBuilder = builderMethod.invoke(null, permission);
            Method buildMethod = nodeBuilder.getClass().getMethod("build");
            Object permNode = buildMethod.invoke(nodeBuilder);
            
            // Remove from user's data
            Class<?> nodeClass = Class.forName("net.luckperms.api.node.Node");
            Method dataMethod = user.getClass().getMethod("data");
            Object userData = dataMethod.invoke(user);
            Method removeMethod = findMethodByParamType(userData.getClass(), "remove", nodeClass);
            if (removeMethod != null) {
                removeMethod.setAccessible(true);
                removeMethod.invoke(userData, permNode);
            }
            
            // Save the user
            Class<?> userClass = Class.forName("net.luckperms.api.model.user.User");
            Method saveUserMethod = findMethodByParamType(userManager.getClass(), "saveUser", userClass);
            if (saveUserMethod != null) {
                saveUserMethod.setAccessible(true);
                Object saveFuture = saveUserMethod.invoke(userManager, user);
                Method joinMethod = saveFuture.getClass().getMethod("join");
                joinMethod.invoke(saveFuture);
            }
            
            return true;
            
        } catch (Exception e) {
            logger.warning("[LuckPerms] Error unsetting permission: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Helper method to get LuckPerms API objects for a player.
     * @return [luckPerms, userManager, user] or null if failed
     */
    private static Object[] getLuckPermsObjects(java.util.UUID playerId) {
        Logger logger = Logger.getLogger("EliteEssentials");
        
        try {
            Class<?> providerClass = Class.forName("net.luckperms.api.LuckPermsProvider");
            Method getMethod = providerClass.getMethod("get");
            Object luckPerms = getMethod.invoke(null);
            
            if (luckPerms == null) {
                return null;
            }
            
            // Get UserManager
            Method getUserManagerMethod = luckPerms.getClass().getMethod("getUserManager");
            Object userManager = getUserManagerMethod.invoke(luckPerms);
            
            // Try to get user from cache first
            Method getUserMethod = userManager.getClass().getMethod("getUser", java.util.UUID.class);
            Object user = getUserMethod.invoke(userManager, playerId);
            
            // If user not in cache, load it
            if (user == null) {
                Method loadUserMethod = userManager.getClass().getMethod("loadUser", java.util.UUID.class);
                Object completableFuture = loadUserMethod.invoke(userManager, playerId);
                Method joinMethod = completableFuture.getClass().getMethod("join");
                user = joinMethod.invoke(completableFuture);
            }
            
            if (user == null) {
                return null;
            }
            
            return new Object[] { luckPerms, userManager, user };
            
        } catch (Exception e) {
            logger.warning("[LuckPerms] Error getting LP objects: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Find and invoke a method, making it accessible if needed.
     */
    private static Object invokeMethod(Object target, String methodName, Class<?>[] paramTypes, Object... args) throws Exception {
        Method method = null;
        
        // Try public methods first
        try {
            method = target.getClass().getMethod(methodName, paramTypes);
        } catch (NoSuchMethodException e) {
            // Try declared methods (including private)
            method = target.getClass().getDeclaredMethod(methodName, paramTypes);
        }
        
        method.setAccessible(true);
        return method.invoke(target, args);
    }
    
    /**
     * Find a method by name with a specific parameter type name.
     */
    private static Method findMethod(Class<?> clazz, String name, String paramTypeName) {
        for (Method method : clazz.getMethods()) {
            if (method.getName().equals(name)) {
                Class<?>[] params = method.getParameterTypes();
                if (params.length == 1 && params[0].getName().equals(paramTypeName)) {
                    return method;
                }
            }
        }
        return null;
    }
    
    /**
     * Find a method by name with a specific parameter type class.
     */
    private static Method findMethodByParamType(Class<?> clazz, String name, Class<?> paramType) {
        // Check all methods including inherited
        for (Method method : clazz.getMethods()) {
            if (method.getName().equals(name)) {
                Class<?>[] params = method.getParameterTypes();
                if (params.length == 1 && params[0].isAssignableFrom(paramType)) {
                    return method;
                }
            }
        }
        // Also check declared methods
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.getName().equals(name)) {
                Class<?>[] params = method.getParameterTypes();
                if (params.length == 1 && params[0].isAssignableFrom(paramType)) {
                    return method;
                }
            }
        }
        return null;
    }
    
    /**
     * Promote a player along a track.
     * Equivalent to: /lp user <player> promote <track>
     * 
     * @param playerId Player UUID
     * @param trackName Track to promote along
     * @return true if successful
     */
    public static boolean promote(java.util.UUID playerId, String trackName) {
        Logger logger = Logger.getLogger("EliteEssentials");
        
        try {
            Object[] lpObjects = getLuckPermsObjects(playerId);
            if (lpObjects == null) {
                logger.warning("[LuckPerms] Could not get LuckPerms objects for promote");
                return false;
            }
            
            Object luckPerms = lpObjects[0];
            Object userManager = lpObjects[1];
            Object user = lpObjects[2];
            
            // Get TrackManager
            Method getTrackManagerMethod = luckPerms.getClass().getMethod("getTrackManager");
            Object trackManager = getTrackManagerMethod.invoke(luckPerms);
            
            // Get the track
            Method getTrackMethod = trackManager.getClass().getMethod("getTrack", String.class);
            Object track = getTrackMethod.invoke(trackManager, trackName);
            
            if (track == null) {
                logger.warning("[LuckPerms] Track not found: " + trackName);
                return false;
            }
            
            // Get track groups
            Method getGroupsMethod = track.getClass().getMethod("getGroups");
            @SuppressWarnings("unchecked")
            List<String> trackGroups = (List<String>) getGroupsMethod.invoke(track);
            
            if (trackGroups == null || trackGroups.isEmpty()) {
                logger.warning("[LuckPerms] Track has no groups: " + trackName);
                return false;
            }
            
            // Get user's current primary group
            Method getPrimaryGroupMethod = user.getClass().getMethod("getPrimaryGroup");
            String currentGroup = (String) getPrimaryGroupMethod.invoke(user);
            
            // Find current position in track
            int currentIndex = trackGroups.indexOf(currentGroup);
            
            // Determine next group
            String nextGroup;
            if (currentIndex == -1) {
                // Not on track, add to first group
                nextGroup = trackGroups.get(0);
            } else if (currentIndex >= trackGroups.size() - 1) {
                // Already at top of track
                return true;
            } else {
                // Promote to next group
                nextGroup = trackGroups.get(currentIndex + 1);
            }
            
            // Remove current group if on track, add next group
            if (currentIndex >= 0) {
                removeGroup(playerId, currentGroup);
            }
            boolean result = addGroup(playerId, nextGroup);
            
            return result;
            
        } catch (Exception e) {
            logger.warning("[LuckPerms] Error promoting: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Demote a player along a track.
     * Equivalent to: /lp user <player> demote <track>
     * 
     * @param playerId Player UUID
     * @param trackName Track to demote along
     * @return true if successful
     */
    public static boolean demote(java.util.UUID playerId, String trackName) {
        Logger logger = Logger.getLogger("EliteEssentials");
        
        try {
            Object[] lpObjects = getLuckPermsObjects(playerId);
            if (lpObjects == null) {
                logger.warning("[LuckPerms] Could not get LuckPerms objects for demote");
                return false;
            }
            
            Object luckPerms = lpObjects[0];
            Object userManager = lpObjects[1];
            Object user = lpObjects[2];
            
            // Get TrackManager
            Method getTrackManagerMethod = luckPerms.getClass().getMethod("getTrackManager");
            Object trackManager = getTrackManagerMethod.invoke(luckPerms);
            
            // Get the track
            Method getTrackMethod = trackManager.getClass().getMethod("getTrack", String.class);
            Object track = getTrackMethod.invoke(trackManager, trackName);
            
            if (track == null) {
                logger.warning("[LuckPerms] Track not found: " + trackName);
                return false;
            }
            
            // Get track groups
            Method getGroupsMethod = track.getClass().getMethod("getGroups");
            @SuppressWarnings("unchecked")
            List<String> trackGroups = (List<String>) getGroupsMethod.invoke(track);
            
            if (trackGroups == null || trackGroups.isEmpty()) {
                logger.warning("[LuckPerms] Track has no groups: " + trackName);
                return false;
            }
            
            // Get user's current primary group
            Method getPrimaryGroupMethod = user.getClass().getMethod("getPrimaryGroup");
            String currentGroup = (String) getPrimaryGroupMethod.invoke(user);
            
            // Find current position in track
            int currentIndex = trackGroups.indexOf(currentGroup);
            
            if (currentIndex == -1) {
                // Not on track
                return false;
            } else if (currentIndex == 0) {
                // Already at bottom of track
                return true;
            }
            
            // Demote to previous group
            String prevGroup = trackGroups.get(currentIndex - 1);
            
            // Remove current group, add previous group
            removeGroup(playerId, currentGroup);
            boolean result = addGroup(playerId, prevGroup);
            
            return result;
            
        } catch (Exception e) {
            logger.warning("[LuckPerms] Error demoting: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    // ==================== PERMISSION VALUE EXTRACTION ====================
    
    /**
     * Get a numeric value from a permission node pattern.
     * Scans user's permissions for nodes matching the prefix and extracts the number.
     * 
     * Example: getPermissionValue(uuid, "eliteessentials.command.misc.repair.cooldown.") 
     * would find "eliteessentials.command.misc.repair.cooldown.1000" and return 1000.
     * 
     * @param playerId Player UUID
     * @param permissionPrefix Permission prefix to search for (e.g., "eliteessentials.command.misc.repair.cooldown.")
     * @return The numeric value found, or -1 if not found
     */
    public static int getPermissionValue(java.util.UUID playerId, String permissionPrefix) {
        try {
            Object[] lpObjects = getLuckPermsObjects(playerId);
            if (lpObjects == null) {
                return -1;
            }
            
            Object user = lpObjects[2];
            
            // Get all nodes from the user (includes inherited permissions)
            Method getNodesMethod = user.getClass().getMethod("getNodes");
            Object nodesCollection = getNodesMethod.invoke(user);
            
            if (!(nodesCollection instanceof java.util.Collection)) {
                return -1;
            }
            
            int lowestValue = Integer.MAX_VALUE;
            boolean found = false;
            
            for (Object node : (java.util.Collection<?>) nodesCollection) {
                // Get the permission key
                Method getKeyMethod = node.getClass().getMethod("getKey");
                String key = (String) getKeyMethod.invoke(node);
                
                // Check if it matches our prefix
                if (key.startsWith(permissionPrefix)) {
                    String valuePart = key.substring(permissionPrefix.length());
                    try {
                        int value = Integer.parseInt(valuePart);
                        // Take the lowest value (most favorable to player)
                        if (value < lowestValue) {
                            lowestValue = value;
                            found = true;
                        }
                    } catch (NumberFormatException ignored) {
                        // Not a number, skip
                    }
                }
            }
            
            return found ? lowestValue : -1;
            
        } catch (Exception e) {
            return -1;
        }
    }
    
    /**
     * Get a numeric value from a permission node pattern, checking inherited permissions too.
     * This version resolves all inherited permissions from groups.
     * 
     * @param playerId Player UUID
     * @param permissionPrefix Permission prefix to search for
     * @return The numeric value found (lowest/most favorable), or -1 if not found
     */
    public static int getInheritedPermissionValue(java.util.UUID playerId, String permissionPrefix) {
        try {
            Object[] lpObjects = getLuckPermsObjects(playerId);
            if (lpObjects == null) {
                return -1;
            }
            
            Object user = lpObjects[2];
            
            // Try to get resolved/inherited nodes using CachedData
            try {
                Method getCachedDataMethod = user.getClass().getMethod("getCachedData");
                Object cachedData = getCachedDataMethod.invoke(user);
                
                Method getPermissionDataMethod = cachedData.getClass().getMethod("getPermissionData");
                Object permissionData = getPermissionDataMethod.invoke(cachedData);
                
                // Get the permission map
                Method getPermissionMapMethod = permissionData.getClass().getMethod("getPermissionMap");
                @SuppressWarnings("unchecked")
                java.util.Map<String, Boolean> permMap = (java.util.Map<String, Boolean>) getPermissionMapMethod.invoke(permissionData);
                
                int lowestValue = Integer.MAX_VALUE;
                boolean found = false;
                
                for (java.util.Map.Entry<String, Boolean> entry : permMap.entrySet()) {
                    if (entry.getValue() && entry.getKey().startsWith(permissionPrefix)) {
                        String valuePart = entry.getKey().substring(permissionPrefix.length());
                        try {
                            int value = Integer.parseInt(valuePart);
                            if (value < lowestValue) {
                                lowestValue = value;
                                found = true;
                            }
                        } catch (NumberFormatException ignored) {}
                    }
                }
                
                if (found) {
                    return lowestValue;
                }
            } catch (Exception e) {
                // Fall through to direct node check
            }
            
            // Fallback to direct node check
            return getPermissionValue(playerId, permissionPrefix);
            
        } catch (Exception e) {
            return -1;
        }
    }
    
    /**
     * Get a meta value for a player.
     * LuckPerms meta values are set via: /lp user <player> meta set <key> <value>
     * 
     * @param playerId Player UUID
     * @param metaKey Meta key to look up
     * @return The meta value, or null if not found
     */
    public static String getMetaValue(java.util.UUID playerId, String metaKey) {
        try {
            Object[] lpObjects = getLuckPermsObjects(playerId);
            if (lpObjects == null) {
                return null;
            }
            
            Object user = lpObjects[2];
            
            // Get CachedData -> MetaData
            Method getCachedDataMethod = user.getClass().getMethod("getCachedData");
            Object cachedData = getCachedDataMethod.invoke(user);
            
            Method getMetaDataMethod = cachedData.getClass().getMethod("getMetaData");
            Object metaData = getMetaDataMethod.invoke(cachedData);
            
            // Get meta value
            Method getMetaValueMethod = metaData.getClass().getMethod("getMetaValue", String.class);
            return (String) getMetaValueMethod.invoke(metaData, metaKey);
            
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Get a meta value as an integer.
     * 
     * @param playerId Player UUID
     * @param metaKey Meta key to look up
     * @param defaultValue Default value if not found or not a number
     * @return The meta value as int, or defaultValue if not found
     */
    public static int getMetaValueInt(java.util.UUID playerId, String metaKey, int defaultValue) {
        String value = getMetaValue(playerId, metaKey);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    // ==================== PREFIX/SUFFIX METHODS ====================
    
    /**
     * Get the player's prefix from LuckPerms.
     * This returns the highest priority prefix assigned to the player (directly or via groups).
     * Set via: /lp user <player> meta setprefix <priority> <prefix>
     * Or on groups: /lp group <group> meta setprefix <priority> <prefix>
     * 
     * @param playerId Player UUID
     * @return The prefix string, or empty string if not set
     */
    public static String getPrefix(java.util.UUID playerId) {
        try {
            Object[] lpObjects = getLuckPermsObjects(playerId);
            if (lpObjects == null) {
                return "";
            }
            
            Object user = lpObjects[2];
            
            // Get CachedData -> MetaData
            Method getCachedDataMethod = user.getClass().getMethod("getCachedData");
            Object cachedData = getCachedDataMethod.invoke(user);
            
            Method getMetaDataMethod = cachedData.getClass().getMethod("getMetaData");
            Object metaData = getMetaDataMethod.invoke(cachedData);
            
            // Get prefix - this returns the highest priority prefix
            Method getPrefixMethod = metaData.getClass().getMethod("getPrefix");
            String prefix = (String) getPrefixMethod.invoke(metaData);
            
            return prefix != null ? prefix : "";
            
        } catch (Exception e) {
            return "";
        }
    }
    
    /**
     * Get the player's suffix from LuckPerms.
     * This returns the highest priority suffix assigned to the player (directly or via groups).
     * Set via: /lp user <player> meta setsuffix <priority> <suffix>
     * Or on groups: /lp group <group> meta setsuffix <priority> <suffix>
     * 
     * @param playerId Player UUID
     * @return The suffix string, or empty string if not set
     */
    public static String getSuffix(java.util.UUID playerId) {
        try {
            Object[] lpObjects = getLuckPermsObjects(playerId);
            if (lpObjects == null) {
                return "";
            }
            
            Object user = lpObjects[2];
            
            // Get CachedData -> MetaData
            Method getCachedDataMethod = user.getClass().getMethod("getCachedData");
            Object cachedData = getCachedDataMethod.invoke(user);
            
            Method getMetaDataMethod = cachedData.getClass().getMethod("getMetaData");
            Object metaData = getMetaDataMethod.invoke(cachedData);
            
            // Get suffix - this returns the highest priority suffix
            Method getSuffixMethod = metaData.getClass().getMethod("getSuffix");
            String suffix = (String) getSuffixMethod.invoke(metaData);
            
            return suffix != null ? suffix : "";
            
        } catch (Exception e) {
            return "";
        }
    }
    
    /**
     * Get the player's primary group display name from LuckPerms.
     * This is the primary group the player belongs to.
     * 
     * @param playerId Player UUID
     * @return The primary group name, or empty string if not found
     */
    public static String getPrimaryGroupDisplay(java.util.UUID playerId) {
        String group = getPrimaryGroup(playerId);
        return group != null ? group : "";
    }

    /**
     * Force LuckPerms to recalculate cached metadata (prefix, suffix, etc.) for all online players.
     * 
     * LuckPerms caches prefix/suffix data per-user. When a group's prefix is changed via
     * /lp group <group> meta setprefix, the cache for users in that group is not automatically
     * invalidated in all environments. Calling recalculateCaches() forces a fresh recalculation
     * so the new prefix is visible immediately without a server restart.
     */
    public static void refreshCachesForOnlinePlayers() {
        Logger logger = Logger.getLogger("EliteEssentials");
        if (!isAvailable()) return;

        com.hypixel.hytale.server.core.universe.Universe universe = com.hypixel.hytale.server.core.universe.Universe.get();
        if (universe == null) return;

        int refreshed = 0;
        for (com.hypixel.hytale.server.core.universe.PlayerRef playerRef : universe.getPlayers()) {
            try {
                Object[] lpObjects = getLuckPermsObjects(playerRef.getUuid());
                if (lpObjects == null) continue;

                Object user = lpObjects[2];

                // recalculateCaches() forces LuckPerms to rebuild prefix/suffix/meta from current data
                Method recalcMethod = user.getClass().getMethod("recalculateCaches");
                recalcMethod.invoke(user);
                refreshed++;
            } catch (Exception e) {
                logger.warning("[LuckPerms] Failed to refresh cache for " + playerRef.getUsername() + ": " + e.getMessage());
            }
        }

        logger.info("[LuckPerms] Refreshed caches for " + refreshed + " online player(s)");
    }
}
