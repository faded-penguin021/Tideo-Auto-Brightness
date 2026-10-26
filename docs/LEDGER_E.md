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
- DE-002: **1.15.1's Night Light fix shipped as device-verified, but only the debug install
  was checked, and it did not take the release install's path, so the release still showed the
  wrong Kelvin first (owner, 2026-10-26).** DD-093 passed DD-090 on 1.15.1-debug vc31; that install
  had never learned the DC-057 verdict, so it made one Shizuku call on return, while the release
  install, holding the verdict, made two and hit DE-001's 4 s bind hang. Learned device facts
  (the DC-057 verdict, DD-077's calibration) live per install in `display_prefs`; a device check
  that depends on one first makes the debug install hold the same fact as the release install.
  Step 38b's DE-001 case does this with the app's own probe.
