/* === EXTRACTED JAVA BLOCK (S1 ground-truth, verbatim, html.unescape-decoded) ===
 * Task: task630 "_ContextLocnListener V4"
 * Block: #2 of 2 in this task
 * XML: Advanced_Auto_Brightness_V3.3.prj_9.xml  arg0 (Java source) L27818-L27892; <code>474</code> at L27817
 * Output: none (uses tasker.setVariable() directly)
 * %vars consumed (S4 parameters): (none)
 * ============================================================================ */
import android.location.LocationManager;
import android.location.LocationListener;
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
    
    maxLogSize = 4000;
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

/* Sends par1 to the _LocnListenerToast task via callTask. */
void debugToast(String msg) {
    map = new HashMap();
    map.put("par1", msg);
    tasker.callTask("_LocnListenerToast", map);
}

/* =======================================================
   STOP LISTENER
   ======================================================= */
listener = tasker.getJavaVariable("locationListener");
locMgr = tasker.getJavaVariable("locationManager");

if (listener != null && listener != void && locMgr != null && locMgr != void) {
    try {
        locMgr.removeUpdates(listener);
        log("Listener stopped.");
    } catch (e) {
        log("Err stopping: " + e.getMessage());
    }
} else {
    log("No active listener found to stop.");
}

/* Clear global references */
tasker.setJavaVariable("locationListener", null);
tasker.setJavaVariable("locationManager", null);
tasker.setVariable("AAB_LocnListener_healthy", "false");

/* =======================================================
   DEBUG: SHOW LOG VIA TOAST
   ======================================================= */
debugCheck = tasker.getVariable("AAB_Debug");
if ("9".equals(debugCheck)) {
    logSnapshot = tasker.getVariable("AAB_LocnLog");
    if (logSnapshot != null && logSnapshot.length() > 0) {
        debugToast(logSnapshot);
    }
}
