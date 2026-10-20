
# Komikku (KNS fork) — AI Agent Guide

**xkana-shii/komikku** is a personal fork of **Komikku** (`komikku-app/komikku`), an Android manga reader derived from Mihon and TachiyomiSY.

Its **direct upstream** is official Komikku. **Komi-kun** (`komi-kun/komi-kun`) is an **additional, indirect upstream** whose changes are selectively cherry-picked into this fork; it is not the Git parent or direct upstream. Mihon and TachiyomiSY remain inherited upstreams.

The intended application identity is `app.komikku` (debug: `app.komikku.dev`), not Komi-kun's `app.komikun`.

The integration foundation begins with official Komikku and adopts Komi-kun's functional improvements. New personal features are implemented here using the **KNS** marker, not copied wholesale from the old personal fork.

Verify architecture, dependency versions, and build variants against the checked-out integration branch rather than assuming either source repository's current configuration.

Features: configurable reader, downloads/offline reading, trackers (MyAnimeList, AniList, Kitsu, MangaUpdates, Bangumi, Comick, Hikka, Kavita, Komga, MangaBaka, MangaDex, Shikimori, Suwayomi), recommendations, metadata editing, library categories/tags/filters, multi-source browsing and feed tabs.

### Upstreams

| Origin / relationship | Code marker | Strings |
|-----------------------|-------------|---------|
| Mihon (inherited upstream) | none (base code) | `MR` (`i18n/`) |
| TachiyomiSY (inherited upstream) | `// SY -->` … `// SY <--` (legacy `// EXH` also belongs to SY) | `SYMR` (`i18n-sy/`) |
| **Official Komikku (direct upstream)** | `// KMK -->` … `// KMK <--` | `KMR` (`i18n-kmk/`) |
| **Komi-kun (indirect/additional upstream)** | `// KUN -->` … `// KUN <--` | `KMR` (`i18n-kmk/`) |
| **xkana-shii/komikku (this fork)** | `// KNS -->` … `// KNS <--` | **`KMR`** (`i18n-kmk/`) |

Markers identify **code provenance**, not merely the repository in which code currently resides.

Keep existing `SY`, `EXH`, `KMK`, and `KUN` markers with their imported code. Only new changes made **by this fork** use `KNS`.

No `KNSMR` resource class or `i18n-kns` module is introduced.

---

## Mandatory rules for AI agents

**Read this section before every change.** These rules override shortcuts (e.g. copying nearby `MR` imports or only running `compileDebugKotlin`).

### Keep this guide current

`AGENTS.md` documents the **current checked-out branch** and the architecture intended for the future `main`.

Update this file in the same change whenever architecture, DI, module layout, markers, i18n, build flags, workflows, or key paths change.

Do not claim that a feature or migration is already present unless it actually exists on that branch. Remove stale guidance rather than carrying forward old Komikku or Komi-kun assumptions.

### Git

| Rule | Required behavior |
|------|-------------------|
| Branch | Create a **feature branch** for the task (`git checkout -b <type>/<short-description>`, e.g. `feat/manga-recommendations`). |
| Commit | **OK** on a feature branch when work is ready. **Never** commit directly to `master` / `main` unless the user explicitly asks. |
| Push | **OK** to push the **current feature branch** when work is ready. **Never** push to `master` / `main` unless the user explicitly asks. |
| Merge | Prefer an audited squash merge for ordinary feature PRs. **Never merge the old personal `main` into the rebuilt branch.** Replacing `main` with the new foundation requires explicit user approval and an independently verified backup. |

Before `git push`, confirm the current branch is not `master` or `main` (`git branch --show-current`).

Keep a permanent backup of the original personal `main`. Never force-update `main` or alter the backup without explicit approval.

### Fork markers

Markers record **the source of a change**, not the name of this Git repository.

Every new addition or change authored specifically for `xkana-shii/komikku` **must** be marked with `KNS` where the project's marker convention applies:

```kotlin
// KNS -->
// new or modified fork-specific code
// KNS <--
```

| Code origin | Required marker |
|-------------|-----------------|
| **This personal fork (`xkana-shii/komikku`)** | `// KNS -->` … `// KNS <--` |
| **Komi-kun** (indirect upstream) | `// KUN -->` … `// KUN <--` |
| **Official Komikku** (direct upstream) | `// KMK -->` … `// KMK <--` |
| TachiyomiSY | `// SY -->` … `// SY <--` (also legacy `// EXH`) |
| Mihon base code | none |

**Hard rules:**

