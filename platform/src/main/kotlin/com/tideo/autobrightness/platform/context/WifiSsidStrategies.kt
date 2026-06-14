package com.tideo.autobrightness.platform.context

import android.content.Context
import com.tideo.autobrightness.platform.privilege.ShizukuShell

/**
 * One ordered attempt to read the connected SSID WITHOUT Location, per Tasker's `_GetWifiNoLocation
 * V3` (task105/633, S12.7d/G2R-F41). Returns the SSID, or null when this strategy can't resolve it
 * so [AndroidWifiInfoReader] falls through to the next (Shizuku → dumpsys → Location callback last).
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
 * Strategy 2 — dump access (no Shizuku): `dumpsys wifi`, take the `mWifiInfo` … `COMPLETED` line and
 * regex the SSID out (Tasker: `dumpsys wifi | grep mWifiInfo | grep COMPLETED`). Works when the app
 * process holds DUMP / secure access; otherwise the exec is permission-denied → null (falls through).
 */
class DumpsysWifiSsidStrategy(@Suppress("unused") private val context: Context) : WifiSsidStrategy {
    override suspend fun trySsid(): String? {
        val out = execInProcess(arrayOf("sh", "-c", "dumpsys wifi")) ?: return null
        return normalizeSsid(parseDumpsysWifi(out))
    }

    private fun execInProcess(command: Array<String>): String? = try {
        val process = Runtime.getRuntime().exec(command)
        val stdout = process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor()
        stdout.ifBlank { null }
    } catch (_: Throwable) {
        null
    }
}

/** Extracts the SSID from `cmd wifi status` output (`Wifi is connected to "SSID"`). */
internal fun parseCmdWifiStatus(output: String): String? {
    val match = Regex("""Wifi is connected to\s+"?([^"\n]+?)"?\s*$""", RegexOption.MULTILINE)
        .find(output) ?: return null
    return match.groupValues[1].trim().ifEmpty { null }
}

/** Extracts the SSID from the `mWifiInfo` … `COMPLETED` line of `dumpsys wifi`. */
internal fun parseDumpsysWifi(output: String): String? {
    val line = output.lineSequence()
        .firstOrNull { it.contains("mWifiInfo", ignoreCase = true) && it.contains("COMPLETED") }
        ?: output.lineSequence().firstOrNull { it.contains("SSID:") && it.contains("COMPLETED") }
        ?: return null
    val match = Regex("""SSID:\s*"?(.*?)"?\s*,""").find(line) ?: return null
    return match.groupValues[1].trim().ifEmpty { null }
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
