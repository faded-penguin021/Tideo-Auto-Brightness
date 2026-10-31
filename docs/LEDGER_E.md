# DEVIATIONS & DISCOVERIES LEDGER E — permanent registry (DE-001…)

> **Append-only registry — NEVER archived, compressed, or truncated.** The continuation of
> `LEDGER_D.md`, which closed at its 1000-line cap (D-153 mechanism, DA-001 line-based
> cap). Code comments and docs cite entries as bare `DE-0NN` and must always resolve here, so no
> entry may ever be deleted or summarized away. **Append new maintenance deviations as DE-001,
> DE-002, … at the bottom** — one continuous sequence, never restart numbering. Code + golden
> vectors are ground truth; an entry that conflicts with current code is historical, and the code
> settles present behaviour. **Search before appending (DA-006):** grep the ledger files for the topic
> first — extend or cite an existing row rather than append a near-duplicate.
>
> **Rows are immutable (AMH 10.0.0).** The ` [cited]` marker is metadata and may be synchronized in
> place — the citation rung requires it to track the citation set in BOTH directions, so any
> append-only guard must permit adding AND dropping it (AMH 10.3.0). Otherwise correct a detail with
> a new row and append `Corrected by <ID>.` to the old row, or replace its whole conclusion with a
> new row and append `Superseded by <ID>.` The first pointer is final; which verb is honest is the
> reviewer's call, not a guard's.
>
> **Paths in rows (AMH 14.0.0).** A row's immutability covers its text, not the lifetime or location
> of a file it names. A new path reference must resolve in the tree where the row is authored; a
> committed row's target may later move or disappear, and that drift leaves the historical text
> alone. A new path that does not resolve, and any citation of a plan's path (RUNBOOK **Session
> discipline** 5), are both forbidden — and both are **prose-only here**: this repository ships no
> path-reference guard and nothing scans rows for plan paths, so the rule-review pass is the whole
> enforcement.
>
> **Boundaries determine when machinery intervenes, not how much content an author should produce
> (AMH 12.0.0/13.0.0).** `LEDGER_ROW_SENTENCE_CAP` and `LEDGER_ROW_CHAR_CAP` are rejection
> boundaries for new rows; `LEDGER_LINE_CAP` is a rollover boundary; this volume's byte size is
> measurement only, reported and never judged. Crossing a rejection boundary rejects the row;
> passing one proves no more than the absence of obvious oversizing, and is not a verdict on
> concision, scope or quality. Never merge sentences, repunctuate, or drop useful qualifiers solely
> to move a counter; nearing a boundary is a classification signal, so split the material or reduce
> it to its durable conclusion. The keys are
> named here and deliberately not restated as a number, because nothing checks this preamble
> against the config and a copied number goes stale the first time a cap moves. Read them from
> `amh.conf`; a green ladder deliberately does not print the limits. Bytes are counted with
> `LC_ALL=C` over the whole row, line breaks included, so ASCII is one
> byte per character and non-ASCII UTF-8 is charged by encoded bytes. Capture the durable lesson,
> not the whole debugging narrative — the narrative stays in the commit and its PR body (which
> survive the squash as the merged commit's message) and `docs/history/` is frozen (DB-010). But
> the SEQUENCE of work does not survive: intermediate states inside a train are destroyed, so
> anything a later session must be able to look up belongs in the row, not in the history around
> it. Rows already present at HEAD are historical and exempt.
>
> **File cap & rollover.** THIS FILE holds at most `LEDGER_LINE_CAP` lines from `amh.conf`,
> named rather than restated for the same reason as the row cap above; the ladder prints the
> live count against it on every run. The FINAL row may finish past the cap, but no row
> may ever START past it: when this file stands at more than that many lines, create
> **`LEDGER_F.md`** with this same header discipline and start numbering at **DF-001**.
> The suffix advances as an odometer over A–Z without limit (`_Z` → `_AA`, `_AZ` → `_BA`,
> `_ZZ` → `_AAA`). The volumes form a chain walked from `LEDGER.md`; a volume after a missing
> link is unreachable and is not a volume, however well its name is shaped. The ladder computes
> and prints the next reachable volume name when rollover is due.
> Existing rows are never moved, renumbered, or rewritten by a rollover.

- DE-001 [cited]: **Shizuku's helper now stays bound while Tideo's process lives, so two back-to-back
  calls no longer cost a 4 s bind timeout (owner report and adb capture, 2026-10-26: on return
  from a Night-Light-off profile, 4082 K showed for ~5 s before 7308 K).** Each call unbound with
  `remove = true`, which leaves Shizuku 13.1.5's cached client connection in place; the next bind
  joined it and the old helper's death cleared it, so `onServiceConnected` never came (read from
  the bytecode). A thrown or timed-out call, a dead binder or Shizuku turning unusable releases
  with `remove = true`; a null or -1 answer keeps the helper, since it answered and a release
  would re-arm the race. The helper is `daemon(false)`, and the first call after a helper death
  can still time out once (accepted). JVM-tested against a fake that loses the bind as 13.1.5 does; on-device unverified.
  Device-verified by DE-003. Liveness check amended by DE-004.
