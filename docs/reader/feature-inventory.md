# Reader feature inventory (komi-kun)

This file inventories every reader capability this fork has on top of plain Mihon, so the five upstream
reader re-architectures (steps 1-6 below) can be ported without silently dropping any of them. Base commit
`7d4be1b84c` on `feat/reader-compose`. The `R<n>` IDs are stable: later commits cite them, so never
renumber, reorder or reuse an ID; add `R<n+1>` instead. Evidence was read directly from this worktree
(`rg`, file reads, `git diff 8124d324a1^ HEAD -- <path>`, `git show <sha>:<path>`); the codebase-memory
graph was deliberately not used because its index covers a different checkout.

## 2. Capability inventory

Provenance: KMK = Komikku upstream, SY = TachiyomiSY upstream, KUN = komi-kun's own code. "Risk" names the
step in section 5 that deletes or threatens the capability.

### Autoscroll

| ID | Capability (prov) | User-visible behaviour | Entry point -> consumers | Risk | Migration home |
|----|-------------------|------------------------|--------------------------|------|----------------|
| R1 | Webtoon autoscroll driver (SY) | Continuous viewers scroll themselves: `syncAutoScrollSettings` clamps the interval and arms/clears pause, `performAutoScrollStep` either glides a screen (`linearScroll`) or jumps a step (`scrollDown`), and skips while a gesture/menu is active. | `viewer/webtoon/WebtoonViewer.kt:292`, `:341`, `:358` -> `ReaderActivity.kt:831`, `:849` | 5 | `WebtoonViewer`, fed from settings/activity instead of prefs |
| R2 | Activity autoscroll loop (SY) | One loop combines `autoscrollInterval` with `state.autoScroll`; per viewer it calls `moveToNext()` (pager) or `performAutoScrollStep` (webtoon) inside `repeatOnLifecycle(STARTED)` and pauses while the menu is open. | `ReaderActivity.kt:810` (called `:248`) -> `ReaderViewModel.kt:1690` | 1, 5 | `ReaderActivity`; needs `Viewer.moveForward()` + an autoscroll hook |
| R3 | Autoscroll speed pref (SY) | Float seconds stored in `eh_util_autoscroll_interval`; `-1` means "off"; invalid or out-of-range text (`>9999`, `<=0`) flips `isAutoScrollEnabled` off. | `setting/ReaderPreferences.kt:227` -> `ReaderViewModel.kt:457`, `presentation/reader/appbars/ExhUtils.kt:124` | 2, 5 | `GeneralSettings` (or a new autoscroll settings object) |
| R4 | Autoscroll toggle + switch (SY) | Reader overlay has an "Autoscroll" row with a `Switch`; disabled with an error hint when the stored frequency is invalid. | `ReaderViewModel.kt:1317` -> `ExhUtils.kt:88`, `ReaderActivity.kt:753` | 1, 5 | activity state + `ReaderAppBars`/`ExhUtils` |
| R5 | Autoscroll gestures (SY) | Two-finger tap toggles pause, long-press starts, swipe sets speed, tap stops; toasts optional. **Already on base (`0efe2f094b`), do not re-add.** | `WebtoonRecyclerView.kt:70`-`:75`, `:107`, `:336` -> `WebtoonViewer.kt:184`-`:189` | 0 | keep as-is |
| R6 | Smooth vs stepped scroll (SY) | `smooth_auto_scroll` picks `linearScroll(interval)` over `scrollDown()`. | `ReaderPreferences.kt:229` -> `WebtoonViewer.kt:350`, `:462` | 2 | `GeneralSettings` |
| R7 | Autoscroll help dialogs (SY) | Three dialogs: autoscroll, gestures, gesture toasts. | `ReaderViewModel.kt:1297` -> `ReaderActivity.kt:479`-`:512` | 1 | activity dialogs |
| R8 | Autoscroll/menu mutual exclusion (SY) | Opening the menu stops the scroll and interrupts the inter-step delay (`consumeAutoScrollDelayInterrupt`). | `WebtoonViewer.kt:319`, `:366` -> `ReaderActivity.kt:992`, `:1028` | 1, 5 | activity |

### Dual page join / shift / isolate

| ID | Capability (prov) | User-visible behaviour | Entry point -> consumers | Risk | Migration home |
|----|-------------------|------------------------|--------------------------|------|----------------|
| R9 | Dual-page join engine (SY) | `setJoinedItems` runs six steps: segment pages per chapter id (a chapter boundary always breaks pairing), clear shifts, mark one `shiftedPage`, insert blank slots after full/shifted pages, isolate the neighbour of a full page, then `chunked(2)` into `joinedItems`. | `viewer/pager/PagerViewerAdapter.kt:254` -> `PagerViewer.kt:200`, `:329` | 2, 4 | new engine over the `PagerItems` type from step 4 |
| R10 | Shift page (SY) | "Shift page" moves the pairing parity by one from the current page (or the last full page before it); recomputed at chapter load so the first page lands on the correct side. | `ReaderActivity.kt:974`, `:1133` -> `PagerViewer.kt:507`, adapter `:298`-`:317` | 2, 4 | step 4 engine + a viewer-level `shiftDoublePage` |
| R11 | Isolated page (SY) | A full/wide page leaves its would-be partner alone (`isolatedPage = true`) instead of pairing it with the following page, and adds a blank slot at an even index. | `PagerViewerAdapter.kt:322`-`:341` -> `PagerPageHolder.kt:289` | 4 | step 4 engine |
| R12 | Wide-image split, rotate, merge (SY) | In dual mode a wide image is split in half; `dualPageRotateToFit` rotates a wide single page by +-90; two portrait pages are merged onto one bitmap with optional centre margins and canvas colour; animated/undecodable pages force a split (`fullPage`). | `PagerPageHolder.kt:244`, `:267`, `:277`, `:342`, `:400` -> `core/common/.../ImageUtil.kt:492` | 2, 6 | step 4 + the new paged page holder |
| R13 | `pageLayout` single/double/automatic (SY) | Three layouts; `AUTOMATIC` turns double pages on in landscape (`setDoublePageMode`) and is re-evaluated on orientation change. | `ReaderPreferences.kt:249` -> `PagerConfig.kt:67`, `ReaderActivity.kt:946`, `ReadingModePage.kt:115` | 2, 4 | map onto `WebGpuPagedSettings.Page.dualPageView` + a paged equivalent |
| R14 | Invert double pages (SY) | Swaps which side the first page of a pair sits on; also feeds page-number text. | `ReaderPreferences.kt:251` -> `ReaderViewModel.kt:1407`, `:1534`; `ReaderActivity.kt:1220` | 2, 4 | step 4 engine (paged side) |
| R15 | Centre margin for joined pages (SY) | None / double page / wide page / both, drawn as blank gutter when merging. | `ReaderPreferences.kt:253` -> `PagerPageHolder.kt:346`, `:371` | 2, 6 | step 4 merge code |
| R16 | Paged canvas colour (SY) | Pager page background follows the reader theme (white / black / `0x202125`), used as the merge gutter colour and page background. | `PagerConfig.kt:263` -> `PagerPageHolder.kt:207`, `:337` | 2, 6 | `GeneralSettings.theme` derived colour |
| R17 | Chapter-boundary pairing (SY) | Segmentation by `chapter.id` guarantees the first page of a new chapter is never paired with the last page of the previous one, and chapter transitions get their own slot. | `PagerViewerAdapter.kt:279`-`:295` | 4 | step 4 engine |
| R18 | WebGPU dual-page seeding (KUN) | First WebGPU use maps the SSIV/SY `pageLayout` + `dualPageSplitPaged` choice onto the newer `dualPageView` pref (once, only if unset), so enabling the high-quality renderer does not silently reset dual pages. | `viewer/webgpu/WebGpuConfig.kt:88`-`:98`, `:228`-`:240` | 2, 6 | a migration/seed step next to the new `WebGpuSettings` |

