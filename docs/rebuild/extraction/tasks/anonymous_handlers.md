# anonymous_handlers.md — census of all 168 unnamed (anonymous) tasks (S3.5)

**Provenance:** S3.5 owner-review errata. The owner flagged that `tasks/` only covers *named*
tasks; the XML holds **276 tasks, of which 168 are anonymous** scene-element handlers
(`clickTask`, `checkchangeTask`, `valueselectedTask`, `keyTask`, …). Every one of the 168 is
referenced from exactly one scene (none are dead). 34 of them are `keyTask` handlers on scene
`PropertiesElement`s — i.e. **hardware-/back-key behavior** — which S2's element dispositions
dropped as "scene chrome"; S12 must re-derive per-screen back behavior from them.

**Method (regenerable):** mechanical decode — task id + XML line from `<Task sr=>` blocks
lacking `<nme>`; wiring from `<*Task>ID</*Task>` refs inside `<Scene>` blocks; per action:
code → name (INDEX.md legend + best-effort Tasker labels — treat code numbers + args as ground
truth, labels outside the INDEX-verified set as advisory), first condition for If/Else, first
`arg0` (truncated, entity-decoded). Action chains are truncated at ~220 chars.

**How to consume (S12/S13 precondition):** every row must end up either *ported* (wired into
the Compose screen that absorbs its scene, per `screen_map.md`) or *dropped(reason)*. Most rows
are 1–3-action glue (set var / perform named task / scene nav); deep-dive only the branching
ones (validation guards, e.g. task384/386/395/403, task517) during the owning segment. Use
XML_RECIPES R2 (extract task by id) for the full block.

