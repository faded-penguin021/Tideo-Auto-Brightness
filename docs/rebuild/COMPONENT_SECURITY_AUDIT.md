# Android component security audit

This segment inventories every activity, service, receiver, and provider declared in
`app/src/main/AndroidManifest.xml`. It traces externally supplied actions and extras to their
side effects and evaluates the component boundary against `SECURITY_AUDIT_MODEL.md`. Code is the
ground truth; manifest comments are treated as design context, not enforcement.

## Executive result

No high-confidence authorization bypass was found. The tile and Shizuku provider have platform
permission gates, the mutation-capable widget receiver and monitoring/accessibility services are
non-exported, boot rechecks persisted consent, and the exported widget provider only repaints.

`ControlReceiver` is the important residual boundary. It deliberately has no caller permission or
identity check. Its persisted boolean defaults off and prevents command side effects until the
user opts in. Once on, however, it is a feature-enable switch rather than caller authentication:
**any co-installed app can exercise every bounded control verb**. That ambient local authority is
an explicit product decision for Tasker/MacroDroid usability, not automatically a defect. It is
acceptable if the promised model is “enabling external control lets any installed app control
Tideo”; it is insufficient if the intended promise is “only my chosen automation app can control
Tideo.” The UI/help should state the former plainly.

## Component matrix

“Permission” below is the caller-side component permission, not a permission the app itself uses.
An intent filter is discoverability/routing, not authorization; an explicit intent can reach any
exported component without matching its filter.

| Component | Type | Exported | Caller permission / code authorization | Accepted actions and extras | Final side effect |
|---|---|---:|---|---|---|
| `.app.MainActivity` | activity | yes | none; ordinary launcher boundary | `MAIN` + `LAUNCHER`; code reads no action, data, `ClipData`, or extras | Requests notification visibility if needed, schedules maintenance, conditionally restarts already-enabled monitoring, renders Compose UI |
| `.app.runtime.AmbientMonitoringService` | FGS | no | app UID via non-exported component | internal `START`, `PAUSE`, `RESUME`, `REAPPLY`, `RESUME_CONTEXT`, `PANIC`, `DISABLE`; `reason` is attached to `START` but never read | Controls the sensor/pipeline/display runtime; panic restores and stops; null sticky restart rechecks persisted enablement |
| `.app.runtime.BrightnessTileService` | tile service | yes | `android.permission.BIND_QUICK_SETTINGS_TILE` | platform tile binding/lifecycle and `QS_TILE`; no app extras | System-UI click atomically flips persisted enablement and starts/stops monitoring |
| `.app.runtime.AabToastAccessibilityService` | accessibility service | no | non-exported plus `android.permission.BIND_ACCESSIBILITY_SERVICE`; user enables it in Android settings | platform bind/lifecycle; no app actions/extras; accessibility events ignored | Registers an in-process presenter for temporary accessibility-overlay flashes |
| `.app.runtime.BootCompletedReceiver` | receiver | yes | protected system broadcast plus persisted `serviceEnabled` gate | exact `BOOT_COMPLETED`; no extras | Schedules maintenance; starts monitoring only when persisted enablement is true |
| `.app.widget.DashboardWidgetProvider` | receiver | yes | none | `APPWIDGET_UPDATE`; framework supplies widget IDs, but implementation ignores the supplied IDs and refreshes all owned instances | Reads state and repaints `RemoteViews`; cannot mutate settings or start monitoring |
| `.app.widget.WidgetActionReceiver` | receiver | no | non-exported; app-created explicit immutable `PendingIntent` | exact `…widget.action.TOGGLE` and `…RESET`; no extras | Toggle flips enablement and starts/stops; reset requests a running-pipeline reapply |
| `.app.control.ControlReceiver` | receiver | yes | no manifest permission; `externalControlEnabled`, default off, is the first side-effect gate | nine exact `…control.*` actions; `LOAD_PROFILE` optionally accepts String `name` | Bounded service control, panic, named-profile apply, or context-automation resume |
| `rikka.shizuku.ShizukuProvider` | provider | yes | `android.permission.INTERACT_ACROSS_USERS_FULL`, enforced by Android before provider calls | content-provider protocol at `${applicationId}.shizuku`, not broadcast actions; no app-defined extras | Shizuku dependency bootstrap/binder handoff; no app business operation is exposed directly |

