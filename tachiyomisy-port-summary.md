# TachiyomiSY port summary

## Cumulative status

- Original inventory: 1,019
- Audited candidates: 55
- Genuine cherry-picks: 15
- Manual ports: 1
- Already implemented: 7
- Behaviorally equivalent: 4
- Superseded: 12
- Reverted or obsolete: 10
- Blocked: 6
- Uninvestigated: 964

The categories are mutually exclusive and total 1,019. The latest completed window contains the 50 consecutive candidates from `336f6b7d4eec5ded42c9321c48784da937efb365` through `5621751c15585be94dc72607f2d7d277a79b81a3`. The next unprocessed candidate is `dfc0910912d0b45f03f7835c76cc3724d09ddd06`.

## Implemented in the 50-candidate window

- WebView spoofing now keeps the configured user agent and matching `Sec-CH-UA` metadata in embedded and interceptor WebViews (`bec4673603`), using WebKit 1.15.0 because SY's 1.16.0 needs the blocked AGP 9 line.
- Updates supports included/excluded category filters using Komikku's current SQL schema and existing blacklist/scanlator filters (`79d869a625`).
- Tokyo Night is available in Compose and platform themes (`7bfdf807d1`).
- Private extension updates retain private installation, Shizuku detection uses its permission, and the existing Shizuku installer/R8 fixes are recorded (`62682f288f`, `0e4b4287a9`, `5e73c5db41`, `0d82b9a45b`).
- Sync state keys and SQLDelight `RETURNING` notification timing match later SY behavior (`dc0675ba01`, `f470161a30`).
- Reader app bars close after page slider navigation in both horizontal and Komikku vertical layouts (`394cb8369d`).
- Okio 3.18.1, Spotless 8.9.0, and MaterialKolor 5.0.0 were applied with genuine `-x` provenance (`424a2aad31`, `43dd4859e0`, `aa024c5aeb`).

## Existing implementations confirmed

- Backup restore already retries failed batch entries individually and scopes restored chapters/history correctly.
- The migration dialog already avoids inaccessible reflective ViewModel construction.
- AndroidX ViewModels, async MoveSorting migration, SQLCipher 4.19.1/Room3 driver, min SDK 26, FlexibleAdapter 5.1.0, ConstraintLayout 2.2.2, and MangaBaka raw user-agent behavior are present.
- Reader initialization already reports failures without the historical indefinite-loading path.

## Deferred dependency chains

- Compose alpha BOM/workaround removal, AGP 9.3.1, AboutLibraries 15.0.4, every-cold-start update checks, and SyncYomi protocol v2 require coordinated adaptations described in `tachiyomisy-blocked-commits.txt`.
- Resumable image downloads remain disabled in final upstream history; the historical progress fix is obsolete unless Komikku independently enables the feature.

## Previous ports

- MangaBaka date-only values preserve their calendar day in negative time zones (`5f7a4f83f5`, manual adaptation).
- Optional OEM Window R8 warnings and verbose enhanced-source crash logs were ported with genuine cherry-picks (`0a87df9feb`, `97dc173791`).