- Use `KNS` for this fork's **new code, new adaptations, and modifications**. Never use `KMK` or `KUN` for code first authored here.
- **Do not mass-rename existing `KMK` to `KUN` or `KUN` to `KNS`.** Preserve inherited markers during upstream cherry-picks and file moves.
- When changing inherited code inside a `KUN`, `KMK`, `SY`, or `EXH` section, keep the original provenance and mark the fork-specific delta with a **nested `KNS` block** where feasible.
- Do not relabel untouched imported upstream code just because it now lives in this repository.
- For `.sq` and `.sqm`, use SQL comments: `-- KNS -->` and `-- KNS <--`. Apply the same provenance rules to query definitions, schema adaptations, and mappers.
- If a change cannot be enclosed sensibly (for example, a one-line annotation or a whole-file rewrite), preserve provenance through a clear nearby `KNS` annotation or a documented commit rather than inventing incorrect upstream attribution.
- A clean, unmodified cherry-pick keeps its source markers and a `(cherry picked from commit …)` trailer; only its **locally authored adaptation** is `KNS`.

### Upstream priority and the clean rebuild

- **Official Komikku is the direct upstream and starting Git foundation. Komi-kun is the additional indirect upstream for cherry-picked functional improvements.** Neither is the personal fork's new-code marker.
- When official Komikku and Komi-kun implement the **same functionality incompatibly**, intentionally choose **Komi-kun's final implementation**. Do not create an unintended hybrid of two dependency-injection, ViewModel, or database architectures.
- `git cherry-pick -X theirs` favors incoming hunks, **not** the full latest Komi-kun version of a file. Inspect conflicted files against the pinned Komi-kun revision when applying this rule.
- Preserve nonconflicting official Komikku functionality. If mandatory compatibility fixes are needed after accepting Komi-kun's implementation, make and mark them as distinct `KNS` changes.
- Keep the original personal fork's old `main` isolated during the foundation rebuild. Do not import its previous feature implementations unless the user explicitly requests it later.
- Preserve this repository's application identity and existing workflow/secrets references. Source-code precedence must not overwrite its signing and release compatibility.
- Database migrations require special handling: honor Komi-kun's intended schema but reconcile incompatible migration histories without overwriting existing users' data or claiming untested upgrade compatibility.

### Internationalization (strings)

| String origin | Module | Resource class | New base strings |
|---------------|--------|----------------|------------------|
| **KNS features authored in this fork** | `i18n-kmk/` | **`KMR`** | `i18n-kmk/src/commonMain/moko-resources/base/` |
| Komi-kun and official Komikku features | `i18n-kmk/` | `KMR` | `i18n-kmk/src/commonMain/moko-resources/base/` |
| Mihon | `i18n/` | `MR` | `i18n/src/commonMain/moko-resources/base/` |
| TachiyomiSY | `i18n-sy/` | `SYMR` | `i18n-sy/src/commonMain/moko-resources/base/` |

**Hard rules:**

- **All new KNS feature strings belong in `i18n-kmk/`**, accessed with `import tachiyomi.i18n.kmk.KMR`.
- **Do not introduce `KNSMR`, `i18n-kns/`, or a duplicate string-resource namespace.** `KNS` is the code provenance marker; `KMR` is the shared Komikku-family resource class.
- Reuse existing upstream `MR`, `SYMR`, and `KMR` entries when appropriate. Do not add new personal feature strings to `MR` or `SYMR`.
- Keep upstream strings/translations during cherry-picks. Determine whether this fork's translation files are managed locally or through Weblate before editing non-`base` locale files; follow the repository's actual configured translation ownership.
- Where translations are locally maintained, add accurate new translations in the same change when possible and retain fallback to `base` for missing translations. Where Weblate owns non-`base` locales, do not manually rewrite them.
- Within `KNS` code, prefer `KMR` for new strings even if adjacent code imports `MR` or `SYMR`.

**Porting translations:** When source locale files have diverged, preserve upstream values for upstream keys and retain still-valid fork-only translations according to the current translation workflow.

Do not delete a translation for a base key that still exists. Validate resource XML and generated `KMR` references.

**Self-check:** New KNS text must resolve through `KMR`, and edits to translations must conform to the verified ownership policy. Do not introduce duplicated keys or unrelated locale churn.

### Formatting & build verification

**“Build passes” is not enough.** After Kotlin/XML edits, run **in this order** before marking work complete:

```bash
./gradlew spotlessApply    # fix formatting
./gradlew spotlessCheck    # must pass (CI gate)
./gradlew assembleDebug    # or :app:compileDebugKotlin for a faster compile-only check
```

