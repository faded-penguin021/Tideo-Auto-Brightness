# screen_map.md — Scene → M3 Screen consolidation matrix (S2)

THE consolidation matrix: 20 Tasker scenes / **450 elements** → target Material 3 screens.
Built by S2 from `extraction/scenes/*.md` (per-scene element tables) and the four
`extraction/scenes/_disp_group*.md` disposition fragments. Every one of the 450 raw
`<*Element sr="...">` entries in the XML scene region (L799–L8868) carries exactly one
disposition row below. Functional elements map to a target screen; nested `background`
rects and `PropertiesElement` scene-chrome are dropped (replaced by the M3 Scaffold/surface).

## Target M3 screen set

> **S12.5b update (G2-F2):** the S2 plan folded Misc-scene fields into other screens; Gate 2 judged
> that grouping wrong vs Tasker. A dedicated **Misc** screen is re-added — it owns the Misc scene's
> brightness range (min/max/offset/scale), animation (steps/min/max wait + derived throttle),
> notifications and the debug selector. Curve & Brightness keeps only the curve-zone coefficients;
> Animation & Dimming keeps only super-dimming + PWM. The 9→**10** target screens still honour the
> owner-approved 20→consolidation; this is a grouping correction within it, not a new scene.

> **S12.5c update (feature/behaviour fidelity, no new screens):** Contexts gains a faithful rule
> editor (`<queries>` launcher app list with icons, "use current Wi-Fi" helper, SUNRISE/SUNSET time
> tokens, usage-access prompt when an app trigger is set); Tools surfaces the `%AAB_Test` wizard
> diagnostics → clipboard (D-025); the Misc debug selector now drives runtime debug **toasts** for the
> 10 `%AAB_Debug` categories (D-023); confirmations/warnings toast across screens (G2-F12); the QS
> tile shows the live paused/running state (G2-F17). Behaviour-only — the screen set is unchanged.

> **S12.6a update (IA + naming, G2R-F1/F2/F3/F4):** the **AAB Menu is now a real home screen** (hub +
> back-target; route `menu`) — the S12.5a nav drawer was promoted into it, and the Profiles/Contexts
> hero cards moved off the Dashboard onto the Menu. The Dashboard is now a focused live-status screen
> reached from the Menu (back → Menu, not Dashboard). Two screens are **renamed** to the Tasker names:
> `Animation & Dimming` → **Super Dimming** (`super_dimming`), `Dynamic Scale` → **Circadian** (`circadian`).

| Screen | Source scenes | Charts |
|---|---|---|
| **Menu** (home hub) | AAB Menu scene (banner + grouped nav + Profiles/Contexts hero cards) | — |
| **Dashboard** (live status) | Misc Settings (service switch), live pipeline readout | — |
| **Curve & Brightness** | Brightness Settings (curve coefficients), Brightness Graph | BrightnessCurveChart |
| **Reactivity** | Reactivity Settings, Reactivity Graph, Alpha Graph | ReactivityChart |
| **Super Dimming** | Superdimming Settings, Color Filter, Dimming/Circadian Graphs | DimmingChart, CircadianChart |
| **Circadian** | Experiment Settings, Experiment Graph, Taper Graph | ExperimentChart, TaperChart |
| **Misc** | Misc Settings (min/max/offset/scale, anim steps + waits + throttle, notifications) | — |
| **Live Debug Info** | AAB Debug Scene (live %AAB_* glass-box readout + global debug-category selector, S12.6b) | — |
| **Contexts** | (rules surfaced from contexts_spec — no dedicated Tasker scene; editor lives in AAB Profile) | — |
| **Tools** | Debug Scene power-draw calibration, Power Draw Graph, (wizard from Brightness Graph 'Suggest', calibration from task524) | PowerDrawChart |
| **Profiles & Import/Export** | AAB Profile (profile manager + context-rule editor) | — |
| **About+Guide+Onboarding** | AAB About, AAB User Guide, privilege onboarding | — |
| _(dropped)_ | AAB Chart.Js License (Chart.js removed) | — |

Every Chart.js WebElement maps to a named Compose-Canvas chart (built S12/S13). Mapping:

| Scene | HTML var | Generator task | Series / axes | Compose chart |
|---|---|---|---|---|
| AAB Brightness Graph | %AAB_HTML_Graph | task663 | log lux (0.1→100k) → level 0–255, series: Curve (live draft) + dashed-gold Reference (FIXED committed snapshot, F69) + Suggested (wizard fit, ≥9 pts) + tappable override scatter; legend + interactive scrub readout + nice y-ticks/axis labels (S12.7g: F36/F55/F62/F66/F69) | BrightnessCurveChart |
| AAB Reactivity Graph | %AAB_HTML_Graph2 | task703 | log lux (39 pts) → threshold % 0–100, 2 series (new, reference) | ReactivityChart |
| AAB Alpha Graph | %AAB_HTML_Graph3 | task557 | lux-delta (39 pts) → smoothing alpha 0–1, 2 series | ReactivityChart (alpha overlay) |
| AAB Dimming Graph | %AAB_HTML_Graph6 | task556 | brightness level → dim % 0–100, 3 series (dim, reference, shell) | DimmingChart |
| AAB Circadian Dimming Graph | %AAB_HTML_Graph7 | task705 | time 00–24h → dim multiplier, 1 series + sun markers | CircadianChart |
| AAB Taper Graph | %AAB_HTML_Graph5 | task657 (compression) | brightness level → scale multiplier ~1.0, 2 series (day, night) | TaperChart |
| AAB Experiment Graph | %AAB_HTML_Graph4 | task549 (circadian/scaling) | time 00–24h → scaling multiplier, 1 series + sun markers | ExperimentChart |
| AAB Power Draw Graph | %AAB_HTML_Graph8 | task524 | brightness 0–255 → power(W)+current(mA) dual-axis, 2 measured series | PowerDrawChart |

