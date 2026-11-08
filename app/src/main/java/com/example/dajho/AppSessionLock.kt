package com.example.dajho

import android.os.SystemClock

/** Shared by protected activities; grants never survive process death. Main thread only. */
internal object AppSessionLock {
    val state = SessionLockState(now = SystemClock::elapsedRealtime)

    fun onLogin(userId: String) {
        state.bindSession(userId)
        state.authenticate(userId)
    }

    fun clear() = state.bindSession(null)
}
