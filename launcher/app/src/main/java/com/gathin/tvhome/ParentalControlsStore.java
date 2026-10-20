package com.gathin.tvhome;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Private, offline storage for the optional parental PIN and protected app package names. */
final class ParentalControlsStore {
    static final int PIN_MIN = 4;
    static final int PIN_MAX = 6;
    static final int PIN_ITERATIONS = 100_000;
    static final long FIRST_LOCK_MS = 30_000L;
    static final long MAX_LOCK_MS = 30L * 60L * 1000L;
    private static final int MAX_FAILURES = 33;

    enum PinResult { MATCH, FAILED, LOCKED, NOT_CONFIGURED, INVALID, ERROR }

    static final class Snapshot {
        final boolean storageHealthy;
        final boolean pinConfigured;
        final Set<String> protectedApps;

        Snapshot(boolean storageHealthy, boolean pinConfigured, Set<String> protectedApps) {
            this.storageHealthy = storageHealthy;
            this.pinConfigured = pinConfigured;
            this.protectedApps = Collections.unmodifiableSet(new LinkedHashSet<>(protectedApps));
        }
    }

    private static final String PIN_SALT = "pin_salt";
    private static final String PIN_HASH = "pin_hash";
    private static final String PROTECTED_APPS = "protected_apps";
    private static final String FAILED_ATTEMPTS = "failed_attempts";
    private static final String LOCKED_UNTIL = "locked_until";

    private final File file;
    private final SecureRandom random = new SecureRandom();
    private boolean storageError;

    ParentalControlsStore(File file) {
        this.file = file;
    }

    synchronized Snapshot snapshot() {
        Properties p = read();
        if (storageError) return new Snapshot(false, true, Collections.emptySet());
        return new Snapshot(true, hasPin(p), parseApps(p.getProperty(PROTECTED_APPS, "")));
    }

    synchronized boolean saveProtectedApps(Set<String> packages) {
        Properties p = read();
        if (storageError || !hasPin(p)) return false;
        Set<String> clean = cleanPackages(packages);
        if (clean == null) return false;
        p.setProperty(PROTECTED_APPS, join(clean));
        return write(p);
    }

    synchronized boolean setPin(char[] pin) {
        if (!validPin(pin)) return false;
        Properties p = read();
        if (storageError) return false;
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        byte[] hash = null;
        try {
            hash = derive(pin, salt);
            p.setProperty(PIN_SALT, java.util.Base64.getEncoder().encodeToString(salt));
            p.setProperty(PIN_HASH, java.util.Base64.getEncoder().encodeToString(hash));
            p.setProperty(FAILED_ATTEMPTS, "0");
            p.setProperty(LOCKED_UNTIL, "0");
            return write(p);
        } catch (Exception e) {
            storageError = true;
            return false;
        } finally {
            Arrays.fill(salt, (byte) 0);
            if (hash != null) Arrays.fill(hash, (byte) 0);
        }
    }

    synchronized PinResult verifyPin(char[] pin, long nowMillis) {
        if (!validPin(pin)) return PinResult.INVALID;
        Properties p = read();
        if (storageError) return PinResult.ERROR;
        if (!hasPin(p)) return PinResult.NOT_CONFIGURED;
        int failures = parseInt(p.getProperty(FAILED_ATTEMPTS, "0"));
        long lockedUntil = parseLong(p.getProperty(LOCKED_UNTIL, "0"));
        if (failures < 0 || failures > MAX_FAILURES || lockedUntil < 0) {
            storageError = true;
            return PinResult.ERROR;
        }
        if (nowMillis < lockedUntil) return PinResult.LOCKED;

        byte[] salt = null;
        byte[] expected = null;
        byte[] actual = null;
        try {
            salt = java.util.Base64.getDecoder().decode(p.getProperty(PIN_SALT));
            expected = java.util.Base64.getDecoder().decode(p.getProperty(PIN_HASH));
            if (salt.length != 16 || expected.length != 32) {
                storageError = true;
                return PinResult.ERROR;
            }
            actual = derive(pin, salt);
            boolean match = MessageDigest.isEqual(expected, actual);
            if (match) {
                p.setProperty(FAILED_ATTEMPTS, "0");
                p.setProperty(LOCKED_UNTIL, "0");
                return write(p) ? PinResult.MATCH : PinResult.ERROR;
            }
            failures = Math.min(MAX_FAILURES, failures + 1);
            p.setProperty(FAILED_ATTEMPTS, Integer.toString(failures));
            if (failures >= 3) {
                int shift = Math.min(10, failures - 3);
                long delay = Math.min(MAX_LOCK_MS, FIRST_LOCK_MS << shift);
                long until = nowMillis > Long.MAX_VALUE - delay ? Long.MAX_VALUE : nowMillis + delay;
                p.setProperty(LOCKED_UNTIL, Long.toString(until));
                return write(p) ? PinResult.LOCKED : PinResult.ERROR;
            }
            return write(p) ? PinResult.FAILED : PinResult.ERROR;
        } catch (Exception e) {
            storageError = true;
            return PinResult.ERROR;
        } finally {
            if (salt != null) Arrays.fill(salt, (byte) 0);
            if (expected != null) Arrays.fill(expected, (byte) 0);
            if (actual != null) Arrays.fill(actual, (byte) 0);
        }
    }

