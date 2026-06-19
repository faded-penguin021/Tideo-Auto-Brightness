/* === EXTRACTED JAVA BLOCK (S1 ground-truth, verbatim, html.unescape-decoded) ===
 * Task: task655 "_SetSuggestedVariables"
 * Block: #1 of 1 in this task
 * XML: Advanced_Auto_Brightness_V3.3.prj_9.xml  arg0 (Java source) L32592-L32613; <code>474</code> at L32591
 * Output: none (uses tasker.setVariable() directly)
 * %vars consumed (S4 parameters): (none)
 * ============================================================================ */
/* Sanitize input variables by replacing comma decimal separators with periods. */
String[] varNames = new String[]{
    "suggestion_form1a",
    "suggestion_zone1end",
    "suggestion_form2b",
    "suggestion_form2c",
    "suggestion_zone2end"
};

for (int i = 0; i < varNames.length; i++) {
    varName = varNames[i];
    currentValue = tasker.getVariable(varName);

    /* Process only if the variable is set. */
    if (currentValue != null) {
        /* Replace all occurrences of comma with a period. */
        sanitizedValue = currentValue.replace(',', '.');
        
        /* Update the variable in Tasker with the sanitized value. */
        tasker.setVariable(varName, sanitizedValue);
    }
}
