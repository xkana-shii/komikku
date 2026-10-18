# Komi-kun Recovery Audit

## References

- Original Komikku baseline: `be7cb155fb8356acc9f2c01932ee41550d3354ab`
- Destructive integration merge: `b4f59800db830873b728306b58719863f54a22fc`
- Komi-kun integration parent: `be0699c571de8d4309d356b532a6813ef4da08eb`
- Recovery safety reference: `backup-recovery-start-20261018-1602`

This branch starts at the original Komikku baseline. No files from the destructive
merge are accepted wholesale. Each later change must be evaluated against this
baseline before it is ported.

## Initial merge comparison

`be7cb155..b4f59800` changed 1,479 paths: 305 added, 132 deleted, 961 modified,
and 81 renamed. The following findings are based on the actual merge diff.

| Area | Classification | Finding | Recovery handling |
|---|---|---|---|
| Category management | Deleted/overwritten | Parent picker, tracker-status picker, update-interval picker, hierarchy controls, and category UI logic were removed or substantially replaced. | Retained from the baseline; later ports must preserve nested categories, smart categories, backup/restore, and per-category settings. |
| Tracking | Deleted/overwritten | `TrackerBatch`, `UpdateTracks`, and `SyncCategoryTrackStatus` were deleted; several existing tracker interactors were replaced. | Retained from the baseline; evaluate Komi-kun tracker changes as small ports only. |
| Bookmark colors | Partially overwritten | Bookmark UI composition changed in the merge. | Baseline implementation is authoritative; verify chapter, reader, filter, persistence, and backup behavior before any import. |
| Library | Overwritten | `LibraryViewModel`, tab/settings/item behavior, and library SQL views were substantially rewritten. | Retain baseline query and cache behavior first; assess only isolated Komi-kun improvements later. |
| Database | Requires migration audit | The merge rewrote core SQL queries/views and introduced migration `57.sqm`. | Do not import schema changes without a forward-compatible upgrade plan from both baseline and already-integrated installs. |
| Houri features | Requires behavioral audit | Merge touches chapter, history, feed, metadata, and UI layers that interact with blacklist, scanlator merge, bookmarks, feeds, and relations. | Baseline is retained; audit with regression tests before accepting any overlapping port. |
| Komi-kun additions | Pending | SyncYomi v2, Metro migration, and selected Mihon backlog were bundled into the integration parent. | Break into independent dependencies and port only after compatibility review. |

## Post-merge work

The current main branch contains later changes after the destructive merge. They are
preserved by `backup-recovery-start-20261018-1602` and will be classified before
being replayed on this branch. No post-merge commit is assumed compatible merely
because it builds.

## Next audit groups

1. Category hierarchy, smart categories, and category backup/restore.
2. Unified tracking, tracker batches, preferred trackers, and MangaBaka behavior.
3. Bookmark colors, blacklist identity matching, and Smart Scanlator Merge.
4. Library SQL/cache/query behavior and large-library performance.
5. Feeds, saved searches, global search, history, relations, and reader behavior.
6. Database schema and migration compatibility for baseline and already-integrated installs.

## Required pre-integration fixes

These fixes were completed on the recovered baseline before any Komi-kun integration.

### Chapter blacklist identity

- Baseline source: `b82417882d` introduced URL/hybrid identity and conservative legacy
  number/scanlator matching.
- Reference follow-up: `a124437ece` added the version-two hybrid key and complete-snapshot
  plumbing.
- The collision was caused by Mangago announcement chapters sharing a parsed number and empty
  scanlator with normal chapters. The new `hybrid:v2` key stores normalized chapter name in
  addition to number, scanlator, and URL. URL remains authoritative; refreshed URLs use the name
  discriminator, and ambiguous legacy matches remain visible.
- `filterBlacklistedChapters` accepts an authoritative snapshot so filtered subsets cannot turn a
  legacy collision into a unique match. Smart merge and chapter-list visibility use the same
  matcher.
- Regression coverage includes both Mangago extras, simultaneous blacklisting, URL refreshes,
  v1 compatibility, ambiguous legacy keys, filtered subsets, and Smart Scanlator Merge.

### Hierarchical Set Categories

- Baseline source: `ce1064ba13` retained parent-scoped category identity.
- Reference follow-up: `a124437ece` supplied the hierarchy-aware dialog helpers.
- `ChangeCategoryDialog` now displays each parent followed by its children with indentation and
  saves selected child IDs together with their ancestor IDs. Parent selection does not select
  children; IDs and `parentId` are used throughout, so same-named children remain independent.
- Regression coverage verifies ordering, indentation-related ordering helpers, ancestor inclusion,
  and parent-only selection.

### Parent-aware Smart Categories

- Reference follow-up: `a124437ece` changed `SyncCategoriesWithTrackStatus` from global status
  assignment to active parent-branch assignment.
- Synchronization now derives active roots from current parent or child memberships, preserves
  manual memberships, updates all active branches, and retains legacy standalone root bindings.
  Child-only membership implies and restores its parent. Empty membership does not create nested
  branches, and repeated synchronization is idempotent.
- Regression coverage verifies single and multiple parents, status changes, child-only recovery,
  unrelated/manual categories, root compatibility, and idempotence.

Validation for this milestone: focused domain and app unit tests, `spotlessApply`,
`spotlessCheck`, `:app:compileDebugKotlin`, `assembleDebug`, and `git diff --check` passed.
No schema files changed, so SQLDelight regeneration was not required. Commits:

- `e7c51bdcec` — `fix: prevent chapter blacklist collisions`
- `431657f5f5` — `feat: make set categories hierarchy-aware`
- `62933f19dc` — `fix: scope smart categories to parent branches`
