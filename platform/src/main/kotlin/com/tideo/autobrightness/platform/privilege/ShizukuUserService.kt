package com.tideo.autobrightness.platform.privilege

/**
 * The Shizuku user-service implementation (S11, closes D-032). Shizuku's server instantiates this
 * class by name (via [Shizuku.UserServiceArgs]) inside a process that holds adb-shell (uid 2000) or
 * root privileges, then exposes its [IShizukuUserService.Stub] binder back to the app. Running
 * `pm grant` here therefore succeeds exactly as the documented adb command does — without the app
 * itself ever holding WRITE_SECURE_SETTINGS at install time.
 *
 * Must have a public no-arg (or single-Context) constructor: Shizuku reflects it into existence.
 *
 * We exec `pm grant` rather than calling IPackageManager.grantRuntimePermission because
 * WRITE_SECURE_SETTINGS is a signature|privileged permission, not a runtime (dangerous) one, so the
 * runtime-grant path does not apply to it; `pm grant` is the canonical channel (D-016).
 */
class ShizukuUserService : IShizukuUserService.Stub() {

    override fun destroy() {
        // Shizuku calls this (transaction 16777114) to tear the user service process down.
        System.exit(0)
    }

    override fun grantWriteSecureSettings(packageName: String): String = try {
        val process = Runtime.getRuntime().exec(
            arrayOf("pm", "grant", packageName, "android.permission.WRITE_SECURE_SETTINGS"),
        )
        val stderr = process.errorStream.bufferedReader().use { it.readText() }.trim()
        val code = process.waitFor()
        if (code == 0) "" else "pm grant exit=$code${if (stderr.isNotEmpty()) ": $stderr" else ""}"
    } catch (t: Throwable) {
        t.message ?: t.javaClass.simpleName
    }

    // S12.7d (G2R-F41): run a command in the privileged process and hand back its stdout. Used by
    // the no-Location SSID path (`cmd wifi status`), which reads the connected SSID without the
    // ACCESS_FINE_LOCATION runtime grant + Location services that the framework otherwise demands.
    override fun exec(command: Array<String>): String = try {
        val process = Runtime.getRuntime().exec(command)
        val stdout = process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor()
        stdout
    } catch (_: Throwable) {
        ""
    }
}
