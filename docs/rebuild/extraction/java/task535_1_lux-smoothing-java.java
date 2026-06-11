/* === EXTRACTED JAVA BLOCK (S1 ground-truth, verbatim, html.unescape-decoded) ===
 * Task: task535 "Lux Smoothing (Java)"
 * Block: #1 of 1 in this task
 * XML: Advanced_Auto_Brightness_V3.3.prj_9.xml  arg0 (Java source) L15205-L15248; <code>474</code> at L15204
 * Output: arg1 var %output
 * %vars consumed (S4 parameters): %AAB_DeltaFactor, %AAB_ThreshDynamic, %AAB_Zone1End, %par1, %par2
 * ============================================================================ */
/* --- Java Code for Lux Smoothing (Enhanced with Rounding) --- */

import java.math.BigDecimal;

/* Read literal values inserted by Tasker */
double par1 = %par1;
double par2 = %par2;
double AAB_ThreshDynamic = %AAB_ThreshDynamic;
double AAB_DeltaFactor = %AAB_DeltaFactor;
double AAB_Zone1End = %AAB_Zone1End;

/* A1: Calculate lux_delta */
double lux_delta_raw = Math.abs((par1 - par2) / (par2 + 1.0));
double lux_delta = Math.round(lux_delta_raw * 1000.0) / 1000.0;

/* A2: Calculate effective_delta */
double effective_delta_raw = lux_delta - (AAB_ThreshDynamic / 100.0);
double effective_delta = Math.round(effective_delta_raw * 1000.0) / 1000.0;

/* A3: Calculate lux_alpha */
double lux_alpha_raw = 1.0 - Math.exp(-AAB_DeltaFactor * effective_delta);
double lux_alpha = Math.round(lux_alpha_raw * 1000.0) / 1000.0;

/* A4: Calculate new_smoothed_lux (unrounded) */
double new_smoothed_lux_raw = (par1 * lux_alpha) + (par2 * (1.0 - lux_alpha));

/* --- NEW LOGIC: Using BigDecimal for readable rounding --- */

/* Create a BigDecimal object from the calculated double */
BigDecimal luxToRound = new BigDecimal(new_smoothed_lux_raw);

String final_lux_string;
if (new_smoothed_lux_raw < AAB_Zone1End) {
    /* Round to 2 decimal places using HALF_UP rounding (standard) */
    BigDecimal roundedLux = luxToRound.setScale(2, BigDecimal.ROUND_HALF_UP);
    final_lux_string = roundedLux.toString();
} else {
    /* Round to 0 decimal places. This will not have a ".0" */
    BigDecimal roundedLux = luxToRound.setScale(0, BigDecimal.ROUND_HALF_UP);
    final_lux_string = roundedLux.toString();
}

/* Return the final values, using the correctly formatted string */
return final_lux_string + "," + lux_alpha + "," + lux_delta;