> Note: Experiment Graph reuses generator task549 (circadian/scaling, `HTML_Graph4`) and
> Taper Graph reuses task657 (compression, `HTML_Graph5`) — generators are not 1:1 with scene names.

## Per-scene element dispositions (all 450 rows)

### AAB Menu  ·  `menu`  ·  XML L4462–4717  ·  3 elements  → **Menu home screen**

**S12.5a:** first rebuilt as the `AabNavDrawer` (`ModalNavigationDrawer`, `ui/components/AppShell.kt`)
opened from the branded `AabTopBar` hamburger — the menu's three HTML "cards" become groups
(Profiles&Contexts hero / Settings / Info&Help); Recheck Permissions → Onboarding; the Chart.js License
entry is dropped (D-046).

**S12.6a (G2R-F1/F2):** promoted into a real `AppRoute.Menu` home screen (`ui/screens/MenuScreen.kt`)
= the app hub and the **start destination after onboarding**. Renders the gold-sun teal banner
(`AabMenuBanner`, branded "Tideo Auto Brightness"), the **Profiles + Contexts hero cards** (moved OFF
the Dashboard), and the Settings / Info&Help nav groups. Every settings/tool screen's back returns
here (`navigateTopLevel` roots the back stack at `menu`). The Dashboard is now just another menu
destination. The S12.5a drawer is retired.

| Element | Type | Disposition |
|---|---|---|
| elements0 | Web | dropped-HTML; **nav rebuilt as `AabNavDrawer` (S12.5a)** |
| background#1 | Rect | dropped(decorative/background rect) |
| props | Properties | dropped(nav replaced by M3 navigation) |

### AAB About  ·  `about`  ·  XML L799–1037  ·  4 elements  → About+Guide+Onboarding

| Element | Type | Disposition |
|---|---|---|
| elements0 | Web | merged-into About+Guide+Onboarding |
| background#1 | Rect | dropped(decorative/background rect) |
| elements1 | Button | merged-into About+Guide+Onboarding |
| props | Properties | merged-into About+Guide+Onboarding |

### AAB User Guide  ·  `user_guide`  ·  XML L8551–8868  ·  4 elements  → About+Guide+Onboarding

| Element | Type | Disposition |
|---|---|---|
| elements0 | Web | merged-into About+Guide+Onboarding |
| background#1 | Rect | dropped(decorative/background rect) |
| elements1 | Button | merged-into About+Guide+Onboarding |
| props | Properties | merged-into About+Guide+Onboarding |

### AAB Chart.Js License  ·  `chartjs_license`  ·  XML L2194–2387  ·  4 elements  → (dropped)

| Element | Type | Disposition |
|---|---|---|
| elements0 | Web | dropped(Chart.js removed) |
| background#1 | Rect | dropped(decorative/background rect) |
| elements1 | Button | dropped(Chart.js removed) |
| props | Properties | dropped(Chart.js removed) |

### AAB Brightness Settings  ·  `brightness_settings`  ·  XML L1415–2193  ·  60 elements  → Curve & Brightness

| Element | Type | Disposition |
|---|---|---|
| elements0 | Web | dropped(scene chrome) |
| background#1 | Rect | dropped(decorative/background rect) |
| elements1 | Rect | dropped(scene chrome) |
| elements10 | Text | kept-as Curve & Brightness |
| background#2 | Rect | dropped(decorative/background rect) |
| elements11 | Text | merged-into Curve & Brightness |
| background#3 | Rect | dropped(decorative/background rect) |
| elements12 | EditText | kept-as Curve & Brightness |
| background#4 | Rect | dropped(decorative/background rect) |
| elements13 | Text | merged-into Curve & Brightness |
| background#5 | Rect | dropped(decorative/background rect) |
| elements14 | EditText | kept-as Curve & Brightness |
| background#6 | Rect | dropped(decorative/background rect) |
| elements15 | Text | merged-into Curve & Brightness |
| background#7 | Rect | dropped(decorative/background rect) |
| elements16 | Text | merged-into Curve & Brightness |
| background#8 | Rect | dropped(decorative/background rect) |
| elements17 | Text | kept-as Curve & Brightness |
| background#9 | Rect | dropped(decorative/background rect) |
| elements18 | Text | merged-into Curve & Brightness |
| background#10 | Rect | dropped(decorative/background rect) |
| elements19 | EditText | kept-as Curve & Brightness |
| background#11 | Rect | dropped(decorative/background rect) |
| elements2 | Rect | dropped(scene chrome) |
| elements20 | EditText | kept-as Curve & Brightness |
| background#12 | Rect | dropped(decorative/background rect) |
| elements21 | Text | merged-into Curve & Brightness |
| background#13 | Rect | dropped(decorative/background rect) |
| elements22 | Text | merged-into Curve & Brightness |
| background#14 | Rect | dropped(decorative/background rect) |
| elements23 | Text | dropped(scene chrome) |
| background#15 | Rect | dropped(decorative/background rect) |
| elements24 | Text | merged-into Curve & Brightness |
| background#16 | Rect | dropped(decorative/background rect) |
| elements25 | Text | dropped(scene chrome) |
| background#17 | Rect | dropped(decorative/background rect) |
| elements26 | Text | dropped(scene chrome) |
| background#18 | Rect | dropped(decorative/background rect) |
| elements27 | Text | dropped(scene chrome) |
| background#19 | Rect | dropped(decorative/background rect) |
| elements28 | Text | merged-into Curve & Brightness |
| background#20 | Rect | dropped(decorative/background rect) |
| elements29 | Switch | merged-into Dashboard |
| background#21 | Rect | dropped(decorative/background rect) |
| elements3 | Text | dropped(scene chrome) |
| background#22 | Rect | dropped(decorative/background rect) |
| elements30 | Switch | merged-into Dashboard |
| background#23 | Rect | dropped(decorative/background rect) |
| elements31 | Rect | dropped(scene chrome) |
| elements4 | Rect | dropped(scene chrome) |
| elements5 | Text | merged-into Curve & Brightness |
| background#24 | Rect | dropped(decorative/background rect) |
| elements6 | Text | merged-into Curve & Brightness |
| background#25 | Rect | dropped(decorative/background rect) |
| elements7 | EditText | kept-as Curve & Brightness |
| background#26 | Rect | dropped(decorative/background rect) |
| elements8 | Rect | dropped(scene chrome) |
| elements9 | Text | merged-into Curve & Brightness |
| background#27 | Rect | dropped(decorative/background rect) |
| props | Properties | merged-into Curve & Brightness |

