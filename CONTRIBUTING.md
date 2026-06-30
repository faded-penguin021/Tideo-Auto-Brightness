# Contributing to Tideo Auto Brightness

Thanks for your interest! Please read this before opening anything.

## Tideo is a downstream build; the upstream is AAB

Tideo Auto Brightness is the **native-app build** of the
**[Advanced Auto Brightness][aab]** (AAB) project. The original AAB Tasker project is the
**source of truth** for design decisions, the brightness math, and feature direction. Tideo tracks it.

So the place to propose **features** and discuss **brightness behaviour** is **AAB**, not here.
(And even there: *please discuss before opening a PR*, open an issue first.)

## App-layer bug fixes ARE welcome here

Fixes for the layer that exists **only in Tideo** are welcome via PRs. 

- crashes (e.g. a `ShizukuGrantGateway` failure on a new Android version),
- OEM quirks (e.g. a renamed `reduce_bright_colors` secure key, a battery-saver killing the service),
- UI/Compose bugs (a `ChartCanvas` leak, a layout glitch, an accessibility issue),
- packaging, manifest, permissions, and build problems.

These can't go to AAB (as the bug doesn't exist there), so this repository is the right place. The repo maintainer triages them. Branch protection on `main` (required reviews) is the authoritative guard, so review still gates every merge.

When you open one, keep it scoped to the app layer and include device model + Android version. Please don't change the brightness math or golden test fixtures in a Tideo PR
(see below).

## Translations (human-only) are welcome here

The app is fully localizable — every user-facing string lives in
`app/src/main/res/values/strings.xml`. Right now English is the only language. A translation is an
app-layer contribution, so it's welcome here via PR.

> **Human translations only.** Please do **not** submit machine- or AI-generated translations
> (Google Translate, DeepL, ChatGPT, etc.). A fluent speaker's judgement is what makes a translation
> worth shipping — we'd rather have no translation than a machine one. By opening a translation PR you
> confirm a fluent human did the translation.

### How to add a language

1. Copy `app/src/main/res/values/strings.xml` to `app/src/main/res/values-<lang>/strings.xml`, where
   `<lang>` is the Android locale qualifier — e.g. `values-nl` (Dutch), `values-de` (German),
   `values-fr` (French), `values-pt-rBR` (Brazilian Portuguese).
2. Translate the **text content** of each `<string>` and each `<string-array>`'s `<item>`s. Do **not**
   change the `name=` attributes or the file structure.
3. Leave these untouched:
   - format placeholders — `%1$s`, `%1$d`, `%1$.4f`, … (keep them; reorder only if your language reads
     more naturally that way),
   - escapes (`\'`, `\"`, `\n`) and XML entities (`&amp;`, `&lt;`, `&gt;`),
   - entries marked `translatable="false"` (e.g. the `ⓘ` glyph) — omit them entirely,
   - technical tokens that are identical in every language (`WRITE_SECURE_SETTINGS`, `dumpsys wifi`,
     `SSID`, `ADB`, `Shizuku`, `PWM`).
4. Keep strings roughly the same length where you can — some sit on buttons / single lines.
5. Build to validate: `./gradlew :app:assembleDebug` and `./gradlew :app:lintDebug` (lint flags
   missing or mis-formatted translations).
6. Open a PR with just the new `values-<lang>/strings.xml`, noting the language and that you translated
   it yourself.

The in-app **Language** selector (Misc screen) lists only English today; once a translated locale is
merged it gets added there.

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
`needs-triage` label), so bug-fix PRs can be reviewed and
merged while feature PRs are redirected upstream.

[aab]: https://github.com/faded-penguin021/AdvancedAutoBrightness
