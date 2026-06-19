/* === EXTRACTED JAVA BLOCK (S1 ground-truth, verbatim, html.unescape-decoded) ===
 * Task: task703 "_GenerateReactivityGraph (Java)"
 * Block: #1 of 1 in this task
 * XML: Advanced_Auto_Brightness_V3.3.prj_9.xml  arg0 (Java source) L36848-L36946; <code>474</code> at L36847
 * Output: none (uses tasker.setVariable() directly)
 * %vars consumed (S4 parameters): %aab_threshbright, %aab_threshdark, %aab_threshdim, %aab_threshmidpoint, %aab_threshsteepness, %aab_zone1end, %lux
 * ============================================================================ */
/* --- Java Code for Reactivity Graph Data Generation --- */

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/* --- Read all necessary Tasker variables safely --- */
/* Helper method to parse doubles safely from Tasker variables */
double getDouble(String name) {
    try {
        String val = tasker.getVariable(name);
        return (val == null || val.length() == 0) ? 0.0 : Double.parseDouble(val);
    } catch (Exception e) {
        return 0.0;
    }
}

double zone1end = getDouble("aab_zone1end");
double threshdark = getDouble("aab_threshdark");
double threshdim = getDouble("aab_threshdim");
double threshbright = getDouble("aab_threshbright");
double threshsteepness = getDouble("aab_threshsteepness");
double threshmidpoint = getDouble("aab_threshmidpoint");

/* --- The hardcoded lux values from the Tasker For loop --- */
double[] luxValues = {
    1, 1.47, 2.15, 3.16, 4.64, 6.81, 
    10, 13.34, 17.78, 23.71, 31.62, 42.17, 56.24, 75.01, 
    100, 133.4, 177.8, 237.1, 316.2, 421.7, 562.4, 750.1, 
    1000, 1334, 1778, 2371, 3162, 4217, 5624, 7501, 
    10000, 13340, 17783, 23714, 31623, 42174, 56234, 75011, 100000
};

/* --- Initialize the lists to hold our data points --- */
List lux_labels_list = new ArrayList();
List new_data_list = new ArrayList();
List ref_data_list = new ArrayList();

/* --- The main calculation loop --- */
for (int i = 0; i < luxValues.length; i++) {
    double lux = luxValues[i];
    double new_point = 0.0;
    double ref_point = 0.0;

    /* --- Calculate New Reactivity Threshold Point --- */
    if (lux < zone1end) {
        /* Linear interpolation for dark zone */
        /* Tasker: (%aab_threshdark - ((%aab_threshdark - %aab_threshdim) / %aab_zone1end) * %lux) * 100 */
        new_point = (threshdark - ((threshdark - threshdim) / zone1end) * lux) * 100.0;
    } else {
        /* Sigmoid curve for brighter zones */
        /* Tasker: (%aab_threshdim + (%aab_threshbright - %aab_threshdim) / (1 + e^(-%aab_threshsteepness * (log10(%lux+1) - %aab_threshmidpoint)))) * 100 */
        double exponent = -threshsteepness * (Math.log10(lux + 1) - threshmidpoint);
        double denominator = 1.0 + Math.exp(exponent);
        new_point = (threshdim + (threshbright - threshdim) / denominator) * 100.0;
    }

    /* --- Calculate Reference Threshold Point --- */
    if (lux < 35.0) {
        /* Linear interpolation reference */
        /* Tasker: (0.30 - ((0.30 - 0.25) / 35) * %lux) * 100 */
        ref_point = (0.30 - ((0.30 - 0.25) / 35.0) * lux) * 100.0;
    } else {
        /* Sigmoid curve reference */
        /* Tasker: (0.25 + (0.08 - 0.25) / (1 + e^(-2.1 * (log10(%lux+1) - 4)))) * 100 */
        double exponentRef = -2.1 * (Math.log10(lux + 1) - 4.0);
        double denominatorRef = 1.0 + Math.exp(exponentRef);
        ref_point = (0.25 + (0.08 - 0.25) / denominatorRef) * 100.0;
    }

    /* --- Add the calculated points to our lists --- */
    lux_labels_list.add(new Double(lux));
    new_data_list.add(new Double(new_point));
    ref_data_list.add(new Double(ref_point));
}

/* --- Join the lists into comma-separated strings --- */
StringBuffer lux_labels_str = new StringBuffer();
StringBuffer new_data_str = new StringBuffer();
StringBuffer ref_data_str = new StringBuffer();

for (int i = 0; i < lux_labels_list.size(); i++) {
    if (i > 0) {
        lux_labels_str.append(",");
        new_data_str.append(",");
        ref_data_str.append(",");
    }
    
    lux_labels_str.append(lux_labels_list.get(i));
    
    /* Format to 3 decimal places using BigDecimal */
    new_data_str.append(new BigDecimal(((Double) new_data_list.get(i)).doubleValue()).setScale(3, BigDecimal.ROUND_HALF_UP).toString());
    ref_data_str.append(new BigDecimal(((Double) ref_data_list.get(i)).doubleValue()).setScale(3, BigDecimal.ROUND_HALF_UP).toString());
}

/* --- Set the final variables for Tasker to use --- */
tasker.setVariable("lux_labels", lux_labels_str.toString());
tasker.setVariable("new_data", new_data_str.toString());
tasker.setVariable("ref_data", ref_data_str.toString());
