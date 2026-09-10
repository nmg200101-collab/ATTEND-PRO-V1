package com.attendpro.store

import android.os.Bundle
import android.speech.tts.TextToSpeech
import com.attendpro.core.AttendanceAction
import com.attendpro.core.StoreRepository
import java.util.Locale

/** Optional configurable attendance TTS. UI preferences are isolated from attendance data. */
class StoreVoiceAnnouncer(context: android.content.Context, private val repo: StoreRepository) : TextToSpeech.OnInitListener {
    private val tts = TextToSpeech(context.applicationContext, this)
    @Volatile private var ready = false
    private val pending = ArrayDeque<String>()

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (!ready) return
        val result = tts.setLanguage(Locale("ar"))
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) tts.language = Locale.getDefault()
        applyPreferences()
        while (pending.isNotEmpty()) speak(pending.removeFirst())
    }

    fun applyPreferences() {
        if (!ready) return
        tts.setSpeechRate((repo.voiceRatePercent.coerceIn(50, 150) / 100f))
        val requested = repo.voiceName
        if (requested.isNotBlank()) tts.voices?.firstOrNull { it.name == requested }?.let { tts.voice = it }
    }

    fun availableVoices(): List<Pair<String, String>> = if (!ready) emptyList() else
        tts.voices.orEmpty().sortedWith(compareBy({ it.locale.displayLanguage }, { it.name })).map { it.name to "${it.locale.displayLanguage} • ${it.name}" }

    fun announceAttendance(name: String, action: AttendanceAction) {
        if (!repo.storeVoiceAttendanceEnabled) return
        val automatic = if (action == AttendanceAction.CHECK_IN) "تم إثبات حضور {name}" else "تم تسجيل انصراف {name}"
        speak(resolve(automatic, name))
    }

    fun announceRequestSent(name: String) { if (!repo.storeVoiceRequestEnabled) return; speak(resolve(if (repo.voiceMode == "CUSTOM") repo.voiceRequestSentText else "تم إرسال طلب إثبات حضور إلى {name}", name)); }
    fun announceMissingProof(name: String) { if (!repo.storeVoiceMissingProofEnabled) return; speak(resolve(if (repo.voiceMode == "CUSTOM") repo.voiceMissingProofText else "{name} لم يثبت الحضور", name)); }
    fun announceLate(name: String) { if (!repo.storeVoiceLateEnabled) return; speak(resolve(if (repo.voiceMode == "CUSTOM") repo.voiceLateText else "الموظف {name} لم يسجل الحضور في الموعد المحدد", name)) }

    fun speak(text: String) {
        if (text.isBlank() || !repo.attendanceVoiceAnnouncementEnabled) return
        if (!ready) { if (pending.size < 4) pending.addLast(text); return }
        applyPreferences()
        val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, repo.voiceVolumePercent.coerceIn(0, 100) / 100f) }
        tts.speak(text, TextToSpeech.QUEUE_ADD, params, "attend_store_${System.currentTimeMillis()}")
    }

    private fun resolve(template: String, name: String): String = template.ifBlank { "{name}" }.replace("{name}", name.ifBlank { "الموظف" })
    fun shutdown() = runCatching { tts.stop(); tts.shutdown() }.let { Unit }
}
