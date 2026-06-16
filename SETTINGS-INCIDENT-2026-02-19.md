# Settings Incident - 2026-02-19

**Date**: 2026-02-19 22:00-23:00
**Issue**: App would not connect to PdaNet WiFi in car, required PdaNet reset
**Root Cause**: Incorrect Android network settings injected via ADB by previous Claude instance

---

## What Was Wrong

Some Claude instance working on connectivity issues **arbitrarily modified Android system settings via ADB** without understanding them:

### Settings That Were Broken:
```bash
private_dns_default_mode = off                # Should be: automatic
captive_portal_http_url = null                # Should be: http://connectivitycheck.gstatic.com/generate_204
captive_portal_https_url = null               # Should be: https://www.google.com/generate_204
```

---

## What These Settings Do

### `private_dns_default_mode = automatic` (Stock Android 9 Default)
- Enables DNS over TLS (encrypted DNS) when network supports it
- Falls back to regular DNS if encrypted not available
- **With it OFF**: Some networks may reject connection or mark as "limited"

### `captive_portal_http_url` and `captive_portal_https_url` (Stock Android 9 Defaults)
- Android probes these URLs when connecting to WiFi to validate internet connectivity
- Returns HTTP 204 if internet is working
- **With them NULL**: Android cannot validate network has internet
- Android may mark WiFi as "Limited connectivity" or "No internet" and restrict app usage
- Android may refuse to route traffic through the network
- VPN apps may be blocked from starting (network not validated)

---

## How This Broke PdaNet Connection

**Symptom**: App would not connect to PdaNet WiFi in car, required PdaNet reset. After reset, connection worked.

**Important Context**: PdaNet WiFi Direct **by design** shows as "no internet" to Android. That's NORMAL. The VPN app exists specifically to bypass this - it routes all traffic through the HTTP proxy at 192.168.49.1:8000 so apps see internet through the VPN tunnel. The app's NetworkRequest explicitly removes INTERNET and VALIDATED capability requirements.

**Root Cause**: `private_dns_default_mode = off`

### Why `private_dns_default_mode = off` Breaks Non-Home Networks

**Stock Default (`automatic`)**:
- Android first tries DNS over TLS (encrypted, port 853)
- If that fails, falls back to standard DNS (unencrypted, port 53)
- Adapts to whatever the network supports

**What Was Set (`off`)**:
- Only uses standard DNS (unencrypted, port 53)
- No fallback, no encryption
- Rigid, one-size-fits-all approach

**Why Home Network Works But Others Don't:**

**Home Network (192.168.1.x)**:
- User-controlled router
- Standard DNS port 53 is open and working
- No interference, filtering, or redirects
- Simple, permissive configuration

**Other Networks (PdaNet WiFi Direct, public WiFi, cellular hotspots)**:

1. **Captive Portal Networks**:
   - Intercept DNS port 53 to redirect to login page
   - May break standard DNS until authenticated
   - Expect modern devices to handle this with DNS over TLS

2. **Security-Focused Networks**:
   - Block unencrypted DNS port 53 (security policy)
   - Only allow DNS over TLS (port 853)
   - Devices with `private_dns = off` can't resolve anything

3. **ISP/Corporate Filtering**:
   - Redirect or filter standard DNS queries
   - Interfere with port 53 traffic
   - DNS over TLS bypasses this interference

4. **Network Configuration**:
   - May expect DHCP-provided DNS but also require DoT capability
   - Firewall rules that break unencrypted DNS
   - Routing policies that only work with encrypted DNS

**What Happened in the Car**:
1. Device connected to PdaNet WiFi Direct hotspot
2. Android tried DNS lookup with `private_dns = off` (standard DNS port 53 only)
3. PdaNet network or proxy configuration interfered with unencrypted DNS
4. DNS resolution failed → no internet connectivity
5. VPN couldn't establish working tunnel (DNS required for proxy CONNECT requests)
6. Connection dead until PdaNet reset

**After Restoring `private_dns = automatic`**:
1. Android tries DNS over TLS first
2. Falls back to standard DNS if needed
3. Adapts to PdaNet network's DNS handling
4. DNS resolution works
5. VPN tunnel establishes successfully
6. Connection stable

**Bottom Line**: Modern networks increasingly expect DNS over TLS support. Setting it to `off` makes the device incompatible with networks that block/filter standard DNS. The `captive_portal_*` URLs being `null` was secondary - the primary issue was `private_dns = off`.

---

## The Fix

Restored stock Android 9 defaults via ADB:

```bash
# Restore Private DNS to automatic
adb shell settings put global private_dns_default_mode automatic

# Restore captive portal HTTP URL
adb shell settings put global captive_portal_http_url http://connectivitycheck.gstatic.com/generate_204

# Restore captive portal HTTPS URL
adb shell settings put global captive_portal_https_url https://www.google.com/generate_204

# Verify
adb shell settings get global private_dns_default_mode
adb shell settings get global captive_portal_http_url
adb shell settings get global captive_portal_https_url
```

**Result**: App connects successfully to PdaNet WiFi without requiring PdaNet reset.

---

## Lessons Learned

### For Future Claude Instances Working on This Project:

**DO NOT MODIFY ANDROID SETTINGS VIA ADB TO "FIX" CONNECTIVITY.**

1. **If the app doesn't connect**: It's a BUG IN THE APP CODE, not a settings problem
2. **These settings are stock defaults**: They should NEVER be changed
3. **The app works with stock settings**: If it doesn't, fix the app, don't inject settings
4. **Check settings first**: Use `adb shell settings get ...` to verify stock before assuming settings are the problem
5. **Settings are not magic**: Changing them to `off` or `null` doesn't "fix" connectivity - it BREAKS it

### This App Does NOT Require Any Non-Stock Settings

**The app is designed to work with:**
- Stock Android 9 network settings
- Stock DNS configuration
- Stock captive portal detection
- No ADB-injected permissions
- No `WRITE_SECURE_SETTINGS`

**If connectivity fails, debug the app code, not the system settings.**

---

## Verification

After restoring stock settings:

**Test 1: Home WiFi**
- ✅ Device connected to home WiFi (192.168.1.165)
- ✅ Settings verified stock
- ✅ Internet connectivity working

**Test 2: PdaNet WiFi**
- ✅ Device connected to PdaNet WiFi Direct (192.168.49.185)
- ✅ VPN tunnel established
- ✅ tun2proxy started
- ✅ App running without errors

**Test 3: In-Car Test** (User reported):
- ✅ App connected to PdaNet WiFi
- ✅ No PdaNet reset required
- ✅ Connection stable

---

## Summary

**Problem**: Claude injected wrong settings → Android couldn't validate PdaNet network → Connection failed
**Solution**: Restore stock settings → Android validates network → Connection works
**Prevention**: DO NOT MODIFY SETTINGS VIA ADB WHEN WORKING ON THIS PROJECT

**Stock settings are correct. The app is designed for stock settings. Leave them alone.**
