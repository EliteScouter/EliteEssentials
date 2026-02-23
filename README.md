# EliteEssentials

A comprehensive server essentials plugin for Hytale that brings everything you need to run a professional multiplayer server. From advanced teleportation systems and home management to group-based chat formatting and customizable kits - EliteEssentials has it all.

**Fully modular design** - Enable only the features you want. **LuckPerms compatible** - Seamless integration with advanced permission systems. **Actively developed** - Regular updates with new features and improvements.

## Features

### Full Localization Support

All 60+ player-facing messages are configurable in `messages.json`. Translate your server to any language!

- Placeholder support: `{player}`, `{seconds}`, `{name}`, `{count}`, `{max}`, `{location}`
- Config auto-migrates - existing settings preserved when updating

### Home System
- **`/sethome [name]`** - Save named home locations (default: "home")
- **`/home [name]`** - Teleport to your saved homes
- **`/delhome <name>`** - Delete a home
- **`/homes`** - List all your homes
- Configurable max homes per player

### Server Warps
- **`/warp [name]`** - Teleport to a server warp (lists warps if no name)
- **`/warps`** - List all available warps with coordinates
- **`/setwarp <name> [all|op]`** - Create a warp (Admin)
- **`/delwarp <name>`** - Delete a warp (Admin)
- **`/warpadmin`** - Admin panel for managing warps
- Permission levels: `all` (everyone) or `op` (admins only)
- Persisted to `warps.json`

### Back Command
- **`/back`** - Return to your previous location
- Remembers multiple locations (configurable history)
- Works on death - return to where you died (configurable)

### Random Teleport
- **`/rtp`** - Teleport to a random location in the world
- Safe landing detection (avoids spawning underground)
- Configurable min/max range, cooldown, and warmup
- **Force World Mode** - Optionally restrict RTP to a specific world
  - `rtp.forceWorldEnabled: false` (default) - RTP within current world
  - `rtp.forceWorldEnabled: true` - Always RTP to specified world
  - `rtp.forceWorld` - Specify which world to force (e.g., "main", "explore")
  - Perfect for servers with multiple worlds where you want RTP only in one

### Teleport Requests
- **`/tpa <player>`** - Request to teleport to another player
- **`/tpahere <player>`** - Request a player to teleport to you
- **`/tpaccept`** - Accept a teleport request
- **`/tpdeny`** - Deny a teleport request
- **`/tphere <player>`** - Instantly teleport a player to you (Admin)
- 30-second timeout (configurable)

### Spawn

- **`/spawn`** - Teleport to the world spawn point
- **Per-world or global spawn**: Configure whether `/spawn` uses per-world spawns or always goes to main world
  - `spawn.perWorld: false` (default) - Always teleport to main world spawn
  - `spawn.perWorld: true` - Teleport to current world's spawn
  - `spawn.mainWorld` - Specify which world is the main world
- **Spawn teleport delay**: Configure delay before teleporting players to spawn on join
  - `spawn.teleportDelaySeconds: 2` (default) - Delay in seconds before teleporting
  - Increase this value if players experience client crashes or timeouts during login
  - Applies to both first-join and every-login teleport features

### Kit System
- **`/kit [name]`** - Open kit GUI or claim a specific kit
- **One-time kits** - Kits that can only be claimed once per player
- **Cooldown kits** - Configurable cooldown between claims
- **Starter Kit** - Automatically given to new players on first join
- **Kit Commands** - Kits can execute server commands on claim (e.g., grant permissions, give currency, run any plugin command)
- Fully configurable items, cooldowns, and permissions per kit

### Utility Commands
- **`/god`** - Toggle invincibility (become immune to all damage)
- **`/heal`** - Fully restore your health
- **`/fly`** - Toggle creative flight without creative mode
- **`/flyspeed <speed>`** - Set fly speed multiplier (10-100, or 'reset' for default)
- **`/top`** - Teleport to the highest block at your current position
- **`/msg <player> <message>`** - Send a private message
- **`/reply`** - Reply to the last private message (aliases: /r)
- **`/clearinv`** - Clear all items from your inventory (Admin, aliases: /clearinventory, /ci)
- **`/invsee <player>`** - View and edit another player's inventory (Admin)
  - Uses the same approach as Hytale's built-in `/inv see` command

