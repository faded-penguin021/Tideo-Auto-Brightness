<!-- Session branches are squash-merged, so this body becomes the squash commit and the basis for the
     release notes. NEVER write the literal skip-ci token in this body (it would skip the release — D-115). -->

## Summary
<!-- 1–3 lines: what changed and why. List the deviations covered: D-NN, D-NN. -->

## Release impact
<!-- versionName / versionCode bump + a one-line semver rationale, or "None (CI / docs / test only)".
     versionCode must exceed the latest v* tag — release-preflight.yml enforces this on PRs.
     On a version bump, LINK the F-Droid changelog that ships with it —
     fastlane/metadata/android/en-US/changelogs/<versionCode>.txt — which release.yml reuses as the
     GitHub release "What's new" (D-123). If you can't link it, it likely doesn't exist yet: write it. -->

## Verification
<!-- Acceptance ladder (domain/platform/app tests + lint + assembleDebug): green, or which subset ran.
     Call out anything owner-verified-only (on-device behaviour Robolectric / CI can't exercise). -->

## Repo hygiene
<!-- Meta / infra / process changes NOT tied to app behaviour, so they read separately from the Summary:
     a new/changed GitHub Actions workflow, build config, docs/RUNBOOK/ledger, refactors, dependency or
     tooling bumps (e.g. this PR template itself). "None" if this is purely an app change. -->

## Owner action
<!-- The handoff: squash-merge (strip any stray skip-ci token from the body, D-115); if this ships a
     release, publish it from the GitHub "Draft a new release" UI; list any on-device Pass A/B gates.
     "None" if purely internal. -->
