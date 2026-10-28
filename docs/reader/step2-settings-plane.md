# Step 2 (phase A): the reader settings plane

Commit ported: `8124d324a1` — "Replace reader config listeners with settings view model state".
Base: `9bd2f451b7` on `feat/reader-compose`.

This is **phase A**: it builds the destination (`ViewerSettings`, `PagedSettings`, `SimplePagedSettings`,
`LongStripSettings`, `SimpleLongStripSettings`, `WebGpuSettings`, `WebGpuPagedSettings`,
`WebGpuLongStripSettings`, `GeneralSettings`, the extended `ReaderSettingsViewModel`, the
`ContextExtensions` change) with every fork knob already carried. **No consumer is migrated**:
`PagerViewer`, `WebtoonViewer`, `WebGpuViewer`, the page holders, `ReaderActivity`, the presentation
dialogs and `ReadingMode.toViewer()` still use `ViewerConfig` / `PagerConfig` / `WebtoonConfig` /
`WebGpuConfig`, so nothing in the plane is read by production code yet. Phase B (the real step 2) moves
the consumers and deletes those four config classes.

Paths below are relative to `app/src/main/java/eu/kanade/tachiyomi/ui/reader/` unless they start with
`app/`.

## 1. Fork knob dispositions

"Old location" is the field/accessor as the fork has it today. "Home" is the new settings file and line,
"stays" means the knob keeps its existing `ReaderPreferences` accessor because its consumer is outside
the viewer settings plane (loader, activity, image view) and step 2 does not touch it, "deferred" names
the later step that owns it.

### 1.1 Reader-only preferences (the inventory's §3.6 list, 28 rows)

