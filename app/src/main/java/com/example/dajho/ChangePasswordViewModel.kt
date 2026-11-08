package com.example.dajho

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.jan.supabase.gotrue.auth
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal sealed interface PasswordChangeState {
    data object Idle : PasswordChangeState
    data object Submitting : PasswordChangeState
    data class Failure(val reason: PasswordChangeError) : PasswordChangeState
    data class Success(val accountId: String) : PasswordChangeState
}

/** Retains an in-flight operation across rotation, never passwords in SavedStateHandle. */
internal class ChangePasswordViewModel : ViewModel() {
    private val mutableState = MutableStateFlow<PasswordChangeState>(PasswordChangeState.Idle)
    val state = mutableState.asStateFlow()

    fun submit(current: String, fresh: String, confirmation: String) {
        if (mutableState.value is PasswordChangeState.Submitting ||
            mutableState.value is PasswordChangeState.Success) return
        validatePasswordChange(current, fresh, confirmation)?.let {
            mutableState.value = PasswordChangeState.Failure(it)
            return
        }
        mutableState.value = PasswordChangeState.Submitting
        viewModelScope.launch {
            val client = HttpClient(Android) {
                expectSuccess = false
                followRedirects = false
                install(HttpTimeout) {
                    requestTimeoutMillis = 15_000
                    connectTimeoutMillis = 15_000
                    socketTimeoutMillis = 15_000
                }
            }
            try {
                val auth = SupabaseModule.client.auth
                auth.awaitInitialization()
                val user = auth.currentUserOrNull()
                    ?: throw PasswordChangeException(PasswordChangeError.SESSION_REQUIRED)
                val email = user.email?.takeIf { it.isNotBlank() }
                    ?: throw PasswordChangeException(PasswordChangeError.SESSION_REQUIRED)
                PasswordChangeService(client, BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY)
                    .change(user.id, email, current, fresh) { auth.currentUserOrNull()?.id == user.id }
                mutableState.value = PasswordChangeState.Success(user.id)
            } catch (error: CancellationException) {
                throw error
            } catch (error: PasswordChangeException) {
                mutableState.value = PasswordChangeState.Failure(error.reason)
            } catch (_: Exception) {
                mutableState.value = PasswordChangeState.Failure(PasswordChangeError.UNEXPECTED)
            } finally {
                client.close()
            }
        }
    }

    fun consumeSuccess() { mutableState.value = PasswordChangeState.Idle }
}
