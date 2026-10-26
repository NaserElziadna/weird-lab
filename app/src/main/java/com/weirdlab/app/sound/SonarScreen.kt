package com.weirdlab.app.sound

import android.hardware.Sensor
import android.hardware.SensorManager
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weirdlab.app.Tool
import com.weirdlab.app.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

// ---- sonar constants ----
private const val SSR = 48000                 // sample rate (native on Samsung = least latency jitter)
private const val PERIOD = SSR * 180 / 1000   // one ping every 180 ms
private const val CHIRP_N = 240               // 5 ms chirp
private const val SPEED = 343f                // speed of sound, m/s
private const val MAX_RANGE = 5f              // metres
private const val BIN_M = 0.03f
private const val NBINS = 166                 // 5 m / 3 cm
private const val BLIND = 0.45f               // phone body ringing hides the first ~45 cm
private const val SECTORS = 120               // 3° slices around you

private val TWO_PI = (2 * PI).toFloat()

private fun makeChirp(): FloatArray {
    val f0 = 3000.0
    val f1 = 9000.0
    val dur = CHIRP_N.toDouble() / SSR
    return FloatArray(CHIRP_N) { i ->
        val t = i.toDouble() / SSR
        val ph = 2 * PI * (f0 * t + (f1 - f0) * t * t / (2 * dur))
        val w = 0.5 - 0.5 * cos(2 * PI * i / (CHIRP_N - 1))
        (sin(ph) * w).toFloat()
    }
}

/**
 * Active sonar: the speaker emits a chirp every [PERIOD] samples while the mic streams.
 * Matched-filtering the recording against the chirp gives a sharp peak for the direct
 * speaker→mic sound, then smaller peaks for echoes. Echo delay × speed of sound / 2 = distance.
 */
class SonarEngine {
    val chirp = makeChirp()
    private val buf = FloatArray(PERIOD * 3)
    private var len = 0
    private val bg = FloatArray(NBINS)
    private val env = FloatArray((2 * MAX_RANGE / SPEED * SSR).toInt() + 32)

    val current = FloatArray(NBINS)
    val map = Array(SECTORS) { FloatArray(NBINS) }
    val nearestBySector = FloatArray(SECTORS) { -1f }
    val scanned = BooleanArray(SECTORS)

    @Volatile var heading = 0f      // radians, clockwise from the start direction
    private var lastHeading = 0f
    @Volatile var nearest = -1f
    @Volatile var threshold = 0.02f
    @Volatile var pings = 0
    @Volatile var heard = false
    @Volatile var mapMax = 1e-4f

    fun reset() {
        map.forEach { it.fill(0f) }
        nearestBySector.fill(-1f)
        scanned.fill(false)
        bg.fill(0f)
        mapMax = 1e-4f
        pings = 0
    }

    fun feed(frame: ShortArray) {
        val n = frame.size
        if (len + n > buf.size) len = 0
        for (i in 0 until n) buf[len + i] = frame[i] / 32768f
        len += n
        while (len >= 2 * PERIOD) process()
    }

    private fun corrAt(k: Int): Float {
        var s = 0f
        for (j in 0 until CHIRP_N) s += buf[k + j] * chirp[j]
        return s
    }

    private fun process() {
        // 1. find the direct-path chirp in the first period
        var d = 0
        var best = 0f
        for (k in 0 until PERIOD) {
            val v = abs(corrAt(k))
            if (v > best) { best = v; d = k }
        }
        heard = best > 0.05f
        if (heard) analyse(d, best)
        // 2. slide the buffer so the next ping lands near the start
        val drop = (d + PERIOD - 300).coerceIn(1, len)
        System.arraycopy(buf, drop, buf, 0, len - drop)
        len -= drop
    }

