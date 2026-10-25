# Komi-kun foundation integration — progress record

Checkpoint for the rebuild of **xkana-shii/komikku** on official Komikku with Komi-kun's architecture.
Read this before continuing the integration or starting feature work in a new session.

## Objective

Replace the personal fork's foundation with the latest official Komikku `master` plus all relevant
Komi-kun functional/architectural changes (Komi-kun wins on conflicts), while keeping this
repository's identity, signing, release infrastructure and secrets. Personal features are **not**
migrated from the old `main`; they will be re-implemented one by one from `features.md`.

## Pinned revisions

### #70 follow-up integration (2026-10-24)

Komi-kun squash `32914fb3ed3f960bbc7e627f29fcb9166a229426` is being integrated on a feature branch.
It ports the TachiyomiSY/Mihon commits recorded as “ported in #70”, including delegated sources,
metadata editor/tracker improvements, Compose downloads, subscriber-gated feeds and merged-entry
views. Fork migrations 57 (KNS storage) and 58 (bookmark-color updates view) are deliberately
preserved; the imported view and editable-manga changes are appended as KNS-compatible migrations
59 and 60. Commit `f5c71cdddc` records the local integration. SQLDelight generation, migration
verification, Spotless, `:app:compileDebugKotlin` and focused `:data:testDebugUnitTest` passed;
the full unit-test suite remains a follow-up verification task.

| What | Ref | SHA |
|------|-----|-----|
| Old personal `main` (= `origin/main` at start) | `main` | `0ff967c77feab85bc77b91f1350bb789264388e9` |
| Permanent backup of it | `backup/pre-komikun-foundation-20261020` | `0ff967c77feab85bc77b91f1350bb789264388e9` |
| Official Komikku | `komikku/master` (komikku-app/komikku) | `1656575e8fdaaef010c652f9758130c2a01d8890` |
| Komi-kun (initial integration) | `komikun/master` (komi-kun/komi-kun) | `2c6743696d745e25703822f82cf74f96a45bc61b` |
| Komi-kun (follow-up batch, fetched 2026-10-20) | `komikun/master` | `9215170d59b7bd99c5f9fca4396ebe314f66efa2` |
| Komikku/Komi-kun merge base | — | `43e92b62949a33a672f16a6a084f86c54b6e7f10` |
| Mihon (cherry-pick log only) | `mihon/main` | `487c313d31ae9cf746a5d324550fdbae5f6c7b99` |
| TachiyomiSY (cherry-pick log only) | `tachiyomiSY/master` | `5a469745d39e8f8cea9a1669bea6108cd71dc951` |

> **SHA note:** after the initial integration the branch's committer identity was rewritten
> (`git filter-branch`; old tip `020bdb4899`, new tip `775f85b9ef`, identical tree). Integration and
> `KNS` SHAs in the tables below that predate the follow-up batch are the **pre-rewrite** values; find
> the current commit by title (`git log --oneline --grep='<title>' codex/komikun-foundation`).
> `cherrypick_log.md` is regenerated and shows the current SHAs.

Integration branch: **`codex/komikun-foundation`**, created from `komikku/master` `1656575e8f` (not
from the old `main`). The old `main` is untouched; nothing from it was merged or cherry-picked.
Only repository configuration was read from it (application id, version line, workflows/secret
names, README, `bump_version.sh`/`bump.yml`, `features.md`).

## History analysis

- Komi-kun is 32 commits ahead of the merge base (31 non-merge + merge `6dd80a5155`). The first,
  `5d0a67227d`, is a 1,422-file squash ("Port SyncYomi v2 + Mihon 2026-06→10 backlog") containing
  Metro DI, AndroidX ViewModels, the async SQLDelight/AndroidX driver stack, the Mihon schema
  rework (migrations 47–53), SyncYomi v2, WebGPU/imagedecoder, the source-manager and extension-store
  rework, Apollo trackers, Gradle isolated projects and the build-logic updates.
