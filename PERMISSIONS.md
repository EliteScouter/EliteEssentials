# EliteEssentials Permissions

## Permission Modes

EliteEssentials supports two permission modes controlled by `advancedPermissions` in config.json:

### Simple Mode (Default: `advancedPermissions: false`)

Commands are either available to **Everyone** or **Admin only**:

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
| `/setwarp <name> [perm]` | Create warp | Admin |
| `/delwarp <name>` | Delete warp | Admin |
| `/warpadmin` | Warp admin panel | Admin |
| `/sleeppercent [%]` | Set sleep percentage | Admin |
| `/eliteessentials reload` | Reload config | Admin |
| `/god` | Toggle invincibility | Admin |
| `/heal` | Restore full health | Admin |
| `/msg <player> <message>` | Private message | Everyone |
| `/reply <message>` | Reply to last message | Everyone |
| `/top` | Teleport to highest block | Everyone |
| `/fly` | Toggle flight mode | Admin |
| `/flyspeed <speed>` | Set fly speed (0-10) | Admin |
| `/kit` | Open kit selection GUI | Everyone |

In simple mode, "Admin" means players in the OP group or with `eliteessentials.admin.*` permission.

### Advanced Mode (`advancedPermissions: true`)

Full granular permission nodes following Hytale best practices: `namespace.category.action`


## Permission Hierarchy (Advanced Mode)

```
eliteessentials
├── command
│   ├── home                    # Home category
│   │   ├── home                # /home command
│   │   ├── sethome             # /sethome command
│   │   ├── delhome             # /delhome command
│   │   ├── homes               # /homes command
│   │   ├── limit
│   │   │   ├── <number>        # Max homes (e.g., limit.5)
│   │   │   └── unlimited       # Unlimited homes
│   │   └── bypass
│   │       ├── cooldown        # Bypass home cooldown
│   │       └── warmup          # Bypass home warmup
│   ├── tp                      # Teleport category
│   │   ├── tpa                 # /tpa command
│   │   ├── tpaccept            # /tpaccept command
│   │   ├── tpdeny              # /tpdeny command
│   │   ├── rtp                 # /rtp command
│   │   ├── back                # /back command
│   │   │   └── ondeath         # Use /back after death
│   │   └── bypass
│   │       ├── cooldown        # Bypass all tp cooldowns
│   │       │   └── <cmd>       # Bypass specific (rtp, back, tpa)
│   │       └── warmup          # Bypass all tp warmups
│   │           └── <cmd>       # Bypass specific
│   ├── warp                    # Warp category
│   │   ├── use                 # /warp command
│   │   ├── list                # /warps command
│   │   ├── set                 # /setwarp command
│   │   ├── delete              # /delwarp command
│   │   ├── admin               # /warpadmin command
│   │   ├── <warpname>          # Access specific warp
│   │   └── bypass
│   │       ├── cooldown
│   │       └── warmup
│   ├── spawn                   # Spawn category
│   │   ├── use                 # /spawn command
│   │   ├── set                 # /setspawn command
│   │   └── bypass
│   │       ├── cooldown
│   │       └── warmup
│   ├── misc                    # Miscellaneous commands
│   │   ├── sleeppercent        # /sleeppercent command
│   │   ├── god                 # /god command
│   │   ├── heal                # /heal command
│   │   ├── msg                 # /msg and /reply commands
│   │   ├── fly                 # /fly command
│   │   └── flyspeed            # /flyspeed command
│   └── kit                     # Kit category
│       ├── use                 # /kit command
│       ├── <kitname>           # Access specific kit
│       ├── create              # Create kits
│       ├── delete              # Delete kits
│       └── bypass
│           └── cooldown        # Bypass kit cooldowns
└── admin
    ├── *                       # Full admin access
    └── reload                  # /eliteessentials reload
```

## Permission Reference (Advanced Mode)

