# Komikku Fork — Complete Features and Changes

**Project:** xkana-shii/komikku
**Purpose:** Functional specification for retaining or rebuilding the fork's behavior in another Komikku-derived application, including Komi-kun.
**Basis:** The two supplied audits, `2features.md` and `features(2).md`, comparing the fork's `main` with official Komikku as of **19 October 2026**.

## How to read this document

This is **not a changelog, source-code map, or list of Git commits**. It is a behavior-oriented account of everything the audits identify: features, options, interface changes, internal improvements that affect behavior, bug fixes, migrations, and removals. Similar changes are consolidated, **but distinct behaviors are not intentionally discarded**. In particular, a performance port is described by what it is supposed to preserve or improve, rather than by its implementation files.

The source audits examined **730 differing tracked paths** and included **1,218 historical fork-only commits**. Neither number equals the number of live features. Many commits were merge operations, fixes, refactors, reversions, or experiments; many files belong to the same feature. This document therefore distinguishes:

- **Documented current behavior:** the audits traced corresponding functionality in the pinned fork tree, sometimes with dedicated tests. This does **not** imply live-device or remote-service testing.
- **Documented correction or optimization:** a behavior, reliability, or efficiency change evidenced by the inspected tree or associated validation reports.
- **Historical / verify before porting:** mentioned in the commit history, but the audits did not establish that the complete behavior survives in the final tree.
- **Removed / superseded:** an older implementation or option was removed, replaced, or explicitly reverted. It must not automatically be restored as a separate feature.

This document covers **fork-specific differences**. It does not repeat every normal feature shared unchanged with official Komikku. When integrating with Komi-kun, preserve its own existing functions as well; **add the missing fork behaviors rather than replacing Komi-kun's newer implementations with older ones**.

---

## 1. Library organization and nested categories

### 1.1 Parent categories and subcategories

- **True hierarchical categories.** Categories can be nested below a parent instead of existing only as a flat list. Manga organization, category browsing, selection dialogs, and persistence must understand the parent-child relationship.
- **Same names under different parents.** A category is identified by its own identity and parent, not just its displayed name. `Shounen / Reading` and `Josei / Reading` must remain separate categories and must not be matched or merged merely because both are called `Reading`.
- **Parent-child membership consistency.** Assigning a manga to a nested child must produce the intended ancestor relationship. A child-only selection should not leave a logically orphaned membership, and removing or changing categories must not silently create unrelated memberships.
- **Library navigation for nested categories.** The library provides a way to browse parents and their children, including subcategory navigation or chips. A parent view should honor whether descendants are included in its results.
- **Parent-inclusive counts.** Parent category manga counts can include relevant descendants; duplicate manga appearing in multiple child categories must be handled according to the category view's intended counting behavior.
- **Parent/child display mode.** The historical UI work includes an appearance option affecting how parent and child category views are displayed. The current hierarchy system is confirmed, while exact availability of each historical display toggle should be checked against the target build.
- **Default-category behavior.** The fork contains changes to when and how `Default` appears in category tabs, particularly when no custom categories exist. Do not create an empty or duplicate default tab unintentionally.

### 1.2 Category manager

- **Tree-based category manager.** Present categories in a visible hierarchy, with indentation or equivalent parent-child structure, instead of treating all entries as peers.
- **Expandable and collapsible branches.** Expand/collapse individual parents and provide expand-all/collapse-all behavior so large trees remain navigable.
- **Category search.** Search category names while preserving enough hierarchy context to distinguish similarly named children.
- **Create a child category.** Allow the user to choose the parent when adding a category, including creating children from an existing parent.
- **Rename categories.** Edit names without unexpectedly changing parents or conflating similarly named siblings or unrelated children.
- **Move/reparent categories.** Change a category's parent or position while preserving its content and handling invalid moves, such as cycles, safely.
- **Merge categories.** Merge selected categories and their membership according to the intended hierarchy rules, without accidentally collapsing unrelated same-name children.
- **Category sort and reorder.** Support manual ordering and category sorting; the hierarchy must remain stable during changes.
- **Batched/debounced reordering.** Rapid drag or reorder operations should not trigger excessive database writes or cause index/order inconsistencies.
- **Bulk selection.** Select multiple categories for applicable batch operations.
- **Bulk hide/show.** Hide or show selected categories without deleting membership data.
- **Bulk move/merge/delete.** Apply the relevant operation to multiple selected categories while respecting nesting and descendants.
- **Counts and visual state.** Display category counts and appropriate hierarchy/selection indicators; preserve list state while editing.
- **Smooth navigation.** Category tab/chip interactions and scrolling were refined to avoid abrupt or incorrect selection changes.

### 1.3 Assigning categories to manga

- **Hierarchy-aware Set Categories dialog.** Choosing a nested category must recognize the appropriate parent and must not select a different child with the same name under another parent.
- **Correct bulk assignment.** Multi-manga category edits must retain all requested parent-child relationships and must not lose pre-existing unrelated memberships.
- **Category updates after edits.** Library tabs, selection chips, active tab, displayed counts, and membership-dependent filters should react to a category addition, deletion, rename, movement, or merge.
- **Category selection while searching/filtering.** Searching or filtering the library should not arbitrarily jump to an invalid category tab; preserve or resolve the active selection sensibly.
- **Category filtering correctness.** Include/exclude categories must continue to work when the tree changes, when there are no results, and when children share names.

### 1.4 Smart Categories

- **Tracker-status-driven categories.** A category may be associated with a tracker status so manga can automatically enter the appropriate reading-status category as tracker state changes.
- **Parent-branch-scoped Smart Categories.** Status automation must only operate within a relevant category family. Manga already in `Shounen` should move to `Shounen / Reading` when appropriate, not to `Josei / Reading` solely because that child has the same status.
- **Multiple relevant parents.** If manga belongs to more than one relevant parent branch, synchronization must honor the actual category memberships and configured policies instead of selecting the first same-status child globally.
- **Tracker/category synchronization.** Category updates must run after usable tracker state is obtained and persisted. A failed tracker operation should not silently apply an invented status.
- **Hierarchy-safe status changes.** Changing status, completing a manga, starting it, or resetting it must not leave the manga in an unintended sibling branch.
- **Category status policy configuration.** Users can assign/update the tracker status associated with a category and have that policy reflected in automation.

### 1.5 Category update schedules

- **Per-category update intervals.** Configure how often particular categories should be checked for updates rather than relying solely on one blanket interval.
- **Subcategory update scope.** Define how the update policy applies to child categories and avoid accidentally scheduling or skipping unrelated branches.
- **Category-specific picker and saved settings.** The chosen policy should be visible, editable, and persisted through restarts and supported restore paths.
- **Update selection consistency.** The updater must respect the configured category policy and avoid duplicate or inappropriate work for manga in multiple categories.

---

## 2. Library browsing, filtering, sorting, and presentation

### 2.1 Advanced library search

- **Parsed search expressions.** Library search can interpret supported tokenized queries instead of treating every input as a single literal substring.
- **Field-specific terms.** Search syntax can target supported manga attributes using prefixes or other structured terms. Exact supported operators should remain consistent with the fork's parser rather than being invented during a port.
- **Negative/exclusion terms.** Users can exclude matching entries as part of a structured query.
- **Search with nested categories.** Search results must respect the selected parent/child view and include/exclude filters.
- **Responsive live search.** Filtering and preparing results should not stall keyboard input or constantly rebuild unrelated library state.
- **Empty-result correctness.** Empty results should be displayed as an actual filter outcome, without transient index errors or broken category selections.

### 2.2 Library filters

- **Include categories.** Require membership in selected categories under the chosen tree semantics.
- **Exclude categories.** Remove entries belonging to explicitly excluded categories or descendants where applicable.
- **All-category subcategory exclusion.** Historical category work includes a way to exclude chosen subcategories when viewing `All`; validate the exact current control before recreating it.
- **Tracker filters.** Filter/group by tracker association or tracking status without unnecessarily querying each title independently.
- **Bookmark-color filter.** Find manga containing chapters with selected bookmark colors, using the actual stored chapter colors.
- **Unread, downloaded, and other existing filters.** Maintain compatibility with built-in filters while accounting for blacklist and smart-merge visibility.
- **Merged-entry filter correctness.** Downloaded/unread visibility in merged manga must not count inappropriate hidden duplicates or ignore valid child-source chapters.

### 2.3 Source-related grouping and searching

