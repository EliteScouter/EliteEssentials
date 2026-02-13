# EliteEssentials Permissions

## Permission Modes

EliteEssentials supports two permission modes controlled by `advancedPermissions` in config.json:

### Simple Mode (Default: `advancedPermissions: false`)

Commands are either available to Everyone or Admin only:

| Command | Description | Access |
|---------|-------------|--------|
| `/home [name]` | Teleport to home | Everyone |
| `/sethome [name]` | Set a home | Everyone |
| `/delhome [name]` | Delete a home | Everyone |
| `/homes` | List your homes (GUI) | Everyone |
| `/back` | Return to previous location | Everyone |
| `/spawn` | Teleport to spawn | Everyone |
| `/setspawn` | Set spawn location | Admin |
| `/rtp` | Random teleport | Everyone |
| `/tpa <player>` | Request teleport | Everyone |
| `/tpahere <player>` | Request player to you | Everyone |
| `/tpaccept` | Accept teleport request | Everyone |
| `/tpdeny` | Deny teleport request | Everyone |
| `/tphere <player>` | Teleport player to you | Admin |
| `/top` | Teleport to highest block | Admin |
| `/warp [name]` | Teleport to warp | Everyone |
| `/warp list` | List all warps | Everyone |
| `/warpadmin` | Warp admin panel | Admin |
| `/warpsetperm <warp> <perm>` | Set warp permission | Admin |
| `/warpsetdesc <warp> <desc>` | Set warp description | Admin |
| `/kit` | Open kit selection GUI | Everyone |
| `/kit <name>` | Claim specific kit | Everyone |
| `/kitcreate <name>` | Create kit from inventory | Admin |
| `/kitdelete <name>` | Delete a kit | Admin |
| `/msg <player> <message>` | Private message | Everyone |
| `/reply <message>` | Reply to last message | Everyone |
| `/motd` | View message of the day | Everyone |
| `/rules` | View server rules | Everyone |
| `/discord` | View discord info | Everyone |
| `/list` | View online players | Everyone |
| `/eehelp` | View available commands | Everyone |
| `/seen <player>` | Check player info | Everyone |
| `/god` | Toggle invincibility | Admin |
| `/heal` | Restore full health | Admin |
| `/fly` | Toggle flight mode | Admin |
| `/flyspeed <speed>` | Set fly speed (10-100) | Admin |
| `/broadcast <message>` | Server announcement | Admin |
| `/clearchat` | Clear chat for all players | Admin |
| `/clearinv [player]` | Clear inventory | Admin |
| `/sleeppercent [%]` | Set sleep percentage | Admin |
| `/wallet` | View your balance | Everyone |
| `/wallet <player>` | View player balance | Everyone |
| `/wallet set/add/remove` | Modify balance | Admin |
| `/pay <player> <amount>` | Send money | Everyone |
| `/baltop` | View richest players | Everyone |
| `/eco` | Economy admin | Admin |
| `/alias` | Manage command aliases | Admin |
| `/afk` | Toggle AFK status | Everyone |
| `/eliteessentials reload` | Reload config | Admin |

In simple mode, "Admin" means players in the OP group or with `eliteessentials.admin.*` permission.

### Advanced Mode (`advancedPermissions: true`)

Full granular permission nodes following Hytale best practices: `namespace.category.action`

**Note: Custom Cooldowns and Limits require LuckPerms**

When using advanced permissions mode, custom numeric values for cooldowns and limits (e.g., `eliteessentials.command.home.limit.37` or `eliteessentials.command.tp.cooldown.rtp.1000`) require LuckPerms to be installed. Without LuckPerms, only config default values are used.

- **Cooldowns**: Use any number of seconds (e.g., `.cooldown.1000` for 1000 seconds)
- **Limits**: Use any number (e.g., `.limit.37` for 37 homes)
- Lowest cooldown value wins (most favorable to player)
- Highest limit value wins (most favorable to player)

## Permission Hierarchy (Advanced Mode)

