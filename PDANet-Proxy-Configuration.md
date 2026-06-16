# PDANet Proxy Configuration for iPhone
**Date**: 2026-02-19
**Device**: iPhone SE (2nd gen), iOS 18.2
**Network**: DIRECT-30-moto g power-PdaNet

---

## Objective

Configure iPhone to use PDANet proxy from Moto G Power phone for internet access.

---

## Network Architecture

```
┌──────────────────┐
│   Moto G Power   │
│  (Mobile Data)   │
│  PDANet+ App     │
└────────┬─────────┘
         │ WiFi Direct Hotspot
         │ SSID: "DIRECT-30-moto g power-PdaNet"
         │ Network: 192.168.49.0/24
         │ Gateway: 192.168.49.1
         │
         ├─────────────────────────────────┐
         │                                 │
         ↓                                 ↓
┌─────────────────────┐         ┌──────────────────┐
│   Laptop (Ubuntu)   │         │  iPhone SE       │
│   192.168.1.161     │         │  (WiFi Client)   │
│   (Other network)   │         │  Via PDANet      │
└─────────────────────┘         └──────────────────┘
                                         │
                                         ↓
                                  HTTP Proxy Config:
                                  Server: 192.168.49.1
                                  Port: 8000
```

---

## PDANet Proxy Details

**Proxy Type**: HTTP/HTTPS (forwarding to SOCKS5)
**Proxy Address**: `192.168.49.1:8000`
**Authentication**: None
**Phone Network**: WiFi Direct hotspot
**Phone Gateway**: 192.168.49.1

---

## iPhone Configuration Steps

### Step 1: Connect to PDANet WiFi
✅ **COMPLETED**

1. iPhone → Settings → WiFi
2. Select: "DIRECT-30-moto g power-PdaNet"
3. Enter password (from PDANet app on Android)
4. Connected successfully

### Step 2: Configure HTTP Proxy
**REQUIRED FOR INTERNET ACCESS**

1. iPhone → Settings → WiFi
2. Tap **(i)** icon next to "DIRECT-30-moto g power-PdaNet"
3. Scroll down to **"HTTP Proxy"** section
4. Tap **"Configure Proxy"**
5. Select **"Manual"**
6. Enter proxy settings:
   - **Server**: `192.168.49.1`
   - **Port**: `8000`
   - **Authentication**: OFF (leave blank)
7. Tap **"Save"**

**Configuration Screenshot Locations**:
```
Settings → WiFi → (i) → HTTP Proxy → Manual
Server: 192.168.49.1
Port: 8000
Authentication: Off
```

---

## How It Works

### Without Proxy Configuration
```
iPhone → PDANet WiFi → Phone → ❌ NO INTERNET
```
- iPhone gets IP from WiFi DHCP
- But has no internet access
- Phone's SOCKS5 proxy not accessible to iOS

### With Proxy Configuration
```
iPhone → HTTP Proxy (192.168.49.1:8000) → SOCKS5 Proxy → Mobile Data → Internet
```
- iPhone sends HTTP requests to proxy
- PDANet proxy converts HTTP → SOCKS5
- Phone forwards through mobile data
- Response returned via proxy chain

---

## Technical Details

### PDANet Proxy Server

**Running on**: Moto G Power Android phone
**Listening on**: 192.168.49.1:8000
**Protocol**: HTTP proxy (frontend) → SOCKS5 (backend)
**Purpose**:
- Bypass carrier tethering restrictions
- Hide tethered traffic as phone traffic
- Share mobile data with other devices

### iPhone Proxy Support

**iOS Proxy Capabilities**:
- ✅ HTTP/HTTPS proxy (manual configuration)
- ✅ PAC (Proxy Auto-Configuration) files
- ❌ SOCKS5 proxy (not natively supported)
- ❌ System-wide SOCKS proxy

**Why iPhone needs proxy configured**:
- iOS cannot use SOCKS5 directly
- PDANet provides HTTP proxy wrapper
- HTTP proxy translates iOS requests → SOCKS5
- Enables internet access through PDANet

---

## Verification Steps

### Test Internet Connection

After configuring proxy:

