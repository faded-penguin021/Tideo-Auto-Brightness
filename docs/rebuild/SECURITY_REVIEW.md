# Security review — boundaries, controls, evidence

**Reviewed at:** the DA-043…DB-006 round (2026-07-31), on top of the DA-031…DA-042 hardening.
**Model:** [`SECURITY_AUDIT_MODEL.md`](SECURITY_AUDIT_MODEL.md) — assumptions, assets, attacker
classes, invariants. Keep that file implementation-light; this one carries the implementation.

This replaces six point-in-time audit documents (`COMPONENT_SECURITY_AUDIT.md`,
`DEPENDENCY_RELEASE_SECURITY_AUDIT.md`, `architecture/runtime_display_safety_audit.md`,
`architecture/runtime_resource_lifetime_audit.md`, `architecture/privileged_command_audit.md`,
`architecture/geo_ip_audit.md`). They repeated each other's trust boundaries, call traces and test
wish-lists; what was durable is in the matrix below, linked to the code and the tests rather than
re-narrated. Decisions and accepted risks live in `../LEDGER_A.md`; dated observations live
in `../STATE.md`.

## Status legend

| Status | Meaning |
|---|---|
| **code+test** | Enforced in production code and pinned by an executable test. |
| **code** | Enforced in production code; no test pins it (usually because the OS boundary is not reachable from JVM/Robolectric). |
| **manual** | Verified by reading/running, not by a test that would catch a regression. |
| **product risk** | Deliberate behaviour with a security consequence the owner has accepted. |
| **residual** | Known limit, not currently mitigated. Device verification or accepted. |

## Boundary matrix

| Boundary | Invariant | Control | Evidence | Residual | Status |
|---|---|---|---|---|---|
| Exported `ControlReceiver` | No side effect before the opt-in gate | `externalControlEnabled` read first; unknown verbs refused before the admission gate | `ControlReceiverAdmissionTest`, `ControlReceiver.KNOWN_ACTIONS` | Once ON, **any installed app** may send every verb — see product risk below | code+test |
| Exported `ControlReceiver` | A flood cannot exhaust the runtime | One command in flight (receiver) **and** a bounded, coalescing control queue (pipeline) | `ControlFloodBoundTest` (10 000 reapplies → 1 pending; alternating flood → capped) | Broadcast delivery itself is the OS's to schedule | code+test |
| Pipeline control queue | No state transition is lost to coalescing | Only *consecutive* duplicates collapse; `OverrideDetected` never coalesces | `ControlFloodBoundTest.coalescingNeverLosesAStateTransition` | — | code+test |
| `AmbientMonitoringService` | An unknown action never starts the runtime | Non-`START` actions with a non-null action are refused and the instance stops | `AmbientMonitoringServiceTest` | Service is not exported today; this is defence for if it ever is | code+test |
| Sticky restart | A disabled service never resurrects | Null-intent restart gates the whole graph behind a generation-checked DataStore read; read failure fails closed | `AmbientMonitoringServiceTest` (DA-030 cases) | — | code+test |
| SAF profile import | An untrusted provider cannot exhaust memory | 256 KiB streamed cap + one probe byte; strict UTF-8; declared size is a hint only | `ProfileLoadResultTest` | — | code+test |
| SAF profile import/export | An untrusted provider cannot block the UI or hang the caller | All provider work on `Dispatchers.IO` under a 20 s bound; `CancellationException` rethrown | `ProfileImportExportManager` (DA-044) | A hostile provider can still park **one IO thread** past the timeout — Android offers no abort for a binder call already in progress | code |
| Imported profile JSON | Malformed input cannot become silent defaults | Depth/duplicate-key guard, schema-version checks, native payloads never fall back to the legacy parser | `ProfileLoadResultTest`, `ImportStructureGuard` | — | code+test |
| Saved-profile store | Corrupt app-private state cannot allocate without bound | Read capped before parse; profile count and name limits after | `UserProfileStore` (DB-004) | — | code |
| Extra Dim (secure write) | A failed write never leaves a stale, stronger dim active | Failed level write while engaged → best-effort deactivate + latch to UNKNOWN; success reported only after the write | `SuperDimmingCoordinatorTest` (DB-001, 3 cases) | OEM key variance: some skins rename/relocate `reduce_bright_colors_*` | code+test |
| Display writes | Teardown restores mode and clears dimming | `stop()` independently `runCatching`s disengage + restoreMode | `BrightnessPipelineControllerTest` (DA-038) | A hard process kill runs no callback; next transition/panic recovers | code+test |
| Panic | The 255 restore is the final write | Consumer cancel-and-**join** before the panic effect | `PanicHandlerTest` (D-139) | — | code+test |
| Backup / restore | Restored config never asserts runtime state | `SettingsBackupAgent.onRestoreFinished()` → `SettingsBackupSanitizer` forces `serviceEnabled`/`contextOverride` | `SettingsBackupSanitizerTest` (6 cases), `DataExtractionRulesTest` | Allowlist and sanitizer are one control in two files — changing either alone reopens the hole | code+test |
| Privileged user service | The binder cannot select a package or command | Typed AIDL operations only (no argv); package derived from the service's own `Context` | `IShizukuUserService.aidl`, `ShizukuUserService` | Root cannot authenticate `su`; the user chose that provider | code |
| Privileged child process | No unbounded wait | 10 s command timeout, bounded post-kill reap and reader joins, bounded stdout/stderr | `ShizukuUserService` (DB-005) | A binder transaction already executing is not cancellable; the child's own timeout is the bound | code |
| Shizuku grant flow | One flow at a time, always terminated | Single-flight admission; prompt timeout as well as bind timeout; listener removed on every path | `ShizukuGrantGateway` (DB-005) | Device verification: manager restart, prompt dismissal, root prompt | code |
| Geo-IP | Cancellation actually releases the socket | Blocking request runs as a child; parent unwinds at `await()` and disconnects | `BlockingReadCancellationTest` (both halves) | Uncancelled worst case remains 30 s connect + 30 s read | code+test |
| Geo-IP | Public-IP disclosure is opt-in and minimal | Fixed HTTPS endpoint, redirects disabled, 16 KiB cap, strict numeric parse, once-daily gate, default-off consent | `GeoIpLocationClientTest` | The endpoint operator sees the device's public IP by construction | code+test |
| Release build | No Play dependency blob in the APK | `dependenciesInfo { includeInApk = false }` + a signing-block allowlist check in CI | `scripts/fdroid-check.py signing-blocks`, verified against a deliberately regressed build | — | code+test |
| Reproducibility | Two independent builds of a commit agree | SHA-256 over decompressed entry bytes, signature files excluded | `fdroid-compat.yml` stage 4; `fdroid-check.py selftest` | Not proof of F-Droid acceptance — see `FDROID_VALIDATION.md` | code+test |
| CI checkout | No git credential inside a third-party image | `persist-credentials: false` on the buildserver and unpinned-tooling jobs | `fdroid-compat.yml` | Token is read-only regardless | code |
| Dependencies | No unreviewed repository or dynamic version | `google()`/`mavenCentral()`/plugin portal only; `FAIL_ON_PROJECT_REPOS`; wrapper distribution SHA-256 pinned | `settings.gradle.kts`, `gradle-wrapper.properties` | Not dependency verification (deliberately declined, ledger) | code |

