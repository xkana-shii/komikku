# Komikku Fork — Fork-Specific Features and Changes (Mihon/TachiyomiSY Imports Excluded)

**Project:** xkana-shii/komikku
**Purpose:** Detailed specification of fork-specific behavior to retain or rebuild **on top of the existing Komi-kun integration, without removing or regressing any behavior already brought over by Komi-kun commits**. Identifiable Mihon/TachiyomiSY cherry-picks are omitted only from the list of work to import.
**Basis:** The two supplied audits, `2features.md` and `features(2).md`, comparing the fork's `main` with official Komikku as of **19 October 2026**.

## How to read this document

This is **not a changelog, source-code map, or list of Git commits**. **Scope:** Mihon and TachiyomiSY features and fixes traced to cherry-picks are deliberately excluded, even when they are present in the fork. Behaviors developed separately in the fork are retained, including later fork-specific modifications in otherwise imported areas. Unclear origins are not automatically treated as imported. It is a behavior-oriented account of everything the audits identify: features, options, interface changes, internal improvements that affect behavior, bug fixes, migrations, and removals. Similar changes are consolidated, **but distinct behaviors are not intentionally discarded**. Identified imported optimizations are omitted; separately developed fork-specific improvements remain described by their behavior.

The source audits examined **730 differing tracked paths** and included **1,218 historical fork-only commits**; many of those commits were direct Mihon/TachiyomiSY imports. Neither number equals the number of live features. Many commits were merge operations, fixes, refactors, reversions, imported cherry-picks, or experiments; many files belong to the same feature. This document therefore distinguishes:

- **Documented current behavior:** the audits traced corresponding functionality in the pinned fork tree, sometimes with dedicated tests. This does **not** imply live-device or remote-service testing.
- **Documented correction or optimization:** a behavior, reliability, or efficiency change evidenced by the inspected tree or associated validation reports.
- **Historical / verify before porting:** mentioned in the commit history, but the audits did not establish that the complete behavior survives in the final tree.
- **Removed / superseded:** an older implementation or option was removed, replaced, or explicitly reverted. It must not automatically be restored as a separate feature.

This document covers **fork-specific differences with identified Mihon/TachiyomiSY cherry-picks omitted from the import requirements**. It does not repeat every normal feature shared unchanged with official Komikku. **Nothing omitted from this document is authorized to be removed from Komi-kun.** The destination is the **actual current local integration branch**, including all Komi-kun commits already brought over and any subsequent local fixes, not the older original Komikku fork or an earlier recorded integration HEAD. **Add only missing fork-specific behavior while preserving the full set of existing Komi-kun behaviors.**

---

## Mandatory preservation contract — Komi-kun integration

**Non-negotiable:** The Komi-kun features and behavioral changes already integrated into the target branch are **existing functionality**, not disposable scaffolding. This document is an **additive parity checklist for the fork**, never instructions to rebuild Komi-kun from scratch, roll back imported commits, or make the target match an older fork snapshot by subtracting features.

