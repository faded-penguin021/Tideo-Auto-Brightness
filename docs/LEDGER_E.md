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
