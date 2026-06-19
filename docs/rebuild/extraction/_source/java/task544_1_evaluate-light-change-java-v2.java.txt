/* === EXTRACTED JAVA BLOCK (S1 ground-truth, verbatim, html.unescape-decoded) ===
 * Task: task544 "Evaluate Light Change (Java) V2"
 * Block: #1 of 1 in this task
 * XML: Advanced_Auto_Brightness_V3.3.prj_9.xml  arg0 (Java source) L16063-L16100; <code>474</code> at L16062
 * Output: none (uses tasker.setVariable() directly)
 * %vars consumed (S4 parameters): %AAB_ThreshBright, %AAB_ThreshDark, %AAB_ThreshDim, %AAB_ThreshMidpoint, %AAB_ThreshSteepness, %AAB_Zone1End, %SmoothedLux, %par1
 * ============================================================================ */
/* --- Java Code for Dynamic Threshold Calculation --- */

/* Read all necessary Tasker variables */
double par1 = %par1;
double SmoothedLux = %SmoothedLux;
double AAB_ThreshDim = %AAB_ThreshDim;
double AAB_ThreshBright = %AAB_ThreshBright;
double AAB_ThreshSteepness = %AAB_ThreshSteepness;
double AAB_ThreshMidpoint = %AAB_ThreshMidpoint;
double AAB_ThreshDark = %AAB_ThreshDark;
double AAB_Zone1End = %AAB_Zone1End;

/* A15 & A16: Calculate relative_change */
double lux_difference = Math.abs(par1 - SmoothedLux);
double relative_change = Math.round((lux_difference / (SmoothedLux + 1.0)) * 1000.0) / 1000.0;

/* A17: Calculate the sigmoid threshold for brighter light */
double log_lux = Math.log10(SmoothedLux + 1.0);
double exponent = -AAB_ThreshSteepness * (log_lux - AAB_ThreshMidpoint);
double thresh_sig = AAB_ThreshDim + (AAB_ThreshBright - AAB_ThreshDim) / (1.0 + Math.exp(exponent));
thresh_sig = Math.round(thresh_sig * 1000.0) / 1000.0;

/* A18: Calculate the linear threshold for dim light */
double thresh_low = AAB_ThreshDark - ((AAB_ThreshDark - AAB_ThreshDim) / AAB_Zone1End) * SmoothedLux;
thresh_low = Math.round(thresh_low * 1000.0) / 1000.0;

/* A19-A23: Choose the correct threshold based on the current lux */
double dynamic_threshold;
if (SmoothedLux < AAB_Zone1End) {
    dynamic_threshold = thresh_low;
} else {
    dynamic_threshold = thresh_sig;
}

/* --- MODIFIED SECTION --- */
/* Set the Tasker variables directly */
tasker.setVariable("relative_change", String.valueOf(relative_change));
tasker.setVariable("dynamic_threshold", String.valueOf(dynamic_threshold));
