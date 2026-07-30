# Dependency and release security audit — 2026-07-30

## Scope and evidence contract

This is a source audit of the root and three module Gradle files, version catalog, wrapper
properties, signing blocks, empty app ProGuard file, lint policy, manifests, every workflow under
`.github/workflows/`, and the release/F-Droid helper scripts. Dependency versions were not changed.
The direct inventory below is from the declarations, not a claim that every transitive component is
listed.

The repository-approved vulnerability channel is GitHub Dependabot **security updates only**:
version-update PRs are intentionally disabled in `.github/dependabot.yml`. The owner confirmed on
2026-07-30 that the repository has **no open Dependabot alerts**. That is the approved advisory result
for this audit; it does not claim that an independent scanner was run or that future advisories cannot
appear. A future Dependabot alert remains the approved trigger for a version change. Substituting an
unapproved scanner here would create a conflicting second policy signal.

## Direct dependency inventory

| Module/scope | Direct declarations | Security relevance |
|---|---|---|
| Root plugins | AGP 8.13.2; Kotlin Android/JVM/Compose/serialization 2.0.21 | Build-time code execution; all exact catalog versions. |
| `:domain` runtime | kotlinx-coroutines-core 1.9.0 | Pure JVM logic only. |
| `:platform` runtime | `:domain`; coroutines-android 1.9.0; kotlinx-serialization-json 1.7.3; Shizuku API 13.1.5 | Android adapters, JSON models, and privileged Binder client. |
| `:app` runtime | `:domain`, `:platform`; AndroidX core-ktx 1.13.1, activity-compose 1.9.3, navigation-compose 2.8.5, lifecycle viewmodel/runtime Compose 2.8.7, DataStore core/preferences 1.1.1, WorkManager runtime-ktx 2.10.0; serialization-json 1.7.3; coroutines-android 1.9.0; Shizuku API/provider 13.1.5; Compose BOM 2024.12.01 plus Material3, material-icons-core, UI, UI tooling preview | DataStore persists settings/profile/context JSON; WorkManager schedules maintenance; Shizuku provider and API are privileged surfaces. |
| Debug only | Compose UI tooling | `debugImplementation`; absent from release. |
| Tests only | kotlin-test; Robolectric 4.16.1; AndroidX Test core 1.6.1/JUnit 1.2.1; coroutines-test 1.9.0; Compose UI test JUnit4; Compose test manifest is debug-only | No release runtime declaration. |

There is no third-party HTTP client. Networking uses Android/JDK `HttpURLConnection` against the
fixed HTTPS geo-IP endpoint, while the manifest declares `INTERNET` and the network-security config
forbids cleartext. The other network-related APIs are Android `ConnectivityManager`/Wi-Fi/location
APIs. DataStore and WorkManager are privileged only in the sense that they preserve configuration
and schedule background work; they do not grant OS privilege.

Shizuku is the genuinely privileged library. Both API and provider ship in the app; the API also
ships through `:platform`. Its Binder user service exposes fixed typed operations rather than a
caller-selected shell command. Runtime Shizuku shell use is limited to Wi-Fi status and force-dark;
the separate grant gateway can grant this package `WRITE_SECURE_SETTINGS`.

## Findings

### Verified risk: wrapper distribution lacks a repository-pinned digest

`gradle-wrapper.properties` validates the distribution URL but has no `distributionSha256Sum`.
Normal local and GitHub builds therefore trust HTTPS, DNS/CDN delivery, and the Gradle cache for the
Gradle 8.14.3 executable rather than checking a digest committed with the wrapper configuration.
F-Droid's separate `gradlew-fdroid` path checksum-verifies against its transparency log, so that path
does not remove the normal-build gap. This is narrower than, and is not a re-proposal of, Gradle
dependency verification: it concerns the wrapper executable itself. Add the official Gradle 8.14.3
binary-distribution SHA-256 in a dedicated follow-up after independently obtaining it from Gradle's
official checksum publication.

### Approved advisory result: no open Dependabot alerts

The security-only Dependabot configuration is present, and the owner confirmed the repository alert
page has no open alerts as of 2026-07-30. No dependency version change is warranted by the approved
process at this time. This is a point-in-time advisory result, not a transitive dependency lock or a
substitute for continuing server-side monitoring.

### Accepted/intentional boundaries (not verified vulnerabilities)

* Repositories are restricted to `google()`, `mavenCentral()`, and the Gradle Plugin Portal for
  plugins; project repositories fail the build. No dynamic, snapshot, local, flat-directory, JitPack,
  or cleartext repository declaration was found.
* Release signing is conditional on an environment-selected existing keystore. Tracked files contain
  secret/variable **names**, not a keystore, password, key, certificate, or credential value. Release
  workflows decode the keystore under `RUNNER_TEMP`, remove it under `if: always()`, verify the APK,
  and upload only the APK. A failed process or runner compromise remains inside GitHub's secret-runner
  trust boundary.
* Release and manual-signing workflows are maintainer-triggered and do not run fork code with signing
  secrets. The release workflow checks out the named tag before signing and quotes the tag in shell
  use. PR workflows are read-only and secret-free. Cross-job F-Droid artifacts have fixed names and
  are consumed only inside the same workflow run; untrusted PR code can forge those artifacts, but
  cannot publish or obtain secrets, and the result is only a PR check.
* Debug tooling and the Compose test manifest are debug-only. The debug package has an application-ID
  suffix. Android release defaults remain non-debuggable, and throwable logging is compiled behind
  `BuildConfig.DEBUG`. Release unit tests are disabled, but their source set is not packaged; test and
  lint execute against debug before release signing.
* Release minification and resource shrinking are not enabled. `app/proguard-rules.pro` is empty, so
  R8 currently cannot rename serialized fields/classes or remove Binder/provider entry points. This
  means there is no present minification-induced serialization or Shizuku Binder defect. Enabling
  minification later requires a release round-trip test for every persisted/imported serializer, an
  installed Shizuku provider/user-service bind test, inspection of dependency consumer rules, and
  explicit keep rules where reflection/manifest reachability requires them.
* The reproducibility claim is APK-content equality across a normal release build and F-Droid's
  current buildserver image, ignoring signatures as documented. The workflow intentionally tracks an
  unpinned `buildserver` image and latest `fdroidserver` scanner, and GitHub jobs use moving Ubuntu
  runner images and major action tags. Thus a green run is evidence for that run, not a hermetic or
  cryptographically attested SLSA provenance statement. Release authenticity ultimately rests on the
  Android signing key and GitHub release permissions. No provenance attestation is emitted.
* Artifact diagnostics are uploaded from fixed staging directories. F-Droid logs and diffoscope
  reports may contain source paths/build metadata but those jobs receive no repository secrets.
  Signed release artifacts are exposed according to GitHub Actions artifact/release access controls;
  no workflow downloads an artifact from another run, repository, URL, or user-selected name.
* Lint has no baseline. Its only global ignores are version-availability checks; permission
  suppressions are localized to calls with explicit permission/error handling, and manifest lint
  ignores are localized to the four deliberately privileged permissions. No `tools:replace`,
  `tools:node`, `tools:remove`, or `overrideLibrary` manifest merge operator was found.

## Previously declined process changes

This audit supplies no evidence that a referenced GitHub Action major is compromised and therefore
does not revive action SHA-pinning. It also found no dependency-resolution tampering or unexpected
repository and therefore does not revive Gradle dependency verification. The wrapper checksum finding
above is a concrete, separately scoped executable-integrity gap; it must not be expanded into those
declined programs without new evidence.