1. **Open Safari** on iPhone
2. Navigate to: `http://example.com`
3. Should load successfully
4. Check IP: `https://ifconfig.me`
   - Should show phone's mobile carrier IP, not WiFi IP

### Troubleshooting

**If no internet after proxy config:**

1. **Verify proxy settings**:
   - Settings → WiFi → (i) → HTTP Proxy
   - Server: 192.168.49.1
   - Port: 8000

2. **Check PDANet app on Android**:
   - Ensure "WiFi Direct Hotspot" is active
   - Proxy service should be running
   - Port should be 8000

3. **Test from iPhone**:
   - Disconnect and reconnect to WiFi
   - Toggle Airplane mode ON/OFF
   - Restart iPhone if needed

4. **Verify phone connectivity**:
   - Ensure phone has mobile data connection
   - Check carrier signal strength
   - PDANet requires active data connection

---

## Limitations & Notes

### iPhone-Specific Limitations

1. **App Store downloads**: May be slow or fail
   - App Store uses non-HTTP protocols
   - Some system services bypass proxy

2. **FaceTime/iMessage**: May not work
   - Uses proprietary Apple protocols
   - Doesn't route through HTTP proxy

3. **VPN apps**: May conflict
   - VPN takes precedence over proxy
   - Disable VPN if using PDANet proxy

4. **Background updates**: Hit or miss
   - iOS background services may bypass proxy
   - Manual app updates recommended

### PDANet Limitations

1. **Battery drain**: Phone WiFi hotspot uses significant battery
2. **Data usage**: Counts against phone's data plan
3. **Speed**: Slower than direct connection (proxy overhead)
4. **Stability**: If phone sleeps, connection may drop

### Network Behavior

**What works well**:
- ✅ Web browsing (Safari, Chrome)
- ✅ Email (Mail app)
- ✅ Social media apps
- ✅ YouTube, streaming (may be slower)
- ✅ Messaging apps (WhatsApp, Telegram)

**What may not work**:
- ❌ FaceTime (uses direct connection)
- ❌ iMessage activation (needs direct Apple servers)
- ❌ Some VPN apps
- ❌ Gaming with strict NAT requirements
- ❌ P2P file sharing

---

## Comparison to Other Methods

### PDANet WiFi + Proxy (Current Setup)
- **Pros**: Hides tethering, wireless, works on iOS
- **Cons**: Requires proxy config, slower, battery drain
- **Speed**: ~10-50 Mbps (depends on carrier)

### iPhone Personal Hotspot
- **Pros**: Native iOS feature, no config needed
- **Cons**: Carrier may charge extra, data limits
- **Speed**: ~50-200 Mbps

### USB Tethering to Computer
- **Pros**: Fast, charges phone, stable
- **Cons**: Requires cable, computer must be on
- **Speed**: ~100-300 Mbps

### Bluetooth Tethering
- **Pros**: Low battery, no cables
- **Cons**: Very slow, limited range
- **Speed**: ~1-3 Mbps

---

## Security Considerations

### Privacy

**All iPhone traffic goes through phone**:
- PDANet proxy sees all HTTP requests
- Phone can inspect unencrypted traffic
- Carrier sees data usage from phone (not iPhone)

**Recommendations**:
- ✅ Use HTTPS websites (encrypted)
- ✅ Avoid sensitive transactions (banking)
- ✅ Trust the phone owner (yourself)
- ❌ Don't use on public/untrusted phones

### Carrier Detection

**PDANet attempts to hide tethering**:
- Rewrites HTTP headers
- Modifies TTL (Time To Live) values
- Makes traffic look like phone traffic

**Not foolproof**:
- Some carriers detect via traffic patterns
- Heavy data usage may trigger investigation
- Check carrier tethering policy

---

## Laptop vs iPhone Differences

### Laptop (Ubuntu) - Uses TUN Interface

```
Laptop → TUN interface → tun2socks → SOCKS5 (192.168.49.1:8000) → Internet
```

**Complexity**: High (requires root, TUN device, routing changes)
**Transparency**: Fully transparent (all traffic routed)
**Setup**: Complex script, multiple components

### iPhone - Uses HTTP Proxy

