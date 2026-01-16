# EliteEssentials

A server-side essentials plugin for Hytale that provides teleportation and utility commands for survival multiplayer servers. Actively developed with big plans ahead. Our goal is a complete, modular essentials suite where you enable only what you need.

## **Please visit the [Wiki](https://github.com/EliteScouter/EliteEssentials/wiki) for FULL DETAILS.**

## Features

### Full Localization Support

All 60+ player-facing messages are configurable in `config.json`. Translate the mod to any language!

*   Placeholder support: `{player}`, `{seconds}`, `{name}`, `{count}`, `{max}`, `{location}`
*   Config auto-migrates - existing settings preserved when updating

### Kit System with GUI

*   **`/kit`** - Opens a stylish kit selection interface
*   Configurable kits with items, cooldowns, and permissions
*   **One-time kits** - Kits that can only be claimed once per player
*   **Cooldown kits** - Configurable cooldown between kit claims
*   **Starter kit** - Name a kit "Starter" to auto-give to new players on first join
*   Per-kit permissions in advanced mode
*   Kits stored in `kits.json` with full customization

### Home System

*   **`/sethome [name]`** - Save named home locations (default: "home")
*   **`/home [name]`** - Teleport to your saved homes
*   **`/delhome <name>`** - Delete a home
*   **`/homes`** - List all your homes
*   Configurable max homes per player

### Server Warps

*   **`/warp [name]`** - Teleport to a server warp (lists warps if no name)
*   **`/warps`** - List all available warps with coordinates
*   **`/setwarp <name> [all|op]`** - Create a warp (Admin)
*   **`/delwarp <name>`** - Delete a warp (Admin)
*   **`/warpadmin`** - Admin panel for managing warps
*   Permission levels: `all` (everyone) or `op` (admins only)
*   Persisted to `warps.json`

### Back Command

*   **`/back`** - Return to your previous location
*   Remembers multiple locations (configurable history)
*   Works on death - return to where you died (configurable)

### Random Teleport

*   **`/rtp`** - Teleport to a random location in the world
*   Safe landing detection (avoids spawning underground)
*   Configurable min/max range, cooldown, and warmup

### Teleport Requests

*   **`/tpa <player>`** - Request to teleport to another player
*   **`/tpaccept`** - Accept a teleport request
*   **`/tpdeny`** - Deny a teleport request
*   30-second timeout (configurable)

### Spawn & Spawn Protection

*   **`/spawn`** - Teleport to the server spawn point
*   **`/setspawn`** - Set the server spawn location (Admin)
*   **Spawn Protection** - Protect spawn area from building/breaking
*   Configurable radius and Y-range
*   PvP protection in spawn area
*   Bypass permission for admins
*   **Requires `/setspawn` to be used first**

### Private Messaging

*   **`/msg <player> <message>`** - Send a private message
*   **`/reply`** - Reply to your last conversation
*   Tracks last conversation partner for quick replies
*   Aliases: /m, /message, /whisper, /pm, /tell

### Utility Commands

*   **`/god`** - Toggle invincibility (Admin)
*   **`/heal`** - Fully restore health (Admin)
*   **`/fly`** - Toggle creative flight mode (Admin)
*   **`/top`** - Teleport to highest block at your position

### Sleep Percentage (Admin)

*   **`/sleeppercent <0-100>`** - Set percentage of players needed to skip the night
*   Progress messages shown to all players
*   Automatically skips to morning when threshold reached

## Configuration

All settings are fully configurable via `mods/EliteEssentials/config.json`:

*   **Enable/disable any command** - Disabled commands become OP-only
*   **Cooldowns** - Prevent command spam
*   **Warmups** - Require players to stand still before teleporting (with movement detection)
*   **RTP range** - Set min/max teleport distance
*   **Home limits** - Max homes per player
*   **Back history** - How many locations to remember
*   **Death tracking** - Enable/disable /back to death location
*   **Spawn protection** - Radius, Y-range, PvP settings
*   **Messages** - 60+ configurable messages for full localization

