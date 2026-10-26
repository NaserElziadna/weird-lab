package com.weirdlab.app.sound

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

private val NOTE_NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

fun hzToMidi(hz: Float) = 69f + 12f * (ln(hz / 440f) / ln(2f))
fun midiToHz(m: Float) = 440f * 2f.pow((m - 69f) / 12f)
fun noteName(midi: Int) = NOTE_NAMES[((midi % 12) + 12) % 12] + (midi / 12 - 1)

/** YIN pitch detector. Returns Hz, or 0 when no clear pitch. */
fun yin(x: FloatArray, start: Int, w: Int, sr: Int, minF: Float = 70f, maxF: Float = 1000f): Float {
    val tauMin = (sr / maxF).toInt()
    val tauMax = (sr / minF).toInt()
    if (start < 0 || start + w + tauMax >= x.size) return 0f
    val d = FloatArray(tauMax + 1)
    for (tau in 1..tauMax) {
        var s = 0f
        for (j in 0 until w) {
            val diff = x[start + j] - x[start + j + tau]
            s += diff * diff
        }
        d[tau] = s
    }
    val cm = FloatArray(tauMax + 1)
    cm[0] = 1f
    var running = 0f
    for (tau in 1..tauMax) {
        running += d[tau]
        cm[tau] = if (running > 0f) d[tau] * tau / running else 1f
    }
    var tau = tauMin
    var found = -1
    while (tau <= tauMax) {
        if (cm[tau] < 0.15f) {
            while (tau + 1 <= tauMax && cm[tau + 1] < cm[tau]) tau++
            found = tau
            break
        }
        tau++
    }
    if (found < 0) return 0f
    var better = found.toFloat()
    if (found in 1 until tauMax) {
        val s0 = cm[found - 1]; val s1 = cm[found]; val s2 = cm[found + 1]
        val denom = 2f * (s0 - 2f * s1 + s2)
        if (abs(denom) > 1e-9f) better = found + (s0 - s2) / denom
    }
    return sr / better
}

/** Halve the sample rate with a tiny low-pass. */
fun decimate2(x: FloatArray): FloatArray = FloatArray(x.size / 2) { i ->
    val a = x[2 * i]
    val b = x[2 * i + 1]
    val c = if (2 * i + 2 < x.size) x[2 * i + 2] else b
    0.25f * a + 0.5f * b + 0.25f * c
}

data class Note(val start: Int, val end: Int, val midi: Int, val loud: Float)

class PitchTrack(
    val hz: FloatArray,      // per frame, 0 = unvoiced
    val loud: FloatArray,    // per frame, 0..1
    val notes: List<Note>,
    val hopSamples: Int      // hop in samples at 44.1 kHz
)

/** Analyse a recording into a pitch contour + note list. */
fun analyzePitch(pcm: ShortArray): PitchTrack {
    val half = decimate2(AudioKit.toFloat(pcm))
    val sr = SR / 2
    val hop = 256
    val w = 512
    val frames = max(0, (half.size - w - sr / 70) / hop)
    val hz = FloatArray(frames)
    val loud = FloatArray(frames)
    var maxRms = 1e-6f
    for (f in 0 until frames) {
        var s = 0f
        val st = f * hop
        for (j in 0 until w) s += half[st + j] * half[st + j]
        loud[f] = sqrt(s / w)
        maxRms = max(maxRms, loud[f])
    }
    for (f in 0 until frames) {
        loud[f] /= maxRms
        hz[f] = if (loud[f] > 0.08f && loud[f] * maxRms > 0.004f) yin(half, f * hop, w, sr) else 0f
    }
    // median filter (5) to kill octave blips
    val sm = FloatArray(frames)
    for (f in 0 until frames) {
        val win = ArrayList<Float>(5)
        for (k in -2..2) { val i = f + k; if (i in 0 until frames) win += hz[i] }
        val voiced = win.filter { it > 0f }.sorted()
        sm[f] = if (hz[f] > 0f && voiced.isNotEmpty()) voiced[voiced.size / 2] else 0f
    }
    // segment into notes with a bit of hysteresis
    val notes = ArrayList<Note>()
    var cur = -1
    var curStart = 0
    var candidate = -1
    var candCount = 0
    var loudAcc = 0f
    fun close(end: Int) {
        if (cur >= 0 && end - curStart >= 4) notes += Note(curStart, end, cur, loudAcc / (end - curStart))
        cur = -1; loudAcc = 0f
    }
    for (f in 0 until frames) {
        val m = if (sm[f] > 0f) hzToMidi(sm[f]).roundToInt() else -1
        if (m < 0) { close(f); candidate = -1; continue }
        if (cur < 0) { cur = m; curStart = f; loudAcc = 0f }
        else if (m != cur) {
            if (m == candidate) candCount++ else { candidate = m; candCount = 1 }
            if (candCount >= 3) { close(f - 2); cur = m; curStart = f - 2; loudAcc = 0f; candidate = -1 }
        } else candidate = -1
        loudAcc += loud[f]
    }
    close(frames)
    return PitchTrack(sm, loud, notes, hop * 2)
}

