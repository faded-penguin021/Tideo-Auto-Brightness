/* === EXTRACTED JAVA BLOCK (S1 ground-truth, verbatim, html.unescape-decoded) ===
 * Task: task696 "Smooth Brightness Transition V5 (Java)"
 * Block: #1 of 1 in this task
 * XML: Advanced_Auto_Brightness_V3.3.prj_9.xml  arg0 (Java source) L35734-L35886; <code>474</code> at L35733
 * Output: none (uses tasker.setVariable() directly)
 * %vars consumed (S4 parameters): %par1, %par2
 * ============================================================================ */
import android.provider.Settings;
import android.content.ContentResolver;

/* --- Safe Initialization Phase --- */

/* 1. Get Content Resolver */
cResolver = context.getContentResolver();

/* 2. Get Current Brightness (Safely) */
try {
    startBrightness = Settings.System.getInt(cResolver, Settings.System.SCREEN_BRIGHTNESS);
} catch (Exception e) {
    tasker.log("AAB: Could not read system brightness. Aborting.");
    return; 
}

/* 3. Get Target Brightness (%par1) */
targetStr = tasker.getVariable("par1");
if (targetStr == null) {
    tasker.log("AAB: No target brightness (%par1) set. Aborting.");
    return;
}
targetBrightness = Double.parseDouble(targetStr);

/* 4. Get Configuration (%par2 -> loops,wait) */
par2 = tasker.getVariable("par2");
if (par2 == null) par2 = "20,30"; /* Default safety fallback */
parts = par2.split(",");
loops = Integer.parseInt(parts[0]);
wait = Long.parseLong(parts[1]);

/* 5. Get Global Settings */
dimmingThresholdStr = tasker.getVariable("AAB_DimmingThreshold");
dimmingThreshold = (dimmingThresholdStr != null) ? Double.parseDouble(dimmingThresholdStr) : 5.0;

dimmingEnabledStr = tasker.getVariable("AAB_DimmingEnabled");
dimmingEnabled = (dimmingEnabledStr != null && dimmingEnabledStr.equals("true"));

detectOverridesStr = tasker.getVariable("AAB_DetectOverrides");
detectOverrides = (detectOverridesStr != null && detectOverridesStr.equals("On"));

/* 6. Calculate Min/Max Targets */
if (startBrightness < targetBrightness) {
    minTarget = startBrightness;
    maxTarget = targetBrightness + 1;
} else {
    minTarget = targetBrightness - 1;
    maxTarget = startBrightness;
}

/* Export these for the Debug Flash (A13) */
tasker.setVariable("max_target", String.valueOf(maxTarget));
tasker.setVariable("min_target", String.valueOf(minTarget));
tasker.setVariable("loops", String.valueOf(loops));
tasker.setVariable("wait", String.valueOf(wait));

/* --- Execution Phase --- */

engineStartTime = System.currentTimeMillis();
consecutiveOutOfBounds = 0;
overrideTriggerThreshold = 2; 
previousBrightnessInt = -1;

tasker.log("--- Starting AAB Animation: " + startBrightness + " -> " + targetBrightness + " ---");

for (int counter = 0; counter < loops; counter++) {

    /* Check for manual stop flag - Doing this every frame is necessary for responsiveness, 
       but we avoid the IPC overhead of writing variables unless necessary below. */
    override = tasker.getVariable("AAB_Manual_Override");
    if ("true".equals(override)) {
        tasker.log("Manual override detected via variable. Stopping.");
        break;
    }

    /* Calculate interpolation */
    progress = (double)counter / loops;
    calculated_brightness_smooth = startBrightness + progress * (targetBrightness - startBrightness);
    
    /* Apply Brightness to System (Every Frame for Smoothness) */
    brightnessInt = (int) Math.round(calculated_brightness_smooth);
    if (brightnessInt < 0) brightnessInt = 0;
    if (brightnessInt > 255) brightnessInt = 255;
    
    /* Write to system settings ONLY if value changed */
    if (brightnessInt != previousBrightnessInt) {
        Settings.System.putInt(cResolver, Settings.System.SCREEN_BRIGHTNESS, brightnessInt);
        previousBrightnessInt = brightnessInt;
    }
    
    /* 
     * OPTIMIZATION: Only update Tasker variables and check overrides every 5th frame.
     */
    if (counter % 5 == 0 || counter == loops - 1) {
        
        /* 1. Format Display Variable (math-based for efficiency instead of BigDecimal) */
        AAB_CurrentBright_str = "";
        if (calculated_brightness_smooth < dimmingThreshold) {
            /* Round to 1 decimal place: (int)(val * 10) / 10.0 */
            roundedVal = Math.round(calculated_brightness_smooth * 10.0) / 10.0;
            AAB_CurrentBright_str = String.valueOf(roundedVal);
        } else {
            AAB_CurrentBright_str = String.valueOf(Math.round(calculated_brightness_smooth));
        }
        
        /* 2. Update Global Variable */
        tasker.setVariable("AAB_CurrentBright", AAB_CurrentBright_str);

        /* 3. Dimming Decider Hook */
        if (dimmingEnabled) {
            tasker.callTask("Dimming Decider", null);
        }
        
        /* 4. Override Detection */
        if (detectOverrides) {
            try {
                actualSystemBrightness = Settings.System.getInt(cResolver, Settings.System.SCREEN_BRIGHTNESS);
                
                if (actualSystemBrightness > maxTarget + 2 || actualSystemBrightness < minTarget - 2) {
                    consecutiveOutOfBounds++;
                    
                    if (consecutiveOutOfBounds >= overrideTriggerThreshold) {
                        tasker.log("Hardware override detected (System: " + actualSystemBrightness + " vs Calc: " + brightnessInt + "). Stopping.");
                        tasker.setVariable("AAB_Manual_Override", "true");
                        tasker.callTask("Manual Override", null);
                        break;
                    }
                } else {
                    consecutiveOutOfBounds = 0;
                }
            } catch (Exception e) {
                /* Ignore read errors during loop */
            }
        }
    }

    /* Self-Adjusting Wait */
    timeSpentSoFar = System.currentTimeMillis() - engineStartTime;
    targetTimeForStep = (long)((counter + 1) * wait);
    adjustedWait = targetTimeForStep - timeSpentSoFar;
    
    try {
        if (adjustedWait > 0 && counter < loops - 1) {
            Thread.sleep(adjustedWait);
        }
    } catch (InterruptedException e) {
        break;
    }
}

engineEndTime = System.currentTimeMillis();
tasker.setVariable("java_loop_duration", String.valueOf(engineEndTime - engineStartTime));
tasker.setVariable("AutoBrightRunning", "0");
