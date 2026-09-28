package com.tideo.autobrightness.app.settings

import androidx.datastore.core.DataStore
import com.tideo.autobrightness.domain.brightness.BrightnessFormulae
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CurveParamFreedomTest {

    private class FakeDataStore<T>(initial: T) : DataStore<T> {
        private val state = MutableStateFlow(initial)
        override val data: Flow<T> = state
        override suspend fun updateData(transform: suspend (t: T) -> T): T {
            val updated = transform(state.value)
            state.update { updated }
            return updated
        }
    }

    private val weirdCurves: Map<String, AabSettings> = mapOf(
        "issue 133 wizard output" to
            AabSettings(form1A = 28.7353, zone1End = 17, form2B = 0.6759f, form2C = 1, zone2End = 383),
        "steep zone 1 ending at 1 lux, nearly flat zone 2" to
            AabSettings(form1A = 50.0, zone1End = 1, form2B = 0.01f, form2C = 0, zone2End = 10_000),
        "perfectly flat zone 2" to
            AabSettings(form1A = 10.0, zone1End = 100, form2B = 0f, form2C = 18, zone2End = 5_000),
        "black zone 1" to
            AabSettings(form1A = 0.0, zone1End = 1, form2B = 10f, form2C = 0, zone2End = 1_000),
        "very steep, narrow zone 2" to
            AabSettings(form1A = 1.0, zone1End = 5, form2B = 45f, form2C = 1, zone2End = 60),
        "negative zone 2 offset" to
            AabSettings(form1A = 3.0, zone1End = 35, form2B = 8.8f, form2C = -50, zone2End = 10_000),
        "zone 2 offset far above 50" to
            AabSettings(form1A = 2.0, zone1End = 500, form2B = 20f, form2C = 400, zone2End = 2_000),
        "zone ends past sunlight" to
            AabSettings(form1A = 1.2, zone1End = 25_000, form2B = 1f, form2C = 18, zone2End = 150_000),
    )

    @Test
    fun `every weird curve is one Apply accepts`() {
        for ((name, s) in weirdCurves) {
            val coeffs = BrightnessFormulae.deriveContinuityCoefficients(
                s.form1A, s.form2B.toDouble(), s.form2C.toDouble(),
                s.zone1End.toDouble(), s.zone2End.toDouble(), s.maxBrightness.toDouble(),
            )
            assertTrue(coeffs.form3A >= 0.0, "$name: Form3A ${coeffs.form3A} must be ≥ 0")
            val critical = SettingsValidator.validate(s).filter { it.severity == Severity.CRITICAL }
            assertTrue(critical.isEmpty(), "$name: no CRITICAL error expected, got $critical")
        }
    }

    @Test
    fun `validate leaves every weird curve untouched`() {
        for ((name, s) in weirdCurves) assertEquals(s, s.validate(), name)
    }

    @Test
    fun `every weird curve survives a DataStore write and cold read`() = runTest {
        for ((name, s) in weirdCurves) {
            val out = ByteArrayOutputStream()
            AabSettingsSerializer.writeTo(s, out)
            assertEquals(s, AabSettingsSerializer.readFrom(out.toByteArray().inputStream()), name)
        }
    }

    @Test
    fun `every weird curve survives profile save and load`() = runTest {
        val store = UserProfileStore(FakeDataStore(SavedProfiles()))
        for ((name, s) in weirdCurves) {
            store.save(name, s)
            assertEquals(s, store.get(name), name)
        }
    }

    @Test
    fun `a legacy import keeps an out-of-old-range curve`() {
        val s = TaskerLegacyProfileSerializer.deserialize(
            """
                %AAB_Form1A = 28.7353
                %AAB_Zone1End = 17
                %AAB_Form2B = 45
                %AAB_Form2C = -12
                %AAB_Zone2End = 150000
            """.trimIndent(),
        )
        assertEquals(28.7353, s.form1A, 1e-9)
        assertEquals(45f, s.form2B)
        assertEquals(-12, s.form2C)
        assertEquals(150_000, s.zone2End)
    }

    @Test
    fun `validate still repairs what breaks the curve math`() {
        val repaired = AabSettings(
            form1A = -1.0,
            form2B = Float.POSITIVE_INFINITY,
            zone1End = 0,
            form2C = 9,
            zone2End = -5,
        ).validate()
        assertEquals(0.0, repaired.form1A, "negative Form1A makes Form2A < 0")
        assertEquals(AabSettings().form2B, repaired.form2B, "non-finite Form2B falls back to default")
        assertEquals(1, repaired.zone1End)
        assertEquals(1, repaired.form2C, "Form2C above Zone1End makes the zone-2 root NaN")
        assertEquals(1, repaired.zone2End, "Zone2End below Zone1End")
        assertEquals(AabSettings().form1A, AabSettings(form1A = Double.POSITIVE_INFINITY).validate().form1A)
    }
}