- DE-002: **1.15.1's Night Light fix shipped as device-verified, but only the debug install
  was checked, and it did not take the release install's path, so the release still showed the
  wrong Kelvin first (owner, 2026-10-26).** DD-093 passed DD-090 on 1.15.1-debug vc31; that install
  had never learned the DC-057 verdict, so it made one Shizuku call on return, while the release
  install, holding the verdict, made two and hit DE-001's 4 s bind hang. Learned device facts
  (the DC-057 verdict, DD-077's calibration) live per install in `display_prefs`; a device check
  that depends on one first makes the debug install hold the same fact as the release install.
  Step 38b's DE-001 case does this with the app's own probe.
- DE-003: **DE-001 is device-verified: on the owner's OnePlus 13, 1.15.2-debug vc32 holding the
  DC-057 verdict from the app's own probe (owner, 2026-10-26), context-rule returns from Photos
  landed 7308 K with Night Light on in one ~0.1 s adb sample, never 4082 K or a 4 s pause, and one
  `:aab_shell` PID stayed up across them.** After a Shizuku restart a new helper bound and the
  switches still landed 7308 K; leaving the circadian profile restored 2596 K (DC-056), and a
  force-stop of Tideo ended the helper (`daemon(false)`).
- DE-004 [cited]: **The held helper's liveness check is `isBinderAlive`, a local read, never
  `pingBinder` (Astra, PR #155).** `pingBinder` is a blocking IPC that DE-001 ran on the caller's
  thread under the `serial` lock, outside the cancellable worker, so a helper that stayed alive but
  stopped answering would hang every later Shizuku call. A helper that died before its death notice
  arrives now costs that one call a null answer; the next call binds again. Not re-checked on device.
  Shizuku's own server ping in `isUsable` and the bind and unbind still run on the caller's
  thread, as before DE-001; a hung Shizuku server stays out of scope (Sol).
- DE-005: **On a Pixel the display calibration (DD-079) cannot pass, because manual brightness
  stops at the panel's High Brightness Mode transition point, so the sweep's top levels read one
  value; the failure then blames the DUMP grant (a reporter's `dumpsys display`, relayed by the
  owner, 2026-10-26).** The dump's brightness event log holds the sweep: `screen_brightness` 1 to
  150 gave 0.0 to 0.5866142 on the 0–1 float scale, `(level − 1) / 254`, while 175 and 200 both
  gave 0.63 with `unclampedBrt` 0.685 and 0.783 under `hbmMax=0.63` (`mHbmData` transition 0.63,
  `minLux` 10000), and the restore came 3.3 s after 200 was written, as the 3 s window expired.
  The sweep accepts a point only on a stable reading, so its ten points show a read source
  answered; every failure stores AAB's one `error` (DD-078), which the flash and the card both
  word as a missing DUMP grant. Any setting above about 161 shows 0.63 on this panel, Tideo's own
  curve included, and only Adaptive Brightness at 10 000 lx or more may pass it
  (`mIsHbmAllowed=false` in manual); below that, Adaptive is capped at 0.377495 under 1000 lx and
  0.5353106 under 6000 lx. The dump names neither the model nor the build, so it is not shown to
  be DD-087's crDroid Pixel 9 Pro.
- DE-006: **On DE-005's Pixel, `screen_brightness` is linear in light while Android's slider is
  perceptual, so Tideo's lowest step skips the dark range: setting 1 is 0 % of the slider at
  2 nits and setting 2 is already 14 % at about 8 nits (owner, 2026-10-26, from
  AdvancedAutoBrightness issue 26, whose body was not read here).** The dump's manual events give
  the float as `(setting − 1) / 254` and nits as about 2 + 1584 × float, and their slider
  percentages match AOSP's HLG gamma curve from 0 to the 0.63 ceiling at every logged point
  (setting 4 at 24 %, 5 at 27 %, 15 at 51 %, 100 at 91 %, 150 at 99 %). From that curve, settings
  2, 3 and 14 sit at 14, 19 and 49 % and 161 at 100 %, so half the slider lies between settings 1
  and 14, and 162 to 255 add nothing. Under Adaptive Brightness the slider's top falls to
  0.377495 below 1000 lx, so a float shows higher there: 0.010567754 is 22 % in manual, 29 % in
  auto. No integer setting reaches between 2 and 8 nits; only a float write can.
- DE-007: **In sunlight on the owner's OnePlus, `cmd display get-brightness 0`, `screen_brightness`
  and `mCachedBrightnessInfo.brightness` stop at 4095 while `mScreenBrightness` passes it, so
  neither `get-brightness` nor BrightnessInfo reads High Brightness Mode there and `dumpsys
  display` stays the readout's source (owner's 2 s probe, 2026-10-28).** Over 20 s all four rose
  together from 1734.0 to 3019.0, `get` reporting the setting's 0–4095 value; from the fourth
  sample the three held 4095.0 while `mScreenBrightness` climbed from 4111.0 to 4174.0, within
  DD-083's 4674 range maximum. So the setting tracks Adaptive Brightness (DD-083) only up to the
  normal maximum, and `get-brightness` returns that setting, as AOSP's `getBrightness` does. The
  mode and the build were not logged; whatever drove the panel past 4095, the other three did not
  follow it. This adds no unit to the Pixel calibration plan, and gates the faster-Shizuku plan's
  BrightnessInfo source on a sunlight re-measurement under ColorOS 17, since on this build it
  would cap the readout at 255 and DD-081's flash would never fire.
  Its mode, AUTOMATIC under Tideo's hand-off: DE-008.
- DE-008: **DE-007's probe ran with Tideo running and handed off to Adaptive Brightness above
  65 000 lx (owner, 2026-10-28), so Android was in AUTOMATIC under the hand-off (DD-073), the
  state in which the HBM readout runs (DD-077).** The setting that followed the panel up to 4095
  was therefore OPlus's auto value (DD-083), and `get-brightness` and BrightnessInfo stop at 4095
  exactly where the readout needs them. The build was still not logged.
- DE-009 [cited]: **All nine workflows move from `ubuntu-24.04` to `ubuntu-26.04`, still an exact
  image and never `ubuntu-latest`, on 2026-10-28 while GitHub's `latest` rollout still runs (owner's
  queue answer (a), timed by the owner; DD-068).** The change is the twelve `runs-on` labels and
  `build.yml`'s pin comment, nothing else in the workflows, and its acceptance is RUNBOOK §8's: full
  CI green on the PR on 26.04 (`Build`, CodeQL, Release preflight and the four `F-Droid
  compatibility` jobs), the reproducibility job above all. `release.yml`, `release-signing.yml`,
  `clean-dist.yml` and `translation-badges.yml` run only on a tag, a dispatch or `main`, and
  `redirect-external-prs.yml` runs the base branch's copy, so no PR runs them on the new image; of
  the preinstalled tools they lean on, `gh` and the SDK's `apksigner` (`release.yml`), `apksigner`
  (`release-signing.yml`) and `python3` (`translation-badges.yml`) first run there after the merge,
  while `fdroid-compat.yml`'s `pipx`, `docker`, `sudo` and apt run on the PR. The reproducibility
  job compares entry contents, not zip framing, which is where a host image would differ, so the
  first release's F-Droid verification is the full proof.
