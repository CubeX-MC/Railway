# Railway Plugin

[中文](README.md) | English<br>
[Discord](https://discord.com/invite/7tJeSZPZgv) | [QQ Channel](https://pd.qq.com/s/1n3hpe4e7?b=9)

Railway is a Minecraft train operation plugin based on [Metro](https://github.com/CubeX-MC/Metro). Building upon the original station management, it introduces **Fixed Headway**, **Train Consists**, and **Automated Dispatching** systems, allowing players to experience realistic arrival, docking, and departure processes.

### Core Features
- **Based on Metro**: Inherits station management, UI prompts, sound systems, and multi-language support.
- **Folia Compatibility**: Untested.
- **Automated Dispatching**: Generates trains based on headway, supporting loop/single-direction operations.
- **Three Control Modes**: Kinematic, Reactive, Leashed.
- **Virtual Dispatching**: Local mode generates trains on demand to save server performance.
- **Station HUD**: Real-time countdown for next train, supporting Title/ActionBar/Hologram.
- **Permission Management**: Lines and stops support owner and admin systems.

### Configuration
The default configuration is located in `config.yml`. Future updates will extend `lines.yml` to support per-line `service.*` fields.

#### Core Configuration Items

**Service Configuration (service)**
```yaml
service:
  mode: local                      # Run mode: local (virtual timetable) | global (always-on entities)
  default_headway_seconds: 120     # Default headway (seconds)
  default_dwell_ticks: 100         # Default dwell time (ticks)
  default_train_cars: 3            # Default consist length (number of cars)
```

**General Settings (settings)**
```yaml
settings:
  default_language: en_US          # Default language (en_US / zh_CN)
  cart_speed: 0.4                  # Default cart speed (can be overridden by line max_speed)
  train_spacing: 1.6               # Spacing between cars in a consist (blocks)
  safe_speed_mode: true            # Automatic speed limit on curves/slopes (prevents derailment)
  
  control:
    default_mode: kinematic        # Global default control mode
    leash:                         # Leash mode configuration
      mob_type: ALLAY              # Leash holder mob type (ALLAY/FOX/WOLF etc.)
      update_interval_ticks: 1     # Leash holder update interval
      offset_y: 0.2                # Leash holder vertical offset
  
  selection:
    tool: GOLDEN_HOE               # Stop area selection tool
    
  interact:
    type: SUBTITLE                 # Display type for rail ETA: ACTIONBAR, TITLE, SUBTITLE
    stay_ticks: 60                 # Display duration in ticks
  
  chunk_loading:                   # Global mode chunk loading strategy
    enabled: true
    radius: 2                      # Loading radius around each car (chunks)
    forward_preload_radius: 1      # Forward preload radius
    only_when_moving: false        # Only load when moving
    update_interval_ticks: 10      # Refresh interval
```

**Travel Time Estimation (travel_time)**
```yaml
travel_time:
  enabled: true                    # Enable Bayesian estimation
  default_section_seconds: 20.0    # Default section travel time
  prior_strength: 4.0              # Prior strength (virtual sample count)
  outlier_sigma: 3.0               # Outlier filtering threshold (sigma)
  decay_per_day: 0.95              # Daily decay factor
  use_unboarded_samples: false     # Whether to use unboarded samples
  unboarded_weight: 0.2            # Unboarded sample weight
```

**Local Mode Configuration (local)**
```yaml
local:
  activation_radius: 96            # Player radius to trigger spawning (blocks)
  spawn_lead_ticks: 120            # Target arrival lead time (ticks)
  suppress_threshold_seconds: 10.0 # Suppression threshold if a train is arriving (seconds)
  virtualization:
    enabled: true                  # Enable virtualization
    idle_ticks: 200                # Time to wait before recycling demand-free trains
    lookahead_stops: 2             # Number of stops to check for demand
```

**Physics Engine Configuration (physics)**
```yaml
physics:
  lead_kinematic: true             # Lead car driven kinematically
  lookahead_blocks: 4              # Rail geometry lookahead blocks
  trail_seconds: 10                # Trail buffer duration (seconds)
  smoothing_lerp: 0.15             # Smoothing interpolation factor (0=instant, 0.3=smooth)
  snap_to_rail: true               # Snap to rail center
  strict_when_has_passenger: true  # Strict alignment when carrying passengers
  position_only_mode: false        # Position-only mode (cinematic/display use)
```

**Station UI Configuration (titles)**

Supports `stop_continuous` (Station HUD), `arrive_stop` (Arrival), `terminal_stop` (Terminus), `departure` (Journey Info), `waiting` (Waiting for departure) scenarios. See `config.yml` for full configuration.

**Sound Configuration (sounds)**

Supports `departure`, `arrival`, `station_arrival`, `waiting` sound sequences. See `config.yml` for full configuration.

**Scoreboard Configuration (scoreboard)**
```yaml
scoreboard:
  enabled: true                    # Enable scoreboard (Incompatible with Folia)
  styles:
    current_stop: "&f"             # Current stop style
    next_stop: "&a"                # Next stop style
    other_stops: "&7"              # Other stops style
  line_symbol: "●"                 # Line symbol
```

> Tip: The plugin will automatically populate missing keys in `config.yml` on startup (preserving your existing values). Language files will also automatically merge new entries. No manual config updates are needed for upgrades.
> Starting from this version, old `settings.consist.*` settings are deprecated and removed, consolidated into `physics.*`.

`lines.yml` example (Planned extension):

```yaml
red:
  name: Red Line
  color: "&c"
  ordered_stop_ids: [r1, r2, r3, r4]
  terminus_name: "To R4"
  max_speed: 0.6
  service:
    enabled: true
    headway_seconds: 90
    dwell_ticks: 100
    train_cars: 4
    direction_mode: bi_directional   # bi_directional | circular | single_direction
    first_departure_tick: 200
```

### Commands

**Basic Commands**
- `/rail` - Show main help menu
- `/rail reload` - Reload configuration and data, rebuild line services
- `/rail gui` - Open server-wide line GUI panel
- `/rail line status [lineId]` - View global or specific line status
- `/rail line list` - List all lines summary
- `/rail stop list` - List all stops configuration status

**Line Management** (`/rail line`)
- `/rail line` - Show line management help
- `/rail line create <id> <name>` - Create new line
- `/rail line delete <id>` - Delete line
- `/rail line list` - List all lines
- `/rail line stops <id>` - List all stops on a line (including stop point status)
- `/rail line control <id> <kinematic|leashed|reactive>` - Set control mode for line
- `/rail line rename <id> <name>` - Rename line
- `/rail line setcolor <id> <&code>` - Set line color (e.g. `&c` for red)
- `/rail line addstop <id> <stopId> [index]` - Add stop to line
- `/rail line delstop <id> <stopId>` - Remove stop from line
- `/rail line service <id> <enable|disable>` - Enable/Disable line service
- `/rail line setheadway <id> <seconds>` - Set headway
- `/rail line setdwell <id> <ticks>` - Set dwell time
- `/rail line settraincars <id> <count>` - Set consist length
- `/rail line setterminus <id> <name>` - Set terminus name
- `/rail line setmaxspeed <id> <speed>` - Set max speed
- `/rail line setowner <id> <uuid>` - Set line owner
- `/rail line trust <id> <uuid>` - Add administrator
- `/rail line untrust <id> <uuid>` - Remove administrator

**Stop Management** (`/rail stop`)
- `/rail stop` - Show stop management help
- `/rail stop create <id> [name]` - Create new stop
- `/rail stop delete <id>` - Delete stop
- `/rail stop list` - List all stops
- `/rail stop tp <id>` - Teleport to stop
- `/rail stop rename <id> <name>` - Rename stop
- `/rail stop setcorners <id>` - Set stop region (use Golden Hoe to select corners)
- `/rail stop setpoint [id] [yaw]` - Set stop point at current location (id optional if inside a stop, supports custom yaw)
- `/rail stop allowline <id> <lineId>` - Allow line to pass through stop
- `/rail stop denyline <id> <lineId>` - Deny line from passing through stop
- `/rail stop addtransfer <id> <lineId>` - Add transfer line
- `/rail stop deltransfer <id> <lineId>` - Remove transfer line
- `/rail stop settitle <id> <type> <field> <value>` - Set custom title
- `/rail stop deltitle <id> <type>` - Remove custom title
- `/rail stop setowner <id> <uuid>` - Set stop owner
- `/rail stop trust <id> <uuid>` - Add administrator
- `/rail stop untrust <id> <uuid>` - Remove administrator

All commands support Tab completion.

> All command feedback and prompts are integrated into the language system and will output text according to the player's selected language.

**Permissions**
- `railway.admin` - Use all admin commands (Default: OP)
- `railway.use` - Use basic plugin features (riding trains etc., Default: All players)
- `railway.line.create` - Allow players to create new lines (Default: false)
- `railway.stop.create` - Allow players to create new stops (Default: false)

Lines and stops support an owner and admin system. Permissions can managed via `setowner`, `trust`, and `untrust` commands. OPs and players with `railway.admin` permission can bypass ownership checks.

### Run Modes
- **local** (Default): "Virtual Timetable + Proximity Spawning". Generates train entities only near players, balancing immersion and performance. Supports virtualization recycling (automatically clears trains with no demand).
- **global**: Always-on entities running continuously based on headway. Supports moving window chunk loading to ensure trains run normally throughout the route.

### Control Modes
- **kinematic**: Lead car completely takes over movement. Followers strictly follow the trail. Stable on curves/slopes, keeps passengers synchronized. Recommended for high-speed lines and precise dispatching.
- **reactive**: Spacing control based on vanilla physics. Real-time correction to maintain consist. Balances vanilla feel and synchronization. Suitable for low-speed sightseeing lines.
- **leashed**: Visual connection via leashed mobs. Lightweight control, compatible with TrainCarts and other plugins. Suitable for displays and compatibility scenarios.

All control modes support line speed limits (`max_speed`), safe speed limits on curves/slopes, and strict passenger synchronization.

### Metro Migration Guide
Railway is directly compatible with Metro data files. Migration steps:
1. Install Railway plugin.
2. Delete `Metro/config.yml` (Recommended to use the new config generated by Railway).
3. Copy `Metro/lines.yml` and `Metro/stops.yml` to the `Railway/` directory.
4. Restart server.
5. Use `/rail line list` to confirm lines are loaded.

### Folia Support
Untested.

### Physics Reference
TrainCarts, Metro
