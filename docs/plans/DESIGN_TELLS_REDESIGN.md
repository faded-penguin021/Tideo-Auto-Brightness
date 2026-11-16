# Minor redesign: remove the AI design tells, keep the good design

## Status

- **Spec only. No code has changed.** The owner asked for this plan on 2026-11-16. That request
  authorised **Unit 0 only**: committing this file on the session branch `ccr-51963cf9-268nmr`.
  Every later unit needs the owner's separate go-ahead.
- **Owner guidance (2026-11-16).**
  - A minor redesign, not a new look: the palette, fonts, layout grid, screens and navigation stay.
  - **Good design stays, AI-made or not.** Only patterns that fail the test below change.
  - Any label change accounts for i18n and accessibility (the label rules below).
  - The plan uses Claude Design (Units 2 and 9).
  - The README stays as is: its text, `docs/banner.png` and the screenshots it embeds from
    `docs/screenshots/`.
- No unit opens a PR, replies on an issue, tags or releases.

## Context

**Why.** On 2026-11-16 the owner asked for an audit of the app's design for AI tells. The audit
compared the four screenshots in `docs/screenshots/` and the UI code with published catalogues of
AI design tells: chiefly Impeccable's "slop" catalogue (46 rules, most checked by its detector)
and the Hallmark ban list, plus Pangram's frequency data for AI writing. Those sources are blogs
and tool vendors, so their rules are informed opinion, not measurement. Claude Design's own
checklists say the same about the strongest one: both its Design and Design System types list
"left-border cards" among the AI tropes to avoid (read 2026-11-16).

**Where the tells came from.** The palette is from the Tasker scenes (`Color.kt`) and is not a
tell. The patterns below arrived with the S13c' polish pass (2026-06-20,
`docs/history/STATE_rebuild.md` row S13c', `design/m3_audit.md` §6), which ran an owner-forwarded
"Pro-Tool aesthetic" spec (m3_audit §2.5).

**The screenshots predate the code.** They show a text "‹ Back" button, while every scaffold now
draws an `ArrowBack` icon (`SettingsControls.kt:311`, `SettingsScaffold.kt:46`,
`AppShell.kt:47`). `circadian.png` draws "Compression taper" under "Taper midpoint: 190"; whether
the current build still overlaps is unknown (Unit 7 asks).

## The test: slop or good design

A catalogue match is not a verdict. Each pattern gets one of three:

- **Remove (slop):** decoration that carries no information, or copy that contradicts what the app
  is. It goes.
- **Fix (a real defect, AI or not):** the pattern hurts reading, i18n or accessibility. It is
  repaired, keeping its job.
- **Keep (good design):** it does a job for the user, even if AI output often uses it too. It stays.

The question for each: *what does the user lose if it disappears?* Nothing means remove. Something
means keep it, or fix how it does its job.

| # | Pattern | Where | Verdict and reason |
|---|---|---|---|
| T1 | Side-tab accent border on a rounded card (Impeccable's "most recognizable tell"; Claude Design's checklists) | `AabCard.kt:72-80`: `AabCardVariant.Hero` draws a 3 dp teal bar (`Dimens.accentEdge`); used once, by `BrightnessInstrument` (Dashboard). `HeroNavCard` has the same bar behind `prominent`, which its only caller sets to `false` (`AabNav.kt:92`, `MenuScreen.kt:109`). | **Remove.** It marks the one card that already leads by its 64 sp figure, and it is the same colour as everything else teal, so it says nothing. |
| T2 | Uppercase tracked mono labels above values (Hallmark: "uppercase mono-cap labels before each heading"; Impeccable: monospace as "technical" shorthand) | `KeyValueRow` (`AabCard.kt:116`), `BrightnessInstrument.kt:78`, `DashboardScreen.kt:338`, the Menu tagline (`AppShell.kt:92`); style `AabDataCaption` in `Type.kt` (Plex Mono, 1.6 sp tracking). | **Fix.** Small muted labels over values are good and stay. What is wrong is how they are drawn: the 1.6 sp tracking also spreads the Chinese labels (`已应用亮度`) and tagline (`随环境光按确定规则调节亮度`), Plex Mono has no Han glyphs so those labels fall back anyway, and `.uppercase()` hands TalkBack an all-caps copy instead of the resource string. Labels become the label rules below. |
| T3 | Redundant headers (Impeccable specimen "Redundant UX Writing") | `GraphSettingsGroup` caption "Affects the X graph" (`GraphScaffold.kt:126`), then a `SectionHeader` title, then a divider (`CircadianScreen.kt:193-194, 223-224`; also Reactivity and Super Dimming). A "Profiles & Contexts" `SectionHeader` over the single "Profiles & Contexts" card (`MenuScreen.kt:107-109`). A "Quick actions" heading over one button and a link (`DashboardScreen.kt:152`). | **Fix.** Three labels for one group make the reader parse twice. The graph's name stays (it is G2R-F82's requirement, D-066); the repetition goes. |
| T4 | Marketing buzzwords | `about_intro`, `guide_welcome_body` ("more intelligent"), `guide_outro` ("Enjoy your new intelligent auto brightness!"), `guide_s4_body` ("A smart dead zone"), `help_form_2c` ("Subtle but powerful"). The Chinese lines say the same (`智能`). | **Remove.** The app's whole case is deterministic, inspectable math; "intelligent" claims the opposite. |
| T5 | Status pill: outlined capsule with a coloured dot | `BrightnessInstrument.kt:154-172` | **Fix, lightly.** The state and its colour are useful at a glance and stay; the capsule and the dot are decoration, and the dot repeats the colour the text can carry. |

