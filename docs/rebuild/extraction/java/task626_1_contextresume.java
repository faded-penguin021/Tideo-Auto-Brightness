/* === EXTRACTED JAVA BLOCK (S1 ground-truth, verbatim, html.unescape-decoded) ===
 * Task: task626 "_ContextResume"
 * Block: #1 of 1 in this task
 * XML: Advanced_Auto_Brightness_V3.3.prj_9.xml  arg0 (Java source) L27356-L27366; <code>474</code> at L27355
 * Output: none (uses tasker.setVariable() directly)
 * %vars consumed (S4 parameters): (none)
 * ============================================================================ */

org.json.JSONObject state = new org.json.JSONObject();

String[] keys = {"AAB_Form1A", "AAB_Zone1End", "AAB_Form2A", "AAB_Form2B", "AAB_Form2C", "AAB_Zone2End", "AAB_Form3A", "AAB_MinBright", "AAB_MaxBright", "AAB_Scale", "AAB_Offset", "AAB_AnimSteps", "AAB_MinWait", "AAB_MaxWait", "AAB_DeltaFactor", "AAB_NotifyUse", "AAB_DetectOverrides", "AAB_ThreshDark", "AAB_ThreshDim", "AAB_ThreshBright", "AAB_ThreshSteepness", "AAB_ThreshMidpoint", "AAB_TrustUnreliable", "AAB_ScalingUse", "AAB_ScaleSpread", "AAB_ScaleTransitionFactor", "AAB_ScaleSteepness", "AAB_ScaleTaperMidpoint", "AAB_ScaleTaperSteepness", "AAB_QSUse", "AAB_DimmingEnabled", "AAB_DimmingThreshold", "AAB_DimmingStrength", "AAB_DimmingExponent", "AAB_DimSpread", "AAB_PWMSensitive", "AAB_PWMExp", "AAB_ProfileUser", "AAB_CurrentActiveProfile"};

for (String k : keys) {
    String val = tasker.getVariable(k);
    state.put(k, (val != null) ? val : "Unset");
}

tasker.setVariable("json_state", state.toString());
