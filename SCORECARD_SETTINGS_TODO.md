# Owner settings tasks from the Scorecard audit (DB-038)

**Temporary file.** It exists only until the owner has worked through it; delete it afterwards.
Everything here needs the GitHub web UI — the session that produced it had no admin API access
(`/branches/main/protection` → 403), so nothing below could be applied or observed from a session.

Scope rule used: a task is listed only if it is a **valid finding from this audit**. A check
scoring low is not by itself a finding, and a control that is already in place is not a task.
That leaves exactly one action.

---

## 1. Confirm private vulnerability reporting is enabled — ACTION NEEDED

**Settings → Code security → Private vulnerability reporting → Enable**

`SECURITY.md` tells reporters to use private reporting, and it now links straight at the form:

    https://github.com/faded-penguin021/Tideo-Auto-Brightness/security/advisories/new

If the toggle is off, that link 404s for anyone who follows it and the policy is promising a
channel that does not exist. That is the finding — a documented gap between what `SECURITY.md`
says and what the repository does — not a score item. Scorecard's Security-Policy check already
reads 10/10 on the file's contents alone.

While in the same panel: **Dependabot security updates** should be on, since `.github/dependabot.yml`
is explicitly configured as security-only for gradle (D-135) and that policy assumes the toggle is
live. This is a one-glance confirmation, not a change.

---

## 2. Branch protection on `main` — CLOSED, NO ACTION

Recorded here so the reasoning survives rather than looking like something that was missed.

Your externally authenticated Scorecard v5.5.0 run already established that `main` has deletion
disabled, force-push disabled, pull requests required, and a required status check. Those are the
mechanical, history-integrity protections, and they are the ones that matter regardless of team
size. **They are already correct — there is nothing to verify or change.**

Branch-Protection scores 3/10 despite that because the remaining points live in the
social/multi-maintainer tier: required approver counts, stale-review dismissal, CODEOWNERS review,
last-push approval, and up-to-date-branch enforcement. Those are deliberately **not** recommended
here — see below.

---

## Deliberately not recommended

Listed explicitly, because silence would read as an oversight.

- **Required PR approvals, stale-review dismissal, CODEOWNERS.** On a solo-maintained repository
  these resolve to either self-approval theater or a maintainer who cannot merge their own work.
  Raising Branch-Protection from 3/10 is not a reason to adopt a control whose entire mechanism is
  a second person. Revisit if this project ever gains a second regular committer.

- **Letting `github-actions[bot]` bypass branch protection on `main`.** `clean-dist.yml`'s own
  header offers this as the route to silent auto-clean of leftover `dist/` artifacts. Do not do it
  as a security measure: it would let a `contents: write` job push directly to `main`, weakening
  the exact property (nothing reaches `main` unreviewed) that this audit is trying to strengthen.
  The workflow's current behavior — fail loudly when the push is rejected, so the leftover is
  surfaced for manual removal — is the better default. If you ever want the silent path, that is a
  separate, deliberate tradeoff, not a Scorecard remediation.

- **Secret scanning / push protection.** Real controls, and enabling them is reasonable on its own
  merits. They are not listed as tasks because this audit produced no finding pointing at them;
  including them would be padding a security-adjacent checklist rather than reporting evidence.

---

## Not settings, but raised by the audit and left undone

- **SLSA release provenance** (Scorecard: Signed-Releases). The APK is signed, which is what Android
  verifies, but that key says nothing about *what built the artifact and from which source*.
  Attaching provenance would be a new attestation step on the existing release pipeline — new token
  permissions, a new published artifact, and a verification story for consumers and F-Droid. Worth
  doing as its own change with its own testing; deliberately not folded into this one.

- **Fuzzing** (Scorecard: 0/10). Deferred as disproportionate for this pass rather than dismissed:
  SAF profile import parses arbitrary user-chosen files and is a plausible target. The golden
  vectors pin known-correct behavior; they do not probe malformed input. Needs its own
  cost/benefit call before a harness is added.
