package com.weirdlab.app.inspect

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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weirdlab.app.Tool
import com.weirdlab.app.sound.AudioKit
import com.weirdlab.app.sound.SR
import com.weirdlab.app.ui.*
import kotlinx.coroutines.delay
import kotlin.math.ln
import kotlin.math.max

/** Log-frequency spectrum with optional highlighted band. */
@Composable
fun SpectrumView(
    spec: Spectrum, tick: Any, color: Color, fMin: Float, fMax: Float,
    modifier: Modifier = Modifier, band: Pair<Float, Float>? = null, markers: List<Float> = emptyList()
) {
    Canvas(modifier) {
        tick
        drawRoundRect(Color.White.copy(alpha = 0.04f), cornerRadius = CornerRadius(24f, 24f))
        fun xOf(f: Float) = (ln(f / fMin) / ln(fMax / fMin)) * size.width
        band?.let { (a, b) ->
            drawRect(color.copy(alpha = 0.1f), Offset(xOf(a), 0f), Size(xOf(b) - xOf(a), size.height))
        }
        markers.forEach { f -> drawLine(Neon.Yellow.copy(alpha = 0.35f), Offset(xOf(f), 0f), Offset(xOf(f), size.height), 2f) }
        val path = Path()
        val fill = Path()
        val cols = 160
        val lo = -110f
        val hi = -20f
        fill.moveTo(0f, size.height)
        for (i in 0..cols) {
            val f0 = fMin * Math.pow((fMax / fMin).toDouble(), i / cols.toDouble()).toFloat()
            val f1 = fMin * Math.pow((fMax / fMin).toDouble(), (i + 1) / cols.toDouble()).toFloat()
            var m = -200f
            for (k in spec.bin(f0, SR)..spec.bin(f1, SR)) m = max(m, spec.db[k])
            val y = size.height * (1f - ((m - lo) / (hi - lo)).coerceIn(0f, 1f))
            val x = size.width * i / cols
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            fill.lineTo(x, y)
        }
        fill.lineTo(size.width, size.height); fill.close()
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.4f), Color.Transparent)))
        drawPath(path, color, style = Stroke(2.5f))
    }
}

@Composable
fun LeakFinderScreen(onBack: () -> Unit) {
    val tool = Tool.LEAK
    ToolScaffold(tool, onBack) {
        MicGate(tool) { LeakContent(tool) }
    }
}

@Composable
private fun LeakContent(tool: Tool) {
    val ctx = LocalContext.current
    val resumed = rememberIsResumed()
    val spec = remember { Spectrum(2048) }
    val ring = remember { Ring(150) }
    val beeper = rememberBeeper()
    var frame by remember { mutableIntStateOf(0) }
    var baseline by remember { mutableFloatStateOf(Float.NaN) }
    var score by remember { mutableFloatStateOf(0f) }
    var peak by remember { mutableFloatStateOf(0f) }
    var sound by remember { mutableStateOf(true) }

    LaunchedEffect(resumed) {
        if (!resumed) return@LaunchedEffect
        AudioKit.listen(2048, SR, AudioKit.rawSource(ctx)) { pcm ->
            spec.process(pcm)
            val hf = spec.bandDb(3000f, 12000f, SR)
            // auto-baseline: follows quiet moments instantly, creeps up slowly
            baseline = if (baseline.isNaN() || hf < baseline) hf else baseline + 0.006f
            val rel = (hf - baseline).coerceAtLeast(0f)
            score = score * 0.8f + rel * 0.2f
            peak = max(peak * 0.999f, score)
            ring.push(score)
            frame++
        }
    }
    LaunchedEffect(sound) {
        while (sound) {
            val s = score
            if (s > 3f) {
                beeper.click()
                delay((700 - (s.coerceAtMost(30f) / 30f) * 640).toLong())
            } else delay(200)
        }
    }

    val frac = (score / 30f).coerceIn(0f, 1f)
    val col = when {
        frac > 0.6f -> Neon.Red
        frac > 0.35f -> Neon.Orange
        frac > 0.15f -> Neon.Yellow
        else -> tool.c1
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        ArcGauge(frac, col, "%.0f".format(score), "dB of hiss above quiet", Modifier.size(210.dp))
        Text(
            when {
                frac > 0.6f -> "💥 Strong hiss — very close!"
                frac > 0.35f -> "💧 Hissing — getting warmer"
                frac > 0.15f -> "〰️ Faint hiss"
                else -> "🤫 Quiet"
            },
            color = col, fontSize = 20.sp, fontWeight = FontWeight.Black
        )
        Spacer(Modifier.height(10.dp))
        LiveGraph(ring, frame.toLong(), col, Modifier.fillMaxWidth().height(70.dp), lo = 0f, hi = 30f)
        Spacer(Modifier.height(10.dp))
        Text("SOUND SPECTRUM · hiss band 3–12 kHz", color = Neon.TextDim, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(Modifier.height(4.dp))
        SpectrumView(spec, frame, tool.c1, 60f, 20000f, Modifier.fillMaxWidth().height(110.dp), band = 3000f to 12000f)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("Loudest", "%.0f dB".format(peak), Neon.Orange, Modifier.weight(1f))
            GlowButton(if (sound) "🔊 Clicks on" else "🔇 Clicks off", Neon.SurfaceHi, tool.c1, Modifier.weight(1f)) { sound = !sound }
        }
        Spacer(Modifier.height(8.dp))
        GlowButton("↺ Re-learn quiet", tool.c1, tool.c2, Modifier.fillMaxWidth()) { baseline = Float.NaN; peak = 0f }
        Spacer(Modifier.height(8.dp))
        Hint(
            "Turn off fans, AC and TV first. Then slowly move the phone's bottom edge along pipes, under sinks, around the toilet tank, " +
                "window and door frames (drafts whistle) or a car tyre valve. The clicks speed up as you get closer to a hiss.\n" +
                "⚠️ If you smell gas, don't search — get out and call the gas emergency line."
        )
        Spacer(Modifier.height(16.dp))
    }
}
