package com.example.dajho

import android.content.Context
import com.example.dajho.db.AppDatabase
import com.example.dajho.db.entities.VentaPendienteEntity
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Persist the exact request before sending it, so response loss never generates another sale ID. */
object PendingSales {
    private fun key(business: Long) = "${requireNotNull(SupabaseModule.client.auth.currentUserOrNull()).id}:$business"

    fun get(context: Context, business: Long): JsonObject? {
        val spJson = context.getSharedPreferences("pending_sales", Context.MODE_PRIVATE)
            .getString(key(business), null)
        if (spJson != null) {
            return Json.parseToJsonElement(spJson) as JsonObject
        }
        return null
    }

    fun save(context: Context, business: Long, payload: JsonObject) {
        context.getSharedPreferences("pending_sales", Context.MODE_PRIVATE)
            .edit().putString(key(business), payload.toString()).apply()

        // También persistir en Room DB para la cola offline
        val db = AppDatabase.getInstance(context)
        val dateStr = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date())
        val entity = VentaPendienteEntity(
            negocio_id = business,
            payload_json = payload.toString(),
            fecha_creacion = dateStr,
            sincronizado = false
        )
        Thread {
            try {
                db.ventaPendienteDao().insertVentaPendiente(entity)
            } catch (_: Exception) {}
        }.start()
    }

    fun clear(context: Context, business: Long) {
        context.getSharedPreferences("pending_sales", Context.MODE_PRIVATE)
            .edit().remove(key(business)).apply()
    }

    suspend fun syncPendingSales(context: Context, business: Long) {
        val db = AppDatabase.getInstance(context)
        withContext(Dispatchers.IO) {
            try {
                val pendientes = db.ventaPendienteDao().getVentasPendientes(business)
                for (item in pendientes) {
                    try {
                        val jsonObject = Json.parseToJsonElement(item.payload_json) as JsonObject
                        SupabaseModule.client.postgrest.rpc("registrar_venta", jsonObject)
                        db.ventaPendienteDao().marcarComoSincronizado(item.id_local)
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        // Si falla por error de conflicto (ya registrada), eliminarla de la cola
                        if (e is io.github.jan.supabase.exceptions.RestException && e.statusCode in listOf(400, 409, 422)) {
                            db.ventaPendienteDao().deleteVentaPendiente(item.id_local)
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }
}
