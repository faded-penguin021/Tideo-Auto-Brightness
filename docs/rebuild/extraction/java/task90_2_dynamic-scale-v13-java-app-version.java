/* === EXTRACTED JAVA BLOCK (S1 ground-truth, verbatim, html.unescape-decoded) ===
 * Task: task90 "Dynamic Scale V13 (Java) App Version"
 * Block: #2 of 2 in this task
 * XML: Advanced_Auto_Brightness_V3.3.prj_9.xml  arg0 (Java source) L41086-L41207; <code>474</code> at L41085
 * Output: none (uses tasker.setVariable() directly)
 * %vars consumed (S4 parameters): (none)
 * ============================================================================ */
import java.math.BigDecimal;

/* --- Java Code: Dynamic Scale Calculation V13 Fix --- */

/* 1. Helper for parsing */
double safeParse(String val, double defVal) {
    if (val == null || val.length() == 0) return defVal;
    try {
        return Double.parseDouble(val);
    } catch (NumberFormatException e) {
        return defVal;
    }
}

/* 2. Retrieve Inputs */
/* We calculate 'now' directly to ensure it matches the System Time exactly */
long sysTime = System.currentTimeMillis() / 1000;
double now = (double) (sysTime % 86400);

/* Retrieve Schedule Variables */
/* Note: We rely on the Tasker actions A66-A81 having run successfully to populate these globals */
double morningStart = safeParse(tasker.getVariable("AAB_MorningStart"), 0.0);
double morningEnd = safeParse(tasker.getVariable("AAB_MorningEnd"), 0.0);
double morningDuration = safeParse(tasker.getVariable("AAB_MorningDuration"), 60.0);

double eveningStart = safeParse(tasker.getVariable("AAB_EveningStart"), 0.0);
double eveningEnd = safeParse(tasker.getVariable("AAB_EveningEnd"), 0.0);
double eveningDuration = safeParse(tasker.getVariable("AAB_EveningDuration"), 60.0);

double sunlightDuration = safeParse(tasker.getVariable("AAB_Sunlightduration"), 0.0);

/* Settings */
String polarStr = tasker.getVariable("AAB_PolarState");
boolean isPolar = (polarStr != null && polarStr.equals("true"));

double steepness = safeParse(tasker.getVariable("AAB_ScaleSteepness"), 4.0);
double dimSpread = safeParse(tasker.getVariable("AAB_DimSpread"), 0.0);
double scaleSpread = safeParse(tasker.getVariable("AAB_ScaleSpread"), 0.0);

/* 3. Safety Guards */
if (morningDuration < 1.0) morningDuration = 60.0;
if (eveningDuration < 1.0) eveningDuration = 60.0;

/* 4. Progress Calculation */
double progress = 0.0;
double time_v2 = now + 86400.0; 
double time_prev = now - 86400.0; /* Crucial for negative start times (previous day overlap) */

/* Helper to keep logic clean */
boolean inRange(double t, double s, double e) {
    return t >= s && t < e;
}

if (isPolar) {
    if (sunlightDuration > 1380) progress = 1.0;
    else progress = 0.0;
} else {
    /* Morning Ramp: 0.0 -> 1.0 */
    /* FIX: Check Now, Next Day (+24h), AND Prev Day (-24h) */
    if (inRange(now, morningStart, morningEnd)) {
        progress = (now - morningStart) / morningDuration;
    } 
    else if (inRange(time_v2, morningStart, morningEnd)) {
        progress = (time_v2 - morningStart) / morningDuration;
    }
    else if (inRange(time_prev, morningStart, morningEnd)) {
        progress = (time_prev - morningStart) / morningDuration;
    }
    
    /* Evening Ramp: 1.0 -> 0.0 */
    else if (inRange(now, eveningStart, eveningEnd)) {
        progress = 1.0 - ((now - eveningStart) / eveningDuration);
    }
    else if (inRange(time_v2, eveningStart, eveningEnd)) {
        progress = 1.0 - ((time_v2 - eveningStart) / eveningDuration);
    }
    else if (inRange(time_prev, eveningStart, eveningEnd)) {
        progress = 1.0 - ((time_prev - eveningStart) / eveningDuration);
    }
    
    /* Full Day (Between Morning End and Evening Start) */
    else {
        boolean isDay = false;
        
        // Check current day window
        if (now >= morningEnd && now <= eveningStart) isDay = true;
        // Check next day wrap (e.g. shifts > 86400)
        else if (time_v2 >= morningEnd && time_v2 <= eveningStart) isDay = true;
        // Check prev day wrap (e.g. shifts < 0)
        else if (time_prev >= morningEnd && time_prev <= eveningStart) isDay = true;
        
        if (isDay) progress = 1.0;
        else progress = 0.0; // Night
    }
}

/* Clamp Progress */
if (progress > 1.0) progress = 1.0;
if (progress < 0.0) progress = 0.0;

/* 5. Modifier Calculation (Sigmoid/Tanh) */
double x_factor = (progress - 0.5) * steepness;
double tanh_max = Math.tanh(steepness / 2.0);
double tanh_raw = Math.tanh(x_factor);

double modifier = 0.0;
if (Math.abs(tanh_max) > 0.000001) {
    modifier = tanh_raw / tanh_max;
}

/* 6. Calculate Final Dynamic Values */
double dim_dynamic_raw = 2.0 - (1.0 + (dimSpread / 100.0) * modifier);
BigDecimal dim_bd = new BigDecimal(dim_dynamic_raw).setScale(3, BigDecimal.ROUND_HALF_UP);

double scale_dynamic_raw = 1.0 + (scaleSpread / 100.0) * modifier;
BigDecimal scale_bd = new BigDecimal(scale_dynamic_raw).setScale(3, BigDecimal.ROUND_HALF_UP);

/* 7. Write Outputs */
tasker.setVariable("progress", String.valueOf(progress));
tasker.setVariable("modifier", String.valueOf(modifier)); 
tasker.setVariable("AAB_DimDynamic", dim_bd.toString());
tasker.setVariable("AAB_ScaleDynamic", scale_bd.toString());
