# Hardware Candidates for KineticAI Boot Sensor

Tracking what boards could replace or complement the M5StickC Plus. Updated
as options come and go. Think of this as the "hardware scouting report."

## Current reference: M5StickC Plus v1.1

| Spec           | Value                                    |
|----------------|------------------------------------------|
| MCU            | ESP32-PICO-D4 (dual-core Xtensa, 240 MHz)|
| Flash          | 4 MB                                     |
| PSRAM          | none                                     |
| SRAM           | 520 KB (128 KB IRAM, 320 KB DRAM)        |
| IMU            | MPU6886 (6-axis, up to 1 kHz)            |
| Display        | 135×240 ST7789v2 color LCD               |
| Battery        | 120 mAh LiPo                             |
| Wireless       | BLE 4.2 + WiFi 2.4 GHz                   |
| Form factor    | 48 × 24 × 14 mm                          |
| Enclosure      | Plastic shell, not weatherproof          |
| Approx. price  | ~$25                                     |
| Known issues   | ADC driver conflict on esp32 core 3.x (need 2.1.4), tight IRAM |

**Verdict:** good for prototype; not ski-rated. Battery too small for a full
resort day. Waterproofing is DIY.

## Candidate replacements / upgrades

Ranked by suitability for KineticAI's needs.

### 1. Seeed XIAO ESP32-S3 — **recommended next stop**

| Spec           | Value                                    |
|----------------|------------------------------------------|
| MCU            | ESP32-S3 (Xtensa LX7, 240 MHz)           |
| Flash          | 8 MB                                     |
| PSRAM          | 8 MB                                     |
| SRAM           | 512 KB (includes large IRAM — no overflow issues) |
| IMU            | **none** (external required)             |
| Display        | **none** (external required)             |
| Battery        | none built-in (charging circuit on-board)|
| Wireless       | BLE 5 + WiFi 2.4 GHz                     |
| Form factor    | 21 × 17.5 × 3.5 mm (tiny!)               |
| Approx. price  | ~$7                                      |

**Pros:** much more headroom (IRAM, PSRAM, flash), BLE 5, tiny, cheap,
modern ESP32-S3 fixes the ADC driver_ng issue we hit.
**Cons:** no built-in IMU, display, or battery. Becomes a custom assembly.
**Port effort:** ~1 week for BLE + IMU; +1 week for enclosure + integration.

### 2. Seeed XIAO nRF52840 Sense

| Spec           | Value                                    |
|----------------|------------------------------------------|
| MCU            | nRF52840 (Cortex-M4F, 64 MHz)            |
| Flash          | 1 MB                                     |
| SRAM           | 256 KB                                   |
| IMU            | LSM6DS3TR (built-in!)                    |
| Microphone     | PDM (built-in)                           |
| Wireless       | BLE 5 only (no WiFi)                     |
| Form factor    | 21 × 17.5 mm                             |
| Approx. price  | ~$15                                     |

**Pros:** lowest-power BLE chip on the market, built-in IMU, longer battery
life, professional BLE stack (Nordic SoftDevice).
**Cons:** no WiFi (fine if you drop the bulk-transfer feature), no display,
completely different toolchain (nRF Connect SDK or Zephyr).
**Port effort:** ~2 weeks (new BLE library, new build system).

### 3. Custom PCB (ESP32-S3 + MPU6886 + OLED + battery)

| Spec           | Value                                    |
|----------------|------------------------------------------|
| MCU            | ESP32-S3 (chosen for dev-familiarity)    |
| Everything else| Your choice                              |

**Pros:** full control — pick the exact sensors, battery size, enclosure,
mounting. IP67 rating attainable. Optimal for production at scale.
**Cons:** ~2 months engineering + ~$5k NRE for first run + certification ($10k).
**Economical at volumes >100 units/year.**

### 4. Movesense (medical-grade)

| Spec           | Value                                    |
|----------------|------------------------------------------|
| MCU            | nRF52832                                 |
| Sensors        | 9-axis IMU, ECG capable, temperature     |
| Wireless       | BLE                                      |
| Form factor    | 36 mm round, IP68                        |
| Approx. price  | ~$300 per unit                           |

**Pros:** already waterproof, professionally validated, rugged.
**Cons:** very expensive, closed ecosystem, overkill.

### 5. Sony Spresense

| Spec           | Value                                    |
|----------------|------------------------------------------|
| MCU            | 6-core Cortex-M4F, 156 MHz               |
| Extras         | GPS built-in, audio codec                |
| Approx. price  | ~$65                                     |

**Pros:** richer sensor suite, GPS on-board.
**Cons:** expensive, niche toolchain, bigger than needed.

## Decision framework

When evaluating a candidate board, score these dimensions:

| Dimension          | Why it matters                          | How to measure |
|--------------------|-----------------------------------------|----------------|
| Size               | Boot-mount comfort                      | mm³ |
| Battery life       | Full-day skiing without recharge        | Hours at 50 Hz IMU + BLE |
| BLE reliability    | Real-time coaching needs stable connection | Connection drop rate |
| Sensor quality     | Turn detection accuracy                 | IMU noise floor, bias stability |
| Toolchain maturity | How fast can you iterate?               | Time to first BLE hello-world |
| Cost at volume     | Unit economics for a product            | 100-unit and 1000-unit pricing |
| Certification path | FCC/CE regulatory readiness             | Module pre-certified? |
| Waterproofing path | Snow survival                           | IP rating of module or ease of enclosing |

## When to switch

Triggers that should prompt a hardware migration:

- **Waitlist signal validates enough demand** to justify custom PCB engineering
- **IRAM / memory ceiling blocks a must-have feature** (unlikely — we're already lean)
- **M5 chronic reliability issues** in real-world ski testing
- **A specific customer segment demands ski-rated hardware** we can't DIY to

Until any of these fires, the M5 is a completely fine reference platform.

## Parking lot (interesting but not prioritized)

- Arduino Nicla Sense ME — rich sensor suite but expensive, weak community
- Particle Photon 2 — good toolchain, LTE optional
- RAK Wireless WisBlock modules — LoRa for ski-area mesh networking (cool but niche)
