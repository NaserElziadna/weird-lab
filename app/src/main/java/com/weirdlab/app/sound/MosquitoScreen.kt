package com.weirdlab.app.sound

import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weirdlab.app.Tool
import com.weirdlab.app.ui.*
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin

/** Continuous sine generator with click-free frequency & volume glides. */
class ToneEngine {
    @Volatile var freq = 1000.0
    @Volatile var amp = 0.0
    @Volatile private var running = false
    private var thread: Thread? = null

    fun start() {
        if (running) return
        running = true
        thread = Thread {
            val minBuf = AudioTrack.getMinBufferSize(SR, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val track = AudioKit.buildTrack(SR, max(minBuf, 4096))
            track.play()
            val buf = ShortArray(512)
            var phase = 0.0
            var curF = freq
            var curA = 0.0
            try {
                while (running) {
                    for (i in buf.indices) {
                        curF += (freq - curF) * 0.002
                        curA += (amp - curA) * 0.002
                        phase += 2 * PI * curF / SR
                        if (phase > 2 * PI) phase -= 2 * PI
                        buf[i] = (sin(phase) * curA * 32000).toInt().toShort()
                    }
                    track.write(buf, 0, buf.size)
                }
            } finally {
                try { track.stop() } catch (_: Exception) {}
                track.release()
            }
        }.also { it.start() }
    }

    fun stop() {
        running = false
        thread?.join(400)
        thread = null
    }
}

private fun earAge(hz: Float) = when {
    hz >= 17400 -> "under 25" to "🧒"
    hz >= 16000 -> "under 30" to "🧑"
    hz >= 15000 -> "under 40" to "🧔"
    hz >= 14000 -> "under 50" to "👨‍💼"
    hz >= 12000 -> "under 60" to "👴"
    hz >= 10000 -> "under 70" to "🧓"
    else -> "70+… or turn the volume up!" to "🦕"
}

private const val F_MIN = 1000f
private const val F_MAX = 22000f
private fun sliderToHz(v: Float) = F_MIN * exp(v * ln(F_MAX / F_MIN))
private fun hzToSlider(hz: Float) = ln(hz / F_MIN) / ln(F_MAX / F_MIN)

@Composable
fun MosquitoScreen(onBack: () -> Unit) {
    val tool = Tool.MOSQUITO
    val engine = remember { ToneEngine() }
    val resumed = rememberIsResumed()
    var playing by remember { mutableStateOf(false) }
    var hz by remember { mutableFloatStateOf(8000f) }
    var volume by remember { mutableFloatStateOf(0.5f) }
    var testing by remember { mutableStateOf(false) }
    var resultHz by remember { mutableStateOf<Float?>(null) }

    DisposableEffect(Unit) { onDispose { engine.stop() } }
    LaunchedEffect(playing, resumed) {
        if (playing && resumed) engine.start() else { engine.amp = 0.0; delay(60); engine.stop() }
    }
    LaunchedEffect(hz, volume, playing) {
        engine.freq = hz.toDouble()
        engine.amp = if (playing) volume.toDouble() else 0.0
    }
    // Ear test: sweep up at 350 Hz per second from 8 kHz
    LaunchedEffect(testing) {
        if (!testing) return@LaunchedEffect
        hz = 8000f
        playing = true
        while (testing && hz < 21500f) {
            delay(50)
            hz += 350f * 0.05f
        }
        if (testing) { resultHz = hz; testing = false; playing = false }
    }

    val transition = rememberInfiniteTransition(label = "mos")
    val t by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1000, easing = LinearEasing)), label = "t")

    ToolScaffold(tool, onBack) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            // Visual: waveform whose density follows pitch, with a buzzing mosquito
            Box(Modifier.fillMaxWidth().height(210.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val mid = size.height / 2f
                    val cycles = 2f + hz / 900f
                    val ampPx = if (playing) size.height * 0.32f * (0.4f + volume * 0.6f) else 6f
                    for (layer in 0..2) {
                        val path = Path()
                        val steps = 400
                        for (i in 0..steps) {
                            val x = size.width * i / steps
                            val fade = sin(PI * i / steps).toFloat()
                            val y = mid + sin((i.toFloat() / steps * cycles + t * (3 + layer)) * 2 * PI).toFloat() * ampPx * fade * (1f - layer * 0.25f)
                            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        val col = listOf(tool.c1, tool.c2, Neon.Pink)[layer]
                        drawPath(path, col.copy(alpha = 0.25f), style = Stroke(10f, cap = StrokeCap.Round))
                        drawPath(path, col.copy(alpha = 0.9f - layer * 0.25f), style = Stroke(2.5f, cap = StrokeCap.Round))
                    }
                }
                val buzz = if (playing) sin(t * 2 * PI * 7).toFloat() * (2f + hz / 3000f) else 0f
                Text(
                    "🦟", fontSize = 64.sp,
                    modifier = Modifier.graphicsLayer {
                        translationX = buzz * 3f
                        translationY = sin(t * 2 * PI * 5).toFloat() * (if (playing) 8f else 2f)
                        rotationZ = buzz * 2f
                    }
                )
            }

            Text(
                if (hz >= 1000) "%.1f kHz".format(hz / 1000f) else "%.0f Hz".format(hz),
                color = tool.c1, fontSize = 56.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace
            )
            Text(pitchFact(hz), color = Neon.TextDim, fontSize = 13.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))

            Slider(
                value = hzToSlider(hz),
                onValueChange = { if (!testing) hz = sliderToHz(it) },
                colors = SliderDefaults.colors(thumbColor = tool.c1, activeTrackColor = tool.c1, inactiveTrackColor = Neon.SurfaceHi)
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("🔈", fontSize = 18.sp)
                Slider(
                    value = volume, onValueChange = { volume = it },
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    colors = SliderDefaults.colors(thumbColor = tool.c2, activeTrackColor = tool.c2, inactiveTrackColor = Neon.SurfaceHi)
                )
                Text("🔊", fontSize = 18.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(12000f to "12k", 15000f to "15k", 17400f to "17.4k teen", 20000f to "20k").forEach { (f, label) ->
                    NeonChip(label, kotlin.math.abs(hz - f) < 50, tool.c1) { if (!testing) { hz = f; playing = true } }
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlowButton(if (playing && !testing) "■ Stop" else "▶ Play", tool.c1, tool.c2, Modifier.weight(1f), enabled = !testing) {
                    playing = !playing
                }
                GlowButton(
                    if (testing) "🙉 I can't hear it!" else "👂 Ear age test",
                    Neon.Pink, Neon.Purple, Modifier.weight(1.3f)
                ) {
                    if (testing) { resultHz = hz; testing = false; playing = false }
                    else { resultHz = null; testing = true }
                }
            }
            Spacer(Modifier.height(14.dp))
            resultHz?.let { r ->
                val (age, emoji) = earAge(r)
                GlassCard(Modifier.fillMaxWidth(), glow = Neon.Pink) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(emoji, fontSize = 48.sp)
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text("Your ears say…", color = Neon.TextDim, fontSize = 13.sp)
                            Text(age, color = Neon.Pink, fontSize = 26.sp, fontWeight = FontWeight.Black)
                            Text("You stopped hearing at %.1f kHz".format(r / 1000f), color = Neon.Text, fontSize = 13.sp)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            Hint(
                "Ear test: the pitch rises slowly from 8 kHz — tap the moment it vanishes. Phone speakers also fade at very high pitches, so earphones give a fairer result. " +
                    "Just for fun, not a medical hearing test. Keep the volume comfortable."
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun pitchFact(hz: Float) = when {
    hz < 2000 -> "Middle of the human voice range"
    hz < 5000 -> "Where your ears are the most sensitive"
    hz < 10000 -> "Hissing 'sss' sounds live here"
    hz < 14000 -> "Most adults still hear this"
    hz < 16500 -> "Older ears start missing this"
    hz < 18500 -> "Teen-only zone 🧒 — the 'mosquito' anti-loitering tone"
    hz < 20000 -> "Very few humans hear this… dogs do 🐶"
    else -> "Beyond human hearing — bats & dogs only 🦇"
}
