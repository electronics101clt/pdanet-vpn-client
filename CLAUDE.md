# PdaNet Client Android App

**Project**: pdanet-client
**Location**: `/home/jonathan/Desktop/pdanet-client/`
**Status**: ✅ **FULLY WORKING** — Services survive boot, no app idle kills (2026-02-20)

**⚠️ CRITICAL**: Read `SETTINGS-INCIDENT-2026-02-19.md` before touching ANY network settings. Previous Claude instance broke connectivity by injecting wrong settings via ADB.

---

## What This App Does

Standalone Android APK (no root required) that transparently routes all device traffic through a PdaNet+ WiFi Direct hotspot. Apps on the device see a normal internet connection — no proxy configuration needed.

---

## How PdaNet+ Works

- Phone runs PdaNet+ and creates a WiFi Direct hotspot (`DIRECT-xx-...-PdaNet` SSID)
- Gateway: `192.168.49.1`
- HTTP proxy at: `192.168.49.1:8000`
- The proxy handles both HTTP and HTTPS (via CONNECT tunneling)

---

## How This App Works

```
App traffic → tun0 (VpnService) → tun2proxy (Rust, JNI) → 192.168.49.1:8000 → PdaNet phone → Internet
```

**Step by step:**
1. Service starts at boot (`BootReceiver`)
2. Registers a `NetworkCallback` for WiFi networks (without INTERNET/VALIDATED capability requirements)
3. On network available/changed: reads default route gateway from `LinkProperties`
4. If gateway == `192.168.49.1` and VPN permission already granted → `startTunnel()`
5. If gateway == `192.168.49.1` and VPN permission NOT granted → launches `VpnPermissionActivity` (system dialog)
6. If VPN already running → ignores network events (`checkAndUpdateTunnel` returns early if `vpn != null`)
7. Only `onLost` stops the tunnel — not link property changes or Android's internet validation opinion

**tun2proxy runs on a daemon background thread** — the JNI call blocks until shutdown. Running it on the main thread caused the service to ANR and get killed after 30 seconds.

**Routing excludes 192.168.0.0/16** — to prevent a routing loop where tun2proxy's own connection to the proxy gets intercepted by the VPN. The proxy subnet goes directly via wlan0.

**Virtual DNS (Fake-IP mode)** — HTTP proxies don't support UDP, so DNS doesn't work through the proxy directly. tun2proxy intercepts DNS queries and maps domains to fake IPs in `198.18.0.0/15`. When a connection hits a fake IP, tun2proxy sends `CONNECT domain:port` to the proxy which resolves and forwards it.

---

## What the App Does NOT Do

- Does NOT set system proxy settings (`http_proxy`, `global_http_proxy_host`, etc.)
- Does NOT clear system proxy settings (removed — not needed, VPN handles everything)
- Does NOT use `WRITE_SECURE_SETTINGS`
- Does NOT use `appops` or any ADB-injected permissions
- Does NOT require ADB after install
- Does NOT show any UI window

---

## User Experience

**First install:**
- User taps the app icon
- If VPN permission not yet granted: system VPN dialog appears immediately
- User taps Allow once
- App finishes, service runs silently in background
- Notification appears in tray showing status

**Every boot after:**
- `BootReceiver` starts the service silently
- When device connects to PdaNet WiFi: tunnel starts automatically, notification updates to "Connected via PdaNet"
- If somehow permission was revoked: VPN dialog appears automatically when PdaNet is detected

**Zero ongoing user interaction required.**

---

## Files

```
app/src/main/
├── kotlin/
│   ├── com/pdanetclient/
│   │   ├── MainActivity.kt          # Transparent launcher — checks VPN perm, starts service, finishes
│   │   ├── VpnPermissionActivity.kt # Transparent — shows system VPN dialog, starts service on grant
│   │   └── BootReceiver.kt          # Starts service on BOOT_COMPLETED
│   ├── tun/proxy/service/
│   │   └── Tun2HttpVpnService.kt    # Core: detection, VPN setup, tun2proxy integration
│   ├── tun/utils/
│   │   └── Util.kt                  # DNS server helper (ConnectivityManager + getprop fallback)
│   └── com/github/shadowsocks/bg/
│       └── Tun2proxy.kt             # Kotlin JNI wrapper for libtun2proxy.so
├── jniLibs/
│   ├── arm64-v8a/libtun2proxy.so    # Pre-built Rust native lib (~6.5MB)
│   ├── armeabi-v7a/libtun2proxy.so  # Pre-built Rust native lib (~4.5MB)
│   ├── x86_64/libtun2proxy.so
│   └── x86/libtun2proxy.so
├── res/
└── AndroidManifest.xml
```

