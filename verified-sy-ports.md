# Verified TachiyomiSY ports

These records are limited to ports whose resulting Komikku commit contains the
upstream `-x` trailer. A conflict resolution remains a genuine cherry-pick when
the cherry-pick operation was continued rather than replaced with a normal
manual commit.

| SY commit | Komikku commit | Verification |
| --- | --- | --- |
| `b9a52f9dddfbe5cb128a9b2f8fc3367ebe402918` | `f9fe95eb31` | Reflog `cherry-pick`; trailer present; MAL partial-date parser port. |
| `a82473af8f9449f882592cc27e02ee724ca3d110` | `adf36cd63b` | Reflog `commit (cherry-pick)`; trailer present; Upcoming category-filter port. |
| `82e5e7f6fdf2e3170e875067f562e24df2f6350e` | `cf3bb3294d` | Reflog `commit (cherry-pick)`; trailer present; tracker `id:` search port. |
| `5a469745d39e8f8cea9a1669bea6108cd71dc951` | `418f100288` | Reflog `commit (cherry-pick)`; trailer present; final OkHttp 5.5.0 version bump. |

Historical verified mappings from the earlier, now-rebaselined window remain in
Git history and are retained here for provenance:

| SY commit | Komikku commit |
| --- | --- |
| `2e77e33538c1510efc3916b2edb0daa6284f6fcd` | `0a87df9feb` |
| `e3d052922368b51fbcf28e63ac901acbc520db16` | `97dc173791` |
| `336f6b7d4eec5ded42c9321c48784da937efb365` | `bec4673603` |
| `f3b74f72d2e4a56da9a0a56bcc8b86f7132b5792` | `0d82b9a45b` |
| `6965f8a81c0779e383ddcf3875c5373ab0396245` | `79d869a625` |
| `9edb24dd102d9d3f3ea6b019115432fdb7ec7dc1` | `424a2aad31` |
| `d282e4ad0a74b10a111c7c1f34fb528386fb585c` | `43dd4859e0` |
| `e8fd1c87dce3f5de40c531e6648395c8a69563cc` | `7bfdf807d1` |
| `57ad4b8d846b0f2b0badf86c000aefd68434076c` | `5e73c5db41` |
| `82ee03df5c765b6c809aa44bf4a06de98adb18ff` | `62682f288f` |
| `8717f578fac5a9cfcfc7ff19da26b3fddb6659cf` | `0e4b4287a9` |
| `148496e754d1f59a66cb5f335f58c4d0d73c2ad0` | `dc0675ba01` |
| `8e3a32f983277e50d5b39818d1385f3b4a345423` | `f470161a30` |
| `28e61a1dd69830ee47fb663111cec4b70ed9d1fa` | `aa024c5aeb` |
| `5621751c15585be94dc72607f2d7d277a79b81a3` | `394cb8369d` |

`c9b9ca2c9d` is intentionally absent: it is a Komikku follow-up adaptation,
not an upstream cherry-pick.
