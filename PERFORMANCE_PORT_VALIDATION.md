# TachiyomiSY performance port validation — 2026-10-06

Branch: `codex/sy-performance-port`. Checkpoint `d872e456a57d9ed28e8b682342ff0c2c59c4f5f2` and all subsequent merges/commits were preserved. No reset, rebase, amend, replacement branch, or repeated cherry-pick was used. The intermediate continuation changes were committed by the user while validation was in progress; the final pass continued from `fe2c91553d`. The final follow-up commit is reported in the accompanying completion message.

The implementation reference remains TachiyomiSY `14648c7cf0aa84e5a35d48de9dbf1386df6cca42`. No additional performance-history search or expansion of the closed set was performed.

## Closed-set disposition

All rows below refer to adaptations already present in the checkpoint, with the continuation corrections called out explicitly. They were not cherry-picked again.

| Target SY commit | Mihon origin, where supplied | Final disposition |
| --- | --- | --- |
| A1 `56923c76d4` | `db22517339` | Verified resolved SQLDelight 2.3.2. |
| A2 `5d8d2ce48a` | `8c480c6355` | Verified bundled unencrypted driver and separate SQLCipher path, including Android runtime tests. |
| A3 `4552221020` | `cb0e329716` | Komikku's existing Result/mapCatching handling already accepts the new SQLite exception; no narrow legacy SQLiteException catch needed replacing. |
| A4 `925fb118af` | `f8e82b9322` | Retained the shared lock and WeakReference around both driver-creation branches. |
| A5 `2b18cd23d5` | `8e0c911f93` | Verified direct async Database APIs; added chapter-stat flow regressions and preserved cancellation during refill. |
| A6 `ccfc139fe9` | `b4a3ec0b32` | Verified RETURNING IDs and write-only paths; removed redundant inner insert transactions in saved-search/feed paths. |
| A7 `050a74c9e2` | `f87c4d55e3` | Verified native coroutine query helpers; retained Komikku's necessary library preparation/invalidation step. |
| A8 `25981280c8` | `35f6a607b8` | Verified resolved Mihon driver fork revision 0637a964a1. Intermediate driver revision was not reintroduced. |
| A9 `9c03016076` | — | Verified resolved bundled SQLite 2.7.0. |
| B1 `db5bdb9f56` | `06497622f6` | Corrected failed-chunk handling and deferred external custom metadata until commit; tested rollback, retry, cancellation, and restore contents. |
| B2 `5fc99fc96a` | `77580cd55f` | Retained post-library-restore cache invalidation and cancellation propagation. |
| C1 `592d904396` | `c612a6ca8d` | Retained data/badge separation; verified all 16 badge combinations against filter data. |
| D1 `3a18f0b8ad` | `b3e190c627` | Retained modern module; removed obsolete module; corrected Windows generated source-directory handling and documented connected-device generation. |
| D references `adb5f7338a`, `bdee775a23` | — | Reference-only generated-profile commits; no SY-generated profile data copied. |
| E1 `e6372b3751` | SY-specific | Tested stable manga/chapter identity and repeated convergence; preserved notes/memo alongside flags and custom metadata. |
| F1 `14648c7cf0` | SY-specific | Verified MoveSortingModeSettingsMigration awaits async query results inside its transaction. |

Already-present library/index/search/cache/browsing optimizations, reverted starvation/startup-cache experiments, resumable downloads, archive work, and import-only upstream commits were not re-ported.

## Database architecture and inserts

Resolved app runtime dependencies are SQLDelight **2.3.2**, `androidx.sqlite:sqlite-bundled` **2.7.0**, and `com.github.mihon.sqldelight-androidx-driver` **0637a964a1**.

Unencrypted databases use `AndroidxSqliteDriver`, `BundledSQLiteDriver`, async `Database.Schema`, foreign keys enabled, and the existing `tachiyomi.db` filename. Encrypted databases retain `AndroidSqliteDriver`, SQLCipher, `SupportOpenHelperFactory`, `Database.Schema.synchronous()`, `CbzCrypto.DATABASE_NAME`, and the existing decrypted-password path. SQLCipher is not routed through the bundled driver. Framework SQLite support can still be a legitimate transitive dependency of the encrypted path; Requery is no longer the normal driver.

The lock covers driver lookup and creation, and the WeakReference caches the created instance. Injekt still has one driver lifetime strategy. `DatabaseHandler`, `AndroidDatabaseHandler`, and `TransactionContext` remain removed from executable source. The affected code uses async awaits, coroutine query helpers, and native SQLDelight transactions.

New `insertReturningId` queries cover:

- `mangas`: MangaRepositoryImpl and MangaRestorer;
- `chapters`: ChapterRepositoryImpl;
- `categories`: CategoryRepositoryImpl and CategoriesRestorer;
- `merged`: MangaMergeRepositoryImpl;
- `saved_search`: SavedSearchRepositoryImpl and FeedRestorer;
- `feed_saved_search`: FeedSavedSearchRepositoryImpl (the table also represents feeds).

