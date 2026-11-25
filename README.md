# nightlight-kmp

A bedside night light: the screen stays on with a warm, candle-like glow that slowly breathes.

- **Kotlin Multiplatform**: all logic lives in `shared/commonMain`: color temperature, the breathing/flicker flame model, the brightness curve, and the settings controller.
- **Native Android UI**: Jetpack Compose in `androidApp`. It only renders the frames and forwards input.

## Features

- Screen stays on (`FLAG_KEEP_SCREEN_ON`) and runs full-screen immersive.
- Warm light from 1200 K (ember) to 4000 K (warm white). Defaults to candle (1900 K).
- Breathing modes: Steady, Slow, Calm, Lively. The light gets slightly redder as it dims, like a real flame, with a subtle non-repeating flicker. Steady mode stops the frame loop to save battery.
- In-app blue light filter (default 60%): cuts blue fully and green partly, like a system eye-comfort filter, so the light gets more amber.
- One brightness slider drives both the backlight and the pixel gain, so it can go darker than the system minimum.
- Settings are saved between launches.

## Controls

| Gesture | Action |
|---|---|
| Tap | Show/hide controls (they auto-hide after 5 s) |
| Swipe ↕ | Brightness |
| Swipe ↔ | Warmth (right = warmer) |
| Double-tap | Reset to candle color |

## Structure

```
shared/src/commonMain/   ColorTemperature, FlameModel, Brightness, NightLightController, NightLightSettings
shared/src/androidMain/  SharedPrefsSettingsStore
shared/src/commonTest/   unit tests
androidApp/              MainActivity (window flags, brightness), NightLightScreen (Compose)
```

## Build

Open in Android Studio, or run:

```
./gradlew :androidApp:installDebug
./gradlew :shared:allTests
```

iOS targets (`iosArm64`, `iosSimulatorArm64`) are declared in `shared` so the logic stays platform-neutral. There's no iOS app yet; it would only need a SwiftUI view plus an `NSUserDefaults` `SettingsStore`.
