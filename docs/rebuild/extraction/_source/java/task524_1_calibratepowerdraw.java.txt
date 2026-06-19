/* === EXTRACTED JAVA BLOCK (S1 ground-truth, verbatim, html.unescape-decoded) ===
 * Task: task524 "_CalibratePowerDraw"
 * Block: #1 of 1 in this task
 * XML: Advanced_Auto_Brightness_V3.3.prj_9.xml  arg0 (Java source) L14247-L14702; <code>474</code> at L14246
 * Output: none (uses tasker.setVariable() directly)
 * %vars consumed (S4 parameters): (none)
 * ============================================================================ */
import android.app.Activity;
import android.app.Dialog;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.graphics.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

/* --- CONFIGURATION --- */
TARGET_POINTS = 16;
DISTRIBUTION_EXPONENT = 0.45;
MIN_STEP_DIFF = 5;
NUDGE_THRESHOLD_MS = 3500;
MAX_WAIT_MS = 12000;
POST_LATCH_DELAY_MS = 2000;
POLL_INTERVAL_MS = 200;
INITIAL_SETTLE_MS = 6000;

/* Synchronization */
waiter = new CountDownLatch(1);
isRunning = new boolean[]{true};

/* --- HELPER FUNCTIONS --- */
getRealVoltage(context) {
    try {
        ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        batteryStatus = context.registerReceiver(null, ifilter);
        if (batteryStatus != null) {
            mv = batteryStatus.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
            if (mv > 0) return (double)mv / 1000.0;
        }
    } catch (e) { }
    return 0.0;
}

getMedian(samples) {
    if (samples.isEmpty()) return 0.0;
    sorted = new ArrayList(samples);
    Collections.sort(sorted);
    size = sorted.size();
    if (size % 2 == 0) {
        v1 = sorted.get(size / 2 - 1);
        v2 = sorted.get(size / 2);
        return (v1 + v2) / 2.0;
    }
    return sorted.get(size / 2);
}

