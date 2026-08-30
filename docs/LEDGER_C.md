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
