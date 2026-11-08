package com.example.dajho

import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class LoginRetryTest {
    @Test fun retriesOneTemporaryNetworkFailure() = runBlocking {
        var attempts = 0
        val result = retryLoginOnNetworkFailure(wait = {}) {
            if (++attempts == 1) throw IOException("timeout")
            "signed in"
        }
        assertEquals("signed in", result)
        assertEquals(2, attempts)
    }

    @Test fun doesNotRetryInvalidCredentials() = runBlocking {
        var attempts = 0
        try {
            retryLoginOnNetworkFailure(wait = {}) {
                attempts++
                throw IllegalArgumentException("invalid credentials")
            }
            fail("Expected failure")
        } catch (_: IllegalArgumentException) {
            assertEquals(1, attempts)
        }
    }

    @Test fun stopsAfterSecondNetworkFailure() = runBlocking {
        var attempts = 0
        try {
            retryLoginOnNetworkFailure(wait = {}) {
                attempts++
                throw IOException("offline")
            }
            fail("Expected failure")
        } catch (_: IOException) {
            assertEquals(2, attempts)
        }
    }
}
