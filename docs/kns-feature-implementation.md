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
| 2a | Category hierarchy + Smart Categories: domain, data, backup/restore, sync, tracker hook | done | see session log |
| 2b | Category manager UI, Set Categories dialog, library subcategory navigation/filters | done | see session log |
| 3 | Chapter blacklist, bookmark colors, Fillermark migration, Smart Scanlator Merge, unified visibility | done | `2af2c9215a` |
| 4 | Library search/filters/sort/presentation + performance | partial (search fields, color filters); sort/presentation open | `5f901e1f43` |
| 5 | Tracking: progress correctness, MangaBaka, unified editor, preferred/priority | partial (progress safety, unified editor, preferred/priority); batch actions open | `689b2339df` |
| 6 | Manga details/relations, reader controls & automation | partial (tracker relations `12a038e658`, reader automation `65e92a8e98`); title/back parity open | see session log |
| 7 | Browse / Global Search / feeds / extensions | planned | — |
| 8 | History, Updates, release prediction, category update schedules | partial (category schedules, refresh scope, History day counts `0f759e47aa`); release prediction open | see session log |
| 9 | Auto Reread, webhooks, Discord verification, storage, settings | partial (Auto Reread, webhooks `d042b5196f`; storage overview — batch 10) | see session log |
| 10 | Remaining items (storage, History filters, …), final audit | in progress | see session log |

## Requirements

Columns: **ID** (section.subsection-letter) · **Requirement** · **Status** · **Notes** (existing behavior to
keep / what is missing / persistence, backup, sync) · **Tests** · **Commit**.

### 1. Library organization and nested categories

| ID | Requirement | Status | Notes | Tests | Commit |
|----|-------------|--------|-------|-------|--------|
| 1.1-a | Hierarchical categories (parent/child) across organization, browsing, dialogs, persistence | DONE-UNVERIFIED | Storage, domain, backup/sync (2a); manager tree, assignment dialog, library tabs + subcategory chips (2b). Manager UI not exercised on a device yet | `CategoryHierarchyTest`, `CategoryManagerRowsTest` (7), `LibraryCategoryTreeTest` (4) | batch 2a + 2b |
| 1.1-b | Same names under different parents stay distinct (identity = id + parent) | DONE | Identity = id/uid; names unique per parent (`CategoryTree.hasSiblingName`); child uids `Category.childUid(parentUid, name)`; restore, manga category references and SyncYomi merges scoped by parent | `CategoryTreeTest`, `CategoryHierarchyTest`, `BackupCategoryHierarchyTest`, `SyncCategoryHierarchyTest`, `CategoryUidTest` | batch 2a |
| 1.1-c | Parent-child membership consistency (child assignment implies ancestry; no orphan/unrelated memberships) | DONE-UNVERIFIED | Assignment dialog stores a child with its ancestor chain (`categorySelectionChange`); Smart Categories keep the branch parent | `CategoryManagerRowsTest` (7), `SyncCategoriesWithTrackStatusTest` | batch 2a + 2b |
| 1.1-d | Library navigation for nested categories (subcategory chips); parent view honors include-descendants | DONE-UNVERIFIED | Top-level tabs include their subtree; `LibrarySubcategoryChips` narrows to a branch (any depth). Komi-kun #55 tab selection kept. Manager UI not exercised on a device yet | `LibraryCategoryTreeTest` (4) | batch 2a + 2b |
| 1.1-e | Parent-inclusive counts with duplicate handling | DONE | Tab counts and manager counts are distinct manga of the subtree | `LibraryCategoryTreeTest` (4), `CategoryManagerRowsTest` (7) | batch 2a + 2b |
| 1.1-f | Parent/child display mode option (verify historical toggles) | HISTORICAL | Not restored: no separate display toggle; flat libraries are unchanged and nested ones use tabs + chips. Revisit if the old toggle is wanted | — | — |
| 1.1-g | Default-category behavior (no empty/duplicate Default tab) | PARTIAL | Komi-kun library tabs; verify with hierarchy | — | — |
| 1.2-a | Tree-based category manager | DONE-UNVERIFIED | `CategoryScreen` tree (indentation, counts, status/interval details). Manager UI not exercised on a device yet | `CategoryManagerRowsTest` (7) | batch 2a + 2b |
| 1.2-b | Expand/collapse branches + expand-all/collapse-all | DONE-UNVERIFIED | Per-row expand/collapse, expand/collapse all, persisted (`category_manager_collapsed_ids`) | `CategoryManagerRowsTest` (7) | batch 2a + 2b |
| 1.2-c | Category search with hierarchy context | DONE-UNVERIFIED | Search shows matches with ancestors and full path | `CategoryManagerRowsTest` (7) | batch 2a + 2b |
| 1.2-d | Create child category (choose parent) | DONE-UNVERIFIED | Row menu → Create subcategory (sibling-name check) | `CategoryHierarchyTest` | batch 2a + 2b |
| 1.2-e | Rename without changing parent / conflating siblings | DONE-UNVERIFIED | Rename dialog checks sibling names only | `CategoryHierarchyTest` | batch 2a + 2b |
| 1.2-f | Move/reparent with cycle prevention | DONE-UNVERIFIED | Move dialog lists only valid parents (no self/descendants) | `CategoryHierarchyTest` | batch 2a + 2b |
| 1.2-g | Merge categories (hierarchy rules) | DONE-UNVERIFIED | Selection → Merge → pick target among selected | `CategoryHierarchyTest` | batch 2a + 2b |
| 1.2-h | Sort and manual reorder, stable hierarchy | DONE-UNVERIFIED | Drag among siblings (manual sort) + 9 display sorts per level (`category_manager_sort_mode`) | `CategoryManagerRowsTest` (7), `CategoryHierarchyTest` | batch 2a + 2b |
| 1.2-i | Batched/debounced reorder writes | DONE-UNVERIFIED | One `ReorderCategory` write per drop; each hierarchy change is one transaction | `CategoryManagerRowsTest` (7) | batch 2a + 2b |
| 1.2-j | Bulk selection | DONE-UNVERIFIED | Selection mode (toolbar or long-press), select all shown | — | batch 2a + 2b |
| 1.2-k | Bulk hide/show (keeps membership) | DONE-UNVERIFIED | Selection → Hide/Show selected | — | batch 2a + 2b |
| 1.2-l | Bulk move/merge/delete respecting descendants | DONE-UNVERIFIED | Selection → Move / Merge / Delete (children move up) | `CategoryHierarchyTest` | batch 2a + 2b |
| 1.2-m | Counts, hierarchy/selection indicators, list state kept | DONE-UNVERIFIED | Subtree manga counts, depth indentation, selection checkboxes; list state kept while dragging | `CategoryManagerRowsTest` (7) | batch 2a + 2b |
| 1.3-a | Hierarchy-aware Set Categories dialog | DONE-UNVERIFIED | `ChangeCategoryDialog` tree order + indentation, id-based | `CategoryManagerRowsTest` (7) | batch 2a + 2b |
| 1.3-b | Correct bulk assignment (keep unrelated memberships) | DONE-UNVERIFIED | Bulk assignment keeps other memberships (Mihon tri-state) and adds ancestors of included children | `CategoryManagerRowsTest` (7) | batch 2a + 2b |
| 1.3-c | Tabs/chips/counts/filters react to category edits | PARTIAL | Reactive flows exist; verify with hierarchy ops | — | — |
| 1.3-d | Search/filter does not jump to invalid tab | PARTIAL | Komi-kun #55 resolves selection from rendered list | — | — |
| 1.3-e | Include/exclude category filters correct with tree changes and same-name children | DONE-UNVERIFIED | Include/exclude filters use ancestry (`CategoryTree.withAncestors`), id-based | — (logic in LibraryViewModel) | batch 2a + 2b |
| 1.4-a | Tracker-status-driven categories | DONE-UNVERIFIED | `Category.trackStatus` + `SyncCategoriesWithTrackStatus` + `SyncCategoryTrackStatus` (app, unified `TrackStatus`); binding UI pending (1.4-k) | `SyncCategoriesWithTrackStatusTest` (11) | batch 2a |
| 1.4-b | (Rationale) no cross-branch assignment by status alone | DONE | Covered by 1.4-c..h | `SyncCategoriesWithTrackStatusTest` (11) | batch 2a |
| 1.4-c | Subcategory binding eligible only if parent is a member or ancestor of a member | DONE | Subcategory binding in scope iff its parent is a member or an ancestor of a member | `SyncCategoriesWithTrackStatusTest` (11) | batch 2a |
| 1.4-d | Reachability walk (transitive ancestry) | DONE | `CategoryTree.withAncestors` (transitive) | `SyncCategoriesWithTrackStatusTest` (11) | batch 2a |
| 1.4-e | Out-of-scope branches completely untouched | DONE | Only in-scope bound categories are added/removed; everything else untouched | `SyncCategoriesWithTrackStatusTest` (11) | batch 2a |
| 1.4-f | Multiple relevant parents update independently | DONE | Each reachable branch evaluated independently | `SyncCategoriesWithTrackStatusTest` (11) | batch 2a |
| 1.4-g | Top-level status categories stay global | DONE | `parentId == 0` bindings always in scope | `SyncCategoriesWithTrackStatusTest` (11) | batch 2a |
| 1.4-h | Deeper nesting | DONE | Three-level test (Shounen / Ongoing / Reading) | `SyncCategoriesWithTrackStatusTest` (11) | batch 2a |
| 1.4-i | Hierarchy-safe status changes (start/complete/reset) | DONE | Status changes move between status categories inside each branch; the branch parent is kept as anchor | `SyncCategoriesWithTrackStatusTest` (11) | batch 2a |
| 1.4-j | Category update only after persisted tracker state | DONE | Triggered by `UpsertTrack` only after a successful write (`TrackPersistenceListener`); no usable persisted status = no change | `UpsertTrackTest` (3) | batch 2a |
| 1.4-k | Category status policy configuration UI | DONE-UNVERIFIED | Manager row menu → Tracker status picker (unified `TrackStatus`, none) | — | batch 2a + 2b |
| 1.5-a | Per-category update intervals | DONE | Scheduled runs apply `category.update_interval` (inherited from the nearest ancestor) via `CategoryUpdateSchedule`; last run per category in `library_update_category_last_run` (device state) | `CategoryUpdateScheduleTest` | batch 2a + 8 |
| 1.5-b | Subcategory update scope | DONE | `pref_library_update_subcategory_scope` (whole category / selected subcategory); category refreshes cover the subtree | `CategoryUpdateScheduleTest` | batch 8 |
| 1.5-c | Category-specific picker + persisted settings | DONE | Manager picker (batch 2b) + Settings → Library scope; persisted and backed up (interval) | — | batch 2b + 8 |
| 1.5-d | Updater respects policy, no duplicate work for multi-category manga | DONE | A manga is due through any of its categories, a global one always wins; `distinctBy` manga id kept | `CategoryUpdateScheduleTest` | batch 8 |

### 2. Library browsing, filtering, sorting, presentation

