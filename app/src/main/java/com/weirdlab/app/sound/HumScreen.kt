package com.weirdlab.app.sound

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weirdlab.app.Tool
import com.weirdlab.app.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val noteLabelPaint = android.graphics.Paint().apply {
    color = android.graphics.Color.argb(150, 255, 255, 255); textSize = 24f; isAntiAlias = true
}

/** Colour per pitch class – a rainbow around the octave. */
private fun noteColor(midi: Int): Color {
    val hue = ((midi % 12 + 12) % 12) / 12f * 360f
    return Color.hsv(hue, 0.65f, 1f)
}

@Composable
fun HumScreen(onBack: () -> Unit) {
    val tool = Tool.HUM
    ToolScaffold(tool, onBack) {
        MicGate(tool) { HumContent(tool) }
    }
}

@Composable
private fun HumContent(tool: Tool) {
    val scope = rememberCoroutineScope()
    var recording by remember { mutableStateOf(false) }
    var stopRequested by remember { mutableStateOf(false) }
    var livePitch by remember { mutableFloatStateOf(0f) }
    var audio by remember { mutableStateOf(ShortArray(0)) }
    var track by remember { mutableStateOf<PitchTrack?>(null) }
    var analyzing by remember { mutableStateOf(false) }
    var instrument by remember { mutableStateOf(Instrument.VIOLIN) }
    var snap by remember { mutableStateOf(true) }
    var progress by remember { mutableFloatStateOf(-1f) }
    var rendering by remember { mutableStateOf(false) }
    var playJob by remember { mutableStateOf<Job?>(null) }
    val liveContour = remember { mutableStateListOf<Float>() }

    fun toggleRecord() {
        if (recording) { stopRequested = true; return }
        playJob?.cancel()
        stopRequested = false
        recording = true
        track = null
        liveContour.clear()
        val ring = FloatArray(2048)
        var filled = 0
        var chunkCount = 0
        scope.launch {
            val pcm = AudioKit.record(
                maxSeconds = 15f,
                shouldStop = { stopRequested },
                onChunk = { buf, n, _ ->
                    // keep the latest 2048 samples
                    if (n >= ring.size) {
                        for (i in ring.indices) ring[i] = buf[n - ring.size + i] / 32768f
                    } else {
                        System.arraycopy(ring, n, ring, 0, ring.size - n)
                        for (i in 0 until n) ring[ring.size - n + i] = buf[i] / 32768f
                    }
                    filled = min(ring.size, filled + n)
                    chunkCount++
                    if (filled == ring.size && chunkCount % 2 == 0) {
                        val hz = if (AudioKit.rms(buf, n) > 0.01f) yin(decimate2(ring), 0, 512, SR / 2) else 0f
                        livePitch = hz
                        scope.launch {
                            liveContour.add(if (hz > 0f) hzToMidi(hz) else 0f)
                            if (liveContour.size > 120) liveContour.removeAt(0)
                        }
                    }
                }
            )
            recording = false
            livePitch = 0f
            audio = pcm
            analyzing = true
            track = withContext(Dispatchers.Default) { analyzePitch(pcm) }
            analyzing = false
        }
    }

    fun playOriginal() {
        playJob?.cancel()
        playJob = scope.launch {
            try { AudioKit.play(audio) { progress = it } } finally { progress = -1f }
        }
    }

    fun playInstrument() {
        val t = track ?: return
        playJob?.cancel()
        playJob = scope.launch {
            rendering = true
            val pcm = withContext(Dispatchers.Default) { synthesize(t, instrument, snap, audio.size) }
            rendering = false
            try { AudioKit.play(pcm) { progress = it } } finally { progress = -1f }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // Tuner / big note
        GlassCard(Modifier.fillMaxWidth(), glow = tool.c1) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val midi = if (livePitch > 0f) hzToMidi(livePitch) else 0f
                val nearest = midi.roundToInt()
                Column(Modifier.weight(1f)) {
                    Text(
                        when {
                            recording && livePitch > 0f -> noteName(nearest)
                            recording -> "…"
                            analyzing -> "🧠"
                            track != null -> "${track!!.notes.size} notes"
                            else -> "Hum!"
                        },
                        color = if (recording && livePitch > 0f) noteColor(nearest) else tool.c1,
                        fontSize = 48.sp, fontWeight = FontWeight.Black
                    )
                    Text(
                        when {
                            recording && livePitch > 0f -> "%.0f Hz".format(livePitch)
                            recording -> "Waiting for a steady hum"
                            track != null -> "Pick an instrument and press play"
                            else -> "Hum, whistle or sing a tune"
                        },
                        color = Neon.TextDim, fontSize = 13.sp, fontFamily = FontFamily.Monospace
                    )
                }
                TunerNeedle(if (livePitch > 0f) midi - nearest else 0f, livePitch > 0f, tool.c1, Modifier.size(90.dp, 60.dp))
            }
        }
        Spacer(Modifier.height(12.dp))

        // Piano roll
        val t = track
        PianoRoll(
            track = t,
            live = if (recording) liveContour.toList() else emptyList(),
            progress = progress,
            accent = tool.c1,
            modifier = Modifier.fillMaxWidth().height(220.dp)
        )
        Spacer(Modifier.height(12.dp))

        GlowButton(
            if (recording) "■  Stop" else "🎙  Record your hum",
            if (recording) Neon.Red else tool.c1, if (recording) Neon.Orange else tool.c2,
            Modifier.fillMaxWidth().height(60.dp)
        ) { toggleRecord() }
        Spacer(Modifier.height(12.dp))

        Text("INSTRUMENT", color = Neon.TextDim, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Instrument.entries.forEach { inst ->
                NeonChip("${inst.emoji} ${inst.label}", instrument == inst, tool.c1) { instrument = inst }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NeonChip("🎯 Auto-tune ON", snap, Neon.Mint) { snap = true }
            NeonChip("〰️ Free pitch", !snap, Neon.Yellow) { snap = false }
        }
        Spacer(Modifier.height(12.dp))
        val ready = t != null && !recording && !analyzing
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlowButton(
                if (rendering) "Composing…" else "${instrument.emoji} Play as ${instrument.label}",
                tool.c2, Neon.Purple, Modifier.weight(1.4f), enabled = ready && !rendering
            ) { playInstrument() }
            GlowButton("🗣 Original", Neon.SurfaceHi, Neon.Cyan, Modifier.weight(1f), enabled = ready) { playOriginal() }
        }
        Spacer(Modifier.height(8.dp))
        Hint("Tip: hum \"daa daa daa\" with clear separate notes — it makes cleaner piano and 8-bit melodies. Whistling works great too.")
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun TunerNeedle(cents: Float, active: Boolean, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val c = Offset(size.width / 2f, size.height)
        val r = size.height - 6f
        drawArc(Color.White.copy(alpha = 0.12f), 200f, 140f, false, Offset(c.x - r, c.y - r), Size(2 * r, 2 * r), style = Stroke(6f, cap = StrokeCap.Round))
        drawArc(Neon.Mint.copy(alpha = 0.6f), 264f, 12f, false, Offset(c.x - r, c.y - r), Size(2 * r, 2 * r), style = Stroke(6f, cap = StrokeCap.Round))
        val ang = Math.toRadians((270.0 + cents.coerceIn(-0.5f, 0.5f) * 140.0))
        val tip = Offset(c.x + (r * kotlin.math.cos(ang)).toFloat(), c.y + (r * kotlin.math.sin(ang)).toFloat())
        drawLine(if (active) color else Neon.TextDim, c, tip, 4f, cap = StrokeCap.Round)
        drawCircle(Color.White, 6f, c)
    }
}

