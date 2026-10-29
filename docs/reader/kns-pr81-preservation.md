# KNS reader preservation for Komi-kun PR #81

Komi-kun [#81](https://github.com/komi-kun/komi-kun/pull/81) (squash `bc5bfa077c`, head `39740757a7`,
24 commits) ports Mihon's three reader steps: `ca028cb657` (the activity owns the viewer),
`8124d324a1` (reactive settings replace `ViewerConfig` / `PagerConfig` / `WebtoonConfig` /
`WebGpuConfig`) and `00335b2a70` (the reader is drawn through Compose). Its own inventory
([`feature-inventory.md`](feature-inventory.md)) covers Komi-kun; this file covers what this fork adds
on top. It was ported on `codex/komikun-pr81-compose-reader` from `main` `6ffd8272e7` (safety branch
`backup/pre-komikun-pr81-20261029`).

## How the fork-only code was found

Every `KNS` block in the reader packages was listed and its count compared per file before and after
the port. The only blocks that left their file are the three in `WebGpuConfig.kt`, which upstream
deletes; they moved to the settings plane (below). The four retired config classes were also diffed
against Komi-kun's copies at `85d7d1fade`: only `WebGpuConfig` differed (the HQ zoom rules).

## Preservation matrix

| Feature | Origin | Where it lives now | Status |
|---|---|---|---|
| Reader automation (auto scroll / auto flip, speed, limits, stop on tap) | KNS | `ui/reader/automation/`, controls in `ReaderAppBars` | Adapted: the controls read the activity's viewer (step 1); engine, preferences and defaults unchanged. Unit-tested; not run on a device |
| Only one automation | KNS | — | TachiyomiSY's autoscroll loop, gestures, help dialog, settings-plane fields and "Smooth auto scroll" switch removed; `ReaderAutomationOnlyTest` pins their absence |
| Action row (Bookmark, Retry all, Boost page), title-only top bar | KNS | `ExhUtils`, `ReaderTopBar` | Unchanged; seen on the emulator |
| Colored bookmarks and long-press colour menu in the reader | KNS | `ExhUtils`, `ReaderViewModel` | Unchanged; bookmark button seen on the emulator, colour menu not exercised |
| HQ paged zoom: double tap zoom, disable zoom in | KNS | `WebGpuSettings.zoomGestures` ← `ReaderSettingsViewModel.webgpuPaged`, applied in `WebGpuViewer` | Reimplemented on the settings plane; unit-tested (live preference changes) |
| HQ long strip zoom: double tap and pinch independently | KNS | `ReaderSettingsViewModel.webgpuLongStrip` | Reimplemented; unit-tested |
| HQ: no double tap detection without double tap zoom (drag-zoom bypass) | KNS | `WebGpuZoomGestures.doubleTapGestures` | Preserved; unit-tested |
| HQ double tap gate (second tap and added finger kept from the surface) | Houri `7e06ee89b8` via KNS | `WebGpuDoubleTapZoomGateLayout`, hosted by `WebGpuViewer.Content()` | Adapted to Compose: the gate View holds a `ComposeView` drawing the surface (`Surface()`, overridden by the long strip). Not run on a device |
| Legacy webtoon one-finger quick scale vs two-finger pinch | Houri (port `48360cedf1`) | `WebtoonScaleGesture`, `WebtoonFrame`, `WebtoonRecyclerView` | Unchanged (files untouched except upstream's settings consumers); existing tests pass |
| Legacy pager / animated page zoom restrictions | Houri (port `48360cedf1`) | `ReaderPageZoom`, `ReaderPageImageView` | Unchanged (only upstream's page colour filter added); existing tests pass |
| 100-page preload | KNS | Settings → Reader, `HttpPageLoader` | Unchanged |
| Chapter visibility (blacklist, Smart Scanlator Merge) in reader navigation | KNS | `ReaderViewModel` | Unchanged (17 KNS blocks intact) |
| Reader chapter list: retried download leaves the error state | KNS | `ChapterListDialog` | Unchanged |
| Auto Reread / completion statuses | KNS | `ReaderViewModel` | Unchanged |
| Automation settings group | KNS | `SettingsReaderScreen` | Unchanged |

## Upstream fixes included

All 24 commits are in, including the follow-ups: reading mode resolved like the viewer (#5), webtoon
pinch/zoom applied once the strip exists (#6), tap zones from the live viewer (#11), the settings sheet
keyed off the built viewer (#14), WebGPU page filters and the loading chrome (#15), the dual-page
decision from the viewer's own box (#16, #21), and the cover colour seeding the WebGPU host (#22).
Upstream's documented limitation stands: WebGPU colour filters on the API 26–33 surface path.

## Per-commit status

| # | Upstream | Result | Notes |
|---|---|---|---|
| 1 | 84ae0bd3da | picked | Komi-kun's inventory doc. |
| 2 | 896ddb1038 | picked, adapted | Viewer owned by ReaderActivity. Kept the fork's side where upstream wired TachiyomiSY autoscroll gestures (excluded since PR #80); KNS automation controls read the activity's viewer. |
| 3 | 905fad7899 | picked | Settings plane (phase A). |
| 4 | 40bb25cfba | picked, adapted | Config listeners replaced. WebGpuConfig deletion accepted; its KNS HQ zoom rules move to the settings plane in a KNS follow-up. TachiyomiSY gesture fields removed from GeneralSettings/ReaderSettingsViewModel. |
| 4b | KNS follow-up | adapted | HQ zoom rules (WebGpuZoomGestures) carried by WebGpuSettings and applied by WebGpuViewer. |
| 5 | 622475911b | picked, adapted | Reading-mode resolution and WebGPU dual-page seed fix taken; gesture fields kept out. |
| 6 | bf3c73dfee | picked | |
| 7 | e2552e5451 | picked | Compose navigation overlay. |
| 8 | 64fb4a1c1b | picked, adapted | WebGpuViewer: KNS zoom block kept on viewerState; Content() hosts the fork's Houri double-tap gate (AndroidView of WebGpuDoubleTapZoomGateLayout around the surface) instead of the bare ImageViewer composable. |
| 9 | 12e7f16bb5 | picked, adapted | Reader drawn through Compose; the fork's layout had no changes of its own. TachiyomiSY gesture hooks not taken; CHANGELOG kept as the fork's. |
| 10 | 5f403f2ae7 | picked, adapted | View bridges removed. The fork's HQ double-tap gate now holds a ComposeView drawing ImageViewer(viewerState), and Content() hosts the gate. |
| 11–15 | 68c74b6e07, 7c60c2cf7e, b4915b657b, 9bae801d66, 78316baaf0 | picked | Tap-zone routing, docs, sheet keyed off the built viewer, WebGPU page filters, loading chrome. |
| 16 | b01b41d1da | picked, adapted | The viewport size is measured on the fork's gate view (AndroidView) instead of the bare ImageViewer. |
| 17–20 | 192e5db00e, 5f1fde02d0, 2d9076cf61, 4115f65c18 | picked | Docs and tests (settings plane signals, long-strip tap zones). |
| 21 | 67d9a88bbe | picked, adapted | publishViewerViewport() applied to the fork's gate view. The long strip no longer overrides Content: the base Content (gate plus viewport) hosts its ImageViewerContinuous through a KNS Surface() hook, so the HQ long strip keeps the double-tap gate it had before. |
| — | KNS follow-ups | adapted | `e5c7728cf5` HQ zoom on the settings plane, `1886a2d174` TachiyomiSY autoscroll removed, `cdfcd67451` tests |

## Runtime checks done (emulator, API 36, x86_64, debug build)

A local test manga (two chapters of generated pages) was opened through the local source:
- the Compose reader opened, showed the navigation overlay and the first page, without a crash;
- two taps on the left zone in right-to-left mode moved to page 3 (pixel colour of the page checked);
- the menu showed the fork's top bar, action row and chapter slider.

During the run the bottom bar once showed five icons together with a chapter-transition page, after a
tap near the bottom; a later state rendered page 4 normally. Not understood or reproduced yet.

Not checked on a device: Legacy long strip, HQ paged and long strip, dual page, landscape, animated
pages, zoom gestures, reader automation, colour filters, API levels below 36.