### Reader theming

| ID | Capability (prov) | User-visible behaviour | Entry point -> consumers | Risk | Migration home |
|----|-------------------|------------------------|--------------------------|------|----------------|
| R19 | Cover-seeded reader theme (KMK) | Reader sheet/chrome is themed from the manga cover's vibrant colour when `themeCoverBased` is on; cached map fallback for the pre-load frame. | `ReaderActivity.kt:316`, `:795`, `:801` -> `ReadingMode.kt:105`, `TachiyomiTheme.kt:59` | 1, 3, 6 | new Compose reader root (step 3) |
| R20 | Seed colour in viewer chrome (KMK) | Seed is threaded into the loading indicator, chapter transition and page holders so non-image surfaces match the themed chrome. | `PagerViewer.kt:36`, `:42`; `WebtoonViewer.kt:49`, `:90` -> `ReaderTransitionView.kt:87`, `ReaderProgressIndicator.kt:53`, `PagerPageHolder.kt:47` | 3, 6 | keep the seed parameter on the Compose reader/page composables |

### WebGPU viewers and settings

| ID | Capability (prov) | User-visible behaviour | Entry point -> consumers | Risk | Migration home |
|----|-------------------|------------------------|--------------------------|------|----------------|
| R21 | High-quality renderer capability gate (KUN) | Probes `WebGpuRenderer.isAvailable`; if the GPU bring-up failed the reader falls back to the SSIV viewer instead of a blank WebGPU surface, and logs the reason. | `setting/ReadingMode.kt:84`-`:125` | 3, 6 | `ReadingMode.toViewer` (or its Compose successor) |
| R22 | WebGPU settings surface (Mihon) | Crop, image/tile filters (import, reorder, delete), long-strip min width/gap, dual page view, cutout, transitions, zoom start. No fork delta: the file is byte-identical to `8124d324a1^`. | `viewer/webgpu/WebGpuViewerSettings.kt:65` | 6 | `WebGpuViewerSettings` (unchanged upstream file) |
| R23 | SSIV long-strip escape hatch (KMK) | Forces SubsamplingScaleImageView decoding for long-strip/webtoon pages instead of the mpreg decoder, for very tall strips that blank/OOM. | `ReaderPreferences.kt:104` -> `viewer/ReaderPageImageView.kt:67`, `:180`; `SettingsAdvancedScreen.kt:455` | 2, 6 | long-strip page composable / image loader |
| R24 | Webtoon scale type (KMK) | Fit / 4:3 / 3:2 / 16:9 / 20:9 width ratios; scales the recycler so a strip renders at the chosen aspect. | `ReaderPreferences.kt:96` -> `WebtoonViewer.kt:211`-`:235`; `ReadingModePage.kt:238` | 2, 6 | new long-strip settings field |
| R25 | Long-strip smart-scale gap (KMK) | Restricts `webtoonScaleType` changes to fit the "vertical with gaps" mode only. | `ReaderPreferences.kt:97` -> `WebtoonViewer.kt:212`; `ReadingModePage.kt:240` | 2 | same settings object |
| R26 | Webtoon pinch-to-zoom toggle (KMK) | Disables two-finger scaling in the strip while keeping double-tap zoom. | `ReaderPreferences.kt:90` -> `WebtoonConfig.kt:44`, `WebtoonFrame.kt:37`, `WebtoonRecyclerView.kt:63`, `:247` | 2, 6 | long-strip settings |
| R27 | Paged zoom-in lock (KMK) | Disables zooming into paged images entirely. | `ReaderPreferences.kt:143` -> `ReaderPageImageView.kt:323`; `PagerPageHolder.kt:225` | 2, 6 | zoom cluster in step 6 |
| R28 | Paged double-tap zoom toggle (KMK) | Turns double-tap zoom off for paged viewers (webtoon has its own upstream pref). | `ReaderPreferences.kt:88` -> `PagerConfig.kt:166`, `PagerPageHolder.kt:226` | 2, 6 | zoom cluster |
| R29 | Landscape zoom scale type (KMK) | `FIT` (fit screen) or `DOUBLE` (2x) target scale for the landscape-zoom gesture. | `ReaderPreferences.kt:123` -> `ReaderPageImageView.kt:147`; `PagerPageHolder.kt:227` | 2, 6 | zoom cluster |
| R30 | Landscape zoom restricted by scale type (KMK) | The landscape-zoom toggle is only offered (and honoured) for centre-inside/original-size scale types (`zoomWideImagesAllowedList`). | `ReadingModePage.kt:140` -> `ReaderPreferences.kt:427` | 2, 6 | zoom cluster |

### Navigation, gestures and tap zones

