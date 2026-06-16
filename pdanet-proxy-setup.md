# PDANet Proxy Setup for iPhone

## Problem
- PDANet uses SOCKS5 proxy at `192.168.49.1:8000`
- iPhone only supports HTTP/HTTPS proxy in WiFi settings
- iOS cannot use SOCKS5 directly

## Solution Options

### Option 1: Manual HTTP Proxy (if available)
If PDANet also provides HTTP proxy (check phone):
1. iPhone → Settings → WiFi
2. Tap (i) next to "DIRECT-30-moto g power-PdaNet"
3. Scroll to "HTTP Proxy" → Configure Proxy → Manual
4. Server: 192.168.49.1
5. Port: (check PDANet app - might be 8080 or 8888)

### Option 2: Bridge SOCKS5 to HTTP (requires setup)
Create HTTP proxy on this computer that bridges to SOCKS5:
- Install privoxy or similar
- Configure to forward to 192.168.49.1:8000
- Point iPhone to this computer's IP

### Option 3: Use PAC File
Auto-configuration using Proxy Auto-Config file.

What's the PDANet port shown in the Android app?