/* --- MAIN LOGIC --- */
startCalibration(activity) {
    ctx = activity.getApplicationContext();
    bm = activity.getSystemService(Context.BATTERY_SERVICE);
    
    // Safety Checks
    testRead = bm.getLongProperty(2); // BATTERY_PROPERTY_CURRENT_NOW
    if (testRead == 0 || testRead == Long.MIN_VALUE) {
        tasker.setVariable("should_stop", "true");
        tasker.log("Hardware sensor not responding.");
        waiter.countDown();
        return;
    }
    status = bm.getIntProperty(6); // BATTERY_PROPERTY_STATUS
    if (status == 2 || status == 5) { // Charging or Full
        tasker.setVariable("should_stop", "true");
        tasker.log("Please unplug charger.");
        waiter.countDown();
        return;
    }

    /* UI Construction */
    root = new FrameLayout(activity);
    root.setBackgroundColor(Color.WHITE);
    progressLayout = new LinearLayout(activity);
    progressLayout.setOrientation(1);
    progressLayout.setGravity(17);
    
    statusText = new TextView(activity);
    statusText.setText("Initializing...");
    statusText.setTextColor(Color.BLACK);
    statusText.setTextSize(18);
    statusText.setGravity(17);
    progressLayout.addView(statusText);

    progressText = new TextView(activity);
    progressText.setText("Preparing...");
    progressText.setTextColor(Color.parseColor("#666666"));
    progressText.setTextSize(14);
    progressText.setGravity(17);
    textParams = new LinearLayout.LayoutParams(-1, -2);
    textParams.setMargins(0, 20, 0, 10);
    progressText.setLayoutParams(textParams);
    progressLayout.addView(progressText);

    progressBarContainer = new FrameLayout(activity);
    containerParams = new LinearLayout.LayoutParams(-1, 20);
    containerParams.setMargins(40, 0, 40, 0);
    progressBarContainer.setLayoutParams(containerParams);
    progressBarContainer.setBackgroundColor(Color.parseColor("#EEEEEE"));

    progressBar = new View(activity);
    progressBar.setBackgroundColor(Color.parseColor("#007C63"));
    barParams = new FrameLayout.LayoutParams(0, -1);
    progressBar.setLayoutParams(barParams);
    progressBarContainer.addView(progressBar);
    progressLayout.addView(progressBarContainer);
    root.addView(progressLayout);

    dialog = new Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
    dialog.setContentView(root);
    dialog.setCancelable(false);
    
    win = dialog.getWindow();
    win.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    lp = win.getAttributes();
    lp.screenBrightness = 0.004f;
    win.setAttributes(lp);

    dialog.setOnKeyListener(new DialogInterface.OnKeyListener() {
        onKey(di, keyCode, event) {
            if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
                isRunning[0] = false;
                tasker.setVariable("should_stop", "true");
                di.dismiss();
                return true;
            }
            return false;
        }
    });

    dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
        onDismiss(di) {
            isRunning[0] = false;
            activity.finish();
            waiter.countDown();
        }
    });
    dialog.show();

    /* Worker Thread */
    new Thread(new Runnable() {
        run() {
            try {
                xVals = new ArrayList();
                yValsRaw = new ArrayList();
                yValsWatt = new ArrayList();
                dataList = new ArrayList();
                mainHandler = new Handler(Looper.getMainLooper());

                // 1. GENERATE GEOMETRIC STEPS
                steps = new ArrayList();
                lastVal = 0;
                for (i = 1; i <= TARGET_POINTS; i++) {
                    ratio = (double) i / TARGET_POINTS;
                    curve = Math.pow(ratio, DISTRIBUTION_EXPONENT);
                    val = (int) (255.0 * curve);
                    if (val > 255) val = 255;
                    if (val - lastVal >= MIN_STEP_DIFF) {
                        steps.add(val);
                        lastVal = val;
                    } else if (val == 255 && lastVal != 255) {
                        steps.add(255);
                        lastVal = 255;
                    }
                }
                if (steps.size() > 0 && (Integer)steps.get(steps.size()-1) != 255) steps.add(255);
                totalSteps = steps.size();

                // 2. PRE-FLIGHT RAMP DOWN
                startBright = 128;
                try {
                    strBright = tasker.getVariable("BRIGHT");
                    if (strBright != null) startBright = Integer.parseInt(strBright);
                } catch (e) { }

                mainHandler.post(new Runnable() { run() { statusText.setText("Ramping down to 0..."); } });
                for (b = startBright; b >= 0; b-=2) {
                    if (!isRunning[0]) break;
                    final int curB = b;
                    mainHandler.post(new Runnable() {
                        run() {
                            lpUpdate = dialog.getWindow().getAttributes();
                            float f = (float)curB/255.0f;
                            if (f < 0.004f) f = 0.004f;
                            lpUpdate.screenBrightness = f;
                            dialog.getWindow().setAttributes(lpUpdate);
                        }
                    });
                    Thread.sleep(10);
                }

                // 3. BASELINE SANITY CAPTURE
                mainHandler.post(new Runnable() { run() { statusText.setText("Stabilizing Baseline (0/255)..."); } });
                Thread.sleep(INITIAL_SETTLE_MS);
                
                sanityChecks = 0;
                lastMa = 0.0;
                baselineAccepted = false;
                
                while (sanityChecks < 20) {
                    if (!isRunning[0]) break;
                    rawI = bm.getLongProperty(2);
                    if (rawI == 0) rawI = bm.getLongProperty(3);
                    absI = Math.abs(rawI);
                    checkMa = (absI > 50000) ? (absI / 1000.0) : (double) absI;
                    final double dispMa = checkMa;
                    mainHandler.post(new Runnable() { run() { progressText.setText("Baseline Check: " + dispMa + "mA (<150 needed)"); } });
                    
                    if (checkMa < 150.0) {
                        lastMa = checkMa;
                        baselineAccepted = true;
                        v0 = getRealVoltage(ctx);
                        xVals.add(0);
                        yValsRaw.add(lastMa);
                        yValsWatt.add((lastMa / 1000.0) * v0);
                        break;
                    }
                    sanityChecks++;
                    Thread.sleep(1000);
                }
                
                if (!baselineAccepted && isRunning[0]) {
                    v0 = getRealVoltage(ctx);
                    xVals.add(0);
                    yValsRaw.add(lastMa);
                    yValsWatt.add((lastMa / 1000.0) * v0);
                }

                // 4. MAIN LATCH-BREAKER LOOP
                for (i = 0; i < totalSteps; i++) {
                    if (!isRunning[0]) break;
                    targetB = (Integer) steps.get(i);
                    final int finalB = targetB;
                    final int stepIndex = i;
                    final double refMa = lastMa;

                    mainHandler.post(new Runnable() {
                        run() {
                            lpUpdate = dialog.getWindow().getAttributes();
                            lpUpdate.screenBrightness = (float)finalB / 255.0f;
                            dialog.getWindow().setAttributes(lpUpdate);
                            statusText.setText("Target: " + finalB + "/255 (Waiting for Change)");
                            barP = progressBar.getLayoutParams();
                            screenW = activity.getResources().getDisplayMetrics().widthPixels;
                            barP.width = (int)((screenW - 80) * ((float)(stepIndex+1)/totalSteps));
                            progressBar.setLayoutParams(barP);
                        }
                    });

                    latchBroken = false;
                    nudged = false;
                    waitStart = System.currentTimeMillis();
                    currMa = 0.0;
                    
                    while (System.currentTimeMillis() - waitStart < MAX_WAIT_MS) {
                        if (!isRunning[0]) break;
                        tick = (System.currentTimeMillis() / 200) % 4;
                        final String spin = (tick==0?"|":(tick==1?"/":(tick==2?"-":"\\")));
                        final int elapsed = (int)(System.currentTimeMillis() - waitStart);
                        final int remMs = MAX_WAIT_MS - elapsed;
                        
                        // Nudge if stalled
                        if (!nudged && elapsed > NUDGE_THRESHOLD_MS) {
                            nudged = true;
                            mainHandler.post(new Runnable() {
                                run() {
                                    float nudgeVal = (float)(finalB + 1) / 255.0f;
                                    if (nudgeVal > 1.0f) nudgeVal = (float)(finalB - 1) / 255.0f;
                                    WindowManager.LayoutParams lp = dialog.getWindow().getAttributes();
                                    lp.screenBrightness = nudgeVal;
                                    dialog.getWindow().setAttributes(lp);
                                    statusText.setText("Nudging Sensor...");
                                }
                            });
                            Thread.sleep(200);
                            mainHandler.post(new Runnable() {
                                run() {
                                    float originalVal = (float)finalB / 255.0f;
                                    if (originalVal < 0.004f) originalVal = 0.004f;
                                    WindowManager.LayoutParams lp = dialog.getWindow().getAttributes();
                                    lp.screenBrightness = originalVal;
                                    dialog.getWindow().setAttributes(lp);
                                    statusText.setText("Target: " + finalB + " (Post-Nudge)");
                                }
                            });
                        }
                        
                        mainHandler.post(new Runnable() { run() { progressText.setText("Polling... " + spin + " (" + (remMs/1000) + "s timeout)"); } });
                        
                        rawI = bm.getLongProperty(2);
                        if (rawI == 0) rawI = bm.getLongProperty(3);
                        absI = Math.abs(rawI);
                        currMa = (absI > 50000) ? (absI / 1000.0) : (double) absI;
                        
                        if (currMa != refMa) {
                            latchBroken = true;
                            break;
                        }
                        Thread.sleep(POLL_INTERVAL_MS);
                    }

                    statusMsg = latchBroken ? "Change Detected! Settling..." : "Timeout. Accepting...";
                    mainHandler.post(new Runnable() { run() { progressText.setText(statusMsg); } });
                    Thread.sleep(POST_LATCH_DELAY_MS);
                    
                    rawI = bm.getLongProperty(2);
                    if (rawI == 0) rawI = bm.getLongProperty(3);
                    absI = Math.abs(rawI);
                    finalMa = (absI > 50000) ? (absI / 1000.0) : (double) absI;
                    
                    v = getRealVoltage(ctx);
                    xVals.add(finalB);
                    yValsRaw.add(finalMa);
                    yValsWatt.add((finalMa / 1000.0) * v);
                    lastMa = finalMa;
                }

                if (isRunning[0]) {
                    // Post-Process
                    if (yValsRaw.size() >= 2) {
                        val0 = (Double) yValsRaw.get(0);
                        val1 = (Double) yValsRaw.get(1);
                        if (val0 > val1) {
                            yValsRaw.set(0, 0.0);
                            yValsWatt.set(0, 0.0);
                        }
                    }
                    minMa = Collections.min(yValsRaw);
                    finalMa = new ArrayList();
                    finalW = new ArrayList();
                    dataList = new ArrayList();
                    
                    for (i = 0; i < xVals.size(); i++) {
                        rawM = yValsRaw.get(i);
                        rawW = yValsWatt.get(i);
                        currentV = getRealVoltage(ctx);
                        minW = (minMa / 1000.0) * currentV;
                        if (minW > rawW) minW = rawW;
                        
                        netMa = Math.max(0.0, rawM - minMa);
                        netW = Math.max(0.0, rawW - minW);
                        
                        finalMa.add(netMa);
                        finalW.add(netW);
                        map = new java.util.HashMap();
                        map.put("brightness", xVals.get(i));
                        map.put("current_ma", netMa);
                        map.put("power_w", netW);
                        dataList.add(map);
                    }

                    jsonX = tasker.toJson(xVals);
                    jsonY = tasker.toJson(finalMa);
                    maxW = Collections.max(finalW);
                    useMw = maxW < 0.5;
                    unit = useMw ? "mW" : "W";
                    scale = useMw ? 1000.0 : 1.0;
                    scaledW = new ArrayList();
                    for (i = 0; i < finalW.size(); i++) scaledW.add(finalW.get(i) * scale);
                    jsonW = tasker.toJson(scaledW);

                    // Build Chart HTML
                    p1 = new StringBuilder();
                    p1.append("<!DOCTYPE html><html lang='en'><head><meta charset='UTF-8' /><meta name='viewport' content='width=device-width, initial-scale=1.0, viewport-fit=cover' /><title>Power Calibration</title><style>html, body { margin: 0; padding: 0; height: 100%; background-color: #121212; color: #ffffff; font-family: -apple-system, BlinkMacSystemFont, sans-serif; } .chart-container { position: relative; width: 100%; height: 100%; padding: 1rem; box-sizing: border-box; background: linear-gradient(135deg, #1e1e1e, #292929); box-shadow: 0 4px 12px rgba(0, 0, 0, 0.5); } canvas { display: block; width: 100% !important; height: 100% !important; } @media (orientation: portrait) { .chart-container { padding: 0.5rem; } }</style></head><body><div class='chart-container'><canvas id='powerChart'></canvas></div><script>");
                    
                    p2 = new StringBuilder();
                    p2.append("</script><script>");
                    p2.append("const rawX = " + jsonX + ";");
                    p2.append("const rawW = " + jsonW + ";");
                    p2.append("const rawY = " + jsonY + ";");
                    p2.append("const dataW = rawX.map((x, i) => ({x: x, y: rawW[i]}));");
                    p2.append("const dataY = rawX.map((x, i) => ({x: x, y: rawY[i]}));");
                    p2.append("const ctx = document.getElementById('powerChart').getContext('2d');");
                    p2.append("new Chart(ctx, { type: 'line', data: { datasets: [ { label: 'Screen Power (" + unit + ")', data: dataW, borderColor: '#007C63', backgroundColor: 'rgba(0, 124, 99, 0.1)', borderWidth: 3, pointRadius: 4, pointHoverRadius: 6, tension: 0.3, fill: true, yAxisID: 'y' }, { label: 'Current (mA)', data: dataY, borderColor: '#FFC107', backgroundColor: 'rgba(255, 193, 7, 0)', borderDash: [6, 4], borderWidth: 2, pointRadius: 2, tension: 0.3, fill: false, yAxisID: 'y1' } ] }, options: { responsive: true, maintainAspectRatio: false, interaction: { mode: 'index', intersect: false }, plugins: { legend: { labels: { color: '#ffffff', font: { size: 14 }, usePointStyle: true, boxWidth: 8 }, filter: function(item, chart) { return item.text !== 'Upper' && item.text !== 'Stability (SD)'; } }, tooltip: { backgroundColor: 'rgba(18,18,18,0.95)', titleColor: '#ffffff', bodyColor: '#ffffff', cornerRadius: 6, titleFont: { size: 14, weight: '600' } } }, scales: { y: { type: 'linear', display: true, position: 'left', title: { display: true, text: 'Power (" + unit + ")', color:'#007C63', font: {weight: '600'} }, grid: { color: 'rgba(255,255,255,0.1)' }, ticks: { color: '#cccccc' } }, y1: { type: 'linear', display: true, position: 'right', title: { display: true, text: 'Current (mA)', color:'#FFC107', font: {weight: '600'} }, grid: { drawOnChartArea: false }, ticks: { color: '#cccccc' } }, x: { type: 'linear', min: 0, max: 255, title: { display: true, text: 'Brightness Level (0-255)', color:'#cccccc', font: {size: 14} }, grid: { color: 'rgba(255,255,255,0.1)' }, ticks: { color: '#cccccc' } } } } });");
                    p2.append("</script></body></html>");
                    
                    tasker.setVariable("part_one", p1.toString());
                    tasker.setVariable("part_two", p2.toString());
                    tasker.setVariable("data", tasker.toJson(dataList));
                }
                mainHandler.post(new Runnable() { run() { if (dialog.isShowing()) dialog.dismiss(); } });
            } catch (e) {
                tasker.log("Worker Exception: " + e.toString());
                isRunning[0] = false;
                mainHandler.post(new Runnable() { run() { if (dialog.isShowing()) dialog.dismiss(); } });
            }
        }
    }).start();
}

