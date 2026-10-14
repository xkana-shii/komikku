# Library navigation reconciliation

Baseline: TachiyomiSY `5a469745d39e8f8cea9a1669bea6108cd71dc951`.
Komikku base: `61e647e6c9719213a75278971888a345d68458c5` (latest origin/main when work began).

## Source comparison and changes

- `LibraryContent`: reuse SY's bounded initial page, direct `animateScrollToPage` tab action,
  and category-list shrink correction. Remove the correction toward the asynchronously
  published ViewModel index. It could cancel an in-progress swipe when category metadata
  changed. It was not a per-frame callback, and cannot explain every reported stutter.
- `LibraryPager`: restore SY's default HorizontalPager composition policy, Top alignment,
  and existing current-page +/- 1 content guard. Remove the additional category key and
  `beyondViewportPageCount = 1`. The latter eagerly composed adjacent grids and their images;
  its benefit was never measured. Lazy prefetch remains managed by Compose, as in SY.
- Subcategory chips now belong to each page, above its weighted content area, including
  empty-category content. Remove the shared 56dp minimum-height placeholder. Each page
  retains its own child controls during a transition; the outer pager does not change height.
- Publish the settled page rather than the halfway-crossed current page. SY publishes
  currentPage, but Komikku additionally resets the old child filter on that event. Delaying
  publication avoids rebuilding the visible source page from child content to All mid-swipe.
  Parent tab highlighting still follows currentPage. Toolbar/refresh/selection state follows
  the settled page. A cancelled swipe preserves its previous child selection.
- `LibraryViewModel`: preserve the separation of data preparation from selection, matching
  SY's library/state flow separation. Guard identical selection and preference writes. Bound
  the index before storing it. Keep the existing reset-to-All policy when changing parents.
- `LibraryCategoryCache`: retain prepared parent and child item lists, but remove the
  redundant count map: List.size is constant time. These lists are built on data/filter/sort
  changes, never on parent or child selection. This is a necessary deviation from SY's
  ID-to-item mapping during composition to satisfy prepared subcategory membership.
- `LibraryTab`: remove the extra activeCategoryIndex parameter. Keep lifecycle state
  collection, actions, dialogs, tracking, downloads, and refresh scopes.
- `LibraryTabs`: unchanged. Compared with SY, Komikku retains the divider/Column/zIndex
  wrapper and child FilterChips. Parent selection and tab animation dispatch are the SY path.
- Compact grid, comfortable grid, list, and `CommonMangaItem`: inspected and preserved.
  Remaining differences include stable manga keys, prepared badges, source/language icons,
  cover colors, panorama rendering and cover-ratio state. They do add rendering work, but
  there is no device evidence justifying their removal. No image-cache/request policy changed.

## Previous performance changes

`558cf0a`: retain removal of navigation from grouping inputs. Reintroducing it would regroup
and sort on every parent change, contrary to both SY's architecture and the requirements.

`d2cbac1`: retain prepared item/child lists and stable manga keys; remove the count map,
pager key, eager offscreen pages and shared subcategory height container. No additional cache
or chapter-query layer was introduced.

Smart merge override subscriptions, blacklist-aware visible-chapter statistics, bookmark
filters, download counts and sorting remain driven by their underlying data/preferences.
Neither active parent nor active child is an input to those pipelines. Hierarchy grouping,
direct-parent membership, ordering, hiding, schedules, tracker categories, category manager,
database, migrations and backup/restore were not replaced.

## Evidence and limits

The code establishes additional eager page composition and a child-filter reset during an
unfinished transition, plus a potential competing scroll correction. These are removed or
isolated here. It does **not** establish which dominates wall-clock lag on the user's device.
No frame-time improvement or SY smoothness parity is claimed.

The Android SDK adb executable was found; `adb devices` returned no attached devices.
There are therefore no before/after frame measurements, screenshots, recomposition counts,
or image-reload measurements. No temporary logging was added to the release hot path.

For runtime acceptance, use the same device, refresh rate, library data and display settings
for SY and Komikku. Warm covers first. Record separate cold and warm runs; repeat swipes and
tab taps across populated/empty parents, large libraries, selected/empty children, and parents
without children in compact, comfortable, panorama and list layouts. Verify cancelled and
rapidly reversed swipes, rotation, search filtering and category removal/reordering too.

Capture Android Studio System Traces (FrameTimeline/main thread/layout/Compose), and use
Layout Inspector recomposition counts in a separate diagnostic run. Compare p50/p95/p99
frame duration, missed deadlines and frozen frames. Check chapter queries/grouping do not
start on navigation and cover requests are not repeatedly restarted. Debug timing alone is
not representative of release performance; use a profileable build for acceptance.

Reset/capture a supplementary frame report using the SDK's adb:

```text
adb shell dumpsys gfxinfo app.komikku.dev reset
# Perform one repeatable navigation scenario.
adb shell dumpsys gfxinfo app.komikku.dev framestats
```

Regression coverage includes prepared child membership and order, direct parent/All items,
empty child versus All behavior, empty parents, bounded selection after category removal,
category reordering, and reuse of prepared lists when selection changes. Existing full-suite
tests cover merge/blacklist counts, bookmark filtering, tracking, feeds and backup behavior.

Validation completed: `spotlessApply`, `spotlessCheck`, `:app:compileDebugKotlin`,
`assembleDebug`, `testReleaseUnitTest`, and `git diff --check` passed. Three new
LibraryNavigationStateTest cases passed alongside the existing cache tests. The build
emitted Kotlin/D8 metadata and Gradle deprecation warnings but completed successfully.