- Komikku is 10 commits ahead of the merge base. All of them are represented in the result through
  Komi-kun equivalents or were superseded — see "Komikku commits after the merge base" below.

## Imported Komi-kun commits (`git cherry-pick -x`)

| Komi-kun | Integration | Title |
|----------|-------------|-------|
| `5d0a67227d` | `da2188cceb` | Port SyncYomi v2 + Mihon 2026-06→10 backlog (conflicts resolved, see below) |
| `3a524dfafe` | `195f2d440c` | Repo docs + cherry-pick tooling |
| `7797e850de` | `31a4dc2357` | Sync Komikku: MangaDex header plumbing + marker cleanup (port of Komikku `1656575e8f`) |
| `aeb4642f83` | `439acfcd1a` | Migrate to gradle daemon (foojay toolchain resolver, daemon JVM pin) |
| `9101b3a7d6` | `7d3c1ca6ff` | Restore behaviour dropped in the SyncYomi v2 ViewModel rewrite |
| `fe52935fe8` | `fea403c462` | Disable the updater in debug builds |
| `67f981e197` | `ba6f20e95d` | Set UPDATER_ENABLED=false in the debug build type |
| `c3686cb6da` | `ff53e37369` | Recover stale manga covers (migration 54, `55.db`) |
| `f0cd0bb805` | `369108793d` | Clean up compiler warnings |
| `7c564d6707` | `944ea8c5a4` | Keep transient network failures out of the cover source breaker |
| `3cc6ddbcbb` | `3cc4f44455` | Port Mihon WebView/download fixes, Time Format setting, translations |
| `1c0ca6e2a7` | `724fb8ccfb` | Translation backlog, pruned strings, app-locale timestamps |
| `0d9dcfa4e4` | `73e26b18a7` | Refresh source resolution when extensions load |
| `8dc87533d5` | `0a798e50df` | Don't block library loading on the extension scan |
| `f9a1bf398f` | `3e6b4c63ff` | Prevent R8 stripping delegated source constructors |
| `e3a7f715b1` | `f3b73d8ed4` | Sync: preserve manga category associations |
| `3991bd8537` | `3fae16ceed` | Sync: silent unfavoriting, WebDAV/Drive wipes, reconciliation |
| `c58381c964` | `332b5c838e` | Sync: cursor commit timing, tracking triggers |
| `181a16b797` | `aee845c58c` | Sync: manga flags, reading progress, preference reconciliation |
| `249ae243b1` | `e5d58abdf6` | Sync: cross-device category identity, prevent category wiping |
| `c446922669` | `c8c54f374e` | Keep the updating entry in the progress notification |
| `224a1f36b5` | `820c1e47f3` | Sync: drop chapters tombstoned on the server |
| `b0718fef5f` | `7f2d17cb6d` | Sync: manual force-full sync action |
| `c96b78491f` | `225acb44c4` | Sync: include favorites with a null favorite timestamp |
| `7d83d351e4` | `43d6624184` | Port Mihon cover/library fixes, split-tall-images removal, webgpuviewer 53 |
| `2c6743696d` | `7b920332fe` | Keep the grid fast scroller in sync with the list |

26 of 31 non-merge commits imported; all picks after the first applied without conflicts.

### Follow-up batch (Komi-kun after `2c6743696d`)

Five newer commits were reviewed on 2026-10-20 (`2c6743696d..9215170d59`). Three functional ones were
picked with `git cherry-pick -x` onto `775f85b9ef`, in upstream order:

| Komi-kun | Integration | Title |
|----------|-------------|-------|
| `eece71d0e3` | `b152f459a2` | Sync: stop category tabs flickering and losing the selection on restore (single-transaction `restoreCategories`, pager realigned by category id, `CategoryRepositoryRestoreTest`) |
| `a52730100f` | `c93caf330b` | Sync: user-confirmed force-push repair for stale SyncYomi v2 devices (resumable `forcePushPending`, disabled sections preserved from the server snapshot, version-winner chapter merge; `SyncForcePushTest`, `SyncSettingsTest`) |
| `6611a4dbb5` | `a3ccc29298` | Sync: restore chapter metadata (name, scanlator, number, fetch date) on existing chapters; ordinary restores keep the local fetch date (`RestoreRepositoryChapterMetadataTest`) |

