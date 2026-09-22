# Plan — Circadian + Night Light fix (AAB issue 15)

Owner-approved fix spec, executed in segments. **Provisional context, not permanent evidence** —
the durable record is DC-055/DC-056 and the STATE Changelog. Segment N1 deletes this file
(RUNBOOK playbook 5). No ledger row may cite this path.

## The two-repository split — read before acting

The **issue** is in the AAB repo, `faded-penguin021/AdvancedAutoBrightness`, issue **15**
(originally an FR, "[FR] Circadian scaling ONLY for Night Light"; the bug report is the last
comment in that thread, by the same author). The **code** is this repo. So every fix is a Tideo
commit against an AAB issue: cross-reference it as
`faded-penguin021/AdvancedAutoBrightness#15` — a bare `#15` resolves to the wrong repo.

Reported against v1.10.0. Reporter's environment: Pixel 8 Pro / GrapheneOS / Android 17.

## Segments

- [x] **N1 — F1 + F2, the reported bug.** Shipped as DC-055 (read-back) and DC-056 (anchor).
  JVM-verified only; the settings-row confirmation below is still **owed** and needs a debug build
  installed on a device (the owner offered one, 2026-09-21; nothing was installed).
- [ ] **N2 — F4: the AOSP Kelvin bounds are hardcoded and are per-panel config.**
- [ ] **N3 — F5: daytime activation.** BLOCKED on device evidence; do not ship a guess.
- [ ] **N4 — close-out.** Delete this file once N2 and N3 are settled and the reply question
  below is answered.

F3 needs no code at all (see below) and is not a segment.

## What N1 shipped, and the contracts it moved

`nightLightTemperature == null` means "device default: never write the key". Two paths broke it.

**F1 — the anchor was a constant.** `AppModule` resolved a null setpoint to
`NIGHT_LIGHT_DEFAULT_K` (2850), so a user sitting at 1500 K got a 2850 K night and
`readNightLightTemperature()` went uncalled. Now the device is read **once per ownership period**
(never per tick — after the first write the device returns the ramp's own sample and the anchor
would walk) and kept in a `display_prefs` preferences store via `NightLightAnchorStore`.

**F2 — the read-back adopted a ramp sample.** `withDeviceSnapshot` guarded the temperature only
while circadian was on, so the moment it went off the next read-back copied the ramp's last Kelvin
into the profile, and re-enabling circadian took that as the night anchor — ratcheting toward
4082 K per cycle. A null setpoint now never adopts a device number.

**Two ledger contracts were revised, both with append-only pointers added** (`Corrected by
DC-056`): D-154's null hand-off used to leave the last ramp value on the device, and D-155 wrote no
temperature on panic. Both were right while Tideo had no record of the displaced value; both now
restore it.

### Decisions already made — do not re-litigate

| Decision | Who, when |
| --- | --- |
| Anchor lives in its own `display_prefs` store, **not** an `AabSettings` field (it is a device fact; `AabSettings` is the per-profile record that is exported, Tasker-serialised and context-merged) | gpt-6-astra, HIGH confidence, 2026-09-21 |
| Persistence **awaited** under `applyMutex`, not fire-and-forget | same |
| **No migration** resets already-snowballed stored values | owner, 2026-09-21 (STATE Decided non-items) |
| **Panic restores** the displaced Kelvin, revising D-155 | owner, 2026-09-21 |
| Do **not** add a Night Light spread factor (F3) | fix spec; redundant with the setpoint |

### Known, disclosed, unverified

- Awaiting the store inside `stop()`'s `runBlocking` adds storage latency to `onDestroy`. No test
  pins it, nor a cancellation landing inside the anchor clear. Raised by both reviewers; accepted.
- On a device with no Night Light, entering circadian still acquires an anchor (2850) that writes
  nothing, because the capability gate is below this layer. Inert, not wrong.
- Device behaviour is unverified everywhere: see "What a device can and cannot show" below.

## N2 — F4: the Kelvin bounds (ready to start, self-contained)

