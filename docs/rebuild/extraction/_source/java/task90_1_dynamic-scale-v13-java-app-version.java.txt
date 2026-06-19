/* === EXTRACTED JAVA BLOCK (S1 ground-truth, verbatim, html.unescape-decoded) ===
 * Task: task90 "Dynamic Scale V13 (Java) App Version"
 * Block: #1 of 2 in this task
 * XML: Advanced_Auto_Brightness_V3.3.prj_9.xml  arg0 (Java source) L40430-L40693; <code>474</code> at L40429
 * Output: none (uses tasker.setVariable() directly)
 * %vars consumed (S4 parameters): %LOC
 * ============================================================================ */
import java.util.Calendar;
import java.util.TimeZone;
import java.math.BigDecimal;
import android.location.Location;
import android.location.LocationManager;
import android.content.Context;

/* --- NOAA Solar Calculation Algorithm (Robust Inline Fix) --- */

/* 1. Get Inputs & Handle Defaults */
latStr = tasker.getVariable("AAB_Latitude");
lngStr = tasker.getVariable("AAB_Longitude");
dateStr = tasker.getVariable("AAB_Date");

/* APK FIX: Check standard Tasker variables if globals are empty or null string */
if (latStr == null || latStr.length() == 0) latStr = tasker.getVariable("gl_latitude");
if (lngStr == null || lngStr.length() == 0) lngStr = tasker.getVariable("gl_longitude");

/* FAILSAFE: Try standard %LOC variable (lat,lng) if still empty */
if (latStr == null || latStr.length() == 0) {
    locRaw = tasker.getVariable("LOC");
    if (locRaw != null && locRaw.indexOf(",") != -1) {
        parts = locRaw.split(",");
        if (parts.length >= 2) {
            latStr = parts[0];
            lngStr = parts[1];
        }
    }
}

lat = 0.0;
lng = 0.0;
dateSeconds = 0; /* Use 0 instead of 0L to avoid parser quirks */

/* Flags to track if we need to clear variables at the end */
clearDate = false;
clearLoc = false;

/* --- Handle Date (Flattened Logic) --- */
/* Attempt to parse dateStr if it exists */
dateParsed = false;
if (dateStr != null && dateStr.length() > 0) {
    try {
        dateSeconds = Long.parseLong(dateStr);
        dateParsed = true;
    } catch (Exception e) {
        /* Ignore error, will fallback below */
    }
}

/* Fallback to Today if parsing failed or string was empty */
if (!dateParsed) {
    dateSeconds = System.currentTimeMillis() / 1000;
    clearDate = true;
}

/* --- Handle Location --- */
locFound = false;

if (latStr != null && lngStr != null && latStr.length() > 0 && lngStr.length() > 0) {
    try {
        lat = Double.parseDouble(latStr);
        lng = Double.parseDouble(lngStr);
        locFound = true;
    } catch (NumberFormatException e) { 
        locFound = false;
    }
}

/* Check if we need to cleanup AAB vars later (if they were originally missing) */
if (tasker.getVariable("AAB_Latitude") == null) {
    clearLoc = true;
}

/* Try 'LastKnownLocation' if explicit location missing */
if (!locFound) {
    try {
        ctx = context.getApplicationContext();
        lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
        bestLoc = null;
        
        providers = lm.getProviders(true);
        for (int i = 0; i < providers.size(); i++) {
            provider = (String) providers.get(i);
            l = lm.getLastKnownLocation(provider);
            if (l == null) continue;
            
            if (bestLoc == null || l.getTime() > bestLoc.getTime()) {
                bestLoc = l;
            }
        }
        
        if (bestLoc != null) {
            lat = bestLoc.getLatitude();
            lng = bestLoc.getLongitude();
            locFound = true;
        } 
    } catch (Exception e) {
        tasker.log("AAB: Location error: " + e.getMessage());
    }
}

/* APK FAILSAFE: Set not_found flag if location missing */
if (!locFound) {
    tasker.setVariable("not_found", "true");
    
    /* Perform cleanup before exiting to avoid leaving temporary vars */
    if (clearDate) {
        tasker.setVariable("AAB_Date", null);
    }
    if (clearLoc) {
        tasker.setVariable("AAB_Latitude", null);
        tasker.setVariable("AAB_Longitude", null);
    }
    return;
}

/* 2. Setup Calendar */
cal = Calendar.getInstance();
cal.setTimeInMillis(dateSeconds * 1000);
dayOfYear = cal.get(Calendar.DAY_OF_YEAR);
year = cal.get(Calendar.YEAR);
zoneOffset = cal.getTimeZone().getOffset(cal.getTimeInMillis()) / 3600000.0;

/* 
 * 3. Solar Math (Inline Loop)
 * Replaces nested methods/classes to prevent BeanShell syntax errors.
 * Indices: 0=Rise, 1=Set, 2=Dawn, 3=Dusk 
 */

/* Config arrays */
boolean[] isSunriseArr = { true, false, true, false };
double[] zenithArr = { 90.8333, 90.8333, 96.0, 96.0 };
double[] results = new double[4];

