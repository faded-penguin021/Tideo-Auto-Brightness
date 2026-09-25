# DEVIATIONS & DISCOVERIES LEDGER D — permanent registry (DD-001…)

> **Append-only registry — NEVER archived, compressed, or truncated.** The continuation of
> `LEDGER_C.md`, which closed at its 1000-line cap (D-153 mechanism, DA-001 line-based
> cap). Code comments and docs cite entries as bare `DD-0NN` and must always resolve here, so no
> entry may ever be deleted or summarized away. **Append new maintenance deviations as DD-001,
> DD-002, … at the bottom** — one continuous sequence, never restart numbering. Code + golden
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
> **`LEDGER_E.md`** with this same header discipline and start numbering at **DE-001**.
> The suffix advances as an odometer over A–Z without limit (`_Z` → `_AA`, `_AZ` → `_BA`,
> `_ZZ` → `_AAA`). The volumes form a chain walked from `LEDGER.md`; a volume after a missing
> link is unreachable and is not a volume, however well its name is shaped. The ladder computes
> and prints the next reachable volume name when rollover is due.
> Existing rows are never moved, renumbered, or rewritten by a rollover.

- DD-001: **The light-stall train for Tideo #130 and #132 is closed (2026-09-25): it fixed the
  notification and the defects a test reproduced, and added the diagnostics #132 needs, but it is
  not shown to be the fix for #132's freeze.** Shipped: the notification no longer falls back to
  "Monitoring" on a start command (DC-065), Live Debug's Light Sensor card (DC-066), sensor
  registration after settings load (DC-067), live state cleared by service ownership (DC-068),
  Tasker's dead band restored (DC-063), the proximity damp confined to the reported α (DC-064), the
  pending-latest slot (DC-069, owner Q1 (b)) and the settling path (DC-070, owner Q2 with the
  endpoint revised to "inside the stored band"), each with the entry test that failed first.
  DC-069 and DC-070 are the rule-review rows for Q1 and Q2, and DC-071 is the Tasker check behind
  Q2. The low-accuracy re-admission policy was dropped rather than deferred (DD-004), and the
  dashboard STALE banner redesign waits until the Light Sensor card's signals have been seen in the
  field. Open: H1 (DD-003) and H2 (DD-002); the proximity parity change still owes its device run
  (STATE Owner queue).

