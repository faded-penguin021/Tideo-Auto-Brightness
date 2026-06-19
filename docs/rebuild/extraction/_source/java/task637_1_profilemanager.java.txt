/* === EXTRACTED JAVA BLOCK (S1 ground-truth, verbatim, html.unescape-decoded) ===
 * Task: task637 "_ProfileManager"
 * Block: #1 of 1 in this task
 * XML: Advanced_Auto_Brightness_V3.3.prj_9.xml  arg0 (Java source) L29304-L29611; <code>474</code> at L29303
 * Output: none (uses tasker.setVariable() directly)
 * %vars consumed (S4 parameters): (none)
 * ============================================================================ */
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;

/* --- Pass 1: Gather Command Inputs --- */
String MODE = tasker.getVariable("par1");
String DATA = tasker.getVariable("par2");
String caller = tasker.getVariable("caller1");

if (MODE == null) MODE = "load";
String BASE_PATH = "/storage/emulated/0/Download/AAB/configs/";

/* Helper: Determine if we should update the user baseline variable */
boolean isManualLoad = (caller == null || !caller.contains("_EvaluateContexts"));

/* UI Payload Container */
JSONObject uiPayload = new JSONObject();

File getCleanFile(String input, String basePath) {
    if (input.startsWith("/")) return new File(input);
    String fileName = input.replaceAll("[^a-zA-Z0-9.\\- ]", "_");
    if (!fileName.toLowerCase().endsWith(".json")) fileName += ".json";
    return new File(basePath, fileName);
}

double getG(String name, double def) {
    String val = tasker.getVariable(name);
    if (val != null && val.length() > 0) try { return Double.parseDouble(val); } catch(Exception e){}
    return def;
}

boolean getB(String name, boolean def) {
    String val = tasker.getVariable(name);
    if (val != null) return val.equalsIgnoreCase("On") || val.equalsIgnoreCase("true");
    return def;
}

int getI(String name, int def) {
    String val = tasker.getVariable(name);
    if (val != null && val.length() > 0) try { return (int)Double.parseDouble(val); } catch(Exception e){}
    return def;
}

/* Updated setG: Sets global variable AND adds to UI payload */
void setG(String name, String val) { 
    tasker.setVariable(name, val);
    try { uiPayload.put(name, val); } catch(Exception e){}
}

double round(double value, int places) {
    if (places < 0) return value;
    try {
        BigDecimal bd = new BigDecimal(value);
        return bd.setScale(places, RoundingMode.HALF_UP).doubleValue();
    } catch (Exception e) { return value; }
}

