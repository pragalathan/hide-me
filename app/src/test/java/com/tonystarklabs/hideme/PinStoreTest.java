package com.tonystarklabs.hideme;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PinStoreTest {

    @Test
    public void matchingConfirmSavesThePin() {
        PinStore.State state = new PinStore.State();

        assertEquals(PinStore.Result.ACCEPTED, PinStore.confirmPin(state, "1234", "1234"));
        assertTrue(state.hasPin());
        assertEquals(PinStore.Result.ACCEPTED, PinStore.verifyPin(state, "1234", 1_000L));
    }

    @Test
    public void mismatchedConfirmDoesNotSave() {
        PinStore.State state = new PinStore.State();

        assertEquals(PinStore.Result.MISMATCH, PinStore.confirmPin(state, "1234", "9999"));
        assertFalse(state.hasPin());
        assertEquals(PinStore.Result.MISMATCH, PinStore.verifyPin(state, "1234", 1_000L));
    }

    @Test
    public void fifthWrongTryWaitsThirtySeconds() {
        PinStore.State state = new PinStore.State();
        PinStore.confirmPin(state, "1234", "1234");

        long now = 5_000L;
        for (int attempt = 1; attempt < PinStore.MAX_FAILURES; attempt++) {
            assertEquals(PinStore.Result.WRONG, PinStore.verifyPin(state, "0000", now));
        }
        assertEquals(PinStore.Result.LOCKED, PinStore.verifyPin(state, "0000", now));
        assertEquals(now + PinStore.LOCKOUT_MS, state.lockedUntilMillis);
        assertEquals(PinStore.Result.LOCKED, PinStore.verifyPin(state, "1234", now + 1_000L));
        assertEquals(
                PinStore.Result.ACCEPTED,
                PinStore.verifyPin(state, "1234", now + PinStore.LOCKOUT_MS));
    }
}
