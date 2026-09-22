package com.tideo.autobrightness.platform.display

import android.content.Context
import com.tideo.autobrightness.platform.privilege.ColorDisplayCli
import com.tideo.autobrightness.platform.privilege.ShizukuShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

interface NightDisplayServiceBridge {
    suspend fun readKelvin(): Int?

    suspend fun setKelvin(kelvin: Int, quick: Boolean = false): Int?
}

class AndroidNightDisplayServiceBridge(context: Context) : NightDisplayServiceBridge {
    private val appContext = context.applicationContext
    private val mutex = Mutex()

    @Volatile private var rootUnavailable = false

    override suspend fun readKelvin(): Int? = mutex.withLock {
        ShizukuShell.readNightDisplayTemperature(appContext) ?: rootCli(ROOT_TIMEOUT_SECONDS, "get")
    }

    override suspend fun setKelvin(kelvin: Int, quick: Boolean): Int? = mutex.withLock {
        ShizukuShell.setNightDisplayTemperature(appContext, kelvin)
            ?: rootCli(if (quick) QUICK_ROOT_TIMEOUT_SECONDS else ROOT_TIMEOUT_SECONDS, "set", kelvin.toString())
    }

    private suspend fun rootCli(timeoutSeconds: Long, vararg args: String): Int? = withContext(Dispatchers.IO) {
        if (rootUnavailable) return@withContext null
        val result = try {
            val apk = appContext.applicationInfo.sourceDir
            val script = "CLASSPATH=$apk app_process /system/bin ${ColorDisplayCli::class.java.name} " +
                args.joinToString(" ")
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", script))
            process.outputStream.close()
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                null
            } else if (process.exitValue() != 0) {
                null
            } else {
                process.inputStream.bufferedReader().use { it.readText() }.trim().toIntOrNull()?.takeIf { it > 0 }
            }
        } catch (_: Throwable) {
            null
        }
        if (result == null) rootUnavailable = true
        result
    }

    private companion object {
        const val ROOT_TIMEOUT_SECONDS = 15L
        const val QUICK_ROOT_TIMEOUT_SECONDS = 3L
    }
}
