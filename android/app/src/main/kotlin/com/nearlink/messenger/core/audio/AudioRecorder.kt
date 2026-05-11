package com.nearlink.messenger.core.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 语音录制：AAC + MP4 容器，44.1k 单声道。
 *
 * 用法：start(file) → … → stop() → 返回时长。
 */
@Singleton
class AudioRecorder @Inject constructor(
    @ApplicationContext private val ctx: Context,
) {
    private var recorder: MediaRecorder? = null
    private var startedAtMs: Long = 0
    private var outFile: File? = null

    fun start(target: File) {
        outFile = target
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(ctx) else MediaRecorder()
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioEncodingBitRate(64_000)
        r.setAudioSamplingRate(44_100)
        r.setOutputFile(target.absolutePath)
        r.prepare()
        r.start()
        startedAtMs = System.currentTimeMillis()
        recorder = r
    }

    fun stop(): Long {
        val r = recorder ?: return 0
        return try {
            r.stop(); r.reset(); r.release()
            System.currentTimeMillis() - startedAtMs
        } catch (t: Throwable) { 0 } finally {
            recorder = null
        }
    }

    fun cancel() {
        recorder?.runCatching { stop(); reset(); release() }
        recorder = null
        outFile?.delete()
    }
}
