# v1.23.2 Library regression

Compared good `b9f22f5cc9095730aa1faac7e1e0459aebd713cb`, bad
`591e0aa43b5f1103665de106191967cf8c4b94b0`, and current main
`27ce902139744489eb1a5a4769f0f83198085e22`.
The tag interval contains the visibility feature (`49456890`) and version changes.

## Causal code evidence

The feature replaced the three-input getFavoritesFlow combine with a flatMapLatest
containing N+1 visibility preference flows. AndroidPreference.changes collects a cold
callbackFlow: each collection registers a separate SharedPreferences listener. Thus a
20,129-title library added 20,130 listeners and combine children, regardless of global
merge enablement. Library snapshots cancelled and recreated the collection tree.

Android notifies registered listeners before each flow filters its key. Consequently
even last-used-category, cover metadata and unrelated preference writes incur the fanout.
This explains why removing category changes from grouping did not remove the regression.
It does not prove continuous CPU usage while idle: the work is triggered by emissions
and preference writes. Frame drops still require device measurement.

The same feature placed chapter loading/filtering/counting inside toLibraryItems. Every
download-cache, display-preference or visibility-preference emission rebuilt all items
and queried all merge/blacklist-affected entries. ChapterSmartMerge allocates filtered
lists, groups and ID sets; doing this repeatedly over large chapter collections adds
substantial allocation pressure. The old three-input pipeline did no visibility queries.

The remainder of the original diff unified Reader/History/Manga visibility and introduced
the batched chapter query. Those correctness changes remain. GetVisibleChaptersByMangaIds
still applies the existing excluded-scanlator/bookmark filtering and shared smart-merge,
range-rule and hybrid-blacklist implementation, including merged sources.

## Correction

- One filtered preference observer, installed before its initial snapshot. Burst events
  conflate into a complete current snapshot; clear/delete and overrides remain observable.
  Unrelated preference writes cause no Library settings snapshot read or emission.
- Database + visibility settings prepare visible counts separately from downloads,
  badges and display preferences. Source-registration updates also avoid chapter loading.
- Per-collection count cache stores prepared LibraryManga values, never chapter lists.
  A changed override loads only entries whose effective merge setting changed. Explicit
  overrides remain independent of the global switch. Disabling merge restores raw counts
  for entries without blacklists; blacklist counts remain filtered.
- Global recalculation materializes chapters in batches of 256 titles rather than holding
  the entire affected library's chapter collections together.
- Equal LibraryItems retain their prior object identity; equal item lists suppress
  downstream emissions. Badge preparation still scans the library as in v1.23.1.
- No pager, UI, schema, migration, Reader, History, Downloads or category-manager changes.

## Deliberate invalidation limit

Database emissions still invalidate all cached visibility counts. Library aggregate
counts and manga versions cannot prove chapters are unchanged: scanlator/number edits,
sync updates, excluded-scanlator changes and merged-source updates can preserve those
values. Reusing on equality would risk stale visible/read/bookmark counts.

Therefore this fix makes preference changes selective and removes download/display
requeries, but does **not** implement selective per-manga database invalidation. That
requires a reliable row-level revision/invalidation mechanism across all these writers.
Frequent database updates with global merge ON may still be expensive. This limitation
is intentionally explicit rather than hidden behind an unsafe equality cache.

## Reproducible validation

LibraryVisibilityPreferencesTest uses the production AndroidPreferenceStore and a mocked
SharedPreferences backend with 20,129 override entries, counts actual registered listeners,
checks 100 unrelated navigation writes, a 1,000-key burst, delete, clear, and disposal.
LibraryVisibilityCacheTest exercises 20,129 titles, global ON/OFF, selective override changes,
identity reuse, blacklist behavior, removal and equal-aggregate database invalidation.

Expected visibility-specific counts (historical counts derived from source, not device runs):

| Scenario | v1.23.1 | v1.23.2 / pre-fix main | Corrected pipeline |
|---|---:|---:|---:|
| Preference listeners for visibility | 0 | 20,130 | 1 |
| OFF, no overrides/blacklists: chapter loads | 0 | 0, but listeners retained | 0 |
| Change one effective override | No unified count pipeline | All affected titles | One affected title |
| Download/display change | No visibility query | All affected titles | No visibility query |
| Unrelated preference write | No visibility fanout | 20,130 callbacks | One predicate check |

No device was connected (`adb devices` returned an empty list). GC, allocations, RSS,
query duration, main-thread frame timings and swipe drops have **not** been measured on
the user's library. Unit operation counts are not a claim of smoothness parity.
For runtime acceptance, compare all four versions with identical library/settings, both
global modes and overrides, warm/cold covers, and swipes/tab taps. Capture Perfetto
FrameTimeline, main-thread CPU and GC, Android Studio allocation profiling, and SQL query
timings in a debug/profileable build. No diagnostic logging runs in release code.

## Recorded validation

All required tasks passed: spotlessApply, spotlessCheck, :app:compileDebugKotlin,
assembleDebug, testReleaseUnitTest, and git diff --check.
The three cache tests completed in 0.069s and the production-observer/mock-backend test
in 0.213s on this host. These are test-suite durations, not database or device benchmarks.
Recorded counters matched the table: one active listener, zero snapshot reads for 100
navigation writes, all 1,000 burst updates retained; OFF loaded zero titles, each of two
overrides loaded one title, global ON loaded the remaining 20,127, repetition loaded zero.

The first full run exhausted the test heap because the burst test used an unconfined
collector and retained many full snapshots. The corrected scheduled burst test exercises
conflation and passes without increasing the heap. Build output still includes existing
Kotlin/D8 metadata and Gradle deprecation warnings.
