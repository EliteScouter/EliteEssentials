# EliteEssentials

A server-side essentials plugin for Hytale that provides essential teleportation and utility commands for survival multiplayer servers.

## Features

### 🌍 Full Localization Support
All 60+ player-facing messages are configurable in `config.json`. Translate your server to any language!

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
- **`/tpaccept`** - Accept a teleport request
- **`/tpdeny`** - Deny a teleport request
- 30-second timeout (configurable)

### Spawn
- **`/spawn`** - Teleport to the world spawn point

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
- **`/top`** - Teleport to the highest block at your current position
- **`/msg <player> <message>`** - Send a private message
- **`/reply`** - Reply to the last private message (aliases: /r)

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
| `/tpaccept` | Accept teleport request | Everyone |
| `/tpdeny` | Deny teleport request | Everyone |
| `/warp [name]` | Teleport to warp | Everyone |
| `/warps` | List all warps | Everyone |
| `/kit [name]` | Open kit GUI or claim kit | Everyone |
| `/god` | Toggle invincibility | Everyone |
| `/heal` | Fully restore health | Everyone |
| `/fly` | Toggle creative flight | Everyone |
| `/top` | Teleport to highest block | Everyone |
| `/msg <player> <msg>` | Private message | Everyone |
| `/reply` | Reply to last message | Everyone |
| `/setwarp <name> [perm]` | Create warp | Admin |
| `/delwarp <name>` | Delete warp | Admin |
| `/warpadmin` | Warp admin panel | Admin |
| `/sleeppercent [%]` | Set sleep percentage | Admin |
| `/eliteessentials reload` | Reload configuration | Admin |

*In simple mode (default), "Everyone" commands work for all players, "Admin" requires OP.*

## Permissions

EliteEssentials supports two permission modes via `advancedPermissions` in config.json:

### Simple Mode (Default)
- **Everyone** commands work for all players
- **Admin** commands require OP or `eliteessentials.admin.*`

### Advanced Mode
Full granular permissions following `eliteessentials.command.<category>.<action>` structure:

| Category | Example Permissions |
|----------|---------------------|
| Home | `command.home.home`, `command.home.sethome`, `command.home.limit.5` |
| Teleport | `command.tp.tpa`, `command.tp.back`, `command.tp.back.ondeath` |
| Warp | `command.warp.use`, `command.warp.<warpname>` |
| Spawn | `command.spawn.use` |
| Bypass | `command.home.bypass.cooldown`, `command.tp.bypass.warmup` |

See [PERMISSIONS.md](PERMISSIONS.md) for the complete permission reference.

## Roadmap

Features planned for future releases:

- **Permissions & Groups** - Granular permission system with groups/ranks. Control access to any command per player or group, including commands from other addons.
- **Chat Prefixes** - Display rank/group prefixes in player names and chat.
- **Chat Filter** - Configurable word filter with customizable actions (warn, mute, kick).
- **Player Nicknames** - Allow players to set display names.
- **MOTD & Announcements** - Customizable join messages and scheduled broadcasts.
- **AFK Detection** - Auto-kick or mark players as AFK after inactivity.
- **Vanish** - Allow admins to go invisible to players.
- **Invsee** - View and edit other players' inventories.
- **Trashcan** - Dispose of unwanted items.
