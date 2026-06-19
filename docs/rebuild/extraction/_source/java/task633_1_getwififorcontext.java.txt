/* === EXTRACTED JAVA BLOCK (S1 ground-truth, verbatim, html.unescape-decoded) ===
 * Task: task633 "_GetWifiForContext"
 * Block: #1 of 1 in this task
 * XML: Advanced_Auto_Brightness_V3.3.prj_9.xml  arg0 (Java source) L28828-L28856; <code>474</code> at L28827
 * Output: none (uses tasker.setVariable() directly)
 * %vars consumed (S4 parameters): (none)
 * ============================================================================ */
         import android.net.wifi.WifiManager;
         import android.net.wifi.WifiInfo;
         import android.content.Context;
         
         wifiMgr = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
         ssid = "";
         
         if (wifiMgr != null) {
             try {
                 wifiInfo = wifiMgr.getConnectionInfo();
                 if (wifiInfo != null && wifiInfo.getNetworkId() != -1) {
                     rawSsid = wifiInfo.getSSID();
                     if (rawSsid != null && !rawSsid.equals("<unknown ssid>") && !rawSsid.equals("<redacted>")) {
                         /* Strip quotes wrapped by Android OS */
                         if (rawSsid.startsWith("\"") && rawSsid.endsWith("\"")) {
                             ssid = rawSsid.substring(1, rawSsid.length() - 1);
                         } else {
                             ssid = rawSsid;
                         }
                     }
                 }
             } catch (Exception e) {}
         }
         
         if (ssid.equals("")) {
             tasker.setVariable("err_msg", "Could not read SSID. Ensure Wi-Fi is connected and Location is enabled.");
         } else {
             tasker.setVariable("current_ssid", ssid);
         }