/* --- ENTRY POINT --- */
myConsumer = new Consumer() {
    accept(activityObj) {
        activity = (Activity) activityObj;
        existing = tasker.getVariable("AAB_HTML_Graph8");
        if (existing != null && existing.length() > 50) {
            builder = new AlertDialog.Builder(activity);
            builder.setTitle("Data Found");
            builder.setMessage("Previous calibration data exists. What would you like to do?");
            builder.setNegativeButton("View Graph", new DialogInterface.OnClickListener() {
                onClick(dialog, which) {
                    tasker.setVariable("skip", "true");
                    activity.finish();
                    waiter.countDown();
                }
            });
            builder.setPositiveButton("Recalibrate", new DialogInterface.OnClickListener() {
                onClick(dialog, which) {
                    showInstructions(activity);
                }
            });
            builder.setCancelable(false);
            builder.show();
        } else {
            showInstructions(activity);
        }
    }
};

showInstructions(activity) {
    builder = new AlertDialog.Builder(activity);
    builder.setTitle("Calibration Prep");
    builder.setMessage("For accurate results:\n\n1. Enable Airplane Mode (Critical)\n2. Close all background apps\n3. Do not touch screen during test\n4. Unplug charger");
    builder.setPositiveButton("Start", new DialogInterface.OnClickListener() {
        onClick(dialog, which) {
            startCalibration(activity);
        }
    });
    builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
        onClick(dialog, which) {
            tasker.setVariable("should_stop", "true");
            activity.finish();
            waiter.countDown();
        }
    });
    builder.setCancelable(false);
    builder.show();
}

tasker.doWithActivity(myConsumer);
waiter.await();
