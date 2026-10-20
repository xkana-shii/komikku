# xkana-shii/komikku — personal fork of Komikku with the Komi-kun foundation

This repository is a **personal fork of [Komikku](https://github.com/komikku-app/komikku)** that
imports **[Komi-kun](https://github.com/komi-kun/komi-kun)**'s architecture and keeps its own app
identity (`app.komikku.kns`, published as Komikku) and release infrastructure. This file documents
the upstream topology, the provenance markers and the porting workflow. `AGENTS.md` is the mandatory
rulebook for code style, markers and i18n — read it first. How the foundation was assembled
(pinned SHAs, imported/skipped commits, conflict decisions, validation) is recorded in
[`docs/komikun-foundation-progress.md`](docs/komikun-foundation-progress.md).

## Upstreams

| Role | Remote | Branch | What we take |
|------|--------|--------|--------------|
| **Direct upstream** (Git foundation) | `komikku` (`komikku-app/komikku`) | `master` | Everything that does not conflict with Komi-kun. The foundation branch starts from a `komikku/master` commit. |
| **Additional upstream** (indirect) | `komikun` (`komi-kun/komi-kun`) | `master` | Its functional and architectural changes, cherry-picked with `-x`. **Komi-kun wins** where it conflicts with Komikku. Not its branding or release model. |
| Inherited upstream | `mihon` (`mihonapp/mihon`) | `main` | Usually arrives through Komi-kun/Komikku; ported directly on request. |
| Inherited upstream | `tachiyomiSY` (`jobobby04/TachiyomiSY`) | `master` | Usually arrives through Komi-kun/Komikku; ported directly on request. |
| This fork | `origin` (`xkana-shii/komikku`) | `main` (target, after replacement) | Personal features (`KNS`) on feature branches. The rebuilt foundation lives on `codex/komikun-foundation` until it replaces `main`; the old `main` is preserved as `backup/pre-komikun-foundation-20261020`. |

```bash
git remote add komikku https://github.com/komikku-app/komikku
git remote add komikun https://github.com/komi-kun/komi-kun
git remote add mihon https://github.com/mihonapp/mihon
git remote add tachiyomiSY https://github.com/jobobby04/TachiyomiSY
git fetch komikku komikun mihon tachiyomiSY
```

The upstreams are configured in `.agents/skills/cherrypick-log/config.json`; the
[`cherrypick-log`](.agents/skills/cherrypick-log/README.md) skill/script is the source of truth for
what has and has not been ported (`cherrypick_log.md`).

## Provenance markers

Markers record where code first diverged, so the tree can still be diffed against each upstream.

| Code origin | Marker |
|-------------|--------|
| This fork (xkana-shii/komikku) | `// KNS -->` … `// KNS <--` |
| Komi-kun | `// KUN -->` … `// KUN <--` |
| Komikku | `// KMK -->` … `// KMK <--` |
| TachiyomiSY | `// SY -->` … `// SY <--` (legacy `// EXH` also) |
| Mihon (base) | no block marker — keep the commit's `(cherry picked from commit <sha>)` trailer |
| Changes *we* make inside an inherited `KUN`/`KMK`/`SY`/`EXH` block | nest a `// KNS -->` … `// KNS <--` block inside it |

- `KMK`/`KUN` are **not** this fork's markers; never relabel imported code, never mass-rename markers.
- New strings go to `i18n-kmk/.../base/strings.xml` via `KMR` (no `KNSMR`, no `i18n-kns`). Weblate is
  not connected; translations arrive by porting upstream translation commits.
- Markers also apply inside `.sq` / `.sqm` (`-- KNS -->` / `-- KNS <--`) and XML (`<!-- KNS -->`).
- A pure cherry-pick needs no marker: the `-x` trailer is the provenance, and
  `update_cherrypick_log.py` detects it as `O`.

## Porting workflow

1. `python3 .agents/skills/cherrypick-log/update_cherrypick_log.py` — refresh the log.
2. Review `?` rows (and empty ones when asked) with the `cherrypick-log` skill; record verdicts with
   the script's `mark` command, never by hand.
3. Port on a **feature branch**, one sync per branch/PR: `git checkout -b chore/upstream-<what> main`
   (the foundation branch until it replaces `main`).
4. `git cherry-pick -x <sha>` in **chronological order**. Conflict policy:
   - **Komi-kun vs Komikku:** take Komi-kun's complete implementation for a genuinely conflicting
     source file (`git checkout <komikun-sha> -- <path>`), keep its deletions, keep nonconflicting
     Komikku changes, and put required compatibility fixes in a separate `KNS` commit.
   - **Mihon/TachiyomiSY:** adapt to this fork, never revert Komikku/Komi-kun features. Prefer the
     TachiyomiSY copy of a Mihon commit when one exists (`↳ tachiyomiSY` rows).
   - Repository identity, signing, workflows and the migration chain are never overwritten by an
     upstream file (see `AGENTS.md`).
5. After each batch: `./gradlew spotlessApply spotlessCheck :app:compileDebugKotlin`, the relevant unit
   tests, and `:data:verifyDebugDatabaseMigration` for SQL changes.
6. **Translation commits are ports too** — never `X`; resolve locale conflicts with
   `scripts/resolve_i18n_union.py` from the `upstream-port` skill.
7. SQL: append a **new** migration at the next number; never renumber. Komikku and Komi-kun numbered
   migrations diverge (e.g. both have a different `47.sqm`): reconcile by content, not by number.
8. After merging, rerun the generator so ported rows flip to `O`; record skipped commits as `X` with a
   reason.

## Foundation integration (Oct 2026)

The foundation branch was built from Komikku `master` `1656575e8f` plus Komi-kun `master`
`2c6743696d` (26 of its 31 non-merge commits picked with `-x`; branding, icon, nightly-release, MAL
OAuth client and its release-notes workflow commit not imported), followed by `KNS` commits for the
application identity, CI/release infrastructure, a migration fix for fresh-installed Komikku
databases (`49.sqm`) and documentation. Komikku's own commits after the shared base are all
represented by Komi-kun equivalents, except two MangaDex-cover reverts (`c10081a5d6`, `411523a2a0`)
that conflict with Komi-kun's `NetworkHelper` and stay pending. A follow-up batch picked Komi-kun
`eece71d0e3`, `a52730100f` and `6611a4dbb5` (category-tab restore, SyncYomi force-push repair, chapter
metadata restore) and skipped the OpenCode PR-review workflow `9853e52eb3`. Full record:
[`docs/komikun-foundation-progress.md`](docs/komikun-foundation-progress.md).

> The sections below are carried over from Komi-kun's `FORK.md`. "The fork" there means Komi-kun,
> whose code this repository now contains, so they describe this tree as well.

## Inherited from Komi-kun: followed Mihon re-architectures

Komi-kun follows Mihon on its internal re-architectures (adopted Oct 2026) and this fork inherits
them through the foundation integration. When porting, assume these are the current state, not
divergences:

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

## Inherited from Komi-kun: still divergent from Mihon (do not port blindly)

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

## Komi-kun history: the first big upstream sync (Oct 2026)

Komi-kun's squashed port `5d0a67227d` (imported here as the first foundation commit) contains:

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

## Known divergences / review notes (Komi-kun, plus this fork)

Accepted divergences, recorded during review of the first big upstream sync (Oct 2026):

- **Brand strings (this fork).** Komi-kun renames the app to "Komikun" inside upstream-owned i18n
  files and tracker User-Agents. xkana-shii/komikku ships as **Komikku** (`app.komikku.kns`), so
  foundation commit `chore(identity): keep the xkana-shii/komikku application identity` restores Komikku's exact strings where Komi-kun only renamed them
  and nests `// KNS` blocks where the code is Komi-kun's own. When syncing Komi-kun, keep the Komikku
  brand; don't re-import `Komikun` strings.
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