- DE-010 [cited]: **Astra High reviewed the codemap plan before any code (relayed by the owner,
  2026-10-29): two blockers and seven should-fixes, all accepted.** The blockers: brace depth 0
  does not find top-level Kotlin declarations, since `DynamicScaleInput`'s constructor properties
  sit at depth 0 inside its header's parentheses, so the scanner counts nesting across `()`, `[]`
  and `{}`; and its answers are lexical reference candidates, never semantic usage, so an empty
  one means none were found in the scanned scope, not that nothing uses a symbol. The
  should-fixes add a byte-level determinism contract, a symbol identity of package plus name with
  ambiguity failing closed, loud failure on malformed input, `write` and `check` built before
  they are measured, a reproducible first-parent churn replay that reports its exclusions, ledger
  joins that name their citing files, a probe baseline isolated from the plan, and the keyword
  heuristic as a query first. The harness-fit finding was widened: `AGENTS.md`'s Harness section
  already requires a new repo-local script to be named in `RULE_FILES` and the HARNESS_LOCAL split
  table in the commit that adds it, so the scanner's first unit carries that under rule review
  and wires its tests into `verify.sh`, which puts code units on the full ladder. Astra's D1–D4
  recommendations match the plan's, with D3 now the query-only option, and went to the Owner
  queue as recommendations, not decisions.