The Shizuku provider implementation is dependency code (`shizuku-provider` 13.1.5), not vendored
in this repository. This segment verifies the app-controlled manifest boundary, authority, URI
grant capability, and permission. A dependency-source review of its individual provider methods
and binder-identity handling remains separate evidence; `grantUriPermissions=true` alone grants
nothing unless an authorized party actually issues a URI grant.

## Entry-point traces

### MainActivity

The activity has no deep link and consumes no caller-controlled intent field. `onCreate` calls
`AutoBrightnessRuntime.bootstrap`, which always schedules unique periodic maintenance and reads
settings before starting monitoring. It cannot turn a disabled feature on; it can restart a
system-killed service whose persisted opt-in is already true. Replayed launches repeat scheduling
and the convergent start request. Unread extras cannot affect this path.

### AmbientMonitoringService

This service is the final privileged sink for most controls, but is non-exported. All repository
callers target it explicitly. Its action behavior is:

| Internal action | Result and lifecycle gate |
|---|---|
| `START` | Explicit-command branch calls `ensureRunning()`; callers must persist enablement first. |
| `PAUSE` | Pauses only an existing pipeline; otherwise the freshly created foreground service stops itself. |
| `RESUME` | Deliberately calls `ensureRunning()` and can resurrect an enabled, system-killed pipeline. |
| `REAPPLY` | Re-evaluates and reapplies only an existing pipeline; otherwise self-stops. |
| `RESUME_CONTEXT` | Runs genuine context resolution then reapplies only an existing pipeline; otherwise self-stops. |
| `PANIC` | Restores brightness/display defaults, tears down, and returns `START_NOT_STICKY`. |
| `DISABLE` | Disables and tears down, returning `START_NOT_STICKY`. |
| null intent | OS sticky restart: foregrounds for the deadline, then reads persisted enablement before starting any runtime resource; read failure fails closed. |
| unknown non-null action | Calls `ensureRunning()`. |

Unknown explicit actions are therefore not rejected at this final sink. That is safe only while
the service remains non-exported and its callers remain trusted; exporting it later would turn
this into an unwanted-start vulnerability. The `reason` extra on `START` is never consumed and
has no behavioral effect.

Null sticky restart uses cancellation plus a generation check: a newer command or destruction
supersedes an older DataStore result. A false or failed read stops the foreground shell without
initializing sensors or writers. Notification action `PendingIntent`s are explicit by component
and package and immutable.

### BrightnessTileService

The exported tile is gated by Android's Quick Settings binding permission. Its state mutation is a
System-UI-delivered click, not an arbitrary intent extra. The DataStore update atomically flips
`serviceEnabled`, then `onSettingChanged` starts or stops monitoring. Click replay is intentionally
non-idempotent: two clicks cause two flips. Binding/lifecycle churn only starts and cancels a state
collector and redraws the tile.

### AabToastAccessibilityService

The service is non-exported, requires Android's accessibility binding permission, and additionally
depends on explicit user enablement in system settings. It consumes no window content and ignores
all accessibility events. On connection it registers an in-process flash presenter; each message
replaces the previous small overlay and is removed on timeout, tap, interrupt, or unbind. There is
no broadcast parser, persistence mutation, or FGS creation path.

### BootCompletedReceiver

The receiver rejects null and every non-`BOOT_COMPLETED` action before asynchronous work. Android
protects the boot action from ordinary senders, but the receiver still treats delivery as a
lifecycle hint rather than authorization: it re-reads persisted `serviceEnabled`. It always
schedules maintenance and starts the explicit monitoring service only when enabled. Duplicate
delivery is convergent and cannot opt the user in.

### DashboardWidgetProvider

The exported provider owns no custom mutation action. A standard update calls `refresh`, which
does nothing if no widget exists; otherwise it reads enablement/live state and repaints all owned
widget IDs. Caller-supplied IDs do not select another provider or a privileged object. A spoofed
explicit delivery can at most induce bounded repaint/DataStore work while a widget exists, not
toggle settings or start the FGS.

The widget body opens `MainActivity` using an explicit immutable activity `PendingIntent`. Toggle
and reset use explicit component-and-package immutable broadcast `PendingIntent`s targeting the
non-exported `WidgetActionReceiver`. The widget has `updatePeriodMillis=0`, so it creates no polling
alarm or independent wakeup loop.

### WidgetActionReceiver

Unknown/null actions are no-ops. `TOGGLE` atomically flips enablement, drives start/stop, and
repaints. `RESET` sends an explicit internal `REAPPLY` then repaints. Toggle replay is deliberately
non-idempotent. Reset while monitoring is off can briefly instantiate and foreground the service
because `startForegroundService` is the command transport, but the service-side not-running gate
immediately stops it without starting the pipeline; it cannot leave an unintended monitoring FGS.

