# F-Droid compatibility validation

What `.github/workflows/fdroid-compat.yml` is, what it proves, what it deliberately does not,
and how to read a failure. Registered as **DA-027**.

## Why it exists

F-Droid does not redistribute a copy of our CI's work by accident of trust — it **rebuilds the
tagged commit itself**, in its own environment, and for this app it runs in *reproducible-build
mode*: the fdroiddata recipe carries `Binaries:` + `AllowedAPKSigningKeys`, so F-Droid downloads
our GitHub-signed APK, rebuilds the same commit from source, and publishes **our** APK only if its
own rebuild matches. A mismatch does not delist the app; it silently means that version never
appears in F-Droid.

That makes a green `build.yml` weak evidence. Their build differs from ours in ways that matter:

| | our `build.yml` | F-Droid |
|---|---|---|
| Toolchain | runner JDK + our Gradle wrapper | `fdroidserver:buildserver` image, `gradlew-fdroid` |
| Wrapper jar | used | **deleted** (unverifiable binary), Gradle re-fetched by checksum |
| Signing | none (debug) | none — builds unsigned, then compares to our signed APK |
| Driven by | this repo | the fdroiddata recipe (`subdir`, `gradle:`, `Binaries:`) |
| Caches | warm | cold, clean checkout |

The failure mode this workflow removes is the expensive one: a change lands, a release is tagged,
and the incompatibility surfaces days later on someone else's infrastructure, after the release is
already public.

## Architecture, and why this shape

**Use F-Droid's own tooling; do not model it.** The compatibility build runs in F-Droid's published
`registry.gitlab.com/fdroid/fdroidserver:buildserver` image through their own entry point,
`gradlew-fdroid assembleRelease`. Nothing about their build logic is reimplemented here, so when
their tooling changes, this stage inherits the change instead of drifting from it. The only
preparation applied to the checkout is the two things a buildserver checkout differs by — an
SDK-pointing `local.properties`, and no `gradle-wrapper.jar`.

**Vary the environment, don't repeat one.** Stage 4 compares an APK built normally on a GitHub
runner against the one built in F-Droid's image. Building twice in one environment would prove
determinism, which was never the risk; reproducibility breaks *across* environments, which is
exactly what F-Droid exercises.

**Compare contents, not bytes.** `scripts/fdroid-check.py compare` compares zip-entry CRCs and
skips v1 signature files (v2/v3 signatures live outside the zip). It is therefore signature-blind
by construction — the signed control and the unsigned F-Droid build are directly comparable — and
it uses the same acceptance criterion F-Droid's own comparison does, so it cannot fail on a
difference F-Droid would forgive. `apksigcopier compare` is the tool for the signed-vs-signed case;
both inputs here are ours, so content comparison is the more direct check. `diffoscope` runs only
on failure, as a diagnosis aid, never as the gate.

**One helper script, only for the gaps.** `scripts/fdroid-check.py` has three subcommands and
exists because fdroidserver has no equivalent: `compare` (no upstream tool answers "did these two
builds match" before a release exists), `signing-blocks` (see below), and `metadata` (nothing
upstream checks *our* repo against the recipe *they* build us with). Everything else is official
tooling. Each subcommand runs locally, offline except `metadata`, so any CI failure is reproducible
by hand.

**`fdroid scanner` comes from PyPI, unpinned.** It is the same scanner F-Droid runs against
submissions, and it consults a signature database that upstream updates. Pinning it would freeze
our copy of upstream's opinion — a scanner that lags is a scanner that misses what upstream will
reject.

## What each stage validates

| Stage | Validates | Fails when |
|---|---|---|
| 2 — normal release build | the repo still builds a release APK cleanly, with no build-output cache to hide breakage; signs it with a throwaway runner-local key so the real AGP signing path runs | `Normal release build failed` |
| 3 — F-Droid build | the project builds under fdroidserver's toolchain, and still produces an **unsigned** APK (F-Droid supplies no signing config) | `F-Droid compatibility validation failed` |
| 3 — scanner | `fdroid scanner --exit-code` on the built APK: known non-free classes, extra signing blocks | `F-Droid scanner check failed` |
| 3 — signing blocks | no unexpected APK Signing Block IDs — chiefly AGP's Play dependency-metadata blob `0x504b4453`, which `dependenciesInfo { … = false }` disables (D-137) | `Signing assumption check failed` |
| 3 — metadata | the repo still satisfies the **live** fdroiddata recipe: `subdir` exists, the release asset name still matches `Binaries:`, `versionName` is still tag-shaped, `versionCode` has not gone backwards, no product flavors | `Metadata validation failed` |
| 4 — reproducibility | the normal build and the F-Droid build of one commit contain the same bytes | `Reproducibility validation failed` |

The signing-block check earns its place empirically: `fdroid scanner` was run against a build with
`dependenciesInfo` deliberately re-enabled and **did not flag it** (the blob is legal; it just
ruins reproducibility). This check does — verified against that same artifact.

## What it deliberately does NOT validate

Stating these plainly is the point — a guardrail that is believed to cover more than it does is
worse than none.

- **It is not `fdroid build`.** The full orchestration (metadata-driven clone, source tarball,
  scanner-before-build, the isolated buildserver VM with its network policy) is not reproduced. We
  run the build stage of it, in their image.
- **The real signing key is never involved.** Stage 2 signs with a throwaway key, so
  `AllowedAPKSigningKeys` and the actual signed-release path (`release-signing.yml`) are not
  exercised. Nothing here would catch a key or signing-workflow problem.
