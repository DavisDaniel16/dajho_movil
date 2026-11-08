package com.example.dajho

import java.io.IOException
import kotlinx.coroutines.delay

/** Retry once only for transport failures, never for invalid credentials or other server responses. */
internal suspend fun <T> retryLoginOnNetworkFailure(
    wait: suspend () -> Unit = { delay(1000) },
    signIn: suspend () -> T
): T {
    return try {
        signIn()
    } catch (error: IOException) {
        wait()
        signIn()
    }
}
