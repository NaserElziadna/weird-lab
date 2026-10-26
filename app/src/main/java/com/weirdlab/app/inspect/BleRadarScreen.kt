package com.weirdlab.app.inspect

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weirdlab.app.Tool
import com.weirdlab.app.ui.*
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin

enum class BleKind(val label: String, val emoji: String, val tracker: Boolean) {
    FIND_MY("Apple Find My tag / AirTag", "🍎", true),
    SMART_TAG("Samsung SmartTag", "🏷️", true),
    TILE("Tile tracker", "🔲", true),
    APPLE("Apple device", "🍏", false),
    SAMSUNG("Samsung device", "📱", false),
    GOOGLE("Google Fast Pair (earbuds?)", "🎧", false),
    MICROSOFT("Windows PC", "💻", false),
    OTHER("Bluetooth device", "🔵", false)
}

data class BleDevice(
    val address: String,
    val name: String?,
    val rssi: Float,
    val txPower: Int,
    val kind: BleKind,
    val lastSeen: Long,
    val firstSeen: Long
) {
    /** Very rough distance from signal strength (log-distance path-loss model). */
    val meters: Float get() = 10f.pow((txPower - rssi) / (10f * 2.2f)).coerceIn(0.1f, 50f)
}

private val SMART_TAG_UUID = ParcelUuid.fromString("0000FD5A-0000-1000-8000-00805F9B34FB")
private val TILE_UUIDS = listOf("0000FEED-0000-1000-8000-00805F9B34FB", "0000FEEC-0000-1000-8000-00805F9B34FB").map(ParcelUuid::fromString)
private val FAST_PAIR_UUID = ParcelUuid.fromString("0000FE2C-0000-1000-8000-00805F9B34FB")

private fun classify(r: ScanResult): BleKind {
    val rec = r.scanRecord ?: return BleKind.OTHER
    val uuids = rec.serviceUuids.orEmpty()
    if (SMART_TAG_UUID in uuids || rec.getServiceData(SMART_TAG_UUID) != null) return BleKind.SMART_TAG
    if (uuids.any { it in TILE_UUIDS }) return BleKind.TILE
    rec.getManufacturerSpecificData(0x004C)?.let { d ->
        return if (d.isNotEmpty() && d[0].toInt() == 0x12) BleKind.FIND_MY else BleKind.APPLE
    }
    if (FAST_PAIR_UUID in uuids || rec.getServiceData(FAST_PAIR_UUID) != null) return BleKind.GOOGLE
    if (rec.getManufacturerSpecificData(0x0075) != null) return BleKind.SAMSUNG
    if (rec.getManufacturerSpecificData(0x0006) != null) return BleKind.MICROSOFT
    return BleKind.OTHER
}

