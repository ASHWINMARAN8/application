package com.ironai.assistant.engine

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

/**
 * High-quality bilingual Text-to-Speech manager.
 * Operates offline using on-device voice models.
 * Automatically switches between English (en-IN/en-GB) and Tamil (ta-IN).
 */
class IronTtsManager(
    context: Context,
    private val personaManager: PersonaManager,
    private val onSpeechStatusChanged: (isSpeaking: Boolean) -> Unit
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = TextToSpeech(context, this)
    private var isInitialized = false

    private val tamilLocale = Locale("ta", "IN")
    private val englishLocale = Locale.US

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    onSpeechStatusChanged(true)
                }

                override fun onDone(utteranceId: String?) {
                    onSpeechStatusChanged(false)
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    onSpeechStatusChanged(false)
                }
            })
        } else {
            Log.e("IronTTS", "Failed to initialize TextToSpeech engine")
        }
    }

    fun speak(text: String, isTamil: Boolean) {
        if (!isInitialized || tts == null) return

        val targetLocale = if (isTamil) tamilLocale else englishLocale

        val langResult = tts?.setLanguage(targetLocale)
        if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            // Fallback to default if Tamil offline voice pack is downloading
            Log.w("IronTTS", "Target language data missing, checking fallbacks")
        }

        tts?.setPitch(personaManager.speechPitch)
        tts?.setSpeechRate(personaManager.speechRate)

        val utteranceId = "iron_speech_${System.currentTimeMillis()}"
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun stop() {
        tts?.stop()
        onSpeechStatusChanged(false)
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