Then, at the maintainer's request, the fifth commit:

| Komi-kun | Integration | Title |
|----------|-------------|-------|
| `9215170d59` | `ff21b56966` | Backup: feed identity by source + global + saved search; extension-store restore merges into the local store (blank signing key / null list URL keep local values, never downgrade to legacy) (`FeedRestorerTest`, `ExtensionStoreRestorerTest`) |

All four applied without conflicts. The only file they touched that carries a `KNS` delta
(`i18n-kmk/.../base/strings.xml`) auto-merged with the delta unchanged; every other touched file is
byte-identical to Komi-kun's. No `.sqm` was added (only `.sq` queries changed), so the schema version
stays 56. No compatibility commit was needed.

Not imported from this batch:

| Komi-kun | Status | Reason |
|----------|--------|--------|
| `9853e52eb3` | `X` (skipped) | OpenCode automated PR-review workflow (`.github/workflows/opencode_review.yml`, its script/config and docs) — Komi-kun repository infrastructure, not app functionality; this fork's workflows stay unchanged. |

## Skipped Komi-kun commits

| Komi-kun | Reason |
|----------|--------|
| `d2bad54d7f` | Nightly-only release path + `nightly-release` skill — Komi-kun release infrastructure; this fork keeps `v*` releases and the preview repository. |
| `bf9ab9f1ef` | Release-note link fix for Komi-kun's workflows; the fix itself was adapted into this repo's `build_release.yml`/`build_nightly.yml` (`86b5058636`). |
| `b7b57324ba` | "Komikun" branding: asset names, `rootProject.name`, keystore alias rename, docs. |
| `64fb7b9bf5` | Komikun launcher icon. |
| `17a11a16f9` | Switches MyAnimeList to Komi-kun's own OAuth client (`komikun://` redirect) and drops tokens from other clients, which would log existing users out. Komikku's client is kept. |
| `6dd80a5155` | Merge commit; its content (`fe52935fe8`, `67f981e197`) was picked individually. |
| `9853e52eb3` | OpenCode automated PR-review workflow (follow-up batch, see above). |

These rows are marked `X` with notes in `cherrypick_log.md`.

## Conflict resolutions (first pick, `5d0a67227d` onto Komikku `1656575e8f`)

Every file Komikku changed after the merge base was classified against the Komi-kun commit's tree:

- **Conflicted → Komi-kun's complete file:** `app/build.gradle.kts`, `CategoriesRestorer.kt`,
  `MangaDex.kt`, `exh/md/handlers/PageHandler.kt`, `core/common/.../NetworkHelper.kt`,
  `MangaMergeRepositoryImpl.kt`, `migrations/47.sqm`, `gradle/androidx.versions.toml`,
  `gradle/libs.versions.toml`, `source-api/.../HttpSource.kt`.
- **Komi-kun deletions kept:** `app/src/main/java/eu/kanade/tachiyomi/di/AppModule.kt` (replaced by
  Metro `mihon/app/di/AppBindings.kt`), `data/.../mangas.sq` (renamed `manga.sq`).
- **Clean merge but semantically conflicting → Komi-kun's file:** `migrations/28.sqm`, `34.sqm` and
  the CI verify steps in `build_pull_request.yml`/`build_push.yml` (Komikku `00b9be1ff7` rewrote them
  for a schema without `mangas_categories.last_modified_at`; Komi-kun keeps the column).
- **MangaDex files** (`exh/md/*`) left at Komi-kun's state so Komikku `1656575e8f` arrives through
  Komi-kun's own port `7797e850de`.
- **Kept from Komikku (nonconflicting):** removal of the stale requery ProGuard rule (Komi-kun drops
  it as well in `7797e850de`).