`SecureDisplayController.kt` hardcodes `NIGHT_LIGHT_MIN_K = 2596`, `MAX_K = 4082`,
`DEFAULT_K = 2850`. AOSP's Night Light integration guide presents all three as device
configuration-overlay entries (`config_nightDisplayColorTemperatureMin` / `…Max` / `…Default`) and
tells manufacturers to customise them per panel. **The hardcoded values are AOSP's own documented
example, correctly copied out of a document that frames them as overridable.** So the defect is of
a clear kind — a documented extension point read as a constant — independently of how many devices
diverge.

Fallout to fix:

- `PrivilegedDisplayScreen.kt` clamps the slider to `2596f..4082f`, so a device floor below 2596
  cannot be represented. The reporter observes **686** as their floor.
- `AppModule` uses `NIGHT_LIGHT_MAX_K` as the ramp's day endpoint, wrong wherever the real max
  differs.
- `strings.xml` asserts "Standard Android range 2596–4082 K" as fact and hardcodes "4082 K" in the
  circadian help text. The numerals are not wrong about AOSP's example; "standard" claims a
  universality AOSP explicitly disclaims. Drive both from the device and give them format args.

**How:** add `frameworkInteger(name, identifier, read)` mirroring the existing `frameworkBoolean`
(`frameworkString` is the other precedent), extend `FrameworkDisplayCapabilities` with the three
values, and drive the slider range, `dayKelvin` and both strings from them. Keep the existing
constants as the fallback when a resource is absent — which is what they were always meant to be.
Inject the reader so a unit test can supply a diverging device; that is the gate, because F4 is
**not locally reproducible** (see below).

Separately: `setNightLightTemperature` clamps to `1_000..10_000` and `SettingsValidator` validates
the same band. Those are sanity rails, not AOSP bounds, and are fine — but a device floor of 686
sits below the 1000 rail, so a user there still cannot express their real setting. Decide whether
the rail should follow the device too; that is a question, not a finding.

### Where 686 comes from — open, and it sizes F4 rather than gating it

