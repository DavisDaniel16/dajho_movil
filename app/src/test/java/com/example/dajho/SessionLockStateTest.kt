package com.example.dajho

import org.junit.Assert.*
import org.junit.Test

class SessionLockStateTest {
    private var now = 0L
    private fun state() = SessionLockState(timeoutMillis = 300_000L, now = { now })

    @Test fun restoredSessionStartsLocked() {
        val state = state()
        state.bindSession("user")
        assertTrue(state.isLocked)
    }

    @Test fun freshLoginUnlocksOnlyTheCurrentAccount() {
        val state = state()
        state.bindSession("user")
        assertFalse(state.authenticate("other"))
        assertTrue(state.isLocked)
        assertTrue(state.authenticate("user"))
        assertFalse(state.isLocked)
    }

    @Test fun locksAtExactlyFiveMinutesEvenIfNextEventIsATouch() {
        val state = state()
        state.bindSession("user")
        state.authenticate("user")
        now = 300_000L
        assertFalse(state.recordInteraction())
        assertTrue(state.isLocked)
    }

    @Test fun interactionExtendsDeadlineButCheckingDoesNot() {
        val state = state()
        state.bindSession("user")
        state.authenticate("user")
        now = 240_000L
        assertTrue(state.recordInteraction())
        now = 300_000L
        assertEquals(240_000L, state.remainingMillis())
        now = 540_000L
        assertTrue(state.checkTimeout())
    }

    @Test fun backgroundTimeCountsFromLastInteraction() {
        val state = state()
        state.bindSession("user")
        state.authenticate("user")
        now = 299_999L
        assertFalse(state.checkTimeout())
        now = 300_000L
        assertTrue(state.checkTimeout())
    }

    @Test fun changingAccountAndRecreatingProcessRequireAuthentication() {
        val state = state()
        state.bindSession("first")
        state.authenticate("first")
        state.bindSession("second")
        assertTrue(state.isLocked)
        assertFalse(state.authenticate("first"))
        val recreated = state()
        recreated.bindSession("second")
        assertTrue(recreated.isLocked)
    }

    @Test fun signingOutClearsTheUnlockGrant() {
        val state = state()
        state.bindSession("user")
        state.authenticate("user")
        state.bindSession(null)
        assertFalse(state.authenticate("user"))
        state.bindSession("user")
        assertTrue(state.isLocked)
    }

    @Test fun recreatingActivityDoesNotResetTheDeadline() {
        val state = state()
        state.bindSession("user")
        state.authenticate("user")
        now = 200_000L
        state.bindSession("user")
        assertEquals(100_000L, state.remainingMillis())
    }

    @Test fun aClockResetFailsClosed() {
        now = 500_000L
        val state = state()
        state.bindSession("user")
        state.authenticate("user")
        now = 1L
        assertTrue(state.checkTimeout())
    }
}