### Command Aliases
- **`/alias create <name> <command> [permission]`** - Create custom shortcut commands (Admin)
- **`/alias delete <name>`** - Delete an alias
- **`/alias list`** - List all aliases
- **`/alias info <name>`** - Show alias details
- **Works with ANY command** - Execute commands from any mod/plugin (e.g., `/alias claims sc` runs `/sc`)
- **Silent mode** - Add `"silent": true` in aliases.json to suppress teleport messages
- **Command chains** - Use `;` to execute multiple commands (e.g., `warp spawn; heal; fly`)
- **Security** - Two-gate permission model: alias permission controls who can use the alias, target command permission is always enforced to prevent privilege escalation
- **Optimized paths** - EE commands (warp, spawn, home, etc.) support silent mode and /back saving
- **Generic dispatch** - All other commands (including other mods) run via `CommandManager.handleCommand()`
- **Auto-generated permissions** - Custom permissions auto-generate as `eliteessentials.command.alias.<name>`
  - Example: `/alias create chatty /gc alias.chatty` creates permission `eliteessentials.command.alias.chatty`
  - Permissions `everyone` and `op` remain unchanged

### Communication & Server Management
- **`/motd`** - Display the Message of the Day
  - Rich formatting with color codes (`&a`, `&c`, `&l`, etc.)
  - Clickable URLs automatically detected
  - Placeholders: `{player}`, `{server}`, `{world}`, `{playercount}`
  - Stored in `motd.json` for easy editing
  - Auto-display on join (configurable)
- **`/rules`** - Display the server rules
  - Color-coded formatting for readability
  - Stored in `rules.json` for easy editing
  - Fully customizable content
- **`/broadcast <message>`** - Broadcast a message to all players (Admin, alias: /bc)
  - Supports color codes for formatted announcements
- **`/clearchat`** - Clear chat for all players (Admin, alias: /cc)
  - Displays configurable message after clearing
- **`/list`** - Show all online players (aliases: /online, /who)
  - Displays player count and sorted list of names
  - Helpful for finding exact player names for commands
- **Group-Based Chat Formatting** - Customize chat appearance by player group
  - Works with LuckPerms groups and simple permissions
  - Priority-based group selection (highest priority wins)
  - Color codes and placeholders: `{player}`, `{displayname}`, `{message}`, `{prefix}`, `{suffix}`, `{group}`
  - **Hex color support**: Use `&#RRGGBB` format for precise colors (e.g., `&#FF5555`)
  - **LuckPerms prefix/suffix support**: Use `{prefix}` and `{suffix}` to display LuckPerms meta
  - Create gradients with per-character hex colors
  - Fully configurable per group in `config.json`
  - Easy to add custom groups
- **PlaceholderAPI Integration** - Full support for cross-plugin placeholders
  - Use placeholders from other plugins in chat format (e.g., `%vault_eco_balance%`)
  - EliteEssentials provides its own placeholders for other plugins to use
  - Config option: `chatFormat.placeholderapi` to toggle PAPI processing
- **Range-Based Group Chat** - Create proximity-based chat channels
  - Configure `range` field in group chats for local/proximity chat
  - Perfect for RP servers where only nearby players should hear you
- **Group Chat Formatting** - Use the same prefix/color formatting from regular chat in group chat
  - When enabled, player names in group chat use LuckPerms prefixes/suffixes and group priorities
  - Configurable format template: `{channel_color}{channel_prefix} {chat_format}`
  - Admin spy mode to monitor all channels with `/gcspy`
- **Group Chat Channels** - Create private chat channels for different groups
  - Group-based chats tied to LuckPerms groups (e.g., admin, moderator)
  - Permission-based chats tied to permission nodes (e.g., trade chat)
  - Range-limited chats for proximity-based communication
  - Configure channels in `groupchat.json`
