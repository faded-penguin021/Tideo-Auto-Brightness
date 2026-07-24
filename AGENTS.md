# Agent instructions

All instructions for working in this repository live in **[`CLAUDE.md`](CLAUDE.md)** — it is
the constitution for **any** coding agent, not only Claude Code (the filename is historical:
dozens of docs and an append-only ledger cite it, so it is not renamed). **Read it in full
before doing anything else**, then follow its "Maintenance protocol (every session)".

If your harness has no session-start hook wired, run `scripts/session-start.sh` yourself at
the start of the session. Adapter wiring requirements: CLAUDE.md § "Agent harness".
