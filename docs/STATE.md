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

Harness AMH 5.2.0 (DB-027); upstream manifest scripts are immutable. Shipped v1.8.2/vc20, F-Droid
reproducible-build verified (owner, 2026-08-13). Branch carries unreleased v1.9.0/vc21.
DB-060's crash is device-verified fixed (owner, 2026-08-17, no crash), so that hold is lifted. The
Contexts location round-trip that opened in its place is **triaged and fixed (DB-061)**: the owner
compared against the tagged release and saw the same failure, and `v1.8.2` carries the identical
code, so it is pre-existing and does **not** block the tag. Nothing now blocks it.
Parity checklist and parity gaps empty. Live ledger: `LEDGER_B.md`.

**Resuming cold?** The train itself is finished and green. Branch
`claude/pr-116-branch-train-review-c4lo6i`, PR **#117** open against `main`, describing the whole
train; #115 closed, #116 was intra-train. No agent work is half-done. Queue item 1 is fixed and
needs only the owner's device check (it never blocked the tag). **Start at item 2** — an owner
judgement on unreviewed legislation, and the last thing before the merge. Then squash-merge #117, publish
from the GitHub UI, and **delete `docs/rebuild/DEVICE_TEST_SCRIPT_1.9.0.md`** (ephemeral), folding
its results into the standing script. Two rule-file edits landed without their DA-005 review and are
flagged: the AGENTS.md sentence (DB-056) and `format-args.sh` (item 2). Do **not** re-open the
closed force-stop location investigation — read the Incoming line and DB-051…DB-060 first; item 1 is
a different screen and a different defect.

## Owner queue

> Protected by D-167. Test observable claims before restating them; preserve unresolved items.

1. **Contexts location round-trip — fixed (DB-061), awaiting device verification.** The lead held:
   one defect, DB-051's mechanism on the other screen, and all three observations came from it.
   `formatCoord`/`parseCoord` now live in `ui/components/Coordinates.kt` and are used by both
   screens; the save path is a unit-testable `locationTriggerOf`; the rules list names the circle
   instead of saying "near location". Pre-existing (v1.8.2 has the same three lines), so it never
   blocked the tag. **Owner: on device, create a location rule, save, reopen it — the toggle should
   still be on with the coordinates shown, and the list row should name them.**
2. **`format-args.sh` corrections are unreviewed legislation.** Three DA-005 rounds, each finding
   the last wrong: round 1 killed the asserted crash mechanism (single-arg `getString` does not
   format); round 2 died on an API 529 with no verdict; round 3 found the rewrite blind to
   `emptyArray()` spreads (`CircadianScreen.kt:409,419`) and to `flashDrop`, a second vararg
   resolver. All fixed and fixtured — but **those fixes had no review of their own.** Judge whether
   that matters before the train merges. **Same status, larger surface: `python-edit.sh` (DB-062)** —
   a new 228-line repo-local guard plus a second `PreToolUse` hook in `.claude/settings.json`,
   committed with the DA-005 warning showing and no fresh-context review run. Its own ladder rung
   passes (8 edit shapes matched, 10 legitimate uses passed, arming verified), but a rung the same
   change wrote is not a review of it. Third unreviewed rule edit this train, with AGENTS.md
   (DB-056); a reviewer can take all three in one pass.
3. **Provenance manifest: one record removed, one restored.** `ProfilesScreen.kt`'s went because its
   only `// Tasker` line was wrapped prose this change deleted. `MiscScreen.kt`'s was removed and
   then **restored** — its marker is still in the tree and states Tasker behaviour the port matches,
   the stated keep-criterion. 14 no-coordinate records (the earlier "17" was wrong; it was 15).
   Hand-edited, never regenerated from a mid-change tree (DB-032).
4. DB-041…DB-043's unavailable-feature boundary still unverified (B1 BLOCKED twice): the owner's
   device reports Night Light/AOD available. Needs hardware reporting them unavailable.

Open questions: none. Owed reviews: item 2.

Incoming: the force-stop location defect is **closed as no app defect** (owner, 2026-08-17) — three
stationary retries at 44/23/4 s were cold-GNSS warm-up, and that curve became DB-059. Device rounds
to date: 1.8.2-debug 49 PASS/5 FAIL/2 BLOCKED/3 SKIPPED; 1.9.0 `7970765` 11/1/3, whose FAIL and two
BLOCKED are owner-closed below; `fc35a6e` and `036ec77` produced DB-054…DB-058; `12b5a21` produced
DB-060, whose fix the owner then **device-verified on the Contexts screen (2026-08-17, no crash)** —
the regression that blocked the tag is closed. DB-051 device-confirmed 2026-08-16. Detail lives in
the rows, not here.

