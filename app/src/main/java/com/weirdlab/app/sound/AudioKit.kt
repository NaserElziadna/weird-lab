package com.weirdlab.app.sound

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

const val SR = 44100

object AudioKit {

    fun buildTrack(sampleRate: Int, bufferBytes: Int): AudioTrack =
        AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(bufferBytes)
            .build()

    /** Plays PCM and suspends until finished (or the coroutine is cancelled). */
    suspend fun play(samples: ShortArray, sampleRate: Int = SR, onProgress: (Float) -> Unit = {}) =
        withContext(Dispatchers.IO) {
            if (samples.isEmpty()) return@withContext
            val minBuf = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val track = buildTrack(sampleRate, max(minBuf, 8192))
            try {
                track.play()
                var i = 0
                val chunk = 1024
                while (i < samples.size && isActive) {
                    val n = min(chunk, samples.size - i)
                    track.write(samples, i, n)
                    i += n
                    val heard = track.playbackHeadPosition.coerceAtMost(samples.size)
                    onProgress(heard.toFloat() / samples.size)
                }
                // A stream track only starts once its buffer has filled: pad short sounds with silence.
                if (isActive) {
                    val pad = ShortArray(track.bufferSizeInFrames)
                    track.write(pad, 0, pad.size)
                }
                var guard = 0
                while (isActive && track.playbackHeadPosition < samples.size && guard < 300) {
                    delay(10)
                    onProgress(track.playbackHeadPosition.coerceAtMost(samples.size).toFloat() / samples.size)
                    guard++
                }
                onProgress(1f)
            } finally {
                try { track.stop() } catch (_: Exception) {}
                track.release()
            }
        }

    /**
     * Records up to [maxSeconds]. [onChunk] receives each fresh block plus the total samples so far;
     * return true from [shouldStop] to end early.
     */
    @SuppressLint("MissingPermission")
    suspend fun record(
        maxSeconds: Float,
        sampleRate: Int = SR,
        source: Int = MediaRecorder.AudioSource.MIC,
        shouldStop: () -> Boolean = { false },
        onChunk: (ShortArray, Int, Int) -> Unit = { _, _, _ -> }
    ): ShortArray = withContext(Dispatchers.IO) {
        val minBuf = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = AudioRecord(source, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, max(minBuf, 16384))
        val total = (maxSeconds * sampleRate).toInt()
        val out = ShortArray(total)
        var pos = 0
        val buf = ShortArray(1024)
        try {
            rec.startRecording()
            while (isActive && pos < total && !shouldStop()) {
                val n = rec.read(buf, 0, min(buf.size, total - pos))
                if (n > 0) {
                    System.arraycopy(buf, 0, out, pos, n)
                    pos += n
                    onChunk(buf, n, pos)
                } else if (n < 0) break
            }
        } finally {
            try { rec.stop() } catch (_: Exception) {}
            rec.release()
        }
        out.copyOf(pos)
    }

    /** Streams microphone blocks of [frameSize] samples into [onFrame] until cancelled. */
    @SuppressLint("MissingPermission")
    suspend fun listen(
        frameSize: Int,
        sampleRate: Int = SR,
        source: Int = MediaRecorder.AudioSource.MIC,
        onFrame: (ShortArray) -> Unit
    ) = withContext(Dispatchers.IO) {
        val minBuf = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = AudioRecord(source, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, max(minBuf, frameSize * 8))
        val frame = ShortArray(frameSize)
        try {
            rec.startRecording()
            while (isActive) {
                var got = 0
                while (got < frameSize && isActive) {
                    val n = rec.read(frame, got, frameSize - got)
                    if (n <= 0) break
                    got += n
                }
                if (got == frameSize) onFrame(frame)
            }
        } finally {
            try { rec.stop() } catch (_: Exception) {}
            rec.release()
        }
    }

    /** Least-processed mic source the phone offers (no noise suppression / AGC). */
    fun rawSource(ctx: Context): Int {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return if (am.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true")
            MediaRecorder.AudioSource.UNPROCESSED
        else MediaRecorder.AudioSource.VOICE_RECOGNITION
    }

    // ---------- little DSP helpers ----------

    fun toFloat(x: ShortArray): FloatArray = FloatArray(x.size) { x[it] / 32768f }

    fun toPcm(x: FloatArray, normalize: Boolean = true): ShortArray {
        var peak = 1e-6f
        if (normalize) for (v in x) peak = max(peak, abs(v))
        val g = if (normalize) 0.9f / peak else 1f
        return ShortArray(x.size) { (x[it] * g * 32767f).toInt().coerceIn(-32767, 32767).toShort() }
    }

    fun rms(x: ShortArray, n: Int = x.size): Float {
        if (n == 0) return 0f
        var s = 0.0
        for (i in 0 until n) s += x[i].toDouble() * x[i]
        return (sqrt(s / n) / 32768.0).toFloat()
    }

    /** Power of a single frequency (Goertzel algorithm). */
    fun goertzel(x: FloatArray, freq: Double, sampleRate: Int): Double {
        val k = 2.0 * cos(2.0 * PI * freq / sampleRate)
        var s1 = 0.0
        var s2 = 0.0
        for (v in x) {
            val s0 = v + k * s1 - s2
            s2 = s1; s1 = s0
        }
        return s1 * s1 + s2 * s2 - k * s1 * s2
    }

    fun hann(n: Int) = FloatArray(n) { (0.5 - 0.5 * cos(2.0 * PI * it / (n - 1))).toFloat() }

    /** Pure tone with soft fade in/out (avoids clicks). */
    fun tone(freq: Double, ms: Int, sampleRate: Int = SR, amp: Float = 0.8f, fadeMs: Int = 4): FloatArray {
        val n = sampleRate * ms / 1000
        val fade = max(1, sampleRate * fadeMs / 1000)
        return FloatArray(n) { i ->
            val env = when {
                i < fade -> i / fade.toFloat()
                i > n - fade -> (n - i) / fade.toFloat()
                else -> 1f
            }
            (sin(2.0 * PI * freq * i / sampleRate) * amp * env).toFloat()
        }
    }

    /** Smoothed amplitude envelope in blocks, for waveform drawing. */
    fun envelope(x: ShortArray, buckets: Int): FloatArray {
        if (x.isEmpty()) return FloatArray(buckets)
        val per = max(1, x.size / buckets)
        return FloatArray(buckets) { b ->
            var m = 0
            val start = b * per
            val end = min(x.size, start + per)
            for (i in start until end) m = max(m, abs(x[i].toInt()))
            m / 32768f
        }
    }
}