---

## Permissions

```xml
INTERNET
ACCESS_NETWORK_STATE
ACCESS_WIFI_STATE
CHANGE_NETWORK_STATE
RECEIVE_BOOT_COMPLETED
FOREGROUND_SERVICE
FOREGROUND_SERVICE_DATA_SYNC
WAKE_LOCK
REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
BIND_VPN_SERVICE  <!-- on the service declaration -->
```

No `WRITE_SECURE_SETTINGS`. No location permissions.

---

## Recent Fixes (2026-02-20)

### Fixed "Service Headache" - Both Services Now Survive Boot

**Problem**: Both PdaNetMonitorService and Tun2HttpVpnService were being killed by Android shortly after starting, especially when started from boot receiver.

**Root Causes**:
1. **PdaNetMonitorService**: Had `foregroundServiceType="dataSync"` but missing `FOREGROUND_SERVICE_DATA_SYNC` permission → Android killed it with "app idle"
2. **Tun2HttpVpnService**: Called `startForegroundService()` on Android O+ but never called `startForeground()` → Android killed it after 5 seconds with "Bringing down service while still waiting for start foreground"

**Fixes Applied**:
1. Added `FOREGROUND_SERVICE_DATA_SYNC` permission to manifest
2. Made Tun2HttpVpnService call `startForeground()` in `onStartCommand()` with its own notification channel
3. Added `foregroundServiceType="dataSync"` to VPN service manifest declaration
4. Added battery optimization exemption request in MainActivity (prevents "app idle" kills when started from boot)
5. MainActivity now requests battery optimization exemption before VPN permission

**Result**: Both services now survive boot, stay alive indefinitely, and tunnel works without any ADB-injected settings.

**Files Modified**:
- `app/src/main/kotlin/tun/proxy/service/Tun2HttpVpnService.kt` - Added foreground notification
- `app/src/main/kotlin/com/pdanetclient/MainActivity.kt` - Added battery optimization request
- `app/src/main/AndroidManifest.xml` - Added permissions and foregroundServiceType

---

## Key Implementation Details

### NetworkRequest — no capability requirements

```kotlin
NetworkRequest.Builder()
    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
    .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    .removeCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    .build()
```

Without removing these, Android's captive portal check fails on PdaNet WiFi (it can't reach validation URLs without the proxy), triggers `onLost`, and kills the tunnel ~30 seconds after connect.

### Routing table — excludes 192.168.0.0/16

```kotlin
builder.addRoute("0.0.0.0", 1)       // 0.0.0.0   - 127.255.255.255
builder.addRoute("128.0.0.0", 2)     // 128.0.0.0  - 191.255.255.255
builder.addRoute("192.0.0.0", 9)     // 192.0.0.0  - 192.127.255.255
builder.addRoute("192.128.0.0", 11)  // 192.128.0.0- 192.159.255.255
builder.addRoute("192.160.0.0", 13)  // 192.160.0.0- 192.167.255.255
// 192.168.0.0/16 excluded — proxy lives here
builder.addRoute("192.169.0.0", 16)
builder.addRoute("192.170.0.0", 15)
builder.addRoute("192.172.0.0", 14)
builder.addRoute("192.176.0.0", 12)
builder.addRoute("192.192.0.0", 10)
builder.addRoute("193.0.0.0", 8)
builder.addRoute("194.0.0.0", 7)
builder.addRoute("196.0.0.0", 6)
builder.addRoute("200.0.0.0", 5)
builder.addRoute("208.0.0.0", 4)
builder.addRoute("224.0.0.0", 3)
```

### tun2proxy call

```kotlin
Thread({
    val result = Tun2proxy.start(
        proxyUrl = "http://192.168.49.1:8000",
        tunFd = pfd.fd,
        closeFdOnDrop = false,
        tunMtu = 1500,
        verbosity = Tun2proxy.Verbosity.INFO,
        dnsStrategy = Tun2proxy.DnsStrategy.OVER_TCP
    )
    Log.i(TAG, "tun2proxy exited with code $result")
}, "tun2proxy").apply { isDaemon = true; start() }
```

### DNS Strategy — why OVER_TCP, not VIRTUAL

**VIRTUAL (fake-IP) was causing audio streaming to stall mid-song.**

