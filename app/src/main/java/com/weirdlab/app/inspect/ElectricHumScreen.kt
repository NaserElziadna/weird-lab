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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weirdlab.app.Tool
import com.weirdlab.app.sound.AudioKit
import com.weirdlab.app.sound.SR
import com.weirdlab.app.ui.*
import kotlin.math.max

private const val HARMONICS = 6

private val harmonicPaint = android.graphics.Paint().apply {
    color = android.graphics.Color.argb(170, 255, 255, 255); textSize = 24f; isAntiAlias = true
    textAlign = android.graphics.Paint.Align.CENTER
}

@Composable
fun ElectricHumScreen(onBack: () -> Unit) {
    val tool = Tool.HUMDET
    ToolScaffold(tool, onBack) {
        MicGate(tool) { HumDetectorContent(tool) }
    }
}

@Composable
private fun HumDetectorContent(tool: Tool) {
    val ctx = LocalContext.current
    val resumed = rememberIsResumed()
    val spec = remember { Spectrum(8192) }  // 5.4 Hz per bin
    val prominence = remember { FloatArray(HARMONICS) }
    val ring = remember { Ring(100) }
    var frame by remember { mutableIntStateOf(0) }
    var grid by remember { mutableIntStateOf(50) }
    var score by remember { mutableFloatStateOf(0f) }
    var peak by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(resumed) {
        if (!resumed) return@LaunchedEffect
        AudioKit.listen(8192, SR, AudioKit.rawSource(ctx)) { pcm ->
            spec.process(pcm)
            // how far each mains harmonic sticks out above its neighbourhood
            fun prom(f: Float): Float {
                val p = spec.peakDb(f, 3f, SR)
                val around = (spec.bandDb(f - 22f, f - 8f, SR) + spec.bandDb(f + 8f, f + 22f, SR)) / 2f
                return (p - around).coerceAtLeast(0f)
            }
            var s50 = 0f
            var s60 = 0f
            for (h in 1..HARMONICS) { s50 += prom(50f * h); s60 += prom(60f * h) }
            val base = if (s60 > s50 * 1.3f) 60 else 50
            grid = base
            var best = 0f
            for (h in 1..HARMONICS) {
                val v = prom(base.toFloat() * h)
                prominence[h - 1] = prominence[h - 1] * 0.6f + v * 0.4f
                best = max(best, prominence[h - 1])
            }
            score = best
            peak = max(peak * 0.998f, best)
            ring.push(best)
            frame++
        }
    }

    val frac = (score / 30f).coerceIn(0f, 1f)
    val col = when {
        frac > 0.6f -> Neon.Red
        frac > 0.35f -> Neon.Orange
        frac > 0.15f -> Neon.Yellow
        else -> Neon.Mint
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        ArcGauge(frac, col, "%.0f".format(score), "dB of $grid Hz hum", Modifier.size(210.dp))
        Text(
            when {
                frac > 0.6f -> "⚡ Loud electrical buzz!"
                frac > 0.35f -> "🔌 Clear mains hum"
                frac > 0.15f -> "〰️ Faint hum"
                else -> "✅ No hum"
            },
            color = col, fontSize = 20.sp, fontWeight = FontWeight.Black
        )
        Spacer(Modifier.height(12.dp))
        Text("MAINS HARMONICS ($grid Hz grid)", color = Neon.TextDim, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(Modifier.height(6.dp))
        Canvas(Modifier.fillMaxWidth().height(140.dp)) {
            frame
            drawRoundRect(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.04f), cornerRadius = CornerRadius(24f, 24f))
            val bw = size.width / HARMONICS
            for (i in 0 until HARMONICS) {
                val h = (prominence[i] / 30f).coerceIn(0f, 1f) * (size.height - 40f)
                val x = i * bw + bw * 0.2f
                drawRoundRect(
                    Brush.verticalGradient(listOf(tool.c1, tool.c2), size.height - 30f - h, size.height - 30f),
                    Offset(x, size.height - 30f - h), Size(bw * 0.6f, max(h, 4f)), CornerRadius(12f, 12f)
                )
                drawContext.canvas.nativeCanvas.drawText("${grid * (i + 1)}", x + bw * 0.3f, size.height - 6f, harmonicPaint)
            }
        }
        Spacer(Modifier.height(10.dp))
        LiveGraph(ring, frame.toLong(), col, Modifier.fillMaxWidth().height(70.dp), lo = 0f, hi = 30f)
        Spacer(Modifier.height(10.dp))
        SpectrumView(spec, frame, tool.c1, 30f, 1000f, Modifier.fillMaxWidth().height(100.dp), markers = (1..HARMONICS).map { (grid * it).toFloat() })
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("Loudest", "%.0f dB".format(peak), Neon.Orange, Modifier.weight(1f))
            GlowButton("↺ Reset", Neon.SurfaceHi, tool.c1, Modifier.weight(1f)) { peak = 0f }
        }
        Spacer(Modifier.height(8.dp))
        Hint(
            "Mains power alternates 50 times a second (60 in the Americas), and anything stressed by it — transformers, chargers, fluorescent ballasts, dimmers, loose connections — buzzes at 100, 150, 200 Hz. " +
                "Hold the phone's bottom edge near sockets, power strips, the electrical panel or appliances and compare. A socket or plug that buzzes AND feels warm should be checked by an electrician.\n" +
                "This hears buzzing; it can't tell if a wire is live — never rely on it for safety."
        )
        Spacer(Modifier.height(16.dp))
    }
}
