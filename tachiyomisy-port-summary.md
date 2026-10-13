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

## Cherry-pick provenance verification

The following mappings were verified against the commit trailers, reflog entries, and upstream/result patches. Every row below was applied with `git cherry-pick -x`; “adapted” means the cherry-pick was retained while resolving Komikku-specific paths, APIs, schema, or conflicts. No duplicate cherry-picks were performed during this verification.

| TachiyomiSY SHA | Komikku SHA | Verified application method |
| --- | --- | --- |
| `2e77e33538c1510efc3916b2edb0daa6284f6fcd` | `0a87df9feb` | Direct `-x` cherry-pick; resulting patch ID matches upstream. |
| `e3d052922368b51fbcf28e63ac901acbc520db16` | `97dc173791` | `-x` cherry-pick with Komikku logging API adaptation. |
| `336f6b7d4eec5ded42c9321c48784da937efb365` | `bec4673603` | `-x` cherry-pick with WebKit 1.15 and Komikku WebView/provider adaptation. |
| `f3b74f72d2e4a56da9a0a56bcc8b86f7132b5792` | `0d82b9a45b` | `-x` cherry-pick with the upstream changelog-only hunk omitted. |
| `6965f8a81c0779e383ddcf3875c5373ab0396245` | `79d869a625` | `-x` cherry-pick with Komikku SQL schema and filter integration adaptation. |
| `9edb24dd102d9d3f3ea6b019115432fdb7ec7dc1` | `424a2aad31` | `-x` cherry-pick applied to Komikku's split version catalog. |
| `d282e4ad0a74b10a111c7c1f34fb528386fb585c` | `43dd4859e0` | `-x` cherry-pick applied to Komikku's split version catalog. |
| `e8fd1c87dce3f5de40c531e6648395c8a69563cc` | `7bfdf807d1` | `-x` cherry-pick with the changelog conflict resolved and Komikku theme files retained. |
| `57ad4b8d846b0f2b0badf86c000aefd68434076c` | `5e73c5db41` | `-x` cherry-pick with existing signature-qualified extension behavior retained. |
| `82ee03df5c765b6c809aa44bf4a06de98adb18ff` | `62682f288f` | `-x` cherry-pick with Komikku private-installer/package-ID adaptation. |
| `8717f578fac5a9cfcfc7ff19da26b3fddb6659cf` | `0e4b4287a9` | `-x` cherry-pick with the changelog conflict omitted. |
| `148496e754d1f59a66cb5f335f58c4d0d73c2ad0` | `dc0675ba01` | `-x` cherry-pick adapted to Komikku's getter-style preference API. |
| `8e3a32f983277e50d5b39818d1385f3b4a345423` | `f470161a30` | `-x` cherry-pick; only the missing MangaMerge transaction hunk was applicable because other repositories already matched. |
| `28e61a1dd69830ee47fb663111cec4b70ed9d1fa` | `aa024c5aeb` | `-x` cherry-pick applied to Komikku's split version catalog. |
| `5621751c15585be94dc72607f2d7d277a79b81a3` | `394cb8369d` | `-x` cherry-pick with Komikku's vertical reader slider callback preserved. |

The final `c9b9ca2c9d` commit is a manual follow-up, not an upstream cherry-pick. It contains marker normalization, the WebKit 1.15 compatibility correction, the AboutLibraries 15.0.4 revert after AGP validation, and audit documentation. The upstream AboutLibraries commit was genuinely cherry-picked as `52f5dc07f7`, then its incompatible dependency pin was reverted and remains classified as blocked.
