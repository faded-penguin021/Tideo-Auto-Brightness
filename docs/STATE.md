# STATE — project state & session memory

> **Length guard (DA-004 hysteresis — read before editing).** The thresholds are
> `STATE_WARN_KB`, `STATE_COMPRESS_TO_KB` and `STATE_HARD_KB` in `amh.conf`, named here and
> deliberately **not** restated as numbers: nothing checks this prose against the config, so a
> copied number drifts silently the first time a threshold moves. `scripts/ladder.sh` names the
> soft and hard caps whenever it reports the size, and the floor whenever it warns, fails, or
> confirms a completed landing. Grow freely to the soft cap; no
> trimming below it. When the guard warns, run ONE deep pass to the floor — never trim to just
> under the soft cap, because a micro-trim re-arms the warning a session later and the wide band
> IS the debounce. The floor is a **ceiling, not a target**: if the pass lands short, fold MORE
> completed stages rather than micro-trimming toward it. Fail above the hard cap. Compression
> means: collapse each completed stage into one Changelog line, fold changelog clusters, move
> durable gotchas to the append-only ledger, delete narrative prose.
>
> **Project**, **Current state**, **Decided non-items** and **Changelog** must always survive —
> those four are `STATE_REQUIRED_SECTIONS` and the guard FAILS on a missing or empty one.
> **Owner queue** is protected separately at WARN level: never delete it and never silently drop
> items during compression — they are the owner's to close, so compress their prose rather than
> dropping an open item. Separately, **no `##` heading may appear twice** — that check is asked of
> every level-2 heading in the document, not just the configured four, because a scripted edit
> that splices the file into itself duplicates whatever it happens to duplicate. Two copies of a
> section are two answers to the same question, and nothing else here can see one: the caps
> measure bytes and the landing check measures shrink, both of which a duplicate satisfies.
>
> The ladder also checks WHERE a pass lands, and the rule is sharper than it reads. **Any** edit
> that takes the file from above the soft cap to at or below it must reach the floor, however
> small the edit was: a five-byte typo fix at 14340 bytes crosses the cap and FAILS unless it
> also lands under the floor. The `STATE_EDIT_DELTA_BYTES` distinction between a compression
> pass and an ordinary edit applies only while the file is **still above** the cap. Two traps
> follow: never pad the file back up to escape that failure, and never trim a file that is
> already under the cap — a pass that starts below it is invisible to the landing check, so
> nothing will tell you it stopped short. The structure checks above still run at every size; it
> is only the size guard's landing half that goes quiet. That silence is the absence of a check,
> not a verdict that the edit was right — and **do not reach for a threshold to cover it.** It is
> the SHRINK that is measured, never the band, and a check that read any large shrink as a
> compression pass would fail a session for deleting one resolved Owner-queue item from a healthy
> file, leaving padding the file back as the only way to pass.
>
> **That list is the whole of what these two functions check** — this file existing at all, sizes,
> the required sections and their bodies, repeated headings, the Owner-queue heading, the landing
> check, plus a warning if `STATE_EDIT_DELTA_BYTES` is malformed. It is a claim about
> `guard_state_size` and `guard_state_structure` in `scripts/ladder.sh`, a file that upgrades
> independently of this one, so treat those two functions as the authority: if a later harness
> version adds a rung, this sentence is what goes stale, and nothing checks it against the script.
> Everything else here — what to fold, what to move to the ledger, whether to compress at all —
> is prose you are asked to keep, and no guard will catch you breaking it.

## Project

Native Kotlin/Compose rebuild of Tasker `Advanced_Auto_Brightness_V3.3`: `:domain` pure JVM,
`:platform` Android adapters, `:app` Compose/DataStore/FGS. BASIC runs core brightness; ELEVATED
adds super dimming and Privileged Display.

## Current state

Harness AMH 5.2.0 (DB-027); upstream manifest scripts are immutable. Shipped v1.8.2/vc20 and
F-Droid reproducible-build verified (owner, 2026-08-13). The branch carries unreleased
v1.9.0/vc21. **STILL NOT releasable, and PR #117 must not be merged**: the DB-060 crash is fixed in
code but is device-unverified, and the hold lifts on the owner's pass (queue item 1), not on a green
ladder. Parity checklist and parity gaps are empty. Live ledger: `LEDGER_B.md`.

## Owner queue