- **Group manga by source.** Provide source-based views/groupings and avoid expensive regrouping whenever unrelated library state changes.
- **Display source languages.** Source grouping can show the source language, with a language icon/flag when selected by settings.
- **Search source name and language.** Historical improvements allow finding source groups using name and language terms; verify exact active behavior in the final library implementation.
- **Source and language badges.** Optional source identifier/language flag display on manga covers or related surfaces.

### 2.4 Sorting, cover appearance, and tabs

- **Extended sort and display choices.** Preserve fork-specific sorting behavior and any custom presentation choices in addition to the destination application's built-in options.
- **Correct sort behavior.** Avoid resetting, reversing, or incorrectly selecting the active library sort mode after state updates.
- **Custom cover ratios and styles.** Apply configured cover aspect ratio, sizing, badge colors, and appearance controls consistently across grid/list modes.
- **Tab/chip navigation.** Clicking a parent category tab can reveal or toggle relevant subcategory chips, with appropriate scrolling and active-state feedback.
- **Stable list item updates.** Download badges, progress, bookmark state, and filters should refresh affected items without disrupting the entire visible list.

---

## 3. Manga details, metadata, and relations

### 3.1 Enhanced manga information

- **Custom metadata.** Preserve additional metadata attached to a manga beyond normal source fields, including supported local overrides and their backups/sync behavior.
- **Description and alternative metadata formatting.** Maintain the fork's source-specific description presentation and Markdown handling instead of flattening rich descriptions.
- **Metadata updates.** When a source or tracker supplies changed details, update the intended metadata while respecting local customizations and identity.
- **Avoid repeat formatting work.** Reuse expensive description/Markdown preparation rather than reconstructing it for every UI recomposition.
- **Cover presentation and updates.** Handle covers, cover dialogs, refresh, and cover-derived appearance without introducing unnecessary repeated database observation.
- **Related-manga metadata.** Retain data needed to display relationships even when it comes from a separate tracker rather than the source itself.

### 3.2 Sequel, prequel, and relation views

- **Related-title lookup.** Retrieve relevant series relationships from supported tracker services, including AniList and MangaBaka where available.
- **Sequel/prequel rows.** Show related series in a dedicated manga-details area with readable titles/covers and appropriate navigation.
- **Cross-service resilience.** Relations should not depend on a single provider when another configured supported service has usable data.
- **Horizontal-row presentation.** Keep the fork's compact relation-card sizing, horizontal scrolling, and edge-to-edge layout conventions rather than rendering a cramped vertical list.
- **Search concurrency limits.** Bound related-series requests so opening a manga cannot overwhelm source/network work.

### 3.3 Chapter-list controls on a manga page

- **Color-bookmark controls.** Users can choose a chapter's bookmark color through the manga chapter UI, including selection and long-press affordances.
- **Chapter filter controls.** Display and reset chapter filters without losing intended filter selections.
- **Reveal hidden chapters.** Allow inspection of blacklisted/hidden chapters from the manga screen rather than making them impossible to access.
- **Chapter sort choices.** Preserve custom sort order and avoid reorder regressions after source synchronization.
- **Chapter selection actions.** Multi-select actions should affect the selected chapters and refresh read/download/bookmark indicators correctly.
- **No unwarranted tracking popup.** Do not automatically reopen the tracking dialog merely because an un-favorited manga was viewed after a dismissal.

---

## 4. Colored chapter bookmarks and legacy marker conversion

### 4.1 Bookmark colors

- **Multiple colors per chapter.** A bookmarked chapter can carry a chosen color from the supported fixed palette; the color is actual chapter state, not merely a temporary icon tint.
- **Normal bookmark behavior remains.** Marking and unmarking bookmarks must still work normally alongside color selection.
- **Color picker from chapter list.** Long-press or corresponding chapter actions let the user choose a bookmark color, including when chapters are selected in bulk where supported.
- **Color picker while reading.** Reader bookmark controls allow selection/change of colors without leaving the reader.
- **Remember/display chosen colors.** Icons, chapter rows, and associated surfaces reflect the saved color and update reactively.
- **Filter by color.** Supported library/chapter filtering uses assigned bookmark colors, not all bookmarks indiscriminately.
- **Reader chapter-list synchronization.** Changing a color in the reader must update its chapter navigation list without forcing an app restart.
- **Reader icon interaction.** Preserve the intended rounded press/ripple behavior and avoid a square touch highlight or displaced bookmark icon.
- **Persist through source updates.** Chapter reloads should not erase a color when the same chapter can still be matched.
- **Preserve through backup and sync.** Import/export/synchronization must retain colors and their relationship to chapter identity.

### 4.2 Migration from Fillermarks

- **Legacy marker preservation.** Existing Fillermark information is migrated into the replacement blacklist semantics where designated by the fork's migration path.
- **Uncolored legacy bookmarks.** Existing ordinary bookmarks are retained without arbitrarily assigning a nondefault color.
- **Identity-aware conversion.** Migration must not blacklist unrelated chapters with similar names or numbers.
- **Do not reintroduce obsolete Fillermark UI as an independent system.** The current audit treats blacklist plus colored bookmarks as the successor behavior. Any coexistence with old marker data is a migration requirement, not a mandate to maintain duplicate permanent state.

---

## 5. Chapter blacklist and visibility

### 5.1 Selective chapter blacklist

- **Blacklist individual chapters.** Hide specific selected chapters from ordinary reading/navigation lists without deleting those chapters from the database or source.
- **Manage the blacklist per manga.** Add/remove exclusions in an accessible chapter blacklist interface.
- **Reveal blacklisted chapters.** Offer a way to inspect hidden content so the user can diagnose or reverse a rule.
- **Exact identity first.** Prefer stable chapter URL/identity to distinguish chapters; use chapter name/number or legacy matching only as a controlled fallback.
- **Prevent name/number collisions.** Blacklisting an anniversary extra or `Season Comeback 6/15` must not accidentally hide chapter 1 or chapter 6 because their numeric fragments overlap.
- **Avoid ambiguous duplicate identities.** When multiple chapters have similar chapter numbers or labels, account for identifying properties such as URL and scanlator rather than relying only on a loose name match.
- **Survive source refresh.** Keep exclusions when refreshed chapters represent the same underlying item, and avoid applying them to a different item after reordering.
- **Do not discard history/bookmarks.** Hiding a chapter does not mean deleting read history or bookmark colors.

### 5.2 Unified visibility across the app

- **Manga chapter list.** Blacklisted chapters are absent from the normal visible list unless explicitly revealed.
- **Reader next/previous navigation.** Skip hidden chapters instead of trapping the user or opening the wrong chapter.
- **History continuation.** Continuing after a hidden chapter resolves to the next visible/readable entry.
- **Library unread/total counts.** Hidden chapters must not inflate visible total or unread counts where visibility-aware totals are intended.
- **Downloads and next-chapter actions.** Follow the same visibility choices to avoid downloading/opening unwanted chapters accidentally.
- **Explicit chapter selection remains meaningful.** If the user deliberately requests a chapter, the reader should honor that request where possible even if an excluded scanlator filter would normally hide it.
- **Consistent fallback behavior.** If a hidden or merged duplicate is encountered via a deep link, notification, or history record, resolve to the appropriate visible equivalent when available.

---

## 6. Scanlator preferences and Smart Scanlator Merge

### 6.1 Scanlator controls

- **Exclude scanlators.** Allow per-manga preferences specifying which scanlators to hide from normal chapter lists.
- **Prioritize scanlators.** Determine the preferred release among multiple scanlators offering the same logical chapter.
- **Manga-specific settings.** Keep exceptions and customized priorities associated with the correct manga.
- **Preserve source chapters.** Filtering or merging changes what the user sees; it must not needlessly delete chapter records, read state, or bookmarks.

### 6.2 Smart Scanlator Merge

- **Group duplicates.** Identify chapters representing the same logical release across scanlators and show a chosen visible version.
- **Priority-based choice.** Apply preferred scanlator rules when selecting the representative chapter.
- **Per-manga overrides.** Overrides can change Smart Merge behavior for one series without rewriting global preferences.
- **Incremental override updates.** Changing one manga's rule updates its corresponding chapter visibility without requiring a full-library recomputation.
- **Consistent counts.** Unread/total counts reflect the selected visible releases, not every duplicate source entry.
- **Reader navigation.** Move to visible representative chapters rather than opening an arbitrary hidden duplicate.
- **History and deep links.** Read/continue actions originating from a hidden version should resolve to the chosen visible representative.
- **Read progress and merged manga.** Keep meaningful read state synchronized when duplicate and merged-source entries are involved.
- **Interaction with chapter blacklist.** The blacklist is applied consistently with Smart Merge; neither rule should inadvertently resurrect chapters hidden by the other.
- **Stable settings changes.** Toggles and priorities should produce predictable results immediately, without orphaned cached state.