## Decided non-items

- Repo/process declines: root changelog, speculative dependency bumps, standalone drift audit,
  Gradle dependency verification, wider session-branch CI, the D-162/DA-021 triage sets. Action SHA
  pinning left this list when Dependabot supplied a refresh path (DB-038).
- Privileged Display declines: per-toggle scheduling, persisted seed without real reports, grayscale
  quick action, refresh-rate/OEM keys, manual Extra Dim (D-150–152).
- Panic re-firing after teardown (1.9.0 D4): owner-closed as the still-installed Tasker project's own
  prof769 gesture — a third armed listener of the D-128 class, which also explains the 1.8.2
  "double". Reopen only on a report from a device with no sibling armed.
- §11.39a C1/C2 (external Night Light tracked twice): wontfix — no quick-settings tile on the
  device, path covered by unit tests.
- Never repeat whole-device backup/restore verification: `bmgr restore` damaged unrelated apps. The
  sanitizer stays unit-tested; callback invocation is accepted unverified residual (DB-013).

## Changelog

Newest first; ledger rows are the durable detail.

- 2026-08-17 — **Inline-Python edits get a one-time advisory (DB-062).** Owner-requested: `Write`/
  `Edit` show a diff as the change happens, a `python3 - <<EOF` heredoc does not. New repo-local
  `scripts/guards/python-edit.sh`, wired as a second `PreToolUse` hook (the shipped command guard is
  integrity-hashed and cannot host a local rule); it blocks the first inline-Python file write of a
  session and passes the rest, like the shipped `.env` advisory. Ladder mode runs its matcher
  fixtures; 12 new cases in `local-guards.sh`.
- 2026-08-17 — **Contexts location rules round-trip (DB-061).** DB-051's fix went one screen deep:
  the rule editor wrote the field with the default locale and read it back dot-only, so a
  comma-decimal device parsed null and dropped the whole location trigger — the rule reopened with
  the toggle off. Pre-existing (`v1.8.2` identical), so it never blocked the tag. The format/parse
  pair moved to `ui/components/Coordinates.kt`; the save path became a unit-testable
  `locationTriggerOf`; the rules list now names the circle instead of "near location".
- 2026-08-17 — **Contexts "Use current location" crash fixed, and the class guarded (DB-060).**
  DB-057's new `%1$d` reached only the circadian caller, so the Contexts one toasted a formatted
  string with no arguments and `Toaster`'s VARARG `getString(resId, *formatArgs)` threw — the
  single-arg `getString` does not format at all. Seconds constant now shared by both callers. New
  fail-closed `scripts/guards/format-args.sh` fails the ladder when a formatted string reaches
  either vararg resolver with no arguments; single-line call sites, literal ids, default locale
  only — its header has the limits. Stale-comment pass alongside it.
- 2026-08-17 — **Location: force-stop defect closed, latency behind it fixed (DB-057…DB-059).**
  `runCatching` swallowed `CancellationException` so a cancelled fix reported as a failed one
  (DB-058); the button held out for GNSS accuracy sun times cannot use and now takes a last-known
  fix under an hour old (DB-059); Location off no longer spends the window, and the toast names the
  wait (DB-057).
- 2026-08-11..16 — **v1.9.0/vc21 train (DB-028…DB-051).** Privileged Display shows the device rather
  than the stored profile and cannot write what the framework reports unsupported (read-back with a
  re-merge gate, Night Light/AOD capability gates, Disable HDR retained as a stored preference).
  Panic confirms once at every entry point. Dropped control commands explain themselves at level 8.
  Repo tier: Kotlin prose moved to the `.md` tier behind a fail-closed comment budget, Actions
  SHA-pinned with a Dependabot refresh path, tokens scoped, AMH 4.1.0 → 5.2.0.
- 2026-06-23..08-10 — **v1.0.0 → v1.8.2 and AMH convergence (D-096…D-176, DA-001…DA-044,
  DB-001…DB-027).** Rebuild/release/glue gates, F-Droid, hardening, Tasker parity, security review,
  triage and AMH upgrades through 5.2.0.
