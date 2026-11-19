# Wi-Fi Priority (Android)

**English** | [繁體中文](README.md)

Rank your Wi-Fi networks yourself, and the app automatically switches to the highest-ranked network that is in range with a strong enough signal — in the background.

[![Support on Ko-fi](https://img.shields.io/badge/Ko--fi-Buy%20me%20a%20coffee-FF5E5B?logo=ko-fi&logoColor=white)](https://ko-fi.com/sagiriben)

## Download
👉 **[Download the APK (v1.3)](https://github.com/pajh103082/android-wifi-priority-switcher/raw/main/release/WiFi-Priority.apk)**

(The file lives in the [`release/`](release/) folder and is always the latest version.)

| | |
|---|---|
| Requires | Android 8.0 or newer (iPhone is not supported — iOS doesn't let apps control Wi-Fi) |
| Language | English / Traditional Chinese — follows your phone's language, or pick one in Settings |
| Price | Free, no ads |
| Privacy | No internet access used, no data collected; settings stay on your phone |
| Play Store | Not available (see "Why targetSdk 28" below) |

## Install
1. Copy the APK to your phone and tap it (allow "Install unknown apps" when asked).
2. If you see "This app was built for an older version of Android", tap **Install anyway** — this is intentional.
3. If Play Protect blocks it, tap **More details → Install anyway**.

## First-time setup
1. Open the app, allow **Location** and choose **Precise** (Android requires it for apps to see nearby Wi-Fi names). Keep the phone's Location turned on.
2. Tap **＋ Nearby / saved** to add networks to the list. Networks not saved on your phone will ask for a password.
3. Reorder with ▲ ▼ — the top one has the highest priority.
4. Tap the big button at the top to turn it on (green = ON, gray = OFF; tap again to turn off).
   You can also pick how often it checks in **Settings → Check frequency** (10 s – 30 min, default 30 s).
5. Recommended: set location permission to **Allow all the time** and tap **Don't restrict this app**, so it keeps working with the screen off.

To change the language: **Settings → 語言 Language → System / 中文 / English**.

## Switching rules
- Checks at the **check frequency** you choose (default every 30 s; 10 s – 30 min). The app shows the last check time and a countdown to the next one.
- Android limits active scans to roughly once per 30 s, so with shorter intervals some checks reuse the latest scan results.
- When Wi-Fi disconnects or is just turned on, it checks right away instead of waiting for the next round.
- Only switches when the target's signal is stronger than the **switch threshold** (default -75 dBm, adjustable).
- A higher-ranked current network is kept unless its signal drops 5 dBm below the threshold.
- After a switch, waits 45 seconds before switching again; a network that fails to connect is skipped for 5 minutes.
- Every switch is recorded in the in-app **Switch log**.

## Turning it off
- Tap the big button at the top of the app again, or **Turn off** in the notification.
- When off, the background service stops completely, the notification disappears, and it won't start at boot until you turn it on again.

## Permissions
| Permission | Why |
|---|---|
| Location (precise / all the time) | Android only shows nearby Wi-Fi names to apps with location permission. The app never reads or stores your GPS position |
| Wi-Fi state / change Wi-Fi | Read the current connection, switch networks, add network settings |
| Foreground service, notifications | Keep checking in the background and show the status in the notification bar |
| Run at startup | Resume auto-switch after reboot (if you turned it on) |
| Ignore battery optimizations | Optional — prevents Android from killing the app in the background |

## Known limitations
- Some brands (Xiaomi, OPPO, vivo, …) add extra background restrictions — allow "Autostart" or disable battery saving for the app.
- In deep sleep (Doze) checks slow down and catch up when the phone wakes.
- Manual add supports open and WPA/WPA2-Personal networks only. For WPA3 or enterprise networks, connect once in the phone's Wi-Fi settings, then add it from **Nearby / saved**.

## Why targetSdk 28
Since Android 10, Google doesn't let newer apps choose which saved Wi-Fi the phone connects to (`WifiManager.enableNetwork`).
Only apps that declare targetSdk ≤ 28 can still do it, so this app does that on purpose — which also means it can't be published on the Play Store and has to be installed manually.

## Build from source
Requires JDK 17+ and the Android SDK (platform 34, build-tools 34):
```
./gradlew assembleDebug
```
Output: `app/build/outputs/apk/debug/app-debug.apk`

Developer notes (architecture, design decisions, switching logic, translation guide) are in [HANDOFF.md](HANDOFF.md) (Traditional Chinese).

## Support
This app is free. If it helps you, you can **[buy me a coffee on Ko-fi](https://ko-fi.com/sagiriben)** ☕
There's also a **Support the developer** page at the bottom of the app's main screen.