- DE-011: **A second review of the revised codemap plan, by ChatGPT (relayed by the owner,
  2026-10-29), found no blockers and three residual gaps, each taken as an amendment rather than
  new machinery.** The map relates files only through anchors and references, so two readers of
  one `AabSettings` field look unrelated; the plan had deferred a settings-field scan until its
  seed-and-measure unit showed such a coupling, but no unit measured it, so that unit now does.
  Two solar symbols were too easy a real-tree check, so the reference unit's corpus adds a
  callable reference (`::hourToHhmm`, same package, no import), an extension call, a nested-type
  import and `ThresholdConfig`'s two packages as expected non-matches, and identifiers after `.`
  or `::` stay candidates because an extension call looks like member access. A feature nobody
  registers stays invisible, since the guard proves only that declared anchors exist; that
  residual is disclosed in the plan and left unguarded, as the review advised.
- DE-012: **The owner decided the codemap plan's four questions by taking every recommendation
  (2026-10-30), which were also Astra's.** The author regenerates the map and CI only checks it
  (D1 (a)); the feature-level map is committed (D2 (a)), provisionally, since the plan's replay
  can still turn it to anchors-only; the keyword heuristic is a query only (D3 (b)); and the plan
  adds its own `AGENTS.md` sentence (D4 (a)).