---

## 7. Tracking services

### 7.1 Newly supported services

- **MangaBaka tracking.** Find and bind titles; authenticate; read/write status, reading progress, scores, and dates; normalize the service's scoring conventions.
- **MangaBaka metadata integration.** Use bound MangaBaka entries as a source for supported manga metadata and related-series data.
- **MangaBaka date fidelity.** Preserve dates supplied by the service rather than accidentally replacing valid values during conversions.
- **Hikka tracking.** Authenticate and manage titles, status, search, and progress through Hikka.
- **Hikka initial status.** Entries with no progress can use `Plan to Read` as appropriate, rather than incorrectly assigning an active-reading status.
- **Hikka error handling.** Search should close completed responses correctly, handle naming/media-type variations, and surface feedback when a title has been removed remotely.

### 7.2 Improvements to other tracking services

- **AniList relations and tracking.** Continue supporting tracking and related-title information used by the manga-details view.
- **MangaUpdates compatibility.** Keep the fork's search, binding, status, and authentication/error-handling changes; the audits do not guarantee that every remote auth session succeeds.
- **MyAnimeList enhancements.** Support additional author/artist metadata work documented historically; handle partial dates and standard score/status conversions without losing data.
- **Kitsu, Bangumi, and Shikimori improvements.** Retain relevant search/auth, date, score, and status behavior where implemented.
- **Shikimori endpoint modernization.** Historical ports replace obsolete URLs and prefer GraphQL operations when supported.
- **Search by tracker ID.** On supported third-party trackers, an `id:` query can resolve a record directly instead of depending solely on title text.
- **Remote error reporting.** Show meaningful failures for rejected requests, missing results, expired credentials, or remote removal rather than pretending the update succeeded.
- **Tracker usernames in settings.** Display the account name associated with each connected service so multiple logins can be distinguished.

### 7.3 Tracker status and progress correctness

- **Do not lower reading progress.** A normal chapter-read event cannot accidentally roll a tracker backward.
- **Reject invalid chapter numbers.** Ignore nonfinite or negative chapter values rather than sending invalid progress to a service.
- **Refresh before advancing when needed.** Work from current tracker state, apply the new progress, then retain the actual saved result.
- **Start-tracking status correctness.** Adding an entry to a tracker should not put it in an unexpected status simply because tracking was enabled.
- **Automatic progress propagation.** Chapter-read events update linked trackers as intended without requiring a manual edit for each chapter.
- **Tracker categories update after successful state changes.** Smart Category movement must be based on persisted tracker state rather than speculative UI values.
- **Reread count integration.** Historical changes increment reread counters where the relevant tracker supports them; verify current service coverage.

---

## 8. Unified tracker editor and tracker management

### 8.1 One interface for multiple services

- **Unified tracking view.** Show and manage all linked trackers for a manga in one coordinated interface.
- **Edit statuses together.** Apply a selected status to linked services while translating that status to each service's conventions.
- **Edit scores together.** Convert the user's selected score into each tracker scale instead of assigning a raw incompatible value.
- **Edit start/completion dates.** Handle date conversions, empty dates, and service limitations without corrupting supported values.
- **Track and untrack.** Support binding and removing local tracking entries, with remote operations where supported.
- **Multi-title batch actions.** Update or delete track state across several manga through batch operations.
- **Operation queue.** Serialize or coordinate overlapping tracking actions so simultaneous updates do not overwrite each other unpredictably.
- **Partial-failure resilience.** One service failing should be reported and should not invalidate successful writes to unrelated services.

### 8.2 Preferred and priority tracking

- **Preferred tracker per manga.** Remember which linked service is preferred for a particular title.
- **Global tracker priority.** Define a service ordering used when choosing a source of tracker status/metadata where multiple are available.
- **Preferred tracker persistence.** Preferences survive reopening the dialog and supported backup/sync flows.
- **Compatible unified/individual editing.** Switching between editing one tracker and all trackers should not change score semantics or create divergent local state.

### 8.3 Search, autofill, and interface details

- **Tracker search resolver.** Search results are matched to appropriate remote entries and converted into usable local bindings.
- **Tracker metadata autofill.** Pull supported status, date, score, and other metadata from the relevant tracker rather than blindly copying unsupported fields.
- **Distinct linked-service identities.** Keep remote IDs and titles associated with the correct service and manga; do not attach a result to the wrong entry.
- **Tracker error indicator.** Show a concise visual indication when a service refresh or update fails, alongside enough context to retry or inspect the entry.
- **Unified tracker sheet and controls.** Preserve the consolidated presentation and editable state while respecting the destination application's own visual language.
- **Do not reopen a dismissed dialog unexpectedly.** Tracking UI must respect explicit dismissal and whether the manga is in the library.

**Scope note:** The audits verify a unified editor, preferred/priority state, batch operations, and searches. They do **not** establish that every later proposed redesign detail (such as a pen-only Edit Mode, special long-press semantics, or MangaBaka-link pasting into every third-party search) was implemented in this pinned fork. Those are not silently presented as confirmed features here.

---

## 9. Reader, reading controls, and automation

### 9.1 Reader navigation and UI

- **Paged and vertical-reading compatibility.** Fork reader changes must coexist with the supported traditional reading modes.
- **Reader chapter navigation.** Navigating through chapters honors Smart Merge, chapter blacklist, and explicit requests.
- **Title/back navigation parity.** Tapping the manga title in the reader follows the intended back-navigation path rather than returning to a different destination from the back/arrow control.
- **Reader toolbar visibility correction.** Tapping pages after using the chapter navigation slider should correctly dismiss app bars.
- **Bookmark icon in the reader.** Expose bookmark selection and color changing in the reader without removing standard bookmarking behavior.
- **Rounded bookmark touch feedback.** Keep the intended circle-shaped ripple/press target and correct icon placement.
- **Reader chapter-list bookmark updates.** Color changes become visible promptly in reader chapter navigation.
- **Reader loading reliability.** Avoid infinitely waiting on noncancellable initialization work; failure/cancellation should be handled appropriately.
- **Page jumping correctness.** Page-jump controls should land on the intended page and preserve usable toolbar/reader state.
- **Settings and slider state fixes.** Reader setting controls should not fail with key-editing or animation-disabled scrollbar errors.

### 9.2 Automatic scroll and page flip

- **Button-controlled auto-scroll.** The newer automation system starts/stops automatic scrolling from reader controls; old removed implementations should not be copied back wholesale.
- **Automatic page flip.** Support automatic advancement in relevant paged modes.
- **Automation for Webtoon.** Provide continuous-reading automation adapted to vertical mode behavior.
- **Automation for WebGPU.** Allow the reader's automation controls to work with supported GPU viewer modes.
- **Speed/rate selection.** User-facing speeds should be understandable, with usable increments rather than awkward values.
- **Device refresh-rate support.** Account for variable display refresh rates where applicable.
- **Duration limit.** Allow automation to stop after the configured amount of time where supported.
- **Stop at the end.** Automation must not continue attempting to advance beyond the final usable chapter.
- **Stop when sleeping or backgrounded.** Avoid continuing automation if the device sleeps or the reader is no longer active.
- **No disabled background worker.** Do not start unnecessary automation work when the feature is turned off.
- **Keep screen awake during automation.** Prevent unintended screen sleep while the active reader automation requires the display.
- **Saved automation settings.** Retain relevant speed, duration, and behavior preferences.

### 9.3 Webtoon fit and scrolling

- **Optional pinch-to-zoom disable.** The historical changes include a Webtoon preference to prevent pinch zoom.
- **Webtoon scale-type controls.** Historical reader work introduces smart/adaptive fit and other scale choices for long-strip pages.
- **Gap preference for long strips.** Historical settings allow adjusting the gap between images, subject to scale-mode compatibility.
- **Responsive scaling on wider/narrower screens.** Re-evaluate image size after layout or window changes and avoid invalid calculations.
- **No persistent gap after zoom/window resize.** A documented fix addresses unwanted blank space in Webtoon mode.
- **Scrollbars under disabled animations.** Keep scrollbars visible when Android animation duration scaling is disabled.
- **Lifecycle-safe layout listeners.** Remove or release view listeners appropriately to avoid leaks and repeated scaling work.

