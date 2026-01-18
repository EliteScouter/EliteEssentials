# Changelog

All notable changes to EliteEssentials will be documented in this file.

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
- **MOTD System Overhaul**: Professional Message of the Day with rich formatting
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