**Kept, though AI output often has them too.**
- **The big brightness figure** (64 sp, tabular). It is the app's main output. Impeccable's "hero
  metric" rule targets decorative numbers; this one is the product.
- **Tabular mono for live numbers** (`tnum` in `AabDataDisplay`): digits keep their width, so a
  changing value does not jitter.
- **The 2×2 stat strip** (ambient light, circadian factor, profile, context): the four inputs
  behind the figure, at a glance.
- **Hairline edges on cards** (5% white). They separate `#383838` cards from the `#333333`
  ground, where a shadow barely shows. Hairline plus shadow is an opt-in Impeccable rule only, and
  the shadow does give depth in light theme, so both stay.
- **Gold data values** (m3_audit B4, owner-forwarded): emphasis on what the user reads.
- **The ⓘ help on every field**: per-setting help is the point of a tuning app.
- **`tween`-only motion** (`AabMotion.kt`), **12–16 dp corners**, **Plex Sans and Plex Mono**
  (no source flags Plex; the faces named as overused are Inter, Geist, Space Grotesk and
  Instrument Serif), and the Tasker palette.
- **Em dashes** in the strings (68 of 744). A writing tell, but one that costs the reader nothing;
  only those inside a string rewritten for T4 change.

## Label rules (i18n and accessibility)

Every unit that touches a label keeps these.

1. **Casing comes from the string, never from code.** No `.uppercase()` on display text. The
   resource string is what is drawn and what TalkBack reads, in every locale.
2. **No letter spacing on text that may be Chinese.** Labels use `labelMedium` with its own
   spacing (0.5 sp), not 1.6 sp tracking.
3. **Labels in Plex Sans, numbers in Plex Mono.** A label in Chinese falls back to the system Han
   face either way; the split keeps the two roles distinct.
4. **No string resource changes for a style change.** Units 5 and 6 change no key and no English
   text, so `values-b+zh+Hans` is untouched there.
5. **A removed key is removed from every locale in the same commit** (`dashboard_quick_actions`
   if nothing else uses it), or lint's extra-translation check fails.
6. **TalkBack output is tested, not assumed.** Robolectric checks that each changed label's
   semantics text equals its resource string, that `KeyValueRow` still merges label and value
   (D-156), and that the status text is announced in every state.
