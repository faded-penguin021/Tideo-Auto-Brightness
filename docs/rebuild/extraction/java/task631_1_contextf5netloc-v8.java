/* === EXTRACTED JAVA BLOCK (S1 ground-truth, verbatim, html.unescape-decoded) ===
 * Task: task631 "_ContextF5NetLoc V8"
 * Block: #1 of 2 in this task
 * XML: Advanced_Auto_Brightness_V3.3.prj_9.xml  arg0 (Java source) L27940-L28252; <code>474</code> at L27939
 * Output: none (uses tasker.setVariable() directly)
 * %vars consumed (S4 parameters): (none)
 * ============================================================================ */
import android.location.LocationManager;
import android.location.Location;
import android.content.Context;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.SupplicantState;
import android.os.PowerManager;

/* PHASE 0: LOCATION SERVICES GATE */
LocationManager gateMgr = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
if (gateMgr != null) {
    if (!gateMgr.isProviderEnabled(LocationManager.NETWORK_PROVIDER) &&
        !gateMgr.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
        tasker.setVariable("should_stop", "true");
        tasker.setVariable("adaptive_mode", "DISABLED (Location Off)");
        tasker.log("Refresher: Location services disabled. Early exit.");
        return;
    }
}

/* ========================================
 * PHASE 1: GATHER CURRENT STATUS
 * ======================================== */

/* 1.1 Check Listener Health */
listener = tasker.getJavaVariable("locationListener");
locMgr = tasker.getJavaVariable("locationManager");

/* BeanShell check for null AND void (undefined) */
isListenerHealthy = (listener != null && listener != void && locMgr != null && locMgr != void);
tasker.setVariable("AAB_LocnListener_healthy", isListenerHealthy ? "true" : "false");

/* 1.2 Check Power Save Mode (Safe Wrapper) */
isPowerSave = false;
try {
    pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
    if (pm != null) isPowerSave = pm.isPowerSaveMode();
} catch (Exception e) {
    /* Default to false if API fails */
    isPowerSave = false; 
}
tasker.setVariable("is_power_save", isPowerSave ? "true" : "false");

if (isPowerSave) {
    tasker.log("Refresher: Power Saver Active (Passive listener kept alive, increasing backoff)");
}

/* 1.3 Check WiFi Status (Stronger Check) */
wifiMgr = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
isConnected = false;
if (wifiMgr != null) {
    try {
        wifiInfo = wifiMgr.getConnectionInfo();
        if (wifiInfo != null) {
            /* Check 1: Supplicant State */
            if (wifiInfo.getSupplicantState() == SupplicantState.COMPLETED) {
                isConnected = true;
            }
            /* Check 2: Valid Network ID (filters out some connecting states) */
            if (wifiInfo.getNetworkId() == -1) {
                isConnected = false;
            }
        }
    } catch (Exception e) { /* isConnected remains false */ }
}

/* 1.4 Get System's Last Known Location (Passive Check) */
systemLocMgr = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
lastLoc = null;
providerUsed = "NONE";

if (systemLocMgr != null) {
    try {
        /* Try Fused first (Battery saving, usually most recent) */
        lastLoc = systemLocMgr.getLastKnownLocation("fused");
        if (lastLoc != null) providerUsed = "FUSED";
    } catch (Exception e) {}

    if (lastLoc == null) {
        try {
            /* Fallback to Network */
            lastLoc = systemLocMgr.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (lastLoc != null) {
                providerUsed = "NETWORK";
            }
        } catch (Exception e) {}
    }
    
    /* Apply Accuracy Filter (> 500m) */
    if (lastLoc != null && lastLoc.hasAccuracy() && lastLoc.getAccuracy() > 500.0f) {
        tasker.log("System location ignored (Accuracy " + Math.round(lastLoc.getAccuracy()) + "m > 500m)");
        lastLoc = null;
        providerUsed = "NONE";
    }
}
tasker.setVariable("location_provider_used", providerUsed);

/* 1.5 Check Movement Speed */
isMovingFast = false;
nowMillis = System.currentTimeMillis();

