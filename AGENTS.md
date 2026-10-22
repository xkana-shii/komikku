# Komikku (KNS fork) – AI Agent Guide

**xkana-shii/komikku** is a personal fork of **Komikku** (`komikku-app/komikku`), an Android manga
reader (min SDK 26, target SDK 36, compileSdk 37.2, JVM target 17 / Kotlin) derived from **Mihon** and
**TachiyomiSY**. The app is published as **Komikku** with `applicationId` **`app.komikku.kns`**
(debug `app.komikku.kns.dev`, nightly `.beta`), not Komikku's `app.komikku` and not Komi-kun's
`app.komikun`.

- **Official Komikku is the direct upstream** and the Git foundation: the rebuilt branch starts from
  a `komikku/master` commit.
- **Komi-kun** (`komi-kun/komi-kun`) is an **additional, indirect upstream**. Its architecture (Metro
  DI, AndroidX ViewModels, the database rework, SyncYomi v2, the WebGPU reader, …) is imported with
  `git cherry-pick -x`; it is not the Git parent.
- **Mihon** and **TachiyomiSY** are inherited upstreams (reachable through Komikku and Komi-kun, and
  ported directly on request).
- Personal features are implemented natively on this foundation with the **`KNS`** marker. The
  previous personal `main` is kept only as a backup branch; its implementations are not reused.

`FORK.md` documents the upstream topology, the cherry-pick workflow and the inherited architecture;
`docs/komikun-foundation-progress.md` records how this foundation was built. `features.md` is the
functional specification of personal features to (re)implement on request — it does **not** describe
code that exists.

Features: configurable reader, downloads/offline reading, trackers (MyAnimeList, AniList, Kitsu,
MangaUpdates, Bangumi, Comick, Hikka, Kavita, Komga, MangaBaka, MangaDex, Shikimori, Suwayomi),
recommendations, metadata editing, library categories/tags/filters, multi-source browsing and feed
tabs, SyncYomi/Google Drive/WebDAV sync.

### Upstreams

| Origin / relationship | Code marker | Strings |
|-----------------------|-------------|---------|
| Mihon (inherited upstream) | none (base code) | `MR` (`i18n/`) |
| TachiyomiSY (inherited upstream) | `// SY -->` … `// SY <--` (legacy `// EXH` also belongs to SY) | `SYMR` (`i18n-sy/`) |
| **Official Komikku (direct upstream)** | `// KMK -->` … `// KMK <--` | `KMR` (`i18n-kmk/`) |
| **Komi-kun (additional, indirect upstream)** | `// KUN -->` … `// KUN <--` | `KMR` (`i18n-kmk/`) |
| **xkana-shii/komikku (this fork)** | `// KNS -->` … `// KNS <--` | **`KMR`** (`i18n-kmk/`) |

Markers record **code provenance** — which project first diverged the code — not the repository the
code currently lives in. Keep `SY`, `EXH`, `KMK` and `KUN` markers with their imported code; only
changes authored **by this fork** use `KNS`. There is no `KNSMR` class and no `i18n-kns` module.

---

## Mandatory rules for AI agents

**Read this section before every change.** These rules override shortcuts (e.g. copying nearby `MR`
imports or only running `compileDebugKotlin`).

### Keep this guide current

`AGENTS.md` documents the **checked-out branch** (the rebuilt foundation is the future `main`). Update
this file in the same change whenever architecture, DI, module layout, markers, i18n, build flags,
workflows or key paths change. Do not claim that a feature or migration exists unless it exists on
the branch; delete stale guidance instead of carrying it forward.

### Git

| Rule | Required behavior |
|------|-------------------|
| Branch | Create a **feature branch** for the task (`git checkout -b <type>/<short-description>`, e.g. `feat/colored-bookmarks`), based on the rebuilt foundation (`codex/komikun-foundation` until it replaces `main`). |
| Commit | **OK** on a feature branch when work is ready. **Never** commit directly to `main` / `master` unless the user explicitly asks. |
| Push | **OK** to push the **current feature branch**. **Never** push to `main` / `master` unless the user explicitly asks. Before `git push`, confirm `git branch --show-current`. |
| Merge | Prefer an audited squash merge for ordinary feature PRs. **Never merge the old personal `main`** (backup: `backup/pre-komikun-foundation-20261020`) into the rebuilt branch. |
| History | Do not rewrite or force-push shared history without explicit approval. Replacing `main` with the foundation requires explicit approval and a verified backup; update `main` to the foundation's exact commit, never by merging the old `main`. |