### Home Commands
| Permission | Description |
|------------|-------------|
| `eliteessentials.command.home.home` | Teleport to your home |
| `eliteessentials.command.home.sethome` | Set a home location |
| `eliteessentials.command.home.delhome` | Delete a home |
| `eliteessentials.command.home.homes` | List your homes |
| `eliteessentials.command.home.limit.<n>` | Max homes (e.g., `.limit.5`) |
| `eliteessentials.command.home.limit.unlimited` | Unlimited homes |
| `eliteessentials.command.home.bypass.cooldown` | Bypass home cooldown |
| `eliteessentials.command.home.bypass.warmup` | Bypass home warmup |

### Teleport Commands
| Permission | Description |
|------------|-------------|
| `eliteessentials.command.tp.tpa` | Request teleport |
| `eliteessentials.command.tp.tpaccept` | Accept requests |
| `eliteessentials.command.tp.tpdeny` | Deny requests |
| `eliteessentials.command.tp.rtp` | Random teleport |
| `eliteessentials.command.tp.back` | Return to previous location |
| `eliteessentials.command.tp.back.ondeath` | Use /back after death |
| `eliteessentials.command.tp.bypass.cooldown` | Bypass all tp cooldowns |
| `eliteessentials.command.tp.bypass.warmup` | Bypass all tp warmups |

### Warp Commands
| Permission | Description |
|------------|-------------|
| `eliteessentials.command.warp.use` | Use /warp |
| `eliteessentials.command.warp.list` | List warps |
| `eliteessentials.command.warp.set` | Create warps |
| `eliteessentials.command.warp.delete` | Delete warps |
| `eliteessentials.command.warp.admin` | Warp administration |
| `eliteessentials.command.warp.<name>` | Access specific warp |

### Spawn Commands
| Permission | Description |
|------------|-------------|
| `eliteessentials.command.spawn.use` | Teleport to spawn |
| `eliteessentials.command.spawn.set` | Set spawn location |

### Admin
| Permission | Description |
|------------|-------------|
| `eliteessentials.admin.*` | Full admin access |
| `eliteessentials.admin.reload` | Reload configuration |
| `eliteessentials.command.misc.sleeppercent` | Set sleep percentage |

### Miscellaneous Commands
| Permission | Description |
|------------|-------------|
| `eliteessentials.command.misc.god` | Toggle god mode |
| `eliteessentials.command.misc.heal` | Heal to full health |
| `eliteessentials.command.misc.msg` | Private messaging (/msg, /reply) |
| `eliteessentials.command.misc.fly` | Toggle flight mode |
| `eliteessentials.command.misc.flyspeed` | Set fly speed (0-10) |
| `eliteessentials.command.tp.top` | Teleport to highest block |

### Kit Commands
| Permission | Description |
|------------|-------------|
| `eliteessentials.command.kit.use` | Open kit selection GUI |
| `eliteessentials.command.kit.<name>` | Access specific kit |
| `eliteessentials.command.kit.create` | Create new kits |
| `eliteessentials.command.kit.delete` | Delete kits |
| `eliteessentials.command.kit.bypass.cooldown` | Bypass kit cooldowns |

### Spawn Protection
| Permission | Description |
|------------|-------------|
| `eliteessentials.command.spawn.protection.bypass` | Bypass spawn protection |

## Wildcard Support

- `eliteessentials.*` - All permissions
- `eliteessentials.command.*` - All commands
- `eliteessentials.command.home.*` - All home commands + limits + bypass
- `eliteessentials.command.tp.*` - All teleport commands + bypass
- `eliteessentials.command.warp.*` - All warp commands + bypass

## Example Group Configurations (Advanced Mode)

### VIP Group
```
eliteessentials.command.home.limit.10
eliteessentials.command.home.bypass.cooldown
eliteessentials.command.tp.bypass.cooldown.rtp
```

### Moderator Group
```
eliteessentials.command.warp.set
eliteessentials.command.warp.delete
eliteessentials.command.home.bypass.*
eliteessentials.command.tp.bypass.*
```
