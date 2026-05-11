# Railway Migration Plan

## Fork Strategy

Railway is a fork of [Metro](https://github.com/CubeX-MC/Metro) (v1.1.6).

- **Package**: `org.cubexmc.metro` (identical to upstream — no rename)
- **Main class**: `org.cubexmc.metro.Metro` (same class name as upstream)
- **Plugin identity**: `plugin.yml` name=**Railway**, `pom.xml` artifactId=**railway**
- **Changes vs Metro**: Only 11 files differ (pom.xml, plugin.yml, 9 permission references)

### Sync with upstream

```bash
git fetch upstream
git checkout main
git merge upstream/main          # clean merge, ~zero conflicts
git checkout railway
git merge main                   # brings upstream changes into railway branch
```

### Branches

| Branch | Purpose |
|--------|---------|
| `main` | Clean Metro fork base — only plugin identity changes. Merge upstream here. |
| `railway` | Railway features stacked on `main`. All new code goes here. |

---

## Completed (Phase 0)

### Fork Base (`main` branch, commit `974f5d4`)
- pom.xml: artifactId=railway, base.name=railway, version=0.3.2
- plugin.yml: name=Railway, version=0.3.2, railway.* permissions
- 9 Java files: `metro.*` → `railway.*` permission string updates

### Railway Utilities (`railway` branch)
| File | Description |
|------|-------------|
| `control/TrainControlMode.java` | KINEMATIC / LEASHED / REACTIVE enum |
| `model/DirectionMode.java` | BI_DIRECTIONAL / CIRCULAR / SINGLE_DIRECTION enum |
| `util/LocationUtil.java` | Enhanced: RailType enum, rail physics helpers (curve detection, safe speed, rail snapping, rail direction) |
| `util/FaceUtil.java` | Block face utilities |
| `util/IntVector3.java` | Integer vector with chunk operations |
| `util/LineTopologyUtil.java` | Line topology analysis |
| `util/MathUtil.java` | Math helpers (lerp, clamping, angles) |
| `util/Quaternion.java` | Quaternion math for rotation |

---

## Pending: Physics & Train Layer (Phase 1)

The physics engine and train system form a tightly-coupled unit. All files below must be ported together
due to circular dependencies.

### Dependency Graph

```
Metro.java (main class — needs Railway config methods)
  └── Line.java (needs: headwaySeconds, dwellTicks, trainCars fields)
        └── LineService.java
              ├── LineServiceManager.java
              ├── DispatchStrategy.java
              │     ├── GlobalDispatchStrategy.java
              │     └── LocalDispatchStrategy.java
              └── TrainSpawner.java
                    └── TrainInstance.java
                          ├── TrainConsist.java          (multi-cart consist)
                          ├── TrainNavigator.java         (route navigation)
                          ├── TrainNavigatorDecisions.java
                          ├── TrainPassengerRegistry.java
                          ├── TrainRuntimeDecisions.java
                          ├── TrainStateMath.java
                          ├── PassengerExperience.java    (needs AdventureUtil)
                          ├── ConsistController.java      (cart coupling control)
                          ├── ArrivalHeuristics.java
                          └── TrainPhysicsEngine.java
                                ├── physics/KinematicRailPhysics.java     (19 files)
                                ├── physics/LeashedRailPhysics.java
                                ├── physics/ReactiveRailPhysics.java
                                ├── physics/LeashCoupler.java
                                ├── physics/TrainCartsBridge.java
                                └── physics/RailPathUtil.java
```

### Porting Steps

1. **Add Railway config fields to `Metro.java`**:
   - `isTravelTimeEnabled()`, `getDefaultSectionSeconds()`, `getPriorStrength()`, etc.
   - `getServiceHeartbeatIntervalTicks()`, `getConfigFacade()`

2. **Add Railway fields to `model/Line.java`**:
   - `headwaySeconds` (int) — headway-based dispatch interval
   - `dwellTicks` (int) — station dwell time
   - `trainCars` (int) — number of minecarts per consist

3. **Port service layer** (in order):
   - `service/DispatchStrategy.java` (interface)
   - `service/strategy/GlobalDispatchStrategy.java`
   - `service/strategy/LocalDispatchStrategy.java`
   - `service/LineServiceManager.java`
   - `service/TrainSpawner.java`
   - `service/LineService.java`
   - `service/BlockSectionManager.java`

4. **Port train models** (in order):
   - `train/TrainConsist.java`
   - `train/TrainNavigatorDecisions.java`
   - `train/TrainStateMath.java`
   - `train/TrainRuntimeDecisions.java`
   - `train/TrainNavigator.java`
   - `train/TrainPassengerRegistry.java`
   - `train/TrainInstance.java`
   - `train/ArrivalHeuristics.java`
   - `train/ConsistController.java`
   - `train/PassengerExperience.java`

5. **Port physics engine**:
   - `physics/TrainPhysicsEngine.java` (interface)
   - `physics/KinematicRailPhysics.java`
   - `physics/LeashedRailPhysics.java`
   - `physics/ReactiveRailPhysics.java`
   - All supporting physics files

6. **Port estimation**:
   - `estimation/TravelTimeEstimator.java`

### API Compatibility Notes

- `Bukkit.getCurrentTick()` (Paper 1.19+) — used in LineService.tick().
  Replace with reflection or a tick counter managed by SchedulerUtil.
- `player.sendActionBar(Component)` (Paper 1.18+) — used in PassengerExperience.
  Use Adventure Audience API or Spigot fallback in AdventureUtil.
- `player.clearTitle()` (Paper 1.18+) — use `player.resetTitle()` (Bukkit 1.17+).
- NMS-based `MinecartNmsUtil` — may need version-specific implementations.
  Wrap in reflection with Spigot 1.18+ fallback.

---

## Pending: Secondary Features (Phase 2)

| Module | Description | Priority |
|--------|-------------|----------|
| `placeholder/RailwayPlaceholders.java` | PlaceholderAPI expansion — needs `me.clip.placeholderapi` dependency in pom.xml | Medium |
| `gui/menu/` (7 files) | Menu-based GUI — alternative to Metro's controller/view pattern. Depends on Railway-specific GuiManager methods | Low |
| `storage/` (6 files) | YAML persistence — Metro uses `persistence/` package instead. Could be kept as alternative or removed | Low |
| `config/RailwayConfig.java` | Railway config facade — Metro uses ConfigFacade. Consolidate into one | Low |
| `model/EntityModelController.java` | Visual entity model replacement for minecarts — needs Paper API | Low |

---

## How to Continue

1. Checkout the railway branch:
   ```bash
   git checkout railway
   ```

2. Copy missing files from backup:
   ```bash
   # Backup of original Railway src/ is at:
   # C:\Users\Angus\AppData\Local\Temp\opencode\railway_backup\
   ```

3. Port files following the dependency order in Phase 1.

4. After each batch, compile and fix errors:
   ```bash
   mvn compile
   ```

5. Commit working batches:
   ```bash
   git add -A -- ':!target/' ':!nul'
   git commit -m "feat: ..."
   ```

6. When Metro upstream releases:
   ```bash
   git checkout main
   git fetch upstream
   git merge upstream/main
   git checkout railway
   git merge main
   ```

---

## Reference

- Upstream Metro: https://github.com/CubeX-MC/Metro
- Railway fork:  https://github.com/CubeX-MC/Railway
- Metro v1.1.6 commit: `0f20ac8`