### Fork markers

Every new addition or change authored for `xkana-shii/komikku` **must** be marked:

```kotlin
// KNS -->
// new or modified fork-specific code
// KNS <--
```

| Code origin | Required marker |
|-------------|-----------------|
| **This fork (`xkana-shii/komikku`)** | `// KNS -->` … `// KNS <--` |
| **Komi-kun** | `// KUN -->` … `// KUN <--` |
| **Official Komikku** | `// KMK -->` … `// KMK <--` |
| TachiyomiSY | `// SY -->` … `// SY <--` (also legacy `// EXH`) |
| Mihon base code | none |

**Hard rules:**

- Use `KNS` for this fork's new code, adaptations and modifications. Never use `KMK` or `KUN` for code
  first authored here, and never label code `SY`/`KMK`/`KUN` that did not come from that project.
- **Never mass-rename markers** (`KMK` → `KUN`, `KUN` → `KNS`, …). Preserve inherited markers during
  cherry-picks and file moves.
- When changing code inside an inherited `KUN`/`KMK`/`SY`/`EXH` block, keep the original block and
  nest a `KNS` block for the fork-specific delta.
- `.sq` / `.sqm` use SQL comments: `-- KNS -->` / `-- KNS <--`; XML uses `<!-- KNS -->`.
- If a change cannot be enclosed sensibly (one-line annotation, whole-file rewrite), record the
  provenance with a nearby `KNS` comment or in the commit message rather than inventing attribution.
- A clean cherry-pick keeps its source markers and its `(cherry picked from commit …)` trailer; only
  the locally authored adaptation is `KNS`.

### Upstream priority

- When official Komikku and Komi-kun implement the same thing incompatibly, **choose Komi-kun's
  implementation**, even when Komikku's is newer. Never build a hybrid of two DI, ViewModel or
  database architectures.
- `git cherry-pick -X theirs` favours incoming *hunks*, not the whole Komi-kun file. For a genuinely
  conflicting source file, check out the complete file from the Komi-kun commit being picked
  (`git checkout <sha> -- <path>`) and verify the result against `komikun/master`.
- Keep nonconflicting Komikku changes. Apply mandatory compatibility fixes afterwards as separate
  `KNS` commits.