### AAB Brightness Graph  ·  `brightness_graph`  ·  XML L1202–1414  ·  16 elements  → Curve & Brightness

| Element | Type | Disposition |
|---|---|---|
| elements0 | Web | merged-into Curve & Brightness |
| elements1 | Web | merged-into Curve & Brightness |
| background#1 | Rect | dropped(decorative/background rect) |
| elements2 | Text | merged-into Curve & Brightness |
| background#2 | Rect | dropped(decorative/background rect) |
| elements3 | Text | merged-into Curve & Brightness |
| background#3 | Rect | dropped(decorative/background rect) |
| elements4 | Text | merged-into Curve & Brightness |
| background#4 | Rect | dropped(decorative/background rect) |
| elements5 | Text | merged-into Curve & Brightness |
| background#5 | Rect | dropped(decorative/background rect) |
| elements6 | Text | merged-into Curve & Brightness |
| background#6 | Rect | dropped(decorative/background rect) |
| elements7 | Text | merged-into Curve & Brightness |
| background#7 | Rect | dropped(decorative/background rect) |
| props | Properties | merged-into Curve & Brightness |

### AAB Reactivity Settings  ·  `reactivity_settings`  ·  XML L6739–7532  ·  63 elements  → Reactivity

| Element | Type | Disposition |
|---|---|---|
| elements0 | Web | dropped(scene chrome) |
| elements1 | Rect | dropped(scene chrome) |
| elements10 | Text | merged-into Reactivity |
| background#1 | Rect | dropped(decorative/background rect) |
| elements11 | Text | merged-into Reactivity |
| background#2 | Rect | dropped(decorative/background rect) |
| elements12 | Text | merged-into Reactivity |
| background#3 | Rect | dropped(decorative/background rect) |
| elements13 | EditText | kept-as Reactivity |
| background#4 | Rect | dropped(decorative/background rect) |
| elements14 | EditText | kept-as Reactivity |
| background#5 | Rect | dropped(decorative/background rect) |
| elements15 | EditText | kept-as Reactivity |
| background#6 | Rect | dropped(decorative/background rect) |
| elements16 | EditText | kept-as Reactivity |
| background#7 | Rect | dropped(decorative/background rect) |
| elements17 | Rect | dropped(scene chrome) |
| elements18 | Text | dropped(scene chrome) |
| background#8 | Rect | dropped(decorative/background rect) |
| elements19 | Text | dropped(scene chrome) |
| background#9 | Rect | dropped(decorative/background rect) |
| elements2 | Rect | dropped(scene chrome) |
| elements20 | Text | merged-into Reactivity |
| background#10 | Rect | dropped(decorative/background rect) |
| elements21 | Text | merged-into Reactivity |
| background#11 | Rect | dropped(decorative/background rect) |
| elements22 | Text | dropped(scene chrome) |
| background#12 | Rect | dropped(decorative/background rect) |
| elements23 | EditText | kept-as Reactivity |
| background#13 | Rect | dropped(decorative/background rect) |
| elements24 | Text | dropped(scene chrome) |
| background#14 | Rect | dropped(decorative/background rect) |
| elements25 | Text | merged-into Reactivity |
| background#15 | Rect | dropped(decorative/background rect) |
| elements26 | Rect | dropped(scene chrome) |
| elements27 | Text | merged-into Reactivity |
| background#16 | Rect | dropped(decorative/background rect) |
| elements28 | Switch | kept-as Reactivity |
| background#17 | Rect | dropped(decorative/background rect) |
| elements29 | Switch | merged-into Reactivity |
| background#18 | Rect | dropped(decorative/background rect) |
| elements3 | Text | dropped(scene chrome) |
| background#19 | Rect | dropped(decorative/background rect) |
| elements30 | Rect | dropped(scene chrome) |
| elements31 | Text | merged-into Reactivity |
| background#20 | Rect | dropped(decorative/background rect) |
| elements32 | Switch | kept-as Reactivity |
| background#21 | Rect | dropped(decorative/background rect) |
| elements33 | Switch | merged-into Reactivity |
| background#22 | Rect | dropped(decorative/background rect) |
| elements34 | Rect | dropped(scene chrome) |
| elements4 | Text | merged-into Reactivity |
| background#23 | Rect | dropped(decorative/background rect) |
| elements5 | Rect | dropped(scene chrome) |
| elements6 | Text | merged-into Reactivity |
| background#24 | Rect | dropped(decorative/background rect) |
| elements7 | Text | merged-into Reactivity |
| background#25 | Rect | dropped(decorative/background rect) |
| elements8 | Text | merged-into Reactivity |
| background#26 | Rect | dropped(decorative/background rect) |
| elements9 | Text | merged-into Reactivity |
| background#27 | Rect | dropped(decorative/background rect) |
| props | Properties | merged-into Reactivity |

