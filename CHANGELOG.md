# Changelog

## 1.1.7 (unreleased)

- **Reload safety**: `/rw reload` now runs as named stages. If line, stop or
  portal changes cannot be written to disk first, the stages that re-read
  `lines.yml` / `stops.yml` / `portals.yml` are skipped instead of replacing
  those unsaved changes with the older file. A failing stage stops the reload,
  and the reply and the console name the stage that failed.
- **Saving**: a synchronous save that fails on every retry now leaves the store
  marked as unsaved (it used to count as saved), so it is retried later.
- **Language fallback**: a key missing from a server's language file now falls
  back to the copy bundled in the jar, then down the locale chain
  (`zh_CN` → `en_US`), instead of showing `Missing message: ...`.

- **Fare destination**: fares from a line with **no owner** used to be withdrawn
  and destroyed. The new `economy.account` names the server account they are
  paid into instead (player UUID, `name:<account>`, a player name, or
  `bank:<name>`), reusing the shared `cubex-economy` routing. Owned lines are
  unchanged - they still pay their owner. Config migrates to v3 on first start
  with the key empty, which is exactly the old behaviour.

- **Spatial**: fix `Range3D.contains` half-open interval bug (mismatch with
  Bukkit `BoundingBox`); remove `Range3D` dependency from `Stop.java`
- **Docs**: add JavaDoc to `Range3D`, `Point3D`, `Octree`; add `@since`
  annotations to all `MetroAPI` methods; configure `maven-javadoc-plugin`

## 1.1.6

- **Pricing**: add `PriceRule` with flat/distance/interval modes, time-based
  discounts; replace flat `ticketPrice` with rule-based `PriceService`
- **Line status**: add `NORMAL` / `MAINTENANCE` / `SUSPENDED` states with
  alternate-route suggestions on suspension
- **Public API**: introduce `MetroAPI.getInstance()` with snapshot records,
  ownership queries, line status, pricing, portal mutations
- **Commands**: `setprice` (flat/distance/interval/reset), `setstatus`,
  `priceinfo`
- **Events**: `LineStatusChangeEvent`
- **Train**: distance/interval fare settlement at station arrival
- **Map**: BlueMap 3D stop volumes, orthogonal routes, route sampling config;
  dynmap/squaremap improvements
- **Folia**: 26.1.2 compat, command/scoreboard fallbacks

## 1.1.5

- **Spatial**: introduce `Octree` + `Range3D` spatial index for O(log N) stop
  queries; use Bukkit `BoundingBox` for containment checks
- **Selection**: `SelectionManager` + selection tool (default golden shovel)
- **GUI**: line boarding choice GUI for stops served by multiple lines
- **Stop titles**: configurable `stop_continuous`, `arrive_stop`,
  `terminal_stop`, `departure` title/subtitle/actionbar
- **Commands**: `cloud` command framework migration complete
- **Portal**: admin permission checks, line-bound portal usage, link command
- **Protection**: rail break protection in safe mode
- **Lifecycle**: configurable async save coordinator

## 1.1.4

- **Route recording**: `RouteRecorder` with collinear point normalisation
- **Scheduling**: Folia-aware `SchedulerUtil` (region/entity/global dispatch)
- **Map integration**: Dynmap, BlueMap, Squaremap lifecycles
- **Scoreboard**: per-train in-game ETA display
- **Ownership**: stop/line/portal admin and permission model
- **Localisation**: `zh_CN`, `zh_TW`, `en_US`, `de_DE`, `es_ES`, `nl_NL`,
  `tr_TR`
- **Data migration**: auto-upgrade from 1.0.x schemas

## 1.0.10

- Fix minecart stopping at terminal stops
- Basic continuous title display
- Original command structure (pre-`cloud` framework)
