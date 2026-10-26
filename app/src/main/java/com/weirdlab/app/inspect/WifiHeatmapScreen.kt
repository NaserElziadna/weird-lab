package com.weirdlab.app.inspect

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.WifiManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weirdlab.app.Tool
import com.weirdlab.app.ui.*
import kotlinx.coroutines.delay

private const val COLS = 8
private const val ROWS = 11

private fun rssiColor(dbm: Float): Color {
    val f = ((dbm + 90f) / 40f).coerceIn(0f, 1f) // -90 → 0, -50 → 1
    return when {
        f < 0.33f -> lerp(Neon.Red, Neon.Orange, f / 0.33f)
        f < 0.66f -> lerp(Neon.Orange, Neon.Yellow, (f - 0.33f) / 0.33f)
        else -> lerp(Neon.Yellow, Neon.Mint, (f - 0.66f) / 0.34f)
    }
}

private fun quality(dbm: Int) = when {
    dbm <= -120 -> "Not connected" to "🚫"
    dbm >= -55 -> "Excellent" to "🚀"
    dbm >= -67 -> "Good – video calls OK" to "👍"
    dbm >= -75 -> "Fair – may buffer" to "😐"
    dbm >= -85 -> "Weak" to "🐢"
    else -> "Dead zone" to "💀"
}

private val cellLabelPaint = android.graphics.Paint().apply {
    color = android.graphics.Color.argb(230, 10, 10, 26); textSize = 26f; isAntiAlias = true; isFakeBoldText = true
    textAlign = android.graphics.Paint.Align.CENTER
}

@Composable
fun WifiHeatmapScreen(onBack: () -> Unit) {
    val tool = Tool.WIFI
    ToolScaffold(tool, onBack) {
        PermissionGate(
            tool, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), "📶",
            "Android only shows your Wi-Fi name to apps with location permission. Nothing leaves your phone."
        ) { WifiContent(tool) }
    }
}

@SuppressLint("MissingPermission")
@Suppress("DEPRECATION")
@Composable
private fun WifiContent(tool: Tool) {
    val ctx = LocalContext.current
    val wm = remember { ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager }
    val resumed = rememberIsResumed()
    var rssi by remember { mutableIntStateOf(-127) }
    var ssid by remember { mutableStateOf("") }
    var speed by remember { mutableIntStateOf(0) }
    val ring = remember { Ring(120) }
    val samples = remember { mutableStateMapOf<Int, Float>() } // cell index → avg dBm
    val tick = rememberFrameTick()

    LaunchedEffect(resumed) {
        while (resumed) {
            val info = wm.connectionInfo
            rssi = if (info != null && info.networkId != -1) info.rssi else -127
            ssid = info?.ssid?.trim('"')?.takeIf { it != "<unknown ssid>" } ?: ""
            speed = info?.linkSpeed ?: 0
            if (rssi > -120) ring.push(rssi.toFloat())
            delay(400)
        }
    }

    fun recentAverage(): Float {
        val n = minOf(ring.count, 8)
        if (n == 0) return rssi.toFloat()
        var s = 0f
        for (i in 0 until n) s += ring[i]
        return s / n
    }

    val (label, emoji) = quality(rssi)
    val color = if (rssi > -120) rssiColor(rssi.toFloat()) else Neon.TextDim

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ArcGauge(
                ((rssi + 95f) / 60f), color,
                if (rssi > -120) "$rssi" else "—", "dBm", Modifier.size(150.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text("$emoji $label", color = color, fontSize = 20.sp, fontWeight = FontWeight.Black)
                Text(if (ssid.isNotEmpty()) "Network: $ssid" else "Connected network", color = Neon.Text, fontSize = 13.sp)
                if (speed > 0) Text("Link speed: $speed Mbps", color = Neon.TextDim, fontSize = 13.sp)
            }
        }
        LiveGraph(ring, tick, color, Modifier.fillMaxWidth().height(60.dp), lo = -95f, hi = -30f)
        Spacer(Modifier.height(12.dp))

        Text("YOUR FLOOR MAP", color = Neon.TextDim, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Spacer(Modifier.height(6.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .aspectRatio(COLS / ROWS.toFloat())
                .pointerInput(Unit) {
                    detectTapGestures { p ->
                        val cw = size.width / COLS.toFloat()
                        val ch = size.height / ROWS.toFloat()
                        val c = (p.x / cw).toInt().coerceIn(0, COLS - 1)
                        val r = (p.y / ch).toInt().coerceIn(0, ROWS - 1)
                        val idx = r * COLS + c
                        if (samples.containsKey(idx)) samples.remove(idx)
                        else if (rssi > -120) samples[idx] = recentAverage()
                    }
                }
        ) {
            val cw = size.width / COLS
            val ch = size.height / ROWS
            drawRoundRect(Color.White.copy(alpha = 0.04f), cornerRadius = CornerRadius(24f, 24f))
            val pts = samples.entries.map { (idx, v) -> Triple(idx % COLS, idx / COLS, v) }
            // inverse-distance-weighted heat for every cell
            for (r in 0 until ROWS) for (c in 0 until COLS) {
                if (pts.isEmpty()) break
                var wsum = 0f
                var vsum = 0f
                var exact: Float? = null
                for ((pc, pr, v) in pts) {
                    val d2 = ((pc - c) * (pc - c) + (pr - r) * (pr - r)).toFloat()
                    if (d2 == 0f) { exact = v; break }
                    val w = 1f / (d2 * d2)
                    wsum += w; vsum += w * v
                }
                val v = exact ?: (vsum / wsum)
                val nearest = pts.minOf { (pc, pr, _) -> (pc - c) * (pc - c) + (pr - r) * (pr - r) }
                val alpha = (0.85f - nearest * 0.06f).coerceIn(0.12f, 0.85f)
                drawRoundRect(
                    rssiColor(v).copy(alpha = alpha),
                    Offset(c * cw + 2f, r * ch + 2f), Size(cw - 4f, ch - 4f), CornerRadius(12f, 12f)
                )
            }
            // grid
            for (r in 0..ROWS) drawLine(Color.White.copy(alpha = 0.06f), Offset(0f, r * ch), Offset(size.width, r * ch))
            for (c in 0..COLS) drawLine(Color.White.copy(alpha = 0.06f), Offset(c * cw, 0f), Offset(c * cw, size.height))
            // measured points
            for ((pc, pr, v) in pts) {
                val center = Offset(pc * cw + cw / 2, pr * ch + ch / 2)
                drawCircle(Color.White, minOf(cw, ch) * 0.36f, center)
                drawContext.canvas.nativeCanvas.drawText("${v.toInt()}", center.x, center.y + 9f, cellLabelPaint)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("Spots measured", "${samples.size}", tool.c1, Modifier.weight(1f))
            StatTile("Worst spot", samples.values.minOrNull()?.let { "${it.toInt()} dBm" } ?: "—", Neon.Red, Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        GlowButton("🧽 Clear map", Neon.SurfaceHi, tool.c1, Modifier.fillMaxWidth()) { samples.clear() }
        Spacer(Modifier.height(8.dp))
        Hint(
            "Imagine the grid is your home's floor plan. Walk to a spot, wait 3 seconds, then tap the matching square to pin the signal there. " +
                "Pin 6–10 spots and the colours fill in: green = strong, red = dead zone. Tap a pin again to remove it. Great for deciding where the router or a mesh extender should go."
        )
        Spacer(Modifier.height(16.dp))
    }
}