Three candidate sources, very different blast radii: a GrapheneOS change (most likely — and
GrapheneOS users are exactly this app's Shizuku/root-capable audience, so they are
over-represented among Tideo users), an AOSP change in Android 17 (now unlikely: AOSP still
documents 2596/2850/4082, and the owner's Android 16 device returns stock values), or a local
overlay the reporter installed (unevidenced; GrapheneOS does not support Magisk). The Magisk
"intense night light" module overrides the same key to 0 K, not 686, and cites 2596 as the Pixel
default — so 686 is not that module.

**Ask the reporter for** the output of these three, which is what distinguishes a real effective
resource value from a reading of the Settings intensity slider (a 0–100 % control, not Kelvin):

```sh
adb shell cmd overlay lookup android android:integer/config_nightDisplayColorTemperatureMin
adb shell cmd overlay lookup android android:integer/config_nightDisplayColorTemperatureMax
adb shell cmd overlay lookup android android:integer/config_nightDisplayColorTemperatureDefault
```

Add `--verbose` to see which overlay supplied a value. Fallbacks if that ever fails on a vendor
build: `aapt2 dump resources` on a pulled `framework-res.apk` (base values only — also enumerate
`/vendor/overlay`, `/product/overlay`, `/system_ext/overlay`), or `dumpsys color_display`. What does
**not** work: writing an out-of-range value and reading it back. `Settings.Secure.putInt` stores
whatever it is given; the clamping lives in the Settings UI and in ColorDisplayService's
application path, never in the stored value — that measures your own write.

## N3 — F5: daytime activation (BLOCKED, needs device evidence)

Reporter: Night Light activates during the daytime, only with circadian on. Two candidate
mechanisms, neither verified:

**(a) The enable-flag latch — leading candidate.** `DeviceDisplaySnapshot.kt` merges
`nightLightEnabled = snapshot.nightLight ?: nightLightEnabled` **unconditionally** — there is no
circadian guard on the enable flag, unlike the temperature on the very next line. So: Night Light
is genuinely on at night because Tideo drove it there → an ON_RESUME read-back writes
`nightLightEnabled = true` into the draft → Apply commits it → later, in daylight, any profile or
context change re-asserts `setNightLight(true)`. The user never toggled it; the read-back did. That
correlates with circadian being on precisely because that is the mode where Tideo itself drives
Night Light on at night, which matches the report.

**(b) The ticker writes while Night Light is off.** `tickLocked` checks
`nightLightCircadianEnabled` and the tier but not `settings.nightLightEnabled`, so Tideo pokes the
temperature key every 60 s even with Night Light off. On stock AOSP a temperature write does not
activate, so this is probably not sufficient alone — but it is a third party writing the key, and
on a modified ColorDisplayService it is an unknown. Observed on the owner's device 2026-09-21:
`night_display_activated = 0` while the key sat at 3118, which is consistent with (b) writing
harmlessly.

**If (a) confirms**, the fix is to guard the `nightLightEnabled` read-back the way DC-055 now
guards the temperature, so Tideo's own assertion cannot be re-read as user intent.

**Ask the reporter for** `settings get secure night_display_auto_mode`. If it is 1 (custom
schedule) or 2 (twilight), AOSP's own ColorDisplayService is also driving `night_display_activated`
and is a third party in the fight — `readNightLightAutoMode()` exists and is surfaced to the
screen, but the coordinator never consults it. Also ask whether `night_display_activated` flips
before or after a Tideo write: that separates (a) from (b) cleanly.

## F3 — no code, and the original FR is already satisfied

`DynamicScaleEngine` computes `scaleDynamic = 1 + scaleSpread/100 * modifier`, but the Night Light
path takes only `.modifier` and discards `scaleDynamic`, and
`NightLightTemperatureRamp.temperature()` has no spread parameter. **Do not present this as the
cause of the reported symptom:** at full night `progress = 0`, so `modifier = -1` exactly,
`dayFraction = 0`, and the ramp returns `nightKelvin` on the nose regardless of spread. The ramp
maths was always correct; the anchor fed into it was not. `NightLightTemperatureRampTest` and
`DynamicScaleEngine`'s tests keep that pinned.

**The FR is already implemented.** `scalingEnabled` (the "Circadian scaling" switch) gates
*brightness* scaling only; the Night Light ramp never consults it and is driven solely by
`nightLightCircadianEnabled`. So **Circadian scaling OFF + Night Light "Follow circadian scaling"
ON is exactly the requested behaviour**, with no code. Worth telling the reporter — and worth
asking whether the UI makes it discoverable, since they did not find it and instead set Scale
Spread to 100, which is maximum *brightness* scaling, the opposite of what they wanted. That is a
discoverability problem, not a code one.

**Decline the FR's second shape, a Night Light spread factor.** Spread exists for brightness
because brightness has no user-set night endpoint — the curve is the baseline and the modifier
perturbs it, so spread is its only amplitude control. Night Light already has an explicit night
endpoint (the setpoint) and a fixed day endpoint, so its amplitude is already `dayKelvin −
setpoint`. Adding spread `s` would yield `dayKelvin − (s/100)(dayKelvin − setpoint)` —
algebraically just a different setpoint, two controls for one degree of freedom, and ill-defined
besides, since brightness spread scales around a neutral of 1.0 and there is no natural "neutral
temperature". The only genuinely non-redundant missing knob would be a **day endpoint** (how weak
the filter becomes in daylight, currently hardcoded to `NIGHT_LIGHT_MAX_K`). Probably YAGNI; noted
so it is on record rather than rediscovered.

## Ruled out — do not re-litigate

The UTC seconds-of-day arithmetic looks like a timezone bug and is not one. `AppModule` and
`PipelineCycleRunner` both compute `(millis / 1000) % 86_400` in UTC, and `CircadianWindows` is
UTC-framed too, so the frames match. Checked because it is the obvious first suspect. Minor and
unrelated: the KDoc on `DynamicScaleInput.nowSecOfDay` says "seconds into the local day" while
every caller passes UTC — the comment is wrong, the code is right.

## What a device can and cannot show

