# Changelog

All notable changes to EliteEssentials will be documented in this file.

## [1.0.3] - 2026-01-15

### Added
- **LuckPerms Integration**: All 52 EliteEssentials permissions are now automatically registered with LuckPerms for autocomplete and discovery in the web editor and commands
- Permissions appear in LuckPerms dropdown immediately on server start (no need to use commands first)

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