| ID | Capability (prov) | User-visible behaviour | Entry point -> consumers | Risk | Migration home |
|----|-------------------|------------------------|--------------------------|------|----------------|
| R31 | Smaller tap zones (KMK) | Shrinks the prev/next tap zones from 33% to 25% columns height/width, growing the menu region; applies to every tap-zone layout and to the navigation overlay drawing. | `ReaderPreferences.kt:212` -> `viewer/ViewerNavigation.kt:59`-`:63`; `PagerConfig.kt:130`; `WebtoonConfig.kt:79`; `ReadingModePage.kt:128` | 2, 3, 5 | region geometry from settings; `ViewerNavigation` must keep a size input |
| R32 | Vertical pan before page turn (KMK) | Up/Down/PageUp/PageDown (and volume keys) pan a zoomed page to its edge first and only then turn the page. | `viewer/pager/PagerViewer.kt:395`, `:409` -> `ReaderPageImageView.kt:207`-`:270` | 3, 5 | `ViewerNavigation` action set (needs a PAN action, not just FORWARD/BACKWARD) |
| R33 | Tap-zone scheme set (Mihon) | Default / L-shaped / kindlish / edge / right-and-left / disabled. Upstream, listed only so the port keeps all six. | `ReaderPreferences.kt:394` -> `PagerConfig.kt:237`, `WebtoonConfig.kt:153` | 3, 5 | `NavigationSettings.mode` |
| R34 | Volume/DPAD/page keys + Ctrl skip (Mihon) | Volume keys (invertible), DPAD, PageUp/Down, Ctrl+Left/Right chapter skip, Menu key. | `PagerViewer.kt:435` -> `WebtoonViewer.kt:512`, `WebGpuViewer.kt:1893` | 5 | `ReaderActivity.onKeyEvent` (`9079ad8c9a`) |
| R35 | Mouse-wheel paging (Mihon) | Scroll wheel turns pages instead of scrolling content. | `PagerViewer.kt:478` -> `WebGpuViewer.kt:1925` | 5 | `Modifier.mouseWheelPaging` |
| R36 | Continuous vertical tap-by-page pref (SY) | Pref exists and is exposed in settings, but nothing consumes it at HEAD (dead knob). | `ReaderPreferences.kt:243` -> `SettingsReaderScreen.kt:481` only | 2 | decide: wire it or drop it |

### Reader settings plane and reader-local actions

| ID | Capability (prov) | User-visible behaviour | Entry point -> consumers | Risk | Migration home |
|----|-------------------|------------------------|--------------------------|------|----------------|
| R37 | Per-mode page transitions (SY) | Separate transition toggles for pager and webtoon (upstream has one shared knob). | `ReaderPreferences.kt:30`, `:32` -> `PagerConfig.kt:63`, `WebtoonConfig.kt:56` | 2 | two fields, or keep one and accept the loss |
| R38 | Reader download threads (SY) | `eh_reader_threads` bounds concurrent page downloads via a semaphore (KUN wiring). | `ReaderPreferences.kt:219` -> `loader/HttpPageLoader.kt:45`-`:52`, `:284` | 2 | loader settings |
| R39 | Preload window (SY) | `eh_preload_size` limits how far ahead the rolling schedule reaches; combined with aggressive loading it covers the chapter. | `ReaderPreferences.kt:239` -> `HttpPageLoader.kt:56`, `:214`-`:250` | 2 | loader settings |
| R40 | Aggressive page loading (SY) | Loads every page index instead of only the window around the current page. | `ReaderPreferences.kt:223` -> `HttpPageLoader.kt:220` | 2 | loader settings |
| R41 | Instant retry (SY) | A failed page is reloaded immediately, bypassing the rolling chunk schedule. | `ReaderPreferences.kt:221` -> `HttpPageLoader.kt:136` | 2 | loader settings |
| R42 | Image cache size (SY) | `eh_cache_size` (MB string) sizes the chapter image cache. | `ReaderPreferences.kt:225` -> `mihon/data/cache/ChapterCache.kt:58` | 2 | reader/pref outside the config classes: low risk |
| R43 | Archive reader mode (SY) | Load from file / into memory / cache to disk, controlling zip page extraction and temp-dir cleanup. | `ReaderPreferences.kt:255` -> `loader/ArchivePageLoader.kt:66`-`:94` | 2 | loader settings |
| R44 | Configurable bottom-bar buttons (SY) | Which buttons appear in the reader bottom bar (chapters, web view, browser, share, reading mode, rotation, three crop toggles, page layout). | `ReaderPreferences.kt:247` -> `setting/ReaderBottomButton.kt:7`; `ReaderActivity.kt:678`; `ReaderBottomBar.kt:32` | 1, 3 | `ReaderAppBars` parameters |
| R45 | Auto webtoon mode (SY) | When the manga has no explicit reading mode and its source default is webtoon, open in webtoon and toast it. | `ReaderPreferences.kt:241` -> `ReaderViewModel.kt:1169`-`:1175`; `ReaderActivity.kt:1047` | 1 | `ReadingMode` resolution in the activity |
| R46 | Preserve reading position (SY) | Keeps the saved page for EH-based manga when chapters are updated/re-downloaded. | `ReaderPreferences.kt:237` -> `loader/ChapterLoader.kt:75`; `MangaViewModel.kt:571` | 2 | reader prefs; low risk |
| R47 | Double-page page number text (SY) | The navigator shows "N-M" when two pages are joined; also used for progress. | `ReaderViewModel.kt:785` -> `ReaderActivity.kt:1218`; `ChapterNavigator.kt:73` | 1, 3 | activity page text + Compose overlay |
| R48 | Chapter list dialog (SY) | Full chapter list with bookmark/read state from inside the reader, jumping between chapters. | `ReaderViewModel.kt:580` -> `ReaderActivity.kt:452`; `presentation/reader/ChapterListDialog.kt` | 1 | activity + `ChapterListDialog` |
| R49 | Bookmark toggle in reader (SY) | Bookmark/unbookmark the current chapter from the reader app bar. | `ReaderViewModel.kt:1149` -> `ReaderActivity.kt:461` | 1 | activity |
| R50 | Crop-border quick toggle (SY) | Bottom-bar crop button flips the paged/webtoon/continuous-vertical crop pref and toasts on/off. | `ReaderViewModel.kt:1242` -> `ReaderActivity.kt:745` | 1 | activity |
| R51 | Second-page actions (SY) | When a spread is joined, "share/save/copy/set as cover" can target the first or the second page; `hasExtraPage` changes the labels. | `ReaderViewModel.kt:1403`, `:1530` -> `ReaderActivity.kt:420`-`:450`; `ReaderPageActionsDialog.kt:37`-`:121` | 1, 4 | activity + page-actions dialog |
| R52 | Boost page (SY) | Re-requests the current page with the highest priority ("boost") on HTTP sources. | `ReaderActivity.kt:932` -> `loader/HttpPageLoader.kt:339` | 1 | activity/loader |
| R53 | Discord rich presence (KMK) | Publishes "reading manga X, chapter Y" while the reader is open and resets it on exit. | `ReaderActivity.kt:1557` -> `:554`, `:569`, `:1152` | 1 | activity; keep call sites |
| R54 | Open at a specific page (SY) | `page` intent extra restores an exact page (used by continue-reading/merge flows). | `ReaderViewModel.kt:196` -> `ReaderActivity.kt:158` | 1 | activity intent handling |
| R55 | Merged-source reading (KMK/SY) | Per-chapter manga mapping so chapters from different merged entries load their own source/title in one reader session. | `ReaderViewModel.kt:816`, `:579`-`:600` -> `loader/ChapterLoader.kt:112`-`:120` | 1 | `ReaderViewModel` merged-manga plumbing |

## 3. Config field register

