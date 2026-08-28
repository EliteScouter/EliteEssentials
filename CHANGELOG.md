# Changelog

## 2.0.11 - 2026-08-27

Compatibility release for Hytale server `0.6.1`. **If you have updated your server to `0.6.x`, this release is required.** Older builds crash the world the moment a player finishes connecting, which leaves the server unjoinable. Nothing in your config, permissions, or data files needs to change; drop the new jar in and restart.

This build targets `0.6.1` and will not load on a `0.5.x` server. Stay on 2.0.10 until you update your server.

### Fixed
* **The world crashed as soon as a player joined** - on a `0.6.1` server, every join killed the world the player was joining. The player was disconnected with "The world you were on has crashed (possibly caused by com.eliteessentials:EliteEssentials)", the server reloaded the world, and the next join did it again, so nobody could get on. The console showed `NoSuchMethodError` for `ServerPlayerListPlayer` pointing at EliteEssentials. Cause was the tab list entry format: `0.6.1` added a spectator flag and a lives counter to it, so the older call no longer existed on the new server. Every place the plugin writes a tab list entry (nicknames, permission prefixes, AFK status, and unvanishing) was updated to the new format
* **`/fly` and timed flight on `0.6.1`** - the server replaced its flight on/off switch with a three-state mode (disabled, allowed, forced). `/fly` for yourself and for another player, `/fly` used through a command alias, and the automatic shutoff for timed flight all read and write the new mode. Fly speed and `/flyspeed` are unaffected
* **`spawn.teleportOnEveryLogin` overwrote your saved position in other worlds** - on a multi-world server, logging out of one world wrote the spawn coordinates into that world's saved position as well as the spawn world's. Login still put you at spawn correctly, but walking back through a portal into the world you had logged out of dropped you at the spawn world's raw coordinates in that world instead of where you left off, which on differently shaped worlds could mean mid-air, underground, or in water. Only the spawn world's saved position is rewritten now, so every other world keeps the position you left it at. If a player has never visited the spawn world, that entry is created rather than skipped, so the first login after enabling the setting lands at spawn instead of the spawn world's own default spawn point. Single-world servers and `spawn.perWorld = true` were never affected, and `/back` is unaffected either way since it keeps its own history. Reported by a server owner running 2.0.10

### Changed
* **Requires Hytale server `0.6.1` or newer `0.6.x`** - the plugin now declares `^0.6.1`, so a `0.5.x` server will refuse to load it rather than starting up and failing later
* **`/fly` now reports flight correctly in game modes that force it** - creative and any mode configured to force flight are treated as flight already being on, so your first `/fly` press turns it off instead of appearing to do nothing. Previously a forced-flight mode could report flight as off
* **Internal permission handling updated for the new server API** - the way commands opt out of the server's automatic permission nodes changed in `0.6.1`, and every EliteEssentials command was migrated. No permission node was added, removed, or renamed, so no LuckPerms or config changes are needed. `/kit create` and `/kit delete` keep their own dedicated nodes as before

### Known issues
* **Hardcore lives and the spectator marker can go missing from the tab list** - `0.6.1` shows a lives counter (on hardcore worlds) and a spectator marker in the player list. When EliteEssentials rewrites a player's tab list entry to show a nickname, a permission prefix, or AFK status, those two markers are cleared for that player until the server sends its own update. Only affects servers running hardcore or using spectator mode, and only the tab list display; lives and spectating themselves work normally
* **A nickname or prefix can briefly drop off the tab list on a hardcore world** - when the server broadcasts a lives change, it resends its own version of the player list, which overwrites the nickname or prefix until the next refresh

### Note
* **Check your admin group if admins lose access to commands after updating** - `0.6.1` tightened which permission nodes the server accepts and now rejects a bare `*` node inside a group, logging `Skipping invalid permission node '*' in group '<group>'` at startup. This is a Hytale change, not an EliteEssentials one, but it can take admin rights away from anyone who was made an admin with a bare `*`. Replace it with explicit nodes such as `eliteessentials.admin.*`, which is still accepted

## 2.0.10 - 2026-08-01

Storage reliability release, plus timestamps in chat formats, a `/tempmute` command, and per-group RTP ranges. Two independent storage problems: MySQL never finished creating its schema and silently fell back to JSON on every startup, and the JSON backend could destroy a player's file mid-write and then silently replace it with an empty record. **If you use MySQL or JSON storage, this release is required.** SQLite users get the JSON-path hardening for the shared write logic but were not exposed to either bug.

### Added
* **`/tempmute <player> <time> [reason]`** - mutes a player for a set duration instead of indefinitely. Accepts the same durations as `/tempban` (`1d`, `2h`, `30m`, `1d12h`, and a bare number meaning minutes), works on offline players, and can be run from the console. The mute expires on its own, and one that expired while the server was down is dropped on load. `/unmute` lifts either kind, and `/mute` is unchanged and still permanent
  * **Permission:** `eliteessentials.admin.tempmute`. The console is always allowed
  * **Messages:** new configurable `tempmuteUsage`, `tempmuteSelf`, `tempmuteInvalidTime`, `tempmuteSuccess`, `tempmuteAlready`, `tempmutedNotify`, `tempmutedNotifyReason`, `tempmutedBlocked`, and `playerinfoTempMuted` entries (placeholders: `{player}`, `{time}`, `{reason}`, `{by}`)
  * A temp-muted player who tries to talk now gets the time remaining rather than a bare "you are muted", across public chat, group chat, `/msg`, and `/reply`. `/playerinfo` and the `/eeadmin` Mutes tab also show the remaining time
  * Listed in `/eehelp` for admins, alongside `/mute` and `/unmute`
  * **Storage:** `mutes.json` entries gain a `muteEndTimestamp` field. Existing entries do not have it, so Gson leaves it at `0`, which the plugin reads as permanent. No migration needed and the file stays readable by older builds
* **Per-group RTP ranges** - `/rtp` min and max distance can now differ per permission group, so a VIP rank can be sent further out than the default rank. Advanced permissions mode only; simple mode is unchanged. Two ways to configure it, and both are empty by default so existing servers keep their current behavior
  * **By permission:** define a named range under the new `rtp.permissionRanges` config map, then grant `eliteessentials.command.tp.rtp.range.<name>` to the group. For example `"permissionRanges": {"vip": {"minRange": 500, "maxRange": 10000}}` paired with `eliteessentials.command.tp.rtp.range.vip`. This is a plain boolean node, not a numeric one, so unlike the existing `.limit.<n>` and `.cooldown.<n>` nodes it does not require LuckPerms specifically and works through HyperPerms or any other permission source
  * **By group name:** put the LuckPerms or HyperPerms group name straight into the new `rtp.groupRanges` map, matched case-insensitively, following the same shape as `warps.groupLimits`. No permission node needed
  * **Resolution order:** `permissionRanges` match, then `groupRanges` match, then the existing per-world `rtp.worldRanges` entry, then the global `rtp.minRange` / `rtp.maxRange`. A group range therefore overrides a world range; per-world *and* per-group at the same time is not supported. When a player matches several entries, the one with the largest `maxRange` wins, tie-broken by the smaller `minRange`
  * The range belongs to the player being teleported, so `/rtp <player>` from an admin or the console uses that player's tier rather than the sender's. Granting the `eliteessentials.command.tp.rtp.*` wildcard hands out every named range, which is worth knowing if you grant wildcards by category
  * A range with a negative `minRange`, or a `maxRange` below its `minRange`, is now clamped to something usable and logged as a warning instead of throwing during the location search. This also covers the pre-existing `worldRanges` and global values
