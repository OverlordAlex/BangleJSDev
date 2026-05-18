# Bangle.js 2 — Development Reference Notes

Quick-reference for developing watch faces and apps on the Bangle.js 2.

## Hardware Specs

| Component | Detail |
|---|---|
| CPU | Nordic nRF52840, ARM Cortex-M4 @ 64 MHz |
| RAM | 256 kB |
| Flash | 1024 kB on-chip + 8 MB external (GD25Q64C/E) |
| Display | 1.3" 176×176, always-on, **3-bit colour** LCD (LPM013M126) |
| Touchscreen | Full touch (Hynitron CST816D) |
| Button | Single side button |
| Heart Rate | Vcare VC31 / VC31B |
| Accelerometer | Kionix KX022 (3-axis) |
| Magnetometer | 3-axis |
| Barometer | Bosch BMP280 / Goertek SPL06 (pressure + temperature) |
| GPS | AT6558 / AT6558R (GPS + GLONASS) |
| Battery | 175 mAh — ~4 weeks standby, ~2 weeks with 1 Hz clock updates |
| Body | 36 × 43 × 12 mm, IP67, standard 20 mm straps |

## Power Budget (key figures)

| State | Current Draw |
|---|---|
| Idle (accel 12.5 Hz) | 0.3 mA |
| Idle (accel 1.25 Hz, after ~120 s still) | 0.15 mA |
| Clock updating 1/sec | ~0.5 mA |
| Heart rate monitor on | +0.7 mA |
| 100% CPU (JS running) | +3 mA |
| LCD touchscreen (unlocked) | +2.5 mA |
| GPS on | +25 mA |
| LCD backlight on | +16 mA |

**Takeaway:** Minimise screen redraws, avoid unnecessary sensor use, keep JS execution short. A clock updating once per minute vs. once per second roughly doubles battery life.

## Display & Graphics

- The global `g` is a `Graphics` instance pointing to the **176×176, 3-bit colour** framebuffer.
- The display is **buffered** — changes only appear after `g.flip()` or when code finishes and returns to idle.
- 3-bit colour means 8 colours: black, red, green, blue, cyan, magenta, yellow, white. Set via `g.setColor(r, g, b)` with 0 or 1 per channel.
- Off-screen buffers: `Graphics.createArrayBuffer(w, h, bpp, {msb:true})` for compositing.
- `g.drawImage(buffer, x, y)` blits an offscreen buffer to the screen.
- Fonts: built-in bitmap fonts (`"6x8"`, `"6x15"`, `"4x6"`) and vector fonts (`"Vector"` with size). Custom fonts loadable.
- Alignment: `g.setFontAlign(h, v, rotation)` — h: -1 left, 0 center, 1 right; v: -1 top, 0 center, 1 bottom.
- Method chaining is supported: `g.setColor(...).setFont(...).drawString(...)`.

## Key Bangle.js APIs

### Clock Registration
```javascript
Bangle.setUI("clock");  // Button press opens launcher
```

### Events
| Event | Fires When |
|---|---|
| `Bangle.on('health', fn)` | Every 10 minutes with `{steps, bpm, movement}` |
| `Bangle.on('HRM', fn)` | Each heart rate reading (when HRM enabled) |
| `Bangle.on('lock', fn)` | Screen lock/unlock — `fn(locked, reason)` |
| `Bangle.on('charging', fn)` | Charge cable connected/disconnected |
| `Bangle.on('midnight', fn)` | At midnight for daily resets |
| `Bangle.on('swipe', fn)` | Swipe gesture — `fn(dirLR, dirUD)` |
| `Bangle.on('touch', fn)` | Tap — `fn(zone, {x,y})` |
| `Bangle.on('drag', fn)` | Drag — `fn({x,y,dx,dy,b})` |

### Useful Functions
```javascript
E.getBattery()           // Battery percentage (integer)
E.getTemperature()       // CPU die temperature in °C
Bangle.buzz(ms, strength) // Vibrate
Bangle.getHealthStatus() // Current health data
process.memory(gc)       // Memory usage; pass true to GC first
```

### Storage
```javascript
require("Storage").writeJSON("file.json", obj);
require("Storage").readJSON("file.json", true); // true = return undefined on error
require("Storage").read("file");                // raw string (flash-backed)
```

