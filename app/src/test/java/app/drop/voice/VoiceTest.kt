package app.drop.voice

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.drop.attach.AttachmentRepository
import app.drop.attach.AttachmentStore
import app.drop.data.AttachmentKind
import app.drop.data.DropDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** Voice Dump's rules: honest transcription, recordings that are kept or cleanly dropped, safe saves. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class VoiceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val main = StandardTestDispatcher()
    private lateinit var db: DropDatabase
    private lateinit var repository: AttachmentRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(main)
        db = Room.inMemoryDatabaseBuilder(context, DropDatabase::class.java).allowMainThreadQueries().build()
        repository = AttachmentRepository(db, AttachmentStore(File(context.filesDir, "voice-test")))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    private class FakeSpeech : SpeechEngine {
        var listener: SpeechListener? = null
        var starts = 0
        var released = false

        override fun start(languageTag: String, listener: SpeechListener) {
            this.listener = listener
            starts++
        }

        override fun stop() = Unit

        override fun cancel() = Unit

        override fun release() {
            released = true
        }
    }

    private class FakeRecorder(private val ok: Boolean = true) : AudioRecorder {
        var file: File? = null
        var onAutoStop: ((RecorderStop) -> Unit)? = null

        override fun start(file: File, maxBytes: Long, onAutoStop: (RecorderStop) -> Unit) {
            this.file = file
            this.onAutoStop = onAutoStop
            file.writeBytes(ByteArray(4096) { 1 })
        }

        override fun stop() = ok

        override fun cancel() = Unit

        override fun level() = 0.5f
    }

    private var now = 1_000L

    private fun vm(speech: FakeSpeech = FakeSpeech(), recorder: FakeRecorder = FakeRecorder(), handle: SavedStateHandle = SavedStateHandle()) =
        VoiceViewModel(handle, db.noteDao(), repository, { speech }, { recorder }, clock = { now }, stopGraceMs = 2500)

    @Test
    fun transcriptionGoesOnOnlyWhereItCanRunAndIsNeverFaked() {
        val onDevice = SpeechSupport(OnDeviceSupport.Ready, systemAvailable = true)
        assertEquals(SpeechPath.OnDevice, transcriptionPath(onDevice, allowSystem = false))
        assertEquals(SpeechPath.OnDevice, transcriptionPath(SpeechSupport(OnDeviceSupport.LanguageUnknown, false), false))
        // The phone's speech service may send audio away, so only with the person's permission.
        val none = SpeechSupport(OnDeviceSupport.Unavailable, systemAvailable = true)
        assertNull(transcriptionPath(none, allowSystem = false))
        assertEquals(SpeechPath.System, transcriptionPath(none, allowSystem = true))
        assertNull(transcriptionPath(SpeechSupport(OnDeviceSupport.LanguageNotInstalled, false), true))

        val speech = FakeSpeech()
        val v = vm(speech)
        v.start(VoiceMode.Transcribe, "tr-TR", path = null)
        assertEquals(VoiceState.Idle, v.state)
        assertEquals(VoiceMessage.LanguageUnavailable, v.message)
        assertEquals(0, speech.starts)
    }

    @Test
    fun languagesMatchByTagOrLanguage() {
        assertTrue(SpeechCapabilities.languageMatches(listOf("tr-TR"), "tr-TR"))
        assertTrue(SpeechCapabilities.languageMatches(listOf("tr"), "tr-TR"))
        assertTrue(SpeechCapabilities.languageMatches(listOf("en_US"), "en-US"))
        assertTrue(SpeechCapabilities.languageMatches(listOf("EN-gb"), "en-US"))
        assertFalse(SpeechCapabilities.languageMatches(listOf("de-DE", "en-US"), "tr-TR"))
        assertFalse(SpeechCapabilities.languageMatches(emptyList(), "tr-TR"))
    }

    @Test
    fun dictationKeepsListeningThroughPausesAndJoinsWhatWasSaid() {
        val speech = FakeSpeech()
        val v = vm(speech)
        v.start(VoiceMode.Transcribe, "en-US", SpeechPath.OnDevice)
        val l = speech.listener!!
        l.onPartial("buy")
        assertEquals("buy", (v.state as VoiceState.Listening).partial)
        l.onSegment("Buy oat milk ")
        // Silence ends a stretch; recognition starts again by itself.
        repeat(5) { l.onError(SpeechError.NoSpeech) }
        l.onSegment(" and lemons")
        assertEquals("Buy oat milk and lemons", (v.state as VoiceState.Listening).text)
        assertTrue(speech.starts >= 7)
        v.stop()
        l.onPartial("for the cake")
        main.scheduler.advanceTimeBy(2600)
        main.scheduler.runCurrent()
        assertEquals(VoiceState.ReviewText("Buy oat milk and lemons for the cake"), v.state)
        assertNull(v.message)
        assertTrue(speech.released)
    }

    @Test
    fun aLongSilenceEndsListeningAndSaysNothingWasHeard() {
        val speech = FakeSpeech()
        val v = vm(speech)
        v.start(VoiceMode.Transcribe, "en-US", SpeechPath.OnDevice)
        repeat(VoiceViewModel.MAX_SILENT_RESTARTS + 1) { speech.listener!!.onError(SpeechError.NoSpeech) }
        assertEquals(VoiceState.Idle, v.state)
        assertEquals(VoiceMessage.NothingHeard, v.message)
    }

    @Test
    fun wordsHeardBeforeAnErrorAreKeptForReview() {
        val speech = FakeSpeech()
        val v = vm(speech)
        v.start(VoiceMode.Transcribe, "en-US", SpeechPath.System)
        speech.listener!!.onSegment("Call the plumber")
        speech.listener!!.onPartial("on Tuesday")
        speech.listener!!.onError(SpeechError.Network)
        assertEquals(VoiceState.ReviewText("Call the plumber on Tuesday"), v.state)
        assertEquals(VoiceMessage.ConnectionLost, v.message)
    }

    @Test
    fun aRecordingIsKeptForReviewAndSavedAsAVoiceNote() {
        val recorder = FakeRecorder()
        val v = vm(recorder = recorder)
        v.start(VoiceMode.Record, "en-US", null)
        assertTrue(v.state is VoiceState.Recording)
        now += 65_000
        v.stop()
        val review = v.state as VoiceState.ReviewAudio
        assertEquals(65_000L, review.durationMs)
        assertEquals(4096L, review.sizeBytes)
        v.save("Voice note")
        main.scheduler.runCurrent()
        waitUntil { v.state is VoiceState.Saved }
        val noteId = (v.state as VoiceState.Saved).noteId
        val note = runBlocking { db.noteDao().getById(noteId) }!!
        assertEquals("Voice note", note.title)
        val audio = runBlocking { db.attachmentDao().forNotes(listOf(noteId)) }.single()
        assertEquals(AttachmentKind.Audio, audio.attachmentKind)
        assertEquals(65_000L, audio.durationMs)
        assertTrue(repository.store.file(audio.fileName).exists())
    }

    @Test
    fun recordingsThatFailOrAreTooShortLeaveNothingBehind() {
        val short = FakeRecorder(ok = false)
        val v = vm(recorder = short)
        v.start(VoiceMode.Record, "en-US", null)
        v.stop()
        assertEquals(VoiceState.Idle, v.state)
        assertEquals(VoiceMessage.TooShort, v.message)
        assertFalse(short.file!!.exists())

        val broken = FakeRecorder()
        val w = vm(recorder = broken)
        w.start(VoiceMode.Record, "en-US", null)
        broken.onAutoStop!!(RecorderStop.Error)
        main.scheduler.runCurrent()
        assertEquals(VoiceState.Idle, w.state)
        assertEquals(VoiceMessage.RecordingFailed, w.message)
        assertFalse(broken.file!!.exists())

        val long = FakeRecorder()
        val x = vm(recorder = long)
        x.start(VoiceMode.Record, "en-US", null)
        long.onAutoStop!!(RecorderStop.MaxDuration)
        main.scheduler.runCurrent()
        assertTrue(x.state is VoiceState.ReviewAudio)
        assertEquals(VoiceMessage.MaxLengthReached, x.message)
    }

    @Test
    fun savingDictationTwiceMakesOneNote() {
        val handle = SavedStateHandle()
        val speech = FakeSpeech()
        val v = vm(speech, handle = handle)
        v.start(VoiceMode.Transcribe, "en-US", SpeechPath.OnDevice)
        speech.listener!!.onSegment("Pick up the keys")
        v.stop()
        main.scheduler.advanceTimeBy(2600)
        main.scheduler.runCurrent()
        v.onReviewTextChange("Pick up the spare keys")
        val uuid = "fixed-uuid"
        handle[VoiceViewModel.KEY_UUID] = uuid
        v.save("Voice note")
        main.scheduler.runCurrent()
        waitUntil { v.state is VoiceState.Saved }
        // A second screen with the same pending id (after a crash, say) finds the same note.
        val again = vm(FakeSpeech(), handle = SavedStateHandle(mapOf(VoiceViewModel.KEY_UUID to uuid)))
        again.start(VoiceMode.Transcribe, "en-US", SpeechPath.OnDevice)
        val s2 = (again.state as VoiceState.Listening)
        assertEquals("", s2.text)
        val all = runBlocking { db.noteDao().allNotes() }
        assertEquals(1, all.size)
        assertEquals("Pick up the spare keys", all.single().body)
    }

    private fun waitUntil(condition: () -> Boolean) {
        val end = System.currentTimeMillis() + 10_000
        while (!condition()) {
            check(System.currentTimeMillis() < end) { "timed out" }
            main.scheduler.runCurrent()
            Thread.sleep(20)
        }
    }
}