**Status note:** Some Webtoon adaptive-scaling changes were reverted and reapplied historically. The exact menu choices in the final reader must be confirmed before promising pixel-perfect parity.

### 9.4 WebGPU / high-quality rendering

- **Alternative WebGPU renderer.** Provide a GPU-assisted reader path beyond the traditional image viewer.
- **Continuous WebGPU mode.** Support long-strip reading in the WebGPU system.
- **High-quality rendering option.** Allow the user to select the appropriate quality/rendering behavior if supported by the destination renderer.
- **Consistent background and transitions.** Match expected reader background, page transitions, and animated-image background colors.
- **Fit-height behavior.** Fit-to-height and other supported scaling modes must work properly.
- **Navigation direction and bounds.** Page-direction handling and long-strip boundaries must not invert or skip content.
- **Toolbar/menu behavior in long-strip.** Reader UI must remain hideable and reappear appropriately during long-strip navigation.
- **Texture lifecycle cleanup.** Dispose of GPU textures when a decoded page is no longer usable to prevent memory leakage.
- **Progressive page rendering.** Stream/render page content progressively where supported by the active renderer/decoder.
- **Animated-image handling.** Preserve background and decoding behavior for animated images.
- **Graceful compatibility.** If WebGPU is unavailable on a device, the app must retain a usable non-WebGPU reading path.

### 9.5 Reader downloads, images, and memory

- **Download from the reader.** Historical work adds/reworks downloading from reader chapter actions; final UI parity should be checked because one earlier implementation was reverted.
- **Download status in reader lists.** Surface chapter download failures/loading state with appropriate retry affordances where present.
- **Stream image/download data.** Avoid requiring large entire-image buffers unnecessarily for common reading paths.
- **Choose the appropriate image decoder.** The newer decoder and progressive-rendering setup supersedes older decoders.
- **Bound image request concurrency.** Image fetches should not overwhelm network connections or starve metadata/API requests.
- **Source-aware throttling.** Control transfer concurrency per source/host rather than applying a single excessive global burst.
- **Reader database work off the UI path.** Page loading and reader state should not block on synchronous database work.
- **Image/cache cleanup.** Historical Coil cache work seeks to avoid accumulating leaked data over long sessions.
- **Page preloading configuration.** Keep applicable preloading/reading performance preferences.

---
## 10. Browse, source browsing, and Global Search

### 10.1 Browse source lists and filters

- **Source-category organization.** Organize and filter available sources by configured source categories.
- **Source icons and language flags.** Use appropriate source icons or language indicators in source/browse surfaces when enabled.
- **Source status and enable/disable behavior.** Disabled sources should not be treated as eligible for background source searches unless explicitly re-enabled.
- **Source list responsiveness.** Fetch/source initialization must not block browsing controls or make the search input unresponsive.
- **Source matching and initialization.** Recognize source identities consistently after extension updates, backup restores, and source-category preference changes.
- **Source settings.** Keep editable source-specific preferences and make them available from the appropriate browse/extension screens.
- **Fresh request behavior.** Request fresh results when necessary without indiscriminately clearing caches for unrelated source calls.
- **Source API concurrency control.** Avoid unrestricted concurrent calls during source metadata or related-manga operations.
- **Background subscription cleanup.** Stop observing source/database data when the corresponding Browse screen is off-screen; resume observation when it becomes relevant again.

### 10.2 Global Search

- **Search selected source categories.** Limit a Global Search to the sources belonging to a chosen source category, instead of always searching every installed or enabled source.
- **Persistent search-source scope.** Preserve relevant selection/category settings where the fork already saves them.
- **Source category controls.** Allow the user to choose the intended category of sources and understand which sources will be searched.
- **Search result grouping.** Keep results associated with their originating sources and avoid confusing stale and current queries.
- **Bound parallel searches.** Run a controlled number of searches rather than launching unrestricted simultaneous source requests.
- **Atomic result updates.** Prevent late results from an older search from partially overwriting a newer query's result set.
- **Debounced input.** Avoid a new expensive search for every keystroke when the user is typing quickly.
- **Usable loading/error/empty states.** Show source-specific progress and failures without blocking already available results.

### 10.3 Source page browsing

- **Responsive search bar.** Typing into a source's browse/search field must remain responsive even when filters or network requests are being prepared.
- **Filter state isolation.** A source filter request should use a safe snapshot of selected filter values so concurrent requests cannot accidentally mutate each other's filters.
- **Filter reset and persistence.** Preserve or reset filters appropriately when moving between source pages and saved searches.
- **Source result refresh.** Reload the selected source state without recreating unrelated browse navigation or dropping the user's input.
- **Correct back navigation.** Returning from manga/search screens should keep the intended source/browse context.

---

## 11. Source feeds, saved searches, and feed refresh

### 11.1 Feeds based on sources and searches

- **Persistent source feeds.** Users can maintain feed entries associated with individual sources, not just temporarily browse `Latest` or `Popular`.
- **Saved-search feeds.** A saved search can become a feed, preserving the search term and supported source filters.
- **Search/filter snapshot.** Reconstruct the intended saved search when requesting feed results instead of losing its filters or treating it as a plain search.
- **Source-feed associations.** Preserve which saved search belongs to which source and feed entry.
- **Feed ordering.** Store and restore the order of configured feeds.
- **Saved-search overwrite handling.** Updating an existing saved search/feed should replace the correct entry and should not generate an unintended duplicate.
- **Feed restore compatibility.** Restore saved-search definitions before or together with the feed references that depend on them.
- **Source-category-aware feed behavior.** Integrate feeds with the relevant source/category context rather than querying unrelated disabled sources.

### 11.2 Feed lifecycle and errors

- **Independent feed loading.** Each feed can have its own loading, success, or failure state.
- **Per-feed retry.** A failing feed offers a retry action for that feed, without requiring a full refresh of every feed.
- **Refresh all feeds.** The global refresh action must refresh all intended feeds, not only entries previously marked as errors.
- **Clear error feedback.** A source/feed failure should be shown in an appropriate error row or state with a retry affordance.
- **No stale first-result substitution.** A previously cached or in-flight request should not silently replace a newly selected feed's contents.
- **Source-specific request safety.** Use properly isolated filter/request data so two simultaneous feed requests do not interfere with one another.
- **Usable transitions.** Refreshing, restoring, or switching feeds should not make the whole feed page unusable while one request is pending.

### 11.3 Saved-search usability

- **Reusable saved searches.** Saved searches remain usable both when opened directly through a source and when invoked through a feed.
- **Correct search matching.** Feeds using saved searches should not be rejected as missing or unmatched when the saved source/query/filter combination is still valid.
- **Backup/restore of feeds.** Feed and saved-search definitions, order, and relationships survive compatible backups.
- **No accidental cross-source requests.** Do not apply filters from one source's saved search to another source just because the names overlap.

---

## 12. Extensions, repositories, and WebView

### 12.1 Extension management

- **Additional extension repository customization.** Retain the fork's repository configuration and source/extension icon customization.
- **Extension store management.** Manage extension stores/repositories and update available lists when their enabled state changes.
- **Refresh after repository changes.** Enabling or disabling a repository refreshes the extension list so changes appear without restarting the app.
- **Extension details and source preferences.** Preserve the ability to inspect an installed extension and configure its sources.
- **Extension/language/source filters.** Present and save the intended filters without unnecessary background observation when the screen is inactive.
- **Prevent stuck pending installs.** Installation/download tasks should progress from pending or report a meaningful failure rather than remaining pending indefinitely.
- **Private extension update route.** Updates to private extensions use the intended private installer rather than accidentally switching installation methods.
- **Shizuku permission-aware detection.** Treat Shizuku as usable only when the required service/permission state actually supports it.
- **Shizuku installation fixes.** Preserve the updated installation behavior and compatibility required for invoking external Shizuku methods.

### 12.2 WebView and request compatibility

- **Proper multi-window WebView support.** Distinguish a new window from an ordinary redirect, instead of incorrectly forcing all such actions into the same navigation path.
- **Multi-window user experience.** Opening and leaving multiple windows should preserve the intended browser/source navigation.
- **WebView crash fixes.** Retain corrections for crashes caused by WebView lifecycle or window changes.
- **Source request compatibility.** Preserve supported client-hint/header spoofing changes for sources requiring browser-like requests.
- **Graceful absence of optional Android window APIs.** Build/runtime compatibility should not depend on optional extension classes being present on every device.
- **Avoid stale network results.** Explicitly fresh source requests should not return an unrelated older response simply because a request was cached.

---

## 13. History, reading continuation, and read state

### 13.1 History screen

