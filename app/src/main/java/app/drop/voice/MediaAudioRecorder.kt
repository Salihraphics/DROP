package app.drop.voice

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/** Records AAC in an .m4a file, mono 44.1 kHz at 64 kbit/s, for at most 30 minutes. */
class MediaAudioRecorder(private val context: Context) : AudioRecorder {
    private var recorder: MediaRecorder? = null

    override fun start(file: File, maxBytes: Long, onAutoStop: (RecorderStop) -> Unit) {
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioChannels(1)
            r.setAudioSamplingRate(44_100)
            r.setAudioEncodingBitRate(64_000)
            r.setMaxDuration(MAX_DURATION_MS)
            r.setMaxFileSize(maxBytes)
            r.setOutputFile(file.path)
            r.setOnInfoListener { _, what, _ ->
                when (what) {
                    MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED -> onAutoStop(RecorderStop.MaxDuration)
                    MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED -> onAutoStop(RecorderStop.MaxSize)
                }
            }
            r.setOnErrorListener { _, _, _ -> onAutoStop(RecorderStop.Error) }
            r.prepare()
            r.start()
            recorder = r
        } catch (e: Exception) {
            r.release()
            throw e
        }
    }

    override fun stop(): Boolean {
        val r = recorder ?: return false
        recorder = null
        return try {
            r.stop()
            true
        } catch (e: RuntimeException) {
            // Stopping straight after starting throws: nothing usable was recorded.
            false
        } finally {
            r.release()
        }
    }

    override fun cancel() {
        val r = recorder ?: return
        recorder = null
        runCatching { r.stop() }
        r.release()
    }

    override fun level(): Float = runCatching { (recorder?.maxAmplitude ?: 0) / 32767f }.getOrDefault(0f)

    companion object {
        const val MAX_DURATION_MS = 30 * 60 * 1000
    }
}
