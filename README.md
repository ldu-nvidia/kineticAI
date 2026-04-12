# MyCarv — AI Ski Coach for Android

**Version 1.0.0**

A Carv-inspired ski coaching app that uses your Samsung Galaxy S26 Ultra's built-in sensors (accelerometer, gyroscope, magnetometer, barometer, GPS) to provide real-time technique feedback, post-run analysis, and a Ski:IQ score — all without any external hardware.

---

## What It Does

MyCarv turns your phone into a ski coach. Strap it to your body (chest pocket, thigh strap, or armband), hit Start, and ski. The app:

1. **Captures** IMU data at 50 Hz via the Madgwick AHRS sensor fusion filter
2. **Detects** every turn automatically using gyroscope zero-crossing algorithms (validated in academic research at 99.6% accuracy for carved turns)
3. **Scores** each turn across 12 technique metrics organized in the 4 fundamental skiing skills
4. **Coaches** you in real-time with tips targeted at your weakest metric
5. **Saves** everything for post-run analysis with charts and detailed breakdowns

---

## Features

### Real-Time (During Run)
- Live speed gauge, G-force, edge angle, altitude, distance
- Turn-by-turn detection with left/right counting
- Real-time coaching banners (identifies weakest metric, delivers actionable tips)
- 4-category skill breakdown bars (Balance, Edging, Rotary, Pressure)
- Live Ski:IQ score (0–200)
- Foreground service keeps tracking with screen off

### Post-Run Analysis
- Ski:IQ score with level classification (Developing → Intermediate → Advanced → Expert)
- 4-category skill breakdown with individual scores
- Speed profile, edge angle, and G-force time-series charts
- Turn balance visualization (left vs right)
- Detailed coaching insights (8–12 personalized tips per run)

### Run History
- All past runs saved with full metrics
- Compare Ski:IQ progression over time
- Season stats: total runs, avg/peak Ski:IQ, top speed, total distance, total vertical

### Auto-Detection
- Run vs lift detection via barometer + GPS state machine
- Terrain-aware (speed, altitude gradient)

---

## The 12 Technique Metrics

Organized in the 4 fundamental skiing skills (PSIA/CSIA framework):

### Balance
| Metric | What It Measures |
|--------|-----------------|
| Early Forward Movement | Fore-aft projection at turn initiation |
| Centered Balance | Pitch stability over bindings during mid-turn |

### Edging
| Metric | What It Measures |
|--------|-----------------|
| Edge Angle | Peak roll angle achieved during turns |
| Early Edging | How quickly peak edge angle is reached (toppling) |
| Edge Similarity | Left vs right turn edge angle consistency |
| Progressive Edge Build | Sustained/increasing edge through 10–80% of turn |

### Rotary
| Metric | What It Measures |
|--------|-----------------|
| Parallel Skis | Yaw smoothness proxy (sinusoidal = parallel) |
| Turn Shape | C-shaped (round) vs Z-shaped (jerky) — sinusoidal correlation |

### Pressure
| Metric | What It Measures |
|--------|-----------------|
| Outside Ski Pressure | Lateral acceleration magnitude (outside ski loading) |
| Pressure Smoothness | Inverse of lateral G variance (gradual vs jerky loading) |
| Early Weight Transfer | How early lateral acceleration flips at turn start |
| Transition Weight Release | Vertical G drop at edge change (dynamic unweighting) |
| G-Force | Peak centripetal acceleration in turns |

---

## Ski:IQ Scoring (0–200)

Weighted composite of the 4 skill categories:

```
Ski:IQ = Balance × 0.20 + Edging × 0.35 + Rotary × 0.15 + Pressure × 0.30
```

Mapped to 0–200 scale. Benchmarks:

| Ski:IQ | Level |
|--------|-------|
| < 90 | Developing |
| 90–115 | Intermediate |
| 115–140 | Advanced |
| 140–160 | Expert |
| 160+ | Professional |

---

## Algorithm Pipeline

```
Raw IMU @ 50 Hz (accel_xyz, gyro_xyz, pressure)
    │
    ├─ [1] Static Calibration (first 1s) → gravity vector, gyro bias
    │
    ├─ [2] Madgwick AHRS Filter → quaternion orientation
    │       └─ Euler: roll (edge angle), pitch (fore-aft), yaw (heading)
    │
    ├─ [3] Gravity Removal → linear acceleration in Earth frame
    │
    ├─ [4] Turn Detection: Butterworth 3 Hz LPF on gyro-Z → zero-crossing + hysteresis
    │
    ├─ [5] Turn Phase Segmentation (initiation → steering → apex → transition)
    │
    ├─ [6] Per-Turn Metric Extraction (12 metrics)
    │
    ├─ [7] Ski:IQ Computation (weighted composite)
    │
    └─ [8] Feedback: weakest metric → coaching tip → visual banner
```