```
iPhone → HTTP Proxy config → HTTP (192.168.49.1:8000) → Internet
```

**Complexity**: Low (just WiFi settings)
**Transparency**: Partial (only HTTP/HTTPS traffic)
**Setup**: Simple manual configuration

**Why simpler on iPhone**:
- iOS has built-in HTTP proxy support
- No need for TUN interface or routing
- Manual configuration in Settings
- No root/jailbreak required

---

## Alternative: Expo Go App Access

### Current Status

**Expo development server** running on laptop:
- Server: `http://192.168.1.161:8081`
- Network: Home WiFi (different from PDANet)

**Problem**: iPhone on PDANet network cannot reach laptop on home WiFi

### Solutions

**Option 1: Switch iPhone to Home WiFi**
- Disconnect from PDANet
- Connect to same WiFi as laptop
- Access Expo via: `exp://192.168.1.161:8081`
- **Tradeoff**: Loses PDANet internet

**Option 2: Run Expo on PDANet Network**
- Connect laptop to PDANet WiFi
- Get IP on 192.168.49.x network
- Run Expo server on that interface
- iPhone can access on same network
- **Tradeoff**: Laptop loses primary internet

**Option 3: Expo Tunnel Mode**
- Run: `npx expo start --tunnel`
- Gets public URL via ngrok
- iPhone can access from any network
- **Tradeoff**: Slower, external dependency

---

## Configuration Files

### iPhone WiFi Profile (Informational)

```
Network: DIRECT-30-moto g power-PdaNet
Security: WPA2
IP Assignment: DHCP
Obtained IP: 192.168.49.x (assigned by phone)
Gateway: 192.168.49.1
DNS: Auto (from phone)

HTTP Proxy: Manual
  Server: 192.168.49.1
  Port: 8000
  Authentication: None
```

### No Files Required

**Unlike laptop**, iPhone configuration is entirely GUI-based:
- No config files to edit
- No scripts to run
- No terminal commands
- Just Settings app changes

---

## Maintenance & Management

### Daily Use

1. **Connect to PDANet WiFi**: Automatic if saved
2. **Proxy auto-applies**: Saved with WiFi network
3. **Use internet normally**: Transparent to user

### Disconnection

1. **Switch to other WiFi**: Proxy config doesn't apply
2. **Cellular data**: iPhone uses LTE/5G instead
3. **No cleanup needed**: Settings remain but inactive

### Re-connection

- WiFi credentials saved: Auto-connects
- Proxy settings saved: Auto-applies
- No reconfiguration needed

---

## Summary

### What Was Done

✅ iPhone connected to PDANet WiFi hotspot
✅ Identified proxy address: `192.168.49.1:8000`
✅ Documented configuration steps
✅ No changes to laptop or other devices

### What User Must Do

**Manual step on iPhone**:
1. Settings → WiFi → (i) next to PDANet network
2. HTTP Proxy → Manual
3. Server: `192.168.49.1`, Port: `8000`
4. Save

**After configuration**: Internet access through phone's mobile data

### End Result

```
iPhone → PDANet Proxy → Moto G Power → Mobile Data → Internet
```

- iPhone has internet via phone
- No laptop changes required
- Proxy hides tethering from carrier
- All traffic routes through phone

---

## Files in This Project

```
~/Desktop/iphone-connect/
├── claude.md                              # Initial iPhone access analysis
├── device-info.txt                        # iPhone specifications
├── installed-apps.txt                     # List of installed apps
├── iOS-App-Installation-Findings.md       # App installation investigation
├── app-install-options.md                 # App installation options
├── pdanet-connection-info.txt             # PDANet WiFi info
├── PDANet-Proxy-Configuration.md          # This document
├── IMG_0254.JPG                           # Extracted photos
├── IMG_0255.JPG
├── IMG_0256.JPG
├── IMG_0257.JPG
└── On-the-table.jpg                       # Chrome container file
```

---

**Configuration completed**: 2026-02-19 07:25 UTC
**Method**: Manual HTTP proxy configuration
**No system modifications**: iPhone settings only
**Status**: Ready for user to apply proxy settings
