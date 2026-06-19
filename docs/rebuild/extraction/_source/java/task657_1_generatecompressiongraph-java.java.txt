/* === EXTRACTED JAVA BLOCK (S1 ground-truth, verbatim, html.unescape-decoded) ===
 * Task: task657 "_GenerateCompressionGraph (Java)"
 * Block: #1 of 1 in this task
 * XML: Advanced_Auto_Brightness_V3.3.prj_9.xml  arg0 (Java source) L32987-L33065; <code>474</code> at L32986
 * Output: none (uses tasker.setVariable() directly)
 * %vars consumed (S4 parameters): %AAB_MaxBright, %AAB_MinBright, %aab_scalespread, %aab_scaletapermidpoint, %aab_scaletapersteepness
 * ============================================================================ */
/* --- Java Code for Compression Graph Generation (Corrected) --- */

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/* --- Read all necessary Tasker variables --- */
spread_factor = %aab_scalespread / 100.0;
sim_day_scale = 1.0 + spread_factor;
sim_night_scale = 1.0 - spread_factor;
midpoint = %aab_scaletapermidpoint;
steepness = %aab_scaletapersteepness;
min_bright = %AAB_MinBright;
max_bright = %AAB_MaxBright;

/* --- Initialize lists to hold our data points --- */
day_scale_points_list = new ArrayList();
night_scale_points_list = new ArrayList();
brightness_labels_list = new ArrayList();

/* --- The main calculation loop --- */
for (double mapped_brightness = min_bright; mapped_brightness <= max_bright; mapped_brightness++) {
    
    /* Calculate the sigmoid taper effect */
    exponent = -steepness * (mapped_brightness - midpoint);
    compression_factor = 1.0 / (1.0 + Math.exp(exponent));
    taper_effect = 1.0 - compression_factor;

    /* --- THE FIX IS HERE --- */
    /* Replicate the original Tasker math: apply taper to the spread (scale - 1) */
    tapered_day_scale = 1.0 + (sim_day_scale - 1.0) * taper_effect;
    tapered_night_scale = 1.0 + (sim_night_scale - 1.0) * taper_effect;
    
    effective_day_scale = 0.0;
    effective_night_scale = 0.0;

    /* Apply dynamic caps and floors */
    if (mapped_brightness > 0) {
        dynamic_cap = max_bright / mapped_brightness;
        dynamic_floor = 2.0 - dynamic_cap;
        
        effective_day_scale = Math.min(tapered_day_scale, dynamic_cap);
        effective_night_scale = Math.max(tapered_night_scale, dynamic_floor);
    } else {
        effective_day_scale = sim_day_scale;
        effective_night_scale = sim_night_scale;
    }

    day_scale_points_list.add(new Double(effective_day_scale));
    night_scale_points_list.add(new Double(effective_night_scale));
    brightness_labels_list.add(new Double(mapped_brightness));
}

/* --- Join the lists into comma-separated strings --- */
day_scale_points_str = new StringBuffer();
night_scale_points_str = new StringBuffer();
brightness_labels_str = new StringBuffer();

for (i = 0; i < brightness_labels_list.size(); i++) {
    if (i > 0) {
        day_scale_points_str.append(",");
        night_scale_points_str.append(",");
        brightness_labels_str.append(",");
    }
    
    day_scale = new BigDecimal(((Double) day_scale_points_list.get(i)).doubleValue()).setScale(3, BigDecimal.ROUND_HALF_UP);
    day_scale_points_str.append(day_scale.toString());
    
    night_scale = new BigDecimal(((Double) night_scale_points_list.get(i)).doubleValue()).setScale(3, BigDecimal.ROUND_HALF_UP);
    night_scale_points_str.append(night_scale.toString());
    
    brightness_label = new BigDecimal(((Double) brightness_labels_list.get(i)).doubleValue()).setScale(0, BigDecimal.ROUND_HALF_UP);
    brightness_labels_str.append(brightness_label.toString());
}

/* --- Set the final variables for Tasker to use --- */
tasker.setVariable("day_scale_points", day_scale_points_str.toString());
tasker.setVariable("night_scale_points", night_scale_points_str.toString());
tasker.setVariable("brightness_labels", brightness_labels_str.toString());
