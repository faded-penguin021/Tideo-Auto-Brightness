/* === EXTRACTED JAVA BLOCK (S1 ground-truth, verbatim, html.unescape-decoded) ===
 * Task: task630 "_ContextLocnListener V4"
 * Block: #1 of 2 in this task
 * XML: Advanced_Auto_Brightness_V3.3.prj_9.xml  arg0 (Java source) L27586-L27776; <code>474</code> at L27585
 * Output: none (uses tasker.setVariable() directly)
 * %vars consumed (S4 parameters): (none)
 * ============================================================================ */
import android.location.LocationManager;
import android.location.Location;
import android.location.LocationListener;
import android.content.Context;
import android.os.Bundle;
import android.os.Looper;
import java.util.List;
import java.util.HashMap;
import java.util.Date;
import java.text.SimpleDateFormat;

/* =======================================================
   HELPER: ROLLING LOG WITH HEADER PRESERVATION
   ======================================================= */
SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");

void log(String msg) {
    ts = sdf.format(new Date());
    current = tasker.getVariable("AAB_LocnLog");
    if (current == null) current = "";
    
    newEntry = ts + " | " + msg + "\n";
    newLog = current + newEntry;
    
    maxLogSize = 1000;
    headerSize = 300;
    
    if (newLog.length() > maxLogSize) {
        header = newLog.substring(0, Math.min(newLog.length(), headerSize));
        trimMsg = "\n... [LOG TRIMMED] ...\n";
        
        tailSpace = maxLogSize - header.length() - trimMsg.length();
        if (tailSpace < 0) tailSpace = 0;
        
        tail = newLog.substring(newLog.length() - tailSpace);
        newLog = header + trimMsg + tail;
    }
    
    tasker.setVariable("AAB_LocnLog", newLog);
}

void debugToast(String msg) {
    map = new HashMap();
    map.put("par1", msg);
    tasker.callTask("_LocnListenerToast", map);
}

/* =======================================================
   CLEANUP & INIT
   ======================================================= */
existingListener = tasker.getJavaVariable("locationListener");
if (existingListener != null && existingListener != void) {
    lMgr = tasker.getJavaVariable("locationManager");
    if (lMgr != null && lMgr != void) lMgr.removeUpdates(existingListener);
    log("Restarting listener...");
} else {
    log("Starting listener...");
}

locMgr = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
if (locMgr == null) {
    log("Error: LocationManager unavailable");
    tasker.setVariable("listener_error", "No LocationManager");
    return;
}

/* =======================================================
   LISTENER LOGIC
   ======================================================= */
listener = new LocationListener() {
    onLocationChanged(paramLoc) {
        try {
            /* Handle List<Location> overload vs Single Location */
            finalLoc = paramLoc;
            if (paramLoc instanceof List) {
                list = (List) paramLoc;
                if (list.isEmpty()) return;
                finalLoc = (Location) list.get(0);
            }

            /* [FIX] HEARTBEAT & BACKOFF RESET
               We record the time of *any* callback from the OS.
               This proves the listener is alive, even if we discard the location. */
            nowMs = System.currentTimeMillis();
            tasker.setVariable("AAB_ListenerHeartbeatTMS", String.valueOf(nowMs));
            
            /* [FIX] If the OS is talking to us, we don't need to back off. */
            tasker.setVariable("AAB_LocnBackOff", "0");

            acc = finalLoc.getAccuracy();
            coords = finalLoc.getLatitude() + "," + finalLoc.getLongitude();
            nowSecs = nowMs / 1000;

            /* 1. Accuracy Filter (> 500m) */
            if (acc > 500) {
                log("Ignored: Low Acc (" + Math.round(acc) + "m)");
                return;
            }

            /* 2. Retrieve Previous */
            prevLoc = tasker.getVariable("AAB_NetLocation");

            /* 3. First Fix Handling */
            if (prevLoc == null || prevLoc.length() == 0) {
                tasker.setVariable("AAB_NetLocation", coords);
                tasker.setVariable("AAB_NetLocationTMS", String.valueOf(nowSecs));
                tasker.setVariable("loc_update_source", "INIT");
                log("Init: " + coords + " | Acc: " + Math.round(acc) + "m");
                return;
            }

            /* 4. Distance Calculation */
            parts = prevLoc.split(",");
            if (parts.length >= 2) {
                res = new float[1];
                Location.distanceBetween(
                    Double.parseDouble(parts[0]), Double.parseDouble(parts[1]),
                    finalLoc.getLatitude(), finalLoc.getLongitude(),
                    res
                );
                dist = res[0];

                /* 5. Distance Threshold (>= 100m) */
                if (dist >= 100) {
                    tasker.setVariable("AAB_NetLocation", coords);
                    tasker.setVariable("AAB_NetLocationTMS", String.valueOf(nowSecs));
                    tasker.setVariable("loc_update_source", "LISTENER");

                    log("Upd: " + coords + " | Move: " + Math.round(dist) + "m | Acc: " + Math.round(acc) + "m");
                } else {
                    log("Skip: Moved " + Math.round(dist) + "m (<100m) | Acc: " + Math.round(acc) + "m");
                }
            }
        } catch (e) {
            log("Err in callback: " + e.getMessage());
        }
    }

    onStatusChanged(p, s, e) {}
    onProviderEnabled(p) {}
    onProviderDisabled(p) {
        log("Provider disabled: " + p);
    }
};

/* =======================================================
   REGISTER (ROBUST PROVIDER SELECTION)
   ======================================================= */
tTime = 180000L; /* 3 minutes (Long) */
tDist = 100.0f;  /* 100 meters (Float) */
provider = "none";

try {
    /* [FIX] Check available providers instead of blind try/catch */
    List allProviders = locMgr.getAllProviders();
    
    if (allProviders.contains("fused")) {
        provider = "fused";
    } else if (allProviders.contains("network")) {
        provider = "network";
    } else if (allProviders.contains("gps")) {
        provider = "gps";
    }

    if (!provider.equals("none")) {
        locMgr.requestLocationUpdates(provider, tTime, tDist, listener, Looper.getMainLooper());
    } else {
        throw new Exception("No suitable providers (fused/network/gps) found.");
    }

} catch (ex) {
    log("Fatal: Registration failed. " + ex.getMessage());
    tasker.setVariable("listener_error", ex.getMessage());
    return;
}

/* Global housekeeping */
tasker.setJavaVariable("locationListener", listener);
tasker.setJavaVariable("locationManager", locMgr);
tasker.setVariable("AAB_LocnListener_healthy", "true");
tasker.setVariable("loc_provider_actual", provider);
log("Registered: " + provider + " (3m/100m)");

/* Debug Toast (Init only) */
debugCheck = tasker.getVariable("AAB_Debug");
if ("9".equals(debugCheck)) {
    logSnapshot = tasker.getVariable("AAB_LocnLog");
    if (logSnapshot != null && logSnapshot.length() > 0) {
        debugToast(logSnapshot);
    }
}
