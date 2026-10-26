package com.weirdlab.app.inspect

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sin

/** Reusable radix-2 FFT giving a magnitude spectrum in dB for blocks of [n] samples. */
class Spectrum(val n: Int) {
    private val re = FloatArray(n)
    private val im = FloatArray(n)
    private val window = FloatArray(n) { (0.5 - 0.5 * cos(2.0 * PI * it / (n - 1))).toFloat() }
    private val cosT = FloatArray(n / 2) { cos(2.0 * PI * it / n).toFloat() }
    private val sinT = FloatArray(n / 2) { sin(2.0 * PI * it / n).toFloat() }
    private val bits = Integer.numberOfTrailingZeros(n)

    /** dB magnitudes for bins 0..n/2-1 (bin width = sampleRate / n). */
    val db = FloatArray(n / 2)

    fun process(x: ShortArray, offset: Int = 0) {
        for (i in 0 until n) {
            val j = Integer.reverse(i) ushr (32 - bits)
            re[j] = x[offset + i] / 32768f * window[i]
            im[j] = 0f
        }
        var size = 2
        while (size <= n) {
            val half = size / 2
            val step = n / size
            var start = 0
            while (start < n) {
                for (k in 0 until half) {
                    val wr = cosT[k * step]
                    val wi = -sinT[k * step]
                    val a = start + k
                    val b = a + half
                    val tr = re[b] * wr - im[b] * wi
                    val ti = re[b] * wi + im[b] * wr
                    re[b] = re[a] - tr; im[b] = im[a] - ti
                    re[a] += tr; im[a] += ti
                }
                start += size
            }
            size *= 2
        }
        for (k in 0 until n / 2) {
            val p = re[k] * re[k] + im[k] * im[k]
            db[k] = (10 * log10(max(p, 1e-14f))).toFloat()
        }
    }

    fun bin(hz: Float, sampleRate: Int) = (hz * n / sampleRate).toInt().coerceIn(0, n / 2 - 1)

    /** Mean power (dB) between two frequencies. */
    fun bandDb(f0: Float, f1: Float, sampleRate: Int): Float {
        val a = bin(f0, sampleRate)
        val b = max(a + 1, bin(f1, sampleRate))
        var s = 0.0
        for (k in a until b) s += Math.pow(10.0, db[k] / 10.0)
        return (10 * log10(max(s / (b - a), 1e-14))).toFloat()
    }

    /** Peak dB within ±[spanHz] of [hz]. */
    fun peakDb(hz: Float, spanHz: Float, sampleRate: Int): Float {
        var m = -200f
        for (k in bin(hz - spanHz, sampleRate)..bin(hz + spanHz, sampleRate)) m = max(m, db[k])
        return m
    }
}
