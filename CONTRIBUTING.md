# Contributing to Tideo Auto Brightness

Thanks for your interest! Please read this before opening anything.

## Tideo is a downstream build; the upstream is AAB

Tideo Auto Brightness is the **native-app build** of the
**[Advanced Auto Brightness][aab]** (AAB) project. AAB — the original Tasker project — is the
**source of truth** for design decisions, the brightness math, and feature direction. Tideo tracks it.

So the place to propose features, discuss behaviour, and contribute changes is **AAB**, not here.
(And even there: *please discuss before opening a PR* — open an issue first.)

## Direct pull requests to this repository are not accepted

PRs opened against `faded-penguin021/tideo-auto-brightness` by anyone other than the maintainer are
**closed automatically** (see `.github/workflows/redirect-external-prs.yml`). This is by design — Tideo
is generated/maintained in lockstep with AAB and is not a parallel development target.

This is not a comment on the quality of your change — it's about keeping one source of truth.

## What you *can* do here

- **Open an issue** for a Tideo-specific bug (a crash, an OEM brightness/secure-key quirk, a packaging
  problem) — things that are about the *Android app build* rather than the brightness logic itself.
  Include device model, Android version, and steps to reproduce.
- **Discuss** in issues. Maintainer-authored PRs are how code lands here.

## Where features and logic changes go

→ **[Advanced Auto Brightness][aab]** — open an issue there first, per its `CONTRIBUTING.md`.

## Maintainer note

Branch protection on `main` is the authoritative guard (required reviews + restricted pushes); the
auto-close workflow is a courtesy redirect on top of it.

[aab]: https://github.com/faded-penguin021/AdvancedAutoBrightness