```
eliteessentials
├── command
│   ├── home                        # Home category
│   │   ├── home                    # /home command
│   │   ├── sethome                 # /sethome command
│   │   ├── delhome                 # /delhome command
│   │   ├── homes                   # /homes command (GUI)
│   │   ├── limit
│   │   │   ├── <number>            # Max homes (e.g., limit.5)
│   │   │   └── unlimited           # Unlimited homes
│   │   └── bypass
│   │       ├── cooldown            # Bypass home cooldown
│   │       └── warmup              # Bypass home warmup
│   │
│   ├── tp                          # Teleport category
│   │   ├── tpa                     # /tpa command
│   │   ├── tpahere                 # /tpahere command
│   │   ├── tpaccept                # /tpaccept command
│   │   ├── tpdeny                  # /tpdeny command
│   │   ├── rtp                     # /rtp command
│   │   ├── back                    # /back command
│   │   │   └── ondeath             # Use /back after death
│   │   ├── top                     # /top command (Admin)
│   │   ├── tphere                  # /tphere command (Admin)
│   │   ├── cooldown                # Cooldown overrides
│   │   │   ├── rtp.<seconds>       # Custom RTP cooldown
│   │   │   ├── tpa.<seconds>       # Custom TPA cooldown
│   │   │   └── back.<seconds>      # Custom back cooldown
│   │   └── bypass
│   │       ├── cooldown            # Bypass all tp cooldowns
│   │       │   ├── rtp             # Bypass RTP cooldown
│   │       │   ├── back            # Bypass back cooldown
│   │       │   └── tpa             # Bypass TPA cooldown
│   │       └── warmup              # Bypass all tp warmups
│   │           └── rtp             # Bypass RTP warmup
│   │
│   ├── warp                        # Warp category
│   │   ├── use                     # Teleport to ALL public warps
│   │   ├── list                    # /warp (GUI) and /warp list
│   │   ├── set                     # /warpadmin create (Admin)
│   │   ├── delete                  # /warpadmin delete (Admin)
│   │   ├── admin                   # /warpadmin command (Admin)
│   │   ├── setperm                 # /warpsetperm (Admin)
│   │   ├── setdesc                 # /warpsetdesc (Admin)
│   │   ├── <warpname>              # Access specific warp
│   │   ├── limit
│   │   │   ├── <number>            # Max warps player can create
│   │   │   └── unlimited           # Unlimited warp creation
│   │   └── bypass
│   │       ├── cooldown            # Bypass warp cooldown
│   │       └── warmup              # Bypass warp warmup
│   │
│   ├── spawn                       # Spawn category
│   │   ├── use                     # /spawn command
│   │   ├── set                     # /setspawn command (Admin)
│   │   ├── protection
│   │   │   └── bypass              # Bypass spawn protection
│   │   └── bypass
│   │       ├── cooldown            # Bypass spawn cooldown
│   │       └── warmup              # Bypass spawn warmup
│   │
│   ├── kit                         # Kit category
│   │   ├── use                     # Base permission for /kit
│   │   ├── gui                     # Open kit selection GUI
│   │   ├── <kitname>               # Access specific kit
│   │   ├── create                  # /kitcreate (Admin)
│   │   ├── delete                  # /kitdelete (Admin)
│   │   └── bypass
│   │       └── cooldown            # Bypass kit cooldowns
│   │
│   ├── misc                        # Utility commands
│   │   ├── msg                     # /msg command
│   │   ├── reply                   # /reply command
│   │   ├── motd                    # /motd command
│   │   ├── rules                   # /rules command
│   │   ├── discord                 # /discord command
│   │   ├── list                    # /list command
│   │   ├── eehelp                  # /eehelp command
│   │   ├── seen                    # /seen command
│   │   ├── god                     # /god command (Admin)
│   │   ├── heal                    # /heal command (Admin)
│   │   │   ├── bypass
│   │   │   │   └── cooldown        # Bypass heal cooldown
│   │   │   └── cooldown
│   │   │       └── <seconds>       # Custom heal cooldown
│   │   ├── fly                     # /fly command (Admin)
│   │   ├── flyspeed                # /flyspeed command (Admin)
│   │   ├── broadcast               # /broadcast command (Admin)
│   │   ├── clearchat               # /clearchat command (Admin)
│   │   ├── clearinv                # /clearinv command (Admin)
│   │   ├── sleeppercent            # /sleeppercent command (Admin)
│   │   ├── afk                     # /afk command (Everyone)
│   │   └── groupchat               # /gc command
│   │
│   └── economy                     # Economy commands
│       ├── wallet                  # /wallet (own balance)
│       │   ├── others              # /wallet <player>
│       │   └── admin               # /wallet set/add/remove
│       ├── pay                     # /pay command
│       └── baltop                  # /baltop command
│
├── bypass
│   └── cost                        # Bypass ALL command costs
│       ├── home                    # Bypass home cost
│       ├── sethome                 # Bypass sethome cost
│       ├── spawn                   # Bypass spawn cost
│       ├── warp                    # Bypass warp cost
│       ├── back                    # Bypass back cost
│       ├── rtp                     # Bypass RTP cost
│       ├── tpa                     # Bypass TPA cost
│       └── tpahere                 # Bypass TPAHere cost
│
└── admin
    ├── *                           # Full admin access (wildcard)
    ├── reload                      # /eliteessentials reload
    └── alias                       # /alias commands
```

## Permission Reference (Advanced Mode)

### Home Commands