* **`{time}` and `{date}` placeholders for chat formats** - a requested addition, for servers that want a timestamp on each chat line. Both work anywhere a chat format string is accepted: every entry in `chatFormat.groupFormats`, `chatFormat.defaultFormat`, and (when `groupChat.useChatFormatting` is enabled) group chat, including `groupChat.formattedMessageFormat`. For example `"Admin": "&8[&7{time}&8] &c[Admin] {player}&r: {message}"` renders as `[14:35] [Admin] Steve: Hello!`
  * **Config:** three new fields under `chatFormat`. `timeFormat` (default `HH:mm`) and `dateFormat` (default `yyyy-MM-dd`) are [Java DateTimeFormatter](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/time/format/DateTimeFormatter.html) patterns, so `h:mm a` gives `2:35 PM`, `HH:mm:ss` gives `14:35:07`, and `dd MMM` gives `01 Aug`. `timeZone` (default empty, meaning the server machine's zone) accepts a zone ID such as `America/New_York` or `UTC`, which matters because most hosts run their boxes on UTC
  * An invalid pattern or an unrecognized zone falls back to the default and logs a warning once per bad value, rather than throwing on every chat message. Existing configs need no changes; the fields are added with their defaults on next load
  * Both placeholders are resolved from a single timestamp per message, so a message sent at midnight cannot show tomorrow's date beside yesterday's time. Substitution happens before the player's message is inserted, so typing `{time}` in chat is not resolved as a placeholder
  * **Naming note:** `{time}` in `messages.json` remains a *duration* (`5m 30s`) used by cooldown, playtime, and fly messages. The two never share a string, since chat format strings do not use the duration form, but the overlap is worth knowing when reading the docs

### Fixed
* **JSON storage could permanently wipe a single player's homes, balance, and playtime** - reported as isolated players losing everything at once while the server ran on `storageType: "json"`. `PlayerFileStorage.savePlayer` opened `new FileOutputStream({uuid}.json)`, which truncates the target to zero bytes immediately, and then streamed Gson directly into it. Any failure after that truncation left a zero-length or half-written file where the player's only copy of their data used to be. Two things caused such failures: a server crash, kill, or full disk landing inside the write window, and `ConcurrentModificationException` thrown by Gson partway through serializing, because `PlayerFile` holds plain `LinkedHashMap`/`ArrayList`/`HashSet` collections that are mutated without synchronization from at least three threads (the world thread via commands, the `EliteEssentials-PeriodicPlayTimeSave` daemon, and the `EliteEssentials-JoinQuit` scheduler), and nothing serialized concurrent writers to the same file. The damage then became permanent on the next load: `loadFromDisk` returned null for an unreadable file, and `getPlayer(uuid, name)` treats null as "brand new player", so it built an empty `PlayerFile` and the next save wrote that empty record over the remains. A zero-length file was the quietest case of all, because Gson returns null for it without throwing, so nothing was logged at any point. Four changes close this:
  * **Player files are now serialized to memory first and only written if serialization succeeded.** A `ConcurrentModificationException` can no longer touch the file; the previous good copy is left exactly as it was. Serialization retries once, since a CME here is transient
  * **Writes go to `{uuid}.json.tmp` and are then atomically renamed over the target.** The live file is never truncated, so a crash or full disk mid-write leaves the previous complete copy intact instead of destroying it. Falls back to a plain replace on filesystems that reject `ATOMIC_MOVE`. `player_index.json` is written the same way
  * **Concurrent writes to the same player file are serialized under a per-player lock**, so two threads can no longer interleave their output into one file
  * **An unreadable player file is now moved to `players/corrupted/{uuid}.json.{timestamp}` and logged at severe level instead of being silently replaced.** The data cannot be reconstructed programmatically from corrupt JSON, but the original bytes are preserved for manual recovery and the event is no longer invisible. If you have already lost data to this bug, those files are gone; this only protects from here on
* **A failed save on disconnect discarded the session's data** - `unloadPlayer` called `savePlayer` when the record was dirty and then removed it from the cache unconditionally. Because the save failure was caught and logged rather than propagated, an IO error or serialization failure meant the entry was evicted while still dirty, so that session's playtime, wallet changes, and homes were dropped with only a severe log line to show for it. `savePlayer` now reports success, and a player whose save failed is kept in memory and left dirty so it is retried by the next save and at shutdown
* **The periodic playtime flush raced the shutdown save** - `shutdown()` called `playerStorageProvider.saveAll()` while the `EliteEssentials-PeriodicPlayTimeSave` thread was still running, and only stopped that thread roughly fifty lines later. `stopPeriodicSave()` now runs before the final `saveAll()`
* **`updateIndex` mutated the name index outside the lock that guards it** - only the file write held `indexLock`, so two threads updating different players could interleave their `removeIf` and `put` on `nameIndex` and then each persist a different view of the map. The read, the mutation, and the write now all happen under the lock. Also fixed a latent `NullPointerException` when `updateIndex` was reached from `getPlayer(uuid, name)` for a stored file with no name recorded
* **MySQL storage never initialized and silently fell back to JSON on every startup** - `SchemaManager` created all of its indexes with `CREATE INDEX IF NOT EXISTS`. That clause is a SQLite extension; standard MySQL has never supported it and rejects it with a syntax error. The index on `players(name)` is the second statement in `createTables()`, immediately after the `ee_players` table, so schema creation aborted at the first index on every start. `StorageFactory` then closed the connection pool and returned `PlayerFileStorage`, and the global and player-warp factories saw the now-null pool and fell back too. The result was a MySQL database holding a single empty `ee_players` table while the server ran entirely on JSON files. All eleven index statements (seven in `createTables`, four across the v2 and v3 migrations) now route through a `createIndex()` helper that keeps `IF NOT EXISTS` on SQLite and, on MySQL, issues a plain `CREATE INDEX` and treats error 1061 (duplicate key name) as success. Catching 1061 was chosen over an `information_schema` pre-check because it is race-free and avoids an extra round trip per index. SQLite behaviour is byte-for-byte unchanged
  * **MariaDB was never affected.** MariaDB accepts `CREATE INDEX IF NOT EXISTS` as an extension of its own (10.0.2 and later), which is why this only ever reproduced on standard MySQL. Reported against MySQL 8.4
  * **No manual database cleanup is needed.** The orphaned `ee_players` table left behind by earlier failed starts is handled by `CREATE TABLE IF NOT EXISTS`, and the remaining tables plus `ee_schema_version` are created on the next startup
  * **Data written while the fallback was active stays in JSON.** Anything saved during the period MySQL was silently inactive lives in the plugin's JSON files and does not move to MySQL on its own. Once you have confirmed `SQL storage initialized successfully (mysql)` in the startup log, run `/eemigration sql force` to bring it across, then `/eemigration cleanup` to move the old JSON files into `backup/`
  * The startup failure was not actually silent: `Schema initialization failed`, `Failed to initialize SQL player storage`, and `Falling back to JSON storage` were all logged at severe level. If your console showed none of these while MySQL was quietly inactive, that points at a separate log-visibility problem worth reporting

### Changed
* **`/tempban` can now be run from the console** - `/tempban <player> <time> [reason]` works from the console and from scheduled tasks, so an automated moderation setup no longer has to shell out to `/ban`. Previously `/tempban` extended the engine's `AbstractPlayerCommand`, which rejects console senders with `Sender must be a player or provide the --player option!`. It now extends `CommandBase` and parses arguments directly, matching `/rtp`, `/heal`, `/spawn`, and `/warp`. Player usage is unchanged, including the offline-player lookup and the kick of an online target. Bans placed from the console are attributed to `Console` in `tempbans.json` and in the `{bannedBy}` placeholder
  * As with `/spawn` and `/warp` in 2.0.9, moving off `AbstractPlayerCommand` detaches the engine's built-in `--player` flag and drops tab-completion of the player name for this command. Use the plain `/tempban <player> <time>` form
  * `/ban`, `/unban`, `/mute`, and `/unmute` are unchanged and still player-only. Say the word if you want those converted too
* **Migration SQL dialect is now passed in rather than sniffed from the JDBC driver** - `SchemaManager.migrate()` picked its dialect by testing whether `DatabaseMetaData.getDatabaseProductName()` contained `mysql`. This worked only by accident: the plugin loads MySQL Connector/J for both MySQL and MariaDB, so both report `MySQL`. Under MariaDB's own driver the product name is `MariaDB`, the check would return false, and every schema migration would emit SQLite DDL against a MariaDB server. `initialize()` already knows the correct backend from the configured `storageType`, so it now passes that flag down instead of re-deriving it. No behaviour change on current builds; this closes a latent break should the driver ever change

## 2.0.9 - 2026-06-22

Console support for the teleport and kit commands, so server owners automating their servers can move players and apply kits from the console or scheduled tasks.

### Added
* **`/kit <player> <kit>` gives a kit to another player from the console or in-game admins** - server owners automating their servers (for example, granting a kit on a scheduled event or from another mod) can now apply kits directly to a named online player. In-game use requires the new `eliteessentials.command.kit.give` permission; the console is always allowed. This admin give intentionally bypasses the target's kit access permission, cooldown, and one-time-claim restrictions, and does not record a cooldown or mark a one-time kit as claimed against them. Inventory space handling is unchanged (overflow drops on the ground, or the kit's replace-inventory behavior applies), and any commands attached to the kit still run for the receiving player. Player usage is unchanged: `/kit` opens the GUI and `/kit <name>` claims a kit for yourself with the normal permission, cooldown, and one-time checks. `/kit` now extends `CommandBase` to allow the console sender (matching the other console-capable commands), so tab-completion of kit names for `/kit <name>` is no longer offered; the `/kit create` and `/kit delete` admin subcommands are unchanged
* **`/near` command** - lists other online players within a configurable radius of you, sorted closest first, with each player's distance shown in blocks (e.g. `Steve (42m)`). Only players in the same world are considered, since cross-world distance is meaningless and reading another player's components off their world thread is unsafe in the ECS. Vanished players are hidden unless you are an admin or hold the vanish permission, matching `/list`. Nicknames are respected when set. Aliased as `/nearby`
  * **Config:** new `near` section with `near.enabled` (default `true`) and `near.distance` (radius in blocks, default `200`)
  * **Permission:** `eliteessentials.command.misc.near` (Everyone in simple mode; grant via LuckPerms in advanced mode)
  * **Messages:** new configurable `nearHeader`, `nearEntry`, `nearPlayers`, and `nearNoPlayers` entries (placeholders: `{count}`, `{distance}`, `{player}`, `{players}`)

### Changed
* **`/spawn` can now be run from the console** - `/spawn <player> [world] [spawnName]` sends the named online player to spawn. With just `/spawn <player>` they go to the primary ("main") spawn of their current world (or the configured main world when `perWorld` is disabled). A `world` argument targets that world's primary spawn, and adding a `spawnName` targets a specific named spawn point in that world (for example `/spawn Steve explore two`). World and spawn names are matched case-insensitively. Previously `/spawn` extended the engine's `AbstractPlayerCommand`, which rejected console senders with `Sender must be a player or provide the --player option!` since Update 5. It now extends `CommandBase` and parses arguments directly, matching the `/rtp` pattern. Player usage is unchanged: `/spawn` still sends you to your own spawn and `/spawn <name>` still selects a named spawn point when `perWorld` is enabled. The console action saves the target's previous location for `/back` and bypasses warmup and cooldown
* **`/warp` can now be run from the console** - `/warp <warpname> <player>` sends the named online player to a warp. In-game admins can use the same two-argument form to send another player (gated on the warp admin permission). Player usage is unchanged: `/warp` opens the GUI, `/warp list` lists warps, and `/warp <name>` warps you. Like `/spawn`, the console/admin action saves the target's previous location for `/back` and bypasses warmup, cooldown, cost, and per-warp access checks
* Both commands now resolve the target with the shared `PlayerSuggestionProvider`, fetch fresh entity refs on the owning world thread before teleporting, and route through `TeleportUtil.safeTeleport`, consistent with the existing console-capable commands (`/rtp`, `/heal`, `/god`, `/fly`, `/clearinv`)

### Note
* Because `/spawn` and `/warp` moved off `AbstractPlayerCommand`, the engine's built-in `--player` flag is no longer attached to them. Use the plain `/spawn <player>` and `/warp <warpname> <player>` forms instead. Tab-completion of spawn-point and warp names is also no longer offered for these two commands (the same trade-off `/rtp` already made); the commands themselves are unaffected

### Fixed
* **`/eeadmin` disconnect when teleporting to another player's home from the Player Data tab.** The Player Data tab's "go to home" (and the related "go to back location" and "go to player warp") buttons routed through `teleportToLocation()`, which hardcoded the `#TpStatusMsg` status element on its error paths. That element only exists in the Teleports view, not the Player Data view (which uses `#PdStatusMsg`). When a teleport hit an error path, most commonly the home's world resolving to `null`, the plugin sent a UI update for an element that did not exist in the active view, and the Hytale client disconnected with "Selected element in CustomUI command was not found. Selector: #TpStatusMsg.Text". Teleporting to your own home appeared to work because that world was loaded, so the teleport succeeded and closed the UI without ever writing a status message. `teleportToLocation()` now takes the status selector from its caller, so each view reports errors to the label it actually renders

## 2.0.8 - 2026-05-26

Compatibility update for Hytale Update 5 (server `0.5.1`). The plugin now compiles and runs against the new server jar; older 2.0.x builds will fail with `NoSuchMethodError` on the new server because of breaking API changes in the network and ECS layers.