"Upstream?" = the property exists in the Mihon version of the same class at `8124d324a1^`.
"Superseded?" = what `8124d324a1` replaces it with (`ViewerSettings.kt`, `PagedSettings.kt`,
`LongStripSettings.kt`, `GeneralSettings.kt`, `SimplePagedSettings.kt`, `SimpleLongStripSettings.kt`,
`WebGpu{Paged,LongStrip,}Settings.kt`, `ReaderSettingsViewModel.kt`).

### 3.1 `ui/reader/viewer/ViewerConfig.kt` (16 fields)

| Property | Type | file:line | Origin | Consumer | Upstream? | Superseded? |
|---|---|---|---|---|---|---|
| imagePropertyChangedListener | (() -> Unit)? | ViewerConfig.kt:15 | Mihon | PagerViewer.kt:161, WebtoonViewer.kt:193, WebGpuViewer.kt:800 | yes | yes - deleted; `collectChanges` (ViewerSettings.kt:56) |
| navigationModeChangedListener | (() -> Unit)? | ViewerConfig.kt:17 | Mihon | PagerViewer.kt:165, WebtoonViewer.kt:244, WebGpuViewer.kt:876 | yes | yes - deleted |
| tappingInverted | ReaderPreferences.TappingInvertMode | ViewerConfig.kt:19 | Mihon | PagerConfig.kt:123, WebtoonConfig.kt:73 | yes | yes - `NavigationSettings.inverted` |
| longTapEnabled | Boolean | ViewerConfig.kt:20 | Mihon | PagerViewer.kt:139, WebtoonViewer.kt:170 | yes | yes - `GeneralSettings.longTapEnabled` |
| doubleTapAnimDuration | Int | ViewerConfig.kt:21 | Mihon | PagerPageHolder.kt:219, WebtoonPageHolder.kt:206 | yes | yes - `GeneralSettings.doubleTapAnimDuration` |
| volumeKeysEnabled | Boolean | ViewerConfig.kt:22 | Mihon | PagerViewer.kt:441, WebtoonViewer.kt:516, WebGpuViewer.kt:1897 | yes | yes - `GeneralSettings.volumeKeysEnabled` |
| volumeKeysInverted | Boolean | ViewerConfig.kt:23 | Mihon | PagerViewer.kt:444 | yes | yes - `GeneralSettings.volumeKeysInverted` |
| alwaysShowChapterTransition | Boolean | ViewerConfig.kt:24 | Mihon | WebtoonViewer.kt:412, WebGpuViewer.kt:596 | yes | yes - `GeneralSettings.alwaysShowChapterTransition` |
| navigationMode | Int (protected set) | ViewerConfig.kt:25 | Mihon | PagerConfig.kt:237, WebtoonConfig.kt:153 | yes | yes - `NavigationSettings.mode` (`NavigationMode` enum) |
| forceNavigationOverlay | Boolean | ViewerConfig.kt:28 | Mihon | PagerViewer.kt:166, WebtoonViewer.kt:245 | yes | yes - `ReaderSettingsViewModel.takeNavigationOverlayForNewUser()` |
| navigationOverlayOnStart | Boolean | ViewerConfig.kt:30 | Mihon | PagerViewer.kt:166 | yes | yes - `GeneralSettings.navigationOverlayOnStart` |
| dualPageSplit | Boolean (protected set) | ViewerConfig.kt:32 | Mihon | PagerPageHolder.kt:200 | yes | yes - `SimplePagedSettings.dualPageSplit` |
| dualPageInvert | Boolean (protected set) | ViewerConfig.kt:35 | Mihon | WebtoonPageHolder.kt:231 | yes | yes - `SimplePagedSettings.dualPageInvert` |
| dualPageRotateToFit | Boolean (protected set) | ViewerConfig.kt:38 | Mihon | PagerPageHolder.kt:267 | yes | yes - `SimplePagedSettings.dualPageRotateToFit` |
| dualPageRotateToFitInvert | Boolean (protected set) | ViewerConfig.kt:41 | Mihon | PagerPageHolder.kt:270 | yes | yes - `SimplePagedSettings.dualPageRotateToFitInvert` |
| navigator | ViewerNavigation | ViewerConfig.kt:44 | Mihon | PagerViewer.kt:130, WebtoonViewer.kt:163, WebGpuViewer.kt:784 | yes | yes - `NavigationSettings.navigator()` |

Fork delta: the fork **removed** upstream's `usePageTransitions` (ViewerConfig.kt:21 upstream) and moved it
into `PagerConfig`/`WebtoonConfig` as two SY prefs (R37).

### 3.2 `ui/reader/viewer/pager/PagerConfig.kt` (18 fields)

| Property | Type | file:line | Origin | Consumer | Upstream? | Superseded? |
|---|---|---|---|---|---|---|
| automaticBackground | Boolean (private set) | PagerConfig.kt:29 | Mihon | PagerPageHolder.kt:207 | yes | yes - `GeneralSettings.automaticBackground` |
| dualPageSplitChangedListener | ((Boolean) -> Unit)? | PagerConfig.kt:32 | Mihon | PagerViewer.kt:151 | yes | yes - deleted (settings flow) |
| reloadChapterListener | ((Boolean) -> Unit)? | PagerConfig.kt:34 | SY | PagerViewer.kt:157 | no | **no slot** |
| imageScaleType | Int (private set) | PagerConfig.kt:36 | Mihon | PagerPageHolder.kt:220 | yes | yes - `PagedSettings.Page.imageScaleType` |
| imageZoomType | ReaderPageImageView.ZoomStartPosition | PagerConfig.kt:39 | Mihon | PagerPageHolder.kt:222 | yes | yes - `PagedSettings.Page.zoomStart` + `position()` |
| imageCropBorders | Boolean (private set) | PagerConfig.kt:42 | Mihon | PagerPageHolder.kt:221 | yes | yes - `PagedSettings.Page.cropBorders` |
| navigateToPan | Boolean (private set) | PagerConfig.kt:45 | Mihon | PagerViewer.kt:370, :384 | yes | yes - `PagedSettings.navigateToPan` |
| landscapeZoom | Boolean (private set) | PagerConfig.kt:48 | Mihon | ReaderPageImageView.kt:113 | yes | yes - `PagedSettings.Page.landscapeZoom` |
| disableZoomIn | Boolean (private set) | PagerConfig.kt:52 | KMK | ReaderPageImageView.kt:323, PagerPageHolder.kt:225 | no | **no slot** |
| doubleTapZoom | Boolean (private set) | PagerConfig.kt:55 | KMK | PagerPageHolder.kt:226 | no | **no slot** |
| landscapeZoomScaleType | ReaderPreferences.LandscapeZoomScaleType | PagerConfig.kt:58 | KMK | ReaderPageImageView.kt:147, PagerPageHolder.kt:227 | no | **no slot** |
| usePageTransitions | Boolean | PagerConfig.kt:63 | SY | PagerViewer.kt:373, :387 | no | partial - single `GeneralSettings.usePageTransitions` |
| shiftDoublePage | Boolean | PagerConfig.kt:65 | SY | ReaderActivity.kt:959, :977; adapter:298 | no | **no slot** (runtime state) |
| doublePages | Boolean (custom setter) | PagerConfig.kt:67 | SY | ReaderActivity.kt:781, :952; adapter:257 | no | **no slot** |
| invertDoublePages | Boolean | PagerConfig.kt:76 | SY | ReaderViewModel.kt:1407, ReaderActivity.kt:1220 | no | **no slot** |
| autoDoublePages | Boolean | PagerConfig.kt:78 | SY | ReaderActivity.kt:949 | no | **no slot** |
| pageCanvasColor | Int (@ColorInt) | PagerConfig.kt:81 | SY | PagerPageHolder.kt:207, :337; ReaderViewModel.kt:1408 | no | **no slot** (only `GeneralSettings.theme`) |
| centerMarginType | Int | PagerConfig.kt:83 | SY | PagerPageHolder.kt:346, :371 | no | **no slot** |