| Permission | Description |
|------------|-------------|
| `eliteessentials.command.home.home` | Teleport to your home |
| `eliteessentials.command.home.sethome` | Set a home location |
| `eliteessentials.command.home.delhome` | Delete a home |
| `eliteessentials.command.home.homes` | List your homes (GUI) |
| `eliteessentials.command.home.limit.<n>` | Max homes (any value, requires LuckPerms) |
| `eliteessentials.command.home.limit.unlimited` | Unlimited homes |
| `eliteessentials.command.home.bypass.cooldown` | Bypass home cooldown |
| `eliteessentials.command.home.bypass.warmup` | Bypass home warmup |

### Teleport Commands

| Permission | Description |
|------------|-------------|
| `eliteessentials.command.tp.tpa` | Request teleport |
| `eliteessentials.command.tp.tpahere` | Request player to you |
| `eliteessentials.command.tp.tpaccept` | Accept requests |
| `eliteessentials.command.tp.tpdeny` | Deny requests |
| `eliteessentials.command.tp.rtp` | Random teleport |
| `eliteessentials.command.tp.back` | Return to previous location |
| `eliteessentials.command.tp.back.ondeath` | Use /back after death |
| `eliteessentials.command.tp.top` | Teleport to highest block (Admin) |
| `eliteessentials.command.tp.tphere` | Teleport player to you (Admin) |
| `eliteessentials.command.tp.bypass.cooldown` | Bypass all tp cooldowns |
| `eliteessentials.command.tp.bypass.cooldown.rtp` | Bypass RTP cooldown |
| `eliteessentials.command.tp.bypass.cooldown.back` | Bypass back cooldown |
| `eliteessentials.command.tp.bypass.cooldown.tpa` | Bypass TPA cooldown |
| `eliteessentials.command.tp.bypass.warmup` | Bypass all tp warmups |
| `eliteessentials.command.tp.bypass.warmup.rtp` | Bypass RTP warmup |
| `eliteessentials.command.tp.cooldown.rtp.<seconds>` | Custom RTP cooldown (any value, requires LuckPerms) |
| `eliteessentials.command.tp.cooldown.tpa.<seconds>` | Custom TPA cooldown (any value, requires LuckPerms) |
| `eliteessentials.command.tp.cooldown.back.<seconds>` | Custom back cooldown (any value, requires LuckPerms) |

### Warp Commands

| Permission | Description |
|------------|-------------|
| `eliteessentials.command.warp.list` | Use /warp (GUI) and /warp list |
| `eliteessentials.command.warp.use` | Teleport to ALL public warps |
| `eliteessentials.command.warp.<warpname>` | Access specific warp |
| `eliteessentials.command.warp.set` | Create warps (Admin) |
| `eliteessentials.command.warp.delete` | Delete warps (Admin) |
| `eliteessentials.command.warp.admin` | Warp administration (Admin) |
| `eliteessentials.command.warp.setperm` | Set warp permissions (Admin) |
| `eliteessentials.command.warp.setdesc` | Set warp descriptions (Admin) |
| `eliteessentials.command.warp.limit.<n>` | Max warps player can create (any value, requires LuckPerms) |
| `eliteessentials.command.warp.limit.unlimited` | Unlimited warp creation |
| `eliteessentials.command.warp.bypass.cooldown` | Bypass warp cooldown |
| `eliteessentials.command.warp.bypass.warmup` | Bypass warp warmup |

### Spawn Commands

| Permission | Description |
|------------|-------------|
| `eliteessentials.command.spawn.use` | Teleport to spawn |
| `eliteessentials.command.spawn.set` | Set spawn location (Admin) |
| `eliteessentials.command.spawn.protection.bypass` | Bypass spawn protection |
| `eliteessentials.command.spawn.bypass.cooldown` | Bypass spawn cooldown |
| `eliteessentials.command.spawn.bypass.warmup` | Bypass spawn warmup |

### Kit Commands

| Permission | Description |
|------------|-------------|
| `eliteessentials.command.kit.use` | Base permission for /kit |
| `eliteessentials.command.kit.gui` | Open kit selection GUI |
| `eliteessentials.command.kit.<name>` | Access specific kit |
| `eliteessentials.command.kit.create` | Create kits (Admin) |
| `eliteessentials.command.kit.delete` | Delete kits (Admin) |
| `eliteessentials.command.kit.bypass.cooldown` | Bypass kit cooldowns |

### Utility Commands

