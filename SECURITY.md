# Security Policy

## Supported versions

Only the **latest release** (the newest `v*` tag, which is also the F-Droid version) receives
fixes. There are no maintenance branches; a security fix ships as a new patch release.

## Reporting a vulnerability

Use GitHub's **private vulnerability reporting**:

> **[Report a vulnerability privately →](https://github.com/faded-penguin021/Tideo-Auto-Brightness/security/advisories/new)**

That is the *Security → Advisories → Report a vulnerability* form on this repository; the link goes
straight to it. Please do not open a public issue for anything exploitable.

This is a solo-maintained app; reports are handled best-effort, normally within a week.

## Scope notes (by design, not vulnerabilities)

The assumptions, trust boundaries, attacker classes, and release-blocking invariants used for
implementation audits are defined in
[`docs/rebuild/SECURITY_AUDIT_MODEL.md`](docs/rebuild/SECURITY_AUDIT_MODEL.md). Read that model
before assessing individual code paths; the controls that enforce it, the evidence for each, and the
residual risks are in [`docs/rebuild/SECURITY_REVIEW.md`](docs/rebuild/SECURITY_REVIEW.md).

- The app writes `Settings.System.SCREEN_BRIGHTNESS`/`SCREEN_BRIGHTNESS_MODE` under the
  user-granted `WRITE_SETTINGS`, and — only after an explicit one-time ADB/Shizuku/root
  `pm grant` — `Settings.Secure` dimming (`WRITE_SECURE_SETTINGS`) and `dumpsys wifi` SSID
  reads (`DUMP`).
- The app makes **no network calls** except the optional, default-OFF geo-IP location fallback
  (HTTPS `ipwho.is`); cleartext traffic is disabled app-wide via network-security-config.
- The Shizuku user service (`ShizukuUserService.exec`) runs shell commands in Shizuku's
  privileged process, but its binder is handed only to this app by the Shizuku manager (which
  gates it on the user's per-app Shizuku authorization); the app itself sends only fixed
  command strings (`cmd wifi status`, `pm grant …`). It is not an IPC surface for other apps.
- Profile import accepts arbitrary user-chosen files by design (SAF); all parsed values are
  clamped to their valid ranges (including non-finite numbers) before persisting, and file
  names are sanitized to `[a-zA-Z0-9._-]` for app-private storage.
- The `dumpsys wifi` SSID extraction anchors on the first `SSID:` token, so a network literally
  named "SSID" mis-parses — a Tasker-parity quirk, shipped knowingly (see D-130).