## Exported ControlReceiver

### Authorization semantics

`onReceive` first reads the action and the optional `name` String, then `goAsync` calls `handle`.
`handle` reads the separate `ControlPrefsStore` and returns before routing if
`externalControlEnabled` is false. Thus “first check” means the first **business side-effect**
check, not literally the first operation on untrusted parcel data. Keeping the flag outside
`AabSettings` prevents profile apply/import/reset from silently enabling external control.

After opt-in there is no caller authentication: no permission, UID/package check, allowlist,
signature pin, capability token, nonce, timestamp, rate limit, or confirmation. Explicit targeting
improves delivery reliability but does not authorize the sender.

### Public verb trace

| Action | Gate after global opt-in | Final effect | Replay / FGS behavior |
|---|---|---|---|
| `SERVICE_ON` | none | Persist true, schedule maintenance, start monitoring, repaint widget | State-idempotent, but repeats scheduling/start/update work; intentionally creates FGS. |
| `SERVICE_OFF` | none | Persist false, schedule maintenance, stop monitoring, repaint | State-idempotent; creates no service. |
| `SERVICE_TOGGLE` | none | Atomically invert persisted flag and start/stop | Non-idempotent and race-sensitive by contract; off→on intentionally creates FGS. Prefer ON/OFF for retrying automation. |
| `PAUSE` | service must already be running | Send explicit internal pause | Repeatable; absent pipeline produces only a brief foreground shell which self-stops. |
| `RESUME` | persisted `serviceEnabled` must be true | Send internal resume; resurrect enabled system-killed runtime if necessary | Disabled state is rejected at this exported boundary; converges on running/unpaused. |
| `REAPPLY` | service must already be running | Re-evaluate settings/context and apply brightness | Repeatable writes; absent pipeline briefly foregrounds then self-stops. |
| `PANIC` | none | Restore brightness/display defaults and tear down | Repeatable restoration/teardown; may briefly instantiate the service but never leaves it sticky. |
| `LOAD_PROFILE` | `name` must resolve exactly | Clear stale baseline, record manual profile, replace profile-scoped settings while preserving globals, latch context override, update active label, reapply if enabled | Same valid name is convergent but repeats writes/reapply. |
| `CONTEXTS_RESUME` | none | Clear stale baseline and manual lock; if enabled, resolve current context and reapply | Largely idempotent; enabled replay repeats evaluation/reapply. |
| null/unknown | null rejected before async work; unknown rejected by router | none | no side effect |

### Profile-name and malformed-extra handling

Profile resolution is `userProfiles.get(name) ?: DefaultProfiles.all[name] ?: return`. It is an
exact lookup with no trimming, case folding, path interpretation, file access, shell interpolation,
dynamic loading, or profile-content injection. Missing, blank, and unknown names are normally
no-ops. A user profile wins over a built-in with the same key. Valid application intentionally
latches manual context override while preserving enablement, override detection, debug level, and
panic sensitivity.

Android's Binder transaction limit rejects grossly oversized broadcasts before normal delivery.
A large but deliverable String is used only as a lookup key, so there is no unbounded document
parser or allocation proportional to a declared length beyond the parcel/String itself. A value
of the wrong type is outside the contract and has no local type/length guard around
`getStringExtra`. Since extraction precedes the boolean read, malformed parcel/type handling can
target process availability even with the feature off. This is a low-severity local robustness
opportunity, not an authorization bypass; framework parcel validation and Binder size limits
substantially constrain it.

### Residual product risk

After opt-in, an unprivileged app can toggle or disable monitoring, pause adjustment, force panic
restoration, select any known/guessed stored profile, clear the manual context lock, replay work,
and race the intended automation. These are integrity and availability effects over the bounded
feature surface. It cannot supply a profile body, settings key, shell command, URI, component, or
new privilege tier, and it receives no response data.

The implementation comments explicitly reject a shared secret as automation-UI friction because
the verbs mirror user-facing controls. This audit records that trade as **accepted product-design
risk**, not a defect. If the product promise changes to selected-caller authorization, the boolean
is insufficient. A signature permission would exclude independently signed automation apps;
package/signature allowlisting, a user-managed capability, or a bound capability exchange would be
a new usability/security design decision rather than a mechanical fix.

### Outbound automation event