The tests insert rows, check returned IDs against retrieved rows, exercise bulk/write-only paths, check saved-search/feed deduplication, and check foreign-key cascading. No `last_insert_rowid()` or `selectLastInsertedRowId` remains in repository source. The network-manga grouped insert/select and library-update-error-message insert/select use stable logical keys inside transactions, not a separate connection-global last-row-ID lookup.

## Chapter statistics and historical schema

One-shot library reads refill missing statistics before querying. The library Flow checks for missing statistics on invalidation, refills only when needed, and lets the refill invalidation trigger the next query. The conflated query channel and existing 250 ms throttle avoid repeated work during bursts. If refill fails, the library query falls back to its live aggregates; cancellation propagates.

Regression coverage verifies changed read counts emit during a live subscription, refill does not loop, subsequent writes still emit, and recollecting unchanged data writes zero new cache rows. The existing randomized aggregate tests remain active.

Historical migration corrections were real defects: `23.sqm` and `29.sqm` lacked a comma between bookmark and fillermark aggregates; `40.sqm` spelled `coalesce` as `oalesce`. Actual SQLDelight debug and release migration verification passed.

The only available schema fixture is `28.db`. A logical comparison against the pre-checkpoint fixture confirmed that its sole schema correction was adding `chapters.fillermark INTEGER NOT NULL`, which historical migration 13 and later views already required. All table data and other schema objects were unchanged. SQLite integrity returned `ok`, and foreign-key checking returned no violations. The continuation did not reconstruct the fixture again. Migration verification exercises that fixture forward from version 28; it is not a claim that arbitrary external databases or every pre-28 installation were tested.

Fresh schema definitions were reconciled with existing migration history: category `hidden` uses the same DEFAULT/NOT NULL declaration order, and `mangas_categories` includes the timestamp column/trigger already installed by migration 28. This does not introduce a new logical schema feature or change existing user data. No new numbered migration was added. Existing current-version databases created without that unused timestamp retain their original shape until a future migration; application queries do not depend on that field.

## Restore correctness

The PR #1160 fixed thread pool, ExecutorCoroutineDispatcher, per-manga async jobs, coroutine awaitAll, and CPU-count-based progress batching are gone. `InsertTrack.awaitAll` is a repository bulk-write method, not the removed coroutine fan-out.

Normal operation restores sequential chunks of 100 entries in one transaction per chunk. Categories finish before manga/category associations. Saved searches finish before feed restoration. Progress uses AtomicInt and errors use CopyOnWriteArrayList, with progress reported after each logical chunk.

SQLDelight's actual nested transaction behavior was demonstrated by a regression: swallowing a nested exception inside the outer transaction rolls back otherwise successful siblings. `restoreBatch` therefore catches failures outside the chunk transaction. A failed chunk is rolled back and retried sequentially in individual transactions; only failing entries are reported. Successful neighbors survive, later entries continue, and valid chunks retain the fast batch path. Cancellation is always rethrown and never converted into an entry error or retried. Source names remain in manga error messages.

Custom metadata is stored outside SQLite, so its write now runs after commit. A test proves that a failed outer transaction does not publish metadata containing rolled-back manga IDs. Newer notes/memo are preserved with the selected manga version.

Database-backed tests cover manga/chapter creation, categories/associations, history, tracking, metadata, stable IDs, read/bookmark/fillermark state, version-only changes, repeated restore, and saved-search/feed deduplication and filters. Extension-store upserts use the same batch failure handling. App/source preference restore and option gating were inspected rather than exercised through an end-to-end Android backup file. BackupRestoreStatus and notifier orchestration remain intact.

DownloadCache invalidation runs after completed library restoration, including successful entries of a partially restored backup. It does not run for a preferences-only restore or after cancellation. Its failure is logged, and cancellation is rethrown. No startup cache invalidation was added.

## Library and sync

Raw downloadCount, unreadCount, and isLocal remain independent from LibraryItem.Badges. Downloaded filtering reads isDownloaded (`isLocal || downloadCount > 0`), unread filtering reads LibraryManga's unread count, and fillermark filtering reads its fillermark aggregate. The predicate does not query DownloadManager.

The library regression exercises all 16 download/unread/local/language badge combinations. Disabling badges leaves downloaded, unread, local, and fillermark outcomes unchanged; source display data and language-icon configuration remain intact. Merged manga still sum child download counts while constructing library items, before filtering; this code path was inspected and its aggregate filtering input tested. The existing potential N+1 merged lookup was deliberately not redesigned. DownloadCache.changes remains a combine input that rebuilds counts; source registration also refreshes items. No device UI test or performance timing claim is made for those paths.

SyncManager and SyncService use source + URL for manga identity and chapter URL for chapter identity. Mutable title/author/name/number do not split entries. Tests cover changed metadata, separate sources sharing a URL, newer read/bookmark/fillermark states, version-only restoration when visible state already matches, stable database IDs, and repeated merge/restore. Flags, update strategy, scheduling fields, notes/memo, and custom metadata remain supported. Sync selection compares the resulting versions, so unchanged entries stop being selected on those differences. Live remote-service credentials/network synchronization were not exercised.

