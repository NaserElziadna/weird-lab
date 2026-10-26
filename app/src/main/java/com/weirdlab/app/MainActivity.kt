package com.weirdlab.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.weirdlab.app.sensors.MagnetPainterScreen
import com.weirdlab.app.sensors.SeismographScreen
import com.weirdlab.app.sound.EchoScreen
import com.weirdlab.app.sound.HumScreen
import com.weirdlab.app.sound.MosquitoScreen
import com.weirdlab.app.sound.ReverseScreen
import com.weirdlab.app.sound.SonarScreen
import com.weirdlab.app.sound.UltrasonicScreen
import com.weirdlab.app.ui.HomeScreen
import com.weirdlab.app.ui.WeirdLabTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WeirdLabTheme {
                WeirdLabApp()
            }
        }
    }
}

@Composable
fun WeirdLabApp() {
    var currentName by rememberSaveable { mutableStateOf<String?>(null) }
    val current = currentName?.let { name -> Tool.entries.firstOrNull { it.name == name } }

    BackHandler(enabled = current != null) { currentName = null }

    AnimatedContent(
        targetState = current,
        transitionSpec = {
            (fadeIn(tween(280)) + scaleIn(tween(320), initialScale = 0.92f)) togetherWith
                (fadeOut(tween(200)) + scaleOut(tween(220), targetScale = 1.04f))
        },
        label = "nav"
    ) { tool ->
        val back = { currentName = null }
        when (tool) {
            null -> HomeScreen(onOpen = { currentName = it.name })
            Tool.SEISMO -> SeismographScreen(back)
            Tool.MAGNET -> MagnetPainterScreen(back)
            Tool.SONAR -> SonarScreen(back)
            Tool.ULTRA -> UltrasonicScreen(back)
            Tool.ECHO -> EchoScreen(back)
            Tool.MOSQUITO -> MosquitoScreen(back)
            Tool.REVERSE -> ReverseScreen(back)
            Tool.HUM -> HumScreen(back)
        }
    }
}
