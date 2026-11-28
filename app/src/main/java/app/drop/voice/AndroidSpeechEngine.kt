package app.drop.voice

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer

/** Android's SpeechRecognizer, on the phone or through the phone's speech service. Main thread only. */
class AndroidSpeechEngine(private val context: Context, private val path: SpeechPath) : SpeechEngine {
    private var recognizer: SpeechRecognizer? = null

    private fun recognizer(): SpeechRecognizer = recognizer ?: when {
        path == SpeechPath.OnDevice && Build.VERSION.SDK_INT >= 31 -> SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        path == SpeechPath.System -> SpeechRecognizer.createSpeechRecognizer(context)
        else -> throw IllegalStateException("on-device recognition needs Android 12")
    }.also { recognizer = it }

    override fun start(languageTag: String, listener: SpeechListener) {
        val r = recognizer()
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = listener.onReady()

            override fun onBeginningOfSpeech() = Unit

            override fun onRmsChanged(rmsdB: Float) = listener.onLevel(((2f + rmsdB) / 12f).coerceIn(0f, 1f))

            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() = Unit

            override fun onError(error: Int) = listener.onError(mapError(error))

            override fun onResults(results: Bundle?) = listener.onSegment(firstResult(results))

            override fun onPartialResults(partialResults: Bundle?) = listener.onPartial(firstResult(partialResults))

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        r.startListening(recognitionIntent(languageTag))
    }

    override fun stop() {
        recognizer?.stopListening()
    }

    override fun cancel() {
        recognizer?.cancel()
    }

    override fun release() {
        recognizer?.destroy()
        recognizer = null
    }

    internal fun firstResult(bundle: Bundle?): String = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()

    companion object {
        private const val ERROR_LANGUAGE_NOT_SUPPORTED = 12
        private const val ERROR_LANGUAGE_UNAVAILABLE = 13

        fun mapError(code: Int): SpeechError = when (code) {
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_SERVER -> SpeechError.Network
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT, SpeechRecognizer.ERROR_NO_MATCH -> SpeechError.NoSpeech
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> SpeechError.Busy
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> SpeechError.Permission
            ERROR_LANGUAGE_NOT_SUPPORTED, ERROR_LANGUAGE_UNAVAILABLE -> SpeechError.LanguageUnavailable
            else -> SpeechError.Other
        }
    }
}
