# TachiyomiSY synchronization summary

## Refreshed baseline

The audit was rebased on the current synchronized heads instead of reusing the
previous 1,019-entry snapshot.

| Repository | Commit |
| --- | --- |
| Komikku base (`main`) | `499f3cba2debd9a781e174211f3c51b8907a8318` |
| TachiyomiSY (`sy/master`) | `5a469745d39e8f8cea9a1669bea6108cd71dc951` |
| Mihon (`mihon/main`) | `446f6df82c390ab760802c2fce3b677bce3f31c1` |

`git cherry -v main sy/master` reports 1,507 topology candidates: 1,029
patch-absent (`+`) and 478 patch-equivalent (`-`). A patch-absent result is an
audit input, not evidence that the feature is semantically missing; conflict
resolution, reorganized code, and Komikku features change patch identities.

## Completed in this synchronization batch

| Outcome | Count |
| --- | ---: |
| Verified `git cherry-pick -x` ports | 4 |
| Behaviorally equivalent candidates reconfirmed | 8 |
| Dependency chains blocked or obsolete | 3 |
| Remaining patch-absent review queue | 1,029 |

The four completed ports are listed with their immutable provenance in
[`verified-sy-ports.md`](verified-sy-ports.md). The candidate inventory and
queue are [`missing-sy-commits.txt`](missing-sy-commits.txt) and
[`remaining-sy-commits.txt`](remaining-sy-commits.txt). They intentionally
retain adapted cherry-picks until semantic review removes them.

## Validation

- `:data:generateSqlDelightInterface` passed after the Upcoming query change.
- `spotlessApply` and `spotlessCheck` passed.
- `:app:compileDebugKotlin` passed after resolving two current-architecture
  seams in the upstream ports.
- `assembleDebug` passed.
- `:data:testReleaseUnitTest` passed.

Device runtime testing was not available. The APK and unit-test checks verify
integration and database query generation, but not tracker-server responses or
interactive calendar filtering on a device.
