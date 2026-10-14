# CI Setup Guide

How to enable GitHub Actions for **komi-kun**, add the repository secrets,
configure repo settings, and verify each workflow.

komi-kun is a personal fork of [Komikku](https://github.com/komikku-app/komikku)
(`komi-kun/komi-kun`, app id `app.komikun`). The workflows under
`.github/workflows/` are inherited from Komikku/Mihon; this guide describes their
current state in this repo.

- **Repo:** `komi-kun/komi-kun`
- **Default branch:** `master`
- **App id:** `app.komikun`
- **JDK:** 21 (`.github/.java-version`)
- **Gradle:** 9.8.1 (wrapper)

> **Scope note:** none of this requires editing Kotlin/Gradle. The build already
> supports the `-Pdist` / `MIHON_GITHUB_RELEASE` / env-var signing contract the
> workflows use. The only things to configure are repo settings and secrets.

---

## 0. TL;DR checklist

1. [ ] Enable Actions on the fork (one-time UI acknowledgement).
2. [ ] Confirm default branch = `master`; make the repo public or enable Dependency Graph.
3. [ ] Add the 4 signing secrets. The 2 Google secrets are **optional** — CI is
   green without them.
4. [ ] Leave Actions workflow permissions at "Read repository contents".
5. [ ] No environments / no Actions variables required.
6. [ ] Test: same-repo PR → push to `master` → `Nightly Builder`. The nightly is
   **the release path** here; the `v*` tag release is optional and normally unused
   (see [§4](#4-which-workflows-are-active-disabled-and-why)).

---

## 1. Enable Actions for the fork

Forks ship with workflows **disabled** until the owner acknowledges them.

1. Open `https://github.com/komi-kun/komi-kun/actions`.
2. Click the green **"I understand my workflows, go ahead and enable them"**
   button.
3. After this, any workflow with a matching trigger runs normally
   (`push`, `pull_request`, `tags`, `workflow_dispatch`).

If you later want to stop a single workflow without deleting it, use
**Actions → <workflow> → ⋯ → Disable workflow**. Three upstream-only workflows
are already disabled by renaming them to `*.yml.disabled` — see
[§4](#4-which-workflows-are-active-disabled-and-why).

---

## 2. Repository settings

Go to **Settings**.

### 2.1 Default branch

**Settings → General → Default branch** must be **`master`**. The push, nightly,
and release workflows all key off `master`. (Already true for this fork.)

### 2.2 Dependency Graph (for `dependency-review-action`)

`build_pull_request.yml` runs `actions/dependency-review-action`, which needs the
**Dependency Graph**.

- **Public repo (recommended):** Dependency Graph is on by default. Nothing to do.
- **Private repo:** `Settings → Code security and analysis → Dependency graph`
  → **Enable**. If you don't want it, remove the "Dependency Review" step from
  `build_pull_request.yml` instead, or the PR job fails at that step.

### 2.3 Actions permissions

**Settings → Actions → General → Workflow permissions:** leave the default
**"Read repository contents and packages permissions"**.

Do **not** switch to "Read and write permissions" globally. The release and
nightly workflows declare their own `permissions: contents: write` at the
workflow level, which is the least-privilege way to let them create releases.

### 2.4 Environments

**No environments are needed.** No workflow declares an `environment:` block, so
there are no environment-scoped secrets or protection rules to configure.

### 2.5 Actions variables

**None required.** All build flags (`-Pdist=…`, `-Pinclude-telemetry`,
`-Penable-updater`, …) are passed inline in the workflow commands, not via repo
variables.

---

## 3. Repository secrets

**Settings → Secrets and variables → Actions → New repository secret.**

Add the **4 signing secrets** below. The **2 Google secrets are optional**: the
workflows check for `GOOGLE_SERVICES_JSON` at runtime and, when it is absent,
skip writing the Google files and pass `-Pinclude-telemetry=false` so the build
stays green (see [§7](#7-what-each--pdist-does)).

| # | Secret name | Required? | Used by | What it is |
|---|-------------|-----------|---------|------------|
| 1 | `SIGNING_KEY` | **Yes** | push, PR (same-repo), nightly, release, benchmark | base64 of the release keystore |
| 2 | `KEY_STORE_PASSWORD` | **Yes** | all signing | keystore password |
| 3 | `ALIAS` | **Yes** | all signing | key alias |
| 4 | `KEY_PASSWORD` | **Yes** | all signing | key password |
| 5 | `GOOGLE_SERVICES_JSON` | No | push, PR (same-repo), nightly, release | Firebase `google-services.json` for `app.komikun` |
| 6 | `GOOGLE_CLIENT_SECRETS_JSON` | No | push, PR (same-repo), nightly, release | OAuth client secrets (trackers) |

> **Current state of this fork:** the four signing secrets are set; neither
> Google secret is set yet (no Firebase project exists). That is why the
> telemetry flag is `-Pinclude-telemetry=false` and CI is green. See
> [`GOOGLE_SERVICES_SETUP.md`](GOOGLE_SERVICES_SETUP.md) if you later want to
> enable telemetry or Google Drive backup.

### 3.1 `SIGNING_KEY`, `KEY_STORE_PASSWORD`, `ALIAS`, `KEY_PASSWORD`

Create (or reuse) an Android release keystore:

```bash
keytool -genkeypair -v \
  -keystore release.keystore \
  -alias komikku \
  -keyalg RSA -keysize 4096 -validity 10000
```

The prompts set your **store password** and **key password**. Pick an alias
(the example uses `komikku`).

Generate each secret value:

```bash
# SIGNING_KEY — single line, no wrapping newlines
base64 -w0 release.keystore

# KEY_STORE_PASSWORD — the store password you entered
# ALIAS              — the alias you entered (e.g. komikku)
# KEY_PASSWORD       — the key password you entered
```

Paste the `base64 -w0` output as the value of `SIGNING_KEY`. Keep
`release.keystore` somewhere safe — it is git-ignored (`*.keystore`) and must
never be committed.

> **Why base64:** `SIGNING_KEY` is consumed two ways. The `r0adkll` signer
> (`push`/`PR`/`nightly`/`benchmark`) reads it as `signingKeyBase64`; the release
> workflow passes it as the `storeFileBase64` env var so the AGP signing config
> writes `$RUNNER_TEMP/komikku.keystore`. Both expect the raw base64 of the
> keystore file with no line breaks, hence `-w0`.

### 3.2 `GOOGLE_SERVICES_JSON` (optional)

Contents of a **valid Firebase `google-services.json`**, written to
`app/google-services.json` at build time. Add it only if you want telemetry
compiled in.

- The `package_name` inside must match every variant you build **with telemetry
  on**. CI builds the **`nightly`** variant (`app.komikun.beta`) for PR/push/
  nightly and the **`release`** variant (`app.komikun`) for tags, so include at
  least both (`app.komikun.dev` too if you want local debug telemetry to resolve).
- When this secret is present, the workflows write the file and build with
  `-Pdist=ci`/`github`, which applies the Google Services (and Crashlytics)
  plugins. A missing client for the variant's package then fails the build.
- When this secret is **absent**, the workflows skip the write and add
  `-Pinclude-telemetry=false`, so the plugins are never applied and the build
  succeeds without the file. No configuration is required.
- **Crashlytics:** `-Pdist=github` (used only by the release workflow) sets
  `uploadCrashlyticsMapping = true`. If you keep `-Pdist=github` in release, the
  Firebase project must have **Crashlytics enabled**, or the release job fails
  while uploading the mapping. If you don't use Crashlytics, switch the release
  build to `-Pdist=ci` or keep the secret unset (telemetry off). See
  [§7](#7-what-each--pdist-does).

### 3.3 `GOOGLE_CLIENT_SECRETS_JSON` (optional)

Contents of the OAuth client-secrets JSON used by Google Drive backup, written to
`app/src/main/assets/client_secrets.json`.

- Absence is **not** a build failure. Without it, Google Drive backup/sync is
  unavailable at runtime (it throws when it tries to read the file). If you don't
  use Drive backup, leave it unset.

### 3.4 Setting the secrets with the `gh` CLI

If you have the [GitHub CLI](https://cli.github.com/) installed, you can skip the
web UI entirely. Run these from the repo root.

> **This checkout has multiple remotes** (`origin`, `mihon`, `tachiyomiSY`,
> `komikku`), so `gh` cannot infer the target — every command needs `--repo`
> (or `-R`). Use `komi-kun/komi-kun`.

```bash
# one-time
gh auth login

REPO=komi-kun/komi-kun

# 1-4: signing. SIGNING_KEY must be single-line base64, so pipe it in.
base64 -w0 release.keystore | gh secret set SIGNING_KEY --repo "$REPO"
gh secret set KEY_STORE_PASSWORD --repo "$REPO" --body 'YOUR_STORE_PASSWORD'
gh secret set KEY_PASSWORD       --repo "$REPO" --body 'YOUR_KEY_PASSWORD'   # same as the store password for PKCS12
gh secret set ALIAS              --repo "$REPO" --body 'komikku'

# 5-6 (optional): Google JSON files -- pipe the file's exact bytes as the secret value.
gh secret set GOOGLE_SERVICES_JSON       --repo "$REPO" < app/google-services.json
gh secret set GOOGLE_CLIENT_SECRETS_JSON --repo "$REPO" < path/to/client_secrets.json

# verify (lists names + updated dates; values are never printed)
gh secret list --repo "$REPO"
```

Notes:

- `--repo` is required here (multiple remotes); if you only had one it could be
  omitted.
- `gh secret set NAME < file` stores the file's exact bytes. For `SIGNING_KEY`
  use the `base64 -w0 … | gh secret set` form so there are no wrapping newlines.
- Secrets set via `gh` are identical to ones added in the web UI.
- Overwrite with `gh secret set NAME …`; remove with `gh secret delete NAME`.
- For an organisation repo, add `--org <org>` (or `--env <env>` for an
  environment secret) instead of `--repo`.

### 3.5 Secrets you do **not** need

- `BOT_PAT` — belonged to the disabled upstream-dispatch workflows.
- `CODEBERG_SSH` — belonged to the disabled mirror workflow.

Do not add these unless you deliberately re-purpose those workflows.

---

## 4. Which workflows are active, disabled, and why

| Workflow (name) | Status | Notes |
|----------|--------|-------|
| `build_pull_request.yml` (PR build check) | **Keep** | Safe: uses `pull_request` (not `pull_request_target`). Fork PRs build unsigned with no secrets; same-repo PRs use the secrets when present. |
| `build_push.yml` (CI) | **Keep** | Runs on `master` pushes. Runs `spotlessCheck`, `assembleNightly`, unit tests, and the SQLDelight migration gate. |
| `build_nightly.yml` (Nightly Builder) | **Keep — the release path** | Manual, `master`-only. Auto-tags `r<count>` and publishes a **non-draft prerelease** with 5 APKs. Requires the 4 signing secrets. This is how releases are cut here. |
| `build_release.yml` (Release Builder) | **Keep** (dormant) | `v*` tag → non-draft prerelease, 5 APKs, AGP signing. Only fires on `v*` tags; this fork ships nightlies, not stable releases, so it is normally unused. Needs a Crashlytics-capable config for `-Pdist=github` (or telemetry off). |
| `build_benchmark.yml` (Benchmark Builder) | **Keep** (optional) | Manual benchmark build; needs only the 4 signing secrets. Niche. |
| `pr_label.yml` (Label PRs) | **Keep** (low value) | Labels Weblate-authored PRs `translations`. Harmless, safe. |
| `build_dispatch_preview.yml.disabled` | **Disabled** | Cross-repo dispatch to upstream `komikku-app/komikku-preview` with `BOT_PAT` — cannot work on the fork. |
| `codeberg_mirror.yml.disabled` | **Disabled** | Guarded `github.repository == 'komikku-app/komikku'` → permanent no-op; mirrors to an upstream Codeberg repo. |
| `update_website.yml.disabled` | **Disabled** | Cross-repo dispatch to upstream `komikku-app/komikku-app.github.io` with `BOT_PAT` — cannot work. |
| `.github/dependabot.yml` | **Keep** | Opens weekly GitHub Actions dependency PRs against `master`. |

Renaming to `*.yml.disabled` keeps the file in git but out of GitHub Actions,
which only loads `.yml`/`.yaml` files in `.github/workflows/`. To re-enable one,
rename it back to `.yml` **and** fix its upstream URLs/guards first.

---

## 5. How to test each workflow

Do the setup in [§1](#1-enable-actions-for-the-fork)–[§3](#3-repository-secrets)
first. Then walk through these in order.

### 5.1 Throwaway same-repo PR (exercises `build_pull_request.yml`)

1. Branch off `master`: `git checkout -b chore/ci-smoke-test`.
2. Push the branch to `komi-kun/komi-kun` and open a PR **against `master` in the
   same repo** (not a fork).
3. Expected steps: Dependency Review → Validate Gradle Wrapper → Set up JDK →
   Check optional secrets → (write Google files, if the secrets exist) →
   `spotlessCheck` → `assembleNightly -Pdist=ci` → `testDebugUnitTest` →
   `:data:verifyDebugDatabaseMigration` → sign → rename → upload APK + mapping.
4. Expected artifact: **`Komikku-p<PR number>-r<count>.apk`** (e.g.
   `Komikku-p123-r85.apk`), plus **`mapping-<sha>`**. On test failure,
   **`test-report-<sha>`** is also uploaded.
5. Close the PR and delete the branch when done; don't merge it.

> A PR **from a fork** (e.g. an upstream contributor) runs the same job but with
> **no secrets** and plain `assembleNightly` (no `-Pdist`), so it builds unsigned
> with telemetry off. That path needs no configuration.

### 5.2 Push to `master` (exercises `build_push.yml`)

1. Push one non-`.md` commit to `master` (or trigger via
   **Actions → CI → Run workflow**).
2. Expected: same chain as the PR job, ending in the sign + rename + upload.
3. Expected artifact: **`Komikku-master-r<count>.apk`** and `mapping-<sha>`.

### 5.3 Nightly (`build_nightly.yml`) — **the release path**

1. **Actions → Nightly Builder → Run workflow**, choose branch **`master`**.
   (The `build-app` job only runs when `github.ref == 'refs/heads/master'`.)
2. Expected: creates tag `r<commit-count>`, builds `assembleNightly -Pdist=ci`,
   signs with `r0adkll`, publishes a **non-draft prerelease**.
3. Expected assets (5): **`Komikku-r<count>.apk`**, `Komikku-arm64-v8a-r<count>.apk`,
   `Komikku-armeabi-v7a-r<count>.apk`, `Komikku-x86-r<count>.apk`,
   `Komikku-x86_64-r<count>.apk`.
4. Visit **Releases** to confirm it, then delete it (and the auto-tag) if it was
   just a smoke test.

### 5.4 Tag release (`build_release.yml`) — optional / normally unused

1. Tag `master` and push the tag:
   ```bash
   git tag v1.14.1-ci-test
   git push origin v1.14.1-ci-test
   ```
   (Tags must start with `v`.)
2. Expected: `prepare-build` (changelog compare) → `assembleRelease -Pdist=github`
   with AGP signing → non-draft prerelease.
3. Expected assets (5): **`Komikku-v1.14.1-ci-test.apk`**,
   `Komikku-arm64-v8a-v1.14.1-ci-test.apk`,
   `Komikku-armeabi-v7a-v1.14.1-ci-test.apk`,
   `Komikku-x86-v1.14.1-ci-test.apk`,
   `Komikku-x86_64-v1.14.1-ci-test.apk`.
4. Delete the tag/release after verifying.

> This fork has no stable channel: both the nightly and the tag release are
> published as **prereleases**, and the in-app updater offers the newest one.

### 5.5 Benchmark (exercises `build_benchmark.yml`, optional)

**Actions → Benchmark Builder → Run workflow.** Builds `assembleBenchmark`,
signs with `r0adkll`, uploads APK artifacts (retained 30 days). Needs only the 4
signing secrets.

---

## 6. Common failure modes

| Symptom | Cause | Fix |
|---------|-------|-----|
| PR job fails at **Dependency Review** | Dependency Graph disabled (private repo) | Enable Dependency Graph, or remove that step from `build_pull_request.yml`. |
| Same-repo PR / push / nightly build fails applying the **google-services plugin** | `GOOGLE_SERVICES_JSON` present but the JSON lacks a client for the variant package (`app.komikun.beta` for nightly, `app.komikun` for release) | Add the missing package in Firebase and re-download the combined JSON; or unset the secret to build with telemetry off. |
| Release job fails **uploading the Crashlytics mapping** | `-Pdist=github` sets `uploadCrashlyticsMapping=true`, but the Firebase project has no Crashlytics | Enable Crashlytics in Firebase, switch the release build to `-Pdist=ci`, or leave `GOOGLE_SERVICES_JSON` unset. |
| Build fails **signing** | Wrong `SIGNING_KEY` (not `base64 -w0`), or wrong password/alias | Regenerate: `base64 -w0 release.keystore`; verify `KEY_STORE_PASSWORD`/`ALIAS`/`KEY_PASSWORD` match the keystore. |
| Nightly/release job fails at **Sign APK** | The 4 signing secrets are missing | Add `SIGNING_KEY`, `KEY_STORE_PASSWORD`, `ALIAS`, `KEY_PASSWORD`. |
| Google Drive backup broken at runtime (not a build failure) | `GOOGLE_CLIENT_SECRETS_JSON` missing | Add the client-secrets JSON; see [`GOOGLE_SERVICES_SETUP.md`](GOOGLE_SERVICES_SETUP.md). |
| Nightly/release `workflow_dispatch` does nothing | Triggered from a non-`master` branch | Run from `master`. |
| Release workflow doesn't run on a tag | Tag doesn't start with `v` | Use `v*` tags. |
| `resource not accessible by integration` when creating a release | Missing `contents: write` | The workflows already declare it; ensure you didn't override repo-level permissions to "read only". |

---

## 7. What each `-Pdist` does

Distributions are `LOCAL | CI | GITHUB | FOSS`; an unknown value fails the build
with `error(...)`. Default (no flag) is **LOCAL**.

| `-Pdist` | Telemetry | Crashlytics mapping upload | Updater | `google-services.json` needed | Used by |
|----------|-----------|----------------------------|---------|-------------------------------|---------|
| *(none)* = `LOCAL` | off | no | off | no | fork PRs, benchmark |
| `CI` | on | no | on | **yes**, unless the workflow passes `-Pinclude-telemetry=false` | same-repo PR, `push`, nightly |
| `GITHUB` | on | **yes** | on | **yes** (Crashlytics-capable), unless the workflow passes `-Pinclude-telemetry=false` | release |
| `FOSS` | off | no | on | no | not used by any workflow |

Derived from `buildSrc/src/main/kotlin/mihon/buildlogic/BuildConfig.kt`:

- `includeTelemetry = -Pinclude-telemetry ?: (dist ∈ {CI, GITHUB})`
- `uploadCrashlyticsMapping = includeTelemetry && dist == GITHUB`
- `enableUpdater = -Penable-updater ?: (dist != LOCAL)`
- `enableCodeShrink = !-Pdisable-code-shrink`
- `includeDependencyInfo = -Pinclude-dependency-info ?: false`

To force telemetry **off** even for `CI`/`GITHUB`, pass
`-Pinclude-telemetry=false` (note: an empty value counts as *true*). Every
workflow that builds with `-Pdist=ci`/`github` appends this flag automatically
when `GOOGLE_SERVICES_JSON` is absent, which is why the same-repo CI paths are
green without any Google setup.
