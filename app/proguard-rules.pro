-keep class tun.proxy.service.Tun2HttpVpnService { *; }
-keep class tun.utils.Util { *; }
-keepclassmembers class tun.proxy.service.Tun2HttpVpnService {
    private void nativeExit(java.lang.String);
    private void nativeError(int, java.lang.String);
}
