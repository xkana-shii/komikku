# komi-kun — personal fork of Komikku

This repository is a **personal vendoring fork of [Komikku](../AGENTS.md)**. It intends to stay a
superset of Komikku while also tracking two further upstreams. This file documents the upstreams,
the provenance markers, and the workflow that keeps future porting cheap. `AGENTS.md` is still the
mandatory rulebook for code style, markers and i18n — read it first; this file only adds the fork's
upstream topology.

## Upstreams

| Role | Remote | Branch | What we take |
|------|--------|--------|--------------|
| Base upstream (the fork's parent) | `komikku` (`komikku-app/komikku`) | `master` | Everything. Our `master` is a Komikku commit; fast-forward it from `komikku/master` regularly. |
| This fork | `origin` (`komi-kun/komi-kun`) | `master` | Our own work lives on feature branches, not on `master`. |
| Additional upstream (primary) | `mihon` (`mihonapp/mihon`) | `main` | Bug fixes and features, cherry-picked individually. |
| Additional upstream (secondary) | `tachiyomiSY` (`jobobby04/TachiyomiSY`) | `master` | Sync and SY features the fork wants, cherry-picked individually. |

```bash
git fetch komikku mihon tachiyomiSY
```

The upstreams are configured in `.agents/skills/cherrypick-log/config.json`; the
[`cherrypick-log`](.agents/skills/cherrypick-log/README.md) skill/script is the source of truth for
what has and has not been ported (`cherrypick_log.md`).

## Provenance markers

Markers are how we can still diff our tree against an upstream later. Markers record **divergence from
upstream** — they never mark upstream code itself. Rules (extending `AGENTS.md`):

| Code origin | Marker |
|-------------|--------|
| This fork (komi-kun) — our own code | `// KUN -->` … `// KUN <--` |
| Komikku upstream | `// KMK -->` … `// KMK <--` |
| TachiyomiSY upstream | `// SY -->` … `// SY <--` (legacy `// EXH` also) |
| Mihon upstream (base) | no block marker — keep the commit's `(cherry picked from commit <sha>)` trailer |
| Changes *we* make inside an inherited `// KMK` / `// SY` / `// EXH` block | nest a `// KUN -->` … `// KUN <--` block inside it |

- `// KMK` is **not** this fork's marker: it means the code came from the **Komikku** upstream (this
  repo's base). komi-kun's own code uses `// KUN`.
- New strings go to `i18n-kmk/.../base/strings.xml` and are referenced via `KMR` (`AGENTS.md`).
  Weblate is **not** connected to this repo, so non-`base` locales are maintained here: translation
  updates arrive by porting upstream translation commits, and new strings ship with best-effort
  translations instead of waiting for a translation service.
- Markers also apply inside `.sq` / `.sqm` files (use `-- KUN -->` / `-- KUN <--`).
- A pure cherry-pick needs no marker: the trailing `(cherry picked from commit <sha>)` is the
  provenance, and `update_cherrypick_log.py` detects it as `O`.

## Porting workflow (Mihon / TachiyomiSY → this fork)

1. `python3 .agents/skills/cherrypick-log/update_cherrypick_log.py` — refresh the log.
2. Review the `?` rows (and, when asked, the empty ones) with the `cherrypick-log` skill; mark
   verdicts through the script (`mark`), never by hand.
3. Port on a **feature branch**, one upstream sync per branch/PR:
   `git checkout -b chore/upstream-<what> master`.
4. Prefer `git cherry-pick -x <sha>` in **chronological order** — earlier commits in a series often
   unblock later ones. Resolve conflicts by adapting to this fork, never by reverting Komikku
   features. When a port needs local changes, keep the `(cherry picked from …)` trailer and wrap the
   local delta in `// KUN` markers.
5. **Prefer the TachiyomiSY copy of a Mihon commit** when one exists (the log shows them as a
   `↳ tachiyomiSY` row): Komikku descends from SY, so the SY-adapted version conflicts far less.
6. After each batch: `./gradlew spotlessApply spotlessCheck :app:compileDebugKotlin`, then the unit
   tests (`:app:testDebugUnitTest`, `:domain:testDebugUnitTest`) where relevant.
7. **Translation commits are ports too.** “Translations update from Hosted Weblate” commits are never
   skipped (`X`): Weblate is not connected to this repo, so they are the only source of refreshed
   translations. Resolve locale-file conflicts with `scripts/resolve_i18n_union.py` from the
   `upstream-port` skill (upstream wins per key, fork-only translations are re-appended), then
   validate the XML and that no base key lost its translation.
8. For SQL changes append a **new** migration at the next number
   (`data/src/main/sqldelight/tachiyomi/migrations/<n+1>.sqm`); never renumber or edit an existing
   migration. Reconcile the incoming triggers/columns with what Komikku already has — some may
   already exist.
9. After the PR is merged into `master`, run the generator again: it finds the ported commits by
   their cherry-pick trailer and marks them `O`.
10. Push the feature branch and open a PR; never push to `master`.

## Followed Mihon re-architectures

The fork now follows Mihon on its internal re-architectures (adopted Oct 2026). When porting,
assume these are the current state, not divergences:

- **Metro DI** (`dev.zacsweers.metro` + `metrox-viewmodel`) replacing Injekt, with the `AppGraph`
  dependency graph and `AppBindings`/`DatabaseBindings` binding containers.
- **Preference getters are properties** (`val`, not `fun`) — ported from Mihon #3091 / SY #3091.
- **Jetpack AndroidX ViewModel** — screens use `ViewModel`; Voyager is kept for navigation only.
- **`WebGPU` renderer (`ca.mpreg:webgpuviewer`)** plus the **mpreg `ca.mpreg:imagedecoder`** and
  progressive rendering; SSIV remains for the non-high-quality modes. Mihon's **v0.21.0 release**
  reverted it (`f45d0ad31d`, release-tag only — it was experimental and unstable, with ~17 follow-up
  fixes), but we **follow `mihon/main`** and keep it. The choice stays user-facing: `Settings →
  Advanced → Use high quality renderer` (`basePreferences.highQualityRenderer`, default off) switches
  WebGPU ↔ SSIV, with an automatic SSIV fallback when `WebGpuRenderer.isAvailable` is false, plus the
  KMK `alwaysDecodeLongStripWithSSIV` override for long strip.
- **Material Symbols + Valkyrie** generated icons (`icons/material-symbols`, …);
  `material-icons-extended` is gone.
- **Apollo GraphQL** trackers (AniList, Kitsu, Shikimori, Suwayomi).
- **`com.eygraber:sqldelight-androidx-driver`** with SQLDelight `generateAsync`; the old
  `AndroidDatabaseHandler`/`DatabaseHandler` transaction helpers are gone — use
  `Database.transaction`/`transactionWithResult` + `await*`.
- **Database schema rework** — singular table/column names (`manga`, `chapter`, `category`,
  `manga_track`, `remote_*`, `user_*`), `favorite` merged into `favorite_at`, rebuilt views. The
  migrations live from `49.sqm` and **preserve the SyncYomi columns/triggers and Komikku's extra
  tables**; there is a migration test (`data/src/test/.../MigrationTest.kt`) and
  `:data:verifyDebugDatabaseMigration` gates it.
- **Raw database access is restricted to `:data`** (`RestoreRepositoryImpl`, `DatabaseBindings`).
- **`:source-api`/`:source-local` are Android library modules** (no longer KMP).
- **Gradle isolated projects is enabled** (`org.gradle.isolated-projects=true`) on the coherent
  AGP 9.4.1 / Gradle 9.8.1 / Kotlin 2.4.21 toolchain, matching `mihon/main`. This is what removed the
  moko-resources `findProperty` blocker, the compileSdk-37 AAR-metadata excludes and the
  whole-configuration Compose force (they were only needed on AGP 8.13.2).
- **Mihon's extension-store model** (`Extension.Loaded`/`NotLoaded`, `signatures`, `store`).
- **`kotlinx-collections-immutable` is dropped** — use stdlib collections.
- `MangaViewModel` collects the entry only while subscribed.
- compileSdk 37.2, aboutLibraries v15.

## Still divergent (do not port blindly)

- **Injekt is not fully removed.** Mihon keeps Injekt in ~32 files; this fork keeps it in ~51
  Komikku/SY/KMK files (extension-facing sources, the SyncYomi/connections stacks, abstract
  services, static objects, and `open` ViewModels with fork subclasses) bridged from the Metro graph
  via `mihon/app/di/injekt/KMKMetroInteropModule.kt`. Converting those is a redesign, not a
  mechanical move. New code should prefer Metro constructor injection.
- **Metro interop follow-ups**: `1c43addeca` ("Resolve Injekt dependencies directly from the Metro
  graph") is adapted locally in `0f0c72f0ef`. `d435dd9217` ("Switch to upstream Injekt") is **now
  ported** — the fork depends on upstream `uy.kohesive.injekt:injekt-core` instead of the
  `com.github.mihonapp:injekt` fork — while the remaining Injekt consumers are still bridged from the
  Metro graph by the fork's own `KMKMetroInteropModule`.
- `c67a33f3d4` ("Remove unused sync scaffold") is not ported — SyncYomi v2 uses
  `version`/`is_syncing`/`last_modified_at`.

When a Mihon commit exists only as part of one of these, mark it `X` (won't pick) with a note, or
leave it for the maintainer.

### Source manager surface (Mihon `5fadea61e7`, #3869)

Mihon `5fadea61e7` ("Make the source manager surface suspend") is **ported**. `SourceManager`
`get`/`getOrStub`/`getAll`/`getOnlineSources`/`getStubSources` (and the fork's `getVisible*`) are
`suspend`, `AndroidSourceManager` keeps `sourcesMapFlow` null until extensions load and awaits it in
`get`/`getOrStub`/etc.; `ExtensionManager.getExtensionPackage`/`getAppIconForSource` and
`Source.icon()` are `suspend`; `GetIncognitoState.await` is `suspend`; the
`installed`/`uninstalled`-extension flows use `mapExtensionsWhenInitialized`; and `ifSourcesLoaded()`
is deleted in favour of null-gated state.

Residual fork-only divergence:

- **`Source.installedExtension`** stays a synchronous accessor over the loaded-extensions snapshot.
  Turning it into an awaited path would cascade into many Compose call sites with no clear benefit;
  it is only read after the extensions/library have loaded.
- **`runBlocking` at genuinely synchronous surfaces.** These have no coroutine scope and are
  commented at their call sites: `SearchableSettings.isEnabled()` (called during settings
  composition), the `MdList` tracker and the `SuwayomiApi`/`Kavita` OkHttp interceptor threads, the
  `exh` debug helpers, and the merged-settings Android `ViewHolder`/`ArrayAdapter` bindings.
- **`EHentaiPagingSource` was folded into `BaseSourcePagingSource`.** The base now takes Mihon's
  `suspend () -> Source` factory and resolves the source inside `load()`, and its
  `getPageLoadResult` handles `MetadataMangasPage` (using its explicit `nextKey` when present) so
  the separate EHentai variants are no longer needed.

## Status of the first big upstream sync (Oct 2026)

The branch that introduced this file ports:

- **TachiyomiSY PR #1648** — SyncYomi protocol v2 with v1 fallback — plus its related fixes
  (PRs #1641/#1650 assessed) and the SY category-uid prerequisite (SY #1559).
- **Mihon backlog 2026-06 → 2026-10**: the portable fixes/features (TachiyomiSY's copies used where
  available), **and** the re-architectures listed under "Followed Mihon re-architectures" above,
  including the backfilled prerequisites (preference properties #3091, `:source-api`/`:source-local`
  Android modules, Metro DI, the database schema rework, Gradle isolated projects (deferred)).

The items under "Still divergent" remain in `cherrypick_log.md` for dedicated follow-ups.

### Provenance caveats (SyncYomi v2)

- SyncYomi v2 (`ccaa248bd7`) was ported from the then-**unmerged** `tachiyomiSY/pr-1648` (plus the
  `tachiyomiSY/pr-1658` chapter-tombstone branch). That PR has since **squash-merged as `380496a5be`**
  on SY `master`, so the individual branch commits the fork cites (`2d208b7431`, `2dfae69961`,
  `41574e8235`, `397c478394`, `c2d2ee6af5`) now live only behind the stale PR refs. Re-diff the
  series against `380496a5be` before the next TachiyomiSY re-sync, then stop tracking the PR branches.
- `3b88dd7ca9`'s `(cherry picked from commit 4496c9db1c…)` trailer pointed at a **non-existent
  object**, and `cd3987a228` carried **no trailer**. Both were fixed on 2026-10-14: they now cite the
  real SY sources (`41574e8235` and `e96895345e`), so the SyncYomi-v2 series is attributable again.
  The `15c4b5beef` port of TachiyomiSY #1641 cites the merged squash `148496e754` (its branch commit
  `4e8e01d4f4` was superseded when #1641 landed).

## Known divergences / review notes

Accepted divergences, recorded during review of the first big upstream sync (Oct 2026):

- **Brand strings live inside upstream-owned i18n files.** The app-name strings are edited in
  `i18n/src/commonMain/moko-resources/base/strings.xml` and
  `i18n-sy/src/commonMain/moko-resources/base/strings.xml` (name → Komikun), i.e. inside modules
  `AGENTS.md` marks as upstream-owned. The edits are `// KUN`-wrapped and are an intentional
  divergence, not a sync mistake — so default to keeping the Komikun brand when resolving conflicts
  there. But treat that as a default, not a hard rule: if an incoming upstream change supplants or
  improves on the fork's branding (a rebrand, a better string/plumbing mechanism, a translation
  fix), adopt the upstream version and drop or adapt the local edit instead of preserving the fork
  string blindly.
- **The Mihon copy was preferred over the TachiyomiSY copy in ~57 cases.** The porting workflow
  above says to prefer the SY copy when one exists, but this sync (and earlier history, ~60 commits
  in total) often took the Mihon copy instead. This is a known, accepted deviation: it increases
  future conflict surface but is not a functional break. **Audited 2026-10-14:** the SY copies of
  those commits contain no SY-only fix or behaviour that this fork is missing — every difference is
  SY architecture adaptation (SY's extra fields/features) that the fork's adapted ports already
  preserve, so there is nothing to back-port.
- **Backups are one-way compatible.** `3b88dd7ca9` adds proto3 defaults to fields that previously
  had none; with `encodeDefaults = false`, values equal to a default are omitted from the payload, so
  a backup written by this build can throw `MissingFieldException` in an older build (surfaced as
  `invalid_backup_file_unknown`, aborting the restore). Old backup → this build is fine. This is
  documented, not reverted; only the old → new direction is supported.
- **Ports with prose provenance instead of a cherry-pick trailer — resolved 2026-10-14.** The Mihon
  ports (`771ba33a33`, `ea2b73ad78`, `db45dda525`, `a4abb6292b`, `f8fff318ba`, `5fadea61e7`) and the
  SY sync ports (`e96895345e`, `397c478394`) now carry the standard
  `(cherry picked from commit <sha>)` trailer, so `update_cherrypick_log.py` detects them
  automatically. Adding the trailers rewrote those commit messages, so the branch SHAs changed.
  The only remaining prose-only port is `387ea9a5b5`, which merges two SY PRs (TachiyomiSY #1559 /
  #1648) and therefore has no single upstream commit to cite.