Latency: <130ms from turn completion to feedback delivery.

---

## Architecture

```
com.mycarv.app/
├── analysis/
│   ├── MadgwickFilter.kt        # Quaternion AHRS sensor fusion
│   ├── SignalProcessing.kt       # Butterworth LPF, moving average, ring buffer
│   ├── TurnDetector.kt           # Gyro-Z zero-crossing with per-turn metric extraction
│   ├── SkiAnalysisEngine.kt      # Central pipeline: IMU → orientation → turns → metrics
│   ├── SkiMetrics.kt             # Data classes for 12 metrics + Ski:IQ
│   ├── FeedbackGenerator.kt      # Real-time tips, post-run feedback, chairlift tips
│   └── RunLiftDetector.kt        # Barometer + GPS state machine
├── sensor/
│   ├── SensorCollector.kt        # Android Sensor API streaming (accel, gyro, mag, baro)
│   ├── LocationTracker.kt        # FusedLocationProvider GPS streaming
│   └── SensorData.kt             # IMU/GPS sample data classes
├── data/
│   ├── db/                       # Room entities, DAO, database
│   └── repository/               # RunRepository
├── service/
│   └── SkiTrackingService.kt     # Foreground service with wake lock
├── viewmodel/                    # MVVM ViewModels per screen
└── ui/
    ├── theme/                    # Material 3 dark theme (ice/mountain palette)
    ├── components/               # SpeedGauge, SkiIQRing, SkillBreakdownCard, etc.
    ├── screens/                  # Dashboard, LiveRun, PostAnalysis, RunHistory
    └── MyCarvAppRoot.kt          # Navigation graph
```

---

## Tech Stack

- **Language:** Kotlin 2.1
- **UI:** Jetpack Compose + Material 3
- **Database:** Room (KSP)
- **Location:** Google Play Services FusedLocationProvider
- **Sensors:** Android Sensor API (accelerometer, gyroscope, magnetometer, barometer)
- **Charts:** Custom Canvas composables + Vico (available)
- **Min SDK:** 29 (Android 10)
- **Target SDK:** 35

---

## Build & Run

1. Open `my_carv/` in **Android Studio Ladybug** (2024.2+) or newer
2. Sync Gradle
3. Connect your Samsung Galaxy S26 Ultra (or any device with IMU + GPS)
4. Run the app
5. Grant permissions (Location, Notifications, Sensors)

---

## How to Use

1. **Dashboard** — See season stats (Ski:IQ, top speed, total vertical) and tap **Start Run**
2. **Mount your phone** — Secure in a chest pocket or thigh strap
3. **Ski** — Live speed gauge, edge angle, G-force, coaching tips appear as you ride
4. **Stop Run** — Tap stop to save and see full analysis
5. **Post Analysis** — Review Ski:IQ, 4-skill breakdown, speed/edge/G-force charts, coaching insights
6. **History** — Browse all runs, track Ski:IQ improvement over time

---

## Phone Mounting Guide

| Mount Point | Pros | Cons |
|-------------|------|------|
| Chest pocket | Good G-force, balance detection | Edge angle needs scaling factor |
| Thigh strap | Closest to boot kinematics | May shift during aggressive skiing |
| Armband | Easy access | Worst for edge angle correlation |

**Recommended:** Chest pocket (zipped) for the best trade-off.

---

## Research & References

See [RESEARCH.md](RESEARCH.md) for the full deep-dive including:
- Carv's 12 metrics and how each is derived from IMU data
- Madgwick AHRS filter math and tuning
- Academic papers on turn detection accuracy
- Edge angle estimation challenges and solutions
- G-force physics and benchmarks
- Balance/weight transfer measurement approaches

Key papers:
1. Martínez et al., "Gyroscope-Based Turn Detection Algorithm for Alpine Skiing" (Frontiers, 2019)
2. Madgwick, "An Efficient Orientation Filter for IMUs" (University of Bristol)
3. "Estimating Ski Orientation Using IMUs in Alpine Skiing" (CISS Journal, 2021)

---

## Roadmap (Future Versions)

- [ ] TTS audio coaching (real-time voice feedback via earbuds)
- [ ] GPS map overlay of run path
- [ ] Run comparison mode (overlay two runs)
- [ ] Export to GPX/CSV
- [ ] Ski resort detection and trail mapping
- [ ] Wear OS companion for wrist haptic feedback
- [ ] ML model trained on personal data for adaptive scoring
- [ ] Video overlay (record + sync metrics to video timestamps)

---

## License

Personal project. Not affiliated with Carv or Motion Metrics Ltd.
