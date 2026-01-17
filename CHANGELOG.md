# Changelog

All notable changes to EliteEssentials will be documented in this file.

## [1.0.5] - 2026-01-17

### Added

- **`/tpahere <player>` Command**: Request a player to teleport TO YOU (opposite of `/tpa`)
  - New request type system distinguishes between TPA (you go to them) and TPAHERE (they come to you)
  - Uses same permission structure as TPA
  - Permission: `eliteessentials.command.tp.tpahere` in advanced mode
  - Config messages: `tpahereRequestSent`, `tpahereRequestReceived`, `tpahereAcceptedTarget`, `tpahereAcceptedRequester`
  - Both players' `/back` locations are saved
  - Warmup applies to the person being teleported
- **Fly Speed Command**: `/flyspeed <speed>` sets fly speed multiplier (0-10, where 0 = default)
  - Validates input range (0-10)
  - Admin-only command in simple mode
  - Permission: `eliteessentials.command.misc.flyspeed` in advanced mode
  - Config messages: `flySpeedSet`, `flySpeedReset`, `flySpeedInvalid`, `flySpeedOutOfRange`

### Fixed

- **RTP ground detection**: Fixed players spawning in the sky
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
- **Teleport rotation bug**: Fixed character tilting/leaning forward after using `/home`, `/warp`, `/back` commands
  - Issue was caused by incorrect rotation vector construction
  - Changed from using `rotation.getYaw()`/`rotation.getPitch()` methods to direct field access (`rotation.y`/`rotation.x`)
  - Vector3f structure: x=pitch, y=yaw, z=roll
  - Affects: `/home`, `/sethome`, `/warp`, `/setwarp`, `/back` commands
  - Players now teleport with correct orientation matching how they were facing when the location was saved

### Changed

- **TpaRequest model**: Added `Type` enum to distinguish between TPA and TPAHERE requests
- **TpaService**: Added overloaded `createRequest()` method accepting request type parameter
- **`/tpaccept` command**: Now handles both TPA and TPAHERE request types with correct teleport direction
- Updated documentation (README.md, PERMISSIONS.md) to include `/flyspeed` and `/tpahere` commands
- Command registration log now includes `/flyspeed` and `/tpahere`

### Technical Improvements

- RTP now uses proper solid block detection instead of height map
- Enhanced RTP debug logging for troubleshooting purposes
- Improved fluid detection system for safer teleportation
- Better error handling for chunk loading failures in RTP

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
