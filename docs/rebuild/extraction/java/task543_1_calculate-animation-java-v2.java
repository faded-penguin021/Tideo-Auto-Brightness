/* === EXTRACTED JAVA BLOCK (S1 ground-truth, verbatim, html.unescape-decoded) ===
 * Task: task543 "Calculate Animation (Java) V2"
 * Block: #1 of 1 in this task
 * XML: Advanced_Auto_Brightness_V3.3.prj_9.xml  arg0 (Java source) L15879-L15916; <code>474</code> at L15878
 * Output: none (uses tasker.setVariable() directly)
 * %vars consumed (S4 parameters): %AAB_AnimSteps, %AAB_MaxWait, %AAB_MinWait, %par1
 * ============================================================================ */
/* --- Java Code for Animation Calculation --- */

/* Read literal values inserted by Tasker */
double p1 = %par1;
double max_steps = %AAB_AnimSteps;
double min_wait = %AAB_MinWait;
double max_wait = %AAB_MaxWait;

/* Clamp p1 just in case */
if (p1 < 0.0) p1 = 0.0;
if (p1 > 1.0) p1 = 1.0;

/* Compute loops and wait, rounding both to 0 digits (whole numbers) */
long loops = Math.round(1.0 + p1 * (max_steps - 1.0));
double raw_wait = (1.0 - p1) * (max_wait - min_wait) + min_wait;
long wait = Math.round(raw_wait);

/* Compute throttle, also rounding to a whole number */
long aabThrottle = Math.round(loops * wait + 10.0);

/* Add CycleTime if it exists and is <= 2000 */
String cycleTimeStr = tasker.getVariable("AAB_CycleTime");
if (cycleTimeStr != null) {
    try {
        double cycleTime = Double.parseDouble(cycleTimeStr);
        if (cycleTime <= 2000.0) {
            aabThrottle += Math.round(cycleTime);
        }
    } catch (NumberFormatException e) {
        /* Ignore invalid number format */
    }
}

/* --- CORRECTED SECTION --- */
/* Set the Tasker variables directly using the correct syntax */
tasker.setVariable("loops", String.valueOf(loops));
tasker.setVariable("wait", String.valueOf(wait));
tasker.setVariable("AAB_Throttle", String.valueOf(aabThrottle));