### AAB Reactivity Graph  ·  `reactivity_graph`  ·  XML L6563–6738  ·  13 elements  → Reactivity

| Element | Type | Disposition |
|---|---|---|
| elements0 | Web | merged-into Reactivity |
| background#1 | Rect | dropped(decorative/background rect) |
| elements1 | Web | merged-into Reactivity |
| background#2 | Rect | dropped(decorative/background rect) |
| elements2 | Text | merged-into Reactivity |
| background#3 | Rect | dropped(decorative/background rect) |
| elements3 | Text | merged-into Reactivity |
| background#4 | Rect | dropped(decorative/background rect) |
| elements4 | Text | merged-into Reactivity |
| background#5 | Rect | dropped(decorative/background rect) |
| elements5 | Text | merged-into Reactivity |
| background#6 | Rect | dropped(decorative/background rect) |
| props | Properties | merged-into Reactivity |

### AAB Alpha Graph  ·  `alpha_graph`  ·  XML L1038–1201  ·  12 elements  → Reactivity

| Element | Type | Disposition |
|---|---|---|
| elements0 | Web | merged-into Reactivity |
| elements1 | Web | merged-into Reactivity |
| background#1 | Rect | dropped(decorative/background rect) |
| elements2 | Text | merged-into Reactivity |
| background#2 | Rect | dropped(decorative/background rect) |
| elements3 | Text | merged-into Reactivity |
| background#3 | Rect | dropped(decorative/background rect) |
| elements4 | Text | merged-into Reactivity |
| background#4 | Rect | dropped(decorative/background rect) |
| elements5 | Text | merged-into Reactivity |
| background#5 | Rect | dropped(decorative/background rect) |
| props | Properties | merged-into Reactivity |

### AAB Superdimming Settings  ·  `superdimming_settings`  ·  XML L7533–8386  ·  67 elements  → Animation & Dimming

| Element | Type | Disposition |
|---|---|---|
| elements0 | Web | dropped(scene chrome) |
| elements1 | Rect | dropped(scene chrome) |
| elements10 | Text | merged-into Animation & Dimming |
| background#1 | Rect | dropped(decorative/background rect) |
| elements11 | Text | dropped(scene chrome) |
| background#2 | Rect | dropped(decorative/background rect) |
| elements12 | Text | merged-into Animation & Dimming |
| background#3 | Rect | dropped(decorative/background rect) |
| elements13 | Text | merged-into Animation & Dimming |
| background#4 | Rect | dropped(decorative/background rect) |
| elements14 | Text | merged-into Animation & Dimming |
| background#5 | Rect | dropped(decorative/background rect) |
| elements15 | EditText | kept-as Animation & Dimming |
| background#6 | Rect | dropped(decorative/background rect) |
| elements16 | Switch | kept-as Animation & Dimming |
| background#7 | Rect | dropped(decorative/background rect) |
| elements17 | Text | merged-into Animation & Dimming |
| background#8 | Rect | dropped(decorative/background rect) |
| elements18 | Switch | merged-into Animation & Dimming |
| background#9 | Rect | dropped(decorative/background rect) |
| elements19 | Rect | dropped(scene chrome) |
| elements2 | Rect | dropped(scene chrome) |
| elements20 | Text | dropped(scene chrome) |
| background#10 | Rect | dropped(decorative/background rect) |
| elements21 | Text | merged-into Animation & Dimming |
| background#11 | Rect | dropped(decorative/background rect) |
| elements22 | Rect | dropped(scene chrome) |
| elements23 | Text | merged-into Animation & Dimming |
| background#12 | Rect | dropped(decorative/background rect) |
| elements24 | Text | merged-into Animation & Dimming |
| background#13 | Rect | dropped(decorative/background rect) |
| elements25 | Rect | dropped(scene chrome) |
| elements26 | Text | merged-into Animation & Dimming |
| background#14 | Rect | dropped(decorative/background rect) |
| elements27 | Text | merged-into Animation & Dimming |
| background#15 | Rect | dropped(decorative/background rect) |
| elements28 | EditText | kept-as Animation & Dimming |
| background#16 | Rect | dropped(decorative/background rect) |
| elements29 | EditText | kept-as Animation & Dimming |
| background#17 | Rect | dropped(decorative/background rect) |
| elements3 | Text | merged-into Animation & Dimming |
| background#18 | Rect | dropped(decorative/background rect) |
| elements30 | Text | merged-into Animation & Dimming |
| background#19 | Rect | dropped(decorative/background rect) |
| elements31 | EditText | kept-as Animation & Dimming |
| background#20 | Rect | dropped(decorative/background rect) |
| elements32 | Text | merged-into Animation & Dimming |
| background#21 | Rect | dropped(decorative/background rect) |
| elements33 | Switch | kept-as Animation & Dimming |
| background#22 | Rect | dropped(decorative/background rect) |
| elements34 | Switch | merged-into Animation & Dimming |
| background#23 | Rect | dropped(decorative/background rect) |
| elements35 | Rect | dropped(scene chrome) |
| elements36 | EditText | kept-as Animation & Dimming |
| background#24 | Rect | dropped(decorative/background rect) |
| elements4 | Text | dropped(scene chrome) |
| background#25 | Rect | dropped(decorative/background rect) |
| elements5 | Rect | dropped(scene chrome) |
| elements6 | Text | dropped(scene chrome) |
| background#26 | Rect | dropped(decorative/background rect) |
| elements7 | Text | merged-into Animation & Dimming |
| background#27 | Rect | dropped(decorative/background rect) |
| elements8 | Text | dropped(scene chrome) |
| background#28 | Rect | dropped(decorative/background rect) |
| elements9 | Text | dropped(scene chrome) |
| background#29 | Rect | dropped(decorative/background rect) |
| props | Properties | merged-into Animation & Dimming |

