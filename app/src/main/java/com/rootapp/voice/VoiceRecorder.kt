package com.rootapp.voice

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File

/** Records mic audio to an m4a file for Groq Whisper transcription. */
class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var file: File? = null

    val isRecording: Boolean get() = recorder != null

    /** Peak amplitude since last call (0..32767); used for silence detection. */
    fun amplitude(): Int = runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0)

    fun start(): Boolean {
        if (recorder != null) return false
        val out = File(context.cacheDir, "voice_${System.nanoTime()}.m4a")
        val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context)
        else @Suppress("DEPRECATION") MediaRecorder()
        return runCatching {
            rec.setAudioSource(MediaRecorder.AudioSource.MIC)
            rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            rec.setAudioEncodingBitRate(128000)
            rec.setAudioSamplingRate(44100)
            rec.setOutputFile(out.absolutePath)
            rec.prepare()
            rec.start()
            recorder = rec
            file = out
            true
        }.getOrElse { Log.w("VoiceRecorder", "start failed: ${it.message}"); runCatching { rec.release() }; false }
    }

    /** Stops and returns the recorded file (or null on failure). */
    fun stop(): File? {
        val rec = recorder ?: return null
        recorder = null
        return runCatching {
            rec.stop(); rec.release(); file
        }.getOrElse { Log.w("VoiceRecorder", "stop failed: ${it.message}"); runCatching { rec.release() }; null }
    }
}
