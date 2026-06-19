/* === EXTRACTED JAVA BLOCK (S1 ground-truth, verbatim, html.unescape-decoded) ===
 * Task: task378 "_PrivilegeDetection V5 (Java)"
 * Block: #1 of 1 in this task
 * XML: Advanced_Auto_Brightness_V3.3.prj_9.xml  arg0 (Java source) L9469-L9622; <code>474</code> at L9468
 * Output: none (uses tasker.setVariable() directly)
 * %vars consumed (S4 parameters): (none)
 * ============================================================================ */
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.InetSocketAddress;

caller = tasker.getVariable("caller1");
privilege = tasker.getVariable("AAB_Privilege");

isAnon = (caller != null && caller.matches(".*anon.*"));
isSetInitialBrightness = (caller != null && caller.matches(".*Set Initial Brightness.*"));

isPrivilegeSet = (privilege != null && privilege.length() > 0 && !privilege.equals("None"));

if (isSetInitialBrightness) {
    tasker.setVariable("AAB_Privilege", null);
    privilege = null;
    isPrivilegeSet = false;
}

if (!isPrivilegeSet) {
    hasPrivilegeFound = false;

    /* --- Check Root --- */
    p = null;
    killer = null;
    try {
        p = Runtime.getRuntime().exec(new String[]{"su", "-c", "id"});
        final Process proc = p;

        /* Drain stderr so the process can't block on a full buffer */
        Thread stderrDrainer = new Thread(new Runnable() {
            public void run() {
                try {
                    byte[] buf = new byte[512];
                    while (proc.getErrorStream().read(buf) != -1) {}
                } catch (Throwable t) {}
            }
        });
        stderrDrainer.setDaemon(true);
        stderrDrainer.start();

        /* Hard-kill after 2s — replaces waitFor(long, TimeUnit) which needs API 26 */
        killer = new Thread(new Runnable() {
            public void run() {
                try {
                    Thread.sleep(2000);
                    proc.destroy();
                } catch (Throwable t) {}
            }
        });
        killer.setDaemon(true);
        killer.start();

        p.getOutputStream().close();
        int exitVal = -1;
        try { exitVal = p.waitFor(); } catch (Throwable t) {}

        if (exitVal == 0) {
            reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            output = reader.readLine();
            reader.close();
            if (output != null && output.contains("uid=0")) {
                tasker.setVariable("AAB_Privilege", "Root");
                privilege = "Root";
                hasPrivilegeFound = true;
            }
        }
    } catch (Throwable e) {
        if (p != null) try { p.destroy(); } catch (Throwable t) {}
    } finally {
        if (killer != null) killer.interrupt();
    }

    /* --- Check Write Secure Settings --- */
    if (!hasPrivilegeFound) {
        try {
            pm = context.getPackageManager();
            packageName = context.getPackageName();
            if (pm.checkPermission("android.permission.WRITE_SECURE_SETTINGS", packageName) == PackageManager.PERMISSION_GRANTED) {
                tasker.setVariable("AAB_Privilege", "Write Secure");
                privilege = "Write Secure";
                hasPrivilegeFound = true;
            }
        } catch (Exception e) {}
    }

    /* --- Check Shizuku --- */
    if (!hasPrivilegeFound) {
        try {
            if (tasker.getShizukuService("package") != null) {
                tasker.setVariable("AAB_Privilege", "Shizuku");
                privilege = "Shizuku";
                hasPrivilegeFound = true;
            }
        } catch (Exception e) {}
    }

    /* --- Check ADB WiFi --- */
    if (!hasPrivilegeFound) {
        port = 5555;
        portStr = null;

        try {
            sp = context.getSharedPreferences("net.dinglisch.android.tasker.preffy", 0);
            portStr = sp.getString("adbwp", null);
        } catch (Exception e) {}

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
            } catch (Exception e) {}
        }

        if (portStr != null && portStr.length() > 0) {
            try {
                parsedPort = Integer.parseInt(portStr);
                if (parsedPort > 0 && parsedPort <= 65535) {
                    port = parsedPort;
                }
            } catch (Exception e) {}
        }

        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress("127.0.0.1", port), 200);
            socket.close();
            tasker.setVariable("AAB_Privilege", "ADB WiFi");
            privilege = "ADB WiFi";
            hasPrivilegeFound = true;
        } catch (Exception e) {}
    }

    /* --- Fallback --- */
    if (!hasPrivilegeFound) {
        tasker.setVariable("AAB_Privilege", "None");
        privilege = "None";
        tasker.showToast("⚠️ Unprivileged will draw a semi-transparent overlay and eat your battery. Not recommended!");
    }
}

finalPrivilege = tasker.getVariable("AAB_Privilege");
if (isAnon && finalPrivilege != null && !finalPrivilege.equals("None")) {
    tasker.showToast("Current privilege: " + finalPrivilege + ".");
}