- DD-002: **H2, open: the first light event after a wake is sometimes not delivered, and Tideo
  then writes nothing (2026-09-25, from the owner's OnePlus 13 observations of 2026-09-23).**
  Screen-off hibernate nulls smoothed lux, the last reading, the band and the cooldown; on wake
  `reinit()` loads settings before re-registering, and `setInitialBrightness` returns without a
  write when it has no lux, so if no first event arrives an on-change sensor in steady light sends
  nothing and brightness stays where the framework restored it. Tasker is not exposed: task618 on
  prof761's wake reads the sensor itself (act8) and retries up to seven times while accuracy is
  under 2 and trust is off (act9–12), then seeds smoothed lux and the last reading from it. The
  evidence is one intermittent wake that ended on "Monitoring" before DC-065, which a start command
  landing after the wake's cycle explains equally well, while the one instrumented dark wake
  (2026-09-24, screen off 3 min) was healthy: first event 0.0 lx at accuracy 3 within 2 s, then
  about 4 events a second. On a sensor that repeats like that H2 clears itself within seconds, so
  a separating reproduction needs the Light Sensor card's "First event after registration" read
  at once after a wake that stays wrong; no fix is planned until one exists.

- DD-003: **H1, open: the sensor path may go quiet while still enabled (2026-09-25, Tideo #132).**
  In both of #132's logs, which are attachments on the issue and not stored here, the ambient light
  sensor stays enabled at the HAL until the service toggle's `Enable = 0`, which is consistent with
  Tideo's registration surviving but does not name the client and says nothing about delivery to
  Tideo's callback. Neither log contains a stall's onset: their last samples fall 2–4 min before
  each log starts. The evidence that would separate H1 from a dropped reading is, from a build with
  the Light Sensor card and before toggling: a Live Debug screenshot, `adb shell dumpsys
  sensorservice`, and `adb logcat -d` with `adb logcat -G 16M` set beforehand; missing recent
  light events in `dumpsys` alone does not name a firmware fault. The card's callback counters are
  what separate the two, and the OnePlus 13 baseline for sensor-to-callback lag is 172–230 ms
  (daylight, 2026-09-24). Exposure probably depends on event cadence, a strictly on-change sensor
  such as the Pixel's being the likeliest, but no event rate has been measured on #132's device.

- DD-004: **Rejecting a low-accuracy light reading cannot happen on an AOSP framework, so the
  re-admission policy for it was dropped (owner, 2026-09-24).** The framework's JNI receiver copies
  the HAL status into `event.accuracy` only for the motion, magnetic and heart-rate types, and every
  other type, `TYPE_LIGHT` included, gets `SENSOR_STATUS_ACCURACY_HIGH` (3) from the `default:`
  arm in `android_hardware_SensorManager.cpp`, present at `android-12.0.0_r1` and
  `android-16.0.0_r1`; `SystemSensorManager.dispatchSensorEvent` calls `onAccuracyChanged` only
  while delivering an event whose accuracy changed. So "Trust low-accuracy sensor" cannot change
  which light readings pass on an AOSP-derived framework (crDroid included), and accuracy cannot
  recover without a new event; OxygenOS admitted 0 lx readings with the setting off. Withdrawn with
  it: that the OnePlus reports accuracy ≤ 1 at 0 lx, and that a wake which stays on "Monitoring"
  points to accuracy. Reopen only as a new analysis if the Light Sensor card, which records every
  callback's accuracy and the trust setting in effect, shows an OEM framework reporting light
  accuracy ≤ 1. Still unchecked: whether Tasker re-evaluates prof760 when `%AAB_TrustUnreliable`
  changes.

- DD-005: **Claims the light-stall analysis withdrew, so they are not reintroduced (2026-09-25).**
  A stale "Last sample" does not show that nothing reached the app, since one rejected final
  reading goes stale in step with the last completed cycle and both screens truncate ages to whole
  minutes. Live Debug's raw lux is the last processed reading, not the latest received one, and
  acknowledged writes with normal cycle times describe completed work only, not the absence of a
  block. Re-feeding the last reading cannot finish a transition, because α reaches 0 above a
  drop's band (DC-070), and "α ≥ 0 on every smoothed row" is not a parity test, because task535
  subtracts the previous cycle's stored threshold. "No more cycles than before plus one" is not a
  no-ghosts test either, since a correct pending slot can exceed it over a long burst (DC-069).

- DD-006: **The settling path passed its device check (owner's OnePlus 13, debug build of
  `4cace33`, dark room, 2026-09-25; DC-070).** A flashlight flickered at the sensor for about
  10 s and ended dark; brightness, sampled every ~250 ms, then kept falling for about 5 s
  (980 → 691 → 434 → 273 → 177 → 161 on the device's raw scale) and held 161, the same value as
  the dark baseline before the run, for the remaining 24 s, where R6's run on `938058a` had held
  42 lx's brightness. The Light Sensor card read a last cycle of `SETTLED`, with later 0 lx
  readings refused as `DEAD_BAND`. The silent-sensor continuation was not exercised: this sensor
  keeps reporting about 4 times a second, so real readings finished the settling and the
  "settling" count did not move. Proximity's device check (DC-064) was offered in the same session
  and skipped by the owner, so it stays open.


- DD-007: **The OnePlus 13's light sensor keeps reporting in a dark room, although it declares
  itself on-change (2026-09-25, owner's phone).** `dumpsys sensorservice` lists "OPLUS Fusion
  Light Sensor Next Gen", Tideo's light sensor, with `flags: 0x00000002` (on-change), yet its last
  50 events were all 0.00 lx over 13 s, about 3.8 a second, and Tideo's Light Sensor card counted
  151 callbacks in 41 s at 0 lx. Each event carries further fields that keep changing, one of them
  counting up, which probably explains the steady stream, but that is inferred. On this phone a
  missed or dropped reading is therefore replaced within about 250 ms, which hides the dropped
  reading, the unfinished transition and H2 alike; a sensor that falls silent in steady light, as
  #132's Pixel may, would expose them (DD-003).