Fork delta: the fork **removed** upstream's `theme` (PagerConfig.kt:26 upstream); `automaticBackground`
remains and is derived from it.

### 3.3 `ui/reader/viewer/webtoon/WebtoonConfig.kt` (13 fields)

| Property | Type | file:line | Origin | Consumer | Upstream? | Superseded? |
|---|---|---|---|---|---|---|
| themeChangedListener | (() -> Unit)? | WebtoonConfig.kt:25 | Mihon | WebtoonViewer.kt:203 | yes | yes - deleted |
| imageCropBorders | Boolean (private set) | WebtoonConfig.kt:27 | Mihon | WebtoonPageHolder.kt:209 | yes | yes - `LongStripSettings.cropBorders` |
| zoomOutDisabled | Boolean (private set) | WebtoonConfig.kt:30 | Mihon | WebtoonViewer.kt:241, WebtoonRecyclerView.kt:38 | yes | yes - `LongStripSettings.zoomOutDisabled` |
| zoomPropertyChangedListener | ((Boolean) -> Unit)? | WebtoonConfig.kt:33 | Mihon | WebtoonViewer.kt:240 | yes | yes - deleted |
| sidePadding | Int (private set) | WebtoonConfig.kt:35 | Mihon | WebtoonViewer.kt:121, :194 | yes | yes - `SimpleLongStripSettings.Page.sidePadding` |
| doubleTapZoom | Boolean (private set) | WebtoonConfig.kt:38 | Mihon | WebtoonViewer.kt:202 | yes | yes - `SimpleLongStripSettings.doubleTapZoom` |
| doubleTapZoomChangedListener | ((Boolean) -> Unit)? | WebtoonConfig.kt:41 | Mihon | WebtoonViewer.kt:202 | yes | yes - deleted |
| pinchToZoom | Boolean (private set) | WebtoonConfig.kt:44 | KMK | WebtoonViewer.kt:207, WebtoonFrame.kt:37 | no | **no slot** |
| pinchToZoomChangedListener | ((Boolean) -> Unit)? | WebtoonConfig.kt:47 | KMK | WebtoonViewer.kt:207 | no | **no slot** |
| webtoonScaleType | ReaderPreferences.WebtoonScaleType | WebtoonConfig.kt:49 | KMK | WebtoonViewer.kt:211 | no | **no slot** |
| webtoonScaleTypeChangedListener | ((WebtoonScaleType) -> Unit)? | WebtoonConfig.kt:52 | KMK | WebtoonViewer.kt:211 | no | **no slot** |
| usePageTransitions | Boolean | WebtoonConfig.kt:56 | SY | WebtoonViewer.kt:455, :486, :500 | no | partial - `GeneralSettings.usePageTransitions` |
| continuousCropBorders | Boolean (private set) | WebtoonConfig.kt:58 | SY | WebtoonPageHolder.kt:210 | no | **no slot** |

Fork delta: the fork **removed** upstream's `val theme` (WebtoonConfig.kt:43 upstream).

### 3.4 `ui/reader/viewer/webgpu/WebGpuConfig.kt` (21 fields)

| Property | Type | file:line | Origin | Consumer | Upstream? | Superseded? |
|---|---|---|---|---|---|---|
| theme | Int (private set) | WebGpuConfig.kt:27 | Mihon | WebGpuViewer.kt:93 | yes | yes - `GeneralSettings.theme` |
| automaticBackground | Boolean (private set) | WebGpuConfig.kt:30 | Mihon | WebGpuViewer.kt:1249 | yes | yes - `GeneralSettings.automaticBackground` |
| dualPageSplitChangedListener | ((Boolean) -> Unit)? | WebGpuConfig.kt:33 | Mihon | none at HEAD (declared only; listened to by `PagerViewer.kt:151`) | yes | yes - deleted |
| imageScaleType | Int (private set) | WebGpuConfig.kt:35 | Mihon | WebGpuViewer.kt:1538 | yes | yes - `PagedSettings.Page.imageScaleType` |
| imageZoomType | ZoomStartPosition (private set) | WebGpuConfig.kt:38 | Mihon | WebGpuViewer.kt:1526, :1568 | yes | yes - `PagedSettings.Page.zoomStart` |
| imageCropBorders | Boolean (private set) | WebGpuConfig.kt:41 | Mihon | WebGpuViewer.kt:942 | yes | yes - `PagedSettings.Page.cropBorders` |
| imageCropBordersWebtoon | Boolean (private set) | WebGpuConfig.kt:44 | Mihon | WebGpuViewer.kt:942 | yes | yes - `LongStripSettings.cropBorders` |
| navigateToPan | Boolean (private set) | WebGpuConfig.kt:47 | Mihon | WebGpuViewer.kt:1819, :1840 | yes | yes - `PagedSettings.navigateToPan` |
| landscapeZoom | Boolean (private set) | WebGpuConfig.kt:50 | Mihon | WebGpuViewer.kt:1490 | yes | yes - `PagedSettings.Page.landscapeZoom` |
| transitionAnimation | ReaderPreferences.TransitionAnimation | WebGpuConfig.kt:53 | Mihon | WebGpuViewer.kt:807 | yes | yes - `WebGpuPagedSettings.Page.transitionAnimation` |
| transitionAnimationDual | ReaderPreferences.TransitionAnimation | WebGpuConfig.kt:56 | Mihon | WebGpuViewer.kt:807 | yes | yes - `WebGpuPagedSettings.Page.transitionAnimationDual` |
| cutoutMode | ReaderPreferences.CutoutMode | WebGpuConfig.kt:59 | Mihon | WebGpuViewer.kt:824 | yes | yes - `WebGpuPagedSettings.Page.cutoutMode` |
| cutoutModeDual | ReaderPreferences.CutoutMode | WebGpuConfig.kt:62 | Mihon | WebGpuViewer.kt:824 | yes | yes - `WebGpuPagedSettings.Page.cutoutModeDual` |
| dualPageView | ReaderPreferences.DualPageView | WebGpuConfig.kt:65 | Mihon | WebGpuViewer.kt:631 | yes | yes - `WebGpuPagedSettings.Page.dualPageView`; **KUN seeding lost** (R18) |
| continuousMinWidth | Int (private set) | WebGpuConfig.kt:68 | Mihon | WebGpuViewer.kt:839 | yes | yes - `WebGpuLongStripSettings.Page.continuousMinWidth` |
| zoomOutDisabled | Boolean (private set) | WebGpuConfig.kt:71 | Mihon | WebGpuViewer.kt:842 | yes | yes - `LongStripSettings.zoomOutDisabled` |
| continuousGap | Int (private set) | WebGpuConfig.kt:74 | Mihon | WebGpuViewer.kt:846 | yes | yes - `WebGpuLongStripSettings.Page.continuousGap` |
| imageFilters | List<WebGpuShader> | WebGpuConfig.kt:77 | Mihon | WebGpuViewer.kt:882 | yes | yes - `WebGpuSettings.imageFilters` |
| imageFiltersChangedListener | (() -> Unit)? | WebGpuConfig.kt:80 | Mihon | WebGpuViewer.kt:881 | yes | yes - deleted |
| tileFilters | List<WebGpuShader> | WebGpuConfig.kt:82 | Mihon | WebGpuViewer.kt:889 | yes | yes - `WebGpuSettings.tileFilters` |
| tileFiltersChangedListener | (() -> Unit)? | WebGpuConfig.kt:85 | Mihon | WebGpuViewer.kt:888 | yes | yes - deleted |

