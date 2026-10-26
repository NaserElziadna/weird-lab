# 🧪 Weird Lab

Eight strange experiments using your phone's sensors, speaker and microphone. 100% offline, Kotlin + Jetpack Compose.

| | Experiment | How it works |
|---|---|---|
| 🌋 | **Seismograph** | Accelerometer at ~200 Hz, gravity removed with a high-pass filter, drawn as 3 scrolling seismograph traces in milli-g |
| 🧲 | **Magnet Painter** | Magnetometer strength (µT) becomes paint colour; the game-rotation-vector turns the phone into a laser-pointer brush |
| 🦇 | **Bat Sonar** | Speaker fires a 5 ms chirp every 180 ms, mic matched-filters the echoes → distance; gyroscope heading turns it into a 360° radar map |
| 📡 | **Ultrasonic Chat** | 16-tone FSK modem at 16.7–19.7 kHz with alternating tone banks, start/end markers and a checksum. UTF-8, so Arabic works |
| 👏 | **Echo Mapper** | Clap (or let the phone ping), Schroeder backward integration → reverb time RT60 → Sabine room-size guess |
| 🦟 | **Mosquito Tone** | Click-free sine generator up to 22 kHz + a rising-sweep "ear age" test |
| 🔁 | **Reverse Speech** | Record, play backwards, chipmunk / slow-mo speeds, plus the reverse-imitation challenge |
| 🎻 | **Hum to Music** | YIN pitch detection → note segmentation (auto-tune) → re-synthesised as piano, violin, flute, trumpet, 8-bit or robot |

## Run it
1. Unzip, open the `WeirdLab` folder in **Android Studio** (Ladybug or newer).
2. Let Gradle sync (accept any suggested AGP/Gradle upgrades — they're safe).
3. Enable USB/Wi-Fi debugging on the phone, pick it in the device list, press ▶ Run.

Min Android 8.0 (API 26). Tested design target: Galaxy S23 Ultra.

## Tips
- Sound experiments need media volume up. Ultrasonic and sonar work best with nothing covering the bottom edge.
- If Ultrasonic Chat doesn't decode, switch both phones to **Audible** mode — some speakers/mics roll off above 18 kHz.
- Sonar: hold the phone flat, bottom edge pointing away from you, turn slowly on the spot (~10 s per circle).

## Code map
```
app/src/main/java/com/weirdlab/app/
  MainActivity.kt          navigation between experiments
  Tool.kt                  list of experiments (title, emoji, colours)
  ui/                      theme, neon components, home screen
  sensors/                 seismograph, magnet painter
  sound/AudioKit.kt        record / play / Goertzel / tone helpers
  sound/SonicCodec.kt      ultrasonic modem
  sound/PitchKit.kt        YIN + note segmentation + instrument synth
  sound/*Screen.kt         one file per sound experiment (incl. Bat Sonar)
```
