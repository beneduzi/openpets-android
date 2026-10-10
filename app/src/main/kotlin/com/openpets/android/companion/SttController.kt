package com.openpets.android.companion

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import java.util.Locale

enum class SttState { IDLE, LISTENING, ERROR, UNAVAILABLE }

class SttController(
    context: Context,
    private val onText: (String) -> Unit,
    private val onState: (SttState) -> Unit,
) {
    private val context = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun start() = main.post {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED || !isAvailable()) {
            onState(SttState.UNAVAILABLE)
            return@post
        }
        try {
            val speechRecognizer = recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also {
                it.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: android.os.Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onError(error: Int) = onState(SttState.ERROR)
                    override fun onResults(results: android.os.Bundle?) {
                        results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(onText)
                        onState(SttState.IDLE)
                    }
                    override fun onPartialResults(partialResults: android.os.Bundle?) {
                        partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(onText)
                    }
                    override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
                })
            }.also { recognizer = it }
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            }
            speechRecognizer.startListening(intent)
            onState(SttState.LISTENING)
        } catch (_: Exception) { onState(SttState.UNAVAILABLE) }
    }

    fun stop() = main.post { try { recognizer?.stopListening() } catch (_: Exception) {} }
    fun cancel() = main.post { try { recognizer?.cancel() } catch (_: Exception) {}; onState(SttState.IDLE) }
    fun destroy() = main.post { try { recognizer?.destroy() } catch (_: Exception) {}; recognizer = null }
}
