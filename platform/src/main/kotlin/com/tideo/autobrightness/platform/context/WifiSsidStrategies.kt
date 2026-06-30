package com.tideo.autobrightness.platform.context

import android.content.Context
import com.tideo.autobrightness.platform.privilege.ShizukuShell

/**
 * One ordered attempt to read the connected SSID WITHOUT Location, per Tasker's `_GetWifiNoLocation
 * V3` (task105/633, S12.7d/G2R-F41). Returns the SSID, or null when this strategy can't resolve it so
 * [AndroidWifiInfoReader] falls through to the next (Shizuku → root → dumpsys → Location callback last).
 */
fun interface WifiSsidStrategy {
    suspend fun trySsid(): String?
}

/**
 * Strategy 1 — Shizuku: `cmd wifi status` prints `Wifi is connected to "SSID"` (Tasker task105/633:
 * `cmd wifi status | grep "Wifi is connected to" | cut -d\" -f2`). Needs no Location at all.
 */
class ShizukuWifiSsidStrategy(private val context: Context) : WifiSsidStrategy {
    override suspend fun trySsid(): String? {
        val out = ShizukuShell.exec(context, arrayOf("sh", "-c", "cmd wifi status")) ?: return null
        return normalizeSsid(parseCmdWifiStatus(out))
    }
}

/**
 * Strategy 2 — root: `su -c 'cmd wifi status'`, the same `cmd wifi status` read as the Shizuku path but
 * over a root shell (task105 detects "Root" as a secondary privilege). No-op fall-through (null) when
 * the device isn't rooted or the user denies the su prompt.
 */
class RootWifiSsidStrategy(@Suppress("unused") private val context: Context) : WifiSsidStrategy {
    override suspend fun trySsid(): String? {
        val out = execShell(arrayOf("su", "-c", "cmd wifi status")) ?: return null
        return normalizeSsid(parseCmdWifiStatus(out))
    }
}

/**
 * Strategy 3 — DUMP grant (no Shizuku/root): in-process `dumpsys wifi`, take the `mWifiInfo` …
 * `COMPLETED` line and regex the SSID out (Tasker: `dumpsys wifi | grep mWifiInfo | grep COMPLETED`).
 * Works only when the app process holds `android.permission.DUMP` — which a user can grant once over
 * ADB (`adb shell pm grant <pkg> android.permission.DUMP`; DUMP carries the `development` protection
 * flag, like WRITE_SECURE_SETTINGS). Ungranted, the exec is permission-denied → null (falls through).
 */
class DumpsysWifiSsidStrategy(@Suppress("unused") private val context: Context) : WifiSsidStrategy {
    override suspend fun trySsid(): String? {
        val out = execShell(arrayOf("sh", "-c", "dumpsys wifi")) ?: return null
        return normalizeSsid(parseDumpsysWifi(out))
    }
}

/**
 * Runs a short-lived shell command, returning its stdout (null on failure / empty). Reads stdout to
 * EOF *before* waiting so the (potentially large) `dumpsys wifi` output can't deadlock against a full
 * pipe buffer; mirrors AndroidPrivilegeManager.tryGrantViaRoot's blocking `su` invocation.
 */
private fun execShell(command: Array<String>): String? = try {
    val process = Runtime.getRuntime().exec(command)
    val stdout = process.inputStream.bufferedReader().use { it.readText() }
    process.waitFor()
    stdout.ifBlank { null }
} catch (_: Throwable) {
    null
}

/** Extracts the SSID from `cmd wifi status` output (`Wifi is connected to "SSID"`). */
internal fun parseCmdWifiStatus(output: String): String? {
    val match = Regex("""Wifi is connected to\s+"?([^"\n]+?)"?\s*$""", RegexOption.MULTILINE)
        .find(output) ?: return null
    return match.groupValues[1].trim().ifEmpty { null }
}

/**
 * Extracts the SSID from `dumpsys wifi`, mirroring Tasker's two-step Variable Search Replace strategy
 * (each step replaces the whole string with `$1`) over the `mWifiInfo` … `COMPLETED` line:
 *   1. `(?s).*?SSID:\s*"([^"]+)".*` — the SSID inside the OS-applied quotes.
 *   2. `(?s).*?SSID:\s*([^,]+),.*`  — only when step 1 left the line untouched (no quotes, so it still
 *                                     contains `mWifiInfo`), the unquoted SSID up to the next comma.
 * ⚠ Both steps anchor on the FIRST `SSID:` token, so a network literally named "SSID" mis-anchors —
 * Tasker shipped with this caveat ("If your SSID contains SSID, this will cause problems").
 */
internal fun parseDumpsysWifi(output: String): String? {
    // Tasker: `dumpsys wifi | grep mWifiInfo | grep COMPLETED` — the connected-network info line.
    val line = output.lineSequence()
        .firstOrNull { it.contains("mWifiInfo") && it.contains("COMPLETED") }
        ?: return null

    // Step 1 ($1): quoted SSID. matchEntire mirrors Tasker's whole-string replace.
    Regex("""(?s).*?SSID:\s*"([^"]+)".*""").matchEntire(line)?.let { return it.groupValues[1] }

    // Step 2 ($1): runs only because step 1 didn't match (the line still holds mWifiInfo) — the
    // unquoted SSID up to the next comma.
    return Regex("""(?s).*?SSID:\s*([^,]+),.*""").matchEntire(line)?.groupValues?.get(1)?.trim()
}

/**
 * Strips OS-applied quotes and rejects the framework placeholders (`<unknown ssid>`, `<redacted>`,
 * `<none>`) so a redacted read never masquerades as a real SSID (mirrors task633's checks).
 */
internal fun normalizeSsid(raw: String?): String? {
    val s = raw?.trim()?.removeSurrounding("\"")?.trim() ?: return null
    if (s.isEmpty() || s.startsWith("<")) return null
    return s
}