- **Do not** skip `spotlessCheck`. If it fails, run `spotlessApply` and re-run `spotlessCheck`.
- Use `compileDebugKotlin` instead of `assembleDebug` only if the user asked for a quick compile check — Spotless still applies.
- Spotless + ktlint config: `buildSrc/src/main/kotlin/mihon.code.lint.gradle.kts` (trim trailing whitespace, end with newline; non-base i18n locales and `**/build/**` excluded).

---

## Tech stack

- **UI:** Jetpack Compose + Material 3, [Voyager](https://voyager.adriel.cafe/) navigation, Coil 3 for images
- **DI:** [Metro](https://zacsweers.github.io/metro/) (`dev.zacsweers.metro`) — compile-time DI (`@Inject`, `@AssistedInject`, `@DependencyGraph`, `@ContributesIntoMap` / `@ContributesIntoSet`); an **Injekt** (`uy.kohesive.injekt`) interop bridge survives for extension-facing code (`mihon/app/di/injekt/`)
- **Database:** SQLDelight, SQLCipher encryption
- **Network:** OkHttp 5 with DNS-over-HTTPS
- **Serialization:** Kotlinx Serialization (JSON/Protobuf)
- **JS engine:** QuickJS (used by sources)
- **Concurrency:** Kotlin coroutines + Flow for new code; RxJava 1 remains in `source-api` for extension compatibility
- **Toolchain:** Use the pinned Komi-kun architecture and verify the actual JDK, Gradle, Android Gradle Plugin, and SDK versions in the integrated branch; the reference Komi-kun guide used JDK 21, Java 17 bytecode, Gradle 9.8.1, and compileSdk 37.2

Version catalogs in `gradle/`: `libs.versions.toml`, `kotlinx.versions.toml`, `androidx.versions.toml`, `compose.versions.toml`, `sy.versions.toml`.

---

## Module layout

| Module | Purpose |
|--------|---------|
| `app/` | UI (`eu.kanade.*`, `exh/`, `mihon/`), Metro ViewModels, the DI graph, workers, build variants |
| `domain/` | Use cases in `…/interactor/` (e.g. `GetManga`), models, repo interfaces |
| `data/` | SQLDelight DB, `*RepositoryImpl` (`tachiyomi.data.*`) |
| `core:common/` | Network (OkHttp), security, storage, shared utils |
| `core:metro/` | Shared Metro helpers (`mihon.core.metro`: `GraphProvider`, `AppCoroutineScope`, `IsDebugBuild`) |
| `core:archive/` | CBZ/archive reading with optional encryption |
| `core-metadata/` | Comic-info metadata parsing |
| `source-api/` / `source-local/` | Extension `Source` API + local source |
| `presentation-core/` | Shared Compose components |
| `presentation-widget/` | Home-screen Glance widget |
| `i18n/` / `i18n-sy/` / `i18n-kmk/` | Strings → `MR` / `SYMR` / `KMR` (see [Internationalization](#internationalization-strings)) |
| `flagkit/` | Country-flag drawables |
| `icons:material-symbols/`, `icons:custom-material-symbols/`, `icons:simple-icons/` | Generated icon packs (`MaterialSymbols`, custom Material Symbols, Simple Icons) |
| `telemetry/` | Firebase/Crashlytics (noop unless `-Pinclude-telemetry`) |
| `baseline-profile/` | Baseline profile and startup benchmark generation (CI-only) |
| `buildSrc/` | Convention plugins and build logic |

Dependency flow: `app` → `domain` → `source-api`; `data` implements `domain` repos.

Package roots: `eu.kanade.tachiyomi.*` (legacy UI), `tachiyomi.*` (domain/data), `mihon.*` (Mihon's newer package root — also used by komi-kun code, e.g. `mihon.feature.*`), `exh.*` (enhanced sources, SY).

---

## Architecture

**DI** – [Metro](https://zacsweers.github.io/metro/), not Hilt/Injekt.

The graph is `mihon/app/di/AppGraph.kt` (`@DependencyGraph`) with providers/bindings in `mihon/app/di/AppBindings.kt`; it is built in `App.kt` (`createGraphFactory<AppGraph.Factory>().create(...)`) and reached as `context.appGraph` or by injecting `AppGraph`.

Most classes need only `@Inject` on the constructor (interactors, repositories, services); use `@Provides`/`@Binds` in `AppBindings` for third-party or interface types, and `@ContributesIntoMap` / `@ContributesIntoSet` for maps/sets.

Parameterised types use `@AssistedInject` + `@AssistedFactory`. Extension-facing code still resolves through the Injekt interop bridge (`mihon/app/di/injekt/`).

**UI & navigation** – Voyager `Screen`s in `eu.kanade.tachiyomi.ui.*`, composables in `eu.kanade.presentation.*`.

Base type: `eu.kanade.presentation.util.Screen` (in `Navigator.kt`).

State lives in **Metro ViewModels** obtained with `metroViewModel<VM>()` or `assistedMetroViewModel<VM, Factory> { create(...) }`; they extend `androidx.lifecycle.ViewModel`, expose a `StateFlow` / `MutableStateFlow` (often a `State` data class) and run work in `viewModelScope`.

Example: `NewUpdateScreen` + `NewUpdateViewModel` in `app/src/main/java/eu/kanade/tachiyomi/ui/more/`.

**Coroutines** – In ViewModels use `viewModelScope`.

Helpers in `tachiyomi.core.common.util.lang`: `withIOContext`, `withUIContext`, `CoroutineScope.launchIO` (on a given scope).

The top-level `launchIO` / `launchNow` run on **GlobalScope** — avoid them for lifecycle-bound work.

`rememberCoroutineScope()` is fine in Compose; long-lived services may own their own scope (`AppCoroutineScope` in `mihon.core.metro`).

**Activities (not Voyager)** – `MainActivity` (shell / Voyager host), `ReaderActivity` + `ReaderViewModel`, `WebViewActivity`, `UnlockActivity`, OAuth login activities, `DeepLinkActivity`.

Reader: `ReaderActivity.newIntent(context, mangaId, chapterId)`.

Web: both `WebViewScreen` (Voyager) and `WebViewActivity.newIntent(...)`.

**Domain / data** – One class per operation under `domain/src/main/java/tachiyomi/domain/*/interactor/` (verb names like `GetTracksPerManga`, `HideCategory`; no `*Interactor` suffix), annotated `@Inject` so Metro resolves it directly — no module registration.

App-specific cases go in `app/src/main/java/eu/kanade/domain/…/interactor/`.

`Manga` / `Chapter` are domain models; `SManga` / `SChapter` are source-layer types — convert at the boundary (`SManga.toDomainManga()`, `Manga.toSManga()`, `Chapter.toSChapter()`).

**Database** – SQLDelight in `data/src/main/sqldelight/tachiyomi/`.

For schema changes:

1. Add a new `migrations/*.sqm` file.
2. Update the `.sq` queries.
3. Update the `*RepositoryImpl` mapper.
4. Regenerate: `./gradlew :data:generateSqlDelightInterface` (or any compile that touches `:data`).

**Preferences** – `eu.kanade.domain.*.service.*Preferences` (e.g. `SourcePreferences.relatedMangas()`).

App preference migrations: `app/src/main/java/mihon/core/migration/migrations/` (`mihon.core.migration.Migration`).

**Images** – Coil 3 (`coil3.*`, `context.imageLoader`), configured in `App.kt`. No Glide/Picasso.

**Logging** – Komi-kun / Komikku / KNS code: `xLogE()` / `xLog()` from `exh.log`. Mihon code: `logcat { }` from `tachiyomi.core.common.util.system`. Avoid raw `android.util.Log`.

---

## Extensions & sources

- Catalog sources: installable APK extensions (not in this repo).
- In-repo: delegated sources and metadata in `exh/` (E-Hentai, NHentai, MangaDex, `exh/recs/`).
- `source-api`: `eu.kanade.tachiyomi.source.*` — **avoid breaking extension ABI**.

---

## Adding a KNS feature

1. **Interactor:** `domain/src/main/java/tachiyomi/domain/<area>/interactor/MyFeature.kt`, annotated `@Inject` (Metro resolves it; add a `@Provides` / `@Binds` to `mihon/app/di/AppBindings.kt` only if it needs manual wiring).
2. **UI:** Metro ViewModel + `Screen` under `app/src/main/java/eu/kanade/tachiyomi/ui/` (see [Architecture](#architecture)).
3. **Strings:** `i18n-kmk/src/commonMain/moko-resources/base/strings.xml`, referenced via `KMR`.
4. **Markers:** wrap new fork-authored changes in `// KNS -->` / `// KNS <--` (or `-- KNS` in SQL); retain `KUN` and `KMK` for unmodified inherited code.

Reference: `domain/src/main/java/tachiyomi/domain/category/interactor/HideCategory.kt` (an `@Inject` interactor) and `app/src/main/java/eu/kanade/tachiyomi/ui/more/NewUpdateScreen.kt` + `NewUpdateViewModel.kt` (ViewModel + Screen).

---

## Build & CI

Build types (`applicationIdSuffix`): `debug` (`.dev`), `release`, `releaseTest` (`.rt`), `foss` (`.foss`), `nightly` (`.beta`, CI default), `benchmark` (`.benchmark`).

Gradle `-P` flags (`buildSrc/src/main/kotlin/mihon/buildlogic/BuildConfig.kt`):

| Flag | Effect |
|------|--------|
| `include-telemetry` | Firebase Analytics + Crashlytics |
| `enable-updater` | In-app update checker |
| `disable-code-shrink` | Skip R8 minification |
| `include-dependency-info` | Dependency metadata in APK |

```bash
./gradlew spotlessApply
./gradlew spotlessCheck
./gradlew :app:compileDebugKotlin
./gradlew assembleDebug
./gradlew assembleNightly
./gradlew assembleNightly -Pinclude-telemetry -Penable-updater
./gradlew assembleRelease
./gradlew testDebugUnitTest
./gradlew test
./gradlew installDebug
./gradlew :data:generateSqlDelightInterface
```

**Workflows** (`.github/workflows/`): Keep the workflow files and secret references belonging to **`xkana-shii/komikku`**.

Verify their actual names and Gradle tasks in this repository after integration; do not assume Komi-kun's nightly-only release model applies.

In particular, do not import Komi-kun's `app.komikun` branding, `r<commit-count>` tag scheme, or fixed version code unless explicitly requested.

The commands above show reference tasks available in Komi-kun's architecture. Verify tasks and build variants against the finished integration.

Release APK signing must continue to use this repository's existing signing configuration, without displaying secrets.

---

## Tests

- **Framework:** JUnit (Jupiter) + Kotest assertions + MockK.
- **Locations:** `domain/src/test/`, `app/src/test/` (e.g. `app/src/test/java/mihon/core/migration/MigratorTest.kt`). No broad UI test suite — focus on domain and critical logic.
- **Single class:** `./gradlew :domain:testDebugUnitTest --tests "*.ClassName"`

---

## Key files

- `App.kt` – Metro graph bootstrap, logging setup, Coil initialization.
- `MainActivity.kt` – Voyager navigation host.
- `app/src/main/java/mihon/app/di/AppGraph.kt` – Metro `@DependencyGraph` (the DI graph).
- `app/src/main/java/mihon/app/di/AppBindings.kt` – `@Provides` / `@Binds` / contributed bindings.
- `buildSrc/src/main/kotlin/mihon/buildlogic/BuildConfig.kt`, `AndroidConfig.kt` – flags, SDK versions.
- `app/build.gradle.kts`, `settings.gradle.kts`.
- `CONTRIBUTING.md` – prerequisites and contribution process.

---

## Upstream cherry-pick log

Treat **`komikku-app/komikku` (`master`) as the direct upstream** and **`komi-kun/komi-kun` (`master`) as the indirect additional upstream** used for functional cherry-picks.

Mihon and TachiyomiSY are inherited upstreams and may provide further changes by explicit request.

- Preserve original commit provenance using `git cherry-pick -x` where feasible.
- Audit upstream commits for duplication and equivalent implementations before porting.
- Favor the Komi-kun implementation for conflicts as described above, while retaining nonconflicting official Komikku updates.
- Do not import Komi-kun-only branding, application identifiers, or release setup into this repository.
- Update `.agents/skills/cherrypick-log/config.json`, `cherrypick_log.md`, and related scripts **if present**, to reflect the actual remotes and target branch. Do not assume Komi-kun's old `master` targeting or upstream configuration is correct here.
- Keep a record of ported, skipped, and adapted commits, including SHA mappings and any `KNS` compatibility changes.
- Preserve `KUN` / `KMK` attribution in imported source; do not re-mark every cherry-picked line `KNS`.

Rules and any available porting guide live under `.agents/skills/` in the integrated codebase; use them only if they exist and match this repository's configuration.

---

## Common issues

- **Gradle OOM** – the daemon uses `-Xmx4g` (`gradle.properties`); run `./gradlew --stop` and retry.
- **Spotless failures** – run `spotlessApply`, then `spotlessCheck`.
- **SQLDelight errors** – run `./gradlew :data:generateSqlDelightInterface` after schema changes (see [Database](#architecture)).
- **Missing `google-services.json` / `client_secrets.json`** – these may be supplied through CI secrets and build-time configuration. Do not expose their contents; confirm requirements for the selected variant and this repository's workflows.
- **First build is slow** – it downloads ~1 GB of dependencies; later builds use the Gradle cache.
