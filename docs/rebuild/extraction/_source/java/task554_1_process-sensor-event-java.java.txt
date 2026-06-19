/* === EXTRACTED JAVA BLOCK (S1 ground-truth, verbatim, html.unescape-decoded) ===
 * Task: task554 "Process Sensor Event (Java)"
 * Block: #1 of 1 in this task
 * XML: Advanced_Auto_Brightness_V3.3.prj_9.xml  arg0 (Java source) L18133-L18173; <code>474</code> at L18132
 * Output: none (uses tasker.setVariable() directly)
 * %vars consumed (S4 parameters): %AAB_LastRawLux, %TIMEMS, %as_accuracy, %as_values1
 * ============================================================================ */
/* --- Java Code for Sensor Event Pre-Processing (Corrected) --- */

import java.math.BigDecimal;

/* A1: Set the cycle start time */
/* Read TIMEMS as a string and parse it as a long to handle the large number. */
long now = Long.parseLong("%TIMEMS");
tasker.setVariable("AAB_CycleStart", String.valueOf(now));

/*
 * A2 & A3: Process the raw lux value (%as_values1).
 * The try-catch block replaces the "If %as_values1 Set" condition.
 */
try {
    String rawSensorValue = "%as_values1";
    
    /* A2: Strip potential brackets from the string */
    String cleanedValue = rawSensorValue.replace("[", "").replace("]", "");
    
    /* A3: Convert to a number, round it, and set %AAB_LastRawLux */
    double luxDouble = Double.parseDouble(cleanedValue);
    BigDecimal luxBd = new BigDecimal(luxDouble).setScale(3, BigDecimal.ROUND_HALF_UP);
    tasker.setVariable("AAB_LastRawLux", luxBd.toString());
    
    /* Also update %as_values1 itself with the cleaned value for the next task */
    tasker.setVariable("as_values1", cleanedValue);
    
} catch (Exception e) {
    /* If %as_values1 is not set or is not a valid number, this block is skipped. */
}

/*
 * A4: Process the sensor accuracy.
 * The try-catch block replaces the "If %as_accuracy Set" condition.
 */
try {
    String accuracy = "%as_accuracy";
    tasker.setVariable("AAB_LastSensorAccuracy", accuracy);
} catch (Exception e) {
    /* If %as_accuracy is not set, this block is skipped. */
}
