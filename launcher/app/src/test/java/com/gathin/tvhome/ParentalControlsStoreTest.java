package com.gathin.tvhome;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ParentalControlsStoreTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    private File storeFile() throws Exception {
        return new File(temporary.newFolder("private"), "parental-controls.properties");
    }

    @Test
    public void featureIsOffUntilTheOwnerCreatesAPin() throws Exception {
        ParentalControlsStore store = new ParentalControlsStore(storeFile());
        ParentalControlsStore.Snapshot state = store.snapshot();
        assertTrue(state.storageHealthy);
        assertFalse(state.pinConfigured);
        assertTrue(state.protectedApps.isEmpty());
        assertFalse(store.saveProtectedApps(Collections.singleton("com.example.video")));
        assertEquals(AppAccessPolicy.Decision.ALLOW,
                AppAccessPolicy.forOpen(state, "com.example.video", false, AppAccessPolicy.Path.HOME_TILE));
    }

    @Test
    public void pinFormatIsNumericAndRestrictedToFourThroughSixDigits() {
        assertFalse(ParentalControlsStore.validPin(null));
        assertFalse(ParentalControlsStore.validPin("123".toCharArray()));
        assertFalse(ParentalControlsStore.validPin("1234567".toCharArray()));
        assertFalse(ParentalControlsStore.validPin("12a4".toCharArray()));
        assertTrue(ParentalControlsStore.validPin("4826".toCharArray()));
        assertTrue(ParentalControlsStore.validPin("482615".toCharArray()));
        assertTrue(ParentalControlsStore.samePin("482615".toCharArray(), "482615".toCharArray()));
        assertFalse(ParentalControlsStore.samePin("482615".toCharArray(), "482614".toCharArray()));
    }

    @Test
    public void saltedPinAndProtectedPackagesPersistWithoutPlaintext() throws Exception {
        File file = storeFile();
        ParentalControlsStore first = new ParentalControlsStore(file);
        assertTrue(first.setPin("4826".toCharArray()));
        Set<String> apps = new HashSet<>();
        apps.add("com.example.video");
        apps.add("org.example.games");
        assertTrue(first.saveProtectedApps(apps));

        String encoded = new String(Files.readAllBytes(file.toPath()), StandardCharsets.ISO_8859_1);
        assertFalse(encoded.contains("4826"));
        assertTrue(encoded.contains("pin_salt"));
        assertTrue(encoded.contains("pin_hash"));

        ParentalControlsStore reopened = new ParentalControlsStore(file);
        ParentalControlsStore.Snapshot state = reopened.snapshot();
        assertTrue(state.storageHealthy);
        assertTrue(state.pinConfigured);
        assertEquals(apps, state.protectedApps);
        assertEquals(ParentalControlsStore.PinResult.MATCH,
                reopened.verifyPin("4826".toCharArray(), 1_000L));
        assertEquals(ParentalControlsStore.PinResult.FAILED,
                reopened.verifyPin("5639".toCharArray(), 2_000L));
    }

    @Test
    public void failedAttemptsLockOutProgressivelyAndSurviveRestart() throws Exception {
        File file = storeFile();
        ParentalControlsStore first = new ParentalControlsStore(file);
        assertTrue(first.setPin("704918".toCharArray()));
        assertEquals(ParentalControlsStore.PinResult.FAILED,
                first.verifyPin("9185".toCharArray(), 1_000L));
        assertEquals(ParentalControlsStore.PinResult.FAILED,
                first.verifyPin("9185".toCharArray(), 2_000L));
        assertEquals(ParentalControlsStore.PinResult.LOCKED,
                first.verifyPin("9185".toCharArray(), 3_000L));

        ParentalControlsStore reopened = new ParentalControlsStore(file);
        assertEquals(1_000L, reopened.lockRemainingMs(32_000L));
        assertEquals(ParentalControlsStore.PinResult.LOCKED,
                reopened.verifyPin("704918".toCharArray(), 32_999L));
        assertEquals(ParentalControlsStore.PinResult.LOCKED,
                reopened.verifyPin("9185".toCharArray(), 33_000L));
        assertEquals(60_000L, reopened.lockRemainingMs(33_000L));
        assertEquals(ParentalControlsStore.PinResult.LOCKED,
                new ParentalControlsStore(file).verifyPin("704918".toCharArray(), 92_999L));
        assertEquals(ParentalControlsStore.PinResult.MATCH,
                new ParentalControlsStore(file).verifyPin("704918".toCharArray(), 93_000L));
    }

    @Test
    public void changingPinReplacesTheVerifierAndResetsLockout() throws Exception {
        File file = storeFile();
        ParentalControlsStore store = new ParentalControlsStore(file);
        assertTrue(store.setPin("4826".toCharArray()));
        assertEquals(ParentalControlsStore.PinResult.FAILED,
                store.verifyPin("5639".toCharArray(), 1_000L));
        assertTrue(store.setPin("915204".toCharArray()));
        assertEquals(ParentalControlsStore.PinResult.MATCH,
                new ParentalControlsStore(file).verifyPin("915204".toCharArray(), 2_000L));
        assertEquals(ParentalControlsStore.PinResult.FAILED,
                new ParentalControlsStore(file).verifyPin("4826".toCharArray(), 3_000L));
    }

    @Test
    public void corruptOrUnwritableStateFailsClosed() throws Exception {
        File file = storeFile();
        Files.write(file.toPath(), "pin_salt=bad\npin_hash=bad\nprotected_apps=com.example.video\n"
                .getBytes(StandardCharsets.ISO_8859_1));
        ParentalControlsStore broken = new ParentalControlsStore(file);
        ParentalControlsStore.Snapshot state = broken.snapshot();
        assertFalse(state.storageHealthy);
        assertEquals(ParentalControlsStore.PinResult.ERROR,
                broken.verifyPin("4826".toCharArray(), 1_000L));
        assertEquals(AppAccessPolicy.Decision.DENY_STORAGE_ERROR,
                AppAccessPolicy.forOpen(state, "com.example.other", false, AppAccessPolicy.Path.HOME_TILE));

        ParentalControlsStore missingPath = new ParentalControlsStore(null);
        assertFalse(missingPath.snapshot().storageHealthy);
        assertEquals(AppAccessPolicy.Decision.DENY_STORAGE_ERROR,
                AppAccessPolicy.forManagement(missingPath.snapshot(), "com.example.other", false,
                        AppAccessPolicy.Path.APP_INFO));
    }

    @Test
    public void disablingControlsClearsPinAndProtection() throws Exception {
        File file = storeFile();
        ParentalControlsStore store = new ParentalControlsStore(file);
        assertTrue(store.setPin("4826".toCharArray()));
        assertTrue(store.saveProtectedApps(Collections.singleton("com.example.video")));
        assertTrue(store.clear());

        ParentalControlsStore reopened = new ParentalControlsStore(file);
        ParentalControlsStore.Snapshot state = reopened.snapshot();
        assertTrue(state.storageHealthy);
        assertFalse(state.pinConfigured);
        assertTrue(state.protectedApps.isEmpty());
        assertEquals(ParentalControlsStore.PinResult.NOT_CONFIGURED,
                reopened.verifyPin("4826".toCharArray(), 1_000L));
    }
}
