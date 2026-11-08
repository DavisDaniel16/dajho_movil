package com.example.dajho

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.*

internal enum class PasswordChangeError {
    CURRENT_REQUIRED, NEW_TOO_SHORT, NEW_TOO_LONG, CONFIRM_MISMATCH, SAME_PASSWORD,
    INVALID_CURRENT, NETWORK, RATE_LIMIT, WEAK_NEW, REAUTH_REQUIRED, ACCOUNT_CHANGED,
    SESSION_REQUIRED, UNEXPECTED
}

internal class PasswordChangeException(val reason: PasswordChangeError) : Exception(reason.name)

internal fun validatePasswordChange(current: String, fresh: String, confirmation: String): PasswordChangeError? = when {
    current.isEmpty() -> PasswordChangeError.CURRENT_REQUIRED
    fresh.codePointCount(0, fresh.length) < 8 -> PasswordChangeError.NEW_TOO_SHORT
    fresh.toByteArray(Charsets.UTF_8).size > 72 -> PasswordChangeError.NEW_TOO_LONG
    fresh != confirmation -> PasswordChangeError.CONFIRM_MISMATCH
    current == fresh -> PasswordChangeError.SAME_PASSWORD
    else -> null
}

/**
 * Auth REST flow for the pinned 2.6 SDK, which cannot send current_password.
 * The verification session stays in memory; it never replaces DAJHO's stored session.
 * No HTTP logging, automatic retries or redirects are installed on the dedicated client.
 */
internal class PasswordChangeService(
    private val http: HttpClient,
    baseUrl: String,
    private val apiKey: String
) {
    private val endpoint = baseUrl.trimEnd('/') + "/auth/v1"

    init { require(baseUrl.startsWith("https://")) }

    suspend fun change(
        accountId: String,
        email: String,
        current: String,
        fresh: String,
        isCurrentAccount: () -> Boolean
    ) {
        var temporaryToken: String? = null
        try {
            if (!isCurrentAccount()) throw PasswordChangeException(PasswordChangeError.ACCOUNT_CHANGED)
            val verification = http.post("$endpoint/token") {
                parameter("grant_type", "password")
                header("apikey", apiKey)
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject { put("email", email); put("password", current) }.toString())
            }
            checkResponse(verification, verifying = true)
            val session = Json.parseToJsonElement(verification.bodyAsText()).jsonObject
            temporaryToken = session["access_token"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: throw PasswordChangeException(PasswordChangeError.UNEXPECTED)
            val verifiedId = session["user"]?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull
            if (verifiedId != accountId || !isCurrentAccount()) {
                throw PasswordChangeException(PasswordChangeError.ACCOUNT_CHANGED)
            }
            val response = http.put("$endpoint/user") {
                header("apikey", apiKey)
                bearerAuth(temporaryToken)
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("password", fresh)
                    put("current_password", current)
                }.toString())
            }
            checkResponse(response, verifying = false)
        } catch (error: CancellationException) {
            throw error
        } catch (error: PasswordChangeException) {
            throw error
        } catch (_: java.io.IOException) {
            throw PasswordChangeException(PasswordChangeError.NETWORK)
        } catch (_: HttpRequestTimeoutException) {
            throw PasswordChangeException(PasswordChangeError.NETWORK)
        } catch (_: Exception) {
            throw PasswordChangeException(PasswordChangeError.UNEXPECTED)
        } finally {
            temporaryToken?.let { token ->
                withContext(NonCancellable) {
                    withTimeoutOrNull(3_000L) {
                        try {
                            http.post("$endpoint/logout") {
                                parameter("scope", "local")
                                header("apikey", apiKey)
                                bearerAuth(token)
                            }
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Exception) {
                            // Best effort cleanup must not hide a successful update or its original error.
                        }
                    }
                }
            }
        }
    }

    private suspend fun checkResponse(response: HttpResponse, verifying: Boolean) {
        if (response.status.value in 200..299) return
        val code = runCatching {
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            (body["error_code"] ?: body["code"])?.jsonPrimitive?.contentOrNull
        }.getOrNull()
        val reason = when {
            response.status.value == 429 -> PasswordChangeError.RATE_LIMIT
            code in setOf("invalid_credentials", "current_password_mismatch") -> PasswordChangeError.INVALID_CURRENT
            code == "weak_password" -> PasswordChangeError.WEAK_NEW
            code == "same_password" -> PasswordChangeError.SAME_PASSWORD
            code in setOf("reauthentication_needed", "reauthentication_not_valid", "insufficient_aal", "current_password_required") ->
                PasswordChangeError.REAUTH_REQUIRED
            !verifying && response.status.value == 401 -> PasswordChangeError.SESSION_REQUIRED
            response.status.value >= 500 -> PasswordChangeError.NETWORK
            else -> PasswordChangeError.UNEXPECTED
        }
        throw PasswordChangeException(reason)
    }
}