    private fun analyse(d: Int, direct: Float) {
        // matched-filter envelope after the direct peak, normalised to it
        for (l in env.indices) env[l] = abs(corrAt(d + l)) / direct
        // light smoothing (moving max over 6 samples)
        for (l in env.size - 1 downTo 6) {
            var m = env[l]
            for (k in 1..5) m = max(m, env[l - k])
            env[l] = m
        }
        val cleaned = FloatArray(NBINS)
        val moving = abs(angleDiff(heading, lastHeading)) > 0.02f
        lastHeading = heading
        for (b in 0 until NBINS) {
            val r0 = b * BIN_M
            val r1 = r0 + BIN_M
            if (r1 < BLIND) continue
            val l0 = (2 * r0 / SPEED * SSR).toInt()
            val l1 = max(l0 + 1, (2 * r1 / SPEED * SSR).toInt()).coerceAtMost(env.size)
            var v = 0f
            for (l in l0 until l1) v = max(v, env[l])
            v *= (r0 + BIN_M / 2).pow(1.2f) // far echoes are fainter – boost them
            // learn the phone's own constant ringing only while you're turning
            if (moving) bg[b] = 0.9f * bg[b] + 0.1f * v
            cleaned[b] = max(0f, v - 0.7f * bg[b])
        }
        val sorted = cleaned.sortedArray()
        val thr = max(sorted[sorted.size / 2] * 5f, 0.015f)
        threshold = thr
        var near = -1f
        for (b in 0 until NBINS) if (cleaned[b] > thr) { near = (b + 0.5f) * BIN_M; break }
        nearest = near
        cleaned.copyInto(current)

        val h = ((heading % TWO_PI) + TWO_PI) % TWO_PI
        val s = ((h / TWO_PI) * SECTORS).toInt().coerceIn(0, SECTORS - 1)
        val row = map[s]
        var mx = 0f
        for (b in 0 until NBINS) {
            row[b] = if (scanned[s]) 0.5f * row[b] + 0.5f * cleaned[b] else cleaned[b]
            mx = max(mx, row[b])
        }
        mapMax = max(mapMax * 0.997f, mx)
        if (near > 0f) {
            val old = nearestBySector[s]
            nearestBySector[s] = if (old < 0f) near else 0.5f * old + 0.5f * near
        }
        scanned[s] = true
        pings++
    }
}

private fun angleDiff(a: Float, b: Float): Float {
    var d = a - b
    while (d > PI) d -= TWO_PI
    while (d < -PI) d += TWO_PI
    return d
}