### App Registration (run once in console)
```javascript
require("Storage").write("myapp.info", {
  "id": "myapp",
  "name": "My App",
  "type": "clock",
  "src": "myapp.app.js"
});
```

## Performance Tips (from Espruino docs)

1. **Source size matters** — Espruino interprets from source. Whitespace and long names slow execution. Use short variable names in hot loops.
2. **`"ram"` directive** — Add `"ram"` as the first statement in a function to pretokenise and keep in RAM (faster than flash). Safe, broadly compatible, ~10–20% speedup. Uses more RAM since the function body stays in RAM instead of flash. Recommended for hot rendering and data-processing functions.
3. **`"jit"` directive** — JIT-compile a function (firmware 2v16+). **Use with caution.** Does not support all JS constructs (closures capturing mutable variables, `try`/`catch`, complex expressions, `arguments`). Can crash silently on unsupported patterns. Best suited for simple numeric loops only. Avoid for functions using method chaining, closures, or object access patterns.
4. **Typed Arrays > plain Arrays** — `Uint8Array`, `Uint16Array` etc. are stored as flat memory. Plain arrays are linked lists — O(n) access.
5. **Batch operations** — `SPI1.send([1,2,3])` is far faster than three separate calls.
6. **Scope depth** — Local variables are faster to look up than globals.
7. **`process.memory(true)`** — Forces garbage collection; useful after heavy allocation.
8. **Avoid frequent `require()`** — Cache the module reference if used repeatedly.
9. **Off-screen buffers** — Drawing to an `ArrayBuffer` image and blitting once is often faster than many small draw calls to `g`.
10. **Minification** — The Bangle.js App Loader has a pretokenise option. For maximum compression, run Terser (`{ mangle: true, compress: true }`) before upload, then pretokenise on top. `E.compiledC()` can compile C functions inline to native ARM thumb code for compute-heavy sections.

## Fast Load (for snappy launcher transition)

- Add a `remove` handler to `Bangle.setUI({mode:"clock", remove: fn})`.
- Wrap all code in a block `{ ... }` using `let`/`const` instead of `var`.
- Clean up all `setInterval`/`setTimeout` handles and event listeners in the `remove` function.
- This avoids the ~200 ms full-reset penalty when switching apps.

## Clock Info Module

The `clock_info` module provides pluggable info cards (battery, HR, steps, altitude, etc.) that users can cycle through. Clocks can integrate this with `require("clock_info").addInteractive(...)` to display swipeable info areas — a nice way to add extensibility without hardcoding everything.

## BLE UART Data Sync

The built-in BLE UART (NUS — Nordic UART Service) is used for health data sync. BLE is disabled at app startup (`NRF.sleep()`) and woken at midnight for a 1-hour sync window.

### Key APIs
```javascript
Bluetooth.write(data)              // Send string, ArrayBuffer, or typed array over BLE UART
Bluetooth.on('data', fn)           // Receive data over BLE UART
NRF.sleep() / NRF.wake()           // Disable/enable BLE radio
setTime(epochSeconds)              // Set the system clock
```

### Sync Lifecycle
1. **Midnight**: Watch calls `NRF.wake()`, enters sync mode, starts 1-hour timeout.
2. **Android connects** via BLE UART (NUS) and sends `SYNC\n` (text).
3. **Watch responds** with a 16-byte binary header + `histLen * 3` bytes of packed data.
4. **Android sends** `TIME:epochSeconds\n` (text, epoch seconds).
5. **Watch** corrects clock via `setTime()`, clears history, resets daily counters, sends `OK\n`.
6. **Watch** sleeps BLE after 2-second flush delay.
7. **If no sync by 1am**: watch resets daily counters, clears history, sleeps BLE.

The `syncMode` flag prevents the handler from responding when the user manually enables BLE for development.

### Binary Protocol — Data Encoding Spec

The sync payload is raw binary (no JSON). Commands from Android to watch are text; data from watch to Android is binary.

#### History Buffer

A single `Uint8Array(450)` holding up to 150 records (25 hours at 10 min/record) in a compact 3-byte-per-record encoding. Append-only: `histLen` tracks how many records have been written since the last sync. `histStart` is the epoch timestamp (seconds) of the first record.

#### 3-Byte Record Encoding

Each 10-minute health reading is packed into 3 bytes:

