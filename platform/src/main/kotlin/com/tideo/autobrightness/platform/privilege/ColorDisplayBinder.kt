package com.tideo.autobrightness.platform.privilege

import android.os.IBinder
import kotlin.system.exitProcess

internal object ColorDisplayBinder {
    private fun service(): Any? {
        val binder = Class.forName("android.os.ServiceManager")
            .getMethod("getService", String::class.java)
            .invoke(null, "color_display") as? IBinder ?: return null
        return Class.forName("android.hardware.display.IColorDisplayManager\$Stub")
            .getMethod("asInterface", IBinder::class.java)
            .invoke(null, binder)
    }

    fun readKelvin(): Int? = runCatching {
        val service = service() ?: return null
        service.javaClass.getMethod("getNightDisplayColorTemperature").invoke(service) as Int
    }.getOrNull()

    fun setKelvin(kelvin: Int): Int? = runCatching {
        val service = service() ?: return null
        val accepted = service.javaClass
            .getMethod("setNightDisplayColorTemperature", Int::class.javaPrimitiveType)
            .invoke(service, kelvin) as Boolean
        if (accepted) readKelvin() else null
    }.getOrNull()
}

object ColorDisplayCli {
    @JvmStatic
    fun main(args: Array<String>) {
        val kelvin = when (args.getOrNull(0)) {
            "get" -> ColorDisplayBinder.readKelvin()
            "set" -> args.getOrNull(1)?.toIntOrNull()?.let(ColorDisplayBinder::setKelvin)
            else -> null
        }
        if (kelvin == null) exitProcess(1)
        println(kelvin)
        exitProcess(0)
    }
}