- **Start from the real integration state.** Inspect the actual current local branch, HEAD, index, and working tree before changing anything. The Komi-kun integration may include later commits, conflict resolutions, and validation fixes after the previously documented integration range. Treat **all of those changes**, not merely the original Komi-kun source tip or an old checkpoint, as the starting baseline.
- **Preserve every Komi-kun-origin behavior already brought over.** Keep existing features, interactions, settings, defaults, state transitions, persistence semantics, navigation, network behavior, reader and chapter handling, extension/source compatibility, tracking, downloads, backup/restore, synchronization, performance safeguards, and previous bug fixes. Do not restrict preservation to items explicitly named in this fork-focused document.
- **Do not reverse or drop previous integration work.** Do not reset the integration branch to an older Komikku or Komi-kun revision; revert, overwrite, or silently bypass existing integrated functionality; or replace entire modules/files with older fork versions merely because they implement the same feature name. Do not discard uncommitted local work.
- **Cherry-pick exclusion is not a removal instruction.** Excluding a Mihon/TachiyomiSY-origin feature here means **do not independently re-port it from this fork**. If it already exists in Komi-kun or in the current integration, **keep it as-is**, including its required supporting changes and fixes. The same rule applies to features that are not listed because they were unchanged in official Komikku.
- **Build on existing implementations.** Before importing any requested behavior, determine whether the current integration already provides it. If fully present, retain it without duplicating it. If partially present, add only the missing behavior. If its semantics differ, reconcile them deliberately without dropping existing use cases, settings, capabilities, or data compatibility.
- **Feature completeness includes all layers.** Preserve the existing implementation's user interaction, background work, state management, business rules, storage, migrations, backup/restore, synchronization, source/tracker integrations, and error handling. A newer screen that looks correct is not a replacement if it loses behavior behind the screen.
- **Merges must preserve both sides.** When a fork requirement and existing Komi-kun behavior touch the same area, implement a compatible union wherever feasible. Do not resolve conflicts by keeping the fork version wholesale and deleting Komi-kun handling, or by retaining Komi-kun while silently omitting a required fork enhancement. If requirements are genuinely incompatible, record the conflict and seek an explicit choice instead of unilaterally removing either behavior.
- **Do not equate absence from this document with absence from the application.** The detailed sections describe what to add from this fork. They are not an exhaustive catalogue of Komi-kun's features and must not be used as a deletion whitelist.
- **Protect migrations and existing data.** Retain all schema/data compatibility required by the current integration, including Komi-kun's added persistence models, when adding category, bookmark, blacklist, reader, feed, tracker, or sync changes. Existing user data must not be invalidated to make an older implementation fit.
- **Regression-test the current integration, not just the imported feature.** Before modifying each subsystem, identify its existing Komi-kun behavior and tests; after modification, verify both those established behaviors and the new fork requirements. Passing a build alone is not proof of preservation.

### Required decision process for each feature below

1. **Already present and correct in the current integration:** leave its existing implementation and behavior intact; do not duplicate or overwrite it.
2. **Present, but missing a fork-specific detail:** extend the existing implementation and test both old and newly added behavior.
3. **Not present:** add the behavior using the current integration's architecture and compatible data model.
4. **Potentially conflicting:** compare real user-visible behavior and dependencies; preserve the Komi-kun behavior while designing a compatible addition. Never silently treat a conflict as permission to remove an existing feature.
5. **Excluded as a Mihon/TachiyomiSY import:** do not use this file to request another import, and **do not remove, disable, downgrade, or refactor away** the implementation already present in Komi-kun.

**Meaning of “removed,” “replaced,” or “superseded” elsewhere in this file:** these words describe the fork's historical evolution. They **do not** authorize deleting an unrelated or more capable feature currently present in the Komi-kun integration. A retired fork implementation can remain retired while its equivalent or successor in Komi-kun stays fully functional.

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

### 1.3 Assigning categories to manga

- **Hierarchy-aware Set Categories dialog.** Choosing a nested category must recognize the appropriate parent and must not select a different child with the same name under another parent.
- **Correct bulk assignment.** Multi-manga category edits must retain all requested parent-child relationships and must not lose pre-existing unrelated memberships.
- **Category updates after edits.** Library tabs, selection chips, active tab, displayed counts, and membership-dependent filters should react to a category addition, deletion, rename, movement, or merge.
- **Category selection while searching/filtering.** Searching or filtering the library should not arbitrarily jump to an invalid category tab; preserve or resolve the active selection sensibly.
- **Category filtering correctness.** Include/exclude categories must continue to work when the tree changes, when there are no results, and when children share names.

### 1.4 Smart Categories

