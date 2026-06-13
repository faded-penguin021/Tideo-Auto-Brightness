# Gate 1 debug build

`tideo-auto-brightness-gate1-debug.apk` — debug-signed APK built from this branch
(`claude/cool-meitner-sv6u5x`) at the S9b commit. This is a throwaway test artifact for the
Gate 1 on-device check; it can be deleted from the repo once Gate 1 is recorded.

## Install (phone-only)

1. On the GitHub mobile app/site, open this branch (or PR #21) → `dist/` →
   `tideo-auto-brightness-gate1-debug.apk` → **Download raw file**.
2. Open the downloaded file; allow "install unknown apps" for your browser/files app if prompted.
3. Install. (It's debug-signed, so it won't conflict with any Play build.)

## Grant permissions (no onboarding UI yet — S11)

- **Notifications:** allow when prompted (or Settings → Apps → Tideo Auto Brightness → Notifications).
- **Modify system settings (WRITE_SETTINGS):** Settings → Apps → Special app access →
  *Modify system settings* → Tideo Auto Brightness → allow. (Required for the core loop.)
- **Optional super dimming (WRITE_SECURE_SETTINGS):** needs a one-time grant from a computer or
  Shizuku/root — skip if phone-only; the core loop works without it.

## What to verify (Gate 1)

1. Enable the service → notification appears with live lux/target.
2. Cover the light sensor → brightness animates **down** smoothly; uncover → animates **up**.
3. Drag the system brightness slider mid-run → auto **pauses** (notification says so);
   tap **Resume** → it resumes.
4. Screen off → on → brightness re-initialises.
5. Reboot → service self-starts (if it was enabled).
6. Notification **Pause / Resume / Reset / Disable** actions work.
7. (If WRITE_SECURE_SETTINGS granted) in darkness below the dimming threshold, extra dimming
   engages and disengages cleanly.

Record findings on the PR or send them back here — they go into `docs/rebuild/STATE.md` → "Gate findings".
