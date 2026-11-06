package com.example.dajho

import android.content.Context
import com.example.dajho.db.AppDatabase
import com.example.dajho.db.entities.ClienteEntity
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object CustomerRepository {
    suspend fun clients(business: Long, context: Context? = null): List<ClienteDto> {
        val db = context?.let { AppDatabase.getInstance(it) }

        // 1. Cargar desde la base de datos local (Room) primero
        var localList: List<ClienteDto> = emptyList()
        if (db != null) {
            withContext(Dispatchers.IO) {
                try {
                    val entities = db.clienteDao().getClientesByNegocio(business)
                    localList = entities.map { it.toDto() }
                } catch (_: Exception) {
                    // Fallback silencioso si falla la lectura local
                }
            }
        }

        // 2. Intentar refrescar desde Supabase si hay red
        try {
            val all = mutableListOf<ClienteDto>()
            do {
                val page = SupabaseModule.client.postgrest.rpc("listar_gestion", buildJsonObject {
                    put("p_negocio", business)
                    put("p_modulo", "cliente")
                    put("p_offset", all.size)
                }).decodeList<ClienteDto>()
                all.addAll(page)
            } while (page.size == 200)

            // Guardar en la base de datos local Room
            if (db != null) {
                withContext(Dispatchers.IO) {
                    try {
                        val entities = all.mapNotNull { it.toEntity(business) }
                        db.clienteDao().insertOrUpdateClientes(entities)
                    } catch (_: Exception) {
                        // Ignore local cache error
                    }
                }
            }

            return all
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            if (localList.isNotEmpty()) {
                return localList
            }
            throw e
        }
    }

    suspend fun debts(business: Long): List<DeudaDto> {
        val all = mutableListOf<DeudaDto>()
        do {
            val page = SupabaseModule.client.postgrest.rpc("deudas_negocio", buildJsonObject {
                put("p_negocio", business); put("p_offset", all.size)
            }).decodeList<DeudaDto>()
            all.addAll(page)
        } while (page.size == 200)
        return all
    }

    private fun ClienteDto.toEntity(negocioId: Long): ClienteEntity? {
        val clienteId = id ?: return null
        return ClienteEntity(
            id = clienteId,
            negocio_id = negocioId,
            nombre = nombre ?: "",
            telefono = telefono
        )
    }

    private fun ClienteEntity.toDto(): ClienteDto {
        return ClienteDto(
            id = id,
            nombre = nombre,
            telefono = telefono,
            negocio_id = negocio_id
        )
    }
}
