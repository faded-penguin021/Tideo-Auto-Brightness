/* === EXTRACTED JAVA BLOCK (S1 ground-truth, verbatim, html.unescape-decoded) ===
 * Task: task548 "Dynamic Range Compressed Scale (Java) V2"
 * Block: #1 of 1 in this task
 * XML: Advanced_Auto_Brightness_V3.3.prj_9.xml  arg0 (Java source) L16631-L16698; <code>474</code> at L16630
 * Output: arg1 var %dr_results
 * %vars consumed (S4 parameters): %AAB_MaxBright, %AAB_MinBright, %AAB_Offset, %AAB_ScaleDynamic, %AAB_ScaleTaperMidpoint, %AAB_ScaleTaperSteepness, %par1
 * ============================================================================ */
/* --- Java Code for Dynamic Range Compressed Scale (Modern) --- */

/* Read literal values inserted by Tasker */
double mappedBrightness = %par1;
double AAB_MinBright = %AAB_MinBright;
double AAB_ScaleDynamic = %AAB_ScaleDynamic;
double AAB_MaxBright = %AAB_MaxBright;
double AAB_Offset = %AAB_Offset;
double AAB_ScaleTaperMidpoint = %AAB_ScaleTaperMidpoint;
double AAB_ScaleTaperSteepness = %AAB_ScaleTaperSteepness;

/* Handle the special case where mappedBrightness is zero. */
if (mappedBrightness == 0.0) {
    double calculatedBrightness = AAB_MinBright;
    double scaleForGlobal = AAB_ScaleDynamic;
    
    /* Set the global variable directly */
    tasker.setVariable("AAB_ScaleDynamicCompress", String.valueOf(scaleForGlobal));
    
    /* Return only the primary result */
    return calculatedBrightness;

} else {
    /* Calculate the sigmoid-based compression factor and taper effect. */
    double midpoint = AAB_ScaleTaperMidpoint;
    double steepness = AAB_ScaleTaperSteepness;
    
    double exponent_raw = -steepness * (mappedBrightness - midpoint);
    double exponent = Math.round(exponent_raw * 1000.0) / 1000.0;
    
    double compression_factor_raw = 1.0 / (1.0 + Math.exp(exponent));
    double compression_factor = Math.round(compression_factor_raw * 1000.0) / 1000.0;
    
    double taper_effect_raw = 1.0 - compression_factor;
    double taper_effect = Math.round(taper_effect_raw * 1000.0) / 1000.0;
    
    /* Calculate the tapered scale and its dynamic limits (cap and floor). */
    double tapered_scale_raw = 1.0 + (AAB_ScaleDynamic - 1.0) * taper_effect;
    double tapered_scale = Math.round(tapered_scale_raw * 1000.0) / 1000.0;
    
    double dynamic_cap_raw = AAB_MaxBright / mappedBrightness;
    double dynamic_cap = Math.round(dynamic_cap_raw * 1000.0) / 1000.0;
    
    double dynamic_floor_raw = 2.0 - dynamic_cap;
    double dynamic_floor = Math.round(dynamic_floor_raw * 1000.0) / 1000.0;
    
    /* Apply the cap or floor to get the final effective_scale. */
    double effective_scale;
    if (AAB_ScaleDynamic > 1.0) {
        effective_scale = Math.min(tapered_scale, dynamic_cap);
    } else {
        effective_scale = Math.max(tapered_scale, dynamic_floor);
    }
    effective_scale = Math.round(effective_scale * 1000.0) / 1000.0;

    double scaleForGlobal = effective_scale;
    
    /* Calculate the final brightness and round to 1 decimal place. */
    double calculated_brightness_raw = mappedBrightness * effective_scale + AAB_Offset;
    double calculated_brightness = Math.round(calculated_brightness_raw * 10.0) / 10.0;
    
    /* --- MODIFIED SECTION --- */
    /* Set the global variable directly */
    tasker.setVariable("AAB_ScaleDynamicCompress", String.valueOf(scaleForGlobal));
    
    /* Return only the primary result */
    return calculated_brightness;
}