| R<n> | Knob | Old location | Home | Note |
|---|---|---|---|---|
| R37 | `pageTransitionsPager` | `setting/ReaderPreferences.kt:30` | `setting/GeneralSettings.kt:23` (`usePageTransitions`) | upstream's single `usePageTransitions`; fed from the pager toggle |
| R37 | `pageTransitionsWebtoon` | `setting/ReaderPreferences.kt:32` | `setting/GeneralSettings.kt:35` (`usePageTransitionsWebtoon`) | the second half of the fork's per-mode split |
| R38 | `readerThreads` | `setting/ReaderPreferences.kt:219` | stays (`loader/HttpPageLoader.kt:45`) | loader knob, no viewer reads it |
| R41 | `readerInstantRetry` | `setting/ReaderPreferences.kt:221` | stays (`loader/HttpPageLoader.kt:136`) | loader knob |
| R40 | `aggressivePageLoading` | `setting/ReaderPreferences.kt:223` | stays (`loader/HttpPageLoader.kt:220`) | loader knob |
| R42 | `cacheSize` | `setting/ReaderPreferences.kt:225` | stays (`mihon/data/cache/ChapterCache.kt:58`) | cache knob |
| R3 | `autoscrollInterval` | `setting/ReaderPreferences.kt:227` | `setting/GeneralSettings.kt:37` | float seconds, `-1` = off |
| R6 | `smoothAutoScroll` | `setting/ReaderPreferences.kt:229` | `setting/GeneralSettings.kt:39` | smooth vs stepped |
| R5 | `autoscrollGesturesEnabled` | `setting/ReaderPreferences.kt:231` | `setting/GeneralSettings.kt:41` | the gestures themselves stay on `WebtoonRecyclerView` |
| R7 | `autoscrollGestureToastsEnabled` | `setting/ReaderPreferences.kt:234` | `setting/GeneralSettings.kt:43` | help/toast plumbing stays in the activity |
| R46 | `preserveReadingPosition` | `setting/ReaderPreferences.kt:237` | stays (`loader/ChapterLoader.kt:75`) | loader knob |
| R39 | `preloadSize` | `setting/ReaderPreferences.kt:239` | stays (`loader/HttpPageLoader.kt:56`) | loader knob |
| R45 | `useAutoWebtoon` | `setting/ReaderPreferences.kt:241` | stays (`ReaderViewModel.kt:1164`) | resolved in `getMangaReadingMode()`, needs the source name |
| R36 | `continuousVerticalTappingByPage` | `setting/ReaderPreferences.kt:243` | stays (dead) | no consumer at HEAD; still undecided (wire it or drop it) |
| R50 | `cropBordersContinuousVertical` | `setting/ReaderPreferences.kt:245` | `viewer/LongStripSettings.kt:15` (`continuousCropBorders`) | the bottom-bar crop toggle edits the pref |
| R44 | `readerBottomButtons` | `setting/ReaderPreferences.kt:247` | stays (`ReaderActivity.kt:678`) | bottom-bar content, an activity concern |
| R13 | `pageLayout` | `setting/ReaderPreferences.kt:249` | `viewer/pager/SimplePagedSettings.kt:19` (+ `:21` `doublePages`, `:23` `autoDoublePages`) | typed as the new `ReaderPreferences.PageLayout` enum (`setting/ReaderPreferences.kt:387`) |
| R14 | `invertDoublePages` | `setting/ReaderPreferences.kt:251` | `viewer/pager/SimplePagedSettings.kt:25` | `false` while wide pages are split, as before |
| R15 | `centerMarginType` | `setting/ReaderPreferences.kt:253` | `viewer/PagedSettings.kt:34` | typed as the new `ReaderPreferences.CenterMarginType` enum (`:402`) |
| R43 | `archiveReaderMode` | `setting/ReaderPreferences.kt:255` | stays (`loader/ArchivePageLoader.kt:67`) | loader knob |
| R31 | `smallerTapZone` | `setting/ReaderPreferences.kt:212` | `viewer/ViewerSettings.kt:26` (`NavigationSettings.smallerTapZone`) | geometry itself still lives in `ViewerNavigation` |
| R24 | `webtoonScaleType` | `setting/ReaderPreferences.kt:96` | `viewer/webtoon/SimpleLongStripSettings.kt:21` | |
| R25 | `longStripGapSmartScale` | `setting/ReaderPreferences.kt:97` | `viewer/webtoon/SimpleLongStripSettings.kt:23` | |
| R23 | `alwaysDecodeLongStripWithSSIV` | `setting/ReaderPreferences.kt:104` | `viewer/LongStripSettings.kt:17` | the image view may also keep reading the pref directly |
| R29 | `landscapeZoomType` | `setting/ReaderPreferences.kt:123` | `viewer/PagedSettings.kt:32` | |
| R27 | `pagedDisableZoomIn` | `setting/ReaderPreferences.kt:143` | `viewer/PagedSettings.kt:28` | |
| R28 | `pagedDoubleTapZoomEnabled` | `setting/ReaderPreferences.kt:88` | `viewer/PagedSettings.kt:30` | |
| R26 | `webtoonPinchToZoomEnabled` | `setting/ReaderPreferences.kt:90` | `viewer/webtoon/SimpleLongStripSettings.kt:19` | |

### 1.2 Fork-only fields and behaviours of the four retired config classes

