# Proxy Option — System Proxy + Captive Portal Bypass

**Date**: 2026-02-18
**Device**: AC8257 UJC201 (O7VODQ9HR48LDE89, API 28)
**Status**: Working

---

## What This Does

Sets the Android system proxy to route through PdaNet's HTTP proxy at
`192.168.49.1:8000`, then disables Android's captive portal check so it
doesn't show "no internet" and block connections.

---

## Commands (in order)

```bash
# 1. Set the system proxy — both settings required
adb shell settings put global http_proxy 192.168.49.1:8000
adb shell settings put global global_http_proxy_host 192.168.49.1
adb shell settings put global global_http_proxy_port 8000

# 2. Disable captive portal check so Android stops showing "no internet"
adb shell settings put global captive_portal_mode 0

# 3. Set captive portal server (belt and suspenders)
adb shell settings put global captive_portal_server connectivitycheck.gstatic.com

# 4. Toggle WiFi to force Android to re-validate with new settings
adb shell svc wifi disable
# wait 2 seconds
adb shell svc wifi enable
```

---

## Verify

```bash
adb shell settings list global | grep -i proxy
# Expected:
# http_proxy=192.168.49.1:8000
# global_http_proxy_host=192.168.49.1
# global_http_proxy_port=8000

adb shell settings get global captive_portal_mode
# Expected: 0
```

---

## Clear / Restore

```bash
# Remove proxy
adb shell settings delete global http_proxy
adb shell settings delete global global_http_proxy_host
adb shell settings delete global global_http_proxy_port

# Re-enable captive portal check
adb shell settings put global captive_portal_mode 1
```

---

## What Triggers It (Full Sequence — Confirmed Working 2026-02-18)

1. Set proxy (`http_proxy` + `global_http_proxy_host/port`) — all three required
2. Set `captive_portal_mode=0` — Android stops requiring internet validation, marks network VALIDATED
3. Toggle WiFi off/on — forces Android to re-register the network and pick up settings
4. **Restart PdaNet hotspot on the phone** — this is critical. PdaNet's proxy on port 8000 is NOT listening until the hotspot is restarted. Without this step, port 8000 times out and apps get no internet even though Android shows connected.

**Root cause of "app having trouble"**: Android showed green (captive portal bypassed) but port 8000 wasn't accepting connections. Restarting the PdaNet hotspot on the phone started the proxy listener — apps connected immediately after.

---

## How Android Routing Actually Works With PdaNet (Investigated 2026-02-18)

### The Key Discovery

After clearing ALL proxy settings, Android apps continued to work through
PdaNet. Raw socket tools (busybox wget, ping) failed. This was investigated
and here is the full explanation:

### Android Policy-Based Routing

Android uses policy-based routing — NOT a single routing table. From
`ip rule list` on the device:

```
22000: from all fwmark 0x0/0xffff iif lo lookup wlan0
32000: from all unreachable
```

Android app traffic is tagged with a firewall mark and routed via the `wlan0`
routing table, which contains:

```
default via 192.168.49.1 dev wlan0 table wlan0 proto static
192.168.49.0/24 dev wlan0 table wlan0 proto static scope link
```

**Android apps always have a default route via 192.168.49.1** — they send all
traffic to the PdaNet gateway regardless of system proxy settings.

### Why busybox/ping Fails

busybox wget and ping are statically linked binaries. They bypass Android's
UID-based routing and use the main kernel routing table, which only has:

```
192.168.49.0/24 dev wlan0 proto kernel scope link src 192.168.49.185
unreachable default ...
```

No default route in the main table → "Network is unreachable" for anything
outside 192.168.49.0/24. This is NOT a connectivity failure — it's a routing
table mismatch. Android apps are fine.

### What PdaNet Actually Does After Hotspot Restart

After the PdaNet hotspot is restarted, it appears to do **transparent NAT**
for TCP traffic at the IP level — not just HTTP proxy. Evidence:
- ICMP (ping) blocked — "Destination Net Unreachable" from 192.168.49.1
- TCP app traffic works — passes through without proxy config
- Port 8000 (HTTP proxy) is an additional/optional mechanism on top of this

### Implications for the VpnService App

The VpnService app may be simpler than thought:
- Android already routes app traffic to 192.168.49.1 via wlan0 table
- PdaNet transparently NATs TCP through
- VpnService mainly needed for apps that bypass Android routing entirely
  (native code, NDK sockets using SO_MARK or similar)
- The "no internet" indicator is the main problem to solve — captive portal

---

## Notes

- `captive_portal_mode 0` = Android skips internet validation entirely —
  no more "no internet" warning even when the proxy is the only path out
- Both `http_proxy` AND `global_http_proxy_host/port` must be set —
  one alone is not enough on this device
- WiFi toggle forces Android to reconnect and pick up the new settings
- PdaNet hotspot restart on the HOST phone is required to activate port 8000
- This is the simpler alternative to the VpnService approach (no APK needed)
- Limitation: apps that bypass system proxy (raw sockets) still won't work —
  that's what the VpnService APK solves
- These settings persist across reboots but clear if factory reset