## Baseline infrastructure

Only `:baseline-profile` is registered. The obsolete tracked `macrobenchmark/` module was removed; the app's existing `benchmark` build type and its CI build command remain. Target application ID comes from tested APK metadata. Non-minified and benchmark variants suppress onboarding, updater and changelog UI; normal debug/release detection is unchanged.

The Windows failure was `Illegal char <?> at index 56: C:\Users\Isabella\Documents\GitHub\komikku\app\provider(?)`. Inspection of the installed profile plugin and source-set configuration showed a copied unresolved Kotlin provider becoming a literal directory. This was a source-set integration bug, not path length or managed-device storage. The scoped fix resets generated profile/benchmark Kotlin source directories using actual paths, excluding that invalid literal. Non-minified compilation then passed. PowerShell also requires quoting the dotted `-PbaselineProfile.useConnectedDevices=true` argument, as documented.

Connected generation succeeded on the API 36 emulator against `app.komikku.kns`, using `:baseline-profile:connectedNonMinifiedReleaseAndroidTest` through `:app:generateBaselineProfile`. Both generators passed; 12 timing-only cases were intentionally skipped in profile-generation mode. Real baseline (39,545 lines) and startup (30,273 lines) profiles were written to `app/src/main/baselineProfiles/`. The obsolete `app/src/main/baseline-prof.txt`, which still named removed database-handler classes, was replaced by those generated artifacts. No generated SY profile was copied. Physical-device startup timing comparisons are outside this validation. The generated Room `getTransactionContext` reference is legitimate and unrelated to the removed custom TransactionContext class.

## Verification and cleanup

| Check | Result |
| --- | --- |
| `:data:generateSqlDelightInterface` | Passed |
| Discovered `:data:verifySqlDelightMigration` (debug + release verification) | Passed |
| `spotlessApply` then `spotlessCheck` then `assembleDebug` | Passed in that order |
| `testReleaseUnitTest` | 281 discovered: 280 passed, 1 pre-existing disabled `Tester.stripBackup`, 0 failures/errors |
| `:data:testDebugUnitTest` | All 19 passed |
| Chapter-stat / library-flag / feed tests | 12 / 3 / 2 passed, plus insert-ID and custom-metadata tests |
| Restore / sync / badge regressions | 5 / 2 / 1 passed; badge test covers 16 preference combinations |
| Android bundled fresh DB | Passed |
| Android reopen existing unencrypted DB | Passed; existing rows preserved across synchronous-to-bundled driver reopening |
| Android SQLCipher create/reopen | Passed, including shared generated-ID assertions |
| `28.db` integrity/data/schema comparison | Passed |
| App runtime dependency resolution | Verified exact target versions/revision |
| Connected Komikku baseline generation | Passed; baseline and startup profiles generated from the fork's app |
| `:app:compileNonMinifiedReleaseArtProfile` and debug rebuild with new profiles | Passed; formatting sequence repeated before the rebuild |
| `git diff --check` | Passed before final commit |

The two previously ignored feed tests returned inferred non-Unit values from runBlocking; they now explicitly return Unit and JUnit runs them. Enabling them exposed a JDBC pool setup error: enabling foreign keys on one connection was insufficient. The test driver now enables foreign keys through its connection properties. Neither test was disabled or weakened.

Temporary `port_database.py`, `port_followups.py`, `port_library_tests.py`, `repair_schema_fixture.py`, and tracked `__pycache__/*.pyc` were removed without rerunning the transformation scripts. No tracked pyc or obsolete macrobenchmark source remains. No non-base translations, unrelated features, or extension ABI changes were introduced by this continuation. Production changes remain KMK-marked, and normal imports are used in the added code.

Changed-file groups across the continuation: database schema alignment and repository transaction cleanup; shared insert assertions and JVM/Android driver tests; chapter-stat/active feed tests; backup batch recovery and metadata commit handling; sync and restore regressions; library badge/filter helpers and regression; profile source-directory configuration and documentation; obsolete helper/module cleanup. The earlier checkpoint retains the broader async repository migration. User merges, version bumps, and pre-existing formatting commits remain untouched.

Final follow-up files: `app/build.gradle.kts`; `MangaRestorer.kt`; `LibraryItem.kt`; `LibraryScreenModel.kt`; `RestoreDatabaseTest.kt`; new `LibraryBadgesTest.kt`; `MangaChapterStatsTest.kt`; `baseline-profile/README.md`; this report; replacement of `app/src/main/baseline-prof.txt` with `app/src/main/baselineProfiles/baseline-prof.txt` and `startup-prof.txt`.

Remaining validation limits: no arbitrary real-user backup/database was used, no live remote sync service was contacted, and no physical-device startup performance measurements were taken. Deprecated Gradle APIs and Kotlin metadata warnings were emitted by the current toolchain; they did not fail the required build or tests. D8 also reported unmatched startup rules and distributed startup classes across two DEX files; profile compilation and packaging succeeded, but this is not evidence of a measured startup speedup. Running every duplicate unit-test build variant was unnecessary after the full release suite and targeted debug database suite passed.
