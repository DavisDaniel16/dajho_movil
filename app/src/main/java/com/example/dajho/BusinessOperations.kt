package com.example.dajho

import android.content.Context
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.json.*

/** Expenses, debt payments and purchases retain the exact request until its result is known. */
object BusinessOperations {
    private fun key(business: Long, type: String) = "${requireNotNull(SupabaseModule.client.auth.currentUserOrNull()).id}:$business:$type"
    fun pending(context: Context, business: Long, type: String): JsonObject? = context.getSharedPreferences("pending_operations", Context.MODE_PRIVATE)
        .getString(key(business, type), null)?.let { Json.parseToJsonElement(it).jsonObject }

    suspend fun execute(context: Context, business: Long, type: String, data: JsonObject): Long {
        val prefs = context.getSharedPreferences("pending_operations", Context.MODE_PRIVATE)
        val key = key(business, type)
        val prior = pending(context, business, type)
        require(prior == null || prior["p_datos"] == data) { "Hay una operación pendiente con otros datos. Recupérala desde Gestión del negocio antes de continuar." }
        val payload = prior ?: buildJsonObject {
            put("p_negocio", business); put("p_tipo", type); put("p_solicitud", java.util.UUID.randomUUID().toString()); put("p_datos", data)
        }
        check(prefs.edit().putString(key, payload.toString()).commit()) { "No se pudo guardar la operación pendiente" }
        try {
            val id = SupabaseModule.client.postgrest.rpc("operacion_idempotente", payload).decodeAs<Long>()
            check(prefs.edit().remove(key).commit())
            return id
        } catch (e: Exception) {
            if (e is io.github.jan.supabase.exceptions.RestException && e.statusCode in listOf(400, 409, 422)) prefs.edit().remove(key).commit()
            throw e
        }
    }
}
