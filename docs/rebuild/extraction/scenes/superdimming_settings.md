# Scene: AAB Superdimming Settings

- XML line range: **L7533–8386** (`<Scene sr="sceneAAB Superdimming Settings">`)
- Scene geom: 1440 x 2944 (portrait), gridSize 5
- Title banner var: `%AAB_SettingsBGone'Superdimming'%AAB_SettingsBGtwo` (WebElement `bg_banner_title`)
- Target M3 screen: **Animation & Dimming**

## Element count by type (top-level Scene children = 38)

| Type | Count |
|------|-------|
| WebElement | 1 |
| RectElement | 6 |
| TextElement | 20 |
| EditTextElement | 5 |
| SwitchElement | 4 |
| PropertiesElement | 1 |
| **Total** | **38** |

(Note: each Text/Edit/Switch/Web element also carries one nested `background` RectElement — intrinsic styling, not counted. The raw `<RectElement sr=` grep returns 36 because it includes those nested backgrounds.)

## Elements (in document order)

| # | name (sr) | type | bound variable | value range / options | handler task(s) | purpose |
|---|-----------|------|----------------|-----------------------|-----------------|---------|
| elements0 | bg_banner_title | WebElement | `%AAB_SettingsBGone`/`%AAB_SettingsBGtwo` (title="Superdimming") | — | — | HTML banner/title background ("Superdimming" tab header) |
| elements2 | rectangle_3 | RectElement | — | — | — | Decorative card bg (#FF404040, super-dimming params panel, y=384) |
| elements3 | Strength_label | TextElement | displays `%AAB_DimmingStrength` | — | longclick=465 | Label "Strength setpoint" + value; longtap=help (`_ShowUserGuide`) |
| elements29 | strength | EditTextElement | `%aab_dimmingstrength` | numeric | valueselected=608, focuschange=607 | Edit super-dimming strength setpoint |
| elements13 | dim_spread_label | TextElement | displays `%AAB_DimSpread` | — | longclick=505 | Label "Spread" + value; longtap help |
| elements28 | spread | EditTextElement | `%aab_dimspread` | numeric | valueselected=522, focuschange=523 | Edit dimming spread |
| elements14 | Dimming_Exponent_label | TextElement | displays `%AAB_DimmingExponent` | — | longclick=506 | Label "SD Exponent" + value; longtap help |
| elements15 | steepness | EditTextElement | `%aab_dimmingexponent` | numeric | valueselected=508, focuschange=609 | Edit super-dimming exponent |
| elements24 | Dimming_Threshold_label | TextElement | displays `%AAB_DimmingThreshold` | — | longclick=421 | Label "Threshold" + value; longtap=`_EvaluateContexts V2`/help |
| elements36 | dimming threshold | EditTextElement | `%aab_dimmingthreshold` | numeric | valueselected=531, focuschange=610 | Edit super-dimming activation threshold |
| elements16 | Switch1 | SwitchElement | `%AAB_DimmingEnabled` (on-overlay) | bool | checkchange=509 | Use-super-dimming toggle ON half (geom y=879) |
| elements18 | Switch2 | SwitchElement | `%AAB_DimmingEnabled` (off-overlay) | bool | checkchange=511 | Use-super-dimming toggle OFF half |
| elements17 | superdimming_label | TextElement | displays `%AAB_DimmingEnabled` | — | longclick=510 | Label "Use super dimming" + value; longtap help |
| elements19 | Dimming_on_green | RectElement | — | — | — | Green "super dimming on" indicator dot (#FF007C63) |
| elements25 | rectangle_7 | RectElement | — | — | — | Decorative card bg for software-dimming band (y=1059) |
| elements26 | Privilege_label | TextElement | displays `%AAB_Privilege` | — | longclick=527 | Label "Privilege" + value; longtap=`_PanicButton`/help |
| elements27 | privilege_button | TextElement | static "Check privilege" | — | click=492 | Button: check/grant privilege tier (`_ShowUserGuide`) |
| elements30 | pwm_exponent | TextElement | displays `%AAB_PWMExp` | — | longclick=702 | Label "Software exp." + value; longtap=`_GenerateReactivityGraph`/help |
| elements31 | pwm exponent | EditTextElement | `%aab_pwmexp` | numeric | valueselected=530, focuschange=611 | Edit software-dimming (PWM) exponent |
| elements32 | pwm_label | TextElement | displays `%AAB_PWMSensitive` | — | longclick=529 | Label "Use software dimming" + value; longtap help |
| elements33 | Switch3 | SwitchElement | `%AAB_PWMSensitive` (on-overlay) | bool | checkchange=534 | Use-software-dimming toggle ON half (geom y=1359) |
| elements34 | Switch4 | SwitchElement | `%AAB_PWMSensitive` (off-overlay) | bool | checkchange=533 | Use-software-dimming toggle OFF half |
| elements35 | PWM_on_green | RectElement | — | — | — | Green "software dimming on" indicator dot (#FF007C63) |
| elements22 | rectangle_6 | RectElement | — | — | — | Decorative card bg for live-status panel (y=1534) |
| elements23 | abs_and_rel_super_dimming_effect | TextElement | displays `%AAB_DimmingCurrent`, `%AAB_DimmingDS`, `%AAB_CurrentBright` | — | longclick=514 | Live readout: current super-dimming strength (rel/abs) + brightness; longtap action |
| elements1 | rectangle_10 | RectElement | — | — | — | Decorative card bg for circadian/graph row (y=2034) |
| elements9 | draw_graph4_drop_shadow | TextElement | static "Draw Circadian Graph" | — | — | Drop-shadow layer behind "Draw Circadian Graph" |
| elements10 | draw_graph4 | TextElement | static "Draw Circadian Graph" | — | click=517 | Button: opens **AAB Circadian Dimming Graph** — task517 warns if `%aab_scaletransitionfactor>0.5`, clears `%AAB_SunLastDate`, runs task90 + task705 `_GenerateCircadianDimmingGraph`, resizes (task620), hides this scene. Shown only when `%AAB_ScalingUse` is enabled (owner). S3.5 fix — the earlier `_CalibratePowerDraw` gloss was wrong (D-026) |
| elements20 | draw_graph5_drop_shadow | TextElement | static "Draw Dimming Graph" | — | — | Drop-shadow layer behind "Draw Dimming Graph" |
| elements21 | draw_graph5 | TextElement | static "Draw Dimming Graph" | — | click=513 | Button: open dimming graph |
| elements5 | rectangle_9 | RectElement | — | — | — | Decorative card bg for bottom button bar (y=1234) |
| elements6 | save_button_drop_shadow | TextElement | static (empty) | — | — | Drop-shadow layer behind Apply button |
| elements7 | save_button | TextElement | static "Apply" | — | click=466 | Button: apply/save super-dimming settings |
| elements4 | exit_scenes_drop_shadows | TextElement | static "Exit" | — | — | Drop-shadow layer behind Exit button |
| elements8 | exit_scenes | TextElement | static "Exit" | — | click=481, longclick=490 | Button: close scene; longtap secondary action |
| elements11 | undo_button_drop_shadow | TextElement | static "Reset tab defaults" | — | — | Drop-shadow layer behind Reset button |
| elements12 | undo_button | TextElement | static "Reset tab defaults" | — | click=500 | Button: reset this tab's settings to defaults |
| props | props | PropertiesElement | — | scene props (overlay=2) | — | Scene properties (background #FF000000) |

### Notes
- Two switch pairs render bound ON/OFF toggles: `Switch1`/`Switch2` (geom y=879) = **Use super dimming** (`%AAB_DimmingEnabled`), `Switch3`/`Switch4` (geom y=1359) = **Use software dimming / PWM** (`%AAB_PWMSensitive`). Each collapses to one M3 toggle; `*_on_green` rects are the lit indicators.
- All EditText fields carry both a `valueselectedTask` (commit) and a `focuschangeTask` (607/609/610/611/523 — validation/reformat on focus loss).
- `Privilege_label` + `privilege_button` surface the tiered PrivilegeManager (BASIC/ELEVATED, CLAUDE.md). Super dimming requires ELEVATED (WRITE_SECURE_SETTINGS); this is the in-scene privilege check/grant entry point.
- Software dimming (PWM exponent) is the in-app dimming path that does not need ELEVATED; distinct from secure-settings super dimming.

## Disposition

| element | disposition |
|---------|-------------|
| elements0 (bg_banner_title) | dropped(scene chrome) |
| elements2 (rectangle_3) | dropped(scene chrome) |
| elements3 (Strength_label) | merged-into Animation & Dimming |
| elements29 (strength) | kept-as Animation & Dimming |
| elements13 (dim_spread_label) | merged-into Animation & Dimming |
| elements28 (spread) | kept-as Animation & Dimming |
| elements14 (Dimming_Exponent_label) | merged-into Animation & Dimming |
| elements15 (steepness) | kept-as Animation & Dimming |
| elements24 (Dimming_Threshold_label) | merged-into Animation & Dimming |
| elements36 (dimming threshold) | kept-as Animation & Dimming |
| elements16 (Switch1) | kept-as Animation & Dimming |
| elements18 (Switch2) | merged-into Animation & Dimming |
| elements17 (superdimming_label) | merged-into Animation & Dimming |
| elements19 (Dimming_on_green) | dropped(scene chrome) |
| elements25 (rectangle_7) | dropped(scene chrome) |
| elements26 (Privilege_label) | merged-into Animation & Dimming |
| elements27 (privilege_button) | merged-into Animation & Dimming |
| elements30 (pwm_exponent) | merged-into Animation & Dimming |
| elements31 (pwm exponent) | kept-as Animation & Dimming |
| elements32 (pwm_label) | merged-into Animation & Dimming |
| elements33 (Switch3) | kept-as Animation & Dimming |
| elements34 (Switch4) | merged-into Animation & Dimming |
| elements35 (PWM_on_green) | dropped(scene chrome) |
| elements22 (rectangle_6) | dropped(scene chrome) |
| elements23 (abs_and_rel_super_dimming_effect) | merged-into Animation & Dimming |
| elements1 (rectangle_10) | dropped(scene chrome) |
| elements9 (draw_graph4_drop_shadow) | dropped(scene chrome) |
| elements10 (draw_graph4) | merged-into Animation & Dimming |
| elements20 (draw_graph5_drop_shadow) | dropped(scene chrome) |
| elements21 (draw_graph5) | merged-into Animation & Dimming |
| elements5 (rectangle_9) | dropped(scene chrome) |
| elements6 (save_button_drop_shadow) | dropped(scene chrome) |
| elements7 (save_button) | merged-into Animation & Dimming |
| elements4 (exit_scenes_drop_shadows) | dropped(scene chrome) |
| elements8 (exit_scenes) | dropped(scene chrome) |
| elements11 (undo_button_drop_shadow) | dropped(scene chrome) |
| elements12 (undo_button) | merged-into Animation & Dimming |
| props | dropped(scene chrome) |