    synchronized long lockRemainingMs(long nowMillis) {
        Properties p = read();
        if (storageError) return Long.MAX_VALUE;
        return Math.max(0L, parseLong(p.getProperty(LOCKED_UNTIL, "0")) - nowMillis);
    }

    /** Called only after the current PIN has been verified and the user confirms disabling controls. */
    synchronized boolean clear() {
        return write(new Properties());
    }

    static boolean validPin(char[] pin) {
        if (pin == null || pin.length < PIN_MIN || pin.length > PIN_MAX) return false;
        for (char c : pin) if (c < '0' || c > '9') return false;
        return true;
    }

    static boolean samePin(char[] first, char[] second) {
        if (first == null || second == null || first.length != second.length) return false;
        byte[] a = new byte[first.length];
        byte[] b = new byte[second.length];
        for (int i = 0; i < first.length; i++) a[i] = (byte) first[i];
        for (int i = 0; i < second.length; i++) b[i] = (byte) second[i];
        try {
            return MessageDigest.isEqual(a, b);
        } finally {
            Arrays.fill(a, (byte) 0);
            Arrays.fill(b, (byte) 0);
        }
    }

    private Properties read() {
        Properties p = new Properties();
        storageError = false;
        if (file == null) {
            storageError = true;
            return p;
        }
        if (!file.exists()) return p;
        if (!file.isFile()) {
            storageError = true;
            return p;
        }
        try (FileInputStream in = new FileInputStream(file)) {
            p.load(in);
            String salt = p.getProperty(PIN_SALT);
            String hash = p.getProperty(PIN_HASH);
            if ((salt == null) != (hash == null)) storageError = true;
            if (salt != null) {
                byte[] decodedSalt = null;
                byte[] decodedHash = null;
                try {
                    decodedSalt = java.util.Base64.getDecoder().decode(salt);
                    decodedHash = java.util.Base64.getDecoder().decode(hash);
                    if (decodedSalt.length != 16 || decodedHash.length != 32) storageError = true;
                } finally {
                    if (decodedSalt != null) Arrays.fill(decodedSalt, (byte) 0);
                    if (decodedHash != null) Arrays.fill(decodedHash, (byte) 0);
                }
            }
            Set<String> apps = parseApps(p.getProperty(PROTECTED_APPS, ""));
            if (apps == null || (salt == null && !apps.isEmpty())) storageError = true;
            int failures = parseInt(p.getProperty(FAILED_ATTEMPTS, "0"));
            long lockedUntil = parseLong(p.getProperty(LOCKED_UNTIL, "0"));
            if (failures < 0 || failures > MAX_FAILURES || lockedUntil < 0
                    || (salt == null && (failures != 0 || lockedUntil != 0))) storageError = true;
        } catch (Exception e) {
            storageError = true;
        }
        return p;
    }

    private boolean write(Properties p) {
        if (file == null) {
            storageError = true;
            return false;
        }
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            storageError = true;
            return false;
        }
        File temp = new File(file.getPath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(temp)) {
            p.store(out, "CleanYourTV parental settings");
            out.getFD().sync();
        } catch (IOException e) {
            temp.delete();
            storageError = true;
            return false;
        }
        try {
            try {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException unsupportedAtomicMove) {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            storageError = false;
            return true;
        } catch (IOException e) {
            temp.delete();
            storageError = true;
            return false;
        }
    }

    private static byte[] derive(char[] pin, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(pin, salt, PIN_ITERATIONS, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }

    private static boolean hasPin(Properties p) {
        return p.getProperty(PIN_SALT) != null && p.getProperty(PIN_HASH) != null;
    }

    private static Set<String> parseApps(String value) {
        LinkedHashSet<String> apps = new LinkedHashSet<>();
        if (value == null || value.isEmpty()) return apps;
        for (String item : value.split("\\n", -1)) {
            if (item.isEmpty() || !validPackage(item) || !apps.add(item)) return null;
        }
        return apps;
    }

    private static Set<String> cleanPackages(Set<String> packages) {
        TreeSet<String> clean = new TreeSet<>();
        if (packages != null) for (String pkg : packages) {
            if (!validPackage(pkg)) return null;
            clean.add(pkg);
        }
        return clean;
    }

    private static String join(Set<String> packages) {
        return String.join("\n", new ArrayList<>(packages));
    }

    private static boolean validPackage(String pkg) {
        if (pkg == null || pkg.length() < 3 || pkg.length() > 255) return false;
        boolean segmentStart = true;
        boolean hasDot = false;
        for (int i = 0; i < pkg.length(); i++) {
            char c = pkg.charAt(i);
            if (c == '.') {
                if (segmentStart) return false;
                hasDot = true;
                segmentStart = true;
            } else {
                boolean letter = c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c == '_';
                if (segmentStart && !letter || !segmentStart && !letter && (c < '0' || c > '9')) return false;
                segmentStart = false;
            }
        }
        return hasDot && !segmentStart;
    }

    private static int parseInt(String text) {
        try { return Integer.parseInt(text); } catch (Exception e) { return -1; }
    }

    private static long parseLong(String text) {
        try { return Long.parseLong(text); } catch (Exception e) { return -1L; }
    }
}