- **Group Chat Formatting** - Use the same prefix/color formatting from regular chat in group chat
  - When enabled, player names in group chat use LuckPerms prefixes/suffixes and group priorities
  - Configurable format template: `{channel_color}{channel_prefix} {chat_format}`
  - Admin spy mode to monitor all channels with `/gcspy`
- **Join Messages** - Automatic messages when players join
  - First join messages broadcast to everyone
  - Fully customizable in config
  - Option to suppress default Hytale join messages

### Nickname System
- **`/nick <nickname>`** - Set your own display nickname (persists across restarts)
- **`/nick off`** - Clear your own nickname
- **`/nick <player> <nickname|off>`** - Set/clear another player's nickname (requires `misc.nickname.others`)
- **`/realname <name>`** - Look up the real username behind a nickname (requires `misc.nickname.lookup`)
- Nicknames appear in chat, group chat, tab list, `/msg`, `/list`, and join/quit messages
- Color codes in nicknames gated behind `eliteessentials.command.misc.nick.color` permission
- Note: other mods will still show the player's real username
- Admin only in simple mode

### Ignore System
- **`/ignore <player>`** - Block a player's public and private messages
- **`/ignore list`** - View all players you are currently ignoring
- **`/unignore <player>`** - Stop ignoring a specific player
- **`/unignore all`** - Stop ignoring all players (reset)
- Ignored players' chat messages are hidden only for you
- Private messages from ignored players are silently blocked
- Data stored per-player (persists across restarts)

### Mute System (Admin)
- **`/mute <player> [reason]`** - Mute a player server-wide with optional reason
- **`/unmute <player>`** - Unmute a player
- Muted players cannot send public chat or private messages
- Muted players are notified when muted/unmuted
- Mute data stored in `mutes.json` (persists across restarts)

### Ban System (Admin)
- **`/ban <player> [reason]`** - Permanently ban a player with optional reason
- **`/tempban <player> <time> [reason]`** - Temporarily ban a player
  - Time format: `1d` (1 day), `2h` (2 hours), `30m` (30 minutes), `1d12h` (1 day 12 hours)
  - Supports days (d), hours (h), minutes (m), seconds (s)
- **`/ipban <player> [reason]`** - Ban a player's IP address to prevent alt accounts
- **`/unban <player>`** - Remove a permanent or temporary ban
- **`/unipban <player>`** - Remove an IP ban
- Banned players are immediately kicked from the server
- Ban data persists across restarts in `bans.json`, `tempbans.json`, and `ipbans.json`
- Temporary bans automatically expire and are cleaned up on server start
- IP bans work with both TCP and QUIC connections
- Supports offline player unbanning by name

### Freeze System (Admin)
- **`/freeze <player>`** - Toggle freeze on a player (prevents all movement)
- Frozen players cannot move, jump, or fly
- Freeze state persists across restarts in `freezes.json`
- Frozen players are notified when frozen/unfrozen
- Perfect for moderating rule-breakers or conducting investigations

### Player Info Commands
- **`/seen <player>`** - Check when a player was last online
- **`/joindate [player]`** - View when a player first joined the server
  - No argument shows your own join date
  - Separate permission for viewing other players' join dates
- **`/playtime [player]`** - View total play time on the server
  - No argument shows your own playtime
  - Includes current session for accurate live totals
  - Separate permission for viewing other players' playtime

### Help System
- **`/eehelp`** - Shows all commands the player has permission to use (alias: /ehelp)
- **Custom help entries** - Add help text for commands from other plugins via `custom_help.json`
  - Each entry has `command`, `description`, `permission`, and `enabled` fields
  - Permission supports `everyone`, `op`, or custom permission nodes
  - Reloads with `/ee reload`

### AFK System
- **`/afk`** - Toggle AFK (Away From Keyboard) status
- Automatic inactivity detection after configurable timeout (default: 5 minutes)
- Players automatically exit AFK when they move
- AFK players show `[AFK]` prefix in tab list and `/list`
- Optional chat broadcast when players go AFK or return
- PlayTime Rewards integration - admins can exclude AFK players from earning rewards
- Configurable: timeout, broadcast, tab list display, reward exclusion

