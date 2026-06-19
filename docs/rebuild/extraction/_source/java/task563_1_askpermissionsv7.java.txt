/* === EXTRACTED JAVA BLOCK (S1 ground-truth, verbatim, html.unescape-decoded) ===
 * Task: task563 "_AskPermissionsV7"
 * Block: #1 of 1 in this task
 * XML: Advanced_Auto_Brightness_V3.3.prj_9.xml  arg0 (Java source) L19678-L20042; <code>474</code> at L19677
 * Output: none (uses tasker.setVariable() directly)
 * %vars consumed (S4 parameters): (none)
 * ============================================================================ */
            import android.provider.Settings;
             import android.content.Intent;
             import android.net.Uri;
             import android.app.AlertDialog;
             import android.content.DialogInterface;
             import android.app.Activity;
             import android.app.NotificationManager;
             import android.app.AppOpsManager;
             import android.app.AlarmManager;
             import android.content.Context;
             import android.content.pm.PackageManager;
             import android.Manifest;
             import android.os.Build;
             import android.os.Environment;
             import android.os.PowerManager;
             import java.util.function.Consumer;
             import io.reactivex.subjects.SingleSubject;
             
             /* 1. Setup & Context */
             sdk = Build.VERSION.SDK_INT;
             targetPkg = context.getPackageName();
             tasker.setVariable("AAB_Package", targetPkg);
             
             nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
             pm = context.getPackageManager();
             appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
             powMgr = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
             alarmMgr = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
             
             /* State Tracking */
             forceRestrictedFix = false;
             triedLocPopup = false;
             triedNotifPopup = false;
             triedLegacyStoragePopup = false;
             
             /* --- HELPER: Try Popups with SAFE Reference Holding --- */
             boolean tryRuntimePermissions(String[] perms) {
                 // 1. Check if ALL are granted
                 allGranted = true;
                 for (i=0; i<perms.length; i++) {
                     if (context.checkSelfPermission(perms[i]) != PackageManager.PERMISSION_GRANTED) {
                         allGranted = false;
                         break;
                     }
                 }
                 if (allGranted) return true;
             
                 // 2. Trigger Popup & Capture Activity
                 hostActivity = new Activity[1];
                 permsFinal = perms;
                 
                 tasker.doWithActivity(new Consumer() {
                     accept(Object activityObj) {
                         act = (Activity) activityObj;
                         hostActivity[0] = act; 
                         try {
                             act.requestPermissions(permsFinal, 101);
                         } catch (Exception e) {}
                     }
                 });
             
                 // 3. Wait (Polling)
                 attempts = 0;
                 success = false;
                 while (attempts < 20) { 
                     try { Thread.sleep(500); } catch (Exception e) {}
                     
                     nowGranted = true;
                     for (i=0; i<perms.length; i++) {
                         if (context.checkSelfPermission(perms[i]) != PackageManager.PERMISSION_GRANTED) {
                             nowGranted = false;
                             break;
                         }
                     }
                     if (nowGranted) {
                         success = true;
                         break;
                     }
                     attempts++;
                 }
                 
                 // 4. Safe Cleanup
                 if (hostActivity[0] != null) {
                     try { 
                         hostActivity[0].finish(); 
                     } catch(Exception e) {}
                 }
                 
                 return success;
             }
             
             /* MAIN LOOP */
             while (true) {
                 try { Thread.sleep(500); } catch (Exception s) {}
             
                 /* 2. Check Permissions */
             
                 /* A. Write Settings */
                 hasWrite = Settings.System.canWrite(context);
             
                 /* B. Overlay */
                 try {
                     uid = pm.getPackageUid(targetPkg, 0);
                     opMode = appOps.checkOpNoThrow("android:system_alert_window", uid, targetPkg);
                     hasOverlay = (opMode == 0);
                 } catch (Exception e) {
                     hasOverlay = Settings.canDrawOverlays(context);
                 }
             
                 /* C. Usage Stats */
                 try {
                     uid = pm.getPackageUid(targetPkg, 0);
                     opMode = appOps.checkOpNoThrow("android:get_usage_stats", uid, targetPkg);
                     hasUsage = (opMode == 0);
                 } catch (Exception e) {
                     hasUsage = false;
                 }
             
                 /* D. Storage */
                 hasStorage = false;
                 if (sdk >= 30) {
                     hasStorage = Environment.isExternalStorageManager();
                 } else {
                     if (context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                         hasStorage = true;
                     } else {
                         if (!triedLegacyStoragePopup) {
                             triedLegacyStoragePopup = true;
                             legPerms = new String[] { Manifest.permission.WRITE_EXTERNAL_STORAGE };
                             if (tryRuntimePermissions(legPerms)) hasStorage = true;
                         }
                     }
                 }
             
                 /* E. Location */
                 hasLoc = false;
                 if (pm.checkPermission(Manifest.permission.ACCESS_FINE_LOCATION, targetPkg) == PackageManager.PERMISSION_GRANTED) {
                     if (sdk >= 29) {
                         if (pm.checkPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION, targetPkg) == PackageManager.PERMISSION_GRANTED) {
                             hasLoc = true;
                         }
                     } else {
                         hasLoc = true;
                     }
                 } else {
                     if (sdk < 29 && !triedLocPopup) {
                         triedLocPopup = true;
                         locPerms = new String[]{ Manifest.permission.ACCESS_FINE_LOCATION };
                         if (tryRuntimePermissions(locPerms)) continue; 
                     }
                 }
             
                 /* F. Notifications */
                 hasNotify = nm.areNotificationsEnabled();
                 
                 /* G. Battery Optimizations */
                 hasBattOpt = false;
                 if (sdk >= 23) {
                     hasBattOpt = powMgr.isIgnoringBatteryOptimizations(targetPkg);
                 } else {
                     hasBattOpt = true;
                 }
                 
                 /* H. Exact Alarms (API 31+) */
                 hasExactAlarm = true;
                 if (sdk >= 31) {
                     if (alarmMgr != null) {
                         hasExactAlarm = alarmMgr.canScheduleExactAlarms();
                     }
                 }
             
                 /* 3. Exit Condition */
                 if (hasWrite && hasOverlay && hasUsage && hasStorage && hasLoc && hasNotify && hasBattOpt && hasExactAlarm) {
                     tasker.setVariable("AAB_PermGranted", "3");
                     return "Success";
                 }
             
                 /* 4. Determine NEXT step */
                 nextAction = "";
                 title = "";
                 message = "";
                 checkType = 0; 
             
                 if (!hasWrite) {
                     title = "Step 1: Write Settings";
                     message = "Required to control brightness.\n\n1. Tap 'Grant'.\n2. Enable the switch.\n3. Press Back.";
                     nextAction = Settings.ACTION_MANAGE_WRITE_SETTINGS;
                     checkType = 1;
                 } 
                 else if (!hasOverlay) {
                     if (forceRestrictedFix) {
                         title = "Restricted Settings Fix";
                         message = "1. Tap 'Fix' (opens App Info).\n2. Tap 3-dots (top right) -> 'Allow restricted settings'.";
                         nextAction = Settings.ACTION_APPLICATION_DETAILS_SETTINGS;
                         checkType = 5; 
                     } else {
                         title = "Step 2: Display Overlay";
                         message = "Required for the dimming filter.\n\n1. Tap 'Grant'.\n2. Enable the switch.";
                         nextAction = Settings.ACTION_MANAGE_OVERLAY_PERMISSION;
                         checkType = 2;
                     }
                 } 
                 else if (!hasUsage) {
                     title = "Step 3: Usage Stats";
                     message = "Required to detect which app is open.\n\n1. Tap 'Grant'.\n2. SCROLL to find this app in the list.\n3. Enable the switch.";
                     nextAction = Settings.ACTION_USAGE_ACCESS_SETTINGS;
                     checkType = 6;
                 }
                 else if (!hasStorage) {
                     title = "Step 4: All Files Access";
                     if (sdk >= 30) {
                         message = "Required to save config files in Download/AAB.\n\nMedia permissions are NOT enough.\n\n1. Tap 'Grant'.\n2. Enable 'Allow access to manage all files'.";
                         nextAction = Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION;
                         checkType = 7;
                     } else {
                         message = "Required to save config files.\n\n1. Tap 'Grant'.\n2. Permissions -> Storage -> Allow.";
                         nextAction = Settings.ACTION_APPLICATION_DETAILS_SETTINGS;
                         checkType = 8;
                     }
                 }
                 else if (!hasLoc) {
                     title = "Step 5: Location (Always)";
                     message = "Required for sunrise/sunset.\n\nWE NEED 'ALLOW ALL THE TIME'.\n\n1. Tap 'Grant'.\n2. Tap 'Permissions' > 'Location'.\n3. Select 'Allow all the time'.";
                     nextAction = Settings.ACTION_APPLICATION_DETAILS_SETTINGS;
                     checkType = 3;
                 } 
                 else if (!hasNotify) {
                     if (!triedNotifPopup && sdk >= 33) {
                         triedNotifPopup = true;
                         notifPerms = new String[]{ Manifest.permission.POST_NOTIFICATIONS };
                         tryRuntimePermissions(notifPerms);
                         continue; 
                     }
                     title = "Step 6: Notifications";
                     message = "Required for service stability.\n\n1. Tap 'Grant'.\n2. Enable notifications.";
                     if (sdk >= 26) nextAction = Settings.ACTION_APP_NOTIFICATION_SETTINGS;
                     else nextAction = Settings.ACTION_APPLICATION_DETAILS_SETTINGS;
                     checkType = 4;
                 }
                 else if (!hasBattOpt) {
                     title = "Step 7: Battery Optimization";
                     message = "Required to keep the app changed listener alive.\n\n1. Tap 'Grant'.\n2. Tap 'Allow' to ignore battery restrictions.";
                     nextAction = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS;
                     checkType = 9;
                 }
                 else if (!hasExactAlarm) {
                     title = "Step 8: Exact Alarms";
                     message = "Required for time-based contexts.\n\n1. Tap 'Grant'.\n2. Enable 'Allow setting alarms and reminders'.";
                     nextAction = Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM;
                     checkType = 10;
                 }
             
                 /* 5. Show Dialog (Blocking) */
                 dialogSignal = SingleSubject.create();
             
                 uiConsumer = new Consumer() {
                     accept(Object activityObj) {
                         try {
                             act = (Activity) activityObj;
                             builder = new AlertDialog.Builder(act);
                             builder.setTitle(title);
                             builder.setMessage(message);
                             builder.setCancelable(false);
                             
                             btnText = (checkType == 5) ? "Fix" : "Grant";
                             
                             builder.setPositiveButton(btnText, new DialogInterface.OnClickListener() {
                                 onClick(DialogInterface dialog, int which) {
                                     dialogSignal.onSuccess("GRANT");
                                     act.finish();
                                 }
                             });
                             builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                                 onClick(DialogInterface dialog, int which) {
                                     dialogSignal.onSuccess("CANCEL");
                                     act.finish();
                                 }
                             });
                             builder.show();
                         } catch (Exception e) {
                             dialogSignal.onSuccess("ERROR");
                             if (activityObj instanceof Activity) {
                                 castAct = (Activity) activityObj;
                                 castAct.finish();
                             }
                         }
                     }
                 };
             
                 tasker.doWithActivity(uiConsumer);
                 userChoice = dialogSignal.blockingGet();
             
                 if ("CANCEL".equals(userChoice)) return "Cancelled";
                 if ("ERROR".equals(userChoice)) return "Error";
             
                 /* 6. Launch Intent */
                 try {
                     intent = new Intent(nextAction);
                     
                     if (checkType == 6) {
                         // Usage Access: List only
                     } 
                     else if (checkType == 7 || checkType == 9 || checkType == 10) {
                         intent.setData(Uri.parse("package:" + targetPkg));
                     }
                     else if (checkType == 4 && sdk >= 26) {
                          intent.putExtra(Settings.EXTRA_APP_PACKAGE, targetPkg);
                          intent.putExtra("android.provider.extra.APP_PACKAGE", targetPkg);
                     } 
                     else {
                          intent.setData(Uri.parse("package:" + targetPkg));
                     }
                     
                     tasker.getWithActivityForResult(intent).blockingGet();
                 } catch (Exception e) {
                     try { Thread.sleep(1000); } catch (Exception s) {}
                 }
             
                 /* 7. Logic Reset & Verification */
                 if (checkType == 5) {
                     forceRestrictedFix = false;
                     try { Thread.sleep(500); } catch (Exception s) {}
                     continue;
                 }
             
                 /* 8. Troubleshooting (Overlay Only) */
                 if (checkType == 2) {
                     stillMissing = false;
                     try {
                         uid = pm.getPackageUid(targetPkg, 0);
                         opMode = appOps.checkOpNoThrow("android:system_alert_window", uid, targetPkg);
                         if (opMode != 0) stillMissing = true;
                     } catch (Exception e) {
                         if (!Settings.canDrawOverlays(context)) stillMissing = true;
                     }
             
                     if (stillMissing) {
                         troubleSignal = SingleSubject.create();
                         tasker.doWithActivity(new Consumer() {
                             accept(Object activityObj) {
                                 act = (Activity) activityObj;
                                 builder = new AlertDialog.Builder(act);
                                 builder.setTitle("Permission Not Detected");
                                 builder.setMessage("Overlay is still off.\n\nWas the switch GREYED OUT?");
                                 builder.setCancelable(false);
                                 builder.setPositiveButton("Yes (Restricted)", new DialogInterface.OnClickListener() {
                                     onClick(DialogInterface dialog, int which) {
                                         troubleSignal.onSuccess("RESTRICTED");
                                         act.finish();
                                     }
                                 });
                                 builder.setNegativeButton("No (Retry)", new DialogInterface.OnClickListener() {
                                     onClick(DialogInterface dialog, int which) {
                                         troubleSignal.onSuccess("RETRY");
                                         act.finish();
                                     }
                                 });
                                 builder.show();
                             }
                         });
                         if ("RESTRICTED".equals(troubleSignal.blockingGet())) forceRestrictedFix = true;
                         else forceRestrictedFix = false;
                     }
                 }
             }
