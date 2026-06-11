# Scene: AAB Experiment Settings

- XML line range: **L3334–4461** (`<Scene sr="sceneAAB Experiment Settings">`)
- Scene geom: 1440 x 2944 (portrait)
- Title banner var: `%AAB_SettingsBGone'Circadian'%AAB_SettingsBGtwo` (WebElement `bg_banner_title`)
- Title: PropertiesElement `props`, keyTask 597
- Note: despite the scene name "Experiment", the in-app tab label is **"Circadian"**. Covers circadian scaling, dynamic-scale compression (taper), QS tile, notifications, and a date/location picker.

## Element count by type (top-level Scene children = 40)

| Type | Count |
|------|-------|
| WebElement | 2 |
| RectElement | 8 |
| TextElement | 20 |
| EditTextElement | 4 |
| SliderElement | 1 |
| SwitchElement | 4 |
| ButtonElement | 1 |
| PropertiesElement | 1 |
| **Total** | **41** |

(The raw `<RectElement sr=` grep returns 37 because it also counts the nested `background` Rect inside each non-Rect element. Standalone decorative/status Rects: elements1, 2, 20, 21, 24, 29, 34 plus card Rects = 8.)

## Elements (in document order)

| # | name (sr) | type | bound variable | value range / options | handler task(s) | purpose |
|---|-----------|------|----------------|-----------------------|-----------------|---------|
| elements0 | bg_banner_title | WebElement | `%AAB_SettingsBGone`/`%AAB_SettingsBGtwo` (title="Circadian") | — | — | HTML banner/title background ("Circadian" tab header) |
| elements1 | rectangle_10 | RectElement | — | — | — | Decorative card bg (bottom buttons panel) |
| elements2 | rectangle_3 | RectElement | — | — | — | Decorative card bg (circadian scaling panel) |
| elements3 | spread_label | TextElement | displays `%AAB_ScaleSpread` | — | longclick=658 | Label "Spread" + value; longtap help |
| elements4 | scale | EditTextElement | `%aab_scalespread` | numeric decimal | valueselected=664 | Edit circadian scale spread |
| elements5 | exit_scenes_drop_shadows | TextElement | — ("Exit") | — | — | Drop-shadow underlay for Exit (decorative) |
| elements6 | save_button_drop_shadow | TextElement | — (blank) | — | — | Drop-shadow underlay for Apply (decorative) |
| elements7 | save_button | TextElement | — ("Apply") | — | click=665 | Apply/save Circadian settings |
| elements8 | exit_scenes | TextElement | — ("Exit") | — | click=666, longclick=667 | Exit scene; longtap alt action (667) |
| elements9 | draw_graph4_drop_shadow | TextElement | — ("Draw Circadian Graph") | — | — | Drop-shadow underlay for graph button (decorative) |
| elements10 | draw_graph4 | TextElement | — ("Draw Circadian Graph") | — | click=674 | Render circadian graph |
| elements11 | undo_button_drop_shadow | TextElement | — ("Reset tab defaults") | — | — | Drop-shadow underlay for Reset (decorative) |
| elements12 | undo_button | TextElement | — ("Reset tab defaults") | — | click=675 | Reset this tab to defaults |
| elements13 | transition_factor_label | TextElement | displays `%AAB_ScaleTransitionFactor` | — | longclick=678 | Label "Transit factor" + value; longtap help |
| elements14 | curve_steepness_label | TextElement | displays `%AAB_ScaleSteepness` | — | longclick=662 | Label "Steepness" + value; longtap help |
| elements15 | transition | EditTextElement | `%aab_scaletransitionfactor` | numeric decimal | valueselected=539 | Edit transition factor |
| elements16 | steepness | EditTextElement | `%aab_scalesteepness` | numeric decimal | valueselected=660 | Edit curve steepness |
| elements17 | Switch1 | SwitchElement | circadian-scaling toggle (off-state) | on/off | checkchange=677 | Use-circadian-scaling switch (handler 677) |
| elements18 | experimental_label | TextElement | displays `%AAB_ScalingUse` | — | longclick=672 | Label "Use circadian scaling" + value; longtap help |
| elements19 | Switch2 | SwitchElement | circadian-scaling toggle (on-state) | on/off | checkchange=676 | Circadian switch on-state (paired w/ Switch1, handler 676) |
| elements20 | Experimental_on_green | RectElement | `%AAB_ScalingUse` indicator | — | — | Green status dot for circadian scaling |
| elements21 | rectangle_7 | RectElement | — | — | — | Decorative card bg (compression-graph panel) |
| elements22 | draw_graph5_drop_shadow | TextElement | — ("Draw Compression Graph") | — | — | Drop-shadow underlay for graph button (decorative) |
| elements23 | draw_graph5 | TextElement | — ("Draw Compression Graph") | — | click=686 | Render compression graph |
| elements24 | rectangle_5 | RectElement | — | — | — | Decorative card bg (taper panel) |
| elements25 | taper_midpoint_label | TextElement | displays `%AAB_ScaleTaperMidpoint` | — | longclick=687 | Label "Taper mid" + value; longtap help |
| elements26 | taper midpoint | SliderElement | `%AAB_ScaleTaperMidpoint` | 130–240 | valueselected=689 | Set taper midpoint (compression curve) |
| elements27 | taper_slope_label | TextElement | displays `%AAB_ScaleTaperSteepness` | — | longclick=690 | Label "Taper slope" + value; longtap help |
| elements28 | taper_scalesteepness | EditTextElement | `%aab_scaletapersteepness` | numeric decimal | valueselected=691 | Edit taper steepness/slope |
| elements29 | rectangle_6 | RectElement | — | — | — | Decorative card bg (uncompressed/true scale readout) |
| elements30 | maximum_scalespread_and_true_scalespread | TextElement | displays `%AAB_ScaleDynamic`, `%TIME`, `%AAB_ScaleDynamicCompress`, `%AAB_CurrentBright`, `%AAB_MinBright`, `%AAB_MaxBright` | — | longclick=694 | Read-only live readout: uncompressed scale @ time + true compressed scale @ brightness; longtap help |
| elements31 | qs_label | TextElement | displays `%AAB_QSUse` | — | longclick=688 | Label "Use quick settings tile" + value; longtap help |
| elements32 | Switch3 | SwitchElement | QS-tile toggle (on-state) | on/off | checkchange=697 | Quick-settings-tile switch (handler 697) |
| elements33 | Switch4 | SwitchElement | QS-tile toggle (off-state) | on/off | checkchange=699 | QS-tile switch off-state (paired, handler 699) |
| elements34 | QS_on_green | RectElement | `%AAB_QSUse` indicator | — | — | Green status dot for QS tile |
| elements35 | date_label | TextElement | displays date/location ("[unset]") | — | longclick=670 | Label "Date & loc." + status; longtap help |
| elements36 | date_button | TextElement | — ("Tap to set") | — | click=683 | Open date/location picker |
| elements37 | date picker | WebElement | `%AAB_Latitude`, `%AAB_Longitude`, `%AAB_Date` | HTML date+lat/lon inputs | `performTask('_ExperimentSetDate',…)` / `_ExperimentClearDate` (Live Data) | HTML modal to set fixed date + lat/lon (or revert to live data) |
| elements38 | Button1 | ButtonElement | — | 16–100 | click=681 | (Hidden/utility button range 16–100, handler 681) |
| props | props | PropertiesElement | — | — | keyTask=597; LinkClickFilter `back` | Scene properties: back filter, key task 597 |

