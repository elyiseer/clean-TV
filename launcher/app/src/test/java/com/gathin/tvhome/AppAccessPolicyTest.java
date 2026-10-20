package com.gathin.tvhome;

import org.junit.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class AppAccessPolicyTest {
    private ParentalControlsStore.Snapshot state(boolean healthy, boolean configured, String... packages) {
        Set<String> protectedApps = new HashSet<>();
        Collections.addAll(protectedApps, packages);
        return new ParentalControlsStore.Snapshot(healthy, configured, protectedApps);
    }

    @Test
    public void anUnconfiguredFeatureLeavesExistingLaunchesAlone() {
        ParentalControlsStore.Snapshot state = state(true, false);
        assertEquals(AppAccessPolicy.Decision.ALLOW, AppAccessPolicy.forOpen(state, "com.example.video", false,
                AppAccessPolicy.Path.HOME_TILE));
        assertEquals(AppAccessPolicy.Decision.ALLOW, AppAccessPolicy.forManagement(state, "com.example.video", false,
                AppAccessPolicy.Path.APP_INFO));
    }

    @Test
    public void aProtectedAppNeedsAuthenticationToOpenFromAnyLauncherEntryPoint() {
        ParentalControlsStore.Snapshot state = state(true, true, "com.example.video");
        for (AppAccessPolicy.Path path : AppAccessPolicy.Path.values()) {
            assertEquals(path.name(), AppAccessPolicy.Decision.AUTHENTICATION_REQUIRED,
                    AppAccessPolicy.forOpen(state, "com.example.video", false, path));
            assertEquals(path.name(), AppAccessPolicy.Decision.ALLOW,
                    AppAccessPolicy.forOpen(state, "com.example.video", true, path));
        }
    }

    @Test
    public void protectedAppInformationAndShortcutManagementNeedAuthentication() {
        ParentalControlsStore.Snapshot state = state(true, true, "com.example.video");
        assertEquals(AppAccessPolicy.Decision.AUTHENTICATION_REQUIRED,
                AppAccessPolicy.forManagement(state, "com.example.video", false, AppAccessPolicy.Path.APP_INFO));
        assertEquals(AppAccessPolicy.Decision.ALLOW,
                AppAccessPolicy.forManagement(state, "com.example.video", true, AppAccessPolicy.Path.APP_INFO));
        assertEquals(AppAccessPolicy.Decision.AUTHENTICATION_REQUIRED,
                AppAccessPolicy.forManagement(state, "com.example.video", false,
                        AppAccessPolicy.Path.LIBRARY_MANAGEMENT));
        assertEquals(AppAccessPolicy.Decision.ALLOW,
                AppAccessPolicy.forManagement(state, "com.example.other", false,
                        AppAccessPolicy.Path.LIBRARY_MANAGEMENT));
    }

    @Test
    public void storageErrorsBlockOpensAndManagementEvenForUnlistedApps() {
        ParentalControlsStore.Snapshot state = state(false, true);
        assertEquals(AppAccessPolicy.Decision.DENY_STORAGE_ERROR,
                AppAccessPolicy.forOpen(state, "com.example.video", true, AppAccessPolicy.Path.APP_LIBRARY));
        assertEquals(AppAccessPolicy.Decision.DENY_STORAGE_ERROR,
                AppAccessPolicy.forManagement(state, "com.example.video", true, AppAccessPolicy.Path.APP_INFO));
    }

    @Test
    public void inconsistentProtectedStateFailsClosed() {
        ParentalControlsStore.Snapshot state = state(true, false, "com.example.video");
        assertEquals(AppAccessPolicy.Decision.DENY_STORAGE_ERROR,
                AppAccessPolicy.forOpen(state, "com.example.video", false, AppAccessPolicy.Path.HOME_TILE));
        assertEquals(AppAccessPolicy.Decision.DENY_STORAGE_ERROR,
                AppAccessPolicy.forManagement(state, "com.example.video", false, AppAccessPolicy.Path.APP_INFO));
    }

    @Test
    public void missingEntryPointFailsClosed() {
        ParentalControlsStore.Snapshot state = state(true, true);
        assertEquals(AppAccessPolicy.Decision.DENY_STORAGE_ERROR,
                AppAccessPolicy.forOpen(state, "com.example.other", false, null));
    }
}
