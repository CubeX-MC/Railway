# Contributing to Railway

Railway lives in the CubeX-Plugins monorepo. Start with the root `AGENTS.md`: it lists the build
commands, the hard constraints (embedded packaging, no inter-plugin compile dependencies), and the
decisions not to "fix" — including that Railway intentionally keeps the `org.cubexmc.metro` package.

## Local Setup

- Use Java 17.
- Build from the repository root. On Windows use PowerShell (the path contains spaces):
  - `.\gradlew.bat :Railway:build` — compile, test, and build the deployable jar.
  - `.\gradlew.bat :Railway:test --tests "..."` — run a filtered subset of tests.
  - `.\gradlew.bat :Railway:jarGate` — check the deployable jar.

## Branch and PR Rules

- Keep each PR focused on one concern (tests, refactor, bugfix, docs).
- Prefer small, reviewable commits scoped to `Railway/`.
- Do not mix refactors with gameplay, config, or wording changes.
- Include a short test plan in each PR description.

## Required Checks

- CI must pass on `main` and on your branch.
- For runtime-impacting changes, run manual checks from `docs/regression-baseline.md`.

## Coding Conventions

- Follow the root `KOTLIN_STYLE_GUIDE.md` and `COMMAND_PERMISSION_GUIDE.md`.
- Shared helpers live in `modules/cubex-*`; if one is missing a capability, extend the module
  instead of adding a local copy (see `docs/architecture.md`).
- Keep command handlers small and route-only in entry classes.
- Add defensive null checks on config-driven paths and log actionable warnings.

## Release Preparation

- Follow `docs/release-checklist.md`.
- Ensure language keys remain consistent across locale files.
- Update `CHANGELOG.md` for user-visible changes.
