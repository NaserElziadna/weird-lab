package com.weirdlab.app.sound

import android.media.MediaRecorder
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weirdlab.app.Tool
import com.weirdlab.app.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

data class EchoResult(
    val rt60: Float,
    val roomMeters: Float,
    val decayDb: FloatArray,      // energy-decay curve, 0 dB at the clap
    val blockMs: Float,
    val noiseDb: Float
)

private const val BLOCK = 220 // 5 ms at 44.1 kHz

/** Schroeder backward-integration reverb estimate. Returns null if no clear impulse was found. */
fun analyzeEcho(x: ShortArray): EchoResult? {
    val nb = x.size / BLOCK
    if (nb < 40) return null
    val e = DoubleArray(nb)
    for (b in 0 until nb) {
        var s = 0.0
        for (i in b * BLOCK until (b + 1) * BLOCK) { val v = x[i] / 32768.0; s += v * v }
        e[b] = s / BLOCK
    }
    var peak = 0
    for (b in 1 until nb) if (e[b] > e[peak]) peak = b
    if (e[peak] < 0.003) return null // too quiet – no clap

    val noise = if (peak >= 12) {
        e.copyOfRange(0, peak).sorted()[peak / 2]
    } else {
        val tail = e.copyOfRange(max(peak + 1, nb - 40), nb).sorted()
        tail[tail.size / 4]
    }
    val noiseFloor = max(noise, 1e-10)

    var end = nb
    for (b in peak + 2 until nb) {
        if (e[b] < noiseFloor * 2.0) { end = b; break }
    }
    if (end - peak < 4) return null

    val cum = DoubleArray(end - peak)
    var acc = 0.0
    for (i in end - 1 downTo peak) {
        acc += max(0.0, e[i] - noiseFloor)
        cum[i - peak] = acc
    }
    val edc = FloatArray(cum.size) { (10 * log10(max(cum[it], 1e-20) / cum[0])).toFloat() }

    fun firstBelow(db: Float) = edc.indexOfFirst { it <= db }.takeIf { it >= 0 }
    val blockSec = BLOCK.toFloat() / SR
    val t5 = firstBelow(-5f) ?: 0
    val rt = firstBelow(-25f)?.let { (it - t5) * blockSec * 3f }
        ?: firstBelow(-15f)?.let { (it - t5) * blockSec * 6f }
        ?: run {
            val drop = edc.last()
            if (drop < -3f) (edc.size * blockSec) * 60f / -drop else null
        }
        ?: return null
    val rt60 = rt.coerceIn(0.08f, 6f)
    // Sabine with a guessed average absorption of 0.2 for a cube-ish room: L = RT60·6·α / 0.161
    val meters = rt60 * 6f * 0.2f / 0.161f
    val noiseDb = (10 * log10(noiseFloor / e[peak])).toFloat()
    return EchoResult(rt60, meters, edc, blockSec * 1000f, noiseDb)
}

private data class RoomKind(val name: String, val emoji: String)

private fun roomKind(rt: Float) = when {
    rt < 0.22f -> RoomKind("Closet or car", "🚗")
    rt < 0.4f -> RoomKind("Cozy bedroom", "🛏️")
    rt < 0.65f -> RoomKind("Living room", "🛋️")
    rt < 1.0f -> RoomKind("Big hall or tiled bathroom", "🏫")
    rt < 1.8f -> RoomKind("Gym, mosque or stairwell", "🕌")
    else -> RoomKind("Cathedral… or a cave", "🦇")
}

private enum class EchoState { IDLE, LISTENING, ANALYZING, RESULT, FAILED }

/** Short rising chirp used in Ping mode. */
private fun pingSound(): ShortArray {
    val n = SR / 40 // 25 ms
    val f0 = 600.0
    val f1 = 9000.0
    val out = FloatArray(n) { i ->
        val t = i.toDouble() / SR
        val dur = n.toDouble() / SR
        val phase = 2 * PI * (f0 * t + (f1 - f0) * t * t / (2 * dur))
        val env = sin(PI * i / n)
        (sin(phase) * env).toFloat()
    }
    return AudioKit.toPcm(out)
}

@Composable
fun EchoScreen(onBack: () -> Unit) {
    val tool = Tool.ECHO
    ToolScaffold(tool, onBack) {
        MicGate(tool) { EchoContent(tool) }
    }
}

