# Contributing to Tideo Auto Brightness

Thanks for your interest! Please read this before opening anything.

## Tideo is a downstream build; the upstream is AAB

Tideo Auto Brightness is the **native-app build** of the
**[Advanced Auto Brightness][aab]** (AAB) project. AAB — the original Tasker project — is the
**source of truth** for design decisions, the brightness math, and feature direction. Tideo tracks it.

So the place to propose **features** and discuss **brightness behaviour** is **AAB**, not here.
(And even there: *please discuss before opening a PR* — open an issue first.)

## App-layer bug fixes ARE welcome here

There's a whole layer that exists **only in Tideo** and has no home upstream — the Android/Kotlin app
itself. Fixes for that layer are welcome as pull requests here:

- crashes (e.g. a `ShizukuGrantGateway` failure on a new Android version),
- OEM quirks (a renamed `reduce_bright_colors` secure key, a battery-saver killing the service),
- UI/Compose bugs (a `ChartCanvas` leak, a layout glitch, an accessibility issue),
- packaging, manifest, permissions, and build problems.

These can't go to AAB (it's a Tasker project — the bug doesn't exist there), so this repository is the
right place. The repo maintainer triages them. Branch protection on `main`
(required reviews) is the authoritative guard, so review still gates every merge.

When you open one, keep it scoped to the app layer and include device model + Android version. Please
don't change the brightness math or golden test fixtures in a Tideo PR — that's upstream's domain
(see below).

## Where features and brightness-logic changes go

→ **[Advanced Auto Brightness][aab]** — the math and decision logic are golden-tested against the
original Tasker engine and are locked here. New features and any change to brightness behaviour start
upstream at AAB (open an issue there first, per its `CONTRIBUTING.md`); the port into Tideo follows.

## Reporting bugs

Open an issue using the **Bug report** template (`.github/ISSUE_TEMPLATE/bug_report.md`). Include the
device model, Android version, privilege tier (BASIC/ELEVATED), and steps to reproduce.

## Maintainer note

Branch protection on `main` is the authoritative guard (required reviews + restricted pushes). The
`redirect-external-prs.yml` workflow  **triages** external PRs (a friendly comment + a
`needs-triage` label) instead of auto-closing them, so app-layer bug-fix PRs can be reviewed and
merged while feature PRs are redirected upstream.

[aab]: https://github.com/faded-penguin021/AdvancedAutoBrightness
