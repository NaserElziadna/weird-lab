package com.weirdlab.app.sensors

import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.RadialGradientShader
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weirdlab.app.Tool
import com.weirdlab.app.ui.*
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

/** Holds the painting surface – an offscreen bitmap we additively splat glowing dots into. */
private class FieldCanvas {
    var bitmap: ImageBitmap? = null
    private var canvas: androidx.compose.ui.graphics.Canvas? = null
    private val paint = Paint().apply { isAntiAlias = true; blendMode = BlendMode.Plus }
    var lastPoint: Offset? = null

    fun ensure(size: IntSize) {
        if (size.width <= 0 || size.height <= 0) return
        val b = bitmap
        if (b == null || b.width != size.width || b.height != size.height) {
            val nb = ImageBitmap(size.width, size.height)
            bitmap = nb
            canvas = androidx.compose.ui.graphics.Canvas(nb)
        }
    }

    fun clear() {
        val b = bitmap ?: return
        val nb = ImageBitmap(b.width, b.height)
        bitmap = nb
        canvas = androidx.compose.ui.graphics.Canvas(nb)
        lastPoint = null
    }

    /** Paint a soft dot, interpolating from the last point so fast moves leave a continuous trail. */
    fun splat(p: Offset, color: Color, radius: Float) {
        val c = canvas ?: return
        val from = lastPoint ?: p
        val dist = (p - from).getDistance()
        val steps = (dist / (radius * 0.35f)).toInt().coerceIn(1, 40)
        for (i in 1..steps) {
            val q = from + (p - from) * (i / steps.toFloat())
            paint.shader = RadialGradientShader(q, radius, listOf(color.copy(alpha = 0.55f), color.copy(alpha = 0.18f), Color.Transparent), listOf(0f, 0.45f, 1f))
            c.drawCircle(q, radius, paint)
        }
        lastPoint = p
    }
}

/** µT → glowing colour. Earth's field is ~25–65 µT, magnets are hundreds to thousands. */
fun fieldColor(ut: Float): Color {
    val stops = listOf(
        30f to Color(0xFF2A3BFF),
        60f to Neon.Cyan,
        120f to Neon.Mint,
        250f to Neon.Yellow,
        600f to Neon.Orange,
        1200f to Neon.Pink,
        2500f to Color.White
    )
    if (ut <= stops.first().first) return stops.first().second
    for (i in 1 until stops.size) {
        val (u1, c1) = stops[i]
        val (u0, c0) = stops[i - 1]
        if (ut <= u1) {
            val f = (ln(ut / u0) / ln(u1 / u0)).coerceIn(0f, 1f)
            return lerp(c0, c1, f)
        }
    }
    return stops.last().second
}

