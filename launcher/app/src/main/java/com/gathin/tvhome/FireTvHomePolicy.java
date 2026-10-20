package com.gathin.tvhome;

/** Window metadata only. No screen contents, key capture or polling. */
final class FireTvHomePolicy {
    private final String stockPackage;
    private final String[] homeClasses;
    private boolean homeVisible;
    private final long[] recentRequests = new long[3];
    private int requests;

    FireTvHomePolicy(String stockPackage, String[] homeClasses) {
        this.stockPackage = stockPackage;
        this.homeClasses = homeClasses.clone();
    }

    // 1: queue/coalesce Home; -1: cancel; 0: ignore a child-view event.
    int window(String pkg, String cls, boolean enabled) {
        if (!enabled || stockPackage.isEmpty() || !stockPackage.equals(pkg)) {
            homeVisible = false;
            return -1;
        }
        for (String home : homeClasses) {
            if (home.equals(cls)) { homeVisible = true; return 1; }
        }
        if (!cls.isEmpty() && !cls.startsWith("android.")) {
            homeVisible = false;
            return -1;
        }
        return 0;
    }

    boolean request(boolean enabled, long now) {
        if (!enabled || !homeVisible) return false;
        if (requests == recentRequests.length && now - recentRequests[0] <= 5000) return false;
        if (requests == recentRequests.length) {
            System.arraycopy(recentRequests, 1, recentRequests, 0, recentRequests.length - 1);
            requests--;
        }
        recentRequests[requests++] = now;
        return true;
    }
}
