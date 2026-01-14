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

| Command | Description | Permission |
|---------|-------------|------------|
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
| `/setwarp <name> [perm]` | Create warp | Admin |
| `/delwarp <name>` | Delete warp | Admin |
| `/warpadmin` | Warp admin panel | Admin |
| `/sleeppercent [%]` | Set sleep percentage | Admin |

*Any command can be disabled in config, making it OP-only.*