void performSave(String profileName, boolean silent) {
    try {
        File file = getCleanFile(profileName, BASE_PATH);
        JSONObject root = new JSONObject();
        JSONObject meta = new JSONObject();
        meta.put("name", profileName);
        meta.put("version", tasker.getVariable("AAB_Version"));
        meta.put("timestamp", System.currentTimeMillis());
        root.put("meta", meta);

        JSONObject general = new JSONObject();
        general.put("z1_end", getG("AAB_Zone1End", 35.0));
        general.put("z2_end", getG("AAB_Zone2End", 10000.0));
        general.put("form1a", getG("AAB_Form1A", 5.0));
        general.put("form2a", getG("AAB_Form2A", 29.5));
        general.put("form2b", getG("AAB_Form2B", 8.8));
        general.put("form2c", getG("AAB_Form2C", 18.0));
        general.put("form2d", getG("AAB_Form2D", 35.0));
        general.put("form3a", getG("AAB_Form3A", 2513.0));
        root.put("general", general);

        JSONObject misc = new JSONObject();
        misc.put("min_bright", getG("AAB_MinBright", 10.0));
        misc.put("max_bright", getG("AAB_MaxBright", 255.0));
        misc.put("scale", getG("AAB_Scale", 1.0));
        misc.put("offset", getG("AAB_Offset", 0.0));
        misc.put("anim_steps", getI("AAB_AnimSteps", 50));
        misc.put("min_wait", getI("AAB_MinWait", 5));
        misc.put("max_wait", getI("AAB_MaxWait", 30));
        misc.put("delta_factor", getG("AAB_DeltaFactor", 1.8));
        misc.put("throttle", getI("AAB_Throttle", 1000));
        root.put("misc", misc);

        JSONObject reactivity = new JSONObject();
        reactivity.put("detect_overrides", getB("AAB_DetectOverrides", false));
        reactivity.put("thresh_dark", getG("AAB_ThreshDark", 0.3));
        reactivity.put("thresh_dim", getG("AAB_ThreshDim", 0.25));
        reactivity.put("thresh_bright", getG("AAB_ThreshBright", 0.08));
        reactivity.put("thresh_steepness", getG("AAB_ThreshSteepness", 2.1));
        reactivity.put("thresh_midpoint", getG("AAB_ThreshMidpoint", 3.0));
        reactivity.put("trust_unreliable", getB("AAB_TrustUnreliable", false));
        root.put("reactivity", reactivity);

        JSONObject circadian = new JSONObject();
        circadian.put("spread", getG("AAB_ScaleSpread", 15.0));
        circadian.put("transition", getG("AAB_ScaleTransitionFactor", 0.1));
        circadian.put("steepness", getG("AAB_ScaleSteepness", 6.0));
        circadian.put("enabled", getB("AAB_ScalingUse", false));
        circadian.put("taper_mid", getG("AAB_ScaleTaperMidpoint", 190.0));
        circadian.put("taper_steep", getG("AAB_ScaleTaperSteepness", 0.075));
        circadian.put("qs_use", getB("AAB_QSUse", false));
        root.put("circadian", circadian);

        JSONObject superdimming = new JSONObject();
        superdimming.put("enabled", getB("AAB_DimmingEnabled", false));
        superdimming.put("threshold", getG("AAB_DimmingThreshold", 15.0));
        superdimming.put("strength", getG("AAB_DimmingStrength", 25.0));
        superdimming.put("exponent", getG("AAB_DimmingExponent", 2.5));
        superdimming.put("spread", getG("AAB_DimSpread", 100.0));
        superdimming.put("pwm_exp", getG("AAB_PWMExp", 0.8));
        superdimming.put("pwm_sensitive", getB("AAB_PWMSensitive", false));
        root.put("superdimming", superdimming);

        FileWriter writer = new FileWriter(file);
        writer.write(root.toString(4));
        writer.flush(); writer.close();
        tasker.setVariable("saved", silent ? "silent" : "true");
    } catch (Exception e) { tasker.setVariable("err_msg", e.getMessage()); }
}

