package com.weirdlab.app

import androidx.compose.ui.graphics.Color
import com.weirdlab.app.ui.Neon

enum class Section(val title: String, val emoji: String) {
    SENSOR("Sensor Tricks", "🧲"),
    SOUND("Sound Weirdness", "🔊"),
    INSPECT("Home Inspector", "🏠")
}

enum class Tool(
    val title: String,
    val emoji: String,
    val tagline: String,
    val c1: Color,
    val c2: Color,
    val section: Section,
    val needsMic: Boolean
) {
    SEISMO("Seismograph", "🌋", "Feel every tiny tremor", Neon.Orange, Neon.Red, Section.SENSOR, false),
    MAGNET("Magnet Painter", "🧲", "Paint invisible fields", Neon.Purple, Neon.Cyan, Section.SENSOR, false),
    SONAR("Bat Sonar", "🦇", "Map the room with echoes", Neon.Lime, Neon.Cyan, Section.SENSOR, true),
    ULTRA("Ultrasonic Chat", "📡", "Text through silent sound", Neon.Cyan, Neon.Mint, Section.SOUND, true),
    ECHO("Echo Mapper", "👏", "Clap to size your room", Neon.Mint, Neon.Lime, Section.SOUND, true),
    MOSQUITO("Mosquito Tone", "🦟", "How old are your ears?", Neon.Yellow, Neon.Orange, Section.SOUND, false),
    REVERSE("Reverse Speech", "🔁", "Talk backwards", Neon.Pink, Neon.Purple, Section.SOUND, true),
    HUM("Hum to Music", "🎻", "Your hum, any instrument", Neon.Violet, Neon.Pink, Section.SOUND, true),
    WIFI("Wi-Fi Heatmap", "📶", "Find the dead zones", Neon.Cyan, Neon.Purple, Section.INSPECT, false),
    BLE("Bluetooth Radar", "🔵", "Find gadgets & trackers", Neon.Purple, Neon.Cyan, Section.INSPECT, false),
    LEAK("Leak Finder", "💧", "Hunt hissing pipes & drafts", Neon.Cyan, Neon.Mint, Section.INSPECT, true),
    HUMDET("Electric Hum", "⚡", "Hear buzzing wiring & gear", Neon.Yellow, Neon.Orange, Section.INSPECT, true),
    STUD("Stud Finder", "🔩", "Spot nails & steel in walls", Neon.Orange, Neon.Yellow, Section.INSPECT, false),
    DOOR("Door Sentinel", "🚪", "Air pressure spy", Neon.Mint, Neon.Cyan, Section.INSPECT, false)
}
