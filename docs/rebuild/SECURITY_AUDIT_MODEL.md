# Security audit model: assumptions and trust boundaries

This document fixes the security assumptions, protected assets, attacker model, and required
invariants **before** implementation details are reviewed. It is an audit contract, not a claim
that the current implementation satisfies every invariant. Findings must cite code or tests and
must distinguish a violated invariant from an assumption that could not be validated.

## Scope and security objectives

The audit covers the Android application, its app-private state, Android system settings it can
change, imported profile documents, its single optional network integration, and its root/Shizuku
execution paths. Build infrastructure, the Android OS, a fully compromised device, and denial of
service by a device owner intentionally revoking permissions or killing the app are out of scope.
Those exclusions do not excuse unsafe behavior when an ordinary lifecycle event, process death,
permission denial, malformed input, or dependency failure occurs.

The objectives are:

1. preserve the integrity and safe range of display state;
2. prevent an external caller or imported document from gaining a privilege it was not granted;
3. protect persisted configuration, profiles, and user-identifying context data;
4. minimize network disclosure and reject untrusted network responses safely; and
5. remain fail-safe during cancellation, process death, boot, foreground-service restrictions,
   and partial failure.

## Protected assets

### Display and Android settings state

The primary safety-critical asset is the effective display state: brightness, brightness mode,
extra-dimming state, display-related global/secure toggles, and any original values needed for
restoration. The audit treats all writes through `Settings.System`, `Settings.Secure`, and
`Settings.Global` as privileged sinks, even when Android labels the underlying permission or API
differently. It must trace:

- which keys can be read or written at each privilege tier;
- validation and normalization before every write, including `NaN`, infinities, overflow,
  reversed bounds, and OEM-specific brightness ranges;
- ownership and ordering when the user, Android, another app, and the service write concurrently;
- capture, persistence, and restoration of pre-service values; and
- partial-write behavior when a multi-key change succeeds only in part.

An Android permission grant authorizes only the documented app feature. It is not consent for an
external caller, profile document, or network response to select arbitrary settings keys or values.

### Persisted settings and profiles

All state under
`app/src/main/kotlin/com/tideo/autobrightness/app/settings/` and
`app/src/main/kotlin/com/tideo/autobrightness/app/storage/` is trusted only after schema and
semantic validation. This includes DataStore values, opt-in gates, active-profile selection,
restoration snapshots, and app-private profile files. The audit must consider corruption,
truncation, stale versions, non-atomic updates, rollback/backup restore, concurrent updates, and
state observed after process death.

Persisted state is less trusted than compiled policy: it may choose among supported behavior but
must not introduce a new settings key, shell command, component, URI, or privilege tier. Backup
eligibility is a disclosure and integrity boundary; secrets, precise/user-identifying context,
transient authorization material, and unsafe stale runtime state must not be backed up merely
because they live in app-private storage.

### External Android component callers

Every exported manifest component is an IPC boundary. Intent actions, extras, data URIs, flags,
ClipData, binder calls, repeated delivery, and delivery order are untrusted unless Android enforces
an appropriate caller permission. Intent-filter matching alone is not authentication, and explicit
intents can reach an exported component without matching its filter.

The manifest review must inventory at least:

| Surface | Expected boundary and audit assumption |
|---|---|
| Exported launcher activity | Any app or user may launch it. Launching UI must not itself perform a privileged action or treat intent extras/data as trusted. |
| Exported boot receiver | Boot broadcasts are lifecycle hints, not authorization. The receiver must re-check persisted user opt-in and tolerate duplicates or spoofed explicit delivery. |
| Exported widget provider | System widget broadcasts and identifiers are untrusted input. Privileged widget actions should terminate at non-exported components or authenticated, immutable app-created `PendingIntent`s. |
| Exported automation receiver | Any co-installed app can send explicit or matching broadcasts. The entire surface must be inert until the user explicitly enables external control; enabling it does not authorize arbitrary commands, profile paths, or privilege expansion. |
| Exported QS tile service | The platform binding permission is part of the boundary and must remain declared. Binder callbacks and lifecycle churn still require validation and idempotence. |
| Exported provider | Provider export and its manifest permission are security-critical. Provider methods, URI grants, and binder identity must not expose a general privileged proxy. |
| Non-exported activities, receivers, and services | Non-exported status is relied upon as defense in depth, but app-created `PendingIntent`s and in-app routing still require immutable targets and bounded inputs. |

