---
name: nightly-release
description: Cut and verify a nightly build/release for komi-kun. Use when asked to make a release, cut/publish/ship a nightly, produce signed APKs, or check what a release contains. Covers the manual dispatch, the artifacts, and how the in-app updater sees them. For the one-time CI/secrets setup see CI_SETUP.md.
---

# Cutting a nightly release

komi-kun ships **nightlies only** — there is no stable channel and the version numbers are not meaningful. A "release" *is* a nightly: the `Nightly Builder` workflow builds, signs, and publishes a prerelease tagged `r<commit-count>`. The tag release (`build_release.yml`, `v*` tags) is dormant here — don't use it.

## 1. Prerequisites

- The change is merged to `master` — the build job only runs when `github.ref == 'refs/heads/master'`.
- The 4 signing secrets are set (`SIGNING_KEY`, `KEY_STORE_PASSWORD`, `ALIAS`, `KEY_PASSWORD`); see `CI_SETUP.md` §3.
- The 2 Google secrets are optional — without them the build runs with `-Pinclude-telemetry=false`.

## 2. Dispatch it

Actions → **Nightly Builder** → *Run workflow*, branch **`master`**. Or from the CLI:

```bash
gh workflow run build_nightly.yml --ref master
sleep 15
rid=$(gh run list --workflow build_nightly.yml --limit 1 --json databaseId -q '.[0].databaseId')
gh run watch "$rid" --exit-status
```

## 3. What it produces

- Tag **`r<commit-count>`** (pushed by the workflow; the release action creates it if the tag step is skipped).
- A signed `assembleNightly -Pdist=ci`, published as a **non-draft prerelease** with 5 APKs:
  `Komikku-r<count>.apk` (universal) plus `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`.

## 4. How the in-app updater sees it

The updater reads releases from **`komi-kun/komi-kun`**, matches `r*` tags for a nightly build, and offers an update when the release's count is greater than the installed build's `BuildConfig.COMMIT_COUNT`. So installing one nightly is enough — newer nightlies are offered by the manual "Check for updates" and the 3-day periodic job.

## 5. Verify / clean up

```bash
gh release list --limit 5
gh release view "r<count>" --json tagName,isPrerelease,assets
# smoke test? remove it and its tag:
gh release delete "r<count>" --yes --cleanup-tag
```

## Notes

- **Don't bump `versionCode`/`versionName`** for a nightly: `versionName` becomes `1.14.1-<commit-count>` automatically, and Android accepts an equal-`versionCode` install when the signature matches.
- Never push to `master` by hand; land changes through a PR (the PR check runs the same build plus tests and the migration gate).
