/* === EXTRACTED JAVA BLOCK (S1 ground-truth, verbatim, html.unescape-decoded) ===
 * Task: task43 "_EvaluateContexts V2"
 * Block: #1 of 1 in this task
 * XML: Advanced_Auto_Brightness_V3.3.prj_9.xml  arg0 (Java source) L12093-L12564; <code>474</code> at L12091
 * Output: none (uses tasker.setVariable() directly)
 * %vars consumed (S4 parameters): %02d
 * ============================================================================ */
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Paths;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.location.Location;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiInfo;
import android.content.Context;

try {
    /* --- PASS 1: GATHER INPUTS & DYNAMIC COOLDOWN --- */

    /* 1. Get Caller Early (Needed for Cooldown Decision) */
    rawCaller = tasker.getVariable("caller1");
    if (rawCaller == null) { rawCaller = ""; }

    /* 2. Determine Dynamic Cooldown Threshold */
    cooldownMs = 500L; /* Default (App Changed / General) */

    if (rawCaller.equals("_ContextResume")) {
        cooldownMs = 0L; /* Immediate execution on resume */
    } else if (rawCaller.contains("Battery")) {
        cooldownMs = 30000L;
    } else if (rawCaller.contains("Location")) {
        cooldownMs = 8000L;
    } else if (rawCaller.contains("Wifi") || rawCaller.contains("WIFI")) {
        cooldownMs = 8000L;
    } else if (rawCaller.contains("Time")) {
        cooldownMs = 1000L;
    }

    /* 3. Global Cooldown Check */
    lastRunStr = tasker.getVariable("AAB_LastEvalTime");
    lastRun = 0L;
    if (lastRunStr != null && lastRunStr.length() > 0) {
        try { lastRun = Long.parseLong(lastRunStr); } catch(Exception e) {}
    }
    nowMs = System.currentTimeMillis();

    /* Only block if cooldown > 0 */
    if (cooldownMs > 0 && (nowMs - lastRun) < cooldownMs) { 
        return; 
    }

    /* 4. Update Last Run Time */
    tasker.setVariable("AAB_LastEvalTime", String.valueOf(nowMs));

    /* --- NEW: SOLAR VARIABLES --- */
    varSunR = tasker.getVariable("AAB_Sunrise");
    varSunS = tasker.getVariable("AAB_Sunset");

    /* Default to 06:00 (21600s) and 18:00 (64800s) if missing/invalid to prevent crash */
    valSunrise = 21600L;
    if (varSunR != null && varSunR.matches("\\d+")) {
        valSunrise = Long.parseLong(varSunR);
    }

    valSunset = 64800L;
    if (varSunS != null && varSunS.matches("\\d+")) {
        valSunset = Long.parseLong(varSunS);
    }

    /* Shift Solar UTC seconds to Local seconds for evaluation */
    offsetSecs = Calendar.getInstance().getTimeZone().getOffset(System.currentTimeMillis()) / 1000L;
    valSunrise = (valSunrise + offsetSecs + 86400L) % 86400L;
    valSunset  = (valSunset + offsetSecs + 86400L) % 86400L;

    /* Clean Caller Name logic */
    if (rawCaller.length() == 0) {
        cleanCallerName = "Unknown";
    } else {
        primaryTrigger = rawCaller.split(",")[0];
        lastColon = primaryTrigger.lastIndexOf(":");
        lastEquals = primaryTrigger.lastIndexOf("=");
        separatorIndex = Math.max(lastColon, lastEquals);
        
        if (separatorIndex > -1) {
            cleanCallerName = primaryTrigger.substring(separatorIndex + 1);
        } else {
            cleanCallerName = primaryTrigger;
        }
    }
    
    dbgRaw = tasker.getVariable("AAB_Debug");
    isDebug = (dbgRaw != null && dbgRaw.equals("8"));

    overrideVar = tasker.getVariable("AAB_ContextOverride");
    isOverrideActive = (overrideVar != null && overrideVar.equals("true"));

    /* State De-serialization */
    stateRaw = tasker.getVariable("AAB_ContextState");
    lastApp = ""; lastLat = 0.0; lastLon = 0.0; lastBatt = -1; lastPlug = -1; lastDay = -1; lastWifi = "";
    
    if (stateRaw != null && stateRaw.contains("#")) {
        sParts = stateRaw.split("#");
        if (sParts.length >= 1) lastApp = sParts[0];
        if (sParts.length >= 3) { try { lastLat = Double.parseDouble(sParts[1]); lastLon = Double.parseDouble(sParts[2]); } catch(Exception e){} }
        if (sParts.length >= 4) { try { lastBatt = Integer.parseInt(sParts[3]); } catch(Exception e){} }
        if (sParts.length >= 5) { try { lastPlug = Integer.parseInt(sParts[4]); } catch(Exception e){} }
        if (sParts.length >= 6) { try { lastDay = Integer.parseInt(sParts[5]); } catch(Exception e){} }
        if (sParts.length >= 7) { lastWifi = sParts[6]; }
    }

    /* Current Inputs & Fallback Logic */
    curApp = tasker.getVariable("app_package");
    if (curApp == null || curApp.length() == 0) {
        if (rawCaller.contains("App Changed")) {
            if (isDebug) tasker.setVariable("eval_log", "Veto: App Changed but package missing");
            return;
        } else if (lastApp.length() > 0) {
            curApp = lastApp;
        } else {
            curApp = "";
        }
    }
    
    locN = tasker.getVariable("net_location");
    curLat = 0.0; curLon = 0.0;
    if (locN != null && locN.contains(",")) {
        parts = locN.split(",");
        try { 
            curLat = Double.parseDouble(parts[0].trim()); 
            curLon = Double.parseDouble(parts[1].trim()); 
        } catch(Exception e) {}
    }
    
    battRaw = tasker.getVariable("BATT");
    curBatt = (battRaw != null) ? Integer.parseInt(battRaw) : 0;

    ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
    batteryStatus = context.registerReceiver(null, ifilter);
    status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
    isPlugged = (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL);
    curPlug = isPlugged ? 1 : 0;

    cal = Calendar.getInstance();
    nowSecs = (cal.get(Calendar.HOUR_OF_DAY) * 3600) + (cal.get(Calendar.MINUTE) * 60) + cal.get(Calendar.SECOND);
    curDay = cal.get(Calendar.DAY_OF_WEEK); 

    /* --- Fetch current Wi-Fi SSID (Location Bypass Integration) --- */
    
    /* 1. First, check if our bypass task fetched the SSID */
    bypassSsid = tasker.getVariable("bypass_ssid");
    curWifi = "";
    
    if (bypassSsid != null && bypassSsid.length() > 0) {
        curWifi = bypassSsid;
    } else {
        /* 2. Fallback to standard API if bypass didn't run or failed */
        wifiMgr = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        if (wifiMgr != null) {
            try {
                wifiInfo = wifiMgr.getConnectionInfo();
                if (wifiInfo != null && wifiInfo.getNetworkId() != -1) {
                    rawSsid = wifiInfo.getSSID();
                    if (rawSsid != null && !rawSsid.equals("<unknown ssid>")) {
                        /* Strip quotes wrapped by Android OS */
                        if (rawSsid.startsWith("\"") && rawSsid.endsWith("\"")) {
                            curWifi = rawSsid.substring(1, rawSsid.length() - 1);
                        } else {
                            curWifi = rawSsid;
                        }
                    }
                }
            } catch (Exception e) {}
        }
    }

    /* --- PASS 2: VETO GATES --- */
    
    cache = tasker.getVariable("AAB_ContextCache");
    if (cache == null) cache = "";
    
    activeRule = tasker.getVariable("AAB_ActiveContext");
    isRuleActive = (activeRule != null && activeRule.length() > 0);

    updateState = false;
    proceedToEval = false;

    /* Midnight Rollover: Always Eval */
    if (curDay != lastDay) {
        updateState = true; proceedToEval = true;
    }
    
    /* Context Resume: Always Eval (Ignore Veto) */
    if (rawCaller.equals("_ContextResume")) {
        updateState = true; proceedToEval = true;
    }

    if (!proceedToEval) {
        if (rawCaller.contains("App Changed")) {
            curProf = tasker.getVariable("AAB_CurrentActiveProfile");
            defProf = tasker.getVariable("AAB_ProfileUser");
            if (defProf == null || defProf.length() == 0) defProf = "Default";
            isNonDefault = (curProf != null && !curProf.equals(defProf));

            if (isRuleActive || isNonDefault || cache.contains("," + curApp + ",")) {
                if (!curApp.equals(lastApp)) {
                    updateState = true; proceedToEval = true;
                } else if (isDebug) tasker.setVariable("eval_log", "Veto: App duplicate");
            } else if (isDebug) tasker.setVariable("eval_log", "Veto: App not in cache");
        }
        else if (rawCaller.contains("Location")) {
            if (cache.contains("[LOC]")) {
                updateState = true;
                proceedToEval = true;
            }
        }
        else if (rawCaller.contains("Battery")) {
            if (cache.contains("[BATT]")) {
                if (curPlug != lastPlug || Math.abs(curBatt - lastBatt) >= 5) {
                    updateState = true; proceedToEval = true;
                } else if (isDebug) tasker.setVariable("eval_log", "Veto: Batt < 5%");
            }
        }
        else if (rawCaller.contains("Wifi") || rawCaller.contains("WIFI")) {
            if (cache.contains("[WIFI]")) {
                if (!curWifi.equals(lastWifi)) {
                    updateState = true; proceedToEval = true;
                } else if (isDebug) tasker.setVariable("eval_log", "Veto: Wi-Fi duplicate");
            }
        }
        else {
            proceedToEval = true; updateState = true; 
        }
    }

    if (!proceedToEval) return;

    if (updateState) {
        /* Append curWifi to state to prevent duplicate runs */
        newState = curApp + "#" + curLat + "#" + curLon + "#" + curBatt + "#" + curPlug + "#" + curDay + "#" + curWifi;
        tasker.setVariable("AAB_ContextState", newState);
    }

     /* --- PASS 3: EVALUATE RULES (RAM CACHED + CRASH PROOF LOAD) --- */
    
    contexts = new JSONArray();
    cachedJsonStr = tasker.getVariable("AAB_ContextJSONCache");
    loadedFromDisk = false;

    /* 1. Try RAM Cache First */
    if (cachedJsonStr != null && cachedJsonStr.length() > 2) {
        try {
            contexts = new JSONArray(cachedJsonStr);
        } catch (Exception e) {
            cachedJsonStr = null; /* Corrupt cache or daily reload triggered, force disk fallback */
        }
    }

    /* 2. Fallback to Disk if RAM Cache is empty or corrupt */
    if (cachedJsonStr == null || cachedJsonStr.length() <= 2) {
        path = "/storage/emulated/0/Download/AAB/configs/contexts.json";
        file = new File(path);
        
        if (file.exists()) {
            try {
                content = new String(Files.readAllBytes(Paths.get(path)));
                contexts = new JSONArray(content);
                
                /* Repopulate the RAM cache for next time */
                tasker.setVariable("AAB_ContextJSONCache", content);
                loadedFromDisk = true;
                
            } catch (Exception jsonEx) {
                if (isDebug) tasker.setVariable("eval_log", "JSON Load Error: " + jsonEx.getMessage());
            }
        }
    }
    
    if (isDebug && loadedFromDisk) {
        String currentLog = tasker.getVariable("eval_log");
        if (currentLog == null) currentLog = "";
        tasker.setVariable("eval_log", currentLog + "\n[Loaded from Disk]");
    }

    winningContext = null;
    highestPriority = -1;
    highestSpecificity = -1;
    wakeTimes = new ArrayList();
    winningRuleName = "None"; 

    for (i = 0; i < contexts.length(); i++) {
        ctx = contexts.getJSONObject(i);
        triggers = ctx.getJSONObject("triggers");
        isMatch = true;
        specificity = 0;

        activeDays = new ArrayList(); 
        hasDays = false;
        if (triggers.has("days") && !triggers.isNull("days")) {
            hasDays = true;
            daysArr = triggers.getJSONArray("days");
            for (d = 0; d < daysArr.length(); d++) activeDays.add(daysArr.getInt(d));
        }

        hasTime = (triggers.has("time_range") && !triggers.isNull("time_range"));
        timeDayMatch = true;

        if (hasTime) {
            range = triggers.getJSONArray("time_range");
            
            start = 0L;
            sRaw = range.getString(0);
            
            if (sRaw.equals("SUNRISE")) {
                start = valSunrise;
            } else if (sRaw.equals("SUNSET")) {
                start = valSunset;
            } else {
                sT = sRaw.split(":"); 
                start = (Long.parseLong(sT[0]) * 3600) + (Long.parseLong(sT[1]) * 60);
            }

            end = 0L;
            eRaw = range.getString(1);
            
            if (eRaw.equals("SUNRISE")) {
                end = valSunrise;
            } else if (eRaw.equals("SUNSET")) {
                end = valSunset;
            } else {
                eT = eRaw.split(":");
                end = (Long.parseLong(eT[0]) * 3600) + (Long.parseLong(eT[1]) * 60);
            }
            
            wakeTimes.add(new Long(start)); 
            wakeTimes.add(new Long(end));

            activeToday = (activeDays.isEmpty() || activeDays.contains(curDay));

            if (start <= end) {
                if (!activeToday || nowSecs < start || nowSecs > end) timeDayMatch = false;
            } else {
                prevDay = (curDay == 1) ? 7 : curDay - 1;
                activeYesterday = (activeDays.isEmpty() || activeDays.contains(prevDay));
                matchToday = activeToday && (nowSecs >= start);
                matchYest = activeYesterday && (nowSecs <= end);
                if (!matchToday && !matchYest) timeDayMatch = false;
            }
            
            specificity++;
            if (hasDays) specificity++;
        } else if (hasDays) {
            if (!activeDays.contains(curDay)) timeDayMatch = false;
            specificity++;
        }

        if (!timeDayMatch) isMatch = false;

        if (isMatch && triggers.has("apps") && !triggers.isNull("apps")) {
            apps = triggers.getJSONArray("apps");
            foundApp = false;
            for (j = 0; j < apps.length(); j++) {
                if (apps.getString(j).equals(curApp)) { foundApp = true; break; }
            }
            if (!foundApp) isMatch = false;
            specificity++;
        }

        if (isMatch && triggers.has("battery") && !triggers.isNull("battery")) {
            batt = triggers.getJSONObject("battery");
            if (batt.has("min") && curBatt < batt.getInt("min")) isMatch = false;
            if (isMatch && batt.has("max") && curBatt > batt.getInt("max")) isMatch = false;
            if (isMatch && batt.has("on_power") && batt.getBoolean("on_power") != isPlugged) isMatch = false;
            specificity++;
        }

        if (isMatch && triggers.has("location") && !triggers.isNull("location")) {
            loc = triggers.getJSONObject("location");
            lat = loc.getDouble("lat"); lon = loc.getDouble("lon"); radius = loc.getDouble("radius");
            ruleDist = new float[1];
            Location.distanceBetween(lat, lon, curLat, curLon, ruleDist);
            if (ruleDist[0] > radius) isMatch = false;
            specificity++;
        }
        
        if (isMatch && triggers.has("wifi") && !triggers.isNull("wifi")) {
            wifiArr = triggers.getJSONArray("wifi");
            foundWifi = false;
            for (w = 0; w < wifiArr.length(); w++) {
                if (curWifi.equals(wifiArr.getString(w).trim())) { foundWifi = true; break; }
            }
            if (!foundWifi) isMatch = false;
            specificity++;
        }

        if (isMatch) {
            currentPriority = ctx.optInt("priority", 0);
            newWinner = false;
            if (currentPriority > highestPriority) {
                newWinner = true;
            } else if (currentPriority == highestPriority) {
                if (specificity > highestSpecificity) {
                    newWinner = true;
                }
            }
            if (newWinner) {
                highestPriority = currentPriority;
                highestSpecificity = specificity;
                winningContext = ctx;
                winningRuleName = ctx.optString("name", "Unnamed Rule");
            }
        }
    }

    /* --- PASS 4: OUTPUT & OVERRIDE LOGIC --- */
    
    if (!isOverrideActive) {
        userProfile = tasker.getVariable("AAB_ProfileUser");
        targetProfile = (userProfile != null && userProfile.length() > 0) ? userProfile : "Default";

        if (winningContext != null) {
            targetProfile = winningContext.getString("profile");
            tasker.setVariable("AAB_ActiveContext", winningRuleName);
        } else {
            tasker.setVariable("AAB_ActiveContext", null);
            
            checkFile = new File("/storage/emulated/0/Download/AAB/configs/" + targetProfile + ".json");
            if (!checkFile.exists()) {
                targetProfile = "Default";
                tasker.setVariable("AAB_ProfileUser", "Default");
            }
        }
        
        tasker.setVariable("target_context_profile", targetProfile);
        
        prevLog = tasker.getVariable("eval_log");
        if (prevLog == null || !prevLog.startsWith("JSON Error")) {
            log = new StringBuilder();
            log.append("Context:" + cleanCallerName + "\n");
            if (winningContext != null) {
                priority = winningContext.optInt("priority", 0);
                log.append("Rule: " + winningRuleName + "[P" + priority + "]\n");
            } else {
                log.append("Rule: No Match (Fallback)\n");
            }
            log.append("Profile: " + targetProfile);
            tasker.setVariable("eval_log", log.toString());
        }
    } else {
        if(isDebug) tasker.setVariable("eval_log", "Override Active. Wake times updated, Profile switch skipped.");
    }

    minDiff = 9223372036854775807L;
    nextWake = -1;
    for (k = 0; k < wakeTimes.size(); k++) {
        timeValue = ((Long)wakeTimes.get(k)).longValue();
        diff = timeValue - nowSecs;
        if (diff <= 0) diff += 86400;
        if (diff < minDiff) { minDiff = diff; nextWake = timeValue; }
    }
    
    if (nextWake != -1) {
        h = nextWake / 3600; 
        m = (nextWake % 3600) / 60;
        
        tasker.setVariable("AAB_NextContextTime", String.format("%02d.%02d", new Object[]{new Long(h), new Long(m)}));
    } else {
        tasker.setVariable("AAB_NextContextTime", null);
    }

} catch (Exception e) {
    tasker.setVariable("err_msg", "Context Eval Error: " + e.getMessage());
}
