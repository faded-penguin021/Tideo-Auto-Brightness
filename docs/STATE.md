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

1. DB-041…DB-043's unavailable-feature boundary is still unverified (B1 BLOCKED twice): the owner's
   device reports Night Light/AOD available, so no pass yet could exercise the hidden/no-write case.
   Needs hardware that reports them unavailable.
2. Device location fix fails a few times before succeeding while geo-IP resolves instantly (owner,
   out of script). The banner half of that report was a real defect and is fixed (DB-051). Triage of
   the retry half found no defect in `AndroidLocationReader.activeFix`: both enabled providers are
   registered together, every completion path releases them (now pinned by test), and it reports
   Unavailable only when no fresh fix lands within 20 s AND there is no last-known fix at all — a
   state that self-heals the moment one lands, which is the shape the owner described. Two candidate
   mechanisms remain, and the Open question below discriminates them.

Open questions and owed reviews:

- [2026-08-16] **When "Use current location" failed, which did you see?** (a) the coordinate fields
  filled in but Set did nothing — then those failures were DB-051 and are already fixed, and nothing
  is left here; or (b) the toast "Couldn't acquire a location" — then acquisition genuinely failed
  and the likely cause is the second candidate: `activeFix` only registers GPS and NETWORK, and
  gives up at once when a device reports both disabled (some OEM battery-saving location modes
  expose only the fused provider), where the `currentLocation()` path it replaced in D-122 still
  had a PASSIVE fallback. Recommendation: answer (a)/(b) before any change — adding a PASSIVE or
  fused fallback to the active path is a real coverage gap but unevidenced on this device, and
  D-122 chose the active path deliberately.

Incoming: 1.9.0-debug/vc21 round (commit `7970765`) = **11 PASS / 1 FAIL / 3 BLOCKED**. A1/A2 pass,
so DB-048 and DB-049 are device-confirmed; B2/B3 pass, closing the DB-044/DB-045 state checks the
queue had held (reboot/blank caveats not separately reported). FAIL is D4 (item 1); BLOCKED are
items 2-3. Earlier: 1.8.2-debug was 49 PASS / 5 FAIL / 2 BLOCKED / 3 SKIPPED; DB-011/012 fixed real
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
- 2026-06-23..08-10 — **v1.0.0 → v1.8.2 and AMH convergence (D-096…D-176,
  DA-001…DA-044, DB-001…DB-027).** Rebuild/release/glue gates, F-Droid, hardening, Tasker parity,
  security review, triage and AMH upgrades through 5.2.0.
