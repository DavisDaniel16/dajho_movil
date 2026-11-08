package com.example.dajho

import android.content.Context
import kotlinx.serialization.json.*
import java.time.LocalDate

object BusinessOperations {
    suspend fun saveExpense(context: Context, business: Long, data: JsonObject, requestId: String): Long {
        val db = DajhoDatabase.getInstance(context)
        val montoStr = data["monto"]?.jsonPrimitive?.content ?: "0"
        val monto = montoStr.toDoubleOrNull() ?: 0.0

        // Local update in SQLite
        db.addGastoLocalAResumen(business, LocalDate.now().toString(), monto)

        // Queue for sync
        val payload = buildJsonObject {
            put("p_negocio", business)
            put("p_tipo", "gasto")
            put("p_solicitud", requestId)
            put("p_datos", data)
        }
        SyncManager.enqueueAndTrySync(context, business, "gasto", payload, requestId)
        return System.currentTimeMillis()
    }

    suspend fun execute(
        context: Context,
        business: Long,
        type: String,
        data: JsonObject,
        requestId: String = java.util.UUID.randomUUID().toString()
    ): Long {
        val payload = buildJsonObject {
            put("p_negocio", business)
            put("p_tipo", type)
            put("p_solicitud", requestId)
            put("p_datos", data)
        }
        SyncManager.enqueueAndTrySync(context, business, type, payload, requestId)
        return System.currentTimeMillis()
    }
}