7. **Large text.** At font scale 2.0 no changed label clips or overlaps (Robolectric where it can
   see it; the owner's phone in Unit 10 otherwise).
8. **Contrast.** Labels stay on `onSurfaceVariant` (`#ECECEC` on `#383838` dark, `#1A1C1B` on
   `#FFFFFF` light); status colours keep 4.5:1 on their surface in both themes, or the state also
   reads from its text alone, which it does.
9. **Rewritten copy and Chinese** (decision D8).

## Claude Design

Claude Design now lives in Claude as two artifact types, both reachable from an agent session's
Artifact tool (checked 2026-11-16): **Design System** (tokens, components with live previews,
guidelines, built from a codebase) and **Design** (a canvas of artboards, 390×844 for a phone
screen). The `DesignSync` tool is reserved for the user-started `/design-sync` skill, and
standalone claude.ai/design closes on 2026-12-14 (its tool description, read 2026-11-16), so this
plan uses the artifact types only. The owner has no Design System artifact yet.

Two uses:
- **A Tideo design system, built from the code** (Unit 2): tokens from `Color.kt`, `Type.kt`,
  `Shape.kt` and `Dimens.kt` exactly; components for the card, `KeyValueRow`, the status text and
  `GraphSettingsGroup`; a README that carries the test above, the Keep list and the label rules.
  Its own checklist adds 4.5:1 text contrast in every theme.
- **A before-and-after canvas** (Unit 2): Dashboard, Menu and Circadian, current and proposed, dark
  and light, plus the Dashboard in Chinese, so the owner sees the decisions before any Kotlin.

Limits, stated so nobody leans on them: both are private claude.ai pages outside the repository.
An agent without claude.ai access cannot read them, so `design/m3_audit.md` stays the binding
in-repo rule and the artifacts are review aids. The canvas is HTML, not Compose, so it shows
intent, not exact rendering; Compose is the source of truth.

## Decisions

Each has options and a recommendation. The owner decides after Unit 1.

- **D1. The Dashboard card without the stripe (T1).** (a) Drop the stripe only; the Hero keeps its
  padding and 6 dp elevation. (b) Also drop the Hero to resting elevation. **Recommend (a)**, the
  smaller change. `AabCardVariant.Hero` keeps its name, so call sites do not move; the dead
  `prominent` bar in `HeroNavCard` goes too.
- **D2. Label style (T2).** (a) Sentence-case Plex Sans, `labelMedium`, `onSurfaceVariant`. (b)
  Keep uppercase, but drawn from an uppercase string resource per locale, with no extra tracking.
  **Recommend (a):** (b) adds keys to translate for a look the owner did not ask to keep.
- **D3. Where mono stays (T2).** Numbers that change and their unit marks (`AabDataDisplay`, the
  64 sp figure, `/ 255`, `DiagnosticCard` values). Labels and the Menu tagline move to sans.
  **Recommend this; no alternative proposed.**
- **D4. The status (T5).** (a) Plain text in the label row, no capsule and no dot, coloured by
  state: `onSurfaceVariant` for Active, `secondary` (gold) for Starting, Paused and Override,
  `error` for Off. (b) Keep the pill. **Recommend (a):** it keeps the `dashboard_status` test tag,
  the colour and TalkBack's announcement.
- **D5. Graph-group headers (T3).** (a) Title first, then "Affects the X graph" as a `bodySmall`
  supporting line, no divider inside a `GraphSettingsGroup`. (b) Delete the caption. **Recommend
  (a):** it keeps G2R-F82's graph name and the `group_<graph>` test contract (m3_audit §3 row 4)
  and drops one header of three. The string `graph_affects` is unchanged.
- **D6. Single-item headings (T3).** Delete the Menu's "Profiles & Contexts" `SectionHeader` and
  the Dashboard's "Quick actions" heading. **Recommend this.** The card's own title and the
  button's label say the same thing.
- **D7. Copy scope (T4).** The five strings named in T4, plus any em dash inside them. Nothing else.
  **Recommend this.**
- **D8. Chinese for the rewritten copy.** The Chinese lines carry the same buzzword. (a) Keep each
  Chinese line and ask #143's translators to drop `智能` where it is only an adjective. (b) Delete
  the Chinese lines so they fall back to English until translated. (c) The agent deletes `智能`
  where it is a removable adjective, with the owner checking the result. **Recommend (a):** (b)
  turns whole Chinese paragraphs into English, and (c) is translation without a native reader.
  Owner queue item 1 names the five keys.
- **D9. Version.** This tree's 1.15.3 is already tagged, so the first code unit carries the train's
  one bump. (a) Patch `1.15.4` / vc34: no behaviour or capability changes. (b) Minor `1.16.0` /
  vc34. **Recommend (a)**, under playbook 6's semver rule, unless the train also carries another
  plan's feature unit.
- **D10. How the rule is held.** (a) m3_audit §7 (the test, the Keep list, the label rules) plus a
  pointer in RUNBOOK's design-coherence block, prose only, mirrored in the Claude Design system's
  README. (b) Also a `scripts/guards/` check. **Recommend (a):** a grep could catch `.uppercase()`
  on a `Text` but not the judgement the test asks for, and a partial guard would claim more than
  it holds.

**Out of scope, with reasons.**
- The README, the banner and `docs/screenshots/` (owner).
- `fastlane/metadata/android/en-US/images/phoneScreenshots/`: they will show the old look until
  refreshed; refreshing them is the owner's call at release.
- The Menu icons picked from `material-icons-core` by name (Play for Super Dimming, Refresh for
  Reactivity, Star for Hand-off). That is a real usability problem, not slop, and no source in the
  audit covers it; fixing it needs new vector drawables or a new dependency, so it is its own
  change.
- Everything on the Keep list.

## Constraints every unit keeps

- **Look stays.** No recolour (RUNBOOK design-coherence block); `MaterialTheme.colorScheme.*` and
  `Dimens.*` only.
- **Test contracts stay.** Every `testTag` m3_audit §6 and the screen tests pin. The uppercase
  assertions in `ComponentLibraryTest` and `ComponentsA11yTest` change as sanctioned casing-only
  edits, named in the commit body.
- **No runtime, domain, `ChartCanvas`, golden, gradle or manifest change.** So no glue review is
  owed; the rule-review protocol applies to Unit 3 only.
- **Codemap first.** `python3 scripts/codemap.py impact <file>` before editing each Kotlin file.
- **i18n.** New text through `stringResource`; `HardcodedStringCheckTest` stays green.
- **Evidence.** There is no emulator. Robolectric proves structure, tags and semantics; the
  Claude Design canvas shows intent; the owner's phone (Unit 10) shows the real look.
- **Overlap.** The light-refactor plan's Unit 2 touches `BrightnessInstrument` labels. Whichever
  lands second rebases on the first; neither changes the other's meaning.

## Units (sequential)

Each code or docs unit ends shippable: STATE Changelog line, ladder green, commit,
`--guards-only`, push.

**0. Commit the plan (no device).**
- Write this file to `docs/plans/DESIGN_TELLS_REDESIGN.md`.
- In STATE `## Active work`, add the item with a one-line unit checklist; add a Changelog line.
- Run `--guards-only`, commit, run `--guards-only` again, push. **Stop.**

**1. Astra review of the plan, at `-e high` (the owner runs it).**
- The owner gives Astra this file, pointing it at "Review brief for Astra" below. The review
  covers every decision, D1–D10, the verdicts in the test table and the implicit decisions the
  brief lists.
- Agent, next session: triage every finding by user-visible impact. An accepted finding amends
  the plan; a rejected one gets its reason. The owner then decides D1–D10.
- Commit the revised plan, one ledger row in the live volume (the findings, each disposition and
  the owner's decisions, naming the plan in prose) and a STATE line.
- Acceptance: every finding has a recorded disposition and every decision an answer.

**2. Claude Design: the design system and the before-and-after canvas (no repo code).**
- Create a Design System artifact named "Tideo" from the code, following its type's
  `from-code.md`: exact tokens for both themes, the four components, the README described above.
- Create a Design canvas, "Tideo redesign: before and after", with the artboards described above,
  using the Tideo system.
- The owner looks at both. A decision the canvas changes their mind on goes back through Unit 1's
  ledger row as an amendment.
- Record both links in this file and a STATE line. Acceptance: the owner confirms the canvas
  matches the decisions.

**3. Design rules first (docs; rule review).**
- `design/m3_audit.md`: add §7 with the test, the Keep list and the label rules; mark B2's teal
  edge and §6's accent edge and tracked-mono captions as superseded by §7; record that B4's gold
  values and the hairline stand.
- RUNBOOK design-coherence block: one sentence pointing at m3_audit §7. That block is in
  `RULE_FILES`, so the rule-review protocol runs (fresh context, strongest tier, verdict in the
  commit body), and the layer is named: prose only (D10).
- Acceptance: the review's findings triaged; `--guards-only` green.

**4. The Dashboard card and the version bump.**
- `AabCard`: drop the Hero stripe and `Dimens.accentEdge` (D1). `HeroNavCard`: drop the unused
  `prominent` bar.
- Bump per D9 (`app/build.gradle.kts`, `fastlane/.../changelogs/<vc>.txt` naming the look change),
  following playbook 6.
- Acceptance: full `scripts/ladder.sh` green.

**5. Labels and mono.**
- `AabDataCaption` becomes the D2 style; remove `.uppercase()` at `AabCard.kt:116`,
  `BrightnessInstrument.kt:78`, `DashboardScreen.kt:338` and `AppShell.kt:92`; D3 keeps mono on
  values and unit marks.
- Tests per label rules 6 and 7, in English and Chinese locale.
- Acceptance: full ladder green.

**6. The status.**
- The status per D4. Tests: `dashboard_status` found in every state, its text announced, its colour
  per state.
- Acceptance: full ladder green.

**7. Headers.**
- `GraphSettingsGroup` per D5 on Circadian, Reactivity and Super Dimming; the two single-item
  headings per D6, with label rule 5 for `dashboard_quick_actions`.
- Ask the owner whether the current build still draws "Compression taper" under the taper slider's
  label. If yes, fix it here as a playbook-4 bug with a test where Robolectric can see it.
- Acceptance: full ladder green.

**8. Copy.**
- The five strings per D7; Chinese per D8; Owner queue item 1 names the keys.
- Acceptance: full ladder green (the string tests run there).

**9. Claude Design catch-up.**
- Update the Tideo design system's components and README to the code as shipped, and add an
  "after" note to the canvas wherever the code differs from the mockup, with the reason.
- Acceptance: the system's README and m3_audit §7 say the same.

**10. Device check and close-out.**
- Owner queue item: install the debug build and check the Dashboard, Menu, Curve, Circadian,
  Reactivity, Super Dimming and Live Debug screens in dark and light theme, in English and
  Chinese, at the largest font size, and with TalkBack on the Dashboard. It passes when the
  Remove and Fix verdicts all hold, every Keep item is unchanged, nothing overlaps or clips, and
  TalkBack reads each label as written.
- Once it passes: one ledger row for the redesign, a STATE Changelog line, the Active work item
  removed, and this file deleted. Code comments cite the ledger row, never this file.

## Review brief for Astra (Unit 1)

**Scope.**
- In scope: the test and each verdict in its table, the Keep list, D1–D10, the label rules, the
  Claude Design units, the implicit decisions below, the unit order, the evidence strategy, and
  the fit with AGENTS.md and RUNBOOK (design-coherence block, playbooks 4–6, rule review).
  Check that each cited file and line says what the table claims.
- Out of scope: the README and its images (owner), the out-of-scope items (check only their
  reasons), runtime and domain code, and the release process.

**Implicit decisions to review.** None is stated as a choice above, but each is one.
1. **The test itself.** "What does the user lose" is the line between slop and good design. Is it
   the right line, and is any verdict on the wrong side of it? Is anything on the Keep list
   actually slop, or anything marked Remove actually doing a job?
2. **The catalogues as evidence.** Web-CSS rules from Impeccable and Hallmark are taken to apply
   to a Compose app. Does any verdict rest on a rule that does not transfer?
3. **"Minor" is the limit.** Is the result still recognisably today's look, or half-changed?
4. **Claude Design outside the repo.** The design system and canvas live on claude.ai, readable
   only with access. Is that a problem for agent-agnosticism (D-176), given m3_audit stays
   binding? Is an HTML canvas a fair preview of Compose here?
5. **Unit order.** Astra, then the canvas, then the rules, then code. Should the canvas come
   before the review instead?
6. **Overriding owner-forwarded design.** B2's teal edge and §6's tracked captions came from the
   owner's 2026-06-20 spec; this plan reverses them while keeping B4 and the hairline. Coherent?
7. **i18n.** Are the label rules complete for a two-locale app (English and Simplified Chinese),
   and do they hold if a third locale is added? Is D8's handling of the Chinese copy right?
8. **Accessibility.** Do the TalkBack, font-scale and contrast checks catch what this change could
   break? Does D4's plain-text status lose anything for colour-blind users?
9. **No guard (D10).** Is prose-only enough, given AGENTS.md's "identify the layer" rule?
10. **Version (D9).** Is a look-only change a patch under playbook 6, and what if the train also
    carries other plans' units?
11. **Interaction with the other plans.** Light-refactor Unit 2 shares `BrightnessInstrument`;
    which should land first?
12. **Unit sizing.** About one focused hour each, binary acceptance. Split, merge or reorder?

## Sources

- Impeccable, "The visible tells of AI design": https://924b8765.impeccable-2rv.pages.dev/slop
- Hallmark banned anti-patterns: https://instagit.com/Nutlope/hallmark/hallmark-banned-anti-patterns/
- The Fountain Institute, "7 Signs a UI Has Been Vibe Coded":
  https://www.thefountaininstitute.com/blog/signs-vibe-coded-ui
- Pangram, "9 Signs of AI Writing, Backed by Data": https://www.pangram.com/signs-of-ai-writing
- aiskill.market, "Banning Inter": https://aiskill.market/blog/banning-inter-the-font-tell
- Claude Design: the Design and Design System artifact types' instructions (their AI-trope
  checklists), read through the Artifact tool on 2026-11-16.
