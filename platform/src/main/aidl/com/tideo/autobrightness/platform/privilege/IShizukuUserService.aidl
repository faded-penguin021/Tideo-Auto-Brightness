// Shizuku user-service interface (S11). Bound via Shizuku.bindUserService; the implementation
// runs in a process with shell (uid 2000) or root privileges, so it can execute the same
// `pm grant WRITE_SECURE_SETTINGS` the adb instruction documents (D-016/D-024 grant channel only).
package com.tideo.autobrightness.platform.privilege;

interface IShizukuUserService {
    // Special transaction id Shizuku invokes when it tears the user service down.
    void destroy() = 16777114;

    // Runs `pm grant <packageName> android.permission.WRITE_SECURE_SETTINGS` in the privileged
    // process. Returns an empty string on success, or a non-empty diagnostic on failure.
    String grantWriteSecureSettings(String packageName) = 1;

    // Runs an arbitrary command in the privileged (shell uid 2000 / root) process and returns its
    // stdout (empty string on failure). Used by the no-Location SSID path (_GetWifiNoLocation V3,
    // S12.7d/G2R-F41): `cmd wifi status` reads the connected SSID without ACCESS_FINE_LOCATION.
    String exec(in String[] command) = 2;
}
