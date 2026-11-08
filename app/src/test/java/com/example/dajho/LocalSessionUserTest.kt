package com.example.dajho

import io.github.jan.supabase.gotrue.SessionStatus
import io.github.jan.supabase.gotrue.user.UserInfo
import io.github.jan.supabase.gotrue.user.UserSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LocalSessionUserTest {
    private val user = UserInfo(aud = "authenticated", id = "account-a")
    private fun authenticated(id: String = user.id) = SessionStatus.Authenticated(
        UserSession(accessToken = "test-only", refreshToken = "test-only", expiresIn = 3600,
            tokenType = "bearer", user = user.copy(id = id))
    )

    @Test fun usesLiveSessionWithoutReadingStorage() = runBlocking {
        val result = resolveLocalUser({ authenticated() }) { error("Must not read storage") }
        assertEquals(user.id, result?.id)
    }

    @Test fun allowsStoredIdentityOnlyDuringNetworkError() = runBlocking {
        assertEquals(user, resolveLocalUser({ SessionStatus.NetworkError }) { user })
    }

    @Test fun signedOutAccountCannotReturnThroughOfflineCache() = runBlocking {
        assertNull(resolveLocalUser({ SessionStatus.NotAuthenticated(true) }) { error("Must not read storage") })
    }

    @Test fun absentSessionCannotReturnThroughOfflineCache() = runBlocking {
        assertNull(resolveLocalUser({ SessionStatus.NotAuthenticated(false) }) { error("Must not read storage") })
    }

    @Test fun noCachedIdentityMeansNoOfflineAccess() = runBlocking {
        assertNull(resolveLocalUser({ SessionStatus.NetworkError }) { null })
    }

    @Test fun signOutDuringStorageReadRejectsTheOldIdentity() = runBlocking {
        var status: SessionStatus = SessionStatus.NetworkError
        assertNull(resolveLocalUser({ status }) {
            status = SessionStatus.NotAuthenticated(true)
            user
        })
    }

    @Test fun accountChangeDuringStorageReadUsesTheNewSession() = runBlocking {
        var status: SessionStatus = SessionStatus.NetworkError
        val result = resolveLocalUser({ status }) {
            status = authenticated("account-b")
            user
        }
        assertEquals("account-b", result?.id)
    }

    @Test fun corruptStorageFailsClosed() = runBlocking {
        assertNull(resolveLocalUser({ SessionStatus.NetworkError }) { error("Corrupt session") })
    }

    @Test(expected = CancellationException::class)
    fun cancellationDoesNotBecomeASignedOutAccount() { runBlocking {
        resolveLocalUser({ SessionStatus.NetworkError }) { throw CancellationException() }
    } }
}