The only fork-only behaviour in this class is the `init` block seeding `dualPageView` from `pageLayout`
(WebGpuConfig.kt:88-98, helper `:228-240`) - no property is fork-only.

### 3.5 `ui/reader/viewer/webgpu/WebGpuViewerSettings.kt` (19 rendered knobs)

This file holds no config properties; it is `@Composable WebGpuViewerSettings(viewModel)` at
`WebGpuViewerSettings.kt:65` plus `ShaderList` at `:263`. Registering what it renders, because the upstream
rewrite (step 2) changes how it derives `isDual`/`readingMode` and step 6 rebuilds the zoom cluster.

| Knob | Type | file:line | Origin | Consumer | Upstream? | Superseded? |
|---|---|---|---|---|---|---|
| manga reading mode chips | ReadingMode | WebGpuViewerSettings.kt:71 | Mihon | ReaderSettingsViewModel | n/a | no; source becomes `viewModel.readingMode` (8124) |
| dualPageView chips | DualPageView | WebGpuViewerSettings.kt:90 | Mihon | `preferences.dualPageView` | n/a | no |
| continuousMinWidth slider | Int 1..100 | WebGpuViewerSettings.kt:104 | Mihon | `preferences.continuousMinWidth` | n/a | no |
| continuousGap slider | Int 1..100 | WebGpuViewerSettings.kt:117 | Mihon | `preferences.continuousGap` | n/a | no |
| webtoonDisableZoomOut | Boolean | WebGpuViewerSettings.kt:129 | Mihon | `preferences.webtoonDisableZoomOut` | n/a | no |
| orientation chips | ReaderOrientation | WebGpuViewerSettings.kt:136 | Mihon | ReaderSettingsViewModel | n/a | no; becomes `setOrientation` |
| tap zones | Int + invert | WebGpuViewerSettings.kt:152 | Mihon | `navigationModePager`/`pagerNavInverted` | n/a | no |
| transitionAnimationDual | TransitionAnimation | WebGpuViewerSettings.kt:161 | Mihon | `preferences.transitionAnimationDual` | n/a | no |
| cutoutModeDual | CutoutMode | WebGpuViewerSettings.kt:174 | Mihon | `preferences.cutoutModeDual` | n/a | no |
| imageScaleType (WebGPU subset) | Int | WebGpuViewerSettings.kt:188 | Mihon | `preferences.imageScaleType` | n/a | no |
| zoomStart | Int | WebGpuViewerSettings.kt:201 | Mihon | `preferences.zoomStart` | n/a | no |
| cropBorders | Boolean | WebGpuViewerSettings.kt:212 | Mihon | `preferences.cropBorders` | n/a | no |
| landscapeZoom | Boolean | WebGpuViewerSettings.kt:217 | Mihon | `preferences.landscapeZoom` | n/a | no |
| navigateToPan | Boolean | WebGpuViewerSettings.kt:222 | Mihon | `preferences.navigateToPan` | n/a | no |
| disableDoubleTapGestures | Boolean | WebGpuViewerSettings.kt:227 | Mihon | `preferences.disableDoubleTapGestures` | n/a | no |
| transitionAnimation | TransitionAnimation | WebGpuViewerSettings.kt:232 | Mihon | `preferences.transitionAnimation` | n/a | no |
| cutoutMode | CutoutMode | WebGpuViewerSettings.kt:243 | Mihon | `preferences.cutoutMode` | n/a | no |
| image filters list | List<WebGpuShader> | WebGpuViewerSettings.kt:258 | Mihon | `preferences.imageFilters` | n/a | no |
| tile filters list | List<WebGpuShader> | WebGpuViewerSettings.kt:259 | Mihon | `preferences.tileFilters` | n/a | no |

`isDual` at `:148` is the only logic changed by `8124d324a1`: the viewer-instance probe is replaced by
`LocalConfiguration` + `resolved.type` (`8124d324a1:WebGpuViewerSettings.kt:141-147`).

### 3.6 Preference accessors added by `8124d324a1` to `ReaderPreferences.kt`

None. That commit adds **zero** `val <name> = preferenceStore...` accessors to the reader preferences; it
only adds four `enum class` value mappers at `8124d324a1:ReaderPreferences.kt:272-332`: `ReaderTheme`,
`ScaleType`, `ZoomStartType`, `NavigationMode`. Every fork knob below is therefore absent from the
upstream file (verified against `git show 8124d324a1:.../ReaderPreferences.kt`, HEAD is 473 lines vs 434).