@Composable
private fun PianoRoll(track: PitchTrack?, live: List<Float>, progress: Float, accent: Color, modifier: Modifier) {
    Canvas(modifier) {
        drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF14102E), Color(0xFF0C0C20))), cornerRadius = CornerRadius(36f, 36f))
        val voicedMidis = when {
            track != null -> track.hz.filter { it > 0f }.map { hzToMidi(it) }
            else -> live.filter { it > 0f }
        }
        val lo = ((voicedMidis.minOrNull() ?: 55f) - 3f).toInt()
        val hi = max(lo + 12, ((voicedMidis.maxOrNull() ?: 67f) + 3f).toInt())
        val rows = hi - lo
        val rowH = size.height / rows
        fun yOf(m: Float) = size.height - (m - lo) * rowH

        // lanes – darker for black keys, labels on every C
        for (m in lo until hi) {
            val pc = ((m % 12) + 12) % 12
            val black = pc in intArrayOf(1, 3, 6, 8, 10)
            if (black) drawRect(Color.White.copy(alpha = 0.03f), Offset(0f, yOf(m + 1f)), Size(size.width, rowH))
            if (pc == 0) {
                drawLine(Color.White.copy(alpha = 0.12f), Offset(0f, yOf(m.toFloat())), Offset(size.width, yOf(m.toFloat())))
                drawContext.canvas.nativeCanvas.drawText(noteName(m), 10f, yOf(m.toFloat()) - 6f, noteLabelPaint)
            }
        }

        if (track != null && track.hz.isNotEmpty()) {
            val n = track.hz.size
            val dx = size.width / n
            // raw contour dots
            for (i in 0 until n) {
                val h = track.hz[i]
                if (h > 0f) drawCircle(Color.White.copy(alpha = 0.25f), 2f, Offset(i * dx, yOf(hzToMidi(h) + 0.5f)))
            }
            // note blocks
            for (note in track.notes) {
                val col = noteColor(note.midi)
                val x0 = note.start * dx
                val x1 = note.end * dx
                val y0 = yOf(note.midi + 1f)
                drawRoundRect(col.copy(alpha = 0.3f), Offset(x0, y0 - 4f), Size(x1 - x0, rowH + 8f), CornerRadius(14f, 14f))
                drawRoundRect(
                    Brush.horizontalGradient(listOf(col, col.copy(alpha = 0.6f)), x0, x1),
                    Offset(x0, y0), Size(max(4f, x1 - x0), rowH), CornerRadius(10f, 10f)
                )
            }
            if (progress >= 0f) {
                val x = size.width * progress
                drawLine(Color.White, Offset(x, 0f), Offset(x, size.height), 3f)
                drawCircle(accent, 8f, Offset(x, 10f))
            }
        } else if (live.isNotEmpty()) {
            val dx = size.width / 120f
            val offset = 120 - live.size
            var prev: Offset? = null
            live.forEachIndexed { i, m ->
                if (m > 0f) {
                    val p = Offset((offset + i) * dx, yOf(m + 0.5f))
                    prev?.let { drawLine(noteColor(m.roundToInt()), it, p, 6f, cap = StrokeCap.Round) }
                    drawCircle(noteColor(m.roundToInt()), 5f, p)
                    prev = p
                } else prev = null
            }
        } else {
            drawContext.canvas.nativeCanvas.drawText(
                "Your melody will appear here 🎼", size.width / 2f - 190f, size.height / 2f, noteLabelPaint
            )
        }
    }
}
