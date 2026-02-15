# Changelog

All notable changes to EliteEssentials will be documented in this file.

## [Unreleased]

(No changes currently.)

## [1.1.9] - UPCOMING

### Added

**Trash Command** - Disposal window for unwanted items
* `/trash` - Opens an inventory window where you can drag in items to delete
* `/trash <size>` - Open trash window with custom slot count (1-45)
* Items placed in the window are permanently deleted when the window is closed
* Uses Hytale's ContainerWindow system for a native inventory feel
* Configurable default size (default: 27 slots) and max size (default: 45 slots)
* Aliases: `/dispose`, `/disposal`
* Permission: `eliteessentials.command.misc.trash` (Everyone)
* Cooldown support with permission-based overrides
* Config section: `trash.enabled`, `trash.defaultSize`, `trash.maxSize`, `trash.cooldownSeconds`
* Configurable messages: `trashOpened`, `trashFailed`

**Group Chat Default Selection** - Set your preferred default chat channel
* `/gcset <chat>` - Set your default chat for `/gc` and `/g` commands
* `/gcset` - View your current default chat
* When you have multiple chat channels, `/gc <message>` now uses your set default instead of always using the first one
* Default preference persists across server restarts (stored in player data)
* Automatically falls back to first available chat if you lose access to your default
* You can still override by specifying the chat: `/gc staff Hello everyone`
* Configurable messages: `groupChatDefaultSet`, `groupChatDefaultCurrent`, `groupChatDefaultNone`, `groupChatNotFound`, `groupChatNoAccessSpecific`

**RTP Force World** - Restrict RTP to a specific world
* New config options: `rtp.forceWorldEnabled` (default: false) and `rtp.forceWorld` (default: "")
* When enabled, `/rtp` always teleports players to the specified world regardless of their current world
* Example: Set `forceWorldEnabled: true` and `forceWorld: "main"` to force all RTP to the "main" world
* Only applies to self-RTP - admin RTP with explicit world argument still works normally
* Thread-safe cross-world teleportation with proper /back location saving
* Helpful error messages if configured world doesn't exist

**Join Date Command** - View when a player first joined the server
* `/joindate` - View your own join date
* `/joindate <player>` - View another player's join date
* Displays formatted date (e.g., "Jan 15, 2026 at 14:30")
* Separate permissions for checking self vs others (great for rank perks)
* Permissions: `eliteessentials.command.misc.joindate` (Everyone), `eliteessentials.command.misc.joindate.others` (Everyone)
* Config section: `joindate.enabled` (default: true)
* Configurable messages: `joindateSelf`, `joindateOther`, `joindateNeverJoined`

**Playtime Command** - View total play time on the server
* `/playtime` - View your own total playtime
* `/playtime <player>` - View another player's playtime
* Includes current session time for accurate live totals (not just last-saved value)
* Formatted output (e.g., "2d 3h", "45m", "12s")
* Separate permissions for checking self vs others (great for rank perks)
* Permissions: `eliteessentials.command.misc.playtime` (Everyone), `eliteessentials.command.misc.playtime.others` (Everyone)
* Config section: `playtime.enabled` (default: true)
* Configurable messages: `playtimeSelf`, `playtimeOther`, `playtimeNeverJoined`

**AFK System** - Automatic and manual AFK detection with full integration
* `/afk` command to manually toggle AFK status
* Automatic inactivity detection - players marked AFK after configurable timeout (default: 300 seconds)
* Movement detection removes AFK status automatically (same polling approach as warmup system)
* AFK players show `[AFK]` prefix in the tab player list via packet updates
* AFK players show `[AFK]` prefix in `/list` command output
* Optional chat broadcast when players go AFK or return (configurable)
* PlayTime Rewards integration - admins can choose whether AFK players count toward rewards (`excludeFromRewards` config option, default: true)
* Full config section: `afk.enabled`, `afk.inactivityTimeoutSeconds`, `afk.broadcastAfk`, `afk.showInTabList`, `afk.excludeFromRewards`
* Configurable messages: `afkOn`, `afkOff`, `afkOnSelf`, `afkOffSelf`
* Permission: `eliteessentials.command.misc.afk` (Everyone)
* Supports `/ee reload` - AFK service restarts with new config values
* Clean shutdown - all AFK state cleared on plugin disable

**Periodic Play Time Save** - Crash protection for player play time
* New `periodicPlayTimeSaveMinutes` config option (under General settings, default: `0` = disabled)
* When enabled, flushes accumulated session play time to disk at the configured interval for all online players
* Protects against play time loss during server crashes (previously, the entire session was lost if the server crashed before a clean disconnect)
* Automatically syncs with PlayTime Reward Service session tracking to prevent double-counting
* Supports `/ee reload` to change the interval without restarting
* Recommended value: `5` to `10` minutes

**Safe Teleport Utility** - New `TeleportUtil` class for chunk-safe teleportation
* New shared utility `TeleportUtil.safeTeleport()` ensures the destination chunk is loaded before moving a player
* Fast path: if the chunk is already loaded, teleports immediately with no delay
* Slow path: loads the chunk asynchronously via `world.getChunkAsync()`, then teleports on completion
* If the chunk fails to load, the player receives an error message instead of crashing the world
* Debug logging shows chunk load status when debug mode is enabled

