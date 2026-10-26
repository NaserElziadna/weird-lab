package com.weirdlab.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.weirdlab.app.Tool
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private class Star(val x: Float, val y: Float, val r: Float, val phase: Float)

/** Deep-space background with two drifting colour nebulas and twinkling stars. */
@Composable
fun NeonBackground(
    accent: Color = Neon.Purple,
    accent2: Color = Neon.Cyan,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val transition = rememberInfiniteTransition(label = "bg")
    val t by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(24000, easing = LinearEasing)),
        label = "t"
    )
    val stars = remember { List(80) { Star(Random.nextFloat(), Random.nextFloat(), Random.nextFloat() * 1.8f + 0.6f, Random.nextFloat()) } }

    Box(modifier.fillMaxSize().background(Neon.Bg)) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val a = t * 2f * PI.toFloat()
            val c1 = Offset(w * (0.15f + 0.15f * sin(a)), h * (0.12f + 0.06f * cos(a)))
            val c2 = Offset(w * (0.85f + 0.12f * cos(a * 2)), h * (0.75f + 0.08f * sin(a)))
            drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.32f), Color.Transparent), c1, w * 0.9f), w * 0.9f, c1)
            drawCircle(Brush.radialGradient(listOf(accent2.copy(alpha = 0.22f), Color.Transparent), c2, w * 0.8f), w * 0.8f, c2)
            stars.forEach { s ->
                val tw = 0.3f + 0.7f * abs(sin(a * 4f + s.phase * 6.28f))
                val y = ((s.y + t * 0.04f * s.r) % 1f) * h
                drawCircle(Color.White.copy(alpha = 0.55f * tw), s.r, Offset(s.x * w, y))
            }
        }
        content()
    }
}

/** Text filled with a horizontal gradient. */
@Composable
fun GradientText(
    text: String,
    colors: List<Color>,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight = FontWeight.Black,
    textAlign: TextAlign? = null
) {
    Text(
        text = text,
        fontSize = fontSize,
        fontWeight = fontWeight,
        color = Color.White,
        textAlign = textAlign,
        modifier = modifier
            .graphicsLayer(alpha = 0.99f)
            .drawWithCache {
                val brush = Brush.horizontalGradient(colors)
                onDrawWithContent {
                    drawContent()
                    drawRect(brush, blendMode = BlendMode.SrcAtop)
                }
            }
    )
}

@Composable
fun GlowButton(
    text: String,
    c1: Color,
    c2: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.93f else 1f, label = "press")
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier
            .graphicsLayer {
                scaleX = scale; scaleY = scale
                alpha = if (enabled) 1f else 0.4f
            }
            .shadow(14.dp, shape, ambientColor = c1, spotColor = c1)
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(c1, c2)))
            .clickable(interaction, indication = null, enabled = enabled) { onClick() }
            .padding(horizontal = 18.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Neon.Ink, fontWeight = FontWeight.Bold, fontSize = 16.sp, textAlign = TextAlign.Center)
    }
}

/** Secondary pill-shaped toggle / chip. */
@Composable
fun NeonChip(text: String, selected: Boolean, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .clip(shape)
            .background(if (selected) color.copy(alpha = 0.22f) else Neon.Surface.copy(alpha = 0.6f))
            .border(1.dp, if (selected) color else Neon.TextDim.copy(alpha = 0.3f), shape)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (selected) color else Neon.TextDim, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    glow: Color = Neon.Purple,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier
            .clip(shape)
            .background(Neon.Surface.copy(alpha = 0.72f))
            .border(1.dp, Brush.linearGradient(listOf(glow.copy(alpha = 0.7f), glow.copy(alpha = 0.08f))), shape)
            .padding(16.dp),
        content = content
    )
}

@Composable
fun StatTile(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    GlassCard(modifier, glow = color) {
        Text(label.uppercase(), color = Neon.TextDim, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(Modifier.height(4.dp))
        Text(value, color = color, fontSize = 22.sp, fontWeight = FontWeight.Black)
    }
}

/** Common screen chrome: background, back button, gradient title. Keeps the screen awake. */
@Composable
fun ToolScaffold(tool: Tool, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    NeonBackground(tool.c1, tool.c2) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 10.dp)) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Neon.SurfaceHi.copy(alpha = 0.75f))
                        .border(1.dp, tool.c1.copy(alpha = 0.5f), CircleShape)
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) { Text("←", fontSize = 22.sp, color = Neon.Text) }
                Spacer(Modifier.width(12.dp))
                Text(tool.emoji, fontSize = 30.sp)
                Spacer(Modifier.width(10.dp))
                Column {
                    GradientText(tool.title, listOf(tool.c1, tool.c2), 22.sp)
                    Text(tool.tagline, color = Neon.TextDim, fontSize = 13.sp)
                }
            }
            content()
        }
    }
}

/** Shows [content] only once microphone permission is granted. */
@Composable
fun MicGate(tool: Tool, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(hasMic(ctx)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.RECORD_AUDIO) }
    if (granted) {
        content()
    } else {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            GlassCard(glow = tool.c1) {
                Text("🎤", fontSize = 48.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(8.dp))
                Text(
                    "This experiment listens through the microphone. Nothing is saved or sent anywhere.",
                    color = Neon.Text, textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(14.dp))
                GlowButton("Allow microphone", tool.c1, tool.c2, Modifier.fillMaxWidth()) {
                    launcher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        }
    }
}

fun hasMic(ctx: Context) =
    ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

/** True while the activity is resumed (stop sensors / mic in the background). */
@Composable
fun rememberIsResumed(): Boolean {
    val owner = LocalLifecycleOwner.current
    val state by owner.lifecycle.currentStateFlow.collectAsState()
    return state.isAtLeast(Lifecycle.State.RESUMED)
}

/** A counter that increments every display frame – read it inside a Canvas to redraw continuously. */
@Composable
fun rememberFrameTick(running: Boolean = true): Long {
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(running) {
        while (running) {
            withFrameNanos { tick = it }
        }
    }
    return tick
}

/** Registers a sensor listener while [active]. Returns false if the sensor doesn't exist. */
@Composable
fun SensorListener(
    types: List<Int>,
    active: Boolean,
    rate: Int = SensorManager.SENSOR_DELAY_GAME,
    onEvent: (SensorEvent) -> Unit
): Boolean {
    val ctx = LocalContext.current
    val sm = remember { ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val sensor = remember { types.firstNotNullOfOrNull { sm.getDefaultSensor(it) } }
    val latest by rememberUpdatedState(onEvent)
    DisposableEffect(active, sensor) {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) = latest(event)
            override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {}
        }
        if (active && sensor != null) sm.registerListener(listener, sensor, rate)
        onDispose { sm.unregisterListener(listener) }
    }
    return sensor != null
}

@Composable
fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(text, color = Neon.TextDim, fontSize = 12.sp, lineHeight = 16.sp, modifier = modifier)
}