| Permission | Description |
|------------|-------------|
| `eliteessentials.command.misc.msg` | Private messaging (/msg) |
| `eliteessentials.command.misc.reply` | Reply to messages (/reply) |
| `eliteessentials.command.misc.motd` | View message of the day |
| `eliteessentials.command.misc.rules` | View server rules |
| `eliteessentials.command.misc.discord` | View discord info |
| `eliteessentials.command.misc.list` | View online players |
| `eliteessentials.command.misc.eehelp` | View available commands |
| `eliteessentials.command.misc.seen` | Check player info |
| `eliteessentials.command.misc.god` | Toggle god mode (Admin) |
| `eliteessentials.command.misc.heal` | Heal to full health (Admin) |
| `eliteessentials.command.misc.heal.bypass.cooldown` | Bypass heal cooldown |
| `eliteessentials.command.misc.heal.cooldown.<seconds>` | Custom heal cooldown (any value, requires LuckPerms) |
| `eliteessentials.command.misc.fly` | Toggle flight mode (Admin) |
| `eliteessentials.command.misc.flyspeed` | Set fly speed (Admin) |
| `eliteessentials.command.misc.broadcast` | Server announcements (Admin) |
| `eliteessentials.command.misc.clearchat` | Clear chat for all players (Admin) |
| `eliteessentials.command.misc.clearinv` | Clear inventory (Admin) |
| `eliteessentials.command.misc.repair` | Repair items (Admin) |
| `eliteessentials.command.misc.repair.all` | Repair all items (Admin) |
| `eliteessentials.command.misc.repair.bypass.cooldown` | Bypass repair cooldown |
| `eliteessentials.command.misc.repair.cooldown.<seconds>` | Custom repair cooldown (any value, requires LuckPerms) |
| `eliteessentials.command.misc.sleeppercent` | Set sleep percentage (Admin) |
| `eliteessentials.command.misc.afk` | Toggle AFK status |

### Economy Commands

| Permission | Description |
|------------|-------------|
| `eliteessentials.command.economy.wallet` | View own balance |
| `eliteessentials.command.economy.wallet.others` | View other player balance |
| `eliteessentials.command.economy.wallet.admin` | Modify balances (Admin) |
| `eliteessentials.command.economy.pay` | Send money to players |
| `eliteessentials.command.economy.baltop` | View richest players |

### Admin Permissions

| Permission | Description |
|------------|-------------|
| `eliteessentials.admin.*` | Full admin access |
| `eliteessentials.admin` | Also works as admin wildcard |
| `eliteessentials.admin.reload` | Reload configuration |
| `eliteessentials.admin.alias` | Manage command aliases |

### Cost Bypass Permissions

| Permission | Description |
|------------|-------------|
| `eliteessentials.bypass.cost` | Bypass ALL command costs |
| `eliteessentials.bypass.cost.home` | Bypass home cost |
| `eliteessentials.bypass.cost.sethome` | Bypass sethome cost |
| `eliteessentials.bypass.cost.spawn` | Bypass spawn cost |
| `eliteessentials.bypass.cost.warp` | Bypass warp cost |
| `eliteessentials.bypass.cost.back` | Bypass back cost |
| `eliteessentials.bypass.cost.rtp` | Bypass RTP cost |
| `eliteessentials.bypass.cost.tpa` | Bypass TPA cost |
| `eliteessentials.bypass.cost.tpahere` | Bypass TPAHere cost |

## Wildcard Support

| Wildcard | Grants |
|----------|--------|
| `eliteessentials.*` | All permissions |
| `eliteessentials.admin.*` | All admin permissions |
| `eliteessentials.admin` | Also works as admin wildcard |
| `eliteessentials.command.*` | All commands |
| `eliteessentials.command.home.*` | All home commands + limits + bypass |
| `eliteessentials.command.tp.*` | All teleport commands + bypass |
| `eliteessentials.command.warp.*` | All warp commands + bypass |
| `eliteessentials.command.spawn.*` | All spawn commands + bypass |
| `eliteessentials.command.kit.*` | All kit commands + bypass |
| `eliteessentials.command.misc.*` | All utility commands |
| `eliteessentials.command.economy.*` | All economy commands |
| `eliteessentials.bypass.cost.*` | Bypass all command costs |

## Example Group Configurations (Advanced Mode)

### VIP Group

```
eliteessentials.command.home.limit.10
eliteessentials.command.home.bypass.cooldown
eliteessentials.command.tp.cooldown.rtp.300
eliteessentials.command.warp.bypass.cooldown
eliteessentials.command.kit.bypass.cooldown
eliteessentials.bypass.cost
```

### Moderator Group

```
eliteessentials.command.misc.fly
eliteessentials.command.misc.heal
eliteessentials.command.misc.god
eliteessentials.command.misc.broadcast
eliteessentials.command.misc.clearchat
eliteessentials.command.tp.top
eliteessentials.command.tp.tphere
eliteessentials.command.spawn.protection.bypass
eliteessentials.command.tp.bypass.cooldown
eliteessentials.command.tp.bypass.warmup
eliteessentials.command.warp.use
eliteessentials.command.economy.wallet.others
eliteessentials.command.home.limit.20
```

### Admin Group

```
eliteessentials.admin.*
```
