# DEVIATIONS & DISCOVERIES LEDGER C — permanent registry (DC-001…)

> **Append-only registry — NEVER archived, compressed, or truncated.** The continuation of
> `LEDGER_B.md`, which closed at its 1000-line cap (D-153 mechanism, DA-001 line-based
> cap). Code comments and docs cite entries as bare `DC-0NN` and must always resolve here, so no
> entry may ever be deleted or summarized away. **Append new maintenance deviations as DC-001,
> DC-002, … at the bottom** — one continuous sequence, never restart numbering. Code + golden
> vectors are ground truth; if an entry conflicts with current code, trust the code and correct the
> entry (don't delete it). **Search before appending (DA-006):** grep the ledger files for the topic
> first — extend or cite an existing row rather than append a near-duplicate.
> **Keep new rows concise and at or below `LEDGER_ROW_SENTENCE_CAP`** in `amh.conf`; the
> sentence limit is the working bound, while `LEDGER_ROW_CHAR_CAP` is a byte backstop with
> real headroom. The keys are
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
> **`LEDGER_D.md`** with this same header discipline and start numbering at **DD-001**.
> The suffix advances as an odometer over A–Z without limit (`_Z` → `_AA`, `_AZ` → `_BA`,
> `_ZZ` → `_AAA`). The volumes form a chain walked from `LEDGER.md`; a volume after a missing
> link is unreachable and is not a volume, however well its name is shaped. The ladder computes
> and prints the next reachable volume name when rollover is due.
> Existing rows are never moved, renumbered, or rewritten by a rollover.

- DC-001 [cited]: **Graph Metrics debug (%AAB_Debug = 7) now times chart (re)draws, as it should.**
  The port miscategorised it: the only `GRAPH_METRICS` emit was `PipelineCycleRunner`'s `"cycle Xms"`
  pipeline-cycle timer (`%AAB_CycleTotal`), which `features_spec.md §4` explicitly says level 7 is NOT,
  while the Compose charts emitted nothing, so the category flashed nothing on a graph screen. The fix
  times each `ChartCanvas` (re)generation and flashes it under `GRAPH_METRICS` via `GraphMetricsSink` /
  `LocalGraphMetricsSink`, provided by `AutoBrightnessApp` only at level 7 and deduped by
  `graphSignature` so scrub/recompose redraws with unchanged inputs are not re-timed. The
  miscategorised pipeline emit is deleted; cycle time stays in `PipelineState.cycleTimeMs` (Live Debug).
  Faithful to Tasker task663 `_GenerateGraph`'s render toasts (D-023).

- DC-002 [cited]: **The self-write marker now records what Android STORED, not what we asked for
  (#126/#127).** `write()` assigned the marker after `putInt` returned, ignored `putInt`'s Boolean and
  demanded exact equality, so an OEM that clamps or quantizes made every one of our own writes look
  external, an echo dispatched before the marker existed was unfilterable, and a refused write was
  indistinguishable from a successful one. It is now a transaction returning `BrightnessWriteResult`
  (requested, acknowledged, the `deviceMax` THIS write converted with, status): the marker is armed
  before `putInt` and moved to the read-back value on success, `@Volatile selfWriteInProgress` covers
  the gap, and `finally` restores the previous marker and clears the flag on every exit including the
  rethrow. `WriteStatus.WRITTEN_UNACKNOWLEDGED` (putInt true, read-back failed) is deliberately not
  "failed" — `ok=false` would assert nothing moved — and keeps the REQUESTED raw as the marker so the
  write's own echo is still filtered, while `REFUSED` and `DENIED` restore the previous one; `DENIED`
  stays distinct because it also says `WRITE_SETTINGS` is gone. `forceManualMode()` returns `putInt`'s
  own Boolean, so a REFUSED mode write reports false as well as a denied one — deliberate, because the
  caller wants to know whether MANUAL is actually in force, not merely that no exception was thrown.
  `isSelfWrite` matches the acknowledged raw OR the requested raw, because a provider that applies
  asynchronously echoes the requested value after the read-back has already recorded the pre-write
  one; `write()` is `@Synchronized` so the marker pair and the flag cannot interleave (uncontended
  today — the three pipeline writers share the serialized consumer, and `PanicHandler` is ordered
  after it by `emergencyStop`'s `cancelAndJoin`, which is a different mechanism in a different file
  and must not be mistaken for the consumer serialising it).

- DC-003 [cited]: **What DC-002's read-back cannot do, recorded so it is not rediscovered as a bug.**
  The read-back is corroboration, not proof of authorship: it catches only SYNCHRONOUS normalization,
  so a provider that re-writes the key milliseconds later still reads as external, and if a foreign
  write lands between our `putInt` and our read-back we adopt ITS value as the acknowledged marker,
  so that override is filtered until our next write re-points the marker — a sharper cost than the
  `selfWriteInProgress` flag's, which clears the instant the write returns, and there is no local
  test that separates "the provider clamped our value" from "someone else wrote in that window".
  `REFUSED` likewise assumes `putInt == false` stored nothing, so a provider that stores the value
  and still returns false has its own echo read as external and pauses the pipeline. The cost of the
  read-back is one extra binder read per WRITE, which on the animation path is per frame — up to
  `animSteps` extra round-trips per sweep, on top of the band read `AnimationRunner` already does.
  `isOnScreenSelfWrite()` is deleted as it had no production caller; D-049 stays cited from
  `AnimationRunner` and `PipelineCycleRunner`.

- DC-004 [cited]: **Both override detectors now consult what the provider acknowledged, and the
  baseline records it (#126).** `AnimationRunner` was a second detector that read brightness directly
  and consulted neither the observer nor the self-write marker, so on a device that normalizes our
  writes out of the sweep band it aborted the sweep, `runCycle` returned before the `ctx.update` that
  refreshes `lastAppliedBrightness`, and `handleOverride` then compared the settled value against the
  PREVIOUS cycle's baseline and paused — a false pause with no foreign writer anywhere. `animate()`
  now returns `AnimationOutcome.Completed|Overridden` carrying the latest ACKNOWLEDGED frame write and,
  for the abort, the read that actually tripped the two-read detector (sealed, because "Overridden with
  no triggering read" is unreachable); an out-of-band read counts toward the threshold only if it also
  differs from `acknowledgedDomain`, which is exact-safe because a device that stored our frame reports
  `toDomain` of the very raw the acknowledgement holds. `lastAppliedBrightness` is now set from the
  acknowledgement at every write site — acknowledged wins, `WRITTEN_UNACKNOWLEDGED` records the
  requested value as an explicit diagnosable assumption, and `REFUSED`/`DENIED` leave the previous
  baseline because that is the truthful one when nothing landed. The band plus two-read debounce stays
  authoritative for wrong-direction and overshoot, so this does not reinstate the D-054 exact-match
  defect: the acknowledgement is an extra in-band condition ANDed in, never a replacement for the band.

- DC-005 [cited]: **The override settle keeps `%AAB_CycleTime` only, and the commit guard gains a
  one-step deadband.** The plan for #126 asked to restore D-049 #1's "fallback to throttle", on the
  belief it was never implemented; it WAS, and D-062(2)/F71 deliberately removed it, because
  `%AAB_Throttle` gates only the prof760 main loop while task567 act7 is a separate settle, and
  `override_settleIsNotGatedByThrottleCooldown` pins that a 60 s cooldown must not delay a pause.
  Reinstating it was therefore rejected against the evidence; what the wake path really needed is the
  floor, since `hibernate()` nulls `cycleTimeMs` and `delay(0)` returns WITHOUT suspending, so the
  re-read could share a dispatch with the event that asked for it — `MIN_SETTLE_MS = 1` is a yield
  floor, not a settling estimate, and no larger number is defensible from anything on hand. The commit
  comparison is now `abs(settled - lastApplied) <= 1` in domain space rather than exact: at the
  reporter's scale one domain step is 12–16 raw units, so any round trip crossing a boundary paused
  the pipeline. This is a deliberate deadband, not a formality — a persistent one-step deviation is
  made undetectable on purpose, accepted because 1/255 is imperceptible and because the blindness is
  bounded in MAGNITUDE, unlike a grace period, which is blind to a change of any size for its duration.

- DC-006 [cited]: **A non-MANUAL brightness mode at commit time dismisses the override instead of
  labelling it user input (#127).** `OverrideRules.shouldCommitPause` takes `isManualMode` (defaulting
  to true, so the pre-settle gate stays state-only and a failed mode read fails toward pausing); when
  it is false `handleOverride` calls `forceManualMode()` and returns without pausing, and the next
  cycle re-establishes our brightness normally. The claim is deliberately narrow: a non-MANUAL mode
  proves only that Tideo no longer owns the display mode it writes against — the user may have enabled
  adaptive brightness themselves — so the event is AMBIGUOUS, which is not the same as proving the
  framework wrote it. When the recovery itself fails the app still does not pause: pausing would print
  "Manual Override Detected", the exact misattribution this exists to stop, and a failed mode write
  means `WRITE_SETTINGS` is gone so the brightness writes are failing too — now checkable rather than
  assumed, since `WriteStatus.DENIED` says the same thing and the diagnostic shows both. This is a
  DEVIATION from Tasker (task567/prof755 never consult the mode) and adds a second mode-recovery site
  beside `setInitialBrightness`, since `runCycle`'s sits inside `if (target != from)`.

- DC-007 [cited]: **Two override diagnostics with different lifetimes, and the event now says which
  detector fired.** Both detector paths converged on `OverrideDetected(observedBrightness)`, so nothing
  downstream could tell the observer route from the animation abort and any recorded `source` would
  have been a guess; the event now carries `OverrideSource{OBSERVER, ANIMATION_BAND}` through
  `postOverrideDetected` and `handleOverride`, which also finally gives `observed` a use — the
  parameter was dead, the function deciding entirely on the post-settle re-read. `lastBrightnessWrite`
  is CONTINUOUS, written once per cycle that writes (not per frame — 50 state updates per sweep would
  churn every Compose consumer), so requested-vs-acknowledged-vs-`deviceMax` is readable on a device
  good enough never to fire an override at all, which is what the owner's device check asks for and
  what a single event-scoped record cannot deliver. `overrideDiagnostic` is EVENT-scoped, written where
  an override is detected or dismissed, and carries source, disposition, observed, settled, expected,
  mode and the write in force. Two records rather than five loose nullables because `PipelineState` is
  a coherent single-consumer snapshot and loose fields admit combinations that cannot occur; two rather
  than one because collapsing them loses the normalization readout in exactly the well-behaved case.
  They identify the mechanism CLASS, never the writer — nothing in the app can identify that.

- DC-008 [cited]: **The band detector's self-explanation now shifts with the provider, not just the
  last acknowledgement — the DC-004 exact match only covered a SYNCHRONOUS device.** With a provider
  that applies a write a frame late, the read-back inside `write()` returns the PREVIOUS frame's
  value, so every band read differed from the acknowledgement by one frame step and every sweep on a
  normalizing device still tripped — the #126 shape surviving its own fix, and an asymmetric sibling
  gate against `isSelfWrite`, which this train had already taught to match a set. The band is now
  SHIFTED by `acknowledgedDomain - requestedDomain` of the latest acknowledged frame, which explains
  a clamped range and a lagging one alike and is exactly today's band on a device that stores what it
  is given. Four sibling defects went with it: `AnimationOutcome` carries `lastResult` (any status),
  so a sweep of unacknowledged or refused frames follows the same baseline rule as the two direct
  write sites instead of freezing the baseline; the abort path no longer null-clobbers
  `lastBrightnessWrite`; `hibernate()` nulls `lastAppliedBrightness`, because a stale non-null
  baseline is neither treated as unknown nor true of the screen, which is what made the wake path's
  second event pause; and the commit re-checks `detectOverrides` after the settle, which the monitor
  gated but the commit did not.

- DC-009 [cited]: **Reclaiming MANUAL is itself a brightness event, and it must be ordered and
  suppressed like one.** Flipping the mode back makes many OEM builds re-assert `SCREEN_BRIGHTNESS`;
  that write is not one of ours, so it returned as a NEW override which — the mode now being MANUAL —
  passed the very gate that had just dismissed the first one, handing #127 back one event later.
  `reclaimManualMode()` therefore arms the F64/DB-082 settle window before `forceManualMode()`, as
  every other Tideo-initiated transition does. The reclamation also had to move BEFORE the drift
  check: the framework's own auto-brightness frequently lands within the ±1 deadband, so ordering
  drift first dismissed the event as harmless and left the device in AUTOMATIC indefinitely, since
  `runCycle` only reclaims the mode when the target actually changes. Disposition reporting is
  unchanged — `DISMISSED_DRIFT` still wins — but the recovery no longer depends on it.

- DC-010: **A device check injected in RAW units does not test the DOMAIN rule it is aimed at.**
  §2 10b pinned the ±1 deadband with "one domain step" and "twice that offset" of raw, but on a
  12-bit panel (`deviceMax` 4095, 16.06 raw per domain step) the doubled step quantises back to a
  domain delta of 1 at 28 start values, which would have failed a correct build, while one step
  quantises to 0 at 14 more and tests nothing. The check now converts — `raw(n) = round(n × M / 255)`
  for the domain value it wants — so the injected distance is exactly 1 then 2. The same arithmetic
  explains the 2026-08-30 round's headline oddity, two identical `+20` writes reading "no override"
  then "override": 161→181 is domain 10→11 and 181→201 is 11→13, and both dispositions are correct.

- DC-011: **A pass signal another actor can produce is not evidence, even when the check can fail.**
  §2 10c read `screen_brightness_mode` back as `0` to show Tideo had reclaimed MANUAL, but an OEM
  build may clear the mode on any manual `screen_brightness` write, so the expected observation can
  arrive whether or not the reclaim ran — DB-083's shape (a check that cannot fail on a well-behaved
  phone) one step along, since this one does fail on a device that never clears the mode and passes
  vacuously on one that always does. The check now requires the `DISMISSED_MODE` disposition on the
  Live Debug card as its attribution and records a bare `0` as SKIPPED.

- DC-012: **The pause latch is sticky, so a check that pauses disarms every check after it.**
  `OverrideMonitor` drops an observed change when `isAlreadyPaused`, and only an explicit Resume
  (notification action or UI) clears `paused` — no timeout, no cycle, no screen-off does. §2 10b's
  control pauses deliberately, so running 10b then 10c in order leaves the pipeline paused and 10c
  observes nothing while reporting exactly the quiet its pass condition asks for. Both checks now
  open by requiring System Status → "Manual override" to read `No`. The general shape: a suite whose
  steps mutate the state their successors gate on must re-establish that state per step, not once.

- DC-013: **A confounded pass signal is often separable by a second observable the check already
  has.** DC-011 called §2 10c unattributable because the OEM can clear `screen_brightness_mode`
  itself, but the check's other observable settles it when the injected value is far outside the ±1
  deadband: if the OEM had cleared the mode first, Tideo would read MANUAL, fail the drift test and
  PAUSE, so quiet at that distance can only be the mode branch — the mode readback is confounded
  while the pause outcome is not. Quiet at a NEARBY value stays worthless, since the deadband
  dismisses it under either explanation, which is why the injected distance is now load-bearing and
  said so in the step.

- DC-014: **`deviceMax` resolved 255 on a device whose provider range is 0–4095, and that
  supersedes DC-010's account of the 2026-08-30 round.** `Resources.getSystem().getInteger(
  config_screenBrightnessSettingMaximum)` returned the AOSP default rather than the vendor's value;
  the owner's system slider at maximum reads `4095` from `settings get system screen_brightness`,
  while the Live Debug card reads `Device max: 255` and `Raw requested: 10` for domain 10 — the
  `deviceMax == 255` identity branch of `toDevice`/`toDomain`. Two consequences: the whole 0–255
  domain is written into the bottom 6.2% of the panel, and `toDomain` clamps every provider value
  above 255 to 255, so a slider move anywhere in the upper 94% of the range reads as a domain delta
  of 0 and is dismissed as drift. DC-010 read the panel's 12-bit depth as the app's M and explained
  the round's two `+20` results as quantisation; the app converts at 255, so those injections were
  20 domain apart, both far outside the deadband, and the first one's silence is unexplained rather
  than correct. The rule DC-010 states still holds wherever M ≠ 255 — what was wrong was verifying
  the number the hardware has instead of the number the code uses.

- DC-015: **A quiet injection is two different events wearing one face.** A check that records only
  "did it pause" cannot separate `handleOverride` running and judging the value from the monitor
  never delivering it — a closed gate, the F64/DB-082 settle window, or the DC-003 self-write
  adoption — and only the first is the check passing. §2 10b now records "Last override" and
  "Override seen" after each half, where a fresh `DISMISSED_DRIFT` is the pass and a stale or absent
  timestamp means the event was dropped upstream.

- DC-016: **Both consequences DC-014 drew are withdrawn, and the detection one was a reasoning
  error rather than a wrong device fact.** The pause test compares an observed value against Tideo's
  OWN last write, never against a second user value, so even a read clamped to 255 still registers a
  large delta against a baseline of 10 — override detection cannot go blind at high brightness by
  that mechanism whatever `deviceMax` resolves to, and the owner confirms it never has. The 6.2%
  ceiling is withdrawn on the owner's report that brightness works normally; the override history
  spanning the full domain corroborates it for the SHIPPED app but not for this branch, being
  v1.9.2 data on a persisted history. What survives is narrower and unresolved: the 1.10.0-debug
  card reads `Device max: 255` and `Raw requested: 10` for domain 10 on a device whose provider
  stores 4095 at full slider, so either the display is wrong or this train regressed a resolution
  that worked in 1.9.2 — and §2 10d is nothing but a reading of that card, so it cannot be trusted
  until this is settled. Pair the two at one instant to settle it: the card's "Current brightness"
  against `settings get system screen_brightness`, where an equal pair means the app really is
  writing raw-identity and a ratio near 16 means only the display is wrong.

- DC-017: **The device's `config_screenBrightnessSettingMaximum` really is 255, so `deviceMax` reads
  it correctly and nothing about it regressed.** `adb shell cmd overlay lookup --verbose --user
  current android android:integer/config_screenBrightnessSettingMaximum` (owner, 2026-08-30)
  resolved to `255` from "the default configuration of android", the one other package in the stack
  skipped for want of an entry — no runtime resource overlay touches this resource on that phone.
  That kills both horns of the fork DC-016 left open: the Live Debug card is right, and the
  discovery block is byte-identical between `v1.9.2` and this branch (`git show
  v1.9.2:platform/src/main/kotlin/com/tideo/autobrightness/platform/brightness/ScreenBrightnessController.kt`),
  so this train cannot have regressed it and 1.9.2 converted on the same identity branch. The
  provider's 0–4095 range is therefore OEM-private and not derivable from this resource by ANY
  `Resources` object, which also means "resolve it through `context.resources` instead" would return
  the same 255 here. All that is left unsettled is whether the OEM normalizes Tideo's 255-scale
  write on the way in, which §2 10d now reads directly.

- DC-018: **A ratio cannot say who did the scaling, so DC-016's settling test could not settle
  anything.** It paired the card's "Current brightness" (app domain 0–255) against `settings get
  system screen_brightness` (provider raw) and read a ratio near 16 as proof that only the display
  was wrong, but that same ratio is produced just as well by the app writing raw-identity and the
  provider normalizing it afterwards — the two hypotheses the comparison existed to separate. Its
  equal branch was sound and its ratio branch was not, which is enough to void it, and the card
  already answers what it was asking: "Raw requested" and "Device max" come off the same
  `BrightnessWriteResult` that `toDevice` produced, so `Raw requested: 10` for domain 10 IS the
  identity branch, with no adb needed. The discriminator is a SETTLED provider reading rather than
  an instantaneous pair — drive the top of the domain, let it settle, then read `screen_brightness`
  and `screen_brightness_float`: about 4095 with a float near 1.0 means the OEM normalized our write
  and there is no ceiling, while a value that stays near 255 with a float near 0.06 means the
  ceiling is real. The general shape: where two mechanisms predict the same number, take the reading
  where they predict different ones — here, after the provider has finished with the value.

- DC-019: **`Resources.getSystem()` is documented as unaffected by runtime resource overlays, which
  is exactly how OEMs retune framework config.** `deviceMax` resolves
  `config_screenBrightnessSettingMaximum` through that global object, so on a phone that DOES ship
  an RRO for it Tideo would convert against AOSP's 255 while the provider expects the overlaid
  range, silently and with no diagnostic; `context.resources` is an application `Resources` and does
  apply overlays. DC-017 establishes this is not the owner's phone's defect — no overlay exists
  there and both paths return 255 — so switching is latent-defect hygiene for other hardware rather
  than a fix for anything observed, and it stays frozen with every other conversion change until the
  owner rules (STATE Open questions).

- DC-020: **The 2026-08-30 round already carries a lead the identity branch cannot explain.** Its §2
  10b starting raw, read straight from the provider while the app was driving the bottom of the
  curve, was `161` — exactly round(10 × 4095/255) — at a level the Live Debug card reports the app
  requesting as raw `10` on a `Device max: 255`. On the identity branch Tideo cannot have written
  161, so either something else rescaled our write or the value predates it, and the first is
  precisely the hypothesis DC-018's settled reading tests. It is a lead and not evidence: the 161
  and the card reading were taken at different moments in the round, and the raw could equally be
  slider residue from before the app last wrote.

- DC-021: **The best remaining explanation is that the 0–4095 scale lives BELOW the app-facing
  Settings API rather than inside Tideo.** On that reading OxygenOS keeps AOSP's 0–255 contract for
  `Settings.System.SCREEN_BRIGHTNESS` as apps see it and stores a 12-bit value underneath, so
  Tideo's identity branch is right and the shell's `settings get` reports the stored scale rather
  than the app's — domain 10 written, 10 read back, 161 in the shell. It accounts for everything
  observed at once: the card, the slider, the byte-identical 1.9.2 behaviour, and the absence of the
  per-cycle re-sweep the rival model requires, since a `read()` returning 161 against a target of 10
  would re-animate every cycle forever and the owner reports none. It also rehabilitates DC-010's
  arithmetic while relocating its attribution — the 10b injections were 1 and 3 domain apart, not
  the 20 DC-014 computed, because the shell's 181 and 201 reach the app as 11 and 13. **It stays a
  hypothesis:** every reading so far is consistent with it and none is consistent ONLY with it, and
  §2 10d driven at the top of the domain is what separates it from a real ceiling.

- DC-022: **A model that explains a quiet half is still not a reading of it, so §2 10b stays
  re-opened.** DC-021 predicts the observed quiet-then-pause exactly and it is tempting to call 10b
  passed on the strength of that fit, but DC-015 already names why the inference is unavailable: a
  quiet half is `handleOverride` judging the value and the monitor never delivering it wearing one
  face, and the fit is equally good either way. Marking it passed would certify a check that may
  never have run, to save one observation the re-run takes anyway — "Last override" after each half,
  where a fresh `DISMISSED_DRIFT` is the pass. What DC-021 does change is the framing: DC-014's
  "unexplained, not correct" overstated it, because a correct build now has a natural account of
  that silence, and the re-run decides between the account and the drop.

- DC-023: **`deviceMax`, `requestedRaw` and `acknowledgedRaw` all name hardware, and every one of
  them is an app-facing Settings API value.** `deviceMax` is `config_screenBrightnessSettingMaximum`
  and the other two bracket `Settings.System.SCREEN_BRIGHTNESS`, so `settingsApiMax`,
  `requestedSettingValue` and `readBackSettingValue` — with the Live Debug labels moving to
  "Settings API max" and "Settings value requested" — describe what the code actually reads
  whichever way DC-021 resolves; this is a naming defect in its own right, not a consequence of the
  hypothesis, and the present names are what made DC-014 read a provider number as the app's. It is
  deferred to AFTER the round rather than taken now, because §2 10b and 10d cite those two card
  labels verbatim and the owner would be reading a script that no longer matches the APK in hand.

- DC-024: **`screen_brightness_float` does not exist on the owner's device** — `settings get system
  screen_brightness_float` returns `null` (owner, 2026-08-30), so the float half of §2 10d's reading
  is dead there and the settled integer alone carries the verdict. Consistent with DC-021, where the
  OEM has replaced the platform float path with its own 12-bit integer rather than layering on it,
  though a missing setting is weak evidence for anything on its own.

- DC-025: **Read: the 0–4095 scale sits BELOW the app-facing Settings API, so DC-021 is the
  device's behaviour and not a hypothesis, and nothing is capped.** §2 10d run as written on
  1.10.0-debug vc24 (owner, 2026-08-31) with Tideo driving the top and nothing touched — raw lux
  2061.5, `Service: Running`, `Manual override: No`, `Target brightness: 255` — the card read
  `Requested → acknowledged: 255 → 255`, `ACKNOWLEDGED`, `Raw requested: 255`, `Device max: 255`,
  while the settled `adb shell settings get system screen_brightness` read **4095** (three samples
  at 4095, one transient 3019 ≈ domain 188 between them; `screen_brightness_float` still `null`,
  DC-024). That pair separates the two models where DC-018's ratio could not: the app's own
  read-back of `SCREEN_BRIGHTNESS` returned 255 for the same key the shell read as 4095, so the two
  callers are seeing different scales, and a real 6.2% ceiling would instead have left the stored
  value near 255 with the panel dim. Consequences: the identity branch is correct and Tideo's whole
  0–255 domain reaches the whole panel; the card's five 255s are the right record of that; DC-014's
  "20 domain apart" stays void; and DC-003(b)'s per-cycle re-sweep cannot arise here, because
  `read()` returns the app scale and matches the target. **Not discharged by this:** §2 10b, which
  still needs "Last override" read after each half (DC-015, DC-022).

- DC-026: **The owner's ruling — no fix; the split scale is the device's reality and the workaround
  is where the effort goes** (2026-08-31, "if we fix this it reeks of 'but does it work in theory'").
  Nothing in the conversion path changes: `deviceMax` keeps `Resources.getSystem()`, so DC-019's
  overlay switch is declined as hardening for hardware nobody has, joining auto-learning the device
  maximum in the rejected set, and DC-003's two trades stay as built. What the reading does change
  is every DEVICE CHECK that goes through adb: `settings get/put system screen_brightness` speaks
  the stored 12-bit scale on this phone while the app speaks 0–255, so measure the SHELL's own
  ceiling and convert with that (S = 4095 here), never with the card's `Device max`, which is the
  app-facing maximum and need not equal it. §2 10b's "at M = 255 the conversion is the identity and
  `raw(n) = n`" was exactly that mistake — it would have injected two more domain-adjacent values
  and re-read the same silence — and is rewritten to S. DC-010's rule survives unchanged: pick the
  raw value for the domain value you want; only the scale it converts with was wrong.

- DC-027: **§2 10b passes on a device — one domain step is dismissed as drift, two steps still
  pause, and the quiet half was JUDGED rather than dropped.** Re-run on 1.10.0-debug vc24 (owner,
  2026-08-31), both injections converted on the shell's own ceiling as DC-026 requires (S = 4095)
  and each read off the Brightness Writes card. `raw(d + 1)`: stored `1092` → domain 68 → injected
  `1108` = domain 69; no pause, `Manual override: No`, `Last override: DISMISSED_DRIFT (OBSERVER)`,
  `Observed / settled / expected: 69 / 69 / 68`, `Requested → acknowledged: 68 → 68 ACKNOWLEDGED`;
  control `raw(d + 2)`: stored `931` → domain 58 → injected `964` = domain 60, and it paused, with
  the notification and `60 / 60 / 58`. That is the reading DC-015 and DC-022 held the check open
  for, discriminating on three axes at once: the disposition names the drift branch, so
  `handleOverride` ran and judged the event rather than a closed gate, the F64/DB-082 settle window
  or DC-003's self-write adoption swallowing it upstream; `Mode at commit: Manual` in both halves
  separates it from 10c's `DISMISSED_MODE`, which needs `Not manual` (DC-013); and the control
  pausing excludes the detection-disabled build the step exists to fail. The card's "Override seen"
  age line sat below the fold in both captures, so freshness rests instead on each
  observed/expected pair matching its own injection exactly — the same discrimination DC-015 asked
  the timestamp for. Incidentally the round reproduces DC-025 away from the ceiling, the shell
  reading `1092` and `931` where the card read domain 68 and 58 (ratios 16.06 and 16.05 against
  S/M = 16.06), and it closes the last owed reading of the #126/#127 train.

- DC-028: **Expected from the code, and worth recording for exactly that reason — the same
  within-deadband injection 1.10.0 dismisses PAUSES v1.9.2, which is 10b's negative control.** The
  shipped build has no deadband (`git show v1.9.2:…/OverrideRules.kt` has no
  `isRepresentationalDrift`, so a one-step foreign write is simply absent from `expectedValues` and
  `isManualOverride` returns true), so the owner's 2026-08-31 back-to-back run — same phone, same
  conversion, S = 4095 — confirms a prediction rather than discovering anything, and it showed up
  as the notification and paused state because the Brightness Writes card postdates v1.9.2. What it
  buys is the one thing a passing run cannot give an injected check by itself: 10b opens by
  admitting it "fails on a device that never shows the bug", DB-083's lesson is that a device check
  must be able to fail on a well-behaved phone, and one injection yielding opposite outcomes on two
  builds is what makes the pass mean something. It corroborates DC-027's quiet half from the other
  side — the write is demonstrably detectable on this hardware and this arithmetic — though the
  `DISMISSED_DRIFT` disposition stays the direct evidence, since this train also moved the settle
  path (DC-005) and the two builds are therefore not identical upstream. Keep v1.9.2 as the
  known-failing baseline for future 10b runs (DC-005, DC-027).

- DC-029: **A `redact.sh --self-test` failure on Windows is a checkout defect, not a regression, and
  the repair needs a forced re-checkout that `git checkout-index -f -a` does not give.** Git for
  Windows sets `core.autocrlf=true` in its SYSTEM config, so this worktree held CRLF while the index
  held LF — `git show HEAD:scripts/redact.sh` carried 0 CR bytes against 611 in the file on disk —
  and the redactor, which compares bytes rather than lines, was diffing its filtered stream against a
  CRLF copy of itself. Installing the seed `.gitattributes` and running `git add --renormalize .`
  both leave that worktree untouched; only deleting the governed files and restoring them with `git
  checkout -- .` converts them, because `checkout-index` skips what it considers unchanged.
  Renormalisation moved just two blobs, so the harness files were already LF in the index and this
  was never a content problem, and `*.bat text eol=crlf` keeps the Gradle wrapper CRLF on disk while
  normalising it in the index. AMH 14.0.0's own `redact.sh` fixes the same false failure a second way
  with a `--baseline` comparison, and both were taken because the attributes file is what stops every
  other byte-comparing rung from relapsing. This upgrade carried scripts and version surfaces only:
  the hand-applied seed prose for 9.2.0 and MAJORs 10.0.0…14.0.0 rewrites binding rules across
  `RULE_FILES`, and the owner directed in session that it land as its own reviewed unit, which is
  why the version advanced ahead of the policy migration it names.

- DC-030 [cited]: **Working memory stopped caching release standing, and the replacement is a repo-local
  session add-on rather than a deletion.** AMH 14.0.0 makes working memory tree-relative, but
  striking `docs/STATE.md`'s "v1.9.2 is the newest release" sentence without replacing it only moves
  the cost onto whoever resumes cold, so `scripts/session-facts.sh` computes the same facts at every
  session start — tree `versionName`/`versionCode`, the newest `v*` tag on origin, and whether this
  version is released — where staleness is impossible by construction. The shipped banner's own
  release line was rejected for the job because it reads the version from the FIRST LINE of
  `VERSION_FILE`, and this project's version lives inside `app/build.gradle.kts`, so setting that key
  would have created a second source of truth free to drift from the build. The script is
  deliberately absent from `scripts/MANIFEST.sha256` and unshipped, with `scripts/bootstrap.sh` as
  the precedent, and it always exits 0, degrading to an explicit UNKNOWN plus the settling command
  both when origin is unreachable and when no `timeout` exists to bound the probe — a bootstrap that
  blocks a session over a network hiccup is worse than one that says it could not look. Only Claude
  Code runs it, since its hook lives in `.claude/settings.json` and Codex was not observed to fire
  any repository hook on 0.152.1, so for Codex this is prose in `docs/HARNESS_LOCAL.md` and nothing
  more. The completed override-attribution plan was deleted
  rather than archived once 14.0.0 withdrew the retained-in-place exception it had been kept under,
  leaving no redirect or tombstone, while the device results it sat beside stay in STATE as an
  explicitly dated observation rather than as current truth.