| ID | Requirement | Status | Notes | Tests | Commit |
|----|-------------|--------|-------|-------|--------|
| 2.1-a | Parsed search expressions | DONE | Mihon/Komi-kun AST parser (fields, `-`, `||`, parentheses, comparisons, SY metadata) kept; the old fork's parser is superseded, its extra fields added as `LibraryFieldQueryNode` | `LibrarySearchParserTest`, `LibraryFieldQueryTest` | batch 4 |
| 2.1-b | Field-specific terms (fork parser's operators only) | DONE | Old-fork-only fields restored: `category:`/`cat:`, `tracker:`, `status:` (`LibrarySearchFields`); the old `$` exact prefix is not restored (the AST has its own quoting) | `LibraryFieldQueryTest`, `LibrarySearchFieldsTest` | batch 4 |
| 2.1-c | Negative/exclusion terms | DONE | `-term`, `-field:value`, NOT via the AST; library fields honor negation | `LibraryFieldQueryTest` | batch 4 |
| 2.1-d | Search respects selected parent/child view and filters | PARTIAL | Search runs over the tab's subtree and the selected chip branch; structured parser pending (batch 4) | `LibraryCategoryTreeTest` (4) | batch 2a + 2b |
| 2.1-e | Responsive live search | PRESENT | Search input debounced 250 ms before filtering (`searchQuery.debounce`); filtering runs off the main thread (`flowOn(IO)`) | — | — |
| 2.1-f | Empty-result correctness | PARTIAL | | — | — |
| 2.2-a | Include categories (tree semantics) | DONE-UNVERIFIED | See 1.3-e | — | batch 2a + 2b |
| 2.2-b | Exclude categories/descendants | DONE-UNVERIFIED | See 1.3-e | — | batch 2a + 2b |
| 2.2-c | All-category subcategory exclusion | HISTORICAL | | — | — |
| 2.2-d | Tracker filters without per-title queries | PARTIAL | Mihon tracker filters exist | — | — |
| 2.2-e | Bookmark-color filter | DONE | `pref_filter_library_bookmark_colors` + filter sheet row/dialog; colors read with one query (`getBookmarkColorsByManga`, merged members included) only while the filter is set | `BookmarkColorsByMangaTest`, `BookmarkColorFilterTest` | batch 4 |
| 2.2-f | Built-in filters account for blacklist/Smart Merge | DONE | Unread/started/bookmarked filters read the recomputed counts; downloaded badge/filter count downloads of visible chapters (`LibraryVisibleCounts.visibleChapters`); the color filter still uses stored colors | `LibraryVisibleCountsTest` | batch 3 + 4 + 10 |
| 2.2-g | Merged-entry downloaded/unread correctness | PARTIAL | Continue-reading of merged entries fixed (merged check compared the manga id); downloaded/unread filters for merged entries batch 4 | — | batch 3 |
| 2.3-a | Group by source without expensive regrouping | PRESENT | Komikku/Komi-kun group by source (non-blocking source-name snapshot); nothing fork-specific to add | — | — |
| 2.3-b | Source language in source grouping (+ flag option) | HISTORICAL | The old fork's language suffix on source groups was commented out (`TODO`), so it never shipped; covers already show language badges/flags (KMK `useLangIcon`) | — | — |
| 2.3-c | Search source name and language | HISTORICAL | | — | — |
| 2.3-d | Source/language badges on covers | PRESENT | KMK source and language badges (`LibraryBadges`, flag option) | — | — |
| 2.4-a | Extended sort/display choices | PARTIAL | Identify fork-only sort modes in old `LibrarySort` | — | — |
| 2.4-b | Correct sort behavior after updates | PARTIAL | | — | — |
| 2.4-c | Custom cover ratios/styles/badge colors | PARTIAL | Komikku cover ratio exists | — | — |
| 2.4-d | Parent tab reveals/toggles subcategory chips | DONE-UNVERIFIED | Subcategory chips under the tab row (All / path / children) | — | batch 2a + 2b |
| 2.4-e | Stable targeted list-item updates | PARTIAL | | — | — |

### 3. Manga details, metadata, relations

| ID | Requirement | Status | Notes | Tests | Commit |
|----|-------------|--------|-------|-------|--------|
| 3.1-a | Custom metadata with backup/sync | PRESENT | SY `CustomMangaInfo` + backup; verify sync | — | — |
| 3.1-b | Fork description/alternative-metadata formatting | HISTORICAL | Inspect old `MangaInfoHeader` diffs | — | — |
| 3.1-c | Avoid re-preparing rich description on recomposition | MISSING | | — | — |
| 3.1-d | Metadata updates respect local customizations | PARTIAL | Komi-kun #60 clearable fields; verify | — | — |
| 3.1-e | Cover presentation without repeated DB observation | PARTIAL | | — | — |
| 3.1-f | Related-manga metadata from trackers | DONE | Related series metadata (title, cover, relation) from AniList/MangaBaka bindings | `AnilistRelationsTest`, `MangaBakaRelationsTest` | batch 6 |
| 3.2-a | Related-title lookup via AniList/MangaBaka | DONE | `Tracker.getRelatedEntries` (AniList GraphQL `AniListGetMangaRelations`, MangaBaka `relationships_v2`) | `AnilistRelationsTest`, `MangaBakaRelationsTest` | batch 6 |
| 3.2-b | Sequel/prequel rows on details | DONE | `SequelPrequelRow` on the entry screen (phone + tablet layouts), behind `pref_sequel_prequel_enabled` (Settings → Browse) | manual pending | batch 6 |
| 3.2-c | Cross-service fallback | DONE | Preferred tracker first, then the other linked trackers, then MangaDex relations for MangaDex entries | `GetSequelPrequelTest` | batch 6 |
| 3.2-d | Compact horizontal relation cards | DONE | Compact 112 dp cards with relation label, cover, title and In library | manual pending | batch 6 |
| 3.2-e | Bounded relation-search concurrency | DONE | MangaBaka related series fetched sequentially, at most 50; MangaDex at most 100; one lookup per manga at a time (mutex) and a 24 h in-memory cache | `GetSequelPrequelTest` | batch 6 |
| 3.3-a | Bookmark-color controls in chapter UI | DONE | Selection bar Bookmark color action (bulk) + `BookmarkColorDialog`; colored bookmark icon in the chapter row | manual pending | batch 3 |
| 3.3-b | Chapter filter controls display/reset | PARTIAL | | — | — |
| 3.3-c | Reveal hidden (blacklisted) chapters | DONE | Blacklisted chapters dialog toggles reveal (`pref_hide_blacklisted_chapters_<id>`); revealed rows dimmed + block icon, selectable for un-blacklist | `ChapterSmartMergeTest` reveal case | batch 3 |
| 3.3-d | Chapter sort choices stable after sync | PARTIAL | | — | — |
| 3.3-e | Multi-select actions refresh indicators | PARTIAL | | — | — |
| 3.3-f | No tracking popup after dismissal for non-favorites | MISSING | Verify current behavior | — | — |

### 4. Colored bookmarks and Fillermark migration

| ID | Requirement | Status | Notes | Tests | Commit |
|----|-------------|--------|-------|-------|--------|
| 4.1-a | Bookmark color as chapter state (fixed palette) | DONE | `chapter.user_bookmark_color` (migration 57) + `BookmarkColor` 0–6 | `KnsColumnsMigrationTest` | batch 1 + 3 |
| 4.1-b | Normal bookmark behavior unchanged | PRESENT | Keep | — | — |
| 4.1-c | Color picker in chapter list (incl. bulk) | DONE | Selection bar → `BookmarkColorDialog`; choosing a color bookmarks; plain (un)bookmark resets to default | manual pending | batch 3 |
| 4.1-d | Color picker in reader | DONE | Long-press the reader bookmark button → `BookmarkColorDialog` (`ReaderViewModel.setChapterBookmarkColor`) | manual pending | batch 3 |
| 4.1-e | Reactive display of saved color | DONE-UNVERIFIED | Manga list observes chapters; reader top bar state carries `bookmarkColor` | manual pending | batch 3 |
| 4.1-f | Filter by color | DONE | Library filter (2.2-e) and per-manga chapter filter `manga_bookmark_color_filter_<id>` in the chapter filter sheet; the manga's filter icon shows it as active | `BookmarkColorFilterTest` | batch 4 |
| 4.1-g | Reader chapter-list sync on color change | DONE-UNVERIFIED | Reader chapter list shows the color; toggling there resets it and updates the top bar for the current chapter | manual pending | batch 3 |
| 4.1-h | Rounded reader bookmark ripple | DONE | Long-press bookmark button clipped to a circle (round ripple) | manual pending | batch 3 |
| 4.1-i | Color survives source refresh | DONE | `SyncChaptersWithSource` carries a removed chapter's color to its re-upload | `SyncChaptersBookmarkColorTest` | batch 3 |
| 4.1-j | Color in backup + sync | DONE | `BackupChapter` #14 (nullable → unknown, never stored); sync: newer side owns the color, ordinary restore never resets | `RestoreRepositoryForkStateTest`, `BackupForkStateTest` | batch 3 |
| 4.2-a | Fillermark → blacklist/color migration | DONE | Database: converter; backup: #15 fillermark → exact URL blacklist entries | `OldForkDatabaseConverterTest`, `BackupForkStateTest` | batch 1 + 3 |
| 4.2-b | Uncolored legacy bookmarks stay default | DONE | Missing color decodes as unknown and leaves the stored value (default 0) alone | `BackupForkStateTest` | batch 3 |
| 4.2-c | Identity-aware conversion | DONE | Fillermarks become `url:` keys (exact); new selections use hybrid keys | `ChapterBlacklistTest`, `BackupForkStateTest` | batch 1 + 3 |
| 4.2-d | No standalone Fillermark UI | SUPERSEDED | Migration only | — | — |

### 5. Chapter blacklist and visibility

| ID | Requirement | Status | Notes | Tests | Commit |
|----|-------------|--------|-------|-------|--------|
| 5.1-a | Blacklist individual chapters (no deletion) | DONE | `manga.user_blacklisted_chapters`; hiding never deletes chapter rows | `ChapterBlacklistTest` | batch 3 |
| 5.1-b | Per-manga blacklist management UI | DONE | Overflow → Blacklisted chapters: entries with resolved chapter names, unavailable marker, remove | manual pending | batch 3 |
| 5.1-c | Reveal blacklisted chapters | DONE | See 3.3-c | `ChapterSmartMergeTest` | batch 3 |
| 5.1-d | Exact identity (URL) first, controlled fallback | DONE | Hybrid keys: URL first, unique metadata fallback, ambiguous → visible | `ChapterBlacklistTest` | batch 3 |
| 5.1-e | No number/name collisions (extras vs ch.1/6) | DONE | Extras named like numbers never hide numbered chapters (Mangago cases) | `ChapterBlacklistTest` | batch 3 |
| 5.1-f | Disambiguate by URL/scanlator | DONE | URL + scanlator + name discriminator | `ChapterBlacklistTest` | batch 3 |
| 5.1-g | Survive source refresh | DONE | Keys resolve against the full refreshed snapshot | `ChapterBlacklistTest` | batch 3 |
| 5.1-h | Keep history/bookmarks of hidden chapters | DONE | Rows (history, bookmarks, read state) untouched; only display filtered | `ChapterBlacklistTest` | batch 3 |
| 5.2-a | Chapter list hides blacklisted unless revealed | DONE | `chapterListVisibility` in `MangaViewModel` | `ChapterSmartMergeTest` | batch 3 |
| 5.2-b | Reader next/prev skip hidden | DONE | `ReaderViewModel` loads `ChapterVisibility.visibleChapters` (prev/next + chapter list) | `ChapterSmartMergeTest` | batch 3 |
| 5.2-c | History continuation skips hidden | DONE | `GetNextChapters` resolves a hidden start (after / representative / end) | `GetNextChaptersVisibilityTest` | batch 3 |
| 5.2-d | Library unread/total exclude hidden | DONE | `LibraryVisibleCounts` (total/read/unread/bookmark) for entries with rules | `LibraryVisibleCountsTest` | batch 3 |
| 5.2-e | Downloads / next-chapter follow visibility | DONE | `FilterChaptersForDownload`, library + manga continue-reading, Updates feed (`UpdatesVisibilityFilter`) | `UpdatesVisibilityFilterTest` | batch 3 |
| 5.2-f | Deep link / notification / history fallback to visible equivalent | DONE-UNVERIFIED | Reader reassigns a hidden duplicate to its representative; an explicitly opened blacklisted chapter still opens | `ChapterSmartMergeTest` | batch 3 |

### 6. Scanlator preferences and Smart Scanlator Merge

| ID | Requirement | Status | Notes | Tests | Commit |
|----|-------------|--------|-------|-------|--------|
| 6.1-a | Exclude scanlators per manga | PRESENT | `excluded_scanlator` table + Komi-kun #60 sync; keep, do not duplicate | Komi-kun tests | — |
| 6.1-b | Prioritize scanlators | DONE | `manga.user_scanlator_priority`; Scanlator preferences dialog reorders | `ChapterSmartMergeTest` | batch 3 |
| 6.1-c | Manga-specific settings | DONE | Per-manga priority + override on the manga row | `RestoreRepositoryForkStateTest` | batch 3 |
| 6.1-d | No deletion of hidden chapter records | DONE | Display-only filtering | `ChapterSmartMergeTest` | batch 3 |
| 6.2-a | Group duplicates, show representative | DONE | `applySmartScanlatorMerge` | `ChapterSmartMergeTest` | batch 3 |
| 6.2-b | Priority-based representative | DONE | Range rules over priority, last matching rule wins; editor in Scanlator preferences (add/remove) | `ChapterSmartMergeTest` (rule parsing/precedence) | batch 3 + 10 |
| 6.2-c | Per-manga override | DONE | `manga.user_smart_merge_override` (null = global); old prefs migrated by `SmartMergeOverrideMigration`; backup #618 | `SmartMergeOverrideMigrationTest`, `BackupForkStateTest` | batch 3 |
| 6.2-d | Incremental override updates | DONE | Library counts cached per entry, keyed by counts + rules | `LibraryVisibleCountsTest` | batch 3 |
| 6.2-e | Counts reflect representatives | DONE | See 5.2-d | `LibraryVisibleCountsTest` | batch 3 |
| 6.2-f | Reader navigates representatives | DONE | See 5.2-b | `ChapterSmartMergeTest` | batch 3 |
| 6.2-g | History/deep links resolve to representative | DONE | See 5.2-c / 5.2-f | `GetNextChaptersVisibilityTest` | batch 3 |
| 6.2-h | Merged manga read state | DONE-UNVERIFIED | Merged entries use `GetMergedChaptersByMangaId` in counts, reader and next-chapter | — | batch 3 |
| 6.2-i | Consistent with blacklist | DONE | One rule: `filterChapterVisibility` / `ChapterVisibility` | `ChapterSmartMergeTest` | batch 3 |
| 6.2-j | Predictable toggles, no orphaned cache | DONE | Cache drops entries without rules | `LibraryVisibleCountsTest` | batch 3 |

### 7. Tracking services

| ID | Requirement | Status | Notes | Tests | Commit |
|----|-------------|--------|-------|-------|--------|
| 7.1-a | Fork MangaBaka integration (search, binding, progress, status/score/date) | PARTIAL | Mihon MangaBaka tracker on HEAD; diff old fork's service for fork-only behavior | — | — |
| 7.1-b | MangaBaka metadata + related series | PARTIAL | MangaBaka related series done; MangaBaka-driven metadata autofill not implemented | `MangaBakaRelationsTest` | batch 6 |
| 7.1-c | MangaBaka date fidelity | PARTIAL | Verify conversions | — | — |
| 7.2-a | AniList relation integration | DONE | AniList relations (novels filtered by format) | `AnilistRelationsTest` | batch 6 |
| 7.2-b | MangaUpdates fork handling + unified binding | PARTIAL | | — | — |
| 7.2-c | Remote error presentation in unified editor | DONE | Unified edits report each failing tracker (toast + `Last update failed` marker until it succeeds again) | `UpdateTracksTest`, `UnifiedTrackerEditsTest` | batch 5 |
| 7.3-a | No progress rollback | DONE | `TrackChapter` sends max(refreshed remote, read chapter) | `TrackChapterProgressTest` | batch 5 |
| 7.3-b | Reject nonfinite/negative chapter numbers | DONE | `TrackChapter` ignores NaN/infinite/negative numbers; `TrackerProgressSync` ignores them | `TrackChapterProgressTest`, `TrackerProgressSyncTest` | batch 5 |
| 7.3-c | Refresh before advancing when needed | DONE | Automatic and unified writes refresh first and save the record the service returns (`TrackChapter`, `SyncChapterProgressWithTrack`, `AddTracks.bind`, `UpdateTracks`) | `TrackChapterProgressTest`, `UpdateTracksTest` | batch 5 |
| 7.3-d | Start-tracking status correctness | PARTIAL | | — | — |
| 7.3-e | Automatic progress propagation | PRESENT | Mihon auto-update track on read | — | — |
| 7.3-f | Smart Categories after persisted tracker state | DONE | See 1.4-j | `UpsertTrackTest` | batch 2a |
| 7.3-g | Reread counters where supported | HISTORICAL | | — | — |

### 8. Unified tracker editor

| ID | Requirement | Status | Notes | Tests | Commit |
|----|-------------|--------|-------|-------|--------|
| 8.1-a | Unified view of all linked trackers | DONE | All trackers panel in the tracking sheet (2+ linked trackers) | manual pending | batch 5 |
| 8.1-b | Edit statuses together with per-service translation | DONE | Status translated per service (MangaUpdates list names mapped), skipped without an equivalent; completion fills progress | `UpdateTracksTest` | batch 5 |
| 8.1-c | Edit scores with scale conversion | DONE | Score via the ten-point scale to the closest score of each service; services without scores skipped | `UpdateTracksTest` | batch 5 |
| 8.1-d | Edit start/finish dates | DONE | Start/finish dates with removal; order validated per service; services without dates skipped | `UpdateTracksTest` | batch 5 |
| 8.1-e | Track/untrack incl. remote removal | DONE | Remove trackers dialog in the All trackers panel (local unlink + optional remote removal, per-service failures) | `UpdateTracksTest`, `UnifiedTrackerEditsTest` | batch 5 + 10 |
| 8.1-f | Multi-title batch actions | HISTORICAL | The old fork's batch operations were across a manga's linked services (`TrackerBatch`, done in batch 5); no multi-title library action existed there | — | — |
| 8.1-g | Operation queue | DONE | `UnifiedTrackerEdits` serializes unified edits app-wide (services within one edit run concurrently) | `UnifiedTrackerEditsTest` | batch 5 |
| 8.1-h | Partial-failure resilience | DONE | `trackerBatch` (supervisor scope; per-service and per-save failures) | `UpdateTracksTest` | batch 5 |
| 8.2-a | Preferred tracker per manga | DONE | `pref_preferred_tracker_for_manga` (`PreferredTrackerMap`, bounded JSON), chosen in the panel | `TrackerProgressSyncTest` | batch 5 |
| 8.2-b | Global tracker priority | DONE | `pref_priority_tracker_id` in Settings → Tracking; used when the manga has no applicable preference | — | batch 5 |
| 8.2-c | Persist preferences (backup/sync) | PARTIAL | Kept across sheet reopenings; device-only (app-state key like the old fork, manga ids are not portable), so not in backups/sync | — | batch 5 |
| 8.2-d | Unified/individual editing consistency | DONE | Unified edits start from the preferred binding and go through each service's own conversions; individual edits are unchanged | `UpdateTracksTest` | batch 5 |
| 8.3-a | Tracker search resolver | PARTIAL | | — | — |
| 8.3-b | Tracker metadata autofill | MISSING | | — | — |
| 8.3-c | Distinct linked-service identities | PARTIAL | | — | — |
| 8.3-d | Tracker error indicator | DONE | Per-tracker error marker in the sheet (see 7.2-c) | `UnifiedTrackerEditsTest` | batch 5 |
| 8.3-e | Unified sheet in destination visual language | DONE | Panel built from M3 chips/menus and the existing selectors | manual pending | batch 5 |
| 8.3-f | No unexpected dialog reopen | MISSING | See 3.3-f | — | — |

### 9. Reader and automation

| ID | Requirement | Status | Notes | Tests | Commit |
|----|-------------|--------|-------|-------|--------|
| 9.1-a | Paged and vertical compatibility | PRESENT | Keep Komi-kun WebGPU/SSIV | — | — |
| 9.1-b | Chapter navigation honors Smart Merge/blacklist | DONE | See 5.2-b | `ChapterSmartMergeTest` | batch 3 |
| 9.1-c | Title tap = back navigation parity | DONE | Title tap = back (`onBackPressedDispatcher`), as in the old fork's fix | manual pending | batch 10 |
| 9.1-d | Reader bookmark selection + colors | DONE | See 4.1-d | manual pending | batch 3 |
| 9.1-e | Rounded bookmark touch feedback | DONE | See 4.1-h | manual pending | batch 3 |
| 9.1-f | Reader chapter list updates on color change | DONE-UNVERIFIED | Reader chapter list shows bookmark colors and resets them on toggle (see 4.1-g) | manual pending | batch 3 |
| 9.1-g | Page-jump correctness | PARTIAL | Verify | — | — |
| 9.2-a | Button-controlled auto-scroll (new system) | DONE | `ReaderAutomationController` + `ReaderAutomationControls` (slower / start-stop / faster above the bottom bar); SY's EH auto-scroll kept as is | manual pending | batch 7 |
| 9.2-b | Automatic page flip (paged) | DONE | Flip at `pref_auto_flip_interval` (1–30 s) in Pager and paged WebGPU | `ReaderAutomationTest` | batch 7 |
| 9.2-c | Webtoon automation | DONE | Per-frame scroll for Webtoon (`automationScrollBy`, fractional carry) and continuous WebGPU (`state.scrollBy`) | `ReaderAutomationTest` | batch 7 |
| 9.2-d | Speed selection with usable increments | DONE | Seconds per screen 1–10 / flip seconds 1–30 in steps of 1, shown as plurals | `ReaderAutomationTest` | batch 7 |
| 9.2-e | Refresh-rate support | DONE | Refresh rate re-read every frame (`Display.refreshRate`) | `ReaderAutomationTest` | batch 7 |
| 9.2-f | Duration limit | DONE | `pref_automation_max_minutes` (0 = off) and `pref_automation_max_chapters` (0 = off) | `ReaderAutomationTest` | batch 7 |
| 9.2-g | Stop at end | DONE | Stops when the viewer stops moving (30 frames) or at the last page of the last chapter; shows the menu | `ReaderAutomationTest` | batch 7 |
| 9.2-h | Stop on sleep/background | DONE | Stops on activity `onStop`, when not STARTED or the screen is not interactive | `ReaderAutomationTest` | batch 7 |
| 9.2-i | No background work when disabled | DONE | Nothing runs until started; controls hidden when automation is off for the viewer | — | batch 7 |
| 9.2-j | Keep screen awake while active | DONE | FLAG_KEEP_SCREEN_ON while running, restored unless the reader's keep-screen-on setting is on | manual pending | batch 7 |
| 9.2-k | Saved automation settings | DONE | Old-fork preference keys kept (`pref_auto_scroll`, `pref_auto_scroll_speed`, `pref_auto_flip`, ...) | — | batch 7 |
| 9.3-a | Webtoon pinch-zoom disable | PRESENT | Komikku `webtoonPinchToZoomEnabled` / `webtoonDisableZoomOut` in Settings → Reader | — | — |
| 9.3-b | Webtoon scale-type controls | PRESENT | Komikku `webtoonScaleType` (Settings → Reader and the reader sheet) | — | — |
| 9.3-c | Long-strip gap preference | PRESENT | Komikku `longStripGapSmartScale` + side padding | — | — |
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
| 13.1-a | Daily distinct-manga count | DONE | `HistoryUiModel.Header.mangaCount` (distinct manga per day) | `HistoryDayCountTest` | batch 8 |
| 13.1-b | Date-grouped history with count | DONE | Date headers keep grouping and add the count | `HistoryDayCountTest` | batch 8 |
| 13.1-c | Initial loading vs empty | PRESENT | KMK `State.isLoading` shows a loading screen before the empty screen | — | — |
| 13.1-d | Visibility-aware history filters | DONE | `HistoryVisibleCounts` recomputes counts for manga with rules; the unfinished filter runs on them | `HistoryVisibleCountsTest` | batch 10 |
| 13.2-a | Continue to next readable chapter | PARTIAL | Mihon `GetNextChapters` | — | — |
| 13.2-b | Skip blacklisted | DONE | See 5.2-c | `GetNextChaptersVisibilityTest` | batch 3 |
| 13.2-c | Resolve Smart Merge duplicates | DONE | See 5.2-c | `GetNextChaptersVisibilityTest` | batch 3 |
| 13.2-d | Preserve history identity | DONE | History rows are not rewritten | `GetNextChaptersVisibilityTest` | batch 3 |
| 13.2-e | Merged-title handling | PARTIAL | | — | — |
| 13.3-a | Mark-read without duplicates | PRESENT | | — | — |
| 13.3-b | Next-chapter consistency across surfaces | DONE-UNVERIFIED | Library, manga screen, History and downloads share `ChapterVisibility` | `GetNextChaptersVisibilityTest`, `LibraryVisibleCountsTest` | batch 3 |
| 13.3-c | Source update keeps user flags | DONE | Color carried over with read/bookmark; blacklist keys are per manga | `SyncChaptersBookmarkColorTest` | batch 3 |

### 14. Updates and release prediction

| ID | Requirement | Status | Notes | Tests | Commit |
|----|-------------|--------|-------|-------|--------|
| 14.1-a | Accurate read/download indicators | PARTIAL | | — | — |
| 14.1-b | Correct refresh/error states | PARTIAL | | — | — |
| 14.2-a | Smart release interval prediction | SUPERSEDED | HEAD's `FetchInterval` (upstream) already predicts per title from upload/fetch history with hiatus, weekday and batch-release detection; the old fork's median/recurring-gap version (`calculateInterval`) is older and is not ported | `FetchIntervalTest` (upstream) | — |
| 14.2-b | Per-title predictions | PRESENT | Per-title prediction (upstream `FetchInterval`) | `FetchIntervalTest` | — |
| 14.2-c | Refresh on new evidence | PRESENT | Recomputed on chapter updates (upstream) | `FetchIntervalTest` | — |
| 14.2-d | Shown as estimates | PRESENT | Shown as expected update dates in Upcoming/manga info (upstream wording) | — | — |
| 14.3-a | Category-specific scheduling | DONE | See 1.5-a/1.5-d | `CategoryUpdateScheduleTest` | batch 8 |
| 14.3-b | Controlled parallel update slots | DONE | `library_update_parallel_slots` (old key; 1–10, default 5) for library and metadata updates | — | batch 10 |
| 14.3-c | Timestamp after completion | DONE | Category last-run times are saved only after a scheduled chapter update completes | — | batch 8 |
| 14.3-d | Cancellation | PRESENT | | — | — |
| 14.3-e | Reliable notifications | PARTIAL | Komi-kun #47 | — | — |
| 14.3-f | No duplicate chapter creation | PRESENT | Komi-kun #58 + Mihon | — | — |

### 15. Backup, restore, migration

| ID | Requirement | Status | Notes | Tests | Commit |
|----|-------------|--------|-------|-------|--------|
| 15.1-a | Category hierarchy in backup | DONE | `BackupCategory` #901 parentId (backup id), #904 parentUid, #907 hierarchyKnown; restored parents-first | `BackupCategoryHierarchyTest`, `CategoryHierarchyTest` | batch 2a |
| 15.1-b | Update schedules + status policies in backup | DONE | #902 trackStatus, #903 updateInterval (old-fork numbers), #905/#906 dates; a payload without the fields never clears them | `CategoryHierarchyTest` | batch 2a |
| 15.1-c | Bookmark colors in backup | DONE | `BackupChapter` #14 | `BackupForkStateTest` | batch 3 |
| 15.1-d | Blacklist in backup | DONE | `BackupManga` #615 (+ #620 presence flag) | `BackupForkStateTest`, `RestoreRepositoryForkStateTest` | batch 3 |
| 15.1-e | Scanlator prefs + Smart Merge overrides | DONE | #614 priority, #616 range rules, #618 override; #619 per-manga reveal toggle is not restored (device preference) | `BackupForkStateTest`, `RestoreRepositoryForkStateTest` | batch 3 |
| 15.1-f | Custom metadata | PRESENT | SY | — | — |
| 15.1-g | Tracking state | PRESENT | | — | — |
| 15.1-h | Source preferences | PARTIAL | Verify | — | — |
| 15.1-i | Feeds and saved searches | PRESENT | Komi-kun #59 | — | — |
| 15.1-j | Synced identity data | PRESENT | Komi-kun | — | — |
| 15.2-a | Old backups with missing fields | DONE | Proto defaults for missing fields; old-fork backups: next update read from #114 (its #113 was last update) | `BackupForkStateTest` | batch 3 + 10 |
| 15.2-b | Identity-safe restore (source+url) | PRESENT | Mihon restore by source+url; verify | — | — |
| 15.2-c | Dependency-aware restore order | PARTIAL | Categories: parents before children (depth order) in `restoreCategories`; saved searches before feeds is Komi-kun #59 | `CategoryHierarchyTest` | batch 2a |
| 15.2-d | Cancellation safety | PARTIAL | | — | — |
| 15.2-e | Worker lifecycle | PARTIAL | | — | — |
| 15.3-a | Migration safety for fork data | PARTIAL | Database: `OldForkDatabaseConverter` (batch 1). Preferences: the 331 literal old-fork keys were compared with HEAD — no key is reused with another type; fork features kept their old keys; Smart Merge override migrated. Old-only keys without a HEAD feature (dev options, rate limits, clean title, chapter rename, auto refresh/reset on open, cache skip, hardware bitmap threshold, vertical seekbar) are left unused. Device test with a real old-fork install still pending | `OldForkDatabaseConverterTest`, `SmartMergeOverrideMigrationTest` | batch 1 + 3 + 10 |
| 15.3-b | Fillermark migration | DONE | Database converter + backup #15 | `OldForkDatabaseConverterTest`, `BackupForkStateTest` | batch 1 + 3 |
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
| 17.1-a | Detect reread start | DONE | Completing a chapter of a manga completed on a tracker with a rereading status (`StartReread.candidates`), once per reader session | `StartRereadTest` | batch 9 |
| 17.1-b | Configurable reset mode | DONE | `pref_auto_reread_reset_mode` (reset to zero / to the current chapter; old keys) | `StartRereadTest` | batch 9 |
| 17.1-c | Confirmation/prompt | DONE | `pref_auto_reread_behavior` ask (dialog) / always / never | manual pending | batch 9 |
| 17.1-d | Correct chapter reset scope | DONE | Only read chapters of the reader's visible list; never other manga | `StartRereadTest` | batch 9 |
| 17.1-e | Tracker coordination (status/progress/reread count) | DONE | Tracker status → rereading, progress per reset mode, start date kept or set; failures reported; reread counters are not touched (no tracker API used for them) | `StartRereadTest` | batch 9 |
| 17.1-f | Saved settings | DONE | Settings → Tracking | — | batch 9 |
| 17.2-a | Automatic tracker progress on reading | PRESENT | Mihon | — | — |
| 17.2-b | Category movement by tracker status | DONE-UNVERIFIED | See 1.4; device check pending | `SyncCategoriesWithTrackStatusTest` (11) | batch 2a |
| 17.2-c | No duplicate automation operations | MISSING | | — | — |

### 18. Webhooks and Discord

| ID | Requirement | Status | Notes | Tests | Commit |
|----|-------------|--------|-------|-------|--------|
| 18.1-a | Configurable webhooks | DONE | `WebhookPreferences.enabled` + per-event switches (old keys) | `WebhookNotifierTest` | batch 9 |
| 18.1-b | Discord webhook | DONE | Discord embed payload, mentions disabled | `WebhookNotifierTest` | batch 9 |
| 18.1-c | Structured payloads | DONE | Generic JSON `{event, timestamp, app, data, cover_url}` | `WebhookNotifierTest` | batch 9 |
| 18.1-d | Asynchronous bounded queue | DONE | `Channel(64)` drained by one app-scoped coroutine; extra events dropped | `WebhookNotifierTest` | batch 9 |
| 18.1-e | Private endpoint storage | DONE | URLs are private preferences; failures logged without the URL | `WebhookNotifierTest` | batch 9 |
| 18.1-f | Failure handling | DONE | Delivery errors caught per endpoint; the originating action never waits | `WebhookNotifierTest` | batch 9 |
| 18.2-a | Presence integration | PRESENT | Komikku Rich Presence (screen-based status, reading activity); the old fork changed only images and a crash guard (both ported) | — | — |
| 18.2-b | Account/login management | PRESENT | Komikku Discord login + multi-account screen; avatar fallback present | — | — |
| 18.2-c | Restart/stop controls | PRESENT | Restart (`DiscordRPCService.restart`) and stop (notification action) in Komikku | — | — |
| 18.2-d | Foreground-service compatibility | DONE | Komikku enters the foreground early; KNS guard stops the service when `startForeground` is refused (old-fork fix) | manual pending | batch 10 |
| 18.2-e | Cover/chapter formatting | DONE | Komikku formatting; the old fork's own status images ported | manual pending | batch 10 |
| 18.2-f | Auth/API resilience | PRESENT | Token checks on start/restart, 429 handling in `RPCExternalAsset`, avatar fallback (Komikku) | — | — |
| 18.2-g | Tokens not exported as normal prefs | DONE | `discord_accounts` (contains tokens) and connection passwords use private keys; `PrivateConnectionsMigration` moves existing values and removes public copies on every start | `PrivateConnectionsMigrationTest` | batch 10 |

### 19. Storage and downloads

| ID | Requirement | Status | Notes | Tests | Commit |
|----|-------------|--------|-------|-------|--------|
| 19.1-a | Dedicated storage screen | DONE | `StorageScreen` + `StorageViewModel` from Settings → Data and storage | manual pending | batch 10 |
| 19.1-b | Storage categorization | DONE | Per-entry downloads or local folder, with chapter counts | `StorageOverviewTest` | batch 10 |
| 19.1-c | Cumulative totals | DONE | Total with a half-ring chart split by entry | `StorageOverviewTest` | batch 10 |
| 19.1-d | Category-based selection | DONE | Category picker; parents include subcategory entries (`CategoryTree.withAncestors`) | `StorageOverviewTest` | batch 10 |
| 19.1-e | Cleanup controls with accurate reclaimed space | DONE | Delete downloads / local folder, optional remove from library; list updates immediately and sizes are recomputed on download-cache changes | manual pending | batch 10 |
| 19.1-f | Merged-manga folder safety | DONE | Merged entries size and delete only non-local members' downloads | `StorageOverviewTest` | batch 10 |
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
| 20.2-d | Loading indicators with progress | DONE | `LoadingScreen(percentage, message)` (presentation-core, `KNS`) used by the storage overview | — | batch 10 |
| 20.2-e | Correct pluralization | PARTIAL | New count strings use plurals (`category_update_interval_days`, `category_manager_delete_many`) | — | batch 2a + 2b |
| 20.2-f | Clear notifications/errors | PARTIAL | | — | — |
| 20.2-g | Navigation/back-stack corrections | PARTIAL | | — | — |

### 21. Performance

| ID | Requirement | Status | Notes | Tests | Commit |
|----|-------------|--------|-------|-------|--------|
| 21.1-a | Cached category grouping | PARTIAL | | — | — |
| 21.1-b | Item reuse | PARTIAL | | — | — |
| 21.1-c | Cached chapter visibility | DONE | `LibraryVisibleCounts` caches visible stats per entry (in memory), keyed by stored counts + rules | `LibraryVisibleCountsTest` | batch 3 |
| 21.1-d | Selective invalidation | PARTIAL | Visible counts: only changed entries recompute; other caches batch 4 | `LibraryVisibleCountsTest` | batch 3 |
| 21.1-e | One preference subscription | DONE | Per-manga override is a manga column; one global preference subscription | `LibraryVisibleCountsTest` | batch 3 |
| 21.1-f | Batched visible-chapter queries | DONE | One `getChaptersByMangaIds` query for all entries with rules | `LibraryVisibleCountsTest` | batch 3 |
| 21.1-g | Avoid unnecessary recomposition | PARTIAL | | — | — |
| 21.1-h | Efficient tracker filters | PARTIAL | | — | — |
| 21.1-i | Efficient category filters | MISSING | | — | — |
| 21.1-j | Throttled invalidations | PARTIAL | | — | — |
| 21.1-k | Aggregate only relevant manga | PARTIAL | Bookmark colors and visible counts are only computed when needed (filter set / entries with rules) | — | batch 3 + 4 |
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
| 1 | Shounen/Reading vs Josei/Reading; manga only in Shounen; status change must not add Josei | No Josei change | `SyncCategoriesWithTrackStatusTest` scenario 1 | PASS (unit) | batch 2a | Device check pending |
| 2 | Manga only in Shounen/Reading; Completed reaches Shounen/Completed via ancestor; Josei untouched | Ancestor reachability | `SyncCategoriesWithTrackStatusTest` scenario 2 (+ branch anchor) | PASS (unit) | batch 2a | Device check pending |
| 3 | Manga in both branches; each scoped rule updates its branch | Independent updates | `SyncCategoriesWithTrackStatusTest` scenario 3 | PASS (unit) | batch 2a | Device check pending |
| 4 | Top-level status category works globally | Global binding | `SyncCategoriesWithTrackStatusTest` scenario 4 | PASS (unit) | batch 2a | Device check pending |
| 5 | Two children named Reading; assign/rename/move/filter/delete/merge one leaves the other | Identity isolation | `CategoryHierarchyTest` + filters by id (`CategoryTree.withAncestors`) | PASS (unit, data + logic) | batch 2a + 2b | Device check of the manager/library pending |
| 6 | Rapid reorder persists hierarchy/order | Stable order | `CategoryHierarchyTest` rapid reorder + `CategoryManagerRowsTest` drop index | PASS (unit) | batch 2a + 2b | Device check of drag-and-drop pending |
| 7 | Backup/restore nested categories, smart policy, update intervals | Round trip | `CategoryHierarchyTest` restore + `BackupCategoryHierarchyTest` proto round trip | PASS (unit) | batch 2a | Full backup file round trip on device pending |
| 8 | Blacklist extras with 1/6; chapters 1 and 6 visible | No false positives | `ChapterBlacklistTest` Mangago extras cases | PASS (unit) | batch 3 | Device check pending |
| 9 | Priority/override change; list, reader, counts, History agree | One representative | `ChapterSmartMergeTest` shared-rule case, `LibraryVisibleCountsTest`, `GetNextChaptersVisibilityTest` | PASS (unit) | batch 3 | Device check pending |
| 10 | Continue from hidden chapter in History; history record unchanged | Next visible opens | `GetNextChaptersVisibilityTest` | PASS (unit) | batch 3 | Device check pending |
| 11 | Source refresh keeps colors, blacklist, read flags, merge decisions | Preserved | `SyncChaptersBookmarkColorTest`, `ChapterBlacklistTest` refresh cases | PASS (unit) | batch 3 | Read flags: upstream behavior, not re-tested |
| 12 | Bookmark color changes propagate (manga + reader navigator) | Immediate | Manual (device) | not run | batch 3 | Implemented; needs a device check |
| 13 | Multi-service status/score/date edit with partial failure | Successful writes kept | `UpdateTracksTest` (updated/failed/skipped, failed save, concurrent writes) | PASS (unit) | batch 5 | Live services not tested |
| 14 | Reading older chapter does not lower progress | No rollback | `TrackChapterProgressTest` | PASS (unit) | batch 5 | Live services not tested |
| 15 | MangaBaka metadata/dates/search/relations/bindings survive restart/backup | Preserved | Unit + manual | not run | — | — |
| 16 | Category movement only after persisted status change | Ordering | `UpsertTrackTest` | PASS (unit) | batch 2a | `SyncCategoryTrackStatus` with real trackers not unit-tested |
| 17 | Auto-scroll/auto-flip speed, duration, sleep/background, end | Stops correctly | `ReaderAutomationTest` (limits, stall/end, speed); device run pending | PASS (unit, partial) | batch 7 | Stop on tap/screen-off/background only checked by code review |
| 18 | Reader title/back destination; bookmark icon color + rounded feedback | Correct | Bookmark color + round ripple implemented (batch 3); title/back parity not verified | not run | batch 3 | Device check pending |
| 19 | Confirmed Webtoon fit/gap controls | Where they exist | Komikku already provides Webtoon scale type, smart gap, pinch-zoom switches | PRESENT | — | Device check pending |
| 20 | Saved filtered search as feed; retry; refresh-all; filters intact | Preserved | Unit + manual | not run | — | — |
| 21 | Source-category Global Search only eligible sources; disabled sources idle | Scoped | Unit test | not run | — | — |
| 22 | Browse search input usable during long request | Responsive | Manual (device) | not run | — | — |
| 23 | Backup/restore feeds + saved searches with identity | Round trip | Unit test | not run | — | — |
| 24 | Backup/restore custom metadata, hierarchy, colors, blacklist, scanlator rules; no same-title mixing | Round trip | `BackupForkStateTest`, `RestoreRepositoryForkStateTest` (colors, blacklist, scanlator rules); hierarchy see #7 | PASS (unit, partial) | batch 2a + 3 | Custom metadata round trip not re-tested; full backup file on device pending |
| 25 | SyncYomi preserves fork fields | Preserved | `RestoreRepositoryForkStateTest` sync-winner cases | PASS (unit, partial) | batch 3 | SyncYomi server round trip not tested |
| 26 | One override/blacklist change does not recompute unrelated results | Targeted | `LibraryVisibleCountsTest` cache case | PASS (unit) | batch 3 | — |
| 27 | Release prediction + category update policies | Correct selection | `CategoryUpdateScheduleTest` (category policies); release prediction is upstream `FetchInterval` (`FetchIntervalTest`) | PASS (unit) | batch 8 + 10 | Device check pending |
| 28 | Unit tests, migrations, debug/release builds, device checks | All pass | Every batch: spotlessCheck, verifyDebugDatabaseMigration, testDebugUnitTest, assembleDebug, assembleRelease | PASS (Gradle) | batches 3-9 | No device checks yet |

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

### Batch 2a — category hierarchy and Smart Categories (logic, persistence, backup, sync)

- **Domain:** `CategoryTree` (N-level: sibling names, ancestry/descendants, cycle-safe moves, depth-first
  order), `SyncCategoriesWithTrackStatus` (branch-scoped rule, see features.md § 1.4), new interactors
  `MoveCategory`, `MergeCategories`, `SetCategoryTrackStatus`, `SetCategoryUpdateInterval`; extended
  `CreateCategoryWithName` (parent, `DuplicateName`), `RenameCategory` (`DuplicateName`), `DeleteCategory`
  (children move up, `DuplicateName`), `HideCategory` (bulk), `ReorderCategory` (siblings).
  The old fork's tree was two levels deep; this one supports any depth as the spec requires.
- **Data:** `CategoryRepository.updateHierarchy` (parents + depth-first order in one transaction),
  `updateTrackStatus`, `updateUpdateInterval`, `copyMemberships`; `restoreCategories` now takes a
  `RestoredCategoryHierarchy` and matches/deduplicates by (parent, name) after uid, processes parents
  first, keeps the local parent/policies when the payload lacks them, and keeps the order depth-first.
- **Tracker hook:** `UpsertTrack` → `TrackPersistenceListener` (domain) → `SyncCategoryTrackStatus` (app,
  unified `TrackStatus`, deterministic tracker order; preferred tracker comes with batch 5).
- **Backup:** `BackupCategory` #901 parentId, #902 trackStatus, #903 updateInterval (old-fork numbers),
  #904 parentUid, #905/#906 dates, #907 hierarchyKnown; `BackupCategoryHierarchy` shared by
  `CategoriesRestorer` and `MangaRestorer` (effective uid, parent, path).
- **Sync:** Komi-kun's `mergeSyncCategories` (v2) and `SyncService.mergeCategoriesLists` (v1) deduplicated
  and matched by bare name, which would merge `Shounen / Reading` with `Josei / Reading` (and the v1 prune
  could then delete one). Both now use `siblingNameKey()` (parent uid + name); root behaviour, including
  Komi-kun's cross-device convergence of same-named roots, is unchanged (`SyncDeltaTest` 29/29).
- **Tests:** `CategoryTreeTest` (5), `SyncCategoriesWithTrackStatusTest` (11), `UpsertTrackTest` (3),
  `CategoryHierarchyTest` (10), `BackupCategoryHierarchyTest` (3), `SyncCategoryHierarchyTest` (2);
  Komi-kun's `CategoryRepositoryRestoreTest`, `MangaRestorerTest`, `SyncDeltaTest`, `SyncFullSyncTest` pass.
- **Changelog:** Added (nested categories logic, Smart Categories), Fixed (category backups/restore,
  SyncYomi same-named categories).

### Batch 2b — category manager, assignment dialog, library navigation

- **Manager:** `CategoryViewModel` / `CategoryScreen` rebuilt as a tree (`CategoryManagerRows.kt`: depth-first
  rows, per-level sort, collapse, search with ancestors and path, distinct subtree counts; drag among
  siblings committed once per drop via `siblingIndexAfterDrop`). Row menu: rename, create subcategory, move,
  tracker status, update interval, hide/show, delete. Selection mode: select all, hide, show, move, merge,
  delete. Dialogs in `CategoryTreeDialogs.kt`. Preferences reuse the old fork's keys
  (`category_manager_sort_mode`, `category_manager_collapsed_ids`).
- **Assignment:** `ChangeCategoryDialog` shows the tree and stores a selected child with its ancestors
  (`CategoryHierarchyUi.kt`), as the old fork did.
- **Library:** with any subcategory present, `LibraryViewModel` groups by top-level category over the whole
  subtree (`groupByCategoryTree`, hidden branches skipped, orphans as top level); `LibrarySubcategoryChips`
  narrows a tab to a branch at any depth; include/exclude filters use ancestry. Flat libraries keep the
  previous grouping path unchanged.
- **Not yet:** the library updater does not use per-category intervals or the subcategory refresh scope
  (batch 8); refreshing a top-level tab updates only manga filed directly in it (to be widened with
  batch 8); no device test of the new screens yet.
- **Tests:** `CategoryManagerRowsTest` (7), `LibraryCategoryTreeTest` (4).
- **Validation (all exit 0):** spotlessCheck, generateSqlDelightInterface, verifyDebugDatabaseMigration,
  testDebugUnitTest (app 179, data 81, domain 143; 0 failures), assembleDebug, assembleRelease, diff check.
- **Changelog:** Added (category manager, library tabs/chips, Set categories tree), Changed (filters cover
  subcategories); the 2a entries no longer say the UI is pending.

### Batch 3 — chapter blacklist, bookmark colors, Smart Scanlator Merge, unified visibility

- **Domain (`KNS`):** `chapter/service/ChapterBlacklist.kt` (url / hybrid v1+v2 / legacy keys, resolved
  against the full chapter snapshot; `ChapterBlacklistKeys.kt` folded in), `chapter/model/ChapterSmartMerge.kt`
  (range rules, priority, `filterChapterVisibility`, `chapterListVisibility`, `resolveVisibleChapter`, stats,
  coverage, `Manga.smartScanlatorMergeEnabled`), and `chapter/service/ChapterVisibility` — the single rule used
  by every surface. `Chapter.BOOKMARK_COLOR_UNKNOWN` (-1) marks a restored chapter without a color; it is never
  stored.
- **Preferences:** `pref_smart_scanlator_merge` (global, default off, Settings → Library),
  `pref_hide_blacklisted_chapters_<mangaId>` (reveal toggle), `pref_filter_library_bookmark_colors` (no UI yet).
  `SmartMergeOverrideMigration` moves the old fork's `pref_smart_scanlator_merge_override_<mangaId>` into
  `manga.user_smart_merge_override` and deletes the old keys.
- **Visibility everywhere:** manga chapter list (+ reveal), reader chapter loading/navigation and chapter list
  (a hidden duplicate opened by id is reassigned to its representative), `GetNextChapters` (History / next
  chapter; a hidden start resolves to after / representative / end instead of `indexOfFirst == -1`),
  `FilterChaptersForDownload`, library continue-reading (also fixed SY's merged check `manga.id ==
  MERGED_SOURCE_ID`) and counts (`LibraryVisibleCounts`, cached per entry), Updates feed
  (`UpdatesVisibilityFilter`).
- **UI:** manga overflow → Blacklisted chapters (`BlacklistedChaptersDialog`) and Scanlator preferences
  (`ScanlatorPreferencesDialog`: override global/on/off with the effective value, priority order); selection bar
  → Bookmark color / Blacklist / Un-blacklist; chapter rows show the color and a blacklisted marker; reader
  bookmark button long press → color picker (round ripple), colored icon, colored reader chapter list.
- **Backup / sync:** `BackupManga` #614/#615/#616/#618/#620, `BackupChapter` #14 (write) / #15 (read-only
  Fillermarks → `url:` keys). `RestoreRepositoryImpl.restoreForkState` follows the excluded-scanlator rules: a
  winning sync side replaces (empty only when strictly newer), a stale sync side only unions the blacklist, an
  ordinary restore unions the blacklist and applies the rules it carries, a payload without the fields changes
  nothing. Chapter colors: the newer sync side owns them; an ordinary restore brings colors in but never resets.
  Source refresh carries a removed chapter's color to its re-upload.
- **Tests:** `ChapterBlacklistTest` (13), `ChapterSmartMergeTest` (12), `ScanlatorCoverageTest` (3),
  `GetNextChaptersVisibilityTest` (4), `RestoreRepositoryForkStateTest` (7), `BackupForkStateTest` (5),
  `SmartMergeOverrideMigrationTest` (2), `SyncChaptersBookmarkColorTest` (1), `LibraryVisibleCountsTest` (3),
  `UpdatesVisibilityFilterTest` (1).
- **Validation:** all exit 0 — spotlessCheck, generateSqlDelightInterface, verifyDebugDatabaseMigration, testDebugUnitTest (app 191 with 1 skipped, data 88, domain 175; 0 failures), assembleDebug, assembleRelease; diff check clean after the tracker rewrite.
- **Deferred:** range-rule editor UI; bookmark-color filters (library + per manga) and library unread/
  bookmarked filter predicates under the visibility rule (batch 4); History filters (batch 8); page previews
  ignore the blacklist; backup #619 (reveal toggle) is not restored. No device test of the new UI yet.

### Batch 4 (part 1) — library search fields and bookmark-color filters

- **Search:** HEAD's Mihon/Komi-kun AST search already covers parsed expressions, field terms, negation,
  `||`/parentheses, comparisons and the SY metadata namespaces, so the old fork's `LibrarySearchParser` is not
  ported. Its fields that HEAD lacked are added as `LibraryField` / `LibraryFieldQueryNode` (`KNS`, in
  `LibrarySearchAst.kt` + one parser branch): `category:`/`cat:` (categories + ancestors by name or id, default
  category for uncategorized), `tracker:` (tracker name or tracking status), `status:` (publishing status name or
  code). Resolved in `LibraryViewModel.filterLibrary` through `LibrarySearchFields`. Not restored: the old `$`
  exact prefix.
- **Bookmark-color filters:** `chapter.sq` `getBookmarkColorsByManga` (bookmarked chapters' colors per manga,
  merged entries include their members') → `ChapterRepository.getBookmarkColorsByMangaAsFlow` →
  `GetLibraryBookmarkColors`, subscribed only while `pref_filter_library_bookmark_colors` is non-empty; library
  filter sheet row + `BookmarkColorFilterDialog`; counts as an active filter. Per manga:
  `manga_bookmark_color_filter_<id>` in the chapter filter sheet, applied in `State.Success.applyFilters`, part
  of `filterActive`. `BookmarkColor.fromNames` / `matchesFilter` hold the shared rule.
- **Visibility and built-in filters:** unread/started/bookmarked library filters already read the counts
  `LibraryVisibleCounts` rewrites (batch 3), so they follow the blacklist and Smart Merge. Known gaps: the
  downloaded filter/badge counts downloaded hidden chapters; the color filter uses stored colors, including
  those of hidden chapters.
- **Tests:** `LibraryFieldQueryTest` (3), `LibrarySearchFieldsTest` (3), `BookmarkColorFilterTest` (3),
  `BookmarkColorsByMangaTest` (1).
- **Validation:** all exit 0 — spotlessCheck, generateSqlDelightInterface, verifyDebugDatabaseMigration, testDebugUnitTest (app 194 with 1 skipped, data 89, domain 181; 0 failures), assembleDebug, assembleRelease, diff check.
- **Still open in batch 4:** source grouping/language (2.3), sort/cover/presentation items (2.4), list-item
  stability (2.4-e), All-category subcategory exclusion (2.2-c, historical), merged downloaded filter (2.2-g).

### Batch 5 (part 1) — tracker progress safety, unified tracker editor, preferred/priority tracker

- **Progress correctness:** `TrackChapter` ignores non-finite/negative chapter numbers, sends
  max(refreshed remote progress, read chapter) and saves the record the service returns;
  `SyncChapterProgressWithTrack` saves the returned record and rethrows cancellation; `AddTracks.bind` saves
  what `bind` returned. `UpsertTrack.awaitOrThrow` (`KNS`) lets callers report a failed save.
- **Unified editor:** `UpdateTracks` (status translated per service incl. MangaUpdates list names, score via the
  ten-point scale, dates with order checks, progress; unsupported services skipped) on `trackerBatch`
  (concurrent services, sequential saves, per-service failures). `UnifiedTrackerEdits` (app singleton) queues
  edits and keeps the last failure per tracker until it succeeds. UI: `UnifiedTrackerPanel` at the top of the
  tracking sheet when 2+ trackers are linked, `UnifiedTrackEditorScreen` reusing the existing selectors, a
  failure marker and a "Preferred tracker" label on tracker rows.
- **Preferred/priority:** `TrackerProgressSync` + `PreferredTrackerMap` ported (`KNS`);
  `TrackPreferences.preferredTrackerForManga` (app-state key, device-only like the old fork),
  `priorityTrackerId` (Settings → Tracking), `resolvePreferredTracker`. The panel offers "Set all to N" when
  progress differs.
- **Tests:** `TrackerProgressSyncTest` (4), `UpdateTracksTest` (8), `TrackChapterProgressTest` (3),
  `UnifiedTrackerEditsTest` (2).
- **Validation:** all exit 0 — spotlessCheck, generateSqlDelightInterface, verifyDebugDatabaseMigration, testDebugUnitTest (app 207 with 1 skipped, data 89, domain 185; 0 failures), assembleDebug, assembleRelease, diff check.
- **Open in batch 5:** MangaBaka fork-only behavior and metadata/relations (7.1), AniList relations (7.2-a, with
  3.2), MangaUpdates differences (7.2-b), multi-title batch actions (8.1-f), unified untrack UI, search resolver /
  autofill (8.3-a/b), reread counters (historical). No device test of the panel yet.

### Batch 6 (part 1) — related titles from trackers

- **Model:** `tachiyomi.domain.manga.model.SequelPrequelEntry` / `SequelPrequelRelation` (`KNS`, transient; the
  old fork's names). `Tracker.getRelatedEntries(remoteId)` (default none); AniList via the new
  `AniListGetMangaRelations.graphql` (Apollo) + `AnilistRelations.kt`; MangaBaka via `MangaBakaItem.relationships`
  (`relationships_v2`), `MangaBakaApi.getSeries` and `MangaBakaRelations.kt`.
- **Lookup:** `GetSequelPrequel` (Metro singleton): logged-in bindings, preferred tracker first, MangaDex relation
  fallback, 24 h in-memory cache per manga + bindings + preference (empty/failed results not cached), local
  matching by tracker binding → source URL → unique same-title favorite; `inLibrary` only for favorites.
- **UI:** `UiPreferences.showSequelPrequel` (`pref_sequel_prequel_enabled`, off by default, Settings → Browse);
  `MangaViewModel` loads relations into `State.Success.sequelPrequel` when bindings, logged-in trackers or the
  preferred tracker change; `SequelPrequelRow` in both entry layouts; tapping opens the local entry or a dialog
  with Global Search / open in browser. Relation strings ported to `KMR`.
- **Tests:** `GetSequelPrequelTest` (5), `AnilistRelationsTest` (2), `MangaBakaRelationsTest` (2).
- **Validation:** all exit 0 — spotlessCheck, generateSqlDelightInterface, verifyDebugDatabaseMigration, testDebugUnitTest (app 216 with 1 skipped, data 89, domain 185; 0 failures), assembleDebug, assembleRelease, diff check.
- **Not verified:** the AniList relations query and MangaBaka `relationships_v2` field against the live services
  (parsing is unit-tested; network calls are not). No device test of the row.

### Batch 7 — reader automation (auto-scroll / auto-flip)

- **Origin:** the old fork's automation (contributed by Guzmazow, KMK-marked there) is rebuilt for the foundation's
  viewers instead of copied: `ui/reader/automation/ReaderAutomation.kt` (pure: `AutomationSession` limits,
  `StallDetector`, `AutomationSpeed`, `PixelAccumulator`) and `ReaderAutomationController` (Choreographer frames
  for scrolling, delays for flipping, keep-screen-on, lifecycle/screen-off stop, limit toasts, end detection).
  Targets: `PagerViewer.moveToNext`, `WebGpuViewer.moveToNext`, new `WebtoonViewer.automationScrollBy` and
  `WebGpuViewerContinuous.automationScrollBy` (library `ImageViewerContinuousState.scrollBy`).
- **UI:** `ReaderAutomationControls` in `ReaderAppBars` (new `automationControls` slot above the bottom bar);
  opening the menu (a tap) stops a run, so does changing the viewer. Settings → Reader → Automation with the old
  fork's keys; new `rounded/remove.svg` icon (Material Symbols). SY's E-Hentai auto-scroll (ExhUtils) untouched.
- **Tests:** `ReaderAutomationTest` (7).
- **Validation:** all exit 0 — spotlessCheck, generateSqlDelightInterface, verifyDebugDatabaseMigration, testDebugUnitTest (app 223 with 1 skipped, data 89, domain 185; 0 failures), assembleDebug, assembleRelease, diff check.
- **Not verified:** on a device (speed feel, stop on tap/screen-off, WebGPU scrolling).

### Batch 8 (part 1) — category update schedules, refresh scope, History day counts

- **Schedules:** `tachiyomi.domain.category.service.CategoryUpdateSchedule` (`KNS`): effective interval (own, else
  nearest ancestor), due interval categories from per-category last-run times, `isMangaDue` (no category or a
  global category → always; else any due interval category), `refreshScope` (category + subtree, never the
  default category's "children"). `manga.last_update` only changes with the chapter list, so last runs are kept
  per category in `LibraryPreferences.categoryUpdateLastRun` (app-state, device-only) and written only after a
  scheduled chapter update completes. Manual and category-targeted refreshes ignore intervals.
- **Refresh scope:** `SubcategoryUpdateScope` + `pref_library_update_subcategory_scope` (old fork key);
  `LibraryViewModel.refreshCategory` picks the selected subcategory chip when set to "selected subcategory only";
  the worker covers the whole subtree of the category it is given.
- **History:** day headers show the number of distinct manga read that day.
- **Tests:** `CategoryUpdateScheduleTest` (5), `HistoryDayCountTest` (1).
- **Validation:** all exit 0 — spotlessCheck, generateSqlDelightInterface, verifyDebugDatabaseMigration, testDebugUnitTest (app 224 with 1 skipped, data 89, domain 190; 0 failures), assembleDebug, assembleRelease, diff check.
- **Open:** release-interval prediction (14.2, historical algorithm not established), visibility-aware History
  filters (13.1-d), parallel update slots (14.3-b).

### Batch 9 (part 1) — Auto Reread and webhooks

- **Auto Reread:** `StartReread` (candidates = logged-in trackers where the manga is completed and that have a
  rereading status; chapter reset per `AutoRereadResetMode`; trackers moved to rereading through `trackerBatch`,
  returned records saved). `ReaderViewModel.maybeStartReread` after a chapter completes (once per session, never
  in incognito), `Dialog.RereadPrompt`, settings in Settings → Tracking with the old fork's keys
  (`pref_auto_reread_behavior`, `pref_auto_reread_reset_mode`). The old fork hard-coded MAL/AniList/MangaBaka; this
  uses each tracker's `getRereadingStatus()`.
- **Webhooks:** `WebhookPreferences` (old keys; URLs private), `WebhookNotifier` (bounded queue, suppression for
  incognito/merged-incognito/excluded categories/private tracks), `WebhookTransport` (OkHttp, 15 s), `WebhookPayload`,
  `SettingsWebhookScreen` linked from Settings → Connections. Events wired: reader (chapter started/read, new manga
  started, caught up, finished), `UpdateManga.awaitUpdateFavorite` (added/removed), library update, downloader
  queue finished, backup created/restored. Not ported: "manga migrated" and "app updated" events.
- **Tests:** `StartRereadTest` (3), `WebhookNotifierTest` (4).
- **Validation:** all exit 0 — spotlessCheck, generateSqlDelightInterface, verifyDebugDatabaseMigration, testDebugUnitTest (app 231 with 1 skipped, data 89, domain 190; 0 failures), assembleDebug, assembleRelease (first run hit a stale incremental-compile state after files were moved mid-build; passed after `:app:compileReleaseKotlin --rerun`), diff check.
- **Not verified:** real Discord/endpoint delivery; reread on a device.

### Resume point (end of session 2, 2026-10-21)

- Branch `codex/komikun-foundation`; batches 3–9 committed this session (`2af2c9215a`, `5f901e1f43`, `689b2339df`,
  `12a038e658`, `65e92a8e98`, `0f759e47aa` and the Batch 9 commit). Nothing pushed.
- **Next, in order:** storage overview screen (19.1, old fork `ui/storage` + `presentation/more/storage`, needs
  `DownloadManager.getDownloadSize` and a percentage loading screen; merged entries must size their member folders);
  visibility-aware History filters (13.1-d) and downloaded counts for hidden chapters (2.2-f gap); range-rule editor
  UI (6.2-b); multi-title tracker batch actions (8.1-f); old-fork SharedPreferences/backup conversion (15.3-a);
  reader title/back parity (9.1-c); Discord RPC verification (18.2, historical); release-interval prediction (14.2,
  historical — confirm the old algorithm first); final audit and device checks (acceptance #28).

### Batch 10 (part 1) — storage overview and visibility-aware History filter

- **Storage overview:** rebuilt from the old fork's `ui/storage` + `presentation/more/storage` as a Metro
  `StorageViewModel` with pure logic in `StorageOverview` (entries, category filter with subcategories, sizes,
  formatting). New `DownloadCache.getDownloadSize` / `DownloadManager.getDownloadSize` (`KNS`), and a
  `LoadingScreen(percentage, message)` overload in presentation-core. Merged entries size and delete their
  non-local members' downloads only. The delete dialog's "remove from library" now starts unchecked (the old fork
  checked it). Strings moved from the old fork's `MR` additions to `KMR`.
- **History:** `HistoryVisibleCounts` recomputes total/read counts for manga with a blacklist or Smart Merge
  (respecting the bookmark chapter filter) and the unfinished-manga filter runs in Kotlin on those counts; the SQL
  filter is no longer used for it.
- **Tests:** `StorageOverviewTest` (4), `HistoryVisibleCountsTest` (2).
- **Validation:** all exit 0 — spotlessCheck, generateSqlDelightInterface, verifyDebugDatabaseMigration, testDebugUnitTest (app 237 with 1 skipped
, data 89
, domain 190; 0 failures), assembleDebug, assembleRelease, diff check.
- **Not verified:** on a device (sizes against the system's storage settings, deletion).

### Batch 10 (part 2) — range rules UI, visible download counts, reader title, tracker removal

- **Range rules:** `ScanlatorPreferencesDialog` gains a Chapter ranges section (from/to + scanlator, add/remove);
  `Dialog.ScanlatorPreferences.rangeRules`, `setScanlatorPreferences(priority, override, rules)` writes
  `manga.user_scanlator_range_rules` (already in backups/sync since batch 3).
- **Download counts:** `LibraryVisibleCounts` keeps the visible chapters of regular entries with rules;
  `LibraryViewModel` counts `getDownloadedChapterIds` of those for the badge and the Downloaded filter.
- **Reader title:** tap = back (old fork commit `1790705b94`); Mihon's `openMangaScreen` intent removed.
- **Tracker removal:** Remove trackers dialog in the All trackers panel through `UnifiedTrackerEdits.remove`
  (queued; clears the failure markers of removed trackers).
- **Tests:** `LibraryVisibleCountsTest` (+1), `UnifiedTrackerEditsTest` (+1).
- **Validation:** all exit 0 — spotlessCheck, generateSqlDelightInterface, verifyDebugDatabaseMigration, testDebugUnitTest (app 239 with 1 skipped
, data 89
, domain 190; 0 failures), assembleDebug, assembleRelease, diff check.

### Batch 10 (part 3) — old-fork compatibility checks, parallel update slots

- **Preferences:** compared every literal preference key of the old fork (331) with HEAD (325): no key is read
  with a different type, so an in-place upgrade cannot crash on a preference read. Old-only keys belong to
  features not in `features.md` (developer options, rate limits, clean title, chapter rename, auto refresh/reset on
  open, cache skip, hardware bitmap threshold, vertical seekbar) and are simply ignored. Key-templated preferences
  were kept identical by the batches that ported them.
- **Backups:** `BackupManga.legacyNextUpdate` (#114, read only) — old-fork backups stored last update at #113,
  which HEAD reads as next update; when #114 is present it wins.
- **Parallel slots:** `LibraryPreferences.libraryUpdateParallelSlots` (`library_update_parallel_slots`, 1–10,
  default 5 = the previous fixed limit) used by `LibraryUpdateWorker` and `MetadataUpdateWorker`; slider in
  Settings → Library.
- **Release prediction (14.2):** not ported — HEAD's upstream `FetchInterval` (hiatus, weekday and batch
  detection) is newer than the old fork's median/recurring-gap variant; upstream priority keeps it.
- **Tests:** `BackupForkStateTest` (+1).
- **Validation:** all exit 0 — spotlessCheck, generateSqlDelightInterface, verifyDebugDatabaseMigration, testDebugUnitTest (app 240 with 1 skipped
, data 89
, domain 190; 0 failures), assembleDebug, assembleRelease, diff check.
- **Still open:** device test of an in-place upgrade from the old fork (database + preferences); Discord RPC
  verification (18.2, historical); acceptance #28 device checks.

### Batch 10 (part 4) — Discord secrets out of backups

- **Finding:** Komikku's `discord_accounts` preference (a JSON list including each account's token) and
  `pref_connections_password_<id>` used ordinary keys, so app-preference backups contained them.
- **Fix:** both use `Preference.privateKey(...)` now (`ConnectionsPreferences`, `KNS`); `PrivateConnectionsMigration`
  (runs every start, acts only while public keys exist) copies values into the private keys (keeping an existing
  private value) and deletes the public ones, which also handles older backups restoring them.
- **Tests:** `PrivateConnectionsMigrationTest` (2).
- **Validation:** all exit 0 — spotlessCheck, generateSqlDelightInterface, verifyDebugDatabaseMigration, testDebugUnitTest (app 242 with 1 skipped
, data 89
, domain 190; 0 failures), assembleDebug, assembleRelease, diff check.
- **Remaining open items:** device test of an in-place upgrade from the old fork (database + preferences + backup);
  Discord Rich Presence behavior review (18.2-a–f, historical); acceptance #28 device checks.

### Batch 10 (part 5) — Discord Rich Presence review (18.2)

- **Method:** `git diff 1656575e8f 0ff967c77f` over the Discord/connections code. The old fork's net changes were:
  the Webhooks entry (done in batch 9), R-class import churn (not applicable), its own status images ("Update
  images to my liking"), and a `startForeground` try/catch. The token guards, early foreground notification,
  429 handling and avatar fallback it had merged from Komikku branches are already in HEAD.
- **Ported (`KNS`):** the status image URLs in `DiscordRPCModels`; `DiscordRPCService.notification` returns false and
  stops the service when `startForeground` throws, and `onCreate` returns without creating the RPC client.
- **Validation:** all exit 0 — spotlessCheck, generateSqlDelightInterface, verifyDebugDatabaseMigration, testDebugUnitTest (app 242 with 1 skipped
, data 89
, domain 190; 0 failures), assembleDebug, assembleRelease, diff check.
- **Remaining open items:** device test of an in-place upgrade from the old fork (database + preferences + backup);
  device checks for acceptance #28 (Rich Presence included).
### Batch 11 — tri-state bookmark-color filters

- **Shared UI:** Library, Updates and manga chapter settings now place an independent Edit action beside
  the existing Bookmarked tri-state control. `BookmarkColorFilterDialog` reuses the category dialog's
  save/cancel tri-state interaction for the ordinary bookmark plus all six palette colors, with color
  swatches. Closing or cancelling leaves stored preferences unchanged.
- **Filtering:** included colors require a match; excluded colors reject a matching bookmarked chapter.
  For Library, a mixed-color manga passes only when it has an included color and no excluded bookmark
  color. The existing aggregate query remains the only Library color data source. Saved color choices
  refine Bookmarked only; disabled and Not bookmarked states preserve their existing semantics.
- **Persistence and Updates:** old Library/per-manga color keys remain the included side of the new
  pair, making migration non-destructive and idempotent. Updates has its own keys and `updatesView`
  now exposes bookmark color, so normal query invalidation immediately refreshes filter eligibility
  without per-row queries.
- **Tests:** `BookmarkColorFilterTest` adds full palette and combined include/exclude coverage.

### Batch 12 — colored bookmarks in Updates

- **UI:** the Updates row tints its bookmark icon with `bookmarkColorOf(update.bookmarkColor) ?:
  MaterialTheme.colorScheme.primary`, the same expression as the manga chapter list, so it reuses the
  `BookmarkColor` palette (0 = theme color, 1–6 = red … purple). Unbookmarked chapters show no icon.
- **Data:** `updatesView`, `UpdatesWithRelations` and `UpdatesRepositoryImpl` already carried the color
  (batch 11). Batch 11 changed the view without a migration, so a database already at schema 58 kept
  the old view and the Updates queries would fail on `bookmarkColor`. `58.sqm` drops and recreates
  `updatesView` from its current definition (schema version 59). It touches no table data, and it is
  independent of `OldForkDatabaseConverter`, which recreates views from the fresh schema anyway.
- **Reactivity:** the view reads `chapter`, so a color change through `ChapterRepositoryImpl.update`
  invalidates the subscribed Updates query; no per-row lookups.
- **Tests:** `UpdatesBookmarkColorTest` (colors 0–6 and an unbookmarked chapter on the real view,
  `bookmarked` filter and ordering unchanged, a color change re-emits the observed rows),
  `UpdatesViewMigrationTest` (a schema-58 database with the old view gains `bookmarkColor` and keeps
  its rows, colors and sync versions), `BookmarkColorTintTest` (0 → theme color, 1–6 → palette).
- **Validation:** spotlessApply, spotlessCheck, generateSqlDelightInterface, verifyDebugDatabaseMigration,
  assembleDebug, assembleRelease and `git diff --check` exit 0. The first testDebugUnitTest run failed on a
  Windows file lock in Kotlin's incremental-compile backup (`:app:compileDebugKotlin`); the re-run exited
  0 (app 244 with 1 skipped, data 93, domain 192; 0 failures).
- **Not verified:** on a device — the Updates icon colors and an in-place upgrade from schema 58.

### Batch 13 — subcategory-aware Library badge counts

- **Cause:** `LibraryViewModel.State.getItemCountForCategory` returned the size of the tab's whole
  group, while `getItemsForCategory` already filtered by that tab's entry in `subcategorySelection`.
- **Fix:** the count now uses the same effective membership as `getItemsForCategory` whenever the tab
  has a selected subtree, keyed by the tab's own id, so inactive tabs keep their own count. Nothing is
  queried: it reuses the state's grouped, already searched and filtered entries; each manga is counted
  once. `getToolbarTitle` already reads this count when tabs are hidden; the whole-library count
  with tabs shown is unchanged. The badge preference and the search exception are unchanged.
- **Safer selection lookup:** `selectedSubtree` ignores selections outside category grouping and on the
  system category, and falls back to the whole tab when the selected category was deleted or is not
  below the tab. Items and counts both use it, so they always agree.
- **UI:** `LibraryTab` passes `state.getItemCountForCategory`; a selection change produces a new
  `State`, so `LibraryTabs` badges recompose without further changes.
- **Tests:** `LibrarySubcategoryCountTest` (8, on the real `State`): All 10 / Test 2 / Other 5 / All
  again; per-tab persistence across tab switches; nested descendants, shared manga counted once, an
  empty child counting 0; membership moves and removals; search/filter-narrowed counts and the badge
  preference; deleted and foreign selections; source grouping; toolbar counts.
- **Validation:** all exit 0 — spotlessApply, spotlessCheck, generateSqlDelightInterface,
  verifyDebugDatabaseMigration, testDebugUnitTest (app 250 with 1 skipped, data 89, domain 190; 0
  failures), assembleDebug, assembleRelease, `git diff --check`.
- **Not verified:** on a device.

### Batch 14 — main's bookmark long-press menu in manga, Updates and the reader

- **Menu:** `BookmarkColorDropdownMenu` (adapted from the old `main`) is an anchored Material dropdown
  with all seven palette entries; each row is `BookmarkColorLabel` (20dp `BookmarkColorSwatch` with
  `MaterialTheme.shapes.small`, the ordinary bookmark in `surfaceVariant`, 12dp to the name) and a
  trailing check for the current color. Unlike `main` the check uses the theme content color, and it
  is shown only when the selection has one shared bookmark color (`ChapterBookmarks.currentColor`).
- **Shared operations:** `tachiyomi.domain.chapter.service.ChapterBookmarks` builds the chapter
  updates for the ordinary toggle (state change + color reset) and for a chosen color (bookmark +
  color, unchanged chapters skipped). The manga screen, Updates and the reader all use it;
  `Chapter.bookmarkTarget()` / `UpdatesWithRelations.bookmarkTarget()` adapt their rows. Updates'
  bookmark toggle previously kept the old color on removal; it now resets it like the other screens.
- **Selection bars:** `MangaBottomActionMenu` takes `onBookmarkColorSelected` + `bookmarkColor`. A long
  press on Bookmark or Remove bookmark opens the menu (with haptic feedback) instead of the
  hold-to-label hint; without the callback the old hint stays. The Palette action is removed and the
  confirm slots shrink from 10 to 9 (blacklist 7, unblacklist 8). The phone and tablet manga
  layouts share `SharedMangaBottomActionMenu`; Updates wires `UpdatesViewModel.setBookmarkColor`.
  `MangaViewModel.Dialog.BookmarkColor` / `showBookmarkColorDialog` are gone.
- **Reader:** `ReaderTopBar` hosts the menu next to the bookmark button (state kept outside the
  rebuilt action list) and calls `ReaderViewModel.setChapterBookmarkColor`. The app bars are one
  Compose overlay above every viewer, so pager, webtoon and both WebGPU viewers share it; no viewer
  code changed. `Dialog.BookmarkColorPicker` / `showBookmarkColorPicker` are gone. The check follows
  `state.bookmarkColor`, which is reset on every chapter change.
- **Dialog:** `BookmarkColorDialog` was first restyled with `BookmarkColorLabel`, then removed in a
  follow-up commit because no screen opens it any more. `KMR.strings.action_bookmark_color` stays as
  the reader button's long-press accessibility label; the bookmark color filter dialogs are unchanged.
  Follow-up validation: all exit 0 (spotlessCheck, generateSqlDelightInterface,
  verifyDebugDatabaseMigration, testDebugUnitTest with the same counts, assembleDebug,
  assembleRelease, `git diff --check`).
- **Tests:** `ChapterBookmarksTest` (6): ordinary add/remove with color reset, all seven ids,
  multi-selection, replacing colors, bookmarking plain chapters, skipped no-op writes, the current
  color for single/mixed/unbookmarked selections, identical updates from a manga chapter and its
  Updates row. The app has no Compose UI test setup (no Robolectric or ui-test), so the
  interaction is not covered by automated tests.
- **Validation:** all exit 0 — spotlessApply, spotlessCheck, generateSqlDelightInterface,
  verifyDebugDatabaseMigration, testDebugUnitTest (app 252 with 1 skipped, data 93, domain 198; 0
  failures), assembleDebug, assembleRelease, assembleNightly, `git diff --check`.
- **Device checks pending:** long press opens the menu without a tap; dismissal changes nothing; menu
  anchoring in the manga (phone and tablet) and Updates bars; the reader in pager, webtoon and WebGPU
  paged/continuous modes, both orientations and RTL, without page turns; the reader check following
  chapter changes; the remaining bottom-bar long-press hints.

### Batch 15 — combined scanlator preferences and blacklist dialog

- **Entry point:** `ChapterHeader` has an optional scanlator button (`Group` icon with a tooltip, disabled
  in selection mode) that opens `MangaViewModel.showScanlatorPreferencesDialog`, in both manga layouts.
  The two overflow entries (Blacklisted chapters, Scanlator preferences) and their `MangaToolbar` /
  `ChapterVisibilityActions` parameters are removed.
- **Dialog:** `ScanlatorPreferencesDialog` (new file) is one `AlertDialog` whose title holds the mode
  switch (`Block` → blacklist, `Group` → scanlators) and whose body is `ScanlatorPreferencesContent`
  or `BlacklistContent`, each with its own scroll state. Both edit one `ScanlatorPreferencesDraft`
  held above the modes, including the half-typed range form, so switching never loses edits. Save
  calls `setScanlatorPreferences(priority, override, rangeRules, excludedScanlators,
  removedBlacklistEntries)`; Cancel stores nothing.
- **Scanlators (Mihon PR #2828 as visual reference only):** `ReorderableColumn` rows with a drag handle,
  the name (dimmed to `DISABLED_ALPHA` when excluded) and a `Visibility`/`VisibilityOff` toggle.
  Excluded scanlators stay draggable so their priority is kept; exclusions are saved through the
  existing `SetExcludedScanlators` only when they changed, and exclusions of scanlators that are not
  available now are kept. The Smart Merge override is three tooltip icon buttons (`Public`,
  `CheckCircle`, `Cancel`; primary when selected) with the effective-state line. Range rules keep
  their parsing, order and "last match wins" semantics, now as a separate section below the list.
  None of Mihon's data model, queries or new-scanlator ordering was imported.
- **Blacklist:** the entries with their resolved chapters (or "unavailable"), removal staged until
  Save (applied to the manga's current blacklist), and the hide toggle as an `IconToggleButton`
  (`CheckBox` / `CheckBoxOutlineBlank`, state in its content description) on the unchanged per-manga
  preference, applied immediately as before. `Dialog.BlacklistedChapters`, `showBlacklistDialog` and
  `removeBlacklistEntries` are gone; `Dialog.ScanlatorPreferences` carries the blacklist and exclusions.
- **Unchanged:** no schema, backup, sync or visibility logic changed; `ChapterSmartMergeTest` and the
  visibility tests still pass. Also removed three imports orphaned by Batch 14.
- **Tests:** `ScanlatorPreferencesDraftTest` (8): initial order, drag moves, exclusion toggles (also
  for moved excluded rows), the three override states, mode switches keeping every edit, range form
  validation and append order, saved ranges still overriding priority via `applySmartScanlatorMerge`,
  staged blacklist removal with unavailable entries. No Compose UI test setup exists.
- **Validation:** all exit 0 — spotlessApply, spotlessCheck, generateSqlDelightInterface,
  verifyDebugDatabaseMigration, testDebugUnitTest (app 260 with 1 skipped, data 93, domain 198; 0
  failures), assembleDebug, assembleRelease, `git diff --check`.
- **Device checks pending:** dragging feel and the lack of auto-scroll while dragging in a long list,
  dialog height with many scanlators and ranges, small screens and large fonts, tooltips, and the
  header button next to the chapter count in phone and tablet layouts.
