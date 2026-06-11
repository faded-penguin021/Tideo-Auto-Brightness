# Scene: AAB Brightness Settings

- XML line range: **L1415–2193** (`<Scene sr="sceneAAB Brightness Settings">`)
- Scene geom: 1440 x 2944 (portrait), gridSize 5
- Title banner var: `%AAB_SettingsBGone'General'%AAB_SettingsBGtwo` (WebElement `bg_banner_title`)
- Target M3 screen: **Curve & Brightness**

## Element count by type (top-level Scene children = 33)

| Type | Count |
|------|-------|
| WebElement | 1 |
| RectElement | 5 |
| TextElement | 19 |
| EditTextElement | 5 |
| SwitchElement | 2 |
| PropertiesElement | 1 |
| **Total** | **33** |

(Note: each Text/Edit/Switch/Web element also carries one nested `background` RectElement — those are intrinsic styling, not counted as scene elements. The raw `<RectElement sr=` grep returns 32 because it includes those nested backgrounds.)

## Elements (in document order)

| # | name (sr) | type | bound variable | value range / options | handler task(s) | purpose |
|---|-----------|------|----------------|-----------------------|-----------------|---------|
| elements0 | bg_banner_title | WebElement | `%AAB_SettingsBGone`/`%AAB_SettingsBGtwo` (title="General") | — | — | HTML banner/title background ("General" tab header) |
| elements1 | rectangle_3 | RectElement | — | — | — | Decorative card bg (#FF404040, the curve-coeffs panel) |
| elements6 | form1a_label | TextElement | displays `%AAB_Form1A` | — | longclick=725 | Label "Zone 1 Scaling" + current value; longtap=help/edit |
| elements7 | form1a | EditTextElement | `%aab_form1a` | numeric decimal (arg8=9) | valueselected=734, focuschange=613 | Edit Zone 1 scaling coefficient (form1A) |
| elements15 | form2d_label | TextElement | displays `%AAB_Zone1End` | — | longclick=747 | Label "Zone 1 End" + value; longtap help |
| elements20 | End_zone_1 | EditTextElement | `%aab_zone1end` | numeric decimal | valueselected=752, focuschange=614 | Edit Zone 1 end boundary lux |
| elements9 | form2a_label | TextElement | displays `%AAB_Form2A` | — | longclick=736 | Label "Zone 2 Align"; longtap help |
| elements10 | Form2a_calculated | TextElement | displays `%aab_form2a` ("(auto)") | — | — | Read-only derived form2A continuity value (auto) |
| elements11 | form2b_label | TextElement | displays `%AAB_Form2B` | — | longclick=737 | Label "Zone 2 Scaling"; longtap help |
| elements12 | form2b | EditTextElement | `%aab_form2b` | numeric decimal | valueselected=739, focuschange=615 | Edit Zone 2 scaling coefficient (form2B) |
| elements13 | form2c_label | TextElement | displays `%AAB_Form2C` | — | longclick=741 | Label "Zone 2 Offset"; longtap help |
| elements14 | form2c | EditTextElement | `%aab_form2c` | numeric decimal | valueselected=744, focuschange=616 | Edit Zone 2 offset coefficient (form2C) |
| elements18 | end_zone_2 label | TextElement | displays `%AAB_Zone2End` | — | longclick=750 | Label "Zone 2 End"; longtap help |
| elements19 | End_zone_2 | EditTextElement | `%aab_zone2end` | numeric decimal | valueselected=751, focuschange=617 | Edit Zone 2 end boundary lux |
| elements16 | form3a_label | TextElement | displays `%AAB_Form3A` | — | longclick=749 | Label "Zone 3 Align"; longtap help |
| elements17 | Form3a_calculated | TextElement | displays `%aab_form3a` ("(auto)") | — | — | Read-only derived form3A continuity value (auto) |
| elements4 | rectangle_6 | RectElement | — | — | — | Decorative card bg for the main-service-toggle row |
| elements21 | switch_description | TextElement | static "Main service toggle" | — | — | Static label for the service on/off switch |
| elements29 | Switch | SwitchElement | service state (off-state, default 0) | bool | checkchange=397 | Toggle main service OFF (off-overlay switch) |
| elements30 | Switch2 | SwitchElement | service state (on-state, default 1) | bool | checkchange=402 | Toggle main service ON (on-overlay switch) |
| elements31 | Service_on_green | RectElement | — | — | — | Green "service on" indicator dot (#FF007C63) |
| elements8 | rectangle_4 | RectElement | — | — | — | Decorative card bg for live-status panel |
| elements22 | current_lux_and_bright | TextElement | displays `%SmoothedLux`, `%AAB_MinBright`, `%AAB_MaxBright`, `%AAB_CurrentBright` | — | longclick=383 | Live readout: current smoothed lux + current brightness; longtap action |
| elements2 | rectangle_5 | RectElement | — | — | — | Decorative card bg for bottom button bar |
| elements3 | draw_graph_drop_shadow | TextElement | static (empty) | — | — | Drop-shadow layer behind "Draw Brightness Graph" |
| elements5 | draw_graph | TextElement | static "Draw Brightness Graph" | — | click=724 | Button: open the brightness graph scene |
| elements23 | save_button_drop_shadow | TextElement | static (empty) | — | — | Drop-shadow layer behind Apply button |
| elements24 | save_button | TextElement | static "Apply" | — | click=386 | Button: apply/save settings (recompute formulae) |
| elements25 | exit_scenes_drop_shadow | TextElement | static "Exit" | — | — | Drop-shadow layer behind Exit button |
| elements26 | exit_scenes | TextElement | static "Exit" | — | click=391 | Button: close scene |
| elements27 | undo_button_drop_shadow | TextElement | static "Reset defaults" | — | — | Drop-shadow layer behind Reset button |
| elements28 | undo_button | TextElement | static "Reset tab defaults" | — | click=396 | Button: reset this tab's settings to defaults |
| props | props | PropertiesElement | — | scene props (overlay=2) | — | Scene properties (background #FF000000) |

### Notes
- The `Switch`/`Switch2` pair share identical geom (955,351,375,225) — Tasker overlays two switches to render a bound ON/OFF state; one is shown depending on `%AAB_ServiceEnabled`-style state, with `Service_on_green` as the lit indicator. In M3 this collapses to a single bound toggle.
- All EditText fields have a `focuschangeTask` (613–617) that fires validation/reformat on focus loss, and a `valueselectedTask` that commits the value. Both feed task659 `_UpdateBrightnessFormulae` (form2A/form3A are derived, see CLAUDE.md).
- `Form2a_calculated` / `Form3a_calculated` are read-only derived-coefficient displays, NOT editable settings.

## Disposition

| element | disposition |
|---------|-------------|
| elements0 (bg_banner_title) | dropped(scene chrome) |
| elements1 (rectangle_3) | dropped(scene chrome) |
| elements6 (form1a_label) | merged-into Curve & Brightness |
| elements7 (form1a) | kept-as Curve & Brightness |
| elements15 (form2d_label / Zone1End label) | merged-into Curve & Brightness |
| elements20 (End_zone_1) | kept-as Curve & Brightness |
| elements9 (form2a_label) | merged-into Curve & Brightness |
| elements10 (Form2a_calculated) | kept-as Curve & Brightness |
| elements11 (form2b_label) | merged-into Curve & Brightness |
| elements12 (form2b) | kept-as Curve & Brightness |
| elements13 (form2c_label) | merged-into Curve & Brightness |
| elements14 (form2c) | kept-as Curve & Brightness |
| elements18 (end_zone_2 label) | merged-into Curve & Brightness |
| elements19 (End_zone_2) | kept-as Curve & Brightness |
| elements16 (form3a_label) | merged-into Curve & Brightness |
| elements17 (Form3a_calculated) | kept-as Curve & Brightness |
| elements4 (rectangle_6) | dropped(scene chrome) |
| elements21 (switch_description) | merged-into Curve & Brightness |
| elements29 (Switch) | merged-into Dashboard |
| elements30 (Switch2) | merged-into Dashboard |
| elements31 (Service_on_green) | dropped(scene chrome) |
| elements8 (rectangle_4) | dropped(scene chrome) |
| elements22 (current_lux_and_bright) | merged-into Curve & Brightness |
| elements2 (rectangle_5) | dropped(scene chrome) |
| elements3 (draw_graph_drop_shadow) | dropped(scene chrome) |
| elements5 (draw_graph) | merged-into Curve & Brightness |
| elements23 (save_button_drop_shadow) | dropped(scene chrome) |
| elements24 (save_button) | merged-into Curve & Brightness |
| elements25 (exit_scenes_drop_shadow) | dropped(scene chrome) |
| elements26 (exit_scenes) | dropped(scene chrome) |
| elements27 (undo_button_drop_shadow) | dropped(scene chrome) |
| elements28 (undo_button) | merged-into Curve & Brightness |
| props | dropped(scene chrome) |