The owner's device is a OnePlus 13 / OxygenOS (`CPH2653`), and **D-048/DC-053 mean its
`ColorDisplayService` does not observe `night_display_color_temperature`** — the key is not
ignored, it is simply not an input; temperature reaches the service only through
`ColorDisplayManager`. So the panel cannot show the ramp working, and F4 is not reproducible there
either: `cmd overlay lookup` returned exactly the hardcoded **2596 / 4082 / 2850** (re-confirmed
2026-09-21 on Android 16), with no overlay involved.

Therefore: **JVM tests are the gate; device checks are confirmation only.** What the device *can*
still confirm is that the right values are being written, which is enough to verify F1 and F2
behaviourally — correct anchor resolution and the absence of the snowball are both visible in the
settings row whether or not the panel reacts:

```sh
adb shell settings get secure night_display_color_temperature
adb shell dumpsys color_display | grep -iA3 'night display'
```

The `ColorDisplayManager`/Shizuku route was reopened by the owner on 2026-09-22 and landed as
DC-057, outside this plan's segments. Once its spike passes (STATE Owner queue), the panel on the
OnePlus should follow the ramp with Shizuku running, and device checks there become visual again.

## Session setup that cost time — reuse it

- **The ladder's Gradle rungs need the x86-64 JDK under qemu**, because `aapt2` is an x86-64
  native: `JAVA_HOME=/opt/java/temurin-21-x64 QEMU_LD_PREFIX=/usr/x86_64-linux-gnu`. The bare
  aarch64 JDK fails at `:app:processDebugResources` with "AAPT2 Daemon startup failed". Roughly
  2.5× slower; `.orch/LOCAL_LADDER.md` is the authority.
- **Always pass `--rerun-tasks`.** Gradle reports UP-TO-DATE across an architecture change it
  cannot see, and `stat` the XML in `app/build/test-results/` against the run before believing a
  number (both traps are in `.orch/LOCAL_LADDER.md`).
- **`adb` here is the Debian arm64 build** (`apt-get install adb`); the SDK's own `adb` is x86-64
  and cannot run. There is **no USB passthrough** (`/dev/bus/usb` does not exist), so use Android
  **Wireless debugging**: `adb pair <ip>:<pair-port> <code>` then `adb connect <ip>:5555`. mDNS
  does not cross the container bridge, so the phone must be addressed by IP.
- **`:app` is AT its comment budget** (2538/2538). Any new comment line fails
  `comment-budget.sh`, so N1's explanatory prose lives in DC-056 and source carries one-line
  `DC-0NN:` pointers only. If N2 needs real source documentation, raising `BUDGET_app` is allowed
  but is a **rule change** requiring the DA-005 rule-review protocol (`.orch/codex.sh rules`) —
  budget for it rather than discovering it at the end.

## The reply to the reporter — drafted, NOT sent

Standing rule: **no issue gets a reply unasked** (owner, DB-082). An Owner-queue item asks whether
to post this. Nothing has been posted.

What the reply owes them:

1. **Their direct question** ("is my understanding of 'Device default' correct?") — **yes for the
   static path, no for the circadian path**, and that divergence was the bug. Fixed.
2. Confirmed: the intensity being "only a fraction of its set value" at full night (F1 + F2), and
   the snowball, which they root-caused correctly.
3. Their premise that `Scale Spread = 100` should mean full night temperature is **wrong** (F3) —
   but it does not rescue the behaviour, and the maths should have been exact anyway.
4. The FR they originally opened is **already available**: Circadian scaling OFF + "Follow
   circadian scaling" ON.
5. They were **right** that the range should not be hardcoded, and **wrong** that 686–4082 is
   "stock AOSP" — AOSP documents 2596–4082 as a per-panel example. F4 is a real defect regardless.
6. Ask for the three `cmd overlay lookup` outputs and their `night_display_auto_mode`.

## Also worth deciding, not in scope here

AAB's last push was 2026-08-24 and the code has moved to Tideo, but AAB still carries the issue
tracker users are on — which is why a Tideo bug was filed against AAB. If that split is not
deliberate, decide where reports should land.
