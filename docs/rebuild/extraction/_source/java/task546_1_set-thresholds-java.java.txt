/* === EXTRACTED JAVA BLOCK (S1 ground-truth, verbatim, html.unescape-decoded) ===
 * Task: task546 "Set Thresholds (Java)"
 * Block: #1 of 1 in this task
 * XML: Advanced_Auto_Brightness_V3.3.prj_9.xml  arg0 (Java source) L16482-L16534; <code>474</code> at L16481
 * Output: none (uses tasker.setVariable() directly)
 * %vars consumed (S4 parameters): %AAB_LastRawLux, %par1, %par2
 * ============================================================================ */
/* --- Java Code for Set Thresholds --- */

import java.math.BigDecimal;

/* Read all necessary input variables */
double par1 = %par1;
double par2 = %par2;
double LastRawLux = %AAB_LastRawLux;

String thresh_dynamic_str;
String thresh_low_str;
String thresh_high_str;

/* A2: If [ %par1 < 0.2 ] */
if (par1 < 0.2) {
    /* A3: Set hardcoded values */
    thresh_dynamic_str = "1";
    thresh_low_str = "0";
    thresh_high_str = "0.1";

} else {
    /* Pre-calculate the raw values since the formulas are the same */
    double dynamic_thresh_raw = par2 * 100.0;
    double thresh_abs_low_raw = LastRawLux * (1.0 - (dynamic_thresh_raw / 100.0));
    double thresh_abs_high_raw = LastRawLux * (1.0 + (dynamic_thresh_raw / 100.0));

    /* A4: Else If [ %par1 < 10 ] */
    if (par1 < 10) {
        /* A5: Round all results to 2 decimal places */
        BigDecimal dt_bd = new BigDecimal(dynamic_thresh_raw).setScale(2, BigDecimal.ROUND_HALF_UP);
        BigDecimal tal_bd = new BigDecimal(thresh_abs_low_raw).setScale(2, BigDecimal.ROUND_HALF_UP);
        BigDecimal tah_bd = new BigDecimal(thresh_abs_high_raw).setScale(2, BigDecimal.ROUND_HALF_UP);

        thresh_dynamic_str = dt_bd.toString();
        thresh_low_str = tal_bd.toString();
        thresh_high_str = tah_bd.toString();

    } else {
        /* A7: Round all results to 0 decimal places */
        BigDecimal dt_bd = new BigDecimal(dynamic_thresh_raw).setScale(0, BigDecimal.ROUND_HALF_UP);
        BigDecimal tal_bd = new BigDecimal(thresh_abs_low_raw).setScale(0, BigDecimal.ROUND_HALF_UP);
        BigDecimal tah_bd = new BigDecimal(thresh_abs_high_raw).setScale(0, BigDecimal.ROUND_HALF_UP);

        thresh_dynamic_str = dt_bd.toString();
        thresh_low_str = tal_bd.toString();
        thresh_high_str = tah_bd.toString();
    }
}

/* Set the Tasker global variables directly */
tasker.setVariable("AAB_ThreshDynamic", thresh_dynamic_str);
tasker.setVariable("AAB_ThreshAbsLow", thresh_low_str);
tasker.setVariable("AAB_ThreshAbsHigh", thresh_high_str);
