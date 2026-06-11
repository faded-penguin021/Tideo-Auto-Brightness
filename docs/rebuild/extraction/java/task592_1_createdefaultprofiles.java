/* === EXTRACTED JAVA BLOCK (S1 ground-truth, verbatim, html.unescape-decoded) ===
 * Task: task592 "_CreateDefaultProfiles"
 * Block: #1 of 1 in this task
 * XML: Advanced_Auto_Brightness_V3.3.prj_9.xml  arg0 (Java source) L24133-L24360; <code>474</code> at L24132
 * Output: none (uses tasker.setVariable() directly)
 * %vars consumed (S4 parameters): (none)
 * ============================================================================ */
import org.json.JSONObject;
import java.io.File;
import java.io.FileWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;

/*
 * AAB Default Profile Generator
 * Generates: "Default", "Battery Saver", "Video Streaming", "Outdoors", and "Night Reading"
 */

/* --- Helper: Get Clean Base Object (Numerics Only) --- */
JSONObject getBaseProfile() {
    JSONObject root = new JSONObject();

    JSONObject meta = new JSONObject();
    // Dynamic Version
    String ver = tasker.getVariable("AAB_Version");
    meta.put("version", ver != null ? ver : "3.3");
    root.put("meta", meta);

    /* Curve Defaults */
    JSONObject general = new JSONObject();
    general.put("z1_end", 35.0);
    general.put("z2_end", 10000.0);
    general.put("form1a", 5.0);
    general.put("form2a", 29.58);
    general.put("form2b", 8.8);
    general.put("form2c", 18.0);
    general.put("form2d", 35.0);
    general.put("form3a", 2513.0);
    root.put("general", general);

    /* General Defaults */
    JSONObject misc = new JSONObject();
    misc.put("min_bright", 10.0);
    misc.put("max_bright", 255.0);
    misc.put("scale", 1.0);
    misc.put("offset", 0.0);
    misc.put("anim_steps", 50);
    misc.put("min_wait", 5);
    misc.put("max_wait", 30);
    misc.put("throttle", 1510); // 50*30 + 10
    misc.put("delta_factor", 1.8);
    root.put("misc", misc);

    /* Reactivity Defaults */
    JSONObject reactivity = new JSONObject();
    // Booleans removed to respect user choices
    reactivity.put("thresh_dark", 0.3);
    reactivity.put("thresh_dim", 0.25);
    reactivity.put("thresh_bright", 0.08);
    reactivity.put("thresh_steepness", 2.1);
    reactivity.put("thresh_midpoint", 3.0);
    root.put("reactivity", reactivity);

    /* Scaling Defaults */
    JSONObject circadian = new JSONObject();
    circadian.put("spread", 15.0);
    circadian.put("transition", 0.1);
    circadian.put("steepness", 6.0);
    circadian.put("taper_mid", 190.0);
    circadian.put("taper_steep", 0.075);
    circadian.put("enabled", false); // Default disabled
    root.put("circadian", circadian);

    /* Dimming Defaults */
    JSONObject superdimming = new JSONObject();
    superdimming.put("threshold", 15.0);
    superdimming.put("strength", 25.0);
    superdimming.put("exponent", 2.5);
    superdimming.put("spread", 100.0);
    superdimming.put("pwm_exp", 0.8);
    superdimming.put("enabled", false); // Default disabled
    superdimming.put("pwm_sensitive", false); // Default disabled
    root.put("superdimming", superdimming);

    return root;
}

/* --- Helper: Write to Disk --- */
void writeProfile(String name, JSONObject json) {
    try {
        /* Update Meta */
        JSONObject meta = json.optJSONObject("meta");
        meta.put("name", name);
        meta.put("timestamp", System.currentTimeMillis());

        /* Ensure Dir */
        File dir = new File("/storage/emulated/0/Download/AAB/configs/");
        if (!dir.exists()) dir.mkdirs();

        /* Write */
        File file = new File(dir, name + ".json");
        FileWriter fw = new FileWriter(file);
        fw.write(json.toString(4));
        fw.flush();
        fw.close();
        tasker.log("Created default profile: " + name);
    } catch (Exception e) {
        tasker.log("Error creating profile " + name + ": " + e.toString());
    }
}

/* --------------------------------------------------------- */
/* 1. DEFAULT (Baseline) */
JSONObject profDefault = getBaseProfile();
writeProfile("Default", profDefault);


/* --------------------------------------------------------- */
/* 2. BATTERY SAVER */
JSONObject profBattery = getBaseProfile();

