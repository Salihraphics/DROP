package app.drop.voice

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.drop.DropApplication
import app.drop.attach.AttachmentRepository
import app.drop.attach.AttachmentStore
import app.drop.attach.StagedFile
import app.drop.data.AttachmentKind
import app.drop.data.Note
import app.drop.data.NoteDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

sealed interface VoiceState {
    data object Idle : VoiceState

    /** [text] is what has been settled; [partial] what is still being heard. */
    data class Listening(val startedAt: Long, val text: String, val partial: String) : VoiceState

    data class Recording(val startedAt: Long) : VoiceState

    data class ReviewText(val text: String) : VoiceState

    data class ReviewAudio(val partName: String, val durationMs: Long, val sizeBytes: Long) : VoiceState

    data object Saving : VoiceState

    data class Saved(val noteId: Long) : VoiceState
}

/**
 * Voice Dump: dictate a note, or record a voice note. Transcription only ever produces what the
 * recogniser heard; when it can't run, the person is told why and can record audio instead.
 */
class VoiceViewModel(
    private val handle: SavedStateHandle,
    private val notes: NoteDao,
    private val attachments: AttachmentRepository,
    private val speechFactory: (SpeechPath) -> SpeechEngine,
    private val recorderFactory: () -> AudioRecorder,
    private val clock: () -> Long = System::currentTimeMillis,
    private val maxRecordingBytes: Long = MAX_RECORDING_BYTES,
    private val stopGraceMs: Long = STOP_GRACE_MS,
) : ViewModel() {
    private val store: AttachmentStore get() = attachments.store

    var state by mutableStateOf<VoiceState>(VoiceState.Idle)
        private set
    var message by mutableStateOf<VoiceMessage?>(null)
        private set
    var level by mutableFloatStateOf(0f)
        private set

    private var engine: SpeechEngine? = null
    private var recorder: AudioRecorder? = null
    private var recordingPart: File? = null
    private var stopping = false
    private var silentRestarts = 0
    private var stopTimeout: Job? = null
    private var levelPoll: Job? = null
    private var language = "en-US"

    fun clearMessage() {
        message = null
    }

    fun onPermissionDenied() {
        message = VoiceMessage.PermissionDenied
    }

    /** [path] null means transcription can't run here; that is reported, never faked. */
    fun start(mode: VoiceMode, languageTag: String, path: SpeechPath?) {
        if (state != VoiceState.Idle) return
        message = null
        when (mode) {
            VoiceMode.Transcribe -> if (path == null) message = VoiceMessage.LanguageUnavailable else startListening(languageTag, path)
            VoiceMode.Record -> startRecording()
        }
    }

    private fun startListening(languageTag: String, path: SpeechPath) {
        language = languageTag
        stopping = false
        silentRestarts = 0
        try {
            engine = speechFactory(path)
            state = VoiceState.Listening(clock(), "", "")
            listen()
        } catch (e: Exception) {
            engine = null
            message = VoiceMessage.MicUnavailable
        }
    }

    private fun listen() {
        val e = engine ?: return
        try {
            e.start(language, listener)
        } catch (ex: Exception) {
            finishListening(VoiceMessage.MicUnavailable)
        }
    }

    private val listener = object : SpeechListener {
        override fun onReady() = Unit

        override fun onLevel(level: Float) {
            this@VoiceViewModel.level = level
        }

        override fun onPartial(text: String) {
            val s = state as? VoiceState.Listening ?: return
            state = s.copy(partial = text)
        }

        override fun onSegment(text: String) {
            val s = state as? VoiceState.Listening ?: return
            state = s.copy(text = join(s.text, text), partial = "")
            if (text.isNotBlank()) silentRestarts = 0
            // Recognisers stop after each stretch of speech; keep going until the person stops.
            if (stopping) finishListening(null) else listen()
        }

        override fun onError(error: SpeechError) {
            if (state !is VoiceState.Listening) return
            if (stopping) {
                finishListening(null)
                return
            }
            if (error == SpeechError.NoSpeech && ++silentRestarts <= MAX_SILENT_RESTARTS) {
                listen()
                return
            }
            finishListening(
                when (error) {
                    SpeechError.NoSpeech -> null
                    SpeechError.LanguageUnavailable -> VoiceMessage.LanguageUnavailable
                    SpeechError.Network -> VoiceMessage.ConnectionLost
                    SpeechError.Permission -> VoiceMessage.PermissionDenied
                    SpeechError.Busy, SpeechError.Other -> VoiceMessage.MicUnavailable
                },
            )
        }
    }

    /** Ends listening; whatever was heard is kept for review, even after an error. */
    private fun finishListening(problem: VoiceMessage?) {
        val s = state as? VoiceState.Listening ?: return
        stopTimeout?.cancel()
        engine?.release()
        engine = null
        level = 0f
        val text = join(s.text, s.partial).trim()
        state = if (text.isEmpty()) VoiceState.Idle else VoiceState.ReviewText(text)
        message = problem ?: if (text.isEmpty()) VoiceMessage.NothingHeard else null
    }

    private fun startRecording() {
        val free = store.dir.usableSpace - AttachmentStore.RESERVE_BYTES
        if (free < MIN_FREE_BYTES) {
            message = VoiceMessage.StorageFull
            return
        }
        val part = store.newPart("m4a")
        val r = recorderFactory()
        try {
            r.start(part, minOf(maxRecordingBytes, free)) { reason -> viewModelScope.launch { onAutoStop(reason) } }
            recorder = r
            recordingPart = part
            state = VoiceState.Recording(clock())
            levelPoll = viewModelScope.launch {
                while (isActive) {
                    level = r.level()
                    delay(LEVEL_POLL_MS)
                }
            }
        } catch (e: Exception) {
            part.delete()
            message = VoiceMessage.MicUnavailable
        }
    }

    private fun onAutoStop(reason: RecorderStop) {
        when (reason) {
            RecorderStop.Error -> cancelRecording(VoiceMessage.RecordingFailed)
            RecorderStop.MaxDuration, RecorderStop.MaxSize -> {
                stopRecording()
                message = if (reason == RecorderStop.MaxSize) VoiceMessage.StorageFull else VoiceMessage.MaxLengthReached
            }
        }
    }

    private fun stopRecording() {
        val s = state as? VoiceState.Recording ?: return
        val r = recorder ?: return
        val part = recordingPart ?: return
        levelPoll?.cancel()
        level = 0f
        recorder = null
        recordingPart = null
        val ok = r.stop()
        val size = part.length()
        if (!ok || size == 0L) {
            part.delete()
            state = VoiceState.Idle
            message = VoiceMessage.TooShort
        } else {
            state = VoiceState.ReviewAudio(part.name, clock() - s.startedAt, size)
        }
    }

    private fun cancelRecording(problem: VoiceMessage?) {
        levelPoll?.cancel()
        level = 0f
        recorder?.cancel()
        recorder = null
        recordingPart?.delete()
        recordingPart = null
        state = VoiceState.Idle
        message = problem
    }

    /** Stop and keep: listening gets a moment to deliver the last words. */
    fun stop() {
        when (state) {
            is VoiceState.Listening -> {
                if (stopping) return
                stopping = true
                engine?.stop()
                stopTimeout = viewModelScope.launch {
                    delay(stopGraceMs)
                    finishListening(null)
                }
            }
            is VoiceState.Recording -> stopRecording()
            else -> Unit
        }
    }

    /** Stop and throw away. */
    fun cancel() {
        when (state) {
            is VoiceState.Listening -> {
                stopTimeout?.cancel()
                engine?.cancel()
                engine?.release()
                engine = null
                level = 0f
                state = VoiceState.Idle
            }
            is VoiceState.Recording -> cancelRecording(null)
            else -> Unit
        }
    }

    /** Leaving the app keeps what was captured, ready to review. */
    fun onBackground() = stop()

    fun onReviewTextChange(text: String) {
        if (state is VoiceState.ReviewText) state = VoiceState.ReviewText(text)
    }

    fun discard() {
        when (val s = state) {
            is VoiceState.ReviewAudio -> store.discard(s.partName)
            is VoiceState.ReviewText -> Unit
            else -> return
        }
        handle.remove<String>(KEY_UUID)
        state = VoiceState.Idle
    }

    /** Saves the dictated text as a note, or the recording as a voice note titled [voiceTitle]. */
    fun save(voiceTitle: String) {
        val review = state
        if (review !is VoiceState.ReviewText && review !is VoiceState.ReviewAudio) return
        if (review is VoiceState.ReviewText && review.text.isBlank()) return
        // The same id on a retry, so a save that half-worked can't make two notes.
        val uuid = handle.get<String>(KEY_UUID) ?: UUID.randomUUID().toString().also { handle[KEY_UUID] = it }
        state = VoiceState.Saving
        viewModelScope.launch {
            try {
                val id = withContext(NonCancellable + Dispatchers.IO) {
                    when (review) {
                        is VoiceState.ReviewText -> notes.getByUuid(uuid)?.id ?: run {
                            val now = clock()
                            notes.insert(Note(uuid = uuid, title = "", body = review.text.trim(), createdAt = now, updatedAt = now).withDerivedFields())
                        }
                        is VoiceState.ReviewAudio -> notes.getByUuid(uuid)?.id ?: attachments.saveAsNewNote(
                            uuid,
                            voiceTitle,
                            "",
                            StagedFile(review.partName, AttachmentKind.Audio, "audio/mp4", review.sizeBytes, "$voiceTitle.m4a", null),
                            review.durationMs,
                        )
                        else -> error("unreachable")
                    }
                }
                handle.remove<String>(KEY_UUID)
                state = VoiceState.Saved(id)
            } catch (e: Exception) {
                state = review
                message = VoiceMessage.SaveFailed
            }
        }
    }

    override fun onCleared() {
        engine?.release()
        recorder?.cancel()
        recordingPart?.delete()
        (state as? VoiceState.ReviewAudio)?.let { store.discard(it.partName) }
    }

    companion object {
        const val KEY_UUID = "voiceItemUuid"
        const val MAX_SILENT_RESTARTS = 20
        const val MAX_RECORDING_BYTES = 200L * 1024 * 1024
        const val STOP_GRACE_MS = 2500L
        private const val MIN_FREE_BYTES = 2L * 1024 * 1024
        private const val LEVEL_POLL_MS = 100L

        /** Joins settled text and a new stretch with one space. */
        fun join(a: String, b: String): String = when {
            b.isBlank() -> a
            a.isBlank() -> b.trim()
            else -> a.trimEnd() + " " + b.trim()
        }

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as DropApplication
                VoiceViewModel(
                    createSavedStateHandle(),
                    app.database.noteDao(),
                    app.attachments,
                    speechFactory = { path -> AndroidSpeechEngine(app, path) },
                    recorderFactory = { MediaAudioRecorder(app) },
                )
            }
        }
    }
}
