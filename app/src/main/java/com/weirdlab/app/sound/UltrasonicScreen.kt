package com.weirdlab.app.sound

import android.media.MediaRecorder
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weirdlab.app.Tool
import com.weirdlab.app.ui.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun UltrasonicScreen(onBack: () -> Unit) {
    val tool = Tool.ULTRA
    ToolScaffold(tool, onBack) {
        MicGate(tool) { UltrasonicContent(tool) }
    }
}

@Composable
private fun UltrasonicContent(tool: Tool) {
    val scope = rememberCoroutineScope()
    val resumed = rememberIsResumed()
    var audible by remember { mutableStateOf(false) }
    var listening by remember { mutableStateOf(true) }
    var text by remember { mutableStateOf("Salam from the bat cave 🦇") }
    var sending by remember { mutableStateOf(false) }
    var sendProgress by remember { mutableFloatStateOf(0f) }
    val messages = remember { mutableStateListOf<SonicMessage>() }

    val codec = remember(audible) { SonicCodec(audible) }
    val decoder = remember(codec) {
        SonicDecoder(codec) { msg -> scope.launch { messages.add(0, msg) } }
    }
    var spectrumTick by remember { mutableIntStateOf(0) }
    var receivingState by remember { mutableStateOf(false) }
    var symbols by remember { mutableIntStateOf(0) }

    LaunchedEffect(listening, resumed, decoder) {
        if (!listening || !resumed) return@LaunchedEffect
        AudioKit.listen(1024, SR, MediaRecorder.AudioSource.VOICE_RECOGNITION) { frame ->
            decoder.processFrame(frame)
            spectrumTick++
            receivingState = decoder.receiving
            symbols = decoder.symbolsSoFar
        }
    }

    var sendJob by remember { mutableStateOf<Job?>(null) }
    val bytes = text.toByteArray(Charsets.UTF_8).size

    // ---- radar visual ----
    val transition = rememberInfiniteTransition(label = "radar")
    val sweep by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "sweep")
    val ring by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "ring")

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                spectrumTick
                val c = center
                val r = size.minDimension / 2f - 4f
                drawCircle(Brush.radialGradient(listOf(tool.c1.copy(alpha = 0.18f), Color.Transparent), c, r), r, c)
                for (i in 1..3) drawCircle(tool.c1.copy(alpha = 0.18f), r * i / 3f, c, style = Stroke(1.5f))
                if (listening) {
                    rotate(sweep, c) {
                        drawArc(
                            Brush.sweepGradient(listOf(Color.Transparent, tool.c2.copy(alpha = 0.45f)), c),
                            -40f, 40f, true, topLeft = Offset(c.x - r, c.y - r), size = Size(2 * r, 2 * r)
                        )
                    }
                }
                if (sending || receivingState) {
                    val col = if (sending) tool.c1 else Neon.Pink
                    for (k in 0..2) {
                        val f = (ring + k / 3f) % 1f
                        drawCircle(col.copy(alpha = (1f - f) * 0.8f), r * f, c, style = Stroke(5f * (1f - f) + 1f))
                    }
                }
                // spectrum bars around the bottom
                val sp = decoder.spectrum
                val bw = size.width / sp.size
                for (i in sp.indices) {
                    val col = when {
                        i == SonicCodec.START -> Neon.Mint
                        i == SonicCodec.END -> Neon.Pink
                        SonicCodec.isA(i) -> tool.c1
                        else -> Neon.Purple
                    }
                    val h = sp[i] * size.height * 0.35f
                    drawRoundRect(
                        col.copy(alpha = 0.85f),
                        topLeft = Offset(i * bw + bw * 0.15f, size.height - h),
                        size = Size(bw * 0.7f, h),
                        cornerRadius = CornerRadius(4f, 4f)
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    when {
                        sending -> "📡 Transmitting…"
                        receivingState -> "📥 Receiving… $symbols"
                        listening -> "👂 Listening"
                        else -> "💤 Not listening"
                    },
                    color = Neon.Text, fontWeight = FontWeight.Bold, fontSize = 18.sp
                )
                Text(
                    if (audible) "Audible mode 3–6 kHz" else "Ultrasonic 16.7–19.7 kHz",
                    color = Neon.TextDim, fontSize = 12.sp
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { if (it.toByteArray(Charsets.UTF_8).size <= SonicCodec.MAX_BYTES) text = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Secret message") },
            supportingText = { Text("$bytes/${SonicCodec.MAX_BYTES} bytes · ≈${"%.1f".format(codec.durationSeconds(text))} s of sound · Arabic works too") },
            shape = RoundedCornerShape(18.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = tool.c1,
                unfocusedBorderColor = Neon.TextDim.copy(alpha = 0.4f),
                focusedLabelColor = tool.c1,
                cursorColor = tool.c1,
                focusedTextColor = Neon.Text,
                unfocusedTextColor = Neon.Text
            )
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlowButton(
                if (sending) "■ Stop (${(sendProgress * 100).toInt()}%)" else "📡 Send",
                tool.c1, tool.c2, Modifier.weight(1f), enabled = text.isNotEmpty()
            ) {
                if (sending) {
                    sendJob?.cancel()
                } else {
                    sendJob = scope.launch {
                        sending = true
                        try {
                            AudioKit.play(codec.encode(text)) { sendProgress = it }
                        } finally {
                            sending = false; sendProgress = 0f
                        }
                    }
                }
            }
            GlowButton(if (listening) "👂 On" else "👂 Off", Neon.SurfaceHi, if (listening) Neon.Mint else Neon.TextDim, Modifier.weight(0.6f)) {
                listening = !listening
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NeonChip("🔇 Ultrasonic", !audible, tool.c1) { audible = false }
            NeonChip("🔊 Audible", audible, Neon.Yellow) { audible = true }
        }
        Spacer(Modifier.height(10.dp))
        Text("INBOX", color = Neon.TextDim, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Spacer(Modifier.height(6.dp))
        if (messages.isEmpty()) {
            Hint(
                "Install Weird Lab on a second phone, put both on the same mode, volume up, ~30 cm apart, and hit Send. " +
                    "You can even test on one phone — it hears itself! If ultrasonic fails, your speaker or mic may cut off high pitches: try Audible."
            )
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(messages) { m ->
                GlassCard(Modifier.fillMaxWidth(), glow = if (m.checksumOk) Neon.Mint else Neon.Orange) {
                    Text(m.text, color = Neon.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        (if (m.checksumOk) "✓ Perfect reception" else "⚠ Some bits got lost") + " · " +
                            SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(m.timeMillis)),
                        color = if (m.checksumOk) Neon.Mint else Neon.Orange, fontSize = 12.sp
                    )
                }
            }
        }
    }
}
