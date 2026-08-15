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
v1.9.0/vc21. Tests green before this session; parity checklist and parity gaps are empty. Live
ledger: `LEDGER_B.md`.

## Owner queue

> Protected by D-167. Test observable claims before restating them; preserve unresolved items.

1. Device-verify the Tasker-parity train: **§11.39a** (Privileged Display read-back, DB-034),
   **§13.44a** (control drop reasons, DB-035), and **§5.14a** (panic vibration, DB-037). No
   emulator/KVM is available locally.
2. Device-verify supported Night Light/AOD behavior and the unavailable-feature hiding/no-write
   safety boundary from DB-041…DB-043 and experimental Disable HDR behavior (DB-044/DB-045), including reboot/brief-blank caveats. No emulator/KVM locally.

Open questions and owed reviews: none.

Incoming: owner device pass on 1.8.2-debug was 49 PASS / 5 FAIL / 2 BLOCKED / 3 SKIPPED; DB-011/012
fixed real defects and DB-013 retired the damaging whole-device backup test. DB-012 still records
that `PrivilegeManager` instances do not share a process-wide cache. Owner confirmed Dependabot,
branch protection and secret-scanning settings in DA-006/DA-041.

## Decided non-items

- Repo/process declines remain: root changelog, speculative dependency bumps, standalone drift
  audit, Gradle dependency verification, wider session-branch CI and the D-162/DA-021 triage sets.
  Action SHA pinning left this list when Dependabot supplied a refresh path (DB-038).
- Privileged Display declines: per-toggle scheduling, persisted seed without real reports,
  grayscale quick action, refresh-rate/OEM keys and manual Extra Dim (D-150–152).
- Never repeat whole-device backup/restore verification: the previous `bmgr restore` damaged
  unrelated apps. The sanitizer stays unit-tested; callback invocation is accepted unverified
  residual (DB-013).

## Changelog

Newest first; ledger rows are the durable detail.

- 2026-08-15 — **Privileged Display capability safety + reviews (DB-041…DB-046).** Night Light and AOD now fail
  closed on their AOSP framework capability resources at the platform-controller boundary, so
  profile swaps, circadian ticks, direct Apply and panic cannot bypass the gate; unavailable UI is
  hidden. Review restored authorization-before-capability result semantics and made unavailable
  read-back fields nullable so hidden profile values survive unrelated Apply operations. External
  review added AOD's required ambient-display component gate, lookup truth-table tests, and corrected
  the evidence boundary: the failure after a direct write is known; the affected OEM flag is not.
  Owner retained Android-14+ **Disable HDR (experimental)** as a stored-preference control (not the
  Force-SDR service API), with reboot and brief display-blank caveats (DB-044). Review then made
  partial/malformed external HDR preferences unrepresentable rather than normalizing them during
  unrelated direct Apply (DB-045). CI then exposed a round-trip fixture that assumed absent HDR
  rows meant OFF; it now seeds canonical OFF explicitly (DB-046).
- 2026-08-13..14 — **Read-back, diagnostics, panic and supply-chain train (DB-034…DB-040).** Device
  read-back now survives repeat changes, Discard, rotation and concurrent collector updates;
  rejected controls explain themselves at debug level 8; every panic entry confirms once after
  recovery; action pins gained Dependabot refresh, scoped permissions and wrapper validation.
- 2026-08-11 — **Comment consolidation and enforcement (DB-028…DB-033).** Kotlin prose moved to
  docs; the local comment/provenance guard gained fail-closed, whitespace-safe fixtures through
  three review passes.
- 2026-06-23..08-10 — **v1.0.0 → v1.8.2 and AMH convergence (D-096…D-176,
  DA-001…DA-044, DB-001…DB-027).** Rebuild/release/glue gates, F-Droid, hardening, Tasker parity,
  security review, triage and AMH upgrades through 5.2.0.
