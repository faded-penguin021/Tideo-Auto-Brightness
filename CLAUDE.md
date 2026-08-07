# CLAUDE.md — pointer

**Read [`AGENTS.md`](AGENTS.md) in full.** It is the canonical constitution for every agent
working in this repository; this file only points at it and must never diverge from it.

`.claude/settings.json` wires the session bootstrap, the pre-execution command guard and the
deny rails. If you are running an agent whose harness has no session-start hook, run
`scripts/session-start.sh` yourself before anything else.

> Historical note: this file held the constitution until the AMH convergence (2026-08-03), so
> ledger rows and docs written before then cite `CLAUDE.md` by name — they mean `AGENTS.md`.
> Those citations are in an append-only registry and are corrected in place only where leaving
> them would mislead.