private suspend fun pingLoop(chirp: FloatArray) = withContext(Dispatchers.IO) {
    val pcm = ShortArray(PERIOD)
    for (i in chirp.indices) pcm[i] = (chirp[i] * 0.95f * 32767f).toInt().toShort()
    val minBuf = AudioTrack.getMinBufferSize(SSR, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
    val track = AudioKit.buildTrack(SSR, max(minBuf, PERIOD * 2))
    try {
        track.play()
        while (isActive) track.write(pcm, 0, pcm.size)
    } finally {
        try { track.stop() } catch (_: Exception) {}
        track.release()
    }
}

private val rangeLabelPaint = android.graphics.Paint().apply {
    color = android.graphics.Color.argb(140, 182, 255, 59); textSize = 26f; isAntiAlias = true
}

@Composable
fun SonarScreen(onBack: () -> Unit) {
    val tool = Tool.SONAR
    ToolScaffold(tool, onBack) {
        MicGate(tool) { SonarContent(tool) }
    }
}

@Composable
private fun SonarContent(tool: Tool) {
    val ctx = LocalContext.current
    val engine = remember { SonarEngine() }
    val resumed = rememberIsResumed()
    var running by remember { mutableStateOf(false) }

    // heading from the game rotation vector (no magnetometer = no compass wobble indoors)
    val rot = remember { FloatArray(9) }
    val orient = remember { FloatArray(3) }
    var yaw0 by remember { mutableStateOf<Float?>(null) }
    var yawNow by remember { mutableFloatStateOf(0f) }
    SensorListener(listOf(Sensor.TYPE_GAME_ROTATION_VECTOR, Sensor.TYPE_ROTATION_VECTOR), resumed, SensorManager.SENSOR_DELAY_GAME) { e ->
        SensorManager.getRotationMatrixFromVector(rot, e.values)
        SensorManager.getOrientation(rot, orient)
        yawNow = orient[0]
        if (yaw0 == null) yaw0 = yawNow
        engine.heading = angleDiff(yawNow, yaw0 ?: yawNow).let { if (it < 0) it + TWO_PI else it }
    }

    LaunchedEffect(running, resumed) {
        if (!running || !resumed) return@LaunchedEffect
        val source = AudioKit.rawSource(ctx)
        coroutineScope {
            launch { pingLoop(engine.chirp) }
            AudioKit.listen(960, SSR, source) { engine.feed(it) }
        }
    }

    val tick = rememberFrameTick(running)
    var nearest by remember { mutableFloatStateOf(-1f) }
    var coverage by remember { mutableIntStateOf(0) }
    var heard by remember { mutableStateOf(false) }
    LaunchedEffect(running) {
        while (running) {
            nearest = engine.nearest
            coverage = engine.scanned.count { it } * 100 / SECTORS
            heard = engine.heard
            delay(150)
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("Nearest echo", if (running && nearest > 0f) "%.2f m".format(nearest) else "—", tool.c1, Modifier.weight(1f))
            StatTile("Room scanned", "$coverage%", tool.c2, Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))

        // ---------- radar ----------
        Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) {
            tick
            val c = center
            val rMax = size.minDimension / 2f - 6f
            drawCircle(Brush.radialGradient(listOf(Color(0xFF0E2A12), Color(0xFF07140B), Color(0xFF050510)), c, rMax), rMax, c)
            for (m in 1..MAX_RANGE.toInt()) {
                val rr = rMax * m / MAX_RANGE
                drawCircle(tool.c1.copy(alpha = 0.16f), rr, c, style = Stroke(1.5f))
                drawContext.canvas.nativeCanvas.drawText("${m}m", c.x + 6f, c.y - rr + 26f, rangeLabelPaint)
            }
            drawLine(tool.c1.copy(alpha = 0.12f), Offset(c.x - rMax, c.y), Offset(c.x + rMax, c.y))
            drawLine(tool.c1.copy(alpha = 0.12f), Offset(c.x, c.y - rMax), Offset(c.x, c.y + rMax))

            fun polar(theta: Float, r: Float) = Offset(c.x + sin(theta) * r, c.y - cos(theta) * r)

            // echo cloud, bucketed by strength so it's only 4 draw calls
            val buckets = Array(4) { ArrayList<Offset>() }
            val mx = engine.mapMax
            for (s in 0 until SECTORS) {
                if (!engine.scanned[s]) continue
                val theta = (s + 0.5f) / SECTORS * TWO_PI
                val row = engine.map[s]
                for (b in 0 until NBINS) {
                    val v = row[b] / mx
                    if (v < 0.18f) continue
                    val k = min(3, ((v - 0.18f) / 0.82f * 4f).toInt())
                    buckets[k].add(polar(theta, (b + 0.5f) * BIN_M / MAX_RANGE * rMax))
                }
            }
            buckets.forEachIndexed { k, pts ->
                if (pts.isNotEmpty()) drawPoints(
                    pts, PointMode.Points, tool.c1.copy(alpha = 0.25f + 0.22f * k),
                    strokeWidth = 5f + 2f * k, cap = StrokeCap.Round
                )
            }

            // outline of the nearest surfaces
            val outline = Path()
            var penDown = false
            for (s in 0..SECTORS) {
                val idx = s % SECTORS
                val r = engine.nearestBySector[idx]
                if (r > 0f) {
                    val p = polar((idx + 0.5f) / SECTORS * TWO_PI, r / MAX_RANGE * rMax)
                    if (penDown) outline.lineTo(p.x, p.y) else outline.moveTo(p.x, p.y)
                    penDown = true
                } else penDown = false
            }
            drawPath(outline, Neon.Pink.copy(alpha = 0.25f), style = Stroke(12f, cap = StrokeCap.Round))
            drawPath(outline, Neon.Pink, style = Stroke(3f, cap = StrokeCap.Round))

            // sweep beam in the direction the phone's bottom edge points
            val hDeg = engine.heading * 180f / PI.toFloat()
            drawArc(
                Brush.radialGradient(listOf(tool.c2.copy(alpha = 0.35f), tool.c2.copy(alpha = 0.02f)), c, rMax),
                hDeg - 90f - 12f, 24f, true, Offset(c.x - rMax, c.y - rMax), Size(2 * rMax, 2 * rMax)
            )
            drawLine(tool.c2, c, polar(engine.heading, rMax), 3f, cap = StrokeCap.Round)
            if (running && engine.nearest > 0f) {
                val p = polar(engine.heading, engine.nearest / MAX_RANGE * rMax)
                drawCircle(Color.White.copy(alpha = 0.3f), 18f, p)
                drawCircle(Color.White, 7f, p)
            }
            drawCircle(Neon.Text, 9f, c)
            drawCircle(tool.c1, 9f, c, style = Stroke(3f))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            when {
                !running -> "Sonar is off"
                !heard -> "🔇 Can't hear my own ping — turn the media volume up"
                else -> "🦇 Pinging… turn slowly in a full circle"
            },
            color = if (running && !heard) Neon.Orange else Neon.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))

        // ---------- A-scan: echo strength vs distance right now ----------
        GlassCard(Modifier.fillMaxWidth(), glow = tool.c2) {
            Text("ECHOES IN FRONT OF YOU", color = Neon.TextDim, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.height(6.dp))
            Canvas(Modifier.fillMaxWidth().height(90.dp)) {
                tick
                val cur = engine.current
                val top = max(engine.threshold * 3f, cur.maxOrNull() ?: 0f).coerceAtLeast(1e-4f)
                val dx = size.width / NBINS
                drawRoundRect(Color.White.copy(alpha = 0.03f), cornerRadius = CornerRadius(16f, 16f))
                val thrY = size.height * (1f - (engine.threshold / top).coerceIn(0f, 1f))
                drawLine(Neon.Orange.copy(alpha = 0.5f), Offset(0f, thrY), Offset(size.width, thrY), 1.5f)
                for (b in 0 until NBINS) {
                    val h = (cur[b] / top).coerceIn(0f, 1f) * size.height
                    val col = if (cur[b] > engine.threshold) tool.c1 else tool.c1.copy(alpha = 0.3f)
                    drawRect(col, Offset(b * dx, size.height - h), Size(max(1f, dx - 1f), h))
                }
                val blindW = BLIND / MAX_RANGE * size.width
                drawRect(Color.Black.copy(alpha = 0.35f), Offset.Zero, Size(blindW, size.height))
            }
            Row(Modifier.fillMaxWidth()) {
                Text("0", color = Neon.TextDim, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.weight(1f))
                Text("2.5 m", color = Neon.TextDim, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.weight(1f))
                Text("5 m", color = Neon.TextDim, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlowButton(if (running) "■ Stop sonar" else "🦇 Start sonar", tool.c1, tool.c2, Modifier.weight(1.3f)) { running = !running }
            GlowButton("🎯 Re-aim", Neon.SurfaceHi, Neon.Cyan, Modifier.weight(1f)) { yaw0 = yawNow; engine.reset() }
            GlowButton("🧽", Neon.SurfaceHi, Neon.Pink, Modifier.weight(0.5f)) { engine.reset() }
        }
        Spacer(Modifier.height(8.dp))
        Hint(
            "How to scan: stand in the middle of a room, volume up, hold the phone flat at chest height with the bottom edge (speaker + mic) pointing away from you. " +
                "Press Start, then turn slowly on the spot — about 10 seconds per full circle. Walls show up as a pink outline, everything that echoes as green dots. " +
                "It's bat-level resolution: big flat things (walls, doors, wardrobes) work best."
        )
        Spacer(Modifier.height(16.dp))
    }
}