### Changed
* **`/tpa`, `/tpahere`, `/tphere`, `/pay` now use the new tab-autocomplete UI** - Update 5 added a chat suggestion popup with `Tab` to cycle and `Shift+Tab` to browse variant options. These four commands switched their `<player>` argument from `ArgTypes.STRING + PlayerSuggestionProvider` to the engine's typed `ArgTypes.PLAYER_REF`. The new chat UI now surfaces online players directly through the engine's built-in `suggestOnlinePlayers` hook, the player name is resolved into a `PlayerRef` at parse time, and offline names are rejected by the engine before the command runs (these commands were online-only anyway). Offline-aware commands (`/ban`, `/unban`, `/mute`, `/unmute`, `/ipban`, `/seen`, `/playtime`, `/joindate`, `/wallet`, `/eco`, `/playerinfo`, `/warn`, `/clearwarnings`, `/warnings`, `/ignore`, `/unignore`, `/tempban`, `/heal`, `/freeze`, `/invsee`, `/msg`) keep the previous `STRING` argument with `PlayerSuggestionProvider` so they can still target offline players
* **`manifest.json` `ServerVersion` is now a SemverRange** - Update 5's plugin manager rejects bare versions (`0.5.1` produced `IllegalArgumentException: Bare version '0.5.1' is not a valid range`) which silently dropped the plugin during pack loading and prevented any commands from registering. The template now resolves to `^0.5.1`, accepting any `0.5.x` server while keeping us out of `0.6.0` until we re-validate. Authors should update to a SemverRange (`^0.5.1`, `~0.5.1`, or `=0.5.1` for an exact match)
* **Hytale server target bumped to `0.5.1`** - `gradle.properties` and `build.gradle.kts` now resolve the official Update 5 server jar from `maven.hytale.com/release`. The pre-release dated coordinate (`2026.03.26-89796e57b`) is no longer used as the default
* **Vector math migrated to JOML** - Hytale moved its custom vector types over to JOML in Update 5 (per the patch notes' "Migrated custom Vector types to JOML equivalents"). Imports of `com.hypixel.hytale.math.vector.Vector3d` / `Vector3i` now point at `org.joml.Vector3d` / `org.joml.Vector3i`, and rotation usage was migrated from the old `Vector3f` to the new `com.hypixel.hytale.math.vector.Rotation3f`. Field semantics are unchanged: `rotation.x` is pitch, `rotation.y` is yaw, `rotation.z` is roll. JOML vector access uses the public `x` / `y` / `z` fields and no longer goes through `getX()` / `getY()` / `getZ()` getters. The `model.Location` class still exposes its original getters and was not affected
* **`PacketHandler#getChannel()` returns `ChannelConnection`** - the network layer no longer hands plugins a raw Netty `Channel`. `IpBanService.getIpFromPacketHandler` now resolves the remote address through `ChannelConnection#remoteAddress()`, which works the same way for both TCP and QUIC connections. The dependency on `com.hypixel.hytale.server.core.io.netty.NettyUtil` was removed
* **`Universe#getPlayers()` returns `Collection<PlayerRef>`** - this was already documented in pre-release update 1 & 2 modders' warnings. Local variables that captured the old `List<PlayerRef>` return type were widened to `Collection<PlayerRef>` in `HytaleListCommand`, `HytaleSendMessageCommand`, and `HytaleReplyCommand`. Call sites that needed list semantics already wrapped the result in `new ArrayList<>(...)` and were unaffected
* **`CommandSender#getDisplayName()` renamed to `getUsername()`** - the anonymous console sender in `CommandExecutor` and the `PlayerCommandSender` wrapper used by aliases were updated to override the new name
* **`SuggestionResult#fuzzySuggest` removed** - `PlayerSuggestionProvider` now manually iterates online players and case-insensitively filters by the typed prefix before forwarding each match through `result.suggest(name)`. Behaviour for end users is unchanged
* **`Player#sendMessage` removed** - per the same modders' warning, `Player` no longer implements `CommandSender`, so it can no longer be used to send chat directly. `WarmupService#tickWarmup` now resolves a `PlayerRef` from `Universe.get().getPlayer(uuid)` for warmup countdown and cancellation messages

## 2.0.7 - 2026-05-03

**IMPORTANT: If you were using H2 storage and have NOT yet run version 2.0.6, you MUST install and run 2.0.6 first before updating to this version.** Version 2.0.6 contains the automatic H2-to-SQLite migration that runs on startup. Starting with 2.0.7, H2 support and the migration code have been fully removed. If you skip 2.0.6, your H2 data will not be migrated and the plugin will fall back to JSON storage with no data. JSON and MySQL users are not affected.

### Removed
* **H2 database engine fully removed** - H2 has been completely removed from the plugin. The H2-to-SQLite migration bridge from 2.0.6 is no longer included. Servers that still have `"h2"` in their config.json will see a clear error on startup and fall back to JSON storage safely. Supported storage backends are now JSON, SQLite, and MySQL only. If you were using H2, you should have run 2.0.6 first to migrate your data to SQLite automatically

### Changed
* **Shadow relocation for SQLite JDBC loader** - the SQLite loader package (`org.xerial`) is now relocated under `com.eliteessentials.libs` in the shaded JAR, matching the existing pattern for Gson, HikariCP, SLF4J, and MySQL. The core `org.sqlite` package is intentionally *not* relocated because SQLite JDBC uses JNI native libraries that call back into Java via `Class.forName("org.sqlite.core.NativeDB")`; relocating it causes a `NoClassDefFoundError` at runtime

### Fixed
* **Vanished players unable to chat** - when a player was in vanish, their chat messages were silently dropped by the Hytale engine's HiddenPlayersManager filtering, making them invisible to all other players. This affected both the default engine chat path (when chat formatting is disabled) and could affect group chat channels. The chat system now detects vanished senders and bypasses the engine's hidden-player filter by cancelling the native event and rebroadcasting the message directly via `sendMessage()`. When chat formatting is enabled, the existing manual broadcast already bypasses the filter. When chat formatting is disabled, a new minimal listener intercepts only vanished players' messages and rebroadcasts them. A new config option `vanish.hideChat` (default `false`) is available for servers that *do* want vanished players' messages hidden from non-admins
* **`/kit` infinite claim exploit when PlayerReadyEvent fails to fire** - if a player's `PlayerReadyEvent` never fired during initial join (observed in production when the login handshake timed out and anti-cheat registration took 36+ minutes), `PlayerService.onPlayerJoin` never ran, so no `PlayerFile` was created. Every subsequent `/kit` call silently skipped cooldown and one-time-claim checks because `playerFileStorage.getPlayer(uuid)` returned null, allowing unlimited kit claims with no persistence. After a server restart the player would also be treated as a first-join again. Fixed with three defensive layers: `KitService.ensurePlayerFile()` now creates and caches a `PlayerFile` on demand if one is missing; `getRemainingCooldown` and `hasClaimedOnetime` are now fail-closed (return full cooldown / already claimed) when the player file is null; and `HytaleKitCommand`, `KitSelectionPage` call `ensurePlayerFile` at every kit entry point (command, GUI open, GUI claim). `PlayerFileStorage.savePlayer` now logs a warning instead of silently no-oping when called for an uncached UUID. Thanks to [Dimotai](https://github.com/Dimotai) for identifying and fixing this (PR #61)

## 2.0.6 - 2026-04-20

**IMPORTANT: If you are using H2 storage, you MUST run this version before updating to 2.0.7.** This version automatically migrates your H2 database to SQLite on startup. Starting with 2.0.7, H2 will be fully removed and the migration will no longer be available. JSON and MySQL users are not affected.

### Added
* **Currency format position option** - new `currencyFormat` config option in the economy section. Set to `"before"` (default, e.g. `$100.00`) or `"after"` (e.g. `100.00$`) to control whether the currency symbol appears before or after the amount. Useful for locales that place the currency identifier after the number
* Centralized all economy formatting through `EconomyAPI.format()` so the setting is respected everywhere (balance notifications, admin dashboard, VaultUnlocked integration, etc.)
* **Unified `/spy` command** - new `/spy` command that consolidates all staff monitoring into one place with three independently toggleable modes:
  * `/spy gchat` - toggle group chat spy (see messages from channels you don't belong to). Replaces the old `/gcspy` command
  * `/spy dm` - toggle DM spy (see all `/msg` and `/reply` private messages between players)
  * `/spy command` - toggle command spy (see all commands executed by other players)
  * `/spy` with no arguments shows your current spy status and usage help
  * Each mode can be enabled/disabled independently in config under the new `spy` section (`spy.enabled`, `spy.gchatSpyEnabled`, `spy.dmSpyEnabled`, `spy.commandSpyEnabled`)
  * Spy message formats are configurable: `spy.dmSpyFormat` (placeholders: `{sender}`, `{receiver}`, `{message}`) and `spy.commandSpyFormat` (placeholders: `{player}`, `{command}`). Group chat spy format remains in `groupChat.spyFormat`
  * `/gcspy` still works as an alias for `/spy gchat` for backward compatibility
  * 13 new configurable messages in `messages.json` (all prefixed with `spy`)
  * **Permission:** `eliteessentials.admin.spy` (Admin in simple mode; grant via LuckPerms in advanced mode)
  * Spy state is cleaned up automatically on player disconnect

### Changed
* **Replaced H2 with SQLite for embedded database storage** - the embedded SQL storage engine has been switched from H2 to SQLite. H2 contains classes that mod platforms flag as "powerful PC abilities," causing JAR rejections on upload. SQLite provides the same embedded database experience with no security flags. The config option is now `"sqlite"` instead of `"h2"`. MySQL is unaffected. Servers that were running on H2 will have their data automatically migrated to SQLite on first startup after updating, with no manual steps required. The old `eliteessentials.mv.db` file is kept as a backup and the config is updated from `"h2"` to `"sqlite"` automatically. If migration fails, the plugin falls back to JSON storage to prevent data loss. H2 is still bundled in this version solely for migration purposes and will be removed in 2.0.7

## 2.0.5 - 2026-04-13

### Fixed 
* **Vanish rapid toggle still corrupting ECS archetype** - the PR #59 `toggleInProgress` guard released in a `finally` block before the deferred `world.execute()` Invulnerable mutation ran, so two `/v` commands fired within one second could both return and then race on the world thread, corrupting the ECS archetype and nulling the Player component. Confirmed in production: a player executed `/v` twice in 1 second and caused a cascade of 3,414 NullPointerExceptions in `GamePacketHandler` over 90 seconds on the world tick thread. The toggle guard is now held for the full lifetime of the toggle including the deferred `world.execute()` mutation, using a `guardRelease` callback threaded through to the lambda's `finally` block with `AtomicBoolean` for exactly-once release. A 500ms per-player cooldown also rejects back-to-back toggles before they reach the guard as defense in depth. `onPlayerLeave` now clears both the toggle guard and cooldown for the disconnecting player so leaked state cannot poison future reconnects. Thanks to [Dimotai](https://github.com/Dimotai) for identifying and fixing this (PR #60)

## 2.0.4 - 2026-04-05

### Fixed
* **Vanish ghost entity on disconnect** - when a vanished player disconnected, their entity could remain as a "ghost" in the EntityStore's UUID registry because the hidden-player state was not fully cleaned up before the engine's disconnect pipeline ran. On reconnect, the UUID collision caused an "Invalid entity reference" crash loop that only a server restart could fix. The disconnect handler now unconditionally un-hides the player from all other players' HiddenPlayersManagers and strips the Invulnerable component (if not in god mode) before the engine tears down the entity, ensuring a clean removal from the store. Thanks to [Dimotai](https://github.com/Dimotai) for identifying and fixing this (PR #59)
* **Vanish rapid toggle corrupting live entity** - quickly toggling vanish (e.g. `/v /v` in rapid succession) could overlap on ForkJoinPool threads, causing concurrent putComponent/removeComponent calls on the entity's Invulnerable component. The ECS archetype migration race would corrupt the entity, nulling the Player component and crashing the server. Added a per-player toggle guard that rejects concurrent vanish toggles, and moved all ECS component mutations to the world thread via world.execute() instead of applying them synchronously from the command handler thread. Thanks to [Dimotai](https://github.com/Dimotai) for identifying and fixing this (PR #59)

## 2.0.3 - 2026-03-29

### Added
* **Player Warps (`/pwarp`)** -- a full player warp system where players can create, share, and teleport to each other's warp locations. Disabled by default; enable with `playerWarps.enabled: true` in config.json
  * `/pwarp` shows a help menu with all subcommands
  * `/pwarp gui` opens an interactive GUI with pagination, showing warp name, owner, description, and visibility
  * `/pwarp create <public|private> <name> [description]` creates a warp at the player's current location
  * `/pwarp delete <name>` deletes a warp (owner only, or admin with `pwarp.admin.delete`)
  * `/pwarp <name>` teleports to a player warp with warmup, cooldown, and cost support
  * `/pwarp list` shows the player's own warps with count and limit
  * `/pwarp info <name>` shows warp details (owner, visibility, description, coordinates)
  * `/pwarp setdesc <name> <description>` updates a warp's description
  * `/pwarp setloc <name>` moves a warp to the player's current position
  * `/pwarp toggle <name>` switches a warp between public and private visibility
  * Public warps are visible and usable by all players; private warps are only visible to the owner
  * GUI includes delete button for owned warps with double-click confirmation (same pattern as server warps)
  * Full storage backend support: JSON (`player_warps.json`), H2, and MySQL. Follows the configured `storage.storageType` automatically. SQL schema is added via migration (v3)
  * Full LuckPerms/HyperPerms integration: per-group warp limits (`pwarp.limit.<number>`, `pwarp.limit.unlimited`), cooldown/warmup bypass and overrides, cost bypass
  * Config section: `playerWarps` with `enabled` (default true), `blacklistedWorlds` (wildcard support), `cooldownSeconds`, `warmupSeconds`, `maxWarps`, `groupLimits`, `cost` (teleport), `createCost` (creation)
  * 25+ configurable messages in `messages.json` (all prefixed with `pwarp` or `gui.PlayerWarp`)
  * Warp names validated: alphanumeric with underscore/dash, max 32 chars. Descriptions max 100 chars
  * Reload support via `/eliteessentials reload`
* **Per-rank kit cooldowns** — kit cooldowns can now be overridden per rank using LuckPerms or HyperPerms permissions. Assign `eliteessentials.command.kit.cooldown.<kitname>.<seconds>` to a group to give that rank a custom cooldown for a specific kit (e.g. `eliteessentials.command.kit.cooldown.daily.3600` for a 1-hour cooldown on the "daily" kit). The existing `eliteessentials.command.kit.bypass.cooldown` permission still works to skip cooldowns entirely. Without a permission override, the kit's default cooldown from `kits.json` is used. Follows the same pattern as `/heal`, `/repair`, `/rtp`, and other commands that already support per-rank cooldowns
* **Kit inventory space check** — kits can no longer be claimed when the player's inventory is full. If there aren't enough empty slots in hotbar/storage to hold the kit items (accounting for items that fit in their target slots), the claim is blocked with a configurable message (`kitInventoryFull`). Replace-inventory kits are exempt since they clear the inventory first. Previously, overflow items were silently dropped on the ground
* **Player Warps in `/eeadmin` Player Data tab** — the Player Data tab now shows a "Player Warps" section listing all warps created by the looked-up player. Each entry displays the warp name, visibility (`[Public]`/`[OP]`), and coordinates with TP and DEL buttons matching the existing Homes layout. Warp count is also shown in the summary panel. Deletions are logged to the activity log. Gated by the existing `eliteessentials.admin.playerdata` permission
* **Quick-remove buttons in `/eeadmin` Bans, Mutes, and Warnings tabs** — active bans, active mutes, and warning entries now display an [X] button next to each row. Clicking it immediately unbans, unmutes, or removes that individual warning without needing to type the player name into the unban/unmute input field. The ban list handles both permanent and temp bans. Warning removal is per-entry (not clear-all), so admins can surgically remove a single warning from a player's history. All removals are logged to the activity log

### Fixed
* **`/freeze` not actually freezing players** — freeze only set movement speeds to 0 once (on command execution or player join), but the Hytale engine resets `MovementSettings` on respawn, game mode changes, and other events, allowing frozen players to move again almost immediately. Added a scheduled enforcement loop in `FreezeService` that re-applies zero movement settings to all frozen online players every 500ms. Also centralized the movement manipulation into `applyFreeze()`/`removeFreeze()` static helpers to eliminate duplicated logic across the command, connect listener, and admin UI
* **`/eeadmin` freeze button not applying movement changes** — the freeze/unfreeze buttons in both the Admin Dashboard and Admin Players pages only toggled the data state in `FreezeService` without actually applying or removing the movement speed changes on the target player. Freeze from the UI would only take effect after the player rejoined, and unfreeze would never restore movement. Both pages now dispatch `applyFreeze()`/`removeFreeze()` on the target's world thread for immediate effect
* **Sleep percentage not skipping night** — `SleepService.triggerSlumber()` was setting `WorldSomnolence` to `WorldSlumber` state and promoting sleeping players to `Slumber`, but the engine's `UpdateWorldSlumberSystem` has additional validation (likely checking all players are in beds) that silently cancelled the slumber when only a percentage of players were sleeping. The "Skipping to morning" message would appear but time never advanced. Fixed by using `timeResource.setGameTime()` to jump directly to morning instead of relying on the engine's slumber pipeline. The 2.0.2 crash from `setGameTime()` was caused by also manually manipulating `PlayerSomnolence`/`MorningWakeUp` state; the new approach only sets the time and lets the engine handle player wake-up naturally. Also added debug logging at the counting and trigger points for easier diagnosis
* **Offline player lookup requiring exact name** — all commands that target offline players (`/ban`, `/tempban`, `/mute`, `/warn`, `/warnings`, `/clearwarnings`, `/freeze`, `/ipban`, `/ignore`, `/unignore`, `/mail send`, `/seen`, `/playerinfo`, `/playtime`, `/joindate`, `/eco`, `/wallet`, and the admin UI pages) required the exact full player name to be typed. Online player lookup uses starts-with-ignore-case matching (typing "eli" finds "EliteScouter"), but the offline fallback did a strict exact match against the name index, so partial names would fail with "player never joined." Both `PlayerFileStorage` and `SqlPlayerStorage` now fall back to starts-with partial matching when no exact match is found, giving offline lookups the same fuzzy behavior as online ones
* **Admin Dashboard ban/tempban/ipban/mute/warn/clearwarns/warnlookup not working for offline players** — the main Admin Dashboard page (`/eeadmin`) only used online player lookup (`findPlayer()`) for all ban, temp ban, IP ban, mute, warn, clear warnings, and warning lookup actions, showing "Player is not online" for any offline player. Added offline fallback via `PlayerStorageProvider.getUuidByName()` to all seven actions so they now work for any player who has joined before. IP ban uses the player's last known IP from their stored IP history when offline. The dedicated Admin Bans/Mutes/Warns sub-pages already had offline support
* **Warmup poller silent death** - a single uncaught exception in `WarmupService.pollWarmups()` could permanently kill the warmup poller thread, causing all warmup-based teleports (`/home`, `/warp`, `/tpa`, `/spawn`, `/back`, `/rtp`) to silently stop working for every player until server restart. Admin teleports with 0 warmup were unaffected, masking the issue. Fixed by adding two-layer try/catch protection in `pollWarmups()` so one bad warmup entry cannot kill processing for other players, fixing `ensurePollerRunning()` to check `isDone()` in addition to `isCancelled()` so crashed pollers are properly detected and restarted, and adding a 60-second maximum lifetime safety net to clear stuck warmup entries that were never completed. Thanks to [Dimotai](https://github.com/Dimotai) for identifying and fixing this (PR #58)

## 2.0.2 - 2026-03-28

### Added
* **Broadcast greeting rules** — greeting rules in `greetings.json` now support a `"broadcast": true` option. When enabled, the greeting message is sent to all online players instead of only the triggering player. Useful for VIP/staff join announcements (e.g. `&6[VIP] &e{player} has joined the server!`). Defaults to `false` so existing configs are unaffected. Broadcast rules are automatically skipped for vanished players. A new default example rule `vip-join-announce` is included (disabled by default). Also added `{displayname}` placeholder support in greetings for nick compatibility

### Fixed
* **Migration NPE for all player data** — `/eemigration` from EssentialsCore, EssentialsPlus, Hyssentials, and HomesPlus would fail with `"this.playerFileStorage" is null` for every player file. All four migration services were typed to the concrete `PlayerFileStorage` class, which is only set in JSON storage mode. Servers using H2 or MySQL storage would always get null passed in. Changed all migration services to accept the `PlayerStorageProvider` interface so migrations work regardless of storage backend
* **Sleep percentage world crash** — `SleepService.triggerSlumber()` was calling `timeResource.setGameTime()` directly and manually constructing `PlayerSomnolence` with `MorningWakeUp`, bypassing the engine's `UpdateWorldSlumberSystem`. This created conflicting state mutations that could crash the world. Fixed by delegating to the engine properly: sets `WorldSomnolence` state to `WorldSlumber` and lets the engine handle time progression and player state transitions. Night skip now has a brief visual transition instead of being instant, matching intended engine behavior
* **Admin UI "player not found" for offline players** — all `/eeadmin` actions (ban, temp ban, IP ban, mute, warn, clear warnings, lookup warnings) were using `PlayerSuggestionProvider.findPlayer()` which only searches online players. Added offline fallback via `PlayerStorageProvider.getUuidByName()` so these actions now work for any player who has joined the server before. IP ban uses the player's last known IP from their stored IP history when offline
* **`/ipban` not working for offline players** — the `/ipban` command had the same online-only limitation. Now falls back to the player's stored IP history when the target is offline

## 2.0.1 - 2026-03-28

### Changed
* **Renamed `/admin` to `/eeadmin`** — avoids conflicts with other mods that register their own `/admin` command

### Fixed
* **Default join/leave messages leaking through** — the vanilla "player has joined default" and "player left the server" messages were showing alongside custom EliteEssentials messages, even with `suppressDefaultMessages: true`. Root cause: the event-based suppression (`AddPlayerToWorldEvent.setBroadcastJoinMessage(false)`) was no longer reliably preventing the server from sending the vanilla join message in the latest Hytale API update.
  * Replaced the old `LeaveMessagePacketFilter` (which only caught leave messages via reflection) with a new `DefaultMessagePacketFilter` that intercepts both join and leave `ServerMessage` packets at the network level. Since `ServerMessage.message` and `FormattedMessage.messageId` are now public fields in the updated API, reflection is no longer needed
  * Added `RemovedPlayerFromWorldEvent` handler with `setBroadcastLeaveMessage(false)` — the new API now provides a proper event-based method to suppress leave messages (previously only join messages had this via `AddPlayerToWorldEvent`)

## 2.0.0 - 2026-03-26

Thank you all for using EliteEssentials -- we're really proud of how far this addon has come, and that's thanks to every one of you and the suggestions you've sent in. This release brings H2/SQL storage support; fresh installs will default to H2, and existing users can migrate from JSON to H2 or MySQL using `/eemigration`. There's also a brand new `/eeadmin` UI -- the Hytale UI system is not exactly my strong suit, so please go easy on me. If you have any feedback or suggestions, drop them in Discord or GitHub.

Huge thanks again to everyone using EliteEssentials!

### Fixed
* **Hytale API compatibility** — updated all calls to match Hytale's latest API changes:
  * `PacketHandler.disconnect()` now requires `Message` instead of `String` — wrapped all disconnect calls with `Message.raw()` across ban, tempban, IP ban, warn, kick, and Admin UI pages
  * `PlayerSetupConnectEvent.setReason()` now requires `Message` instead of `String` — updated ban/tempban/IP ban connection denial in `ConnectListener`
  * `Player.sendInventory()` removed from the API — removed all calls in kit claiming, starter kits, clear inventory, and kit selection GUI. Inventory changes are now applied directly without explicit sync
  * Migrated from deprecated `Inventory` class to new ECS-based `InventoryComponent` system — inventory sections (hotbar, storage, armor, utility, tools, backpack) are now accessed as individual ECS components via `store.getComponent()` instead of the monolithic `player.getInventory()`. Updated across clear inventory, kit claiming, kit creation, repair, starter kits, and kit selection GUI
  * `manifest.json` now specifies `ServerVersion` matching the build target instead of wildcard `*`, eliminating the "does not specify a target server version" warning on startup. Version is driven from `gradle.properties` for single-source-of-truth updates

### Added
* **SQL database storage support** — EliteEssentials can now persist all data to a SQL database instead of JSON files. Choose between three storage backends via `config.json`:
  * `"json"` (default) — existing JSON file storage, no changes to current behavior
  * `"h2"` — embedded H2 database, zero setup required. Database file stored in the plugin data folder. Great for single-server setups that want SQL performance and data integrity without installing external software
  * `"mysql"` — external MySQL/MariaDB database for multi-server networks. Player data is shared across all connected servers. Reads fresh data on player join to avoid stale caches
* **Storage provider abstraction** — all services now depend on `PlayerStorageProvider` and `GlobalStorageProvider` interfaces instead of concrete storage classes. JSON and SQL backends implement these interfaces identically
* **Connection pooling** — SQL storage uses HikariCP for efficient connection management. Pool size, idle connections, and timeout are all configurable
* **Automatic schema management** — SQL tables and indexes are created automatically on first startup. Schema versioning via a metadata table supports incremental migrations in future updates
* **Async SQL writes** — write operations run on a background thread to avoid blocking the server main thread. In-memory cache for online players matches existing JSON behavior
* **JSON-to-SQL migration command** — `/eemigration sql [force]` reads all existing JSON data (players, warps, spawns, first-join spawn) and writes it into the configured SQL database
  * Reports progress during migration (records processed)
  * Continues on individual record failures, logging the specific UUID/identifier
  * Reports summary on completion (total migrated, failed, elapsed time)
  * Refuses to run when `storageType` is `"json"`
  * Checks if target tables are empty before migrating; use `force` to overwrite
* **Configurable table prefix** — all SQL table names use a configurable prefix (default `ee_`) to avoid conflicts when sharing a database with other plugins
* **Graceful shutdown** — all pending SQL writes are flushed before the connection pool closes. Pool waits up to 30 seconds for active queries to complete
* **Multi-server freshness** — when using MySQL, player data is loaded fresh from the database on join rather than relying on stale cache. Data is flushed to DB and evicted from cache on disconnect
* **Post-migration cleanup** — `/eemigration cleanup` moves migrated JSON files (`players/`, `warps.json`, `spawn.json`, `firstjoinspawn.json`, `player_index.json`) into a `backup/` subfolder. Only available when `storageType` is not `"json"`. Keeps the data folder clean after switching to SQL
* **Smart storage defaults for new installs** — fresh installs (no existing data files) now default to `"h2"` storage for better out-of-the-box performance. Existing installs upgrading to 2.0.0 keep `"json"` unless manually changed. If `config.json` is missing but JSON data files exist (e.g. `player_index.json`, `players/`, `warps.json`, `spawn.json`), the plugin stays on `"json"` to protect existing data

### Configuration
* New `storage` section in `config.json`:
  ```json
  "storage": {
    "storageType": "json",
    "mysql": {
      "host": "localhost",
      "port": 3306,
      "database": "eliteessentials",
      "username": "root",
      "password": "",
      "tablePrefix": "ee_",
      "connectionPool": {
        "maximumPoolSize": 10,
        "minimumIdle": 2,
        "connectionTimeout": 30000
      }
    }
  }
  ```
* `storageType` options: `"json"` (default, file-based), `"h2"` (embedded SQL database, stored in plugin folder), `"mysql"` (remote MySQL/MariaDB server)
* The `mysql` section is only used when `storageType` is `"mysql"`
* Unrecognized `storageType` values log an error and fall back to `"json"`
* Schema migration failures log an error and fall back to `"json"` to prevent data corruption

## 1.1.21 - 2026-03-17

### Fixed
* **`/playerinfo` crash when target is in a different world** — Viewing info for a player in another world caused an `IllegalStateException` because the coordinate lookup ran on the wrong world thread. The store read is now dispatched to the target player's world thread via `world.execute()`

## 1.1.20 - 2026-03-16

### Added
* **Console support for player commands** — `/repair`, `/heal`, `/god`, `/fly`, `/flyspeed`, and `/clearinv` can now be executed from console (or NPCs) targeting a specific player. All commands follow the same pattern as `/rtp`:
  * `/repair <player> all` or `/repair all <player>` — repair all of a player's items from console
  * `/repair <player>` — repair a player's held item from console
  * `/heal <player>` — heal a player from console
  * `/god <player>` — toggle god mode on a player from console
  * `/fly <player>` — toggle flight on a player from console
  * `/flyspeed <speed> <player>` — set a player's fly speed from console
  * `/clearinv <player>` — clear a player's inventory from console
  * Console always has permission; cooldowns and costs only apply to self-use; target player is notified
* **AFK players excluded from sleep percentage** — AFK players no longer count toward the total player count for night skip. If 10 players are online and 3 are AFK, only 7 count toward the sleep threshold. Config: `afk.excludeFromSleep` (default: `true`). If all players are AFK, the sleep check is skipped entirely
* **Console command execution delay** — Workaround for a native Hytale CommandManager bug that can cause `IllegalStateException: No match found` when multiple commands run back-to-back (e.g. kit commands, playtime reward commands). Commands are now staggered by a configurable delay. Config: `commandExecutionDelayMs` (default: `150`). Set to `0` to disable (previous behaviour). Applies to kits, starter kits, and playtime rewards
* **Playtime reward command staggering** — When multiple playtime rewards fire in the same check (e.g. 1h and 2h), their command batches are now staggered (200 ms between batches) so they don’t overlap and trigger the native command parser bug
* **Warning system** — `/warn <player> [reason]` issues a warning to a player. Warnings are tracked and persisted in `warns.json`. When a configurable threshold is reached (default: 3), an automatic punishment is applied (tempban by default, 24h). Config options under `warn`:
  * `warn.enabled` (default: `true`) — enable/disable the warning system
  * `warn.autoPunishThreshold` (default: `3`) — number of warnings before auto-punishment. Set to `0` to disable
  * `warn.autoPunishAction` — `"tempban"` (default) or `"ban"` (permanent)
  * `warn.autoPunishTempbanMinutes` (default: `1440` / 24h) — duration for tempban auto-punishment
  * `warn.clearAfterPunishment` (default: `true`) — clear warnings after auto-punishment is applied
  * `/warnings <player>` — view a player's full warning history with dates, issuer, and reasons
  * `/clearwarnings <player>` — clear all warnings for a player
  * **Permissions:** `eliteessentials.admin.warn`, `eliteessentials.admin.clearwarnings`
* **Punishment history in /playerinfo** — When viewing another player's info (`/playerinfo <player>`), staff now see a "Punishment Status" section showing current mute status, ban/tempban status (with remaining time), freeze status, and warning count

### Improved
* **Safe landing on /fly disable** — When flight is toggled off (via `/fly` or timed expiry), the player is now teleported to the ground instead of falling. Uses the same ground detection as `/top`, scanning downward for the highest solid block. Skips the teleport if the player is less than 3 blocks above ground

### Fixed
* **Console command failures** — Commands executed as console (e.g. `give (player) Item --quantity=2` from playtime rewards or kits) could fail with `No match found` due to a race in Hytale’s parser. Staggering command execution avoids the issue

## 1.1.19 - 2026-03-07

### Added
* **Multi-spawn behavior options** — When `spawn.perWorld = true` and a world has multiple spawn points (from `/setspawn <name>`), you can choose how `/spawn` and death respawn pick a spawn:
  * **Default (both off):** Everyone goes to the **primary** spawn for that world. Same as before.
  * **`spawn.multiNearbySpawn: true`** — `/spawn` and death respawn send the player to the **nearest** spawn to their current (or death) position. Good for large worlds with several spawn areas.
  * **`spawn.multiRandomSpawn: true`** — `/spawn` and death respawn send the player to a **random** spawn in that world. Good for variety or minigames. If both are enabled, random takes priority.
  * Config keys: `spawn.multiNearbySpawn` (default `false`), `spawn.multiRandomSpawn` (default `false`). No change for existing servers until you turn one on.
* **IP history** — player files (`data/players/{uuid}.json`) now record `ipHistory`: a list of IP addresses with `lastUsed` timestamps
  * Updated on each join; existing IPs get `lastUsed` refreshed; new IPs added; capped at 50 entries
  * Format: `"ipHistory": [{"ip": "10.10.20.100", "lastUsed": 1773012653552}]`
  * Useful for moderation, alt detection, and auditing
* **Group sync** — sync groups between LuckPerms/HyperPerms and EliteEssentials config
  * `/ee groupsync lp-to-ee` — copy LuckPerms groups into config.json (chatFormat, warps.groupLimits)
  * `/ee groupsync hp-to-ee` — copy HyperPerms groups into config.json
  * `/ee groupsync ee-to-lp` — create missing EE config groups in LuckPerms
  * `/ee groupsync ee-to-hp` — create missing EE config groups in HyperPerms
  * `/ee groupsync` — shows detailed help with all directions and what gets synced
  * **Permission:** `eliteessentials.admin.groupsync` (Admin in simple mode; grant via LuckPerms in advanced mode)
* **Greetings** — rule-based conditional welcome messages. Server owners define rules in `greetings.json` to send different messages to different players based on group, permission, world, spawn point, or first-join status
  * **Triggers:** `server_join` (player connects), `world_enter` (player changes world), `respawn` (player respawns after death)
  * **Conditions (AND between types, OR within lists):** `groups` (LuckPerms groups), `permissions` (permission nodes), `worlds` (world names with `*` wildcards), `spawns` (named spawn points), `firstJoin` (true/false)
  * **Features:** per-rule delay (`delaySeconds`), `stopAfterMatch` to prevent lower rules from firing, `showOnce` to only fire once per session, unlimited message lines per rule, full placeholder support (`{player}`, `{world}`, `{server}`, `{playercount}`, `{group}`, `{spawn}`, PlaceholderAPI)
  * **Config:** `greetings.enabled` (default: `false` — opt-in, won't affect existing servers). Rules stored in `greetings.json`
  * **Permission:** `eliteessentials.admin.greetings` (Admin) — for future management commands
  * **Coexists with MOTD:** greetings run independently from the existing MOTD system. Server owners can use both, or disable MOTD and use greetings exclusively
  * **Reloadable:** `greetings.json` is reloaded on `/ee reload`
* **First-join spawn** — teleport new players to a dedicated spawn point (e.g. a tutorial island) on their first join. Completely independent from the regular spawn system
  * `/setfirstjoinspawn` — stand at the desired location and run the command. New players will be teleported there on first join
  * `/delfirstjoinspawn` — remove the first-join spawn point (new players will use Hytale's default spawn)
  * **Config:** `firstJoinSpawn.enabled` (default: `true`), `firstJoinSpawn.delaySeconds` (default: `2` — delay before teleporting to let the player fully load)
  * **Permissions:** `eliteessentials.command.spawn.setfirstjoin` (Admin), `eliteessentials.command.spawn.delfirstjoin` (Admin)
  * **Messages:** `firstJoinSpawnSet`, `firstJoinSpawnDeleted`, `firstJoinSpawnNotSet`, `firstJoinSpawnTeleported`
  * **No conflict** with `teleportOnEveryLogin`: first-join teleport happens at login, every-login rewrite happens at disconnect. First session goes to tutorial, second login onwards goes to regular spawn
  * **Storage:** Saved in `firstjoinspawn.json` (separate from `spawn.json`), supports cross-world teleport
* **Migration force mode** — `/eemigration <source> force` overwrites existing data with source data instead of skipping. Useful when re-migrating after a failed or partial migration
  * Usage: `/eemigration essentialscore force` or `/eemigration essentialsplus force`
  * Without `force`, existing data is preserved (same behavior as before)
  * `/eemigration` with no args now shows full usage help with all sources and options

### Fixed
* **EssentialsCore migration** (`/eemigration essentialscore`): fixed several missing data migrations
  * **Kit cooldowns** — player kit cooldowns from `players/{uuid}.json` are now migrated so claimed-kit timers carry over
  * **Spawn** — `spawn.json` is now imported as the primary spawn for its world (skipped if a spawn already exists)
  * **Player names** — reads `uuids.json` to resolve player names instead of defaulting to "Unknown" for migrated player files
  * Migration result now reports player files found, players skipped (already migrated), spawns imported, and kit cooldowns imported
* **EssentialsPlus migration** (`/eemigration essentialsplus`): expanded to match current EssentialsPlus data format
  * **Spawns** — imports `spawns.json` (array of spawns with `mainSpawn` flag); grouped by world with primary spawn detection
  * **User profiles** — imports `users/{uuid}.json`: balance, playtime (ms→s), firstJoin/lastSeen timestamps, ipHistory, ignoredPlayers
  * **Kit cooldowns** — `lastClaimed` from each kit file is migrated to player kit cooldowns
  * **Force mode** — `/eemigration essentialsplus force` overwrites existing warps, kits, homes, spawns, and player data (same as EssentialScore)
* **Home GUI crash** — fixed `NullPointerException` in `HomeSelectionPage` when sorting homes with a null name (could occur with corrupted or partially-migrated home data). Sort is now null-safe

## 1.1.18 - 2026-03-01

### Added
* **Kit one-time bypass** - Permission `eliteessentials.command.kit.bypass.onetime` allows players to claim one-time kits repeatedly (useful for admins, testing, or VIP perks)
* **Multi-spawn points** - Support for multiple named spawn points per world when `spawn.perWorld = true`
  * `/setspawn [name]` - Set named spawn points (e.g. `/setspawn north`, `/setspawn outpost`). Without a name, sets/updates the primary spawn (backward compatible)
  * `/delspawn <name>` - Delete a named spawn point
  * `/spawns` - List all spawn points in the current world with coordinates, primary flag, and protection status
  * `/spawn [name]` - Teleport to a specific named spawn, or auto-teleport to the **nearest** spawn when no name is given
  * **Death respawn** - Players without a bed respawn at the nearest spawn point to their death location (instead of always the primary)
  * **Per-spawn protection** - Each spawn point has its own `protection` toggle. Spawn protection zones apply independently to each protected spawn point
  * **Config:** `spawn.maxSpawnsPerWorld` (default: 10, -1 for unlimited) - limits spawn points per world
  * **Permissions:** `eliteessentials.command.spawn.delete` (Admin), `eliteessentials.command.spawn.list` (Admin)
  * **Messages:** `spawnTeleportedNamed`, `spawnSetNamed`, `spawnDeleted`, `spawnDeleteNotFound`, `spawnLimitReached`, `spawnListHeader`, `spawnListEntry`, `spawnListPrimary`, `spawnListProtected`, `spawnListEmpty`, `spawnRespawnedAt`, `spawnMultiSpawnDisabled`
  * **Migration:** Existing `spawn.json` (v1 format) is automatically migrated to v2 array format. Existing spawn points become the "main" primary spawn with protection enabled. No data loss
  * **Design notes:** Multi-spawn only activates when `spawn.perWorld = true`(Toggling perWorld config requires restart). When `perWorld = false`, behavior is unchanged (single primary spawn in mainWorld). Cross-world uses primary spawn only (nearest-spawn doesn't make spatial sense across worlds). `teleportOnEveryLogin` always uses the primary spawn
* `/heal` command: self and others support (like `/playerinfo`)
  * `/heal` - Heal yourself (requires `misc.heal`; simple mode = everyone when enabled)
  * `/heal <player>` - Heal another player (requires `misc.heal.others`; simple mode = OP only)
  * Permissions: `eliteessentials.command.misc.heal`, `eliteessentials.command.misc.heal.others`
  * Messages: `healOthersSuccess`, `healTargetNotify` (target notified when healed by someone else)
* `/playerinfo` now shows **coordinates** for the player
  * **Online:** live position (x, y, z) and world from the player's entity
  * **Offline:** last saved position read from the player's Hytale save file (`universe/players/{uuid}.json`). When spawn-on-logout is enabled, this is the spawn position we write on disconnect, so it reflects where they logged out or where they will spawn
  * Messages: `playerinfoLabelCoordinates` ("Coordinates: "), `playerinfoCoordinatesLastSaved` (" (last saved)") for offline coords
  * New util: `HytaleSaveFileReader.readPosition(UUID)` to read Transform.Position and world from the Hytale save file
* **World blacklist** for most gameplay commands. Server owners can now restrict commands from being used in specific worlds (e.g. PvP arenas, minigame worlds). Supports wildcard patterns using `*` (e.g. `"arena*"` blocks any world starting with "arena").
  * Config: `<feature>.blacklistedWorlds` — empty list by default (no worlds blocked). Add world names or wildcard patterns to restrict usage.
  * Applies to: `/heal`, `/god`, `/fly`, `/repair`, `/tpa`, `/tpahere`, `/rtp`, `/back`, `/top`, `/home`, `/sethome`, `/spawn`, `/warp`, `/kit`
  * Message key: `commandBlacklistedWorld` (default: `"&cThis command cannot be used in this world."`)
  * Example config: `"blacklistedWorlds": ["pvparena_world", "arena*", "minigame_*"]`
  * New shared utility: `WorldBlacklistUtil` consolidates wildcard matching logic (same pattern as `respawnExcludedWorlds`)

### Fixed
* Vanish: rare `IndexOutOfBoundsException` in `ArchetypeChunk.getComponent` when toggling `/vanish` (1 in 1000s reports). Root cause: `updateMobImmunity` used `world.execute()` with a cached ref; deferring to a later tick can make the ref stale (same pattern as TeleportUtil). Fix: when the vanish command runs on the world thread (HytaleVanishCommand, AliasService alias), we now pass the caller's fresh store/ref and apply the Invulnerable component synchronously, avoiding deferred execution. Fallback to `world.execute()` remains for non-command call paths.
* Spawn protection: decorations (mushrooms, flowers, and other entity-based placed items) could still be picked up even with `disableItemPickup` enabled. The existing `UseBlock` interaction hook only covered block-type pickups; decorations use `UseEntity` which was unprotected. Added a custom `UseEntity` interaction handler to block decoration pickups in spawn.
* TPA/TPAHERE: cooldown, warmup, and cost permission options now work like home/spawn/rtp
  * **Warmup bypass for TPAHERE:** `getEffectiveWarmup` always used `"tpa"`; for TPAHERE requests (acceptor teleports), `eliteessentials.bypass.warmup.tpahere` is now respected
  * **Cooldown support:** TPA had no cooldown config or logic. Added `tpa.cooldownSeconds` and `tpa.tpahereCooldownSeconds` (both default 0). Cooldown applies when the teleport executes (TPA: requester; TPAHERE: acceptor). Bypass: `eliteessentials.bypass.cooldown.tpa`, `eliteessentials.bypass.cooldown.tpahere`; LuckPerms overrides: `eliteessentials.command.tp.cooldown.tpa.<seconds>`, `.tp.cooldown.tpahere.<seconds>`
  * **Cost in GUI:** TpaSelectionPage used raw config cost instead of `getEffectiveCost`, so permission-based costs (`eliteessentials.cost.tpa.<amount>`, `.cost.tpahere.<amount>`) and bypass (`eliteessentials.bypass.cost.tpa`, `.bypass.cost.tpahere`) did not apply when sending requests from the player-selection GUI. Now uses `getEffectiveCost` for both checks and charges

## 1.1.17 - 2026-03-01

### Added
- Eco command: `/eco give <player> <amount>`: alias for add (same behavior, permission, and messages as `/eco add`)
- Fly command: cost-per-minute mode: pay for a fixed duration of flight, then flight auto-disables. Feature is only enabled when `fly.costPerMinute` is not 0. Price can be overridden by permission `eliteessentials.cost.fly.<amount>`. Duration is set in config.
  - Config: `fly.costPerMinute` (0 = free/unlimited, feature off; when &gt; 0, player pays this amount and flight turns off after the duration), `fly.costPerMinuteDurationSeconds` (default 60, duration in seconds)
  - When enabled, using `/fly` charges the cost (respects economy and `eliteessentials.bypass.cost.fly`), enables flight, and after the configured duration flight is automatically turned off and the player is notified
  - Config: `fly.expiryWarningSeconds`: list of seconds before expiry to notify the player (default `[30, 10, 5]`). Empty list = no warnings.
  - Messages: `flyEnabledTimed` (placeholder `{time}`), `flyExpired`, `flyExpiringIn` (placeholder `{seconds}`) for warnings
- Repair command: cost and cooldown for single repair and repair all
  - Config: `repair.cost`, `repair.costAll` (0 = free), `repair.cooldownSeconds`, `repair.cooldownAllSeconds` (0 = use single cooldown)
  - Permissions: `eliteessentials.command.misc.repair` (single), `eliteessentials.command.misc.repair.all` (all); cooldown bypass and per-command cooldown `.repair.cooldown.<s>` / `.repair.all.cooldown.<s>`; cost bypass `eliteessentials.bypass.cost.repair` / `.bypass.cost.repair.all`
  - Permission-based costs (LuckPerms/HyperPerms): `eliteessentials.cost.repair.<amount>` and `eliteessentials.cost.repair.all.<amount>` override config cost per player/group
- Nick command: separate permissions for color vs formatting (like chat formatting)
  - `eliteessentials.command.misc.nick.color` - allows color codes in nicknames (when `nick.requireColorPermission` is true)
  - `eliteessentials.command.misc.nick.formatting` - allows formatting codes (bold, italic) in nicknames (when `nick.requireFormattingPermission` is true)
  - Config under `nick`: `requireColorPermission` and `requireFormattingPermission` (both default `false`) so basic-permission servers can allow color/format for everyone; set to `true` to require the respective permission
- Offline player support for all moderation commands - you can now `/ban`, `/tempban`, `/mute`, `/freeze`, and `/ignore` players who are not currently online, as long as they have joined the server before
  - Uses the player name index to resolve offline player names to UUIDs
  - If the target is online, they are still kicked/notified as before
  - If the target is offline, the action is recorded and enforced when they next connect
  - `/unmute` now also works on offline players (searches mutes by name)
  - `/unban`, `/unipban`, and `/unignore` already supported offline players
  - New config message `playerNeverJoined` distinguishes "not online" from "never joined this server"
- Added `unmuteByName()` to MuteService, `unfreezeByName()` to FreezeService, and `unbanByName()` to TempBanService for offline player lookups

### Fixed
- Cross-world TPA crash when warmup is 0 seconds - `IllegalStateException: Assert not in thread` caused by teleport callback executing on the wrong world thread (e.g. acceptor's thread instead of requester's thread). WarmupService now dispatches via `world.execute()` even for zero-warmup teleports.
- Cross-world TPA crash after world unload: when a world (e.g. instance) unloads and the player is moved to another world (e.g. spawn), TPA warmup could still complete on the old world's thread, causing `Store.assertThread` when adding the Teleport component to the player's current (spawn) store. `TeleportUtil.safeTeleport` (PlayerRef overload) now always runs the teleport on the world thread that owns the player's current store, so "fall out of world" / thread assertion no longer occurs after unload/recovery.
- Permission-based custom cooldown values not working for `/spawn`, `/home`, and `/warp` commands
  - `/spawn` only checked bypass permission (boolean), ignoring custom cooldown values like `eliteessentials.command.spawn.cooldown.30`
  - `/home` had no cooldown support at all - now fully supports config cooldown + permission-based overrides
  - `/warp` had no cooldown support at all - now fully supports config cooldown + permission-based overrides
  - Added missing cooldown prefix constants: `HOME_COOLDOWN_PREFIX`, `SPAWN_COOLDOWN_PREFIX`, `WARP_COOLDOWN_PREFIX`
  - `getTpCommandCooldown()` now correctly routes home/spawn/warp to their own category prefixes instead of falling through to the generic `tp.cooldown.<cmd>.` pattern
  - Bypass checks in `getTpCommandCooldown()` and `getTpCommandWarmup()` now use `bypassCooldown()`/`bypassWarmup()` routing methods so spawn/home/warp resolve to their correct bypass permissions (e.g. `eliteessentials.command.spawn.bypass.cooldown` instead of `eliteessentials.command.tp.bypass.cooldown.spawn`)
  - Added `trash` to `getCommandCooldown()` switch (was falling through to default and returning config value without checking permissions)
  - Permission format: `eliteessentials.command.home.cooldown.<seconds>`, `eliteessentials.command.spawn.cooldown.<seconds>`, `eliteessentials.command.warp.cooldown.<seconds>`
- Spawn protection: custom UseBlock interaction is now only registered when `spawnProtection.enabled` is true. When disabled, we no longer replace the "UseBlock" handler in the codec registry, so other mods (e.g. PlotMod, SimpleClaims) that register their own UseBlock interaction for claim checks work correctly. Previously, registering our handler unconditionally overwrote theirs and their block-use/claim logic never ran.

## 1.1.16 - 2026-02-26

### Added
- `/playerinfo` command - detailed player info (Much more detailed compared to Hytale's `/whoami`)
  - `/playerinfo` - Your own info (requires `misc.playerinfo`)
  - `/playerinfo <name>` - Another player's info (requires `misc.playerinfo.others`)
  - Displays: UUID (clickable link to hytaleid.com for lookup/copy), Username, Nickname (if set), First join, Last seen (or "Online now"), Wallet, Playtime, Kit claims, Claimed milestones, Homes count, Default group chat
  - Permissions: `eliteessentials.command.misc.playerinfo`, `eliteessentials.command.misc.playerinfo.others`
  - All labels and headers are in `messages.json` for translation (e.g. `playerinfoHeaderSelf`, `playerinfoLabelUuid`, `playerinfoOnlineNow`, etc.)
  - Listed in `/eehelp`

### Fixed
- Nicknames not showing in tab list for players who joined after the nick was set - `TabListService.onPlayerJoin` only pushed the joining player's own entry, so new joiners received Hytale's native player list with real usernames for everyone already online. Now also sends all nicked/prefixed player entries to the new joiner on connect.
- `/back` teleporting players to wrong locations on death - when a back location referenced a world that no longer exists (e.g. temporary arena world), it silently fell back to the player's current world and used the old coordinates, sending them to seemingly random places. Now properly detects missing worlds, discards the stale location, and tells the player the world no longer exists.
  - Same stale-world fallback bug also fixed in the `/back` alias handler (AliasService)
  - Yaw/pitch swap in back location saves for `/rtp`, `/top`, `/tpaccept`, and `/tphere` - the Location constructor expects (yaw, pitch) but several commands were passing (pitch, yaw), storing the wrong facing direction
  - All back location saves now hardcode pitch to 0 to prevent player tilt/lean on teleport - affects `/home`, `/warp`, `/spawn`, `/rtp`, `/top`, `/tpaccept`, `/tphere`, and warp admin

## 1.1.15 - 2026-02-25


### Added
- Player name autocomplete for all commands that take a player argument
  - Typing a partial name (e.g. `/tpa eli`) now shows fuzzy-matched suggestions of online players
  - Works on: `/tpa`, `/tpahere`, `/tphere`, `/msg`, `/pay`, `/seen`, `/playtime`, `/joindate`, `/wallet`, `/eco`, `/mute`, `/unmute`, `/ban`, `/unban`, `/tempban`, `/freeze`, `/ignore`, `/unignore`, `/ipban`, `/unipban`, `/invsee`
  - Uses the engine's built-in `fuzzySuggest` for ranked partial matching (up to 5 results)
- HyperPerms integration - custom cooldowns, warmups, home limits, warp limits, and command costs now work with HyperPerms in addition to LuckPerms
  - Uses the same permission node format as LuckPerms (e.g. `eliteessentials.command.home.limit.5`, `eliteessentials.command.misc.heal.cooldown.30`)
  - No extra plugins or configuration required - if HyperPerms is detected, it is used automatically as a fallback when LuckPerms is not present
  - Registers EliteEssentials permissions with HyperPerms' permission registry for web editor autocomplete. ** NOTE: You must first execute command before LP/Hyper see's it in WebEditor. **
  - Registration uses the same retry pattern as LuckPerms to handle load order differences
  - Affects: home limits, warp limits, all command cooldowns, all command warmups, chat group resolution, prefix/suffix display, tab list prefix, group message targeting
  - `{prefix}`, `{suffix}`, and `{group}` placeholders in chat format now correctly resolve from HyperPerms group data via `ChatAPI` (which handles group inheritance and priority ordering) rather than the player-specific custom prefix override field
  - TabList will also display HyperPerms prefix as well if enabled in config.

### Fixed
- `respawnExcludedWorlds` wildcard patterns (e.g. `arena*`) were never matching due to a regex bug in `worldMatchesPattern` - `Pattern.quote()` made the `*` literal and the subsequent replace never fired, so excluded arena worlds were still getting spawn-teleported

## 1.1.14 - 2026-02-22

### Added
- Nickname system - `/nick` and `/realname` commands
  - `/nick <nickname>` - Set your own display nickname (persists across restarts)
  - `/nick off` - Clear your own nickname
  - `/nick <player> <nickname|off>` - Set/clear another player's nickname (requires `misc.nickname.others`)
  - `/realname <name>` - Look up the real username behind a nickname (requires `misc.nickname.lookup`)
  - Nicknames appear everywhere player names show within EliteEssentials: chat, group chat, tab list, `/msg`, `/list`, join/quit messages
  - Note: other mods will still show the player's real username
  - Color codes in nicknames gated behind `eliteessentials.command.misc.nick.color` permission
  - Config section: `nick.enabled`, `nick.allowColors`
  - Permissions:
    - `eliteessentials.command.misc.nick` - set your own nickname (admin only in simple mode)
    - `eliteessentials.command.misc.nick.color` - use color codes in nicknames
    - `eliteessentials.command.misc.nickname.others` - set/clear other players' nicknames
    - `eliteessentials.command.misc.nickname.lookup` - look up real name via `/realname`
  - Stored in per-player data file (no separate file needed)
- `spawn.respawnExcludedWorlds` config option - list world names where EliteEssentials will skip the death-respawn teleport to spawn
  - Prevents race conditions when another plugin manages its own respawn/teleport logic in specific worlds
  - Supports wildcard patterns: `arena*` matches any world starting with "arena"
  - Players who die in an excluded world are left entirely to the other plugin's respawn handling

### Fixed
- `/ee reload` now refreshes LuckPerms prefix/suffix caches for all online players
  - Previously, changing a group's prefix via `/lp group <group> meta setprefix` wouldn't show in chat until a server restart
  - On reload, `recalculateCaches()` is called for each online player so the new prefix takes effect immediately

### Improved
- `spawn.respawnExcludedWorlds` now also suppresses the `teleportOnEveryLogin` save-file rewrite for players disconnecting from a matching world
  - A single entry like `arena*` now covers both death-respawn and login-spawn suppression
  - Prevents conflicts when another plugin (e.g. ArenaPvP) restores the player's pre-match position after they return to the main world

## 1.1.13 - 2026-02-20

### Added
- Custom help entries via `custom_help.json` - server admins can now add help text for commands from other plugins to `/eehelp`
  - Each entry has `command`, `description`, `permission` (everyone/op/custom node), and `enabled` fields
  - Ships with disabled examples so admins can see the format immediately
  - Reloads with `/ee reload`
- Added all missing commands to `/eehelp`: `/playtime`, `/joindate`, `/mail`, `/afk`, `/ignore`, `/unignore`, `/gc`, `/chats`, `/repair`, `/trash`, `/vanish`, `/clearchat`, `/mute`, `/unmute`, `/ban`, `/unban`, `/tempban`, `/ipban`, `/unipban`, `/freeze`, `/invsee`
  - Each entry respects its config enabled flag and permission checks
- Console broadcast options for monitoring player communications (all disabled by default)
  - `chatFormat.broadcastToConsole` - logs all player chat to console as `[CHAT]`
  - `groupChat.broadcastToConsole` - logs all group chat messages to console as `[GC]`
  - `msg.broadcastToConsole` - logs all private messages (/msg, /reply) to console as `[MSG]`

### Improved
- Rewrote spawn-on-login system to eliminate the visible teleport flash
  - `teleportOnEveryLogin` now rewrites the player's save file on disconnect instead of teleporting after join
  - Players load directly at spawn with no teleport, no screen flash, no delay
  - Works correctly with per-world spawns and cross-world spawn (mainWorld setting)
- New players now land at the correct `/setspawn` location via Hytale's native spawn provider
- Removed `teleportOnFirstJoin` and `teleportDelaySeconds` config options (no longer needed)
- `/setspawn` message is now customizable via messages.json (`spawnSet` key with `{world}` and `{location}` placeholders)
- Fixed a server crash caused by spawn marker entity conflicts during chunk loading
  - Removed startup spawn provider sync that triggered `SpawnReferenceSystems` crashes

### Fixed
- Fixed alias permission bypass that could allow privilege escalation through aliased commands
  - Added Gate 2 permission enforcement: aliases now validate the player has the target command's actual permission before dispatch, not just the alias's own permission
  - Prevents scenarios where an alias with "everyone" permission could invoke admin-only commands
  - Covers all known EE commands via a permission map; unknown commands (other plugins) still pass through to the target command's own permission check via `CommandManager`
  - Debug logging shows when Gate 2 blocks a command

## 1.1.12 - 2026-02-19

### Added
- Invsee command - view and modify another player's inventory
  - `/invsee <player>` - Opens the target player's live inventory on the Bench page
  - Runs on the target player's world thread for cross-world safety
  - Permission: `eliteessentials.command.misc.invsee` (Admin)
  - Configurable messages: `invseeOpened`, `invseePlayerNotFound`, `invseeError`
- Tab list LuckPerms prefix support - new standalone `tabList` config section
  - `tabList.showLuckPermsPrefix` (default: `false`) prepends the player's LuckPerms prefix to their name in the tab list
  - Works alongside the AFK `[AFK]` prefix - LuckPerms prefix appears after it (e.g. `[AFK] [VIP] PlayerName`)
  - Color codes are automatically stripped since the Hytale tab list only supports plain text
  - New `TabListService` handles all tab list display name composition
  - AFK tab list updates now route through `TabListService` so both prefixes stay in sync

### Fixed
- Fixed server crash (`IndexOutOfBoundsException` in `ArchetypeChunk.getComponent`) when running `/back` after multiple `/rtp` commands
  - Root cause: RTP's post-teleport `Invulnerable` component add/remove caused deferred archetype migrations that invalidated the entity `Ref` cached by `AbstractPlayerCommand.executeAsync()`, creating a race condition with any subsequent command
  - Removed `Invulnerable` component manipulation from RTP — only the `Teleport` component is added now, avoiding extra ECS mutations
  - Players still land on safe ground via the existing `findHighestSolidBlock` + `isSafeLocation` checks
- Fixed aliases for non-EE commands (other mods, LuckPerms, etc.) failing with "Wrong sender type, expecting Player"
  - Generic dispatch now uses the `PlayerRef` overload of `CommandManager.handleCommand()` so the API resolves the real `Player` component, which commands extending `AbstractPlayerCommand` require
- Fixed aliases not passing through extra arguments/subcommands typed by the player
  - e.g., `/lucky editor` where `/lucky` is an alias for `/lp` now correctly dispatches `/lp editor`
  - Alias commands now allow extra arguments and append them to the target command
- Fixed PlaceholderAPI expansions (e.g., RPGLeveling) crashing chat with `IllegalStateException: Assert not in thread`
  - Root cause: `PlayerChatEvent` fires on the network thread (`ServerWorkerGroup`), but PAPI expansions call `store.getComponent()` which requires the `WorldThread`
  - PAPI placeholder resolution and message broadcasting now dispatch to the world thread via `world.execute()`
  - Thread-safe work (mute check, format building, LuckPerms placeholders) stays on the network thread for performance
  - Graceful fallback: if the player's world can't be resolved, chat sends without PAPI placeholders instead of silently disappearing

### Changed
- Economy balance change notifications now show configurable `serverSenderName` (default: "Server") instead of "Unknown" when the server deducts money (e.g., command costs, console /eco commands)
  - New config option: `economy.serverSenderName` - customize the name shown as the sender for server-initiated wallet changes
  - Server owners can set this to anything they like (e.g., "Bank", "System", their server name)

## 1.1.11 - 2026-02-17

### Fixed
- Fixed server crashes caused by Hytale Update 3 (2026.02.17) breaking packet API compatibility
  - Hytale split the `Packet` interface into `Packet` and `ToClientPacket`, changing the signature of `PacketHandler.write()` 
  - This caused `NoSuchMethodError` crashes in AFK tab list updates, vanish player list updates, fly toggle, freeze, and any feature that sends packets directly
  - The crash would kill the entire world thread, disconnecting all players
- Updated Server API dependency from `2026.01.24` to `2026.02.17-255364b8e`
- Added CodeMC Hytale repository as a dependency mirror

## 1.1.10

- Previous release
**Player Balance Change Notifications** - Notify players when their wallet balance changes
* New config option: `economy.playerBalanceChangeNotify` with three modes:
  - `"none"` - No notifications (default)
  - `"chat"` - Show balance change in chat (only to affected player)
  - `"chat_global"` - Broadcast balance change to ALL players on server
  - `"tooltip"` - Show balance change in HUD tooltip (when hovering over wallet icon)
* Chat notifications show: sender name, amount changed, change type (added/removed), old balance, new balance
* When console runs commands, sender shows as "Server" instead of blank
* When a player runs commands, sender shows as the player's name
* New config option: `economy.playerBalanceChangeNotifyGlobal` - when true, shows target player's name instead of "your wallet"
  - Example: "Server added 10$ to Steve's wallet. Balance: $100 -> $110"
* Tooltip notifications store the change data in player data for UI to display
* Works with all balance-changing operations: /eco, /wallet, /pay, command costs
* Configurable message format: `balanceChangeNotify` with placeholders `{sender}`, `{target}`, `{oldBalance}`, `{newBalance}`, `{amount}`, `{changeType}`, `{currency}`

**Fixed /eco Command Help** - Fixed command to show proper usage when run without arguments
* Command now parses arguments manually instead of using RequiredArg
* Shows helpful usage message when arguments are missing
* Works correctly when run from console or in-game
  - `"chat"` - Show balance change in chat with color-coded messages
  - `"tooltip"` - Show balance change in HUD tooltip (when hovering over wallet icon)
* Chat notifications show: sender name, amount changed, change type (added/removed), old balance, new balance
* When console runs commands, sender shows as "Server" instead of blank
* When a player runs commands, sender shows as the player's name
* Tooltip notifications store the change data in player data for UI to display
* Works with all balance-changing operations: /eco, /wallet, /pay, command costs
* Configurable message format: `balanceChangeNotify` with placeholders `{sender}`, `{oldBalance}`, `{newBalance}`, `{amount}`, `{changeType}`, `{currency}`

**Overhauled Command Aliases** - Aliases now work with ANY command on the server
* `/alias` now supports any command from any mod/plugin, not just EliteEssentials commands
* Commands execute in the player's security context - no privilege escalation possible
* Two-layer security: alias permission check + target command's own permission check
* Optimized paths for EE commands (warp, spawn, home, etc.) that support silent mode and /back saving
* Generic dispatch for all other commands (including other mods) via `CommandManager.handleCommand()`
* Works with command chains using `;` separator (e.g., `warp spawn; heal; fly`)
* `/alias info` now shows whether each command uses optimized EE path or generic dispatch
* Existing aliases in `aliases.json` continue to work without any changes
* New `PlayerCommandSender` utility class for safe player-impersonating command dispatch

**Auto-Generated Permission Nodes** - Custom alias permissions now auto-generate as `eliteessentials.command.alias.<name>`
* When you specify a custom permission (e.g., `alias.chatty`), it automatically becomes `eliteessentials.command.alias.chatty`
* This provides a consistent permission structure for all aliases
* Example: `/alias create chatty /gc alias.chatty` creates permission `eliteessentials.command.alias.chatty`
* Existing aliases with custom permissions continue to work - they just get the new normalized format
* Permissions `everyone` and `op` remain unchanged

### Changed

**Alias Command Help Text** - Updated to reflect new capabilities
* `/alias create` now shows that any command works (EE or other mods)
* `/alias info` displays dispatch mode for each command in the chain
* `/alias list` shows permission levels for all aliases

## [1.1.9] - 2026-02-15

### Developer Note

Thank you all for your continued support and for using EliteEssentials! I'm working hard to add new features and fix bugs - some take longer than others, but I'm committed to delivering quality updates and making EliteEssentials the best there is!

### Added

**Configurable Spawn Teleport Delay** - Delay before teleporting players to spawn on join
* New config option: `spawn.teleportDelaySeconds` (default: 2)
* Allows server owners to adjust the delay before teleporting players to `/setspawn` on join
* Prevents client crashes and timeouts during player login by giving more time for full load
* Applies to both `teleportOnFirstJoin` and `teleportOnEveryLogin` features
* Cross-world and same-world teleports both use the configurable delay
* Supports `/ee reload` to change the delay without restarting

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
