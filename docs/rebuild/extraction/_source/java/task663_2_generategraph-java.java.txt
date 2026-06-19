/* === EXTRACTED JAVA BLOCK (S1 ground-truth, verbatim, html.unescape-decoded) ===
 * Task: task663 "_GenerateGraph (Java)"
 * Block: #2 of 2 in this task
 * XML: Advanced_Auto_Brightness_V3.3.prj_9.xml  arg0 (Java source) L34371-L34456; <code>474</code> at L34370
 * Output: none (uses tasker.setVariable() directly)
 * %vars consumed (S4 parameters): %AAB_MaxBright, %AAB_MinBright, %aab_form1a, %aab_form2a, %aab_form2b, %aab_form2c, %aab_form3a, %aab_zone1end, %aab_zone2end
 * ============================================================================ */
/* --- Java Code for Graph Data Generation (V15.8 Compatible) --- */

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/* --- Read all necessary Tasker variables --- */
double aab_zone1end = %aab_zone1end;
double aab_zone2end = %aab_zone2end;
double aab_form1a = %aab_form1a;
double aab_form2a = %aab_form2a;
double aab_form2b = %aab_form2b;
double aab_form2c = %aab_form2c;
double aab_form3a = %aab_form3a;
double AAB_MinBright = %AAB_MinBright;
double AAB_MaxBright = %AAB_MaxBright;

/* --- The hardcoded lux values from the For loop --- */
double[] luxValues = {
    0.1,0.35,1,1.47,2.15,3.16,4.64,6.81,10,13.34,17.78,23.71,31.62,42.17,56.24,75.01,100,133.4,177.8,237.1,316.2,421.7,562.4,750.1,1000,1334,1778,2371,3162,4217,5624,7501,10000,13340,17783,23714,31623,42174,56234,75011,100000
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

    /* --- Calculate New Brightness Point --- */
    if (lux < aab_zone1end) {
        new_point = aab_form1a * Math.sqrt(lux);
    } else if (lux < aab_zone2end) {
        new_point = aab_form2a + aab_form2b * (Math.pow(lux - aab_form2c, 0.33) - Math.pow(aab_zone1end - aab_form2c, 0.33));
    } else {
        /* --- THE FIX IS HERE: Simplified formula with form3b = 1.0 --- */
        new_point = AAB_MaxBright - (aab_form3a / lux) * AAB_MaxBright;
    }

    /* --- Clamp the new_point to the min/max boundaries --- */
    if (new_point < AAB_MinBright) { new_point = AAB_MinBright; }
    if (new_point > AAB_MaxBright) { new_point = AAB_MaxBright; }

    /* --- Calculate Reference Brightness Point --- */
    if (lux < 50) {
        ref_point = 5.0 * Math.sqrt(lux);
    } else if (lux < 10000) {
        ref_point = 29.58 + 8.8 * (Math.pow(lux - 18.0, 0.33) - Math.pow(35.0 - 18.0, 0.33));
    } else {
        ref_point = 255.0 - ((2513.0 / lux) * 255.0);
    }
    
    /* --- Clamp the ref_point to the min/max boundaries --- */
    if (ref_point < AAB_MinBright) { ref_point = AAB_MinBright; }
    if (ref_point > AAB_MaxBright) { ref_point = AAB_MaxBright; }

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
    new_data_str.append(new BigDecimal(((Double) new_data_list.get(i)).doubleValue()).setScale(3, BigDecimal.ROUND_HALF_UP).toString());
    ref_data_str.append(new BigDecimal(((Double) ref_data_list.get(i)).doubleValue()).setScale(3, BigDecimal.ROUND_HALF_UP).toString());
}

/* --- Set the final variables for Tasker to use --- */
tasker.setVariable("lux_labels", lux_labels_str.toString());
tasker.setVariable("new_data", new_data_str.toString());
tasker.setVariable("ref_data", ref_data_str.toString());
