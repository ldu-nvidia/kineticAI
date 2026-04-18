// platform.h — KineticAI Hardware Abstraction Layer (HAL)
//
// This header declares the minimal set of functions every board driver
// must implement. The portable layer (scoring, turn detection, protocol)
// calls ONLY these functions — it never touches board-specific APIs.
//
// To port to a new board:
//   1. Create board_<newboard>.h implementing every function declared here.
//   2. Change the single #include in KineticAI_IMU.ino to point at your driver.
//   3. See PORTING.md for the full guide.
//
// Do NOT put board-specific includes or types here. Keep this file portable.

#pragma once
#include <stdint.h>
#include <stddef.h>

// ══════════════════════════════════════════════════════════════════════════
//  HAL Version
// ══════════════════════════════════════════════════════════════════════════
//
// Bump the minor version on additive changes, the major version on breaking
// changes. Board drivers that don't support a newer HAL feature should
// #error out or provide no-op fallbacks.

#define KAI_HAL_VERSION_MAJOR 1
#define KAI_HAL_VERSION_MINOR 0

// ══════════════════════════════════════════════════════════════════════════
//  Board capability flags
// ══════════════════════════════════════════════════════════════════════════
//
// Board drivers set these via #define. Portable code checks them with #if
// to gracefully degrade when a capability is missing.

// The board driver MUST define these (0 or 1) before this header is parsed.
// See board_m5stickc_plus.h for the canonical example.

#ifndef KAI_HAS_DISPLAY
#error "Board driver must define KAI_HAS_DISPLAY (0 or 1) before including platform.h"
#endif
#ifndef KAI_HAS_BUZZER
#error "Board driver must define KAI_HAS_BUZZER (0 or 1)"
#endif
#ifndef KAI_HAS_VIBRATION
#error "Board driver must define KAI_HAS_VIBRATION (0 or 1)"
#endif
#ifndef KAI_HAS_BUTTONS
#error "Board driver must define KAI_HAS_BUTTONS (0 or 1)"
#endif
#ifndef KAI_HAS_BATTERY_MONITOR
#error "Board driver must define KAI_HAS_BATTERY_MONITOR (0 or 1)"
#endif

// ══════════════════════════════════════════════════════════════════════════
//  Core types
// ══════════════════════════════════════════════════════════════════════════

struct kai_imu_sample_t {
    float accel_x, accel_y, accel_z;  // m/s²
    float gyro_x,  gyro_y,  gyro_z;   // rad/s
    uint32_t timestamp_ms;            // millis() at time of sample
};

enum kai_color_t : uint16_t {
    KAI_COL_BLACK   = 0x0000,
    KAI_COL_WHITE   = 0xFFFF,
    KAI_COL_RED     = 0xF800,
    KAI_COL_GREEN   = 0x07E0,
    KAI_COL_BLUE    = 0x001F,
    KAI_COL_YELLOW  = 0xFFE0,
    KAI_COL_CYAN    = 0x07FF,
    KAI_COL_ORANGE  = 0xFD20,
    KAI_COL_GOLD    = 0xFFE0,
};

enum kai_button_t : uint8_t {
    KAI_BTN_A = 0,  // Main / front
    KAI_BTN_B = 1,  // Secondary / side
    KAI_BTN_C = 2,  // Power (if applicable)
};

enum kai_power_mode_t : uint8_t {
    KAI_POWER_ACTIVE = 0,    // Full-rate sensing + radio
    KAI_POWER_IDLE   = 1,    // Reduced sample rate, dim display
    KAI_POWER_SLEEP  = 2,    // Deep sleep, wake-on-motion
};

// ══════════════════════════════════════════════════════════════════════════
//  Lifecycle
// ══════════════════════════════════════════════════════════════════════════

// Called once from setup(). Initializes board: power, I2C, IMU, display, etc.
// Must not block longer than ~2 seconds.
void kai_platform_init();

// Called at the start of every loop() iteration. Polls buttons, refreshes
// IMU cache if the driver needs that, handles any per-tick housekeeping.
void kai_platform_tick();

// Monotonic milliseconds since boot.
uint32_t kai_platform_millis();

