package com.weirdlab.app.sound

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weirdlab.app.Tool
import com.weirdlab.app.ui.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val MAX_SEC = 15f
private const val BARS = 90

@Composable
fun ReverseScreen(onBack: () -> Unit) {
    val tool = Tool.REVERSE
    ToolScaffold(tool, onBack) {
        MicGate(tool) { ReverseContent(tool) }
    }
}

@Composable
private fun ReverseContent(tool: Tool) {
    val scope = rememberCoroutineScope()
    var recording by remember { mutableStateOf(false) }
    var stopRequested by remember { mutableStateOf(false) }
    var audio by remember { mutableStateOf(ShortArray(0)) }
    val liveLevels = remember { mutableStateListOf<Float>() }
    var progress by remember { mutableFloatStateOf(-1f) }
    var playingReverse by remember { mutableStateOf(false) }
    var speed by remember { mutableFloatStateOf(1f) }
    var playJob by remember { mutableStateOf<Job?>(null) }
    var elapsed by remember { mutableFloatStateOf(0f) }

    val bars = remember(audio) { AudioKit.envelope(audio, BARS) }

    fun play(reverse: Boolean) {
        if (audio.isEmpty()) return
        playJob?.cancel()
        playingReverse = reverse
        val data = if (reverse) audio.reversedArray() else audio
        playJob = scope.launch {
            try {
                AudioKit.play(data, (SR * speed).toInt()) { progress = it }
            } finally {
                progress = -1f
            }
        }
    }

    fun toggleRecord() {
        if (recording) { stopRequested = true; return }
        playJob?.cancel()
        stopRequested = false
        recording = true
        liveLevels.clear()
        scope.launch {
            val result = AudioKit.record(
                maxSeconds = MAX_SEC,
                shouldStop = { stopRequested },
                onChunk = { buf, n, pos ->
                    val lvl = AudioKit.rms(buf, n)
                    elapsed = pos.toFloat() / SR
                    scope.launch { liveLevels.add(lvl); if (liveLevels.size > BARS) liveLevels.removeAt(0) }
                }
            )
            recording = false
            audio = result
        }
    }

    val transition = rememberInfiniteTransition(label = "rec")
    val blink by transition.animateFloat(0.3f, 1f, infiniteRepeatable(tween(600, easing = LinearEasing), androidx.compose.animation.core.RepeatMode.Reverse), label = "b")

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        GlassCard(Modifier.fillMaxWidth(), glow = tool.c1) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when {
                        recording -> "🔴 Recording  %.1fs".format(elapsed)
                        progress >= 0f && playingReverse -> "◀◀ sdrawkcab gniyalP"
                        progress >= 0f -> "▶ Playing forward"
                        audio.isEmpty() -> "Record something first"
                        else -> "%.1f s captured".format(audio.size.toFloat() / SR)
                    },
                    color = if (recording) Neon.Red.copy(alpha = blink) else Neon.Text,
                    fontWeight = FontWeight.Bold, fontSize = 16.sp
                )
            }
            Spacer(Modifier.height(12.dp))
            Canvas(Modifier.fillMaxWidth().height(150.dp)) {
                val levels: List<Float> = if (recording) liveLevels.map { (it * 4f).coerceAtMost(1f) } else bars.toList()
                val n = BARS
                val bw = size.width / n
                val mid = size.height / 2f
                // when playing in reverse the playhead travels right → left
                val headX = if (progress < 0f) -1f else if (playingReverse) size.width * (1f - progress) else size.width * progress
                val offset = (n - levels.size).coerceAtLeast(0) // live bars grow in from the right
                for (i in 0 until n) {
                    val v = if (i >= offset) levels.getOrElse(i - offset) { 0f } else 0f
                    val h = (v * size.height * 0.95f).coerceAtLeast(3f)
                    val x = i * bw
                    val played = headX >= 0f && (if (playingReverse) x > headX else x < headX)
                    val base = lerp(tool.c1, tool.c2, i / n.toFloat())
                    val col = if (headX < 0f || played) base else base.copy(alpha = 0.35f)
                    drawRoundRect(col, Offset(x + bw * 0.2f, mid - h / 2f), Size(bw * 0.6f, h), CornerRadius(bw, bw))
                }
                if (headX >= 0f) {
                    drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.25f), Color.Transparent), headX - 30f, headX + 30f), Offset(headX - 30f, 0f), Size(60f, size.height))
                    drawLine(Color.White, Offset(headX, 0f), Offset(headX, size.height), 3f)
                }
            }
        }
        Spacer(Modifier.height(18.dp))

        // Record button
        GlowButton(
            if (recording) "■  Stop recording" else "🎙  Tap to record",
            if (recording) Neon.Red else tool.c1, if (recording) Neon.Orange else tool.c2,
            Modifier.fillMaxWidth().height(64.dp)
        ) { toggleRecord() }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlowButton("▶ Forward", Neon.SurfaceHi, Neon.Cyan, Modifier.weight(1f), enabled = audio.isNotEmpty() && !recording) { play(false) }
            GlowButton("◀ Reverse", Neon.Pink, Neon.Purple, Modifier.weight(1f), enabled = audio.isNotEmpty() && !recording) { play(true) }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0.6f to "🐢 0.6×", 1f to "1×", 1.5f to "1.5×", 2f to "🐿️ 2×").forEach { (s, label) ->
                NeonChip(label, speed == s, tool.c1) { speed = s }
            }
        }
        Spacer(Modifier.height(16.dp))
        GlassCard(Modifier.fillMaxWidth(), glow = tool.c2) {
            Text("🎮 Reverse challenge", color = tool.c2, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(4.dp))
            Hint(
                "1. Record a word, play it in reverse.\n" +
                    "2. Record YOURSELF imitating that reversed gibberish.\n" +
                    "3. Reverse your imitation — if you nailed it, the original word comes back! 🤯\n" +
                    "Try your name, \"banana\" or a whole sentence."
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}