@Composable
fun BleRadarScreen(onBack: () -> Unit) {
    val tool = Tool.BLE
    val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.ACCESS_FINE_LOCATION)
    else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    ToolScaffold(tool, onBack) {
        PermissionGate(
            tool, perms, "🔵",
            "To see nearby Bluetooth gadgets Android needs the 'Nearby devices' and location permissions. Nothing leaves your phone."
        ) { BleContent(tool) }
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun BleContent(tool: Tool) {
    val ctx = LocalContext.current
    val adapter = remember { (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter }
    val resumed = rememberIsResumed()
    val devices = remember { mutableStateMapOf<String, BleDevice>() }
    var target by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val beeper = rememberBeeper()

    val callback = remember {
        object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) = handle(result)
            override fun onBatchScanResults(results: MutableList<ScanResult>) = results.forEach { handle(it) }
            override fun onScanFailed(errorCode: Int) { error = "Scan failed (code $errorCode). Try toggling Bluetooth." }
            fun handle(r: ScanResult) {
                val addr = r.device.address
                val now = System.currentTimeMillis()
                val old = devices[addr]
                val name = r.scanRecord?.deviceName ?: try { r.device.name } catch (_: SecurityException) { null }
                val tx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && r.txPower != ScanResult.TX_POWER_NOT_PRESENT && r.txPower in -100..20) {
                    // advertised TX power is at 0 m; ~41 dB path loss to 1 m
                    r.txPower - 41
                } else -59
                val smooth = if (old == null) r.rssi.toFloat() else old.rssi * 0.7f + r.rssi * 0.3f
                devices[addr] = BleDevice(addr, name ?: old?.name, smooth, tx, classify(r), now, old?.firstSeen ?: now)
            }
        }
    }

    val btOn = adapter?.isEnabled == true
    DisposableEffect(resumed, btOn) {
        val scanner = adapter?.bluetoothLeScanner
        if (resumed && btOn && scanner != null) {
            try {
                scanner.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), callback)
            } catch (e: Exception) { error = e.message }
        }
        onDispose { try { scanner?.stopScan(callback) } catch (_: Exception) {} }
    }
    // forget devices not heard for 15 s
    LaunchedEffect(Unit) {
        while (true) {
            delay(2000)
            val cutoff = System.currentTimeMillis() - 15000
            devices.keys.filter { (devices[it]?.lastSeen ?: 0) < cutoff }.forEach { devices.remove(it) }
        }
    }
    // geiger-style beeps while hunting one device
    val targetDev = target?.let { devices[it] }
    LaunchedEffect(target) {
        while (target != null) {
            val d = target?.let { devices[it] }
            if (d != null) {
                beeper.click()
                val strength = ((d.rssi + 100f) / 60f).coerceIn(0f, 1f)
                delay((900 - strength * 820).toLong())
            } else delay(500)
        }
    }

    val transition = rememberInfiniteTransition(label = "ble")
    val sweep by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(3000, easing = LinearEasing)), label = "s")

    if (adapter == null || !btOn) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            GlassCard(glow = tool.c1) {
                Text("📴 Bluetooth is off", color = Neon.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Hint("Turn Bluetooth on from the quick settings, then come back.")
            }
        }
        return
    }

    val sorted = devices.values.sortedByDescending { it.rssi }
    val trackers = sorted.filter { it.kind.tracker }

    Column(Modifier.fillMaxSize()) {
        if (targetDev != null) {
            // ---------- hot / cold finder ----------
            val strength = ((targetDev.rssi + 100f) / 60f).coerceIn(0f, 1f)
            val col = when {
                strength > 0.75f -> Neon.Red
                strength > 0.5f -> Neon.Orange
                strength > 0.3f -> Neon.Yellow
                else -> Neon.Cyan
            }
            GlassCard(Modifier.fillMaxWidth(), glow = col) {
                Text("${targetDev.kind.emoji} ${targetDev.name ?: targetDev.kind.label}", color = Neon.Text, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                ArcGauge(strength, col, "%.1f".format(targetDev.meters), "metres (rough)", Modifier.size(200.dp).align(Alignment.CenterHorizontally))
                Text(
                    when {
                        strength > 0.75f -> "🔥 HOT — it's right here!"
                        strength > 0.5f -> "♨️ Warm — getting close"
                        strength > 0.3f -> "🌤 Lukewarm"
                        else -> "🧊 Cold — keep walking"
                    },
                    color = col, fontSize = 20.sp, fontWeight = FontWeight.Black, modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(8.dp))
                GlowButton("✕ Stop hunting", Neon.SurfaceHi, tool.c1, Modifier.fillMaxWidth()) { target = null }
            }
            Spacer(Modifier.height(8.dp))
            Hint("Walk around slowly — the beeps speed up as you get closer. Turn your body: your own body blocks Bluetooth, so the signal is strongest when you face the device.")
            Spacer(Modifier.height(8.dp))
        } else {
            // ---------- radar ----------
            Canvas(Modifier.fillMaxWidth().height(230.dp)) {
                val c = center
                val rMax = size.minDimension / 2f - 6f
                drawCircle(Brush.radialGradient(listOf(tool.c1.copy(alpha = 0.18f), Color.Transparent), c, rMax), rMax, c)
                for (m in listOf(1f, 3f, 10f)) {
                    val rr = radiusFor(m, rMax)
                    drawCircle(tool.c2.copy(alpha = 0.18f), rr, c, style = Stroke(1.5f))
                }
                drawArc(
                    Brush.sweepGradient(listOf(Color.Transparent, tool.c2.copy(alpha = 0.35f)), c),
                    sweep - 40f, 40f, true, Offset(c.x - rMax, c.y - rMax), Size(2 * rMax, 2 * rMax)
                )
                sorted.forEach { d ->
                    // Bluetooth gives distance only, so each device gets a fixed angle from its address
                    val ang = (d.address.hashCode() and 0xFFFF) / 65535f * 2f * PI.toFloat()
                    val rr = radiusFor(d.meters, rMax)
                    val p = Offset(c.x + cos(ang) * rr, c.y + sin(ang) * rr)
                    val col = if (d.kind.tracker) Neon.Pink else tool.c2
                    drawCircle(col.copy(alpha = 0.25f), 16f, p)
                    drawCircle(col, 7f, p)
                }
                drawCircle(Neon.Text, 8f, c)
            }
            Text("Rings: 1 m · 3 m · 10 m  (distance only — direction is decorative)", color = Neon.TextDim, fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Devices", "${sorted.size}", tool.c1, Modifier.weight(1f))
                StatTile("Trackers", "${trackers.size}", if (trackers.isEmpty()) Neon.Mint else Neon.Pink, Modifier.weight(1f))
            }
            if (trackers.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                GlassCard(Modifier.fillMaxWidth(), glow = Neon.Pink) {
                    Text("🕵️ Tracker tags nearby", color = Neon.Pink, fontWeight = FontWeight.Bold)
                    Hint("Usually they're your own or a family member's. If one keeps showing up wherever you go, tap it and hunt it down.")
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        error?.let { Text(it, color = Neon.Orange, fontSize = 12.sp) }
        Text("TAP A DEVICE TO HUNT IT", color = Neon.TextDim, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Spacer(Modifier.height(6.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(sorted, key = { it.address }) { d ->
                val sel = d.address == target
                GlassCard(
                    Modifier.fillMaxWidth().clickable { target = if (sel) null else d.address },
                    glow = if (d.kind.tracker) Neon.Pink else if (sel) Neon.Yellow else tool.c1
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(d.kind.emoji, fontSize = 24.sp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(d.name ?: d.kind.label, color = Neon.Text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                (if (d.name != null) d.kind.label + " · " else "") + "seen ${(System.currentTimeMillis() - d.firstSeen) / 1000}s",
                                color = Neon.TextDim, fontSize = 11.sp, maxLines = 1
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("%.1f m".format(d.meters), color = tool.c2, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            Text("${d.rssi.toInt()} dBm", color = Neon.TextDim, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }
    }
}

private fun radiusFor(m: Float, rMax: Float): Float {
    // log scale: 0.3 m → centre, 20 m → edge
    val f = (ln(m.coerceIn(0.3f, 20f) / 0.3f) / ln(20f / 0.3f)).coerceIn(0f, 1f)
    return 12f + f * (rMax - 12f)
}
