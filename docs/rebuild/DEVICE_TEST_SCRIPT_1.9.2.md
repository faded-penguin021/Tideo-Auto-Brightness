# DEVICE_TEST_SCRIPT_1.9.2 — the short pass for the unreleased vc23 train

**Ephemeral (RUNBOOK §6 / DB-010).** This covers only what v1.9.2 (versionCode 23) changed and is
still unverified on hardware — it is not a substitute for `DEVICE_TEST_SCRIPT.md`, which stays the
full regression sweep for a release. When 1.9.2 ships, fold anything with standing value into the
numbered sections of that file (append or extend; never renumber) and delete this file.

Build: `./gradlew :app:assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`
(package `com.tideo.autobrightness.debug`, version shows `1.9.2-debug`).

Everything else in the train is already owner-verified on 1.9.2-debug: the stay-awake Apply-path
fix (§11 32a, 2026-08-23) and the wake false-pause fix (§2 10a, 2026-08-24). Do not re-run those
unless something below fails.

Two checks, both ELEVATED (WRITE_SECURE_SETTINGS). Log any miss in `../STATE.md` → "Owner queue".

> **Both sections passed as written — owner, 2026-08-25, on 1.9.2-debug vc23.** Nothing here is
> outstanding. The file stays only because retirement is tied to the version shipping (RUNBOOK §6):
> at ship, section B is appended to `DEVICE_TEST_SCRIPT.md` §11 as a new step (section A is already
> §7 21a) and this file is deleted.

---

## A. Live date with a fixed location (DB-084) — §7 21a

This is the one the owner queue is waiting on. Circadian screen, dynamic scaling enabled.

1. Set a **fixed date and location** (Experiment element) so you are in the old two-of-three state.
2. Tap **Live date**, then **Set fixed** with the coordinates still filled in.
   **Expected:** the status line reads **"Fixed location: … (live date)"** — *not* "Fixed: \<a
   date\> @ …" — and the date button shows today marked "(live)".
3. Enter **Sydney** (`-33.87` / `151.21`) and **Set fixed** again.
   **Expected:** sunrise/sunset on the curve jump to Sydney's, and in northern-hemisphere summer the
   daylight window becomes the **SHORT** one. A long window means the date got pinned too — that is
   the DB-084 regression.
4. Confirm the other two combinations are still reachable: pin a date as well (step 21) → the
   date-and-location case; clear the coordinate fields → the date-only case; **Use live data**
   reverts everything.

## B. A custom stay-awake mask survives the service running (DB-077, commit `dfa0c8c`)

§11 32a already covers the Apply path. What is new in vc23 is the **coordinator** path: a profile
switch or a service stop now reads the device's stay-awake state first and skips the write when the
device is *already* on the side being asked for — so a mask Tideo cannot represent (7, 1, …) is left
alone instead of being broadened to 15.

The transition that has to be **OFF → ON while the device already holds a custom mask**. An ON → ON
swap writes nothing on any build, so it proves nothing; don't bother with one.

5. Prepare two saved profiles: **P-off** with stay-awake OFF, **P-on** with stay-awake ON.
6. Master switch on, **P-off** active. Now put the device in the state every pre-v1.9.0 upgrade is
   in, behind the app's back: `adb shell settings put global stay_on_while_plugged_in 7`
7. Switch to **P-on**, then read it back:
   `adb shell settings get global stay_on_while_plugged_in`
   **Expected: `7`.** The device was already staying awake, so nothing is written and the owner's
   charger set survives. **`15` here is the regression** — that is the pre-fix behaviour.
8. Control, so step 7 cannot pass vacuously: `… put global stay_on_while_plugged_in 0`, switch back
   to **P-off**, then to **P-on** again. **Expected: `15`** — a device that really is off still gets
   written, so the skip is conditional and not a dead branch.
9. Service stop. With **P-on** active, set `7` again, then **turn the master switch off** (baseline
   restore). **Expected:** whatever the baseline's stay-awake value is decides it — a stay-awake-ON
   baseline leaves **`7`** untouched, a stay-awake-OFF baseline writes **`0`**. Both are correct;
   what must not happen is `7` → `15`.

Untouched on purpose: the explicit **"Use Tideo's setting instead"** button (DB-078) and panic reset
still replace the mask outright. If a step shows an unexpected `15`, check those first — an Apply or
a panic, not the coordinator, may be the writer.