| Task | XML | Wired from (scene · element·event) | Actions |
|---|---|---|---|
| task383 | L11185 | AAB Brightness Settings · elements22·longclick | Flash(Current light sensor reading after smoothing …) |
| task384 | L11210 | AAB Brightness Graph · elements3·click | If[%aab_form2a < 0]; Flash(⚠️ Invalid brightness settings. Negative valu…); Stop; Else; Perform Task(_SaveButtonGeneral); End If |
| task386 | L11278 | AAB Brightness Settings · elements24·click | If[%aab_form2a < 0]; Flash(⚠️ Invalid brightness settings. Can't be appl…); Stop; Else; Perform Task(_SaveButtonGeneral); End If |
| task390 | L11364 | AAB Misc Settings · elements6·valueselected | Variable Set(%aab_maxbright) |
| task391 | L11380 | AAB Brightness Settings · elements26·click | Perform Task(_ExitButton) |
| task395 | L11402 | AAB Misc Settings · elements4·valueselected | Variable Set(%aab_minbright); If[%new_val < 10]; Flash(Caution: Low brightness may be unreadable on …); Variable Set(%warn); End If |
| task396 | L11466 | AAB Brightness Settings · elements28·click | Variable Clear(%AAB_SetupComplete); Variable Set(%reset); Perform Task(Initialize AAB Defaults); Variable Clear(%reset); Variable Set(%AAB_SetupComplete); Multiple Variables Set; Destroy Scene(AAB Brightness Settings); … |
| task397 | L11566 | AAB Brightness Settings · elements29·checkchange | Perform Task(_QSToggleAABService V2) |
| task402 | L11648 | AAB Brightness Settings · elements30·checkchange | Perform Task(_QSToggleAABService V2) |
| task403 | L11670 | AAB Misc Settings · elements13·click | If[%aab_minwait > %aab_maxwait]; Flash(⚠️ Invalid wait time settings. Can't be appli…); Stop; Else; Perform Task(_SaveButtonMisc); End If |
| task405 | L11732 | AAB Misc Settings · elements33·longclick | Flash(A global multiplier for the entire brightness…) |
| task406 | L11757 | AAB Reactivity Graph · elements3·click | Perform Task(_SaveButtonReactivity) |
| task407 | L11779 | AAB Misc Settings · elements10·valueselected | Variable Set(%aab_scale) |
| task409 | L11795 | AAB Misc Settings · elements9·valueselected | Variable Set(%aab_offset) |
| task411 | L11811 | AAB Reactivity Graph · elements5·click | Multiple Variables Set; Perform Task(_AdaptiveBrightnessSceneSize V4); Perform Task(_LoadedReactivityToggle); Perform Task(_LoadedOverrideToggle); Destroy Scene(AAB Reactivity Graph) |
| task412 | L11888 | AAB Brightness Graph · elements6·click | If[%suggest = true]; Variable Clear(%suggest*); Destroy Scene(AAB Brightness Settings); Perform Task(Advanced Auto Brightness); Destroy Scene(AAB Brightness Graph); Else; Perform Task(Advanced Auto Brightness); Destroy … |
| task417 | L11961 | AAB Misc Settings · elements14·click | Perform Task(_ExitButton) |
| task421 | L11983 | AAB Superdimming Settings · elements24·longclick | If[%AAB_DimmingEnabled = true]; Flash(This is the screen brightness below which sup…); Else[%AAB_PWMSensitive = true]; Flash(This is the lowest hardware screen brightness…); End If |
| task462 | L12855 | AAB Debug Scene · elements1·click | Perform Task(_MenuScene); Wait; Destroy Scene(AAB Debug Scene) |
| task465 | L12889 | AAB Superdimming Settings · elements3·longclick | Flash(Controls the maximum super dimming strength. …) |
| task466 | L12914 | AAB Superdimming Settings · elements7·click | Perform Task(_SaveButtonDimming); Perform Task(_DimmingApplyToggle) |
| task473 | L12952 | AAB Reactivity Settings · elements4·click | Perform Task(_GenerateReactivityGraph (Java)); Multiple Variables Set; Perform Task(_AdaptiveBrightnessSceneSize V4); Hide Scene(AAB Reactivity Settings) |
| task481 | L13013 | AAB Superdimming Settings · elements8·click | Perform Task(_ExitButton) |
| task490 | L13035 | AAB Superdimming Settings · elements8·longclick | Perform Task(_ExitButton) |
| task492 | L13056 | AAB Superdimming Settings · elements27·click | Variable Clear(%AAB_Privilege); Perform Task(_PrivilegeDetection V5 (Java)) |
| task493 | L13085 | AAB Reactivity Settings · elements11·longclick | Flash(Point where the logic switches from zone 1 cu…) |
| task500 | L13110 | AAB Superdimming Settings · elements12·click | Variable Clear(%AAB_SetupComplete); Variable Set(%reset); Perform Task(Initialize AAB Defaults); Variable Clear(%reset); Variable Set(%AAB_SetupComplete); Multiple Variables Set; Perform Task(_SuperDimmingScene) |
| task505 | L13202 | AAB Superdimming Settings · elements13·longclick | Variable Set(%min); Variable Set(%max); If[%max > 65]; Variable Set(%max); End If; Flash(Controls how wide the scale shifts over the d…) |
| task506 | L13270 | AAB Superdimming Settings · elements14·longclick | Flash(Controls how gradual the super dimming kicks …) |
| task508 | L13295 | AAB Superdimming Settings · elements15·valueselected | If[%new_val < 0.00001]; Stop; End If; Variable Set(%aab_dimmingexponent) |
| task509 | L13329 | AAB Superdimming Settings · elements16·checkchange | Perform Task(_DimmingUIToggle); Perform Task(_DimmingApplyToggle); If[%AAB_Privilege = None]; Flash(⚠️ Unprivileged super dimming will use a semi…); End If |
| task510 | L13405 | AAB Superdimming Settings · elements17·longclick | Flash(Enables or disables the entire experimental c…) |
| task511 | L13430 | AAB Superdimming Settings · elements18·checkchange | Perform Task(_DimmingUIToggle); Perform Task(_DimmingApplyToggle); If[%AAB_Privilege = None]; Flash(⚠️ Unprivileged super dimming will use a semi…); End If |
| task513 | L13567 | AAB Superdimming Settings · elements21·click | If[%aab_dimmingthreshold < %aab_minbright]; Flash(⚠️ Threshold is set below minimum brightness!…); Stop; End If; Perform Task(_GenerateDimmingCurveGraph (Java)); Multiple Variables Set; Perform Task(_AdaptiveBrightnessS… |
| task514 | L13665 | AAB Superdimming Settings · elements23·longclick | Flash(Displays the current (un)compressed scale fac…) |
| task515 | L13690 | AAB Dimming Graph · elements3·click | Perform Task(_SaveButtonDimming) |
| task516 | L13712 | AAB Dimming Graph · elements5·click | Show Scene(AAB Superdimming Settings); Perform Task(_LoadedDimmingToggle); Wait; Destroy Scene(AAB Dimming Graph) |
| task517 | L13761 | AAB Superdimming Settings · elements10·click | If[%aab_scaletransitionfactor > 0.5]; Flash(⚠️ Graph might be non-sensical due to scale t…); End If; Flash(Please be patient. Generating the graph might…); Variable Clear(%AAB_SunLastDate); Perform Task(Dynamic Scale V1… |
| task519 | L13896 | AAB Reactivity Settings · elements31·longclick | Flash(Disable this if you get frequent false positi…) |
| task520 | L13921 | AAB Circadian Dimming Graph · elements3·click | Perform Task(_SaveButtonDimming) |
| task521 | L13943 | AAB Circadian Dimming Graph · elements5·click | Show Scene(AAB Superdimming Settings); Perform Task(_LoadedDimmingToggle); Wait; Destroy Scene(AAB Circadian Dimming Graph) |
| task522 | L13992 | AAB Superdimming Settings · elements28·valueselected | If[%new_val > 100]; Stop; End If; Variable Set(%aab_dimspread) |
| task523 | L14032 | AAB Superdimming Settings · elements28·focuschange | If[%focused = false]; If[%new_val > 100]; Flash(⚠️ Dimming spread setting invalid! Has to be …); End If; End If |
| task525 | L14740 | AAB Reactivity Settings · elements33·checkchange | Perform Task(_OverrideToggle) |
| task526 | L14762 | AAB Reactivity Settings · elements32·checkchange | Perform Task(_OverrideToggle) |
| task527 | L14784 | AAB Superdimming Settings · elements26·longclick | Perform Task(_SuperDimmingLongTap) |
| task529 | L14939 | AAB Superdimming Settings · elements32·longclick | Flash(Enables or disables the software dimming feat…) |
| task530 | L14964 | AAB Superdimming Settings · elements31·valueselected | If[%new_val > 0.1999]; Variable Set(%aab_pwmexp); Else; End If |
| task531 | L15002 | AAB Superdimming Settings · elements36·valueselected | If[%new_val < %AAB_MinBright]; Stop; End If; Variable Set(%aab_dimmingthreshold) |
| task532 | L15042 | AAB Power Draw Graph · elements3·click | Show Scene(AAB Debug Scene); If[%err isSet]; Destroy Scene(AAB Debug Scene); Show Scene(AAB Debug Scene); End If; Destroy Scene(AAB Power Draw Graph) |
| task533 | L15105 | AAB Superdimming Settings · elements34·checkchange | Perform Task(_PWMToggle); Wait; Perform Task(_LoadedPWMToggle) |
| task534 | L15151 | AAB Superdimming Settings · elements33·checkchange | Perform Task(_PWMToggle); Wait; Perform Task(_LoadedPWMToggle) |
| task536 | L15261 | AAB Misc Settings · elements5·longclick | Flash(Sets the highest brightness level the screen …) |
| task537 | L15286 | AAB Reactivity Settings · elements22·click | Perform Task(_ExitButton) |
| task538 | L15308 | AAB Misc Settings · elements14·longclick | Perform Task(_ExitButton) |
| task539 | L15329 | AAB Experiment Settings · elements15·valueselected | Variable Set(%aab_scaletransitionfactor) |
| task550 | L17293 | AAB Power Draw Graph · props·key | Show Scene(AAB Debug Scene); Wait; Destroy Scene(AAB Power Draw Graph) |
| task568 | L20886 | AAB Superdimming Settings · props·key | Destroy Scene(AAB Superdimming Settings) |
| task590 | L24045 | AAB Alpha Graph · props·key | Show Scene(AAB Misc Settings); Wait; Destroy Scene(AAB Alpha Graph) |
| task591 | L24077 | AAB Circadian Dimming Graph · props·key | Show Scene(AAB Superdimming Settings); Perform Task(_LoadedDimmingToggle); Wait; Destroy Scene(AAB Circadian Dimming Graph) |
| task593 | L24429 | AAB Reactivity Settings · props·key | Destroy Scene(AAB Reactivity Settings) |
| task594 | L24439 | AAB Taper Graph · props·key | Show Scene(AAB Experiment Settings); Wait; Destroy Scene(AAB Taper Graph) |
| task595 | L24472 | AAB User Guide · props·key | Destroy Scene(AAB User Guide); Wait; Show Scene(AAB Menu) |
| task596 | L24504 | AAB About · props·key | Show Scene(AAB Menu); Wait; Destroy Scene(AAB About) |
| task597 | L24535 | AAB Experiment Settings · props·key | Destroy Scene(AAB Experiment Settings) |
| task598 | L24545 | AAB Misc Settings · props·key | Destroy Scene(AAB Misc Settings) |
| task599 | L24555 | AAB Reactivity Graph · props·key | Show Scene(AAB Reactivity Settings); Wait; Destroy Scene(AAB Reactivity Graph) |
| task600 | L24587 | AAB Debug Scene · props·key | Show Scene(AAB Menu); Wait; Destroy Scene(AAB Debug Scene) |
| task602 | L24792 | AAB Brightness Settings · props·key | Destroy Scene(AAB Brightness Settings) |
| task603 | L24802 | AAB Experiment Graph · props·key | Show Scene(AAB Experiment Settings); Perform Task(_LoadedExperimentTogglesV2); Wait; Destroy Scene(AAB Experiment Graph) |
| task604 | L24850 | AAB Chart.Js License · props·key | Show Scene(AAB Menu); Wait; Destroy Scene(AAB Chart.Js License) |
| task605 | L24882 | AAB Dimming Graph · props·key | Show Scene(AAB Superdimming Settings); Wait; Destroy Scene(AAB Dimming Graph) |
| task606 | L24914 | AAB Menu · props·key | Perform Task(_KeyEventBackMenu) |
| task607 | L24936 | AAB Superdimming Settings · elements29·focuschange | If[%focused = false]; Rotate Image(AAB Superdimming Settings); Stop; If[%new_val > 65]; Flash(⚠️ Dimming strength will be clamped to minimu…); End If; End If |
| task608 | L25017 | AAB Superdimming Settings · elements29·valueselected | Variable Set(%aab_dimmingstrength) |
| task609 | L25033 | AAB Superdimming Settings · elements15·focuschange | If[%focused = false]; If[%new_val < 0.00001]; Flash(⚠️ Exponent cannot be set to 0 or lower!); End If; End If |
| task610 | L25084 | AAB Superdimming Settings · elements36·focuschange | If[%focused = false]; If[%new_val < %AAB_MinBright]; Flash(⚠️ Dimming threshold cannot be set outside br…); End If; End If |
| task611 | L25141 | AAB Superdimming Settings · elements31·focuschange | If[%focused = false]; If[%new_val < 0.2]; Flash(⚠️ Not set!…); End If; End If |
| task612 | L25199 | AAB Misc Settings · elements28·focuschange | If[%focused = false]; If[%new_val < 0.0001]; Flash(Delta factor cannot be 0 or lower! Field not …); End If; End If |
| task613 | L25250 | AAB Brightness Settings · elements7·focuschange | If[%focused = false]; If[%new_val isSet]; Perform Task(_UpdateBrightnessFormulae); Perform Task(_UpdateStaticSceneElements); Perform Task(_RedInvalidFormulae); End If; End If |
| task614 | L25330 | AAB Brightness Settings · elements20·focuschange | If[%focused = false]; If[%new_val isSet]; Perform Task(_UpdateBrightnessFormulae); Perform Task(_UpdateStaticSceneElements); Perform Task(_RedInvalidFormulae); End If; If[%aab_zone1end < %aab_form2c]; Flash(Zone 2 Offse… |
| task615 | L25448 | AAB Brightness Settings · elements12·focuschange | If[%focused = false]; If[%new_val isSet]; Perform Task(_UpdateBrightnessFormulae); Perform Task(_UpdateStaticSceneElements); Perform Task(_RedInvalidFormulae); End If; End If |
| task616 | L25528 | AAB Brightness Settings · elements14·focuschange | If[%focused = false]; If[%new_val > %aab_zone1end]; End If; Flash(Zone 2 Offset cannot exceed Zone 1 End!); Element Visibility(AAB Brightness Settings); Else; Element Visibility(AAB Brightness Settings); Perform Task(_U… |
| task617 | L25652 | AAB Brightness Settings · elements19·focuschange | If[%focused = false]; If[%new_val < %aab_zone1end]; Flash(Zone 2 End cannot be smaller than Zone 1 End!); Else; Perform Task(_UpdateBrightnessFormulae); Perform Task(_UpdateStaticSceneElements); Perform Task(_RedInvalid… |
| task621 | L26607 | AAB Brightness Graph · props·key | If[%suggest = true]; Variable Clear(%suggest*); Perform Task(Advanced Auto Brightness); Destroy Scene(AAB Brightness Graph); Else; Multiple Variables Set; Perform Task(_AdaptiveBrightnessSceneSize V4); Perform Task(_Loa… |
| task632 | L28716 | AAB Profile · props·key | Show Scene(AAB Menu); Wait; Destroy Scene(AAB Profile) |
| task651 | L32230 | AAB Brightness Graph · elements7·click | Perform Task(_SuggestCurveParameters V24 (Hybrid)); Wait; Destroy Scene(AAB Brightness Graph) |
| task658 | L33306 | AAB Experiment Settings · elements3·longclick | Flash(Controls how wide the scale shifts over the d…) |
| task660 | L33366 | AAB Experiment Settings · elements16·valueselected | Variable Set(%aab_scalesteepness) |
| task662 | L33892 | AAB Experiment Settings · elements14·longclick | Flash(Controls the sharpness of the transition curv…) |
| task664 | L34471 | AAB Experiment Settings · elements4·valueselected | Variable Set(%aab_scalespread) |
| task665 | L34487 | AAB Experiment Settings · elements7·click | If[%aab_scalespread isNotSet]; Flash(⚠️ One or more values haven't been set. Can't…); Stop; Else; Perform Task(_SaveButtonExperiment); End If |
| task666 | L34561 | AAB Experiment Settings · elements8·click | Perform Task(_ExitButton) |
| task667 | L34583 | AAB Experiment Settings · elements8·longclick | Perform Task(_ExitButton) |
| task669 | L34604 | AAB Experiment Graph · elements3·click | Perform Task(_SaveButtonExperiment) |
| task670 | L34626 | AAB Experiment Settings · elements35·longclick | Flash(Select the date and/or location for the sunri…) |
| task671 | L34651 | AAB Experiment Graph · elements5·click | Show Scene(AAB Experiment Settings); Perform Task(_LoadedExperimentTogglesV2); Wait; Destroy Scene(AAB Experiment Graph) |
| task672 | L34700 | AAB Experiment Settings · elements18·longclick | Flash(Enables or disables the entire experimental c…) |
| task674 | L34725 | AAB Experiment Settings · elements10·click | If[%aab_scaletransitionfactor > 0.5]; Flash(⚠️ Graph might be non-sensical due to scale t…); End If; Flash(Please be patient. Generating the graph might…); Variable Clear(%AAB_SunLastDate); Perform Task(Dynamic Scale V1… |
| task675 | L34860 | AAB Experiment Settings · elements12·click | Variable Clear(%AAB_SetupComplete); Variable Set(%reset); Perform Task(Initialize AAB Defaults); Variable Clear(%reset); Variable Set(%AAB_SetupComplete); Multiple Variables Set; Perform Task(_ExperimentScene) |
| task676 | L34953 | AAB Experiment Settings · elements19·checkchange | Perform Task(_ExperimentUIToggle) |
| task677 | L34975 | AAB Experiment Settings · elements17·checkchange | Perform Task(_ExperimentUIToggle) |
| task678 | L34997 | AAB Experiment Settings · elements13·longclick | Flash(Adjusts the duration of the transition betwee…) |
| task679 | L35022 | AAB Taper Graph · elements3·click | Perform Task(_SaveButtonExperiment) |
| task680 | L35044 | AAB Taper Graph · elements5·click | Show Scene(AAB Experiment Settings); Perform Task(_LoadedExperimentTogglesV2); Wait; Destroy Scene(AAB Taper Graph) |
| task681 | L35093 | AAB Experiment Settings · elements38·click | Element Value(AAB Experiment Settings); Element Value(AAB Experiment Settings) |
| task682 | L35116 | AAB Misc Settings · elements34·longclick | Flash(Use notifications to keep the service alive a…) |
| task683 | L35141 | AAB Experiment Settings · elements36·click | Element Value(AAB Experiment Settings); Element Value(AAB Experiment Settings) |
| task684 | L35163 | AAB Misc Settings · elements31·longclick | Flash(Current throttle is a time based gatekeeper f……) |
| task685 | L35190 | AAB About · elements1·click | Perform Task(_MenuScene); Wait; Destroy Scene(AAB About) |
| task686 | L35224 | AAB Experiment Settings · elements23·click | Flash(Please be patient. Generating the graph might…); Perform Task(_GenerateCompressionGraph (Java)); Multiple Variables Set; Perform Task(_AdaptiveBrightnessSceneSize V4); Hide Scene(AAB Experiment Settings) |
| task687 | L35304 | AAB Experiment Settings · elements25·longclick | Flash(Sets the brightness level (%AAB_MinBright-%AA…) |
| task688 | L35329 | AAB Experiment Settings · elements31·longclick | If[%AAB_Package = net.dinglisch.android.ta…]; Flash(Use a quick settings tile set to position 3 (…); Else; Flash(This only works for Tasker. It does nothing f…); End If |
| task689 | L35389 | AAB Experiment Settings · elements26·valueselected | If[%aab_scaletapermidpoint < %AAB_MaxBright]; Variable Set(%aab_scaletapermidpoint); Else; Flash(⚠️ Taper midpoint cannot exceed current maxim…); End If |
| task690 | L35440 | AAB Experiment Settings · elements27·longclick | Flash(Controls the slope of the dynamic range compr…) |
| task691 | L35465 | AAB Experiment Settings · elements28·valueselected | Variable Set(%aab_scaletapersteepness) |
| task693 | L35624 | AAB Misc Settings · elements37·click | Perform Task(_NotifyToggle) |
| task694 | L35646 | AAB Experiment Settings · elements30·longclick | Flash(Displays the current (un)compressed scale fac…) |
| task695 | L35671 | AAB Misc Settings · elements35·checkchange | Perform Task(_NotifyToggle) |
| task697 | L35927 | AAB Experiment Settings · elements32·checkchange | Perform Task(_QSExperimentUIToggle) |
| task699 | L36452 | AAB Experiment Settings · elements33·checkchange | Perform Task(_QSExperimentUIToggle) |
| task701 | L36713 | AAB Reactivity Settings · elements13·valueselected | Variable Set(%aab_threshdark_c); Variable Set(%aab_threshdark) |
| task702 | L36739 | AAB Superdimming Settings · elements30·longclick | Flash(Gamma-like exponent. Controls the shape of th……) |
| task704 | L37156 | AAB Reactivity Settings · elements14·valueselected | Variable Set(%aab_threshdim_c); Variable Set(%aab_threshdim) |
| task708 | L38257 | AAB Reactivity Settings · elements15·valueselected | Variable Set(%aab_threshbright_c); Variable Set(%aab_threshbright) |
| task709 | L38283 | AAB Reactivity Settings · elements16·valueselected | Variable Set(%aab_threshsteepness) |
| task710 | L38299 | AAB Reactivity Settings · elements20·click | Perform Task(_SaveButtonReactivity); Multiple Variables Set |
| task711 | L38348 | AAB Reactivity Settings · elements21·longclick | Flash(Brightness is only changed when the change in…) |
| task712 | L38373 | AAB Reactivity Settings · elements10·longclick | Variable Set(%aab_10xthreshmidpoint); Flash(The log-transformed lux level where the syste……) |
| task713 | L38410 | AAB Reactivity Settings · elements23·valueselected | Variable Set(%aab_threshmidpoint) |
| task714 | L38426 | AAB Misc Settings · elements20·valueselected | Variable Set(%aab_animsteps); Variable Set(%aab_throttle); If[%aab_throttle < 1001]; Variable Set(%aab_throttle); End If |
| task715 | L38475 | AAB Misc Settings · elements22·valueselected | If[%aab_minwait < %aab_maxwait]; Variable Set(%aab_minwait); Else; Wait; Variable Set(%aab_maxwait); Flash(Minimum wait cannot exceed maximum wait!); c50(AAB Misc Settings); End If |
| task716 | L38552 | AAB Misc Settings · elements23·valueselected | If[%aab_maxwait > %aab_minwait]; Variable Set(%aab_maxwait); Variable Set(%aab_throttle); End If; Else; Wait; Variable Set(%aab_minwait); Flash(Maximum wait cannot be lower than minimum wai…); c50(AAB Misc Settings); En… |
| task717 | L38662 | AAB User Guide · elements1·click | Perform Task(_MenuScene); Wait; Destroy Scene(AAB User Guide) |
| task718 | L38696 | AAB Chart.Js License · elements1·click | Perform Task(_MenuScene); Wait; Destroy Scene(AAB Chart.Js License) |
| task719 | L38730 | AAB Reactivity Settings · elements6·longclick | Flash(The reactivity level in complete darkness. A …) |
| task720 | L38755 | AAB Reactivity Settings · elements7·longclick | Flash(The baseline reactivity for most situations. …) |
| task721 | L38780 | AAB Reactivity Settings · elements8·longclick | Flash(The baseline reactivity for bright, outdoor l…) |
| task722 | L38805 | AAB Reactivity Settings · elements9·longclick | Flash(Controls how quickly the reactivity changes a…) |
| task723 | L38830 | AAB Misc Settings · elements3·longclick | Flash(Sets the lowest brightness level the screen w…) |
| task724 | L38855 | AAB Brightness Settings · elements5·click | Perform Task(_UpdateBrightnessFormulae); Perform Task(_UpdateStaticSceneElements); Variable Set(%array_size); If[%suggest = true]; Element Value(AAB Brightness Graph); Element Value(AAB Brightness Graph); Else[%array_si… |
| task725 | L39090 | AAB Brightness Settings · elements6·longclick | Flash(Controls how quickly brightness rises in dim …) |
| task726 | L39115 | AAB Misc Settings · elements16·longclick | Flash(The number of steps in a brightness change an…) |
| task727 | L39140 | AAB Misc Settings · elements19·longclick | Flash(The shortest time (in milliseconds) between a…) |
| task728 | L39165 | AAB Misc Settings · elements18·longclick | Flash(The longest time (in milliseconds) between an…) |
| task729 | L39190 | AAB Reactivity Settings · elements27·longclick | Flash(Enable this ONLY if auto-brightness seems unr…) |
| task730 | L39215 | AAB Misc Settings · elements8·longclick | Flash(A global multiplier for the entire brightness…) |
| task731 | L39240 | AAB Reactivity Settings · elements29·checkchange | Perform Task(_ReactivityToggle) |
| task732 | L39262 | AAB Misc Settings · elements7·longclick | Flash(A simple brightness boost or cut. This value …) |
| task733 | L39287 | AAB Reactivity Settings · elements28·checkchange | Perform Task(_ReactivityToggle) |
| task734 | L39309 | AAB Brightness Settings · elements7·valueselected | Variable Set(%aab_form1a) |
| task735 | L39325 | AAB Reactivity Settings · elements25·click | Variable Clear(%AAB_SetupComplete); Variable Set(%reset); Perform Task(Initialize AAB Defaults); Variable Clear(%reset); Variable Set(%AAB_SetupComplete); Multiple Variables Set; Destroy Scene(AAB Reactivity Settings); … |
| task736 | L39422 | AAB Brightness Settings · elements9·longclick | Flash(Hinge point to enable smooth transition from …) |
| task737 | L39447 | AAB Brightness Settings · elements11·longclick | Flash(Higher values give a major boost in medium li…) |
| task738 | L39472 | AAB Misc Settings · elements30·click | Variable Clear(%AAB_SetupComplete); Variable Set(%reset); Perform Task(Initialize AAB Defaults); Variable Clear(%reset); Variable Set(%AAB_SetupComplete); Multiple Variables Set; Perform Task(_MiscScene) |
| task739 | L39570 | AAB Brightness Settings · elements12·valueselected | If[%new_val isSet]; Variable Set(%aab_form2b); End If |
| task740 | L39599 | AAB Misc Settings · elements26·longclick | Flash(Controls how much to smooth out sensor readin…) |
| task741 | L39624 | AAB Brightness Settings · elements13·longclick | Flash(Subtle but powerful "offset" for the midrange…) |
| task742 | L39649 | AAB Misc Settings · elements28·valueselected | If[%new_val < 0.0001]; Stop; Else; Variable Set(%aab_deltafactor); End If |
| task743 | L39686 | AAB Misc Settings · elements29·click | Perform Task(_GenerateAlphaGraph (Java)); Multiple Variables Set; Perform Task(_AdaptiveBrightnessSceneSize V4); Hide Scene(AAB Misc Settings) |
| task744 | L39747 | AAB Brightness Settings · elements14·valueselected | Variable Set(%aab_form2c); Variable Set(%aab_form3a) |
| task746 | L39773 | AAB Alpha Graph · elements3·click | Perform Task(_SaveButtonMisc) |
| task747 | L39795 | AAB Brightness Settings · elements15·longclick | Flash(Sets the lux level where 'dim light' ends and…) |
| task748 | L39820 | AAB Alpha Graph · elements5·click | Multiple Variables Set; Perform Task(_AdaptiveBrightnessSceneSize V4); Perform Task(_LoadedMiscAuto); Destroy Scene(AAB Alpha Graph) |
| task749 | L39880 | AAB Brightness Settings · elements16·longclick | Flash(Hinge point to enable smooth transition from …) |
| task750 | L39905 | AAB Brightness Settings · elements18·longclick | Flash(Sets the lux level where 'indoor light' ends …) |
| task751 | L39930 | AAB Brightness Settings · elements19·valueselected | If[%new_val isSet]; Variable Set(%aab_zone2end); End If |
| task752 | L39959 | AAB Brightness Settings · elements20·valueselected | If[%new_val isSet]; Variable Set(%aab_form2d); Variable Set(%aab_zone1end); End If; If[%aab_zone1end < %aab_form2c]; Variable Set(%aab_form2c); Element Visibility(AAB Brightness Settings); Perform Task(_RedInvalidFormul… |