| Fork knob | file:line | Origin | Consumed by | Slot in upstream replacement |
|---|---|---|---|---|
| pageTransitionsPager | ReaderPreferences.kt:30 | SY | PagerConfig.kt:180 | collapsed into single `GeneralSettings.usePageTransitions` |
| pageTransitionsWebtoon | ReaderPreferences.kt:32 | SY | WebtoonConfig.kt:139 | collapsed (same field) |
| readerThreads | ReaderPreferences.kt:219 | SY | HttpPageLoader.kt:48 | none |
| readerInstantRetry | ReaderPreferences.kt:221 | SY | HttpPageLoader.kt:136 | none |
| aggressivePageLoading | ReaderPreferences.kt:223 | SY | HttpPageLoader.kt:220 | none |
| cacheSize | ReaderPreferences.kt:225 | SY | ChapterCache.kt:58 | none (outside reader config) |
| autoscrollInterval | ReaderPreferences.kt:227 | SY | ReaderActivity.kt:825, ReaderViewModel.kt:457 | none |
| smoothAutoScroll | ReaderPreferences.kt:229 | SY | WebtoonViewer.kt:350 | none |
| autoscrollGesturesEnabled | ReaderPreferences.kt:231 | SY | ReaderActivity.kt:811 | none |
| autoscrollGestureToastsEnabled | ReaderPreferences.kt:234 | SY | ReaderActivity.kt:818 | none |
| preserveReadingPosition | ReaderPreferences.kt:237 | SY | ChapterLoader.kt:75 | none |
| preloadSize | ReaderPreferences.kt:239 | SY | HttpPageLoader.kt:56 | none |
| useAutoWebtoon | ReaderPreferences.kt:241 | SY | ReaderViewModel.kt:1171 | none |
| continuousVerticalTappingByPage | ReaderPreferences.kt:243 | SY | nothing (dead) | none |
| cropBordersContinuousVertical | ReaderPreferences.kt:245 | SY | WebtoonConfig.kt:136 | none (single `LongStripSettings.cropBorders`) |
| readerBottomButtons | ReaderPreferences.kt:247 | SY | ReaderActivity.kt:678 | none |
| pageLayout | ReaderPreferences.kt:249 | SY | PagerConfig.kt:67 | none (WebGPU only has `dualPageView`) |
| invertDoublePages | ReaderPreferences.kt:251 | SY | ReaderViewModel.kt:1407 | none |
| centerMarginType | ReaderPreferences.kt:253 | SY | PagerConfig.kt:200 | none |
| archiveReaderMode | ReaderPreferences.kt:255 | SY | ArchivePageLoader.kt:67 | none |
| smallerTapZone | ReaderPreferences.kt:212 | KMK | ViewerNavigation.kt:59 | none |
| webtoonScaleType | ReaderPreferences.kt:96 | KMK | WebtoonConfig.kt:122 | none |
| longStripGapSmartScale | ReaderPreferences.kt:97 | KMK | WebtoonViewer.kt:212 | none |
| alwaysDecodeLongStripWithSSIV | ReaderPreferences.kt:104 | KMK | ReaderPageImageView.kt:67 | none |
| landscapeZoomType | ReaderPreferences.kt:123 | KMK | PagerConfig.kt:172 | none |
| pagedDisableZoomIn | ReaderPreferences.kt:143 | KMK | PagerConfig.kt:161 | none |
| pagedDoubleTapZoomEnabled | ReaderPreferences.kt:88 | KMK | PagerConfig.kt:166 | none |
| webtoonPinchToZoomEnabled | ReaderPreferences.kt:90 | KMK | WebtoonConfig.kt:116 | none |

## 4. Capabilities with no home in the upstream design