void performLoad(String input) {
    try {
        File file = getCleanFile(input, BASE_PATH);
        
        /* --- SELF-HEALING: RECREATE DEFAULT IF DELETED --- */
        if (!file.exists()) {
            if (input.equalsIgnoreCase("Default")) {
                // Generate emergency default JSON
                JSONObject root = new JSONObject();
                JSONObject meta = new JSONObject();
                meta.put("name", "Default");
                meta.put("version", "3.3");
                root.put("meta", meta);
                
                JSONObject general = new JSONObject();
                general.put("z1_end", 35.0); general.put("z2_end", 10000.0);
                general.put("form1a", 5.0); general.put("form2a", 29.58);
                general.put("form2b", 8.8); general.put("form2c", 18.0);
                general.put("form2d", 35.0); general.put("form3a", 2513.0);
                root.put("general", general);
                
                JSONObject misc = new JSONObject();
                misc.put("min_bright", 10.0); misc.put("max_bright", 255.0);
                misc.put("scale", 1.0); misc.put("offset", 0.0);
                misc.put("anim_steps", 50); misc.put("min_wait", 5);
                misc.put("max_wait", 30); misc.put("throttle", 1510);
                misc.put("delta_factor", 1.8);
                root.put("misc", misc);
                
                JSONObject reactivity = new JSONObject();
                reactivity.put("thresh_dark", 0.3); reactivity.put("thresh_dim", 0.25);
                reactivity.put("thresh_bright", 0.08); reactivity.put("thresh_steepness", 2.1);
                reactivity.put("thresh_midpoint", 3.0);
                root.put("reactivity", reactivity);

                // Defaults: Disabled
                root.put("circadian", new JSONObject().put("enabled", false));
                root.put("superdimming", new JSONObject().put("enabled", false));

                // Write to disk immediately
                FileWriter writer = new FileWriter(file);
                writer.write(root.toString(4));
                writer.flush(); writer.close();
                
                String debugVal = tasker.getVariable("AAB_Debug");
                if (debugVal != null && debugVal.equals("8")) {
                   tasker.setVariable("eval_log", "System restored missing Default.json");
                }
            } else {
                tasker.setVariable("err_msg", "File not found: " + file.getAbsolutePath());
                return;
            }
        }
        
        BufferedReader reader = new BufferedReader(new FileReader(file));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        reader.close();
        JSONObject root = new JSONObject(sb.toString());

        /* --- Baseline Protection Fix --- */
        String loadedName = root.optJSONObject("meta").optString("name", input.replace(".json", ""));
        if (isManualLoad) {
            setG("AAB_ProfileUser", loadedName);
        }

        double c_z1_end = getG("AAB_Zone1End", 35.0);
        double c_z2_end = getG("AAB_Zone2End", 10000.0);
        double c_form1a = getG("AAB_Form1A", 5.0);
        double c_form2b = getG("AAB_Form2B", 8.8);
        double c_form2c = getG("AAB_Form2C", 18.0);
        double c_max_bright = getG("AAB_MaxBright", 255.0);

        JSONObject general = root.optJSONObject("general");
        if (general != null) {
            if(general.has("z1_end")) { c_z1_end = general.getDouble("z1_end"); setG("AAB_Zone1End", String.valueOf(c_z1_end)); }
            if(general.has("z2_end")) { c_z2_end = general.getDouble("z2_end"); setG("AAB_Zone2End", String.valueOf(c_z2_end)); }
            if(general.has("form1a")) { c_form1a = general.getDouble("form1a"); setG("AAB_Form1A", String.valueOf(c_form1a)); }
            if(general.has("form2b")) { c_form2b = general.getDouble("form2b"); setG("AAB_Form2B", String.valueOf(c_form2b)); }
            if(general.has("form2c")) { c_form2c = general.getDouble("form2c"); setG("AAB_Form2C", String.valueOf(c_form2c)); }
            if(general.has("form2d")) setG("AAB_Form2D", String.valueOf(general.getDouble("form2d")));
        }

        JSONObject misc = root.optJSONObject("misc");
        if (misc != null) {
            if(misc.has("min_bright")) setG("AAB_MinBright", String.valueOf(misc.getDouble("min_bright")));
            if(misc.has("max_bright")) { c_max_bright = misc.getDouble("max_bright"); setG("AAB_MaxBright", String.valueOf(c_max_bright)); }
            if(misc.has("scale")) setG("AAB_Scale", String.valueOf(misc.getDouble("scale")));
            if(misc.has("offset")) setG("AAB_Offset", String.valueOf(misc.getDouble("offset")));
            if(misc.has("anim_steps")) setG("AAB_AnimSteps", String.valueOf(misc.getInt("anim_steps")));
            if(misc.has("min_wait")) setG("AAB_MinWait", String.valueOf(misc.getInt("min_wait")));
            if(misc.has("max_wait")) setG("AAB_MaxWait", String.valueOf(misc.getInt("max_wait")));
            if(misc.has("delta_factor")) setG("AAB_DeltaFactor", String.valueOf(misc.getDouble("delta_factor")));
            if(misc.has("throttle")) setG("AAB_Throttle", String.valueOf(misc.getInt("throttle")));
        }

        JSONObject reactivity = root.optJSONObject("reactivity");
        if (reactivity != null) {
            if(reactivity.has("detect_overrides")) setG("AAB_DetectOverrides", reactivity.getBoolean("detect_overrides") ? "On" : "Off");
            if(reactivity.has("trust_unreliable")) setG("AAB_TrustUnreliable", reactivity.getBoolean("trust_unreliable") ? "On" : "Off");
            if(reactivity.has("thresh_dark")) setG("AAB_ThreshDark", String.valueOf(reactivity.getDouble("thresh_dark")));
            if(reactivity.has("thresh_dim")) setG("AAB_ThreshDim", String.valueOf(reactivity.getDouble("thresh_dim")));
            if(reactivity.has("thresh_bright")) setG("AAB_ThreshBright", String.valueOf(reactivity.getDouble("thresh_bright")));
            if(reactivity.has("thresh_steepness")) setG("AAB_ThreshSteepness", String.valueOf(reactivity.getDouble("thresh_steepness")));
            if(reactivity.has("thresh_midpoint")) setG("AAB_ThreshMidpoint", String.valueOf(reactivity.getDouble("thresh_midpoint")));
        }

        JSONObject circadian = root.optJSONObject("circadian");
        if (circadian != null) {
            if(circadian.has("enabled")) setG("AAB_ScalingUse", circadian.getBoolean("enabled") ? "true" : "false");
            if(circadian.has("qs_use")) setG("AAB_QSUse", circadian.getBoolean("qs_use") ? "true" : "false");
            if(circadian.has("spread")) setG("AAB_ScaleSpread", String.valueOf(circadian.getDouble("spread")));
            if(circadian.has("transition")) setG("AAB_ScaleTransitionFactor", String.valueOf(circadian.getDouble("transition")));
            if(circadian.has("steepness")) setG("AAB_ScaleSteepness", String.valueOf(circadian.getDouble("steepness")));
            if(circadian.has("taper_mid")) setG("AAB_ScaleTaperMidpoint", String.valueOf(circadian.getDouble("taper_mid")));
            if(circadian.has("taper_steep")) setG("AAB_ScaleTaperSteepness", String.valueOf(circadian.getDouble("taper_steep")));
        }

        JSONObject superdimming = root.optJSONObject("superdimming");
        if (superdimming != null) {
            if(superdimming.has("enabled")) setG("AAB_DimmingEnabled", superdimming.getBoolean("enabled") ? "true" : "false");
            if(superdimming.has("pwm_sensitive")) setG("AAB_PWMSensitive", superdimming.getBoolean("pwm_sensitive") ? "true" : "false");
            if(superdimming.has("threshold")) setG("AAB_DimmingThreshold", String.valueOf(superdimming.getDouble("threshold")));
            if(superdimming.has("strength")) setG("AAB_DimmingStrength", String.valueOf(superdimming.getDouble("strength")));
            if(superdimming.has("exponent")) setG("AAB_DimmingExponent", String.valueOf(superdimming.getDouble("exponent")));
            if(superdimming.has("spread")) setG("AAB_DimSpread", String.valueOf(superdimming.getDouble("spread")));
            if(superdimming.has("pwm_exp")) setG("AAB_PWMExp", String.valueOf(superdimming.getDouble("pwm_exp")));
        }

        double calc_form2a = c_form1a * Math.sqrt(c_z1_end);
        setG("AAB_Form2A", String.valueOf(round(calc_form2a, 3)));
        double term1 = Math.pow(Math.abs(c_z2_end - c_form2c), 0.33);
        double term2 = Math.pow(Math.abs(c_z1_end - c_form2c), 0.33);
        double num = c_max_bright - (calc_form2a + c_form2b * (term1 - term2));
        double calc_form3a = (c_z2_end * num) / c_max_bright;
        setG("AAB_Form3A", String.valueOf(round(calc_form3a, 0)));

        tasker.setVariable("loaded", "true");
        tasker.setVariable("ui_update_json", uiPayload.toString());
        
    } catch (Exception e) { tasker.setVariable("err_msg", e.getMessage()); }
}

