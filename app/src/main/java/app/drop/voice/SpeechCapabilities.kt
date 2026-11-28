package app.drop.voice

import android.content.Context
import android.os.Build
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.SpeechRecognizer
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/** Finds out what this phone offers for turning speech into text. Call on the main thread. */
class SpeechCapabilities(private val context: Context) {
    /** Never throws: a failing check only rules out the part that failed. */
    suspend fun check(languageTag: String): SpeechSupport {
        val systemAvailable = runCatching { SpeechRecognizer.isRecognitionAvailable(context) }.getOrDefault(false)
        if (Build.VERSION.SDK_INT < 31 || !runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(context) }.getOrDefault(false)) {
            return SpeechSupport(OnDeviceSupport.Unavailable, systemAvailable)
        }
        if (Build.VERSION.SDK_INT < 33) return SpeechSupport(OnDeviceSupport.LanguageUnknown, systemAvailable)
        return try {
            checkLanguage(languageTag, systemAvailable)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // The on-device service misbehaved; the phone's speech service may still be there.
            SpeechSupport(OnDeviceSupport.Unavailable, systemAvailable)
        }
    }

    @RequiresApi(33)
    private suspend fun checkLanguage(languageTag: String, systemAvailable: Boolean): SpeechSupport {
        val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        try {
            val support = withTimeoutOrNull(CHECK_TIMEOUT_MS) {
                suspendCancellableCoroutine<RecognitionSupport?> { cont ->
                    recognizer.checkRecognitionSupport(
                        recognitionIntent(languageTag),
                        ContextCompat.getMainExecutor(context),
                        object : RecognitionSupportCallback {
                            override fun onSupportResult(recognitionSupport: RecognitionSupport) {
                                if (cont.isActive) cont.resume(recognitionSupport)
                            }

                            override fun onError(error: Int) {
                                if (cont.isActive) cont.resume(null)
                            }
                        },
                    )
                }
            } ?: return SpeechSupport(OnDeviceSupport.LanguageUnknown, systemAvailable)
            if (languageMatches(support.installedOnDeviceLanguages, languageTag)) return SpeechSupport(OnDeviceSupport.Ready, systemAvailable)
            val downloadable = languageMatches(support.supportedOnDeviceLanguages, languageTag) ||
                languageMatches(support.pendingOnDeviceLanguages, languageTag)
            return SpeechSupport(OnDeviceSupport.LanguageNotInstalled, systemAvailable, downloadable)
        } finally {
            recognizer.destroy()
        }
    }

    /**
     * Asks Android to download on-device speech for [languageTag] (Android 13+). Returns false
     * when the phone can't; the download itself runs in the system, outside DROP.
     */
    fun requestDownload(languageTag: String): Boolean {
        if (Build.VERSION.SDK_INT < 33) return false
        return runCatching {
            val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            try {
                recognizer.triggerModelDownload(recognitionIntent(languageTag))
            } finally {
                recognizer.destroy()
            }
        }.isSuccess
    }

    companion object {
        private const val CHECK_TIMEOUT_MS = 3000L

        /** "tr-TR" matches "tr-TR", "tr_TR" and plain "tr". */
        fun languageMatches(installed: List<String>, wanted: String): Boolean {
            fun norm(tag: String) = tag.replace('_', '-')
            val base = norm(wanted).substringBefore('-').lowercase(Locale.ROOT)
            return installed.any { norm(it).equals(norm(wanted), ignoreCase = true) || norm(it).substringBefore('-').lowercase(Locale.ROOT) == base }
        }
    }
}