- Repository identity (application id, version line, app name), signing, workflows/secrets and the
  database migration chain are exceptions: source precedence must not overwrite them (see
  [Build & CI](#build--ci) and [Database](#architecture)).
- Do not import Komi-kun branding (`Komikun`, `app.komikun`, its icon, its MyAnimeList OAuth client,
  its nightly-only release model) or restore code from the old personal `main`.

### Internationalization (strings)

| String origin | Module | Resource class | New base strings |
|---------------|--------|----------------|------------------|
| **KNS features (this fork)** | `i18n-kmk/` | **`KMR`** | `i18n-kmk/src/commonMain/moko-resources/base/` |
| Komi-kun and Komikku features | `i18n-kmk/` | `KMR` | `i18n-kmk/src/commonMain/moko-resources/base/` |
| Mihon | `i18n/` | `MR` | `i18n/src/commonMain/moko-resources/base/` |
| TachiyomiSY | `i18n-sy/` | `SYMR` | `i18n-sy/src/commonMain/moko-resources/base/` |

**Hard rules:**

- **All new strings go to `i18n-kmk/`** (`import tachiyomi.i18n.kmk.KMR`), even inside code that
  imports `MR`/`SYMR`. Reuse existing `MR`/`SYMR`/`KMR` entries where they fit; only add or change
  `MR`/`SYMR` entries when syncing upstream.
- **Weblate is not connected to this repo.** Non-`base` locale files are normal source files
  maintained here:
  - Port upstream translation commits ("Translations update from Hosted Weblate" from
    Mihon/TachiyomiSY/Komikku) like any other upstream commit; never mark them `X`.
  - When adding a `base` string, add translations you can provide confidently (missing keys fall
    back to `base`). Hand-edit an existing translation only to fix an obvious error.
- **Porting translation commits:** resolve locale conflicts per file — upstream values win for the
  keys it has, entries whose key no longer exists in the module's `base/` are dropped, fork-only
  entries that still exist in `base/` are re-appended. `scripts/resolve_i18n_union.py` in the
  `upstream-port` skill automates this. Never drop a translation for a key that still exists in
  `base/`; validate that the XML is well-formed.
- **Self-check:** `git diff` must not add `<string>`/`<plurals>` entries under non-`base` locales except
  translations that accompany a new base string or a ported upstream translation commit.

### Formatting & build verification

**"Build passes" is not enough.** After Kotlin/XML edits, run in this order before marking work done:

```bash
./gradlew spotlessApply    # fix formatting
./gradlew spotlessCheck    # must pass (CI gate)
./gradlew assembleDebug    # or :app:compileDebugKotlin for a compile-only check
```

- Add the relevant unit tests (`testDebugUnitTest`, or `:module:testDebugUnitTest --tests …`) and, for
  `.sq`/`.sqm` changes, `./gradlew :data:verifyDebugDatabaseMigration`.
- Spotless + ktlint config: `buildSrc/src/main/kotlin/mihon.code.lint.gradle.kts` (`.editorconfig`:
  max line length 120; non-base i18n locales and `**/build/**` excluded).
- Never report a task as tested when the build or tests were not run; say what was not verified.

---

## Tech stack

- **UI:** Jetpack Compose + Material 3, [Voyager](https://voyager.adriel.cafe/) navigation, Coil 3 for images
- **DI:** [Metro](https://zacsweers.github.io/metro/) (`dev.zacsweers.metro`, 1.4.x) — compile-time DI
  (`@Inject`, `@AssistedInject`, `@DependencyGraph`, `@ContributesBinding`, `@ContributesIntoMap` /
  `@ContributesIntoSet`) with `metrox-viewmodel`; an **Injekt** (`uy.kohesive.injekt`) interop bridge
  survives for extension-facing and legacy code (`mihon/app/di/injekt/`)
- **Database:** SQLDelight 2.4 (`generateAsync`) on `com.eygraber:sqldelight-androidx-driver` with the
  AndroidX bundled SQLite driver (SQLCipher's AndroidX driver when encryption is on), WAL
- **Network:** OkHttp 5 with DNS-over-HTTPS
- **Serialization:** Kotlinx Serialization (JSON/Protobuf); Apollo GraphQL for AniList/Kitsu/Shikimori/Suwayomi
- **Reader:** WebGPU renderer (`ca.mpreg:webgpuviewer`) + `ca.mpreg:imagedecoder` progressive decoding,
  SSIV fallback
- **JS engine:** QuickJS (used by sources)
- **Concurrency:** Kotlin coroutines + Flow for new code; RxJava 1 remains in `source-api` for extension compatibility
- **Toolchain:** JDK 21 to build (`.github/.java-version`; the daemon JVM is pinned in
  `gradle/gradle-daemon-jvm.properties`), Java 17 bytecode, Gradle 9.8.1, AGP 9.4.1, Kotlin 2.4.21,
  compileSdk 37.2, Gradle isolated projects + configuration cache enabled

Version catalogs in `gradle/`: `libs.versions.toml`, `kotlinx.versions.toml`, `androidx.versions.toml`,
`compose.versions.toml`, `sy.versions.toml`.

---

## Module layout

| Module | Purpose |
|--------|---------|
| `app/` | UI (`eu.kanade.*`, `exh/`, `mihon/`), Metro ViewModels, the DI graph, workers, build variants |
| `domain/` | Use cases in `…/interactor/` (e.g. `GetManga`), models, repo interfaces |
| `data/` | SQLDelight DB, `*RepositoryImpl` (`tachiyomi.data.*`), `DatabaseBindings` |
| `core:common/` | Network (OkHttp), security, storage, shared utils |
| `core:metro/` | Shared Metro helpers (`mihon.core.metro`: `GraphProvider`, `AppCoroutineScope`, `IsDebugBuild`) |
| `core:archive/` | CBZ/archive reading with optional encryption |
| `core-metadata/` | Comic-info metadata parsing |
| `source-api/` / `source-local/` | Extension `Source` API + local source (Android library modules) |
| `presentation-core/` | Shared Compose components |
| `presentation-widget/` | Home-screen Glance widget |
| `i18n/` / `i18n-sy/` / `i18n-kmk/` | Strings → `MR` / `SYMR` / `KMR` |
| `flagkit/` | Country-flag drawables |
| `icons:material-symbols/`, `icons:custom-material-symbols/`, `icons:simple-icons/` | Generated icon packs |
| `telemetry/` | Firebase/Crashlytics (noop unless telemetry is compiled in; inert for `app.komikku.kns`) |
| `baseline-profile/` | Baseline profile and startup benchmark generation |
| `buildSrc/` | Convention plugins and build logic |

Dependency flow: `app` → `domain` → `source-api`; `data` implements `domain` repos. Raw database access
is restricted to `:data`.

Package roots: `eu.kanade.tachiyomi.*` (legacy UI), `tachiyomi.*` (domain/data), `mihon.*` (Mihon's
newer root, also used by Komi-kun code such as `mihon.feature.*`), `exh.*` (enhanced sources, SY).

---

## Architecture

**DI** – Metro, not Hilt/Koin. The graph is `mihon/app/di/AppGraph.kt` (`@DependencyGraph`) with
providers in `mihon/app/di/AppBindings.kt` and `data/.../DatabaseBindings.kt`; it is built in `App.kt`
(`createGraphFactory<AppGraph.Factory>().create(...)`) and reached as `context.appGraph` or by
injecting `AppGraph`. Most classes need only `@Inject` on the constructor (interactors, repositories,
services); use `@Provides`/`@Binds`/`@ContributesBinding` for interface or third-party types and
`@ContributesIntoMap`/`@ContributesIntoSet` for maps/sets. Parameterised types use `@AssistedInject` +
`@AssistedFactory`. Code that still uses Injekt resolves through the bridge in
`mihon/app/di/injekt/KMKMetroInteropModule.kt`; new code should use constructor injection.

**UI & navigation** – Voyager `Screen`s in `eu.kanade.tachiyomi.ui.*`, composables in
`eu.kanade.presentation.*`. Base type: `eu.kanade.presentation.util.Screen` (in `Navigator.kt`). State
lives in **AndroidX ViewModels** obtained with `metroViewModel<VM>()` or
`assistedMetroViewModel<VM, Factory> { create(...) }`; they extend `androidx.lifecycle.ViewModel`,
expose a `StateFlow` (often a `State` data class) and run work in `viewModelScope`. Example:
`NewUpdateScreen` + `NewUpdateViewModel` in `app/src/main/java/eu/kanade/tachiyomi/ui/more/`. Do not
reintroduce `ScreenModel`s.

**Coroutines** – In ViewModels use `viewModelScope`. Helpers in `tachiyomi.core.common.util.lang`:
`withIOContext`, `withUIContext`, `CoroutineScope.launchIO`. The top-level `launchIO` / `launchNow` run
on **GlobalScope** — avoid them for lifecycle-bound work. Long-lived services use `AppCoroutineScope`
(`mihon.core.metro`). Collect flows lifecycle-aware in Compose.

**Activities (not Voyager)** – `MainActivity` (shell / Voyager host), `ReaderActivity` +
`ReaderViewModel`, `WebViewActivity`, `UnlockActivity`, OAuth login activities, `DeepLinkActivity`.
Reader: `ReaderActivity.newIntent(context, mangaId, chapterId)`.

**Domain / data** – One class per operation under `domain/src/main/java/tachiyomi/domain/*/interactor/`
(verb names like `GetTracksPerManga`, `HideCategory`; no `*Interactor` suffix), annotated `@Inject`.
App-specific cases go in `app/src/main/java/eu/kanade/domain/…/interactor/`. `Manga` / `Chapter` are
domain models; `SManga` / `SChapter` are source-layer types — convert at the boundary.

**Database** – SQLDelight in `data/src/main/sqldelight/tachiyomi/` (`data/*.sq`, `view/*.sq`,
`migrations/*.sqm`; schema snapshots `data/src/main/sqldelight/<N>.db`). Tables use Mihon's singular
names (`manga`, `chapter`, `category`, `manga_category`, `manga_track`, `remote_*`/`user_*`/`state_*`
columns) and keep SyncYomi's `version`/`is_syncing`/`last_modified_at` columns and triggers. Use
`Database.transaction` / `transactionWithResult` + `await*`. For schema changes:

1. Add a **new** `migrations/<next>.sqm` (`-- KNS` markers). Never renumber or edit a released migration
   unless the change is provably identical for every database it already migrated (document it).
2. Update the `.sq` definitions/queries so a fresh create equals an upgraded database.
3. Update the `*RepositoryImpl` mapper and backup/restore + sync if user data is affected.
4. `./gradlew :data:generateSqlDelightInterface :data:verifyDebugDatabaseMigration` and add a migration
   test in `data/src/test/java/tachiyomi/data/` (see `MigrationTest`, `FreshKomikkuInstallMigrationTest`).

The schema version is the highest migration number + 1 (currently 59; `58.sqm` recreates
`updatesView` with the bookmark color; `57.sqm` holds the KNS
columns). The previous personal `main` used an unrelated migration lineage up to version 57; such a
database is converted on first open by `tachiyomi.data.compat.OldForkDatabaseConverter` (hooked in
`mihon/app/di/OldForkDatabaseGate.kt`). Any new table or column needs no converter change unless an
old-fork column maps to it under another name or meaning (`columnSources`); keep
`OldForkDatabaseConverterTest` passing. See `docs/komikun-foundation-progress.md` before shipping.

**Preferences** – `eu.kanade.domain.*.service.*Preferences` (getters are properties, e.g.
`sourcePreferences.relatedMangas`). App preference migrations:
`app/src/main/java/mihon/core/migration/migrations/`.

**Images** – Coil 3 (`coil3.*`, `context.imageLoader`), configured in `App.kt`. No Glide/Picasso.

**Logging** – Komi-kun / Komikku / KNS code: `xLogE()` / `xLog()` from `exh.log`. Mihon code:
`logcat { }` from `tachiyomi.core.common.util.system`. Avoid raw `android.util.Log`.

---

## Backup protobuf field numbers

Backup and sync payloads are protobuf (`app/src/main/java/eu/kanade/tachiyomi/data/backup/models/`). A `@ProtoNumber` is a **permanent wire identifier**: changing it makes existing backups and peers decode the field as a different type or lose it entirely. Renumbering a field that has ever shipped is forbidden.

### Upstream ownership

| Range | Owner |
|-------|-------|
| `1-18`, `100-113` | Mihon |
| `600-603` | TachiyomiSY |
| `610` | Komikku |
| `800-805` | J2K, via SY (`803` is skipped: earlier builds used a duplicate) |
| `900` | Komikku |

Open upstream claims also count as taken: TachiyomiSY `604` (`List<BackupSortedScanlator>`, SY PR #1420); Komikku `611`, `650`, `900`, `901`; Mihon `806`, `807`.

### komi-kun reserved range: `10000-10999`

komi-kun owns this block. No upstream uses `>= 1000`, so a collision can be avoided by construction. Per-model blocks:

| Range | Model |
|-------|-------|
| `10000-10099` | `BackupManga` |
| `10100-10199` | `BackupChapter` |
| `10200-10299` | `BackupCategory` |
| `10300-10399` | `BackupTracking` |
| `10400-10499` | `BackupHistory` |
| `10500-10599` | `Backup` root |
| `10600-10699` | `BackupMergedMangaReference` |
| `10700-10799` | `BackupFlatMetadata` / metadata |
| `10800-10899` | Saved search / feed / extension store / source / preference |
| `10900-10999` | New models / overflow |

Current komi-kun allocations:

- `BackupManga.customInfoIncluded` = `10000`
- `BackupManga.mergedMangaReferencesIncluded` = `10001`

### This fork's (KNS) fields

These numbers were shipped by the previous personal fork; they stay so its backups restore, and are
guarded by `BackupProtoNumberTest`:

- `BackupManga`: `scanlatorPriority` `614`, `blacklistedChapters` `615`, `scanlatorRangeRules` `616`,
  `smartScanlatorMergeOverride` `618`, `scanlatorSettingsPresent` `620` (`613`, `617`, `619` are not used here).
- `BackupChapter`: `bookmarkColor` `14`, `fillermark` `15` (read only). They sit inside Mihon's range; check
  every Mihon backup-model change for a collision.
- New KNS fields use `20000-20999` (same per-model hundreds as the komi-kun block), never `10000-10999`.

Rules:

- Never allocate a KUN field below `10000`; use the reserved block.
- Never allocate an upstream/shared number inside `10000-10999`.
- If an upstream number ever collides with a KUN allocation, **upstream wins**: renumber the KUN field only if it has never shipped; otherwise retire it and allocate a new number.
- Never renumber a shipped field. Never reuse a retired number: leave a `// retired: ProtoNumber(<n>)` comment instead.
- `19000-19999` is reserved by the protobuf specification; never use it.
- A field number in the 10000 range costs a 3-byte tag instead of 1-2, which is negligible next to the payload, and backups are gzipped.

---

## Extensions & sources

- Catalog sources: installable APK extensions (not in this repo).
- In-repo: delegated sources and metadata in `exh/` (E-Hentai, NHentai, MangaDex, `exh/recs/`).
- `source-api`: `eu.kanade.tachiyomi.source.*` — **avoid breaking extension ABI**. `SourceManager`
  lookups are `suspend`.

---

## Adding a KNS feature

1. Read the feature's section in `features.md`, then inspect the codebase: what already exists, what
   Komi-kun/Komikku already provide, and what is genuinely missing. Do not duplicate or replace modern
   implementations with older ones.
2. **Interactor:** `domain/src/main/java/tachiyomi/domain/<area>/interactor/MyFeature.kt`, `@Inject`.
3. **UI:** Metro ViewModel + `Screen` under `app/src/main/java/eu/kanade/tachiyomi/ui/`.
4. **Strings:** `i18n-kmk/src/commonMain/moko-resources/base/strings.xml`, referenced via `KMR`.
5. **Persistence:** preferences, database migration, backup/restore and sync where the feature owns user state.
6. **Markers:** `// KNS` around fork-authored changes; nest inside inherited blocks.
7. **Tests:** unit/regression tests for the acceptance scenarios in `features.md`.
8. **Backup/sync fields:** new `@ProtoNumber`s must use the fork-reserved `10000-10999` block (see
   [Backup protobuf field numbers](#backup-protobuf-field-numbers)); never take an upstream number.

Reference: `domain/src/main/java/tachiyomi/domain/category/interactor/HideCategory.kt` and
`app/src/main/java/eu/kanade/tachiyomi/ui/more/NewUpdateScreen.kt` + `NewUpdateViewModel.kt`.

---

## Build & CI

Build types (`applicationIdSuffix`): `debug` (`.dev`, updater always off), `release`, `releaseTest`
(`.rt`), `foss` (`.foss`), `nightly` (`.beta`; Komikku's former `preview`), `benchmark` (`.benchmark`).

`defaultConfig` holds this fork's identity inside a `KNS` block: `applicationId = "app.komikku.kns"`,
`versionCode`/`versionName` (one declaration each, on their own lines — `bump_version.sh` edits them).

Gradle `-P` flags (`buildSrc/src/main/kotlin/mihon/buildlogic/BuildConfig.kt`):

| Flag | Effect |
|------|--------|
| `dist=local\|ci\|github\|foss` | Distribution defaults for the flags below (default `local`) |
| `include-telemetry[=false]` | Firebase Analytics + Crashlytics (default on for `ci`/`github`) |
| `enable-updater[=false]` | In-app update checker (default on except `local`) |
| `disable-code-shrink` | Skip R8 minification |
| `include-dependency-info` | Dependency metadata in APK |
| `devSecret` | Passed by CI from `DEV_SECRETS`; not read by the foundation yet |

```bash
./gradlew spotlessApply
./gradlew spotlessCheck
./gradlew :app:compileDebugKotlin
./gradlew assembleDebug
./gradlew assembleNightly
./gradlew assembleRelease
./gradlew testDebugUnitTest
./gradlew :data:generateSqlDelightInterface
./gradlew :data:verifyDebugDatabaseMigration
```

Local signing: `assembleRelease`/`assembleNightly` sign with the debug key unless a root
`keystore.properties` (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`) exists; CI signs
through `MIHON_GITHUB_RELEASE` + the repository secrets. Never print or commit keys or secrets.

**Workflows** (`.github/workflows/`, details in `CI_SETUP.md`):
- `bump.yml` – manual: `spotlessApply`, bump `versionCode`/`versionName`, create `v<version>` (with `BOT_PAT`)
- `build_release.yml` – `v*` tag → signed `assembleRelease -Pdist=github` → regular release in this repo
- `build_dispatch_preview.yml` – dispatches a preview build in `xkana-shii/komikku-preview`
- `build_pull_request.yml` – PR validation: dependency review → wrapper validation → `spotlessCheck` →
  `assembleNightly` → `testDebugUnitTest` → `:data:verifyDebugDatabaseMigration`
- `build_push.yml`, `build_nightly.yml` – inherited, keyed to `master` (dormant on `main`)
- `build_benchmark.yml` – manual benchmark build
- `codeberg_mirror.yml.disabled`, `update_website.yml.disabled` – upstream-only

The in-app updater reads `v*` releases from `xkana-shii/komikku` and `r<count>` nightly releases from
`xkana-shii/komikku-preview` (`AppUpdateChecker.getGithubRepo`).

---

## Tests

- **Framework:** JUnit (Jupiter) + Kotest assertions + MockK.
- **Locations:** `domain/src/test/`, `data/src/test/` (migration and repository tests on the JDBC
  SQLite driver), `app/src/test/` (e.g. `mihon/core/migration/MigratorTest.kt`). No broad UI test suite.
- **Single class:** `./gradlew :data:testDebugUnitTest --tests "*.MigrationTest"`

---

## Key files

- `App.kt` – Metro graph bootstrap, logging setup, Coil initialization
- `MainActivity.kt` – Voyager navigation host
- `app/src/main/java/mihon/app/di/AppGraph.kt` / `AppBindings.kt` – the DI graph and its bindings
- `data/src/main/java/tachiyomi/data/DatabaseBindings.kt` – database wiring
- `buildSrc/src/main/kotlin/mihon/buildlogic/BuildConfig.kt`, `AndroidConfig.kt` – flags, SDK versions
- `app/build.gradle.kts`, `settings.gradle.kts`
- `FORK.md`, `CI_SETUP.md`, `GOOGLE_SERVICES_SETUP.md`, `features.md`, `docs/komikun-foundation-progress.md`

---

## Upstream cherry-pick log

`cherrypick_log.md` lists upstream commits with their status on the target branch. It is generated by
`.agents/skills/cherrypick-log/update_cherrypick_log.py` from `.agents/skills/cherrypick-log/config.json`:
main upstream `mihon/main`, additional `tachiyomiSY/master`, `komikku/master` (direct upstream) and
`komikun/master` (Komi-kun), each with its own `since`; Renovate commits are excluded.

- Remotes: `komikku` (komikku-app/komikku), `komikun` (komi-kun/komi-kun), `mihon` (mihonapp/mihon),
  `tachiyomiSY` (jobobby04/TachiyomiSY).
- `?` rows are reviewed with the `cherrypick-log` skill; `X` means "won't pick" (set by hand with a note,
  or by the skill for version-bump-only commits).
- Never hand-edit columns other than Status and Notes; use the script's `mark` command.
- Port Komikku with the `komikku-sync` skill and Mihon/TachiyomiSY/Komi-kun with `upstream-port`
  (`.agents/skills/`); keep `git cherry-pick -x` provenance and record skipped/adapted commits.

---

## Common issues

- **Gradle OOM** – the daemon uses `-Xmx4g` (`gradle.properties`); run `./gradlew --stop` and retry.
- **Spotless failures** – run `spotlessApply`, then `spotlessCheck`.
- **SQLDelight errors** – run `./gradlew :data:generateSqlDelightInterface` after schema changes.
- **Missing `google-services.json` / `client_secrets.json`** – not needed unless telemetry is compiled in
  (`-Pdist=ci|github` without `-Pinclude-telemetry=false`) or Google Drive backup is used.
- **First build is slow** – it downloads ~1 GB of dependencies and the pinned daemon JDK.

## Mandatory CHANGELOG.md Maintenance

Maintain a complete, accurate `CHANGELOG.md` in the repository throughout development.

**Every time you add, change, fix, optimize, remove, or restore functionality, update `CHANGELOG.md` as part of the same work.**

This requirement applies to the entire feature-restoration project and all future development tasks.

### Changelog requirements

1. Check whether `CHANGELOG.md` already exists. Preserve its existing entries and history. Do not create a duplicate lowercase `changelog.md`.
2. Maintain an `Unreleased` section for changes that have not yet been published.
3. Organize entries using appropriate sections, such as:
   - **Added** — New features, settings, controls, and functionality.
   - **Changed** — Behavior changes, UI adjustments, refactoring, and performance improvements.
   - **Fixed** — Bug fixes, regressions, and reliability corrections.
   - **Removed** — Intentionally removed or replaced functionality.
   - **Technical** — Database migrations, build changes, testing infrastructure, and other important internal changes.
4. Describe what actually changed and why it matters, using clear, concise language.
5. Include smaller modifications and bug fixes, not just major features.
6. Group closely related changes logically, but do not omit distinct functionality.
7. Record database migrations, backup compatibility changes, and synchronization changes explicitly.
8. Record changes to settings, defaults, navigation, reader interactions, and other user-visible behavior.
9. Record meaningful internal changes to architecture, performance, tests, and developer tooling.
10. Do not list previously existing Komi-kun or Mihon/TachiyomiSY functionality as newly added fork features.
11. Distinguish new fork-specific implementations from adaptations of existing upstream functionality where relevant.
12. Do not invent completed features, successful tests, performance improvements, or compatibility guarantees.
13. Preserve historical release entries, version numbers, and dates. Do not rewrite previous releases to make new changes appear older.
14. Keep the writing natural and readable, without repetitive or vague AI-generated descriptions.

### Required development workflow

For every feature or coherent change batch:

1. Implement the change.
2. Add or update relevant regression tests.
3. Run the applicable validation.
4. Update `CHANGELOG.md` to describe the actual changes.
5. Commit the changelog alongside the corresponding source changes.
6. Update the feature implementation progress document.
7. Continue to the next batch.

**Never leave a completed feature or fix undocumented in `CHANGELOG.md`.**

Do not wait until the entire project is finished to write the changelog.

A batch containing five distinct functional changes should have changelog coverage for all five, even if they belong to one commit.

Documentation-only changes and formatting corrections should also be recorded when meaningful, but do not create recursive entries merely for editing the changelog itself.

### Final verification

Before declaring a task complete:

- Compare the new commits and modified files against `CHANGELOG.md`.
- Check that every meaningful addition, behavior change, fix, removal, migration, and optimization is documented.
- Verify that the changelog does not claim unimplemented or untested behavior.
- Ensure the changelog is included in the appropriate local commits.
- Report the changelog updates in the final completion summary.

**Updating `CHANGELOG.md` is a mandatory part of implementing changes, not an optional documentation task.**