- **Daily distinct-manga count.** Each date heading displays the number of *different manga* represented that day. Three chapters of the same manga on one date count as one manga, not three.
- **Date-grouped History.** History entries remain grouped by their reading dates, including the associated distinct-title count for each group.
- **Initial loading separated from empty state.** The app knows the difference between `History has not loaded yet` and `History contains no matching entries`, avoiding misleading empty messaging.
- **Visibility-aware History filters.** When identifying unfinished titles or remaining chapters, consider the chapters actually visible after blacklist and Smart Merge rules.
- **Efficient History observation.** Avoid continually collecting History queries when the History screen is not subscribed/visible.

### 13.2 Continue reading

- **Next readable chapter.** A History continue action advances to the next genuinely readable chapter, not necessarily the next stored row.
- **Skip blacklist exclusions.** Blacklisted chapters do not interrupt continuation or cause the reader to stop early.
- **Resolve Smart Merge duplicates.** Continuing from a hidden duplicate should select the visible representative when one exists.
- **Preserve History identity.** Resolving a chapter for continuation must not rewrite or erase the underlying reading record merely because its visible representative changed.
- **Merged-title handling.** History and progress for merged manga should continue to correspond to the appropriate underlying chapters.

### 13.3 Read/progress corrections

- **Mark-read correctness.** Explicit mark-read/mark-unread and reader progress changes should update the intended chapter without duplicate database entries.
- **Next-chapter state consistency.** Reader, History, library badges, and tracker progress agree on which chapter has been completed.
- **Source update does not erase user flags.** Matching a re-fetched chapter preserves the appropriate read, bookmark, and visibility state.

---

## 14. Updates, Upcoming, update jobs, and release prediction

### 14.1 Updates screen

- **Category filters for Updates.** Narrow the updates list using configured category inclusion/exclusion, maintaining the correct relationship to nested categories.
- **Fetch-date ordering.** Store/query update items in a consistent fetch-date order rather than relying on unstable client-side reordering.
- **Accurate read and download indicators.** Reflect chapter-visible state and keep updates consistent after read/download changes.
- **Subscription-aware Updates screen.** Stop unnecessary observation when the screen is no longer being viewed.
- **Correct refresh/error states.** Report update failures without confusing the entire screen's empty and loading states.

### 14.2 Upcoming calendar

- **Category filters for Upcoming.** Include or exclude releases based on manga category membership.
- **Compatibility with category hierarchy.** Apply selected category scope appropriately when parents contain children.
- **Reactive Upcoming data.** Keep displayed release predictions/current filters in sync without expensive continuous collection while inactive.

### 14.3 Release interval prediction

- **Smart release interval prediction.** The fork contains work to improve estimates of when manga chapters will appear.
- **Per-title predictions.** Predictions should correspond to each manga's release history rather than blindly using the same interval for all titles.
- **Refresh after new chapter evidence.** Update estimates when the underlying release/chapter history changes.
- **Do not imply guaranteed dates.** These are predictions, not source-confirmed future releases. The exact algorithm and display surface are not fully established by the supplied audits.

### 14.4 Library updater

- **Category-specific scheduling.** Honor configured category update intervals/scopes.
- **Controlled parallel update slots.** Keep the ability to limit concurrent source/library update work where available.
- **Update timestamp after completion.** Record the appropriate final update time when work finishes instead of prematurely marking it complete.
- **Cancellation.** Respect a user-initiated cancellation of library update work.
- **Avoid repeated checks on configuration changes.** Rotating or recreating the screen should not trigger unnecessary duplicate update checks.
- **Reliable worker/error notifications.** Surface failures accurately while avoiding misleading or duplicated notifications.
- **No duplicate chapter creation.** Synchronizing source updates should reconcile existing chapter identities rather than appending copies of the same chapters.

---

## 15. Backups, restores, and migration compatibility

### 15.1 Full feature preservation in backups

- **Category hierarchy.** Back up parents, children, memberships, ordering, and necessary policy settings.
- **Category update schedules and tracker-status policies.** Restore enough information for automation to behave the same way afterward.
- **Colored bookmarks.** Preserve chapter color, not just the basic bookmarked/unbookmarked flag.
- **Chapter blacklist.** Preserve per-manga exclusions and stable chapter-identification information.
- **Scanlator preferences and Smart Merge overrides.** Restore preferences alongside the relevant manga, instead of accidentally applying an override to a different title.
- **Custom metadata.** Preserve supported local manga metadata separately from transient display-only data.
- **Tracking state.** Preserve appropriate tracker bindings, preferences, status, and related state where the backup format supports it.
- **Source preferences.** Historical work explicitly expands saving source-specific preferences, so restoring a source should not silently discard its configured settings.
- **Feeds and saved searches.** Preserve saved queries, filters, source associations, feed order, and the links between them.
- **Synced identity data.** Keep the stable keys required to associate restored records with the right remote/source manga.

### 15.2 Restore correctness

- **Compatible old backups.** An older backup lacking a newly introduced field should not automatically fail; use a safe compatibility default.
- **Identity-safe restore.** Manga that share a display title are not interchangeable if they originate from different sources or URLs.
- **Dependency-aware restore order.** Restore parent categories before dependent child/category membership data and saved searches before dependent feed references.
- **Batched transactions.** Restore groups of records efficiently rather than issuing a separate expensive transaction for every small change.
- **Rollback on failure.** A failed batch should not leave corrupt half-written state.
- **Fallback for failed chunks.** When a batch cannot complete, recover in a controlled fashion while preserving successfully restored neighbors where supported.
- **Cancellation safety.** User/system cancellation must not leave the app falsely claiming a complete restore.
- **Post-commit metadata files.** Apply filesystem/external metadata changes only after the database transaction is committed.
- **Accurate worker lifecycle.** Backup/restore jobs should not disappear from the UI unexpectedly or get detached from their completion state.
- **Background I/O execution.** Backup and restore must not block interactive rendering unnecessarily.

### 15.3 Migrations between feature versions

- **Database migration safety.** Apply migrations without losing parent IDs, bookmark colors, blacklist state, track bindings, custom metadata, or feed associations.
- **Legacy Fillermark migration.** Convert supported old marker state to the modern visibility/bookmark model as intended.
- **Compatibility with older schema fixtures.** Restore/tested historical migration paths must continue to work where supported by the audit.
- **Local manga and merged manga safety.** Data cleanup or migration must not delete the wrong manga folder or merge identities incorrectly.
- **No unnecessary source migration exclusions.** Separate visibility rules from source migration matching so hidden chapters do not make valid replacement sources disappear incorrectly.

---

## 16. SyncYomi and cross-device synchronization

### 16.1 Stable identities

- **Manga identity based on source and URL.** Synchronization should identify a title by stable source-specific information rather than its mutable display name.
- **Chapter identity based on chapter URL.** Changes in chapter sorting, displayed title, or chapter numbering should not be interpreted as deletion and creation of an unrelated chapter when the URL still matches.
- **Deduplicate remote data.** Avoid creating repeated copies of the same chapter when remote results contain duplicate URLs.
- **Correct additions/removals/updates.** Reconcile new chapters, removed chapters, and changed source-provided fields while preserving user-owned state.

### 16.2 State convergence

- **Sync custom metadata.** Reflect supported custom manga information, not only base title/chapter fields.
- **Sync chapter flags.** Carry read/bookmark and supported additional chapter state without losing newer local decisions.
- **Sync bookmark colors and visibility state.** Preserve corresponding extended fields when supported by the sync format.
- **Sync versions/changes coherently.** Updated records eventually converge rather than bouncing repeatedly between incompatible versions.
- **Avoid infinite metadata sync loops.** When local and remote representations conflict, stop cycling the same change indefinitely.
- **Stable ETag/remote state bookkeeping.** Treat remote version markers as synchronization state and avoid corrupting them during app-state operations.
- **Wait for restore/sync completion.** Do not mark synchronized restore state as updated before the associated work is actually finished.
- **Error and cancellation handling.** A partial or failed sync must not falsely advance the last-successful-sync state.
- **Preserve user flags through refresh.** Source refresh and remote sync must not unexpectedly mark previously read chapters unread.

---

## 17. Auto Reread and other automation

### 17.1 Auto Reread

