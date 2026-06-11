/* === EXTRACTED JAVA BLOCK (S1 ground-truth, verbatim, html.unescape-decoded) ===
 * Task: task698 "Smooth DC-Like Brightness Transition V5 (Java)"
 * Block: #1 of 1 in this task
 * XML: Advanced_Auto_Brightness_V3.3.prj_9.xml  arg0 (Java source) L36044-L36298; <code>474</code> at L36043
 * Output: none (uses tasker.setVariable() directly)
 * %vars consumed (S4 parameters): (none)
 * ============================================================================ */
import java.util.HashMap;
import android.provider.Settings;
import android.content.ContentResolver;

/* --- Start Timer --- */
engineStartTime = System.currentTimeMillis();

/* --- Read Parameters --- */
targetBrightness = Double.parseDouble(tasker.getVariable("calculated_brightness"));
finalDim = Double.parseDouble(tasker.getVariable("final_dim"));
loops = Integer.parseInt(tasker.getVariable("loops"));
wait = Integer.parseInt(tasker.getVariable("wait"));

/* Continuity Fix */
startBrightness = Double.parseDouble(tasker.getVariable("currentbrightness"));

/* Animation Config */
dimmingThreshold = Double.parseDouble(tasker.getVariable("AAB_DimmingThreshold"));
dimStart = Double.parseDouble(tasker.getVariable("dim_start"));

/* Feature Flags */
privilege = tasker.getVariable("AAB_Privilege");
detectOverrides = "On".equals(tasker.getVariable("AAB_DetectOverrides"));

/* Logic Flags */
isUnprivileged = "None".equals(privilege);
isNativeSecure = "Write Secure".equals(privilege);
requiresTaskCall = !isUnprivileged && !isNativeSecure;

/* System Access */
cResolver = context.getContentResolver();
consecutiveOutOfBounds = 0;

/* --- LOGIC UPGRADE: Time-Based Override Threshold --- */
/* Reduced threshold to 2 because we are checking less frequently (every 5 frames) */
overrideTriggerThreshold = 2;

/* --- STATE TRACKING (Optimization) --- */
/* Track previous values to avoid redundant system writes (Battery Saver & Speed) */
previousHardwareTarget = -1;
previousDimVal = -1;
previousAlpha = -1;

/* Optimization: Cache Dimming Status locally to avoid IPC read inside loop */
dimmingStatusVar = tasker.getVariable("AAB_DimmingStatus");
dimmingStatusActive = (dimmingStatusVar != null && dimmingStatusVar.equals("1"));

dimParams = new HashMap();
overlayParams = new HashMap();

/* Initialize tracking variable outside loop */
calculated_bright = startBrightness;