- **It cannot prove the third environment.** F-Droid compares *their* build against the APK the
  **GitHub Actions release job** produced. This workflow compares a GitHub runner against their
  image — strong evidence, not proof, that the release job agrees too.
- **No index or listing validation.** Whether the store page renders (icon, changelog, screenshots)
  is not checked here; the fastlane rules live in RUNBOOK playbook 6 and ladder guard 6.
- **No on-device behavior.** There is no emulator in CI; behavior remains owner-verified.
- **`metadata` and `scanner` need the network.** If gitlab.com is unreachable, `metadata` warns and
  passes rather than failing on someone else's outage.

## Reading a failure

Every failing stage annotates with its own **title** — that string, not the job name, is what tells
you which check failed. The run's artifacts (APKs, logs, `reproducibility-report.json`,
`diffoscope-report.html`, `fdroid-scanner.log`) are attached even on failure. Each entry below ends
with the command that reproduces it locally.

- **Normal release build failed** — not an F-Droid problem yet. Fix the ordinary build; the other
  stages cannot be read until this is green.
  ```bash
  ./gradlew :app:assembleRelease --no-build-cache --no-configuration-cache
  ```
- **F-Droid compatibility validation failed** — it builds for us but not for them. Usual causes: an
  AGP/Gradle pair `gradlew-fdroid` will not run, a dependency resolvable only from a repository they
  disallow, a build script that needs the deleted wrapper jar, or a hardcoded signing config (which
  also trips the "did it produce an *unsigned* APK" assertion). Reproduce in a scratch clone — the
  two prep lines mutate the checkout:
  ```bash
  git clone . /tmp/fdroid-repro && cd /tmp/fdroid-repro
  printf 'sdk.dir=/opt/android-sdk\n' | tee local.properties app/local.properties
  rm -f gradle/wrapper/gradle-wrapper.jar
  docker run --rm -v "$PWD":/repo -w /repo/app \
    registry.gitlab.com/fdroid/fdroidserver:buildserver gradlew-fdroid assembleRelease
  ```
- **F-Droid scanner check failed** — fdroidserver's own scanner found something upstream rejects,
  typically a new dependency pulling in known non-free code or a binary committed to the repo.
  Needs `ANDROID_HOME` and build-tools on `PATH` for `dexdump`/`apksigner`:
  ```bash
  pipx install fdroidserver
  fdroid scanner --exit-code app/build/outputs/apk/release/app-release.apk
  ```
- **Reproducibility validation failed** — two builds of one commit disagree; that difference would
  appear on F-Droid's side too and stall the next release. `reproducibility-report.json` names the
  differing entries, `diffoscope-report.html` explains them. Timestamps, absolute paths, locale, or
  anything else environment-derived that reaches the APK are the usual culprits. Download both APKs
  from the run's artifacts, then:
  ```bash
  python3 scripts/fdroid-check.py compare normal/app-release.apk fdroid/app-release-unsigned.apk
  diffoscope normal/app-release.apk fdroid/app-release-unsigned.apk   # only if you need the detail
  ```
- **Signing assumption check failed** — an unexpected blob rode into the APK Signing Block. For
  `0x504b4453` restore `dependenciesInfo { includeInApk = false; includeInBundle = false }` (D-137).
  Needs a *signed* APK — an unsigned one has no signing block to inspect and the check says so:
  ```bash
  python3 scripts/fdroid-check.py signing-blocks app/build/outputs/apk/release/app-release.apk
  ```
- **Metadata validation failed** — the repo drifted from the recipe upstream builds us with.
  Changing this repo back is usually right; if the recipe genuinely should change, that is a merge
  request against fdroiddata, not a repo edit. Note this check **warns and passes** when gitlab.com
  is unreachable, so a green run is not proof it ran:
  ```bash
  python3 scripts/fdroid-check.py metadata
  ```

## When it runs, and what it costs

Pull requests **and `main` pushes** touching `**/*.gradle.kts`, `gradle/**`, `gradle.properties`,
`fastlane/metadata/**`, `scripts/fdroid-check.py`, `fdroid-compat.yml` or `release.yml`; every `v*`
tag; and `workflow_dispatch` for anything else. Not every PR — the buildserver image is a multi-GB
pull and the F-Droid build is a cold Gradle run, so it is far heavier than `build.yml` (which still
gates every PR). The path filter is the cost control; the `main` run is what gives a release commit
a verdict *before* it is tagged; the tag run is a backstop that necessarily lands after the tag
exists. A release commit that matched no path has no run — dispatch one by hand before tagging.

## Maintenance burden

Low but not zero, and it is concentrated in known places:

- **Image/tooling drift.** The `buildserver` tag moves as upstream rebuilds it; `fdroid scanner` is
  installed unpinned. Both are deliberate — we want to learn about upstream changes here rather
  than from a rejected release — so an occasional failure caused by *their* change is expected
  behavior, not a bug. Triage it as an environment change (RUNBOOK "When CI fails on a PR").
- **Signing-block allowlist.** `KNOWN_SIGNING_BLOCK_IDS` in `scripts/fdroid-check.py` may need a new
  legitimate ID someday (a new signature scheme). Add it with a comment naming what it is.
- **Recipe shape.** `scripts/fdroid-check.py metadata` reads a handful of recipe keys with regexes,
  no YAML dependency. If upstream restructures the recipe, that subcommand needs a look — it fails
  loudly rather than silently passing.
- **Build-tools version** for the scanner is pinned in the workflow (`build-tools;35.0.0`) and only
  supplies `dexdump`/`apksigner`; bump it when convenient, not urgently.
