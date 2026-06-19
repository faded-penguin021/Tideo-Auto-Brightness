/* === EXTRACTED JAVA BLOCK (S1 ground-truth, verbatim, html.unescape-decoded) ===
 * Task: task105 "_GetWifiNoLocation V3"
 * Block: #1 of 1 in this task
 * XML: Advanced_Auto_Brightness_V3.3.prj_9.xml  arg0 (Java source) L8908-L9046; <code>474</code> at L8906
 * Output: none (uses tasker.setVariable() directly)
 * %vars consumed (S4 parameters): (none)
 * ============================================================================ */
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.content.Context;
import android.content.SharedPreferences;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

/* --- Quick WiFi check --- */
cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
if (cm == null) {
    tasker.setVariable("skip", "true");
    return;
}
net = cm.getActiveNetwork();
if (net == null) {
    tasker.setVariable("skip", "true");
    return;
}
caps = cm.getNetworkCapabilities(net);
if (caps == null || !caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
    tasker.setVariable("skip", "true");
    return;
}

privilege = tasker.getVariable("AAB_SecondaryPrivilege");

/* Treat "None" or empty as not set */
isPrivilegeSet = (privilege != null && privilege.length() > 0 && !privilege.equals("None"));

if (!isPrivilegeSet) {
    hasPrivilegeFound = false;

    /* --- Check Root --- */
    p = null;
    try {
        p = Runtime.getRuntime().exec("su -c id");
        p.getOutputStream().close();
        
        /* waitFor requires Android 8.0+ */
        finished = p.waitFor(2, TimeUnit.SECONDS);
        
        if (finished && p.exitValue() == 0) {
            reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            output = reader.readLine();
            reader.close();
            
            if (output != null && output.contains("uid=0")) {
                tasker.setVariable("AAB_SecondaryPrivilege", "Root");
                hasPrivilegeFound = true;
            }
        }
        
        if (!finished) {
            p.destroyForcibly();
        }
    } catch (Throwable e) { 
        if (p != null) p.destroy();
    }

    /* --- Check Shizuku --- */
    if (!hasPrivilegeFound) {
        try {
            if (tasker.getShizukuService("package") != null) {
                tasker.setVariable("AAB_SecondaryPrivilege", "Shizuku");
                hasPrivilegeFound = true;
            }
        } catch (Throwable e) { }
    }

    /* --- Check ADB WiFi --- */
    if (!hasPrivilegeFound) {
        port = 5555;
        portStr = null;

        try {
            sp = context.getSharedPreferences("net.dinglisch.android.tasker.preffy", 0);
            portStr = sp.getString("adbwp", null);
        } catch (Throwable e) { }

        if (portStr == null || portStr.length() == 0) {
            try {
                propProcess = Runtime.getRuntime().exec("getprop service.adb.tcp.port");
                reader = new BufferedReader(new InputStreamReader(propProcess.getInputStream()));
                propLine = reader.readLine();
                reader.close();
                propProcess.destroy();

                if (propLine != null && propLine.trim().length() > 0) {
                    portStr = propLine.trim();
                }
            } catch (Throwable e) { }
        }

        if (portStr != null && portStr.length() > 0) {
            try {
                parsedPort = Integer.parseInt(portStr);
                if (parsedPort > 0 && parsedPort <= 65535) {
                    port = parsedPort;
                }
            } catch (Throwable e) { }
        }

        socket = null;
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress("127.0.0.1", port), 200);
            socket.setSoTimeout(300);

            /* Verify ADB protocol */
            inStream = socket.getInputStream();
            header = new byte[4];
            bytesRead = inStream.read(header);
            
            if (bytesRead == 4 &&
                header[0] == (byte)'C' && 
                header[1] == (byte)'N' && 
                header[2] == (byte)'X' && 
                header[3] == (byte)'N') {
                
                tasker.setVariable("AAB_SecondaryPrivilege", "ADB WiFi");
                hasPrivilegeFound = true;
            }
        } catch (Throwable e) {
        } finally {
            if (socket != null) {
                try { socket.close(); } catch (Exception e) {}
            }
        }
    }

    /* --- Fallback --- */
    if (!hasPrivilegeFound) {
        tasker.setVariable("AAB_SecondaryPrivilege", "None");
    }
}