enum class Instrument(val label: String, val emoji: String) {
    PIANO("Piano", "🎹"),
    VIOLIN("Violin", "🎻"),
    FLUTE("Flute", "🌬️"),
    TRUMPET("Trumpet", "🎺"),
    CHIP("8-bit", "👾"),
    ROBOT("Robot", "🤖")
}

/** Re-synthesise the hum with an instrument. */
fun synthesize(track: PitchTrack, inst: Instrument, snap: Boolean, totalSamples: Int): ShortArray {
    val out = FloatArray(totalSamples)
    val hop = track.hopSamples
    val frames = track.hz.size
    if (frames == 0) return ShortArray(totalSamples)

    if (inst == Instrument.PIANO) {
        for (n in track.notes) {
            val f0 = midiToHz(n.midi.toFloat())
            val start = n.start * hop
            val len = min(totalSamples - start, (n.end - n.start) * hop + SR / 2)
            val vel = 0.4f + 0.6f * n.loud
            for (i in 0 until max(0, len)) {
                val t = i.toFloat() / SR
                val env = exp(-t * 2.6f) * min(1f, i / 80f)
                // release once the note ends
                val rel = if (i > (n.end - n.start) * hop) exp(-(i - (n.end - n.start) * hop).toFloat() / (SR * 0.08f)) else 1f
                var s = 0f
                var h = 1
                while (h <= 6) {
                    val partial = f0 * h * (1f + 0.0004f * h * h) // slight inharmonicity
                    if (partial > 12000f) break
                    s += sin(2f * PI.toFloat() * partial * t) * exp(-t * h * 0.8f) / h
                    h++
                }
                out[start + i] += s * env * rel * vel
            }
        }
        return AudioKit.toPcm(out)
    }

    // continuous instruments
    var phase = 0.0
    var curF = 0f
    var amp = 0f
    var lp = 0f
    var vibPhase = 0.0
    for (i in 0 until totalSamples) {
        val fi = min(frames - 1, i / hop)
        val raw = track.hz[fi]
        val target = when {
            raw <= 0f -> 0f
            snap || inst == Instrument.CHIP -> midiToHz(hzToMidi(raw).roundToInt().toFloat())
            else -> raw
        }
        val targetAmp = if (raw > 0f) 0.25f + 0.75f * track.loud[fi] else 0f
        amp += (targetAmp - amp) * (if (targetAmp > amp) 0.003f else 0.0012f)
        if (target > 0f) {
            curF = if (curF <= 0f) target else curF + (target - curF) * (if (inst == Instrument.CHIP) 1f else 0.004f)
        }
        if (curF <= 0f || amp < 1e-4f) { phase = 0.0; continue }

        var f = curF
        if (inst == Instrument.VIOLIN || inst == Instrument.FLUTE) {
            vibPhase += 2 * PI * 5.5 / SR
            f *= 1f + 0.006f * sin(vibPhase).toFloat() * min(1f, amp * 2f)
        }
        phase += 2 * PI * f / SR
        if (phase > 2 * PI * 1000) phase -= 2 * PI * 1000
        val p = phase
        val s: Float = when (inst) {
            Instrument.VIOLIN -> {
                var acc = 0f
                var h = 1
                while (h <= 12 && f * h < 14000f) { acc += sin(p * h).toFloat() / h; h++ }
                lp += (acc - lp) * 0.35f
                lp * 0.7f
            }
            Instrument.FLUTE -> {
                val breath = (Random.nextFloat() - 0.5f) * 0.12f * amp
                sin(p).toFloat() + 0.18f * sin(2 * p).toFloat() + breath
            }
            Instrument.TRUMPET -> {
                val bright = 0.6f + 1.4f * amp
                var acc = 0f
                var h = 1
                while (h <= 10 && f * h < 14000f) { acc += sin(p * h).toFloat() * (1f / h).pow(2.2f - bright); h++ }
                acc * 0.45f
            }
            Instrument.CHIP -> if (sin(p) >= 0) 0.5f else -0.5f
            Instrument.ROBOT -> {
                // ring-modulated buzzy voice
                val saw = (((p / (2 * PI)) % 1.0) * 2 - 1).toFloat()
                saw * sin(2 * PI * 30.0 * i / SR).toFloat() * 0.8f
            }
            Instrument.PIANO -> 0f
        }
        out[i] = s * amp
    }
    return AudioKit.toPcm(out)
}
