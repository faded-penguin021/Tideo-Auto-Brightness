# DEVIATIONS & DISCOVERIES LEDGER B — permanent registry (DB-001…)

> **Append-only registry — NEVER archived, compressed, or truncated.** The continuation of
> `DEVIATIONS_LEDGER_A.md`, which closed at its 1000-line cap (D-153 mechanism, DA-001 line-based
> cap). Code comments and docs cite entries as bare `DB-0NN` and must always resolve here, so no
> entry may ever be deleted or summarized away. **Append new maintenance deviations as DB-001,
> DB-002, … at the bottom** — one continuous sequence, never restart numbering. Code + golden
> vectors are ground truth; if an entry conflicts with current code, trust the code and correct the
> entry (don't delete it). **Search before appending (DA-006):** grep the ledger files for the topic
> first — extend or cite an existing row rather than append a near-duplicate.
>
> **File cap & rollover.** THIS FILE holds at most **1000 lines** (`scripts/ladder.sh`
> `LEDGER_CAP_LINES` — keep the two in lockstep). The FINAL row may finish past the cap, but no row
> may ever START past it: when this file stands at more than 1000 lines, create
> **`DEVIATIONS_LEDGER_C.md`** with this same header discipline and start numbering at **DC-001**.
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