@Composable
fun MagnetPainterScreen(onBack: () -> Unit) {
    val tool = Tool.MAGNET
    val resumed = rememberIsResumed()
    val painter = remember { FieldCanvas() }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    var waveMode by remember { mutableStateOf(true) }   // true = point & wave phone, false = finger
    var painting by remember { mutableStateOf(true) }

    // live values
    val field = remember { FloatArray(3) }
    var strength by remember { mutableFloatStateOf(0f) }
    var maxStrength by remember { mutableFloatStateOf(0f) }

    // orientation (yaw/pitch) for the "laser pointer" brush
    val rot = remember { FloatArray(9) }
    val orient = remember { FloatArray(3) }
    var yaw by remember { mutableFloatStateOf(0f) }
    var pitch by remember { mutableFloatStateOf(0f) }
    var yaw0 by remember { mutableStateOf<Float?>(null) }
    var pitch0 by remember { mutableFloatStateOf(0f) }
    var brush by remember { mutableStateOf(Offset.Zero) }

    val hasMag = SensorListener(listOf(Sensor.TYPE_MAGNETIC_FIELD), resumed, SensorManager.SENSOR_DELAY_GAME) { e ->
        field[0] = e.values[0]; field[1] = e.values[1]; field[2] = e.values[2]
        strength = sqrt(field[0] * field[0] + field[1] * field[1] + field[2] * field[2])
        if (strength > maxStrength) maxStrength = strength
    }
    // GAME rotation vector ignores the magnetometer, so magnets don't make the brush jump around.
    SensorListener(listOf(Sensor.TYPE_GAME_ROTATION_VECTOR, Sensor.TYPE_ROTATION_VECTOR), resumed, SensorManager.SENSOR_DELAY_GAME) { e ->
        SensorManager.getRotationMatrixFromVector(rot, e.values)
        SensorManager.getOrientation(rot, orient)
        yaw = orient[0]; pitch = orient[1]
        if (yaw0 == null) { yaw0 = yaw; pitch0 = pitch }
        if (waveMode && canvasSize.width > 0) {
            var dy = yaw - (yaw0 ?: yaw)
            if (dy > PI) dy -= (2 * PI).toFloat()
            if (dy < -PI) dy += (2 * PI).toFloat()
            val dp = pitch - pitch0
            val span = (PI / 3).toFloat() // ±60° fills the canvas
            val x = canvasSize.width / 2f + (dy / span) * canvasSize.width / 2f
            val y = canvasSize.height / 2f + (dp / span) * canvasSize.height / 2f
            brush = Offset(x.coerceIn(0f, canvasSize.width.toFloat()), y.coerceIn(0f, canvasSize.height.toFloat()))
            if (painting) {
                painter.splat(brush, fieldColor(strength), radiusFor(strength))
            }
        }
    }

    val tick = rememberFrameTick()
    val strengthColor = fieldColor(strength)

    ToolScaffold(tool, onBack) {
        if (!hasMag) {
            Text("This phone has no magnetometer 😢", color = Neon.Text)
            return@ToolScaffold
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("FIELD STRENGTH", color = Neon.TextDim, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("%.0f".format(strength), color = strengthColor, fontSize = 44.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.width(6.dp))
                    Text("µT", color = Neon.TextDim, fontSize = 18.sp, modifier = Modifier.padding(bottom = 8.dp))
                }
                Text(describeField(strength), color = Neon.Text, fontSize = 13.sp)
            }
            FieldCompass(field, strengthColor, Modifier.size(96.dp))
        }
        Spacer(Modifier.height(10.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(26.dp))
                .onSizeChanged { canvasSize = it; painter.ensure(it) }
                .pointerInput(waveMode) {
                    if (!waveMode) {
                        detectDragGestures(
                            onDragStart = { painter.lastPoint = it },
                            onDragEnd = { painter.lastPoint = null }
                        ) { change, _ ->
                            brush = change.position
                            painter.splat(change.position, fieldColor(strength), radiusFor(strength))
                        }
                    }
                }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                tick
                drawRect(Brush.radialGradient(listOf(Color(0xFF15123A), Color(0xFF07071A))))
                // faint polar grid
                val c = center
                for (r in 1..4) drawCircle(Color.White.copy(alpha = 0.05f), size.minDimension / 2f * r / 4f, c, style = Stroke(1.5f))
                drawLine(Color.White.copy(alpha = 0.05f), Offset(0f, c.y), Offset(size.width, c.y))
                drawLine(Color.White.copy(alpha = 0.05f), Offset(c.x, 0f), Offset(c.x, size.height))
                painter.bitmap?.let { drawImage(it) }
                // brush cursor
                val pulse = 1f + 0.2f * sin(tick / 1.5e8f)
                drawCircle(strengthColor.copy(alpha = 0.25f), radiusFor(strength) * 1.3f * pulse, brush)
                drawCircle(Color.White, 6f, brush)
                drawCircle(strengthColor, radiusFor(strength) * pulse, brush, style = Stroke(3f))
            }
            if (painter.bitmap == null || (maxStrength == 0f)) {
                Text("Warming up sensors…", color = Neon.TextDim, modifier = Modifier.align(Alignment.Center))
            }
        }
        Spacer(Modifier.height(10.dp))
        ColorLegend()
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NeonChip("📱 Wave phone", waveMode, tool.c2) { waveMode = true; painter.lastPoint = null }
            NeonChip("☝️ Finger paint", !waveMode, tool.c1) { waveMode = false; painter.lastPoint = null }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlowButton(if (painting) "⏸ Lift brush" else "🖌 Paint", tool.c1, tool.c2, Modifier.weight(1f)) {
                painting = !painting; painter.lastPoint = null
            }
            GlowButton("🎯 Center", Neon.SurfaceHi, Neon.Cyan, Modifier.weight(1f)) {
                yaw0 = yaw; pitch0 = pitch; painter.lastPoint = null
            }
            GlowButton("🧽 Clear", Neon.SurfaceHi, Neon.Pink, Modifier.weight(1f)) {
                painter.clear(); maxStrength = 0f
            }
        }
        Spacer(Modifier.height(6.dp))
        Hint("Point the phone like a laser and sweep slowly over speakers, fridge magnets, a laptop hinge or a phone case clasp. Hot colours = strong hidden magnetic fields.")
        Spacer(Modifier.height(8.dp))
    }
}