if (lastLoc != null) {
    /* Ensure long math for time */
    locAge = nowMillis - lastLoc.getTime();
    /* If loc is < 2 mins old and speed > 5m/s (18km/h) */
    if (locAge < 120000 && lastLoc.hasSpeed() && lastLoc.getSpeed() > 5.0f) {
        isMovingFast = true;
    }
}

/* 1.6 Gather Cache Timestamps & Locations */
appCachedLoc = tasker.getVariable("AAB_NetLocation");
appCacheTMS = tasker.getVariable("AAB_NetLocationTMS");
appCacheTimeMillis = 0L;
if (appCacheTMS != null && !appCacheTMS.equals("")) {
    try {
        appCacheTimeMillis = Long.parseLong(appCacheTMS) * 1000L;
    } catch (Exception e) {}
}

appAgeSeconds = (nowMillis - appCacheTimeMillis) / 1000;
systemAgeSeconds = 999999;
if (lastLoc != null) {
    systemAgeSeconds = (nowMillis - lastLoc.getTime()) / 1000;
}

/* 1.7 Check for Zombie Listener (Displacement Logic) */
if (isListenerHealthy) {
    /* Get the heartbeat timestamp from the listener */
    heartbeatStr = tasker.getVariable("AAB_ListenerHeartbeatTMS");
    heartbeatAge = 999999L; // Default to "very old"

    if (heartbeatStr != null && !heartbeatStr.equals("")) {
        try {
            hbTime = Long.parseLong(heartbeatStr);
            heartbeatAge = (nowMillis - hbTime) / 1000;
        } catch (Exception e) {}
    }

    /* Concept A: Displacement Check
       If listener is silent for > 30 mins, check if we physically moved. */
    isZombie = false;
    
    if (heartbeatAge > 1800) {
        hasMoved = false;
        
        if (lastLoc != null && appCachedLoc != null && !appCachedLoc.equals("")) {
            try {
                parts = appCachedLoc.split(",");
                if (parts.length >= 2) {
                    appLat = Double.parseDouble(parts[0]);
                    appLon = Double.parseDouble(parts[1]);
                    res = new float[1];
                    Location.distanceBetween(appLat, appLon, lastLoc.getLatitude(), lastLoc.getLongitude(), res);
                    
                    if (res[0] > 100.0f) {
                        hasMoved = true;
                        tasker.log("Watchdog: System shows we moved " + Math.round(res[0]) + "m, but listener is silent!");
                    }
                }
            } catch (Exception e) {}
        }
        
        if (hasMoved) {
            isZombie = true;
        } else if (systemAgeSeconds < 600 && appAgeSeconds > 1800 && !isConnected) {
            /* Legacy fallback: if displacement check couldn't run but we are completely disconnected and stale */
            isZombie = true;
        }
    }
    
    if (isZombie) {
        isListenerHealthy = false;
        tasker.setVariable("AAB_LocnListener_healthy", "false");
        tasker.log("Watchdog: Zombie Listener detected & flagged for restart.");
    }
}

/* ========================================
 * PHASE 2: EARLY EXIT DECISION
 * ======================================== */

/* AI doesn't understand tasker's variable scope, so this keeps popping back in. Guess there's no harm? */
tasker.setVariable("should_stop", "false");

if (isListenerHealthy && isConnected && !isMovingFast) {
    tasker.setVariable("needs_active_poll", "false");
    tasker.setVariable("loc_update_source", "LISTENER_ANCHORED");
    tasker.setVariable("adaptive_mode", "ANCHORED (Listener Active)");
    tasker.setVariable("should_stop", "true");
    tasker.log("Refresher: Early Exit (Listener Healthy + Wifi + Stationary)");
    return;
}

/* 1.8 Log Failure/Override Reason */
if (!isListenerHealthy) {
    tasker.setVariable("poll_reason", "Watchdog: Listener dead or null");
} else if (isMovingFast && isConnected) {
    tasker.log("Watchdog: WiFi connected but moving fast. Forcing full evaluation.");
}

/* ========================================
 * PHASE 3: CONTEXT TIER CALCULATION
 * ======================================== */
scanAvailable = false;
if (wifiMgr != null) {
    try {
        scanAvailable = wifiMgr.isScanAlwaysAvailable();
    } catch (Exception e) {}
}

