package com.weirdlab.app.inspect

import android.hardware.Sensor
import android.hardware.SensorManager
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weirdlab.app.Tool
import com.weirdlab.app.ui.*
import kotlinx.coroutines.delay
import kotlin.math.sqrt

@Composable
fun StudFinderScreen(onBack: () -> Unit) {
    val tool = Tool.STUD
    val resumed = rememberIsResumed()
    val beeper = rememberBeeper()
    val ring = remember { Ring(200) }
    val now = remember { FloatArray(3) }
    val base = remember { FloatArray(3) }
    var calibrated by remember { mutableStateOf(false) }
    var delta by remember { mutableFloatStateOf(0f) }
    var sensitivity by remember { mutableFloatStateOf(10f) } // µT for a full beep
    var samples by remember { mutableIntStateOf(0) }

    val has = SensorListener(listOf(Sensor.TYPE_MAGNETIC_FIELD), resumed, SensorManager.SENSOR_DELAY_GAME) { e ->
        now[0] = e.values[0]; now[1] = e.values[1]; now[2] = e.values[2]
        if (!calibrated) {
            // average the first readings as the "empty wall" baseline
            samples++
            for (i in 0..2) base[i] += (now[i] - base[i]) / samples
            if (samples >= 25) calibrated = true
            return@SensorListener
        }
        val dx = now[0] - base[0]; val dy = now[1] - base[1]; val dz = now[2] - base[2]
        delta = delta * 0.6f + sqrt(dx * dx + dy * dy + dz * dz) * 0.4f
        ring.push(delta)
    }
    LaunchedEffect(Unit) {
        while (true) {
            val f = delta / sensitivity
            if (calibrated && f > 0.25f) {
                if (f > 1f) beeper.beep(120) else beeper.click()
                delay((500 - (f.coerceAtMost(1f) * 420)).toLong())
            } else delay(150)
        }
    }
    val tick = rememberFrameTick()
    val frac = (delta / sensitivity).coerceIn(0f, 1f)
    val col = when {
        frac > 0.8f -> Neon.Red
        frac > 0.45f -> Neon.Orange
        frac > 0.2f -> Neon.Yellow
        else -> Neon.Mint
    }

    ToolScaffold(tool, onBack) {
        if (!has) { Text("No magnetometer on this phone 😢", color = Neon.Text); return@ToolScaffold }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            ArcGauge(frac, col, if (calibrated) "%.1f".format(delta) else "…", "µT change", Modifier.size(210.dp))
            Text(
                when {
                    !calibrated -> "Calibrating — hold still"
                    frac > 0.8f -> "🔩 METAL RIGHT HERE"
                    frac > 0.45f -> "🧲 Metal close"
                    frac > 0.2f -> "🤏 Something nearby"
                    else -> "🧱 Just wall"
                },
                color = col, fontSize = 22.sp, fontWeight = FontWeight.Black
            )
            Spacer(Modifier.height(12.dp))
            // vertical "fill" bar like a classic stud finder
            Canvas(Modifier.fillMaxWidth().height(46.dp)) {
                tick
                drawRoundRect(Color.White.copy(alpha = 0.06f), cornerRadius = CornerRadius(30f, 30f))
                val w = size.width * frac
                drawRoundRect(
                    Brush.horizontalGradient(listOf(Neon.Mint, Neon.Yellow, Neon.Orange, Neon.Red), 0f, size.width),
                    Offset.Zero, Size(w, size.height), CornerRadius(30f, 30f)
                )
            }
            Spacer(Modifier.height(10.dp))
            LiveGraph(ring, tick, col, Modifier.fillMaxWidth().height(80.dp), lo = 0f, hi = sensitivity * 1.2f)
            Spacer(Modifier.height(10.dp))
            Text("SENSITIVITY", color = Neon.TextDim, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NeonChip("🐘 Low", sensitivity == 30f, tool.c1) { sensitivity = 30f }
                NeonChip("🐕 Medium", sensitivity == 10f, tool.c1) { sensitivity = 10f }
                NeonChip("🦇 High", sensitivity == 4f, tool.c1) { sensitivity = 4f }
            }
            Spacer(Modifier.height(10.dp))
            GlowButton("🎯 Re-calibrate here", tool.c1, tool.c2, Modifier.fillMaxWidth()) {
                samples = 0; base.fill(0f); calibrated = false; delta = 0f; ring.clear()
            }
            Spacer(Modifier.height(8.dp))
            Hint(
                "First find your phone's magnetic sensor: move a coin-sized magnet or a steel key around the phone — where the number jumps most is the sensor, lead with that corner. " +
                    "Calibrate with the phone flat on an empty part of the wall, then slide it slowly sideways without rotating it. " +
                    "Screws and nails in studs, steel drywall profiles and iron pipes make it beep; wood, plastic and copper are invisible to it."
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}
