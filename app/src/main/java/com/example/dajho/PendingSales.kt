package com.example.dajho

import android.content.Context
import io.github.jan.supabase.gotrue.auth
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** Persist the exact request before sending it, so response loss never generates another sale ID. */
object PendingSales {
    private fun key(business: Long) = "${requireNotNull(SupabaseModule.client.auth.currentUserOrNull()).id}:$business"
    fun get(context: Context, business: Long): JsonObject? = context.getSharedPreferences("pending_sales", Context.MODE_PRIVATE)
        .getString(key(business), null)?.let { Json.parseToJsonElement(it) as JsonObject }
    fun save(context: Context, business: Long, payload: JsonObject) {
        check(context.getSharedPreferences("pending_sales", Context.MODE_PRIVATE).edit().putString(key(business), payload.toString()).commit()) { "No se pudo guardar el cobro pendiente" }
    }
    fun clear(context: Context, business: Long) {
        check(context.getSharedPreferences("pending_sales", Context.MODE_PRIVATE).edit().remove(key(business)).commit())
    }
}
