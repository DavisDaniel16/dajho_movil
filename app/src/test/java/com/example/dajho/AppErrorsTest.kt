package com.example.dajho

import io.github.jan.supabase.exceptions.RestException
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException

class AppErrorsTest {
    private fun remote(error: String, status: Int = 400) =
        RestException(error, "private database details", status, "Authorization: Bearer secret-token")

    @Test fun translatesInvalidCredentialsWithoutShowingServerDetails() {
        val message = remote("invalid_credentials").userMessage("iniciar sesión")
        assertTrue(message.contains("correo y contraseña"))
        assertFalse(message.contains("invalid_credentials"))
        assertFalse(message.contains("secret-token"))
    }

    @Test fun unconfirmedEmailHasAnActionableMessage() {
        assertTrue(remote("email_not_confirmed").userMessage().contains("confirma"))
    }

    @Test fun expiredSessionAndForbiddenOperationAreDifferent() {
        assertTrue(remote("unknown", 401).userMessage().contains("iniciar sesión"))
        assertTrue(remote("unknown", 403).userMessage().contains("permisos"))
    }

    @Test fun rateLimitDoesNotLookLikeANetworkOrCredentialFailure() {
        val message = remote("over_request_rate_limit", 429).userMessage()
        assertTrue(message.contains("Espera"))
        assertFalse(message.contains("contraseña"))
    }

    @Test fun networkCausesAreRecognizedEvenWhenWrapped() {
        assertTrue(RuntimeException("private headers", IOException()).userMessage().contains("conexión"))
        assertTrue(SocketTimeoutException("private URL").userMessage().contains("tiempo"))
    }

    @Test fun businessErrorsAreExplainedWithoutEchoingTheResponse() {
        val message = remote("stock insuficiente; private table payload").userMessage()
        assertTrue(message.contains("existencias"))
        assertFalse(message.contains("private"))
        assertTrue(remote("caja cerrada").userMessage().contains("Abre la caja"))
    }

    @Test fun unknownErrorsUseTheOperationAndNeverRawExceptionText() {
        val message = IllegalStateException("password=secret; URL: private").userMessage("guardar el gasto")
        assertTrue(message.contains("guardar el gasto"))
        assertFalse(message.contains("secret"))
        assertFalse(message.contains("URL"))
        assertFalse(remote("server secret details").userMessage().contains("secret"))
    }

    @Test fun knownLocalValidationRemainsSpecificButUnknownInputIsNotEchoed() {
        assertEquals("Usa máximo dos decimales", IllegalArgumentException("Usa máximo dos decimales").userMessage())
        assertFalse(IllegalArgumentException("secret input").userMessage().contains("secret"))
    }

    @Test(expected = CancellationException::class)
    fun cancelledWorkDoesNotBecomeAnErrorNotification() {
        CancellationException().userMessage()
    }
}
