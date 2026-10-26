package com.weirdlab.app.inspect

import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weirdlab.app.Tool
import com.weirdlab.app.ui.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private data class PressureEvent(val time: Long, val pa: Float, val id: Int)

@Composable
fun DoorSentinelScreen(onBack: () -> Unit) {
    val tool = Tool.DOOR
    val ctx = LocalContext.current
    val resumed = rememberIsResumed()
    val beeper = rememberBeeper()
    @Suppress("DEPRECATION")
    val vibrator = remember { ctx.getSystemService(Vibrator::class.java) }

    val ring = remember { Ring(300) }
    var hPa by remember { mutableFloatStateOf(0f) }
    var slow by remember { mutableStateOf<Float?>(null) }   // slow average, in Pa
    var startAlt by remember { mutableStateOf<Float?>(null) }
    var altitude by remember { mutableFloatStateOf(0f) }
    var threshold by remember { mutableFloatStateOf(1.5f) }  // Pa
    var armed by remember { mutableStateOf(true) }
    var lastEvent by remember { mutableLongStateOf(0L) }
    var counter by remember { mutableIntStateOf(0) }
    val events = remember { mutableStateListOf<PressureEvent>() }

    val has = SensorListener(listOf(Sensor.TYPE_PRESSURE), resumed, SensorManager.SENSOR_DELAY_FASTEST) { e ->
        val p = e.values[0]
        hPa = p
        val pa = p * 100f
        val s = slow
        val avg = if (s == null) pa else s * 0.995f + pa * 0.005f
        slow = avg
        val hp = pa - avg
        ring.push(hp)
        altitude = SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, p)
        if (startAlt == null) startAlt = altitude
        val now = System.currentTimeMillis()
        if (armed && s != null && abs(hp) > threshold && now - lastEvent > 2500) {
            lastEvent = now
            counter++
            events.add(0, PressureEvent(now, hp, counter))
            if (events.size > 30) events.removeAt(events.size - 1)
            beeper.beep(150)
            try { vibrator?.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE)) } catch (_: Exception) {}
        }
    }
    val tick = rememberFrameTick()
    val floors = startAlt?.let { ((altitude - it) / 3f) } ?: 0f
    val recent = System.currentTimeMillis() - lastEvent < 2500

    ToolScaffold(tool, onBack) {
        if (!has) { Text("This phone has no barometer 😢", color = Neon.Text); return@ToolScaffold }
        Column(Modifier.fillMaxSize()) {
            GlassCard(Modifier.fillMaxWidth(), glow = if (recent) Neon.Pink else tool.c1) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (recent) "🚨" else if (armed) "🛡️" else "💤", fontSize = 42.sp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            if (recent) "Door / window moved!" else if (armed) "Watching the air…" else "Paused",
                            color = if (recent) Neon.Pink else tool.c1, fontSize = 20.sp, fontWeight = FontWeight.Black
                        )
                        Text("%.2f hPa".format(hPa), color = Neon.Text, fontFamily = FontFamily.Monospace, fontSize = 15.sp)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("PRESSURE WIGGLES (Pa)", color = Neon.TextDim, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.height(4.dp))
            LiveGraph(ring, tick, if (recent) Neon.Pink else tool.c1, Modifier.fillMaxWidth().height(110.dp), lo = -threshold * 2.5f, hi = threshold * 2.5f)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Events", "${events.size}", Neon.Pink, Modifier.weight(1f))
                StatTile("Floors moved", "%+.1f".format(floors), tool.c2, Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NeonChip("🦇 Sensitive", threshold == 0.8f, tool.c1) { threshold = 0.8f }
                NeonChip("Normal", threshold == 1.5f, tool.c1) { threshold = 1.5f }
                NeonChip("🐘 Calm", threshold == 3f, tool.c1) { threshold = 3f }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlowButton(if (armed) "⏸ Pause" else "🛡️ Arm", tool.c1, tool.c2, Modifier.weight(1f)) { armed = !armed }
                GlowButton("🧽 Clear", Neon.SurfaceHi, Neon.Pink, Modifier.weight(1f)) { events.clear(); startAlt = altitude }
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (events.isEmpty()) item {
                    Hint(
                        "Leave the phone still in a closed room, then open or close the door — the air gets squeezed or pulled and the barometer feels it, even from across the room. " +
                            "Works best with windows shut and AC off. Also walk up or down stairs to watch 'Floors moved' change."
                    )
                }
                items(events, key = { it.id }) { ev ->
                    GlassCard(Modifier.fillMaxWidth(), glow = Neon.Pink) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (ev.pa > 0) "⬆️" else "⬇️", fontSize = 20.sp)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                (if (ev.pa > 0) "Air pushed in (door closed?)" else "Air pulled out (door opened?)"),
                                color = Neon.Text, modifier = Modifier.weight(1f), fontSize = 14.sp
                            )
                            Text(
                                SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(ev.time)) + "\n" + "%+.1f Pa".format(ev.pa),
                                color = Neon.TextDim, fontSize = 11.sp, fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}

@Suppress("unused")
private fun Float.r1() = (this * 10).roundToInt() / 10f
