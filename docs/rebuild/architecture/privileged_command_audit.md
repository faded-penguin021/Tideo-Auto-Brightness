# Privileged command audit (2026-07-29)

Scope: every root, Shizuku, `Runtime.exec`, and shell-command path in `platform/privilege`, plus
the Wi-Fi and force-dark callers. This is a source trace, not an on-device claim; Shizuku and root
manager behavior still require device verification.

## Allowlist and argument provenance

| Operation | Transport and final argv | Origin of every variable argument | Untrusted-input proof |
|---|---|---|---|
| Secure-settings grant | root: `su -c "pm grant <pkg> android.permission.WRITE_SECURE_SETTINGS"`; Shizuku: `pm`, `grant`, `<pkg>`, fixed permission | `<pkg>` is Android's `Context.packageName`. In the Shizuku process it comes from the `Context` supplied by Shizuku when constructing `ShizukuUserService`, not Binder. | Public APIs accept no package, command, intent extra, profile value, network value, or UI string. Android package names cannot contain shell metacharacters. The former Binder `packageName` parameter was removed, so possession or confused use of that binder cannot grant a different package. |
| Wi-Fi status | Shizuku: `cmd`, `wifi`, `status`; root: `su`, `-c`, `cmd wifi status`; DUMP: `sh`, `-c`, `dumpsys wifi` | All tokens and both constant scripts are literals at their strategy call sites. | Network output is consumed only *after* execution by fixed SSID parsers. An SSID cannot flow backward into command selection or argv. Profiles merely cause the Wi-Fi reader to run; they supply no command data. |
| Force-dark read | Shizuku: `getprop`, `debug.hwui.force_dark`; root: `su -c "getprop debug.hwui.force_dark"` | Property name is the `ForceDarkController.PROP` constant. | UI and service callers choose the typed read operation only; there is no string parameter. |
| Force-dark write | Shizuku: `setprop`, fixed property, `true|false`, then fixed read; root: the equivalent fixed `su -c` script | The only variable is a Kotlin `Boolean`, originating either from the Tools switch or the persisted `forceDarkApps` opt-in. It maps through an exhaustive `if` to one of two literals. | Intent actions can cause the service to reassert the persisted `true` choice, but no intent extra is read. Profiles, network responses, and UI labels/text fields are absent from the call graph. |
| Copyable ADB instructions | `adb shell pm grant <pkg>` plus one of two fixed permissions | `<pkg>` is `Context.packageName`. | These strings are displayed/copied, never executed by the app. No editable UI text contributes to them. |

Callers are intentionally typed: `ShizukuShell` exposes `ReadOperation.WIFI_STATUS`,
`ReadOperation.FORCE_DARK`, and `setForceDark(Boolean)`; AIDL exposes four matching operations and
the package-free grant. There is no generic privileged `exec(String[])` capability. The user-service
uses argv execution without a shell for every Shizuku operation.

## Boundary and lifecycle findings

* Shizuku authenticates the app permission before either bind. The service component is derived from
  the application's own package and the returned binder is ping-checked. The important local
  authorization is capability minimization: even if a binder reference were misdelivered, its input
  space is no argument, a Boolean, or one of fixed transactions; it cannot name a package or command.
* The Shizuku grant bind has a 15-second timer, exactly-once completion, unbinds on every result,
  timeout, bind exception, and disconnect, and does blocking Binder work off the callback thread.
  Runtime-operation binds have a four-second cancellable timeout and unbind on cancellation and after
  connection. A transaction already executing cannot be interrupted by Binder cancellation, but the
  service-side child has its own ten-second limit.
* User-service commands close stdin, drain stdout and stderr concurrently, cap stdout by operation
  (64 KiB Wi-Fi; 128 bytes property) and stderr at 4 KiB, require exit zero, kill on timeout, and
  return no stderr. Root/DUMP Wi-Fi processes similarly close stdin, drain both pipes, cap output at
  256 KiB/4 KiB, require exit zero, and kill after 15 seconds. Force-dark root and root grant also
  close stdin, enforce 15 seconds, kill on expiry, and require exit zero.
* Root fallback occurs only after Shizuku returns null. Failure is fail-closed (`null`/`false`) and
  never treats empty failed output as a valid result. Force-dark serializes competing calls so an
  older privileged completion cannot overwrite a newer switch request.
* Privileged Wi-Fi stdout necessarily contains the connected SSID and DUMP output can contain other
  Wi-Fi diagnostics. It remains in memory, is bounded, is reduced immediately to an SSID, and is not
  logged or shown verbatim. Shizuku/root stderr and exception details are not returned to UI. The UI
  receives only typed success/failure or the parsed SSID already needed for local rule evaluation.

## Residual constraints

Cancellation stops waiting for a Shizuku bind and unbinds it, but Java `Process` and synchronous
Binder calls are not coroutine-cancellable once executing; hard process timeouts provide the upper
bound. Root execution cannot cryptographically authenticate the `su` implementation—the user chose
and controls that root provider—so root is a fallback, never a trust substitute. On-device tests
should cover Shizuku manager restart/disconnect, root-prompt timeout, command timeout, and output-cap
failure because Robolectric cannot reproduce those OS boundaries.
