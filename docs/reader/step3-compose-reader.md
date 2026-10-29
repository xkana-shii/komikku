# Step 3: drawing the reader through Compose

Commit ported: `00335b2a70c89e2498c84e72e24118af5888169d` — "Draw the reader through Compose" (#4191).
Base: `bf3c73dfee` on `feat/reader-compose`.

| Commit | What it carries |
|---|---|
| `e2552e5451` | `ViewerNavigation` as immutable value objects, the Compose navigation overlay, `ReaderSettingsViewModel.navigation` |
| `64fb4a1c1b` | `Viewer.Content`, `LocalPageColorFilter`, the WebGPU viewer driving an `ImageViewerState` |
| `12e7f16bb5` | the Compose root in `ReaderActivity`; `ReaderNavigationOverlayView.kt` and `reader_activity.xml` deleted |
| `5f403f2ae7` | `Viewer.getView()` and the reader view bridges deleted |
| `68c74b6e07` | tap zones resolved from the viewer actually built, not the renderer preference; fork markers repaired |

End state: `ReaderActivity` no longer inflates a layout. `onCreate` calls `setComposeContent { ReaderScreen() }`
(`tachiyomi/ui/reader/ReaderActivity.kt:270`), and `ReaderScreen` (`:416`) is the whole reader — seeded
`TachiyomiTheme`, the viewer through `Viewer.Content`, the tap-zone overlay, the app bars, the page indicator,
the content overlay, the loading chrome and every dialog. The activity keeps the viewer in
`MutableStateFlow<Viewer?>` (`:236`) and the overlay in a `ReaderNavigationOverlayState` (`:222`). Nothing named
`ReaderActivityBinding` is left in `app/src`; `tachiyomi/ui/reader/ReaderNavigationOverlayView.kt` and
`app/src/main/res/layout/reader_activity.xml` are gone, and `Viewer` has no `getView()` (`viewer/Viewer.kt:15-49`).

Paths below are relative to `app/src/main/java/eu/kanade/` unless they start with `app/`. Line numbers for the
"before" column are from `bf3c73dfee` and are prefixed with that commit.

## 1. What moved

| Concern | Before | Now | Where |
|---|---|---|---|
| Compose root | `ReaderActivityBinding.inflate` + `setContentView(binding.root)` + `binding.setComposeOverlay()`; the Compose half only drew the chrome and dialogs | one Compose root that also draws the viewer | `ReaderActivity.kt:270` (entry), `:416` (`ReaderScreen`) |
| Navigation overlay | `ReaderNavigationOverlayView` in the XML, pushed by each viewer through `binding.navigationOverlay.setNavigation` | Compose canvas driven by a `@Stable` state holder, fed once from `onCreate` | `presentation/reader/ReaderNavigationOverlay.kt:29`, `:70`; drawn at `ReaderActivity.kt:484`, fed at `:297-303` |
| App bars | `AppBars(state)` inside the old `ComposeView`'s `Box` | same composable, now a sibling of the viewer inside the root | `ReaderActivity.kt:486`, `:813`; `ReaderAppBars` at `presentation/reader/appbars/ReaderAppBars.kt:46` |
| Loading chrome | `ReaderProgressIndicator` view added to `reader_container` by `updateViewer`, removed by `setChapters` (`bf3c73dfee:ReaderActivity.kt:1156`, `:1211`) | `isLoadingViewer` state plus `LoadingScreen()` under the seeded theme | `ReaderActivity.kt:230`, set `:1235`, cleared `:1287`, drawn `:457` |
| Viewer content path | `binding.viewerContainer.addView(newViewer.getView())` (`bf3c73dfee:ReaderActivity.kt:1121`) | `Viewer.Content(modifier)` inside `key(viewer)` | `viewer/Viewer.kt:20`; host `ReaderActivity.kt:788-808`; pager `viewer/pager/PagerViewer.kt:259`; long strip `viewer/webtoon/WebtoonViewer.kt:347`; WebGPU `viewer/webgpu/WebGpuViewer.kt:1016`, `viewer/webgpu/WebGpuViewerContinuous.kt:28` |
| Page colour filter | `setLayerPaint` put a `ColorMatrixColorFilter` layer on the whole `viewerContainer` (`bf3c73dfee:ReaderActivity.kt:1572-1575`) | a composition local the page views read, plus a hardware layer over the WebGPU viewer below API 34 (§3.6); transitions, loading and error chrome keep their colours | `viewer/Viewer.kt:55`; provided `ReaderActivity.kt:802`; consumed `viewer/ReaderPageImageView.kt:81`, `:88-99`; layer `ReaderActivity.kt:813`, `:832-858` |
| XML + overlay view | `reader_activity.xml`, `ReaderNavigationOverlayView.kt` | deleted (§4) | `viewer/Viewer.kt:15-49` has no `getView()` left |

### 1.1 What the root does that the XML binding used to

| Job | Old owner | New call |
|---|---|---|
| Reader background | `applyGeneralSettings` → `binding.readerContainer.setBackgroundColor` | `ReaderActivity.kt:434-436`, `:444` |
| Hide tap zones on the first down | `ReaderNavigationOverlayView.onTouchEvent` → `performClick()` | `ReaderActivity.kt:445-450`, `PointerEventPass.Initial` |
| Keep screen on | `setKeepScreenOn` (window flag) | `ReaderActivity.kt:451` (`Modifier.keepScreenOn()`) |
| Insets / cutout | `updateViewerInset` (padding on `viewer_container`) | `ReaderActivity.kt:791-795`, `:802-806` |
| Page indicator | inside the old `ComposeView` `Box` | `ReaderActivity.kt:468-478` |
| Page-flash overlay | `ContentOverlay` | `ReaderActivity.kt:480-482` |
| Dialogs | inside the old `ComposeView` | `ReaderActivity.kt:502-678` |

## 2. Capability homes

"Home" is where the capability lives at HEAD. "Deferred" names the step that finishes it; nothing in this table
was dropped by the port.

| R | Capability | Home (file:line) | Note |
|---|---|---|---|
| R1 | Webtoon autoscroll driver (SY) | `viewer/webtoon/WebtoonViewer.kt:369` (`syncAutoScrollSettings`), `:418` (`performAutoScrollStep`), `:435` (`autoScrollDelayMillis`), `:443` | untouched; still driven by R2's loop |
| R2 | Activity autoscroll loop (SY) | `tachiyomi/ui/reader/ReaderActivity.kt:969` (`enableExhAutoScroll`), started at `:347` | re-inserted into upstream's rewritten `onCreate`; the loop re-reads the activity-owned `viewer` every iteration (`:1008`) |
| R4 | Autoscroll toggle + switch (SY) | `ReaderActivity.kt:908-910` → `presentation/reader/appbars/ReaderAppBars.kt:80-82` | state and view-model calls unchanged |
| R7 | Autoscroll help dialogs (SY) | `ReaderActivity.kt:622`, `:633`, `:644` (autoscroll, gestures, gesture toasts); entry points `:914-920` | all three branches kept in the root's `when (state.dialog)` |
| R9 | Dual-page join engine (SY) | `viewer/pager/PagerViewerAdapter.kt:255` (`setJoinedItems`) | untouched; runs over `joinedItems` (`:32`), which the new content path does not see |
| R10 | Shift page (SY) | **deferred → step 4**; today `viewer/pager/PagerViewer.kt:110`, `ReaderActivity.kt:1131-1133`, `:1146` | the parity is runtime state on the pager, unchanged by the port |
| R11 | Isolated page / blank slot (SY) | `viewer/pager/PagerViewerAdapter.kt:324` | untouched |
| R12 | Wide split / rotate / merge (SY) | `viewer/pager/PagerPageHolder.kt:269` (rotate), `:279` (merge), `:392` (split), canvas colour `:339` | untouched; the holder is still a `View` inside the pager's `AndroidView` (`PagerViewer.kt:259-271`) |
| R13 | `pageLayout` single / double / automatic (SY) | `viewer/pager/PagerViewer.kt:102`; bottom-bar toggle `ReaderActivity.kt:933-946`; automatic mode `:1140`; preference observers `:311-342` | `doublePages` still written by the activity and read by the adapter |
| R14 | Invert double pages (SY) | `viewer/pager/PagerViewer.kt:115`; page-number text `ReaderActivity.kt:1392-1400` | pure function of `simplePaged`, as before step 2 |
| R19 | Cover-seeded reader theme (KMK) | `ReaderActivity.kt:418-423` (wrap), seed `:954`, `:960`, gate `:181` | **full cover-seeded theming → step 6**; this wrapper is what kept the existing seeding working |
| R20 | Seed colour in reader chrome (KMK) | `ReaderActivity.kt:457` (spinner under the seeded theme); page/transition holders `viewer/pager/PagerPageHolder.kt:104`, `viewer/webtoon/WebtoonPageHolder.kt:275`, `viewer/ReaderTransitionView.kt:87`, `viewer/pager/PagerTransitionHolder.kt:68`, `viewer/webtoon/WebtoonTransitionHolder.kt:41` | **full cover-seeded theming → step 6**; the Android-side `seedColor` parameters are untouched |
| R31 | Smaller tap zones (KMK) | `viewer/ViewerNavigation.kt:16` (value), `:61-67` (equality), `:73-75` (geometry); fed `setting/ReaderSettingsViewModel.kt:153`/`:174` (pager) and `:248`/`:264` (long strip); flow `:333-355`; drawn `presentation/reader/ReaderNavigationOverlay.kt:80` | geometry now reads the navigator's own boolean, not the preference (§3.1) |
| R32 | Vertical pan before page turn (KMK) | **deferred → step 5** (mechanism only); pan code `viewer/pager/PagerViewer.kt:480` (`moveUp`), `:494` (`moveDown`), keys `:529-552` | untouched by this step: the pan still precedes the turn, and the R32 rebuild belongs to step 5 |
| R44 | Configurable bottom-bar buttons (SY) | `ReaderActivity.kt:926` (`enabledButtons`) → `presentation/reader/appbars/ReaderAppBars.kt:98` | read from `readerPreferences.readerBottomButtons` at `ReaderActivity.kt:831-833` |
| R45 | Auto webtoon mode (SY) | `ReaderActivity.kt:1181` (mode the viewer is built from), toast `:1222-1233` | unchanged, and now also the mode the tap zones resolve |
| R47 | Double-page page-number text (SY) | `ReaderActivity.kt:471` (`state.currentPageText`), produced `:1390-1405`; passed on `:925` | reaches both app-bar navigators (`ReaderAppBars.kt:194`, `:225`) |
| R48 | Chapter list dialog (SY) | `ReaderActivity.kt:591-620`, bottom-bar entry `:932` | bookmark/dropdown actions kept |
| R49 | Bookmark toggle (SY) | `ReaderActivity.kt:856` | top bar, unchanged |
| R50 | Crop-border quick toggle (SY) | `ReaderActivity.kt:824-830` (3-way crop pick), `:898-903` (toggle) | paged / webtoon / continuous-vertical, as before |
| R51 | Second-page actions (SY) | `ReaderActivity.kt:556-588` (`PageActions`: `hasExtraPage`, `onShareCombined`, `onSaveCombined`) | dialog branch moved into the root verbatim |
| R52 | Boost page (SY) | `ReaderActivity.kt:923-924` (bar) → `:1083` (`exhBoostPage`) | unchanged |
| R53 | Discord rich presence (KMK) | `ReaderActivity.kt:1577`, called `:697` (`onPause`), `:712` (`onResume`), `:1323` (`setChapters`) | re-added around upstream's rewritten lifecycle hooks |

## 3. Deliberate divergences from upstream

1. **`ViewerNavigation` carries the tap-zone size, including in equality.** The fork's constructor is
   `ViewerNavigation(invertMode, smallerTapZone)` (`viewer/ViewerNavigation.kt:11-18`), the geometry is
   `regionSize1 = if (smallerTapZone) 0.25f else 0.33f` (`:73-75`), and `equals`/`hashCode` both include the
   boolean (`:61-67`). Upstream takes only `invertMode`, hard-codes the fractions in each navigator
   (`0.33f`/`0.66f` in upstream's `LNavigation`), and its `equals` compares class and invert mode only. The size
   has to be part of the value because `ReaderSettingsViewModel.navigation` maps through
   `distinctUntilChanged()` (`setting/ReaderSettingsViewModel.kt:354`): a navigator rebuilt from the same mode,
   inversion and a new tap-zone size compares equal without it, so the flow would not re-emit and R31 would keep
   the old fractions in both the overlay and the tap handling. Every navigator takes the size, defaulted to
   `false` (`viewer/navigation/LNavigation.kt:17-23` and its four siblings), and `NavigationSettings.navigator`
   passes it to all six (`viewer/ViewerSettings.kt:33-42`).

2. **The Compose root is wrapped in the fork's seeded theme.** `ReaderScreen` opens with
   `TachiyomiTheme(seedColor = seedColorState().takeIf { themeCoverBased }, …)`
   (`ReaderActivity.kt:417-423`), the same wrapper `setComposeOverlay` had. Upstream's replacement root is a bare
   `setComposeContent { … }` block with no theme call, which would have dropped R19: the app bars, dialogs, page
   indicator and the loading spinner all read `MaterialTheme` from this scope. The seed itself is
   still computed in `seedColorState`/`seedColorStatic` (`:954-965`) and gated on `uiPreferences.themeCoverBased`
   (`:181`), and the Android-side holders still take `seedColor` through `ReadingMode.toViewer` (`:1186`) for R20.

3. **Tap zones and the settings sheet come from the viewer that was built, not from the renderer preference.**
   `updateViewer` builds the viewer and then passes `isWebtoonViewer = newViewer is WebtoonViewer` and
   `isWebGpuViewer = newViewer is WebGpuViewer` to `settingsViewModel.setViewerReadingMode`
   (`ReaderActivity.kt:1182-1196`); the settings view model stores those flags in one `ViewerMode`
   (`setting/ReaderSettingsViewModel.kt:400`) and resolves long-strip zones from them (`:337-338`).
   Upstream calls `setViewerReadingMode(readingMode)` and the setting view model reads `isWebGpu.get()` instead.
   The fork resolves again because `ReadingMode.toViewer` has the R21 capability gate
   (`setting/ReadingMode.kt:143-157`): with `highQualityRenderer` on and the GPU unavailable it returns a
   `WebtoonViewer` (or `WebGpuViewerContinuous` for continuous vertical), and upstream's preference-based check
   gives that SSIV long-strip viewer the *paged* navigator. The order matters and is deliberate: the mode is set
   before `viewerFlow.value = newViewer` (`:1203`), so the first tap already sees the right zones. Commit
   `68c74b6e07` is this fix.

   The sheet follows the same flags. `ReadingModePage` renders `WebGpuViewerSettings` off
   `ReaderSettingsViewModel.isWebGpuViewer` (`presentation/reader/settings/ReadingModePage.kt:34`), not off
   `highQualityRenderer`: the flow is the built viewer's identity (`setting/ReaderSettingsViewModel.kt:83-85`),
   and the preference is only its value until a viewer exists (`:84`), so a reading-mode page opened before the
   first viewer still shows something. A device the gate falls back on keeps the pager or long-strip knobs its
   viewer reads, instead of WebGPU knobs that write `dualPageView`. Commit `9bae801d66` is this fix.

   The WebGPU sheet's dual-page `WIDE` case reads the box the viewer is drawn in, not the window
   (`viewer/webgpu/WebGpuViewerSettings.kt:147-153`): the viewer publishes `width > height` for its own
   post-inset size (`viewer/webgpu/WebGpuViewer.kt:1035-1037`) into
   `ReaderSettingsViewModel.viewerWideViewport` (`setting/ReaderSettingsViewModel.kt:87-106`), which is null
   again whenever a viewer is rebuilt (`:402`) and reads as not wide until one is drawn. Foldables, split screen
   and cutout / system-bar padding can leave `LocalConfiguration` landscape over a portrait viewer, which would
   offer a spread the viewer does not open, or hide one it does. The comparison lives once, in `isWideViewport`
   (`WebGpuViewer.kt:687`), which both `isDualPageMode` (`:677`) and the published fact call. Commit
   `b01b41d1da` is this fix.

4. **`ReaderAppBars` keeps its fork arguments.** The composable's parameter list is the fork's
   (`presentation/reader/appbars/ReaderAppBars.kt:45-106`), including the whole `// SY` block upstream does not
   have (`:77-105`): the EH-tool and autoscroll arguments (`:78-94`), `currentPageText` (`:97`),
   `enabledButtons` (`:98`), `currentReadingMode`/`dualPageSplitEnabled`/`doublePages` (`:99-101`),
   `onClickChapterList` (`:102`), `onClickPageLayout` (`:103`), `onClickShiftPage` (`:104`). Upstream's signature
   ends at `onClickSettings`.
   `AppBars` calls it with all of them (`ReaderActivity.kt:849-949`), so R44, R47, R48, R50, R51 and R52 survive
   without touching the app-bar file. Second-page actions are separate from the bars and live in the
   `PageActions` branch (`ReaderActivity.kt:556-588`).

5. **The fork's activity-side behaviour was re-inserted around upstream's rewritten `onCreate`/`updateViewer`.**
   Upstream's `onCreate` has no autoscroll, no dual-page observers and no connections work; the fork adds
   `enableExhAutoScroll()` (`ReaderActivity.kt:347`, body `:969-1029`), the `pageLayout` / `dualPageSplitPaged`
   observers (`:311-342`), the display-profile observer (`:307-309`) and the Discord calls (`:697`, `:712`,
   `:1323`). `updateViewer` likewise keeps the `seedColor` argument (`:1186`), the webtoon autoscroll hand-off
   (`:1204-1206`), the SY double-page block (`:1208-1216`) and the auto-webtoon toast (`:1222-1233`) around
   upstream's `readingMode`/`setViewerReadingMode`/`isLoadingViewer` sequence. The loading chrome changed three
   times: `12e7f16bb5` wrapped `CombinedCircularProgressIndicator(progress = { 0f })` in a private
   `LoadingChrome`, `68c74b6e07` replaced it with upstream's `LoadingScreen()` (`:457`) because the wrapper only
   reproduced the indeterminate spinner and the seed already arrives from the ambient `TachiyomiTheme`, and
   `78316baaf0` made the flag unable to latch: `isLoadingViewer` is raised only immediately before the chapters
   the state already holds are handed to the new viewer (`ReaderActivity.kt:1292-1293`), and `setChapters`
   (`:1345-1346`) is still its only clear, so a rebuild that receives no chapters cannot leave the reader behind
   a loading screen with no bars.

6. **The colour filter covers page content only, and the WebGPU viewer only below API 34.** The root provides
   `LocalPageColorFilter` (`ReaderActivity.kt:802`) and only `ReaderPageImageView` reads it (`:81`, `:88-99`), so
   transitions, loading and error pages keep their colours instead of inheriting a layer over the whole
   container. The WebGPU viewer's pages are the `webgpuviewer` surface and read nothing, so `78316baaf0` hosts
   that viewer in a `ComposeView` of the reader's own below API 34 (`ReaderActivity.kt:813`, `:832-858`) and puts
   the same paint on it as a `LAYER_TYPE_HARDWARE` layer, cleared to `LAYER_TYPE_NONE` on release. From API 34
   on the surface is a `SurfaceView` in a layer of its own, which no ancestor can filter — the pre-port
   `viewerContainer` layer never filtered it either — so that path takes no layer and no claim is made about it.

## 4. What was deleted, and what replaced it

| Deleted | What it was | Replaced by |
|---|---|---|
| `tachiyomi/ui/reader/ReaderNavigationOverlayView.kt` (113 lines) | a `View` that drew the regions in `onDraw`, faded itself in with a `ViewPropertyAnimator` and faded out on `performClick` | `ReaderNavigationOverlay` (`presentation/reader/ReaderNavigationOverlay.kt:29`) driven by `ReaderNavigationOverlayState` (`:70`); the new-user flag, the `showOnStart` rule and the `DisabledNavigation` suppression are reproduced in `setNavigation` (`:77-86`), the fade in `AnimatedVisibility` (`:33-38`) |
| `app/src/main/res/layout/reader_activity.xml` (31 lines) | `reader_container`, `viewer_container`, a `ReaderNavigationOverlayView` and an empty `ComposeView` | `ReaderScreen` (`ReaderActivity.kt:416`) |
| `Viewer.getView()` and its pager / long-strip / WebGPU implementations | the activity added the returned view to `viewer_container` | `Viewer.Content(modifier)` (`viewer/Viewer.kt:20`), called once in `ReaderViewer` (`ReaderActivity.kt:788-808`) |
| `forceNavigationOverlay`, the per-viewer `navigator` field and `defaultNavigation` | each viewer built its own navigator and pushed it into `binding.navigationOverlay` | `ReaderSettingsViewModel.navigation` (`setting/ReaderSettingsViewModel.kt:333`), pushed into the overlay once (`ReaderActivity.kt:297-303`); viewers read `activity.settingsViewModel.navigation.value` at tap time (`viewer/pager/PagerViewer.kt:193`, `viewer/webtoon/WebtoonViewer.kt:201`, `viewer/webgpu/WebGpuViewer.kt:824`) |
| `takeNavigationOverlayForNewUser()` | consumed the "new user" flag inside the settings view model, once per viewer | consumed once in `onCreate` (`ReaderActivity.kt:294-296`), next to the overlay state it feeds |
| `applyGeneralSettings`, `updateViewerInset`, `setKeepScreenOn`, `setLayerPaint`, `getCombinedPaint` | activity observers that mutated views and window flags | the root's modifiers (§1.1) and `LocalPageColorFilter` / `viewerColorFilter` (`viewer/Viewer.kt:55`, `ReaderActivity.kt:1619`) |
| the loading `ReaderProgressIndicator` added by `updateViewer` | a view added to `reader_container` and removed by `setChapters` | `isLoadingViewer` (`ReaderActivity.kt:230`, `:1235`, `:1287`) and `LoadingScreen()` (`:457`) |

Nothing fork-only lived in the deleted XML. It held two `FrameLayout`s, the overlay view and an empty
`ComposeView`; the fork's chrome, dialogs and content overlay were already Compose inside that `ComposeView`, and
the overlay view's only fork-relevant behaviour (show once for a new user, skip `DisabledNavigation`, fade in and
out) moved into `ReaderNavigationOverlayState`. `ReaderProgressIndicator` itself is not deleted — page holders and
transitions still use it (`viewer/pager/PagerPageHolder.kt:101`, `viewer/webtoon/WebtoonPageHolder.kt:271`,
`viewer/ReaderTransitionView.kt:87`); it just stopped being the activity's loading chrome.

The overlay's navigator is a flow value instead of a push, which is the guarantee the port buys: the activity
collects `settingsViewModel.navigation` (`setting/ReaderSettingsViewModel.kt:363-386`, which re-emits whenever the
viewer or a tap-zone setting changes) into the state holder (`ReaderActivity.kt:299-304`), so the first navigator
cannot be missed for want of an assigned listener and every later tap-zone change travels the same path. It is
not a change to *when* the overlay appears: master pushed its initial navigator from the preference registration
that runs after the listener is assigned (`85d7d1fade:viewer/pager/PagerConfig.kt:119-120`, `:246-247`;
`viewer/pager/PagerViewer.kt:113-169`), and both sides show it at start under the same
`showNavigationOverlayOnStart` / new-user rule (`ReaderNavigationOverlay.kt:77-86`,
`85d7d1fade:ReaderNavigationOverlayView.kt:25-33`).

## 5. What is compile-only

No device or emulator run was made for this step, and no test covers it. `spotlessCheck` passes on this commit;
Spotless formats `**/*.kt` and `**/*.xml` only (`buildSrc/src/main/kotlin/mihon.code.lint.gradle.kts:21-58`), so
this document is not checked by it. The reader has no UI test harness (`app/src/androidTest` does not exist), and
`ViewerNavigation` cannot be unit-tested as it stands: it is built on `android.graphics.RectF`/`PointF`
(`viewer/ViewerNavigation.kt:28-38`) and the build sets no `testOptions.unitTests.isReturnDefaultValues` and no
Robolectric, so a JVM test would only see stub geometry.

Manual pass owed before step 4:

| Behaviour | What to try | Why it is at risk |
|---|---|---|
| Tap-zone geometry with `smallerTapZone` on | all six schemes, portrait and landscape, pager and long strip; overlay rectangles must match where taps land | geometry now comes from a value rebuilt in the settings view model (`ViewerNavigation.kt:73-75`) and is read by two consumers: the viewers (`PagerViewer.kt:193`, `WebtoonViewer.kt:201`, `WebGpuViewer.kt:824`) and the overlay (`ReaderNavigationOverlay.kt:80`) |
| Overlay hide on first down | tap a zoomed page, swipe in the long strip, tap the app-bar area | the hide is a root `pointerInput` at `PointerEventPass.Initial` (`ReaderActivity.kt:445-450`), while the pager sits inside `AndroidView` behind a `DisallowInterceptBarrier` (`PagerViewer.kt:259-271`, `:608`) and the strip is an `AndroidView` too (`WebtoonViewer.kt:347-354`) |
| Insets / cutout | fullscreen on and off, `drawUnderCutout` on, notched device, gesture nav bar shown and hidden | padding moved from the viewer container (`ViewCompat.setOnApplyWindowInsetsListener`) to the viewer's `Content` modifier (`ReaderActivity.kt:791-795`, `:802-806`), where it is derived from `general` in composition; check the viewer still re-pads when the bars change and that the chrome sits where it did |
| Keep screen on | reading with the setting on, then off | `Modifier.keepScreenOn()` (`ReaderActivity.kt:451`) replaces the window flag the activity used to set |
| Autoscroll at first open | open a long strip with autoscroll already enabled, then a pager; open and close the menu | the loop starts in `onCreate` (`:347`) before a viewer exists and re-reads `viewer` each iteration (`:1008`), and the webtoon pause/resume goes through `setAutoScrollUiBlocked` (`:1165`) |
| Grayscale / inverted on pages only | transition, loading and error pages must keep their colours; animated pages and SSIV pages must take the filter | the filter is applied per page view (`ReaderPageImageView.kt:88-99`), including a hardware layer for SSIV, rather than as one layer over the whole container; the WebGPU viewer is the one exception and takes a layer instead (§3.6) |
| HDR / colour mode on the WebGPU path | grayscale and inverted with the high-quality renderer on an API 26-33 device (an emulator is enough), then on API 34+ | The paged and long-strip path is intact: `setting/ReaderSettingsViewModel.kt:80-81` → `setting/GeneralSettings.kt:17-18` → `viewerColorFilter(...)` (`ReaderActivity.kt:796-797`, definition `:1619-1636`) → `LocalPageColorFilter` (`:802`) → `PagerViewer.kt:254-265`/`PagerPageHolder.kt:81` and `WebtoonViewer.kt:335-353`/`WebtoonPageHolder.kt:98` → applied in `ReaderPageImageView.kt:88-99`. The WebGPU path is what changed: the removed pre-port call was `binding.viewerContainer.setLayerType(LAYER_TYPE_HARDWARE, paint)` over the whole viewer container (`bf3c73dfee:ReaderActivity.kt:1572-1574`), and webgpuviewer 53's `ViewerSurface` branches on `Build.VERSION.SDK_INT` (`gradle/libs.versions.toml:19`) — ≥34 draws through `AndroidExternalSurface` (a `SurfaceView`, its own SurfaceFlinger layer, which a parent's layer paint cannot reach), <34 through `AndroidEmbeddedExternalSurface` (a `TextureView` on the ordinary view pipeline, which that paint did filter). So the old filter reached WebGPU pages only on **API 26-33**; on API 34+ it never applied and nothing applies it there. `78316baaf0` gives the **API 26-33** range its layer back (§3.6): the hosting `ComposeView` takes the paint as a hardware layer (`ReaderActivity.kt:848-851`), cleared on release (`:853-855`), while nothing under `viewer/webgpu/` reads `LocalPageColorFilter` (`WebGpuViewer.kt:1024-1041`, `WebGpuViewerContinuous.kt:26-30`). Proven: the removed call and its target, the SDK-34 branch in webgpuviewer 53, the replacement call site, and the platform semantics. Unproven: that the layer actually reaches that surface on a device — nothing was run on one, and the range is conditional on `WebGpuRenderer.isAvailable` and on the viewer being the WebGPU one |

## 6. Next steps

- **Step 4 — R10 dual-page shift parity**: rebuild the join/shift/isolate engine over the new pager items and own
  `shiftDoublePage`/`pageToShift` there instead of the activity (`ReaderActivity.kt:1131-1133`, `:1146`).
- **Step 5 — R32 pan before page turn**: give the navigation/viewer API a pan action so the surviving
  `PagerViewer.moveUp`/`moveDown` (`viewer/pager/PagerViewer.kt:480`, `:494`) is reachable from the reduced
  action set, not only from the key handler.
- **Step 6 — R19/R20 full cover-seeded theming**: complete the seeding of the reader chrome and page surfaces.
  The WebGPU colour filter is settled in §3.6 (a layer over that viewer below API 34, nothing from API 34 on);
  its API 26-33 path is on the manual pass in §5.