for (counter = 0; counter < loops; counter++) {

    /* 1. Safety Check */
    if ("true".equals(tasker.getVariable("AAB_Manual_Override"))) break;

    /* 2. Calculate Values */
    progress = (double)counter / loops;
    calculated_bright = startBrightness + progress * (targetBrightness - startBrightness);
    
    /* Dimming Curve */
    dim_shell_double = dimStart + progress * (finalDim - dimStart);
    dim_val = (int) Math.round(dim_shell_double);
    
    /* 3. Determine Hardware Target (PWM Logic) */
    hardwareTarget = 0;
    if (calculated_bright < dimmingThreshold) {
        hardwareTarget = (int) Math.round(dimmingThreshold);
    } else {
        hardwareTarget = (int) Math.round(calculated_bright);
    }

    /* OPTIMIZATION: Write Hardware Brightness ONLY if changed */
    if (hardwareTarget != previousHardwareTarget) {
        Settings.System.putInt(cResolver, Settings.System.SCREEN_BRIGHTNESS, hardwareTarget);
        previousHardwareTarget = hardwareTarget;
    }

    /* 4. Override Detection (Throttled) */
    if (detectOverrides) {
        /* OPTIMIZATION: Check only every 5 frames to reduce CPU load */
        if (counter % 5 == 0) {
            try {
                actualBright = Settings.System.getInt(cResolver, Settings.System.SCREEN_BRIGHTNESS);
                
                /* Tolerance is 10% of target, but minimum 4 units */
                dynamicTolerance = (int) Math.max(4, hardwareTarget * 0.10);
                
                if (Math.abs(actualBright - hardwareTarget) > dynamicTolerance) {
                    consecutiveOutOfBounds++;
                    if (consecutiveOutOfBounds >= overrideTriggerThreshold) {
                        tasker.setVariable("AAB_Manual_Override", "true");
                        tasker.callTask("Manual Override", new HashMap()); 
                        break;
                    }
                } else {
                    consecutiveOutOfBounds = 0;
                }
            } catch (Exception e) {}
        }
    }

    /* 5. APPLY DIMMING LOGIC */
    
    if (isUnprivileged) {
        /* Check local cached boolean instead of IPC call */
        if (!dimmingStatusActive) {
             tasker.callTask("_ShowColorFilter", new HashMap());
             tasker.setVariable("AAB_DimmingStatus", "1");
             dimmingStatusActive = true;
        }
        
        alphaVal = dim_val;
        if (alphaVal > 253) alphaVal = 253;
        if (alphaVal < 0) alphaVal = 0;

        /* OPTIMIZATION: Only calculate hex and update overlay if Alpha Value Changed */
        if (alphaVal != previousAlpha) {
            hexStr = Integer.toHexString(alphaVal);
            if (hexStr.length() < 2) hexStr = "0" + hexStr;
            colorCode = "#" + hexStr + "000000";
            
            tasker.setVariable("AAB_HexOverlay", colorCode);
            overlayParams.put("par1", colorCode);
            tasker.callTask("_OverlayColorUpdate", overlayParams);
            
            previousAlpha = alphaVal;
        }
        
        /* OPTIMIZATION: Throttle global status updates */
        if (counter % 5 == 0) {
            dimPct = dim_shell_double / 2.55;
            /* Primitive math rounding replaces BigDecimal */
            roundedDim = Math.round(dimPct * 10.0) / 10.0;
            tasker.setVariable("AAB_DimmingCurrent", String.valueOf(roundedDim));
            tasker.setVariable("AAB_DimmingDS", String.valueOf(dim_val));
        }
    } 
    else if (isNativeSecure) {
        try {
            if (dim_val > 100) dim_val = 100;
            if (dim_val < 0) dim_val = 0;

            /* OPTIMIZATION: Only write Secure Setting if value changed */
            if (dim_val != previousDimVal) {
                Settings.Secure.putInt(cResolver, "reduce_bright_colors_level", dim_val);
                previousDimVal = dim_val;
            }
            
            if (!dimmingStatusActive) {
                Settings.Secure.putInt(cResolver, "reduce_bright_colors_activated", 1);
                tasker.setVariable("AAB_DimmingStatus", "1");
                dimmingStatusActive = true;
            }
            
            /* OPTIMIZATION: Throttle global status updates */
            if (counter % 5 == 0) {
                roundedDim = Math.round(dim_shell_double * 10.0) / 10.0;
                tasker.setVariable("AAB_DimmingCurrent", String.valueOf(roundedDim));
                tasker.setVariable("AAB_DimmingDS", String.valueOf(dim_val));
            }
        } catch (Exception e) {}
    }
    else if (requiresTaskCall) {
        /* Task Call Logic with fixed Guard clauses to prevent Rejected Copy errors. */
        isLastLoop = (counter == loops - 1);
        
        /* Get fresh TRUN value */
        trun = tasker.getVariable("TRUN");
        taskName = "Apply Dimming (Privileged)";
        
        /* FRAME DROPPING LOGIC */
        /* If the task is running... */
        if (trun != null && trun.contains(taskName)) {
            /* If it's the last loop, we MUST wait for the previous task to finish
               so we can apply the final brightness value reliably. */
            if (isLastLoop) {
                /* wait for the previous instance to finish (No timeout!) */
                while (trun.contains(taskName)) {
                    try { Thread.sleep(5); } catch (Exception e) {}
                    trun = tasker.getVariable("TRUN");
                }
            } 
            /* If it's NOT the last loop, just skip this frame to prevent 
               stacking/rejected copy errors. Maintains animation speed. */
            else {
                continue; 
            }
        }
        
        /* PREPARE AND CALL */
        dimParams.put("dim_shell", String.valueOf(dim_shell_double));
        tasker.callTask(taskName, dimParams);
        
        /* CRITICAL FIX: START-UP GUARD (Only needed for Last Loop reliability) */
        /* If this is the last loop, we want to make sure the task we just called 
           actually starts before we finish the script, ensuring the UI is consistent. */
        if (isLastLoop) {
             sTime = System.currentTimeMillis();
             /* Wait up to 200ms for the task to appear in TRUN */
             trun = tasker.getVariable("TRUN");
             while ((trun == null || !trun.contains(taskName)) && (System.currentTimeMillis() - sTime < 200)) {
                 try { Thread.sleep(5); } catch (Exception e) {}
                 trun = tasker.getVariable("TRUN");
             }
        }
    }

    /* 6. Update UI Variables */
    /* OPTIMIZATION: Throttle UI Global updates to every 5 frames */
    /* OPTIMIZATION: Use Primitive Math instead of BigDecimal object allocation */
    if (counter % 5 == 0) {
        currBrightStr = "";
        if (calculated_bright < dimmingThreshold) {
             /* Round to 1 decimal place: (int)(val * 10) / 10.0 */
             roundedVal = Math.round(calculated_bright * 10.0) / 10.0;
             currBrightStr = String.valueOf(roundedVal);
        } else {
             currBrightStr = String.valueOf(Math.round(calculated_bright));
        }
        tasker.setVariable("AAB_CurrentBright", currBrightStr);
    }

    /* 7. Wait Loop */
    timeSpent = System.currentTimeMillis() - engineStartTime;
    targetTime = (long)((counter + 1) * wait);
    adjustedWait = targetTime - timeSpent;
    
    try {
        if (adjustedWait > 0 && counter < loops - 1) {
            Thread.sleep(adjustedWait);
        }
    } catch (InterruptedException e) { break; }
}

/* Post-Loop Cleanup */
if (!"true".equals(tasker.getVariable("AAB_Manual_Override"))) {
    calculated_bright = targetBrightness;
}

/* Final update to ensure target is reached exactly in UI (Primitive Math) */
if (calculated_bright < dimmingThreshold) {
     roundedVal = Math.round(calculated_bright * 10.0) / 10.0;
     currBrightStr = String.valueOf(roundedVal);
} else {
     currBrightStr = String.valueOf(Math.round(calculated_bright));
}
tasker.setVariable("AAB_CurrentBright", currBrightStr);

endTime = System.currentTimeMillis();
tasker.setVariable("java_loop_duration", String.valueOf(endTime - engineStartTime));
tasker.setVariable("AutoBrightRunning", "0");
tasker.setVariable("calculated_brightness_smooth", String.valueOf(calculated_bright));
