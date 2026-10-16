package com.example.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _isContinuousMode = MutableStateFlow(false)
    val isContinuousMode: StateFlow<Boolean> = _isContinuousMode.asStateFlow()

    private val _liveTranscript = MutableStateFlow("")
    val liveTranscript: StateFlow<String> = _liveTranscript.asStateFlow()

    private val _speechRate = MutableStateFlow(0.85f)
    val speechRate: StateFlow<Float> = _speechRate.asStateFlow()

    private var currentLanguage = "auto"
    private var onSpeechResultListener: ((String) -> Unit)? = null
    private var isPaused = false

    init {
        initTts()
    }

    private fun initTts() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.FRENCH)
                isTtsReady = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
                if (!isTtsReady) {
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

    fun startListening(
        language: String = "auto",
        continuous: Boolean = false,
        onResult: ((String) -> Unit)? = null
    ) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.w("FrenchSpeechEngine", "Speech recognition not available on this device")
            return
        }

        currentLanguage = language
        _isContinuousMode.value = continuous
        isPaused = false
        onSpeechResultListener = onResult

        startRecognitionInternal()
    }

    private fun startRecognitionInternal() {
        try {
            speechRecognizer?.destroy()
            speechRecognizer = null

            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(this@FrenchSpeechEngine)
            }

            val resolvedPrimary = when {
                currentLanguage.startsWith("en", ignoreCase = true) -> "en-US"
                currentLanguage.startsWith("fr", ignoreCase = true) -> "fr-FR"
                else -> {
                    // For auto, prefer French if device is set to fr, or en-US with fr support
                    if (Locale.getDefault().language == "fr") "fr-FR" else "en-US"
                }
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, resolvedPrimary)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, resolvedPrimary)
                putExtra("android.speech.extra.ADDITIONAL_LANGUAGES", arrayOf("fr-FR", "en-US"))
                putExtra("android.speech.extra.LANGUAGE_DETECTION_ALLOWED_LANGUAGES", arrayOf("fr-FR", "en-US"))
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            }

            speechRecognizer?.startListening(intent)
            _isListening.value = true
        } catch (e: Exception) {
            Log.e("FrenchSpeechEngine", "Error starting listening", e)
            _isListening.value = false
        }
    }

    fun pauseListening() {
        isPaused = true
        _isListening.value = false
        speechRecognizer?.stopListening()
    }

    fun resumeListening() {
        if (isPaused) {
            isPaused = false
            startRecognitionInternal()
        }
    }

    fun stopListening() {
        _isContinuousMode.value = false
        isPaused = false
        mainHandler.removeCallbacksAndMessages(null)
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
    override fun onReadyForSpeech(params: Bundle?) {
        _isListening.value = true
    }

    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}

    override fun onEndOfSpeech() {
        _isListening.value = false
    }

    override fun onError(error: Int) {
        _isListening.value = false
        Log.d("FrenchSpeechEngine", "SpeechRecognizer error code: $error")

        // If continuous mode is active and not manually stopped/paused, auto-recover
        if (_isContinuousMode.value && !isPaused) {
            mainHandler.postDelayed({
                if (_isContinuousMode.value && !isPaused) {
                    startRecognitionInternal()
                }
            }, 400)
        }
    }

    override fun onResults(results: Bundle?) {
        _isListening.value = false
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val bestResult = matches?.firstOrNull() ?: ""
        if (bestResult.isNotBlank()) {
            _liveTranscript.value = bestResult
            onSpeechResultListener?.invoke(bestResult)
        }

        // In continuous mode, continue listening for the next phrase
        if (_isContinuousMode.value && !isPaused) {
            mainHandler.postDelayed({
                if (_isContinuousMode.value && !isPaused) {
                    startRecognitionInternal()
                }
            }, 300)
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        matches?.firstOrNull()?.let {
            if (it.isNotBlank()) {
                _liveTranscript.value = it
            }
        }
    }

    override fun onEvent(eventType: Int, params: Bundle?) {}

    fun destroy() {
        stopListening()
        tts?.shutdown()
    }
}