**Ignore System** - Block messages from specific players
* `/ignore <player>` - Ignore a player (blocks their public and private messages)
* `/ignore list` - View all players you are currently ignoring
* `/unignore <player>` - Stop ignoring a specific player
* `/unignore all` - Stop ignoring all players (reset)
* Ignored players' public chat messages are hidden only for you (other players still see them)
* Private messages from ignored players are silently blocked (sender sees it as "sent" but you don't receive it)
* Ignore data stored per-player in `players/<uuid>.json` (persists across restarts)
* Permissions: `eliteessentials.command.misc.ignore` (Everyone)
* Config section: `ignore.enabled` (default: true)
* Configurable messages: `ignoreUsage`, `ignoreSelf`, `ignoreAdded`, `ignoreAlready`, `ignoreListEmpty`, `ignoreListHeader`, `unignoreUsage`, `unignoreRemoved`, `unignoreNotIgnored`, `unignoreAll`

**Group Chat Enhancements** - Chat formatting, global toggle, and admin spy mode
* New `groupChat.useChatFormatting` config option (default: `false`) - when enabled, player names in group chat use the same prefix/color formatting as regular chat (from `chatFormat` config)
* Supports LuckPerms prefixes/suffixes, group priorities, and PlaceholderAPI in group chat messages
* New `groupChat.formattedMessageFormat` config option to customize how the channel tag combines with the chat format (default: `{channel_color}{channel_prefix} {chat_format}`)
* New `/gcspy` command - admins can toggle spy mode to see messages from all group chat channels, even ones they don't belong to
* Spy messages use a distinct dimmed format (configurable via `groupChat.spyFormat`)
* New `groupChat.allowSpy` config option (default: `true`) to enable/disable the spy feature
* Permission: `eliteessentials.command.misc.groupchat.spy` (Admin)
* Thread-safe spy tracking using `ConcurrentHashMap`
* Configurable messages: `groupChatSpyEnabled`, `groupChatSpyDisabled`, `groupChatDisabled`

**Mute System** - Admin command to mute players server-wide
* `/mute <player> [reason]` - Mute a player with optional reason
* `/unmute <player>` - Unmute a player
* Muted players cannot send public chat messages or private messages
* Muted players are notified when they are muted/unmuted
* Optional reason displayed to the muted player
* Mute data stored server-wide in `mutes.json` (persists across restarts)
* Supports `/ee reload` to reload mute data
* Permissions: `eliteessentials.admin.mute` (Admin), `eliteessentials.admin.unmute` (Admin)
* Config section: `mute.enabled` (default: true)
* Configurable messages: `muteUsage`, `muteSelf`, `muteSuccess`, `muteAlready`, `mutedNotify`, `mutedNotifyReason`, `mutedBlocked`, `unmuteUsage`, `unmuteSuccess`, `unmuteNotMuted`, `unmutedNotify`

**Ban System** - Comprehensive player ban management with multiple ban types
* `/ban <player> [reason]` - Permanently ban a player with optional reason
* `/tempban <player> <time> [reason]` - Temporarily ban a player (e.g., `1d`, `2h`, `30m`, `1d12h`)
* `/ipban <player> [reason]` - Ban a player's IP address to prevent alt accounts
* `/unban <player>` - Remove a permanent or temporary ban
* `/unipban <player>` - Remove an IP ban
* Banned players are immediately kicked from the server
* Ban data persists across restarts in `bans.json`, `tempbans.json`, and `ipbans.json`
* Temporary bans automatically expire and are cleaned up on server start
* IP bans work with both TCP and QUIC connections via Netty channel extraction
* Supports offline player unbanning by name
* Permissions: `eliteessentials.admin.ban`, `eliteessentials.admin.tempban`, `eliteessentials.admin.ipban`, `eliteessentials.admin.unban`, `eliteessentials.admin.unipban` (Admin)
* Config section: `ban.enabled` (default: true)
* Configurable messages: `banUsage`, `banSelf`, `banSuccess`, `banAlready`, `banKick`, `banKickReason`, `tempbanUsage`, `tempbanSelf`, `tempbanSuccess`, `tempbanAlready`, `tempbanInvalidTime`, `tempbanKick`, `tempbanKickReason`, `ipbanUsage`, `ipbanSuccess`, `ipbanAlready`, `ipbanNoIp`, `ipbanKick`, `ipbanKickReason`, `unbanUsage`, `unbanSuccess`, `unbanNotBanned`, `unipbanUsage`, `unipbanSuccess`, `unipbanNotBanned`

**Freeze System** - Admin command to freeze players in place
* `/freeze <player>` - Toggle freeze on a player (prevents all movement)
* Frozen players cannot move, jump, or fly
* Works by setting all movement speeds to 0 via MovementSettings
* Freeze state persists across restarts in `freezes.json`
* Frozen players are notified when frozen/unfrozen
* Permission: `eliteessentials.admin.freeze` (Admin)
* Config section: `freeze.enabled` (default: true)
* Configurable messages: `freezeUsage`, `freezeSuccess`, `freezeNotify`, `unfreezeSuccess`, `unfreezeNotify`, `freezeError`
* Supports `/ee reload` to reload freeze data
* Config section: `mute.enabled` (default: true)
* Configurable messages: `muteUsage`, `muteSelf`, `muteSuccess`, `muteAlready`, `mutedNotify`, `mutedNotifyReason`, `mutedBlocked`, `unmuteUsage`, `unmuteSuccess`, `unmuteNotMuted`, `unmutedNotify`

**Labeled Link Syntax** - Clickable display text with custom URLs
* New `[display text](url)` syntax for all formatted messages
* Shows custom text (e.g., "DISCORD") that opens a URL when clicked, instead of showing the raw URL
* Works everywhere `MessageFormatter` is used: autobroadcasts, `/discord`, `/motd`, `/rules`, `/broadcast`, join messages, death messages, and more
* Color codes work both outside and inside the brackets: `&b[DISCORD](https://discord.gg/xyz)` or `[&b&lClick Here](https://example.com)`
* Inherits surrounding color/formatting state so links blend naturally into styled messages
* Plain URL auto-linking still works as before alongside the new syntax
* Updated default examples in `autobroadcast.json` and `discord.json` to showcase the new syntax

### Fixed

**World Crash on Teleport to Unloaded Chunks** - Critical server stability fix
* Fixed `ArrayIndexOutOfBoundsException` in `ArchetypeChunk.removeEntity` when teleporting players to locations where the destination chunk was not loaded
* This caused the world to shut down and players to fall into the void on reconnect
* Root cause: `/home`, `/warp`, `/spawn`, `/back`, `/top`, and `/tpaccept` all applied the `Teleport` component without first ensuring the target chunk was loaded, unlike `/rtp` which already handled this correctly
* All teleport commands now pre-load the destination chunk before moving the player
* Affected commands: `/home`, `/warp`, `/spawn`, `/back`, `/top`, `/tpaccept` (both same-world and cross-world), and all alias-based teleports (spawn, back, top via custom aliases)
* Fixed double `world.execute()` wrapping in alias-based `/back` command

**Player Desync in Multi-World Servers** - Critical ECS performance fix
* Fixed spawn protection ECS systems (PvP, block break/place, damage, interaction, pickup, drop) running on every entity in every world, even worlds with no spawn protection configured
* All 7 spawn protection systems now use `PlayerRef.getComponentType()` query instead of `Query.any()`, so the ECS only invokes them for player entities (skips mobs, NPCs, items, etc.)
* Added fast `hasSpawnInWorld()` early-exit check before any component lookups, so arena worlds and other worlds without `/setspawn` bail out immediately with zero overhead
* The `PvpProtection` system was the primary culprit - it sat in the damage filter group intercepting every damage event server-wide, causing combat desync (attacks not registering, items not pickable)
* Added the same world-level early-exit to `SpawnUseBlockInteraction` codec override
* Removed unused `PickupProtectionSystem.java` (dead code, was never registered but duplicated logic from `SpawnProtectionSystem.ItemPickupProtection`)

## [1.1.8] - 2026-02-08

### Added

**Kit Commands** - Kits can now execute server commands when claimed
* Add a `commands` list to any kit in `kits.json` to run commands on claim
* Commands execute as console with full permissions, just like PlayTime Rewards
* Supports `{player}` and `%player%` placeholders for the claiming player's name
* Works from all claim paths: `/kit <name>`, the kit GUI, and starter kits on first join
* Can run ANY registered server command, including commands from other plugins
* Example: give items, set permissions, add to groups, grant economy rewards, trigger other plugin commands

**Example kits.json with commands:**
```json
{
  "id": "vip",
  "displayName": "VIP Kit",
  "commands": [
    "eco add {player} 500",
    "lp user {player} group add vip",
    "broadcast {player} claimed the VIP kit!"
  ]
}
```

**Shared Command Executor** - Centralized command execution utility
* New `CommandExecutor` utility class used by both kits and PlayTime Rewards
* Consistent placeholder handling, thread-safe world execution, and debug logging
* PlayTimeRewardService refactored to use the shared utility (no behavior change)

### Changed

**Vanish Fake Messages** - Now uses standard join/leave messages
* Vanish fake join/leave messages now use the same `joinMessage` and `leaveMessage` as regular player joins/leaves
* Removed separate `vanishFakeJoin` and `vanishFakeLeave` config messages for consistency
* No functional change - vanish still broadcasts fake messages when `mimicJoinLeave` is enabled

### Fixed

**Vanish Performance Optimization** - Fixed lag spike when admin uses `/vanish` on 50+ player servers
* Toggling vanish with all hide settings enabled (hideFromList, hideFromMap, mimicJoinLeave, mobImmunity) previously ran 5 separate full iterations over all online players, causing a noticeable lag spike
* Merged visibility and player list updates into a single pass over all players instead of 3 separate loops
* Pre-build `RemoveFromServerPlayerList` packet once and reuse it for all players instead of creating a new packet per player
* Removed redundant `updateMapFiltersForAll()` call - the map filter lambda already captures the live `vanishedPlayers` set by reference, so it picks up changes automatically each tick without needing to be re-applied
* Replaced player-search loop in `updateMobImmunity()` with direct O(1) lookup from the stored `playerStoreRefs` map
* Net result: vanish toggle goes from ~250+ iterations with packet I/O down to ~50 (single pass) for a 50-player server

**Economy ClassNotFoundException Fix** - Graceful handling when VaultUnlocked classes are missing
* Fixed crash when using commands with costs (e.g., `/rtp`) without VaultUnlocked installed
* Added defensive error handling in `EconomyAPI` to catch `NoClassDefFoundError`
* Commands now gracefully fall back to internal economy when external economy classes are unavailable
* Added warning logs to help diagnose economy integration issues
* Affects: `/rtp`, `/home`, `/warp`, `/kit`, and any command with configured costs

**Spawn protection – F-key item pickup (flowers, pebbles, etc.)**
* Item pickup protection in spawn now correctly blocks picking up world-placed items (flowers, pebbles, mushrooms, etc.) when using the F (interact) key
* Previously the "Item pickups are disabled in spawn" message appeared but the item was still picked up and removed from the world
* Root cause: `InteractivelyPickupItemEvent.setCancelled(true)` is ignored by the Hytale API
* Fix: Registered a custom `UseBlockInteraction` that intercepts at the interaction level before the event fires; in protected spawn areas the interaction is not executed, so the pickup never occurs
* When only `disableItemPickup` is enabled, functional blocks (doors, chests, benches, etc.) remain usable in spawn; only "pickup" blocks (flowers, pebbles, mushrooms) are blocked
* New file: `SpawnUseBlockInteraction.java` in `interactions` package; registered in plugin setup via Interaction codec registry

## [1.1.7] - 2026-02-04

Special thanks to PiggyPiglet for PlaceholderAPI integration and L8-Alphine for fixes on respawn after death.

### Added

**Clear Chat Command** - Admin command to clear chat for all players
* `/clearchat` (alias: `/cc`) - Clears chat
* Displays a configurable message after clearing: "Chat has been cleared by an administrator."
* Permission: `eliteessentials.command.misc.clearchat` (Admin only)
* Config option: `clearChat.enabled` (default: true)

**PlaceholderAPI Integration** - Full support for cross-plugin placeholders
* Optional soft dependency on HelpChat's PlaceholderAPI for Hytale
* Use placeholders from other plugins in your chat format (e.g., `%vault_eco_balance%`, `%othermod_stat%`)
* Placeholders work in chat messages, join messages, and broadcasts
* Config option: `chatFormat.placeholderapi` (default: true) - toggle PAPI processing
* Declared in `manifest.json` as optional dependency - works with or without PAPI installed

**EliteEssentials PAPI Expansion** - Placeholders other plugins can use:
* Economy: `%eliteessentials_balance%`, `%eliteessentials_economy_enabled%`, `%eliteessentials_currency_name%`
* Status: `%eliteessentials_god%`, `%eliteessentials_vanished%`
* Homes: `%eliteessentials_homes_num%`, `%eliteessentials_homes_max%`, `%eliteessentials_homes_names%`
* Kits: `%eliteessentials_all_kits_num%`, `%eliteessentials_allowed_kits_names%`, `%eliteessentials_kit_<id>_cooldown%`
* Warps: `%eliteessentials_all_warps_num%`, `%eliteessentials_allowed_warps_names%`, `%eliteessentials_warp_<name>_coords%`
* Relational placeholders supported for distance-based or player-to-player data

**LuckPerms Prefix/Suffix Placeholders** - Display LuckPerms prefixes and suffixes in chat
* New placeholders for chat format: `{prefix}`, `{suffix}`, `{group}`
* Also supports PAPI-style: `%luckperms_prefix%`, `%luckperms_suffix%`, `%luckperms_primary_group%`
* Prefixes/suffixes are pulled directly from LuckPerms meta data
* Manage all your prefixes in LuckPerms, EliteEssentials handles the display
* Works independently - no PlaceholderAPI required for LuckPerms prefix/suffix

Example chat format:
```json
"groupFormats": {
  "Admin": "{prefix}{player}{suffix}&r: {message}"
}
```

Setting prefixes in LuckPerms:
```
/lp group admin meta setprefix 100 "&c[Admin] "
/lp group vip meta setsuffix 50 " &6[VIP]"
```

**Configurable Sleep Times** - Control when players can sleep and when they wake up
* New config option: `nightStartHour` (default: 19.5 = 7:30 PM) - when players can start sleeping
* New config option: `morningHour` (default: 5.5 = 5:30 AM) - when players wake up
* Supports decimal hours for minute granularity (e.g., 20.5 = 8:30 PM)
* Allows servers to let players sleep earlier or later than Hytale's default
* Note: The client-side "You may sleep starting at X" message is controlled by Hytale and cannot be changed

**Range-Based Group Chat** - Create proximity-based chat channels for RP servers
* New `range` field for group chats - only players within X blocks can see messages
* Perfect for local/say chat channels where only nearby players should hear you
* Default "local" chat added with 50 block range (requires `eliteessentials.chat.local` permission)
* `/chats` command now shows range info for range-limited channels
* Range is calculated in 3D (includes vertical distance)
* Players must be in the same world to receive range-limited messages
* `range: 0` = global chat (no distance limit)
* `range: 50` = only players within 50 blocks see the message

Configuration example in `groupchat.json`:
```json
{
  "groupName": "local",
  "displayName": "Local Chat",
  "prefix": "[LOCAL]",
  "color": "#8b949e",
  "enabled": true,
  "requiresGroup": false,
  "range": 50
}
```

**Permission-Based Warmups** - Set custom warmup times per group via LuckPerms
* Works exactly like the existing cooldown permission system
* Lowest value found wins (most favorable to player)
* Supports all teleport commands: home, spawn, warp, rtp, back, tpa, tpahere

New permission nodes:
* `eliteessentials.command.home.warmup.<seconds>` - Custom /home warmup
* `eliteessentials.command.spawn.warmup.<seconds>` - Custom /spawn warmup
* `eliteessentials.command.warp.warmup.<seconds>` - Custom /warp warmup
* `eliteessentials.command.tp.warmup.rtp.<seconds>` - Custom /rtp warmup
* `eliteessentials.command.tp.warmup.back.<seconds>` - Custom /back warmup
* `eliteessentials.command.tp.warmup.tpa.<seconds>` - Custom /tpa warmup
* `eliteessentials.command.tp.warmup.tpahere.<seconds>` - Custom /tpahere warmup

Example usage:
```
/lp group vip permission set eliteessentials.command.home.warmup.0 true    # Instant /home
/lp group default permission set eliteessentials.command.tp.warmup.rtp.10 true  # 10s RTP warmup
```

### Fixed

**Respawn System Overhaul** - Players now correctly respawn at spawn when they have no bed
* Players without a bed/respawn point now properly teleport to `/setspawn` location after death
* Respects `perWorld` setting: uses current world's spawn or `mainWorld` spawn as configured
* Bed detection via `PlayerConfigData.getRespawnPoints()` - if player has ANY respawn point (bed/custom), vanilla respawn is used
* If no respawn points exist, EliteEssentials teleports player to the configured spawn location
* Cross-world respawn works correctly (e.g., die in explore world, respawn at main world spawn)
* Delayed teleport (1 second) on death world's executor prevents conflicts with Hytale's respawn/ClientReady handshake
* Fixes "ghost player" issues where players would appear disconnected or out of sync after death
* Root cause: Immediate teleport during `DeathComponent` removal interfered with Hytale's internal respawn flow
* Solution: Delay teleport and execute on proper world thread to avoid race conditions with client state synchronization

**Spawn Protection Cross-World Bug** - Fixed spawn protection applying to wrong worlds
* Spawn protection was checking coordinates against ALL world spawns instead of just the current world
* If you set spawn at (100, 64, 100) in the default world, those same coordinates in other worlds (like skyblock_islands) would also be protected
* Root cause: Protection systems were calling `isInProtectedArea()` without passing the world name, causing it to check against all spawns
* Fix: All protection handlers now get the world name from the store and pass it to world-specific protection checks
* Spawn protection now only applies in the world where the spawn was actually set

**RTP Console Spam** - Fixed rapid `/rtp` commands causing continuous error spam in console
* Using `/rtp` twice in quick succession would cause non-stop `NullPointerException` and `IndexOutOfBoundsException` errors
* Root cause: Scheduled invulnerability removal callback captured stale entity refs that became invalid after teleportation
* Fix: Callback now looks up fresh player reference by UUID instead of using captured stale ref
* Errors no longer spam even when players rapidly use `/rtp`

**Flight Toggle** - Fixed `/fly` not properly disabling flight mode when toggled off

**Death Messages Not Using messages.json** - Death messages now honor custom messages from messages.json
* Previously, when Hytale provided a death message (e.g., "You were killed by Skeleton Fighter!"), it was used directly with simple text replacement
* Now the system extracts the killer name and uses the configured `deathByEntity` message format from messages.json
* Server owners can now fully customize death messages like `{player} was slain by {killer}` or any other format
* All death message types (entity, player, fall, fire, etc.) now consistently use messages.json

---

## [1.1.6] - 2026-02-02

### Added

**GUI Improvements - Compact Design & Better UX**
* All GUI entries (Homes, Warps, Kits, TPA) reduced from 70px to 48px height for more compact display
* Default page size reduced from 8 to 6 items per page for better visibility without scrolling (CHANGEABLE IN CONFIG)
* TPA GUI now shows pending incoming requests at the top with Accept/Deny buttons
* Clicking Accept/Deny in TPA GUI directly handles the request without needing to type commands
* Home Edit button widened from 60px to 70px for better readability
* All GUI text and button labels now configurable via messages in config.json

**New Configuration Messages**
* `gui.TpaPendingFrom` - Label for pending TPA requests ("From: {player}")
* `gui.TpaAcceptButton` - Accept button text
* `gui.TpaDenyButton` - Deny button text
* `gui.WarpDeleteConfirmButton` - Changed from "?" to "OK?" (ASCII-safe)

### Changed

**Messages File Format Improved**
* `messages.json` now saves as a flat key-value structure instead of nested objects
* All message keys are sorted alphabetically for easier navigation
* Keys like `gui.HomesTitle` now stay as literal strings instead of becoming nested `{"gui": {"HomesTitle": ...}}`
* Existing translations will continue to work - the plugin reads both formats
* For a clean, organized messages file: delete `messages.json` and restart the server (you'll need to re-apply any custom translations)

**PlayTime Rewards - Universal Command Execution**
* Reward commands now use `CommandManager` to execute ANY registered server command as console
* Works with commands from any mod/plugin (EcoTale, custom mods, etc.)
* No longer limited to few commands
* Supports both `{player}` and `%player%` placeholders
* Commands execute on world thread for thread safety

**GUI Button Sizing**
* Reduced button height from 32px to 28px across all GUIs
* Reduced button padding from 16px to 12px for more compact appearance
* TPA pending Accept button: 90px wide (matches Request button)
* TPA pending Deny button: 70px wide

### Fixed

**Login Spawn Teleport Timing** - Fixed client crashes and timeouts during player login
* Increased spawn teleport delay from 1 second to 2 seconds to allow Hytale's login sequence to complete
* Added world validation before executing delayed teleports to prevent conflicts with Hytale's world changes
* Prevents "Cannot start a fade out while a fade completion callback is pending" client crash
* Prevents 30-second timeout when adding player to world
* Fixes issue where players briefly see default spawn before being teleported
* Applies to both `teleportOnFirstJoin` and `teleportOnEveryLogin` features

**Cross-World Teleportation Stability** - Fully tested and verified all cross-world teleport commands
* Confirmed working: `/rtp`, `/tpa`, `/tpahere`, `/back`, `/spawn`, `/home`, `/warp`
* All commands properly handle teleporting between different worlds
* Thread-safe execution on correct world threads
* Proper world parameter in Teleport constructor for Hytale's cross-world handling

### Technical

**New Files**
* `TpaAcceptHelper.java` - Shared teleport logic for TPA accept from GUI
* `EliteEssentials_TpaPendingEntry.ui` - UI template for pending TPA request entries
* Added `chargeCostDirect()` method to `CommandPermissionUtil` for GUI cost handling

**UI Files Modified**
* All entry UI files made more compact (Home, Warp, Kit, TPA)
* `EliteEssentials_Shared.ui` - Reduced button sizes
* Page size defaults changed in `PluginConfig.java`

### Technical

* Spawn teleports now validate player hasn't changed worlds before executing delayed teleport
* Uses `EntityStore.getWorld()` to check current world (not the deprecated `PlayerRef.getWorld()`)
* Gracefully cancels teleport if player moved to different world during delay period

---

## [1.1.5] - 2026-01-29

Special thanks to AfkF24 for making changes to the GUI system and making it look way better.

### Added

**EssentialsPlus Migration** - Migration tool for fof1092's EssentialsPlus plugin data
* Migrates kits 
* Migrates homes 
* Migrates warps
* Run with `/eemigration essentialsplus`
* Converts kit cooldowns from milliseconds to seconds
* Imports items from all inventory sections: hotbar, storage, armor, utility
* Preserves existing data (skips duplicates)

**HomesPlus Migration** - Migration tool for HomesPlus plugin data
* Migrates homes from `mods/HomesPlus_HomesPlus/homes.json`
* Run with `/eemigration homesplus`
* Preserves existing data (skips duplicates)

**Spawn on Every Login** - Teleport players to spawn every time they log in
* New config option: `spawn.teleportOnEveryLogin` (default: false)
* When enabled, ALL players teleport to spawn when they join the server
* Works with cross-world teleport: log out in world 2, spawn in world 1
* Respects `perWorld` setting: uses `mainWorld` spawn when `perWorld: false`
* Useful for hub/lobby servers that want players to always start at spawn

**Back Command Cooldown** - Added cooldown support for `/back` command
* New config option: `back.cooldownSeconds` (default: 0)
* Supports permission-based cooldown overrides via LuckPerms

**TPA Selection GUI** - New clickable GUI for managing teleport requests
* `/tpa` (no args) opens a GUI showing all pending TPA requests
* View incoming requests from other players wanting to teleport to you
* View outgoing requests you've sent to other players
* Click to accept or deny requests directly from the GUI
* Shows request type (TPA vs TPAHERE) and requester/target name

**Improved Home GUI** - Enhanced home selection interface
* Redesigned layout with cleaner presentation
* Edit button to rename or delete homes
* Shows home coordinates and world name
* Pagination for players with many homes

**Improved Warp GUI** - Enhanced warp selection interface
* Cleaner layout matching the new home GUI style
* Shows warp descriptions when set
* Admin delete button for warp management
* Pagination support for servers with many warps

**Improved Kit GUI** - Enhanced kit selection interface
* Cleaner layout matching the new GUI style
* Shows kit status (ready, on cooldown, claimed)
* Displays cooldown time remaining
* Pagination support for servers with many kits

### Changed

**LuckPerms-Based Custom Values** - Cooldowns and limits now support ANY numeric value via LuckPerms
* No more predefined "supported values" - use any number you want
* Custom cooldowns: `eliteessentials.command.tp.cooldown.rtp.1000` (1000 seconds)
* Custom limits: `eliteessentials.command.home.limit.37` (37 homes)
* Requires LuckPerms for custom values; without LuckPerms, config defaults are used
* Applies to: all cooldowns (tp, misc commands) and limits (homes, warps)
* Lowest cooldown value wins (most favorable to player)
* Highest limit value wins (most favorable to player)

### Fixed

**Respawn Cross-World Teleport** - Fixed `mainWorld` config not working for respawns
* When `perWorld: false`, dying in another world without a bed now correctly respawns you in the `mainWorld` spawn
* Previously players would respawn at the current world's spawn instead of the configured main world
* Now uses cross-world teleport when target spawn is in a different world

**God Mode Server Crash** - Fixed crash exploit when rapidly toggling `/god` with `/gm` commands
* Rapid `/god` spam combined with `/gm c` and `/gm a` could crash the server
* Root cause: `removeComponent()` called without checking if component exists in archetype
* Creative mode manages `Invulnerable` component independently, causing state desync
* Added 500ms rate limiting to prevent rapid command spam
* Command now syncs with actual component state before toggling
* Safe removal: only removes component if it actually exists
* Exception handling prevents crash even in edge cases

**External Economy Compatibility** - Full support for Ecotale and other VaultUnlocked economy plugins
* When `useExternalEconomy: true`, EE no longer registers any economy commands (`/wallet`, `/pay`, `/baltop`, `/eco`) to avoid conflicts
* Command costs (like `/rtp` cost) now properly use the external economy balance via VaultUnlocked
* Fixed VaultUnlocked 2.x API calls - all methods now use correct signature with plugin name parameter
* Fixed `EconomyResponse` handling - now uses `transactionSuccess()` method per VaultUnlocked API
* Fixed VaultUnlocked detection - tries multiple API methods (`economyObj()`, `getEconomy()`, `economy()`)
* Added 30-second delayed retry for external economy detection (handles plugin load order issues)
* Running `/ee reload` will retry external economy detection if it failed at startup

**Duplicate Quit Messages** - Fixed quit message spamming 3-4+ times on disconnect
* `PlayerDisconnectEvent` can fire multiple times for a single disconnect (network issues)
* Added deduplication guard using `onlinePlayers.remove()` return value
* Only the first disconnect event broadcasts the quit message

**Home/Warp Limit Permissions** - Fixed permission-based limits not recognizing all values
* Previously only checked specific values (1, 2, 3, 5, 10, 15, 20, 25, 50, 100)
* Now uses LuckPerms to read any numeric value from permissions
* Permissions like `eliteessentials.command.home.limit.37` now work correctly

**Custom Cooldown Permissions** - Fixed cooldown permissions not recognizing custom values
* Previously only checked predefined values (30, 60, 120, etc.)
* Now uses LuckPerms to read any numeric value from permissions
* Permissions like `eliteessentials.command.misc.repair.cooldown.1000` now work correctly

### Changed

* Updated config comment for `useExternalEconomy` to clarify that `/eco` and `/pay` won't be registered when enabled

---

## [1.1.4] - 2026-01-27

### Added

**Mail System** - Send and receive mail from other players, even when offline
* `/mail send <player> <message>` - Send mail to any player (online or offline)
* `/mail read [number]` - Read a specific mail or first unread
* `/mail list` - List all mail with unread indicators
* `/mail clear` - Clear all mail
* `/mail clear read` - Clear only read mail
* `/mail delete <number>` - Delete specific mail
* Login notification when you have unread mail
* Online recipients get instant notification when they receive new mail
* Spam protection: Cooldown between sending mail to the same player (default: 30 seconds)
* Mailbox limit prevents abuse (default: 50 messages per player)
* Message length limit (default: 500 characters)
* Mail stored in individual player JSON files (persists across restarts)
* Permissions: `eliteessentials.command.mail.use`, `eliteessentials.command.mail.send`
* Config options: `mail.enabled`, `maxMailPerPlayer`, `maxMessageLength`, `sendCooldownSeconds`, `notifyOnLogin`, `notifyDelaySeconds`

**Permission-Based Command Costs** - Set different costs per group via LuckPerms
* Permission format: `eliteessentials.cost.<command>.<amount>`
* Supported commands: `home`, `sethome`, `spawn`, `warp`, `back`, `rtp`, `tpa`, `tpahere`
* Lowest matching cost wins (VIP with `.cost.home.5` pays less than default with `.cost.home.10`)
* Config cost remains the fallback when no permission is set
* Common cost values: 0, 1, 2, 5, 10, 15, 20, 25, 50, 100, 250, 500, 1000
* Example LuckPerms setup:
  - `/lp group vip permission set eliteessentials.cost.home.5`
  - `/lp group default permission set eliteessentials.cost.rtp.50`
  - `/lp group premium permission set eliteessentials.cost.warp.0` (free warps)

**Expanded Cooldown System** - Cooldowns for admin commands with permission-based overrides
* New cooldown config options for: `/god`, `/fly`, `/repair`, `/clearinv`, `/top`
* Each command now has `cooldownSeconds` in config (default: 0 = no cooldown)
* Permission-based cooldown bypass: `eliteessentials.command.misc.<cmd>.bypass.cooldown`
* Permission-based cooldown override: `eliteessentials.command.misc.<cmd>.cooldown.<seconds>`
* Any numeric value supported via LuckPerms (e.g., `.cooldown.1000` for 1000 seconds)
* Works in both simple mode (uses config value) and advanced mode (permission overrides)
* Example: Give VIPs shorter heal cooldown with `eliteessentials.command.misc.heal.cooldown.30`

### Changed

**Human-Readable JSON Files** - Config files now use `&` instead of `\u0026`
* All JSON files (messages.json, motd.json, rules.json, etc.) now save color codes as `&c` instead of `\u0026c`
* Makes editing config files much easier - no more unicode escapes
* Existing files with unicode escapes still work (backwards compatible)
* New saves will use the readable format

### Fixed

**Death Messages Color Codes** - Death messages now properly process color codes
* Color codes like `&b`, `&8`, `&#RRGGBB` now work in death message configs
* Previously color codes were displayed as literal text

**Spawn System Overhaul** - `/setspawn` now works correctly across all scenarios
* New players now spawn at the custom spawn location set via `/setspawn`
* Cross-world spawn teleportation works properly (e.g., `/spawn` from explore world to main world)
* Per-world spawns function correctly - each world respects its own spawn point
* Respawn after death uses the correct world's spawn location

**Vanish Command** - `/vanish` now works properly
* Fixed vanish state not being applied correctly
* Players are now properly hidden from other players when vanished

**Repair All Command** - `/repair all` now works correctly
* Fixed argument parsing - command now accepts `all` as a simple argument
* Previously required `--all=value` format which was confusing

## [1.1.3] - 2026-01-26

### Added

**Chat Color/Formatting Restrictions** - Control who can use color codes in chat
* New config options in `chatFormat` section:
  - `allowPlayerColors: false` - When false, only admins/OPs can use color codes like &c or &#FF0000
  - `allowPlayerFormatting: false` - When false, only admins/OPs can use &l (bold), &o (italic), etc.
* Players without permission will have color/format codes stripped from their messages
* In advanced permission mode, grant `eliteessentials.chat.color` or `eliteessentials.chat.format` to allow specific players/groups
* Defaults to restricted (false) - colors are admin-only out of the box

**VaultUnlocked Integration** - Cross-plugin economy support via VaultUnlocked API
* Register EliteEssentials as an economy provider for other plugins
* Optionally use external economy plugins instead of internal economy
* New config options in `economy` section:
  - `vaultUnlockedProvider: true` - Register as economy provider (default: true)
  - `useExternalEconomy: false` - Use external economy instead of internal (default: false)
* When `vaultUnlockedProvider` is enabled:
  - Other plugins using VaultUnlocked can access EliteEssentials wallets
  - Supports: getBalance, deposit, withdraw, has, transfer
* When `useExternalEconomy` is enabled:
  - `/wallet`, `/pay`, `/baltop` use the external economy
  - Command costs deduct from external economy
  - EliteEssentials becomes a consumer instead of provider
* Reflection-based integration allows building with JVM 21 while running on JVM 25+ servers
* Graceful fallback: If VaultUnlocked not installed, uses internal economy silently

### Changed

**RTP Command Fix** - Fixed `/rtp` not working for regular players
* Command sender can be either `PlayerRef` or `Player` depending on context
* Now properly handles both sender types when detecting console vs player execution
* Self-RTP (`/rtp` with no args) now works correctly for all players

### Fixed

**StarterKitEvent Thread Safety** - Fixed crash when starter kit event fires
* `IllegalStateException: Assert not in thread!` error when new players join
* Event callback now properly executes store operations on the WorldThread
* Fixes server crash even when kits are disabled in config

### Notes

* VaultUnlocked 2.18.3 for Hytale requires JVM 25+ at runtime (your server runs this)
* When using `useExternalEconomy`, set `vaultUnlockedProvider: false` to avoid conflicts
* Compatible economy plugins: TheNewEconomy, EcoTale, DynastyEconomy, and others supporting VaultUnlocked

## [1.1.2] - 2026-01-25

> <span style="color: rgb(224, 62, 45);"><strong>BACKUP WARNING</strong>: This version includes automatic data migrations that restructure how player data is stored. Before upgrading:</span>
> 
> 1. <span style="color: rgb(45, 194, 107);"><strong>Back up your entire `mods/EliteEssentials/` folder</strong></span>
> 2. <span style="color: rgb(45, 194, 107);">Migration runs automatically on first startup and moves old files to `mods/EliteEssentials/backup/migration_{timestamp}/`</span>
> 3. <span style="color: rgb(45, 194, 107);">If you need to downgrade to a previous version, you must restore files from that backup folder since 1.1.2 uses a different data structure</span>
> 4. <span style="color: rgb(45, 194, 107);">Migration has been tested and works reliably, but always have a backup just in case</span>

### Added

**EssentialsCore Migration** - Migration tool for EssentialsCore plugin data
* Migrates homes, warps, spawn, and kits from EssentialsCore format
* Run with `/eemigration essentialscore`
* One-time migration with backup of original files
* Preserves existing data (skips duplicates)

**Hyssentials Migration** - Migration from Hyssentials plugin data
* Migrates homes and warps from Hyssentials format
* Run with `/eemigration hyssentials`
* Preserves existing data (skips duplicates)

**Admin RTP Command** - `/rtp <player> [world]` - RTP other players from console or in-game
* `/rtp <player>` - RTP player in their current world
* `/rtp <player> <world>` - RTP player to a specific world (cross-world teleport)
* Can be executed from server console for automation (e.g., portal NPCs)
* Admin RTP bypasses warmup and cooldown
* Useful for portal automation: NPC executes `/rtp {player} explore` to send player to random location in explore world
* Permission: `eliteessentials.admin.rtp`

**Group Chat Command** - `/gc [group] <message>` (aliases: `/groupchat`, `/gchat`) - Private chat with your LuckPerms group
* Players can chat privately with members of their group
* Detects which LuckPerms groups a player belongs to
* Group chat channels must be configured in `groupchat.json` (groups must match your LuckPerms group names)
* If player belongs to multiple configured groups, specify which group: `/gc admin Hello team!`
* If player belongs to one configured group, just type: `/gc Hello everyone!`
* Default groups: admin, moderator, staff, vip (add/remove to match your LuckPerms setup)
* Requires LuckPerms for group detection
* Permission: `eliteessentials.command.misc.groupchat`

**Send Message Command** - `/sendmessage` (alias: `/sm`) - Send formatted messages from console or in-game
* `/sendmessage player <name> <message>` - Send to a specific player
* `/sendmessage group <group> <message>` - Send to all players in a LuckPerms group
* `/sendmessage all <message>` - Send to all online players
* Supports full chat formatting: color codes (`&0-f`), hex colors (`&#RRGGBB`), bold/italic
* Supports placeholders: `{player}`, `{server}`, `{world}`, `{playercount}`
* Can be executed from server console for automation/scripts
* Permission: `eliteessentials.admin.sendmessage`

**Vanish Command Enhancements** - True invisibility with full stealth features
* Players are now hidden from the Server Players list (tab list)
* Players are hidden from the world map
* Fake join/leave messages broadcast when vanishing/unvanishing (uses standard join/leave messages)
* Config options: `hideFromList`, `hideFromMap`, `mimicJoinLeave`

**Repair Command** - `/repair` and `/repair all` (alias: `/fix`)
* Repairs item in hand or all items in inventory
* Admin command with separate permission for "all" option
* Permissions: `eliteessentials.misc.repair`, `eliteessentials.misc.repair.all`

### Changed

**Per-Player Data Storage** - Migrated from monolithic JSON files to individual player files for better scalability
* Player data now stored in `data/players/{uuid}.json` instead of large shared files
* Each player file contains: homes, back history, kit claims, kit cooldowns, playtime claims, wallet, play time, first join, last seen
* Name-to-UUID index maintained in `data/player_index.json` for offline player lookups
* Lazy loading: player data only loaded when needed, cached while online
* Automatic save on player disconnect and periodic dirty-check saves
* **Automatic Migration**: On first startup after update, old data files are automatically migrated
  * Migrates: `homes.json`, `players.json`, `back_locations.json`, `kit_claims.json`, `playtime_claims.json`, `first_join.json`
  * Old files moved to `backup/migration_{timestamp}/` folder after successful migration
  * Migration is one-time and fully automatic - no manual steps required
* Server-wide data remains in separate files: `kits.json`, `warps.json`, `spawn.json`, `motd.json`, `rules.json`, `discord.json`, `aliases.json`, `autobroadcast.json`, `playtime_rewards.json`

### Technical Improvements

* New `PlayerFile` model consolidates all per-player data into a single class
* New `PlayerFileStorage` handles file I/O with caching and thread-safe operations
* New `DataMigrationService` handles one-time migration from old format
* Updated services to use new storage: `HomeService`, `BackService`, `KitService`, `PlayerService`, `PlayTimeRewardService`
* Better memory efficiency for servers with many players (only online players cached)
* Improved data integrity with immediate saves after changes

## [1.1.1] - 2026-01-25

### Added

- **PlayTime Rewards System**: Reward players based on their total playtime
  - Repeatable rewards: Trigger every X minutes of playtime (e.g., every hour)
  - Milestone rewards: One-time rewards at specific playtime thresholds (e.g., 100 hours)
  - Custom messages sent to players when rewards are claimed
  - Rewards defined in `playtime_rewards.json` with examples
  - Claims tracked in `playtime_claims.json` to prevent duplicate claims
  - Config options: `playTimeRewards.enabled`, `checkIntervalSeconds`, `onlyCountNewPlaytime`
  - Rewards checked on player join and periodically while online

- **LuckPerms API Integration for PlayTime Rewards**: LuckPerms commands execute via the LuckPerms API
  - Supported commands:
    - `lp user {player} group set <group>` - Set player's primary group
    - `lp user {player} group add <group>` - Add player to a group
    - `lp user {player} group remove <group>` - Remove player from a group
    - `lp user {player} permission set <permission> [true/false]` - Set a permission
    - `lp user {player} permission unset <permission>` - Remove a permission
    - `lp user {player} promote <track>` - Promote player on a track
    - `lp user {player} demote <track>` - Demote player on a track
  - Works automatically when LuckPerms is installed
  - Gracefully skips LP commands if LuckPerms is not installed (mod still works)

- **Economy Commands for PlayTime Rewards**: Economy commands execute internally
  - `eco add {player} <amount>` - Add currency to player
  - `eco remove {player} <amount>` - Remove currency from player
  - `eco set {player} <amount>` - Set player's balance

- **onlyCountNewPlaytime Option**: Prevents flood of catch-up rewards on existing servers
  - `playTimeRewards.onlyCountNewPlaytime: true` (default) - Only counts playtime after system was enabled
  - `playTimeRewards.enabledTimestamp` - Auto-populated when system first starts
  - Players who joined before the system was enabled start fresh

### Changed

- **Conditional Command Registration**: Disabled features no longer register their commands
  - When a feature is disabled in config.json (e.g., `economy.enabled: false`), its commands won't be registered
  - Frees up command names for other plugins (e.g., another economy plugin can use `/eco`, `/pay`)
  - Affected features: Economy (`/wallet`, `/pay`, `/baltop`, `/eco`), Homes, Warps, Spawn, Back, RTP, TPA, Kits, God, Heal, Fly, FlySpeed, ClearInv, Broadcast, MOTD, Rules, Discord, List, Seen, Sleep Percent

- **GUI Labels Now Configurable**: Kit and warp GUI status labels moved to messages.json
  - `guiKitStatusLocked` - Shown when player lacks permission for a kit
  - `guiKitStatusClaimed` - Shown for already claimed one-time kits
  - `guiKitStatusReady` - Shown when kit is available to claim
  - `guiWarpStatusOpOnly` - Shown to admins for OP-only warps
  - `playTimeRewardReceived` - Default message when receiving a playtime reward
  - `playTimeMilestoneBroadcast` - Broadcast message for milestone achievements
  - Note: GUI titles (blue bar) are defined in .ui files and cannot be changed via messages.json

- **PlayTime Rewards - Single Grant Per Cycle**: Repeatable rewards grant ONE reward per check cycle
  - Prevents spam of multiple rewards when a player has accumulated playtime
  - Players catch up gradually over multiple cycles

### Fixed

- **Cross-world TPA crash**: Fixed `IllegalStateException: Assert not in thread!` when using `/tpa` or `/tpahere` between players in different worlds
  - The command now properly handles cross-world teleportation by gathering player data on each world's respective thread
  - Same-world TPA continues to work as before with simpler logic

- **Cross-world /tphere crash**: Fixed `/tphere` command crashing when teleporting players between different worlds
  - Now properly handles cross-world admin teleports

- **Late Session Tracking**: Fixed playtime rewards not triggering for players who joined before the service started
  - Players who join before service starts (or after `/ee reload`) now get proper session tracking
  - Ensures all online players are checked for rewards

## [1.1.0] - 2026-01-23

### Added

- **Clickable GUI for Homes**: `/homes` now opens a clickable GUI to select and teleport to homes
  - Respects warmup, cooldown, and cost settings
  - Shows home names and coordinates
  - Click to teleport with full permission checks

- **Clickable GUI for Warps**: `/warp` (no args) now opens a clickable GUI to browse warps
  - Shows warp names and descriptions
  - Respects warmup, cooldown, and cost settings
  - Permission-based filtering (only shows warps you can access)

- **Warp Descriptions**: Warps now support optional descriptions
  - Set via `/warpsetdesc <name> <description...>`
  - Displayed in warp GUI and `/warp list`

- **`/eehelp` Command**: Shows all EliteEssentials commands you have permission to use
  - Dynamically filters based on your permissions
  - Hides admin commands from non-admins
  - Alias: `/ehelp`

- **Separate Warp List/Use Permissions**: Fine-grained warp access control
  - `eliteessentials.command.warp.list` - Can open GUI and see warp list
  - `eliteessentials.command.warp.use` - Can teleport to ALL public warps
  - `eliteessentials.command.warp.<name>` - Can teleport to specific warp only

- **Silent Alias Mode**: Suppress teleport messages when using command aliases
  - Add `"silent": true` to any alias in `aliases.json` to hide teleport confirmation messages
  - Warmup countdown messages (3... 2... 1...) still display so players know something is happening
  - Only the initial "Teleporting to warp 'X' in Y seconds..." and final "Teleported to warp 'X'" messages are hidden
  - Useful when combined with per-world MOTDs that provide context instead
  - Example: `{ "command": "warp explore", "permission": "everyone", "silent": true }`

- **Per-World MOTD System**: Configure unique MOTDs for each world
  - Global MOTD now only shows on initial server join (not when changing worlds)
  - Per-world MOTDs in `motd.json` under `worldMotds` section
  - `enabled` - Enable/disable the world's MOTD
  - `showAlways` - When true, shows every time player enters the world; when false, shows once per session
  - Same placeholders as global MOTD: `{player}`, `{server}`, `{world}`, `{playercount}`
  - Example config in motd.json for "explore" world

- **Command Cost System**: Charge players for using commands (requires economy enabled)
  - Configurable cost per command (default 0.0 = free)
  - Supported commands: `/home`, `/sethome`, `/spawn`, `/warp`, `/back`, `/rtp`, `/tpa`, `/tpahere`
  - Admins automatically bypass all costs
  - Bypass permissions for VIP players
  - Shows cost deducted message or insufficient funds error
  - New config options:
    - `homes.cost` - Cost to teleport home
    - `homes.setHomeCost` - Cost to set a home
    - `spawn.cost` - Cost to teleport to spawn
    - `warps.cost` - Cost to use a warp
    - `back.cost` - Cost to return to previous location
    - `rtp.cost` - Cost for random teleport
    - `tpa.cost` - Cost to send a teleport request
    - `tpa.tpahereCost` - Cost to request someone teleport to you
  - New permissions:
    - `eliteessentials.bypass.cost` - Bypass all command costs
    - `eliteessentials.bypass.cost.<command>` - Bypass cost for specific command
  - New messages: `costCharged`, `costInsufficientFunds`, `costFailed`

- **Player Cache System**: Comprehensive player data tracking stored in `players.json`
  - Tracks: UUID, name, firstJoin, lastSeen, wallet, playTime (in seconds), lastKnownIp
  - Automatic play time tracking (updates on player quit)
  - Name updates when players rejoin with different names
  - Used by economy system and available for future features
  - New classes: `PlayerData`, `PlayerStorage`, `PlayerService`

- **Economy System** (disabled by default): Full-featured economy with API for other mods
  - `/wallet` - View your balance
  - `/wallet <player>` - View another player's balance (requires permission)
  - `/wallet set <player> <amount>` - Set a player's balance (admin)
  - `/wallet add <player> <amount>` - Add to a player's balance (admin)
  - `/wallet remove <player> <amount>` - Remove from a player's balance (admin)
  - `/pay <player> <amount>` - Send money to another player
  - `/baltop` - View richest players leaderboard
  - Config options: `economy.enabled`, `currencyName`, `currencyNamePlural`, `currencySymbol`, `startingBalance`, `minPayment`, `baltopLimit`
  - Permissions: `WALLET`, `WALLET_OTHERS`, `WALLET_ADMIN`, `PAY`, `BALTOP`

- **Economy API**: Public API for other mods to integrate with EliteEssentials economy
  - `EconomyAPI.getBalance(UUID)` - Get player balance
  - `EconomyAPI.has(UUID, double)` - Check if player has enough funds
  - `EconomyAPI.withdraw(UUID, double)` - Remove money from player
  - `EconomyAPI.deposit(UUID, double)` - Add money to player
  - `EconomyAPI.transfer(UUID, UUID, double)` - Transfer between players
  - `EconomyAPI.setBalance(UUID, double)` - Set exact balance
  - `EconomyAPI.format(double)` - Format amount with currency symbol
  - Located at `com.eliteessentials.api.EconomyAPI`

- **Quit/Leave Messages**: Configurable player quit/leave messages
  - Config option: `joinMsg.quitEnabled` (true by default)
  - Message key: `quitMessage` with `{player}` placeholder
  - Default: `&e{player} &7left the server.`
  - Suppresses default Hytale leave messages (same as join messages)

- **Console Economy Command**: `/eco` command for server console
  - `/eco check <player>` - Check a player's balance
  - `/eco set <player> <amount>` - Set a player's balance
  - `/eco add <player> <amount>` - Add to a player's balance
  - `/eco remove <player> <amount>` - Remove from a player's balance
  - Can be run from console or by admins in-game
  - Alias: `/economy`

### Changed

- **Warp Command Consolidation**: Cleaner command structure
  - `/warp` - Opens GUI
  - `/warp <name>` - Teleport to warp
  - `/warp list` - Text list of warps
  - `/warpadmin create <name>` - Create warp at current location
  - `/warpadmin delete <name>` - Delete a warp
  - `/warpadmin info <name>` - Show warp details
  - `/warpsetperm <name> <all|op>` - Set warp permission level
  - `/warpsetdesc <name> <description>` - Set warp description
  - Removed old `/setwarp`, `/delwarp`, `/warps` commands

- **Admin Permission Check**: Now recognizes both `eliteessentials.admin.*` and `eliteessentials.admin` (without wildcard)

- `PlayerService` now accepts `ConfigManager` for starting balance configuration

- Join/quit listener updated to track play time and update player cache

### Fixed

- **`/eehelp` showing no commands for admins**: Admins now see all commands regardless of individual permission nodes

- **Spawn protection not working for per-world spawns**: Spawn protection now works for ALL worlds with `/setspawn`
  - Previously only protected the first/main world spawn
  - Now each world with a spawn set via `/setspawn` has its own protected area
  - `/setspawn` immediately updates spawn protection for that world
  - `/ee reload` refreshes spawn protection from all stored spawns

- **Silent aliases not showing permission errors**: When using a silent alias (e.g., `/explore` -> `/warp explore`), permission errors now always display
  - Previously, if a player didn't have permission, nothing happened and no message was shown
  - Now error messages (no permission, warp not found, insufficient funds, etc.) always show
  - Only success/warmup messages are suppressed in silent mode
  - Also added missing permission checks to alias commands: heal, god, fly, spawn

- **MOTD showing on world changes**: Global MOTD now only displays on initial server join, not when players teleport between worlds or enter instances

- **Warp permissions in advanced mode**: Players with only specific warp permissions (e.g., `warp.spawn`) can now use those warps without needing `warp.use`
  - `warp.use` grants access to ALL public warps
  - `warp.<name>` grants access to only that specific warp
  - Both work independently in advanced permissions mode

### Known Issues

- **World leave messages cannot be suppressed**: The default Hytale "has left [world]" message (e.g., "EliteScouter has left explore") cannot be suppressed by plugins. The Hytale Server API provides `AddPlayerToWorldEvent.setBroadcastJoinMessage(false)` for join messages, but `DrainPlayerFromWorldEvent` has no equivalent method for leave messages. This will be fixed when Hypixel adds the ability to suppress world leave broadcasts.

## [1.0.9] - 2026-01-20

### Added

- **Command Alias System**: Create custom shortcut commands that execute existing commands
  - `/alias create <name> <command> [permission]` - Create an alias
  - `/alias delete <name>` - Delete an alias
  - `/alias list` - List all aliases
  - `/alias info <name>` - Show alias details
  - **Command chains**: Use `;` to execute multiple commands in sequence
    - Example: `/alias create prep warp spawn; heal; fly` - teleports to spawn, heals, and enables fly
  - **Compatible commands for aliases**: `warp`, `spawn`
  - Permission levels: `everyone`, `op`, or custom permission nodes
  - Example: `/alias create explore warp explore` makes `/explore` execute `/warp explore`
  - Aliases stored in `aliases.json`
  - Admin-only command (simple mode) or `eliteessentials.admin.alias` (advanced mode)

- **Spawn perWorld config**: Control whether `/spawn` uses per-world or global spawn
  - `spawn.perWorld: false` (default) - Always teleport to main world spawn
  - `spawn.perWorld: true` - Teleport to current world's spawn
  - `spawn.mainWorld: "default"` - Which world is the main world

- **Config reload validation**: `/ee reload` now validates all JSON files before reloading
  - Checks: config.json, messages.json, motd.json, rules.json, discord.json, warps.json, spawn.json, kits.json, kit_claims.json
  - Shows which file has the error with line number and column
  - Displays the problematic line content
  - Provides hints for common JSON mistakes (missing commas, quotes, etc.)
  - Prevents reload if any file has invalid JSON syntax

- **Hex color code support**: MessageFormatter now supports `&#RRGGBB` format for precise colors
  - Enables per-character gradients in chat formats (e.g., `&#FF0000A&#FF3300d&#FF6600m&#FF9900i&#FFCC00n`)
  - Works alongside existing `&0-f` color codes
  - Both formats can be mixed in the same message

- **Per-world spawn system**: Each world can now have its own spawn point
  - `/setspawn` sets spawn for the current world
  - `/spawn` teleports to spawn in player's current world
  - Respawn after death uses per-world spawn (if no bed set in that world)
  - Automatic migration from old single-spawn format
  - spawn.json now stores spawns as `{ "worldName": { ... }, ... }`

- **Messages moved to separate file**: Messages are now stored in `messages.json`
  - Cleaner config.json without 100+ message entries
  - Easier to edit and share message customizations
  - Automatic one-time migration from config.json on upgrade
  - Old messages in config.json are moved to messages.json and removed from config
  - New message keys are automatically added on reload

### Fixed

- **Chat formatting case sensitivity**: LuckPerms group names are now matched case-insensitively
  - Groups like `admin` now match config keys like `Admin`
  - Previously, mismatched case would fall back to highest priority group (e.g., showing "Owner" instead of "Admin")

- **MOTD {world} placeholder**: Now correctly shows the player's actual world name instead of "default"
  - Fixed by passing world name from join event through to MOTD display

- **Cross-world /spawn teleport crash**: Fixed `IllegalStateException: Assert not in thread!` error
  - Previously crashed when using /spawn from a different world than where spawn was set
  - Now teleports to spawn in player's CURRENT world (per-world spawns)

## [1.0.8] - 2026-01-19

### Added

- **Discord Command**: `/discord` displays server discord info with clickable invite link
  - Stored in `discord.json` for easy customization
  - URLs automatically become clickable
  - Default template includes example discord link
- **Warp Limits**: Configurable limits on total warps
  - Global `maxWarps` setting (-1 for unlimited)
  - Per-group limits via `groupLimits` config (advanced permissions mode)
  - Permission-based limits: `eliteessentials.command.warp.limit.<number>`
- **Spawn Protection - Disable All Damage**: New `disableAllDamage` option
  - Blocks ALL damage in spawn area (mobs, NPCs, fall damage, fire, etc.)
  - Separate from PvP protection - can enable both or either
- **Color Code Support in Messages**: Added `MessageFormatter.formatWithFallback()` utility method
  - Config messages now support color codes using `&` prefix
  - Updated ALL 40+ command files, services, and GUI to support color codes
  - Users can customize message colors in config.json
  - No breaking changes for existing configs
- **Auto Broadcast System**: Automatic server announcements at configurable intervals
  - Multiple broadcast groups with independent intervals
  - Configurable prefix with color/formatting support (e.g., `&6&l[TIP]`)
  - Sequential or random message selection per group
  - `requirePlayers` option - skip broadcasts when server is empty
  - Full color code support (`&a`, `&c`, `&l`, etc.)
  - Stored in `autobroadcast.json` for easy editing
  - Enable/disable individual broadcasts or entire system
  - Reloads with `/ee reload`

### Fixed

- **Spawn protection bypass for damage**: OPs no longer bypass damage protection
  - Block protection bypass still works (admins can build at spawn)
  - Damage protection protects everyone including admins
- **Spawn protection not working after config change**: System now always registers at startup
- **Thread safety in join listener**: Fixed potential race condition
- **Custom "player not found" message**: TPA commands now use configurable message

### Changed

- Spawn protection system always registers (checks enabled state internally)
- **Config merge system**: No longer overwrites user's custom group formats in chat formatting
- Updated wiki documentation for new features
- General code optimization and cleanup
- Removed test data and debug artifacts

## [1.0.7] - 2026-01-18

### Added

- **LuckPerms Setup Guide**: Comprehensive wiki page with ready-to-use commands for setting up permissions
  - Quick setup commands for default and admin groups
  - Example configurations for VIP, Moderator, and Builder groups
  - Complete permission reference organized by category
  - Added to wiki sidebar under Reference section
- **Admin Teleport Command**: `/tphere <player>` instantly teleports a player to your location
  - Admin-only command with no warmup or cooldown
  - Saves target player's location for `/back`
  - Permission: `eliteessentials.command.tp.tphere` (Admin only)
- **List Command**: `/list` (aliases: `/online`) shows all online players
  - Displays player count and sorted list of player names
  - Permission: `eliteessentials.command.misc.list` (Everyone)

### Fixed

- **Broadcast command**: Now properly accepts all text after the command including spaces
  - Changed from single-word argument to greedy string parsing
  - Supports color codes using `&` prefix (e.g., `&c` for red, `&a` for green)
  - Works with `/bc` alias
  - Example: `/broadcast &l&6Server restart in 5 minutes!`
- **Rules command spacing**: Fixed excessive spacing between rule lines
  - Empty lines in rules.json are now skipped to prevent double spacing
  - Same fix as applied to MOTD command
- **Chat formatting with LuckPerms**: Fixed chat formatting not working when LuckPerms is enabled
  - Changed approach to cancel chat event and manually broadcast formatted messages
  - Fixed LuckPerms group retrieval by parsing user nodes (group.groupname format)
  - **Important:** EliteEssentials chat formatting now overrides LuckPerms chat formatting. If you want to use LuckPerms prefixes/suffixes instead, disable chat formatting in config by setting `chatFormat.enabled: false`
- **MOTD spacing**: Fixed excessive spacing between MOTD lines
  - Empty strings in config no longer create double spacing
  - Applied to both join message display and `/motd` command
- **Join message color codes**: Fixed literal color codes displaying in join messages
  - Now uses MessageFormatter to properly process all `&` color codes
  - Example: `&l&f[&r&l&2+&r&l&f]` now displays with proper formatting
- **Instance world protection**: Players can no longer set homes or warps in temporary instance worlds
  - Blocks `/sethome` and `/setwarp` in worlds with "instance-" prefix
  - Prevents homes/warps in temporary worlds that close after dungeons/portals
  - Added error messages: "cannotSetHomeInInstance" and "cannotSetWarpInInstance"
- **Respawn at custom spawn**: Players now respawn at `/setspawn` location after death
  - Uses Hytale's native spawn provider system
  - `/setspawn` now sets the world's spawn provider directly

## [1.0.6] - 2026-01-18

### Added

- **Group-Based Chat Formatting**: Customize chat messages based on player groups
  - Works with both LuckPerms groups and simple permission system
  - Priority-based group selection (highest priority wins when player has multiple groups)
  - Color code support (`&a`, `&c`, `&l`, `&o`, `&r`)
  - Placeholders: `{player}`, `{displayname}`, `{message}`
  - Fully configurable in `config.json` under `chatFormat` section
  - Default formats for Owner, Admin, Moderator, OP, VIP, Player, and Default groups
  - Easy to add custom groups - just add to `groupFormats` and `groupPriorities`
  - Can be enabled/disabled via config
- **MOTD System**: Professional Message of the Day with rich formatting
  - `/motd` command displays customizable welcome message
  - Stored in separate `motd.json` file for easy editing
  - Color code support (`&a`, `&b`, `&c`, `&l`, `&o`, `&r`)
  - Clickable URL detection and formatting
  - Placeholders: `{player}`, `{server}`, `{world}`, `{playercount}`
  - Auto-display on player join (configurable delay)
  - Reloaded by `/ee reload` command
  - Permission: `eliteessentials.command.misc.motd` (Everyone)
- **Rules Command**: `/rules` displays server rules in chat
  - Stored in separate `rules.json` file for easy editing
  - Color code support for attractive formatting
  - Default rules: Be Respectful, No Cheating/Hacking, No Griefing, Have fun!
  - Reloaded by `/ee reload` command
  - Permission: `eliteessentials.command.misc.rules` (Everyone)
- **MessageFormatter Utility**: Centralized color code and URL formatting
  - Converts Minecraft-style color codes (`&a`, `&c`, etc.) to Hytale colors
  - Detects and makes URLs clickable
  - Used by MOTD, Rules, and Chat Formatting systems
- **Broadcast Command**: `/broadcast <message>` (alias: `/bc`) sends server-wide announcements
  - Customizable broadcast format with `[BROADCAST]` prefix
  - Permission: `eliteessentials.command.misc.broadcast` (Admin only)
- **Clear Inventory Command**: `/clearinv` (aliases: `/clearinventory`, `/ci`) clears all player items
  - Clears hotbar, storage, armor, utility, and tool slots
  - Shows count of items cleared
  - Permission: `eliteessentials.command.misc.clearinv` (Admin only)
- **Join Messages**: Automatic messages when players join the server
  - Regular join messages: `{player} joined the server.`
  - First join messages: Special broadcast for new players
  - All messages customizable with `{player}` placeholder
  - First join tracking stored in `first_join.json`
  - Config options: `joinMsg.joinEnabled`, `joinMsg.firstJoinEnabled`
  - Suppress default Hytale join messages (config: `joinMsg.suppressDefaultMessages`)
- **LuckPerms Integration Enhancements**:
  - Added `isAvailable()` method to check if LuckPerms is loaded
  - Added `getPrimaryGroup(UUID)` to get player's primary group
  - Added `getGroups(UUID)` to get all groups including inherited groups
  - Used by chat formatting system for group detection

### Changed

- Command registration log now includes `/motd`, `/rules`, `/broadcast`, `/clearinv`
- Added `bin/` to `.gitignore` to exclude IDE build artifacts

### Technical Improvements

- Join listener uses `PlayerReadyEvent` for reliable player join detection
- Default join message suppression via `AddPlayerToWorldEvent.setBroadcastJoinMessage(false)`
- MOTD and Rules reload support in `/ee reload` command
- Thread-safe file I/O for MOTD and Rules storage
- Chat formatting uses `PlayerChatEvent.Formatter` interface for proper message formatting
- All new commands follow EliteEssentials coding standards and patterns

## [1.0.5] - 2026-01-17

### Added

- **`/tpahere <player>` Command**: Request a player to teleport TO YOU (opposite of `/tpa`)
  - New request type system distinguishes between TPA (you go to them) and TPAHERE (they come to you)
  - Uses same permission structure as TPA
  - Permission: `eliteessentials.command.tp.tpahere` in advanced mode
  - Config messages: `tpahereRequestSent`, `tpahereRequestReceived`
  - Both players' `/back` locations are saved
  - Warmup applies to the person being teleported
- **Fly Speed Command**: `/flyspeed <speed>` or `/flyspeed reset` sets fly speed multiplier (10-100, default is 10)
  - Range: 10 = default, 50 = fast, 100 = maximum speed
  - `/flyspeed reset` restores default speed (10)
  - Admin-only command in simple mode
  - Permission: `eliteessentials.command.misc.flyspeed` in advanced mode
  - Config messages: `flySpeedSet`, `flySpeedReset`, `flySpeedInvalid`, `flySpeedOutOfRange`
  - **WARNING**: Minimum speed is 10 to prevent server issues

### Fixed

- **Teleport rotation bug**: Fixed character tilting/leaning after using ANY teleport command
  - Issue: Players would land at an angle instead of standing upright
  - Solution: All teleports now use pitch=0 (upright) while preserving yaw (horizontal direction)
  - Affects: `/spawn`, `/home`, `/warp`, `/back`, `/tpa`, `/tpahere`, `/rtp`, `/top`
  - Players now always land standing straight, facing the correct direction
- **RTP ground detection**: Fixed players spawning in the sky or underground
  - Now scans from top to bottom for solid blocks (like `/top` command)
  - No longer uses unreliable height map
  - Respects `minSurfaceY` config setting
  - Changed teleport height from `groundY + 2` to `groundY + 1` for more natural landing
- **RTP water/lava detection**: Enhanced safety checks to avoid spawning in or near fluids
  - Checks vertical range (2 blocks below to 3 blocks above teleport point)
  - Checks horizontally adjacent blocks
  - Uses `getFluidId()` method for accurate fluid detection (FluidId 6 = lava, 7 = water)
- **RTP range calculation**: `/rtp` now calculates min/max range from world center (1, 1) instead of player's current position
  - More predictable and consistent behavior
  - Better distribution of random locations
- **`/setspawn` permission check**: Command was using wrong permission method, allowing any player to use it
  - Now properly requires admin permission (simple mode) or `eliteessentials.command.spawn.set` (advanced mode)
- **Starter kit double-claim bug**: Players who received a starter kit on join could claim it again via `/kit`
  - Starter kits are now always marked as claimed when given on join
  - Prevents duplicate claiming through the kit GUI

### Changed

- **TpaRequest model**: Added `Type` enum to distinguish between TPA and TPAHERE requests
- **TpaService**: Added overloaded `createRequest()` method accepting request type parameter
- **`/tpaccept` command**: Now handles both TPA and TPAHERE request types with correct teleport direction
- **All teleport commands**: Now use pitch=0 for upright landing instead of preserving pitch angle
- Updated documentation (README.md, PERMISSIONS.md, CURSEFORGE.MD) to include `/flyspeed` and `/tpahere` commands
- Command registration log now includes `/flyspeed` and `/tpahere`

### Technical Improvements

- RTP now uses proper solid block detection instead of height map
- Enhanced RTP debug logging for troubleshooting purposes
- Improved fluid detection system for safer teleportation
- Better error handling for chunk loading failures in RTP
- Rotation handling now uses direct field access (`rotation.y` for yaw, `rotation.x` for pitch) instead of getter methods
- All Teleport components use `putComponent()` instead of `addComponent()` for creative mode compatibility

## [1.0.4] - 2026-01-16

### Added

- **Kit System with GUI**: `/kit` command opens a stylish kit selection interface
  - Configurable kits with items, cooldowns, and permissions
  - **One-time kits**: Kits that can only be claimed once per player
  - **Cooldown kits**: Configurable cooldown between kit claims
  - **On-join kit (Name it "Starter")**: Automatically give a kit to new players on first join
  - Per-kit permissions: `eliteessentials.command.kit.<kitname>`
  - Cooldown bypass: `eliteessentials.command.kit.bypass.cooldown`
  - Kits stored in `kits.json` with full customization
- **God Mode**: `/god` toggles invincibility - become invulnerable to all damage
- **Heal Command**: `/heal` fully restores player health
- **Fly Command**: `/fly` toggles creative flight mode without needing creative mode
- **Top Command**: `/top` teleports to the highest block at your current X/Z position
- **Private Messaging**: `/msg <player> <message>` and `/reply` for private conversations
  - Tracks last conversation partner for quick replies
  - Aliases: /m, /message, /whisper, /pm, /tell
- **Spawn Protection**: Configurable area protection around spawn
  - Block break/place protection
  - PvP protection in spawn area
  - Configurable radius and Y-range
  - Bypass permission: `eliteessentials.command.spawn.protection.bypass`

### Changed

- Updated permission structure with new categories for kits and misc commands
- All new commands support both simple and advanced permission modes
- **Teleport commands now use `putComponent` instead of `addComponent`** for better compatibility with creative mode players

### Fixed

- **Creative mode compatibility**: Fixed crash when using teleport commands (RTP, spawn, home, warp, back) while in creative mode. Creative mode players already have the Invulnerable component, causing `addComponent` to fail.
- **Kit command crash in creative mode**: Added error handling for component access issues when opening kit GUI
- **God mode toggle**: Now uses `putComponent` to properly handle players who already have invulnerability

## [1.0.3] - 2026-01-15

### Added
- **LuckPerms Integration**: All 52 EliteEssentials permissions are now automatically registered with LuckPerms for autocomplete and discovery in the web editor and commands
- Permissions appear in LuckPerms dropdown immediately on server start (no need to use commands first)

### Fixed
- **`/sleeppercent` permission check**: Command was missing permission validation, allowing any player to use it. Now properly requires admin permission (simple mode) or `eliteessentials.command.misc.sleeppercent` (advanced mode)

## [1.0.2] - 2026-01-15

### Added
- **Permission System Overhaul**: Two-mode permission system
  - Simple mode (default): Commands are either Everyone or Admin only
  - Advanced mode: Full granular permission nodes (`eliteessentials.command.<category>.<action>`)
- **`/eliteessentials reload`** command to reload configuration without server restart
- **`eliteessentials.command.tp.back.ondeath`** permission for controlling death location saving in advanced mode
- Thread-safe file I/O with synchronized locks to prevent data corruption from concurrent saves

### Changed
- Permission structure now follows Hytale best practices: `namespace.category.action`
- Home limits moved under `eliteessentials.command.home.limit.<n>`
- Bypass permissions organized under each category (e.g., `command.home.bypass.cooldown`)
- Warp access permissions: `eliteessentials.command.warp.<warpname>`
- Homes now save immediately to disk after `/sethome` (previously only saved on shutdown)

### Fixed
- Death locations now respect `back.ondeath` permission in advanced mode
- Concurrent file writes no longer risk data corruption (homes, warps, back locations)
- Removed `requirePermission()` from command constructors to allow custom permission logic

## [1.0.1] - 2026-01-10

### Added
- Initial release with core features
- Home system (`/home`, `/sethome`, `/delhome`, `/homes`)
- Warp system (`/warp`, `/warps`, `/setwarp`, `/delwarp`, `/warpadmin`)
- Teleport requests (`/tpa`, `/tpaccept`, `/tpdeny`)
- Random teleport (`/rtp`) with safe landing and invulnerability
- Back command (`/back`) with death location support
- Spawn teleport (`/spawn`)
- Sleep percentage (`/sleeppercent`)
- Full message localization (60+ configurable messages)
- Warmup and cooldown support for all teleport commands
