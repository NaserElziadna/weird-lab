package com.weirdlab.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weirdlab.app.Section
import com.weirdlab.app.Tool
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(onOpen: (Tool) -> Unit) {
    NeonBackground {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = WindowInsets.systemBars.asPaddingValues().let {
                PaddingValues(start = 16.dp, end = 16.dp, top = it.calculateTopPadding() + 12.dp, bottom = it.calculateBottomPadding() + 24.dp)
            }
        ) {
            item { Header() }
            var index = 0
            Section.entries.forEach { section ->
                item(key = "h_${section.name}") {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 22.dp, bottom = 10.dp)) {
                        Text(section.emoji, fontSize = 20.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(section.title.uppercase(), color = Neon.TextDim, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 2.sp)
                    }
                }
                Tool.entries.filter { it.section == section }.chunked(2).forEach { pair ->
                    val baseIndex = index
                    index += pair.size
                    item(key = pair.joinToString { it.name }) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            pair.forEachIndexed { i, tool ->
                                ToolCard(tool, baseIndex + i, Modifier.weight(1f)) { onOpen(tool) }
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
            item {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Every experiment runs 100% offline on your phone ✨",
                    color = Neon.TextDim, fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

@Composable
private fun Header() {
    val transition = rememberInfiniteTransition(label = "hdr")
    val glow by transition.animateFloat(
        0.55f, 1f, infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "glow"
    )
    val shift by transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(5000), RepeatMode.Reverse), label = "shift"
    )
    Column(Modifier.padding(top = 12.dp)) {
        Text("🧪  EXPERIMENTS", color = Neon.TextDim, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier.drawBehind {
                drawCircle(
                    Brush.radialGradient(listOf(Neon.Pink.copy(alpha = 0.35f * glow), Color.Transparent),
                        Offset(size.width * (0.3f + 0.4f * shift), size.height / 2), size.width * 0.6f),
                    size.width * 0.6f, Offset(size.width * (0.3f + 0.4f * shift), size.height / 2)
                )
            }
        ) {
            GradientText(
                "WEIRD LAB",
                listOf(Neon.Cyan, Neon.Purple, Neon.Pink, Neon.Orange),
                52.sp,
                modifier = Modifier.graphicsLayer { alpha = 0.75f + 0.25f * glow }
            )
        }
        Text(
            "${Tool.entries.size} secret experiments hiding inside your phone",
            color = Neon.Text.copy(alpha = 0.8f), fontSize = 15.sp
        )
    }
}

@Composable
private fun ToolCard(tool: Tool, index: Int, modifier: Modifier, onClick: () -> Unit) {
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(60L * index)
        appear.animateTo(1f, tween(450, easing = FastOutSlowInEasing))
    }
    val transition = rememberInfiniteTransition(label = "card")
    val bob by transition.animateFloat(
        -1f, 1f,
        infiniteRepeatable(tween(1400 + index * 170, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bob"
    )
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier
            .height(178.dp)
            .graphicsLayer {
                alpha = appear.value
                translationY = (1f - appear.value) * 60f
                scaleX = 0.9f + 0.1f * appear.value
                scaleY = 0.9f + 0.1f * appear.value
            }
            .clip(shape)
            .background(Brush.verticalGradient(listOf(tool.c1.copy(alpha = 0.22f), Neon.Surface.copy(alpha = 0.85f))))
            .border(1.5.dp, Brush.linearGradient(listOf(tool.c1, tool.c2.copy(alpha = 0.2f))), shape)
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Box(
            Modifier
                .size(62.dp)
                .graphicsLayer {
                    translationY = bob * 6f
                    rotationZ = bob * 6f
                }
                .drawBehind {
                    drawCircle(Brush.radialGradient(listOf(tool.c2.copy(alpha = 0.55f), Color.Transparent)))
                },
            contentAlignment = Alignment.Center
        ) { Text(tool.emoji, fontSize = 38.sp) }
        Spacer(Modifier.weight(1f))
        Text(tool.title, color = Neon.Text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Spacer(Modifier.height(2.dp))
        Text(tool.tagline, color = tool.c1.copy(alpha = 0.9f), fontSize = 12.sp, lineHeight = 15.sp)
    }
}
