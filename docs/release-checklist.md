# Railway Release Checklist

## Pre-release

- [ ] `.\gradlew.bat :Railway:build` passes (compile, tests, shadowJar).
- [ ] `.\gradlew.bat :Railway:jarGate` passes.
- [ ] `build/libs/railway-<version>.jar` is the artifact to ship (never the `*-plain.jar`).
- [ ] GitHub Actions CI workflow is green on the release branch.
- [ ] Manual baseline checklist completed (`docs/regression-baseline.md`).
- [ ] Regression world scenarios A-F from `docs/regression-baseline.md` are present or intentionally skipped with notes.
- [ ] Language keys verified for `zh_CN`, `zh_TW`, `en_US`, `de_DE`, `es_ES`, `nl_NL`, `tr_TR`.
- [ ] `plugin.yml` version and command/permission descriptions are accurate.
- [ ] `plugin.yml` permissions (`railway.*`) match the README / Wiki permission tables.
- [ ] Default `config.yml` keys match the paths read by `ConfigFacade` and `Metro`.
- [ ] Compatibility notes reviewed against `docs/compatibility.md`.

## Runtime Validation

- [ ] Boarding, departure, arrival, terminal flows validated.
- [ ] Line service dispatch validated in the configured `service.mode` (`local` / `global`).
- [ ] Multi-line boarding choice and transfer hub display validated.
- [ ] Route recording, route protection, and portal ride scenarios validated.
- [ ] Enabled map provider renders route lines, stop markers, transfer details, line width, and legacy/hex line colors.
- [ ] GUI and command teleport permissions are consistent.
- [ ] `/rw reload` validated: defaults merged, success message shown, and a failing stage is named in the reply and console.
- [ ] Data migration backup files (`*.bak-<schema_version>`) are created when sample legacy data is migrated.
- [ ] No severe errors in server log during ride lifecycle.

## Packaging

- [ ] Changelog includes behavior changes and migration notes.
- [ ] Release notes are drafted from `docs/release-notes-template.md`.
- [ ] Rollback instructions documented for operators.