### Sleep Percentage (Admin)
- **`/sleeppercent <0-100>`** - Set percentage of players needed to skip the night
- **Configurable sleep times** - Control when players can sleep (`nightStartHour`) and wake up (`morningHour`)
- Progress messages shown to all players
- Automatically skips to morning when threshold reached

### Economy System (Disabled by Default)
- **`/wallet`** - View your balance
- **`/wallet <player>`** - View another player's balance (requires permission)
- **`/wallet set/add/remove <player> <amount>`** - Admin balance management
- **`/pay <player> <amount>`** - Send money to another player
- **`/baltop`** - View richest players leaderboard
- **`/eco`** - Console/admin economy management command
- Configurable currency name, symbol, and starting balance
- Command costs - charge players for using teleport commands
- Full API for other mods to integrate (`com.eliteessentials.api.EconomyAPI`)

### Mail System
- **`/mail send <player> <message>`** - Send mail to any player (online or offline)
- **`/mail read [number]`** - Read a specific mail or first unread
- **`/mail list`** - List all mail with timestamps and unread indicators
- **`/mail clear`** - Clear all mail
- **`/mail clear read`** - Clear only read mail
- **`/mail delete <number>`** - Delete specific mail
- Login notification when you have unread mail
- Spam protection with per-recipient cooldown
- Configurable mailbox limit and message length

### PlayTime Rewards
- **Repeatable Rewards** - Trigger every X minutes of playtime (e.g., hourly bonus)
- **Milestone Rewards** - One-time rewards at specific playtime thresholds (e.g., 100 hours = VIP)
- **LuckPerms Integration** - Execute LuckPerms commands directly via API:
  - `lp user {player} group set/add/remove <group>` - Manage player groups
  - `lp user {player} permission set/unset <permission>` - Manage permissions
  - `lp user {player} promote/demote <track>` - Promote/demote on tracks
- **Economy Integration** - Grant currency rewards with `eco add {player} <amount>`
- **Custom Messages** - Configurable messages per reward
- **onlyCountNewPlaytime** - Option to only count playtime after system was enabled
- Rewards defined in `playtime_rewards.json`
- Works without LuckPerms - LP commands are skipped with a warning if not installed

## Configuration

All settings are fully configurable via `mods/EliteEssentials/config.json`:

- **Enable/disable any command** - Disabled commands become OP-only
- **Cooldowns** - Prevent command spam
- **Warmups** - Require players to stand still before teleporting (with movement detection)
- **RTP range** - Set min/max teleport distance
- **Home limits** - Max homes per player
- **Back history** - How many locations to remember
- **Death tracking** - Enable/disable /back to death location
- **Messages** - 60+ configurable messages for full localization

Config file is automatically created on first server start with sensible defaults. Existing configs auto-migrate when updating to new versions.

## Commands Summary