@Composable
private fun EchoContent(tool: Tool) {
    val scope = rememberCoroutineScope()
    var ping by remember { mutableStateOf(false) }
    var state by remember { mutableStateOf(EchoState.IDLE) }
    var level by remember { mutableFloatStateOf(0f) }
    var result by remember { mutableStateOf<EchoResult?>(null) }
    val reveal = remember { Animatable(0f) }

    fun start() {
        if (state == EchoState.LISTENING || state == EchoState.ANALYZING) return
        state = EchoState.LISTENING
        result = null
        scope.launch {
            reveal.snapTo(0f)
            var peakAt = -1
            var posNow = 0
            val recording = async {
                AudioKit.record(
                    maxSeconds = 4f,
                    source = MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    // stop ~1.6 s after the clap (or ping) so we capture the whole echo tail
                    shouldStop = { peakAt > 0 && posNow > peakAt + SR * 16 / 10 },
                    onChunk = { buf, n, pos ->
                        posNow = pos
                        var m = 0
                        for (i in 0 until n) m = max(m, abs(buf[i].toInt()))
                        level = m / 32768f
                        if (peakAt < 0 && m > 9000 && pos > SR / 5) peakAt = pos
                    }
                )
            }
            if (ping) {
                delay(400)
                AudioKit.play(pingSound())
            }
            val audio = recording.await()
            state = EchoState.ANALYZING
            val r = withContext(Dispatchers.Default) { analyzeEcho(audio) }
            result = r
            level = 0f
            state = if (r != null) EchoState.RESULT else EchoState.FAILED
            if (r != null) reveal.animateTo(1f, tween(1200, easing = FastOutSlowInEasing))
        }
    }

    val transition = rememberInfiniteTransition(label = "echo")
    val pulse by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1500, easing = LinearEasing)), label = "p")

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NeonChip("👏 I'll clap", !ping, tool.c1) { ping = false }
            NeonChip("🔔 Phone pings", ping, tool.c2) { ping = true }
        }
        Spacer(Modifier.height(14.dp))

        // Big button with echo rings
        Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val c = center
                val r = size.minDimension / 2f
                val active = state == EchoState.LISTENING
                for (k in 0..3) {
                    val f = (pulse + k / 4f) % 1f
                    val a = if (active) 0.7f else 0.25f
                    drawCircle(tool.c1.copy(alpha = (1f - f) * a), r * (0.45f + 0.55f * f), c, style = Stroke(3f))
                }
                drawCircle(Brush.radialGradient(listOf(tool.c2.copy(alpha = 0.5f), Color.Transparent), c, r * (0.5f + level)), r * (0.5f + level), c)
            }
            Box(
                Modifier
                    .size(130.dp)
                    .clip(CircleShape)
                    .clickable { start() },
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(Brush.linearGradient(listOf(tool.c1, tool.c2)))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        when (state) {
                            EchoState.LISTENING -> if (ping) "🔔" else "👏"
                            EchoState.ANALYZING -> "🧠"
                            else -> "▶"
                        },
                        fontSize = 40.sp
                    )
                    Text(
                        when (state) {
                            EchoState.LISTENING -> if (ping) "Pinging…" else "CLAP NOW!"
                            EchoState.ANALYZING -> "Thinking…"
                            EchoState.IDLE -> "Tap to start"
                            else -> "Again"
                        },
                        color = Neon.Ink, fontWeight = FontWeight.Black, fontSize = 14.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        when (state) {
            EchoState.FAILED -> Text(
                "Didn't catch a clear clap 🤔\nTry one sharp, loud clap in a quiet room.",
                color = Neon.Orange, textAlign = TextAlign.Center
            )
            EchoState.IDLE -> Hint(
                "Stand in the middle of a room, tap the button and give ONE sharp clap. The app listens to how long the echo takes to die out and guesses how big the room is.",
                Modifier.padding(horizontal = 8.dp)
            )
            else -> {}
        }

        result?.let { r ->
            val kind = roomKind(r.rt60)
            GlassCard(Modifier.fillMaxWidth(), glow = tool.c1) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(kind.emoji, fontSize = 40.sp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(kind.name, color = tool.c1, fontSize = 22.sp, fontWeight = FontWeight.Black)
                        Text("Echo fades in %.2f s".format(r.rt60), color = Neon.Text, fontSize = 14.sp)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RoomCube(r.roomMeters, reveal.value, tool.c1, tool.c2, Modifier.size(130.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("≈ %.1f m".format(r.roomMeters), color = Neon.Text, fontSize = 30.sp, fontWeight = FontWeight.Black)
                        Text("estimated room width", color = Neon.TextDim, fontSize = 12.sp)
                        Spacer(Modifier.height(6.dp))
                        Text("Tiles & glass echo more, so bathrooms 'feel' huge 😄", color = Neon.TextDim, fontSize = 11.sp)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("ECHO DECAY", color = Neon.TextDim, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                DecayChart(r, reveal.value, tool.c1, Modifier.fillMaxWidth().height(130.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** Isometric neon wire-cube that grows to the room size. */
@Composable
private fun RoomCube(meters: Float, reveal: Float, c1: Color, c2: Color, modifier: Modifier) {
    Canvas(modifier) {
        val s = (0.35f + 0.6f * (meters / 12f).coerceIn(0f, 1f)) * reveal * size.minDimension / 2f
        val c = Offset(size.width / 2f, size.height / 2f + s * 0.3f)
        val cx = cos(PI / 6).toFloat()
        val sx = sin(PI / 6).toFloat()
        fun iso(x: Float, y: Float, z: Float) = Offset(c.x + (x - y) * cx * s, c.y + (x + y) * sx * s - z * s)
        val corners = listOf(
            iso(-1f, -1f, 0f), iso(1f, -1f, 0f), iso(1f, 1f, 0f), iso(-1f, 1f, 0f),
            iso(-1f, -1f, 1.2f), iso(1f, -1f, 1.2f), iso(1f, 1f, 1.2f), iso(-1f, 1f, 1.2f)
        )
        val edges = listOf(0 to 1, 1 to 2, 2 to 3, 3 to 0, 4 to 5, 5 to 6, 6 to 7, 7 to 4, 0 to 4, 1 to 5, 2 to 6, 3 to 7)
        // floor glow
        val floor = Path().apply {
            moveTo(corners[0].x, corners[0].y); lineTo(corners[1].x, corners[1].y)
            lineTo(corners[2].x, corners[2].y); lineTo(corners[3].x, corners[3].y); close()
        }
        drawPath(floor, Brush.linearGradient(listOf(c1.copy(alpha = 0.35f), c2.copy(alpha = 0.05f))))
        edges.forEach { (a, b) ->
            drawLine(c1.copy(alpha = 0.25f), corners[a], corners[b], 9f, cap = StrokeCap.Round)
            drawLine(c2, corners[a], corners[b], 2.5f, cap = StrokeCap.Round)
        }
        // you
        drawCircle(Color.White, 5f, iso(0f, 0f, 0.05f))
    }
}

@Composable
private fun DecayChart(r: EchoResult, reveal: Float, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val minDb = -50f
        val n = r.decayDb.size
        val shown = (n * reveal).toInt().coerceAtLeast(2)
        val dx = size.width / (n - 1).coerceAtLeast(1)
        fun y(db: Float) = (db / minDb).coerceIn(0f, 1f) * size.height
        for (db in listOf(-10f, -20f, -30f, -40f)) {
            drawLine(Color.White.copy(alpha = 0.06f), Offset(0f, y(db)), Offset(size.width, y(db)))
        }
        drawLine(
            Neon.Orange.copy(alpha = 0.6f), Offset(0f, y(r.noiseDb)), Offset(size.width, y(r.noiseDb)), 2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))
        )
        val path = Path()
        val fill = Path()
        fill.moveTo(0f, size.height)
        for (i in 0 until min(shown, n)) {
            val p = Offset(i * dx, y(r.decayDb[i]))
            if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            fill.lineTo(p.x, p.y)
        }
        fill.lineTo((min(shown, n) - 1) * dx, size.height)
        fill.close()
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), Color.Transparent)))
        drawPath(path, color.copy(alpha = 0.3f), style = Stroke(10f, cap = StrokeCap.Round))
        drawPath(path, color, style = Stroke(3f, cap = StrokeCap.Round))
    }
}