> Protected by D-167. Test observable claims before restating them; preserve unresolved items.

1. **Device-verify the DB-060 crash fix on the Contexts screen specifically** — Contexts rule →
   **Use current location** must now toast "Acquiring location — this can take up to 45 seconds…"
   and fill the fields instead of crashing. The circadian button passing proves nothing about it,
   which is exactly how this shipped. The owner's stack trace named the cause outright
   (`MissingFormatArgumentException` at `Toaster.kt:25`): DB-057's new `%1$d` reached only the
   circadian caller. The location-layer suspects the queue listed (`activeFix` PASSIVE, the 45 s
   budget, the new default interface methods) were all wrong and are retired.
   Still open on that screen, and NOT touched by this fix: `ContextsScreen` parses lat/lon with a
   bare `toDoubleOrNull()` around its geofence editor, so DB-051's comma-decimal defect is latent
   there. Worth a pass on a comma-decimal locale.
2. **`scripts/guards/format-args.sh` HAS NOT HAD A COMPLETED FRESH-CONTEXT REVIEW (DA-005).** The
   first review reviewed a DIFFERENT guard: it found the crash mechanism asserted in the header was
   wrong (`Resources.getString(int)` does not format; only the vararg overload does), so the guard
   was rewritten wholesale — resolver set narrowed to `toast`, XML parse flattened, `formatted="false"`
   honoured, scan moved to `git ls-files`. The second review, of that rewrite, **terminated on an
   API 529 without producing a verdict** — it is not a pass, and nothing here should be read as one.
   Owed before the train merges: one fresh-context review of `176f2d3`'s legislation, strongest
   tier, no self-review. Its known-unreviewed surface is the rewrite listed above plus the manifest
   deletions in item 3.
3. **Two `(no-coordinate)` provenance records were removed** (`ProfilesScreen.kt`, `MiscScreen.kt`)
   after an audit of all 17. Both stood for a wrapped prose line whose "Tasker" was incidental —
   `// Tasker configs), grouped with…` and `// Tasker-style "adjusted to N"…`, hyphenated adjective
   and mid-sentence continuation, no coordinate, no ported logic. The other 15 were kept: they name
   a Tasker entity, quote Tasker source, or state Tasker behaviour the port must match. Manifest
   edited by exactly those two lines, never regenerated from a mid-change tree (DB-032). Overrule
   either call if you disagree — this is the one part of the change nothing can falsify mechanically.
4. DB-041…DB-043's unavailable-feature boundary is still unverified (B1 BLOCKED twice): the owner's
   device reports Night Light/AOD available, so no pass yet could exercise the hidden/no-write case.
   Needs hardware that reports them unavailable.

Open questions: none. **Owed reviews: one** — queue item 2, the DA-005 review of `176f2d3`.

Incoming: **the force-stop location defect is closed as no app defect** (owner, 2026-08-17): three
stationary retries without a force stop returned 44 s, 23 s, 4 s — cold-GNSS warm-up, so the app-op
hypothesis is retired unused and the restart correlation was coincidence both times it appeared.
The owner's reading of that curve is what became DB-059: accuracy this feature cannot spend was
costing the wait. Earlier: retest round on `fc35a6e` — A2/B1/B2/B3/C pass. A3/A4 found DB-054, the acquired location
never reaching the D-103 cache. A1's "only works after a force stop" resolved to DB-055 once the
owner measured it: the indicator lights and the fix lands at ~15 s against a 20 s budget, so the
restart correlation was coincidence and the timeout was simply too short for GPS-only hardware.
Both fixed; neither is device-reverified yet. Earlier
**DB-051 is device-confirmed** (owner, 2026-08-16): the coordinate fields filled with
comma-decimal values and Set did nothing, which is the reported "location fix fails a few times
before it works" in full — acquisition had succeeded every time; only the write was refused. That
retires the `activeFix` PASSIVE/fused coverage gap as unevidenced (recorded in DB-051, not fixed),
and explains "geo-IP works instantly" as the 20 s on-device window running before the IP fallback,
which is D-122's deliberate order. Earlier: 1.9.0-debug/vc21 round (commit `7970765`) = **11 PASS / 1 FAIL / 3 BLOCKED**. A1/A2 pass,
so DB-048 and DB-049 are device-confirmed; B2/B3 pass, closing the DB-044/DB-045 state checks the
queue had held (reboot/blank caveats not separately reported). Its one FAIL (D4) and two of its
three BLOCKED are now owner-closed in Decided non-items; the third is the only open queue item.
Earlier: 1.8.2-debug was 49 PASS / 5 FAIL / 2 BLOCKED / 3 SKIPPED; DB-011/012 fixed real
defects, DB-013 retired the damaging whole-device backup test, DB-012 records that
`PrivilegeManager` instances share no process-wide cache. Owner confirmed Dependabot, branch
protection and secret-scanning in DA-006/DA-041.

