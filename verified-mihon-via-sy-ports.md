# Verified Mihon changes delivered via TachiyomiSY

TachiyomiSY's history can contain upstream Mihon changes, but provenance is
recorded only when a SY commit itself carries a verifiable Mihon origin.

The three SY commits integrated in this batch (`b9a52f9d`, `a82473af`, and
`82e5e7f6`) have no Mihon cherry-pick trailer in their SY commit bodies. They
are therefore recorded as direct SY ports in `verified-sy-ports.md`, not
misclassified as Mihon-via-SY ports.

No additional Mihon-via-SY port was verified in this batch. This file is kept
separate so later audits can record an original Mihon SHA, the SY carrying SHA,
and the resulting Komikku `-x` commit without conflating upstream provenance.