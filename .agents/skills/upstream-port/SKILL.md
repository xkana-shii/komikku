---
name: upstream-port
description: Port one or more upstream commits (Komi-kun / Mihon / TachiyomiSY) into xkana-shii/komikku on a feature branch — decide whether a commit is worth picking, cherry-pick it, adapt it to the fork, apply the correct provenance markers, verify the build, and open the PR. Use when asked to pull in / port / cherry-pick upstream changes, act on cherrypick_log.md rows, or bring a Komi-kun/Mihon/TachiyomiSY feature into this fork. For the direct upstream (Komikku) use the komikku-sync skill instead.
---

# Porting upstream commits into xkana-shii/komikku

The `cherrypick-log` skill answers *"which upstream commits are not in the fork yet?"*; this skill is
the other half: *"take the ones that make sense and bring them in."* Read `FORK.md` ("Porting
workflow", "Provenance markers") and `AGENTS.md` ("Fork markers", "Internationalization") first —
they are the rulebook.

Upstreams (see `FORK.md`): `komikun` (`komikun/master`, Komi-kun — the additional upstream whose
implementation wins conflicts with Komikku), `mihon` (`mihon/main`), `tachiyomiSY`
(`tachiyomiSY/master`), and the direct upstream `komikku` (`komikku/master`, handled by
`komikku-sync`).

## 1. Decide what to pick

```bash
python3 .agents/skills/cherrypick-log/update_cherrypick_log.py          # refresh the log
python3 .agents/skills/cherrypick-log/update_cherrypick_log.py uncertain --json   # the '?' rows
```

- Review the `?` rows with the `cherrypick-log` skill; a row already `O` is in the fork — skip it.
- **Prefer the TachiyomiSY copy** of a Mihon commit when one exists (the `↳ tachiyomiSY` row):
  Komikku descends from SY, so the SY-adapted version conflicts far less.
- Skip commits whose feature already exists (Komi-kun ports Mihon directly, so a Komikku/SY commit
  that copies a Mihon commit may already be present under another hash).
- **Komi-kun commits:** skip branding/release-only commits (app name `Komikun`, `app.komikun`, icons,
  nightly-only release workflow, Komi-kun's own OAuth clients) and record them `X` with a reason;
  import the functional part when a commit mixes both.
- **Translation commits are in scope.** “Translations update from Hosted Weblate” commits are ported
  like any other commit — Weblate is not connected to this repo, so skipping them (`X`) would freeze
  the fork's translations. Only Renovate/version-bump bookkeeping is excluded.

## 2. Port on a feature branch

```bash
git checkout -b chore/upstream-<what> main   # the foundation branch until it replaces main
git cherry-pick -x <sha>        # chronological order; earlier commits unblock later ones
```

- Keep the `(cherry picked from commit <sha>)` trailer (that is how the log detects the pick).
- Resolve conflicts by adapting to the fork — **never revert Komikku/SY features**. When a Komi-kun
  commit conflicts with Komikku code, take Komi-kun's complete file from that commit
  (`git checkout <sha> -- <path>`), keep its deletions, and verify against `komikun/master`;
  `-X theirs` alone only picks hunks.
- Repository identity (`app.komikku.kns`, version line, app name), signing, workflows and the
  migration chain are never overwritten by an upstream file; adapt them in a separate `KNS` commit.
- If a port needs local changes, wrap the delta (step 3).

## 3. Markers (mandatory — `AGENTS.md`)

| Code origin | Marker |
|---|---|
| xkana-shii/komikku (this fork) | `// KNS -->` … `// KNS <--` |
| Komi-kun upstream | `// KUN -->` … `// KUN <--` |
| Komikku upstream | `// KMK -->` … `// KMK <--` |
| TachiyomiSY upstream | `// SY -->` … `// SY <--` (legacy `// EXH` too) |
| Mihon (base upstream) | none |

- Wrap every local addition/change in `// KNS`; when you change code inside an inherited `// KUN` /
  `// KMK` / `// SY` block, nest a `// KNS` block inside it.
- Never label non-SY code `// SY`, non-Komikku code `// KMK`, or non-Komi-kun code `// KUN`; never
  mass-rename markers.
- `.sq` / `.sqm` use SQL comments: `-- KNS -->` / `-- KNS <--`.

## 4. Strings and translations

New user-facing strings go to `i18n-kmk/.../base/strings.xml` and are referenced via `KMR` — not
`MR`/`SYMR` (`AGENTS.md`).

**Translate them yourself.** Weblate is not connected to this repo, so a new base string would stay
English forever unless you translate it. Add the string to the non-`base` locales you can translate
confidently, as part of the feature. Missing keys fall back to the base language, so skipping a locale
you are unsure about is safe; never machine-translate into a language you cannot check.

**Upstream translation commits** are ports like any other:

```bash
git cherry-pick -x <translation-commit>
```

The fork's locale files diverge from upstream's in both directions (Komikku kept keys Mihon removed,
and the files are missing newer upstream keys). Run the helper after the cherry-pick stops with
conflicts (or right after a `--no-commit` pick, before adding): it rewrites every moko-resources file
the commit touches — including ones git merged without a conflict — keeping upstream's values,
dropping entries for strings removed from the fork's `base/`, and re-appending the fork-only entries
that still exist in `base/` (including `plurals`):

```bash
python3 .agents/skills/upstream-port/scripts/resolve_i18n_union.py    # all moko files changed by the pick
```

Then check that nothing was lost: every key in the fork's `base` that was translated before must still
be translated, and no file may have duplicate keys or invalid XML. Never drop an existing translation.

Strings for a ported Mihon/SY feature stay in their upstream module (`MR`/`SYMR`): that is an upstream
sync, not new fork code.

## 5. Verify

```bash
./gradlew spotlessApply spotlessCheck          # CI gate
./gradlew :app:compileDebugKotlin              # or assembleDebug for a full check
./gradlew :data:verifyDebugDatabaseMigration   # if .sq / .sqm changed
```

## 6. Log + PR

- Open a PR from the feature branch to `main` (**never** push `main` / `master`).
- After the port is merged, refresh the log so the row flips to `O`, or mark it with the script's
  `mark` command (`cherrypick-log` README) — never hand-edit the table.
- If a whole class of markers was wrong, fix it in the same PR rather than leaving it stale.
