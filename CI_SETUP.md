# CI Setup Guide

How GitHub Actions is set up for **xkana-shii/komikku**, which repository secrets
it uses, how releases are cut, and how to verify each workflow.

xkana-shii/komikku is a personal fork of [Komikku](https://github.com/komikku-app/komikku)
that also imports [Komi-kun](https://github.com/komi-kun/komi-kun)'s architecture
(see [`FORK.md`](FORK.md)). The workflows under `.github/workflows/` come from
Komikku/Mihon through Komi-kun and are adapted to this repository's existing
release infrastructure. This guide describes their state on the branch it ships on.

- **Repo:** `xkana-shii/komikku`
- **Default branch:** `main`
- **Preview/nightly repo:** `xkana-shii/komikku-preview` (separate repository, own workflow)
- **App id:** `app.komikku.kns` (`.dev` debug, `.beta` nightly, `.rt`, `.foss`, `.benchmark`)
- **Version line:** `versionCode`/`versionName` in `app/build.gradle.kts`, bumped by `bump.yml`
- **JDK:** 21 (`.github/.java-version`); the Gradle daemon toolchain is pinned in
  `gradle/gradle-daemon-jvm.properties` (resolved through the foojay plugin)
- **Gradle:** wrapper version in `gradle/wrapper/gradle-wrapper.properties`

> **Scope note:** none of this requires editing Kotlin/Gradle. The build supports
> the `-Pdist` / `MIHON_GITHUB_RELEASE` / env-var signing contract the workflows
> use. The things to configure are repo settings and secrets.

---

## 0. TL;DR checklist

1. [ ] Actions enabled on the repository (already true for this fork).
2. [ ] Default branch `main`; public repo or Dependency Graph enabled.
3. [ ] The 4 signing secrets exist (`SIGNING_KEY`, `KEY_STORE_PASSWORD`, `ALIAS`,
   `KEY_PASSWORD`). The same keystore must keep signing every release, or installed
   apps cannot update.
4. [ ] `BOT_PAT` exists (bump/tag workflow and the preview dispatch need it).
5. [ ] `DEV_SECRETS` exists if you use it (passed as `-PdevSecret`; see §3.4).
6. [ ] The 2 Google secrets are **optional**.
7. [ ] Leave Actions workflow permissions at "Read repository contents".
8. [ ] Release path: **Actions → Spotless, bump version, and tag → Run workflow** on `main`.

---

## 1. Enable Actions

Forks ship with workflows **disabled** until the owner acknowledges them. This
repository already has them enabled. On a fresh fork:

1. Open `https://github.com/xkana-shii/komikku/actions`.
2. Click **"I understand my workflows, go ahead and enable them"**.

To stop a single workflow without deleting it, use
**Actions → <workflow> → ⋯ → Disable workflow**. Two upstream-only workflows are
disabled by renaming them to `*.yml.disabled` — see
[§4](#4-which-workflows-are-active-disabled-and-why).

---

## 2. Repository settings

### 2.1 Default branch

The default branch is **`main`**. Note the inherited triggers:

- `build_push.yml` (CI) runs on pushes to **`master`** only, and the Nightly
  Builder's build job only runs for `refs/heads/master`. On this repository they are
  therefore dormant unless started from a `master` branch. This is unchanged from
  the previous `main` of this fork; switch the branch names to `main` in those two
  workflows if you want push CI / in-repo nightlies.
- `bump.yml`, `build_release.yml` (tags `v*`), `build_dispatch_preview.yml` and
  `build_pull_request.yml` work from `main`.

### 2.2 Dependency Graph (for `dependency-review-action`)

`build_pull_request.yml` runs `actions/dependency-review-action`, which needs the
**Dependency Graph**.

- **Public repo:** on by default. Nothing to do.
- **Private repo:** `Settings → Code security and analysis → Dependency graph`
  → **Enable**, or remove the "Dependency Review" step from `build_pull_request.yml`.

### 2.3 Actions permissions

**Settings → Actions → General → Workflow permissions:** leave the default
**"Read repository contents and packages permissions"**. The release, nightly and
bump workflows declare `permissions: contents: write` themselves.

### 2.4 Environments / variables

**None required.** No workflow declares an `environment:` block, and all build
flags (`-Pdist=…`, `-Pinclude-telemetry`, `-PdevSecret`, …) are passed inline.

---

## 3. Repository secrets

**Settings → Secrets and variables → Actions.** Values are never printed by the
workflows; don't echo them in new steps either.

| # | Secret name | Required? | Used by | What it is |
|---|-------------|-----------|---------|------------|
| 1 | `SIGNING_KEY` | **Yes** | release (AGP signing), push, PR (same-repo), nightly, benchmark (`r0adkll` signer) | base64 of the release keystore |
| 2 | `KEY_STORE_PASSWORD` | **Yes** | all signing | keystore password |
| 3 | `ALIAS` | **Yes** | all signing | key alias |
| 4 | `KEY_PASSWORD` | **Yes** | all signing | key password |
| 5 | `BOT_PAT` | **Yes** for releases | `bump.yml` (creates the `v*` tag so it triggers `build_release.yml`), `build_dispatch_preview.yml` | personal access token with `contents: write` on this repo and `actions: write` on `xkana-shii/komikku-preview` |
| 6 | `DEV_SECRETS` | No | release, push, nightly (`-PdevSecret`) | value forwarded to the build as the `devSecret` Gradle property |
| 7 | `GOOGLE_SERVICES_JSON` | No | push, PR (same-repo), nightly, release | Firebase `google-services.json` |
| 8 | `GOOGLE_CLIENT_SECRETS_JSON` | No | push, PR (same-repo), nightly, release | OAuth client secrets for Google Drive backup |

### 3.1 Signing secrets

The release workflow signs through AGP: it sets `MIHON_GITHUB_RELEASE=true` and
passes `storeFileBase64`/`storePassword`/`keyAlias`/`keyPassword` from the four
secrets; `app/build.gradle.kts` writes `$RUNNER_TEMP/komikku.keystore` and uses it
for the `release` signing config. The other workflows sign afterwards with
`r0adkll/sign-android-release` using the same secrets. **Keep using the existing
keystore** — a different key makes every installed copy refuse the update.

To create a keystore for a brand-new fork (not needed here):

```bash
keytool -genkeypair -v -keystore release.keystore -alias komikku \
  -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 release.keystore   # value of SIGNING_KEY
```

`*.keystore` is git-ignored; never commit it. `SIGNING_KEY` must be single-line
base64 (`-w0`).

### 3.2 `BOT_PAT`

`GITHUB_TOKEN` pushes do not trigger other workflows, so `bump.yml` creates the
release tag through the REST API with `BOT_PAT` (it creates a release for the tag
and immediately deletes it, leaving the tag), which then triggers
`build_release.yml`. `build_dispatch_preview.yml` uses the same token to dispatch
`build_app.yml` in `xkana-shii/komikku-preview`.

### 3.3 `GOOGLE_SERVICES_JSON` / `GOOGLE_CLIENT_SECRETS_JSON` (optional)

See [`GOOGLE_SERVICES_SETUP.md`](GOOGLE_SERVICES_SETUP.md). When
`GOOGLE_SERVICES_JSON` is **absent**, the workflows skip writing it and add
`-Pinclude-telemetry=false`, so the build needs no Firebase setup. When it is
**present**, `-Pdist=ci`/`github` compile telemetry in and the JSON must contain a
client for every built package (`app.komikku.kns`, `app.komikku.kns.beta`).

> The previous `main` of this fork never built with telemetry (no
> `-Pinclude-telemetry`, no google-services step). Keeping `GOOGLE_SERVICES_JSON`
> unset preserves that. Even when compiled in, Firebase stays inactive for this app:
> `TelemetryConfig` only initializes for `app.komikku`/`app.komikku.beta` signed with
> upstream's certificate.

`GOOGLE_CLIENT_SECRETS_JSON` is written to `app/src/main/assets/client_secrets.json`
when present. Without it the build succeeds but Google Drive backup/sync is
unavailable at runtime.

### 3.4 `DEV_SECRETS`

Forwarded as `-PdevSecret="…"` by the release, push and nightly workflows (as on
the previous `main`, and by the preview repository's `build_app.yml`). The rebuilt
foundation does not read the property yet: the developer-options feature that
consumed it will be re-implemented separately. Passing an unused property is
harmless.

### 3.5 Setting secrets with the `gh` CLI

This checkout usually has several remotes (`origin`, `komikku`, `komikun`, `mihon`,
`tachiyomiSY`), so pass `--repo`:

```bash
REPO=xkana-shii/komikku
base64 -w0 release.keystore | gh secret set SIGNING_KEY --repo "$REPO"
gh secret set KEY_STORE_PASSWORD --repo "$REPO" --body 'YOUR_STORE_PASSWORD'
gh secret set KEY_PASSWORD       --repo "$REPO" --body 'YOUR_KEY_PASSWORD'
gh secret set ALIAS              --repo "$REPO" --body 'YOUR_ALIAS'
gh secret set GOOGLE_CLIENT_SECRETS_JSON --repo "$REPO" < path/to/client_secrets.json
gh secret list --repo "$REPO"     # names + dates only; values are never shown
```

### 3.6 Secrets you do **not** need

- `CODEBERG_SSH` — belonged to the disabled Codeberg mirror workflow.

---

## 4. Which workflows are active, disabled, and why

| Workflow (name) | Status | Notes |
|----------|--------|-------|
| `bump.yml` (Spotless, bump version, and tag) | **Release entry point** | Manual. Runs `spotlessApply` (skipped when the last commit message has `[no spotless]`), commits the result, runs `.github/scripts/bump_version.sh` (versionCode +1, versionName patch +1, rolling to the next minor after `.10`), commits, then creates `v<versionName>` with `BOT_PAT`. |
| `build_release.yml` (Release Builder) | **Keep** | `v*` tag → `spotlessCheck` → `assembleRelease -Pdist=github` with AGP signing → `testDebugUnitTest` → **regular (non-prerelease) release** with 5 APKs. |
| `build_dispatch_preview.yml` (Remote Dispatch Preview Build) | **Keep** | Manual. Dispatches `build_app.yml` in `xkana-shii/komikku-preview` for the current branch. |
| `build_pull_request.yml` (PR build check) | **Keep** | `pull_request`: dependency review → wrapper validation → `spotlessCheck` → `assembleNightly` → `testDebugUnitTest` → `:data:verifyDebugDatabaseMigration` → sign (same-repo PRs). |
| `build_push.yml` (CI) | Dormant here | Runs on pushes to `master` (see §2.1) or manually. Same checks as the PR job. |
| `build_nightly.yml` (Nightly Builder) | Dormant here | Komikku's preview builder, renamed with the `preview` → `nightly` build type. Manual, `master`-only build job; tags `r<count>` and publishes in this repository. Previews normally come from the preview repository instead. |
| `build_benchmark.yml` (Benchmark Builder) | **Keep** (optional) | Manual benchmark build; needs only the signing secrets. |
| `pr_label.yml` (Label PRs) | **Keep** | Labels Weblate-authored PRs. |
| `codeberg_mirror.yml.disabled` | **Disabled** | Guarded to `komikku-app/komikku`; mirrors Komikku to Codeberg. |
| `update_website.yml.disabled` | **Disabled** | Dispatches into `komikku-app/komikku-app.github.io`, which this repository's token cannot reach; it would fail on every release. |
| `.github/dependabot.yml` | **Keep** | Weekly GitHub Actions update PRs against `main`. |

### 4.1 The preview repository after the foundation lands on `main`

`xkana-shii/komikku-preview`'s `build_app.yml` checks out this repository and runs
`./gradlew assemblePreview`, then signs `app/build/outputs/apk/preview/*-preview-*.apk`.
The integrated foundation follows Mihon/Komi-kun in renaming the build type
`preview` → **`nightly`** (still `.beta`, still `r<count>` tags, read by the
in-app updater from `xkana-shii/komikku-preview`). Before dispatching a preview
build of the new foundation, update that workflow in the preview repository:

- `assemblePreview` → `assembleNightly`
- `apk/preview` → `apk/nightly`, and `*-preview-signed.apk` → `*-nightly-signed.apk`
  in the rename step (`artifacts_prefix=nightly/app`).
- Optionally add `-Pdist=ci -Pinclude-telemetry=false` to match this repository's
  CI builds (the updater is enabled either way through `-Penable-updater`).

Builds of the old `main` keep working with the old workflow until then.

---

## 5. How to test each workflow

### 5.1 Same-repo PR (`build_pull_request.yml`)

1. `git checkout -b chore/ci-smoke-test main`, push, open a PR against `main` in
   this repository.
2. Expected artifact: **`Komikku-p<PR number>-r<count>.apk`** plus `mapping-<sha>`;
   on test failure also `test-report-<sha>`.
3. Close the PR without merging.

### 5.2 Release (`bump.yml` → `build_release.yml`)

1. **Actions → Spotless, bump version, and tag → Run workflow** on `main`.
2. Expected: optional `chore: spotlessApply` commit, `chore: bump version` commit,
   tag `v<versionName>`.
3. The tag starts **Release Builder**: assets `Komikku-v<version>.apk`,
   `Komikku-arm64-v8a-v<version>.apk`, `Komikku-armeabi-v7a-v<version>.apk`,
   `Komikku-x86-v<version>.apk`, `Komikku-x86_64-v<version>.apk`, published as a
   regular release (the in-app updater of release builds reads `v*` releases from
   this repository).

> Do **not** cut a release from the rebuilt foundation until the database upgrade
> path from the previous app is resolved — see `docs/komikun-foundation-progress.md`
> ("Old-fork database compatibility").

### 5.3 Preview (`build_dispatch_preview.yml`)

**Actions → Remote Dispatch Preview Build → Run workflow** on the branch to build.
See §4.1 first.

---

## 6. Common failure modes

| Symptom | Cause | Fix |
|---------|-------|-----|
| PR job fails at **Dependency Review** | Dependency Graph disabled | Enable it, or remove that step. |
| Build fails applying the **google-services plugin** | `GOOGLE_SERVICES_JSON` present but lacks the variant's package | Add `app.komikku.kns` / `app.komikku.kns.beta` in Firebase, or unset the secret. |
| Release job fails **uploading the Crashlytics mapping** | `GOOGLE_SERVICES_JSON` present and `-Pdist=github` uploads mappings, but Crashlytics is not enabled | Enable Crashlytics, switch the release build to `-Pdist=ci`, or unset the secret. |
| Build fails **signing** | `SIGNING_KEY` not `base64 -w0`, or wrong password/alias | Re-set the secret from the original keystore. |
| `bump.yml` fails at **Tag release** | `BOT_PAT` missing/expired, or the tag already exists | Renew the token; bump again if the tag exists. |
| Release workflow doesn't run after bumping | Tag created with `GITHUB_TOKEN` instead of `BOT_PAT` | Keep the `BOT_PAT` step. |
| Preview dispatch builds fail on the new foundation | Preview repo still runs `assemblePreview` | See §4.1. |

---

## 7. What each `-Pdist` does

Distributions are `LOCAL | CI | GITHUB | FOSS`; an unknown value fails the build.
Default (no flag) is **LOCAL**.

| `-Pdist` | Telemetry | Crashlytics mapping upload | Updater | `google-services.json` needed | Used by |
|----------|-----------|----------------------------|---------|-------------------------------|---------|
| *(none)* = `LOCAL` | off | no | off | no | local builds, fork PRs, benchmark |
| `CI` | on | no | on | **yes**, unless `-Pinclude-telemetry=false` | same-repo PR, push, nightly |
| `GITHUB` | on | **yes** | on | **yes** (Crashlytics-capable), unless `-Pinclude-telemetry=false` | release |
| `FOSS` | off | no | on | no | not used by any workflow |

Derived from `buildSrc/src/main/kotlin/mihon/buildlogic/BuildConfig.kt`:

- `includeTelemetry = -Pinclude-telemetry ?: (dist ∈ {CI, GITHUB})`
- `uploadCrashlyticsMapping = includeTelemetry && dist == GITHUB`
- `enableUpdater = -Penable-updater ?: (dist != LOCAL)`
- `enableCodeShrink = !-Pdisable-code-shrink`
- `includeDependencyInfo = -Pinclude-dependency-info ?: false`

The `debug` build type always compiles `UPDATER_ENABLED=false`.

To force telemetry **off** for `CI`/`GITHUB`, pass `-Pinclude-telemetry=false`
(an empty value counts as *true*). Every workflow that builds with
`-Pdist=ci`/`github` appends it automatically when `GOOGLE_SERVICES_JSON` is absent.
