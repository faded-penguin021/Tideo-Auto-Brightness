# Automation (Tasker / MacroDroid)

Tideo exposes an **opt-in** external-control surface so automation apps such as
[Tasker](https://tasker.joaoapps.com/) or [MacroDroid](https://www.macrodroid.com/) can both
**command** Tideo (turn it on/off, pause, reset, load a profile) and **observe** it (react to a
state change). It uses plain Android broadcast intents — no plugin library, no special permission.

> **Off by default.** Nothing below works until you enable it in the app:
> **Tools → Automation control → "Allow external control"**. While it is off, every command below is
> silently ignored and no events are emitted. There is **no password** — while it is on, any app on
> the device can send these commands, so only enable it if you use an automation app.

The exposed commands are exactly what Tideo's own Quick Settings tile, home-screen widget, and
notification already do; no data leaves the device.

---

## Commands you can send (inbound)

Send these as broadcasts. All actions share the namespace `com.tideo.autobrightness.control`:

| Action (append to `com.tideo.autobrightness.control.`) | Effect |
|---|---|
| `SERVICE_ON` | Start the auto-brightness service |
| `SERVICE_OFF` | Stop the service |
| `SERVICE_TOGGLE` | Flip the service on/off |
| `PAUSE` | Pause automatic control (brightness holds) |
| `RESUME` | Resume automatic control |
| `REAPPLY` | Recompute brightness now (e.g. after changing a setting) |
| `PANIC` | Reset — restore brightness and stop, same as the notification's **Reset** |
| `LOAD_PROFILE` | Load a saved profile — pass its name in a **string extra** named `name` |
| `CONTEXTS_RESUME` | Clear a manually loaded profile and hand control back to context rules |

`LOAD_PROFILE` with an unknown or missing `name` is a no-op. While the service is not running,
each verb behaves as follows: `PAUSE` and `REAPPLY` are safe no-ops; `RESUME` is ignored while you
have the service turned **off** (it only resumes a paused service, or restarts one Android killed —
it never overrides your master switch); `PANIC` always works — it restores brightness and display
settings to safe values and then stops, which is exactly what a panic is for.

### `adb` examples

```bash
# Load the "Night" profile
adb shell am broadcast -a com.tideo.autobrightness.control.LOAD_PROFILE --es name "Night" -n com.tideo.autobrightness/.app.control.ControlReceiver

# Turn the service off
adb shell am broadcast -a com.tideo.autobrightness.control.SERVICE_OFF -n com.tideo.autobrightness/.app.control.ControlReceiver
```

> **Debug builds** use the `com.tideo.autobrightness.debug` package in the `-n` component
> (e.g. `-n com.tideo.autobrightness.debug/com.tideo.autobrightness.app.control.ControlReceiver`).

### In Tasker

Use an **action** → *System* → *Send Intent*:

- **Action:** `com.tideo.autobrightness.control.LOAD_PROFILE`
- **Extra:** `name:Night` (Tasker's `key:value` extra form)
- **Target:** *Broadcast Receiver*
- **Package:** `com.tideo.autobrightness` · **Class:**
  `com.tideo.autobrightness.app.control.ControlReceiver`

### In MacroDroid

Use a **Send Intent** action, *Broadcast* type, with **Action**
`com.tideo.autobrightness.control.LOAD_PROFILE` and an extra `name` = `Night`.

### Reliability note for `SERVICE_ON`

When Tideo is **not already running**, Android's background-start rules can block a broadcast from
launching its foreground service (you'd see the service marked *degraded* on the Dashboard). If you
enable Tideo from automation, **exempt it from battery optimization** so the start is allowed
(see [dontkillmyapp.com](https://dontkillmyapp.com/)). All other commands are safe regardless.

---

## Events Tideo sends (outbound)

While external control is enabled, Tideo broadcasts its state so your automation can react to it.

**Action:** `com.tideo.autobrightness.event.STATE_CHANGED` (a global broadcast — register a receiver
for this action).

| Extra | Type | Meaning |
|---|---|---|
| `enabled` | Boolean | The service/pipeline is on |
| `running` | Boolean | Actively adjusting brightness (on **and** not paused) |
| `paused` | Boolean | Paused |
| `profile` | String (optional) | The profile currently in force, if any |

An event is emitted when the state changes — service on/off, pause/resume, and profile changes — and
a final `enabled=false` event is sent when the service stops. Events are only sent while external
control is enabled.

**Tasker:** a *Profile → Event → System → Intent Received* with action
`com.tideo.autobrightness.event.STATE_CHANGED`; the extras arrive as local variables
(`%enabled`, `%running`, `%paused`, `%profile`).

---

## Why did nothing happen?

Dropped commands are silent by design. To see the reason, set **Live Debug Info → Log Level** to
**8 — Context Automation**; Tideo then flashes why it ignored a command:

| Flash | Cause |
|---|---|
| *external control is off* | The opt-in toggle is off — turn on **Tools → Automation control** |
| *LOAD_PROFILE ignored — no "name" extra* | The action arrived without the `name` string extra |
| *LOAD_PROFILE ignored — no profile named "X"* | No saved or built-in profile matches that name (check spelling and case) |
| *RESUME ignored — the service is switched off* | `RESUME` never overrides the master switch (see the verb table above) |

**This list is not exhaustive — other causes are still silent.** An action Tideo does not recognise
is refused before anything else runs, so a mistyped action name produces nothing at all; check it
against the table above first. A command sent while another is still being handled is dropped by the
one-at-a-time admission gate — flood protection, not an error, so retry. And `PAUSE`, `REAPPLY`,
`PANIC` and `CONTEXTS_RESUME` can each be dropped downstream when the service is not running or
Android refuses the background start (see the reliability note above); those failures are not
reported here either. If a verb does nothing and none of the flashes appear, check that the service
is running and that Tideo is exempt from battery optimisation before suspecting the command itself.

Set the level back to **0 — Off** when you are done: while it is 8, any app on the device can raise
a toast by sending a control command.