- **Tracker-status-driven categories.** A category may be associated with a tracker status so manga can automatically enter the appropriate reading-status category as tracker state changes.
- **Why branch scoping is necessary.** Matching a Smart Category by tracker status alone used to send a manga into *every* category bound to that status. If `Shounen → Reading` and `Josei → Reading` were both bound to *Reading*, a manga filed only under Shounen could be assigned to both. Belonging to one branch is not permission to alter a different branch.
- **Parent-branch-scoped Smart Categories.** A **subcategory** with a tracker-status binding is eligible for synchronization only when its **parent** is either a category the manga already belongs to or an **ancestor** of a category the manga already belongs to. Status matches must not override this eligibility rule.
- **Reachability, not direct parent membership.** The eligibility check must walk upward through the manga's existing category ancestry. A manga filed *only* in `Shounen → Reading` is still considered within the `Shounen` branch, so an update to *Completed* may move it to `Shounen → Completed`, even if `Shounen` is not explicitly included in its saved category list.
- **Out-of-scope branches are completely untouched.** An ineligible Smart Category must **not be added, removed, cleared, or otherwise changed** by the status synchronization. This matters even when a manga already has a category in an unrelated branch: the rule must preserve that category instead of treating an ineligible status mapping as a reason to remove it.
- **Multiple relevant parents.** If a manga belongs to **both** Shounen and Josei branches, their independently configured Smart Categories may both update. The algorithm must not select only the first same-status subcategory and must not merge two similarly named children across branches.
- **Top-level Smart Categories are deliberately global.** A status-bound category **without a parent** is not branch-scoped. Its rule remains eligible regardless of existing category membership, because no parent branch exists by which to limit it. Do not accidentally disable an existing top-level binding by imposing a parent-membership requirement on it.
- **Support deeper nesting.** Apply the ancestry/reachability rule transitively across **multiple levels**, not just the immediate parent. Moving between nested status categories must operate within an eligible branch while leaving unrelated parent and sibling branches alone. Deeply nested arrangements deserve dedicated regression tests because ancestor traversal is central to the rule.
- **Hierarchy-safe status changes.** Starting, completing, resetting, or changing a manga's tracker status should update the appropriate status-bound categories **inside each eligible branch** without moving the manga into an unintended branch or disturbing out-of-scope memberships.
- **Tracker/category synchronization.** Category updates must run after usable tracker state is obtained and persisted. A failed tracker operation should not silently apply an invented status.
- **Category status policy configuration.** Users can assign or change the tracker status associated with a category, and that policy must be reflected in automation without defeating the parent-scope rules.

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
- **Description and alternative metadata formatting.** Maintain fork-specific source description presentation and formatting choices instead of flattening richer metadata. Do not import unrelated Mihon Markdown renderer optimizations.
- **Avoid repeat preparation of custom metadata.** Reuse the fork's prepared rich description and local metadata where possible instead of repeatedly reconstructing it during UI recomposition.
- **Metadata updates.** When a source or tracker supplies changed details, update the intended metadata while respecting local customizations and identity.
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

- **Legacy marker preservation.** Existing Fillermark information is migrated to the fork's chapter blacklist and colored bookmark semantics under its migration policy.
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

### 7.1 MangaBaka tracking and fork-specific additions

- **MangaBaka tracking developed in this fork.** Preserve the fork's own MangaBaka integration and later specialized behavior, including search, bindings, progress, status, date and score conversion. This fork had MangaBaka work in December 2025, preceding Mihon's later MangaBaka support; do not treat the whole service as a cherry-pick.
- **MangaBaka metadata integration.** Use bound MangaBaka entries as a source for supported manga metadata and related-series data.
- **MangaBaka date fidelity.** Preserve dates supplied by the service rather than accidentally replacing valid values during conversions.

### 7.2 Fork-specific tracker interoperability

- **AniList relationship integration.** Use a supported AniList binding for the fork's additional sequel/prequel presentation, independently of generic tracker changes.
- **MangaUpdates custom integration.** Preserve fork-specific MangaUpdates handling and unified-tracker binding behavior. The audits do not establish live login reliability.
- **Remote error presentation in the fork's unified editor.** Show meaningful failures for rejected updates or expired credentials without pretending all linked services succeeded.

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
- **Reader chapter navigation.** Navigating through chapters honors Smart Merge and chapter blacklist; do not include unrelated Mihon chapter-opening fixes as a separate port requirement.
- **Title/back navigation parity.** Tapping the manga title in the reader follows the intended back-navigation path rather than returning to a different destination from the back/arrow control.
- **Bookmark icon in the reader.** Expose bookmark selection and color changing in the reader without removing standard bookmarking behavior.
- **Rounded bookmark touch feedback.** Keep the intended circle-shaped ripple/press target and correct icon placement.
- **Reader chapter-list bookmark updates.** Color changes become visible promptly in reader chapter navigation.
- **Page jumping correctness.** Page-jump controls should land on the intended page and preserve usable toolbar/reader state.

### 9.2 Automatic scroll and page flip

