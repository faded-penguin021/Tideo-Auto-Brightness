/* === EXTRACTED JAVA BLOCK (S1 ground-truth, verbatim, html.unescape-decoded) ===
 * Task: task631 "_ContextF5NetLoc V8"
 * Block: #2 of 2 in this task
 * XML: Advanced_Auto_Brightness_V3.3.prj_9.xml  arg0 (Java source) L28436-L28533; <code>474</code> at L28432
 * Output: none (uses tasker.setVariable() directly)
 * %vars consumed (S4 parameters): (none)
 * ============================================================================ */
import android.location.Location;
import com.joaomgcd.taskerm.action.java.JavaCodeException;

candidateLocStr = tasker.getVariable("candidate_loc");
savedNetLocStr = tasker.getVariable("AAB_NetLocation");
needsPoll = tasker.getVariable("needs_active_poll");
updateSource = tasker.getVariable("loc_update_source");

if (candidateLocStr == null || candidateLocStr.equals("")) {
    throw new JavaCodeException("Candidate location is empty or not set.");
}

nowEpoch = System.currentTimeMillis() / 1000;

/* ========================================
 * CASE 1: NO PREVIOUS LOCATION - ALWAYS SAVE
 * ======================================== */

if (savedNetLocStr == null || savedNetLocStr.equals("")) {
    tasker.setVariable("AAB_NetLocation", candidateLocStr);
    tasker.setVariable("AAB_NetLocationTMS", String.valueOf(nowEpoch));
    
    tasker.setVariable("eval_log", "Initial Location Set [" + updateSource + "]: " + candidateLocStr + " (tms=" + nowEpoch + ")");
    tasker.log("Location: Initial set - " + candidateLocStr + " @ " + nowEpoch);
    return;
}

/* ========================================
 * CASE 2: ACTIVE POLL - ALWAYS UPDATE CACHE
 * ========================================
 * Fresh polled data is valuable regardless of distance.
 * This ensures cache is immediately valid for next profile run.
 */

if (needsPoll != null && needsPoll.equals("true")) {
    tasker.setVariable("AAB_OldLOCN", savedNetLocStr);
    tasker.setVariable("AAB_NetLocation", candidateLocStr);
    tasker.setVariable("AAB_NetLocationTMS", String.valueOf(nowEpoch));
    
    // Calculate distance for logging purposes only
    candParts = candidateLocStr.split(",");
    savedParts = savedNetLocStr.split(",");
    
    if (candParts.length >= 2 && savedParts.length >= 2) {
        candLat = Double.parseDouble(candParts[0]);
        candLon = Double.parseDouble(candParts[1]);
        savedLat = Double.parseDouble(savedParts[0]);
        savedLon = Double.parseDouble(savedParts[1]);
        
        results = new float[1];
        Location.distanceBetween(savedLat, savedLon, candLat, candLon, results);
        distance = results[0];
        distanceLabel = Math.round(distance);
        
        tasker.setVariable("eval_log", "Location Updated [" + updateSource + "]. Moved " + distanceLabel + "m (tms=" + nowEpoch + ")");
        tasker.log("Location: Active poll - ALWAYS updating cache - " + candidateLocStr + " @ " + nowEpoch + " (moved " + distanceLabel + "m)");
    } else {
        tasker.setVariable("eval_log", "Location Updated [" + updateSource + "] (tms=" + nowEpoch + ")");
        tasker.log("Location: Active poll - ALWAYS updating cache - " + candidateLocStr + " @ " + nowEpoch);
    }
    return;
}

/* ========================================
 * CASE 3: USING CACHE - APPLY 100M DEBOUNCE
 * ========================================
 * Only apply distance threshold when NOT actively polling.
 * This prevents unnecessary updates from location noise.
 */

candParts = candidateLocStr.split(",");
savedParts = savedNetLocStr.split(",");

if (candParts.length < 2 || savedParts.length < 2) {
    throw new JavaCodeException("Location strings are malformed: [" + candidateLocStr + "] vs [" + savedNetLocStr + "]");
}

candLat = Double.parseDouble(candParts[0]);
candLon = Double.parseDouble(candParts[1]);
savedLat = Double.parseDouble(savedParts[0]);
savedLon = Double.parseDouble(savedParts[1]);

results = new float[1];
Location.distanceBetween(savedLat, savedLon, candLat, candLon, results);
distance = results[0];
distanceLabel = Math.round(distance);

if (distance >= 100) {
    tasker.setVariable("AAB_OldLOCN", savedNetLocStr);
    tasker.setVariable("AAB_NetLocation", candidateLocStr);
    tasker.setVariable("AAB_NetLocationTMS", String.valueOf(nowEpoch));
    
    tasker.setVariable("eval_log", "Location Updated [" + updateSource + "]. Moved " + distanceLabel + "m (tms=" + nowEpoch + ")");
    tasker.log("Location: Cache update - " + candidateLocStr + " @ " + nowEpoch + " (moved " + distanceLabel + "m)");
} else {
    tasker.setVariable("eval_log", "Location Debounced. Moved " + distanceLabel + "m (Ignored < 100m)");
    tasker.log("Location: Debounced - " + distanceLabel + "m < 100m threshold (keeping old cache)");
}