Result check: after `7797e850de` the tree was byte-identical to Komi-kun's; the final tree differs
from `komikun/master` only in the skipped commits and the `KNS` commits listed below.

## Komikku commits after the merge base

| Komikku | Outcome |
|---------|---------|
| `70987d3cb7`, `8efcaecbc5`, `a7b09ce838`, `2c2476cda4` (AndroidX bundled / SQLCipher driver, busy timeout, "database is locked") | Superseded: Komi-kun's `AppBindings` has the same driver setup (bundled driver, SQLCipher AndroidX driver, per-connection `busy_timeout`, WAL). |
| `c9b72c8e2c` (orphan cleanup as `47.sqm`) | Replaced by Komi-kun's `47.sqm`; the same orphan deletes run in Komi-kun's `49.sqm`. No released Komikku build contains it (newest preview `r10617` predates it). |
| `00b9be1ff7` (migration verification in CI) | CI step present via Komi-kun's workflows; its `28.sqm`/`34.sqm`/`mangas.sq` edits not kept (see above). |
| `d29c54404e` (read inserted ids in a transaction) | Superseded: Komi-kun's repositories use `INSERT … RETURNING` inside `transactionWithResult`. |
| `1656575e8f` (MangaDex header plumbing) | Present through Komi-kun `7797e850de`. |
| `c10081a5d6`, `411523a2a0` (revert MangaDex cover interceptor changes) | **Pending.** They conflict with Komi-kun's `NetworkHelper`/`HttpSource`, so Komi-kun's files were kept per the precedence rule; Komi-kun has not ported them yet either. The global `uploads.mangadex.org` cover interceptor therefore remains. Revisit when Komi-kun syncs them. |

## KNS commits (this fork)

| Commit | Change |
|--------|--------|
| `79f7a0b5a7` | Application identity: `app.komikku.kns`, versionCode 172 / versionName 1.24.8, updater repos (`xkana-shii/komikku`, `xkana-shii/komikku-preview`), About GitHub link, "Komikku" product name in strings/User-Agents. |
| `86b5058636` | CI: stable non-prerelease `v*` releases, `-PdevSecret`, preview dispatch re-enabled for `xkana-shii/komikku-preview`, `bump.yml` + `bump_version.sh` (+ test), repo links, dependabot → `main`. |
| `0c5c64e88c` | `49.sqm` fix for fresh-installed Komikku databases + `FreshKomikkuInstallMigrationTest`. |
| (docs commit) | AGENTS.md, FORK.md, CI_SETUP.md, GOOGLE_SERVICES_SETUP.md, CONTRIBUTING.md, README.md, CHANGELOG.md, skills, cherry-pick log, `features.md`, this file. |

Signing is unchanged in substance: Komi-kun's Gradle signing (`MIHON_GITHUB_RELEASE` +
`storeFileBase64`/`storePassword`/`keyAlias`/`keyPassword`) reads the same four secrets
(`SIGNING_KEY`, `KEY_STORE_PASSWORD`, `ALIAS`, `KEY_PASSWORD`) this repository already uses.

## Database reconciliation

- Schema version **56** (migrations `1.sqm`–`55.sqm`, contiguous) at the end of the integration;
  Komi-kun #60 added `56.sqm` and KNS batch 1 added `57.sqm` (schema 58). `data/` is identical to Komi-kun's
  except the `49.sqm` fix and the new test.
- **Komikku stable → foundation** (schema 47 → 56): supported. Komi-kun's chain assumed
  `mangas_categories.last_modified_at` exists, but Komikku's fresh-install schema never had it (only
  `28.sqm` adds it), so fresh-installed Komikku databases failed in `49.sqm`. Fixed with
  `NATURAL LEFT JOIN (SELECT 0 AS last_modified_at)`, which is identical for databases that have the
  column.