VIRTUAL mode assigns fake IPs from `198.18.0.0/15` to resolved domains and maintains a TTL-based mapping table. For short-lived API connections this is fine — those domains are resolved constantly, keeping the mapping fresh. For audio streaming (e.g. YouTube Music), the CDN connection is long-lived. When the initial segment finishes and the player opens a new connection to the next CDN segment, the fake-IP mapping for that CDN host has expired. tun2proxy has no domain name to put in the `CONNECT` request, so it sends `CONNECT 198.18.0.x:443` (fake IP) to PdaNet's proxy. The proxy tries to reach `198.18.0.x` on the real internet — unreachable — connection dies with zero bytes.

Observed in logs: new CDN connections dying with `(Ok(0), Ok(0))` immediately after the previous audio segment completed.

**OVER_TCP** forwards DNS queries as TCP through the proxy instead. tun2proxy gets real domain names and sends `CONNECT googlevideo.com:443` to the proxy. No expiring fake-IP table. Streaming continues past segment boundaries.

---

## Build & Install

### Debug Build (Development)

```bash
cd /home/jonathan/Desktop/pdanet-client

# Clean build
./gradlew clean assembleDebug

# Install (fresh)
adb uninstall com.pdanetclient
adb install app/build/outputs/apk/debug/app-debug.apk

# Or reinstall keeping data/permissions
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Release Build (Signed APK)

```bash
# Build release APK
./gradlew assembleRelease

# Zipalign
zipalign -v -p 4 app/build/outputs/apk/release/app-release-unsigned.apk \
  app/build/outputs/apk/release/app-release-unsigned-aligned.apk

# Sign with release keystore
apksigner sign --ks pdanet-release.jks \
  --ks-pass pass:pdanet123 \
  --key-pass pass:pdanet123 \
  --out pdanet-client-signed.apk \
  app/build/outputs/apk/release/app-release-unsigned-aligned.apk

# Verify signature
apksigner verify pdanet-client-signed.apk
```

**Keystore**: `pdanet-release.jks` (password: `pdanet123`)
**Signed APK**: `pdanet-client-signed.apk` (12 MB)

**ADB is for development install only. Never inject settings via ADB.**

---

## 🚨 ABSOLUTE PROHIBITION: NO ADB SETTINGS MODIFICATIONS

**WHEN WORKING ON THIS PROJECT, DO NOT MODIFY ANDROID SETTINGS VIA ADB. EVER.**

```
❌ adb shell settings put ...
❌ adb shell settings delete ...
❌ adb shell content insert ...
❌ NO modifications to WiFi settings
❌ NO modifications to network settings
❌ NO modifications to VPN settings
❌ NO modifications to DNS settings
❌ NO modifications to proxy settings
❌ NO modifications to captive portal settings
```

### Why This Rule Exists

**PROBLEM**: Claude working on connectivity issues arbitrarily injected settings like:
- `private_dns_default_mode = off`
- `captive_portal_http_url = null`
- `captive_portal_https_url = null`

**RESULT**: Broke device WiFi connectivity outside home network.

**THE APP'S JOB**: If this app needs certain network conditions or settings to work properly, **THE APP CODE MUST DETECT, REQUEST, OR HANDLE THEM.**

**NOT CLAUDE'S JOB**: Injecting arbitrary WiFi/network settings via ADB to make broken app code appear to work.

### If The App Doesn't Work

**DO NOT**: Run `adb shell settings put ...` to "fix" it

**DO**: Fix the app code to handle the situation properly:
- App detects wrong settings → shows user a message/guide
- App needs permission → requests it via Android APIs
- App needs network condition → waits/retries/notifies user
- App has bug → fix the bug in the code

**THIS APP WORKS WITHOUT ANY ADB-INJECTED SETTINGS. If it doesn't work, that's a CODE BUG, not a settings problem.**

## ADB — Read/Status Only

```bash
# Logs
adb logcat -d | grep -E "PdaNetVpn|tun2proxy"

# Interface
adb shell ifconfig tun0

# Routing
adb shell ip rule list

# System proxy (should be null)
adb shell settings get global http_proxy

# Network settings (READ ONLY - for diagnostics)
adb shell settings get global private_dns_default_mode
adb shell settings get global captive_portal_http_url
adb shell settings list global | grep -E "(wifi|network|captive)"
```

---

## Device

**AC8257 UJC201 car head unit — API 28 (Android 9)**
- Package: `com.pdanetclient`
- VPN permission: granted via user dialog (not ADB)
- APK: `app/build/outputs/apk/debug/app-debug.apk`
