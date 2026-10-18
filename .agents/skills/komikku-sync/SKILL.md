---
name: komikku-sync
description: Sync komi-kun's master forward from its base upstream, Komikku (komikku-app/komikku) — list the Komikku commits the fork is behind on, decide which to pull in, merge/cherry-pick them on a feature branch, verify, and open a PR. Use when asked to update or merge Komikku, see how far behind Komikku the fork is, or bring Komikku changes into the fork. For Mihon/TachiyomiSY cherry-picks use the upstream-port skill instead.
---

# Syncing the Komikku base upstream

komi-kun is a vendoring fork of **Komikku**; its `master` *is* a Komikku commit (see `FORK.md`'s
upstream table — remote `komikku`, `komikku-app/komikku`). "Syncing" means moving `master` forward to
include newer Komikku commits, on top of which the fork's own work sits.

## 1. See what is new

```bash
git fetch komikku master
git rev-list --count master..komikku/master        # how far behind
git log --oneline --no-merges master..komikku/master
```

This graph query is the reliable source: a date filter would miss Komikku commits whose committer
date is old (a cherry-pick merged late).

## 2. Assess each commit

For every commit decide **already present** vs **worth pulling**:

```bash
git show <sha>
```

- The fork **ports Mihon directly**, so a Komikku commit that cherry-picks a Mihon commit is often
  already present here (same change, adapted) — check the fork's file before porting.
- The fork migrated to **Metro DI**: Komikku's `AppModule.kt` / `*DomainModule.kt` do **not** exist
  here — the wiring lives in `mihon/app/di/AppBindings.kt` (+ `AppGraph.kt`) and
  `data/src/main/java/tachiyomi/data/DatabaseBindings.kt`. Adapt such commits instead of copying them.
- SQLDelight files (`*.sq`, `migrations/*.sqm`) carry SyncYomi deltas here — reconcile, don't clobber.

## 3. Pull them in

```bash
git checkout -b chore/komikku-sync-<what> master
```

- Want everything? `git merge komikku/master` and resolve conflicts by adapting to the fork (the
  fork's own commits sit on top of the base, so conflicts are expected in shared files).
- Want only some? Cherry-pick those with `-x` and adapt (see `upstream-port`).
- Markers (`AGENTS.md`): Komikku code is `// KMK`; komi-kun's own deltas are `// KUN` (nest inside an
  inherited block when editing within one). Mihon base stays unmarked.

## 4. Verify + PR

```bash
./gradlew spotlessApply spotlessCheck
./gradlew :app:compileDebugKotlin          # or assembleDebug
./gradlew :data:verifyDebugDatabaseMigration   # if .sq / .sqm changed
```

Open a PR from the feature branch to `master` (**never** push `master`).

## 5. Keep the log in step

`cherrypick_log.md` tracks Komikku too (`.agents/skills/cherrypick-log/config.json` lists it as an
additional upstream with its own `since`). After a sync, advance Komikku's `since` in `config.json`
to the new base commit's date and re-run the generator so merged rows flip to `O`:

```bash
python3 .agents/skills/cherrypick-log/update_cherrypick_log.py
```
