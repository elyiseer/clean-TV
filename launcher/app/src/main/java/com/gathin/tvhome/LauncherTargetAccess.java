package com.gathin.tvhome;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** One authorization decision for a launch intent and all of its possible resolver packages. */
final class LauncherTargetAccess {
    interface PackageVisibilityProbe {
        boolean isVisible(String packageName) throws Exception;
    }

    interface PinRequester {
        void request(String packageName, Runnable onAuthenticated);
    }

    private LauncherTargetAccess() { }

    /**
     * A protected app normally stays visible because the launcher discovers it through its launcher
     * intent queries. If that visibility is lost after an app update, Android can still start it from
     * an implicit intent even though PackageManager no longer lists it. Treat that set as incomplete.
     */
    static boolean mayHaveHiddenProtectedTargets(ParentalControlsStore.Snapshot state,
                                                  boolean visibilityFiltering,
                                                  PackageVisibilityProbe probe) {
        if (!visibilityFiltering || state == null || !state.storageHealthy || state.protectedApps.isEmpty()) {
            return false;
        }
        if (probe == null) return true;
        for (String packageName : state.protectedApps) {
            try {
                if (!probe.isVisible(packageName)) return true;
            } catch (Exception unknownVisibility) {
                return true;
            }
        }
        return false;
    }

    /**
     * Authorizes a single launch action. An ambiguous intent can resolve to only one app at launch,
     * so one PIN covers the action even when several protected packages match. If PackageManager
     * results may be incomplete, the destination label is intentionally generic.
     */
    static void run(ParentalControlsStore.Snapshot state, Collection<String> targetPackages,
                    boolean targetsMayBeIncomplete, AppAccessPolicy.Path path, Runnable launch,
                    PinRequester pinRequester, Runnable onStorageError) {
        if (launch == null) return;
        if (state == null || !state.storageHealthy || path == null) {
            onStorageError.run();
            return;
        }

        Set<String> targets = targetPackages == null
                ? Collections.emptySet() : new LinkedHashSet<>(targetPackages);
        boolean authenticationRequired = targetsMayBeIncomplete && !state.protectedApps.isEmpty();
        if (authenticationRequired && !state.pinConfigured) {
            onStorageError.run();
            return;
        }

        int protectedCount = 0;
        String onlyProtectedPackage = null;
        for (String packageName : targets) {
            if (packageName == null || packageName.isEmpty()) {
                if (!state.protectedApps.isEmpty()) {
                    if (!state.pinConfigured) {
                        onStorageError.run();
                        return;
                    }
                    authenticationRequired = true;
                }
                continue;
            }
            AppAccessPolicy.Decision decision = AppAccessPolicy.forOpen(state, packageName, false, path);
            if (decision == AppAccessPolicy.Decision.DENY_STORAGE_ERROR) {
                onStorageError.run();
                return;
            }
            if (decision == AppAccessPolicy.Decision.AUTHENTICATION_REQUIRED) {
                authenticationRequired = true;
                protectedCount++;
                onlyProtectedPackage = packageName;
            }
        }

        if (!authenticationRequired) {
            launch.run();
            return;
        }
        if (pinRequester == null) {
            onStorageError.run();
            return;
        }

        String promptPackage = !targetsMayBeIncomplete && targets.size() == 1 && protectedCount == 1
                ? onlyProtectedPackage : null;
        pinRequester.request(promptPackage, launch);
    }
}