### AAB Dimming Graph  ·  `dimming_graph`  ·  XML L3006–3169  ·  12 elements  → Animation & Dimming

| Element | Type | Disposition |
|---|---|---|
| elements0 | Web | merged-into Animation & Dimming |
| elements1 | Web | merged-into Animation & Dimming |
| background#1 | Rect | dropped(decorative/background rect) |
| elements2 | Text | merged-into Animation & Dimming |
| background#2 | Rect | dropped(decorative/background rect) |
| elements3 | Text | merged-into Animation & Dimming |
| background#3 | Rect | dropped(decorative/background rect) |
| elements4 | Text | merged-into Animation & Dimming |
| background#4 | Rect | dropped(decorative/background rect) |
| elements5 | Text | merged-into Animation & Dimming |
| background#5 | Rect | dropped(decorative/background rect) |
| props | Properties | merged-into Animation & Dimming |

### AAB Color Filter  ·  `color_filter`  ·  XML L2552–2582  ·  2 elements  → Animation & Dimming

| Element | Type | Disposition |
|---|---|---|
| elements0 | Rect | merged-into Animation & Dimming |
| props | Properties | merged-into Animation & Dimming |

### AAB Experiment Settings  ·  `experiment_settings`  ·  XML L3334–4461  ·  70 elements  → Dynamic Scale

| Element | Type | Disposition |
|---|---|---|
| elements0 | Web | merged-into Dynamic Scale |
| elements1 | Rect | merged-into Dynamic Scale |
| elements10 | Text | merged-into Dynamic Scale |
| background#1 | Rect | dropped(decorative/background rect) |
| elements11 | Text | merged-into Dynamic Scale |
| background#2 | Rect | dropped(decorative/background rect) |
| elements12 | Text | merged-into Dynamic Scale |
| background#3 | Rect | dropped(decorative/background rect) |
| elements13 | Text | merged-into Dynamic Scale |
| background#4 | Rect | dropped(decorative/background rect) |
| elements14 | Text | merged-into Dynamic Scale |
| background#5 | Rect | dropped(decorative/background rect) |
| elements15 | EditText | merged-into Dynamic Scale |
| background#6 | Rect | dropped(decorative/background rect) |
| elements16 | EditText | merged-into Dynamic Scale |
| background#7 | Rect | dropped(decorative/background rect) |
| elements17 | Switch | merged-into Dynamic Scale |
| background#8 | Rect | dropped(decorative/background rect) |
| elements18 | Text | merged-into Dynamic Scale |
| background#9 | Rect | dropped(decorative/background rect) |
| elements19 | Switch | merged-into Dynamic Scale |
| background#10 | Rect | dropped(decorative/background rect) |
| elements2 | Rect | merged-into Dynamic Scale |
| elements20 | Rect | merged-into Dynamic Scale |
| elements21 | Rect | merged-into Dynamic Scale |
| elements22 | Text | merged-into Dynamic Scale |
| background#11 | Rect | dropped(decorative/background rect) |
| elements23 | Text | merged-into Dynamic Scale |
| background#12 | Rect | dropped(decorative/background rect) |
| elements24 | Rect | merged-into Dynamic Scale |
| elements25 | Text | merged-into Dynamic Scale |
| background#13 | Rect | dropped(decorative/background rect) |
| elements26 | Slider | merged-into Dynamic Scale |
| background#14 | Rect | dropped(decorative/background rect) |
| elements27 | Text | merged-into Dynamic Scale |
| background#15 | Rect | dropped(decorative/background rect) |
| elements28 | EditText | merged-into Dynamic Scale |
| background#16 | Rect | dropped(decorative/background rect) |
| elements29 | Rect | merged-into Dynamic Scale |
| elements3 | Text | merged-into Dynamic Scale |
| background#17 | Rect | dropped(decorative/background rect) |
| elements30 | Text | merged-into Dynamic Scale |
| background#18 | Rect | dropped(decorative/background rect) |
| elements31 | Text | merged-into Dynamic Scale |
| background#19 | Rect | dropped(decorative/background rect) |
| elements32 | Switch | merged-into Dynamic Scale |
| background#20 | Rect | dropped(decorative/background rect) |
| elements33 | Switch | merged-into Dynamic Scale |
| background#21 | Rect | dropped(decorative/background rect) |
| elements34 | Rect | merged-into Dynamic Scale |
| elements35 | Text | merged-into Dynamic Scale |
| background#22 | Rect | dropped(decorative/background rect) |
| elements36 | Text | merged-into Dynamic Scale |
| background#23 | Rect | dropped(decorative/background rect) |
| elements37 | Web | merged-into Dynamic Scale |
| background#24 | Rect | dropped(decorative/background rect) |
| elements38 | Button | merged-into Dynamic Scale |
| elements4 | EditText | merged-into Dynamic Scale |
| background#25 | Rect | dropped(decorative/background rect) |
| elements5 | Text | merged-into Dynamic Scale |
| background#26 | Rect | dropped(decorative/background rect) |
| elements6 | Text | merged-into Dynamic Scale |
| background#27 | Rect | dropped(decorative/background rect) |
| elements7 | Text | merged-into Dynamic Scale |
| background#28 | Rect | dropped(decorative/background rect) |
| elements8 | Text | merged-into Dynamic Scale |
| background#29 | Rect | dropped(decorative/background rect) |
| elements9 | Text | merged-into Dynamic Scale |
| background#30 | Rect | dropped(decorative/background rect) |
| props | Properties | merged-into Dynamic Scale |

