package com.attendpro.foundation.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PreferenceBackupCodecTest {
    @Test fun typedSettingsRoundTripWithoutTypeLoss() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = context.getSharedPreferences("source_preferences", Context.MODE_PRIVATE)
        source.edit().clear()
            .putString("name", "فرع الكرادة")
            .putInt("count", 12)
            .putLong("time", 9_876_543_210L)
            .putFloat("score", 0.75f)
            .putBoolean("enabled", true)
            .putStringSet("methods", setOf("BLE", "QR"))
            .commit()

        val section = PreferenceBackupCodec.capture(context, "source_preferences") { it != "ignored" }
        val (name, entries) = PreferenceBackupCodec.decode(section, setOf("source_preferences"))
        val target = context.getSharedPreferences("target_preferences", Context.MODE_PRIVATE)
        val editor = target.edit().clear()
        entries.forEach { PreferenceBackupCodec.put(editor, it) }
        assertTrue(editor.commit())

        assertEquals("source_preferences", name)
        assertEquals("فرع الكرادة", target.getString("name", ""))
        assertEquals(12, target.getInt("count", 0))
        assertEquals(9_876_543_210L, target.getLong("time", 0L))
        assertEquals(0.75f, target.getFloat("score", 0f))
        assertTrue(target.getBoolean("enabled", false))
        assertEquals(setOf("BLE", "QR"), target.getStringSet("methods", emptySet()))
    }
}
