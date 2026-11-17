package com.focusflow.app.speech

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

enum class VoiceAvailability {
    /** A recognizer that runs on the phone: audio stays on the device. */
    ON_DEVICE,
    /** Only Android's default recognizer, which may send audio to a server. */
    ONLINE_POSSIBLE,
    UNAVAILABLE,
}

interface VoiceListener {
    fun onListening()
    fun onPartial(text: String)
    fun onFinal(text: String)
    fun onError(message: String)
}

/** Wraps Android's SpeechRecognizer. Must be used from the main thread. */
class VoiceRecognizer(private val context: Context) {
    private var recognizer: SpeechRecognizer? = null

    fun availability(): VoiceAvailability = when {
        Build.VERSION.SDK_INT >= 33 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context) -> VoiceAvailability.ON_DEVICE
        SpeechRecognizer.isRecognitionAvailable(context) -> VoiceAvailability.ONLINE_POSSIBLE
        else -> VoiceAvailability.UNAVAILABLE
    }

    /** Returns false if no allowed recognizer exists (the caller should offer typing instead). */
    fun start(offlineOnly: Boolean, listener: VoiceListener): Boolean {
        stop()
        val avail = availability()
        val r = when {
            avail == VoiceAvailability.ON_DEVICE && Build.VERSION.SDK_INT >= 33 ->
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            avail == VoiceAvailability.ONLINE_POSSIBLE && !offlineOnly -> SpeechRecognizer.createSpeechRecognizer(context)
            else -> return false
        }
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = listener.onListening()
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(listener::onPartial)
            }
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (text.isNullOrBlank()) listener.onError("Didn't catch that. Try again or type instead.") else listener.onFinal(text)
            }
            override fun onError(error: Int) = listener.onError(describe(error))
        })
        r.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            },
        )
        return true
    }

    fun stop() {
        recognizer?.let { runCatching { it.cancel() }; runCatching { it.destroy() } }
        recognizer = null
    }

    /** Ends the current utterance but still delivers what was heard. */
    fun finishListening() { runCatching { recognizer?.stopListening() } }

    private fun describe(code: Int) = when (code) {
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Didn't catch that. Try again or type instead."
        SpeechRecognizer.ERROR_AUDIO -> "There was a problem with the microphone."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is needed for voice input."
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "This recognizer needs a network connection. Type instead, or install an offline speech pack."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "The speech recognizer is busy. Try again in a moment."
        12, 13 -> "Offline speech for your language isn't installed. Type instead, or install it in your phone's speech settings."
        else -> "Voice input failed (code $code). You can type instead."
    }
}