JSONObject genBattery = profBattery.getJSONObject("misc");
genBattery.put("max_bright", 200.0);
genBattery.put("min_bright", 1.0);
genBattery.put("scale", 0.8);
genBattery.put("anim_steps", 1);
genBattery.put("delta_factor", 2.8);


JSONObject reactBattery = profBattery.getJSONObject("reactivity");
reactBattery.put("thresh_dark", 0.5);
reactBattery.put("thresh_dim", 0.5);
reactBattery.put("thresh_bright", 0.5);


// Disable both features for battery saver
JSONObject scaleBattery = profBattery.getJSONObject("circadian");
scaleBattery.put("enabled", false);

JSONObject dimBattery = profBattery.getJSONObject("superdimming");
dimBattery.put("enabled", false);

writeProfile("Battery Saver", profBattery);


/* --------------------------------------------------------- */
/* 3. VIDEO STREAMING */
JSONObject profVideo = getBaseProfile();

JSONObject genVideo = profVideo.getJSONObject("misc");
genVideo.put("anim_steps", 50);
genVideo.put("min_wait", 50);
genVideo.put("max_wait", 100);
genVideo.put("min_bright", 20.0);
genVideo.put("max_bright", 255.0);
genVideo.put("delta_factor", 0.5);
genVideo.put("throttle", 5010); 

JSONObject reactVideo = profVideo.getJSONObject("reactivity");
reactVideo.put("thresh_bright", 0.3);
reactVideo.put("thresh_dark", 0.4);

JSONObject curveVideo = profVideo.getJSONObject("general");
curveVideo.put("form1a", 6.0);
curveVideo.put("form2b", 8.8);

// Disable Circadian
JSONObject scaleVideo = profVideo.getJSONObject("circadian");
scaleVideo.put("enabled", false);

// Enable Super Dimming for a better dark-room viewing experience.
JSONObject dimVideo = profVideo.getJSONObject("superdimming");
dimVideo.put("enabled", true);
dimVideo.put("threshold", 20.0);

writeProfile("Video Streaming", profVideo);


/* --------------------------------------------------------- */
/* 4. OUTDOORS */
JSONObject profOutdoor = getBaseProfile();

JSONObject genOutdoor = profOutdoor.getJSONObject("misc");
genOutdoor.put("min_bright", 25.0);
genOutdoor.put("offset", 15.0);
genOutdoor.put("scale", 1.15);
genOutdoor.put("anim_steps", 10);
genOutdoor.put("min_wait", 10);
genOutdoor.put("delta_factor", 4.0);


JSONObject curveOutdoor = profOutdoor.getJSONObject("general");
curveOutdoor.put("form1a", 8.0);
curveOutdoor.put("z1_end", 55.0); // Extended zone 1
curveOutdoor.put("z2_end", 18000.0);

/* Recalculate Form2A for math continuity */
double z1 = curveOutdoor.getDouble("z1_end");
double f1a = curveOutdoor.getDouble("form1a");
double f2a = f1a * Math.sqrt(z1);
BigDecimal bd = new BigDecimal(f2a).setScale(3, RoundingMode.HALF_UP);
curveOutdoor.put("form2a", bd.doubleValue());

// Disable superdimming for outdoors
JSONObject dimOutdoor = profOutdoor.getJSONObject("superdimming");
dimOutdoor.put("enabled", false);

writeProfile("Outdoors", profOutdoor);


/* --------------------------------------------------------- */
/* 5. NIGHT READING */
JSONObject profNight = getBaseProfile();

// Goal: Ultra-dim, flicker-free, and stable for reading in the dark.
JSONObject dimNight = profNight.getJSONObject("superdimming");
dimNight.put("enabled", false);
dimNight.put("pwm_sensitive", true); // Key feature for OLED reading.
dimNight.put("threshold", 15.0); // Activate dimming early.

JSONObject miscNight = profNight.getJSONObject("misc");
miscNight.put("min_bright", 1.0); // Allow screen to be extremely dim.
miscNight.put("min_wait", 60); // Make animations very slow and smooth.
miscNight.put("max_wait", 120);
miscNight.put("delta_factor", 0.8);
miscNight.put("throttle", 6010); // Recalculated throttle: 50 * 120 + 10

JSONObject reactNight = profNight.getJSONObject("reactivity");
reactNight.put("thresh_dark", 0.6);

// Disable Circadian scaling to prioritize the dimming features.
JSONObject scaleNight = profNight.getJSONObject("circadian");
scaleNight.put("enabled", false);

writeProfile("Night Reading", profNight);
