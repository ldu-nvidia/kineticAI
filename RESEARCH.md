# MyCarv — Deep Research: Ski Metrics, IMU Algorithms, and Technique Analysis

## Table of Contents
1. [Carv Product Feature Map](#1-carv-product-feature-map)
2. [The 12 Technique Metrics (4 Skill Categories)](#2-the-12-technique-metrics)
3. [IMU-to-Insight Pipeline](#3-imu-to-insight-pipeline)
4. [Sensor Fusion: Madgwick AHRS Filter](#4-sensor-fusion-madgwick-ahrs-filter)
5. [Turn Detection Algorithm](#5-turn-detection-algorithm)
6. [Edge Angle Estimation](#6-edge-angle-estimation)
7. [Turn Phase Segmentation](#7-turn-phase-segmentation)
8. [G-Force Measurement](#8-g-force-measurement)
9. [Balance & Weight Transfer](#9-balance--weight-transfer)
10. [Run vs Lift Auto-Detection](#10-run-vs-lift-auto-detection)
11. [Ski:IQ Composite Scoring](#11-skiiq-composite-scoring)
12. [Phone-Based Adaptations](#12-phone-based-adaptations)
13. [References](#13-references)

---

## 1. Carv Product Feature Map

### Hardware
- **Carv 2** (current): Clip-on device with 6-axis IMU (3-axis accelerometer + 3-axis gyroscope). No pressure insole.
- **Carv 1** (legacy): 3mm insole with 36 pressure sensors per foot + 6-axis IMU.
- Bluetooth LE streaming to phone at 20 Hz effective analysis rate.
- Carv 2 proved that **motion sensors alone** can derive all metrics previously requiring pressure data, via "sensor fusion" and ML models trained on 1B+ turns.

### Feature Set
| Category | Features |
|----------|----------|
| **Real-time** | Turn-by-turn audio coaching, active coach mode with pings, terrain-adaptive tips |
| **Monitoring** | Live metric monitors (edge angle, G-force, early edging per turn) |
| **Post-run** | 12 metrics scored per run, average turn graphs, heatmaps, best-8-turns |
| **Scoring** | Ski:IQ (0–200 composite), per-metric scores (0–100) |
| **Detection** | Auto turn start/end, terrain classification (groomer/bumps/powder), run/lift detection |
| **Coaching** | Personalized pathways (carving, parallel, moguls, powder), chairlift tips, focus skill |
| **Social** | Friends tracking, leaderboards, Strava integration |
| **Modes** | No Audio, Track, Learn, Train, Active Coach, Train with an Olympian |

### Other Professional Devices
- **PIQ/Rossignol**: Boot-clip IMU. Metrics: carve angle, edge-to-edge time, jump airtime, G-force.
- **RaceQs/professional setups**: Dual IMU (one per boot) + GNSS for center of mass estimation.

---

## 2. The 12 Technique Metrics

Carv organizes metrics into the 4 fundamental skiing skills (PSIA/CSIA framework):

### BALANCE (Sagittal/Frontal/Transverse planes)
| Metric | What It Measures | How Derived from IMU |
|--------|-----------------|---------------------|
| **Early Forward Movement** | Fore-aft balance at turn initiation — are you projecting forward into the new turn? | Pitch angle change rate in first 20% of turn. Forward pitch acceleration at transition indicates proper anticipation. |
| **Centered Balance (Mid-Turn Balance)** | Fore-aft position over bindings during the steering phase | Average pitch angle deviation from neutral during 20–80% of turn. Stable near-zero pitch = centered. |

### EDGING (Roll mechanics)
| Metric | What It Measures | How Derived from IMU |
|--------|-----------------|---------------------|
| **Edge Angle** | Maximum roll angle achieved during turns | Peak roll angle from Madgwick filter orientation. Phone-on-body requires calibrated gravity reference. |
| **Early Edging** | How quickly maximum edge angle is reached after turn initiation | Time (as % of turn) to reach 80% of peak roll angle. Score = 100 × (1 − t_80/t_turn). |
| **Edge Similarity** | How similarly both left and right turns are edged | |left_peak_roll − right_peak_roll| / max(left, right). Low difference = high score. |
| **Progressive Edge Build (Mid-Turn Edge Build)** | Whether edge angle increases/sustains through 10–80% of turn | Fraction of turn duration (10%–80%) where roll angle ≥ 90% of peak. Sustained = high score. |

### ROTARY (Steering/Direction)
| Metric | What It Measures | How Derived from IMU |
|--------|-----------------|---------------------|
| **Parallel Skis** | Whether skis remain parallel vs wedge/stem | With two sensors: roll angle difference. With one sensor (phone): yaw rate smoothness — parallel turns have clean sinusoidal yaw, stemming creates irregular yaw spikes. |
| **Turn Shape** | C-shaped (round) vs Z-shaped (jerky) turns | Smoothness of yaw rate curve. C-turn = gradual sinusoidal gyro-Z. Z-turn = sharp step function. Metric: correlation of gyro-Z with ideal sinusoid template. |

### PRESSURE (Force management)
| Metric | What It Measures | How Derived from IMU |
|--------|-----------------|---------------------|
| **Outside Ski Pressure** | Dominant weighting on outside (downhill) ski through turn | Lateral acceleration asymmetry during turn. Higher lateral-G toward outside = better pressure. Requires assumptions about phone placement. |
| **Pressure Smoothness** | Gradual pressure application vs sudden loading | Standard deviation of vertical acceleration within turn. Low σ = smooth loading. |
| **Early Weight Transfer** | How early pressure shifts to new outside ski at turn initiation | Time to detect lateral acceleration flip at turn start. Earlier flip = higher score. |
| **Transition Weight Release** | Unweighting during edge change between turns | Vertical G-force variation: ratio of (peak_G − trough_G) / peak_G at transition. Higher ratio = more dynamic unweighting. Experts show G-force dropping to <0.5G briefly. |
| **G-Force** | Centripetal acceleration in turns | Lateral acceleration magnitude at turn apex, isolated via sensor fusion. Formula: a_centripetal = v²/r. Higher G = tighter, faster, more dynamic turns. |

---

## 3. IMU-to-Insight Pipeline

The processing chain from raw sensor data to coaching feedback:

```
Raw IMU (accel_xyz, gyro_xyz) @ 50-200 Hz
    │
    ├─ [1] Madgwick AHRS Filter → Quaternion orientation (q0, q1, q2, q3)
    │       └─ Convert to Euler: roll (edge angle), pitch (fore-aft), yaw (heading)
    │
    ├─ [2] Gravity Removal → Linear acceleration in Earth frame
    │       └─ a_linear = R(q) × a_body − [0, 0, g]
    │
    ├─ [3] Turn Detection (gyro-Z zero-crossings + threshold)
    │       └─ Segment data into individual turns with start/end timestamps
    │
    ├─ [4] Turn Phase Segmentation
    │       └─ Initiation (0–25%), Steering-In (25–50%), Apex (50%), 
    │          Steering-Out (50–85%), Transition (85–100%)
    │
    ├─ [5] Per-Turn Metric Extraction (12 metrics computed per turn)
    │       └─ Edge angle, early edging, G-force, balance, etc.
    │
    ├─ [6] Turn Quality Scoring (each metric → 0-100 score)
    │       └─ Normalized against reference distributions from training data
    │
    ├─ [7] Ski:IQ Computation (weighted combination of 12 metric scores)
    │
    └─ [8] Feedback Generation
            └─ Identify weakest metric → select coaching tip → deliver audio/visual
```

**Latency budget**: The entire pipeline must complete in <130ms after turn completion to deliver real-time feedback before the next turn begins.

---

## 4. Sensor Fusion: Madgwick AHRS Filter

### Why Madgwick?
- Only 109 scalar operations per update (IMU mode) — critical for phone battery life
- Single tuning parameter (beta, the filter gain)
- Static accuracy: <0.6° RMS, Dynamic: <0.8° RMS
- Works at sampling rates as low as 10 Hz
- Superior to Kalman for this use case (less computational cost, comparable accuracy)

### Algorithm Summary

The filter maintains a quaternion `q` representing orientation of the sensor frame relative to Earth:

**Prediction (gyroscope integration):**
```
q̇_ω = ½ × q ⊗ [0, ωx, ωy, ωz]
q_predicted = q + q̇_ω × Δt
```

**Correction (accelerometer gravity reference):**
```
f(q, a) = q* ⊗ [0, 0, 0, g] ⊗ q − [0, ax, ay, az]
∇f = Jᵀ(q) × f(q, a)
q_corrected = q_predicted − β × (∇f / |∇f|) × Δt
```

Where:
- `β` controls the trade-off between gyroscope trust and accelerometer correction
- For skiing: `β ≈ 0.033–0.1` (higher values reduce drift but increase noise sensitivity)
- During high-acceleration events (bumps, icy terrain), reduce accelerometer weight

### Calibration
1. **Static calibration**: Hold device still for 2 seconds at boot-up. Measure gyroscope bias offset and initial gravity direction.
2. **Mounting orientation**: Detect phone orientation in pocket/mount during first few seconds of walking. Establish local coordinate transform.
3. **Continuous gyro bias estimation**: Madgwick filter naturally compensates for slow bias drift.

### Euler Angle Extraction
From quaternion `q = [q0, q1, q2, q3]`:
```
roll  = atan2(2(q0q1 + q2q3), 1 − 2(q1² + q2²))    → Edge angle
pitch = asin(2(q0q2 − q3q1))                          → Fore-aft lean
yaw   = atan2(2(q0q3 + q1q2), 1 − 2(q2² + q3²))     → Heading
```

---

## 5. Turn Detection Algorithm

Based on validated research (Martínez et al., Frontiers in Sports, 2019):

### Method: Gyroscope Z-axis Zero-Crossing with Hysteresis

1. **Low-pass filter** gyro-Z at 3 Hz (Butterworth 2nd order) to remove vibration noise
2. **Detect zero-crossings** in the filtered signal — each crossing represents a turn switch (edge change)
3. **Apply hysteresis threshold** (±0.5 rad/s) to reject noise crossings
4. **Minimum turn duration**: 400ms (reject false triggers from mogul impacts)
5. **Turn direction**: Sign of gyro-Z integral between crossings (positive = left, negative = right)

### Accuracy (from literature):
- Short carved turns: 99.6% precision, 99.6% recall
- Long carved turns: 99.3% precision
- Drifted turns: 100% precision, 83.3% recall
- Snowplow: 83.9% precision, 53.8% recall (weakness area)

### Enhancement: Multi-signal Fusion
For better robustness, combine:
- Gyro-Z zero crossings (primary turn switch)
- Roll angle sign change (confirms edge change)
- Lateral acceleration sign change (confirms direction change)
- Require 2/3 signals to agree within 200ms window

---

## 6. Edge Angle Estimation

### Definition
Edge angle = angle between the ski base and the snow surface. In practice, approximated as the roll angle of the boot/phone relative to the slope.

### From Madgwick Orientation
```
raw_roll = euler_roll from quaternion
slope_angle = estimated from barometer gradient or GPS altitude rate
edge_angle = |raw_roll| − slope_angle (corrected for terrain)
```

### Challenges
- **Accelerometer saturation**: On icy/bumpy terrain, accelerometer can saturate (>16g impacts), causing the filter to drift. Solution: temporarily rely solely on gyroscope integration during saturation events.
- **Slope compensation**: Edge angle relative to horizon ≠ edge angle relative to slope. A 45° roll on a 30° slope means only 15° of actual edging. Use barometer altitude gradient or GPS-derived slope angle to correct.
- **Mounting offset**: Phone in chest pocket vs. thigh pocket has different roll baseline. Calibrate by detecting the mean roll angle during straight-line skiing segments (should be ~0°).

### Validation
- Static accuracy vs. motion capture: r² = 1.0, RMSE = 0.18°–0.24°
- Dynamic accuracy: degrades with angular velocity, max error ~2.7° at 300°/s

---

## 7. Turn Phase Segmentation

Each turn is divided into phases based on roll angle and gyro-Z profile:

```
Turn Progress:  0%          25%         50%         75%        100%
                │           │           │           │           │
                ├─ INITIATION ─┤─ STEERING IN ─┤─ STEERING OUT ─┤─ TRANSITION ─┤
                │              │               │                │              │
                edge change    rising edge      apex (peak      declining     flat/
                begins         angle            edge angle)     edge angle    edge change
```

### Detection Method
1. **Turn start (0%)**: Gyro-Z zero crossing
2. **Rising edge phase (0–30%)**: Roll angle increasing from flat toward peak
3. **Apex (~30–50%)**: Roll angle within 90% of peak value
4. **Declining phase (50–85%)**: Roll angle decreasing
5. **Transition (85–100%)**: Roll angle near zero, G-force at minimum

### Why Phase Segmentation Matters
- **Early Edging** requires analyzing the 0–30% phase
- **Progressive Edge Build** requires analyzing the 10–80% phase
- **Weight Release** specifically looks at the 85–100% transition phase
- **Balance metrics** focus on different phases (initiation vs mid-turn)

---

## 8. G-Force Measurement

### Isolation of Centripetal (Lateral) Acceleration

The key insight from Carv's engineering team: G-force in skiing is specifically the **lateral centripetal acceleration**, not raw total acceleration.

```
1. Get orientation quaternion from Madgwick filter
2. Rotate raw accelerometer into Earth frame: a_earth = R(q) × a_body
3. Remove gravity: a_linear = a_earth − [0, 0, 9.81]
4. Project onto lateral axis (perpendicular to direction of travel):
   a_lateral = component of a_linear perpendicular to velocity vector
5. G-force = |a_lateral| / 9.81
```

### Relationship to Technique
```
G-force = v² / (r × g)

Where:
  v = speed (from GPS)
  r = turn radius (derived from GPS trajectory or from edge angle + ski geometry)
  g = 9.81 m/s²
```

Higher G-force requires: higher speed + smaller turn radius + higher edge angle.

### Benchmarks
| G-force | Equivalent |
|---------|-----------|
| 1.0 G | Standing still |
| 1.5 G | Airplane takeoff / gentle carve |
| 2.0 G | Moderate carving |
| 2.5 G | Aggressive carving, heavy limbs |
| 3.0+ G | Expert/race-level turns |
| 4.5 G | Ted Ligety world-class turns |

---

## 9. Balance & Weight Transfer

### Fore-Aft Balance (Pitch Analysis)
```
centered_balance_score = 100 × (1 − |mean_pitch_20_80| / max_pitch_range)

Where mean_pitch_20_80 = average pitch angle during 20–80% of turn
A score near 100 means pitch stays near neutral (centered over bindings)
```

### Early Forward Movement
```
pitch_rate_at_initiation = d(pitch)/dt during 0–20% of turn
early_forward_score = normalize(pitch_rate_at_initiation, toward_forward)

Positive pitch rate at initiation = projecting forward into new turn = good
```

### Weight Release (Transition Dynamics)
```
peak_vertical_G = max(vertical_acceleration) during 70–90% of turn
trough_vertical_G = min(vertical_acceleration) during 90–110% (transition)
weight_release = (peak_vertical_G − trough_vertical_G) / peak_vertical_G × 100

Expert: peak ~2.0G, trough ~0.3G → release = 85%
Intermediate: peak ~1.3G, trough ~1.0G → release = 23%
```

---

## 10. Run vs Lift Auto-Detection

### State Machine
```
States: IDLE → ASCENDING (lift) → DESCENDING (skiing) → STOPPED → IDLE

Transitions:
  IDLE → ASCENDING:  altitude increasing >2m over 30s AND speed <3 m/s (chairlift)
  ASCENDING → STOPPED: altitude stable for >15s (reached top)
  STOPPED → DESCENDING: speed >3 m/s AND altitude decreasing
  DESCENDING → STOPPED: speed <1 m/s for >10s
  STOPPED → IDLE: no movement for >60s
```

### Signals Used
- **Barometer**: Primary altitude signal (low power, fast, ~10cm resolution)
- **GPS altitude**: Backup, higher accuracy but more battery drain
- **GPS speed**: Movement detection
- **Accelerometer variance**: Distinguish skiing vibration from lift vibration

### Power Optimization
- During ASCENDING state: reduce IMU sampling to 1 Hz, GPS to 0.1 Hz
- During DESCENDING state: full rate IMU (50+ Hz), GPS at 1 Hz
- During IDLE: minimal sensor activity

---

## 11. Ski:IQ Composite Scoring

### Formula
```
Ski:IQ = Σ(wi × metric_score_i) for i in 1..12

Where wi are learned weights (from ML model trained on expert-labeled data)
```

### Score Normalization
Each raw metric is mapped to a 0–100 score via percentile ranking against a reference distribution. For example, if your peak edge angle of 45° places you at the 72nd percentile of all recorded turns, your Edge Angle score is 72.

### Composite Scale
```
Ski:IQ range: 0–200
  <90:    Beginner
  90–115: Intermediate
  115–140: Advanced
  140–160: Expert
  160+:   Professional (Ted Ligety scored 168)
```

### Our Implementation (MyCarv)
Without Carv's 1B-turn training dataset, we use a physics-informed scoring model:
- Weight metrics by importance (edging metrics get higher weight than pressure metrics for beginners)
- Use percentile normalization against accumulated personal history
- The scoring improves over time as the user builds their own reference distribution

---

## 12. Phone-Based Adaptations

### Mounting Differences
A phone in a chest pocket or thigh strap differs from a boot-mounted sensor:
- **Chest mount**: Good for upper body rotation, G-force, speed. Weaker for boot-specific edge angle.
- **Thigh mount**: Closer to boot kinematics. Roll angle correlates with edge angle but with damping.
- **Boot clip**: Ideal but requires custom hardware.

### Adaptations for Phone-Only Sensing
1. **Edge angle proxy**: Use a scaling factor (typically 0.6–0.8×) from thigh/chest roll to estimated boot roll. Calibrate during first few turns by comparing GPS-derived turn radius with roll angle.
2. **Turn detection**: Works identically — gyro-Z zero crossings are body-level, not boot-specific.
3. **G-force**: Phone accelerometer captures whole-body centripetal acceleration accurately regardless of mount point.
4. **GPS augmentation**: Phone GPS provides speed, altitude, trajectory that boot sensors cannot.
5. **Barometer**: Phone barometer enables run/lift detection and slope angle estimation.

### Limitations
- Cannot measure inside vs outside ski pressure difference (single device)
- Edge angle is estimated, not directly measured
- Parallel ski detection requires assumptions (yaw smoothness proxy)
- Phone pocket placement introduces motion artifacts from clothing

---

## 13. References

1. Martínez, A. et al. "Development and Validation of a Gyroscope-Based Turn Detection Algorithm for Alpine Skiing in the Field." *Frontiers in Sports and Active Living*, 2019.
2. Martínez, A. et al. "Development of an Automatic Alpine Skiing Turn Detection Algorithm Based on a Simple Sensor Setup." *Sensors* 19(4), 2019.
3. Madgwick, S. "An Efficient Orientation Filter for Inertial and Inertial/Magnetic Measurement Units." PhD Thesis, University of Bristol.
4. "Estimating Ski Orientation Using IMUs in Alpine Skiing." *Current Issues in Sport Science*, 2021.
5. "Challenges in Edge Angle Measurement During Alpine Skiing Using IMU Sensors." Sport-IAT, 2023.
6. "Development and Evaluation of a Low-Drift Inertial Sensor-Based System for Analysis of Alpine Skiing Performance." *Sensors* 21, 2021.
7. "Classification of Alpine Skiing Styles Using GNSS and IMUs." *Sensors* 20(15), 2020.
8. "A Comprehensive Comparison and Validation of Published Methods to Detect Turn Switch During Alpine Skiing." *Sensors* 21(7), 2021.
9. Carv Blog: "How Carv Turns Your Skiing Into Data" (getcarv.com)
10. Carv Blog: "G-Force: Our Latest Metric" (getcarv.com)
11. Carv Blog: "Early Edging with Carv" (getcarv.com)
12. Carv Blog: "Introducing Mid-Turn Edge Build" (getcarv.com)
13. Carv Blog: "Introducing: Transition Weight Release" (getcarv.com)
14. Carv Blog: "Ski:IQ" (getcarv.com)
15. PSIA/CSIA: "The 5 Skills Framework" — Balance, Edging, Rotary, Pressure, Timing
