package com.example.dajho

import io.github.jan.supabase.gotrue.auth
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import kotlinx.serialization.json.*

object EmployeeService {
    private val http = HttpClient(Android)
    suspend fun create(payload: JsonObject) {
        val token = SupabaseModule.client.auth.currentSessionOrNull()?.accessToken ?: error("Sesión requerida")
        val response = http.post("${BuildConfig.SUPABASE_URL}/functions/v1/crear-empleado") {
            header(HttpHeaders.Authorization, "Bearer $token")
            header("apikey", BuildConfig.SUPABASE_ANON_KEY)
            contentType(ContentType.Application.Json)
            setBody(payload.toString())
        }
        if (!response.status.isSuccess()) {
            val message = runCatching { Json.parseToJsonElement(response.bodyAsText()).jsonObject["error"]?.jsonPrimitive?.content }.getOrNull()
            error(message ?: "No se pudo completar el alta. Reintenta la misma solicitud.")
        }
    }
}