for (int i = 0; i < 4; i++) {
    isSunrise = isSunriseArr[i];
    zenith = zenithArr[i];
    
    lngHour = lng / 15.0;
    
    /* Calculate approximate time */
    if (isSunrise) {
        t_approx = dayOfYear + ((6.0 - lngHour) / 24.0);
    } else {
        t_approx = dayOfYear + ((18.0 - lngHour) / 24.0);
    }

    /* Sun's mean anomaly */
    M = (0.9856 * t_approx) - 3.289;
    
    /* Sun's true longitude */
    L = M + (1.916 * Math.sin(Math.toRadians(M))) + (0.020 * Math.sin(Math.toRadians(2 * M))) + 282.634;
    L = (L + 360.0) % 360.0;

    /* Right Ascension */
    RA = Math.toDegrees(Math.atan(0.91764 * Math.tan(Math.toRadians(L))));
    RA = (RA + 360.0) % 360.0;

    Lquadrant  = (Math.floor( L/90.0)) * 90.0;
    RAquadrant = (Math.floor(RA/90.0)) * 90.0;
    RA = RA + (Lquadrant - RAquadrant);
    RA = RA / 15.0;

    /* Sun's declination */
    sinDec = 0.39782 * Math.sin(Math.toRadians(L));
    cosDec = Math.cos(Math.asin(sinDec));

    /* Local hour angle */
    top = Math.cos(Math.toRadians(zenith)) - (sinDec * Math.sin(Math.toRadians(lat)));
    bottom = cosDec * Math.cos(Math.toRadians(lat));
    cosH = top / bottom;

    /* Handle Polar Cases in loop */
    if (cosH >  1) {
        results[i] = -1.0; /* Sun never rises */
    } else if (cosH < -1) {
        results[i] = -2.0; /* Sun never sets */
    } else {
        if (isSunrise) {
            H = 360.0 - Math.toDegrees(Math.acos(cosH));
        } else {
            H = Math.toDegrees(Math.acos(cosH));
        }
        H = H / 15.0;

        T = H + RA - (0.06571 * t_approx) - 6.622;
        UT = T - lngHour;
        localT = UT + zoneOffset;
        results[i] = (localT + 24.0) % 24.0;
    }
}

riseHour = results[0];
setHour  = results[1];
dawnHour = results[2];
duskHour = results[3];

/* Solar Noon Calculation */
noonHour = (riseHour + setHour) / 2.0;
if (setHour < riseHour) {
    noonHour = ((riseHour + setHour + 24.0) / 2.0) % 24.0;
}

/* 4. Process Results & Set Tasker Variables */
/* Helper to convert hour double to epoch string inline */
calBase = Calendar.getInstance();
calBase.setTimeInMillis(dateSeconds * 1000);
calBase.set(Calendar.HOUR_OF_DAY, 0);
calBase.set(Calendar.MINUTE, 0);
calBase.set(Calendar.SECOND, 0);
calBase.set(Calendar.MILLISECOND, 0);
startOfDay = calBase.getTimeInMillis() / 1000;

/* Inline epoch conversion helper logic */
String getEpoch(double h, long base) {
    long off = (long) (h * 3600.0);
    return String.valueOf(base + off);
}

if (riseHour < 0 || setHour < 0) {
    /* Polar Condition Logic */
    tasker.setVariable("AAB_SunStatus", "polar");
    
    if (riseHour == -2.0 || setHour == -2.0) {
        tasker.setVariable("ss_sunlight_duration", "1440"); /* Midnight Sun */
    } else {
        tasker.setVariable("ss_sunlight_duration", "0"); /* Polar Night */
    }
    
    /* Set safe defaults for times (Noon) */
    defaultTime = getEpoch(12.0, startOfDay);
    tasker.setVariable("ss_sunrise", defaultTime);
    tasker.setVariable("ss_sunset", defaultTime);
    tasker.setVariable("ss_civil_dawn", defaultTime);
    tasker.setVariable("ss_civil_dusk", defaultTime);
    tasker.setVariable("ss_solar_noon", defaultTime);

} else {
    /* Normal Solar Day */
    tasker.setVariable("AAB_SunStatus", "ok");
    
    riseEpoch = Long.parseLong(getEpoch(riseHour, startOfDay));
    setEpoch = Long.parseLong(getEpoch(setHour, startOfDay));
    
    tasker.setVariable("ss_sunrise", String.valueOf(riseEpoch));
    tasker.setVariable("ss_sunset", String.valueOf(setEpoch));
    tasker.setVariable("ss_civil_dawn", getEpoch(dawnHour, startOfDay));
    tasker.setVariable("ss_civil_dusk", getEpoch(duskHour, startOfDay));
    tasker.setVariable("ss_solar_noon", getEpoch(noonHour, startOfDay));
    
    durationMins = (setEpoch - riseEpoch) / 60.0;
    if (durationMins < 0) durationMins += 1440;
    tasker.setVariable("ss_sunlight_duration", String.valueOf(Math.round(durationMins)));
}

/* --- Cleanup --- */
if (clearDate) {
    tasker.setVariable("AAB_Date", null);
}
if (clearLoc) {
    tasker.setVariable("AAB_Latitude", null);
    tasker.setVariable("AAB_Longitude", null);
}
