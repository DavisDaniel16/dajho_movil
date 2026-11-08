package com.example.dajho

import android.content.Context
import android.util.Log
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object SyncManager {
    private const val TAG = "SyncManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()

    fun enqueueAndTrySync(
        context: Context,
        negocioId: Long,
        tipo: String,
        payloadJson: JsonObject,
        requestId: String = java.util.UUID.randomUUID().toString()
    ) {
        val db = DajhoDatabase.getInstance(context)
        val payloadStr = ScreenArgs.json.encodeToString(JsonObject.serializer(), payloadJson)
        db.insertPendingSync(negocioId, tipo, payloadStr, requestId)

        // Try syncing immediately in background if connected
        triggerSync(context)
    }

    fun triggerSync(context: Context) {
        scope.launch {
            syncPending(context)
        }
    }

    suspend fun syncPending(context: Context) {
        if (!context.hasInternetConnection()) return

        syncMutex.withLock {
            val db = DajhoDatabase.getInstance(context)
            val pendingItems = db.getPendingSyncItems()
            if (pendingItems.isEmpty()) return

            Log.d(TAG, "Iniciando sincronización de ${pendingItems.size} operaciones pendientes...")

            for (item in pendingItems) {
                if (!context.hasInternetConnection()) break

                try {
                    val payloadObj = Json.parseToJsonElement(item.payloadJson) as JsonObject

                    when (item.tipo) {
                        "registrar_venta" -> {
                            SupabaseModule.client.postgrest.rpc("registrar_venta", payloadObj)
                        }
                        "abonar_cliente" -> {
                            SupabaseModule.client.postgrest.rpc("abonar_cliente", payloadObj)
                        }
                        "gasto", "operacion_idempotente" -> {
                            val datos = (payloadObj["p_datos"] as? JsonObject) ?: payloadObj

                            val rpcParams = buildJsonObject {
                                put("p_negocio", item.negocioId)
                                put("p_tipo", "gasto")
                                put("p_solicitud", item.requestId)
                                put("p_datos", datos)
                            }
                            SupabaseModule.client.postgrest.rpc("operacion_idempotente", rpcParams)
                        }
                        "crear_producto_offline" -> {
                            SupabaseModule.client.postgrest.rpc("crear_producto_offline", payloadObj)
                        }
                        "editar_producto" -> {
                            SupabaseModule.client.postgrest.rpc("editar_producto", payloadObj)
                        }
                        "editar_variante" -> {
                            SupabaseModule.client.postgrest.rpc("editar_variante", payloadObj)
                        }
                        "editar_empleado" -> {
                            SupabaseModule.client.postgrest.rpc("editar_empleado", payloadObj)
                        }
                        "anular_venta" -> {
                            SupabaseModule.client.postgrest.rpc("anular_venta", payloadObj)
                        }
                        "guardar_catalogo" -> {
                            SupabaseModule.client.postgrest.rpc("guardar_catalogo", payloadObj)
                        }
                        "abrir_caja" -> {
                            SupabaseModule.client.postgrest.rpc("abrir_caja", payloadObj)
                        }
                        "cerrar_caja" -> {
                            SupabaseModule.client.postgrest.rpc("cerrar_caja", payloadObj)
                        }
                        "actualizar_perfil" -> {
                            SupabaseModule.client.postgrest.rpc("actualizar_perfil", payloadObj)
                        }
                        "crear_empleado" -> {
                            EmployeeService.create(payloadObj)
                        }
                        else -> {
                            runCatching {
                                SupabaseModule.client.postgrest.rpc(item.tipo, payloadObj)
                            }
                        }
                    }

                    // Operación sincronizada con éxito
                    db.deletePendingSync(item.id)
                    Log.d(TAG, "Operación ${item.tipo} (id: ${item.id}) sincronizada con éxito")

                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    Log.e(TAG, "Error sincronizando ${item.tipo} (id: ${item.id}): ${e.userMessage()}", e)
                    if (e is io.github.jan.supabase.exceptions.RestException && e.statusCode in listOf(400, 409, 422)) {
                        db.deletePendingSync(item.id)
                    } else {
                        break
                    }
                }
            }
        }
    }
}
