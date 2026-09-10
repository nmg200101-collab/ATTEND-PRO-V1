package com.attendpro.employee

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import com.attendpro.core.EmployeeIdentityStore
import java.util.Locale

class EmployeeVoicePrompter(context: Context) : TextToSpeech.OnInitListener {
    private val appContext = context.applicationContext
    private val identity = EmployeeIdentityStore(appContext)
    private val tts = TextToSpeech(appContext, this)
    @Volatile private var ready = false
    private var pending: String? = null

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            val ar = Locale("ar")
            val result = tts.setLanguage(ar)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts.language = Locale.getDefault()
            }
            tts.setSpeechRate(identity.employeeVoiceRatePercent.coerceIn(50, 150) / 100f)
            pending?.let { speak(it) }
            pending = null
        }
    }

    fun speak(text: String) {
        if (text.isBlank()) return
        if (!ready) { pending = text; return }
        tts.setSpeechRate(identity.employeeVoiceRatePercent.coerceIn(50, 150) / 100f)
        val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, identity.employeeVoiceVolumePercent.coerceIn(0, 100) / 100f) }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, "attend_employee_${System.currentTimeMillis()}")
    }

    fun shutdown() = runCatching { tts.stop(); tts.shutdown() }.let { Unit }
}