The inventory includes activities, broadcast receivers, services, content providers, automation
broadcasts, home-screen widgets, boot delivery, and QS tile binding. A review must fail closed if a
new exported component is found but is absent from this threat model.

### Storage Access Framework profile documents

A user selection in Android's Storage Access Framework grants access to bytes, not trust in those
bytes or in the document provider. Treat the provider-supplied display name, MIME type, size,
metadata, URI, stream behavior, and document content as attacker-controlled. The import boundary
must handle lying or absent lengths, oversized or endless streams, short reads, blocking/failing
providers, malformed or duplicate fields, deeply nested content, invalid UTF-8, extreme numeric
values, non-finite numbers, path-like names, and schema-version mismatches.

Parsing must be bounded before allocation and persistence. Imported values receive the same
semantic validation as UI-created values. Import must be transactional: failure cannot partially
replace the active profile or leave unsafe settings selected. Exported documents must not contain
unnecessary identifiers or internal authorization/restoration state.

### Geo-IP network exchange

The geo-IP provider and every network between the device and it are outside the trust boundary.
The request discloses at least the user's public IP address and timing to the provider; returned
headers, status, body, coordinates, timezone/location labels, and error text are untrusted.
Enabling geo-IP must be an explicit, informed user choice and must not silently broaden into
telemetry.

The audit must verify HTTPS-only transport, bounded time/response size, strict parsing and range
checks, no trust in redirects to weaker schemes, conservative caching/retention, and a safe local
fallback on timeout, malformed response, or hostile coordinates. No profile content, installed-app
context, precise device location, stable device identifier, authorization state, or diagnostic log
may be added to the request unless separately documented and consented to.

### Root and Shizuku execution

Root and Shizuku cross a high-privilege boundary. User approval grants the app access to a narrowly
defined capability; it does not make callers, profile fields, intent extras, network data, or
persisted strings trustworthy shell input. For every elevated operation the audit must establish:

- the command and arguments are fixed in code or selected from a strict allowlist;
- no untrusted value is interpolated into a shell program, property name, package name, settings
  key, path, or option;
- authorization is explicit, revocable, checked at use time, and not confused with feature opt-in;
- failure, timeout, binder death, partial output, and unexpected output fail closed;
- file descriptors and binder capabilities are not exposed through an exported component; and
- logs and UI errors do not disclose command output containing user or device information.

Accidentally granting elevated access is in scope: the app must keep least-privilege feature gates
after a grant and must not automatically activate every elevated behavior.

### Availability and physical safety

Brightness is physically perceptible and can make a device temporarily unusable. Availability is
therefore a security property, not merely reliability. Review must cover runaway update loops,
unsafe minimum/maximum outputs, rapid oscillation or flashing, permanent black-screen states,
failure to restore manual/system state, unwanted service resurrection after opt-out, boot loops,
sensor or retry loops causing battery drain, wakeup storms, and foreground-service/notification
failures that leave work running invisibly or repeatedly crashing.

Panic, stop, shutdown, task-removal, permission-loss, and failure paths must be idempotent. Cleanup
must not depend solely on a cancellable coroutine or an in-memory snapshot. Where Android cannot
guarantee execution after abrupt process death, the design must persist enough bounded restoration
state to recover on the next controlled entry, and document any residual platform limitation.

## Attacker and failure classes

The audit uses the following independent classes; a finding need not require several at once.

1. **Unprivileged co-installed app.** Can discover exported components, send explicit and implicit
   intents/broadcasts, vary extras and flags, replay/reorder calls, and attempt to induce privileged
   work. It has no signature permissions, root, Shizuku grant, or direct app-private-file access.