- **Detect a reread start.** Provide the intended reread flow when returning to a completed manga rather than treating it as ordinary first-time reading.
- **Configurable reset mode.** Let the user choose how read-state reset is handled before or during rereading.
- **Confirmation/prompt behavior.** Provide appropriate user control when a reread begins.
- **Chapter reset semantics.** Reset the intended chapters without unintentionally changing unrelated manga or hidden source records.
- **Tracker coordination.** Apply tracker status/progress/reread-count changes consistently with the reread operation and tracker capabilities.
- **Saved settings.** Preserve Auto Reread preferences and make the setting accessible from the intended screen.

### 17.2 Category and tracking automation

- **Automatic tracker progress on reading.** Progress is propagated from actual chapter-reading events under intended conditions.
- **Category movement by tracker status.** Smart Categories update after valid tracker status changes.
- **No accidental repeated actions.** Multiple tracker writes, background updates, or UI recompositions should not duplicate the same automation operation.

---

## 18. Connections, Discord, and webhooks

### 18.1 Webhooks — documented current feature

- **Configurable webhook notifications.** Enable or disable delivery to configured endpoints.
- **Discord webhook option.** Store an appropriate Discord webhook URL and send supported notification payloads.
- **Structured event payloads.** Send relevant event information rather than an unstructured generic ping.
- **Asynchronous bounded queue.** Delivery occurs without freezing reading or navigation, and the application avoids launching unlimited notification requests.
- **Private endpoint storage.** Protect configured sensitive URLs/preferences and avoid unnecessary exposure in logs or exported data.
- **Failure handling.** Delivery failures should be handled without blocking the originating action or silently corrupting app state.

### 18.2 Discord Rich Presence — historically developed, exact current UX unverified

- **Presence integration.** Historical changes display the current reading/activity state in Discord.
- **Account/login management.** Historical UI and network changes improve login state, account controls, and token retrieval.
- **Restart/stop controls.** Presence could be restarted and stopped, including via a foreground-service notification.
- **Android background-service compatibility.** Foreground-service behavior was adapted for newer Android versions.
- **Cover and chapter formatting.** Historical changes address images, episode/chapter labels, and app identity shown in Discord.
- **Auth and API resilience.** Historical fixes handle missing avatars, no-token state, API limits, and service crashes.
- **Sensitive tokens are not ordinary backup data.** Avoid exporting private Discord tokens as nonsensitive preferences.

The source audit confirms differences in the remaining Discord service/model area but **does not certify that all historical RPC behaviors still exist**. This is a verification list, not a claim that the original full RPC implementation must be copied verbatim.

---

## 19. Storage, downloads, and file maintenance

### 19.1 Storage overview

- **Dedicated storage screen.** Show storage consumption separately from the normal download queue.
- **Storage categorization.** Break down space by the supported categories/types of stored data.
- **Cumulative totals.** Calculate meaningful aggregate sizes so the user can see the major storage consumers.
- **Category-based selection.** Select or filter storage categories for management.
- **Deletion/cleanup controls.** Remove selected stored content while accurately reflecting reclaimed space and preserving unrelated content.
- **Merged-manga folder safety.** A merged entry's data-folder cleanup should target the intended underlying source folders instead of deleting unrelated local files.

### 19.2 Downloads and queues

- **Custom concurrent-download setting.** The documented performance ports include configurable concurrent downloads/page fetching.
- **Download queue state.** Queue, progress, errors, and removal are reflected correctly in the Downloads screen.
- **Release off-screen queue observation.** Do not keep collecting queue updates at full cost when no screen is using them.
- **Source-aware request pressure.** Prevent image downloads from starving source metadata and tracker API traffic.
- **Temporary-file cleanup.** Canceling/removing a download should clean up appropriate temporary files without treating a removed temporary file as a new download error.
- **Progressive/streaming delivery.** Support reader consumption of data as it becomes available instead of duplicating entire buffers unnecessarily.
- **Filtered chapter consistency.** Download-related counts and actions respect visible chapters/merged sources where appropriate.

---

## 20. Themes, settings, and interface behavior

### 20.1 Appearance and themes

- **Tokyo Night theme.** Additional day/night color definitions and selection behavior.
- **Cover-derived colors.** Supported screens can use colors derived from a manga cover in accordance with appearance preferences.
- **Custom cover appearance.** Configure cover proportions, badge/icon display, and associated layout styling.
- **Language icon options.** Choose whether language flags/icons replace or accompany textual source indicators.
- **Badge coloring.** Preserve fork-specific coloring for library progress/status badges.
- **Iconography.** Keep appropriate application, source, and migration icons without restoring an obsolete visual style over a newer target implementation.
- **Material component modernization.** Adopt compatible styling/controls from the newer UI component revisions where relevant.

### 20.2 Settings and dialogs

- **Expanded setting screens.** Preserve settings for categories, update intervals, trackers, reader automation, downloads, appearance, connections/webhooks, and advanced behavior.
- **Slider subtitles/descriptions.** Settings sliders can show explanatory supporting text where implemented.
- **Date picker timezone correction.** Allow valid equal start/end dates even for negative UTC offsets.
- **Adaptive sheets.** Updated bottom-sheet drag/settling behavior should remain functional and dismiss appropriately.
- **Generic selection controls.** Settings pickers work for the appropriate choice types without breaking existing preference values.
- **Readable loading indicators.** Loading screens retain meaningful progress percentages/feedback when available.
- **Correct pluralization.** Automated-speed, duration, count, and related messages use number-appropriate wording.
- **Updated notifications and error messaging.** Use clear warnings and actions without flooding the UI with redundant messages.
- **Navigation/back-stack corrections.** Returning from a dialog, manga, or reader should land on the intended previous screen.

---

## 21. Performance, database, and application architecture

These are behavior-preserving improvements. Unless indicated, the source audit reports code and test evidence, **not new measured battery, FPS, or startup benchmarks**.

### 21.1 Library caching and query efficiency

- **Cached category grouping.** Reuse grouped manga/category views when their inputs are unchanged.
- **Cached manga-item preparation.** Avoid reconstructing every list item after a change to one manga or download badge.
- **Cached chapter visibility.** Store/precompute effective visible-chapter counts for expensive filtering paths.
- **Selective cache invalidation.** A bookmark, smart-merge, blacklist, category, or chapter change invalidates only affected data wherever safe.
- **One relevant preference subscription instead of one per manga.** Avoid creating thousands of independent preference listeners for large libraries.
- **Batched visible-chapter queries.** Calculate counts for groups of manga in one operation rather than issuing a separate query for each entry.
- **Cached chapter statistics.** Store/use relevant aggregated unread, total, and related chapter counts with fallback when cache data is missing.
- **Avoid unnecessary result recomposition.** Equal library items and equivalent lists can be reused so navigation/download updates do not cause full UI rebuilds.
- **Efficient tracker filters.** Avoid redundant per-manga tracker work and unnecessary passes when grouping by tracker status.
- **Efficient category filters.** Query/filter using intended category membership semantics without repeated mapping of the same tree.
- **Throttle invalidations after write bursts.** Rapid read/progress/category updates should not trigger one full library rebuild per individual database write.
- **Aggregate only relevant manga.** Avoid calculating chapter statistics for the entire database when the visible/query result set is much smaller.

### 21.2 Database modernization

- **Asynchronous SQLDelight work.** Use the appropriate async database interfaces for relevant queries and migrations.
- **Stable generated IDs.** Use database-returned generated identities rather than relying on connection-global last-insert-ID state that can become unsafe under concurrent work.
- **Transaction-correct query notifications.** Observers should see committed changes rather than reacting prematurely to an uncommitted insert.
- **Batched chapter synchronization.** Apply related source chapter changes in a coordinated transaction.
- **Specialized update queries.** Use focused operations for categories, chapters, and other targeted updates rather than expensive universal full-row updates.
- **Database connection setup.** Configure modern driver connections in a way that supports safe concurrent reads/writes and avoids avoidable lock contention.
- **Modern bundled SQLite driver.** Use the selected AndroidX database driver where compatible, while keeping encrypted/unencrypted behavior correct.
- **Versioned schema/view updates.** Preserve the migration history and update queries together so existing user databases remain valid.
- **Rollback and fallback in restore.** Keep transactional safety while benefiting from larger batches.

### 21.3 State, lifecycle, and UI efficiency

- **Lifecycle-aware ViewModels.** Replace or adapt old screen-model state holders with lifecycle-aware state management without losing the actual screen functionality.
- **Collect data only while subscribed.** Library, cover, History, Updates, Downloads, categories, migration, sources, extensions, filters, and Upcoming no longer needlessly observe everything when their UI is absent.
- **Do not turn a rename into a feature removal.** Screen-model-to-ViewModel renaming is an internal refactor, not evidence that a screen or capability was deleted.
- **Avoid side effects during UI-state reduction.** Keep writes/network operations out of operations that merely recompute display state.
- **Debounce expensive search updates.** Input remains responsive while the app performs background queries.
- **Bound source/Global Search concurrency.** Avoid flooding slow source servers or the device with unbounded simultaneous work.

