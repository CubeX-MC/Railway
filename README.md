## Railway 插件

Railway 是基于 [Metro](https://github.com/CubexMC/Metro) 的 Minecraft 列车运营插件。在原有站台管理基础上引入**固定发车间隔（Headway）**、**多车编组（Train Consist）**和**自动化调度**系统，让玩家体验真实的进站、停靠、发车流程。

### 核心特性
- **基于 Metro**：继承站台管理、UI 提示、音效系统和多语言支持
- **Folia 兼容**：完全支持 Paper 和 Folia 服务器
- **自动化调度**：按发车间隔生成列车，支持环线/双向运行
- **三种控制模式**：运动学（Kinematic）、反应式（Reactive）、牵引绳（Leashed）
- **自研矿车物理**：车头运动学驱动，后车轨迹跟随，稳定通过弯道坡道
- **虚拟化调度**：Local 模式按需生成列车，节省服务器性能
- **站台 HUD**：实时显示下班车倒计时，支持 Title/ActionBar/Hologram
- **权限管理**：线路和站点支持所有者与管理员系统



### 配置
默认配置位于 `config.yml`，后续将扩展 `lines.yml` 以支持每线 service.* 字段。

#### 核心配置项

**调试选项 (debug)**
```yaml
debug:
  train_physics: false        # 列车物理调试日志（脱轨恢复、轨道检测、速度调整）
  consist_spacing: false      # 编组间距调试日志（输出每节车厢的间距和速度）
```

**服务配置 (service)**
```yaml
service:
  mode: local                      # 运行模式: local (虚拟时刻表) | global (全线实体)
  default_headway_seconds: 120     # 发车间隔默认值（秒）
  default_dwell_ticks: 100         # 停站时间默认值（tick）
  default_train_cars: 3            # 默认编组长度（车厢数）
```

**基础设置 (settings)**
```yaml
settings:
  default_language: zh_CN          # 默认语言（en_US / zh_CN）
  cart_speed: 0.4                  # 默认矿车速度（可被线路 max_speed 覆写）
  train_spacing: 1.6               # 编组内车辆间距（格）
  safe_speed_mode: true            # 弯道/坡道自动限速（防止脱轨）
  
  control:
    default_mode: kinematic        # 全局默认控制模式
    leash:                         # 牵引绳模式配置
      mob_type: ALLAY              # 拴绳端点生物类型（ALLAY/FOX/WOLF等）
      update_interval_ticks: 1     # 端点跟随刷新间隔
      offset_y: 0.2                # 端点垂直偏移
  
  selection:
    tool: GOLDEN_HOE               # 站台区域选择工具
  
  chunk_loading:                   # 全局模式区块加载策略
    enabled: true
    radius: 2                      # 每节车厢周围加载半径（区块）
    forward_preload_radius: 1      # 车头前方预加载半径
    only_when_moving: false        # 仅在列车行进时加载
    update_interval_ticks: 10      # 刷新周期
```

**行程时间估计 (travel_time)**
```yaml
travel_time:
  enabled: true                    # 启用贝叶斯估计
  default_section_seconds: 20.0    # 默认区间运行时间
  prior_strength: 4.0              # 先验强度（虚拟样本数）
  outlier_sigma: 3.0               # 异常值过滤阈值（倍标准差）
  decay_per_day: 0.95              # 每日衰减因子
  use_unboarded_samples: false     # 是否使用无人乘坐样本
  unboarded_weight: 0.2            # 无人样本权重
```

**Local 模式配置 (local)**
```yaml
local:
  activation_radius: 96            # 触发生成的玩家距离（格）
  spawn_lead_ticks: 120            # 目标到站提前量（tick）
  suppress_threshold_seconds: 10.0 # 载客列车到站抑制阈值（秒）
  virtualization:
    enabled: true                  # 启用虚拟化
    idle_ticks: 200                # 无需求后等待回收时间
    lookahead_stops: 2             # 需求检查前瞻站点数
```

**物理引擎配置 (physics)**
```yaml
physics:
  lead_kinematic: true             # 车头运动学驱动
  lookahead_blocks: 4              # 前瞻检查轨道方块数
  trail_seconds: 10                # 轨迹缓冲时长（秒）
  smoothing_lerp: 0.15             # 平滑插值系数（0=直接到位，0.3=平滑）
  snap_to_rail: true               # 吸附轨道中心
  strict_when_has_passenger: true  # 载客时严格对齐
  position_only_mode: false        # 纯位置模式（电影/展示用）
```

**站台 UI 配置 (titles)**

支持 `stop_continuous`（站台 HUD）、`arrive_stop`（到站提示）、`terminal_stop`（终点站）、`departure`（行程信息）、`waiting`（等待发车）等多种场景，详见 `config.yml` 完整配置。

**音效配置 (sounds)**

支持 `departure`（发车）、`arrival`（到站）、`station_arrival`（站台进站）、`waiting`（等待）等音效序列，详见 `config.yml` 完整配置。

**记分板配置 (scoreboard)**
```yaml
scoreboard:
  enabled: true                    # 启用记分板（Folia 不兼容）
  styles:
    current_stop: "&f"             # 当前站样式
    next_stop: "&a"                # 下一站样式
    other_stops: "&7"              # 其它站样式
  line_symbol: "●"                 # 线路标识符
```

> 提示：插件会在启动时自动把 `config.yml` 中缺失的键补齐（保留你已有数值），语言文件也会自动合并内置的新条目，升级版本不需要手动复制配置。
> 本版本起，旧的 `settings.consist.*` 已废弃并从配置中移除，统一使用 `physics.*`。

`lines.yml`（计划中的扩展）示例：

```yaml
red:
  name: Red Line
  color: "&c"
  ordered_stop_ids: [r1, r2, r3, r4]
  terminus_name: "往 R4"
  max_speed: 0.6
  service:
    enabled: true
    headway_seconds: 90
    dwell_ticks: 100
    train_cars: 4
    direction_mode: bi_directional   # bi_directional | circular | single_direction
    first_departure_tick: 200
```

### 指令

**基础命令**
- `/rail` - 显示主帮助菜单
- `/rail reload` - 重载配置和数据，并重建线路服务
- `/rail line status [lineId]` - 查看全局或指定线路的运行状态
- `/rail line list` - 列出所有线路简况
- `/rail stop list` - 列出所有站点配置状态

**线路管理** (`/rail line`)
- `/rail line` - 显示线路管理帮助
- `/rail line create <id> <name>` - 创建新线路
- `/rail line delete <id>` - 删除线路
- `/rail line list` - 列出所有线路
- `/rail line stops <id>` - 列出线路的所有站点（含停靠点配置状态）
- `/rail line control <id> <kinematic|leashed|reactive>` - 设置线路的列车控制模式
- `/rail line set-name <id> <name>` - 设置线路名称
- `/rail line set-color <id> <&code>` - 设置线路颜色（如 `&c` 红色）
- `/rail line add-stop <id> <stopId> [index]` - 添加站点到线路
- `/rail line remove-stop <id> <stopId>` - 从线路移除站点
- `/rail line service <id> <enable|disable>` - 启用/停用线路服务
- `/rail line set-headway <id> <seconds>` - 设置发车间隔
- `/rail line set-dwell <id> <ticks>` - 设置停站时间
- `/rail line set-train-cars <id> <count>` - 设置列车编组数
- `/rail line set-terminus <id> <name>` - 设置终点站名称
- `/rail line set-maxspeed <id> <speed>` - 设置最大速度
- `/rail line set-owner <id> <uuid>` - 设置线路所有者
- `/rail line add-admin <id> <uuid>` - 添加管理员
- `/rail line remove-admin <id> <uuid>` - 移除管理员

**站点管理** (`/rail stop`)
- `/rail stop` - 显示站点管理帮助
- `/rail stop create <id> [name]` - 创建新站点
- `/rail stop delete <id>` - 删除站点
- `/rail stop list` - 列出所有站点
- `/rail stop set-name <id> <name>` - 设置站点名称
- `/rail stop set-corners <id>` - 设置站点区域（使用金锄头选择两个角）
- `/rail stop set-point [id] [yaw]` - 设置当前位置为停靠点（可省略 id 自动匹配，支持自定义朝向）
- `/rail stop allow-line <id> <lineId>` - 允许线路经过此站点
- `/rail stop deny-line <id> <lineId>` - 禁止线路经过此站点
- `/rail stop add-transfer <id> <lineId>` - 添加换乘线路
- `/rail stop remove-transfer <id> <lineId>` - 移除换乘线路
- `/rail stop set-title <id> <type> <field> <value>` - 设置自定义标题
- `/rail stop remove-title <id> <type>` - 移除自定义标题
- `/rail stop set-owner <id> <uuid>` - 设置站点所有者
- `/rail stop add-admin <id> <uuid>` - 添加管理员
- `/rail stop remove-admin <id> <uuid>` - 移除管理员

所有命令均支持 Tab 自动补全。

> 全部指令反馈与提示已接入语言系统，可按玩家所选语言输出对应文本。

**权限节点**
- `railway.admin` - 使用所有管理命令（默认：OP）
- `railway.use` - 使用插件基础功能（乘坐列车等，默认：所有玩家）

线路和站点支持所有者 (owner) 与管理员 (admin) 系统，可通过 `set-owner`、`add-admin`、`remove-admin` 命令管理权限。OP 与拥有 `railway.admin` 权限的玩家可绕过所有权检查。

**使用示例**
```bash
# 1. 创建站点
/rail stop create s1 "中央车站"
/rail stop create s2 "东站"
/rail stop set-point s1  # 站在铁轨上执行，可省略 s1 自动匹配当前站点
/rail stop set-corners s1  # 使用金锄头右键选择区域两个角

# 2. 创建线路
/rail line create red "红线"
/rail line set-color red &c
/rail line add-stop red s1
/rail line add-stop red s2

# 3. 配置服务
/rail line set-headway red 120      # 120秒发车间隔
/rail line set-dwell red 100        # 100 ticks停站时间
/rail line set-train-cars red 3     # 3节车厢
/rail line set-maxspeed red 0.6     # 最大速度 0.6
/rail line control red kinematic    # 设置控制模式为运动学
/rail line service red enable       # 启动服务

# 4. 查看状态与详情
/rail line status red               # 查看红线状态
/rail line stops red                # 查看红线站点列表
/rail line list                     # 列出所有线路
/rail stop list                     # 列出所有站点
```

### 运行模式
- **local**（默认）："虚拟时刻表 + 就近实体化"，仅在玩家附近生成列车实体，兼顾沉浸与性能。支持虚拟化回收（无需求时自动清理列车）。
- **global**：全线常驻实体，按 headway 持续运行。支持移动窗口区块加载，保证列车在全程正常运行。

### 控制模式
- **kinematic**（运动学）：车头完全接管运动，后车按轨迹严格跟随，稳定通过弯道/坡道，载客不掉队。推荐用于高速线路和精准调度。
- **reactive**（反应式）：基于原版物理的间距控制，实时纠偏保持编组，兼顾原版手感与同步性。适合低速观光线路。
- **leashed**（牵引绳）：通过拴绳生物实现视觉连接，轻量控制，可与 TrainCarts 等插件互操作。适合展示和兼容性场景。

所有控制模式均支持线路限速（`max_speed`）、弯道/坡道安全限速、载客严格同步等特性。

### 迁移与改动说明（本次）
- 移除 `settings.consist.*` 全部项目，统一由 `physics.*` 控制。
- 新增 `physics/TrainPhysicsEngine` 接口、`physics/KinematicRailPhysics` 默认实现。
- `TrainInstance` 现在优先调用物理引擎驱动列车，每 tick 接管矿车位置/速度；到站/清理时同步回调。
- 轨道安全限速（弯道/坡道）仍保留，作为运动学驱动的上限速度。
- 载客车厢每 tick 严格对齐（避免摩擦导致掉队），传送/对齐均吸附轨心。

### Folia 兼容说明
尚未测试