### AAB Experiment Graph  ·  `experiment_graph`  ·  XML L3170–3333  ·  12 elements  → Dynamic Scale

| Element | Type | Disposition |
|---|---|---|
| elements0 | Web | merged-into Dynamic Scale |
| elements1 | Web | merged-into Dynamic Scale |
| background#1 | Rect | dropped(decorative/background rect) |
| elements2 | Text | merged-into Dynamic Scale |
| background#2 | Rect | dropped(decorative/background rect) |
| elements3 | Text | merged-into Dynamic Scale |
| background#3 | Rect | dropped(decorative/background rect) |
| elements4 | Text | merged-into Dynamic Scale |
| background#4 | Rect | dropped(decorative/background rect) |
| elements5 | Text | merged-into Dynamic Scale |
| background#5 | Rect | dropped(decorative/background rect) |
| props | Properties | merged-into Dynamic Scale |

### AAB Circadian Dimming Graph  ·  `circadian_dimming_graph`  ·  XML L2388–2551  ·  12 elements  → Animation & Dimming

> **S3.5 (owner, D-026): re-homed Dynamic Scale → Animation & Dimming.** In Tasker this graph is opened
> from **Superdimming Settings** (`draw_graph4` button, handler task517: warning toast if
> `%aab_scaletransitionfactor>0.5`, clears `%AAB_SunLastDate`, runs task90 + task705, hides Superdimming
> Settings), and the button is shown only when circadian scaling (`%AAB_ScalingUse`) is enabled — keep
> that conditional visibility. This also resolves the contradiction with `_disp_group4.md`, which already
> said Animation & Dimming. (Distinct from the Experiment Graph / task549 / Graph4 — that one stays in
> Dynamic Scale.)

| Element | Type | Disposition |
|---|---|---|
| elements0 | Web | merged-into Animation & Dimming |
| elements1 | Web | merged-into Animation & Dimming |
| background#1 | Rect | dropped(decorative/background rect) |
| elements2 | Text | merged-into Animation & Dimming |
| background#2 | Rect | dropped(decorative/background rect) |
| elements3 | Text | merged-into Animation & Dimming |
| background#3 | Rect | dropped(decorative/background rect) |
| elements4 | Text | merged-into Animation & Dimming |
| background#4 | Rect | dropped(decorative/background rect) |
| elements5 | Text | merged-into Animation & Dimming |
| background#5 | Rect | dropped(decorative/background rect) |
| props | Properties | merged-into Animation & Dimming |

### AAB Taper Graph  ·  `taper_graph`  ·  XML L8387–8550  ·  12 elements  → Dynamic Scale

| Element | Type | Disposition |
|---|---|---|
| elements0 | Web | merged-into Dynamic Scale |
| elements1 | Web | merged-into Dynamic Scale |
| background#1 | Rect | dropped(decorative/background rect) |
| elements2 | Text | merged-into Dynamic Scale |
| background#2 | Rect | dropped(decorative/background rect) |
| elements3 | Text | merged-into Dynamic Scale |
| background#3 | Rect | dropped(decorative/background rect) |
| elements4 | Text | merged-into Dynamic Scale |
| background#4 | Rect | dropped(decorative/background rect) |
| elements5 | Text | merged-into Dynamic Scale |
| background#5 | Rect | dropped(decorative/background rect) |
| props | Properties | merged-into Dynamic Scale |

### AAB Power Draw Graph  ·  `power_draw_graph`  ·  XML L5611–5723  ·  8 elements  → Tools

| Element | Type | Disposition |
|---|---|---|
| elements0 | Web | merged-into Tools |
| elements1 | Web | merged-into Tools |
| background#1 | Rect | dropped(decorative/background rect) |
| elements2 | Text | merged-into Tools |
| background#2 | Rect | dropped(decorative/background rect) |
| elements3 | Text | merged-into Tools |
| background#3 | Rect | dropped(decorative/background rect) |
| props | Properties | merged-into Tools |

### AAB Misc Settings  ·  `misc_settings`  ·  XML L4718–5610  ·  69 elements  → Dashboard

