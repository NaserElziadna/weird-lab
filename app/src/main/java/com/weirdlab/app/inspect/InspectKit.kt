package com.weirdlab.app.inspect

import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.weirdlab.app.Tool
import com.weirdlab.app.ui.*

/** Shows [content] once all [perms] are granted, otherwise a friendly ask card. */
@Composable
fun PermissionGate(tool: Tool, perms: Array<String>, emoji: String, why: String, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    fun allGranted() = perms.all { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }
    var granted by remember { mutableStateOf(allGranted()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = allGranted()
    }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(perms) }
    if (granted) content() else {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            GlassCard(glow = tool.c1) {
                Text(emoji, fontSize = 48.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(8.dp))
                Text(why, color = Neon.Text, textAlign = TextAlign.Center)
                Spacer(Modifier.height(14.dp))
                GlowButton("Allow", tool.c1, tool.c2, Modifier.fillMaxWidth()) { launcher.launch(perms) }
            }
        }
    }
}

/** Tiny beeper for "hot/cold" audio feedback. */
class Beeper {
    private val gen: ToneGenerator? = try { ToneGenerator(AudioManager.STREAM_MUSIC, 70) } catch (_: Exception) { null }
    fun click() { gen?.startTone(ToneGenerator.TONE_PROP_BEEP, 25) }
    fun beep(ms: Int = 60) { gen?.startTone(ToneGenerator.TONE_PROP_BEEP2, ms) }
    fun release() { gen?.release() }
}

@Composable
fun rememberBeeper(): Beeper {
    val b = remember { Beeper() }
    DisposableEffect(Unit) { onDispose { b.release() } }
    return b
}

/** Ring buffer of floats for live graphs. */
class Ring(val size: Int) {
    val data = FloatArray(size)
    var head = 0; private set
    var count = 0; private set
    fun push(v: Float) { head = (head + 1) % size; data[head] = v; if (count < size) count++ }
    operator fun get(ago: Int) = data[(head - ago + size * 2) % size]
    fun clear() { count = 0; data.fill(0f) }
}

/** Big semicircular gauge, [frac] 0..1. */
@Composable
fun ArcGauge(frac: Float, color: Color, value: String, unit: String, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.08f
            val tl = Offset(stroke, stroke)
            val sz = Size(size.width - 2 * stroke, size.width - 2 * stroke)
            drawArc(Color.White.copy(alpha = 0.08f), 150f, 240f, false, tl, sz, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(color.copy(alpha = 0.25f), 150f, 240f * frac.coerceIn(0f, 1f), false, tl, sz, style = Stroke(stroke * 2.2f, cap = StrokeCap.Round))
            drawArc(
                Brush.sweepGradient(listOf(color.copy(alpha = 0.6f), color, color.copy(alpha = 0.6f))),
                150f, 240f * frac.coerceIn(0f, 1f), false, tl, sz, style = Stroke(stroke, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = color, fontSize = 40.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
            Text(unit, color = Neon.TextDim, fontSize = 13.sp)
        }
    }
}

/** Scrolling line graph of a [Ring], auto-scaled unless [lo]/[hi] given. */
@Composable
fun LiveGraph(ring: Ring, tick: Long, color: Color, modifier: Modifier = Modifier, lo: Float? = null, hi: Float? = null) {
    Canvas(modifier) {
        tick
        drawRoundRect(Color.White.copy(alpha = 0.04f), cornerRadius = CornerRadius(24f, 24f))
        val n = ring.count
        if (n < 2) return@Canvas
        var mn = Float.MAX_VALUE
        var mx = -Float.MAX_VALUE
        for (i in 0 until n) { val v = ring[i]; if (v < mn) mn = v; if (v > mx) mx = v }
        val low = lo ?: mn
        val high = hi ?: mx
        val span = (high - low).takeIf { it > 1e-6f } ?: 1f
        val path = Path()
        val dx = size.width / (ring.size - 1)
        for (i in 0 until n) {
            val v = ring[n - 1 - i]
            val x = size.width - (n - 1 - i) * dx
            val y = size.height - ((v - low) / span).coerceIn(0f, 1f) * (size.height - 8f) - 4f
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color.copy(alpha = 0.25f), style = Stroke(9f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(path, color, style = Stroke(2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
