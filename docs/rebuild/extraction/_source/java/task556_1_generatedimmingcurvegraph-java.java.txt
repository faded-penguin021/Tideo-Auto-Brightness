/* === EXTRACTED JAVA BLOCK (S1 ground-truth, verbatim, html.unescape-decoded) ===
 * Task: task556 "_GenerateDimmingCurveGraph (Java)"
 * Block: #1 of 1 in this task
 * XML: Advanced_Auto_Brightness_V3.3.prj_9.xml  arg0 (Java source) L18360-L18442; <code>474</code> at L18359
 * Output: none (uses tasker.setVariable() directly)
 * %vars consumed (S4 parameters): %aab_dimmingexponent, %aab_dimmingstrength, %aab_dimmingthreshold, %aab_minbright
 * ============================================================================ */
/* --- Java Code for Dimming Curve Graph Generation --- */

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/* --- Read all necessary Tasker variables --- */
double aab_minbright = %aab_minbright;
double aab_dimmingthreshold = %aab_dimmingthreshold;
double aab_dimmingexponent = %aab_dimmingexponent;
double aab_dimmingstrength = %aab_dimmingstrength;

/* A6-A10: Determine the max threshold for the loop */
double max_threshold = (aab_dimmingthreshold < 15) ? 15 : aab_dimmingthreshold;

double max_dim_strength = aab_minbright; // A11: Initialize

/* --- Initialize lists to hold our data points --- */
List brightness_labels_list = new ArrayList();
List dim_data_points_list = new ArrayList();
List ref_dim_data_points_list = new ArrayList();
List dim_ds_points_list = new ArrayList();

/* --- The main calculation loop (replicates A15-A34) --- */
/* The loop now runs from min bright to max threshold, incrementing by 1 */
for (double target_brightness = aab_minbright; target_brightness <= max_threshold; target_brightness++) {
    double dim_progress = 0.0;
    double ref_dim_progress = 0.0;

    /* A16-A20: Calculate dim_progress */
    if (target_brightness < aab_dimmingthreshold) {
        dim_progress = Math.pow(1.0 - ((target_brightness - aab_minbright) / (aab_dimmingthreshold - aab_minbright)), aab_dimmingexponent);
    } else {
        dim_progress = 0.0;
    }

    /* A21-A25: Calculate ref_dim_progress */
    if (target_brightness < 15) {
        ref_dim_progress = Math.pow(1.0 - (target_brightness / 15.0), 2.5);
    } else {
        ref_dim_progress = 0.0;
    }

    /* A26: Calculate dim_shell */
    double dim_shell = aab_dimmingstrength * dim_progress;

    /* A27-A29: Find the max_dim_strength at the first step */
    if (target_brightness == aab_minbright) {
        max_dim_strength = dim_shell;
    }

    /* Add the calculated points to our lists */
    brightness_labels_list.add(new Double(target_brightness));
    dim_data_points_list.add(new Double(dim_progress * 100.0));
    ref_dim_data_points_list.add(new Double(ref_dim_progress * 100.0));
    dim_ds_points_list.add(new Double(dim_shell));
}

/* --- Join the lists into comma-separated strings (replicates A35-A38) --- */
StringBuffer brightness_labels_str = new StringBuffer();
StringBuffer dim_data_points_str = new StringBuffer();
StringBuffer ref_dim_data_points_str = new StringBuffer();
StringBuffer dim_ds_points_str = new StringBuffer();

for (int i = 0; i < brightness_labels_list.size(); i++) {
    if (i > 0) {
        brightness_labels_str.append(",");
        dim_data_points_str.append(",");
        ref_dim_data_points_str.append(",");
        dim_ds_points_str.append(",");
    }
    brightness_labels_str.append(new BigDecimal(((Double) brightness_labels_list.get(i)).doubleValue()).setScale(0, BigDecimal.ROUND_HALF_UP).toString());
    dim_data_points_str.append(new BigDecimal(((Double) dim_data_points_list.get(i)).doubleValue()).setScale(3, BigDecimal.ROUND_HALF_UP).toString());
    ref_dim_data_points_str.append(new BigDecimal(((Double) ref_dim_data_points_list.get(i)).doubleValue()).setScale(3, BigDecimal.ROUND_HALF_UP).toString());
    dim_ds_points_str.append(new BigDecimal(((Double) dim_ds_points_list.get(i)).doubleValue()).setScale(3, BigDecimal.ROUND_HALF_UP).toString());
}

/* --- Set all the variables Tasker needs for the HTML replacement --- */
tasker.setVariable("brightness_labels", brightness_labels_str.toString());
tasker.setVariable("dim_data_points", dim_data_points_str.toString());
tasker.setVariable("ref_dim_data_points", ref_dim_data_points_str.toString());
tasker.setVariable("dim_ds_points", dim_ds_points_str.toString());
tasker.setVariable("max_dim_strength", String.valueOf(max_dim_strength));
