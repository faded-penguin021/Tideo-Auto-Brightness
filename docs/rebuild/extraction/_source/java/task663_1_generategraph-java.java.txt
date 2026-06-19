/* === EXTRACTED JAVA BLOCK (S1 ground-truth, verbatim, html.unescape-decoded) ===
 * Task: task663 "_GenerateGraph (Java)"
 * Block: #1 of 2 in this task
 * XML: Advanced_Auto_Brightness_V3.3.prj_9.xml  arg0 (Java source) L33945-L33996; <code>474</code> at L33944
 * Output: none (uses tasker.setVariable() directly)
 * %vars consumed (S4 parameters): %AAB_Overrides
 * ============================================================================ */
/* --- Java Code to Format Override Data for Chart.js --- */

/* Create a buffer to hold the formatted data string */
scatter_data_str = new StringBuffer();

/* 
 * FIX: Do not rely on %AAB_Overrides(#) injection.
 * In exported apps, this injection can fail or cause syntax errors.
 * Instead, we manually loop through indices 1, 2, 3... until we find null.
*/
i = 1;

while (true) {
    /* Construct the variable name for the current index (e.g., AAB_Overrides1) */
    varName = "AAB_Overrides" + i;
    
    /* Retrieve the value using the tasker object */
    overrideData = tasker.getVariable(varName);

    /* If the variable returns null, we have reached the end of the array */
    if (overrideData == null) {
        break;
    }

    /* Process the valid data point */
    try {
        /* Read the "lux,brightness" string */
        parts = overrideData.split(",");
        
        if (parts.length == 2) {
            /* Add a comma if this is not the first data point added to the buffer */
            if (scatter_data_str.length() > 0) {
                scatter_data_str.append(",");
            }
            
            /* Append the data in the format {x:..., y:...} */
            /* Using .trim() ensures no accidental whitespace breaks the JSON */
            scatter_data_str.append("{x:" + parts[0].trim() + ",y:" + parts[1].trim() + "}");
        }
    } catch (Exception e) { 
        /* Ignore malformed data lines to prevent crashing */
    }
    
    /* Increment index for the next loop */
    i++;
    
    /* Safety brake: prevent infinite loop in case of corrupt memory (optional but safe) */
    if (i > 500) break;
}

/* Set the final variable that the HTML template will use */
tasker.setVariable("scatter_data", scatter_data_str.toString());