| R<n> | Knob | Old location | Home | Note |
|---|---|---|---|---|
| R16 | `pageCanvasColor` | `viewer/pager/PagerConfig.kt:81` | `setting/GeneralSettings.kt:55` | derived from `theme`, like `themeToColor` was; keeps the old `0x202125` gray (no alpha) rather than `Color.rgb`'s `0xFF202125` |
| R18 | `pageLayout` → `dualPageView` seed | `viewer/webgpu/WebGpuConfig.kt:88`-`:98`, helper `:228`-`:240` | `setting/ReaderSettingsViewModel.kt:50`-`:53`, helper `:383` | one-shot, guarded by `dualPageView.isSet()`; survives the deletion of `WebGpuConfig` |
| R13 | `doublePages` | `viewer/pager/PagerConfig.kt:67` | `viewer/pager/SimplePagedSettings.kt:21` | reintroduces the fork's custom setter clearing `shiftDoublePage` only as data |
| R13 | `autoDoublePages` | `viewer/pager/PagerConfig.kt:78` | `viewer/pager/SimplePagedSettings.kt:23` | |
| R37 | `usePageTransitions` (pager) | `viewer/pager/PagerConfig.kt:63` | `setting/GeneralSettings.kt:23` | |
| R37 | `usePageTransitions` (webtoon) | `viewer/webtoon/WebtoonConfig.kt:56` | `setting/GeneralSettings.kt:35` | |
| R13/R16 | `reloadChapterListener` | `viewer/pager/PagerConfig.kt:34` | no field needed | the trigger (`theme` or the paged layout changing) is expressible from `general.theme` + `simplePaged`; phase B listens with `collectChanges` |
| R10 | `shiftDoublePage` | `viewer/pager/PagerConfig.kt:65` | **deferred → step 4** | runtime pairing parity computed by `reloadChapters`/`updateShifting`, not a preference; the step-4 engine owns it (`ReaderActivity.kt:996` and `:1177` set it today) |
| R32 | vertical pan before page turn | `viewer/pager/PagerViewer.kt:395`, `:409` | **deferred → step 5** | not a config field: it is an action in `ViewerNavigation`, which `9079ad8c9a` collapses to `MENU/FORWARD/BACKWARD` |
| R19/R20 | cover-seeded theme, seed in chrome | `ReaderActivity.kt:316`, `PagerViewer.kt:36`, `WebtoonViewer.kt:49` | **deferred → step 6** | theming, not a settings field; the seed is a parameter of the Compose reader root |
| R30 | `zoomWideImagesAllowedList` | `setting/ReaderPreferences.kt:427` | derived, no field | still a function of `PagedSettings.Page.imageScaleType` (`viewer/PagedSettings.kt:22`) and `landscapeZoomScaleType` (`:32`) |

Counts: 28 reader knobs (18 homed in the plane, 10 stay in `ReaderPreferences`) + 11 config-class
entries (6 homed, 1 needs no field, 3 deferred, 1 derived) — see §2 for the field-level accounting and
§4 for the deferral list.

## 2. What each retired config class' fields became

Totals: `ViewerConfig` 16 + `PagerConfig` 18 + `WebtoonConfig` 13 + `WebGpuConfig` 21 = **68** fields.
The inventory's §3 also registers 19 *rendered* knobs in `WebGpuViewerSettings.kt` (§3.5) that are not
config properties, which is where "87" comes from (68 + 19); they are untouched by this phase and are
not counted below.

### 2.1 `viewer/ViewerConfig.kt` (16)

| Field | Home |
|---|---|
| `imagePropertyChangedListener` | dropped — replaced by `collectChanges` on the settings flow (`viewer/ViewerSettings.kt:67`) |
| `navigationModeChangedListener` | dropped — `NavigationSettings` (`viewer/ViewerSettings.kt:21`) |
| `tappingInverted` | `NavigationSettings.inverted` (`:23`) |
| `longTapEnabled` | `GeneralSettings.kt:22` |
| `doubleTapAnimDuration` | `GeneralSettings.kt:24` |
| `volumeKeysEnabled` | `GeneralSettings.kt:25` |
| `volumeKeysInverted` | `GeneralSettings.kt:26` |
| `alwaysShowChapterTransition` | `GeneralSettings.kt:27` |
| `navigationMode` | `NavigationSettings.mode` (`ViewerSettings.kt:22`) |
| `forceNavigationOverlay` | `ReaderSettingsViewModel.takeNavigationOverlayForNewUser()` (`:344`) |
| `navigationOverlayOnStart` | `GeneralSettings.kt:28` |
| `dualPageSplit` | `SimplePagedSettings.kt:13` and `SimpleLongStripSettings.kt:32` |
| `dualPageInvert` | `SimplePagedSettings.kt:14` and `SimpleLongStripSettings.kt:33` |
| `dualPageRotateToFit` | `SimplePagedSettings.kt:15` and `SimpleLongStripSettings.kt:34` |
| `dualPageRotateToFitInvert` | `SimplePagedSettings.kt:16` and `SimpleLongStripSettings.kt:35` |
| `navigator` | `NavigationSettings.navigator(default)` (`ViewerSettings.kt:36`) |

