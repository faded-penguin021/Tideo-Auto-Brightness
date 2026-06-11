/* === EXTRACTED JAVA BLOCK (S1 ground-truth, verbatim, html.unescape-decoded) ===
 * Task: task623 "_ContextManager"
 * Block: #1 of 1 in this task
 * XML: Advanced_Auto_Brightness_V3.3.prj_9.xml  arg0 (Java source) L26927-L27029; <code>474</code> at L26926
 * Output: none (uses tasker.setVariable() directly)
 * %vars consumed (S4 parameters): (none)
 * ============================================================================ */
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.Iterator;

String filePath = "/storage/emulated/0/Download/AAB/configs/contexts.json";
String command = tasker.getVariable("par1");
String inputData = tasker.getVariable("par2");

try {
    File file = new File(filePath);
    JSONArray contextList;
    if (file.exists()) {
        try {
            String content = new String(Files.readAllBytes(Paths.get(filePath)));
            contextList = new JSONArray(content);
        } catch (Exception parseEx) {
            // If file exists but is corrupt, start fresh to allow overwriting
            contextList = new JSONArray();
        }
    } else {
        contextList = new JSONArray();
    }

    if ("SAVE_CONTEXT".equals(command)) {
        JSONObject newCtx = new JSONObject(inputData);
        String newId = newCtx.getString("id");
        boolean updated = false;
        for (int i = 0; i < contextList.length(); i++) {
            if (contextList.getJSONObject(i).getString("id").equals(newId)) {
                contextList.put(i, newCtx);
                updated = true;
                break;
            }
        }
        if (!updated) contextList.put(newCtx);
        tasker.setVariable("context_status", "Saved");
    } else if ("DELETE_CONTEXT".equals(command)) {
        JSONArray remaining = new JSONArray();
        for (int i = 0; i < contextList.length(); i++) {
            if (!contextList.getJSONObject(i).getString("id").equals(inputData)) {
                remaining.put(contextList.getJSONObject(i));
            }
        }
        contextList = remaining;
        tasker.setVariable("context_status", "Deleted");
    }

    /* --- ATOMIC WRITE VERIFICATION --- */
    File tmpFile = new File(filePath + ".tmp");
    FileWriter writer = new FileWriter(tmpFile);
    writer.write(contextList.toString(4));
    writer.flush();
    writer.close();
    
    if (!tmpFile.renameTo(file)) {
        if (file.delete() && tmpFile.renameTo(file)) {
        } else {
             throw new Exception("Atomic write failed. Check permissions/storage.");
        }
    }

    /* --- CACHE BUILDER --- */
    Set appSet = new HashSet();
    boolean hasBatt = false;
    boolean hasLoc = false;
    boolean hasWifi = false; /* <-- NEW */
    
    for (int i = 0; i < contextList.length(); i++) {
        JSONObject t = contextList.getJSONObject(i).optJSONObject("triggers");
        if (t != null) {
            if (t.has("apps") && !t.isNull("apps")) {
                JSONArray a = t.getJSONArray("apps");
                for(int j=0; j<a.length(); j++) appSet.add(a.getString(j));
            }
            if (t.has("battery") && !t.isNull("battery")) hasBatt = true;
            if (t.has("location") && !t.isNull("location")) hasLoc = true;
            if (t.has("wifi") && !t.isNull("wifi")) hasWifi = true; /* <-- NEW */
        }
    }
    StringBuilder sb = new StringBuilder();
    if (hasBatt) sb.append("[BATT]");
    if (hasLoc) sb.append("[LOC]");
    if (hasWifi) sb.append("[WIFI]");
    sb.append(",");
    Iterator it = appSet.iterator();
    while(it.hasNext()) {
        sb.append(it.next());
        sb.append(",");
    }
    tasker.setVariable("AAB_ContextCache", sb.toString());

/* --- POPULATE RAM CACHE FOR EVALUATOR --- */
tasker.setVariable("AAB_ContextJSONCache", contextList.toString());

} catch (Exception e) {
    tasker.setVariable("err_msg", "Context Manager: " + e.getMessage());
}