- **Button-controlled auto-scroll.** The newer automation system starts/stops automatic scrolling from reader controls; old removed implementations should not be copied back wholesale.
- **Automatic page flip.** Support automatic advancement in relevant paged modes.
- **Automation for Webtoon.** Provide continuous-reading automation adapted to vertical mode behavior.
- **Speed/rate selection.** User-facing speeds should be understandable, with usable increments rather than awkward values.
- **Device refresh-rate support.** Account for variable display refresh rates where applicable.
- **Duration limit.** Allow automation to stop after the configured amount of time where supported.
- **Stop at the end.** Automation must not continue attempting to advance beyond the final usable chapter.
- **Stop when sleeping or backgrounded.** Avoid continuing automation if the device sleeps or the reader is no longer active.
- **No disabled background worker.** Do not start unnecessary fork-owned automation work when the feature is turned off.
- **Keep screen awake during automation.** Prevent unintended screen sleep while the active reader automation requires the display.
- **Saved automation settings.** Retain relevant speed, duration, and behavior preferences.

### 9.3 Webtoon fit and scrolling

- **Optional pinch-to-zoom disable.** The historical changes include a Webtoon preference to prevent pinch zoom.
- **Webtoon scale-type controls.** Historical reader work introduces smart/adaptive fit and other scale choices for long-strip pages.
- **Gap preference for long strips.** Historical settings allow adjusting the gap between images, subject to scale-mode compatibility.
- **Responsive scaling on wider/narrower screens.** Re-evaluate image size after layout or window changes and avoid invalid calculations.
- **Lifecycle-safe layout listeners.** Remove or release view listeners appropriately to avoid leaks and repeated scaling work.

**Status note:** Some earlier fork-specific Webtoon scaling options were reverted and reapplied. Confirm which final controls survive. The imported Mihon resized-window gap correction is deliberately excluded.

### 9.4 Reader downloads, images, and memory

- **Download from the reader.** Historical work adds/reworks downloading from reader chapter actions; final UI parity should be checked because one earlier implementation was reverted.
- **Download status in reader lists.** Surface chapter download failures/loading state with appropriate retry affordances where present.
- **Bound image request concurrency when fork-owned.** Retain the fork's separate source/host throttling where it predates or differs from imported Mihon reader/image ports.
- **Source-aware throttling.** Control transfer concurrency per source/host rather than applying a single excessive global burst.
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

### 10.2 Global Search

- **Search selected source categories.** Limit a Global Search to the sources belonging to a chosen source category, instead of always searching every installed or enabled source.
- **Persistent search-source scope.** Preserve relevant selection/category settings where the fork already saves them.
- **Source category controls.** Allow the user to choose the intended category of sources and understand which sources will be searched.
- **Search result grouping.** Keep results associated with their originating sources and avoid confusing stale and current queries.
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

## 12. Extensions and repository management

### 12.1 Fork-specific extension management

- **Additional extension repository customization.** Retain the fork's repository configuration and source/extension icon customization.
- **Extension store management.** Manage extension stores/repositories and update available lists when their enabled state changes.
- **Refresh after repository changes.** Enabling or disabling a repository refreshes the extension list so changes appear without restarting the app.
- **Extension details and source preferences.** Preserve the ability to inspect an installed extension and configure its sources.
- **Extension/language/source filters.** Present and save the intended filters without unnecessary background observation when the screen is inactive.


---

## 13. History, reading continuation, and read state

### 13.1 History screen

- **Daily distinct-manga count.** Each date heading displays the number of *different manga* represented that day. Three chapters of the same manga on one date count as one manga, not three.
- **Date-grouped History.** History entries remain grouped by their reading dates, including the associated distinct-title count for each group.
- **Initial loading separated from empty state.** The app knows the difference between `History has not loaded yet` and `History contains no matching entries`, avoiding misleading empty messaging.
- **Visibility-aware History filters.** When identifying unfinished titles or remaining chapters, consider the chapters actually visible after blacklist and Smart Merge rules.

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

## 14. Updates, update jobs, and release prediction

### 14.1 Updates screen

- **Accurate read and download indicators.** Reflect chapter-visible state and keep updates consistent after read/download changes.
- **Correct refresh/error states.** Report update failures without confusing the entire screen's empty and loading states.

### 14.2 Release interval prediction

- **Smart release interval prediction.** The fork contains work to improve estimates of when manga chapters will appear.
- **Per-title predictions.** Predictions should correspond to each manga's release history rather than blindly using the same interval for all titles.
- **Refresh after new chapter evidence.** Update estimates when the underlying release/chapter history changes.
- **Do not imply guaranteed dates.** These are predictions, not source-confirmed future releases. The exact algorithm and display surface are not fully established by the supplied audits.

### 14.3 Library updater

