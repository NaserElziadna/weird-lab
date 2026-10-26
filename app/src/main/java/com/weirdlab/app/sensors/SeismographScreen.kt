package com.weirdlab.app.sensors

import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weirdlab.app.Tool
import com.weirdlab.app.ui.*
import kotlin.math.max
import kotlin.math.sqrt

private val labelPaint = android.graphics.Paint().apply {
    color = android.graphics.Color.WHITE; textSize = 22f; isFakeBoldText = true; isAntiAlias = true
}

private const val N = 700 // samples on screen (~3.5 s at 200 Hz)

private class SeismoData {
    val x = FloatArray(N)
    val y = FloatArray(N)
    val z = FloatArray(N)
    val mag = FloatArray(N)
    var head = 0
    val gravity = FloatArray(3)
    var primed = 0
    var maxEver = 0f

    fun push(ax: Float, ay: Float, az: Float) {
        // simple high-pass: subtract a slow low-pass estimate of gravity
        val a = if (primed < 50) 0.8f else 0.985f
        primed++
        gravity[0] = a * gravity[0] + (1 - a) * ax
        gravity[1] = a * gravity[1] + (1 - a) * ay
        gravity[2] = a * gravity[2] + (1 - a) * az
        if (primed < 60) return
        // convert m/s² to milli-g
        val k = 1000f / SensorManager.GRAVITY_EARTH
        val hx = (ax - gravity[0]) * k
        val hy = (ay - gravity[1]) * k
        val hz = (az - gravity[2]) * k
        head = (head + 1) % N
        x[head] = hx; y[head] = hy; z[head] = hz
        val m = sqrt(hx * hx + hy * hy + hz * hz)
        mag[head] = m
        if (m > maxEver) maxEver = m
    }

    fun recentPeak(count: Int = 200): Float {
        var p = 0f
        for (i in 0 until count) p = max(p, mag[(head - i + N) % N])
        return p
    }
}

private data class Level(val name: String, val emoji: String, val color: Color)

private fun levelFor(mg: Float) = when {
    mg < 3f -> Level("Dead calm", "😴", Neon.Mint)
    mg < 12f -> Level("Footsteps", "👣", Neon.Cyan)
    mg < 50f -> Level("Door slam", "🚪", Neon.Yellow)
    mg < 200f -> Level("Table thump", "👊", Neon.Orange)
    mg < 700f -> Level("EARTHQUAKE!", "🌋", Neon.Red)
    else -> Level("Phone in a blender", "🌀", Neon.Pink)
}

