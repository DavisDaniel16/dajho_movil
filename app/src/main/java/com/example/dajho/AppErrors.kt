package com.example.dajho

import io.github.jan.supabase.exceptions.RestException
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.net.SocketTimeoutException

/** Translate known causes; never display raw SDK messages, request headers or SQL details. */
fun Throwable.userMessage(action: String = "completar la operación"): String {
    if (this is CancellationException) throw this
    val causes = generateSequence(this) { it.cause }.take(6).toList()
    if (causes.any { it is SocketTimeoutException || it is io.ktor.client.plugins.HttpRequestTimeoutException }) {
        return "El servidor tardó demasiado tiempo en responder. Comprueba el estado antes de reintentar."
    }
    if (causes.any { it is IOException }) {
        return "No se pudo confirmar la operación por un problema de conexión. Comprueba internet y revisa el estado antes de reintentar."
    }
    val remote = causes.filterIsInstance<RestException>().firstOrNull()
    if (remote != null) {
        val code = remote.error.lowercase(java.util.Locale.ROOT)
        return when {
            code in setOf("invalid_credentials", "invalid_grant", "invalid login credentials") ->
                "No pudimos validar tus datos. Revisa tu correo y contraseña."
            code == "email_not_confirmed" ->
                "Abre el enlace que recibiste por correo y confirma tu cuenta antes de iniciar sesión."
            code in setOf("user_already_exists", "email_exists") ->
                "No se pudo registrar ese correo. Si ya tienes cuenta, inicia sesión o recupera tu contraseña."
            code in setOf("weak_password", "same_password") ->
                "Elige una contraseña diferente y más fuerte que cumpla los requisitos de seguridad."
            code in setOf("otp_expired", "flow_state_expired", "flow_state_not_found") ->
                "El enlace o código ya no es válido. Solicita uno nuevo desde el login."
            code in setOf("reauthentication_needed", "reauthentication_not_valid", "insufficient_aal") ->
                "Tu cuenta necesita una verificación adicional. Vuelve a iniciar sesión."
            code in setOf("session_not_found", "session_expired", "refresh_token_not_found", "refresh_token_already_used") ||
                remote.statusCode == 401 ->
                "La sesión ya no es válida. Vuelve a iniciar sesión para continuar."
            code == "user_banned" ->
                "Esta cuenta tiene el acceso suspendido. Contacta al administrador."
            remote.statusCode == 429 || code.startsWith("over_") && code.endsWith("rate_limit") ->
                "Se alcanzó el límite de solicitudes. Espera unos minutos antes de volver a intentarlo."
            remote.statusCode == 403 || code.contains("permission denied") || code.contains("row-level security") ->
                "No tienes permisos para esta acción. Solicita acceso al propietario del negocio."
            code.contains("stock insuficiente") || code.contains("insufficient stock") ->
                "No hay existencias suficientes. Actualiza el inventario y revisa la cantidad."
            code.contains("caja cerrada") ->
                "La caja está cerrada. Abre la caja antes de registrar la operación."
            remote.statusCode == 409 || code.contains("duplicate key") ->
                "Ya existe un registro con esos datos. Revisa los campos antes de guardar."
            code.contains("foreign key") ->
                "El registro está relacionado con otros datos. Revisa sus movimientos antes de modificarlo."
            remote.statusCode >= 500 ->
                "El servidor no está disponible en este momento. Espera y revisa el estado antes de reintentar."
            else -> "El servidor no confirmó que se pudiera $action. Revisa los datos y el estado antes de reintentar."
        }
    }
    // Only these application-owned validation messages may be shown verbatim.
    val localValidation = setOf(
        "Usa máximo dos decimales", "Escribe sí o no", "Referencia requerida",
        "Debes escribir CONFIRMAR para proceder", "Debes escribir CONFIRMAR para validar",
        "Ingresa el motivo de la solicitud", "La fecha de fin debe ser futura",
        "El abono debe ser menor al total", "La fecha no puede ser anterior a hoy",
        "Nombre requerido", "El monto no puede ser negativo", "Registra primero un proveedor",
        "Registra primero un producto", "Configura primero un autenticador TOTP"
    )
    if (message in localValidation) return message!!
    if (message in setOf("Sesión requerida", "No hay sesión activa")) {
        return "Necesitas iniciar sesión para continuar."
    }
    if (message == "No tienes una membresía activa. Contacta al propietario.") return message!!
    if (this is IllegalArgumentException) {
        return "Revisa los campos: hay un valor vacío, un formato incorrecto o un monto fuera del rango permitido."
    }
    return "No se pudo $action. Revisa el estado antes de reintentar; si continúa, contacta a soporte."
}