- **Category-specific scheduling.** Honor configured category update intervals/scopes.
- **Controlled parallel update slots.** Keep the ability to limit concurrent source/library update work where available.
- **Update timestamp after completion.** Record the appropriate final update time when work finishes instead of prematurely marking it complete.
- **Cancellation.** Respect a user-initiated cancellation of library update work.
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
- **Cancellation safety.** User/system cancellation must not leave the app falsely claiming a complete restore.
- **Accurate worker lifecycle.** Backup/restore jobs should not disappear from the UI unexpectedly or get detached from their completion state.

### 15.3 Migrations between feature versions

- **Database migration safety.** Apply migrations without losing parent IDs, bookmark colors, blacklist state, track bindings, custom metadata, or feed associations.
- **Legacy Fillermark migration.** Convert supported old marker state to the modern visibility/bookmark model as intended.
- **Local manga and merged manga safety.** Data cleanup or migration must not delete the wrong manga folder or merge identities incorrectly.
- **No unnecessary source migration exclusions.** Separate visibility rules from source migration matching so hidden chapters do not make valid replacement sources disappear incorrectly.

---

## 16. SyncYomi and cross-device synchronization

### 16.1 Fork-specific synchronization requirements

- **Sync bookmark colors and visibility state.** Preserve the fork's colored bookmarks, blacklist, scanlator/merge preferences, and custom chapter flags as supported. This requirement concerns the fork-specific fields, not the upstream SY sync engine.
- **Preserve fork-specific flags through refresh.** Source refresh must not discard existing colored bookmark and blacklist state for matching chapters. This does not request the imported SY sync convergence implementation.

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
- **No accidental repeated actions.** Multiple fork-specific unified-tracker writes, category events, or UI recompositions should not duplicate the same automation operation.

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

- **Download queue state.** Queue, progress, errors, and removal are reflected correctly in the Downloads screen.
- **Fork-specific source throttling.** Preserve source/host request pressure limits only to the extent they originate in the fork's own downloader/source integrations, not Mihon's imported decoder/renderer changes.
- **Temporary-file cleanup.** Canceling/removing a download should clean up appropriate temporary files without treating a removed temporary file as a new download error.
- **Filtered chapter consistency.** Download-related counts and actions respect visible chapters/merged sources where appropriate.

---

## 20. Themes, settings, and interface behavior

### 20.1 Appearance and themes

- **Cover-derived colors.** Supported screens can use colors derived from a manga cover in accordance with appearance preferences.
- **Custom cover appearance.** Configure cover proportions, badge/icon display, and associated layout styling.
- **Language icon options.** Choose whether language flags/icons replace or accompany textual source indicators.
- **Badge coloring.** Preserve fork-specific coloring for library progress/status badges.
- **Iconography.** Keep appropriate application, source, and migration icons without restoring an obsolete visual style over a newer target implementation.

### 20.2 Settings and dialogs

- **Expanded setting screens.** Preserve settings for categories, update intervals, trackers, reader automation, downloads, appearance, connections/webhooks, and advanced behavior.
- **Adaptive sheets.** Updated bottom-sheet drag/settling behavior should remain functional and dismiss appropriately.
- **Generic selection controls.** Settings pickers work for the appropriate choice types without breaking existing preference values.
- **Readable loading indicators.** Loading screens retain meaningful progress percentages/feedback when available.
- **Correct pluralization.** Automated-speed, duration, count, and related messages use number-appropriate wording.
- **Updated notifications and error messaging.** Use clear warnings and actions without flooding the UI with redundant messages.
- **Navigation/back-stack corrections.** Returning from a dialog, manga, or reader should land on the intended previous screen.

---

## 21. Performance, database, and application architecture

Only the fork-specific performance work remains here. Imported Mihon/TachiyomiSY database drivers, ViewModel refactors, baseline profiles and associated tests are outside this document. No new measured battery, FPS, or startup benchmarks are claimed.

### 21.1 Library caching and query efficiency

