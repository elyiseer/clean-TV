package com.gathin.tvhome;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Handler;
import android.provider.Settings;

import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * What the network menu (hold OK on the network pill) reads: the TV's IPv4 address, whether classic
 * ADB answers on port 5555, and the Wireless debugging port, found the way `adb mdns services` finds it.
 * Read-only: nothing here changes a setting. Results come back on the given main-thread handler.
 */
final class NetworkMenu {

    static final int ADB_PORT = 5555;
    /** How long the Wireless debugging search runs before the menu says it found nothing. */
    static final long SEARCH_MS = 6000;

    interface Result<T> {
        void done(T value);
    }

    private NetworkMenu() {
    }

    /** The active network's IPv4 address, or null when offline. */
    static String ipv4Address(Context ctx) {
        ConnectivityManager cm = ctx.getSystemService(ConnectivityManager.class);
        Network n = cm != null ? cm.getActiveNetwork() : null;
        LinkProperties lp = n != null ? cm.getLinkProperties(n) : null;
        if (lp == null) return null;
        for (LinkAddress a : lp.getLinkAddresses()) {
            if (a.getAddress() instanceof Inet4Address) return a.getAddress().getHostAddress();
        }
        return null;
    }

    /** True when Wireless debugging is switched on in Developer options (read only). */
    static boolean wirelessDebuggingOn(Context ctx) {
        return Settings.Global.getInt(ctx.getContentResolver(), "adb_wifi_enabled", 0) == 1;
    }

    /** Classic ADB (adb tcpip 5555) is open when something accepts a connection there; checked on its own thread. */
    static void probeAdb(Handler main, Result<Boolean> result) {
        new Thread(() -> {
            boolean open;
            try (Socket sock = new Socket()) {
                sock.connect(new InetSocketAddress("127.0.0.1", ADB_PORT), 400);
                open = true;
            } catch (Exception e) {
                open = false;
            }
            final boolean answer = open;
            main.post(() -> result.done(answer));
        }, "network-menu-adb").start();
    }

    /**
     * Browses _adb-tls-connect._tcp and reports the port of the service on this TV's own address.
     * Returns the listener to pass to {@link #stop}, or null when discovery could not start.
     */
    @SuppressWarnings("deprecation")   // resolveService: the replacement needs API 34, minSdk is 26
    static NsdManager.DiscoveryListener findWirelessPort(NsdManager nsd, String ip, Handler main, Result<Integer> found) {
        if (nsd == null || ip == null) return null;
        NsdManager.DiscoveryListener l = new NsdManager.DiscoveryListener() {
            @Override
            public void onServiceFound(NsdServiceInfo info) {
                nsd.resolveService(info, new NsdManager.ResolveListener() {
                    @Override
                    public void onServiceResolved(NsdServiceInfo r) {
                        if (r.getHost() != null && ip.equals(r.getHost().getHostAddress())) {
                            main.post(() -> found.done(r.getPort()));
                        }
                    }

                    @Override
                    public void onResolveFailed(NsdServiceInfo r, int error) {
                    }
                });
            }

            @Override
            public void onServiceLost(NsdServiceInfo info) {
            }

            @Override
            public void onDiscoveryStarted(String type) {
            }

            @Override
            public void onDiscoveryStopped(String type) {
            }

            @Override
            public void onStartDiscoveryFailed(String type, int error) {
            }

            @Override
            public void onStopDiscoveryFailed(String type, int error) {
            }
        };
        try {
            nsd.discoverServices("_adb-tls-connect._tcp", NsdManager.PROTOCOL_DNS_SD, l);
        } catch (Exception e) {
            return null;
        }
        return l;
    }

    static void stop(NsdManager nsd, NsdManager.DiscoveryListener l) {
        if (nsd == null || l == null) return;
        try {
            nsd.stopServiceDiscovery(l);
        } catch (Exception ignored) {
            // Already stopped.
        }
    }
}