| Element | Type | Disposition |
|---|---|---|
| elements0 | Web | merged-into Dashboard |
| elements1 | Rect | merged-into Dashboard |
| elements10 | EditText | merged-into Dashboard |
| background#1 | Rect | dropped(decorative/background rect) |
| elements11 | Text | merged-into Dashboard |
| background#2 | Rect | dropped(decorative/background rect) |
| elements12 | Text | merged-into Dashboard |
| background#3 | Rect | dropped(decorative/background rect) |
| elements13 | Text | merged-into Dashboard |
| background#4 | Rect | dropped(decorative/background rect) |
| elements14 | Text | merged-into Dashboard |
| background#5 | Rect | dropped(decorative/background rect) |
| elements15 | Rect | merged-into Dashboard |
| elements16 | Text | merged-into Dashboard |
| background#6 | Rect | dropped(decorative/background rect) |
| elements17 | Rect | merged-into Dashboard |
| elements18 | Text | merged-into Dashboard |
| background#7 | Rect | dropped(decorative/background rect) |
| elements19 | Text | merged-into Dashboard |
| background#8 | Rect | dropped(decorative/background rect) |
| elements2 | Rect | merged-into Dashboard |
| elements20 | Slider | merged-into Dashboard |
| background#9 | Rect | dropped(decorative/background rect) |
| elements21 | Text | merged-into Dashboard |
| background#10 | Rect | dropped(decorative/background rect) |
| elements22 | Slider | merged-into Dashboard |
| background#11 | Rect | dropped(decorative/background rect) |
| elements23 | Slider | merged-into Dashboard |
| background#12 | Rect | dropped(decorative/background rect) |
| elements24 | Rect | merged-into Dashboard |
| elements25 | Rect | merged-into Dashboard |
| elements26 | Text | merged-into Dashboard |
| background#13 | Rect | dropped(decorative/background rect) |
| elements27 | Text | merged-into Dashboard |
| background#14 | Rect | dropped(decorative/background rect) |
| elements28 | EditText | merged-into Dashboard |
| background#15 | Rect | dropped(decorative/background rect) |
| elements29 | Text | merged-into Dashboard |
| background#16 | Rect | dropped(decorative/background rect) |
| elements3 | Text | merged-into Dashboard |
| background#17 | Rect | dropped(decorative/background rect) |
| elements30 | Text | merged-into Dashboard |
| background#18 | Rect | dropped(decorative/background rect) |
| elements31 | Text | merged-into Dashboard |
| background#19 | Rect | dropped(decorative/background rect) |
| elements32 | Text | merged-into Dashboard |
| background#20 | Rect | dropped(decorative/background rect) |
| elements33 | Text | merged-into Dashboard |
| background#21 | Rect | dropped(decorative/background rect) |
| elements34 | Text | merged-into Dashboard |
| background#22 | Rect | dropped(decorative/background rect) |
| elements35 | Switch | merged-into Dashboard |
| background#23 | Rect | dropped(decorative/background rect) |
| elements36 | Switch | merged-into Dashboard |
| background#24 | Rect | dropped(decorative/background rect) |
| elements37 | Rect | merged-into Dashboard |
| elements4 | Slider | merged-into Dashboard |
| background#25 | Rect | dropped(decorative/background rect) |
| elements5 | Text | merged-into Dashboard |
| background#26 | Rect | dropped(decorative/background rect) |
| elements6 | Slider | merged-into Dashboard |
| background#27 | Rect | dropped(decorative/background rect) |
| elements7 | Text | merged-into Dashboard |
| background#28 | Rect | dropped(decorative/background rect) |
| elements8 | Text | merged-into Dashboard |
| background#29 | Rect | dropped(decorative/background rect) |
| elements9 | EditText | merged-into Dashboard |
| background#30 | Rect | dropped(decorative/background rect) |
| props | Properties | merged-into Dashboard |

### AAB Profile  ·  `profile`  ·  XML L5724–6562  ·  3 elements  → Profiles & Import/Export

> S12.6d (G2R-F15/F16/F17/F18/F30): the Profiles screen now manages user-editable saved profiles
> (`UserProfileStore` — built-ins seeded once + Save-current-as + per-entry Overwrite/Delete + Restore
> factory profiles), legacy import via a one-time SAF folder grant to `Download/AAB/configs`
> (`LegacyConfigImporter`, no MANAGE_EXTERNAL_STORAGE) listing/loading the `*.json` there, the existing
> single-file import/export, a "Reset settings to defaults", and a manual-load context-lock Resume banner
> (G2R-F30). The 5 draft settings screens each gained a per-screen Reset action (G2R-F17), and their Apply
> is blocked on CRITICAL curve errors (G2R-F18/D-052). Context rule editor gained battery % from/to
> (G2R-F31); Tools wizard surfaces the full verbose report (G2R-F32).

| Element | Type | Disposition |
|---|---|---|
| elements0 | Web | merged-into Profiles & Import/Export |
| background#1 | Rect | dropped(decorative/background rect) |
| props | Properties | merged-into Profiles & Import/Export |

### AAB Debug Scene  ·  `debug`  ·  XML L2583–3005  ·  4 elements  → LiveDebug