16/16 accounted for (13 fields, 2 listeners, 1 navigator).

### 2.2 `viewer/pager/PagerConfig.kt` (18)

| Field | Home |
|---|---|
| `automaticBackground` | `GeneralSettings.automaticBackground` (`:46`, derived from `theme`) |
| `dualPageSplitChangedListener` | dropped — `collectChanges` on `simplePaged` |
| `reloadChapterListener` | dropped — trigger expressible from `general.theme` + `simplePaged` |
| `imageScaleType` | `PagedSettings.kt:22` |
| `imageZoomType` | `PagedSettings.kt:23` + `ZoomStartType.position()` (`ViewerSettings.kt:54`) |
| `imageCropBorders` | `PagedSettings.kt:24` |
| `navigateToPan` | `PagedSettings.kt:14` |
| `landscapeZoom` | `PagedSettings.kt:25` |
| `disableZoomIn` | `PagedSettings.kt:28` |
| `doubleTapZoom` | `PagedSettings.kt:30` |
| `landscapeZoomScaleType` | `PagedSettings.kt:32` |
| `usePageTransitions` | `GeneralSettings.kt:23` |
| `shiftDoublePage` | **deferred, step 4** (§4) |
| `doublePages` | `SimplePagedSettings.kt:21` |
| `invertDoublePages` | `SimplePagedSettings.kt:25` |
| `autoDoublePages` | `SimplePagedSettings.kt:23` |
| `pageCanvasColor` | `GeneralSettings.kt:55` (derived) |
| `centerMarginType` | `PagedSettings.kt:34` |

18/18 accounted for (17 homed, 1 deferred).

### 2.3 `viewer/webtoon/WebtoonConfig.kt` (13)

| Field | Home |
|---|---|
| `themeChangedListener` | dropped — `collectChanges` on `general.theme` |
| `imageCropBorders` | `LongStripSettings.kt:11` |
| `zoomOutDisabled` | `LongStripSettings.kt:12` |
| `zoomPropertyChangedListener` | dropped |
| `sidePadding` | `SimpleLongStripSettings.kt:31` |
| `doubleTapZoom` | `SimpleLongStripSettings.kt:15` |
| `doubleTapZoomChangedListener` | dropped |
| `pinchToZoom` | `SimpleLongStripSettings.kt:19` |
| `pinchToZoomChangedListener` | dropped |
| `webtoonScaleType` | `SimpleLongStripSettings.kt:21` |
| `webtoonScaleTypeChangedListener` | dropped |
| `usePageTransitions` | `GeneralSettings.kt:35` |
| `continuousCropBorders` | `LongStripSettings.kt:15` |

13/13 accounted for (8 fields, 5 listeners).

### 2.4 `viewer/webgpu/WebGpuConfig.kt` (21)

| Field | Home |
|---|---|
| `theme` | `GeneralSettings.kt:16` |
| `automaticBackground` | `GeneralSettings.automaticBackground` (`:46`) |
| `dualPageSplitChangedListener` | dropped |
| `imageScaleType` | `PagedSettings.kt:22` |
| `imageZoomType` | `PagedSettings.kt:23` + `position()` |
| `imageCropBorders` | `PagedSettings.kt:24` |
| `imageCropBordersWebtoon` | `LongStripSettings.kt:11` |
| `navigateToPan` | `PagedSettings.kt:14` |
| `landscapeZoom` | `PagedSettings.kt:25` |
| `transitionAnimation` | `viewer/webgpu/WebGpuPagedSettings.kt:22` |
| `transitionAnimationDual` | `WebGpuPagedSettings.kt:23` |
| `cutoutMode` | `WebGpuPagedSettings.kt:24` |
| `cutoutModeDual` | `WebGpuPagedSettings.kt:25` |
| `dualPageView` | `WebGpuPagedSettings.kt:21` (seeding kept, §1.2) |
| `continuousMinWidth` | `viewer/webgpu/WebGpuLongStripSettings.kt:19` |
| `zoomOutDisabled` | `LongStripSettings.kt:12` |
| `continuousGap` | `WebGpuLongStripSettings.kt:20` |
| `imageFilters` | `viewer/webgpu/WebGpuSettings.kt:14` |
| `imageFiltersChangedListener` | dropped |
| `tileFilters` | `WebGpuSettings.kt:15` |
| `tileFiltersChangedListener` | dropped |

