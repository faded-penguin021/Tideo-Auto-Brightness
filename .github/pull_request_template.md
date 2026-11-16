<!-- Session branches are squash-merged, so this body becomes the squash commit and the basis for the
     release notes. NEVER write the literal skip-ci token in this body (it would skip the release — D-115). -->

## Summary
<!-- 1–3 lines: what changed and why. List the deviations covered: D-NN, D-NN. -->

## Release impact
<!-- versionName / versionCode bump + a one-line semver rationale, or "None (CI / docs / test only)".
     versionCode must exceed the latest v* tag — release-preflight.yml enforces this on PRs.
     On a version bump, LINK the F-Droid changelog that ships with it —
     fastlane/metadata/android/en-US/changelogs/<versionCode>.txt — which release.yml reuses as the
     GitHub release "What's new" (D-123). If you can't link it, it likely doesn't exist yet: write it.
     Also on a version bump, bring MATH_EXPLAINED.md up to date: re-pin the version in its intro and
     the §15 source links (they point at the release tag), and fix any section whose behaviour,
     default, range, message or screen changed since the version it was pinned to (RUNBOOK
     playbook 6, DE-039). Say "MATH_EXPLAINED.md: re-pinned to <version>; updated §N, §M" or
     "MATH_EXPLAINED.md: re-pinned to <version>, no user-visible change". A doc pinned to an older
     release is how §10 went stale through 1.15.3. -->

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