### 21.4 Network, image, and startup performance

- **Share suitable network resources.** Reuse HTTP clients/connections where appropriate instead of creating excess instances.
- **Request freshness controls.** Avoid returning stale source data when a forced refresh is requested, without clearing unrelated caches.
- **Source-image throttling.** Ensure bulk image transfers do not delay normal metadata/API calls.
- **Progressive image decoding/rendering.** Avoid materializing redundant full images where the current reader pipeline can stream/decode incrementally.
- **GPU texture cleanup.** Release graphics resources after pages are discarded or replaced.
- **Baseline and startup profiles.** Package generated profiles as part of startup/launch optimization infrastructure.
- **Prevent listener/cache leaks.** Cleanup long-lived reader layout listeners and other resources when their screen/view is destroyed.

---

## 22. Build, release, and developer tooling changes

- **Automated version bump workflow.** A dedicated manual/CI flow updates version information, runs applicable formatting checks, and can create the expected tag.
- **Correct version rollover.** The fork's bump rules include moving to the next minor version after patch `.10`, rather than continuing incorrectly through `.11`, `.12`, etc. as if the intended rollover never happened.
- **Version declaration safeguards.** Fail clearly when version information is missing or ambiguous rather than rewriting arbitrary values.
- **Stable and preview build adjustments.** Fork-specific GitHub release/preview workflow behavior and publishing paths are maintained.
- **Fork release links and branding.** Release/README links point to the fork's channels instead of misleadingly linking to upstream artifacts.
- **Gradle configuration cache.** Build-configuration performance improvements are enabled where compatible.
- **Gradle isolated-project support.** Build configuration is adapted to support isolated projects where safe.
- **Nontransitive Android resource identifiers.** Build tooling is adapted to the modern resource-reference mode.
- **Dependency modernization.** Updated networking, database, rendering, UI, and build dependencies are incorporated with compatibility fixes.
- **Formatting and quality checks.** Spotless/static checks and targeted unit tests are part of the fork's maintenance workflow.
- **Profile-generation replacement.** The old macrobenchmark generator module was replaced by newer baseline-profile infrastructure; this does not mean profile packaging should be removed.
- **Removed telemetry module.** An older telemetry/Firebase module is absent from the compared fork tree; do not reintroduce it accidentally simply because the destination repository still contains it.
- **Port/audit documentation.** The fork contains records distinguishing imported optimizations, compatibility adaptations, incomplete ports, and blocked dependency changes. These are maintenance safeguards, not end-user UI features.
- **Test compatibility.** Database migrations, saved-search/feed operations, category hierarchy, tracker conversions, backup restoration, and chapter visibility have regression-test coverage in the audits.

---

## 23. Specific bug fixes and behavior corrections

This section explicitly inventories the fixes so that small but important changes are not lost inside feature descriptions. Some overlap conceptually with earlier sections; this is deliberate cross-checking, **not an instruction to implement them twice**.

### 23.1 Categories and library

- **Same-name child collision:** Categories with the same label under different parents are not confused.
- **Hierarchy-aware assignment:** Selecting a child preserves the correct parent relationship.
- **Wrong-branch Smart Category movement:** Tracker updates affect the relevant parent branch, not an unrelated branch with a matching status.
- **Category index/order stability:** Sorting, reordering, and mapping should not generate duplicates or incorrect indexes.
- **Category search and tab selection:** Search/filter changes do not select an invalid or unrelated tab.
- **Default category edge cases:** Empty/custom-category situations show the intended default tab.
- **Empty-library filter crash:** Opening a filter sheet or marking selected items read/unread must not crash when library data is empty or changes during the action.
- **Library index/race issues:** Large or rapidly updated lists should not crash due to stale positions.
- **Tracker-filter performance:** A tracker filter does not require excessive repeated work.
- **Merged manga downloads:** Download filters and badges reflect the correct source/merged state.
- **Duplicated library entries:** Erroneously duplicated stored records are handled safely rather than multiplied by ordinary operations.
- **Library sort regressions:** Preserved sort mode/order remains coherent after updates.
- **Preference fanout:** Avoid one observer per individual manga for shared settings.
- **Visibility invalidation:** Changing one blacklist/Smart Merge override does not unnecessarily recalculate the entire library.

### 23.2 Chapter visibility and reading

- **Blacklist false positives:** Extras and anniversary chapters do not accidentally hide ordinary numbered chapters.
- **Smart Merge consistency:** Reader, History, library counts, and chapter lists choose the same visible representative.
- **History after hidden chapters:** Continue resolves to the next readable chapter rather than the next hidden record.
- **Custom bookmark color updates:** The reader chapter list reflects changes immediately.
- **Reader bookmark press target:** Ripple remains appropriately circular and centered.
- **Reader title navigation:** Title tap follows expected reader-back navigation.
- **Hidden chapter reveal:** User can inspect and reverse blacklisted entries.
- **Correct chapter synchronization:** New source chapters are reconciled by stable identity rather than creating duplicate entries.
- **Merged read progress:** Duplicate/merged entries should not create contradictory read states.
- **Reader initialization deadlock:** Loading can terminate or recover rather than hanging indefinitely.
- **Webtoon resize gap:** Image scaling after zoom/window changes does not leave persistent blank space.
- **GPU texture leak:** Discarded or unusable pages do not retain allocated texture resources.
- **Viewer fit-height and bounds:** Scaling/navigation operate correctly at long-strip edges.
- **Paged toolbar dismissal:** Page taps after using the navigation slider still hide controls normally.
- **WebGPU animated-image background:** Animated content respects expected background colors.

### 23.3 Tracking

- **Incorrect status on starting tracking:** Initial link does not automatically choose an unintended tracker status.
- **Malformed progress:** Ignore negative/nonfinite chapter numbers.
- **Accidental progress rollback:** Reading older chapters does not reduce remote progress.
- **Partial MyAnimeList dates:** Date handling does not fail when only part of a date is available.
- **MangaBaka date conversion:** Remote valid dates survive read/write conversions.
- **Hikka empty-progress state:** Use suitable planned status for not-yet-read titles.
- **Hikka search/resource cleanup:** Search failures do not strand open responses.
- **Hikka removed-remote feedback:** Report when an entry has been removed remotely.
- **Shikimori endpoint changes:** Use supported endpoints/API behavior.
- **Tracker operation ordering:** Concurrent writes do not overwrite each other unpredictably.
- **Tracker login failures:** Fail visibly and preserve other linked services when a token is invalid.

### 23.4 Feeds, search, and extensions

- **Refresh-all only refreshing errors:** Global feed refresh must cover the intended feeds, not just failures.
- **Saved-search feed mismatch:** Valid saved source searches are not rejected due to lost associations or filter state.
- **Feed overwrite collision:** Updating one saved feed does not corrupt a different one.
- **Stale source request results:** New refreshes do not become permanently stuck on the previous cached response.
- **Unresponsive Browse search:** Typing remains responsive during search requests.
- **Global Search stale responses:** Old parallel results cannot replace a more recent query's results.
- **Extension stuck at pending:** Installer state transitions correctly or presents a useful failure.
- **Shizuku installation:** Permission/service checks and externally invoked methods remain compatible.
- **WebView crashes/multiple windows:** Opening site-controlled windows does not trigger redirect confusion or a WebView crash.

### 23.5 Backup, sync, and databases

- **Manga identity collision in backup:** Same-titled entries retain their distinct source/URL identities.
- **Legacy backup missing fields:** Restore accepts supported old files using sensible defaults.
- **Metadata-after-commit atomicity:** External metadata does not get written for a transaction that failed.
- **Metadata sync infinite loop:** Conflicting local/remote fields do not repeatedly sync forever.
- **Correct SyncYomi version state:** Failed/partial sync does not mark itself complete.
- **Batch restore failure:** Preserve valid neighboring records while recovering from failed chunks.
- **Migration compatibility:** Schema migration logic remains compatible with supported historical backup/database states.
- **Potential database lock contention:** Updated connection/query design reduces avoidable lock hazards.
- **Correct generated IDs:** Parallel writes do not attach saved searches, categories, or feeds to the wrong records.
- **Notification timing:** Database observers are notified after appropriate transaction boundaries.

### 23.6 Smaller UI/network corrections mentioned in history

