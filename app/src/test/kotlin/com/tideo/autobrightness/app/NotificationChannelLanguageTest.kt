package com.tideo.autobrightness.app

import android.app.NotificationManager
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import com.tideo.autobrightness.app.runtime.AmbientMonitoringService
import com.tideo.autobrightness.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class NotificationChannelLanguageTest {
    @Test
    @Config(sdk = [31, 32])
    fun appCompatLanguageChange_renamesChannels() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val worker = Executors.newSingleThreadExecutor()
        WorkManager.initialize(app, Configuration.Builder()
            .setExecutor(worker)
            .setTaskExecutor(worker)
            .build())
        val previousLocales = AppCompatDelegate.getApplicationLocales()
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"))
        val serviceController = Robolectric.buildService(AmbientMonitoringService::class.java).create()
        val controller = Robolectric.buildActivity(MainActivity::class.java).create()
        try {
            val activity = controller.get()
            val manager = activity.getSystemService(NotificationManager::class.java)
            assertEquals("Ambient monitoring", manager.getNotificationChannel("ambient_monitoring").name.toString())
            assertEquals("Manual override", manager.getNotificationChannel("manual_override").name.toString())

            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("zh-Hans"))

            assertEquals("环境光监测", manager.getNotificationChannel("ambient_monitoring").name.toString())
            assertEquals("手动调节", manager.getNotificationChannel("manual_override").name.toString())

            app.openFileOutput(
                "androidx.appcompat.app.AppCompatDelegate.application_locales_record_file", Context.MODE_PRIVATE,
            ).use { it.write("<locales application_locales=\"en\"/>".toByteArray()) }
            assertEquals("Ambient monitoring", ContextCompat.getContextForLanguage(app).getString(R.string.notif_channel_ambient))
            serviceController.get().onConfigurationChanged(serviceController.get().resources.configuration)
            assertEquals("环境光监测", manager.getNotificationChannel("ambient_monitoring").name.toString())
            assertEquals("手动调节", manager.getNotificationChannel("manual_override").name.toString())

            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"))

            assertEquals("Ambient monitoring", manager.getNotificationChannel("ambient_monitoring").name.toString())
            assertEquals("Manual override", manager.getNotificationChannel("manual_override").name.toString())
            assertEquals(0, serviceController.get().runtimeStartCount)

            AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
            app.openFileOutput(
                "androidx.appcompat.app.AppCompatDelegate.application_locales_record_file", Context.MODE_PRIVATE,
            ).use { it.write("<locales application_locales=\"zh-Hans\"/>".toByteArray()) }
            serviceController.get().onConfigurationChanged(serviceController.get().resources.configuration)
            assertEquals("Ambient monitoring", manager.getNotificationChannel("ambient_monitoring").name.toString())
            val replacement = Robolectric.buildService(AmbientMonitoringService::class.java).create()
            try {
                assertEquals("Ambient monitoring", manager.getNotificationChannel("ambient_monitoring").name.toString())
                assertEquals("Manual override", manager.getNotificationChannel("manual_override").name.toString())
            } finally {
                replacement.destroy()
            }
        } finally {
            controller.destroy()
            serviceController.destroy()
            AppCompatDelegate.setApplicationLocales(previousLocales)
            app.deleteFile("androidx.appcompat.app.AppCompatDelegate.application_locales_record_file")
            AmbientMonitoringService.activityLocalesInitialized = false
            worker.shutdown()
            check(worker.awaitTermination(10, TimeUnit.SECONDS))
        }
    }

    @Test
    @Config(sdk = [31, 32])
    fun coldServiceStart_usesSavedLocaleWithoutAnActivity() {
        val app = ApplicationProvider.getApplicationContext<Context>()
        AmbientMonitoringService.activityLocalesInitialized = false
        app.openFileOutput(
            "androidx.appcompat.app.AppCompatDelegate.application_locales_record_file", Context.MODE_PRIVATE,
        ).use { it.write("<locales application_locales=\"zh-Hans\"/>".toByteArray()) }
        val controller = Robolectric.buildService(AmbientMonitoringService::class.java).create()
        try {
            val manager = app.getSystemService(NotificationManager::class.java)
            assertEquals("环境光监测", manager.getNotificationChannel("ambient_monitoring").name.toString())
            assertEquals("手动调节", manager.getNotificationChannel("manual_override").name.toString())
        } finally {
            controller.destroy()
            app.deleteFile("androidx.appcompat.app.AppCompatDelegate.application_locales_record_file")
        }
    }
}
