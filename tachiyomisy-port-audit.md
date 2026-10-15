# TachiyomiSY synchronization audit

## Method

This audit uses `git cherry -v main sy/master` only to build a candidate list.
Each resolved item is then inspected against the full SY diff, later SY history,
and the current Komikku implementation. Ports are applied using
`git cherry-pick -x` and completed with `git cherry-pick --continue` when a
Komikku-compatible conflict resolution is required.

The current baseline is Komikku `499f3cba2debd9a781e174211f3c51b8907a8318`,
TachiyomiSY `5a469745d39e8f8cea9a1669bea6108cd71dc951`, and Mihon
`446f6df82c390ab760802c2fce3b677bce3f31c1`.

## Verified ports in this batch

| SY commit | Resulting Komikku commit | Method and Komikku adaptation |
| --- | --- | --- |
| `b9a52f9dddfbe5cb128a9b2f8fc3367ebe402918` | `f9fe95eb31af54ce30f4d59c05816920f279799a` | Genuine `-x` cherry-pick. Preserved the strict existing date parser while accepting year-only and year-month MAL values. Only the unavailable upstream changelog hunk conflicted. |
| `a82473af8f9449f882592cc27e02ee724ca3d110` | `adf36cd63b7d3e7d853a61a4a1064fd9ec3c58b7` | Genuine `-x` cherry-pick, continued after adapting state, Java Time, SQLDelight, and Komikku's prediction UI. Category filters preserve system/default-category semantics and use a Komikku-only string in `i18n-kmk`. |
| `82e5e7f6fdf2e3170e875067f562e24df2f6350e` | `cf3bb3294dbc05a14f5f167cd012037119418d77` | Genuine `-x` cherry-pick, continued after retaining tracker-specific API/error handling and immutable score lists. Adds direct `id:` lookup for AniList, Bangumi, Hikka, Kitsu, MangaUpdates, and Shikimori. |

Each result has the upstream `(cherry picked from commit …)` trailer and the
branch reflog records a `cherry-pick` operation for it.

## Reconfirmed equivalence / supersession

| SY commit | Disposition | Evidence |
| --- | --- | --- |
| `134ec7a0b49aadfc1e507917a2979cd1f5d7d257` | Already implemented | Reader initialization runs in the ViewModel, exposes initialization errors, and no longer has the historical indefinite-loading path. |
| `c6ce95ab9b446a29b6b167cb194370771d65a4e8` | Behaviorally equivalent | Migration uses Voyager ScreenModel construction rather than reflective creation of an inaccessible AndroidX ViewModel. |
| `781334e0cd31aa198b4156ca0db0bd19d290e75d` / `ce810a10d18dede178353915d16c0e7ae4e5c163` | Already implemented | Current extension loading reads numeric metadata through Android's float metadata API and supports the final `1.4` and `1.6` library versions. |
| `ef3f3cf2fef2d7189c6369d914d6045f9e83dd42` | Behaviorally equivalent | The reader navigator uses the project integer-range slider and recomputes its range for the selected chapter rather than retaining stale Material `SliderState.steps`. |
| `bdf847e8671e678eb45159343a6a1450b3a46c4c` | Superseded | Current reader initialization does not use the removed `NonCancellable` activity path. |
| `b884b203a1ea18ef094976e52522b5cd13b224b6` | Already implemented | Extension initialization uses IO-scoped work and source registration waits for initialized installed extensions. |
| `e65e78419e0d24e9aad28601e3e1fda991fe8b9e` | Behaviorally equivalent | Current Java Time formatting uses `ChronoUnit.DAYS.between`, the total-day calculation SY intended. |
| `25b9d8793e83d195e8fc4fc6fac1398e412339d3` | Superseded | The new Upcoming category state drives the active-filter indication; the current Updates state already includes category filtering. |

## Deferred work

See [`blocked-sy-commits.md`](blocked-sy-commits.md) for exact prerequisites.
The next queue item is `c3503dbd3c28496906019eceb1a1326d92c8d278` (`Remove funding`),
but queue order is not an applicability verdict.