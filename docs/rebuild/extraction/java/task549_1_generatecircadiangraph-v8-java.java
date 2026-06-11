/* === EXTRACTED JAVA BLOCK (S1 ground-truth, verbatim, html.unescape-decoded) ===
 * Task: task549 "_GenerateCircadianGraph V8 (Java)"
 * Block: #1 of 1 in this task
 * XML: Advanced_Auto_Brightness_V3.3.prj_9.xml  arg0 (Java source) L17139-L17288; <code>474</code> at L17138
 * Output: none (uses tasker.setVariable() directly)
 * %vars consumed (S4 parameters): %02d
 * ============================================================================ */
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/* 1. Helper: Circular Range Check */
boolean isInRange(double t, double s, double e) {
    if (s < e) return t >= s && t < e;
    else return t >= s || t < e; // Wrapped
}

/* 2. Helper: Circular Progress Calculation */
double getProgress(double t, double s, double e) {
    double dur;
    boolean isWrapped = (s > e);
    
    if (isWrapped) dur = (86400.0 - s) + e;
    else dur = e - s;
    
    if (dur < 60.0) dur = 60.0; // Safety against zero division
    
    // If not in range, return -1 immediately
    if (!isInRange(t, s, e)) return -1.0;
    
    double dist;
    // Calculate distance from start 's'
    if (isWrapped && t < s) dist = (86400.0 - s) + t;
    else dist = t - s;
    
    return dist / dur;
}

/* 3. Inputs & Normalization */
// Parse configuration variables
double sim_spread = Double.parseDouble(tasker.getVariable("sim_spread"));
double sim_transition = Double.parseDouble(tasker.getVariable("sim_transition"));
double sim_steepness = Double.parseDouble(tasker.getVariable("sim_steepness"));
boolean isPolar = "true".equals(tasker.getVariable("AAB_PolarState"));

double sunlightDuration = 0.0;
try { 
    String sd = tasker.getVariable("AAB_Sunlightduration");
    if (sd != null) sunlightDuration = Double.parseDouble(sd); 
} catch (Exception e) {}

// Retrieve Raw Seconds from Tasker
// We use getVariable to ensure we get the string value, then parse
double raw_dawn = Double.parseDouble(tasker.getVariable("AAB_Sundawn"));
double raw_sunrise = Double.parseDouble(tasker.getVariable("AAB_Sunrise"));
double raw_noon = Double.parseDouble(tasker.getVariable("AAB_Sunnoon"));
double raw_sunset = Double.parseDouble(tasker.getVariable("AAB_Sunset"));
double raw_dusk = Double.parseDouble(tasker.getVariable("AAB_Sundusk"));

// Normalize Sun Events to 0-86400 range immediately
// This ensures all subsequent math works on a generic 24h clock
double sim_calc_dawn = ((raw_dawn % 86400) + 86400) % 86400;
double sim_calc_sunrise = ((raw_sunrise % 86400) + 86400) % 86400;
double sim_calc_noon = ((raw_noon % 86400) + 86400) % 86400;
double sim_calc_sunset = ((raw_sunset % 86400) + 86400) % 86400;
double sim_calc_dusk = ((raw_dusk % 86400) + 86400) % 86400;

/* 4. Calculate Windows (Absolute Durations) */
// FIX: Use Normalized values for Day Length to avoid date-stamp confusion
double aab_daylength = sim_calc_sunset - sim_calc_sunrise;
// If sunset is "before" sunrise (midnight crossing), add 24 hours
if (aab_daylength < 0) aab_daylength += 86400;

double delta_day = aab_daylength * sim_transition;
double aab_nightlength = 86400 - aab_daylength;
double delta_night = aab_nightlength * sim_transition;

// Calculate Starts/Ends with strict 0-86400 normalization
double sim_m_start = ((sim_calc_dawn - delta_night) % 86400 + 86400) % 86400;
double sim_m_end = ((sim_calc_sunrise + delta_day) % 86400 + 86400) % 86400;

double sim_e_start = ((sim_calc_sunset - delta_day) % 86400 + 86400) % 86400;
double sim_e_end = ((sim_calc_dusk + delta_night) % 86400 + 86400) % 86400;

/* 5. Main Loop */
List time_labels_list = new ArrayList();
List data_points_list = new ArrayList();

// Loop from 0 to 86400 (inclusive) to cover 00:00 to 24:00
for (int i = 0; i <= 86400; i += 600) {
    // FIX: Normalize 'now' for the calculation logic. 
    // This makes 86400 behave exactly like 0 for continuity checks.
    double now = (double) (i % 86400); 
    double progress = 0.0;
    
    if (isPolar) {
        progress = (sunlightDuration > 43200) ? 1.0 : 0.0; // Simple polar check (>12h)
    } else {
        double mProg = getProgress(now, sim_m_start, sim_m_end);
        double eProg = getProgress(now, sim_e_start, sim_e_end);
        
        if (mProg >= 0.0) {
            progress = mProg;
        } else if (eProg >= 0.0) {
            progress = 1.0 - eProg;
        } else {
            // Check "Day" state (Between Morning End and Evening Start)
            if (isInRange(now, sim_m_end, sim_e_start)) {
                progress = 1.0;
            } else {
                progress = 0.0;
            }
        }
    }
    
    // Clamp
    if (progress > 1.0) progress = 1.0;
    if (progress < 0.0) progress = 0.0;
    
    // Modifier Math (Sigmoid/Tanh smoothing)
    double x_factor = (progress - 0.5) * sim_steepness;
    double tanh_max = Math.tanh(sim_steepness / 2.0);
    double tanh_raw = Math.tanh(x_factor);
    double modifier = (Math.abs(tanh_max) > 1e-6) ? (tanh_raw / tanh_max) : 0.0;
    
    double scaled_value = 1.0 + (sim_spread / 100.0) * modifier;
    
    // Output Formatting
    data_points_list.add(new Double(scaled_value));
    
    int hour = i / 3600;
    if (hour == 24) hour = 0; // Handle 24:00 as 00:00 for label
    int minute = (i % 3600) / 60;
    time_labels_list.add(String.format("'%02d:%02d'", new Object[]{new Integer(hour), new Integer(minute)}));
}

/* 6. String Construction */
StringBuffer time_labels_str = new StringBuffer();
StringBuffer data_points_str = new StringBuffer();

for (int i = 0; i < time_labels_list.size(); i++) {
    if (i > 0) {
        time_labels_str.append(",");
        data_points_str.append(",");
    }
    time_labels_str.append(time_labels_list.get(i));
    data_points_str.append(new BigDecimal(((Double) data_points_list.get(i)).doubleValue()).setScale(3, BigDecimal.ROUND_HALF_UP).toString());
}

/* 7. Return to Tasker */
// Set Local Variables for string replacements
tasker.setVariable("time_labels", time_labels_str.toString());
tasker.setVariable("data_points", data_points_str.toString());
tasker.setVariable("sim_calc_dawn", String.valueOf(sim_calc_dawn));
tasker.setVariable("sim_calc_noon", String.valueOf(sim_calc_noon));
tasker.setVariable("sim_calc_sunset", String.valueOf(sim_calc_sunset));
tasker.setVariable("sim_calc_dusk", String.valueOf(sim_calc_dusk));
