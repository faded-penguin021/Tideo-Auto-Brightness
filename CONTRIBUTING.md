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

## Translations are welcome here

The app is fully localizable — every user-facing string lives in
`app/src/main/res/values/strings.xml`. English and Simplified Chinese are available. A translation is an
app-layer contribution, so it's welcome here via PR.

> **Human and AI-assisted translations are welcome; unreviewed machine translation is not.** A
> fluent speaker's judgement is what makes a translation worth shipping, so the three kinds differ in
> whether a fluent speaker has checked every string:
>
> - **Human translation**: a fluent speaker translated it. Welcome.
> - **AI-assisted translation**: a tool (DeepL, an AI model, etc.) produced a first draft, then a
>   fluent speaker reviewed and edited **every** string against the English original, checking
>   meaning, omissions and placeholders, and checked it in context in the running app. Welcome; say
>   in the PR that it is AI-assisted and which tool made the draft.
> - **Machine translation**: tool output (Google Translate, DeepL, ChatGPT, etc.) submitted without
>   that string-by-string review. Not accepted: we'd rather have no translation than an unchecked
>   one.
>
> By opening a translation PR you confirm that a fluent speaker wrote or reviewed every string.

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
6. Add a coverage badge for your language to the README's **Translations** section. Run
   `./gradlew :app:testDebugUnitTest --tests '*TranslationCoverageBadge*'`; it fails with the exact
   line to paste.
7. Open a PR with the new `values-<lang>/strings.xml`, noting the language and whether it is a human
   or an AI-assisted translation (if AI-assisted, which tool made the draft).

A string without a translation shows in English, so a partial translation still works; lint lists
the missing ones as warnings, not errors. When a change rewrites an English string's meaning,
delete that string from every `values-<lang>/` file in the same change, so the app shows the new
English rather than an outdated translation until a translator catches up. Adding or removing
strings can move a language's coverage past a whole percent; the same test then tells you the new
badge numbers.

The in-app **Language** selector (Setup screen) lists English and Simplified Chinese. When adding
another translated locale, also add it to the picker in `OnboardingScreen.kt` and to
`app/src/main/res/xml/locales_config.xml`.

## Where features and brightness-logic changes go

→ **[Advanced Auto Brightness][aab]** — the math and decision logic are golden-tested against the
original Tasker engine and are locked here. New features and any change to brightness behaviour start
upstream at AAB (open an issue there first, per its `CONTRIBUTING.md`); the port into Tideo follows.

## If the "F-Droid compatibility" check fails on your PR

Tideo is distributed through F-Droid, which does not ship our APK on trust — it **rebuilds the
release from source in its own environment** and publishes ours only if its rebuild matches. So a
change can pass every normal check and still break the store listing. The `F-Droid compatibility`
workflow runs that rebuild ahead of time, on PRs that touch build files, Gradle config, or F-Droid
metadata.

Each stage fails with its own name — *Normal release build failed*, *F-Droid compatibility
validation failed*, *Reproducibility validation failed*, *Signing assumption check failed*,
*Metadata validation failed* — and the run attaches the APKs, logs and diff reports it produced.
**[docs/rebuild/FDROID_VALIDATION.md](docs/rebuild/FDROID_VALIDATION.md)** explains what each stage
checks, what it intentionally doesn't, and the exact command to reproduce the failure locally. You
don't need to know anything about F-Droid internals to act on it — start with that page's "Reading a
failure" section.

## Reporting bugs

Open an issue using the **Bug report** template (`.github/ISSUE_TEMPLATE/bug_report.md`). Include the
device model, Android version, privilege tier (BASIC/ELEVATED), and steps to reproduce.

## Maintainer note

Branch protection on `main` is the authoritative guard (required reviews + restricted pushes). The
`redirect-external-prs.yml` workflow  **triages** external PRs (a friendly comment + a
`needs-triage` label), so bug-fix PRs can be reviewed and
merged while feature PRs are redirected upstream.

[aab]: https://github.com/faded-penguin021/AdvancedAutoBrightness