21/21 accounted for. As upstream does, the WebGPU viewers share the paged/long-strip image settings
(scale type, zoom start, crop, landscape zoom) instead of keeping their own copies, so `WebGpuSettings`
itself needs no crop field — crop is `PagedSettings.Page.cropBorders` / `LongStripSettings.cropBorders`.
`WebGpuSettings` also needs no fork field: the only fork addition to `WebGpuConfig` was the R18 seed.

## 3. Compromises where upstream's shape could not hold a fork field

1. **`GeneralSettings.usePageTransitions` is one value, the fork has two.** Upstream has a single
   `pageTransitions` preference; the fork has `pageTransitionsPager` and `pageTransitionsWebtoon`.
   `usePageTransitions` (`GeneralSettings.kt:23`) keeps upstream's name and carries the pager value;
   the webtoon value is the extra `usePageTransitionsWebtoon` (`:35`). A viewer must read the field for
   its own mode — reading the generic one from a long-strip viewer gives the pager's answer.
2. **`NavigationSettings` cannot build the fork's navigators from its data alone.** Upstream's
   `navigator(default)` constructs `LNavigation()`, `KindlishNavigation()`, … with no arguments; this
   fork's navigator classes still take `ReaderPreferences` (the tap-zone size is read inside them,
   `viewer/ViewerNavigation.kt:60`). `NavigationSettings` therefore carries both the `smallerTapZone`
   value (`ViewerSettings.kt:26`) and a temporary `readerPreferences` reference (`:32`) that exists only
   to construct them; step 3 (`00335b2a70`) replaces the navigator constructor with
   `(invertMode, isReversed)` and both go away.
3. **`pageCanvasColor` is derived, not stored.** It has no preference of its own (it follows the reader
   theme), so `GeneralSettings` derives it (`:55`) instead of carrying a field. Being a derived property
   it is not part of the data class equality — a theme change still re-emits `general`, but code
   comparing two `GeneralSettings` for `pageCanvasColor` must compare `theme`.

Two smaller ones worth knowing: `reloadChapterListener` survives only as an expressible trigger rather
than a field (its two call sites in `PagerConfig` are "theme or page layout changed → reload the
chapter"), and `NavigationSettings.readerPreferences` makes the data class' `equals` depend on the
preferences instance — always the same singleton, so it is stable.

## 4. Deferrals

| R<n> | What | Deferred to | Why |
|---|---|---|---|
| R10 | `shiftDoublePage` (and `pageToShift` parity) | step 4 | Not a preference: it is pairing parity the activity computes on chapter load (`ReaderActivity.kt:996`) and the adapter consumes; it belongs to the dual-page engine step 4 rebuilds |
| R32 | Vertical pan before page turn | step 5 | Not a field at all — an action in `ViewerNavigation`, which `9079ad8c9a` rewrites; adding it now would mean changing `ViewerNavigation` in phase A |
| R19, R20 | Cover-seeded reader theme, seed in viewer chrome | step 6 | Theming of the Compose reader root and of the page/transition composables, not a settings value |
| R36 | `continuousVerticalTappingByPage` (dead knob) | undecided | No consumer at HEAD; kept in `ReaderPreferences`, exposed in settings — wire or drop it in step 3/5 |

Everything else is homed: nothing else from the four config classes or from `ReaderPreferences` is
unaccounted for.

## 5. Verification

Run from the worktree root, in this order:

```
./gradlew spotlessApply
./gradlew spotlessCheck
./gradlew :app:compileDebugKotlin
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

All five pass on this change. The plane is unreferenced by production code in this phase, so no unit
test covers it: the values are a direct translation of the preferences, and the behavioural checks
(dual page parity, autoscroll, zoom cluster) belong to the steps that wire them.
