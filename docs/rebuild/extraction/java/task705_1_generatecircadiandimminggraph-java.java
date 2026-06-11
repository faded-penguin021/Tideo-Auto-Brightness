/* === EXTRACTED JAVA BLOCK (S1 ground-truth, verbatim, html.unescape-decoded) ===
 * Task: task705 "_GenerateCircadianDimmingGraph (Java)"
 * Block: #1 of 1 in this task
 * XML: Advanced_Auto_Brightness_V3.3.prj_9.xml  arg0 (Java source) L37518-L37689; <code>474</code> at L37517
 * Output: none (uses tasker.setVariable() directly)
 * %vars consumed (S4 parameters): (none)
 * ============================================================================ */
/* --- Java Code for Circadian Dimming Graph Generation --- */

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/* --- Helper to safely parse doubles from Tasker --- */
double getDouble(String name) {
    try {
        String val = tasker.getVariable(name);
        return (val == null || val.length() == 0) ? 0.0 : Double.parseDouble(val);
    } catch (Exception e) {
        return 0.0;
    }
}

/* --- Read Input Variables --- */
double dimspread = getDouble("aab_dimspread");
double transition = getDouble("AAB_ScaleTransitionFactor");
double steepness = getDouble("AAB_ScaleSteepness");
double sunlightDuration = getDouble("AAB_Sunlightduration");
String polarState = tasker.getVariable("AAB_PolarState");

/* --- Sun Event Calculations (A7) --- */
/* Normalize to seconds of the day */
double raw_dawn = getDouble("AAB_Sundawn");
double raw_sunrise = getDouble("AAB_Sunrise");
double raw_noon = getDouble("AAB_Sunnoon");
double raw_sunset = getDouble("AAB_Sunset");
double raw_dusk = getDouble("AAB_Sundusk");

double calc_dawn = raw_dawn % 86400;
double calc_sunrise = raw_sunrise % 86400;
double calc_noon = raw_noon % 86400;
double calc_sunset = raw_sunset % 86400;
double calc_dusk = raw_dusk % 86400;

/* --- Day Wrap Logic (A8 - A22) --- */
if (calc_dawn > calc_sunrise) { calc_dawn -= 86400; }
if (calc_noon < calc_sunrise) { calc_noon += 86400; }
if (calc_sunset < calc_sunrise) { calc_sunset += 86400; }
if (calc_dusk < calc_sunset) { calc_dusk += 86400; }

if (calc_dawn < 0) {
    calc_dawn += 86400;
    calc_sunrise += 86400;
    calc_noon += 86400;
    calc_sunset += 86400;
    calc_dusk += 86400;
}

/* --- Window Duration Calculations (A23) --- */
double daylength = calc_sunset - calc_sunrise;
double delta_day = daylength * transition;
double nightlength = 86400 - daylength;
double delta_night = nightlength * transition;

double morning_start = calc_dawn - delta_night;
double morning_end = calc_sunrise + delta_day;
double evening_start = calc_sunset - delta_day;
double evening_end = calc_dusk + delta_night;

double morning_duration = morning_end - morning_start;
double evening_duration = evening_end - evening_start;

/* --- Main Loop for Data Points (A27 - A81) --- */
/* Loop from 0 to 86400 in 600s increments */
StringBuffer time_labels_str = new StringBuffer();
StringBuffer data_points_str = new StringBuffer();