> S12.6b (G2R-F6): rebuilt as the dedicated **Live Debug Info** screen (`AppRoute.LiveDebug`, reached from
> the Menu) — a glass-box readout of the live `%AAB_*` runtime vars grouped per the WebElement's HTML cards
> (Core Metrics / Circadian & Scale / System Status / Performance & Timings), gold `#FFC107` values, plus
> the now-GLOBAL 10-label debug-category selector (moved off Misc, G2R-F9). The in-scene close button +
> back-key props are handled by Compose nav. Per-screen `DiagnosticCard`s embed the same live readout on
> Reactivity (threshold + dead zone) and Circadian (uncompressed vs true scale) (G2R-F7/F8). Power-draw
> calibration stays on Tools.

| Element | Type | Disposition |
|---|---|---|
| elements0 | Web | merged-into LiveDebug (live %AAB_* readout + global debug selector); power-draw calibration → Tools |
| background#1 | Rect | dropped(decorative/background rect) |
| elements1 | Button | dropped(M3 nav back replaces in-scene close) |
| props | Properties | dropped(handled by Compose navigation) |

## Summary

- **450** total element dispositions (matches the XML census: 224 Rect / 129 Text / 28 Web / 22 EditText / 20 Properties / 16 Switch / 6 Slider / 5 Button).
- Functional controls (EditText / Switch / Slider / Button / WebElement charts) are `kept-as` their
  target screen; labels (`TextElement`) are `merged-into`; decorative/background rects and
  `PropertiesElement` scene-chrome are `dropped` (M3 Scaffold replaces them).
- All 28 WebElements: 8 are Chart.js charts → named Compose charts (table above); the rest are
  full-screen HTML surfaces (Menu nav, About, User Guide, Profile manager, settings backgrounds)
  re-implemented as native Compose screens.
- The 4 Chart.Js License elements are `dropped(Chart.js removed)`; the Menu's HTML nav is replaced
  by M3 navigation. **Zero unmapped elements.**

---

## Screen inventory — as shipped (refreshed 2026-06-30)

> This was first written as a dated **S12 implementation status (2026-06-13)** snapshot; it is
> refreshed here to the **routed** screens in `navigation/NavGraph.kt` (the source of truth). Several
> screens were renamed/merged after that snapshot — the stage/deviation tags below preserve the
> lineage. (Ground truth is code; if this drifts again, trust `NavGraph.kt` + the screen files.)

Routed screens (`AppRoute` → `ui/screens/*.kt`):

- **Menu** → `MenuScreen.kt` — the canonical hub / start destination after onboarding; back-target from
  every settings/tool screen (S12.6a, G2R-F1/F2).
- **Dashboard** → `DashboardScreen.kt` — live brightness / lux / profile / context hero, override &
  resume banners (D-110/D-111/D-126).
- **Curve & Brightness** → `CurveBrightnessScreen.kt` (+ `BrightnessCurveChart` on the reusable
  `ui/graph/ChartCanvas.kt` engine) — curve-zone coefficients, draft→Apply; the brightness range
  (min/max/offset/scale) moved to **Misc** (G2-F2). Suggestion preview seeds the draft (D-125).
- **Reactivity** → `ReactivityScreen.kt` (+ `ReactivityChart`) — thresholds, midpoint/steepness/
  deltaFactor, DetectOverrides + trust-unreliable toggles (D-041 F2).
- **Super Dimming** → `SuperDimmingScreen.kt` (+ `DimmingChart`) — **renamed from "Animation & Dimming"**
  (S12.6a, G2R-F3): super dimming (ELEVATED) + PWM/software dimming, mutually exclusive (G2-F10); the
  animation fields moved to **Misc** (G2-F2).
- **Circadian** → `CircadianScreen.kt` (+ Experiment / Taper graphs) — **renamed from "Dynamic Scale"**
  (S12.6a, G2R-F4): day/night curve scaling + location staleness hints (D-110).
- **Misc** → `MiscScreen.kt` — brightness range + animation (both as sliders) + notifications (G2-F2).
- **Tools** → `ToolsScreen.kt` (+ `PowerDrawChart`) — curve-suggestion wizard (`CurveSuggestionEngine`)
  + power-draw calibration.
- **Live Debug** → `LiveDebugScreen.kt` — the 10 named `%AAB_Debug` categories (D-023) + panic
  sensitivity slider (D-116); the debug-category selector lives here now (S12.6b, G2R-F9).
- **Profiles & Contexts** → `ProfilesContextsScreen.kt` — **merged Profiles + Contexts** (S12.9f, D-070):
  saved-profile CRUD / import-export (`ProfilesBody`, housed in `ProfilesScreen.kt`) above a context-rule
  list/editor (`ContextRulesSection`, housed in `ContextsScreen.kt`) over `ContextRuleStore`. After the
  merge, `ProfilesScreen.kt` and `ContextsScreen.kt` are **reused component-houses, not routed screens**.
  The rule editor's "Use current SSID" shows the no-Location SSID-help dialog (Shizuku/root/ADB-DUMP, D-130).
- **User Guide** → `UserGuideScreen.kt` — static themed HTML in a `WebView` (no JS / no network).
- **About** → `AboutScreen.kt` — About / MIT-license page (Chart.js acknowledgment dropped — charts are
  native Compose now).

(Plus the non-`AppRoute` **Onboarding** flow, `ui/onboarding/OnboardingScreen.kt`.)

Charts: all are implemented natively on `ChartCanvas.kt` (`BrightnessCurveChart`, `ReactivityChart`,
`DimmingChart`, the Circadian Experiment/Taper graphs, `PowerDrawChart`) — the S13 "remaining six charts"
deferral (D-044) is complete and Chart.js is removed.
