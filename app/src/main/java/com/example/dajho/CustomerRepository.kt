package com.example.dajho

import android.content.Context
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object CustomerRepository {
    suspend fun clients(business: Long, context: Context? = null): List<ClienteDto> {
        val db = context?.let { DajhoDatabase.getInstance(it) }

        if (context != null && context.hasInternetConnection()) {
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

                db?.saveClientes(business, all)
                return all
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (db != null) {
                    return db.getClientes(business)
                }
                throw e
            }
        } else if (db != null) {
            return db.getClientes(business)
        }

        return emptyList()
    }

    suspend fun debts(business: Long, context: Context? = null): List<DeudaDto> {
        val db = context?.let { DajhoDatabase.getInstance(it) }

        if (context != null && context.hasInternetConnection()) {
            try {
                val all = mutableListOf<DeudaDto>()
                do {
                    val page = SupabaseModule.client.postgrest.rpc("deudas_negocio", buildJsonObject {
                        put("p_negocio", business)
                        put("p_offset", all.size)
                    }).decodeList<DeudaDto>()
                    all.addAll(page)
                } while (page.size == 200)

                db?.saveDeudas(business, all)
                return all
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (db != null) {
                    return db.getDeudas(business)
                }
                throw e
            }
        } else if (db != null) {
            return db.getDeudas(business)
        }

        return emptyList()
    }
}
