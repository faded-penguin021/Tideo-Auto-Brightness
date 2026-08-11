package com.tideo.autobrightness.platform.context

import android.content.Context
import com.tideo.autobrightness.platform.privilege.ShizukuShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** SSID read attempt without Location (task105/633, G2R-F41). Returns null to fallthrough. */
fun interface WifiSsidStrategy {
    suspend fun trySsid(): String?
}

/** Strategy 1 — Shizuku: `cmd wifi status` (task105/633). */
class ShizukuWifiSsidStrategy(private val context: Context) : WifiSsidStrategy {
    override suspend fun trySsid(): String? {
        val out = ShizukuShell.read(context, ShizukuShell.ReadOperation.WIFI_STATUS) ?: return null
        return normalizeSsid(parseCmdWifiStatus(out))
    }
}

/** Strategy 2 — root: `su -c 'cmd wifi status'` (task105). */
class RootWifiSsidStrategy(@Suppress("unused") private val context: Context) : WifiSsidStrategy {
    override suspend fun trySsid(): String? {
        val out = execShell(arrayOf("su", "-c", "cmd wifi status")) ?: return null
        return normalizeSsid(parseCmdWifiStatus(out))
    }
}

/** Strategy 3 — DUMP grant: in-process `dumpsys wifi` (task633, requires android.permission.DUMP). */
class DumpsysWifiSsidStrategy(@Suppress("unused") private val context: Context) : WifiSsidStrategy {
    override suspend fun trySsid(): String? {
        val out = execShell(arrayOf("sh", "-c", "dumpsys wifi")) ?: return null
        return normalizeSsid(parseDumpsysWifi(out))
    }
}

/** Run shell command; read stdout before waiting (avoid deadlock on large output). */
private suspend fun execShell(command: Array<String>): String? = withContext(Dispatchers.IO) {
    try {
        val process = Runtime.getRuntime().exec(command)
        process.outputStream.close()
        var stdout = ByteArray(0)
        var stderrOverflow = false
        var readFailed = false
        val stdoutThread = thread(name = "aab-wifi-stdout") {
            try {
                stdout = process.inputStream.use { it.readNBytes(OUTPUT_LIMIT + 1) }
            } catch (_: Throwable) {
                readFailed = true
            }
        }
        val stderrThread = thread(name = "aab-wifi-stderr") {
            try {
                stderrOverflow = process.errorStream.use { it.readNBytes(ERROR_LIMIT + 1).size > ERROR_LIMIT }
            } catch (_: Throwable) {
                readFailed = true
            }
        }
        if (!process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            process.waitFor()
        }
        stdoutThread.join()
        stderrThread.join()
        if (process.exitValue() != 0 || readFailed || stdout.size > OUTPUT_LIMIT || stderrOverflow) {
            return@withContext null
        }
        stdout.toString(Charsets.UTF_8).ifBlank { null }
    } catch (_: Throwable) {
        null
    }
}

private const val COMMAND_TIMEOUT_SECONDS = 15L
private const val OUTPUT_LIMIT = 256 * 1024
private const val ERROR_LIMIT = 4 * 1024

internal fun parseCmdWifiStatus(output: String): String? {
    val match = Regex("""Wifi is connected to\s+"?([^"\n]+?)"?\s*$""", RegexOption.MULTILINE)
        .find(output) ?: return null
    return match.groupValues[1].trim().ifEmpty { null }
}

/** task633: Extract SSID from `dumpsys wifi` mWifiInfo line (two-step Tasker regex). */
internal fun parseDumpsysWifi(output: String): String? {
    // Tasker: `dumpsys wifi | grep mWifiInfo | grep COMPLETED` — the connected-network info line.
    val line = output.lineSequence()
        .firstOrNull { it.contains("mWifiInfo") && it.contains("COMPLETED") }
        ?: return null
    // Step 1: quoted SSID.
    Regex("""(?s).*?SSID:\s*"([^"]+)".*""").matchEntire(line)?.let { return it.groupValues[1] }
    // Step 2: unquoted SSID up to comma.
    return Regex("""(?s).*?SSID:\s*([^,]+),.*""").matchEntire(line)?.groupValues?.get(1)?.trim()
}

/** Strip quotes, reject OS placeholders (`<*>`). */
internal fun normalizeSsid(raw: String?): String? {
    val s = raw?.trim()?.removeSurrounding("\"")?.trim() ?: return null
    if (s.isEmpty() || s.startsWith("<")) return null
    return s
}
