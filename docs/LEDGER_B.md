# DEVIATIONS & DISCOVERIES LEDGER B — permanent registry (DB-001…)

> **Append-only registry — NEVER archived, compressed, or truncated.** The continuation of
> `LEDGER_A.md`, which closed at its 1000-line cap (D-153 mechanism, DA-001 line-based
> cap). Code comments and docs cite entries as bare `DB-0NN` and must always resolve here, so no
> entry may ever be deleted or summarized away. **Append new maintenance deviations as DB-001,
> DB-002, … at the bottom** — one continuous sequence, never restart numbering. Code + golden
> vectors are ground truth; if an entry conflicts with current code, trust the code and correct the
> entry (don't delete it). **Search before appending (DA-006):** grep the ledger files for the topic
> first — extend or cite an existing row rather than append a near-duplicate.
> **Keep new rows concise and at or below `LEDGER_ROW_CHAR_CAP`** in `amh.conf` — the key is
> named here and deliberately not restated as a number, because nothing checks this preamble
> against the config and a copied number goes stale the first time the cap moves. Read it from
> `amh.conf`; the ladder prints it only on a run that has a new row to check, so it is not a
> substitute. Counted with `LC_ALL=C` over the whole row, line breaks included, so ASCII is one
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
> **`LEDGER_C.md`** with this same header discipline and start numbering at **DC-001**.
> The suffix advances as an odometer over A–Z without limit (`_Z` → `_AA`, `_AZ` → `_BA`,
> `_ZZ` → `_AAA`). The volumes form a chain walked from `LEDGER.md`; a volume after a missing
> link is unreachable and is not a volume, however well its name is shaped. The ladder computes
> and prints the next reachable volume name when rollover is due.
> Existing rows are never moved, renumbered, or rewritten by a rollover.

- DB-001 [cited]: **A failed Extra Dim level write left the previous, stronger level on screen.**
  When the latch already said engaged, `apply()` wrote the new level, skipped activation (correctly —
  already active) and returned **without checking whether the level write succeeded**. Levels fall as
  the target rises, so the stale level is typically *stronger* than the one just requested: the user
  brightens the room, the secure write fails (revoked grant, SettingsProvider error), and the screen
  stays dark. The debug line then reported `ON <new level>`, sending device diagnosis after the wrong
  suspect. Fixed: a failed level write while engaged best-effort deactivates and latches UNKNOWN so
  the next cycle re-engages from scratch, and the success diagnostic is emitted only after a write
  that actually landed. **Narrowed during implementation by an existing test:** the first attempt
  cleared activation from *any* non-false latch, which broke the pre-existing "a missing level must
  never activate an unknown OEM level" invariant — from UNKNOWN nothing of ours is on screen, so
  there is nothing to clear. Evidence: three cases in `SuperDimmingCoordinatorTest` (stale-level
  clear, honest diagnostic, re-engage after recovery). `[cited]`: `SuperDimmingCoordinator.apply`.

- DB-002 [cited]: **Backup carried runtime state; the sanitizer's first design would not have fixed
  it.** `aab_settings.json` is backup-eligible for the user's brightness configuration, but it also
  holds `serviceEnabled` and `contextOverride` — what *this installation was doing*. Restoring them
  asserts a running state the new device has not established, and pins a fresh install to a manual
  context lock whose matching `%AAB_ProfileUser` identity is not backed up (incoherent, not merely
  surprising). **Declined the proposed remedy** (split runtime fields into a non-backed-up
  DataStore): that is a schema migration of the app's central settings object, and backup eligibility
  is a property of the *file*, so the only question is what the file may say after a restore.
  Answering it at the restore boundary — `SettingsBackupAgent.onRestoreFinished()` →
  `SettingsBackupSanitizer` — keeps one schema, one serializer, one migration path.
  **Then the test found the real hole:** `serviceEnabled` defaults to **`true`** and kotlinx omits
  default-valued fields, so a device with the service running backs up a file with *no*
  `serviceEnabled` key at all. The first sanitizer reset only fields that were present, i.e. it
  no-opped on precisely the common case. It now forces the resets unconditionally. The old backup
  test pinned file *paths* — locking in whatever the allowlist happened to say, mistake included; the
  new one asserts semantics (six cases, including unknown-field preservation and unparseable input
  left alone). `[cited]`: `SettingsBackupSanitizer`, `SettingsBackupAgent`.

- DB-003 [cited]: **The F-Droid comparator trusted archive metadata, and its docs overclaimed.**
  `fdroid-check.py compare` read `ZipInfo.CRC` — the checksum an archive *declares* — and never
  decompressed anything, so an entry whose bytes changed while its CRC field did not compared equal.
  This is demonstrated, not argued: `fdroid-check.py selftest` builds that pair at runtime and the
  old code called them identical. Now SHA-256 over decompressed bytes, which also makes a corrupt
  entry a reported failure instead of a silent match. The signing-block reader had two more defects
  the review named correctly: it located the block by `rfind`-ing the magic string over the whole
  file (so entry *content* could decide where a security check starts reading — the selftest includes
  a decoy entry containing the magic), and its bounds check `pos + 8 + length > end + 8` tolerated an
  eight-byte overrun. It is now anchored through the EOCD to the central-directory offset, with
  strict bounds and an exact end-position check. Documentation corrected: the claim that this used
  "the same acceptance criterion F-Droid uses" and "cannot fail on a difference F-Droid would
  forgive" was **wrong** — F-Droid copies the published signature onto its rebuild and verifies it,
  which is a stricter test than equal per-entry content. **Declined: adopt `apksigcopier` here.** It
  verifies a published, upstream-signed APK against a rebuild; both inputs at this stage are ours,
  one signed by a throwaway CI key, so there is no upstream signature to copy until a release exists.
  Also applied from the same review: `persist-credentials: false` on the jobs that mount the
  workspace into the moving buildserver image or run unpinned PyPI tooling. `[cited]`:
  `scripts/fdroid-check.py`.

- DB-004 [cited]: **`SavedProfilesSerializer` limited after reading.** The profile-count and name-length
  limits were applied to the decoded object, so `readBytes()` had already materialised the whole
  file. App-private input, so this is corrupt state rather than an attacker's — but "corrupt state
  cannot make us allocate without limit" is cheap to hold on a path whose entire job is recovering
  from bad data. Bounded to 4 MiB before parsing, derived from the limits that already exist.

- DB-005 [cited]: **Privileged-surface bounds, including one the review did not find.** Four fixes.
  (1) **New finding:** `ShizukuUserService.run()` was called with `stdoutLimit = 0` for `pm grant`
  and `setprop`, and *any* output at all set `overflow`, which the executor treats as failure — so a
  grant that succeeded while printing a warning line was reported as a failed grant, sending the user
  back to the adb instructions for a permission they already had. Replaced with `DISCARD_OUTPUT`:
  drain the pipe (so the child never blocks writing), keep nothing, fail on nothing. The same read
  path stopped boxing every byte through `ArrayList<Byte>`. (2) `waitFor()` after `destroyForcibly()`
  and the reader-thread `join()`s were unbounded, so the "command timeout" bounded the command but
  not the call — now bounded reaps. (3) The Shizuku **permission prompt** had no timeout (only the
  bind did): a dismissed dialog produces no callback, so the listener stayed registered for the life
  of the process and the caller's continuation never ran. (4) All grant requests share one
  `REQUEST_CODE`, so overlapping flows cannot tell their results apart — now single-flight, with a
  second caller told the first is running. Also: unknown non-null service actions no longer fall
  through to `ensureRunning()`. `[cited]`: `ShizukuUserService`, `ShizukuGrantGateway`.

- DB-006 [cited]: **Geo-IP cancellation — written as a rebuttal, ended as a correction.** The review
  called "cancellation disconnects the blocking request" stronger than the implementation
  guarantees. A test was written to disprove it and **failed**, for a reason worth recording:
  `Job.invokeOnCompletion` fires when a job *completes*, and a job parked in an uninterruptible
  `read()` does not complete on `cancel()` — it completes when the read returns. Registering the
  disconnect on the job that then performs the blocking call schedules the rescue behind the very
  wait it exists to cut short; it was dead code for its own purpose. Fixed by structure: the request
  runs in a child coroutine, the parent waits at `await()` (a real suspension point), and the socket
  is closed as that wait unwinds — which is what releases the child. `BlockingReadCancellationTest`
  pins both halves, so the defect cannot return silently and the fix has a reason on file. The
  review's other half is also right and now stated plainly: an **uncancelled** request is bounded by
  30 s connect + 30 s read. `[cited]`: `GeoIpLocationClient.fetchGeoIp`.

- DB-007: **Six audit documents merged into `SECURITY_REVIEW.md`.** `COMPONENT_SECURITY_AUDIT.md`,
  `DEPENDENCY_RELEASE_SECURITY_AUDIT.md` and the four `architecture/*_audit.md` files had begun to
  repeat one another's trust boundaries, call traces, test wish-lists and residual-risk lists, so a
  reader had no way to know which was current. One matrix now carries boundary → invariant → control
  → evidence → residual → status, linked to production and test classes instead of re-narrating them;
  `SECURITY_AUDIT_MODEL.md` stays the implementation-light model. Dated observations (e.g. "no open
  Dependabot alerts on 2026-07-30") belong in `STATE.md`, not in architecture docs. **Partially
  declined:** folding `FDROID_VALIDATION.md` into the RUNBOOK. It is already a single home rather
  than a repetition — the RUNBOOK links to it and does not restate it — and 184 lines of pipeline
  reference inside a change-type playbook would bury the release procedure it is meant to support.

- DB-008 [cited]: **The dimming-strength setpoint is now clamped, not just its effect** (upstream
  Tasker `_SaveButtonDimming` A9–A12, issue #110, owner-reported 2026-08-02). `SoftwareDimming.dimShell`
  has always clamped `strength × dimDynamic` to `[0, 65]` — a fully dark screen locks the user out,
  which is why the panic gesture exists — but the *stored setpoint* kept whatever was typed. A user
  who set 100 saw 100 in the field, read the dimming graph as reaching 100 %, and measured 65 on the
  device. The reporter did exactly that, with `adb shell settings get secure` to confirm. Nothing was
  wrong with the math; **the UI was lying about the input**, which is the bug the owner fixed upstream
  and this row ports.
  Ported: the clamp moved into `AabSettings.validate()`'s per-field clamp block, so the setpoint is
  corrected on **every** write path, and `DraftSettingsViewModel.apply()` announces it (`toast_dimming_
  strength_clamped`) with the value that actually persisted. Apply stays a fixed point — the draft
  snaps to 65, so the field shows what is in effect and the screen is not left perpetually dirty.
  **Deliberate deviation 1 (broader than the Tasker task):** Tasker clamps only in the dimming save
  button; Tideo clamps in the shared validate(). Tasker's per-scene saves write individual variables,
  whereas a Tideo Apply persists the whole settings object, and an imported or legacy profile carrying
  100 would otherwise reproduce the exact lie in another entry path. One rule, every path.
  **Deviation 2 (narrower) — RESOLVED UPSTREAM 2026-08-02, no longer a deviation.** As shipped, A9
  tested `> 64.999999999`, which a float setpoint also satisfies at exactly 65 — so Tasker flashed
  "clamped to 65" for a value it had not changed. Tideo's setpoint is an `Int` and its announcement
  fires only when the value actually moved, because reporting a correction that did not happen is the
  same class of misinformation as the field that showed 100. The owner then changed A9 to
  `> 65.0000000001`, which never fires at 65 either: **the two now agree**, and no Tideo code changed
  to make that true. Enumerated so a future parity pass reads this as convergence, not as a divergence
  to close.
  The pre-Apply `SettingsValidator` advisory is kept and reworded to describe what Apply will do.
  Evidence: three cases in `DraftSettingsViewModelTest` (clamp + draft snap + fixed point; announce
  only when moved, including the exactly-65 case; below-cap untouched). Note for future extraction
  work: `docs/rebuild/extraction/_source/` predates this upstream change, so the frozen XML does not
  contain A9–A12 — the task text in issue #110 is the source of record for it.
  **Owner-confirmed 2026-08-02, then superseded the same day:** the flash stays suppressed at exactly
  65 — first as an accepted deviation, now as plain parity, since upstream A9 became
  `> 65.0000000001`. Either way the behaviour is settled: **do not make Apply announce a clamp at
  exactly 65.**
  `[cited]`: `AabSettingsMapper.validate`, `AabSettings.MAX_DIMMING_STRENGTH_SETPOINT`,
  `DraftSettingsViewModel.apply`, `SuperDimmingScreen`, `SettingsValidator`.

- DB-009 [cited]: **Optional "only when plugged in" panic gesture — and the always-on accelerometer it
  exposed** (upstream Tasker `_PanicButton` A3 + `%AAB_PanicPlugged`, issue #110, owner-reported
  2026-08-02). Two things in one row because the feature and the battery fix are the same code path.
  **The feature.** A new global pref `panicRequiresPlugged` (default **OFF** — the gesture is the way
  out of an unreadable screen, so it must keep working on battery unless the user deliberately narrows
  it) gates the panic gesture on external power, mirroring the upstream Java's early veto. Global, not
  a profile field: a context rule swapping profiles must not change whether the safety escape hatch
  works. Surfaced on Live Debug beside the sensitivity slider, matching the Tasker scene's placement.
  **The battery bug it exposed.** Tideo's structure differs from Tasker's in a way that mattered:
  there, the profile's Orientation STATE does the watching (the platform's job, effectively free) and
  the A3 Java registers the accelerometer only for the ≤10 s shake window. Here the orientation watch
  IS the trigger, so `AndroidPanicSensorSource` held `TYPE_ACCELEROMETER` (plus
  `TYPE_LINEAR_ACCELERATION` where present) at `SENSOR_DELAY_GAME` ≈ 50 Hz for the entire life of the
  service — **including with the screen off, where the gesture cannot fire at all**, because arming
  already required `interactive`. Registration is now demand-driven: the listeners are held only while
  the gesture could actually fire (`interactive && (!requiresPlugged || plugged)`), re-evaluated on
  SCREEN_ON/OFF and POWER_CONNECTED/DISCONNECTED. Plugged state is seeded from the **sticky**
  `ACTION_BATTERY_CHANGED` (`registerReceiver(null, …)` reads the last broadcast without registering)
  and then maintained on the two explicit power transitions — `ACTION_BATTERY_CHANGED` itself is
  deliberately not registered, since it fires on every level/temperature tick, i.e. exactly the
  always-on cost being removed. **A test caught a real regression in the first version:** releasing the
  sensor called `endWindow()`, which consumes the gesture — so after every screen-off the user needed a
  full flip-straight-and-back before the gesture would arm again. Releasing is not an outcome of the
  gesture; it now calls a `resetWindow()` that clears the in-flight window without latching the
  D-021 consume-until-re-entry gate. The detector is also reset so the next registration starts from an
  unseeded gravity estimate rather than a stale one. Deliberately **not** done: a two-rate scheme
  (slow orientation watch, 50 Hz only inside the window). It would cut the remaining screen-on cost,
  but `sustainedFrames` and `PanicGate.REARM_FRAMES` are tuned in FRAMES at ~50 Hz, so changing the
  rate silently changes the gesture's timing; that is a separate, tested change. Evidence: four cases
  in `PanicSensorSourceTest` (no registration while the requirement is unmet; register/release across
  power transitions; release/re-arm across screen off/on; a window interrupted by screen-off does not
  survive). **Device verification for the whole 1.8.2 train lives in one place** —
  `DEVICE_TEST_SCRIPT_1.8.2.md` — which superseded the two per-round scripts this train produced
  (`SECURITY_ROUND_TEST_SCRIPT.md`, `PARITY_ROUND_TEST_SCRIPT.md`). Same reason as DB-007: three
  overlapping checklists is how a reader ends up not knowing which one is current.
  **Owner-confirmed 2026-08-02:** wireless charging counting as plugged
  (`EXTRA_PLUGGED > 0`, matching the upstream Java) is accepted as-is — shaking a phone upside down
  while it stays on a Qi pad is not a scenario worth narrowing the check for. Do not restrict this to
  USB without new evidence. `[cited]`: `AndroidPanicSensorSource`, `AabSettings.panicRequiresPlugged`,
  `LiveDebugViewModel.setPanicRequiresPlugged`, `AmbientMonitoringService.startPanicGateWatcher`.
- DB-010: **Per-round device scripts were accreting into a graveyard; they now have a lifecycle.**
  Every round that needed owner verification added its own `*_TEST*.md` and none were ever removed —
  `RESUME_CONTEXT_TEST.md` (DA-018, executed and shipped in 1.8.1), plus this train's
  `SECURITY_ROUND_TEST_SCRIPT.md` and `PARITY_ROUND_TEST_SCRIPT.md` (already folded into
  `DEVICE_TEST_SCRIPT_1.8.2.md`, DB-009). Four scripts, three of them describing work the owner had
  already signed off, and no rule saying which one a reader should run. The rule (RUNBOOK §6): there
  is **one permanent** script, `DEVICE_TEST_SCRIPT.md`, and **at most one** ephemeral
  `DEVICE_TEST_SCRIPT_<version>.md` for the unreleased train; when that version ships, anything with
  standing value is folded into the permanent script and the round file is **deleted**. Retiring
  `RESUME_CONTEXT_TEST.md` under that rule is what proved the fold is the load-bearing half — the
  DA-018 fix (Resume re-evaluates the rules rather than resetting to Default, and the label agrees
  with the settings screens when nothing matches) was **not** covered by the permanent script's
  step 25, which only checked that the Resume banner appears. Deleting the file without folding
  would have silently lost a regression check for a real shipped bug; it is now step 25's sub-bullets.
  **`docs/history/` is deliberately NOT the archive** — its README defines it as the frozen record of
  the one-time Tasker migration, and filing maintenance-era rounds there would make "frozen" a lie
  and put stale scripts back in a reader's path. Git history is the archive; a deleted script is one
  `git log --diff-filter=D` away. Also de-timestamped the permanent script's headings ("— NEW 1.8.0",
  "— NEW S14"): the D-NNN citations in each section already carry provenance, and version markers on
  shipped sections just read as staleness. Section **numbers** are cited by code
  (`TouchTargetsA11yTest` → §12) and by ledger rows (§11 step 38, §13) — append sections, never
  renumber.
- DB-011 [cited]: **The plugged-only panic restriction was a registration gate, not a firing gate —
  and an unresolved settings snapshot read as "no restriction".** Owner device pass 2026-08-02,
  case C4: with `%AAB_PanicPlugged` ON, the gesture fired on battery. Two independent defects, both
  needed for the observed failure. (1) `AndroidPanicSensorSource.canFire()` decided only whether to
  hold the accelerometer registered, and that decision is re-run **only** on screen/power broadcasts;
  arming itself checked orientation, display and proximity but never the power requirement. So any
  registration taken under a wrong or not-yet-known requirement stayed in force indefinitely — the
  gate was advisory where it needed to be authoritative. The requirement is now evaluated at ARM time
  as well, in one shared `pluggedRequirementMet()`; registration remains a battery optimisation.
  (2) The caller read `(contextEngine.effectiveSnapshot ?: AabSettings()).panicRequiresPlugged`, and
  `effectiveSnapshot` is **null until the first context evaluation completes** (`ContextEngine` seeds
  `_effective` from a launched coroutine in `start()`). Fabricating `AabSettings()` answered "the
  user did not ask for plugged-only" for a user who had — a safety restriction failing OPEN on
  "unknown". Since panic **stops the service**, the natural device sequence is fire → re-enable →
  test again, which lands squarely in that window: a service re-enabled while already unplugged
  registered the sensor and never saw another power broadcast to correct itself. The lambda is now
  `() -> Boolean?`, the null is handled once in the source as **fail-closed**, and
  `AmbientMonitoringService.startPanicDetector` awaits `effectiveFlow.filterNotNull().first()` before
  collecting, so unknown is transient by construction rather than by luck. Fail-closed costs the
  gesture the few hundred ms before the first evaluation; failing open costs the user the setting
  they asked for. Evidence: `requirementTurningOnAfterRegistration_stopsTheGesture_withNoPowerBroadcast`
  fails on the pre-fix source (`expected:<0> but was:<1>`) — the arm-time gate is load-bearing, not
  belt-and-braces — plus two tests pinning that unknown neither registers nor fires and that it
  re-arms as soon as the snapshot resolves. **A nullable value crossing a module boundary is the
  place to look: `?: SomeDefaults()` at a call site turns "I don't know" into a confident wrong
  answer, and the type system stops helping.** `[cited]`: `AndroidPanicSensorSource.requiresPlugged`,
  `AppModule` panic wiring, `AmbientMonitoringService.startPanicDetector`.
- DB-012 [cited]: **The service's cached privilege tier never saw a grant made while the screen was
  on.** Owner device pass 2026-08-02, case F3/F4: after `pm grant WRITE_SECURE_SETTINGS` was
  restored, Extra Dim kept reporting the missing permission — "restarting the app resolved that".
  G1-F5 caches the tier and refreshes it at the service's resume points (start, screen-on) to keep
  two Binder checks off every dimming cycle; that is still right. What was wrong is the assumption in
  its comment that "a post-start ADB/Shizuku grant is still picked up on the next wake": **every
  `AppModule(...)` call site constructs its own `AndroidPrivilegeManager`** (the UI builds one per
  ViewModel/screen), so the UI's `refresh()` on resume updates an instance the service has never
  heard of. With the screen never turning off, nothing re-detected. Fix: `SuperDimmingCoordinator`
  re-detects on the one path where a stale cache is *visible* — it wants to dim and believes it may
  not write — rate-limited to once per 10 s, so the happy path adds no Binder call (pinned by
  `elevatedPath_neverReDetects`) and a permanently unprivileged user pays one check per 10 s, not one
  per cycle. Deliberately NOT done: making `PrivilegeManager` a process singleton. That is the real
  root cause and a better end state, but it changes lifetime and threading for ~10 call sites
  including onboarding, and this train is at its tag; the self-heal is contained and testable. Left
  as an Owner-queue candidate. The rate-limit timestamp is nullable, not `0L` — a clock that starts
  at 0 (tests, and a device moments after boot) would otherwise swallow the first re-detect, which is
  how the first version of the test failed. `[cited]`: `SuperDimmingCoordinator.refreshTier`,
  `AppModule` dimming wiring.
- DB-013: **A test script is executable, and this one shipped a device-wide destructive command.**
  Section J of `DEVICE_TEST_SCRIPT_1.8.2.md` said `adb shell bmgr restore <token>`. Without a package
  argument that is **not** an app-scoped restore: it replays the backup set for every package in it,
  overwriting the current data of unrelated apps. The owner ran it on their daily driver on
  2026-08-02 and lost stored settings across many apps — irreversibly, since the only "undo" would be
  a newer backup that does not exist. The step now requires
  `bmgr restore <token> com.tideo.autobrightness.debug`, carries a stop-sign warning, and the script
  opens with a blast-radius line naming J as the only section that can touch anything outside `$PKG`.
  **The rule this earns: a device script is code, and its commands get the same "what does this touch
  if I am wrong" reading as a migration.** Scope-by-default is the test: `pm revoke $PKG`,
  `am crash $PKG`, `settings get` are all self-limiting, and every other step in this file was; a
  device-wide verb hidden behind an app-shaped one is exactly the shape to catch in review.
  **Owner decision, same day: section J is retired, not re-run** — recorded in `STATE.md` Decided
  non-items. So the DB-010 fold does NOT carry J into `DEVICE_TEST_SCRIPT.md`; what carries is the
  accepted residual, that `SettingsBackupAgent.onRestoreFinished()` is never exercised outside a real
  restore and nothing proves it is invoked (the sanitizer's decision logic and the allowlist are
  covered; the wiring between them is not). Two lesser instances in the same section, corrected
  together: `bmgr transport …` switches the device's transport globally (restore the original), and
  `bmgr backupnow` on the Google transport commonly no-ops for a sideloaded package, so a "nothing
  was backed up" result was being read as an app defect when it is a transport limitation.

- DB-014 [cited]: **The harness this repo invented was replaced by the harness it became.** The
  maintenance harness originated here (constitution + `STATE.md` + `RUNBOOK.md` + the ledger +
  `scripts/ladder.sh` + session bootstrap), was generalized into `docs/AGENTIC_HARNESS_PROMPT.md`
  and spun out as [AMH](https://github.com/faded-penguin021/AMH). The two then diverged, and the
  local copy was the one without upstream's fixes. Converging on **amh-v3.0.0** (`full` profile):
  the five shipped scripts are upstream's byte-for-byte, hash-checked against
  `scripts/MANIFEST.sha256` every run, so **editing one is now a build failure, not a habit** —
  changes go to `amh.conf`, `scripts/guards/*.sh` or `scripts/verify.sh`. Eight of our eleven
  guards were already upstream's; the other three plus the staged half of the secret scan became
  repo-local guards, and we gained author-identity, shipped-integrity and repo-local-guard rungs
  we never had. Docs moved to the AMH layout (`docs/STATE.md`, `docs/RUNBOOK.md`,
  `docs/LEDGER{,_A,_B}.md`) and the constitution moved from `CLAUDE.md` to `AGENTS.md`, reversing
  D-176's "the name is historical, kept so existing citations stay valid" — upstream's canonical
  name won because compatibility with the harness we now consume outranks compatibility with our
  own old citations. Rows written before this date cite `CLAUDE.md`; they mean `AGENTS.md`.
  `docs/AGENTIC_HARNESS_PROMPT.md` was deleted: it is the AMH repository now, and a stale
  snapshot in `docs/` would read as authoritative while drifting against every upstream release.
  **The durable rule: everything local lives in a declared extension point, and
  `docs/HARNESS_LOCAL.md` is the single record of what and why** — that document is what keeps
  the next upgrade a file copy instead of a merge. `[cited]`: `scripts/bootstrap.sh`.

- DB-015: **The ledger's row-header shape is load-bearing, and 124 of 228 rows did not have it.**
  AMH enumerates rows with `sed -n 's/^- \(D[A-Z]\?-[0-9]\+\)\( \[cited\]\)\?:.*/\1\2/p'`, which
  reads a row only when the ID is the first thing after `- `. Our rows had drifted into two
  shapes: `- D-001: …` and `- **D-060 (S12.7h) — …`. The bold form was invisible to the parser,
  so 68 live citations would have failed as unresolved. Normalized every header to
  `- D-NNN[ [cited]]: **…` by moving the bold OPENING marker after the colon — row text
  byte-identical, per-line `**` count unchanged, nothing deleted, renumbered or reordered; 33
  rows that had no colon gained one, which is the whole of the +33 B delta. **The rule: a row
  header is a machine interface, not formatting.** Two ledger preambles were teaching the old
  shape as the canonical example to copy, which would have silently reintroduced invisible rows;
  a fresh-context review caught that, and `scripts/tests/local-guards.sh` now has a fixture where
  a re-bolded volume must fail rather than pass on rows it never read.

- DB-016 [cited]: **A guard that counts the wrong thing reports the strongest line it has.**
  `scripts/guards/ledger-prefix.sh` — the repo-local half of citation checking, since the shipped
  guard pools volumes and cannot know that a `DB-` row belongs in `LEDGER_B.md` — counted
  *volumes* to decide whether it had checked anything. A volume whose rows did not parse
  contributed zero rows, the prefix loop never ran for it, and the guard printed "every row in
  the volume its prefix names". Not hypothetical: that is exactly the state DB-015 converted away
  from, so re-bolding one volume would disarm the guard for it. Fixed to count rows and fail a
  volume yielding none. **Same guard, second defect, same class:** it read `LEDGER_DIR` and
  `LEDGER_BASENAME` from the environment, but the ladder assigns them without `export` and runs
  each guard as `bash <guard>` — a child process. The guard was always using its own hardcoded
  defaults, invisibly, because those defaults happened to equal the configured values; pointing
  `LEDGER_DIR` elsewhere would have left the real ledger unchecked while the guard reported on an
  empty directory. It now sources `amh.conf` itself. **The rule for every repo-local guard: source
  the config, never inherit it, and make "I checked nothing" a failure that names its subject.**
  Both are fixture-pinned. `[cited]`: `scripts/guards/ledger-prefix.sh`.

- DB-017: **The constitution was cut 42% on Anthropic's Claude-5 context-engineering guidance,
  and one part of it was declined.** Owner-requested test of the freshly-converged harness.
  Applied: judgment over enumerated rules ("write code that reads like the code around it"
  replacing style lists); single mentions (secret hygiene stopped restating what
  `command-guard.sh`'s header says and now points at it, because a restatement is a thing that
  drifts); progressive disclosure (the adapter-authoring spec moved to `docs/HARNESS_LOCAL.md`,
  the module map defers to RUNBOOK's deeper copy, the Gradle rungs to `scripts/verify.sh`); and
  references to artifacts over descriptions of them. 217 → 155 lines, 15.7 → 9.2 KB.
  **Declined, owner decision: auto-memory.** The guidance says to stop maintaining memory files
  because the agent preserves its own. That is per-agent, and this harness's memory is a
  cross-agent, in-repo, reviewable artifact — the ledger's whole value is that a bug found by one
  session teaches a *different* agent nine sessions later, which no private memory can do.
  **Also declined: a skill for verification.** Verification here is already one command with the
  detail inside `scripts/verify.sh`; wrapping that in a skill is indirection over an interface
  that is already one word.
  **Two cuts were caught and reverted before landing**, both the same shape — prose that looked
  redundant because another file also said it, where the constitution was the only thing that
  said it *in time*: the "never read the 1.6 MB source XML wholesale, go via `XML_RECIPES.md`"
  hazard (`XML_RECIPES.md` states it, but only after you have already opened the wrong file), and
  "no new dependency unless the change clearly warrants one", which lived nowhere else in live
  prose. **The rule this earns: when compressing legislation, "another file says it" is not
  sufficient — ask whether the reader reaches that file BEFORE the mistake.**
  Supersedes DA-021's decline of a harness rewrite from an external context-engineering blog:
  that source was third-party and general, this one is first-party and model-specific, which is
  the new evidence Decided non-items require.

- DB-018: **The rule-review pass on DB-017 found 12 defects, 3 of them binding — and the worst
  was a rule whose deletion was invisible precisely because the thing it guarded is invisible.**
  DB-017 cut the constitution 42%; the mandatory review landed after the commit (rate limits) and
  is recorded here rather than in that row because the row is immutable.
  **The blocking one:** the reduction dropped "never a personal address, **including one handed to
  the agent in its own session context**". Claude Code injects the owner's real email into every
  session's context, so an agent reading only "use the owner's handle or a no-reply alias" while
  holding that address has nothing telling it *that* address is the forbidden one — the natural
  reading is that a harness-supplied owner email is the sanctioned identity. The ladder's identity
  rung explicitly cannot tell a personal address from a work one, so nothing downstream catches
  it, and a pushed commit cannot be repaired without the rewrite this repo forbids. The clause was
  the entire mechanism. **The rule: when a rule's subject is something the agent is *handed*
  rather than something it fetches, the rule is invisible to a redundancy check — no other file
  mentions it, because no other file can see it.**
  **The second:** the adapter-coverage table written in the same change credited Codex with a
  bootstrap, a command rail and full deny rails, all "per upstream template". Its own adapter
  files say the opposite in terms — no session-start hook, no pre-shell hook, and prefix rules
  with no path-glob operand. The table was three lines above a paragraph correctly describing
  Codex's actual situation, and it violated the honesty requirement stated seven lines above it.
  A false coverage claim in the document the constitution names as the single answer is worse
  than no document.
  **The third:** "say which layer holds a rule whenever you add one" was cut to a narrower remark
  about this file not restating script coverage. That sentence is why `scripts/guards/doc-facts.sh`
  exists (drift incident d66de4c) and why RUNBOOK has to write "no guard enforces this bullet".
  **What this says about compressing legislation:** every one of the three survived a
  "is it stated elsewhere?" check and failed a "would a session do the wrong thing?" check. The
  first is the test an author can run; only a fresh reader can run the second.

- DB-019 [cited]: **An AMH upgrade has three independent version surfaces, and prose authority is not a
  substitute for the value.** At the 3.0.0 → 4.1.0 upgrade, `amh.conf` correctly named 3.0.0 and
  `docs/STATE.md` agreed, but the constitution only said that `amh.conf` was authoritative; it did
  not record a numeric version despite the upstream upgrade contract requiring one. The scripts
  could therefore be replaced without leaving the constitution mechanically comparable on the
  next upgrade. The upgrade now updates all three: `AMH_VERSION`, an explicit constitution
  version line, and STATE's working record. The release-template key diff is a separate required
  check: it exposed the deliberately unset `LEDGER_ROW_CHAR_CAP`, which remains on the shipped
  script's 2000-byte default rather than pretending every declared key was locally configured.
  **Correction pointer (DB-022):** the same upgrade commit also removed three `[cited]` markers
  this row does not mention; the version pair is now anchored in `scripts/guards/doc-facts.sh`.

- DB-020: **The owner set `LEDGER_ROW_CHAR_CAP=750`, superseding DB-019's use of the shipped
  2000-byte default.** The smaller bound is deliberate: a ledger row should preserve one durable
  lesson, not its debugging narrative. The ladder guard holds the rule for new rows; historical
  committed rows remain exempt.

- DB-021 [cited]: **Release preflight now positively identifies artifact-producing paths instead
  of treating every unknown file as app code.** The exclusion-based classifier made the AMH-only
  `amh.conf` change demand a version bump from released code 20 to 21. The workflow now enables
  release preparation for explicit artifact surfaces, skips known maintenance surfaces, and fails
  closed on unknown paths. Rename folding is disabled so both endpoints are classified. Prose only:
  if the build later consumes a known non-shipping path, reclassify it in the introducing PR.
  `[cited]`: `.github/workflows/release-preflight.yml`.

- DB-022 [cited]: **When a guard stops seeing a true fact, change the fact's spelling, never the
  record.** AMH 4.0.0 matches citations with `grep -w`, so the bare-suffixed form 25 comments
  used for a lettered sub-item stopped resolving. Three of the ten parent rows were cited ONLY
  that way, went stale, and upgrade commit `3949383` dropped their `[cited]` markers — deleting
  the warning the marker exists to give, while code still depended on the rows. The other seven
  survived only because a bare cite sat elsewhere. Sub-items are now `D-042(c)`, matched as a
  whole word. `doc-facts.sh` fails the suffixed form, which no shipped rung can see. Supersedes
  that commit's removal; DB-019 does not record it.

- DB-023: **A repo-local guard's exit code became an interface, and the safe default is still to
  fail closed.** AMH 4.2.0 gives `scripts/guards/*.sh` three verdicts: 0 passes, 2 with merged
  output starting `WARN ` warns without turning the ladder red, anything else fails. The marker
  is required because bash exits 2 on a syntax error and `grep`/`diff` on trouble, so an unmarked
  2 stays a failure rather than a downgraded opinion. Reclassification is mechanical —
  exit code plus prefix, never intent — so the upgrade check is `grep -rn 'exit 2' scripts/guards/`;
  ours matched nothing and no verdict moved. All four stay fail-closed: the warn tier is for a rule
  with unenumerated legitimate exceptions, and none of ours has any.

- DB-024: **An explicitly set `amh.conf` key is what turns an upstream default change into a
  no-op.** AMH 5.0.0 dropped the shipped `LEDGER_ROW_CHAR_CAP` default 2000 → 800 and called it a
  MAJOR; this repo absorbed it without an edit because DB-020 had already set 750 explicitly.
  Upgrade cost lands on keys left UNSET: `ladder.sh` assigns its defaults first and sources
  `amh.conf` after, so an omitted key silently tracks upstream. The 5.1.0 corollary: prose
  restating a configured number is a second, unchecked copy — nothing compares preamble text to
  `amh.conf`. The STATE and live-ledger preambles now name the key and let the ladder print the
  live value, removing the lockstep obligation instead of restating it.

- DB-025 [cited]: **Deleting a lockstep obligation is only safe once the copies are actually gone.** The
  5.1.0 prose change removed "keep in lockstep" from `amh.conf` and the ledger header and asserted
  no prose copy of `LEDGER_LINE_CAP` remained — while `1000` still stood twice in the RUNBOOK and
  in HARNESS_LOCAL. Nothing scans docs, so this traded a tripwire for a false all-clear: the next
  session to move the cap would be told there was nothing else to update. De-numbering the copies
  is what makes the claim true; where one copy must survive (HARNESS_LOCAL records what we set
  versus stock), name it as the surviving one rather than denying it exists.

- DB-026: **The STATE landing check fires on the CROSSING, not on the size of the edit.** Any edit
  taking the file from above `STATE_WARN_KB` to at or below it must reach `STATE_COMPRESS_TO_KB`
  — a five-byte typo fix at 14340 bytes hard-fails, and `STATE_EDIT_DELTA_BYTES` does not apply
  because that branch is only reached while still above the cap. The escape is to fold more, never
  to pad the file back up. Inverse trap: `ladder.sh` gates the whole check on the file at HEAD
  exceeding the cap, so a pass that STARTS below it is never landing-checked and can stop short in
  silence — as this upgrade's first pass did, at 9438 bytes against a 9216-byte floor.

- DB-027: **A completeness claim about a guard is a drift class; scope it to named functions.**
  AMH 5.2.0 closed the STATE preamble's list of machine-checked properties: a list that stops
  without saying it is complete leaves every prose rule after it reading in the same enforced
  voice — the shape of DB-026, where a sub-cap pass stopped short and nothing said so. Ours also
  omitted a check it always had (repeated `##` headings). The closure is a claim about
  `guard_state_size` and `guard_state_structure`, not a timeless "and nothing else": the script
  upgrades independently of a seed we own forever and nothing compares the two, so the sentence
  goes stale the first time upstream adds a rung. Named functions make that findable.

- DB-028 [cited]: **A convention with no mechanical layer drifts until it inverts its own rule.**
  `AGENTS.md` put durable prose in the `.md` tier with a `D-NNN` pointer in the code, and nothing
  checked it: the tree reached 7620 comment lines against 40651 of Kotlin (18.7%), much of it
  re-telling a ledger row verbatim — two copies of one lesson, the code copy the one nobody
  updates. Conventions had by then decayed to "match its comment density", instructing each
  session to reproduce the bloat it found. Fixed by `comment-budget.sh`: a 12-line cap on any
  contiguous comment block plus a per-module line budget, failing closed. The cap is the
  load-bearing half — narrative does not fit in 12 lines, so it must go to the `.md`.

- DB-029 [cited]: **A guard that counts a population protects no member of it.** `comment-budget.sh`
  floored `// Tasker` provenance at a tree-wide `grep -c` of 68 and claimed that defended the 33
  files carrying markers. It defended none: delete a marker from one algorithm, add one anywhere
  else, and the total is unchanged — the shape of a maintenance change that splits a ported path.
  A count also cannot name what left. Fixed by making the unit a RECORD
  keyed on file plus the Tasker source coordinates cited. Pin the load-bearing part and no more:
  keying on marker TEXT would have gone red on 22 markers this branch legitimately reworded, and a
  rule firing on every honest edit is regenerated by reflex until it means nothing.

- DB-030: **A ceiling with no headroom makes deletion the only repair.** The comment budgets were
  set to the measured tree, leaving `:platform` 9 lines — so a new adapter with ten lines of honest
  KDoc could not land without deleting unrelated documentation, and the guard would drive out what
  it wanted kept. Worse, the failure text said the fix was "NOT to raise the number" while the
  guard's header and `HARNESS_LOCAL.md` both called that the intended reviewable adjustment. A
  diagnostic that forbids what the documentation permits teaches the reader one of the two is lying
  and they stop checking either. **A guard's remedy text is legislation, and drifts like any other
  prose copy.**

- DB-031: **A record format is an interface, and whitespace is what it loses.** The comment scanner
  emitted `COUNT <file> <n> <m>` and every consumer read `$2` as the path and `$3` as a number, so
  one tracked `Parser Fixtures.kt` would shift every field — the module sum scoring the file as
  zero, the block diagnostic printing a filename fragment where the line number belonged. The
  file-list handoff was already whitespace-safe; the guard's own output protocol threw it away.
  Fixed by putting the numeric fields first and the **path last**, so each consumer takes a fixed
  count of leading fields and treats the remainder as the path.

- DB-032: **A floor keyed more tightly than its own contract fires on honest edits, and its escape
  hatch then erases it.** The provenance manifest keyed each record on a marker's whole
  coordinate SET with multiplicity, while the guard, `AGENTS.md` and its failure text all promised
  "only DROPPING a reference fails". Two edits that drop nothing failed: enriching a marker
  (`task543` → `task543 act7` destroys the key) and merging two markers citing the same
  coordinates — both edits this guard's own cap and budget push you toward. The documented remedy,
  regenerate, re-baselines all records at once and ratifies whatever else went missing. Fix: one
  record per (file, coordinate); key a floor at the granularity its prose promises.

- DB-033: **An input a guard cannot parse must fail, not be skipped — including its own constant.**
  The manifest check dropped any line not matching three tab-separated fields, so the cheapest
  bypass in the guard was a whitespace-only diff: delete a marker, convert that one manifest line's
  tabs to spaces, and it printed "all N record(s) intact" and exited 0 with N quietly one lower.
  Reachable by accident (any heredoc re-indent) and invisible to the fixtures, since a manifest
  parsing to zero records satisfies every case vacuously. The guard already applied the opposite
  doctrine to tracked files and simply did not apply it to its own data.

- DB-034 [cited]: **A read method with no caller is a claim the UI is quietly making instead.**
  Privileged Display rendered all seven toggles from the stored profile while the matching
  `SecureDisplayController.read*` methods sat unused, so a Night Light flipped from the system tile
  left the screen asserting the opposite — and the only-on-change diff never corrected it, its
  desired state having never moved. Tasker's `_ShowPrivilegedScene` re-reads every key on open;
  restored here, screen-only. Two fields stay app-owned: the circadian flag has no Android
  counterpart, and while it is on the ticker owns the temperature key, so a read-back would freeze
  one ramp sample as static. `[cited]`: `AabSettings.withDeviceSnapshot`.

- DB-035 [cited]: **A gate downstream of the work it authorises is not a gate.** Four `ControlReceiver`
  drops now say why at debug level 8 — the gate-off case above all, the first-run mistake. Glue review
  caught the level being read INSIDE the sink, after `flashDrop` had already read the settings store
  and built a sink per rejected broadcast, while the comment claimed the default config was untouched;
  the check moved ahead of the work. It also caught `LOAD_PROFILE`'s caller-chosen name reaching the
  system-wide overlay unbounded — now 40 chars, control/bidi stripped. Unknown actions and in-flight
  rejections stay silent: both precede DA-043's admission gate. `[cited]`: `ControlReceiver.flashDrop`.

- DB-036: **Awaiting one async projection proves nothing about a second derived from it.**
  `DraftSettingsViewModelTest` polled the DataStore until Apply's clamped value landed, then asserted
  `dirty` was false — but `dirty` compares the draft against the VM's own `committed` StateFlow,
  which its collector updates strictly after the store. The store reaching 65 says nothing about the
  collector having seen it, so the assertion raced and failed on ~2 of 3 runs under container load
  while passing on a warm machine. Discovered as a red ladder on an unrelated unit; it was never
  this session's change. Fix: poll the VM's own state before asserting on it, not the store's.

- DB-037 [cited]: **A confirmation emitted before the work it confirms can outlive it.** Panic's
  S.O.S. sat in the two gesture collectors, so the control intent and notification Reset recovered
  the device in silence. Sharing it via `panicAndStop` was the easy half; glue review found the rest.
  Vibrating FIRST (Tasker A6 order) let a sibling DISABLE cancel the coroutine mid-`emergencyStop`,
  leaving the user buzzed but never restored — it now follows the restore. The counter incremented
  before the vibrator null-check, so deleting the `vibrate()` call kept the test green. And nothing
  guarded re-entry: a double-tapped Reset ran the recovery twice. `[cited]`: `panicInFlight`.

- DB-038 [cited]: **A pin is a snapshot; without a refresh path it decays into a claim nobody rechecks.**
  Scorecard (v5.5.0, local) scored Pinned-Dependencies 0 — every action floated on a major tag, so a
  moved tag silently changes what runs. All 39 call sites now pin the commit each `@vN` already
  resolved to, annotated with its semver: immutability without an upgrade. The companion half makes
  it survive — github-actions updates are on in Dependabot, amending D-135's scope, not reversing
  it: no-speculative-bumps reasoned about gradle constraints; an action SHA is a different object.
  Also Token-Permissions 0: top-level `contents: write` moved to job scope.
  `[cited]`: `.github/dependabot.yml`, `build.yml` Node-24 header.

- DB-039 [cited]: **A guard the guarded action invalidates fires exactly once.** The Privileged
  Display read-back (DB-034) merged only while the draft matched the stored profile — but the merge
  writes device values INTO the draft, breaking its own precondition and refusing every later
  snapshot. The screen tracked the device once per entry, then froze: the staleness DB-034 existed
  to end, found by the owner. `dirty` conflated "the user has uncommitted edits" with "the draft
  differs from the profile"; only the first may block a re-merge, so the policy now compares against
  the draft it last produced. The gate had no test at any level — the suites covered the snapshot
  and the merge on either side. `[cited]`: `readBackDraft`.

- DB-040 [cited]: **Fixing a state machine from the outside gets it wrong twice.** DB-039 moved the
  read-back's gate off `dirty` but left three holes review found: whole-object equality read the
  collector's background writes of `serviceEnabled`/`debugLevel` as user edits and froze tracking;
  keying the effect on the snapshot alone never re-ran when the gate RE-opened, so Discard left the
  screen stale as before; and the pre-seed draft is `AabSettings()` with `committed` defaulting to
  it too, so the gate stood open on empty state and a merge could overwrite the profile. Fixed by
  scoping equality to the owned fields, making read-and-write one `update`, and refusing before the
  seed. `[cited]`: `mergeDeviceReadBack`.

- DB-041 [cited]: **A backing Android Settings key does not establish feature support.** A real
  black-screen failure followed a Night Display write on an OEM whose framework reports it
  unavailable. The controller now fails closed on `config_nightDisplayAvailable` and
  `config_dozeAlwaysOnDisplayAvailable`; unsupported writes are harmless no-ops across profiles,
  circadian ticks, Apply and panic, and UI controls hide. Force SDR is disabled: AOSP
  `DisplayManagerService` updates in-memory state/logical displays inside its binder methods but
  does not observe these Global rows, so direct Settings writes cannot establish a live effect.

- DB-042 [cited]: **A safe read default can still erase an unsupported hidden field.** Capability-
  gated reads returned `false` for Night Light/AOD; device read-back then copied those defaults into
  the draft. Opening Privileged Display on an unsupported device and applying an unrelated visible
  edit silently cleared the profile values. Snapshot booleans are now nullable capability sentinels,
  so read-back preserves unavailable fields. The same review also restored authorization-before-
  capability ordering: every setter rejects below ELEVATED before an unsupported no-op/failure.

- DB-043 [cited]: **A component-backed capability is the conjunction, not its headline flag.**
  DB-041 correctly requires framework capability gates but overclaimed that the reported failure's
  OEM flag was known; only the direct Night Display write and failure were observed. Night Light
  follows `config_nightDisplayAvailable`. AOD additionally requires a non-empty
  `config_dozeComponent`, matching AOSP ambient-display availability while deliberately ignoring
  its debug-property escape hatch. Missing/unreadable resources fail closed; pure lookup tests pin
  the exact names and the AOD truth table.
