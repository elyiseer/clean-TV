package com.gathin.tvhome;

/** Shared policy for opening an app and changing settings that belong to a protected app. */
final class AppAccessPolicy {
    enum Decision { ALLOW, AUTHENTICATION_REQUIRED, DENY_STORAGE_ERROR }
    enum Path { HOME_TILE, MODULE_CARD, APP_LIBRARY, APP_LIBRARY_MENU, APP_INFO, LIBRARY_MANAGEMENT, LAUNCHER_INTENT }

    private AppAccessPolicy() { }

    static Decision forOpen(ParentalControlsStore.Snapshot state, String packageName, boolean authenticated, Path path) {
        return decide(state, packageName, authenticated, path);
    }

    static Decision forManagement(ParentalControlsStore.Snapshot state, String packageName, boolean authenticated, Path path) {
        return decide(state, packageName, authenticated, path);
    }

    private static Decision decide(ParentalControlsStore.Snapshot state, String packageName, boolean authenticated, Path path) {
        if (state == null || !state.storageHealthy || path == null) return Decision.DENY_STORAGE_ERROR;
        boolean protectedApp = packageName != null && state.protectedApps.contains(packageName);
        if (protectedApp && !state.pinConfigured) return Decision.DENY_STORAGE_ERROR;
        return protectedApp && !authenticated ? Decision.AUTHENTICATION_REQUIRED : Decision.ALLOW;
    }
}