- **Cached category grouping.** Reuse grouped manga/category views when their inputs are unchanged.
- **Fork-specific item reuse.** Reuse prepared manga items during category navigation and targeted download/visibility updates. This does not include the separate Mihon/SY badge-extraction cherry-pick.
- **Cached chapter visibility.** Store/precompute effective visible-chapter counts for expensive filtering paths.
- **Selective cache invalidation.** A bookmark, smart-merge, blacklist, category, or chapter change invalidates only affected data wherever safe.
- **One relevant preference subscription instead of one per manga.** Avoid creating thousands of independent preference listeners for large libraries.
- **Batched visible-chapter queries.** Calculate counts for groups of manga in one operation rather than issuing a separate query for each entry.
- **Avoid unnecessary result recomposition.** Reuse equal parent/child library views and targeted fork-specific item state instead of rebuilding everything during category navigation.
- **Efficient tracker filters.** Avoid redundant per-manga tracker work and unnecessary passes when grouping by tracker status.
- **Efficient category filters.** Query/filter using intended category membership semantics without repeated mapping of the same tree.
- **Throttle invalidations after write bursts.** Rapid read/progress/category updates should not trigger one full library rebuild per individual database write.
- **Aggregate only relevant manga.** Avoid calculating chapter statistics for the entire database when the visible/query result set is much smaller.

### 21.2 Fork-specific state and UI efficiency

- **Avoid side effects during UI-state reduction.** Keep writes/network operations out of operations that merely recompute display state.
- **Debounce expensive search updates.** Input remains responsive while the app performs background queries.

### 21.3 Fork-specific network and image efficiency

- **Share suitable network resources.** Reuse connections for fork-owned source operations where the implementation is separate from upstream Mihon network imports.
- **Request freshness controls.** Avoid returning stale source data when a forced refresh is requested, without clearing unrelated caches.
- **Fork-specific source-image throttling.** Keep any separately implemented host/source limiter in the fork; do not re-port Mihon's decoder/renderer infrastructure.
- **Prevent fork-owned listener/cache leaks.** Release the fork's reader and category UI observers when their views are disposed. Imported Mihon renderer fixes are out of scope.

---

## 22. Build, release, and developer tooling changes

- **Automated version bump workflow.** A dedicated manual/CI flow updates version information, runs applicable formatting checks, and can create the expected tag.
- **Correct version rollover.** The fork's bump rules include moving to the next minor version after patch `.10`, rather than continuing incorrectly through `.11`, `.12`, etc. as if the intended rollover never happened.
- **Version declaration safeguards.** Fail clearly when version information is missing or ambiguous rather than rewriting arbitrary values.
- **Stable and preview build adjustments.** Fork-specific GitHub release/preview workflow behavior and publishing paths are maintained.
- **Fork release links and branding.** Release/README links point to the fork's channels instead of misleadingly linking to upstream artifacts.
- **Formatting and quality checks.** Spotless/static checks and targeted unit tests are part of the fork's maintenance workflow.
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

### 23.3 Tracking

- **Incorrect status on starting tracking:** Initial link does not automatically choose an unintended tracker status.
- **Malformed progress:** Ignore negative/nonfinite chapter numbers.
- **Accidental progress rollback:** Reading older chapters does not reduce remote progress.
- **MangaBaka date conversion:** Remote valid dates survive read/write conversions.
- **Tracker operation ordering:** Concurrent writes do not overwrite each other unpredictably.
- **Tracker login failures:** Fail visibly and preserve other linked services when a token is invalid.

### 23.4 Feeds, search, and extensions

- **Refresh-all only refreshing errors:** Global feed refresh must cover the intended feeds, not just failures.
- **Saved-search feed mismatch:** Valid saved source searches are not rejected due to lost associations or filter state.
- **Feed overwrite collision:** Updating one saved feed does not corrupt a different one.
- **Stale source request results:** New refreshes do not become permanently stuck on the previous cached response.
- **Unresponsive Browse search:** Typing remains responsive during search requests.
- **Global Search stale responses:** Old parallel results cannot replace a more recent query's results.

### 23.5 Backup, sync, and databases

- **Manga identity collision in backup:** Same-titled entries retain their distinct source/URL identities.
- **Legacy backup missing fields:** Restore accepts supported old files using sensible defaults.
- **Metadata-after-commit atomicity:** External metadata does not get written for a transaction that failed.
- **Migration compatibility:** Schema migration logic remains compatible with supported historical backup/database states.

### 23.6 Smaller UI/network corrections mentioned in history

- **Discord login/account and no-token failures:** Historical fixes avoid inappropriate crashes and invalid login display.
- **Merged-entry folder cleanup:** Deleting a merged manga's download folder chooses the right folders.
- **Temporary download errors:** Cancelling/deleting temporary files is not falsely shown as a new download failure.
- **Crash-log verbosity:** Dump generation respects the verbose-logging preference.
- **Settings and navigation:** Various screen/dialog presentation and back-stack corrections are part of the fork's changes, although generic historical commit messages cannot establish additional specific requirements.

