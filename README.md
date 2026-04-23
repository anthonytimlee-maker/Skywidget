# Sky Widget

A 3×1 Android home-screen widget that shows live weather data on top of a
sky-colored background that shifts through the day — blue at noon, warm
orange at sunrise and sunset, deep blue at night. All sun times are
calculated from your device location; weather comes from Open-Meteo.

![3×1 widget layout](docs/widget-preview.png)

## Features

- Current temperature, daily high/low, precipitation probability
- Sunrise, sunset, daylight duration
- Barometer trend indicator (rising / falling / steady) based on the last 1.5 hours
- Weather icon adapts to condition and time of day (sun for day, moon for night)
- Sky-gradient background that changes continuously with sun altitude
- Refreshes every 15 minutes via `WorkManager`
- Works fully offline using the last-known location if network or GPS fails

## Building

### Option 1: Just download the APK

Every push to `main` triggers a GitHub Actions build. Grab the APK from
the latest successful run under **Actions → Build APK → Artifacts →
SkyWidget-debug**. Tagged releases (`git tag v1.0.0 && git push --tags`)
additionally attach the APK to a GitHub Release, no login required.

### Option 2: Build locally

```bash
git clone <this repo>
cd skywidget

# First-time-only: generate the Gradle wrapper jar. We don't commit the
# binary so the repo stays clean; CI does the same thing.
gradle wrapper --gradle-version 8.9 --distribution-type bin

# Now the standard wrapper commands work
./gradlew assembleDebug
```

The APK ends up at `app/build/outputs/apk/debug/app-debug.apk`.

### Option 3: Build in Android Studio

Clone the repo, open the folder in Android Studio. The IDE will run the
`gradle wrapper` step automatically on first import. Then **Run** or
**Build > Build APK**.

## Installing

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Or transfer the APK to your phone and tap it in your file manager. You'll
need to enable "Install unknown apps" for whichever app (Files, Chrome, etc.)
is opening the APK.

After install, long-press on an empty spot of your home screen →
**Widgets** → find **Sky Widget** → drag a 3×1 widget to your home screen.
The first time you place it, a configuration screen asks whether to use
device location or enter coordinates manually.

## Requirements

- `minSdk 26` (Android 8.0 Oreo)
- `targetSdk 34` (Android 14)
- Google Play Services (for the fused location provider)
- Internet permission (weather fetch)
- Location permission — runtime-requested; you can skip and enter coordinates manually

## Architecture

```
com.anthony.skywidget
├── astro/            SolarCalc.kt          — NOAA solar algorithms
├── data/             OpenMeteoClient.kt    — weather fetch + JSON parse
│                     BarometerTrend.kt     — 1.5h pressure comparison
│                     WeatherCode.kt        — WMO codes → icon categories
├── location/         LocationResolver.kt   — fused → cached → null fallback
├── sky/              SkyPalette.kt         — altitude-driven gradient interpolation
├── widget/           SkyWidgetProvider.kt  — lifecycle & scheduling
│                     WidgetRenderer.kt     — state → RemoteViews
│                     WidgetState.kt        — immutable render snapshot
├── worker/           RefreshWorker.kt      — periodic fetch + repaint
└── config/           ConfigActivity.kt     — first-time setup screen
```

### How it works

1. User drops the widget on the home screen.
2. `ConfigActivity` launches, asks for location permission or manual coords.
3. User completes setup → `RefreshWorker` enqueues immediately.
4. Worker resolves location (live → cached → nothing), fetches Open-Meteo,
   runs `SolarCalc` for sunrise/sunset/altitude, composes a `WidgetState`.
5. `WidgetRenderer` paints the gradient bitmap, sets text, and picks icons.
6. `SkyWidgetProvider` schedules a `PeriodicWorkRequest` for every 15 min.

Sky color is recomputed on every 15-min refresh. That's a compromise: we
could tick every minute like the HTML mockup does, but Android punishes
widgets that keep waking the CPU. Once every 15 minutes during daylight
means the sky color is at most ~4° of sun altitude stale, which is
visually imperceptible except right at dawn or dusk.

## Known limitations

- **No configurable refresh interval.** Android's `PeriodicWorkRequest`
  floors at 15 minutes regardless of what you pass.
- **Widget doesn't resize gracefully.** The layout assumes roughly the
  3×1 cell aspect; at 4×1 or 3×2 the proportions will look off.
- **No Play Services fallback.** Devices without Google Play (de-Googled
  Android, Huawei, etc.) won't get live location. Manual entry still works.
- **WMO codes map to 7 visual categories.** No special icons for freezing
  rain, hail, or ice pellets — they all fall under "rain" or "snow."

## Development

### Tweaking the sky palette

`SkyPalette.kt` has two anchor arrays (`RISING` and `SETTING`), each
defining a gradient at a specific sun altitude. To shift colors, edit the
RGB triplets — interpolation handles the in-between altitudes
automatically.

### Tweaking the barometer window

`BarometerTrend.kt` has `WINDOW_HOURS = 1.5` and `THRESHOLD_HPA = 0.5`.
Raising the window makes the trend less twitchy; raising the threshold
makes "steady" more forgiving.

### Adding a weather condition

1. Add a new enum entry to `WeatherCode.Condition`
2. Add the matching WMO codes to `WeatherCode.categorize`
3. Drop a new vector drawable in `res/drawable/`
4. Wire it up in `WidgetRenderer.iconForCondition`

## License

Personal project. No license specified — if you want to reuse it, ask.
