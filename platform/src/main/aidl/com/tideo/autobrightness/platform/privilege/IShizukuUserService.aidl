// Shizuku user-service interface (S11). Bound via Shizuku.bindUserService; the implementation
// runs in a process with shell (uid 2000) or root privileges, so it can execute the same
// `pm grant WRITE_SECURE_SETTINGS` the adb instruction documents (D-016/D-024 grant channel only).
package com.tideo.autobrightness.platform.privilege;

interface IShizukuUserService {
    // Special transaction id Shizuku invokes when it tears the user service down.
    void destroy() = 16777114;

    // Grants only the package whose Context constructed the user service. No caller-supplied
    // package or command crosses this privileged boundary.
    boolean grantWriteSecureSettings() = 1;

    // Narrow, allowlisted operations. Implementations use argv arrays (never `sh -c`) and bound
    // process lifetime plus stdout/stderr before returning data to the unprivileged app process.
    String wifiStatus() = 2;

    String readForceDark() = 3;

    String setForceDark(boolean enabled) = 4;
}
