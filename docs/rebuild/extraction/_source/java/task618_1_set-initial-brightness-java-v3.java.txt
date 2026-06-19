/* === EXTRACTED JAVA BLOCK (S1 ground-truth, verbatim, html.unescape-decoded) ===
 * Task: task618 "Set Initial Brightness (Java) V3"
 * Block: #1 of 2 in this task
 * XML: Advanced_Auto_Brightness_V3.3.prj_9.xml  arg0 (Java source) L25827-L25845; <code>474</code> at L25826
 * Output: none (uses tasker.setVariable() directly)
 * %vars consumed (S4 parameters): %as_values1
 * ============================================================================ */
/* --- Java Code for Initial Lux Setting --- */

/* Failsafe in case %as_values1 is not a valid number */
double raw_lux = 0.0;
try {
    /* Use direct assignment for the input */
    raw_lux = %as_values1;
} catch (Exception e) {
    /* If the variable is unset or invalid, raw_lux remains 0 */
}

/* Perform both rounding steps at once */
long smoothed_lux_0_digits = Math.round(raw_lux);
double smoothed_lux_2_digits = Math.round(raw_lux * 100.0) / 100.0;

/* --- MODIFIED SECTION --- */
/* Set the Tasker variables directly */
tasker.setVariable("smoothed_lux", String.valueOf(smoothed_lux_0_digits));
tasker.setVariable("SmoothedLux", String.valueOf(smoothed_lux_2_digits));
