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

### Kit System
- **`/kit [name]`** - Open kit GUI or claim a specific kit
- **One-time kits** - Kits that can only be claimed once per player
- **Cooldown kits** - Configurable cooldown between claims
- **Starter Kit** - Automatically given to new players on first join
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
- **`/list`** - Show all online players (aliases: /online, /who)
  - Displays player count and sorted list of names
  - Helpful for finding exact player names for commands
- **Group-Based Chat Formatting** - Customize chat appearance by player group
  - Works with LuckPerms groups and simple permissions
  - Priority-based group selection (highest priority wins)
  - Color codes and placeholders: `{player}`, `{displayname}`, `{message}`
  - **Hex color support**: Use `&#RRGGBB` format for precise colors (e.g., `&#FF5555`)
  - Create gradients with per-character hex colors
  - Fully configurable per group in `config.json`
  - Easy to add custom groups
- **Join Messages** - Automatic messages when players join
  - First join messages broadcast to everyone
  - Fully customizable in config
  - Option to suppress default Hytale join messages

### Sleep Percentage (Admin)
- **`/sleeppercent <0-100>`** - Set percentage of players needed to skip the night
- Progress messages shown to all players
- Automatically skips to morning when threshold reached

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
| `/clearinv` | Clear all inventory items | Admin |
| `/setwarp <name> [perm]` | Create warp | Admin |
| `/delwarp <name>` | Delete warp | Admin |
| `/warpadmin` | Warp admin panel | Admin |
| `/sleeppercent [%]` | Set sleep percentage | Admin |
| `/alias` | Manage command aliases | Admin |
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
| Home | `command.home.home`, `command.home.sethome`, `command.home.limit.5` |
| Teleport | `command.tp.tpa`, `command.tp.back`, `command.tp.back.ondeath` |
| Warp | `command.warp.use`, `command.warp.<warpname>` |
| Spawn | `command.spawn.use`, `command.spawn.protection.bypass` |
| Kit | `command.kit.use`, `command.kit.<kitname>`, `command.kit.bypass.cooldown` |
| Bypass | `command.home.bypass.cooldown`, `command.tp.bypass.warmup` |

See [PERMISSIONS.md](PERMISSIONS.md) for the complete permission reference.

## Roadmap

- **Chat Filter** - Configurable word filter with customizable actions (warn, mute, kick).
- **Player Nicknames** - Allow players to set display names.
- **AFK Detection** - Auto-kick or mark players as AFK after inactivity.
- **Vanish** - Allow admins to go invisible to players.
- **Invsee** - View and edit other players' inventories.
- **Trashcan** - Dispose of unwanted items.
- **SQL Support** - Ability to use External SQL for Mod storage.