void performDelete(String input) {
    try {
        File file = getCleanFile(input, BASE_PATH);
        
        // SAFETY 1: Prevent deleting Default via backend logic
        if (input.equalsIgnoreCase("Default")) {
            tasker.setVariable("err_msg", "Cannot delete Default profile.");
            return;
        }

        if (file.exists() && file.delete()) {
            tasker.setVariable("deleted", "true");
            
            // SAFETY 2: Fallback logic
            // If we deleted the profile that acts as the current Base/User Profile,
            // we must fallback the base to Default immediately.
            String currentUserProf = tasker.getVariable("AAB_ProfileUser");
            if (currentUserProf != null && currentUserProf.equals(input)) {
                setG("AAB_ProfileUser", "Default");
            }
        }
        else {
            tasker.setVariable("err_msg", "Delete failed or file missing.");
        }
    } catch (Exception e) { tasker.setVariable("err_msg", e.getMessage()); }
}

tasker.setVariable("saved", null); tasker.setVariable("loaded", null); tasker.setVariable("deleted", null); tasker.setVariable("err_msg", null);

if (MODE.equals("SAVE_FILE")) performSave(DATA, false);
else if (MODE.equals("SAVE_FILE_SILENT")) performSave(DATA, true);
else if (MODE.equals("LOAD_FILE")) performLoad(DATA);
else if (MODE.equals("DELETE_FILE")) performDelete(DATA);