@Composable
fun SeismographScreen(onBack: () -> Unit) {
    val tool = Tool.SEISMO
    val data = remember { SeismoData() }
    var paused by remember { mutableStateOf(false) }
    var gain by remember { mutableFloatStateOf(1f) }
    val resumed = rememberIsResumed()

    val available = SensorListener(
        listOf(Sensor.TYPE_ACCELEROMETER), active = resumed && !paused, rate = SensorManager.SENSOR_DELAY_FASTEST
    ) { e -> data.push(e.values[0], e.values[1], e.values[2]) }

    val tick = rememberFrameTick(!paused)
    // Slower-updating readouts so the numbers are readable
    var peak by remember { mutableFloatStateOf(0f) }
    var record by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(paused) {
        while (!paused) {
            peak = data.recentPeak()
            record = data.maxEver
            kotlinx.coroutines.delay(150)
        }
    }
    val level = levelFor(peak)

    ToolScaffold(tool, onBack) {
        if (!available) {
            Text("No accelerometer found 😢", color = Neon.Text)
            return@ToolScaffold
        }
        // Big status
        GlassCard(Modifier.fillMaxWidth(), glow = level.color) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(level.emoji, fontSize = 44.sp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(level.name, color = level.color, fontSize = 24.sp, fontWeight = FontWeight.Black)
                    Text(
                        "%.1f milli-g".format(peak),
                        color = Neon.Text, fontFamily = FontFamily.Monospace, fontSize = 15.sp
                    )
                }
                MagnitudeMeter(peak, level.color, Modifier.size(64.dp))
            }
        }
        Spacer(Modifier.height(12.dp))

        // Drum paper with three traces
        Canvas(
            Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            tick // redraw every frame
            drawPaper(tool.c1)
            val laneH = size.height / 3f
            val colors = listOf(Neon.Red, Neon.Yellow, Neon.Cyan)
            val arrays = listOf(data.x, data.y, data.z)
            val labels = listOf("X", "Y", "Z")
            arrays.forEachIndexed { lane, arr ->
                val mid = laneH * lane + laneH / 2f
                val scale = laneH / 2f / (60f / gain)
                drawTrace(arr, data.head, mid, scale, laneH / 2f * 0.95f, colors[lane])
                drawCircle(colors[lane].copy(alpha = 0.25f), 16f, Offset(28f, mid - laneH / 2f + 26f))
                drawContext.canvas.nativeCanvas.drawText(
                    labels[lane], 21f, mid - laneH / 2f + 33f, labelPaint
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("Biggest shake", "%.0f mg".format(record), Neon.Orange, Modifier.weight(1f))
            StatTile("Sensitivity", "×${gain.toInt()}", Neon.Cyan, Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(1f, 5f, 20f, 60f).forEach { g ->
                NeonChip("×${g.toInt()}", gain == g, tool.c1) { gain = g }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlowButton(if (paused) "▶ Resume" else "⏸ Freeze", tool.c1, tool.c2, Modifier.weight(1f)) { paused = !paused }
            GlowButton("↺ Reset record", Neon.SurfaceHi, Neon.Purple, Modifier.weight(1f)) {
                data.maxEver = 0f; record = 0f
            }
        }
        Spacer(Modifier.height(6.dp))
        Hint("Lay the phone flat on a table, then tap the table, walk nearby or drop a coin. Use ×60 to see your own heartbeat through the table!")
        Spacer(Modifier.height(8.dp))
    }
}

private fun DrawScope.drawPaper(accent: Color) {
    drawRoundRect(
        Brush.verticalGradient(listOf(Color(0xFF0F0F26), Color(0xFF15102C))),
        cornerRadius = CornerRadius(36f, 36f)
    )
    val grid = Color.White.copy(alpha = 0.05f)
    var gx = 0f
    while (gx < size.width) { drawLine(grid, Offset(gx, 0f), Offset(gx, size.height), 1f); gx += 40f }
    var gy = 0f
    while (gy < size.height) { drawLine(grid, Offset(0f, gy), Offset(size.width, gy), 1f); gy += 40f }
    for (lane in 1..2) {
        val y = size.height / 3f * lane
        drawLine(accent.copy(alpha = 0.25f), Offset(0f, y), Offset(size.width, y), 2f)
    }
}

private fun DrawScope.drawTrace(arr: FloatArray, head: Int, mid: Float, scale: Float, clamp: Float, color: Color) {
    val path = Path()
    val dx = size.width / (N - 1)
    var lastY = mid
    for (i in 0 until N) {
        val idx = (head - (N - 1 - i) + N) % N
        val v = (arr[idx] * scale).coerceIn(-clamp, clamp)
        val px = i * dx
        val py = mid - v
        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        lastY = py
    }
    drawPath(path, color.copy(alpha = 0.22f), style = Stroke(10f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(path, color, style = Stroke(2.6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    // pen tip
    drawCircle(color.copy(alpha = 0.35f), 16f, Offset(size.width - 4f, lastY))
    drawCircle(Color.White, 5f, Offset(size.width - 4f, lastY))
}

@Composable
private fun MagnitudeMeter(mg: Float, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val frac = (kotlin.math.log10(1f + mg) / 3f).coerceIn(0f, 1f)
        val stroke = 10f
        drawArc(
            Color.White.copy(alpha = 0.1f), 135f, 270f, false,
            style = Stroke(stroke, cap = StrokeCap.Round),
            topLeft = Offset(stroke, stroke),
            size = androidx.compose.ui.geometry.Size(size.width - 2 * stroke, size.height - 2 * stroke)
        )
        drawArc(
            color, 135f, 270f * frac, false,
            style = Stroke(stroke, cap = StrokeCap.Round),
            topLeft = Offset(stroke, stroke),
            size = androidx.compose.ui.geometry.Size(size.width - 2 * stroke, size.height - 2 * stroke)
        )
    }
}