Config file is automatically created on first server start with sensible defaults. Existing configs auto-migrate when updating to new versions.

## Commands Summary

| Command                      |Description                 |Permission |
| ---------------------------- |--------------------------- |---------- |
| <code>/home [name]</code>    |Teleport to home            |Everyone   |
| <code>/sethome [name]</code> |Set a home                  |Everyone   |
| <code>/delhome [name]</code> |Delete a home               |Everyone   |
| <code>/homes</code>          |List your homes             |Everyone   |
| <code>/back</code>           |Return to previous location |Everyone   |
| <code>/spawn</code>          |Teleport to spawn           |Everyone   |
| <code>/setspawn</code>       |Set server spawn location   |Admin      |
| <code>/rtp</code>            |Random teleport             |Everyone   |
| <code>/tpa &lt;player&gt;</code> |Request teleport            |Everyone   |
| <code>/tpaccept</code>       |Accept teleport request     |Everyone   |
| <code>/tpdeny</code>         |Deny teleport request       |Everyone   |
| <code>/warp [name]</code>    |Teleport to warp            |Everyone   |
| <code>/warps</code>          |List all warps              |Everyone   |
| <code>/setwarp &lt;name&gt; [perm]</code> |Create warp                 |Admin      |
| <code>/delwarp &lt;name&gt;</code> |Delete warp                 |Admin      |
| <code>/warpadmin</code>      |Warp admin panel            |Admin      |
| <code>/kit</code>            |Open kit selection GUI      |Everyone   |
| <code>/msg &lt;player&gt; &lt;msg&gt;</code> |Private message             |Everyone   |
| <code>/reply &lt;msg&gt;</code> |Reply to last message       |Everyone   |
| <code>/top</code>            |Teleport to highest block   |Everyone   |
| <code>/god</code>            |Toggle invincibility        |Admin      |
| <code>/heal</code>           |Restore full health         |Admin      |
| <code>/fly</code>            |Toggle flight mode          |Admin      |
| <code>/sleeppercent [%]</code> |Set sleep percentage        |Admin      |
| <code>/eliteessentials reload</code> |Reload configuration        |Admin      |

_In simple mode (default), "Everyone" commands work for all players, "Admin" requires OP._

## Permissions

EliteEssentials supports two permission modes via `advancedPermissions` in config.json:

### Simple Mode (Default)

*   **Everyone** commands work for all players
*   **Admin** commands require OP or `eliteessentials.admin.*`

### Advanced Mode (**LUCKPERMS COMPATIBLE**)

Full granular permissions following `eliteessentials.command.<category>.<action>` structure:

| Category |Example Permissions                                           |
| -------- |------------------------------------------------------------- |
| Home     |<code>command.home.home</code>, <code>command.home.sethome</code>, <code>command.home.limit.5</code> |
| Teleport |<code>command.tp.tpa</code>, <code>command.tp.back</code>, <code>command.tp.back.ondeath</code> |
| Warp     |<code>command.warp.use</code>, <code>command.warp.&lt;warpname&gt;</code> |
| Spawn    |<code>command.spawn.use</code>, <code>command.spawn.set</code>, <code>command.spawn.protection.bypass</code> |
| Kit      |<code>command.kit.use</code>, <code>command.kit.&lt;kitname&gt;</code>, <code>command.kit.bypass.cooldown</code> |
| Bypass   |<code>command.home.bypass.cooldown</code>, <code>command.tp.bypass.warmup</code> |

Enable advanced mode by setting `"advancedPermissions": true` in config.json.

## Roadmap

*   **Chat Filter** - Configurable word filter with customizable actions (warn, mute, kick).
*   **Player Nicknames** - Allow players to set display names.
*   **MOTD & Announcements** - Customizable join messages and scheduled broadcasts.
*   **AFK Detection** - Auto-kick or mark players as AFK after inactivity.
*   **Vanish** - Allow admins to go invisible to players.
*   **Invsee** - View and edit other players' inventories.
*   **Trashcan** - Dispose of unwanted items.
*   **SQL Support** - Ability to use External SQL for Mod storage.