| Capability (rows) | What upstream deletes (evidence) | Sketch of a rebuild |
|---|---|---|
| Autoscroll (R1-R4, R6-R8) | `ReaderActivity.kt` is 902 lines at `8124d324a1` and 900 at `9079ad8c9a` (HEAD: 1600 - the fork's app-bar/overlay/autoscroll wiring is most of the difference); `8124d324a1` deletes the `ReaderConfig` inner class and its subscriptions; `9079ad8c9a` rewrites the same region again and `Viewer.kt` there exposes only `moveForward`/`moveBackward` (`9079ad8c9a:viewer/Viewer.kt:42-47`); `WebtoonViewer.kt` 669 -> 353 lines, autoscroll members gone. | Put the driver back on `WebtoonViewer` (it survives as a Viewer) and drive it from the activity with a `StateFlow`-backed loop calling `moveForward()` for pagers; expose `autoscroll` on the new `Viewer`/settings objects. |
| Dual-page join/shift/isolate (R9-R17) | `PagerViewerAdapter.kt` 415 -> 207 lines, no `joinedItems` (`8124d324a1:PagerViewerAdapter.kt:23` is a plain `items` list); `ReaderPage.kt` 32 -> 18 lines, `shiftedPage`/`isolatedPage`/`fullPage` gone (`8124d324a1:model/ReaderPage.kt:6-17`); `SimplePagedSettings` has only the four split/rotate booleans. | Step 4 rebuilds the six-step pipeline over the new `PagerItems`, keeping `shiftDoublePage`/`pageToShift` as viewer state and `shiftedPage`/`isolatedPage`/`fullPage` on the page model (or a parallel per-page map). |
| Cover-seeded theming (R19-R20) | `ReaderActivity.kt` at all three commits contains zero `seedColor` matches; `00335b2a70` deletes `binding.setComposeOverlay()` (the fork's `TachiyomiTheme(seedColor=...)` wrapper) and introduces the Compose reader root without a seed argument. | Re-wrap the new Compose reader root in `TachiyomiTheme(seedColor = ...)`; keep passing the seed into page holders/transition/loading composables. |
| WebGPU paged/long-strip crop+filter settings + dual-page seeding (R18, R22) | `8124d324a1` deletes `WebGpuConfig.kt` entirely (285 lines) and folds it into `WebGpuPagedSettings`/`WebGpuLongStripSettings`/`WebGpuSettings`/`LongStripSettings`; `WebGpuViewer.kt:1021-1022` now reads crop from `longStrip`/`paged` and no code maps `pageLayout` to `dualPageView`. | All the settings survive as fields; only the `pageLayout -> dualPageView` seed must be re-added as a one-shot migration in `ReaderSettingsViewModel`/`WebGpuViewer`. |
| KMK pan-before-page-turn (R32) | `9079ad8c9a` deletes `PagerViewer.moveUp`/`moveDown` (`git show 9079ad8c9a -- .../pager/PagerViewer.kt` removes both overrides) and reduces the action set to `MENU/FORWARD/BACKWARD` (`9079ad8c9a:viewer/ViewerNavigation.kt:19`), mapping Up/Down straight to BACKWARD/FORWARD in `ReaderActivity.onKeyEvent`. | Add a PAN action (or `viewer.panOrTurn(direction)`) to `ViewerNavigation`/`Viewer` and route keys and taps through it, keeping `canvas.canPanUp/Down` on the SSIV page. |
| Smaller tap zones (R31) | `9079ad8c9a:viewer/ViewerNavigation.kt:14-17` replaces the `readerPreferences` constructor with `(invertMode, isReversed)`, and regions become fixed fractions (`:48-52`). | Inject a `smallerTapZone` boolean into the navigation object (or a `NavigationSettings` field) and keep the 0.25/0.33 geometry helper. |
| Webtoon scale type + pinch toggle (R24-R26) | `SimpleLongStripSettings` has only `sidePadding`, four dual-page booleans, `doubleTapZoom`, navigation (`8124d324a1:webtoon/SimpleLongStripSettings.kt:9-23`); no scale type or pinch field. | Add `webtoonScaleType`/`pinchToZoom` to the long-strip settings and re-apply them in the webtoon `Content`. |
| Zoom cluster (R27-R30) | `PagedSettings.Page` has only `imageScaleType`, `zoomStart`, `cropBorders`, `landscapeZoom` (`8124d324a1:viewer/PagedSettings.kt:17-22`); `disableZoomIn`, paged double-tap and `landscapeZoomScaleType` have no field. | Extend `PagedSettings.Page` (or a new `ZoomSettings`) and thread it into the page composable/long-strip image view. |
| Per-mode page transitions (R37) | `GeneralSettings.usePageTransitions` is one value for both viewers (`8124d324a1:setting/GeneralSettings.kt:14`). | Keep two prefs and pass the right one per viewer, or accept the merge explicitly. |
| Reader-local actions (R44-R52) | `ReaderActivity.kt` is 902 lines at `8124d324a1` and 900 at `9079ad8c9a`, against 1600 at HEAD, so roughly 700 lines of the fork's activity wiring (app bars, overlay, chapter list, bookmark, crop toggle, second-page actions, Discord RPC) have to be reconciled by hand. | Re-attach the existing composables/dialogs to the new activity state; none of them are deleted themselves. |
| Merged-source reading, initial page (R54-R55) | `ReaderViewModel` loses the `viewer` field at `ca028cb657` (12 lines) and its state is restructured at `9079ad8c9a` (11 lines); `ChapterLoader` keeps the SY constructor. | Keep `mergedManga`/`initialPage` in the view model; only `state.viewer` call sites move to the activity. |

## 5. Migration checklist

- [ ] **Step 0 (done on base, do not re-add): SY autoscroll gestures** - `c5899ab776` / `0efe2f094b`
  already contain R5; `8646b13175` flips the default off. Verify the port does not add a second copy of
  `WebtoonRecyclerView`'s gesture detector.
- [ ] **Step 1 - `ca028cb657a1388b712533430619859d1eba4f60` (viewer out of the ViewModel)** preserves:
  R2, R4, R7, R8 (autoscroll loop/state/dialogs run off `state.viewer`), R19 (seed read in the activity),
  R44, R45, R47-R55. Replace every `viewModel.state.value.viewer` (29 sites in `ReaderActivity.kt`, 37 in
  the reader packages, e.g. `ReaderActivity.kt:544`, `:625`, `:814`, `:1004`, `:1143`) with the
  activity-owned viewer, and keep `state.autoScroll`/`ehAutoscrollFreq`.
- [ ] **Step 2 - `8124d324a1` (config classes -> settings plane)** preserves: R3, R6, R13-R16, R18,
  R24-R31, R36, R37, every row of 3.1-3.3 marked "no slot" (15 properties) plus the two page-transition
  knobs that upstream collapses into one. Deleted
  files: `ViewerConfig.kt` (86), `PagerConfig.kt` (270), `WebtoonConfig.kt` (165), `WebGpuConfig.kt` (285).
  Add fields rather than dropping: `reloadChapterListener`, `shiftDoublePage`/`doublePages`/
  `autoDoublePages`, `invertDoublePages`, `pageCanvasColor`, `centerMarginType`, `disableZoomIn`,
  `doubleTapZoom`, `landscapeZoomScaleType`, `pinchToZoom`, `webtoonScaleType`, `continuousCropBorders`.
- [ ] **Step 3 - `00335b2a70` (Compose reader + `ViewerNavigation`)** preserves: R19-R21, R31-R33, R44,
  R47, R51, R53. It deletes `binding.setComposeOverlay`, `ReaderNavigationOverlayView.kt` (113 lines) and
  the activity's direct viewer container; re-wrap the Compose root in `TachiyomiTheme(seedColor=...)`
  and keep the `smallerTapZone` region size in the new `ReaderNavigationOverlay`.
- [ ] **Step 4 - dual-page engine on the new `PagerItems`** rebuilds R9-R17: six-step `setJoinedItems`,
  chapter-id segmentation, `shiftedPage`/`isolatedPage`/`fullPage`, wide-image split/rotate/merge with
  centre margins and canvas colour, and the "return to the old page" restore. Add the R18 seed at the same
  time. Acceptance: a wide double spread splits, a full page isolates its neighbour, shift-page flips
  parity, and a chapter boundary never pairs across chapters.
- [ ] **Step 5 - `9079ad8c9a` (input + autoscroll API)** preserves: R1-R4, R6-R8, R31-R35. The commit
  collapses the viewer API to `moveForward`/`moveBackward` and centralises `onViewerTap`/`onKeyEvent`, and
  contains no autoscroll at all: add the autoscroll driver to the new activity loop and a
  `Viewer`/navigation hook for R32 pan-before-turn (which this commit deletes).
- [ ] **Step 6 - seed-colour theming + WebGPU settings + zoom cluster** preserves: R12, R15, R16, R18,
  R21-R30. Re-seed the Compose theme, re-add `pageLayout -> dualPageView`, and extend `PagedSettings`/
  `SimpleLongStripSettings` with the zoom/scale/pinch knobs (R24-R30) plus the R21 capability gate.

## 6. Provenance of each row

Classification used, in order: the `// KMK` / `// SY` / `// KUN` marker around the code; if unmarked, the
`git diff 8124d324a1^ HEAD` hunk (a hunk that only exists on the fork side is fork code); then the commit
that introduced it. Rows where the marker and the diff disagree, or where a capability is one upstream
feature wearing a fork pref, are called out below.

Ambiguous rows: **R12** (merge/rotate logic is inside `// SY` blocks but relies on `ImageUtil.mergeBitmaps`
/`isWideImage`, themselves SY, while the mpreg decode call is `// KUN`); **R16** (`pageCanvasColor` sits in
a `// SY` block yet its value comes from the KMK reader theme); **R31** (`smallerTapZone` is a KMK pref but
the geometry helper `regionSize1` is wrapped in `// KMK` inside a `// KUN`-marked constructor change);
**R34/R35** (listed as Mihon-common for completeness even though the fork's `ViewerNavigation` plumbing
around them is KUN); **R47** (SY `currentPageText` parameter, but the colour override is KMK);
**R48/R49/R50** (SY methods on the view model, yet the capability is only reachable through the fork's own
bottom bar, which is KUN-wired in `ReaderActivity`); **R55** (merged-source reading is jointly KMK and SY -
`// KMK` per-chapter manga mapping on `// SY` merged-source plumbing). R22 is registered as Mihon-common
because `WebGpuViewerSettings.kt` is byte-identical to `8124d324a1^`; do not treat it as fork code, but do
not let the port regress it either.
