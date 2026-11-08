package com.example.dajho

import android.content.Context
import io.github.jan.supabase.gotrue.SessionStatus
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.user.UserInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Local identity is allowed from storage only while the SDK reports a transport error.
 * This never imports an expired token as a valid server session or replaces server authorization.
 */
internal suspend fun resolveLocalUser(
    status: () -> SessionStatus,
    storedUser: suspend () -> UserInfo?
): UserInfo? {
    val initial = status()
    if (initial is SessionStatus.Authenticated) return initial.session.user
    if (initial !is SessionStatus.NetworkError) return null
    val cached = try {
        storedUser()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }
    // Storage may suspend while a logout or a different login completes.
    return when (val latest = status()) {
        is SessionStatus.Authenticated -> latest.session.user
        is SessionStatus.NetworkError -> cached
        else -> null
    }
}

internal object SessionAccess {
    suspend fun localUser(): UserInfo? {
        val auth = SupabaseModule.client.auth
        auth.awaitInitialization()
        return resolveLocalUser({ auth.sessionStatus.value }) { auth.sessionManager.loadSession()?.user }
    }

    /** Revoke this server session when reachable and always forget it on this device. */
    suspend fun signOut(context: Context) {
        AppSessionLock.clear()
        try {
            if (context.hasInternetConnection()) withTimeoutOrNull(3_000L) {
                try {
                    SupabaseModule.client.auth.signOut()
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    // A transport failure must not retain a session on a device being signed out.
                }
            }
        } finally {
            withContext(NonCancellable) {
                SupabaseModule.client.auth.clearSession()
                BusinessSession.clear()
            }
        }
    }
}