## Disposition

(Experiment Settings → Tools per brief. Circadian/dynamic-scale compression logic is the page's substance; functional toggles that map to existing screens are merged there, the rest land in Tools.)

- elements0 — dropped(HTML banner replaced by M3 top app bar)
- elements1 — dropped(decorative card bg)
- elements2 — dropped(decorative card bg)
- elements3 — merged-into Tools
- elements4 — merged-into Tools
- elements5 — dropped(decorative drop-shadow)
- elements6 — dropped(decorative drop-shadow)
- elements7 — merged-into Tools
- elements8 — dropped(nav replaced by M3 nav)
- elements9 — dropped(decorative drop-shadow)
- elements10 — merged-into Tools
- elements11 — dropped(decorative drop-shadow)
- elements12 — merged-into Tools
- elements13 — merged-into Tools
- elements14 — merged-into Tools
- elements15 — merged-into Tools
- elements16 — merged-into Tools
- elements17 — merged-into Tools
- elements18 — merged-into Tools
- elements19 — dropped(decorative switch on-state pair)
- elements20 — dropped(decorative status dot)
- elements21 — dropped(decorative card bg)
- elements22 — dropped(decorative drop-shadow)
- elements23 — merged-into Tools
- elements24 — dropped(decorative card bg)
- elements25 — merged-into Tools
- elements26 — merged-into Tools
- elements27 — merged-into Tools
- elements28 — merged-into Tools
- elements29 — dropped(decorative card bg)
- elements30 — merged-into Tools
- elements31 — merged-into Tools
- elements32 — merged-into Tools
- elements33 — dropped(decorative switch off-state pair)
- elements34 — dropped(decorative status dot)
- elements35 — merged-into Tools
- elements36 — merged-into Tools
- elements37 — merged-into Tools
- elements38 — dropped(hidden utility button)
- props — dropped(nav replaced by M3 nav)