## Decided non-items

- Repo/process declines remain: root changelog, speculative dependency bumps, standalone drift
  audit, Gradle dependency verification, wider session-branch CI and the D-162/DA-021 triage sets.
  Action SHA pinning left this list when Dependabot supplied a refresh path (DB-038).
- Privileged Display declines: per-toggle scheduling, persisted seed without real reports,
  grayscale quick action, refresh-rate/OEM keys and manual Extra Dim (D-150–152).
- Panic re-firing after teardown (1.9.0 round D4) is closed by owner decision: likely the Tasker
  Advanced_Auto_Brightness project, still installed and carrying its own prof769 panic gesture. That
  is a third armed listener of the same D-128 class the DB-050 triage identified, and it fits the
  1.8.2 "double" too. No code change; reopen only on a report from a device with no sibling armed.
- §11.39a C1/C2 (external Night Light change tracked twice) is wontfix by owner decision: the
  device exposes no Night Light quick-settings tile and the path is covered by unit tests.
- Never repeat whole-device backup/restore verification: the previous `bmgr restore` damaged
  unrelated apps. The sanitizer stays unit-tested; callback invocation is accepted unverified
  residual (DB-013).

## Changelog

Newest first; ledger rows are the durable detail.

- 2026-08-11..16 — **v1.9.0/vc21 train (DB-028…DB-051).** Privileged Display now shows the device
  instead of the stored profile and cannot write what the framework reports unsupported: device
  read-back with a re-merge gate that survives repeat changes, Discard and background writes;
  Night Light/AOD capability gates at the controller boundary with unavailable fields preserved,
  not erased; **Disable HDR (experimental)** retained as an owner-decided stored-preference control
  that preserves custom rows and reads an absent row as AOSP's default; Apply invalidates the
  read-back on both the direct and coordinator paths. Panic confirms on every entry point, once.
  Dropped control commands explain themselves at debug level 8, with caller text bounded and
  sanitized. Repo tier: Kotlin prose moved to the `.md` tier behind a new fail-closed comment
  budget, Actions pinned to SHAs with a Dependabot refresh path, workflow tokens scoped, wrapper
  validated, AMH 4.1.0 → 5.2.0. Pre-merge review added DB-048/DB-049; the device round added the
  D4 triage (DB-050) and the locale-formatted coordinate fix (DB-051).
- 2026-08-17 — **The Contexts "Use current location" crash, and the guard that would have caught it
  (DB-060).** The owner's stack trace resolved it in one hop: DB-057 added `%1$d` to
  `toast_acquiring_location` and updated only the circadian caller, so the Contexts one resolved a
  formatted string with no arguments and `Resources.getString` threw. The seconds constant is now
  shared by both callers, and a new fail-closed `scripts/guards/format-args.sh` fails the ladder on
  any formatted string resolved bare, tree-wide.
- 2026-08-17 — **Location: the force-stop defect closed, and the latency behind it fixed
  (DB-057…DB-059).** The investigation could not name a mechanism and refused to guess; the owner's
  three stationary retries (44 s, 23 s, 4 s) then closed it as cold-GNSS warm-up with no app defect.
  Two real defects fell out along the way: `runCatching { }.getOrNull()` swallowed
  `CancellationException`, so leaving the screen mid-fix toasted the same "Couldn't acquire a
  location" as a genuine timeout (DB-058); and the button held out for GNSS accuracy that sun times
  cannot use, so it now takes any last-known fix under an hour old and skips acquisition (DB-059).
  Location switched off no longer spends the window at all, and the toast names the wait (DB-057).
- 2026-06-23..08-10 — **v1.0.0 → v1.8.2 and AMH convergence (D-096…D-176,
  DA-001…DA-044, DB-001…DB-027).** Rebuild/release/glue gates, F-Droid, hardening, Tasker parity,
  security review, triage and AMH upgrades through 5.2.0.
