# KNS feature implementation tracker

Tracks every requirement of `features.md` ("Komikku Fork — Fork-Specific Features and Changes (Mihon/
TachiyomiSY Imports Excluded)") against the `codex/komikun-foundation` branch. Read this together with
`docs/komikun-foundation-progress.md` before continuing.

- **Baseline:** `codex/komikun-foundation` @ `f5965c217c` (Komi-kun through #61, foundation docs).
- **Reference only:** old personal `main` / `backup/pre-komikun-foundation-20261020` @ `0ff967c77f`. It shares
  the Komikku base `1656575e8f` with the foundation, so `git diff 1656575e8f 0ff967c77f` is exactly the old
  fork's change set (Mihon/SY ports included). Its fork code is marked `KMK`, not `KNS`.
- **Rules:** additive union with Komi-kun; adapt self-contained old-fork logic, rebuild UI on Metro +
  AndroidX ViewModels; `KNS` markers; `KMR` strings; CHANGELOG + this file updated in every batch.

## Status legend

| Code | Meaning |
|------|---------|
| `PRESENT` | Already implemented on the baseline and verified (by code/test reference); keep as is |
| `PARTIAL` | Present (Komikku/SY/Komi-kun), fork-specific detail missing or unverified |
| `MISSING` | Not on the baseline |
| `WIP` | In progress |
| `DONE-UNVERIFIED` | Implemented, automated coverage incomplete or device check pending |
| `DONE` | Implemented and verified (tests named) |
| `HISTORICAL` | Needs verification against the old final tree before restoring |
| `SUPERSEDED` | Intentionally not restored (reverted/replaced in the fork) |
| `BLOCKED` | Waiting on a dependency or a decision |

Initial classification (2026-10-20) is from code inspection of HEAD and of the old fork; every `PRESENT`
row will be re-checked when its subsystem is touched.

## Baseline facts that drive the design

- **Schema.** Foundation schema 57 (`1.sqm`–`56.sqm`, Komi-kun #60 added `56.sqm`). The old fork's database
  is at `user_version` 57 with a **different** lineage (its own `47.sqm` Mihon-style rework, then
  `48`–`56`). Fresh-schema comparison (`.sq` files of both refs) shows the same tables and most columns;
  the differences are:
  - sync columns: old `state_version` / `state_is_syncing` / `state_last_modified_at` vs foundation
    `version` / `is_syncing` / `last_modified_at`; foundation-only `manga.favorite_modified_at`,
    `category.uid/version/last_modified_at/is_syncing`;
  - old-only fork columns: `category.parent_id/track_status/update_interval/date_added/date_modified`,
    `chapter.user_bookmark_color/user_fillermark`, `manga.user_blacklisted_chapters/
    user_scanlator_priority/user_scanlator_range_rules`, plus SY-era `manga.state_date_added/
    user_filtered_scanlators` and Komikku's `category.manga_order`;
  - old-only cache tables `manga_chapter_stats`, `library_visibility_revision/_generation` (+ triggers);
  - a few nullability/default differences (`remote_memo`, `remote_date_upload`, track dates).
  Both versions are 57, so the driver would open an old-fork database **without migrating** it.
- **Driver hook.** `AndroidxSqliteDriver.onConfigure` only exposes PRAGMAs, so an old-lineage conversion
  must run on a raw connection before the driver is built (`AppBindings.providesSqlDriver`).
- **Decision.** New KNS columns reuse the old fork's names so the old-lineage conversion is mostly a
  sync-column rename + table rebuild. Smart Merge overrides stay per-manga preferences
  (`smartScanlatorMergeOverride(mangaId)`), as in the old fork. Backup field numbers reuse the old
  fork's (`docs/komikun-foundation-progress.md` § Backup compatibility).

## Batches

| # | Batch | State | Commits |
|---|-------|-------|---------|
| 1 | Persistence: KNS columns (migration 57), domain fields, mappers, old-fork database conversion | done | see session log |
| 2 | Hierarchical categories (domain + manager + assignment) and Smart Categories | planned | — |
| 3 | Chapter blacklist, bookmark colors, Fillermark migration, Smart Scanlator Merge, unified visibility | planned | — |
| 4 | Library search/filters/sort/presentation + performance | planned | — |
| 5 | Tracking: progress correctness, MangaBaka, unified editor, preferred/priority | planned | — |
| 6 | Manga details/relations, reader controls & automation | planned | — |
| 7 | Browse / Global Search / feeds / extensions | planned | — |
| 8 | History, Updates, release prediction, category update schedules | planned | — |
| 9 | Auto Reread, webhooks, Discord verification, storage, settings | planned | — |
| 10 | Final audit, acceptance matrix, full validation | planned | — |

## Requirements

Columns: **ID** (section.subsection-letter) · **Requirement** · **Status** · **Notes** (existing behavior to
keep / what is missing / persistence, backup, sync) · **Tests** · **Commit**.

### 1. Library organization and nested categories

| ID | Requirement | Status | Notes | Tests | Commit |
|----|-------------|--------|-------|-------|--------|
| 1.1-a | Hierarchical categories (parent/child) across organization, browsing, dialogs, persistence | MISSING | No `parent_id` on HEAD. Old: `category.parent_id` (0 = root), `CategoryTreeHandler`. Needs column, model, mapper, backup #901, sync | — | — |
| 1.1-b | Same names under different parents stay distinct (identity = id + parent) | MISSING | Komi-kun category sync matches by `uid`; keep that. Name lookups (`CreateCategoryWithName`, restore) must become parent-aware | — | — |
| 1.1-c | Parent-child membership consistency (child assignment implies ancestry; no orphan/unrelated memberships) | MISSING | | — | — |
| 1.1-d | Library navigation for nested categories (subcategory chips); parent view honors include-descendants | MISSING | Keep Komi-kun #55 tab selection fix | — | — |
| 1.1-e | Parent-inclusive counts with duplicate handling | MISSING | | — | — |
| 1.1-f | Parent/child display mode option (verify historical toggles) | HISTORICAL | Check old `LibraryPreferences` | — | — |
| 1.1-g | Default-category behavior (no empty/duplicate Default tab) | PARTIAL | Komi-kun library tabs; verify with hierarchy | — | — |
| 1.2-a | Tree-based category manager | MISSING | Current `CategoryScreen` is flat (Mihon/Komikku) | — | — |
| 1.2-b | Expand/collapse branches + expand-all/collapse-all | MISSING | | — | — |
| 1.2-c | Category search with hierarchy context | MISSING | | — | — |
| 1.2-d | Create child category (choose parent) | MISSING | | — | — |
| 1.2-e | Rename without changing parent / conflating siblings | PARTIAL | Rename exists; duplicate-name check is global, must be per-parent | — | — |
| 1.2-f | Move/reparent with cycle prevention | MISSING | Old `MoveCategory` | — | — |
| 1.2-g | Merge categories (hierarchy rules) | MISSING | Old `MergeCategories` | — | — |
| 1.2-h | Sort and manual reorder, stable hierarchy | PARTIAL | Reorder exists (flat); sort-by exists? verify | — | — |
| 1.2-i | Batched/debounced reorder writes | MISSING | | — | — |
| 1.2-j | Bulk selection | MISSING | | — | — |
| 1.2-k | Bulk hide/show (keeps membership) | PARTIAL | Single hide exists (Komikku `hidden`) | — | — |
| 1.2-l | Bulk move/merge/delete respecting descendants | MISSING | | — | — |
| 1.2-m | Counts, hierarchy/selection indicators, list state kept | MISSING | | — | — |
| 1.3-a | Hierarchy-aware Set Categories dialog | MISSING | | — | — |
| 1.3-b | Correct bulk assignment (keep unrelated memberships) | PARTIAL | Mihon tri-state bulk dialog exists; needs hierarchy | — | — |
| 1.3-c | Tabs/chips/counts/filters react to category edits | PARTIAL | Reactive flows exist; verify with hierarchy ops | — | — |
| 1.3-d | Search/filter does not jump to invalid tab | PARTIAL | Komi-kun #55 resolves selection from rendered list | — | — |
| 1.3-e | Include/exclude category filters correct with tree changes and same-name children | MISSING | | — | — |
| 1.4-a | Tracker-status-driven categories | MISSING | Old `category.track_status`, `SyncCategoriesWithTrackStatus` | — | — |
| 1.4-b | (Rationale) no cross-branch assignment by status alone | MISSING | Covered by 1.4-c..h tests | — | — |
| 1.4-c | Subcategory binding eligible only if parent is a member or ancestor of a member | MISSING | | — | — |
| 1.4-d | Reachability walk (transitive ancestry) | MISSING | | — | — |
| 1.4-e | Out-of-scope branches completely untouched | MISSING | | — | — |
| 1.4-f | Multiple relevant parents update independently | MISSING | | — | — |
| 1.4-g | Top-level status categories stay global | MISSING | | — | — |
| 1.4-h | Deeper nesting | MISSING | | — | — |
| 1.4-i | Hierarchy-safe status changes (start/complete/reset) | MISSING | | — | — |
| 1.4-j | Category update only after persisted tracker state | MISSING | Hook after `InsertTrack`/tracker update persistence | — | — |
| 1.4-k | Category status policy configuration UI | MISSING | | — | — |
| 1.5-a | Per-category update intervals | MISSING | Old `category.update_interval`, `GetCategoryUpdateInterval` | — | — |
| 1.5-b | Subcategory update scope | MISSING | Old `SubcategoryUpdateScope` | — | — |
| 1.5-c | Category-specific picker + persisted settings | MISSING | Backup #903 | — | — |
| 1.5-d | Updater respects policy, no duplicate work for multi-category manga | MISSING | `LibraryUpdateWorker` | — | — |

### 2. Library browsing, filtering, sorting, presentation

| ID | Requirement | Status | Notes | Tests | Commit |
|----|-------------|--------|-------|-------|--------|
| 2.1-a | Parsed search expressions | PARTIAL | HEAD has SY/Komikku query filtering; old fork `LibrarySearchParser`/`LibrarySearchToken` | — | — |
| 2.1-b | Field-specific terms (fork parser's operators only) | PARTIAL | Inspect old parser before porting | — | — |
| 2.1-c | Negative/exclusion terms | PARTIAL | SY supports `-` exclusion; verify | — | — |
| 2.1-d | Search respects selected parent/child view and filters | MISSING | Depends on batch 2 | — | — |
| 2.1-e | Responsive live search | PARTIAL | Verify debounce on HEAD | — | — |
| 2.1-f | Empty-result correctness | PARTIAL | | — | — |
| 2.2-a | Include categories (tree semantics) | MISSING | | — | — |
| 2.2-b | Exclude categories/descendants | MISSING | | — | — |
| 2.2-c | All-category subcategory exclusion | HISTORICAL | | — | — |
| 2.2-d | Tracker filters without per-title queries | PARTIAL | Mihon tracker filters exist | — | — |
| 2.2-e | Bookmark-color filter | MISSING | Batch 3 | — | — |
| 2.2-f | Built-in filters account for blacklist/Smart Merge | MISSING | Batch 3/4 | — | — |
| 2.2-g | Merged-entry downloaded/unread correctness | PARTIAL | | — | — |
| 2.3-a | Group by source without expensive regrouping | PARTIAL | Komikku group-by exists; check caching | — | — |
| 2.3-b | Source language in source grouping (+ flag option) | PARTIAL | | — | — |
| 2.3-c | Search source name and language | HISTORICAL | | — | — |
| 2.3-d | Source/language badges on covers | PARTIAL | Komikku language badge exists | — | — |
| 2.4-a | Extended sort/display choices | PARTIAL | Identify fork-only sort modes in old `LibrarySort` | — | — |
| 2.4-b | Correct sort behavior after updates | PARTIAL | | — | — |
| 2.4-c | Custom cover ratios/styles/badge colors | PARTIAL | Komikku cover ratio exists | — | — |
| 2.4-d | Parent tab reveals/toggles subcategory chips | MISSING | | — | — |
| 2.4-e | Stable targeted list-item updates | PARTIAL | | — | — |

### 3. Manga details, metadata, relations

| ID | Requirement | Status | Notes | Tests | Commit |
|----|-------------|--------|-------|-------|--------|
| 3.1-a | Custom metadata with backup/sync | PRESENT | SY `CustomMangaInfo` + backup; verify sync | — | — |
| 3.1-b | Fork description/alternative-metadata formatting | HISTORICAL | Inspect old `MangaInfoHeader` diffs | — | — |
| 3.1-c | Avoid re-preparing rich description on recomposition | MISSING | | — | — |
| 3.1-d | Metadata updates respect local customizations | PARTIAL | Komi-kun #60 clearable fields; verify | — | — |
| 3.1-e | Cover presentation without repeated DB observation | PARTIAL | | — | — |
| 3.1-f | Related-manga metadata from trackers | MISSING | Old `SequelPrequelEntry` | — | — |
| 3.2-a | Related-title lookup via AniList/MangaBaka | MISSING | | — | — |
| 3.2-b | Sequel/prequel rows on details | MISSING | | — | — |
| 3.2-c | Cross-service fallback | MISSING | | — | — |
| 3.2-d | Compact horizontal relation cards | MISSING | | — | — |
| 3.2-e | Bounded relation-search concurrency | MISSING | | — | — |
| 3.3-a | Bookmark-color controls in chapter UI | MISSING | Batch 3 | — | — |
| 3.3-b | Chapter filter controls display/reset | PARTIAL | | — | — |
| 3.3-c | Reveal hidden (blacklisted) chapters | MISSING | Batch 3 | — | — |
| 3.3-d | Chapter sort choices stable after sync | PARTIAL | | — | — |
| 3.3-e | Multi-select actions refresh indicators | PARTIAL | | — | — |
| 3.3-f | No tracking popup after dismissal for non-favorites | MISSING | Verify current behavior | — | — |

### 4. Colored bookmarks and Fillermark migration

| ID | Requirement | Status | Notes | Tests | Commit |
|----|-------------|--------|-------|-------|--------|
| 4.1-a | Bookmark color as chapter state (fixed palette) | MISSING | Old `chapter.user_bookmark_color`, `BookmarkColor` | — | — |
| 4.1-b | Normal bookmark behavior unchanged | PRESENT | Keep | — | — |
| 4.1-c | Color picker in chapter list (incl. bulk) | MISSING | | — | — |
| 4.1-d | Color picker in reader | MISSING | | — | — |
| 4.1-e | Reactive display of saved color | MISSING | | — | — |
| 4.1-f | Filter by color | MISSING | | — | — |
| 4.1-g | Reader chapter-list sync on color change | MISSING | | — | — |
| 4.1-h | Rounded reader bookmark ripple | MISSING | | — | — |
| 4.1-i | Color survives source refresh | MISSING | `SyncChaptersWithSource` copies user state | — | — |
| 4.1-j | Color in backup + sync | MISSING | Backup chapter #14; SyncYomi payload | — | — |
| 4.2-a | Fillermark → blacklist/color migration | PARTIAL | Database part done (converter); backup #15 pending | `OldForkDatabaseConverterTest` | batch 1 |
| 4.2-b | Uncolored legacy bookmarks stay default | MISSING | | — | — |
| 4.2-c | Identity-aware conversion | PARTIAL | Exact URL keys only (no number/name matching) | `CategoryUidTest` (key format), converter test | batch 1 |
| 4.2-d | No standalone Fillermark UI | SUPERSEDED | Migration only | — | — |

### 5. Chapter blacklist and visibility

| ID | Requirement | Status | Notes | Tests | Commit |
|----|-------------|--------|-------|-------|--------|
| 5.1-a | Blacklist individual chapters (no deletion) | MISSING | Old `manga.user_blacklisted_chapters`, `ChapterBlacklist` | — | — |
| 5.1-b | Per-manga blacklist management UI | MISSING | | — | — |
| 5.1-c | Reveal blacklisted chapters | MISSING | | — | — |
| 5.1-d | Exact identity (URL) first, controlled fallback | MISSING | | — | — |
| 5.1-e | No number/name collisions (extras vs ch.1/6) | MISSING | | — | — |
| 5.1-f | Disambiguate by URL/scanlator | MISSING | | — | — |
| 5.1-g | Survive source refresh | MISSING | | — | — |
| 5.1-h | Keep history/bookmarks of hidden chapters | MISSING | | — | — |
| 5.2-a | Chapter list hides blacklisted unless revealed | MISSING | | — | — |
| 5.2-b | Reader next/prev skip hidden | MISSING | | — | — |
| 5.2-c | History continuation skips hidden | MISSING | | — | — |
| 5.2-d | Library unread/total exclude hidden | MISSING | | — | — |
| 5.2-e | Downloads / next-chapter follow visibility | MISSING | | — | — |
| 5.2-f | Deep link / notification / history fallback to visible equivalent | MISSING | | — | — |

### 6. Scanlator preferences and Smart Scanlator Merge

| ID | Requirement | Status | Notes | Tests | Commit |
|----|-------------|--------|-------|-------|--------|
| 6.1-a | Exclude scanlators per manga | PRESENT | `excluded_scanlator` table + Komi-kun #60 sync; keep, do not duplicate | Komi-kun tests | — |
| 6.1-b | Prioritize scanlators | MISSING | Old `manga.user_scanlator_priority` | — | — |
| 6.1-c | Manga-specific settings | MISSING | | — | — |
| 6.1-d | No deletion of hidden chapter records | MISSING | | — | — |
| 6.2-a | Group duplicates, show representative | MISSING | Old `applySmartScanlatorMerge` | — | — |
| 6.2-b | Priority-based representative | MISSING | Range rules + priority | — | — |
| 6.2-c | Per-manga override | MISSING | Preference `smartScanlatorMergeOverride(id)`, backup #618 | — | — |
| 6.2-d | Incremental override updates | MISSING | | — | — |
| 6.2-e | Counts reflect representatives | MISSING | | — | — |
| 6.2-f | Reader navigates representatives | MISSING | | — | — |
| 6.2-g | History/deep links resolve to representative | MISSING | | — | — |
| 6.2-h | Merged manga read state | MISSING | | — | — |
| 6.2-i | Consistent with blacklist | MISSING | | — | — |
| 6.2-j | Predictable toggles, no orphaned cache | MISSING | | — | — |

### 7. Tracking services

| ID | Requirement | Status | Notes | Tests | Commit |
|----|-------------|--------|-------|-------|--------|
| 7.1-a | Fork MangaBaka integration (search, binding, progress, status/score/date) | PARTIAL | Mihon MangaBaka tracker on HEAD; diff old fork's service for fork-only behavior | — | — |
| 7.1-b | MangaBaka metadata + related series | MISSING | | — | — |
| 7.1-c | MangaBaka date fidelity | PARTIAL | Verify conversions | — | — |
| 7.2-a | AniList relation integration | MISSING | | — | — |
| 7.2-b | MangaUpdates fork handling + unified binding | PARTIAL | | — | — |
| 7.2-c | Remote error presentation in unified editor | MISSING | Batch 5 | — | — |
| 7.3-a | No progress rollback | PARTIAL | Mihon `TrackChapter` only raises; verify every path | — | — |
| 7.3-b | Reject nonfinite/negative chapter numbers | MISSING | Old `TrackerProgressSync` | — | — |
| 7.3-c | Refresh before advancing when needed | PARTIAL | | — | — |
| 7.3-d | Start-tracking status correctness | PARTIAL | | — | — |
| 7.3-e | Automatic progress propagation | PRESENT | Mihon auto-update track on read | — | — |
| 7.3-f | Smart Categories after persisted tracker state | MISSING | See 1.4-j | — | — |
| 7.3-g | Reread counters where supported | HISTORICAL | | — | — |

### 8. Unified tracker editor

| ID | Requirement | Status | Notes | Tests | Commit |
|----|-------------|--------|-------|-------|--------|
| 8.1-a | Unified view of all linked trackers | MISSING | Old `UnifiedTrack*` | — | — |
| 8.1-b | Edit statuses together with per-service translation | MISSING | | — | — |
| 8.1-c | Edit scores with scale conversion | MISSING | | — | — |
| 8.1-d | Edit start/finish dates | MISSING | | — | — |
| 8.1-e | Track/untrack incl. remote removal | PARTIAL | Individual track sheet supports it | — | — |
| 8.1-f | Multi-title batch actions | MISSING | | — | — |
| 8.1-g | Operation queue | MISSING | | — | — |
| 8.1-h | Partial-failure resilience | MISSING | | — | — |
| 8.2-a | Preferred tracker per manga | MISSING | Old `PreferredTrackerMap` | — | — |
| 8.2-b | Global tracker priority | MISSING | | — | — |
| 8.2-c | Persist preferences (backup/sync) | MISSING | | — | — |
| 8.2-d | Unified/individual editing consistency | MISSING | | — | — |
| 8.3-a | Tracker search resolver | PARTIAL | | — | — |
| 8.3-b | Tracker metadata autofill | MISSING | | — | — |
| 8.3-c | Distinct linked-service identities | PARTIAL | | — | — |
| 8.3-d | Tracker error indicator | MISSING | | — | — |
| 8.3-e | Unified sheet in destination visual language | MISSING | | — | — |
| 8.3-f | No unexpected dialog reopen | MISSING | See 3.3-f | — | — |

### 9. Reader and automation

| ID | Requirement | Status | Notes | Tests | Commit |
|----|-------------|--------|-------|-------|--------|
| 9.1-a | Paged and vertical compatibility | PRESENT | Keep Komi-kun WebGPU/SSIV | — | — |
| 9.1-b | Chapter navigation honors Smart Merge/blacklist | MISSING | Batch 3 | — | — |
| 9.1-c | Title tap = back navigation parity | MISSING | Verify | — | — |
| 9.1-d | Reader bookmark selection + colors | MISSING | | — | — |
| 9.1-e | Rounded bookmark touch feedback | MISSING | | — | — |
| 9.1-f | Reader chapter list updates on color change | MISSING | | — | — |
| 9.1-g | Page-jump correctness | PARTIAL | Verify | — | — |
| 9.2-a | Button-controlled auto-scroll (new system) | PARTIAL | SY auto-scroll exists on HEAD; fork system replaces old SY UI — reconcile without removing | — | — |
| 9.2-b | Automatic page flip (paged) | PARTIAL | | — | — |
| 9.2-c | Webtoon automation | PARTIAL | | — | — |
| 9.2-d | Speed selection with usable increments | MISSING | | — | — |
| 9.2-e | Refresh-rate support | MISSING | | — | — |
| 9.2-f | Duration limit | MISSING | | — | — |
| 9.2-g | Stop at end | MISSING | | — | — |
| 9.2-h | Stop on sleep/background | MISSING | | — | — |
| 9.2-i | No background work when disabled | MISSING | | — | — |
| 9.2-j | Keep screen awake while active | MISSING | | — | — |
| 9.2-k | Saved automation settings | MISSING | | — | — |
| 9.3-a | Webtoon pinch-zoom disable | PARTIAL | SY/Mihon pref present; verify fork origin | — | — |
| 9.3-b | Webtoon scale-type controls | HISTORICAL | | — | — |
| 9.3-c | Long-strip gap preference | HISTORICAL | | — | — |
| 9.3-d | Responsive scaling on resize | HISTORICAL | | — | — |
| 9.3-e | Lifecycle-safe listeners | HISTORICAL | | — | — |
| 9.4-a | Download from reader | HISTORICAL | | — | — |
| 9.4-b | Download status in reader lists | HISTORICAL | | — | — |
| 9.4-c | Bounded fork-owned image concurrency | HISTORICAL | | — | — |
| 9.4-d | Source-aware throttling | HISTORICAL | | — | — |
| 9.4-e | Image/cache cleanup | HISTORICAL | | — | — |
| 9.4-f | Page preloading configuration | PARTIAL | | — | — |

### 10. Browse and Global Search

| ID | Requirement | Status | Notes | Tests | Commit |
|----|-------------|--------|-------|-------|--------|
| 10.1-a | Source-category organization/filter | PRESENT | SY source categories | — | — |
| 10.1-b | Source icons and language flags | PARTIAL | | — | — |
| 10.1-c | Disabled sources not searched in background | PARTIAL | Verify | — | — |
| 10.1-d | Source list responsiveness | PARTIAL | | — | — |
| 10.1-e | Source identity after updates/restores | PARTIAL | | — | — |
| 10.1-f | Source settings reachable | PRESENT | | — | — |
| 10.1-g | Fresh requests without clearing unrelated caches | MISSING | | — | — |
| 10.1-h | Source API concurrency control | PARTIAL | | — | — |
| 10.2-a | Global Search by source category | PARTIAL | Verify on HEAD | — | — |
| 10.2-b | Persistent scope | PARTIAL | | — | — |
| 10.2-c | Category controls show searched sources | PARTIAL | | — | — |
| 10.2-d | Result grouping, no stale/current confusion | PARTIAL | | — | — |
| 10.2-e | Debounced input | MISSING | | — | — |
| 10.2-f | Loading/error/empty states | PRESENT | | — | — |
| 10.3-a | Responsive source search bar | MISSING | | — | — |
| 10.3-b | Filter snapshot isolation | MISSING | | — | — |
| 10.3-c | Filter reset/persistence across saved searches | PARTIAL | | — | — |
| 10.3-d | Source result refresh keeps input | PARTIAL | | — | — |
| 10.3-e | Correct back navigation | PARTIAL | | — | — |

### 11. Feeds and saved searches

| ID | Requirement | Status | Notes | Tests | Commit |
|----|-------------|--------|-------|-------|--------|
| 11.1-a | Persistent source feeds | PRESENT | SY/Komikku feeds | — | — |
| 11.1-b | Saved-search feeds | PRESENT | | — | — |
| 11.1-c | Search/filter snapshot reconstruction | PARTIAL | | — | — |
| 11.1-d | Source-feed associations | PRESENT | | — | — |
| 11.1-e | Feed ordering | PARTIAL | | — | — |
| 11.1-f | Saved-search overwrite handling | MISSING | Old `UpdateSavedSearch`/`UpdateFeedSavedSearch` | — | — |
| 11.1-g | Restore saved searches before feeds | PRESENT | Komi-kun #59 feed restorer | Komi-kun `FeedRestorerTest` | — |
| 11.1-h | Source-category-aware feeds | PARTIAL | | — | — |
| 11.2-a | Independent feed loading states | PARTIAL | | — | — |
| 11.2-b | Per-feed retry | MISSING | | — | — |
| 11.2-c | Refresh all feeds (not only errors) | MISSING | | — | — |
| 11.2-d | Error rows with retry | MISSING | | — | — |
| 11.2-e | No stale result substitution | MISSING | | — | — |
| 11.2-f | Isolated feed request data | MISSING | | — | — |
| 11.2-g | Usable transitions | PARTIAL | | — | — |
| 11.3-a | Saved searches usable from source and feed | PRESENT | | — | — |
| 11.3-b | Valid saved-search feeds not rejected | PARTIAL | Komi-kun #59 identity fix | — | — |
| 11.3-c | Backup/restore of feeds | PRESENT | Komi-kun #59 | `FeedRestorerTest` | — |
| 11.3-d | No cross-source filter application | PARTIAL | | — | — |

### 12. Extensions

| ID | Requirement | Status | Notes | Tests | Commit |
|----|-------------|--------|-------|-------|--------|
| 12.1-a | Repository configuration + icon customization | PARTIAL | Komi-kun extension stores | — | — |
| 12.1-b | Store management updates lists | PRESENT | | — | — |
| 12.1-c | Refresh after repo enable/disable | PARTIAL | Verify | — | — |
| 12.1-d | Extension details + source preferences | PRESENT | | — | — |
| 12.1-e | Filters without background observation | PARTIAL | | — | — |

### 13. History and read state

| ID | Requirement | Status | Notes | Tests | Commit |
|----|-------------|--------|-------|-------|--------|
| 13.1-a | Daily distinct-manga count | MISSING | Verify HistoryScreen header | — | — |
| 13.1-b | Date-grouped history with count | PARTIAL | | — | — |
| 13.1-c | Initial loading vs empty | PARTIAL | | — | — |
| 13.1-d | Visibility-aware history filters | MISSING | Batch 3 | — | — |
| 13.2-a | Continue to next readable chapter | PARTIAL | Mihon `GetNextChapters` | — | — |
| 13.2-b | Skip blacklisted | MISSING | | — | — |
| 13.2-c | Resolve Smart Merge duplicates | MISSING | | — | — |
| 13.2-d | Preserve history identity | MISSING | | — | — |
| 13.2-e | Merged-title handling | PARTIAL | | — | — |
| 13.3-a | Mark-read without duplicates | PRESENT | | — | — |
| 13.3-b | Next-chapter consistency across surfaces | MISSING | | — | — |
| 13.3-c | Source update keeps user flags | PARTIAL | Read/bookmark kept; add color/visibility | — | — |

### 14. Updates and release prediction

| ID | Requirement | Status | Notes | Tests | Commit |
|----|-------------|--------|-------|-------|--------|
| 14.1-a | Accurate read/download indicators | PARTIAL | | — | — |
| 14.1-b | Correct refresh/error states | PARTIAL | | — | — |
| 14.2-a | Smart release interval prediction | HISTORICAL | Old fork: `FetchInterval`/`GetUpcomingManga` changes; algorithm not established | — | — |
| 14.2-b | Per-title predictions | PARTIAL | Mihon fetch interval is per title | — | — |
| 14.2-c | Refresh on new evidence | PARTIAL | | — | — |
| 14.2-d | Shown as estimates | PARTIAL | | — | — |
| 14.3-a | Category-specific scheduling | MISSING | See 1.5 | — | — |
| 14.3-b | Controlled parallel update slots | PARTIAL | Verify | — | — |
| 14.3-c | Timestamp after completion | PARTIAL | | — | — |
| 14.3-d | Cancellation | PRESENT | | — | — |
| 14.3-e | Reliable notifications | PARTIAL | Komi-kun #47 | — | — |
| 14.3-f | No duplicate chapter creation | PRESENT | Komi-kun #58 + Mihon | — | — |

### 15. Backup, restore, migration

| ID | Requirement | Status | Notes | Tests | Commit |
|----|-------------|--------|-------|-------|--------|
| 15.1-a | Category hierarchy in backup | MISSING | `BackupCategory` #901 `parentId` | — | — |
| 15.1-b | Update schedules + status policies in backup | MISSING | #902 `trackStatus`, #903 `updateInterval` | — | — |
| 15.1-c | Bookmark colors in backup | MISSING | `BackupChapter` #14 | — | — |
| 15.1-d | Blacklist in backup | MISSING | `BackupManga` #615 | — | — |
| 15.1-e | Scanlator prefs + Smart Merge overrides | MISSING | #614, #616, #618 | — | — |
| 15.1-f | Custom metadata | PRESENT | SY | — | — |
| 15.1-g | Tracking state | PRESENT | | — | — |
| 15.1-h | Source preferences | PARTIAL | Verify | — | — |
| 15.1-i | Feeds and saved searches | PRESENT | Komi-kun #59 | — | — |
| 15.1-j | Synced identity data | PRESENT | Komi-kun | — | — |
| 15.2-a | Old backups with missing fields | PARTIAL | Proto defaults; old-fork #113 meaning differs (documented) | — | — |
| 15.2-b | Identity-safe restore (source+url) | PRESENT | Mihon restore by source+url; verify | — | — |
| 15.2-c | Dependency-aware restore order | PARTIAL | Parents before children needed | — | — |
| 15.2-d | Cancellation safety | PARTIAL | | — | — |
| 15.2-e | Worker lifecycle | PARTIAL | | — | — |
| 15.3-a | Migration safety for fork data | DONE-UNVERIFIED | `57.sqm` + `OldForkDatabaseConverter` (old lineage → current schema, all rows, fork columns kept). Device test with a real old-fork DB pending; old-fork SharedPreferences and backups not converted yet | `KnsColumnsMigrationTest`, `OldForkDatabaseConverterTest` | batch 1 |
| 15.3-b | Fillermark migration | PARTIAL | Database Fillermarks → `url:` blacklist keys in the converter; backup `#15 fillermark` pending (batch 3) | `OldForkDatabaseConverterTest` | batch 1 |
| 15.3-c | Local/merged manga folder safety | PARTIAL | | — | — |
| 15.3-d | Visibility separate from source migration matching | MISSING | | — | — |

### 16. SyncYomi

| ID | Requirement | Status | Notes | Tests | Commit |
|----|-------------|--------|-------|-------|--------|
| 16.1-a | Sync bookmark colors, blacklist, scanlator/merge prefs, chapter flags | MISSING | Extend Komi-kun SyncYomi payloads only | — | — |
| 16.1-b | Fork flags survive refresh | MISSING | | — | — |

### 17. Auto Reread and automation

| ID | Requirement | Status | Notes | Tests | Commit |
|----|-------------|--------|-------|-------|--------|
| 17.1-a | Detect reread start | MISSING | | — | — |
| 17.1-b | Configurable reset mode | MISSING | | — | — |
| 17.1-c | Confirmation/prompt | MISSING | | — | — |
| 17.1-d | Correct chapter reset scope | MISSING | | — | — |
| 17.1-e | Tracker coordination (status/progress/reread count) | MISSING | | — | — |
| 17.1-f | Saved settings | MISSING | | — | — |
| 17.2-a | Automatic tracker progress on reading | PRESENT | Mihon | — | — |
| 17.2-b | Category movement by tracker status | MISSING | 1.4 | — | — |
| 17.2-c | No duplicate automation operations | MISSING | | — | — |

### 18. Webhooks and Discord

| ID | Requirement | Status | Notes | Tests | Commit |
|----|-------------|--------|-------|-------|--------|
| 18.1-a | Configurable webhooks | MISSING | | — | — |
| 18.1-b | Discord webhook | MISSING | | — | — |
| 18.1-c | Structured payloads | MISSING | | — | — |
| 18.1-d | Asynchronous bounded queue | MISSING | | — | — |
| 18.1-e | Private endpoint storage | MISSING | | — | — |
| 18.1-f | Failure handling | MISSING | | — | — |
| 18.2-a | Presence integration | PARTIAL | Komikku Discord RPC present on HEAD; compare old fork | — | — |
| 18.2-b | Account/login management | HISTORICAL | | — | — |
| 18.2-c | Restart/stop controls | HISTORICAL | | — | — |
| 18.2-d | Foreground-service compatibility | HISTORICAL | | — | — |
| 18.2-e | Cover/chapter formatting | HISTORICAL | | — | — |
| 18.2-f | Auth/API resilience | HISTORICAL | | — | — |
| 18.2-g | Tokens not exported as normal prefs | PARTIAL | Verify private-pref handling | — | — |

### 19. Storage and downloads

| ID | Requirement | Status | Notes | Tests | Commit |
|----|-------------|--------|-------|-------|--------|
| 19.1-a | Dedicated storage screen | MISSING | | — | — |
| 19.1-b | Storage categorization | MISSING | | — | — |
| 19.1-c | Cumulative totals | MISSING | | — | — |
| 19.1-d | Category-based selection | MISSING | | — | — |
| 19.1-e | Cleanup controls with accurate reclaimed space | MISSING | | — | — |
| 19.1-f | Merged-manga folder safety | PARTIAL | | — | — |
| 19.2-a | Download queue state | PRESENT | | — | — |
| 19.2-b | Fork source throttling | HISTORICAL | | — | — |
| 19.2-c | Temporary-file cleanup without false errors | MISSING | | — | — |
| 19.2-d | Visibility-consistent download actions | MISSING | | — | — |

### 20. Themes, settings, interface

| ID | Requirement | Status | Notes | Tests | Commit |
|----|-------------|--------|-------|-------|--------|
| 20.1-a | Cover-derived colors | PRESENT | Komikku | — | — |
| 20.1-b | Custom cover appearance | PARTIAL | | — | — |
| 20.1-c | Language icon options | PARTIAL | | — | — |
| 20.1-d | Badge coloring | PARTIAL | | — | — |
| 20.1-e | Iconography without obsolete style | PRESENT | | — | — |
| 20.2-a | Expanded settings screens | MISSING | Added per batch | — | — |
| 20.2-b | Adaptive sheets | PARTIAL | | — | — |
| 20.2-c | Generic selection controls | PARTIAL | | — | — |
| 20.2-d | Loading indicators with progress | PARTIAL | | — | — |
| 20.2-e | Correct pluralization | MISSING | Per new string | — | — |
| 20.2-f | Clear notifications/errors | PARTIAL | | — | — |
| 20.2-g | Navigation/back-stack corrections | PARTIAL | | — | — |

### 21. Performance

| ID | Requirement | Status | Notes | Tests | Commit |
|----|-------------|--------|-------|-------|--------|
| 21.1-a | Cached category grouping | PARTIAL | | — | — |
| 21.1-b | Item reuse | PARTIAL | | — | — |
| 21.1-c | Cached chapter visibility | MISSING | Old `manga_chapter_stats` / visibility revision | — | — |
| 21.1-d | Selective invalidation | MISSING | | — | — |
| 21.1-e | One preference subscription | MISSING | Smart Merge override per manga must not fan out | — | — |
| 21.1-f | Batched visible-chapter queries | MISSING | Old `GetVisibleChaptersByMangaIds` | — | — |
| 21.1-g | Avoid unnecessary recomposition | PARTIAL | | — | — |
| 21.1-h | Efficient tracker filters | PARTIAL | | — | — |
| 21.1-i | Efficient category filters | MISSING | | — | — |
| 21.1-j | Throttled invalidations | PARTIAL | | — | — |
| 21.1-k | Aggregate only relevant manga | PARTIAL | | — | — |
| 21.2-a | No side effects in state reduction | PARTIAL | | — | — |
| 21.2-b | Debounced search | PARTIAL | | — | — |
| 21.3-a | Share network resources (fork-owned) | PARTIAL | | — | — |
| 21.3-b | Forced-refresh freshness | MISSING | | — | — |
| 21.3-c | Fork image throttling | HISTORICAL | | — | — |
| 21.3-d | Release fork observers | PARTIAL | | — | — |

### 22. Build and tooling

| ID | Requirement | Status | Notes | Tests | Commit |
|----|-------------|--------|-------|-------|--------|
| 22-a | Version bump workflow | PRESENT | `bump.yml`, `bump_version.sh` (KNS `86b5058636` pre-rewrite) | `bump_version_test.sh` | — |
| 22-b | Patch `.10` → next minor rollover | PRESENT | `bump_version.sh` | `bump_version_test.sh` | — |
| 22-c | Version declaration safeguards | PRESENT | | `bump_version_test.sh` | — |
| 22-d | Stable/preview workflows | PRESENT | | — | — |
| 22-e | Fork release links/branding | PRESENT | | — | — |
| 22-f | Formatting/quality checks | PRESENT | spotless + CI | — | — |
| 22-g | Port/audit documentation | PRESENT | FORK.md, progress docs, this file | — | — |
| 22-h | Regression coverage for fork areas | MISSING | Added per batch | — | — |

### 23. Bug-fix inventory (cross-check, maps to rows above)

| ID | Fix | Maps to | Status |
|----|-----|---------|--------|
| 23.1-a | Same-name child collision | 1.1-b, 1.2-e | MISSING |
| 23.1-b | Hierarchy-aware assignment | 1.3-a | MISSING |
| 23.1-c | Wrong-branch Smart Category | 1.4-c..h | MISSING |
| 23.1-d | Category index/order stability | 1.2-h, 1.2-i | MISSING |
| 23.1-e | Category search/tab selection | 1.3-d | PARTIAL |
| 23.1-f | Tracker-filter performance | 21.1-h | PARTIAL |
| 23.1-g | Merged manga downloads | 2.2-g | PARTIAL |
| 23.1-h | Duplicated library entries | 15.2-b | PARTIAL |
| 23.1-i | Library sort regressions | 2.4-b | PARTIAL |
| 23.1-j | Preference fanout | 21.1-e | MISSING |
| 23.1-k | Visibility invalidation | 21.1-d | MISSING |
| 23.2-a | Blacklist false positives | 5.1-e | MISSING |
| 23.2-b | Smart Merge consistency | 6.2 | MISSING |
| 23.2-c | History after hidden chapters | 13.2-b | MISSING |
| 23.2-d | Bookmark color updates | 4.1-g | MISSING |
| 23.2-e | Reader bookmark press target | 4.1-h | MISSING |
| 23.2-f | Reader title navigation | 9.1-c | MISSING |
| 23.2-g | Hidden chapter reveal | 5.1-c | MISSING |
| 23.2-h | Chapter sync by identity | 14.3-f | PRESENT |
| 23.2-i | Merged read progress | 6.2-h | MISSING |
| 23.3-a | Start-tracking status | 7.3-d | PARTIAL |
| 23.3-b | Malformed progress | 7.3-b | MISSING |
| 23.3-c | Progress rollback | 7.3-a | PARTIAL |
| 23.3-d | MangaBaka dates | 7.1-c | PARTIAL |
| 23.3-e | Tracker operation ordering | 8.1-g | MISSING |
| 23.3-f | Tracker login failures | 7.2-c | MISSING |
| 23.4-a | Refresh-all only errors | 11.2-c | MISSING |
| 23.4-b | Saved-search feed mismatch | 11.3-b | PARTIAL |
| 23.4-c | Feed overwrite collision | 11.1-f | MISSING |
| 23.4-d | Stale source results | 10.1-g | MISSING |
| 23.4-e | Unresponsive Browse search | 10.3-a | MISSING |
| 23.4-f | Global Search stale responses | 10.2-d | PARTIAL |
| 23.5-a | Backup identity collision | 15.2-b | PRESENT |
| 23.5-b | Legacy backup fields | 15.2-a | PARTIAL |
| 23.5-c | Metadata-after-commit atomicity | 15 | MISSING |
| 23.5-d | Migration compatibility | 15.3-a | MISSING |
| 23.6-a | Discord login/no-token | 18.2-f | HISTORICAL |
| 23.6-b | Merged folder cleanup | 19.1-f | PARTIAL |
| 23.6-c | Temporary download errors | 19.2-c | MISSING |
| 23.6-d | Crash-log verbosity | 20 | MISSING |
| 23.6-e | Settings/navigation | 20.2-g | PARTIAL |

### 24. Historical items (verify before restoring)

| # | Item | Decision | Evidence |
|---|------|----------|----------|
| 24.1 | Discord Rich Presence | pending | Komikku RPC on HEAD; diff old fork |
| 24.2 | Adaptive Webtoon scale/gap/pinch | pending | |
| 24.3 | Duplicate-entry management experiments | SUPERSEDED | Reverted in fork; not restored |
| 24.4 | Source/language grouping options | pending | |
| 24.5 | Reader download actions | pending | |
| 24.6 | Auto Reread service counters | pending | |
| 24.7 | Recovery-branch additions | not restored | Absent from pinned `main` |

### 25. Removed / superseded

| Item | Handling |
|------|----------|
| Fillermark UI | SUPERSEDED — data migrated (4.2) |
| Old SY-style auto-scroll UI | Keep existing HEAD controls until the fork automation (9.2) provides an equivalent; never leave the reader without automation |
| Duplicate-management screen | SUPERSEDED (fork experiment) — keep any Komi-kun duplicate handling |
| Fork-specific migrations | Data preserved through batch 1 conversion |

## Acceptance matrix (features.md § 26)

| # | Scenario | Expected | Verification | Result | Commit | Limitations |
|---|----------|----------|--------------|--------|--------|-------------|
| 1 | Shounen/Reading vs Josei/Reading; manga only in Shounen; status change must not add Josei | No Josei change | Unit test | not run | — | — |
| 2 | Manga only in Shounen/Reading; Completed reaches Shounen/Completed via ancestor; Josei untouched | Ancestor reachability | Unit test | not run | — | — |
| 3 | Manga in both branches; each scoped rule updates its branch | Independent updates | Unit test | not run | — | — |
| 4 | Top-level status category works globally | Global binding | Unit test | not run | — | — |
| 5 | Two children named Reading; assign/rename/move/filter/delete/merge one leaves the other | Identity isolation | Unit tests | not run | — | — |
| 6 | Rapid reorder persists hierarchy/order | Stable order | Unit test + manual | not run | — | — |
| 7 | Backup/restore nested categories, smart policy, update intervals | Round trip | Unit test | not run | — | — |
| 8 | Blacklist extras with 1/6; chapters 1 and 6 visible | No false positives | Unit test | not run | — | — |
| 9 | Priority/override change; list, reader, counts, History agree | One representative | Unit tests | not run | — | — |
| 10 | Continue from hidden chapter in History; history record unchanged | Next visible opens | Unit test | not run | — | — |
| 11 | Source refresh keeps colors, blacklist, read flags, merge decisions | Preserved | Unit test | not run | — | — |
| 12 | Bookmark color changes propagate (manga + reader navigator) | Immediate | Manual (device) | not run | — | — |
| 13 | Multi-service status/score/date edit with partial failure | Successful writes kept | Unit test | not run | — | — |
| 14 | Reading older chapter does not lower progress | No rollback | Unit test | not run | — | — |
| 15 | MangaBaka metadata/dates/search/relations/bindings survive restart/backup | Preserved | Unit + manual | not run | — | — |
| 16 | Category movement only after persisted status change | Ordering | Unit test | not run | — | — |
| 17 | Auto-scroll/auto-flip speed, duration, sleep/background, end | Stops correctly | Manual (device) | not run | — | — |
| 18 | Reader title/back destination; bookmark icon color + rounded feedback | Correct | Manual (device) | not run | — | — |
| 19 | Confirmed Webtoon fit/gap controls | Where they exist | Manual (device) | not run | — | — |
| 20 | Saved filtered search as feed; retry; refresh-all; filters intact | Preserved | Unit + manual | not run | — | — |
| 21 | Source-category Global Search only eligible sources; disabled sources idle | Scoped | Unit test | not run | — | — |
| 22 | Browse search input usable during long request | Responsive | Manual (device) | not run | — | — |
| 23 | Backup/restore feeds + saved searches with identity | Round trip | Unit test | not run | — | — |
| 24 | Backup/restore custom metadata, hierarchy, colors, blacklist, scanlator rules; no same-title mixing | Round trip | Unit test | not run | — | — |
| 25 | SyncYomi preserves fork fields | Preserved | Unit test | not run | — | — |
| 26 | One override/blacklist change does not recompute unrelated results | Targeted | Unit test | not run | — | — |
| 27 | Release prediction + category update policies | Correct selection | Unit test | not run | — | — |
| 28 | Unit tests, migrations, debug/release builds, device checks | All pass | Gradle + manual | not run | — | — |

## Session log

### 2026-10-20 — session 1

- Verified state: branch `codex/komikun-foundation`, HEAD `f5965c217c`, clean tree, no operation in progress;
  Komi-kun #53/#55/#58/#59/#60/#61 present; `main` = backup = `0ff967c77f`.
- Read `features.md` (27 sections, 28 scenarios) and wrote this tracker.
- Compared both fresh schemas (see "Baseline facts").
- Next: batch 1.

### Batch 1 — persistence and old-fork database conversion

- **Schema:** `57.sqm` (schema 58) adds `category.parent_id/track_status/update_interval/date_added/
  date_modified`, `chapter.user_bookmark_color`, `manga.user_blacklisted_chapters/user_scanlator_priority/
  user_scanlator_range_rules/user_smart_merge_override` (+ `category_parent_id_index`); the Komi-kun sync
  version triggers are recreated to also bump on these columns. `.sq` files updated so fresh = upgraded.
- **Domain/data:** `Category`, `CategoryUpdate`, `Chapter`, `ChapterUpdate`, `Manga`, `MangaUpdate`, mappers and
  repositories carry the fields; `BookmarkColor` (values 0–6 = old fork), `Category.childUid`,
  `chapterUrlBlacklistKey`/`legacyFillermarkBlacklistKeys`.
- **Restore (pending feature batches):** the generic restore keeps the stored KNS values for existing rows
  (`blacklistedChapters`, scanlator rules, Smart Merge override, bookmark color) and inserts incoming values
  for new rows; feature-specific merge semantics come with batches 2–3.
- **Old-fork database:** `tachiyomi.data.compat.OldForkDatabaseConverter` + `FreshSchemaCapture`, hooked by
  `mihon/app/di/OldForkDatabaseGate.kt` into both connection factories of `AppBindings`.
  Mappings: `state_version/state_is_syncing/state_last_modified_at` → sync columns; old `user_favorite_at`
  (change time) + `state_date_added` → `user_favorite_at` (date added) + `favorite_modified_at` (seconds);
  old chapter sort bits 0x200/0x400/0x600 → 0x100/0x200/0x300, Fillermark filter bits 0x080/0x100 dropped;
  Fillermarks → `url:` keys; category uids (roots by name, children by parent uid). Dropped old-only
  tables: `manga_chapter_stats`, `library_visibility_revision`, `library_visibility_generation`.
- **Discovered for later batches:** old-fork SharedPreferences use the old chapter-flag layout for default
  chapter flags and store Smart Merge overrides per manga (`smartScanlatorMergeOverride(id)`); old-fork
  backups carry chapter flags in the old layout. Both need conversion before an in-place upgrade.
- **Tests:** `OldForkDatabaseConverterTest` (4: detection, schema identical to fresh, data through
  repositories, atomic rollback), `KnsColumnsMigrationTest`, `CategoryUidTest` (5).
- **Validation (all exit 0):** `spotlessApply` (no changes), `spotlessCheck`,
  `:data:generateSqlDelightInterface`, `:data:verifyDebugDatabaseMigration`, `testDebugUnitTest`
  (app 163, data 71, domain 124; 0 failures; 1 pre-existing `@Disabled`), `assembleDebug`,
  `assembleRelease`, `git diff --check`.
- **Changelog:** Unreleased → Other: migration 57; old-fork database conversion.
