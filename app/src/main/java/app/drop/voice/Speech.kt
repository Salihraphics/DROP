package app.drop.voice

import android.content.Intent
import android.speech.RecognizerIntent
import java.io.File

/** Voice Dump either turns speech into text or keeps the audio. They are separate on purpose. */
enum class VoiceMode { Transcribe, Record }

/** Where speech is turned into text. */
enum class SpeechPath { OnDevice, System }

enum class SpeechError { NoSpeech, LanguageUnavailable, Busy, Permission, Network, Other }

/** What Voice Dump tells the person after something didn't go to plan. */
enum class VoiceMessage {
    NothingHeard, LanguageUnavailable, MicUnavailable, PermissionDenied, StorageFull,
    TooShort, RecordingFailed, MaxLengthReached, ConnectionLost, SaveFailed,
}

enum class RecorderStop { MaxDuration, MaxSize, Error }

/** Whether the phone's on-device recognition can transcribe the chosen language. */
enum class OnDeviceSupport { Ready, LanguageNotInstalled, LanguageUnknown, Unavailable }

/**
 * What this phone offers for transcription. [downloadable] is new since 0.10.0: the on-device
 * language isn't installed but Android 13+ can download it, so Voice Dump offers to.
 */
data class SpeechSupport(val onDevice: OnDeviceSupport, val systemAvailable: Boolean, val downloadable: Boolean = false)

/**
 * On-device when it can work; the phone's own speech service only if the person allowed it,
 * because it may send audio to its provider; otherwise none, and Voice Dump says so.
 */
fun transcriptionPath(support: SpeechSupport, allowSystem: Boolean): SpeechPath? = when {
    support.onDevice == OnDeviceSupport.Ready || support.onDevice == OnDeviceSupport.LanguageUnknown -> SpeechPath.OnDevice
    allowSystem && support.systemAvailable -> SpeechPath.System
    else -> null
}

fun recognitionIntent(languageTag: String): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
    .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
    .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
    .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)

interface SpeechListener {
    fun onReady()

    fun onLevel(level: Float)

    fun onPartial(text: String)

    /** A finished stretch of speech; recognition then stops until started again. */
    fun onSegment(text: String)

    fun onError(error: SpeechError)
}

interface SpeechEngine {
    fun start(languageTag: String, listener: SpeechListener)

    fun stop()

    fun cancel()

    fun release()
}

interface AudioRecorder {
    fun start(file: File, maxBytes: Long, onAutoStop: (RecorderStop) -> Unit)

    /** False when nothing usable was recorded. */
    fun stop(): Boolean

    fun cancel()

    /** 0 to 1. */
    fun level(): Float
}
