# Runtime display safety audit (DA-038)

Scope: the complete ambient-sensor pipeline and every runtime path that writes brightness,
Extra Dim, Night Light, color/accessibility display modes, screen persistence/timeout behavior,
HDR refresh behavior, or force-dark state. Extreme unintended darkness or brightness is treated as
a safety failure, not merely a cosmetic defect.

## Sensor-to-write trace

1. `AmbientMonitoringService.ensureRunning()` starts the graph composed by `AppModule`: context and
   display-toggle collectors, panic detector, and `BrightnessPipelineController`.
2. `LightSensorSource.samples()` enters `BrightnessPipelineController.onSensorSample()`. The cached
   opt-in, sensor reliability, dead-band, throttle and atomic `inCycle` gate reject or claim it.
   Accepted ticks enter the single consumer channel; ticks while a cycle/animation is active drop.
3. `PipelineCycleRunner.runCycle()` reads validated effective settings, smooths lux, invokes the
   domain `BrightnessEngine`, applies the PWM hardware floor, and either direct-writes or calls
   `AnimationRunner.animate()`.
4. `AnimationRunner` bounds the frame count to at least one; its inputs and engine target are integer
   domain brightness. Every actual write reaches `AndroidScreenBrightnessController.write()`, which
   clamps `0..255`, normalizes with `Math.round` to the OEM device maximum, and records the raw
   successful self-write for observer-echo rejection. Reads clamp the OEM value before normalizing
   back. No floating-point value crosses this adapter boundary.
5. The un-floored target also enters `SuperDimmingCoordinator`. Domain dimming math may use doubles,
   but the resulting integer is accepted only when positive and the platform adapter clamps it to
   `0..1000`. DA-038 writes the bounded level before activation and advances its state latch only on
   successful writes. Disengage attempts both level-zero and activation-off and remains retryable
   after either failure.
6. `BrightnessObserver` and `OverrideMonitor` reject current-value self echoes and post qualifying
   external changes back to the same consumer. `PipelineCycleRunner.handleOverride()` settles,
   rechecks gates/current brightness, clears Extra Dim and pauses. Animation's independent band and
   consecutive-read check catches a manual write during a long transition.

## Other system-write owners

| State | Writer and trigger | Bounds / ordering / restoration |
|---|---|---|
| Brightness + mode | cycle/reapply/resume; `PanicHandler`; controller stop restores the saved system auto/manual mode | `0..255` then OEM normalization. Saved mode is synchronously persisted before MANUAL and survives process death. Panic best-effort attempts manual, 255, mode restore, and Extra Dim off independently. |
| Extra Dim | `SuperDimmingCoordinator` on every accepted target, pause, hibernate, override, panic, stop | Adapter clamps `0..1000`; level-before-on, level-zero-before-off; unknown initial latch forces a residual clear after process recreation. Failed writes do not become false no-op state. |
| Night Light / temperature | `DisplayTogglesCoordinator` profile changes and circadian ticker; panic reset | booleans are `0/1`; controller sanity-clamps temperature `1000..10000 K`. Coordinator diff-suppresses unchanged opinions and serializes tick/swap/stop/panic. Stop returns to baseline; panic unconditionally disables. A hard process kill cannot execute baseline restoration, so the next profile transition or panic is the recovery path. |
| Color mode | display coordinator: daltonizer/grayscale and inversion | enum-only daltonizer values; value is written before enable to avoid a one-frame stale matrix. Panic disables both; normal stop restores baseline. |
| Screen persistence / timeout | display coordinator: AOD and `STAY_ON_WHILE_PLUGGED_IN` | boolean / fixed all-charger mask, not caller-provided integers. Stop restores baseline; panic disables. The app does not write `SCREEN_OFF_TIMEOUT`. |
| Refresh/HDR behavior | display coordinator force-SDR | Android 14 gate. Disable-list is populated before enforcement; enforcement is removed before clearing. The app does not write peak/min refresh-rate settings. |
| Force dark | Tools UI and service reassert through `ForceDarkController` | fixed property name and literal boolean only; Shizuku then bounded root fallback. A mutex serializes rapid requests. Property is reboot-volatile; service startup reasserts the persisted opt-in. |

All secure/global writers tier-check immediately before the write and return `Result`; revoked grants
and provider exceptions therefore remain contained. A revoked permission necessarily prevents the
app from restoring the protected setting until the grant returns; panic still attempts every
unprotected and protected recovery independently rather than short-circuiting.

## Lifecycle and concurrency findings

* `emergencyStop()` cancels and **joins** the consumer before the panic 255, making 255 the final
  brightness write even when panic interrupts animation. Ordinary stop cancels the consumer before
  teardown; animation suspends between frames, so cancellation prevents subsequent frames.
* Display-toggle stop cancels collection, then synchronously takes the same mutex used by tick/apply
  before baseline restoration. Panic tears that coordinator down first, so `onDestroy()` cannot
  resurrect an impairing baseline afterward.
* A null-intent sticky restart posts the foreground notification but starts no sensor/writer until a
  generation-checked DataStore read confirms opt-in. Read failure fails closed. Explicit commands
  supersede the pending decision; destruction increments the generation and cancels it.
* Brightness self-write detection compares against the latest raw device value rather than consuming
  callback tokens; delayed observer callbacks therefore cannot consume a newer frame's suppression.
* Settings validation is the floating-point boundary: non-finite imported numbers are rejected and
  runtime brightness writes accept integers. Platform clamps remain defense in depth. Force-dark and
  display toggles accept typed booleans/enums rather than arbitrary strings or numeric values.

## Adversarial lifecycle contract tests

The executable suite must retain these cases:

1. **Stop during animation:** park in frame delay, stop, release virtual time, assert no later frame;
   mode/baseline cleanup still runs.
2. **Panic during animation:** park mid-frame, panic, assert consumer unwound and 255 is final; inject
   failures into manual-mode/brightness restoration and assert Extra Dim OFF is still attempted.
3. **Permission revocation:** revoke secure access between tier sample and write; assert a failed
   level never enables Extra Dim, failed OFF remains retryable, and display writes return failures.
4. **SettingsProvider exceptions:** make brightness/provider operations throw independently; assert
   panic attempts all recovery effects. Make sticky settings read throw; assert zero runtime starts.
5. **Null-intent sticky restart:** cover disabled, enabled, unreadable, destroy-before-read, and a
   newer explicit command winning after the read's last suspension.
6. **Rapid control broadcasts:** issue pause/resume/reapply/disable/panic without advancing the test
   dispatcher; assert serialization or terminal teardown, no resurrection, and no post-panic frame.
7. **Process recreation with engaged secure dimming:** construct a fresh coordinator with unknown
   latch and an above-threshold target; assert `level=0` and `activated=false`, then assert the known-
   off path suppresses later no-op writes.
8. **Stale observer echoes / OEM range:** animate across a non-255 OEM maximum, deliver delayed
   callbacks that reread the latest frame, and assert no false override; then write outside the band
   twice and assert pause.
9. **Display apply/stop/panic race:** hold the coordinator mutex in an apply, cancel/stop or panic,
   then release it; assert baseline or panic defaults are final and no queued apply trails them.
10. **Bounds and malformed numbers:** test brightness below/above `0..255`, secure level below/above
    `0..1000`, temperature outside its sanity band, invalid imported NaN/infinity, and OEM maxima both
    below and above 255.

Items 2, 3, 4, 5, 7, 8 and 9 have direct regression coverage in the current unit/Robolectric suite;
items 1, 6 and 10 are split across controller, service, adapter, settings-validation and coordinator
tests because the production ownership is likewise split.
