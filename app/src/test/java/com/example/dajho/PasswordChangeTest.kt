package com.example.dajho

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PasswordChangeTest {
    @Test fun validatesFieldsWithoutTrimmingPasswords() {
        assertEquals(PasswordChangeError.CURRENT_REQUIRED, validatePasswordChange("", "new-pass-123", "new-pass-123"))
        assertEquals(PasswordChangeError.NEW_TOO_SHORT, validatePasswordChange("old", "short", "short"))
        assertEquals(PasswordChangeError.CONFIRM_MISMATCH, validatePasswordChange("old", "new-pass-123", "different"))
        assertEquals(PasswordChangeError.SAME_PASSWORD, validatePasswordChange("same-pass", "same-pass", "same-pass"))
        assertEquals(PasswordChangeError.NEW_TOO_LONG, validatePasswordChange("old", "é".repeat(37), "é".repeat(37)))
        assertNull(validatePasswordChange("old", " new-pass ", " new-pass "))
    }

    @Test fun failedVerificationNeverUpdatesPassword() = runBlocking {
        var calls = 0
        val client = HttpClient(MockEngine {
            calls++
            assertEquals("/auth/v1/token", it.url.encodedPath)
            respond("""{"error_code":"invalid_credentials"}""", HttpStatusCode.BadRequest)
        })
        try {
            val failure = runCatching { service(client).change("account-a", "user@example.test", "wrong", "new-pass-123") { true } }.exceptionOrNull()
            assertEquals(PasswordChangeError.INVALID_CURRENT, (failure as PasswordChangeException).reason)
            assertEquals(1, calls)
        } finally { client.close() }
    }

    @Test fun usesOnlyTemporaryTokenAndRevokesOnlyTemporarySession() = runBlocking {
        val paths = mutableListOf<String>()
        val client = HttpClient(MockEngine {
            paths += it.url.encodedPath
            assertEquals("test-publishable-key", it.headers["apikey"])
            when (it.url.encodedPath) {
                "/auth/v1/token" -> {
                    assertNull(it.headers[HttpHeaders.Authorization])
                    assertEquals("password", it.url.parameters["grant_type"])
                    val json = kotlinx.serialization.json.Json.parseToJsonElement((it.body as TextContent).text)
                    assertTrue(json.toString().contains("old-pass-123"))
                    respond("""{"access_token":"temporary-token","user":{"id":"account-a"}}""", HttpStatusCode.OK)
                }
                "/auth/v1/user" -> {
                    assertEquals(HttpMethod.Put, it.method)
                    assertEquals("Bearer temporary-token", it.headers[HttpHeaders.Authorization])
                    val body = (it.body as TextContent).text
                    assertTrue(body.contains("current_password"))
                    assertTrue(body.contains("new-pass-123"))
                    respond("""{"id":"account-a"}""", HttpStatusCode.OK)
                }
                else -> {
                    assertEquals("/auth/v1/logout", it.url.encodedPath)
                    assertEquals("local", it.url.parameters["scope"])
                    assertEquals("Bearer temporary-token", it.headers[HttpHeaders.Authorization])
                    respond("", HttpStatusCode.NoContent)
                }
            }
        })
        try {
            service(client).change("account-a", "user@example.test", "old-pass-123", "new-pass-123") { true }
            assertEquals(listOf("/auth/v1/token", "/auth/v1/user", "/auth/v1/logout"), paths)
        } finally { client.close() }
    }

    @Test fun accountChangePreventsUpdateAndStillCleansTemporarySession() = runBlocking {
        val paths = mutableListOf<String>()
        var sameAccount = true
        val client = HttpClient(MockEngine {
            paths += it.url.encodedPath
            if (it.url.encodedPath.endsWith("/token")) {
                sameAccount = false
                respond("""{"access_token":"temporary-token","user":{"id":"account-a"}}""", HttpStatusCode.OK)
            } else respond("", HttpStatusCode.NoContent)
        })
        try {
            val error = runCatching { service(client).change("account-a", "user@example.test", "old", "new-pass-123") { sameAccount } }.exceptionOrNull()
            assertEquals(PasswordChangeError.ACCOUNT_CHANGED, (error as PasswordChangeException).reason)
            assertEquals(listOf("/auth/v1/token", "/auth/v1/logout"), paths)
        } finally { client.close() }
    }

    @Test fun wrongVerifiedAccountNeverReceivesAnUpdate() = runBlocking {
        var updated = false
        val client = HttpClient(MockEngine {
            if (it.url.encodedPath.endsWith("/token"))
                respond("""{"access_token":"temporary-token","user":{"id":"account-b"}}""", HttpStatusCode.OK)
            else {
                if (it.method == HttpMethod.Put) updated = true
                respond("", HttpStatusCode.NoContent)
            }
        })
        try {
            val error = runCatching { service(client).change("account-a", "user@example.test", "old", "new-pass-123") { true } }.exceptionOrNull()
            assertEquals(PasswordChangeError.ACCOUNT_CHANGED, (error as PasswordChangeException).reason)
            assertFalse(updated)
        } finally { client.close() }
    }

    @Test fun rejectedNewPasswordCleansTemporarySessionAndReturnsSafeError() = runBlocking {
        var loggedOut = false
        val client = HttpClient(MockEngine {
            when {
                it.url.encodedPath.endsWith("/token") -> respond("""{"access_token":"temporary-token","user":{"id":"account-a"}}""", HttpStatusCode.OK)
                it.method == HttpMethod.Put -> respond("""{"error_code":"weak_password","message":"untrusted secret detail"}""", HttpStatusCode.UnprocessableEntity)
                else -> { loggedOut = true; respond("", HttpStatusCode.NoContent) }
            }
        })
        try {
            val error = runCatching { service(client).change("account-a", "user@example.test", "old", "new-pass-123") { true } }.exceptionOrNull()
            assertEquals(PasswordChangeError.WEAK_NEW, (error as PasswordChangeException).reason)
            assertFalse(error.message.orEmpty().contains("secret detail"))
            assertTrue(loggedOut)
        } finally { client.close() }
    }

    @Test fun transportFailureIsNotReportedAsWrongPassword() = runBlocking {
        val client = HttpClient(MockEngine { throw java.io.IOException("private request headers") })
        try {
            val error = runCatching { service(client).change("account-a", "user@example.test", "old", "new-pass-123") { true } }.exceptionOrNull()
            assertEquals(PasswordChangeError.NETWORK, (error as PasswordChangeException).reason)
            assertFalse(error.message.orEmpty().contains("headers"))
        } finally { client.close() }
    }

    @Test fun cancellationAfterVerificationStillRevokesTemporarySession() = runBlocking {
        var loggedOut = false
        val client = HttpClient(MockEngine {
            when {
                it.url.encodedPath.endsWith("/token") -> respond("""{"access_token":"temporary-token","user":{"id":"account-a"}}""", HttpStatusCode.OK)
                it.method == HttpMethod.Put -> throw kotlinx.coroutines.CancellationException()
                else -> { loggedOut = true; respond("", HttpStatusCode.NoContent) }
            }
        })
        try {
            val error = runCatching { service(client).change("account-a", "user@example.test", "old", "new-password") { true } }.exceptionOrNull()
            assertTrue(error is kotlinx.coroutines.CancellationException)
            assertTrue(loggedOut)
        } finally { client.close() }
    }

    @Test fun rateLimitIsNotRetriedOrReportedAsWrongPassword() = runBlocking {
        var requests = 0
        val client = HttpClient(MockEngine {
            requests++
            respond("""{"error_code":"over_request_rate_limit"}""", HttpStatusCode.TooManyRequests)
        })
        try {
            val error = runCatching { service(client).change("account-a", "user@example.test", "old", "new-password") { true } }.exceptionOrNull()
            assertEquals(PasswordChangeError.RATE_LIMIT, (error as PasswordChangeException).reason)
            assertEquals(1, requests)
        } finally { client.close() }
    }

    @Test fun changedAccountBeforeVerificationMakesNoRequests() = runBlocking {
        var requests = 0
        val client = HttpClient(MockEngine { requests++; respond("", HttpStatusCode.OK) })
        try {
            val error = runCatching { service(client).change("account-a", "user@example.test", "old", "new-password") { false } }.exceptionOrNull()
            assertEquals(PasswordChangeError.ACCOUNT_CHANGED, (error as PasswordChangeException).reason)
            assertEquals(0, requests)
        } finally { client.close() }
    }

    @Test fun cleanupFailureDoesNotTurnASuccessfulChangeIntoAnError() = runBlocking {
        val client = HttpClient(MockEngine {
            when {
                it.url.encodedPath.endsWith("/token") -> respond("""{"access_token":"temporary-token","user":{"id":"account-a"}}""", HttpStatusCode.OK)
                it.method == HttpMethod.Put -> respond("""{"id":"account-a"}""", HttpStatusCode.OK)
                else -> throw java.io.IOException("offline during cleanup")
            }
        })
        try {
            service(client).change("account-a", "user@example.test", "old", "new-password") { true }
        } finally { client.close() }
    }

    private fun service(client: HttpClient) = PasswordChangeService(client, "https://example.test", "test-publishable-key")
}