private fun radiusFor(ut: Float) = (14f + 10f * ln(1f + ut / 40f)).coerceIn(14f, 70f)

private fun describeField(ut: Float) = when {
    ut < 25f -> "Weirdly quiet… are you in a metal box?"
    ut < 70f -> "Just Earth's magnetic field 🌍"
    ut < 150f -> "Something metallic nearby 🔩"
    ut < 500f -> "A magnet is close! 🧲"
    ut < 1500f -> "Strong magnet — speaker or clasp 🔊"
    else -> "Sensor maxed out ⚡"
}

@Composable
private fun ColorLegend() {
    Canvas(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp))) {
        val n = 60
        val w = size.width / n
        for (i in 0 until n) {
            val ut = 30f * Math.pow(2500.0 / 30.0, i / (n - 1.0)).toFloat()
            drawRect(fieldColor(ut), Offset(i * w, 0f), androidx.compose.ui.geometry.Size(w + 1f, size.height))
        }
    }
    Row(Modifier.fillMaxWidth()) {
        Hint("Earth 🌍", Modifier.weight(1f))
        Hint("Metal 🔩", Modifier.weight(1f))
        Hint("Magnet 🧲", Modifier.weight(1f))
        Hint("Max ⚡")
    }
}

/** Arrow showing which way the field points in the phone's own plane. */
@Composable
private fun FieldCompass(field: FloatArray, color: Color, modifier: Modifier) {
    val tick = rememberFrameTick()
    Canvas(modifier) {
        tick
        val c = center
        val r = size.minDimension / 2f - 6f
        drawCircle(Neon.Surface, r, c)
        drawCircle(color.copy(alpha = 0.5f), r, c, style = Stroke(3f))
        val angle = atan2(-field[1], field[0]) // screen y goes down
        val inPlane = sqrt(field[0] * field[0] + field[1] * field[1])
        val total = sqrt(inPlane * inPlane + field[2] * field[2]).coerceAtLeast(1f)
        val len = r * 0.85f * (0.3f + 0.7f * inPlane / total)
        val tip = Offset(c.x + cos(angle) * len, c.y + sin(angle) * len)
        val tail = Offset(c.x - cos(angle) * len * 0.5f, c.y - sin(angle) * len * 0.5f)
        drawLine(Neon.TextDim, tail, c, 5f, cap = StrokeCap.Round)
        drawLine(color, c, tip, 7f, cap = StrokeCap.Round)
        drawCircle(Color.White, 6f, c)
        drawCircle(color, 9f, tip)
    }
}
