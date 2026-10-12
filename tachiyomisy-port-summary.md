# TachiyomiSY port summary

## Performance

- No commits ported in this batch.

## Database

- No commits ported in this batch.

## Stability and crash fixes

- Cherry-picked TachiyomiSY 2e77e33538c1510efc3916b2edb0daa6284f6fcd: suppresses R8 warnings for optional OEM Window extensions. Komikku commit: 0a87df9feb.
- Cherry-picked TachiyomiSY e3d052922368b51fbcf28e63ac901acbc520db16: crash logs include verbose output when enhanced-source logging is enabled. Adapted to Komikku current EHLogLevel API. Komikku commit: 97dc173791.

## Reader and chapters

- Classified TachiyomiSY 134ec7a0b49aadfc1e507917a2979cd1f5d7d257 as equivalent: Komikku already handles invalid launch arguments and ViewModel initialization failures.
- Classified resumable-download progress commit 2c03122571afd23905dbbc8d551787a4c1cc5cea as reverted upstream; resumable downloads were disabled by c148d18c01 after prerequisite 6856da0d5b and fix 7220723afe.

## Library and Updates

- No commits ported in this batch.

## History

- No commits ported in this batch.

## Tracking

- Ported TachiyomiSY f3d5f0fad9663788376dcad2a02e2296889c98db: MangaBaka date-only API values now retain their calendar day in negative time zones. Komikku commit: 5f7a4f83f5.
- Classified TachiyomiSY c12919958b22cb9820013a219aa33e88f1554722 as equivalent: Komikku already converted selected MangaBaka score indices to their configured score values.

## Downloads

- No commits ported in this batch.

## Extensions and sources

- No commits ported in this batch.

## Backup and restore

- No commits ported in this batch.

## UI and navigation

- No commits ported in this batch.

## Other changes

- 1,011 patch-absent candidates await semantic review. See tachiyomisy-port-audit.md for the complete inventory and evidence status.
