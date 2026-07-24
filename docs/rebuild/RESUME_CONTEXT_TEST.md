# Resume-context fix — on-device test (DA-018, 1.8.1/vc19)

Debug build: package `com.tideo.autobrightness.debug`, label **Tideo AB (Debug)**. Run only ONE
variant's service at a time (D-128) — disable the release app's service first, or uninstall it.

## Setup (once)

1. Install the debug APK; open **Tideo AB (Debug)**.
2. Grant **Modify system settings** (WRITE_SETTINGS) when prompted; toggle the service **On**
   (Dashboard). Confirm the notification shows it monitoring.
3. Profiles screen → make sure at least two built-in profiles exist (e.g. **Default** and
   **Outdoors**). Restore factory profiles if the list is empty.
4. Contexts screen → add ONE easy-to-trigger rule, e.g.
   - **App rule** "Cinema" → target profile **Video Streaming**, trigger app = a video app you
     have installed (grant Usage Access if asked), OR
   - **Battery rule** "Saver" → target **Battery Saver**, trigger battery ≤ some % above your
     current level so it matches right now.

## Test A — a matching rule applies immediately on Resume

1. Make the rule's condition currently TRUE (open the app / be under the battery %).
2. Profiles screen → tap a *different* profile (e.g. **Outdoors**) → **Load**. The "Resume context
   automation" banner appears; Outdoors is now active.
3. Tap **Resume** on the banner.
   - ✅ **Expected:** within a moment the active profile switches to the **rule's** profile
     (Video Streaming / Battery Saver), the rule name shows as the active context, and the
     Curve & Brightness screen shows that profile's values.
   - ❌ **Old bug:** it stayed on Outdoors / flipped to "Default" and did not apply the rule.

## Test B — no match reverts to your last manually-loaded profile (in sync)

1. Make sure NO context rule currently matches (close the app / unplug or adjust battery).
2. Profiles screen → **Load** the **Outdoors** profile. Banner appears.
3. Tap **Resume**.
   - ✅ **Expected:** the active profile shows **Outdoors** (your last manual load) — NOT "Default"
     — and the Curve & Brightness / Reactivity screens show **Outdoors'** values. Indicator and
     settings agree.
   - ❌ **Old bug:** the label flipped to "Default" while the settings screens still showed
     Outdoors (stale / mismatched).

## Optional — external verb (if you use Tasker/MacroDroid)

With **External control** enabled (Tools), sending
`com.tideo.autobrightness.control.CONTEXTS_RESUME` behaves exactly like the banner's Resume.

Report back: for A and B, what the active-profile label said vs. what the settings screens showed.
