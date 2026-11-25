# nightlight-kmp

A bedside night light: the screen stays on with a warm, candle-like glow that slowly breathes.

- **Kotlin Multiplatform**: all logic lives in `shared/commonMain`: color temperature, the breathing/flicker flame model, the brightness curve, and the settings controller.
- **Native Android UI**: Jetpack Compose in `androidApp`. It only renders the frames and forwards input.

## Features

- Screen stays on (`FLAG_KEEP_SCREEN_ON`) and runs full-screen immersive.
- Warm light from 1200 K (ember) to 4000 K (warm white). Defaults to candle (1900 K).
- Breathing modes: Steady, Slow (10 s), Calm (7.5 s), Lively (4.5 s). Each breath rises a little faster than it falls. Changing mode eases from where the breath is, with no jump.
- Flame feel: the light gets redder as it dims, the glow sways and swells slightly, there is a soft non-repeating flicker, and now and then a gust of air makes it waver. Steady mode stops the frame loop to save battery.
- Soft glow with a redder halo, dithered so dark gradients don't show bands (per-pixel shader on Android 13+, dithered gradient before that).
- Renders at the display's top refresh rate (120 Hz where available). "Limit to 30 Hz" in the controls trades that for battery.
- Flashlight button in the controls toggles the camera flash (no camera permission needed).
- In-app blue light filter (default 60%): cuts blue fully and green partly, like a system eye-comfort filter, so the light gets more amber.
- One brightness slider drives the backlight, the pixel gain and the size of the glow, so it can go darker than the system minimum. The light also gets a little warmer as it dims.
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
shared/src/commonMain/   ColorTemperature, FlameModel (pure flame math), FlameAnimator (phase, easing, frame pacing),
                         Brightness, NightLightController, NightLightSettings
shared/src/androidMain/  SharedPrefsSettingsStore
shared/src/commonTest/   unit tests
androidApp/              MainActivity (window flags, brightness, refresh rate), NightLightScreen (Compose),
                         GlowPainter (shader / gradient rendering), Torch (camera flash)
```

## Build

Open in Android Studio, or run:

```
./gradlew :androidApp:installDebug
./gradlew :shared:allTests
```

iOS targets (`iosArm64`, `iosSimulatorArm64`) are declared in `shared` so the logic stays platform-neutral. There's no iOS app yet; it would only need a SwiftUI view plus an `NSUserDefaults` `SettingsStore`.