---

## 24. Historical fork-specific changes requiring verification

These historical changes are not all demonstrated in the pinned final tree. Investigate only when independent fork origin can be established; do not reinstate a Mihon/TachiyomiSY cherry-pick under a new name.

1. **Discord Rich Presence:** Historical account management, foreground notification, restart/stop, avatar and cover, error and token-handling improvements. Current complete user experience is unverified.
2. **Adaptive Webtoon scale choices:** Earlier options for fit, gaps and pinch settings passed through reverts and reapplications; do not equate their history with the final setting set. The specifically imported Mihon resized-window gap fix is excluded.
3. **Duplicate-entry management experiments:** A hidden-duplicates UI and automatic matching thresholds were developed but later reverted. They must not be recreated as if currently present.
4. **Source and language grouping options:** Historical advanced source search, nested display toggles, and category presentations may need final-tree confirmation.
5. **Download actions from the reader:** Several earlier designs were reverted or reworked. Preserve only any current fork-specific behavior established in the final tree.
6. **Auto Reread service-specific counters:** The workflow exists, but support and exact counters vary by tracking service.
7. **Later recovery-branch additions:** Work absent from the pinned `main` is not silently treated as already merged.

---

## 25. Fork-specific removed or replaced behavior

**Interpretation warning:** The items in this section describe historical replacements **inside the fork**. They are **not instructions to uninstall or disable** any corresponding features already provided by Komi-kun or brought over during its integration. Preserve the integrated behavior first, then migrate or adapt fork-specific data and interactions only where needed.

- **Fillermark UI:** Legacy Fillermarks are migrated into the fork's chapter blacklist/colored-bookmark model rather than reinstated as a separate permanent fork feature. **Do not delete any existing Komi-kun markers, annotations, or equivalent behavior** without first ensuring that their user-visible function and existing data survive the combined implementation.
- **Old SY-style auto-scroll UI:** The fork's later reader automation replaces obsolete legacy controls. The imported WebGPU renderer and its adaptation are not requirements to **re-port** in this filtered specification. **Preserve any existing Komi-kun reader, WebGPU, automation, and related controls**, and add only missing fork-specific behavior.
- **Experimental duplicate-management screen:** This particular fork experiment was reverted and is not equivalent to Smart Scanlator Merge. This does **not** authorize removing a different duplicate-management capability already present in Komi-kun.
- **Fork-specific migrations:** Preserve user data and relevant backward compatibility when replacing local behavior, without importing unrelated Mihon/SY database/decoder infrastructure.

---

## 26. Functional acceptance scenarios for the remaining fork-specific features

These are observable behavior requirements, not directions to reapply upstream cherry-picks. **All scenarios are additive:** they must pass **alongside** the existing Komi-kun functionality, not at its expense.

### Categories

1. Create `Shounen / Reading` and `Josei / Reading`. Add manga to Shounen only; a tracker status change must not add it to Josei.
2. Add a manga to `Shounen / Reading` without separately saving Shounen membership. A status switch to Completed must reach `Shounen / Completed` via its ancestor, without disturbing Josei.
3. File manga in both Shounen and Josei; each scoped status rule may update its own branch.
4. Give a category with no parent a status binding; confirm it still works globally.
5. Create two differently parented children named Reading. Assigning, renaming, moving, filtering, deleting and merging one must not alter the other.
6. Reorder categories rapidly; reopen the manager and verify the hierarchy/order remains.
7. Backup and restore nested categories, smart-category policy and update intervals.

### Blacklist, bookmarks, and Smart Merge

8. Blacklist anniversary/comeback extras whose labels include 1 or 6. Confirm ordinary chapter 1 and 6 remain visible.
9. Change a manga's scanlator priorities or Smart Merge override; confirm chapter list, reader, library counts and History agree on the visible release.
10. Continue from a hidden chapter in History; the correct next visible chapter opens without rewriting the stored history record.
11. Refresh source chapters; colors, blacklist identity, read flags, and merge decisions stay associated with the intended chapters.
12. Change chapter bookmark colors in the manga screen and reader; changes propagate immediately, including the reader chapter navigator.

### Unified tracking and metadata

