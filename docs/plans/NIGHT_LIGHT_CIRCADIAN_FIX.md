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

A second issue feeds N2 and N2b: **`faded-penguin021/Tideo-Auto-Brightness#142`** ("Full color
temperature range", 2026-10-06), a Tideo issue this time. Its reporter showed the
`color_display` binder drives Night Light well outside the AOSP range, and the owner's reply there
sets the direction recorded under N2b. Per that reply, feature requests belong in AAB, which gets
the feature first and is then ported here.

## Segments

- [x] **N1 — F1 + F2, the reported bug.** Shipped as DC-055 (read-back) and DC-056 (anchor).
  JVM-verified only; the settings-row confirmation below is still **owed** and needs a debug build
  installed on a device (the owner offered one, 2026-09-21; nothing was installed).
- [x] **N2 — F4: the AOSP Kelvin bounds are hardcoded and are per-panel config.** The slider
  follows the device's reported min/max/default (owner, #142, 2026-10-06). Shipped as DD-045,
  inside the 1000–10000 rails, which N2b step 1 moved to 686–7308 (DD-046); JVM-verified only.
- [ ] **N2b — extended range behind a toggle (#142).** 686–7308 K, off by default, per profile,
  shown only with Shizuku or root; the ramp starts from 7308 K while it is on. The AAB reference
  arrived and the owner answered every open question (2026-10-07); both are recorded under N2b
  below, which also holds its step list (steps 1–3 done, DD-046…DD-049).
- [x] **N3 — F5: daytime activation.** Owner, 2026-10-06: most likely the default-solar-times
  fallback that DD-038 fixed. No N3 code; unverified on the reporter's device.
- [ ] **N4 — close-out.** Delete this file once N2, N2b and N3 are settled. The reply question is
  closed: the owner answered the reporter personally (2026-09-23), and the draft below was not
  used.

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

Separately: `setNightLightTemperature` clamped to `1_000..10_000` and `SettingsValidator` validated
the same band. Those are sanity rails, not AOSP bounds. **Settled by the owner (#142,
2026-10-06):** N2 only makes the slider follow the device's reported range. Anything wider is N2b's
opt-in toggle. N2b step 1 has moved the rails to 686..7308, one band
(`SecureDisplayController.NIGHT_LIGHT_RAIL_K`) shared by the controller, `AabSettingsMapper` and
`SettingsValidator` (DD-046).

### Where 686 comes from — explained by the matrix maths; the lookup still sizes F4

**Owner, #142 (2026-10-06):** 686 K is where AOSP's Night Light matrix runs out of blue. #142
quotes the per-channel fit (`r = 1`; `g = -9.6235e-9·K² + 1.5305e-4·K + 0.3908`;
`b = -1.8936e-8·K² + 3.0241e-4·K - 0.1987`). Its roots, rechecked here: blue reaches 0 at
**686.6 K**, green reaches 0 at −2238 K (so `-2200` is close to pure red), and blue peaks at the
vertex 7985 K (≈1.009) and then falls, so the tint turns red again above it (#142's reporter saw
that at 12000 K). Blue first exceeds 1 at **7308.0 K** (green there is 0.995) and stays above 1
until 8662 K, so 7308 is the highest Kelvin at which every multiplier stays within 0–1. A floor at the blue zero is a principled choice for a blue-light filter, which is
why the owner now reads 686 as GrapheneOS's deliberate limit. The lookup below is still worth
asking for, because it shows whether the 686 is a real overlay value. The three candidate sources
were:

A GrapheneOS change (most likely — and
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
whatever it is given, so reading it back only measures your own write. Clamping happens
elsewhere, and #142 shows it is uneven. On the owner's device (2026-10-06) out-of-range writes
**visibly changed** the panel while `dumpsys color_display` reported a clamped value and
`settings get` reported the written one. So the dumpsys or service read is not proof of what the
panel shows.

## N2b — extended range behind a toggle (#142)

**Owner direction (#142 reply, 2026-10-06):** an opt-in toggle, labelled "Go beyond temperature
limits" or something close (the wording is not final), off by default. When it is on, the setpoint
slider spans **686–7308 K** instead of the device's reported range. Never go below 686 (blue is
already zero; going lower only strips green toward red). Never go above 7308 (owner, 2026-10-07:
above it blue exceeds 1, so every RGB multiplier stays in 0–1 only up to 7308). Both bounds are
derived under N2 above.

### Owner decisions (2026-10-07) — do not re-litigate

| Decision | Detail |
| --- | --- |
| **Port AAB's semantics, not invented ones** | The AAB reference is distilled below. Where Tideo departs from it, this table says so and why. |
| **With the toggle on, 7308 K is the ramp's day endpoint** | Night Light normally ramps from the device max (4082 on stock AOSP) down to the setpoint. Extended mode ramps from 7308 instead, so activation is gentler and slower. Day is never warmer than night, because every setpoint is ≤ 7308. With the toggle off, the day endpoint stays the device max (N2). AAB agrees: its circadian help names the active max. |
| **Extended range needs Shizuku or root** | ELEVATED (a `pm grant`) alone is not enough. AAB also offers ADB WiFi; Tideo has no such route, so it is not ported. |
| **Visibility: Shizuku live, root once per screen open** | Shizuku's state follows the existing `refresh()` (it can stop while the app runs). Root is probed once each time the Privileged Display screen opens, as AAB re-detects on every scene open; probing `su` on every resume would raise root-manager toasts. |
| **No route at apply time** | A profile with the toggle on, applied while neither Shizuku nor root is reachable: clamp the setpoint to the **device** range, write it through the normal key path, and show DC-057's existing "needs Shizuku" note. The toggle stays **visible while it is on**, so it can be switched off. (AAB writes nothing in this case; Tideo has a key path AAB lacks.) |
| **The toggle is per profile** | `AabSettings` field, exported, serialised for Tasker as `%AAB_ExtendedNightLight` and context-merged like the setpoint. |
| **Out-of-range setpoints are preserved** | A stored setpoint outside the active range is kept, never rewritten; it is clamped only when applied. Turning the toggle off, importing a profile, or loading one whose value the slider cannot show never changes the stored number. **This departs from AAB**, whose Apply clamps the slider and saves the clamped value. |
| **Extended default = the device's default** | AAB hardcodes 2850 in extended mode. Tideo keeps `nightLightRange.default` (2850 on stock), because "device default" is what an unset key means to the panel. Not objected to (2026-10-07). |
| **Shizuku stays at three runtime places** | Extended writes reuse DC-057's `NightDisplayServiceBridge`, so `doc-facts.sh` (which counts files naming `ShizukuShell`) stays at 3. AGENTS.md's third place is reworded instead: it is no longer "only after that build has been observed ignoring the key". A rule change under the DA-005 rule-review protocol. |
| **README points at a new `SHIZUKU_USAGE.md`** | README's "Three runtime uses of Shizuku" paragraph becomes exactly: "To find out more about the use of Shizuku in this project, please read SHIZUKU_USAGE.md." `SHIZUKU_USAGE.md` (repo root) holds that paragraph's current text with no preamble, updated for extended mode. `doc-facts.sh`'s failure message names README as a restatement site, so it must name the new file. |

Why 7308 makes activation gentle, computed from the #142 fit: at 7308 K, `g ≈ 0.995` and
`b ≈ 1.000`, so the matrix is almost the identity, and Night Light switching on at the start of the
ramp is visually close to nothing. At 4082 K the same fit gives `g ≈ 0.86, b ≈ 0.72`, which is the
visible step the stock ramp starts with. Stopping at 7308 rather than the 7985 blue peak means no
multiplier exceeds 1, so whether ColorDisplayService or the panel would clip a boost never arises.

### The AAB reference — distilled (owner-supplied, 2026-10-07)

Newer than the V3.3 export in `docs/rebuild/extraction/`, so this is the only copy here. Once an
AAB export containing it lands, re-cite by XML coordinates. Only what drives Tideo is kept.

**`_DetermineNightLightThresh`** sets `%AAB_TempMin` / `%AAB_TempMax` / `%AAB_TempDefault`:

| `%par1` | Values |
| --- | --- |
| `on` | 686 / 7308 / 2850, fixed |
| `off` or unset, with Root, Shizuku or ADB WiFi | `cmd overlay lookup android android:integer/config_nightDisplayColorTemperature{Min,Max,Default}` |
| `off` or unset, no route | 2596 / 4082 / 2850 |

The `off` row is what Tideo's N2 already does, without needing a route (`Resources.getInteger`).

**The scene (`AAB Privileged Scene`, WebView):**

- The Extended row sits in the Night Light card, under "Follow circadian scaling", and shows only
  when `%AAB_SecondaryPrivilege` is Shizuku, Root or ADB WiFi. `_ShowPrivilegedScene` A1 runs
  `_SecondaryPrivilegeDetection` before every show. Its info text: "Unlocks lower and higher
  temperature thresholds (down to 686 K and up to 7308 K) using privileged commands."
- The slider's bounds follow `%AAB_ExtendedNightLight` and `%AAB_TempMin/Max`. The range hint says
  "Extended range MIN–MAX K." or "Standard Android range MIN–MAX K."; the circadian help names
  MAX as the daylight endpoint.
- Apply with the toggle changed: set the variable, run `_DetermineNightLightThresh` (`on`/`off`),
  wait for it, re-clamp the slider, and only then build the `_ApplyPrivilegedDisplay` payload
  (`nl|temp|circadian|daltonizer|inversion|aod|charging|sdr`; the flag itself is not in it).
- Android-backed controls, the Extended toggle included, are disabled while the device state
  cannot be read. Tideo's equivalent is the ELEVATED tier gate.

**Profiles (`_ProfileManager`):** saved as `privileged_display.extended_night_light` (boolean);
on load the variable changes only when the key is present, so an older profile keeps the current
mode; a regenerated Default carries `false`. **Owner fix, 2026-10-07:** inside A19 (privileged
block loaded), AAB now re-runs `_DetermineNightLightThresh` with `on` when
`%AAB_ExtendedNightLight ~ true`, else `off`, before applying — previously a profile load left the
range stale until the next Apply on the scene. Tideo derives the range from the flag, so it needs
no equivalent. Tideo's AAB-JSON importer ignores the whole `privileged_display` block today; that
gap is outside N2b.

### AAB's write task — `_NightLightAPI` (owner-supplied, 2026-10-07)

```
A1  Stop                                     If %par12 ~R .*[A-Za-z].* & %par12 Set
A2  %temp = %par12                           If %par12 Set
A3  %temp = %AAB_TempMin                     If %temp < %AAB_TempMin
A4  %temp = %AAB_TempMax                     If %temp > %AAB_TempMax
A5  If %AAB_SecondaryPrivilege ~ Root
A6    Run Shell (root): service call color_display 9 i32 %temp   (10 s, continue after error)
A7    %return = true
A8  Else If %AAB_SecondaryPrivilege ~ Shizuku
A9    Run Shell (Shizuku): service call color_display 9 i32 %temp (continue after error)
A10   %return = true
A11 Else If %AAB_SecondaryPrivilege ~ ADB WiFi
A12   ADB Wifi: service call color_display 9 i32 %temp            (10 s)
A13   %return = true
A14 End If
A15 Return %return
```

- **The write is the binder**: transaction 9, `setNightDisplayColorTemperature`, regardless of any
  probe verdict. In Tideo that is DC-057's existing `ColorDisplayBinder` behind
  `NightDisplayServiceBridge` (do not add a second copy), taken directly while the toggle is on
  rather than only on a NOT_HONOURED build.
- **The clamp happens on write** (A3/A4) to the active mode's min/max, matching "preserve stored,
  clamp on apply".

### Steps — each ends ladder green → STATE line → commit → push

1. [x] **Rails.** 686..7308, one band (`SecureDisplayController.NIGHT_LIGHT_RAIL_K`) shared by the
   controller, `AabSettingsMapper` and `SettingsValidator` (DD-046).
2. [x] **Settings field.** `AabSettings.extendedNightLightEnabled` (default false);
   `%AAB_ExtendedNightLight` rule and `TaskerLegacyProfileSerializer` line beside its siblings;
   `SettingsDisplay` label; keep it with the other display fields wherever they are listed together
   (`DeviceDisplaySnapshot.displayFieldsEqual`, `SettingsViewModel.replaceAll`'s keep-current
   block); migration/round-trip tests (DD-047). **Fork decided under the grant:** a profile saved
   before the field reads it as off, unlike AAB's keep-current-when-absent, because Tideo profiles
   are complete snapshots and a tri-state flag would reach every consumer (DD-047).
3. [x] **Runtime** (DD-048; DD-049 holds its four grant-decided forks: writes-only clamping, a railed
   anchor restore, no route cache, and the read-back keeping a stored setpoint whose clamp the
   device holds — so step 4 needs no read-back change for it). The active range is the extended band when the toggle is on and a route
   exists, else the device range; clamp only on apply, in `DisplayTogglesCoordinator` and in
   `DisplayTogglesViewModel.applyNow`. `AppModule`'s `dayKelvin` is 7308 while extended. Extended
   writes go straight to the bridge and **skip `NightLightTemperatureRoute.observe()`**: if the
   service getter clamps the way #142's dumpsys did, an out-of-range write reads as a mismatch, and
   two of them latch NOT_HONOURED on a build that honours the key. The clamped read also feeds
   `readDeviceKelvin` and the DC-056 anchor; pin both with unit tests. Also from step 1's review:
   `releaseAnchorLocked` sets `deviceTempK` to the requested anchor, so an anchor above 7308 (a
   device that held, say, 8000 before the ramp took over) is tracked as 8000 while the controller
   wrote 7308 — track the clamped value. Glue-review protocol.
4. [ ] **UI.** Toggle row with the visibility above; the slider's range follows the **draft**
   toggle at once (AAB waits for Apply; Tideo's draft model makes that unnecessary). Compose's
   `Slider` coerces its displayed value to `valueRange`, so the draft must keep a stored
   out-of-range setpoint unless the user moves the thumb. New strings take format arguments.
5. [ ] **Collateral.** AGENTS.md wording, README → `SHIZUKU_USAGE.md`, `doc-facts.sh` message (rule
   review); `docs/rebuild/architecture/privilege_tiers.md` restates the Shizuku places too;
   ledger row; `changelogs/28.txt`; Chinese for the new strings via Owner-queue item 1;
   `DEVICE_TEST_SCRIPT.md` and `e2e/scenarios.toml` steps together (RUNBOOK playbook 5);
   `:app`'s comment budget is the limit noted under "Session setup".

**Standing grant (owner, 2026-10-07, while away):** the executing session decides any remaining
N2b fork itself, recording each choice and its reason here and in the step's ledger row, and every
glue or rule review for every N2b step is pre-approved to run as an adversarial fresh-context
subagent.

## N3 — F5: daytime activation (owner diagnosis: fixed by DD-038)

**Owner, 2026-10-06:** the daytime activation was most likely the fallback to default solar times,
fixed in a recent commit. That fix is DD-038 (`716b54b`). Without a pinned location, a live fix or
Android's last-known location, SUNRISE/SUNSET rule tokens fell back to task43's 06:00/18:00
placeholder, so a sunset-anchored rule could fire in daylight. They now resolve through
`ContextSolarTimes`, with the pinned Circadian location first. DD-042 changed how often that
resolver reads the last-known location. **No N3 code is planned.** It stays unverified on the
reporter's device. If the report comes back on a build carrying DD-038, the two suspects below are
where to start; neither has been verified.

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

- **Cloud containers (2026-10-07) are x86-64**, so the qemu note below does not apply there, and
  they have no `.orch/`: run reviews as a fresh-context subagent instead of `.orch/codex.sh`.
- **Maven Central answered Gradle with 429 in a cloud container** (2026-10-07) while `curl` got 200
  for the same jar, so retrying never helped. What worked was a container-local init script,
  `~/.gradle/init.d/maven-central-mirror.gradle.kts`, that rewrites `repo.maven.apache.org/maven2`
  in `pluginManagement` and `dependencyResolutionManagement` to
  `https://maven-central.storage-download.googleapis.com/maven2/` from `settingsEvaluated`. Never
  commit it; the repo's repositories stay as they are.

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

## The reply to the reporter — drafted, superseded

Standing rule: **no issue gets a reply unasked** (owner, DB-082). The owner replied to the reporter
personally (2026-09-23), so this draft was never posted. Its content is kept only as analysis. What
the owner's reply asked for is not recorded here, so do not assume the two outputs below are on
their way.

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

## Where reports land — answered

The owner's #142 reply (2026-10-06) settles this for feature requests: they go to AAB, which builds
the feature first and Tideo ports it. That makes the split deliberate. Bug reports were not
addressed.
