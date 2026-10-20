package com.gathin.tvhome;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class LauncherTargetAccessTest {
    private ParentalControlsStore.Snapshot state(boolean healthy, boolean configured, String... packages) {
        Set<String> protectedApps = new HashSet<>(Arrays.asList(packages));
        return new ParentalControlsStore.Snapshot(healthy, configured, protectedApps);
    }

    private void run(ParentalControlsStore.Snapshot state, Iterable<String> targets, boolean incomplete,
                     AtomicInteger pinRequests, AtomicReference<String> promptPackage,
                     AtomicInteger launches, AtomicInteger errors) {
        java.util.List<String> packages = new java.util.ArrayList<>();
        for (String target : targets) packages.add(target);
        LauncherTargetAccess.run(state, packages, incomplete, AppAccessPolicy.Path.LAUNCHER_INTENT,
                launches::incrementAndGet, (packageName, onAuthenticated) -> {
                    pinRequests.incrementAndGet();
                    promptPackage.set(packageName);
                    onAuthenticated.run();
                }, errors::incrementAndGet);
    }

    @Test
    public void unknownImplicitDestinationRequiresOnePinWhenProtectedPackageMayBeHidden() {
        AtomicInteger pins = new AtomicInteger();
        AtomicReference<String> target = new AtomicReference<>("unexpected");
        AtomicInteger launches = new AtomicInteger();
        AtomicInteger errors = new AtomicInteger();

        run(state(true, true, "com.example.protected"), Collections.emptyList(), true,
                pins, target, launches, errors);

        assertEquals(1, pins.get());
        assertNull(target.get());
        assertEquals(1, launches.get());
        assertEquals(0, errors.get());
    }

    @Test
    public void partialResolverResultsStillGateAnUnqueryableProtectedDestination() {
        AtomicInteger pins = new AtomicInteger();
        AtomicReference<String> target = new AtomicReference<>("unexpected");
        AtomicInteger launches = new AtomicInteger();
        AtomicInteger errors = new AtomicInteger();

        run(state(true, true, "com.example.protected"),
                Collections.singletonList("com.example.open"), true,
                pins, target, launches, errors);

        assertEquals(1, pins.get());
        assertNull(target.get());
        assertEquals(1, launches.get());
        assertEquals(0, errors.get());
    }

    @Test
    public void unresolvedSystemIntentWithOnlyQueryableProtectedAppsNeedsNoPin() {
        AtomicInteger pins = new AtomicInteger();
        AtomicReference<String> target = new AtomicReference<>();
        AtomicInteger launches = new AtomicInteger();
        AtomicInteger errors = new AtomicInteger();

        run(state(true, true, "com.example.protected"), Collections.emptyList(), false,
                pins, target, launches, errors);

        assertEquals(0, pins.get());
        assertEquals(1, launches.get());
        assertEquals(0, errors.get());
    }

    @Test
    public void oneSuccessfulAuthenticationCoversAnAmbiguousSetOfProtectedTargets() {
        AtomicInteger pins = new AtomicInteger();
        AtomicReference<String> target = new AtomicReference<>("unexpected");
        AtomicInteger launches = new AtomicInteger();
        AtomicInteger errors = new AtomicInteger();

        run(state(true, true, "com.example.one", "com.example.two"),
                Arrays.asList("com.example.one", "com.example.two", "com.example.open"),
                false, pins, target, launches, errors);

        assertEquals(1, pins.get());
        assertNull(target.get());
        assertEquals(1, launches.get());
        assertEquals(0, errors.get());
    }

    @Test
    public void mixedAmbiguousDestinationsAuthenticateOnceBeforeStartingChooserIntent() {
        AtomicInteger pins = new AtomicInteger();
        AtomicReference<String> target = new AtomicReference<>("unexpected");
        AtomicInteger launches = new AtomicInteger();
        AtomicInteger errors = new AtomicInteger();

        // Hold the success callback as the real PIN dialog does; launch must wait for success.
        AtomicReference<Runnable> grant = new AtomicReference<>();
        LauncherTargetAccess.run(state(true, true, "com.example.protected"),
                Arrays.asList("com.example.open", "com.example.protected"), false,
                AppAccessPolicy.Path.LAUNCHER_INTENT, launches::incrementAndGet,
                (packageName, onAuthenticated) -> {
                    pins.incrementAndGet();
                    target.set(packageName);
                    grant.set(onAuthenticated);
                }, errors::incrementAndGet);

        assertEquals(1, pins.get());
        assertNull(target.get());
        assertEquals(0, launches.get());
        assertEquals(0, errors.get());
        grant.get().run();
        assertEquals(1, launches.get());
    }

    @Test
    public void aSingleKnownProtectedDestinationKeepsItsSpecificPromptLabel() {
        AtomicInteger pins = new AtomicInteger();
        AtomicReference<String> target = new AtomicReference<>();
        AtomicInteger launches = new AtomicInteger();
        AtomicInteger errors = new AtomicInteger();

        run(state(true, true, "com.example.protected"), Collections.singletonList("com.example.protected"),
                false, pins, target, launches, errors);

        assertEquals(1, pins.get());
        assertEquals("com.example.protected", target.get());
        assertEquals(1, launches.get());
    }

    @Test
    public void visibleUnprotectedSettingsTargetStillOpensWithoutPin() {
        AtomicInteger pins = new AtomicInteger();
        AtomicReference<String> target = new AtomicReference<>();
        AtomicInteger launches = new AtomicInteger();
        AtomicInteger errors = new AtomicInteger();

        run(state(true, true, "com.example.protected"), Collections.singletonList("com.android.settings"),
                false, pins, target, launches, errors);

        assertEquals(0, pins.get());
        assertEquals(1, launches.get());
        assertEquals(0, errors.get());
    }

    @Test
    public void unknownDestinationWithoutAnyConfiguredProtectionRemainsUnchallenged() {
        AtomicInteger pins = new AtomicInteger();
        AtomicReference<String> target = new AtomicReference<>();
        AtomicInteger launches = new AtomicInteger();
        AtomicInteger errors = new AtomicInteger();

        run(state(true, false), Collections.emptyList(), true, pins, target, launches, errors);

        assertEquals(0, pins.get());
        assertEquals(1, launches.get());
        assertEquals(0, errors.get());
    }

    @Test
    public void visibleProtectedPackagesThatDoNotMatchTheIntentDoNotMarkQueriesIncomplete() {
        ParentalControlsStore.Snapshot state = state(true, true, "com.example.protected");
        assertFalse(LauncherTargetAccess.mayHaveHiddenProtectedTargets(state, true, packageName -> true));
        assertFalse(LauncherTargetAccess.mayHaveHiddenProtectedTargets(state, false, packageName -> {
            throw new AssertionError("No package probe is needed before Android 11");
        }));
    }

    @Test
    public void anUnqueryableProtectedPackageMarksImplicitTargetsAsIncomplete() {
        ParentalControlsStore.Snapshot state = state(true, true, "com.example.stale");
        assertTrue(LauncherTargetAccess.mayHaveHiddenProtectedTargets(state, true, packageName -> false));
        assertTrue(LauncherTargetAccess.mayHaveHiddenProtectedTargets(state, true, packageName -> {
            throw new IllegalStateException("Package visibility check failed");
        }));
    }

    @Test
    public void storageErrorsNeverLaunchAnUnknownTarget() {
        AtomicInteger pins = new AtomicInteger();
        AtomicReference<String> target = new AtomicReference<>();
        AtomicInteger launches = new AtomicInteger();
        AtomicInteger errors = new AtomicInteger();

        run(state(false, true), Collections.emptyList(), true, pins, target, launches, errors);

        assertEquals(0, pins.get());
        assertEquals(0, launches.get());
        assertEquals(1, errors.get());
    }
}