13. Link one title to multiple services. Change status, score and dates once; preserve service-appropriate values and report partial failure without erasing successful writes.
14. Read a chapter older than currently tracked progress; progress does not decrease.
15. Verify the fork's MangaBaka metadata, dates, search, related entries and unified bindings survive restart/backup.
16. Confirm tracker-status category movement occurs only after a usable persisted status change.

### Reader and automation

17. Start and stop the fork's own auto-scroll and auto-flip; verify speed, configured duration, sleep/background and end-of-content handling on supported non-WebGPU viewers.
18. Reader title and back navigation produce the intended destination; reader bookmark icons retain correct color and rounded touch feedback.
19. Test confirmed fork-specific Webtoon fit/gap controls only where they still exist, distinguishing them from separately imported Mihon fixes.

### Feeds, browsing and organization

20. Save a filtered source search as a feed; reopen, retry it, refresh every feed, and verify the saved filters/associations remain intact.
21. Source-category Global Search only searches eligible sources, and disabled sources do not fetch in the background.
22. Type into Browse source search during a long request; input remains usable, with correct source and filter context.
23. Backup/restore feed and saved-search relationships, including source and filter identity.

### Storage, sync extensions and performance

24. Backup/restore fork-specific custom metadata, category hierarchy, colored bookmarks, blacklist and scanlator rules without mixing same-titled manga.
25. Confirm SyncYomi can preserve newly added fork fields when supported; do not make a separate SY engine port a requirement.
26. On a large library, changing one Smart Merge override or blacklist preference need not recompute unrelated category and visibility results.
27. Verify release prediction and category-specific update policies with representative chapter histories and nested category membership.
28. Run appropriate unit tests, migrations, debug/release builds and direct behavior checks. Do not count passing compilation as proof that a feature works.

---

## 27. Final integration rules

**Priority:** Preserve the current Komi-kun integration and the behavior of all commits it already contains. Use this document to fill gaps from the fork, not to decide which existing Komi-kun features may be deleted.

- **Use behavior as the contract.** The document intentionally does not dictate class names, source paths, cherry-pick order, or exact commits.
- **Do not omit fork-specific small changes.** Interface details, settings defaults, loading/empty states and corrections remaining above are part of the desired parity. Imported upstream fixes were deliberately removed.
- **Do not resurrect reverted experiments.** Historical-only material requires confirmation before being classified as an active feature.
- **Preserve fork-owned dependencies together.** A feature is not complete if only its UI appears while storage, background workers, migration, tracking, backup and sync support are missing; keep the destination's existing Mihon/SY-equivalent infrastructure.
- **Respect all existing Komi-kun features, especially previously imported commits.** Preserve the actual current local integration branch, all subsequent compatibility fixes, and every behavior those commits introduced. Keep its current architecture and implementations unless an explicitly behavior-preserving adaptation is necessary. Do not re-port Mihon/TachiyomiSY functionality just because the fork once imported it; **also never remove it if Komi-kun already contains it**. Implement only missing fork-owned behavior.
- **Never solve conflicts by deleting functional capabilities.** The desired result is the union of supported Komi-kun behavior **including imported integration commits** plus missing fork-specific behavior, not a source-file overwrite that happens to build.
- **Treat existing users' data as a compatibility requirement.** Older local databases and backups must migrate to the new combined application without silent data loss.
- **Test observable behavior.** Passing compilation alone is insufficient; exercise the distinct category, blacklist, Smart Merge, tracking, reader, feed, restore, and performance regression cases.
- **Audit the baseline before editing.** Inventory existing Komi-kun-specific feature paths and user flows in every touched subsystem; check for lost behavior and lost settings after each integration step.
- **Keep new and old regression checks together.** Re-run any tests carried by the Komi-kun integration in addition to the fork-specific cases here. Test combined workflows (such as reader/navigation, chapter filtering, tracking, background updates, and backup/restore), not just isolated additions.
- **No blind whole-file replacement.** Cherry-picked features may have introduced logic to files also changed by this fork. Merge at the behavior level, carrying forward both sides' code paths and supported configuration where necessary.

**Evidence limitation:** This filtered inventory is based on the supplied audits and inspected upstream-origin records. Only identifiable Mihon/TachiyomiSY cherry-picks and their direct effects have been excluded; ambiguous mixed-origin items are retained or qualified instead of deleted on a guess. It does not claim complete line-by-line Git provenance for every historical commit. See the accompanying exclusion audit.