When the same opt-in is enabled, the service sends global
`com.tideo.autobrightness.event.STATE_CHANGED` broadcasts containing Boolean `enabled`, `running`,
and `paused`, plus nullable String `profile`. Running snapshots are distinct-until-changed and
`onDestroy` emits the authoritative off state. When opt-in is off, no snapshot is emitted.

This deliberately discloses low-sensitivity liveness and the active profile name to all installed
receivers. Because the event has no sender/receiver permission, another app can spoof the same
action to automation consumers. Spoofing does not change Tideo state, but consumers must not treat
the event as authenticated. This is part of the same open-integration product model and a data-
minimization consideration for the broader audit.

## Findings and invariants

### Satisfied in this segment

- External automation is inert by default and its action router is bounded.
- Boot and sticky restart cannot start the runtime after readable persisted opt-out; sticky read
  failure fails closed.
- External `RESUME`, widget reset, pause, reapply, and context resume cannot create a persistent
  disabled-service zombie.
- Widget mutation terminates at a non-exported receiver through explicit immutable
  `PendingIntent`s.
- Tile and Shizuku provider retain their platform permission boundaries.
- No component extra reaches a shell command, arbitrary settings key, URI, or privilege grant.

### Residual and follow-up items

1. **Product decision — ambient automation authority:** once enabled, every installed app receives
   every bounded command capability. Documentation must not imply selected-caller authentication.
2. **Availability — replay/racing:** `SERVICE_TOGGLE` is intentionally non-idempotent; callers that
   retry should use explicit ON/OFF. A malicious app can repeatedly command work after opt-in.
3. **Robustness — pre-gate extra extraction:** `name` is read before authorization and has no local
   type/length guard. Binder bounds it, and it is lookup-only, but hostile parcel/type tests would
   improve executable evidence.
4. **Maintenance invariant — internal unknown action:** `AmbientMonitoringService` starts on an
   unknown non-null action. It must remain non-exported or gain a strict allowlist before exposure.
5. **Event authenticity/minimization:** opted-in state events globally disclose active profile name
   and can be spoofed to consumers.
6. **Dependency evidence:** separately review Shizuku 13.1.5 provider methods, URI handling, and
   binder identity from authenticated dependency source; this repository proves only its manifest
   boundary and version.

No item above demonstrates silent privilege expansion, caller-controlled shell input, arbitrary
profile input, or an unwanted persistent FGS in the reviewed code.

## Evidence inspected

- `app/src/main/AndroidManifest.xml`
- `app/src/main/kotlin/com/tideo/autobrightness/app/MainActivity.kt`
- `app/src/main/kotlin/com/tideo/autobrightness/app/control/ControlReceiver.kt`
- `app/src/main/kotlin/com/tideo/autobrightness/app/control/ControlPrefsStore.kt`
- `app/src/main/kotlin/com/tideo/autobrightness/app/runtime/AabToastAccessibilityService.kt`
- `app/src/main/kotlin/com/tideo/autobrightness/app/runtime/AmbientMonitoringService.kt`
- `app/src/main/kotlin/com/tideo/autobrightness/app/runtime/AutoBrightnessRuntime.kt`
- `app/src/main/kotlin/com/tideo/autobrightness/app/runtime/BootCompletedReceiver.kt`
- `app/src/main/kotlin/com/tideo/autobrightness/app/runtime/BrightnessTileService.kt`
- `app/src/main/kotlin/com/tideo/autobrightness/app/settings/ProfileApplier.kt`
- `app/src/main/kotlin/com/tideo/autobrightness/app/widget/DashboardWidgetProvider.kt`
- `app/src/main/kotlin/com/tideo/autobrightness/app/widget/WidgetActionReceiver.kt`
- `app/src/main/res/xml/aab_accessibility_service.xml`
- `app/src/main/res/xml/dashboard_widget_info.xml`
- `app/src/test/kotlin/com/tideo/autobrightness/app/control/ControlReceiverTest.kt`
- `app/src/test/kotlin/com/tideo/autobrightness/app/runtime/AmbientMonitoringServiceTest.kt`
- `app/src/test/kotlin/com/tideo/autobrightness/app/runtime/BootCompletedReceiverTest.kt`
- `app/src/test/kotlin/com/tideo/autobrightness/app/widget/WidgetActionReceiverTest.kt`

The existing tests pin default-off command suppression, action routing, disabled-service resume
rejection, unknown external action rejection, profile missing/unknown behavior, explicit service
targets, non-boot rejection, service zombie gates, state-event opt-in, and widget reset routing.
