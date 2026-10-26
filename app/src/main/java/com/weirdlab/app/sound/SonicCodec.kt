package com.weirdlab.app.sound

import kotlin.math.max

/**
 * Text-over-sound modem.
 *
 * Every byte becomes two 4-bit symbols. Each symbol is one of 16 tones. To make repeated symbols
 * distinguishable we alternate between two banks of 16 tones (A, B, A, B...). A START tone and an
 * END tone frame the message, and the last byte is a checksum.
 *
 * Ultrasonic mode uses 16.7–19.7 kHz (inaudible to most adults); audible mode shifts everything
 * down by 14 kHz so you can hear the robot chirps.
 */
class SonicCodec(audible: Boolean) {
    private val shift = if (audible) -14000.0 else 0.0
    private val step = 80.0

    val startHz = 16700.0 + shift
    val endHz = 19700.0 + shift
    val bankA = DoubleArray(16) { 17000.0 + shift + it * step }
    val bankB = DoubleArray(16) { 18300.0 + shift + it * step }

    /** All tones in display order: START, A0..A15, B0..B15, END (34 tones). */
    val all: DoubleArray = doubleArrayOf(startHz) + bankA + bankB + doubleArrayOf(endHz)

    companion object {
        const val SYMBOL_MS = 100
        const val MARK_MS = 300
        const val MAX_BYTES = 48
        const val START = 0
        const val END = 33
        fun isA(i: Int) = i in 1..16
        fun isB(i: Int) = i in 17..32
    }

    fun encode(text: String): ShortArray {
        val payload = text.toByteArray(Charsets.UTF_8).take(MAX_BYTES).toByteArray()
        var sum = 0
        payload.forEach { sum += it.toInt() and 0xFF }
        val bytes = payload + byteArrayOf((sum and 0xFF).toByte())

        val pieces = ArrayList<FloatArray>()
        pieces += AudioKit.tone(startHz, MARK_MS)
        var k = 0
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            for (nib in intArrayOf(v shr 4, v and 0x0F)) {
                val f = if (k % 2 == 0) bankA[nib] else bankB[nib]
                pieces += AudioKit.tone(f, SYMBOL_MS)
                k++
            }
        }
        pieces += AudioKit.tone(endHz, MARK_MS)
        pieces += FloatArray(SR / 10) // trailing silence
        val total = pieces.sumOf { it.size }
        val out = FloatArray(total)
        var pos = 0
        for (p in pieces) { p.copyInto(out, pos); pos += p.size }
        return AudioKit.toPcm(out, normalize = false)
    }

    fun durationSeconds(text: String): Float {
        val n = text.toByteArray(Charsets.UTF_8).size.coerceAtMost(MAX_BYTES) + 1
        return (2 * MARK_MS + n * 2 * SYMBOL_MS + 100) / 1000f
    }
}

data class SonicMessage(val text: String, val checksumOk: Boolean, val timeMillis: Long)

/** Frame-by-frame decoder. Feed it one analysis frame at a time with [processFrame]. */
class SonicDecoder(private val codec: SonicCodec, private val onMessage: (SonicMessage) -> Unit) {
    private val frameSize = 1024
    private val window = AudioKit.hann(frameSize)
    private val buf = FloatArray(frameSize)

    /** Latest normalized spectrum for the UI (0..1 per tone). */
    val spectrum = FloatArray(codec.all.size)
    var receiving = false; private set
    var symbolsSoFar = 0; private set

    private var lastTone = -2
    private var run = 0
    private var expectA = true
    private val nibbles = ArrayList<Int>()

    fun processFrame(pcm: ShortArray) {
        for (i in 0 until frameSize) buf[i] = pcm[i] / 32768f * window[i]
        var best = -1
        var bestP = 0.0
        val powers = DoubleArray(codec.all.size)
        for (t in codec.all.indices) {
            val p = AudioKit.goertzel(buf, codec.all[t], SR)
            powers[t] = p
            if (p > bestP) { bestP = p; best = t }
        }
        val sorted = powers.sorted()
        val median = max(sorted[sorted.size / 2], 1e-9)
        val maxP = max(bestP, 1e-9)
        for (t in powers.indices) {
            spectrum[t] = (kotlin.math.log10(powers[t] / median + 1.0) / kotlin.math.log10(maxP / median + 1.0 + 30.0)).toFloat().coerceIn(0f, 1f)
        }
        // Must clearly stand out from the other tones and from silence
        val tone = if (bestP > median * 25 && bestP > 2e-3) best else -1
        feed(tone)
    }

    private fun feed(tone: Int) {
        if (tone == lastTone) run++ else { lastTone = tone; run = 1 }
        if (run != 2) return // act once, when a tone has been stable for two frames
        when {
            tone == SonicCodec.START -> {
                receiving = true; expectA = true; nibbles.clear(); symbolsSoFar = 0
            }
            !receiving || tone < 0 -> {}
            tone == SonicCodec.END -> finish()
            SonicCodec.isA(tone) && expectA -> { nibbles += tone - 1; expectA = false; symbolsSoFar++ }
            SonicCodec.isB(tone) && !expectA -> { nibbles += tone - 17; expectA = true; symbolsSoFar++ }
        }
    }

    private fun finish() {
        receiving = false
        val count = nibbles.size / 2
        if (count < 1) return
        val bytes = ByteArray(count) { ((nibbles[2 * it] shl 4) or nibbles[2 * it + 1]).toByte() }
        val payload = bytes.copyOf(count - 1)
        var sum = 0
        payload.forEach { sum += it.toInt() and 0xFF }
        val ok = count >= 2 && (sum and 0xFF) == (bytes.last().toInt() and 0xFF)
        val text = String(if (count >= 2) payload else bytes, Charsets.UTF_8)
        onMessage(SonicMessage(text, ok, System.currentTimeMillis()))
    }
}