- DE-013: **Codemap Unit 5 seeded five features and measured them (2026-10-30): the map changes
  in few merged snapshots, so D2 stays (a), and keyword leads are mostly noise, so D3 stays (b).**
  The plan's 14 seed anchors missed owners, since an anchor is a symbol and a file reached only
  through a co-declared type, a lambda wired in `AppModule` or an owner's own dependency stays
  outside; the registry now holds 45 anchors (42 symbols, none qualified), circadian, contexts,
  handoff, night-light, privilege and super-dimming have 29, 29, 23, 18, 19 and 21 member files,
  100 in all, and `AppModule` is in all six (4, 2, 2, 6, 1 and 1 anchors), `AmbientMonitoringService`
  in four. Four names are declared in two packages, and the tree holds 0 ambiguous candidates.
  `suspects` gave 205 leads on the seed (contexts 80, mostly through `override`, a Kotlin
  modifier) and 82 on the final registry (20, 5, 16, 14, 6 and 21): 1 second implementation
  (`circadianDimMultiplier`, light-refactor finding 6), 6 missing anchors or edges and 75 noise,
  while findings 1, 3 and 5 sit inside a feature or in a consumer, where it never looks. Over 30
  first-parent snapshots back from `origin/main` at `282c95f` (not shallow), 6 of 29 pairs changed
  an eligible feature, 8 of 99 eligible feature-pairs (privilege 4, super-dimming 3, night-light
  1), with 75 excluded where an anchor did not exist yet (circadian 19, contexts 19, handoff 22,
  night-light 15). Owners read 0–11 `AabSettings` fields and 7 of their 83 outside readers are
  couplings a `feature` answer misses, only finding 6's sharing logic, now that `ProfileApplier`
  (finding 4, a list overlapping `mergeProfile`'s) is anchored, so the settings-field scan stays
  deferred; `check` and `--anchors-only` each take ~1.3 s.
- DE-014: **On a POCO F4 (HyperOS, Android 14) `config_screenBrightnessSettingMaximum` reads 4095
  while Android reads `screen_brightness` on 0–255, so Tideo writes every level about 16 times too
  high (a reporter's fork, commit `1957d5e`, and their replies in its comments, relayed by the
  owner, 2026-10-31).** At 0 lx Tideo's domain 3, 2 and 1 went out as 48, 32 and 16, and
  `dumpsys display` showed 0.18503937, 0.122047246 and 0.05905512, which are 47/254, 31/254 and
  15/254, AOSP's `(setting − 1) / 254`, where a 0–4095 reading of 16 would show about 0.0039. The
  panel's spline (2, 500 and 900 nits at 9.7704E-4, 0.49975574 and 1.0) puts that floor at about
  60 nits instead of 2, and from domain 16 up every write reaches 256 or more, so at about 3 800 lx
  the dashboard's 49 went out as 787 and the screen sat near full. Tideo reads back on the same
  wrong scale, so Live Debug's write card stays `ACKNOWLEDGED` and only `Settings API max: 4095`
  shows it. The reporter's build with the scale forced to 255 shows `Settings API max: 255` and
  10 → 10 with the screen following the dashboard over a few hours, while its display calibration
  still fails, even with longer `dumpsys` and stable-read limits, for a reason not yet found. This
  is the hardware DC-026 called nobody's, but the reverse of DC-019's case, since the config
  overstates the scale and `context.resources` would return the same 4095.
- DE-015: **A plan review relayed by the owner (2026-10-31), read against `c687a84`, found that a
  plateau at the top of the display-calibration sweep proves saturation, not the High Brightness
  Mode threshold, and both calibration plans now take all six of its findings.** A plateau above
  level 128 that holds at 255 cannot tell an HBM transition from a device-configured thermal cap
  or a stuck reading, and DE-005's Pixel throttling near level 98 bounds only that Pixel, so a
  plateau becomes the threshold only when the dump shows a transition point equal to it and no
  other cap in force. Accepting a ceiling before persisting it would let one release store ceiling
  records that later builds read as legacy, extrapolating to about 1150 until each user
  recalibrates, so the boundary is stored with the ceiling and its readout ships in the same
  release. The HBM flag is set from the rounded readout, after an early return while that readout
  is unchanged, so a raw of 0.6301 over a 0.63 boundary still shows 255 and never sets it; a
  stored boundary must be compared with raw. A failed first level with no reading is not a
  permission failure, since timeouts and missing or unparseable fields produce it too, so
  permission advice needs every source shown unavailable, and readings lost midway are their own
  cause. The Pixel plan became the shared calibration plan; the HyperOS findings are DE-016.
- DE-016: **DE-015's review found two errors in the HyperOS scale plan, both now fixed there: its
  scale checks contradicted its own ceiling hypothesis, and its timing argument excluded a cause
  without measurements.** Under H1 the POCO F4's panel holds about 0.49976 from setting 128 up, so
  a correctly scaled request of 200 fails a check expecting `(requested − 1) / 254` from
  `mScreenBrightness`, and `mScreenBrightness × 254 + 1` cannot rebuild a setting above the clamp;
  the scale is now judged at 25, 50 and 100, saturation separately, and above a ceiling the
  app-facing setting is compared with the request, apart from the applied panel value. That the
  fork's 12 s window and 15 s dump limit still failed does not clear a slow read, because nothing
  was timed and one app read may fall through DUMP, root and Shizuku, each with its own limit,
  which a shell `time dumpsys display` does not measure. H1 and H2 are hypotheses, not the list of
  causes; the corrected build's trace of each read's source, field, raw and elapsed time settles
  which, and no limit is raised without it. The review backed D1 (a), 0–255 for the app-facing
  setting, as Tideo's supported contract and Android's documented range rather than proof that
  every OEM build conforms.
- DE-017: **The POCO F4 reporter filed the HyperOS scale report as issue #157 (2026-10-31, relayed
  by the owner); it confirms DE-014 and adds four facts.** At 0 lx in manual mode its dump shows
  `mCachedBrightnessInfo.brightnessMax=0.49975574`, equal to `hbmTransitionPoint` and to
  `mHbmData`'s transition with HBM from 6001 lx, the cap DE-005's Pixel shows at 0.63, while the
  excerpt carries no `brightnessMaxReason` and no sweep has shown a plateau. `adb shell settings
  get system screen_brightness` returned 16, the value Tideo wrote, so on this phone the shell and
  the app share one scale, unlike DC-025's OnePlus. Calibration failed on 1.15.0 with DUMP granted,
  which proves nothing about reading, since every sweep level from 25 up is written as 401 or more
  and the readings stop rising by level 50, while the fork's failure with 12 s and 15 s limits
  fits a plateau from level 128, which fails at level 150 at any window length. Shizuku 13.5 ran
  with Tideo unauthorised, because Tideo asks only from onboarding's elevated step and Privileged
  Display's grant card, both hidden at ELEVATED (`OnboardingScreen.kt:368`,
  `PrivilegedDisplayScreen.kt:154`), so an adb-elevated user never gets a Shizuku runtime use and
  the dump's Shizuku leg returns without binding; the owner routed that gap to the faster-Shizuku
  plan's one-tap-grants unit. The issue's proposals are a settings-scale choice, the HyperOS plan's
  D1 (b), and comparing the dump against both scales, which is learning the scale from the device,
  declined by DC-026.