```
Byte 0: BPM (uint8, range 0-255)
Byte 1: [steps_high:7][sleep:1]
         Bits 7-1: upper 7 bits of step count (steps >> 8)
         Bit 0:    sleep flag (0 = awake, 1 = asleep)
Byte 2: steps_low (uint8, steps & 0xFF)
```

**Decoding (pseudocode):**
```
bpm   = byte[0]
sleep = byte[1] & 0x01
steps = ((byte[1] >> 1) << 8) | byte[2]
```

Step count range: 0–32767 (15 bits). Sufficient for 10-minute intervals (max realistic ~1200 steps at 120 steps/min).

Records are stored sequentially starting at `hist[0]`. Record N is at byte offset `N * 3`. The timestamp of record N is `histStart + N * 600` (each record is 10 minutes = 600 seconds).

#### 16-Byte Binary Header (little-endian)

Sent immediately before the data bytes on SYNC:

| Offset | Type | Field | Description |
|--------|------|-------|-------------|
| 0 | uint32 | histStart | Epoch seconds of the first record |
| 4 | uint8 | histLen | Number of 3-byte records that follow (max 150) |
| 5 | uint8 | battery | Watch battery percentage (0-99), captured before BLE wake for idle accuracy |
| 6 | uint16 | bpmAvg_x10 | Average BPM × 10 (preserves 1 decimal) |
| 8 | uint16 | bpmResting_x10 | Resting BPM × 10 |
| 10 | uint16 | stepCalDay | Daily step-based calories (integer) |
| 12 | uint16 | bpmCalDay | Daily HR-based calories (integer) |
| 14 | uint16 | sleepTotal | Total sleep in minutes |

All multi-byte values are **little-endian** (native nRF52840 byte order).

#### Payload Size

- Header: 16 bytes (fixed)
- Data: `histLen × 3` bytes (max 450 bytes for 150 entries)
- **Max total: 466 bytes** — ~24 BLE packets at 20-byte MTU, streams in under 1 second
- No JSON serialization, no temporary string allocation

#### Android Parsing Summary

1. Connect to the Bangle.js BLE UART (NUS service UUID `6e400001-b5a3-f393-e0a9-e50e24dcca9e`).
2. Write `SYNC\n` to the RX characteristic (`6e400002-...`).
3. Read from the TX characteristic (`6e400003-...`) notifications:
   a. First 16 bytes = header. Parse `histLen` at offset 4 (uint8), `battery` at offset 5 (uint8).
   b. Next `histLen × 3` bytes = packed records.
4. Reconstruct timeline: record `i` timestamp = `histStart + i × 600`.
5. Decode each 3-byte record per the encoding above.
6. Write `TIME:<current_epoch_seconds>\n` to RX characteristic.
7. Read `OK\n` from TX notifications.
8. Disconnect.

### Power Notes
- BLE is disabled at startup via `NRF.sleep()`. Zero idle BLE power draw.
- The midnight sync window draws ~0.3 mA (BLE advertising) for up to 1 hour.
- An active BLE connection draws ~0.75 mA for the ~1–2 seconds of data transfer.
- The nRF52840 crystal drifts ~±40 ppm (~3.5 sec/day). Daily sync keeps drift imperceptible.

## Development Workflow

1. Use the Espruino Web IDE: https://www.espruino.com/ide/
2. Connect via Web Bluetooth (Chrome/Edge/Opera).
3. Upload to **RAM** for development (fast iteration).
4. Save to **Storage** as `myapp.app.js` for persistent deployment.
5. Register the app with a `.info` file (see above).
6. Emulator available at https://www.espruino.com/ide/emulator.html (no sensors/BT).

## Key Documentation Links

- **Device overview:** https://www.espruino.com/Bangle.js2
- **API reference:** https://espruino.com/ReferenceBANGLEJS2
- **Graphics API:** https://www.espruino.com/Reference#Graphics
- **Performance guide:** https://www.espruino.com/Performance
- **Code style guide:** https://www.espruino.com/Code+Style
- **Clock tutorial:** https://www.espruino.com/Bangle.js+Clock
- **Fast load tutorial:** https://www.espruino.com/Bangle.js+Fast+Load
- **Clock Info module:** https://www.espruino.com/Bangle.js+Clock+Info
- **App loader:** https://banglejs.com/apps
- **Community apps repo:** https://github.com/espruino/BangleApps
- **Image converter:** https://www.espruino.com/Image+Converter
- **Source repository:** https://github.com/OverlordAlex/BangleJSDev