- **Date-picker timezone:** Same start/end dates can be selected correctly in negative time zones.
- **Reader key-editing error:** A reader settings action no longer fails with an invalid preference-key edit.
- **Scrollbar with animations disabled:** Webtoon scrollbar is not suppressed by device animation settings.
- **Discord login/account and no-token failures:** Historical fixes avoid inappropriate crashes and invalid login display.
- **Merged-entry folder cleanup:** Deleting a merged manga's download folder chooses the right folders.
- **Temporary download errors:** Cancelling/deleting temporary files is not falsely shown as a new download failure.
- **Source update recheck on rotation:** Screen configuration changes do not schedule another redundant check.
- **Crash-log verbosity:** Dump generation respects the verbose-logging preference.
- **Settings and navigation:** Various screen/dialog presentation and back-stack corrections are part of the fork's changes, although generic historical commit messages cannot establish additional specific requirements.

---

## 24. Historical changes that must be checked before claiming they remain

The following changes appear in the **history** but are not fully confirmed as currently active by the supplied final-tree audit. They are included to avoid losing track of them, **not** to prescribe automatic implementation.

1. **Discord Rich Presence complete UX:** account manager, foreground notification controls, restart/stop, avatars, cover images, and formatted Discord activity.
2. **Adaptive Webtoon scaling option combinations:** smart/adaptive width fit, custom long-strip gaps, and disable-pinch settings passed through reversions and reapplications.
3. **Duplicate-entry management screen:** an experimental UI for locating, listing, sorting, searching, hiding, and backing up duplicate manga was built and later reverted. It is **not a confirmed present-day screen**.
4. **Automatic duplicate matching levels:** historical matching sensitivity and multiple-title thresholds accompanied the reverted duplicate-manager work.
5. **Legacy SY auto-scroll:** removed fields and help dialogs must not be mistaken for the current button-controlled automation, which uses a different system.
6. **Old Fillermark-specific UI:** historical indicator/marker work predates the currently documented colored-bookmark and chapter-blacklist migration architecture.
7. **Download-from-reader UI revisions:** an early reader-download feature was reverted and later reworked; confirm which present-day actions remain.
8. **Additional early source/delegated-source changes:** generic commits describe source delegation, source recommendations, and delegated API work, but do not establish a separate currently active user-facing feature.
9. **Developer/debug options:** historical additions mention a developer-options surface; specific remaining controls are not established in the final audit.
10. **Advanced source-name/language search and category presentation toggles:** features described in the historical category/library commits; verify exact final settings rather than assuming all intermediate revisions survived.
11. **Auto Reread count behavior for each tracker:** the workflow exists, but remote support depends on each tracker implementation.
12. **Extra performance ideas not established as measured results:** intended improvements are documented; do not advertise quantified battery, speed, or memory gains without benchmarks.
13. **Recovery-branch-only changes after the pinned main snapshot:** the supplied audits mention additional active recovery work, including download-queue changes. Work not merged into the pinned main must be treated separately, not assumed present.

---

## 25. Intentionally removed or replaced behavior

- **Old Fillermarks as an independent permanent marking feature:** superseded in the documented design by migration to chapter blacklists and colored bookmarks. Retain compatibility with existing data, not duplicate permanent interfaces by default.
- **Old ScreenModel implementation:** many screens moved to lifecycle-aware ViewModels. This changes implementation, not the feature set of the screens.
- **Old automatic-scroll state/help dialogs:** replaced by the newer reader automation system. Restore actual user-facing functionality, not obsolete internal state.
- **Old macrobenchmark/profile generation module:** replaced by the baseline-profile module and profile packaging flow.
- **Old telemetry module:** removed from the pinned fork tree; don't reintroduce it as a required dependency merely by copying upstream modules.
- **Experimental hidden-duplicates screen:** a historical implementation was reverted. It is not equivalent to Smart Scanlator Merge and should not be silently added as a confirmed current capability.
- **Legacy image decoder paths:** superseded by the newer decoder/progressive-rendering architecture.
- **Some deprecated dependency/build routes:** the fork deliberately adapted or blocked incompatible upgrades. The destination's existing compatible setup takes precedence over blindly reapplying older dependency declarations.

---

## 26. Functional acceptance scenarios for an integration

These checks express expected behavior without requiring a particular source-code structure.

### Categories

1. Create `Shounen / Reading` and `Josei / Reading`. Add a manga only to `Shounen`; when it starts reading, Smart Categories must not add it to the unrelated `Josei / Reading` branch.
2. Create two differently parented children named `Reading`; assigning, renaming, moving, filtering, or deleting one must not modify the other.
3. Reorder categories rapidly, close and reopen the manager, and verify the same hierarchy/order remains.
4. Back up and restore nested categories, membership, update intervals, and tracker-status policies.

### Blacklist and Smart Merge

5. Blacklist an anniversary extra and a comeback extra with numbers in their names; verify normal chapter 1 and chapter 6 remain visible.
6. Change scanlator priorities or a per-manga Smart Merge override; check manga list, library unread count, reader next chapter, and History all select the same visible release.
7. Open History for a hidden duplicate and continue reading; it should resolve to an available visible chapter without rewriting the original history record.
8. Force a source chapter refresh and verify bookmark colors, blacklist matching, and read flags remain associated with the right chapter.

### Tracking and metadata

9. Link one title to multiple trackers. Edit status, score, and dates once; confirm each service receives appropriately mapped values, and partial failure is reported.
10. Read a chapter older than the currently saved remote progress; tracker progress must not decrease.
11. Test a tracker record with an incomplete date; the UI and sync must not corrupt it.
12. Verify MangaBaka, Hikka, and other enabled trackers can be searched/bound and that their linked-service identity survives restart/backup.

### Reader

13. Change bookmark color in the manga chapter list and then in the reader; both surfaces should reflect the saved color promptly.
14. Enable auto-scroll/auto-flip; confirm manual stop, configured limit, screen-sleep/background stop, and final-chapter stop conditions.
15. Use WebGPU continuous reading, fit modes, resize/zoom, and animated images; verify navigation, backgrounds, and texture cleanup remain stable.
16. Tap the reader title and the intended back control; verify both use the desired prior-screen navigation path.

### Feeds/search/extensions

17. Save a filtered source search as a feed. Reopen it, refresh that feed only, then refresh all feeds; results and original filters should be retained.
18. Start a slow Global Search and immediately enter another query; older results must not overwrite the newer search.
19. Toggle an extension repository and refresh its list; verify installed/private/Shizuku extensions still update through the correct installer.

### Data and performance

20. Restore a legacy backup with missing newer fields and confirm it completes without discarding supported data.
21. Create two different-source manga with the same display title; ensure backups and SyncYomi never swap their data.
22. Change one manga's blacklist/merge settings in a large library; only affected visible counts should need recomputation.
23. Navigate between Library, History, Updates, Browse, and Downloads; screen data should refresh on return without remaining unnecessarily subscribed off-screen.
24. Run unit tests, compilation, debug/release builds, and migration checks appropriate to the integration. Do not mark a batch successful if a command's exit status was not actually established.

---

## 27. Final integration rules

- **Use behavior as the contract.** The document intentionally does not dictate class names, source paths, cherry-pick order, or exact commits.
- **Do not omit small changes.** Interface details, settings defaults, loading/empty states, and corrections listed above are part of the desired feature parity.
- **Do not resurrect reverted experiments.** Historical-only material requires confirmation before being classified as an active feature.
- **Preserve dependencies together.** A feature is not complete if only its UI appears while storage, background workers, database migration, tracking, backup, and sync support are missing.
- **Respect Komi-kun's existing features.** Keep its current architecture and implementations where possible; reproduce missing fork behaviors in the compatible form.
- **Never solve conflicts by deleting functional capabilities.** The desired result is the union of supported behavior, not a source-file overwrite that happens to build.
- **Treat existing users' data as a compatibility requirement.** Older local databases and backups must migrate to the new combined application without silent data loss.
- **Test observable behavior.** Passing compilation alone is insufficient; exercise the distinct category, blacklist, Smart Merge, tracking, reader, feed, restore, and performance regression cases.

**Evidence limitation:** The document consolidates everything identifiable from the two supplied audits, including their readable current-tree feature catalogues and meaningful historical subjects. It intentionally excludes raw file-by-file diffs, code references, and the 1,218-row commit log. Because some historical messages are vague or describe reverted work, no documentation-only rewrite can prove an additional undisclosed feature from those messages. Such cases are explicitly marked for verification above rather than invented.
