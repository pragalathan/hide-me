package com.tonystarklabs.hideme;

import android.content.Context;
import android.content.SharedPreferences;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

public final class PinStore {

    public static final int PIN_LENGTH = 4;
    public static final int MAX_FAILURES = 5;
    public static final long LOCKOUT_MS = 30_000L;

    private static final String PREFS = "pin_lock";
    private static final String KEY_SALT = "salt";
    private static final String KEY_HASH = "hash";
    private static final String KEY_FAILURES = "failures";
    private static final String KEY_LOCKED_UNTIL = "locked_until";

    private final SharedPreferences prefs;
    private final State state = new State();

    public PinStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        state.saltHex = prefs.getString(KEY_SALT, "");
        state.hashHex = prefs.getString(KEY_HASH, "");
        state.failures = prefs.getInt(KEY_FAILURES, 0);
        state.lockedUntilMillis = prefs.getLong(KEY_LOCKED_UNTIL, 0L);
    }

    public boolean hasPin() {
        return state.hasPin();
    }

    public long lockRemainingMillis(long nowMillis) {
        return Math.max(0L, state.lockedUntilMillis - nowMillis);
    }

    public Result confirm(String first, String second) {
        Result result = confirmPin(state, first, second);
        if (result == Result.ACCEPTED) {
            persist();
        }
        return result;
    }

    public Result verify(String pin, long nowMillis) {
        Result result = verifyPin(state, pin, nowMillis);
        persist();
        return result;
    }

    public static Result confirmPin(State state, String first, String second) {
        if (!isFourDigits(first) || !first.equals(second)) {
            return Result.MISMATCH;
        }
        byte[] salt = newSalt();
        state.saltHex = toHex(salt);
        state.hashHex = toHex(hash(first, salt));
        state.failures = 0;
        state.lockedUntilMillis = 0L;
        return Result.ACCEPTED;
    }

    public static Result verifyPin(State state, String pin, long nowMillis) {
        if (!state.hasPin()) {
            return Result.MISMATCH;
        }
        if (state.lockedUntilMillis > nowMillis) {
            return Result.LOCKED;
        }
        if (state.lockedUntilMillis != 0L) {
            state.lockedUntilMillis = 0L;
            state.failures = 0;
        }
        if (matches(state, pin)) {
            state.failures = 0;
            state.lockedUntilMillis = 0L;
            return Result.ACCEPTED;
        }
        state.failures++;
        if (state.failures >= MAX_FAILURES) {
            state.failures = 0;
            state.lockedUntilMillis = nowMillis + LOCKOUT_MS;
            return Result.LOCKED;
        }
        return Result.WRONG;
    }

    private static boolean matches(State state, String pin) {
        if (!isFourDigits(pin) || state.saltHex.isEmpty() || state.hashHex.isEmpty()) {
            return false;
        }
        byte[] actual = hash(pin, fromHex(state.saltHex));
        byte[] expected = fromHex(state.hashHex);
        return MessageDigest.isEqual(actual, expected);
    }

    private static boolean isFourDigits(String pin) {
        if (pin == null || pin.length() != PIN_LENGTH) {
            return false;
        }
        for (int index = 0; index < pin.length(); index++) {
            char character = pin.charAt(index);
            if (character < '0' || character > '9') {
                return false;
            }
        }
        return true;
    }

    private static byte[] newSalt() {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return salt;
    }

    private static byte[] hash(String pin, byte[] salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt);
            digest.update(pin.getBytes(StandardCharsets.UTF_8));
            return digest.digest();
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    private static String toHex(byte[] bytes) {
        char[] hex = new char[bytes.length * 2];
        char[] alphabet = "0123456789abcdef".toCharArray();
        for (int index = 0; index < bytes.length; index++) {
            int value = bytes[index] & 0xff;
            hex[index * 2] = alphabet[value >>> 4];
            hex[index * 2 + 1] = alphabet[value & 0x0f];
        }
        return new String(hex);
    }

    private static byte[] fromHex(String hex) {
        byte[] bytes = new byte[hex.length() / 2];
        for (int index = 0; index < bytes.length; index++) {
            int high = Character.digit(hex.charAt(index * 2), 16);
            int low = Character.digit(hex.charAt(index * 2 + 1), 16);
            bytes[index] = (byte) ((high << 4) + low);
        }
        return bytes;
    }

    private void persist() {
        prefs.edit()
                .putString(KEY_SALT, state.saltHex)
                .putString(KEY_HASH, state.hashHex)
                .putInt(KEY_FAILURES, state.failures)
                .putLong(KEY_LOCKED_UNTIL, state.lockedUntilMillis)
                .apply();
    }

    public enum Result {
        ACCEPTED,
        WRONG,
        MISMATCH,
        LOCKED
    }

    public static final class State {
        String saltHex = "";
        String hashHex = "";
        int failures;
        long lockedUntilMillis;

        boolean hasPin() {
            return hashHex != null && !hashHex.isEmpty();
        }
    }
}