for (int sim_now = 0; sim_now <= 86400; sim_now += 600) {
    
    double progress = 0.0;
    boolean progress_found = false;
    double time_v1 = (double) sim_now;
    double time_v2 = time_v1 + 86400;

    /* --- Logic: Determine "Progress" (0=Night, 1=Day) --- */
    if ("true".equals(polarState)) {
        // Polar Day (sunlight > 1380 mins) or Polar Night (< 60 mins)
        if (sunlightDuration > 1380) { progress = 1.0; } 
        else if (sunlightDuration < 60) { progress = 0.0; }
        else { progress = 0.0; } // Default fallback
    } else {
        // Standard Day/Night Logic
        
        // Morning Transition (0 -> 1)
        if (time_v1 > morning_start && time_v1 < morning_end) {
            progress = (time_v1 - morning_start) / morning_duration;
            progress_found = true;
        } else if (time_v2 > morning_start && time_v2 < morning_end) {
            progress = (time_v2 - morning_start) / morning_duration;
            progress_found = true;
        }
        // Evening Transition (1 -> 0)
        else if (time_v1 > evening_start && time_v1 < evening_end) {
            progress = 1.0 - ((time_v1 - evening_start) / evening_duration);
            progress_found = true;
        } else if (time_v2 > evening_start && time_v2 < evening_end) {
            progress = 1.0 - ((time_v2 - evening_start) / evening_duration);
            progress_found = true;
        }
        // Full Day or Full Night
        else {
            boolean is_day = false;
            if ((time_v1 > morning_end && time_v1 < evening_start) || 
                (time_v2 > morning_end && time_v2 < evening_start)) {
                is_day = true;
            }
            progress = is_day ? 1.0 : 0.0;
        }
    }

    /* Clamp Progress */
    if (progress > 0.99) progress = 1.0;
    if (progress < 0.01) progress = 0.0;

    /* --- Logic: Curve Smoothing (Tanh) --- */
    double x_factor = (progress - 0.5) * steepness;
    double neg_x_factor = -1.0 * x_factor;
    
    // Tanh calculation: (e^x - e^-x) / (e^x + e^-x)
    double exp_steep = Math.exp(steepness / 2.0);
    double exp_neg_steep = Math.exp(-steepness / 2.0);
    double tanh_max = (exp_steep - exp_neg_steep) / (exp_steep + exp_neg_steep);

    double exp_x = Math.exp(x_factor);
    double exp_neg_x = Math.exp(neg_x_factor);
    double tanh_raw = (exp_x - exp_neg_x) / (exp_x + exp_neg_x);

    double modifier = 0.0;
    if (tanh_max != 0) {
        modifier = tanh_raw / tanh_max;
    }

    /* Final Dim Modifier Value */
    double dim_val = 2.0 - (1.0 + (dimspread / 100.0) * modifier);

    /* --- Formatting --- */
    if (sim_now > 0) {
        time_labels_str.append(",");
        data_points_str.append(",");
    }

    // Round data point to 3 decimals
    data_points_str.append(new BigDecimal(dim_val).setScale(3, BigDecimal.ROUND_HALF_UP).toString());

    /* --- FIX: Explicitly calculate hour and minute variables --- */
    int hour = sim_now / 3600;
    int minute = (sim_now % 3600) / 60;
    
    /* Format HH:MM using calculated variables */
    String hStr = (hour < 10) ? "0" + hour : "" + hour;
    String mStr = (minute < 10) ? "0" + minute : "" + minute;
    time_labels_str.append("'" + hStr + ":" + mStr + "'");
}

/* --- Calculate NOW_UTC (A82) --- */
long nowTime = System.currentTimeMillis() / 1000;
long nowUtc = nowTime % 86400;

/* --- Set Tasker Variables --- */
// Graph Data
tasker.setVariable("time_labels", time_labels_str.toString());
tasker.setVariable("data_points", data_points_str.toString());
tasker.setVariable("now_utc", String.valueOf(nowUtc));

// Calculated Sun Events (Needed for HTML replacement later)
tasker.setVariable("sim_calc_dawn", String.valueOf((long)calc_dawn));
tasker.setVariable("sim_calc_noon", String.valueOf((long)calc_noon));
tasker.setVariable("sim_calc_sunset", String.valueOf((long)calc_sunset));
tasker.setVariable("sim_calc_dusk", String.valueOf((long)calc_dusk));