tier = "UNKNOWN";
cacheThreshold = 600;

if (!scanAvailable) {
    tier = "BLIND (Scanning Off)";
    cacheThreshold = 220; /* 3m 40s */
} else if (isConnected && !isMovingFast) {
    tier = "ANCHORED (Stationary)";
    cacheThreshold = 1800;
} else if (isConnected && isMovingFast) {
    tier = "ROAMING (Connected+Mobile)";
    cacheThreshold = 200; 
} else {
    tier = "ROAMING (Disconnected)";
    cacheThreshold = 240;
}
tasker.setVariable("adaptive_mode", tier);

/* ========================================
 * PHASE 3b: EXPONENTIAL BACKOFF ADJUSTMENT
 * ======================================== */
backOff = 0;
backOffStr = tasker.getVariable("AAB_LocnBackOff");
if (backOffStr != null && !backOffStr.equals("")) {
    try {
        backOff = Integer.parseInt(backOffStr.trim());
    } catch (Exception e) {
        tasker.setVariable("AAB_LocnBackOff", "0");
    }
}

/* Artificially increase backoff during Power Save to throttle active polls */
if (isPowerSave) {
    backOff = backOff + 3; /* Effectively applies an 8x multiplier */
}

/* Safety bounds */
if (backOff < 0) backOff = 0;

if (backOff > 0) {
    cappedLevel = Math.min(backOff, 5); /* 2^5 = 32x max multiplier */
    multiplier = 1L << cappedLevel; // Bitwise shift for power of 2
    rawThreshold = cacheThreshold * multiplier;
    cacheThreshold = (int) Math.min(rawThreshold, 3600); // Cap at 1 hour
    
    tasker.log("Backoff active: level=" + backOff + " multiplier=x" + multiplier + " threshold=" + cacheThreshold + "s (" + tier + ")");
    tasker.setVariable("adaptive_mode", tier + " [Backoff L" + backOff + "]");
}

/* ========================================
 * PHASE 4: DUAL-CACHE CHECK
 * ======================================== */
needsActivePoll = false;
bestCache = null;
bestCacheAge = 999999999L;
cacheSource = "NONE";

/* CACHE OPTION 1: APP CACHE */
if (appCachedLoc != null && !appCachedLoc.equals("") && appCacheTMS != null && !appCacheTMS.equals("0")) {
    if (appAgeSeconds >= 0) {
        bestCacheAge = appAgeSeconds;
        bestCache = appCachedLoc;
        cacheSource = "APP";
    }
}

/* CACHE OPTION 2: ANDROID SYSTEM CACHE */
systemCacheTrustCap = 600;
if (lastLoc != null) {
    if (systemAgeSeconds >= 0 && systemAgeSeconds < bestCacheAge && systemAgeSeconds < systemCacheTrustCap) {
        bestCacheAge = systemAgeSeconds;
        bestCache = lastLoc.getLatitude() + "," + lastLoc.getLongitude();
        cacheSource = "ANDROID (" + providerUsed + ")";
    }
}

/* ========================================
 * DECISION LOGIC
 * ======================================== */
if (bestCache == null) {
    needsActivePoll = true;
    tasker.setVariable("poll_reason", "No cached location (system or app)");
} else if (bestCacheAge > cacheThreshold) {
    needsActivePoll = true; // <--- THIS LINE WAS MISSING
    tasker.setVariable("poll_reason", cacheSource + " cache stale (" + bestCacheAge + "s > " + cacheThreshold + "s " + tier + " limit)");
} else {
    needsActivePoll = false;
    tasker.setVariable("cached_location", bestCache);
    tasker.setVariable("poll_reason", "Valid " + cacheSource + " Cache (" + tier + ") Age: " + bestCacheAge + "s");
}

/* ========================================
 * FINALIZE
 * ======================================== */
tasker.setVariable("needs_active_poll", needsActivePoll ? "true" : "false");
tasker.setVariable("cache_source", cacheSource);

if (needsActivePoll) {
    tasker.setVariable("loc_update_source", "TIMER_ACTIVE_POLL");
} else {
    tasker.setVariable("loc_update_source", "CACHE_" + cacheSource);
}