| Command | Description | Access |
|---------|-------------|--------|
| `/home [name]` | Teleport to home | Everyone |
| `/sethome [name]` | Set a home | Everyone |
| `/delhome [name]` | Delete a home | Everyone |
| `/homes` | List your homes | Everyone |
| `/back` | Return to previous location | Everyone |
| `/spawn` | Teleport to spawn | Everyone |
| `/rtp` | Random teleport | Everyone |
| `/tpa <player>` | Request teleport | Everyone |
| `/tpahere <player>` | Request player to you | Everyone |
| `/tpaccept` | Accept teleport request | Everyone |
| `/tpdeny` | Deny teleport request | Everyone |
| `/tphere <player>` | Teleport player to you | Admin |
| `/list` | Show online players | Everyone |
| `/afk` | Toggle AFK status | Everyone |
| `/ignore <player>` | Ignore a player's messages | Everyone |
| `/ignore list` | List ignored players | Everyone |
| `/unignore <player>` | Stop ignoring a player | Everyone |
| `/unignore all` | Stop ignoring all players | Everyone |
| `/joindate [player]` | View first join date | Everyone |
| `/playtime [player]` | View total play time | Everyone |
| `/warp [name]` | Teleport to warp | Everyone |
| `/warps` | List all warps | Everyone |
| `/kit [name]` | Open kit GUI or claim kit | Everyone |
| `/god` | Toggle invincibility | Admin |
| `/heal` | Fully restore health | Admin |
| `/fly` | Toggle creative flight | Admin |
| `/flyspeed <speed>` | Set fly speed (10-100) | Admin |
| `/top` | Teleport to highest block | Admin |
| `/msg <player> <msg>` | Private message | Everyone |
| `/reply` | Reply to last message | Everyone |
| `/motd` | Display MOTD | Everyone |
| `/rules` | Display server rules | Everyone |
| `/broadcast <message>` | Broadcast to all players | Admin |
| `/mute <player> [reason]` | Mute a player | Admin |
| `/unmute <player>` | Unmute a player | Admin |
| `/ban <player> [reason]` | Permanently ban a player | Admin |
| `/tempban <player> <time> [reason]` | Temporarily ban a player | Admin |
| `/ipban <player> [reason]` | Ban a player's IP address | Admin |
| `/unban <player>` | Unban a player | Admin |
| `/unipban <player>` | Remove an IP ban | Admin |
| `/freeze <player>` | Toggle freeze on a player | Admin |
| `/clearinv` | Clear all inventory items | Admin |
| `/invsee <player>` | View player's inventory | Admin |
| `/clearchat` | Clear chat for all players | Admin |
| `/setwarp <name> [perm]` | Create warp | Admin |
| `/delwarp <name>` | Delete warp | Admin |
| `/warpadmin` | Warp admin panel | Admin |
| `/sleeppercent [%]` | Set sleep percentage | Admin |
| `/wallet` | View your balance | Everyone |
| `/wallet <player>` | View another's balance | Everyone* |
| `/pay <player> <amount>` | Send money to player | Everyone |
| `/baltop` | View richest players | Everyone |
| `/eco` | Economy admin commands | Admin |
| `/mail` | Send/receive offline mail | Everyone |
| `/nick <nickname>` | Set your display nickname | Admin |
| `/nick off` | Clear your nickname | Admin |
| `/nick <player> <nickname\|off>` | Set/clear another player's nickname | Admin+ |
| `/realname <name>` | Look up real username behind a nickname | Admin |
| `/alias` | Manage command aliases | Admin |
| `/eehelp` | Show available commands | Everyone |
| `/eliteessentials reload` | Reload configuration | Admin |

*In simple mode (default), "Everyone" commands work for all players, "Admin" requires OP.*

## Permissions

EliteEssentials supports two permission modes via `advancedPermissions` in config.json:

### Simple Mode (Default)
- **Everyone** commands work for all players
- **Admin** commands require OP or `eliteessentials.admin.*`

### Advanced Mode (LuckPerms Compatible!)
Full granular permissions following `eliteessentials.command.<category>.<action>` structure:

| Category | Example Permissions |
|----------|---------------------|
| Home | `command.home.home`, `command.home.sethome`, `command.home.limit.5`, `command.home.warmup.0` |
| Teleport | `command.tp.tpa`, `command.tp.back`, `command.tp.back.ondeath`, `command.tp.warmup.rtp.5` |
| Warp | `command.warp.use`, `command.warp.<warpname>`, `command.warp.warmup.0` |
| Spawn | `command.spawn.use`, `command.spawn.protection.bypass`, `command.spawn.warmup.0` |
| Kit | `command.kit.use`, `command.kit.<kitname>`, `command.kit.bypass.cooldown` |
| Ignore | `command.misc.ignore` |
| Invsee | `command.misc.invsee` |
| Admin | `admin.mute`, `admin.unmute` |
| Bypass | `command.home.bypass.cooldown`, `command.tp.bypass.warmup`, `bypass.cost` |

See [PERMISSIONS.md](PERMISSIONS.md) for the complete permission reference.

## Roadmap

- **Chat Filter** - Configurable word filter with customizable actions (warn, mute, kick).
- **SQL Support** - Ability to use External SQL for Mod storage.