// ══════════════════════════════════════════════════════════════════════════
//  IMU
// ══════════════════════════════════════════════════════════════════════════

// Read the latest IMU sample. Returns true on success, false if the IMU
// is unavailable or failed this cycle.
bool kai_imu_read(kai_imu_sample_t& out);

// ══════════════════════════════════════════════════════════════════════════
//  Display  (only if KAI_HAS_DISPLAY)
// ══════════════════════════════════════════════════════════════════════════

#if KAI_HAS_DISPLAY
int  kai_display_width();
int  kai_display_height();
void kai_display_clear(uint16_t bg_color);
void kai_display_fill_rect(int x, int y, int w, int h, uint16_t color);
void kai_display_text(int x, int y, const char* text, uint16_t fg_color, uint8_t size);
void kai_display_set_brightness(uint8_t level);  // 0–255
#endif

// ══════════════════════════════════════════════════════════════════════════
//  Buzzer  (only if KAI_HAS_BUZZER)
// ══════════════════════════════════════════════════════════════════════════

#if KAI_HAS_BUZZER
void kai_buzzer_tone(uint16_t freq_hz, uint16_t duration_ms);
#endif

// ══════════════════════════════════════════════════════════════════════════
//  Vibration  (only if KAI_HAS_VIBRATION)
// ══════════════════════════════════════════════════════════════════════════

#if KAI_HAS_VIBRATION
void kai_vibrate(uint8_t intensity, uint16_t duration_ms);
#endif

// ══════════════════════════════════════════════════════════════════════════
//  Buttons  (only if KAI_HAS_BUTTONS)
// ══════════════════════════════════════════════════════════════════════════

#if KAI_HAS_BUTTONS
// Edge-triggered: true if button transitioned from not-pressed to pressed
// since the last kai_platform_tick().
bool kai_button_pressed(kai_button_t btn);
// Level-triggered: true if currently held down.
bool kai_button_held(kai_button_t btn);
// Edge-triggered: true if the button was held for at least duration_ms.
bool kai_button_held_for(kai_button_t btn, uint32_t duration_ms);
#endif

// ══════════════════════════════════════════════════════════════════════════
//  Battery monitor  (only if KAI_HAS_BATTERY_MONITOR)
// ══════════════════════════════════════════════════════════════════════════

#if KAI_HAS_BATTERY_MONITOR
// Returns battery voltage in volts. 0.0 if unavailable.
float kai_battery_voltage();
// Rough percentage 0–100 based on a simple LiPo curve. May be replaced by
// board-specific calibration.
uint8_t kai_battery_percent();
#endif

// ══════════════════════════════════════════════════════════════════════════
//  BLE
// ══════════════════════════════════════════════════════════════════════════

// Initialize BLE stack with the given device name (e.g., "KineticAI-L").
// After this, the board driver internally creates the service and all
// characteristics per PROTOCOL.md.
void kai_ble_init(const char* device_name);

// Send a 34-byte motion packet as a notification on the motion characteristic.
// Returns true if the notification was queued, false if not connected.
bool kai_ble_notify_motion(const uint8_t packet[34]);

// True if a central (phone) is currently connected.
bool kai_ble_is_connected();

// Callbacks fired when a phone writes a command. See PROTOCOL.md for command
// IDs. The board driver calls these from its BLE callback thread; your
// handler should be brief.
typedef void (*kai_ble_command_handler_t)(const uint8_t* data, size_t length);
void kai_ble_set_command_handler(kai_ble_command_handler_t handler);

// ══════════════════════════════════════════════════════════════════════════
//  Power management
// ══════════════════════════════════════════════════════════════════════════

void kai_power_set_mode(kai_power_mode_t mode);
kai_power_mode_t kai_power_get_mode();

// ══════════════════════════════════════════════════════════════════════════
//  Debug / logging
// ══════════════════════════════════════════════════════════════════════════

// Initialize a serial/UART channel for debug output. No-op if unavailable.
void kai_debug_init(uint32_t baud_rate);

// Print a line to the debug channel. No-op if unavailable.
void kai_debug_print(const char* msg);

// Printf-style helper. No-op if unavailable.
void kai_debug_printf(const char* fmt, ...);
