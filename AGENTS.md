# komi-kun – AI Agent Guide

**komi-kun** is a personal vendoring fork of **Komikku** (app name "Komikun"; `applicationId`: `app.komikun`, debug `app.komikun.dev`). Komikku is itself an Android manga reader (min SDK 26, target SDK 36, JVM target 17 / Kotlin) forked from **Mihon** + **TachiyomiSY**. Komikku is this repo's direct upstream/base; Mihon and TachiyomiSY are Komikku's upstreams (and therefore also reachable from here).

Features: configurable reader, downloads/offline reading, trackers (MyAnimeList, AniList, Kitsu, MangaUpdates, Bangumi, Comick, Hikka, Kavita, Komga, MangaBaka, MangaDex, Shikimori, Suwayomi), recommendations, metadata editing, library categories/tags/filters, multi-source browsing and feed tabs.

### Upstreams

| Upstream | Code marker | Strings |
|----------|-------------|---------|
| Mihon | none (base code) | `MR` (`i18n/`) |
| TachiyomiSY | `// SY -->` … `// SY <--` (legacy `// EXH` blocks also come from SY) | `SYMR` (`i18n-sy/`) |
| Komikku (this fork's base upstream) | `// KMK -->` … `// KMK <--` | `KMR` (`i18n-kmk/`) |
| **komi-kun** (this repo) | `// KUN -->` … `// KUN <--` | `KMR` (`i18n-kmk/`) |

Markers record **divergence from upstream**, never upstream code itself: Mihon is the base (unmarked); `// KMK` / `// SY` mark code inherited from the Komikku / TachiyomiSY upstreams; `// KUN` marks this fork's own code.

---

## Mandatory rules for AI agents

**Read this section before every change.** These rules override shortcuts (e.g. copying nearby `MR` imports or only running `compileDebugKotlin`).

### Keep this guide current

`AGENTS.md` documents the repo **as it is right now** (the tip of `master`). It is living documentation: whenever a change makes any statement here false — architecture, DI, module layout, markers, i18n, build flags, key files, workflows — update this file in the **same** change/PR. If you are on a feature branch that diverges from `master`, describe the branch's state (it is the future `master`). Prefer the current state over history, and delete guidance that no longer applies instead of leaving it stale.

### Git

| Rule | Required behavior |
|------|-------------------|
| Branch | Create a **feature branch** for the task (`git checkout -b <type>/<short-description>`, e.g. `feat/manga-recommendations`). |
| Commit | **OK** on a feature branch when work is ready. **Never** commit directly to `master` / `main` unless the user explicitly asks. |
| Push | **OK** to push the **current feature branch** when work is ready. **Never** push to `master` / `main` unless the user explicitly asks. |

Before `git push`, confirm the current branch is not `master` or `main` (`git branch --show-current`).

### Fork markers

Markers record **what diverges from upstream** — they never mark upstream code itself. Every komi-kun addition or modification to existing code **must** be wrapped:

```kotlin
// KUN -->
// your code here
// KUN <--
```

| Code origin | Marker |
|-------------|--------|
| komi-kun's own code (this repo) | `// KUN -->` … `// KUN <--` |
| Komikku upstream | `// KMK -->` … `// KMK <--` |
| TachiyomiSY upstream | `// SY -->` … `// SY <--` (legacy `// EXH` blocks also come from SY) |
| Mihon (base upstream) | none |

- Use `// KUN` for this fork's own new code. **Never** reuse `// KMK` for komi-kun code: `// KMK` means the code came from the **Komikku** upstream.
- Never label code that is not from TachiyomiSY as `// SY` (or its legacy `// EXH` form), and never label code that is not from Komikku as `// KMK`.
- Keep existing upstream `// KMK` / `// SY` / `// EXH` blocks intact; when changing code inside one, wrap your change in a nested `// KUN` block.
- Markers apply to `.sq` queries and mappers too (use `-- KUN -->` / `-- KUN <--`).

### Internationalization (strings)

| String kind | Module | Resource class | Base folder only |
|-------------|--------|----------------|------------------|
| **komi-kun / Komikku** new features (library-update errors, WebDAV, Discord, etc.) | `i18n-kmk/` | **`KMR`** | `i18n-kmk/src/commonMain/moko-resources/base/` |
| Mihon upstream | `i18n/` | `MR` | `i18n/src/commonMain/moko-resources/base/` |
| TachiyomiSY upstream | `i18n-sy/` | `SYMR` | `i18n-sy/src/commonMain/moko-resources/base/` |

**Hard rules:**

- **All new strings go to `i18n-kmk/`** (`import tachiyomi.i18n.kmk.KMR`). `MR` / `SYMR` are upstream-owned; reuse existing entries, but only add or change them when syncing upstream.
- **Weblate is not connected to this repo.** Translation content is maintained here, not generated: non-`base` locale files in `i18n-kmk/`, `i18n/`, and `i18n-sy/` are normal source files.
  - **Port upstream translation commits** (“Translations update from Hosted Weblate” from Mihon/TachiyomiSY/Komikku) like any other upstream commit. Never skip them or mark them `X` — they are the only source of refreshed translations.
  - **Translate new strings yourself**: when you add a `base` string, add its translation to the non-`base` locales you can translate confidently (missing keys fall back to the base language, so skipping a locale is safe). Do this as part of the feature, not as a follow-up.
  - Hand-edit an existing translation only to fix an obvious error.
- Inside a `// KUN` block (or Komikku's own `// KMK` behavior), default to **`KMR`** even if nearby code imports `MR`.

**Porting translation commits:** upstream locale files and this fork's have diverged in both directions (the fork still has Komikku-era keys that Mihon removed, and is missing newer upstream keys). Resolve conflicts per file by keeping upstream's values for the keys it has, dropping entries whose string no longer exists in the module's `base/`, and re-appending fork-only entries that still exist in `base/` — `scripts/resolve_i18n_union.py` in the `upstream-port` skill automates this. Never drop a translation for a string that still exists in `base/`. Validate that the XML is well-formed and that no key from the fork's `base` lost its translation.

**Examples (→ `i18n-kmk`, not `i18n`):** library update error UI, sync-before-update messages, WebDAV/Discord settings, updater notifications, `mihon/feature/*` screens.

**Self-check before finishing:** `git diff` must not hand-add `<string name="…">` / `<plurals name="…">` entries under non-`base` locales, **except** translations that accompany a new base string or a ported upstream translation commit. New base strings should ship with translations where you can provide them.

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
- **Toolchain:** JDK 21 to build (matches `.github/.java-version`), bytecode targets Java 17, Gradle 9.8.1, compileSdk 37.2

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

**DI** – [Metro](https://zacsweers.github.io/metro/), not Hilt/Injekt. The graph is `mihon/app/di/AppGraph.kt` (`@DependencyGraph`) with providers/bindings in `mihon/app/di/AppBindings.kt`; it is built in `App.kt` (`createGraphFactory<AppGraph.Factory>().create(...)`) and reached as `context.appGraph` or by injecting `AppGraph`. Most classes need only `@Inject` on the constructor (interactors, repositories, services); use `@Provides`/`@Binds` in `AppBindings` for third-party or interface types, and `@ContributesIntoMap` / `@ContributesIntoSet` for maps/sets. Parameterised types use `@AssistedInject` + `@AssistedFactory`. Extension-facing code still resolves through the Injekt interop bridge (`mihon/app/di/injekt/`).

**UI & navigation** – Voyager `Screen`s in `eu.kanade.tachiyomi.ui.*`, composables in `eu.kanade.presentation.*`. Base type: `eu.kanade.presentation.util.Screen` (in `Navigator.kt`). State lives in **Metro ViewModels** obtained with `metroViewModel<VM>()` or `assistedMetroViewModel<VM, Factory> { create(...) }`; they extend `androidx.lifecycle.ViewModel`, expose a `StateFlow` / `MutableStateFlow` (often a `State` data class) and run work in `viewModelScope`. Example: `NewUpdateScreen` + `NewUpdateViewModel` in `app/src/main/java/eu/kanade/tachiyomi/ui/more/`.

**Coroutines** – In ViewModels use `viewModelScope`. Helpers in `tachiyomi.core.common.util.lang`: `withIOContext`, `withUIContext`, `CoroutineScope.launchIO` (on a given scope). The top-level `launchIO` / `launchNow` run on **GlobalScope** — avoid them for lifecycle-bound work. `rememberCoroutineScope()` is fine in Compose; long-lived services may own their own scope (`AppCoroutineScope` in `mihon.core.metro`).

**Activities (not Voyager)** – `MainActivity` (shell / Voyager host), `ReaderActivity` + `ReaderViewModel`, `WebViewActivity`, `UnlockActivity`, OAuth login activities, `DeepLinkActivity`. Reader: `ReaderActivity.newIntent(context, mangaId, chapterId)`. Web: both `WebViewScreen` (Voyager) and `WebViewActivity.newIntent(...)`.

**Domain / data** – One class per operation under `domain/src/main/java/tachiyomi/domain/*/interactor/` (verb names like `GetTracksPerManga`, `HideCategory`; no `*Interactor` suffix), annotated `@Inject` so Metro resolves it directly — no module registration. App-specific cases go in `app/src/main/java/eu/kanade/domain/…/interactor/`. `Manga` / `Chapter` are domain models; `SManga` / `SChapter` are source-layer types — convert at the boundary (`SManga.toDomainManga()`, `Manga.toSManga()`, `Chapter.toSChapter()`).

**Database** – SQLDelight in `data/src/main/sqldelight/tachiyomi/`. For schema changes:
1. Add a new `migrations/*.sqm` file
2. Update the `.sq` queries
3. Update the `*RepositoryImpl` mapper
4. Regenerate: `./gradlew :data:generateSqlDelightInterface` (or any compile that touches `:data`)

**Preferences** – `eu.kanade.domain.*.service.*Preferences` (e.g. `SourcePreferences.relatedMangas()`). App preference migrations: `app/src/main/java/mihon/core/migration/migrations/` (`mihon.core.migration.Migration`).

**Images** – Coil 3 (`coil3.*`, `context.imageLoader`), configured in `App.kt`. No Glide/Picasso.

**Logging** – komi-kun / Komikku code: `xLogE()` / `xLog()` from `exh.log`. Mihon code: `logcat { }` from `tachiyomi.core.common.util.system`. Avoid raw `android.util.Log`.

---

## Extensions & sources

- Catalog sources: installable APK extensions (not in this repo).
- In-repo: delegated sources and metadata in `exh/` (E-Hentai, NHentai, MangaDex, `exh/recs/`).
- `source-api`: `eu.kanade.tachiyomi.source.*` — **avoid breaking extension ABI**.

---

## Adding a komi-kun feature

1. **Interactor:** `domain/src/main/java/tachiyomi/domain/<area>/interactor/MyFeature.kt`, annotated `@Inject` (Metro resolves it; add a `@Provides` / `@Binds` to `mihon/app/di/AppBindings.kt` only if it needs manual wiring)
2. **UI:** Metro ViewModel + `Screen` under `app/src/main/java/eu/kanade/tachiyomi/ui/` (see [Architecture](#architecture))
3. **Strings:** `i18n-kmk/src/commonMain/moko-resources/base/strings.xml`, referenced via `KMR`
4. **Markers:** wrap edits to existing files in `// KUN -->` / `// KUN <--`

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
./gradlew spotlessApply                    # format
./gradlew spotlessCheck                    # CI gate
./gradlew :app:compileDebugKotlin          # compile-only check
./gradlew assembleDebug                    # debug APK
./gradlew assembleNightly                  # nightly (beta) APK, CI equivalent
./gradlew assembleNightly -Pinclude-telemetry -Penable-updater  # full CI build
./gradlew assembleRelease                  # release APK
./gradlew testDebugUnitTest              # CI unit tests
./gradlew test                             # all unit tests
./gradlew installDebug                     # install to device/emulator
./gradlew :data:generateSqlDelightInterface  # after .sq / .sqm changes
```

**Workflows** (`.github/workflows/`):
- `build_pull_request.yml` – PR validation: dependency review → wrapper validation → `spotlessCheck` → `assembleNightly` → `testDebugUnitTest` → APK signing (authorized forks)
- `build_push.yml` – push to `master`: full build + signing
- `build_release.yml` – release builds from `v*` tags
- `build_nightly.yml` – manual nightly builder
- `build_benchmark.yml` – manual benchmark builder

---

## Tests

- **Framework:** JUnit (Jupiter) + Kotest assertions + MockK
- **Locations:** `domain/src/test/`, `app/src/test/` (e.g. `app/src/test/java/mihon/core/migration/MigratorTest.kt`). No broad UI test suite — focus on domain and critical logic.
- **Single class:** `./gradlew :domain:testDebugUnitTest --tests "*.ClassName"`

---

## Key files

- `App.kt` – Metro graph bootstrap, logging setup, Coil initialization
- `MainActivity.kt` – Voyager navigation host
- `app/src/main/java/mihon/app/di/AppGraph.kt` – Metro `@DependencyGraph` (the DI graph)
- `app/src/main/java/mihon/app/di/AppBindings.kt` – `@Provides` / `@Binds` / contributed bindings
- `buildSrc/src/main/kotlin/mihon/buildlogic/BuildConfig.kt`, `AndroidConfig.kt` – flags, SDK versions
- `app/build.gradle.kts`, `settings.gradle.kts`
- `CONTRIBUTING.md` – prerequisites and contribution process

---

## Upstream cherry-pick log

`cherrypick_log.md` (repo root) lists upstream commits since 2024-01-01 with their cherry-pick status on `master`. The main upstream is `mihon/main`, the additional upstream is `tachiyomiSY/master`, and Renovate commits are excluded. All of these are set in `.agents/skills/cherrypick-log/config.json`, and `--since` overrides the start date.

- The list is generated by `.agents/skills/cherrypick-log/update_cherrypick_log.py`.
- `?` rows are reviewed with the `cherrypick-log` skill.
- `X` means "won't pick". The maintainer sets it by hand, and the skill sets it for version-bump-only commits.
- Commits that cancel each other out (revert chains the fork has none of) are flagged `?` by the script with a `Cancels out (script):` note, for review.
- Pre-2024 rows at the end come from a one-off pass and may be deleted by hand.
- Never hand-edit columns other than Status and Notes; use the script's `mark` command.

Rules, commands and the porting guide: `.agents/skills/cherrypick-log/README.md`.

---

## Common issues

- **Gradle OOM** – the daemon uses `-Xmx4g` (`gradle.properties`); run `./gradlew --stop` and retry.
- **Spotless failures** – run `spotlessApply`, then `spotlessCheck`.
- **SQLDelight errors** – run `./gradlew :data:generateSqlDelightInterface` after schema changes (see [Database](#architecture)).
- **Missing `google-services.json` / `client_secrets.json`** – these are CI secrets; builds without `-Pinclude-telemetry` don't need them.
- **First build is slow** – it downloads ~1 GB of dependencies; later builds use the Gradle cache.
