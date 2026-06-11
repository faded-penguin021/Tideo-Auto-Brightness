/* === EXTRACTED JAVA BLOCK (S1 ground-truth, verbatim, html.unescape-decoded) ===
 * Task: task557 "_GenerateAlphaGraph (Java)"
 * Block: #1 of 1 in this task
 * XML: Advanced_Auto_Brightness_V3.3.prj_9.xml  arg0 (Java source) L18960-L19018; <code>474</code> at L18959
 * Output: none (uses tasker.setVariable() directly)
 * %vars consumed (S4 parameters): %aab_deltafactor
 * ============================================================================ */
/* --- Java Code for Alpha Graph Generation --- */

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/* --- Read all necessary Tasker variables --- */
double aab_deltafactor = %aab_deltafactor;

/* --- The hardcoded label values from the For loop --- */
double[] labelValues = {
    1,1.22,1.49,1.82,2.23,2.72,3.32,4.06,4.95,6.05,7.39,9.03,11.03,13.47,16.45,20.09,24.54,29.98,36.61,44.72,54.62,66.72,81.49,99.54,121.58,148.5,181.38,221.55,270.61,330.53,403.72,493.11,602.3,735.67,898.57,1097.55,1340.58,1637.42,2000
};

/* --- Initialize lists to hold our data points --- */
List lux_labels_list = new ArrayList();
List new_data_list = new ArrayList();
List ref_data_list = new ArrayList();

/* --- The main calculation loop (replicates A8-A15) --- */
for (int i = 0; i < labelValues.length; i++) {
    double label = labelValues[i];
    
    /* A9: Calculate lux_delta */
    double lux_delta = label / 100.0;

    /* A10: Calculate new_point */
    double new_point = 1.0 - Math.exp(-aab_deltafactor * lux_delta);

    /* A11: Calculate ref_point */
    double ref_point = 1.0 - Math.exp(-1.8 * lux_delta);

    /* Add the calculated points to our lists */
    lux_labels_list.add(new Double(label));
    new_data_list.add(new Double(new_point));
    ref_data_list.add(new Double(ref_point));
}

/* --- Join the lists into comma-separated strings (replicates A16-A18) --- */
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
    /* Round the data points for clean output */
    new_data_str.append(new BigDecimal(((Double) new_data_list.get(i)).doubleValue()).setScale(6, BigDecimal.ROUND_HALF_UP).toString());
    ref_data_str.append(new BigDecimal(((Double) ref_data_list.get(i)).doubleValue()).setScale(6, BigDecimal.ROUND_HALF_UP).toString());
}

/* --- Set all the variables Tasker needs for the HTML replacement --- */
tasker.setVariable("lux_labels", lux_labels_str.toString());
tasker.setVariable("new_data", new_data_str.toString());
tasker.setVariable("ref_data", ref_data_str.toString());
