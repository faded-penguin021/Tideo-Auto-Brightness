# Scene: AAB Misc Settings

- XML line range: **L4718–5610** (`<Scene sr="sceneAAB Misc Settings">`)
- Scene geom: 1440 x 2944 (portrait), gridSize 5
- Title banner var: `%AAB_SettingsBGone'Misc'%AAB_SettingsBGtwo` (WebElement `bg_banner_title`)
- Title: "AAB Settings" (PropertiesElement `props`, keyTask 598)

## Element count by type (top-level Scene children = 38)

| Type | Count |
|------|-------|
| WebElement | 1 |
| RectElement | 8 |
| TextElement | 20 |
| EditTextElement | 3 |
| SliderElement | 5 |
| SwitchElement | 2 |
| PropertiesElement | 1 |
| **Total** | **40** |

(The raw `<RectElement sr=` grep returns 37 because it also counts the nested `background` Rect inside each Text/Edit/Slider/Switch/Web element — those are intrinsic styling, not scene elements. Standalone decorative Rects here: elements1, 2, 15, 17, 24, 25, 37 = 7, plus the green dot 37; card Rects total 8.)

## Elements (in document order)

| # | name (sr) | type | bound variable | value range / options | handler task(s) | purpose |
|---|-----------|------|----------------|-----------------------|-----------------|---------|
| elements0 | bg_banner_title | WebElement | `%AAB_SettingsBGone`/`%AAB_SettingsBGtwo` (title="Misc") | — | — | HTML banner/title background ("Misc" tab header) |
| elements1 | rectangle_10 | RectElement | — | — | — | Decorative card bg (#FF404040, the bottom buttons / debug panel area) |
| elements2 | rectangle_3 | RectElement | — | — | — | Decorative card bg (top brightness/scale/offset panel) |
| elements3 | min_bright_label | TextElement | displays `%AAB_MinBright` | — | longclick=723 | Label "Min Brightness" + value; longtap help |
| elements4 | Minimum Brightness Slider | SliderElement | `%AAB_MinBright` | 0–75 | valueselected=395 | Set minimum brightness floor |
| elements5 | max_bright_label | TextElement | displays `%AAB_MaxBright` | — | longclick=536 | Label "Max Brightness" + value; longtap help |
| elements6 | Maximum Brightness | SliderElement | `%AAB_MaxBright` | 150–255 | valueselected=390 | Set maximum brightness ceiling |
| elements7 | offset_label | TextElement | displays `%AAB_Offset` | — | longclick=732 | Label "Offset" + value; longtap help |
| elements8 | scale_label | TextElement | displays `%AAB_Scale` | — | longclick=730 | Label "Scale" + value; longtap help |
| elements9 | offset | EditTextElement | `%aab_offset` | numeric decimal (arg8=4) | valueselected=409 | Edit brightness offset |
| elements10 | scale | EditTextElement | `%aab_scale` | numeric decimal (arg8=4) | valueselected=407 | Edit scale value |
| elements11 | exit_scenes_drop_shadows | TextElement | — ("Exit") | — | — | Drop-shadow underlay for Exit button (decorative) |
| elements12 | save_button_drop_shadow | TextElement | — (blank) | — | — | Drop-shadow underlay for Apply button (decorative) |
| elements13 | save_button | TextElement | — ("Apply") | — | click=403 | Apply/save Misc settings |
| elements14 | exit_scenes | TextElement | — ("Exit") | — | click=417, longclick=538 | Exit scene; longtap=alt action (538) |
| elements15 | rectangle_6 | RectElement | — | — | — | Decorative card bg (animation-steps panel) |
| elements16 | Anim_steps_label | TextElement | displays `%AAB_AnimSteps` | — | longclick=726 | Label "Animation Steps" + value; longtap help |
| elements17 | rectangle_8 | RectElement | — | — | — | Decorative card bg (notifications row) |
| elements18 | Max_wait_label | TextElement | displays `%AAB_MaxWait` (ms) | — | longclick=728 | Label "Max wait" + value; longtap help |
| elements19 | min_wait_label | TextElement | displays `%AAB_MinWait` (ms) | — | longclick=727 | Label "Min wait" + value; longtap help |
| elements20 | Animation Steps | SliderElement | `%AAB_AnimSteps` | 0–100 | valueselected=714 | Set number of animation steps |
| elements21 | Undo_drop_shadow | TextElement | — ("Reset tab defaults") | — | — | Drop-shadow underlay for Reset button (decorative) |
| elements22 | Minimum wait | SliderElement | `%AAB_MinWait` | 1–99 | valueselected=715 | Set minimum wait (ms) between updates |
| elements23 | Maximum wait | SliderElement | `%AAB_MaxWait` | 2–100 | valueselected=716 | Set maximum wait (ms) between updates |
| elements24 | rectangle_7 | RectElement | — | — | — | Decorative card bg (smoothing-delta panel) |
| elements25 | rectangle_4 | RectElement | — | — | — | Decorative card bg (current throttle/alpha readout) |
| elements26 | delta_factor_label | TextElement | displays `%AAB_DeltaFactor` | — | longclick=740 | Label "Smoothing Δ" + value; longtap help |
| elements27 | draw_graph3_drop_shadow | TextElement | — ("Draw Alpha Graph") | — | — | Drop-shadow underlay for graph button (decorative) |
| elements28 | delta_factor | EditTextElement | `%aab_deltafactor` | numeric decimal (arg8=4) | valueselected=742, focuschange=612 | Edit smoothing delta factor |
| elements29 | draw_graph3 | TextElement | — ("Draw Alpha Graph") | — | click=743 | Render the alpha/smoothing graph |
| elements30 | undo_button | TextElement | — ("Reset tab defaults") | — | click=738 | Reset this tab to defaults |
| elements31 | current_throttle_and_alpha | TextElement | displays `%AAB_Throttle`, `%LuxAlpha` | — | longclick=684 | Read-only live readout: current throttle (ms) + smoothing α; longtap help |
| elements32 | scale_dynamic | TextElement | displays `%AAB_ScaleDynamicCompress` ("(auto)") | — | — | Read-only derived dynamic-scale value (auto) |
| elements33 | scale_dynamic_label | TextElement | displays `%AAB_ScaleDynamic` | — | longclick=405 | Label "Dynamic Scale" + value; longtap help |
| elements34 | notify_label | TextElement | displays `%AAB_NotifyUse` | — | longclick=682 | Label "Use notifications" + value; longtap help |
| elements35 | Switch2 | SwitchElement | notifications toggle (off-state) | on/off | checkchange=695 | Notifications switch (handler 695) |
| elements36 | Switch1 | SwitchElement | notifications toggle (on-state) | on/off | — | Notifications switch visual on-state (paired with Switch2) |
| elements37 | Notify_on_green | RectElement | `%AAB_NotifyUse` indicator | — | click=693 | Green status dot for notifications; tap toggles (693) |
| props | props | PropertiesElement | — | — | keyTask=598; LinkClickFilter `back` | Scene properties: title "AAB Settings", back filter, key task 598 |

## Disposition

(Misc Settings is split across Dashboard / Tools / Profiles per element meaning. Min/Max/Offset/Scale brightness controls belong to **Curve & Brightness**; AnimSteps/wait/delta to **Animation & Dimming** + **Reactivity**; DynamicScale to **Dynamic Scale**; notifications to **Dashboard**; graph/reset/apply to **Tools**.)

- elements0 — dropped(HTML banner replaced by M3 top app bar)
- elements1 — dropped(decorative card bg)
- elements2 — dropped(decorative card bg)
- elements3 — merged-into Curve & Brightness
- elements4 — kept-as Curve & Brightness
- elements5 — merged-into Curve & Brightness
- elements6 — kept-as Curve & Brightness
- elements7 — merged-into Curve & Brightness
- elements8 — merged-into Curve & Brightness
- elements9 — kept-as Curve & Brightness
- elements10 — kept-as Curve & Brightness
- elements11 — dropped(decorative drop-shadow)
- elements12 — dropped(decorative drop-shadow)
- elements13 — merged-into Tools
- elements14 — dropped(nav replaced by M3 nav)
- elements15 — dropped(decorative card bg)
- elements16 — merged-into Animation & Dimming
- elements17 — dropped(decorative card bg)
- elements18 — merged-into Reactivity
- elements19 — merged-into Reactivity
- elements20 — kept-as Animation & Dimming
- elements21 — dropped(decorative drop-shadow)
- elements22 — kept-as Reactivity
- elements23 — kept-as Reactivity
- elements24 — dropped(decorative card bg)
- elements25 — dropped(decorative card bg)
- elements26 — merged-into Reactivity
- elements27 — dropped(decorative drop-shadow)
- elements28 — kept-as Reactivity
- elements29 — merged-into Tools
- elements30 — merged-into Tools
- elements31 — merged-into Dashboard
- elements32 — merged-into Dynamic Scale
- elements33 — merged-into Dynamic Scale
- elements34 — merged-into Dashboard
- elements35 — kept-as Dashboard
- elements36 — dropped(decorative switch on-state pair)
- elements37 — merged-into Dashboard
- props — dropped(nav replaced by M3 nav)
