# Permission audit (S12.9c #9)

Every permission declared in `app/src/main/AndroidManifest.xml`, why it exists, the feature that needs
it, whether it is in v1 scope, whether it is removable, and the S14 recommendation. **Docs only — S14
decides actual removals / the release-grade permission set.**

| Permission | Use | Feature | v1 scope | Removable | S14 recommendation |
|---|---|---|---|---|---|
| `WRITE_SETTINGS` | write `Settings.System` screen brightness + brightness mode | Core pipeline (BASIC tier) | ✅ yes | ❌ no | **Keep.** Foundational; user-grantable. |
| `WRITE_SECURE_SETTINGS` | write `Settings.Secure` `reduce_bright_colors` (super dimming) | Super dimming (ELEVATED tier) | ✅ yes | ❌ no (feature-defining) | **Keep.** `signature\|privileged`; granted via ADB/Shizuku/root, not at runtime. `tools:ignore=ProtectedPermissions` is expected. |
| `POST_NOTIFICATIONS` | the ongoing FGS notification + override notification | Foreground service runtime | ✅ yes | ❌ no | **Keep.** Runtime-requested on API 33+; guarded by `< TIRAMISU` for 31/32. |
| `FOREGROUND_SERVICE` | run `AmbientMonitoringService` | Service runtime | ✅ yes | ❌ no | **Keep.** |
| `FOREGROUND_SERVICE_SPECIAL_USE` | the `specialUse` FGS subtype (continuous light monitoring) | Service runtime | ✅ yes | ❌ no | **Keep.** Verify the Play `specialUse` declaration/justification at release (S14). |
| `RECEIVE_BOOT_COMPLETED` | restart monitoring after reboot | `BootCompletedReceiver` | ✅ yes | ⚠️ optional | **Keep** (expected UX: auto-start on boot). |
| `PACKAGE_USAGE_STATS` | read the foreground app for per-app context rules | Context rules (app trigger) | ✅ yes (contexts) | ⚠️ yes, if per-app rules cut | **Keep.** appop; granted via the usage-access deep-link, not a dialog. Only meaningful when an app rule exists. |
| `ACCESS_COARSE_LOCATION` | context location gate + circadian sunrise/sunset | Contexts + circadian | ✅ yes | ⚠️ yes, if location features cut | **Keep**, but make optional/lazy (already requested in the Setup Location step). |
| `ACCESS_FINE_LOCATION` | precise location for the context location gate | Contexts | ✅ yes | ⚠️ yes | **Re-evaluate.** COARSE may suffice for the haversine gate radius; consider dropping FINE at S14 if precision isn't needed. |
| `ACCESS_BACKGROUND_LOCATION` | location reads from the FGS while UI backgrounded (API 29+) | Contexts + daily sun refresh | ✅ yes | ⚠️ yes | **Re-evaluate.** Heaviest Play-policy item. Confirm it's truly needed vs. foreground-only reads; document justification or drop at S14. |
| `VIBRATE` | panic S.O.S. morse pattern (task528 / prof769) | Panic button | ✅ yes | ⚠️ optional | **Keep** (cheap, normal permission). |
| `INTERNET` | geo-IP location fallback (`ip-api.com`) when no Android fix (task90 act28) | Circadian location fallback | ✅ yes | ⚠️ yes | **Keep** (owner-binding cleartext fallback, D-069), but document the cleartext exception clearly for review. |
| `DUMP` | — (intentionally **NOT** declared) | no-Location SSID `dumpsys wifi` | n/a | n/a | **Leave undeclared.** `signature\|privileged`, ungrantable; the SSID path falls through to the next strategy without it. |

## Notes

- No `MANAGE_EXTERNAL_STORAGE` / broad storage permission — legacy config import uses SAF (a folder
  grant), so none is needed (G2R-F16).
- The heaviest Play-policy reviews at release will be `*_LOCATION` (esp. background) and the
  `specialUse` FGS justification — flagged above for S14.
- `<queries>` LAUNCHER (not a permission) backs the per-app context-rule picker (G2-F14).
