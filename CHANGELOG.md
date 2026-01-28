# Changelog

All notable changes to EliteEssentials will be documented in this file.

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
* Common cooldown values: 30, 60, 120, 180, 300, 600, 900, 1800, 3600 seconds
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
* Fake join/leave messages broadcast when vanishing/unvanishing
* Config options: `hideFromList`, `hideFromMap`, `mimicJoinLeave`
* Messages: `vanishFakeLeave`, `vanishFakeJoin`

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