- **Komikku `master` builds at schema 48** (Komikku's own `47.sqm`): unsupported in place; no released
  Komikku build is affected, and Komikku's `app.komikku` cannot upgrade into `app.komikku.kns` anyway.
- Official Komikku installs use a different application id, so they can only move to this app
  through backup/restore, never through an in-place database upgrade.

### Old-fork database compatibility (BLOCKER before any release or `main` replacement)

The installed app today is the **old personal fork** (`app.komikku.kns` v1.24.8, versionCode 172),
whose database followed its own lineage: its `47.sqm`–`56.sqm` add bookmark colors, chapter
blacklists, scanlator priority/range rules, hierarchical categories, category tracker status /
update intervals / dates and a `library_visibility_revision` table, ending at **user_version 57**.
The foundation's schema is 56 with different SQL for the same numbers.

`sqldelight-androidx-driver` 0.2.2 only migrates when `user_version < schema.version`; with 57 > 56 it
runs **no migration and raises no error** and opens the old database as-is. The new code would then
query tables/columns that do not exist (crashes), and the version stamp would also hide the mismatch
from any future migration. **Installing a foundation build over the existing app is not safe**, and
once a foundation release with versionCode > 172 is published, the old app's in-app updater would
offer it.

**Status (KNS batch 1, see `docs/kns-feature-implementation.md`):** the database part is implemented.
`OldForkDatabaseConverter` detects the old lineage (`chapter.state_version`) and converts it into the
current schema before the driver opens it (tables rebuilt with their rows, sync columns renamed,
favorite/date-added semantics and the old chapter sort bits remapped, Fillermarks → blacklist keys,
category uids assigned, `user_version` set). `OldForkDatabaseConverterTest` covers it against the old
fork's schema (`data/src/test/resources/oldfork/old-fork-schema-57.sql`). Still open before an
in-place upgrade can be called safe: a test with a real device database, the old fork's
SharedPreferences (Smart Merge overrides, default chapter flags in the old bit layout, other feature
settings) and its backups (old chapter-flag layout, #113). Until then the rules below still apply.

Until an explicit old-lineage migration is implemented and tested:

1. Do not publish a `v*` release from the foundation or replace `main` with it.
2. Test with the debug (`app.komikku.kns.dev`) or nightly (`.beta`) variants, which install side by
   side with separate databases.
3. To move a library, create a backup in the old app and restore it into the new build. Backup
   compatibility (checked by comparing `@ProtoNumber`s of all 14 backup models):
   - Standard fields restore normally.
   - `BackupManga` **#113** is `lastUpdate` in old-fork backups but `nextUpdate` (Mihon's numbering)
     in the foundation, so restored entries get their last-update time as next-update time and are
     treated as due at the next library update — not destructive, recomputed afterwards. Old #114
     (`nextUpdate`) and #115 (`fetchInterval`) are ignored.
   - Old-fork-only fields are ignored until their features exist again: `BackupCategory`
     #901 `parentId`, #902 `trackStatus`, #903 `updateInterval`; `BackupChapter` #14 `bookmarkColor`,
     #15 `fillermark`; `BackupManga` #613 `categoryIds`, #614 `scanlatorPriority`,
     #615 `blacklistedChapters`, #616 `scanlatorRangeRules`, #618 `smartScanlatorMergeOverride`,
     #619 `hideBlacklistedChapters`, #620 `scanlatorSettingsPresent`, #806 `customGenreSet`.
   - **When re-implementing those features, reuse these field numbers** so old backups restore
     completely.
4. Options for a real in-place path (future, explicit decision): a `KNS` open-time migration that
   recognises the old lineage (user_version 57 + its tables) and converts it into the foundation
   schema after the corresponding features exist; or ship under a new application id.

## Validation

Environment limits of the session that built this branch: Maven Central, Google Maven,
`dl.google.com` and the Gradle plugin portal were blocked by the sandbox network policy, so **no
Gradle task could run** (no spotless, SQLDelight generation/verification, unit tests or APK builds).
The repo also could not be pushed from there (no push credentials).

Performed instead:

| Check | Result |
|-------|--------|
| Final tree vs `komikun/master` | Only intended differences (skipped commits, identity, CI, `49.sqm` fix, docs) |
| Komikun branding/repo references in runtime code (`git grep`) | None left |
| Migration chain contiguity | `1.sqm`–`55.sqm`, schema 56 |
| Offline SQLDelight-style verification (SQLite 3.45; `.sq` schema with type adapters stripped) | Snapshots `49.db`, `50.db`, `51.db`, `52.db`, `53.db`, `55.db` → 56 match a fresh create |
| Komikku stable schema 47 (fresh-install and upgraded-through-28 shapes) with seeded data → 56 | Matches fresh create; favorites/dates, categories + uids, manga↔category links, chapters, read/bookmark flags, history, tracks (Kitsu rescale), excluded scanlators kept; orphans removed; no FK violations |
| `FreshKomikkuInstallMigrationTest` scenario replayed in SQLite | Passes (column dropped → 0; column kept → value preserved) |
| `bump_version_test.sh` and a dry run on the new `app/build.gradle.kts` | Pass (172→173, 1.24.8→1.24.9) |
| Workflow/issue-template YAML parse | All valid |
| `cherrypick-log` unit tests (21) and regeneration | Pass; Komikku 10/10 `O`, Komi-kun 26 `O` / 5 `X` |
| `git diff --check` | Clean |

**Not yet run — required before treating the branch as verified** (run on a machine with the
Android toolchain, e.g. the local clone):

```bash
./gradlew spotlessApply          # then commit any formatting changes
./gradlew spotlessCheck
./gradlew :data:generateSqlDelightInterface
./gradlew :data:verifyDebugDatabaseMigration
./gradlew testDebugUnitTest      # includes data MigrationTest + FreshKomikkuInstallMigrationTest
./gradlew assembleDebug
./gradlew assembleRelease        # signs with the debug key without keystore.properties
git diff --check
```

The Komi-kun code itself was built and tested by Komi-kun's CI; the risk added here is limited to the
`KNS` commits (identity strings/ids, `getGithubRepo`, comment-only changes, `49.sqm`, the new test).

### Local Gradle validation (follow-up batch, 2026-10-20, Windows, JDK 21)

Run on `codex/komikun-foundation` after the three picks and again after `9215170d59`; every task exited 0 both times:
`spotlessApply` (no changes), `spotlessCheck`, `:data:generateSqlDelightInterface`,
`:data:verifyDebugDatabaseMigration`, `testDebugUnitTest` (app 155, data 30, domain 119 tests; 0
failures; 1 pre-existing `@Disabled` `Tester.stripBackup`), `assembleDebug`, `assembleRelease`
(debug-key signing), `git diff --check`. This also covers the earlier "not yet run" list above.

## External follow-ups

- `xkana-shii/komikku-preview`'s `build_app.yml` runs `assemblePreview` and signs from
  `apk/preview`; the foundation renames the build type to `nightly`. Update it before dispatching a
  preview of the foundation (details in `CI_SETUP.md` §4.1).
- `build_push.yml` and `build_nightly.yml` trigger on `master` (pre-existing); switch to `main` if
  push CI / in-repo nightlies are wanted.

## Next steps

1. Push `backup/pre-komikun-foundation-20261020` (→ `0ff967c77f…`) and `codex/komikun-foundation`
   to `origin`; verify both remote SHAs.
2. Run the Gradle validation above; fix and commit anything it finds (e.g. a `spotlessApply` diff).
3. Update the preview repository's workflow (above) if previews of the foundation are needed.
4. Start feature work from `features.md`, one feature per branch off `codex/komikun-foundation`,
   reusing the old backup field numbers listed above.
5. Before any release or replacing `main`: resolve "Old-fork database compatibility" and get explicit
   approval for the `main` update (fast-forward/reset to the foundation's exact commit; never merge
   the old `main`).
6. After `main` is replaced, switch `target_branch` in `.agents/skills/cherrypick-log/config.json` to
   `main` and the docs' "foundation branch" wording to `main`.