## Accepted product risk

**External control is ambient local authority.** Once the user enables it, *any* app on the device
can send every control verb. There is no shared secret and no caller identity check, deliberately:
the verbs are exactly what the tile, widget and notification already expose, no data leaves the
device, and a token would be friction in the Tasker/MacroDroid UIs. This is only acceptable while
the product promise is stated as *"enabling external control lets any installed app control Tideo"* —
not *"only my chosen automation app can"*. The in-app help and `docs/AUTOMATION.md` say the former.

## Residual and device-verification items

Not unit-testable from JVM/Robolectric; treat as device checks:

1. Shizuku manager restart / disconnect mid-grant; prompt dismissal (now bounded — verify the bound).
2. Root prompt timeout and command-timeout kill paths.
3. A genuinely hostile SAF provider (slow, stalling, lying about size).
4. Backup → restore on a second device: config arrives, service stays off, no manual context lock.
5. Foreground-service start failure under background restriction.
6. OEM variance in `reduce_bright_colors_*`.

## Findings from the 2026-07-31 adversarial round

Five primary findings were raised against the hardening branch. All five were real; the notes below
record where the *remedy* differs from what was proposed, and why.

| # | Finding | Outcome |
|---|---|---|
| 1 | External-control admission bounds broadcast coroutines, not downstream work | **Fixed** (DA-043). Bounded + coalescing control queue. Two deviations from the proposal: consecutive-duplicate coalescing rather than semantic coalescing of `REAPPLY`/`PAUSE`/`RESUME` (folding a `PAUSE` into an earlier one loses the user's last intent — pinned by test); and **no priority lane for `PANIC`/`DISABLE`**, because neither uses this queue at all — they are service actions that run teardown directly, so they cannot be starved by a flood. |
| 2 | SAF I/O is neither offloaded nor time-bounded | **Fixed** (DA-044). `Dispatchers.IO` + 20 s bound + cancellation rethrow. The proposed "clearly document that Android offers no reliable hard cancellation" is the accurate half and is now documented at the call site: the timeout frees the *caller*, not the thread. |
| 3 | Extra Dim can remain at a stale, stronger level after a failed update | **Fixed** (DB-001), including the false "ON" diagnostic. Narrowed from the proposal: deactivation runs only when the latch says **engaged**, because from UNKNOWN there is nothing of ours on screen and the pre-existing "a missing level must never activate an unknown OEM level" invariant must keep holding. That invariant's test caught the over-broad first attempt. |
| 4 | Backup includes stale runtime state | **Fixed** (DB-002) by sanitizing at the restore boundary, **not** by splitting the DataStore. Splitting is a schema migration of the app's central settings object; backup eligibility is a property of the *file*, so the only question is what the file may say after a restore. Testing then exposed a hole the finding did not name: `serviceEnabled` **defaults to `true`** and kotlinx omits default-valued fields, so the dangerous backup is the one where the key is *absent*. A reset-what-is-present sanitizer would have no-opped on exactly the common case. |
| 5 | The F-Droid comparator does not prove what its docs claim | **Fixed** (DB-003) — and the finding understated it. The comparator trusted declared CRC32 metadata without decompressing: `fdroid-check.py selftest` builds two archives with equal CRC fields and different payload bytes, and the old code called them identical. Now SHA-256 over decompressed bytes. The signing-block reader is EOCD-anchored (it previously `rfind`-ed the magic over the whole file, so entry content could decide where a security check starts reading) with the eight-byte overrun removed. Documentation claims corrected in `FDROID_VALIDATION.md`. `apksigcopier` was **not** adopted: both inputs here are our own builds, one signed by a throwaway CI key, so there is no upstream signature to copy — it is the right tool for verifying a *published* APK, which is F-Droid's job, not this workflow's. |

Secondary items:

| Item | Outcome |
|---|---|
| Unknown service actions call `ensureRunning()` | **Fixed** (DB-005). |
| Shizuku prompt has no timeout; concurrent requests share a request code | **Fixed** (DB-005): prompt timeout + single-flight. |
| Unbounded `waitFor()`/`join()` after force-kill | **Fixed** (DB-005): bounded reap and joins. |
| `SavedProfilesSerializer` reads before limiting | **Fixed** (DB-004). |
| Geo-IP cancellation claim is stronger than the implementation | **Confirmed and fixed** (DB-006). Written as a challenge, it became the round's clearest correction: `invokeOnCompletion` was registered on the job that then did the blocking read, and a job parked in `read()` does not complete on `cancel()` — so the disconnect could only run *after* the wait it existed to cut short. The 60 s worst case for an uncancelled request is also correct and now stated. |
| `ShizukuUserService` output limit of `0` | **New finding, fixed** (DB-005). `pm grant` and `setprop` ran with a stdout limit of 0, and *any* output was treated as overflow → command failure. A successful grant that printed a warning line was reported as a failed grant. Now `DISCARD_OUTPUT`: drain the pipe, keep nothing, fail on nothing. |
| Profile apply spans three transactions; reorder or consolidate | **Declined, with reasoning.** The proposed reorder is worse. Current order is baseline-clear → fallback-name → settings; a death mid-sequence leaves the *label* ahead of the settings, and the next apply or resume converges on what the user asked for. Writing settings first would leave the fallback name pointing at the **previous** profile, so a later "resume context automation" silently reverts the user's deliberate load. The stores are separate DataStores, so there is no atomic option short of merging them, which costs more than the window is worth. |
| `onDestroy()` OFF event uses an async `externalControlEnabled` cache | **Guarantee weakened, as offered.** A synchronous read is not available in `onDestroy()`. The event is best-effort: a rapid opt-in-then-stop may omit it and a rapid opt-out-then-stop may emit one. Automation must treat the outbound event as a hint and read state on the next command, which `docs/AUTOMATION.md` now says. |

## Where things live

| Question | Home |
|---|---|
| What are we protecting, from whom, under what assumptions? | `SECURITY_AUDIT_MODEL.md` |
| Which control enforces which invariant, and what proves it? | this file |
| How do I cut a release / read an F-Droid failure? | `../RUNBOOK.md` playbook 6, `FDROID_VALIDATION.md` |
| Why was it decided this way? What was declined? | `../LEDGER_A.md` |
| What is true as of a date (alerts, versions, state)? | `../STATE.md` |
