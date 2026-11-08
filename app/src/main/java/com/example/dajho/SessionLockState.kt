package com.example.dajho

/**
 * Process-local unlock grant, bound to one account. Never persist an unlocked state.
 * The caller supplies a monotonic clock which includes device sleep.
 */
internal class SessionLockState(
    private val timeoutMillis: Long = 5 * 60 * 1000L,
    private val now: () -> Long
) {
    private var userId: String? = null
    private var lastInteraction = 0L
    var isLocked: Boolean = true
        private set

    fun bindSession(id: String?) {
        if (userId != id || id == null) {
            userId = id
            isLocked = true
        }
    }

    fun authenticate(id: String): Boolean {
        if (id != userId) return false
        lastInteraction = now()
        isLocked = false
        return true
    }

    fun checkTimeout(): Boolean {
        if (!isLocked) {
            val elapsed = now() - lastInteraction
            if (elapsed < 0 || elapsed >= timeoutMillis) isLocked = true
        }
        return isLocked
    }

    fun recordInteraction(): Boolean {
        if (checkTimeout()) return false
        lastInteraction = now()
        return true
    }

    fun remainingMillis(): Long =
        if (checkTimeout()) 0L else timeoutMillis - (now() - lastInteraction)
}
