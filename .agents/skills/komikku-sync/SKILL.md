---
name: komikku-sync
description: Sync xkana-shii/komikku forward from its direct upstream, Komikku (komikku-app/komikku) — list the Komikku commits the fork is behind on, decide which to pull in, merge/cherry-pick them on a feature branch, verify, and open a PR. Use when asked to update or merge Komikku, see how far behind Komikku the fork is, or bring Komikku changes into the fork. For Mihon/TachiyomiSY cherry-picks use the upstream-port skill instead.
---

# Syncing the Komikku base upstream

xkana-shii/komikku is a fork of **Komikku**: the foundation branch starts from a Komikku commit (see
`FORK.md`'s upstream table — remote `komikku`, `komikku-app/komikku`), with Komi-kun's commits and
the fork's own `KNS` work on top. "Syncing" means bringing newer Komikku commits in.

## 1. See what is new

```bash
git fetch komikku master
T=main   # the foundation branch (codex/komikun-foundation) until it replaces main
git rev-list --count $T..komikku/master        # how far behind
git log --oneline --no-merges $T..komikku/master
```

This graph query is the reliable source: a date filter would miss Komikku commits whose committer
date is old (a cherry-pick merged late).

## 2. Assess each commit

For every commit decide **already present** vs **worth pulling**:

```bash
git show <sha>
```

- Komi-kun **ports Mihon directly**, so a Komikku commit that cherry-picks a Mihon commit is often
  already present here (same change, adapted) — check the fork's file before porting.
- **Komi-kun wins:** where a Komikku change conflicts with Komi-kun's implementation (DI, ViewModels,
  database, reader, sync, …), keep Komi-kun's and record the Komikku commit as skipped/pending with a
  reason; port only what does not conflict, adapting it to Komi-kun's architecture.
- The tree uses **Metro DI**: Komikku's `AppModule.kt` / `*DomainModule.kt` do **not** exist
  here — the wiring lives in `mihon/app/di/AppBindings.kt` (+ `AppGraph.kt`) and
  `data/src/main/java/tachiyomi/data/DatabaseBindings.kt`. Adapt such commits instead of copying them.
- SQLDelight files (`*.sq`, `migrations/*.sqm`) carry SyncYomi deltas and Komi-kun's schema rework
  here, and Komikku's migration numbers do not match this chain (both have a different `47.sqm`) —
  reconcile by content into a **new** migration, never clobber or renumber.

## 3. Pull them in

```bash
git checkout -b chore/komikku-sync-<what> main
```

- Prefer cherry-picking with `-x` and adapting (see `upstream-port`); a `git merge komikku/master` is
  possible but every conflict must still follow the Komi-kun-wins rule, file by file.
- Markers (`AGENTS.md`): Komikku code is `// KMK`, Komi-kun's is `// KUN`, this fork's adaptations
  are `// KNS` (nest inside an inherited block when editing within one). Mihon base stays unmarked.

## 4. Verify + PR

```bash
./gradlew spotlessApply spotlessCheck
./gradlew :app:compileDebugKotlin          # or assembleDebug
./gradlew :data:verifyDebugDatabaseMigration   # if .sq / .sqm changed
```

Open a PR from the feature branch to `main` (**never** push `main` / `master`).

## 5. Keep the log in step

`cherrypick_log.md` tracks Komikku too (`.agents/skills/cherrypick-log/config.json` lists it as an
additional upstream with its own `since`). After a sync, advance Komikku's `since` in `config.json`
to the new base commit's date and re-run the generator so merged rows flip to `O`:

```bash
python3 .agents/skills/cherrypick-log/update_cherrypick_log.py
```
