package com.example.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class FrenchSpeechEngine(private val context: Context) : RecognitionListener {

    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    private var speechRecognizer: SpeechRecognizer? = null

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _liveTranscript = MutableStateFlow("")
    val liveTranscript: StateFlow<String> = _liveTranscript.asStateFlow()

    private val _speechRate = MutableStateFlow(0.85f) // Slightly slower for beginning learners
    val speechRate: StateFlow<Float> = _speechRate.asStateFlow()

    private var onSpeechResultListener: ((String) -> Unit)? = null

    init {
        initTts()
    }

    private fun initTts() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.FRENCH)
                isTtsReady = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
                if (!isTtsReady) {
                    // Fallback to France
                    tts?.setLanguage(Locale.FRANCE)
                    isTtsReady = true
                }
                tts?.setSpeechRate(_speechRate.value)
            } else {
                Log.e("FrenchSpeechEngine", "TTS initialization failed")
            }
        }
    }

    fun setSpeechRate(rate: Float) {
        _speechRate.value = rate
        tts?.setSpeechRate(rate)
    }

    fun speak(text: String, rate: Float? = null, locale: Locale = Locale.FRENCH, onComplete: (() -> Unit)? = null) {
        val currentRate = rate ?: _speechRate.value
        tts?.setLanguage(locale)
        tts?.setSpeechRate(currentRate)

        if (onComplete != null) {
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    onComplete()
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {}
            })
        }

        val utteranceId = "tts_${System.currentTimeMillis()}"
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun stopSpeaking() {
        tts?.stop()
    }

    fun startListening(language: String = "auto", onResult: ((String) -> Unit)? = null) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.w("FrenchSpeechEngine", "Speech recognition not available on this device")
            return
        }

        stopListening()
        onSpeechResultListener = onResult

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(this@FrenchSpeechEngine)
        }

        val resolvedLang = when {
            language.equals("auto", ignoreCase = true) -> Locale.getDefault().toLanguageTag()
            language.startsWith("en", ignoreCase = true) -> "en-US"
            language.startsWith("fr", ignoreCase = true) -> "fr-FR"
            else -> language
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, resolvedLang)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, resolvedLang)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        }

        try {
            speechRecognizer?.startListening(intent)
            _isListening.value = true
        } catch (e: Exception) {
            Log.e("FrenchSpeechEngine", "Error starting listening", e)
            _isListening.value = false
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (e: Exception) {
            Log.e("FrenchSpeechEngine", "Error stopping listening", e)
        } finally {
            _isListening.value = false
        }
    }

    // Speech recognition callbacks
    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {
        _isListening.value = false
    }

    override fun onError(error: Int) {
        _isListening.value = false
        Log.w("FrenchSpeechEngine", "SpeechRecognizer error code: $error")
    }

    override fun onResults(results: Bundle?) {
        _isListening.value = false
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val bestResult = matches?.firstOrNull() ?: ""
        if (bestResult.isNotEmpty()) {
            _liveTranscript.value = bestResult
            onSpeechResultListener?.invoke(bestResult)
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        matches?.firstOrNull()?.let {
            _liveTranscript.value = it
        }
    }

    override fun onEvent(eventType: Int, params: Bundle?) {}

    fun destroy() {
        stopListening()
        tts?.shutdown()
    }
}