2. **Malicious document provider.** Controls SAF metadata and stream behavior, including blocking,
   inconsistent, oversized, truncated, or changing content, while operating under its own process
   lifecycle.
3. **Malformed imported content.** A passive file can exercise parser, schema, numeric, naming, and
   persistence edge cases without relying on a malicious provider.
4. **Hostile or compromised network endpoint.** Observes requests and returns malformed, huge,
   delayed, replayed, misleading, or semantically dangerous geo-IP data. TLS platform compromise
   and a fully compromised OS are out of scope, but normal certificate and protocol failures are in.
5. **User accidentally granting elevated access.** The user authorizes root, Shizuku, secure
   settings, DUMP, location, or another special access without intending every possible elevated
   feature. The grant must not erase in-app consent boundaries.
6. **Lifecycle/process-death races.** Android or the user kills, restarts, rebinds, redelivers, or
   cancels work between any two state transitions; callbacks may be duplicated or reordered and
   persisted state may lag in-memory state.

Social engineering outside the app, arbitrary physical interaction by an unlocked-device holder,
kernel/OS compromise, and a malicious app holding the same signing key are excluded. Safety checks
must nevertheless remain effective on rooted devices unless root itself has modified the app or OS.

## Required invariants

These are release-blocking security properties:

1. **No silent privilege acquisition or expansion.** External input cannot grant a permission,
   trigger an authorization prompt deceptively, enable elevated features, or turn an existing grant
   into a broader execution primitive.
2. **No caller-controlled shell.** Root/Shizuku commands and privileged arguments are fixed or
   strictly allowlisted; caller-, document-, persistence-, and network-controlled strings never
   become shell syntax or select arbitrary privileged targets.
3. **Automation defaults off.** External automation is inert until the user opts in. Opt-out takes
   effect across process death and races, and every automation action remains bounded after opt-in.
4. **Safe display outputs.** Invalid, corrupt, non-finite, stale, or adversarial settings cannot
   produce out-of-range or physically unsafe display outputs. Validation occurs at every trust
   boundary and immediately before the privileged sink.
5. **Restoration survives cancellation.** Panic, stop, shutdown, and recovery paths restore owned
   display state despite coroutine cancellation, duplicate callbacks, service teardown, or process
   lifecycle changes, to the extent Android permits; restoration is idempotent and does not
   overwrite a newer user-owned value without an explicit ownership rule.
6. **No unwanted resurrection.** Boot, sticky restart, tile/widget calls, and redelivered intents
   cannot restart monitoring after persisted user opt-out. Failure to read consent fails closed.
7. **Bounded resource use.** Documents, network responses, command output, retries, sensors,
   notifications, and IPC delivery have size/time/rate bounds that prevent sustained battery,
   memory, storage, or CPU exhaustion.
8. **Visible foreground operation.** Monitoring that requires a foreground service does not
   continue invisibly when notification permission, channel creation, or foreground promotion
   fails; failure converges to a safe stopped/restored state without a crash loop.
9. **Data minimization.** Sensitive or user-identifying data is not unnecessarily logged, backed
   up, retained, exported, or transmitted. This includes precise/coarse location, public IP-derived
   location, SSIDs, installed/foreground app context, profile names/content, settings snapshots,
   shell output, document URIs, and authorization state.
10. **Transactional state changes.** Failed imports, partial privileged writes, and lifecycle races
    cannot commit a configuration/runtime combination that would have been rejected as a whole.

## Audit method and evidence standard

Review in trust-boundary order rather than file order: enumerate manifest IPC surfaces; trace each
external value to privileged and persistence sinks; review SAF and network bounds; enumerate every
root/Shizuku command; then model service lifecycle and restoration transitions. Only after this
model is agreed should implementation details be assessed.

For each invariant, record (a) entry points, (b) validation or authorization checks, (c) sinks and
side effects, (d) race/failure behavior, and (e) executable evidence. Tests should include hostile
and boundary inputs, process recreation where practical, and negative assertions that a privileged
sink was not called. A platform guarantee used as a control must be named precisely (permission,
protection level, binder identity, or component export rule); “Android handles it” is not evidence.

